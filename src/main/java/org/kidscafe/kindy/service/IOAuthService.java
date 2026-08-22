package org.kidscafe.kindy.service;

import org.kidscafe.kindy.dto.OAuthDTO;
import org.kidscafe.kindy.dto.OAuthDTO.Provider;

import java.util.List;

/**
 * Social login against Kakao, Naver and Google.
 *
 * <p>Every method here is a step of the authorization code flow or a query about the links it
 * produces. Nothing in this interface creates a Kindy account: a provider identity can only be
 * attached to an account that already signed up with an email address.
 */
public interface IOAuthService {
    /** True when the provider has credentials configured and can be used at all. */
    boolean isConfigured(Provider provider);

    /** The authorization URL to send the browser to, including state and (where supported) PKCE. */
    String buildAuthorizeUrl(Provider provider, String state, String codeVerifier);

    /**
     * Exchanges an authorization code for the provider's identifier for this user.
     *
     * @param codeVerifier the PKCE verifier for providers that support it, null for those that do not
     * @return the identity, carrying only the subject id and (for display) an email
     */
    OAuthDTO exchangeCodeForIdentity(Provider provider, String code, String codeVerifier) throws Exception;

    OAuthDTO getLink(Provider provider, String subjectId) throws Exception;

    List<OAuthDTO> getLinksByUser(String userId) throws Exception;

    int insertLink(OAuthDTO pDTO) throws Exception;

    int deleteLink(Provider provider, String userId) throws Exception;
}
