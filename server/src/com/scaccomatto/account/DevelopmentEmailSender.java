package com.scaccomatto.account;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.Set;

final class DevelopmentEmailSender implements EmailSender {
    private final Path outbox;

    DevelopmentEmailSender(Path outbox) {
        this.outbox = outbox;
    }

    @Override
    public synchronized String sendUsernameChangeCode(
            String email,
            String username,
            String newUsername,
            String code) throws Exception {
        Path parent = outbox.getParent();
        if (parent != null) Files.createDirectories(parent);
        String line = Instant.now() + " | " + email + " | username=" + username
                + " | newUsername=" + newUsername + " | code=" + code + System.lineSeparator();
        Files.writeString(
                outbox,
                line,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
        try {
            Files.setPosixFilePermissions(
                    outbox,
                    Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
        }
        System.out.println("[DEV EMAIL] Username change OTP for " + email + ": " + code);
        return "development_outbox";
    }
}
