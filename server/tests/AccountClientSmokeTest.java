import java.nio.file.Files;
import java.nio.file.Path;

public final class AccountClientSmokeTest {
    public static void main(String[] args) throws Exception {
        String baseUrl = args.length == 0 ? "http://127.0.0.1:8080" : args[0];
        Path outbox = Path.of(args.length < 2 ? "server/data/otp-outbox.log" : args[1]);
        String suffix = Long.toString(System.currentTimeMillis());
        String username = "client" + suffix.substring(Math.max(0, suffix.length() - 10));
        String newUsername = "renamed" + suffix.substring(Math.max(0, suffix.length() - 10));
        String password = "Client-Test-" + suffix;

        AccountApiClient api = new AccountApiClient(baseUrl);
        AccountApiClient.Session session = api.register(
                "Initial Profile",
                username,
                username + "@example.com",
                password.toCharArray());
        require(username.equals(session.profile().username()), "Registration profile mismatch");

        AccountApiClient.Profile updated = api.updateProfileName(
                session.token(),
                "Desktop Client Test");
        require(
                "Desktop Client Test".equals(updated.profileName()),
                "Profile update mismatch");

        AccountApiClient.OtpDelivery delivery = api.requestUsernameChange(
                session.token(),
                newUsername);
        require(delivery.email().contains("***"), "Email address was not masked");
        AccountApiClient.Profile unchanged = api.getProfile(session.token());
        require(username.equals(unchanged.username()), "Username changed before OTP verification");

        try {
            api.verifyUsernameChange(session.token(), "000000");
            throw new AssertionError("Incorrect OTP was accepted");
        } catch (AccountApiClient.ApiException expected) {
            require(expected.getStatusCode() == 400, "Expected HTTP 400 for incorrect OTP");
        }

        String code = readOtp(outbox, newUsername);
        AccountApiClient.Profile renamed = api.verifyUsernameChange(session.token(), code);
        require(newUsername.equals(renamed.username()), "Verified username was not applied");

        api.logout(session.token());
        AccountApiClient.Session login = api.login(newUsername, password.toCharArray());
        AccountApiClient.Profile fetched = api.getProfile(login.token());
        require(fetched.id().equals(session.profile().id()), "Authenticated profile mismatch");

        api.logout(login.token());
        try {
            api.getProfile(login.token());
            throw new AssertionError("Logged-out token remained valid");
        } catch (AccountApiClient.ApiException expected) {
            require(expected.getStatusCode() == 401, "Expected HTTP 401 after logout");
        }
        System.out.println("Desktop account client smoke test passed for " + username);
    }

    private static String readOtp(Path outbox, String newUsername) throws Exception {
        String marker = "newUsername=" + newUsername;
        for (int attempt = 0; attempt < 20; attempt++) {
            if (Files.isRegularFile(outbox)) {
                java.util.List<String> lines = Files.readAllLines(outbox);
                for (int i = lines.size() - 1; i >= 0; i--) {
                    String line = lines.get(i);
                    if (!line.contains(marker)) continue;
                    int codeAt = line.lastIndexOf("code=");
                    if (codeAt >= 0) return line.substring(codeAt + 5).trim();
                }
            }
            Thread.sleep(50L);
        }
        throw new AssertionError("OTP was not written to " + outbox);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
