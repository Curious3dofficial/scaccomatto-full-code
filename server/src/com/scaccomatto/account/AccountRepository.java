package com.scaccomatto.account;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

final class AccountRepository {
    record User(
            UUID id,
            String username,
            String email,
            String passwordHash,
            String displayName,
            double rating,          // exact; clients show it rounded
            int wins,
            int losses,
            int draws,
            boolean isAdmin,
            boolean banned,
            String settingsJson,
            Instant createdAt) {
    }

    record RememberToken(
            UUID userId,
            Instant expiresAt) {
    }

    record OAuthLink(
            UUID id,
            UUID userId,
            String provider,
            String providerUserId,
            Instant createdAt) {
    }

    record UsernameChangeRequest(
            UUID id,
            UUID userId,
            String newUsername,
            String otpHash,
            int attempts,
            Instant createdAt,
            Instant expiresAt) {
    }

    private final Database database;

    AccountRepository(Database database) {
        this.database = database;
    }

    User createUser(
            String username,
            String email,
            String passwordHash,
            String profileName) throws SQLException {
        User user = new User(
                UUID.randomUUID(),
                username,
                email,
                passwordHash,
                profileName,
                1200,
                0,
                0,
                0,
                false,
                false,
                "{}",
                Instant.now());
        String sql = """
                INSERT INTO users (
                    id, username, username_normalized, email, email_normalized,
                    password_hash, display_name, rating, wins, losses, draws,
                    is_admin, is_banned, settings, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, user.id());
            statement.setString(2, user.username());
            statement.setString(3, normalize(user.username()));
            statement.setString(4, user.email());
            statement.setString(5, normalize(user.email()));
            statement.setString(6, user.passwordHash());
            statement.setString(7, user.displayName());
            statement.setDouble(8, user.rating());
            statement.setInt(9, user.wins());
            statement.setInt(10, user.losses());
            statement.setInt(11, user.draws());
            statement.setBoolean(12, user.isAdmin());
            statement.setBoolean(13, user.banned());
            statement.setString(14, user.settingsJson());
            statement.setObject(15, user.createdAt());
            statement.executeUpdate();
        }
        return user;
    }

    Optional<User> findByLogin(String login) throws SQLException {
        String sql = """
                SELECT * FROM users
                WHERE username_normalized = ? OR email_normalized = ?
                """;
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            String normalized = normalize(login);
            statement.setString(1, normalized);
            statement.setString(2, normalized);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readUser(result)) : Optional.empty();
            }
        }
    }

    Optional<User> findBySessionToken(String token) throws SQLException {
        String sql = """
                SELECT u.* FROM users u
                JOIN sessions s ON s.user_id = u.id
                WHERE s.token_hash = ? AND s.expires_at > ?
                """;
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, Security.hashToken(token));
            statement.setObject(2, Instant.now());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readUser(result)) : Optional.empty();
            }
        }
    }

    String createSession(UUID userId, Instant expiresAt) throws SQLException {
        String token = Security.newToken();
        String sql = """
                INSERT INTO sessions (id, user_id, token_hash, created_at, expires_at)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, userId);
            statement.setString(3, Security.hashToken(token));
            statement.setObject(4, Instant.now());
            statement.setObject(5, expiresAt);
            statement.executeUpdate();
        }
        return token;
    }

    String createRememberToken(UUID userId, Instant expiresAt) throws SQLException {
        String token = Security.newToken();
        String sql = """
                INSERT INTO remember_tokens (token_hash, user_id, created_at, expires_at)
                VALUES (?, ?, ?, ?)
                """;
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, Security.hashToken(token));
            statement.setObject(2, userId);
            statement.setObject(3, Instant.now());
            statement.setObject(4, expiresAt);
            statement.executeUpdate();
        }
        return token;
    }

    Optional<RememberToken> findRememberToken(String token) throws SQLException {
        String sql = "SELECT user_id, expires_at FROM remember_tokens WHERE token_hash = ?";
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, Security.hashToken(token));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                return Optional.of(new RememberToken(
                        result.getObject("user_id", UUID.class),
                        result.getObject("expires_at", Instant.class)));
            }
        }
    }

    void deleteRememberToken(String token) throws SQLException {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM remember_tokens WHERE token_hash = ?")) {
            statement.setString(1, Security.hashToken(token));
            statement.executeUpdate();
        }
    }

    void deleteRememberTokensByUserId(UUID userId) throws SQLException {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM remember_tokens WHERE user_id = ?")) {
            statement.setObject(1, userId);
            statement.executeUpdate();
        }
    }

    void deleteSession(String token) throws SQLException {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM sessions WHERE token_hash = ?")) {
            statement.setString(1, Security.hashToken(token));
            statement.executeUpdate();
        }
    }

    Optional<User> findByOAuthLink(String provider, String providerUserId) throws SQLException {
        String sql = """
                SELECT u.* FROM users u
                JOIN oauth_links l ON l.user_id = u.id
                WHERE l.provider = ? AND l.provider_user_id = ?
                """;
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizeProvider(provider));
            statement.setString(2, providerUserId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readUser(result)) : Optional.empty();
            }
        }
    }

    Optional<OAuthLink> findOAuthLink(String provider, String providerUserId) throws SQLException {
        String sql = "SELECT * FROM oauth_links WHERE provider = ? AND provider_user_id = ?";
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizeProvider(provider));
            statement.setString(2, providerUserId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readOAuthLink(result)) : Optional.empty();
            }
        }
    }

    void linkOAuth(UUID userId, String provider, String providerUserId) throws SQLException {
        String sql = """
                INSERT INTO oauth_links (id, user_id, provider, provider_user_id, created_at)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, userId);
            statement.setString(3, normalizeProvider(provider));
            statement.setString(4, providerUserId);
            statement.setObject(5, Instant.now());
            statement.executeUpdate();
        }
    }

    void unlinkOAuth(UUID userId, String provider) throws SQLException {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM oauth_links WHERE user_id = ? AND provider = ?")) {
            statement.setObject(1, userId);
            statement.setString(2, normalizeProvider(provider));
            statement.executeUpdate();
        }
    }

    User createOAuthUser(
            String username,
            String email,
            String passwordHash,
            String profileName,
            String provider,
            String providerUserId) throws SQLException {
        try (Connection connection = database.connect()) {
            connection.setAutoCommit(false);
            try {
                User user = new User(
                        UUID.randomUUID(),
                        username,
                        email,
                        passwordHash,
                        profileName,
                        1200,
                        0,
                        0,
                        0,
                        false,
                        false,
                        "{}",
                        Instant.now());
                try (PreparedStatement insertUser = connection.prepareStatement("""
                        INSERT INTO users (
                            id, username, username_normalized, email, email_normalized,
                            password_hash, display_name, rating, wins, losses, draws,
                            is_admin, is_banned, settings, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)) {
                    insertUser.setObject(1, user.id());
                    insertUser.setString(2, user.username());
                    insertUser.setString(3, normalize(user.username()));
                    insertUser.setString(4, user.email());
                    insertUser.setString(5, normalize(user.email()));
                    insertUser.setString(6, user.passwordHash());
                    insertUser.setString(7, user.displayName());
                    insertUser.setDouble(8, user.rating());
                    insertUser.setInt(9, user.wins());
                    insertUser.setInt(10, user.losses());
                    insertUser.setInt(11, user.draws());
                    insertUser.setBoolean(12, user.isAdmin());
                    insertUser.setBoolean(13, user.banned());
                    insertUser.setString(14, user.settingsJson());
                    insertUser.setObject(15, user.createdAt());
                    insertUser.executeUpdate();
                }
                try (PreparedStatement insertLink = connection.prepareStatement("""
                        INSERT INTO oauth_links (id, user_id, provider, provider_user_id, created_at)
                        VALUES (?, ?, ?, ?, ?)
                        """)) {
                    insertLink.setObject(1, UUID.randomUUID());
                    insertLink.setObject(2, user.id());
                    insertLink.setString(3, normalizeProvider(provider));
                    insertLink.setString(4, providerUserId);
                    insertLink.setObject(5, Instant.now());
                    insertLink.executeUpdate();
                }
                connection.commit();
                return user;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    User updateDisplayName(UUID userId, String displayName) throws SQLException {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE users SET display_name = ? WHERE id = ?")) {
            statement.setString(1, displayName);
            statement.setObject(2, userId);
            statement.executeUpdate();
        }
        return findById(userId).orElseThrow();
    }

    // Profile picture (PNG bytes), one per user. See V7__add_user_avatars.sql.
    void saveAvatar(UUID userId, byte[] png) throws SQLException {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(
                     "MERGE INTO user_avatars (user_id, image, updated_at) KEY (user_id) VALUES (?, ?, ?)")) {
            statement.setObject(1, userId);
            statement.setBytes(2, png);
            statement.setObject(3, java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));
            statement.executeUpdate();
        }
    }

    Optional<byte[]> findAvatar(UUID userId) throws SQLException {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT image FROM user_avatars WHERE user_id = ?")) {
            statement.setObject(1, userId);
            try (java.sql.ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(rs.getBytes(1)) : Optional.empty();
            }
        }
    }

    void deleteAvatar(UUID userId) throws SQLException {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM user_avatars WHERE user_id = ?")) {
            statement.setObject(1, userId);
            statement.executeUpdate();
        }
    }

    void updateSettings(UUID userId, String settingsJson) throws SQLException {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE users SET settings = ? WHERE id = ?")) {
            statement.setString(1, settingsJson);
            statement.setObject(2, userId);
            statement.executeUpdate();
        }
    }

    User applyGameResult(UUID userId, double newRating, String result) throws SQLException {
        String recordColumn = switch (result) {
            case "win" -> "wins";
            case "loss" -> "losses";
            case "draw" -> "draws";
            default -> throw new IllegalArgumentException("Unsupported game result: " + result);
        };
        String sql = "UPDATE users SET rating = ?, " + recordColumn
                + " = " + recordColumn + " + 1 WHERE id = ?";
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDouble(1, newRating);
            statement.setObject(2, userId);
            statement.executeUpdate();
        }
        return findById(userId).orElseThrow();
    }

    java.util.List<User> listUsers() throws SQLException {
        String sql = "SELECT * FROM users ORDER BY username_normalized";
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            java.util.List<User> users = new java.util.ArrayList<>();
            while (result.next()) {
                users.add(readUser(result));
            }
            return users;
        }
    }

    /** Whether this account has any sign-in linked from another service (Google...). */
    boolean hasOAuthLink(UUID userId) throws SQLException {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT 1 FROM oauth_links WHERE user_id = ? LIMIT 1")) {
            statement.setObject(1, userId);
            try (java.sql.ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    void deleteUser(UUID userId) throws SQLException {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM users WHERE id = ?")) {
            statement.setObject(1, userId);
            statement.executeUpdate();
        }
    }

    void updatePassword(UUID userId, String passwordHash) throws SQLException {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE users SET password_hash = ? WHERE id = ?")) {
            statement.setString(1, passwordHash);
            statement.setObject(2, userId);
            statement.executeUpdate();
        }
    }

    void setBannedStatus(UUID userId, boolean banned) throws SQLException {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE users SET is_banned = ? WHERE id = ?")) {
            statement.setBoolean(1, banned);
            statement.setObject(2, userId);
            statement.executeUpdate();
        }
    }

    boolean usernameExists(String username) throws SQLException {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT 1 FROM users WHERE username_normalized = ?")) {
            statement.setString(1, normalize(username));
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    Optional<UsernameChangeRequest> findUsernameChangeRequest(UUID userId) throws SQLException {
        String sql = "SELECT * FROM username_change_requests WHERE user_id = ?";
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        ? Optional.of(readUsernameChangeRequest(result))
                        : Optional.empty();
            }
        }
    }

    void saveUsernameChangeRequest(
            UUID userId,
            String newUsername,
            String otpHash,
            Instant expiresAt) throws SQLException {
        String sql = """
                MERGE INTO username_change_requests (
                    id, user_id, new_username, new_username_normalized,
                    otp_hash, attempts, created_at, expires_at
                ) KEY (user_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, userId);
            statement.setString(3, newUsername);
            statement.setString(4, normalize(newUsername));
            statement.setString(5, otpHash);
            statement.setInt(6, 0);
            statement.setObject(7, Instant.now());
            statement.setObject(8, expiresAt);
            statement.executeUpdate();
        }
    }

    void incrementUsernameChangeAttempts(UUID userId) throws SQLException {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE username_change_requests SET attempts = attempts + 1 WHERE user_id = ?")) {
            statement.setObject(1, userId);
            statement.executeUpdate();
        }
    }

    User applyUsernameChange(UUID userId, String newUsername) throws SQLException {
        try (Connection connection = database.connect()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE users SET username = ?, username_normalized = ? WHERE id = ?")) {
                    update.setString(1, newUsername);
                    update.setString(2, normalize(newUsername));
                    update.setObject(3, userId);
                    if (update.executeUpdate() != 1) {
                        throw new SQLException("User was not found");
                    }
                }
                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM username_change_requests WHERE user_id = ?")) {
                    delete.setObject(1, userId);
                    delete.executeUpdate();
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
        return findById(userId).orElseThrow();
    }

    void deleteUsernameChangeRequest(UUID userId) throws SQLException {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM username_change_requests WHERE user_id = ?")) {
            statement.setObject(1, userId);
            statement.executeUpdate();
        }
    }

    void deleteExpiredSessions() throws SQLException {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM sessions WHERE expires_at <= ?")) {
            statement.setObject(1, Instant.now());
            statement.executeUpdate();
        }
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM username_change_requests WHERE expires_at <= ?")) {
            statement.setObject(1, Instant.now());
            statement.executeUpdate();
        }
    }

    Optional<User> findById(UUID id) throws SQLException {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM users WHERE id = ?")) {
            statement.setObject(1, id);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readUser(result)) : Optional.empty();
            }
        }
    }

    private static User readUser(ResultSet result) throws SQLException {
        return new User(
                result.getObject("id", UUID.class),
                result.getString("username"),
                result.getString("email"),
                result.getString("password_hash"),
                result.getString("display_name"),
                result.getDouble("rating"),
                result.getInt("wins"),
                result.getInt("losses"),
                result.getInt("draws"),
                result.getBoolean("is_admin"),
                result.getBoolean("is_banned"),
                result.getString("settings"),
                result.getObject("created_at", Instant.class));
    }

    private static UsernameChangeRequest readUsernameChangeRequest(ResultSet result)
            throws SQLException {
        return new UsernameChangeRequest(
                result.getObject("id", UUID.class),
                result.getObject("user_id", UUID.class),
                result.getString("new_username"),
                result.getString("otp_hash"),
                result.getInt("attempts"),
                result.getObject("created_at", Instant.class),
                result.getObject("expires_at", Instant.class));
    }

    private static OAuthLink readOAuthLink(ResultSet result) throws SQLException {
        return new OAuthLink(
                result.getObject("id", UUID.class),
                result.getObject("user_id", UUID.class),
                result.getString("provider"),
                result.getString("provider_user_id"),
                result.getObject("created_at", Instant.class));
    }

    private static String normalizeProvider(String value) {
        return normalize(value);
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
