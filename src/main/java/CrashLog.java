import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * Keeps a record of what went wrong, so a player's "it crashed" can be looked into.
 *
 * <ul>
 *   <li>Every error nobody caught goes into {@code ~/.scaccomatto/logs/crash-DATE.log}
 *       with the app version, system and the full trace (the last 10 days are kept).</li>
 *   <li>Everything the app prints as an error while running is also copied into
 *       {@code ~/.scaccomatto/logs/app.log} (the previous run's is kept as app.old.log).</li>
 * </ul>
 *
 * The same error repeating (say, every frame) is written only a few times.
 */
final class CrashLog {
    private CrashLog() { }

    static final File DIR = new File(new File(System.getProperty("user.home"), ".scaccomatto"), "logs");
    private static final int SAME_ERROR_MAX = 3;
    private static final long APP_LOG_MAX_BYTES = 2L * 1024 * 1024;
    private static final Map<String, Integer> SEEN = new HashMap<>();
    private static boolean installed;

    static synchronized void install() {
        if (installed) return;
        installed = true;
        try {
            DIR.mkdirs();
            pruneOldCrashFiles();
            teeErrorsToFile();
        } catch (Throwable ignored) {
        }
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            record(thread, error);
            // keep the usual console output too
            System.err.println("Exception in thread \"" + thread.getName() + "\"");
            error.printStackTrace();
        });
    }

    /** Writes one uncaught error to today's crash file. */
    static synchronized void record(Thread thread, Throwable error) {
        try { ArgusUpdater.noticeCrash(error); } catch (Throwable ignored) { }
        try {
            StringWriter trace = new StringWriter();
            error.printStackTrace(new PrintWriter(trace));
            String key = signature(error);
            int count = SEEN.merge(key, 1, Integer::sum);
            if (count > SAME_ERROR_MAX) return;

            DIR.mkdirs();
            String day = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            File f = new File(DIR, "crash-" + day + ".log");
            StringBuilder sb = new StringBuilder();
            sb.append("==== ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
              .append(" ====\n");
            sb.append("Scaccomatto ").append(AppVersion.CURRENT)
              .append(" | ").append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.version"))
              .append(" | Java ").append(System.getProperty("java.version"))
              .append(" | theme ").append(safeTheme())
              .append('\n');
            sb.append("Thread: ").append(thread == null ? "?" : thread.getName()).append('\n');
            if (count == SAME_ERROR_MAX) sb.append("(this error keeps repeating; later copies are not written)\n");
            sb.append(trace).append('\n');
            Files.write(f.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Throwable ignored) {
            // the crash log must never itself crash the app
        }
    }

    private static String safeTheme() {
        try {
            return UiTheme.DARK ? "dark" : "light";
        } catch (Throwable t) {
            return "?";
        }
    }

    private static String signature(Throwable e) {
        StackTraceElement[] st = e.getStackTrace();
        return e.getClass().getName() + "@" + (st.length > 0 ? st[0].toString() : "");
    }

    private static void pruneOldCrashFiles() {
        File[] crashes = DIR.listFiles((d, n) -> n.startsWith("crash-") && n.endsWith(".log"));
        if (crashes == null || crashes.length <= 10) return;
        Arrays.sort(crashes, Comparator.comparing(File::getName));
        for (int i = 0; i < crashes.length - 10; i++) crashes[i].delete();
    }

    /** Everything printed to the error stream also lands in app.log (capped at 2 MB). */
    private static void teeErrorsToFile() throws IOException {
        File log = new File(DIR, "app.log");
        File old = new File(DIR, "app.old.log");
        if (log.exists()) {
            old.delete();
            log.renameTo(old);
        }
        OutputStream file = new FileOutputStream(log, false);
        String header = "Scaccomatto " + AppVersion.CURRENT + " started "
                + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "\n";
        file.write(header.getBytes(StandardCharsets.UTF_8));
        PrintStream console = System.err;
        OutputStream tee = new OutputStream() {
            private long written = header.length();

            @Override public void write(int b) throws IOException {
                console.write(b);
                if (written < APP_LOG_MAX_BYTES) { file.write(b); written++; }
            }

            @Override public void write(byte[] b, int off, int len) throws IOException {
                console.write(b, off, len);
                if (written < APP_LOG_MAX_BYTES) {
                    int n = (int) Math.min(len, APP_LOG_MAX_BYTES - written);
                    file.write(b, off, n);
                    written += n;
                }
            }

            @Override public void flush() throws IOException {
                console.flush();
                file.flush();
            }
        };
        System.setErr(new PrintStream(tee, true, StandardCharsets.UTF_8));
    }
}
