package org.kidscafe.kindy.config;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.extern.slf4j.Slf4j;
import org.kidscafe.kindy.service.ServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * An access token for the Google Cloud APIs this application bills — speech-to-text and
 * text-to-speech — or a refusal if this deployment has no Google account.
 *
 * <p>This exists because those two endpoints are the one place the language model's bargain does not
 * hold. The model is reached with a static string in an Authorization header, so a deployment
 * changes provider by changing three environment variables and no code. Speech is not: neither
 * speech.googleapis.com nor texttospeech.googleapis.com accepts an API key at all. Both want an
 * OAuth2 bearer scoped to cloud-platform, which is a credential that expires and has to be
 * re-minted — a thing rather than a value, and so a bean rather than a {@code @Value} field.
 *
 * <p><b>Nothing is read until the first call.</b> Not in a constructor and not in a
 * {@code @PostConstruct}: a deployment with no Google account still starts and still serves signing
 * in, classes, photos and notices, the same bargain {@code kindy.oauth.*.client-id} and
 * {@code kindy.storage.s3.bucket} already make. The absence surfaces where it can be understood — at
 * the point of use, as a {@link ServiceUnavailableException}, which the controllers already answer
 * with NOT_AVAILABLE and 503.
 *
 * <p>Caching and refresh are deliberately <b>not</b> written here. {@link GoogleCredentials} already
 * holds the current token, already refreshes it inside its own expiry margin, and already serialises
 * concurrent refreshes; a second cache in front of it would be a second answer to when a token
 * expires, and the wrong one shows up as an intermittent 401 an hour into a deployment. What is
 * written here is the one thing the library cannot do for us: building the credentials at most once,
 * without doing it at startup.
 *
 * <p>Note the token request does not go through the shared {@code RestClient} and so does not inherit
 * its 15s/60s timeouts — google-http-client uses its own. A metadata server that hangs blocks a
 * request thread for that long rather than for ours.
 */
@Slf4j
@Component
public class GoogleTokenProvider {
    /**
     * The service-account key file, or blank for Application Default Credentials.
     *
     * <p>Blank is the ordinary case and is not "unconfigured": on Cloud Run, GCE and GKE the metadata
     * server issues the token and there is no key file to leak in the first place, and on a
     * developer machine {@code gcloud auth application-default login} leaves one where the default
     * chain finds it. Set this only where a key file is the only option, and to a path outside the
     * checkout.
     */
    @Value("${kindy.google.credentials:}")
    private String CREDENTIALS_PATH;

    /**
     * Both APIs authorize on cloud-platform and nothing narrower — there is no speech-only scope to
     * ask for. That is why what holds this should be a service account with the two API-specific
     * roles, and not an owner: the scope cannot narrow the token, so the account has to.
     */
    private static final List<String> SCOPES = List.of("https://www.googleapis.com/auth/cloud-platform");

    /** Built at most once, on first use. Volatile so a second caller sees a finished object. */
    private volatile GoogleCredentials credentials;
    private final Object lock = new Object();

    /**
     * A currently-valid access token.
     *
     * @throws ServiceUnavailableException when this deployment has no usable Google credentials —
     *         the same answer, and the same fix by the same person, as an unset STT_URL.
     */
    public String token() {
        GoogleCredentials creds = this.credentials();

        try {
            // A no-op while the token is still good; mints one on first use and again inside the
            // library's own expiry margin. Thread-safe by contract.
            creds.refreshIfExpired();
        } catch (IOException e) {
            // Deliberately NOT_AVAILABLE rather than "upstream failed", even though a network blip
            // at oauth2.googleapis.com lands here too. The overwhelmingly common cause is a
            // credential that will never work — a revoked key, a deleted service account, a clock an
            // hour out — and treating that as retryable means DiaryService.generateAll spends a week
            // of days on an identical refusal and then reports an empty success.
            log.info("Google would not mint an access token: {}", e.toString());
            throw new ServiceUnavailableException(
                    "kindy.google.credentials could not be exchanged for a token (" + e.getMessage() + ")");
        }

        AccessToken token = creds.getAccessToken();
        if (token == null || token.getTokenValue() == null || token.getTokenValue().isBlank()) {
            throw new ServiceUnavailableException("kindy.google.credentials produced no access token");
        }

        return token.getTokenValue();
    }

    private GoogleCredentials credentials() {
        GoogleCredentials existing = this.credentials;
        if (existing != null) return existing;

        synchronized (lock) {
            if (this.credentials == null) this.credentials = load();
            return this.credentials;
        }
    }

    /**
     * Note what is not cached: a failure. Leaving {@link #credentials} null means the next
     * transcription tries again, so dropping a key file into place fixes a running deployment rather
     * than needing a restart to be believed.
     */
    private GoogleCredentials load() {
        try {
            GoogleCredentials loaded;

            if (CREDENTIALS_PATH != null && !CREDENTIALS_PATH.isBlank()) {
                Path path = Path.of(CREDENTIALS_PATH.trim());
                log.info("Google credentials from {}", path);
                try (InputStream in = Files.newInputStream(path)) {
                    loaded = GoogleCredentials.fromStream(in);
                }
            } else {
                // GOOGLE_APPLICATION_CREDENTIALS, then gcloud's own
                // application_default_credentials.json, then — on Cloud Run, GCE and GKE — the
                // metadata server.
                log.info("Google credentials from the application default chain");
                loaded = GoogleCredentials.getApplicationDefault();
            }

            // Guarded, not unconditional. A service-account key must be scoped or its token is good
            // for nothing; a user credential from `gcloud auth application-default login` carries
            // its scopes already and throws UnsupportedOperationException if asked to take ours.
            // createScopedRequired() is the only thing that tells the two apart, and getting it
            // wrong breaks exactly one of them — whichever one nobody tested with.
            return loaded.createScopedRequired() ? loaded.createScoped(SCOPES) : loaded;
        } catch (IOException e) {
            log.info("No Google credentials available: {}", e.toString());
            throw new ServiceUnavailableException(
                    "kindy.google.credentials is not configured (" + e.getMessage() + ")");
        }
    }
}
