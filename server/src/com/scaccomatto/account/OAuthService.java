package com.scaccomatto.account;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

final class OAuthService {
    record ProviderProfile(
            String provider,
            String providerUserId,
            String email,
            String displayName) {
    }

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(12);
    private final HttpClient client;
    private final String baseUrl;

    OAuthService(String baseUrl) {
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(6))
                .build();
    }

    String authorizationUrl(String provider, String state) throws ApiException {
        String normalized = normalizeProvider(provider);
        String redirectUri = redirectUri(normalized);
        return switch (normalized) {
            case "google" -> {
                requireEnv("GOOGLE_CLIENT_ID");
                yield "https://accounts.google.com/o/oauth2/v2/auth?"
                        + query(Map.of(
                        "client_id", env("GOOGLE_CLIENT_ID"),
                        "redirect_uri", redirectUri,
                        "response_type", "code",
                        "scope", "openid email profile",
                        "state", state,
                        "access_type", "online",
                        "prompt", "select_account"));
            }
            default -> throw new ApiException(404, "unknown_provider", "Unsupported OAuth provider.");
        };
    }

    ProviderProfile exchange(String provider, Map<String, String> params) throws IOException, InterruptedException, ApiException {
        String normalized = normalizeProvider(provider);
        return switch (normalized) {
            case "google" -> googleProfile(required(params, "code"));
            default -> throw new ApiException(404, "unknown_provider", "Unsupported OAuth provider.");
        };
    }

    private ProviderProfile googleProfile(String code) throws IOException, InterruptedException, ApiException {
        requireEnv("GOOGLE_CLIENT_ID");
        requireEnv("GOOGLE_CLIENT_SECRET");
        Map<String, String> token = postForm(
                "https://oauth2.googleapis.com/token",
                Map.of(
                        "client_id", env("GOOGLE_CLIENT_ID"),
                        "client_secret", env("GOOGLE_CLIENT_SECRET"),
                        "code", code,
                        "grant_type", "authorization_code",
                        "redirect_uri", redirectUri("google")));
        String accessToken = required(token, "access_token");
        Map<String, String> profile = getJson(
                "https://www.googleapis.com/oauth2/v2/userinfo",
                accessToken);
        return new ProviderProfile(
                "google",
                required(profile, "id"),
                blankToNull(profile.get("email")),
                firstPresent(profile.get("name"), profile.get("email"), "Google Player"));
    }

    private Map<String, String> postForm(String uri, Map<String, String> fields)
            throws IOException, InterruptedException, ApiException {
        String body = postFormText(uri, fields);
        try {
            return Json.parseObject(body);
        } catch (RuntimeException exception) {
            throw new ApiException(502, "provider_response_invalid", "The OAuth provider returned an invalid response.");
        }
    }

    private String postFormText(String uri, Map<String, String> fields)
            throws IOException, InterruptedException, ApiException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(uri))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(query(fields)))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ApiException(502, "provider_request_failed", "The OAuth provider rejected the request.");
        }
        return response.body();
    }

    private Map<String, String> getJson(String uri, String bearerToken)
            throws IOException, InterruptedException, ApiException {
        String body = getText(uri, bearerToken);
        try {
            return Json.parseObject(body);
        } catch (RuntimeException exception) {
            throw new ApiException(502, "provider_response_invalid", "The OAuth provider returned an invalid response.");
        }
    }

    private String getText(String uri, String bearerToken)
            throws IOException, InterruptedException, ApiException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(uri))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .GET();
        if (bearerToken != null && !bearerToken.isBlank()) {
            request.header("Authorization", "Bearer " + bearerToken);
        }
        HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ApiException(502, "provider_request_failed", "The OAuth provider rejected the request.");
        }
        return response.body();
    }

    private String redirectUri(String provider) {
        return baseUrl + "/api/auth/oauth/callback/" + normalizeProvider(provider);
    }

    private static String query(Map<String, String> values) {
        StringBuilder result = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!first) result.append('&');
            first = false;
            result.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
        }
        return result.toString();
    }

    static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> values = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) return values;
        for (String pair : rawQuery.split("&")) {
            int equals = pair.indexOf('=');
            String key = equals >= 0 ? pair.substring(0, equals) : pair;
            String value = equals >= 0 ? pair.substring(equals + 1) : "";
            values.put(decode(key), decode(value));
        }
        return values;
    }

    private static String required(Map<String, String> values, String key) throws ApiException {
        return Optional.ofNullable(values.get(key))
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new ApiException(400, "oauth_missing_field", "The OAuth response is missing " + key + "."));
    }

    private static String firstPresent(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static void requireEnv(String name) throws ApiException {
        if (env(name).isBlank()) {
            throw new ApiException(503, "oauth_not_configured", name + " is not configured.");
        }
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value.trim();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String decode(String value) {
        return java.net.URLDecoder.decode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String normalizeProvider(String provider) {
        return provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
    }

    private static String stripTrailingSlash(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result.isBlank() ? "http://127.0.0.1:8080" : result;
    }
}
