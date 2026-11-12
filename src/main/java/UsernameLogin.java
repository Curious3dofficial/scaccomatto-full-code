import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Signing in with a username instead of an email.
 *
 * The account service only signs in with an email, so the email has to be found from the
 * username first -- without putting everyone's email where anyone could read it. So each
 * account keeps its email under loginKeys/USERNAME locked with its own password
 * (PBKDF2-SHA256, 210,000 rounds, then AES-GCM). Only someone who knows the password can
 * unlock it; to anyone else it's noise.
 *
 * The lock is (re)made every time you sign in with your email, so a password changed
 * through "Forgot password" just means signing in with the email once more.
 */
final class UsernameLogin {
    private UsernameLogin() { }

    static final String PATH = "loginKeys/";
    private static final int ROUNDS = 210_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Why a username sign-in didn't work, with the HTTP-like status the sign-in page expects. */
    static final class Problem extends Exception {
        final int status;
        Problem(int status, String message) { super(message); this.status = status; }
    }

    /** The email for this username, unlocked with the password. */
    static String emailFor(String typed, char[] password) throws Problem {
        String u = typed == null ? "" : typed.trim().replaceFirst("^@", "").toLowerCase(Locale.ROOT);
        if (!u.matches("[a-z0-9_]{3,20}")) throw new Problem(400, "Type your email or your username.");
        Map<String, Object> key;
        try {
            key = FirebaseLink.asMap(FirebaseLink.get(PATH + u));
            if (key.isEmpty()) {
                Object owner = FirebaseLink.get("usernames/" + u);
                if (!(owner instanceof String)) throw new Problem(401, "Wrong username or password.");
                throw new Problem(400, "Sign in with your email this one time, then your username works too.");
            }
        } catch (Problem p) {
            throw p;
        } catch (Exception e) {
            throw new Problem(503, FirebaseLink.friendlyError(e));
        }
        try {
            byte[] salt = Base64.getDecoder().decode(FirebaseLink.str(key.get("s")));
            byte[] iv = Base64.getDecoder().decode(FirebaseLink.str(key.get("i")));
            byte[] data = Base64.getDecoder().decode(FirebaseLink.str(key.get("d")));
            int rounds = (int) FirebaseLink.num(key.get("n"), ROUNDS);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, aesKey(password, salt, rounds), new GCMParameterSpec(128, iv));
            c.updateAAD(u.getBytes(StandardCharsets.UTF_8));
            String email = new String(c.doFinal(data), StandardCharsets.UTF_8);
            if (!email.contains("@")) throw new IllegalStateException();
            return email;
        } catch (Exception wrong) {
            // wrong password -- or it was changed with "Forgot password" since the key was made
            throw new Problem(401, "Wrong username or password. (Changed your password lately? "
                    + "Sign in with your email once.)");
        }
    }

    /** Locks this account's email with the password, for next time. Never fails the sign-in. */
    static void save(String username, String email, char[] password) {
        if (username == null || email == null || password == null || password.length == 0) return;
        String u = username.trim().toLowerCase(Locale.ROOT);
        if (!u.matches("[a-z0-9_]{3,20}")) return;
        try {
            byte[] salt = new byte[16], iv = new byte[12];
            RANDOM.nextBytes(salt);
            RANDOM.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, aesKey(password, salt, ROUNDS), new GCMParameterSpec(128, iv));
            c.updateAAD(u.getBytes(StandardCharsets.UTF_8));
            byte[] data = c.doFinal(email.trim().getBytes(StandardCharsets.UTF_8));
            Map<String, Object> key = new LinkedHashMap<>();
            key.put("s", Base64.getEncoder().encodeToString(salt));
            key.put("i", Base64.getEncoder().encodeToString(iv));
            key.put("d", Base64.getEncoder().encodeToString(data));
            key.put("n", ROUNDS);
            FirebaseLink.put(PATH + u, key);
        } catch (Exception e) {
            // the database rules may not allow it yet: email sign-in still works as before
            System.err.println("Username sign-in key not saved: " + e.getMessage());
        }
    }

    private static SecretKeySpec aesKey(char[] password, byte[] salt, int rounds) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, rounds, 256);
        try {
            byte[] k = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return new SecretKeySpec(k, "AES");
        } finally {
            spec.clearPassword();
        }
    }
}
