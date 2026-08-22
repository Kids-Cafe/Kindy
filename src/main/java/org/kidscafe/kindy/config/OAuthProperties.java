package org.kidscafe.kindy.config;

import org.kidscafe.kindy.dto.OAuthDTO.Provider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * Credentials for the social login providers, and the endpoints they are used against.
 *
 * <p>Only the client id and secret are configurable. The URLs belong to the provider, not to a
 * deployment, so they live in {@link Endpoints} as constants: a typo in a property file that
 * redirected token exchange — the request that carries our client secret — to another host would
 * be a bad way to find that out.
 *
 * <p>A provider whose client id is blank is simply not configured, and asking to log in with it
 * fails immediately with NOT_AVAILABLE. That is deliberate. The frontend used to treat a missing
 * client id as a cue to fabricate a session, so an unconfigured build looked like a working one;
 * refusing loudly is the behaviour that cannot be mistaken for success.
 */
@Component
@ConfigurationProperties(prefix = "kindy.oauth")
public class OAuthProperties {
    /** Where the provider sends the browser back. {@code {provider}} is replaced per provider. */
    private String redirectUri;

    /** Where we send the browser once the callback has done its work — a route of the SPA. */
    private String returnUri;

    private final Map<Provider, Registration> registrations = new EnumMap<>(Provider.class);

    public static class Registration {
        private String clientId = "";
        private String clientSecret = "";

        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
        public String getClientSecret() { return clientSecret; }
        public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
    }

    // Bound from kindy.oauth.kakao.*, kindy.oauth.naver.*, kindy.oauth.google.*
    public Registration getKakao() { return registrations.computeIfAbsent(Provider.KAKAO, p -> new Registration()); }
    public Registration getNaver() { return registrations.computeIfAbsent(Provider.NAVER, p -> new Registration()); }
    public Registration getGoogle() { return registrations.computeIfAbsent(Provider.GOOGLE, p -> new Registration()); }

    public String getRedirectUri() { return redirectUri; }
    public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }
    public String getReturnUri() { return returnUri; }
    public void setReturnUri(String returnUri) { this.returnUri = returnUri; }

    public Registration registration(Provider provider) {
        return switch (provider) {
            case KAKAO -> getKakao();
            case NAVER -> getNaver();
            case GOOGLE -> getGoogle();
            case APPLE -> null;   // see OAuthDTO.Provider — present in the column, not implemented
        };
    }

    /** True when this provider has credentials and can actually be used. */
    public boolean isConfigured(Provider provider) {
        Registration registration = registration(provider);
        return registration != null
                && !registration.getClientId().isBlank()
                && !registration.getClientSecret().isBlank();
    }

    /** The redirect URI for one provider, with the placeholder filled in. */
    public String redirectUri(Provider provider) {
        return redirectUri.replace("{provider}", provider.name().toLowerCase());
    }

    /**
     * Per-provider endpoints and quirks.
     *
     * <p>The three differ in ways that are easy to get wrong and awkward to discover:
     * <ul>
     *   <li><b>PKCE</b> — Kakao and Google support it; Naver does not, so there the {@code state}
     *       parameter is the only thing binding the callback to the request that started it. That
     *       is a limitation of the provider rather than a shortcut taken here.</li>
     *   <li><b>Where the subject id lives</b> — {@code sub} for Google, a top-level numeric
     *       {@code id} for Kakao, and {@code response.id} nested a level down for Naver.</li>
     *   <li><b>Scope</b> — Google needs one requested explicitly; Kakao and Naver derive consent
     *       from their developer console, and sending a scope they do not recognise is an error.</li>
     * </ul>
     *
     * <p>The subject is read from the userinfo endpoint for all three, including Google, rather
     * than from the {@code id_token} Google also returns. Verifying that token's signature would
     * mean a JOSE dependency and a cached JWKS fetch to gain nothing: the token reached us over a
     * TLS back-channel call we made to Google's own token endpoint, which OIDC Core §3.1.3.7
     * accepts as sufficient. One code path for three providers.
     */
    public record Endpoints(
            String authorizeUrl,
            String tokenUrl,
            String userInfoUrl,
            boolean supportsPkce,
            String scope,
            String[] subjectPath
    ) {
        public static Endpoints of(Provider provider) {
            return switch (provider) {
                case KAKAO -> new Endpoints(
                        "https://kauth.kakao.com/oauth/authorize",
                        "https://kauth.kakao.com/oauth/token",
                        "https://kapi.kakao.com/v2/user/me",
                        true, null, new String[]{"id"});
                case NAVER -> new Endpoints(
                        "https://nid.naver.com/oauth2.0/authorize",
                        "https://nid.naver.com/oauth2.0/token",
                        "https://openapi.naver.com/v1/nid/me",
                        false, null, new String[]{"response", "id"});
                case GOOGLE -> new Endpoints(
                        "https://accounts.google.com/o/oauth2/v2/auth",
                        "https://oauth2.googleapis.com/token",
                        "https://openidconnect.googleapis.com/v1/userinfo",
                        true, "openid%20email%20profile", new String[]{"sub"});
                case APPLE -> throw new IllegalArgumentException("APPLE is not implemented");
            };
        }

        /** Where the account's email sits in the same userinfo response, for display only. */
        public static String[] emailPath(Provider provider) {
            return switch (provider) {
                case KAKAO -> new String[]{"kakao_account", "email"};
                case NAVER -> new String[]{"response", "email"};
                case GOOGLE -> new String[]{"email"};
                case APPLE -> throw new IllegalArgumentException("APPLE is not implemented");
            };
        }
    }
}
