import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Signed in: achievements follow you to any computer. Each account keeps its own progress
 * (separate from the guest's on this computer): on signing in, what's saved in the account
 * (saves/UID/ach) is loaded into it; after that, every change is saved a few seconds later. (Settings travel the same
 * way through AccountApiClient.getSettings / updateSettings.)
 */
final class CloudSave {
    private CloudSave() { }

    private static final Object LOCK = new Object();
    private static java.util.Timer timer;

    /** Right after signing in (or being signed back in at startup). */
    static void signedIn() {
        if (!FirebaseLink.signedInToAccount()) return;
        Thread t = new Thread(() -> {
            try {
                String uid = FirebaseLink.uid();
                Achievements.useProfile(uid);      // this account's own progress, not the guest's
                Moderation.loadBlockListFromAccount();
                Map<String, Object> saved = new LinkedHashMap<>();
                for (Map.Entry<String, Object> e : FirebaseLink.asMap(FirebaseLink.get("saves/" + uid + "/ach")).entrySet()) {
                    saved.put(decode(e.getKey()), e.getValue());
                }
                Achievements.absorb(saved);
                upload();
            } catch (Exception e) {
                System.err.println("Cloud save unavailable: " + e.getMessage());
            }
        }, "cloud-save-sync");
        t.setDaemon(true);
        t.start();
    }

    /** Something was earned: save it to the account in a few seconds (changes are bundled). */
    static void achievementsChanged() {
        if (!FirebaseLink.signedInToAccount()) return;
        synchronized (LOCK) {
            if (timer != null) timer.cancel();
            timer = new java.util.Timer("cloud-save", true);
            timer.schedule(new java.util.TimerTask() {
                @Override public void run() {
                    try { upload(); } catch (Exception ignored) { }
                }
            }, 5000);
        }
    }

    private static void upload() throws java.io.IOException {
        if (!FirebaseLink.signedInToAccount()) return;
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : Achievements.snapshot().entrySet()) {
            out.put(encode(e.getKey()), e.getValue());
        }
        if (!out.isEmpty()) FirebaseLink.patch("saves/" + FirebaseLink.uid() + "/ach", out);
    }

    // Firebase keys can't contain . $ # [ ] /
    private static String encode(String k) {
        return URLEncoder.encode(k, StandardCharsets.UTF_8).replace(".", "%2E");
    }

    private static String decode(String k) {
        return URLDecoder.decode(k, StandardCharsets.UTF_8);
    }
}
