package com.scaccomatto.account;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

final class Database {
    private static final String DB_USER_ENV = "ACCOUNT_DB_USER";
    private static final String DB_PASS_ENV = "ACCOUNT_DB_PASS";

    private final String jdbcUrl;

    Database(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    Connection connect() throws SQLException {
        String user = System.getenv(DB_USER_ENV);
        String pass = System.getenv(DB_PASS_ENV);
        if (user == null || pass == null) {
            throw new IllegalStateException(
                    DB_USER_ENV + " and " + DB_PASS_ENV + " environment variables must be set");
        }
        return DriverManager.getConnection(jdbcUrl, user, pass);
    }

    void migrate(Path migrationPath) throws SQLException, IOException {
        java.util.List<Path> migrations;
        if (Files.isDirectory(migrationPath)) {
            try (java.util.stream.Stream<Path> files = Files.list(migrationPath)) {
                migrations = files
                        .filter(path -> path.getFileName().toString().endsWith(".sql"))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .toList();
            }
        } else {
            migrations = java.util.List.of(migrationPath);
        }
        for (Path migration : migrations) {
            runMigration(migration);
        }
    }

    private void runMigration(Path migration) throws SQLException, IOException {
        String sql = Files.readString(migration, StandardCharsets.UTF_8);
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            for (String command : sql.split(";")) {
                String trimmed = command.trim();
                if (!trimmed.isEmpty()) statement.execute(trimmed);
            }
        }
    }
}
