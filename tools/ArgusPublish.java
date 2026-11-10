import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Signs Argus updates for the full game. Run from the Scaccomatto folder:
 *
 *   java tools/ArgusPublish.java keygen            once, ever: makes the signing key
 *   java tools/ArgusPublish.java sign JAR OUTDIR   used by publish-argus.sh
 *
 * The private key lives in ~/.scaccomatto-signing/, outside the project, so it is never
 * pushed. Back that folder up: without it you can't publish engine updates to people who
 * already have the game (they'd need a new build with a new key).
 */
public class ArgusPublish {
    static final Path KEY = Paths.get(System.getProperty("user.home"), ".scaccomatto-signing", "argus-signing.key");
    static final Path BOOT = Paths.get("src/main/java/ArgusBoot.java");
    static final Path VERSION_FILE = Paths.get("src/main/assets/engine/argus.version");

    public static void main(String[] args) throws Exception {
        if (!Files.isRegularFile(BOOT)) fail("Run this from the Scaccomatto folder (the one with pom.xml).");
        if (args.length == 1 && args[0].equals("keygen")) keygen();
        else if (args.length == 3 && args[0].equals("sign")) sign(Paths.get(args[1]), Paths.get(args[2]));
        else fail("Use: java tools/ArgusPublish.java keygen   or   sign JAR OUTDIR");
    }

    static void keygen() throws Exception {
        if (Files.exists(KEY)) fail("A signing key already exists at " + KEY + " -- keeping it.");
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Files.createDirectories(KEY.getParent());
        Files.write(KEY, Base64.getEncoder().encode(pair.getPrivate().getEncoded()));
        try {
            Files.setPosixFilePermissions(KEY, PosixFilePermissions.fromString("rw-------"));
            Files.setPosixFilePermissions(KEY.getParent(), PosixFilePermissions.fromString("rwx------"));
        } catch (Exception ignored) {
        }
        String pub = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        String boot = new String(Files.readAllBytes(BOOT), StandardCharsets.UTF_8);
        String updated = boot.replaceFirst("static final String PUBLIC_KEY = \"[^\"]*\";",
                "static final String PUBLIC_KEY = \"" + pub + "\";");
        if (updated.equals(boot)) fail("Couldn't find PUBLIC_KEY in ArgusBoot.java.");
        Files.write(BOOT, updated.getBytes(StandardCharsets.UTF_8));
        System.out.println("Signing key made: " + KEY);
        System.out.println("Its public half is now in src/main/java/ArgusBoot.java.");
        System.out.println("Back up " + KEY.getParent() + " somewhere safe. Never share or commit it.");
    }

    static void sign(Path jar, Path outDir) throws Exception {
        if (!Files.exists(KEY)) fail("No signing key yet. Run: java tools/ArgusPublish.java keygen");
        PrivateKey priv = KeyFactory.getInstance("Ed25519")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(Files.readAllBytes(KEY))));
        PublicKey pub = appPublicKey();

        long version = Long.parseLong(ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMddHHmm")));
        try {   // two in the same minute: still a higher number than the last one
            long last = Long.parseLong(new String(Files.readAllBytes(VERSION_FILE), StandardCharsets.US_ASCII).trim());
            if (version <= last) version = last + 1;
        } catch (Exception ignored) {
        }
        String file = "argus-" + version + ".jar";
        byte[] bytes = Files.readAllBytes(jar);
        String sha = hex(MessageDigest.getInstance("SHA-256").digest(bytes));
        String text = "argus-update\n" + version + "\n" + file + "\n" + bytes.length + "\n" + sha;

        Signature s = Signature.getInstance("Ed25519");
        s.initSign(priv);
        s.update(text.getBytes(StandardCharsets.UTF_8));
        byte[] sig = s.sign();

        // the key in the game must be the one we sign with
        Signature v = Signature.getInstance("Ed25519");
        v.initVerify(pub);
        v.update(text.getBytes(StandardCharsets.UTF_8));
        if (!v.verify(sig)) fail("Your signing key doesn't match the PUBLIC_KEY in ArgusBoot.java.");

        Files.createDirectories(outDir);
        Files.write(outDir.resolve(file), bytes);
        String json = "{\n  \"version\": " + version + ",\n  \"file\": \"" + file + "\",\n  \"size\": " + bytes.length
                + ",\n  \"sha256\": \"" + sha + "\",\n  \"sig\": \"" + Base64.getEncoder().encodeToString(sig) + "\"\n}\n";
        Files.write(outDir.resolve("argus.json"), json.getBytes(StandardCharsets.UTF_8));
        Files.createDirectories(VERSION_FILE.getParent());
        Files.write(VERSION_FILE, (version + "\n").getBytes(StandardCharsets.US_ASCII));
        System.out.println(version);
    }

    static PublicKey appPublicKey() throws Exception {
        String boot = new String(Files.readAllBytes(BOOT), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("static final String PUBLIC_KEY = \"([^\"]*)\";").matcher(boot);
        if (!m.find() || m.group(1).isEmpty()) fail("ArgusBoot.java has no PUBLIC_KEY yet. Run: java tools/ArgusPublish.java keygen");
        return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(m.group(1))));
    }

    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    static void fail(String msg) {
        System.err.println(msg);
        System.exit(1);
    }
}
