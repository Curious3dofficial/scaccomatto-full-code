package com.scaccomatto.account;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.List;

final class GoogleOAuthService {
    record GoogleProfile(
            String googleUserId,
            String email,
            String displayName) {
    }

    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = List.of("openid", "email", "profile");

    private final Path credentialsPath;
    private final String redirectUri;
    private final HttpTransport transport;
    private GoogleClientSecrets clientSecrets;
    private GoogleIdTokenVerifier idTokenVerifier;

    GoogleOAuthService(Path credentialsPath, String redirectBaseUrl)
            throws IOException, GeneralSecurityException {
        this.credentialsPath = credentialsPath;
        this.redirectUri = stripTrailingSlash(redirectBaseUrl) + "/oauth/google/callback";
        this.transport = GoogleNetHttpTransport.newTrustedTransport();
        // Google sign-in is optional: without its credentials the rest of the server still
        // runs, and "Continue with Google" reports that it isn't set up.
        try {
            this.clientSecrets = loadClientSecrets(credentialsPath);
            this.idTokenVerifier = new GoogleIdTokenVerifier.Builder(transport, JSON_FACTORY)
                    .setAudience(List.of(details().getClientId()))
                    .build();
        } catch (IOException missing) {
            System.err.println("Google sign-in disabled: " + missing.getMessage());
            this.clientSecrets = null;
            this.idTokenVerifier = null;
        }
    }

    String authorizationUrl(String state) throws IOException {
        return new GoogleAuthorizationCodeRequestUrl(
                details().getClientId(),
                redirectUri,
                SCOPES)
                .setState(state)
                .setAccessType("online")
                .set("prompt", "select_account")
                .build();
    }

    GoogleProfile exchange(String code) throws IOException, ApiException {
        GoogleTokenResponse tokenResponse = new GoogleAuthorizationCodeTokenRequest(
                transport,
                JSON_FACTORY,
                details().getClientId(),
                details().getClientSecret(),
                code,
                redirectUri)
                .execute();

        GoogleIdToken idToken;
        try {
            idToken = idTokenVerifier().verify(tokenResponse.getIdToken());
        } catch (GeneralSecurityException exception) {
            throw new ApiException(401, "google_id_token_invalid", "Google sign-in could not be verified.");
        }
        if (idToken == null) {
            throw new ApiException(401, "google_id_token_invalid", "Google sign-in could not be verified.");
        }

        GoogleIdToken.Payload payload = idToken.getPayload();
        if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw new ApiException(401, "google_email_unverified", "Google did not verify this email address.");
        }

        String email = clean(payload.getEmail());
        if (email.isEmpty()) {
            throw new ApiException(400, "google_email_missing", "Google did not return an email address.");
        }
        String name = clean((String) payload.get("name"));
        if (name.isEmpty()) {
            name = email.substring(0, email.indexOf('@'));
        }
        return new GoogleProfile(payload.getSubject(), email, name);
    }

    Path credentialsPath() {
        return credentialsPath;
    }

    String redirectUri() {
        return redirectUri;
    }

    private synchronized GoogleClientSecrets.Details details() throws IOException {
        if (clientSecrets == null) {
            clientSecrets = loadClientSecrets(credentialsPath);
        }
        return clientSecrets.getDetails();
    }

    private synchronized GoogleIdTokenVerifier idTokenVerifier() throws IOException {
        if (idTokenVerifier == null) {
            idTokenVerifier = new GoogleIdTokenVerifier.Builder(transport, JSON_FACTORY)
                    .setAudience(List.of(details().getClientId()))
                    .build();
        }
        return idTokenVerifier;
    }

    private static GoogleClientSecrets loadClientSecrets(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            GoogleClientSecrets secrets = GoogleClientSecrets.load(JSON_FACTORY, reader);
            if (secrets.getDetails() == null
                    || clean(secrets.getDetails().getClientId()).isEmpty()
                    || clean(secrets.getDetails().getClientSecret()).isEmpty()) {
                throw new IOException("Google OAuth credentials are missing client_id or client_secret.");
            }
            return secrets;
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String stripTrailingSlash(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result.isEmpty() ? "http://127.0.0.1:8080" : result;
    }
}
