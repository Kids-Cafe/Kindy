package org.kidscafe.kindy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * One social identity linked to one Kindy account — a row of T_OAUTH.
 *
 * <p>Signing up still happens with an email address and a password; a provider is a second way to
 * log in to an account that already exists, never a way to bring one into being. That is why this
 * carries no name, no profile picture and no tokens: everything shown about a user comes from
 * T_USER, and the provider's answer is consulted once, to learn which account is being claimed.
 */
@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OAuthDTO {
    /**
     * Mirrors the T_OAUTH.PROVIDER column exactly. APPLE is present in the column and deliberately
     * unimplemented — Sign in with Apple needs a client secret that is an ES256 JWT signed from a
     * .p8 key and rotated at least every six months, and it answers the callback with a POST
     * instead of a GET. Adding the value here without a registration below would let it be
     * requested and fail late; {@code OAuthProperties} rejects it up front instead.
     */
    public enum Provider {
        GOOGLE,
        KAKAO,
        NAVER,
        APPLE
    }

    /**
     * The provider's own identifier for the user — {@code sub} for Google, {@code id} for Kakao and
     * Naver. Stable for the life of the account, which is what makes it usable as a key; an email
     * address is not, because people change theirs.
     */
    private String id;

    private Provider provider;

    /** T_USER.ID of the Kindy account this identity logs in to. */
    private String userId;

    /**
     * The address the provider reported, kept only so the settings screen can show which of two
     * Kakao accounts is connected. Never used to find or match an account — that would make an
     * attacker's control over their own provider email into control over someone's Kindy account.
     */
    private String providerEmail;

    private Long createdAt;

    public static OAuthDTO of(Provider provider, String id) {
        OAuthDTO result = new OAuthDTO();
        result.setProvider(provider);
        result.setId(id);
        return result;
    }

    public static OAuthDTO ofUser(Provider provider, String userId) {
        OAuthDTO result = new OAuthDTO();
        result.setProvider(provider);
        result.setUserId(userId);
        return result;
    }
}
