package com.scaccomatto.account;

import java.nio.file.Path;

final class ServerConfig {
    private ServerConfig() {
    }

    static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    static Path googleCredentialsPath() {
        return Path.of(env("GOOGLE_OAUTH_CREDENTIALS_PATH", "server/credentials/client_secret.json"));
    }

    static String googleRedirectBaseUrl(int port) {
        return env("GOOGLE_OAUTH_REDIRECT_BASE_URL", "http://127.0.0.1:" + port);
    }
}
