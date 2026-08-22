package org.kidscafe.kindy.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * The two random values the authorization code flow needs.
 *
 * <p>{@code state} ties a callback to the request that started it. Without it, an attacker can
 * complete their own authorization at the provider and then feed the resulting code to a victim's
 * browser, silently linking their social account to the victim's account.
 *
 * <p>{@code code_verifier} (PKCE) ties the code to whoever asked for it, so a code intercepted in
 * transit cannot be redeemed by anyone else. Kakao and Google support it; Naver does not, and
 * there {@code state} carries the whole burden.
 *
 * <p>Static rather than a {@code @Component} because it holds no configuration — unlike
 * {@link EncryptUtil}, which needs an injected key and pepper.
 */
public final class PkceUtil {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private PkceUtil() {}

    /** A CSRF state value. 32 bytes — far past guessing, and short enough for a query string. */
    public static String createState() {
        return randomUrlSafe(32);
    }

    /** A PKCE verifier. RFC 7636 wants 43–128 characters; 32 bytes encodes to 43. */
    public static String createCodeVerifier() {
        return randomUrlSafe(32);
    }

    /** The S256 challenge derived from a verifier. */
    public static String challengeOf(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return ENCODER.encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Constant-time comparison, used for state. {@code equals} stops at the first differing byte,
     * which tells anyone timing the response how much of their guess was right.
     */
    public static boolean matches(String expected, String supplied) {
        if (expected == null || supplied == null) return false;
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8));
    }

    private static String randomUrlSafe(int byteLength) {
        byte[] bytes = new byte[byteLength];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }
}
