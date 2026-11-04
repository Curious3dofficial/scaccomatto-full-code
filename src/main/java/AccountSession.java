public final class AccountSession {
    private static AccountApiClient.Session current;

    private AccountSession() {
    }

    public static synchronized AccountApiClient.Session get() {
        return current;
    }

    public static synchronized void set(AccountApiClient.Session session) {
        current = session;
    }

    public static synchronized void updateProfile(AccountApiClient.Profile profile) {
        if (current == null || profile == null) return;
        current = new AccountApiClient.Session(
                current.token(),
                current.expiresAt(),
                profile,
                current.rememberToken());
    }

    public static synchronized void clear() {
        current = null;
    }

    public static synchronized boolean isSignedIn() {
        return current != null;
    }
}
