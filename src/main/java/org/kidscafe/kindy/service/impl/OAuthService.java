package org.kidscafe.kindy.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kidscafe.kindy.config.OAuthProperties;
import org.kidscafe.kindy.config.OAuthProperties.Endpoints;
import org.kidscafe.kindy.dto.OAuthDTO;
import org.kidscafe.kindy.dto.OAuthDTO.Provider;
import org.kidscafe.kindy.mapper.IOAuthMapper;
import org.kidscafe.kindy.service.IOAuthService;
import org.kidscafe.kindy.util.PkceUtil;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Service
public class OAuthService implements IOAuthService {
    private final IOAuthMapper oauthMapper;
    private final OAuthProperties properties;
    private final RestClient restClient;

    @Override
    public boolean isConfigured(Provider provider) {
        return properties.isConfigured(provider);
    }

    @Override
    public String buildAuthorizeUrl(Provider provider, String state, String codeVerifier) {
        Endpoints endpoints = Endpoints.of(provider);

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(endpoints.authorizeUrl())
                .queryParam("response_type", "code")
                .queryParam("client_id", properties.registration(provider).getClientId())
                .queryParam("redirect_uri", properties.redirectUri(provider))
                .queryParam("state", state);

        if (endpoints.scope() != null) builder.queryParam("scope", endpoints.scope());

        if (endpoints.supportsPkce() && codeVerifier != null) {
            builder.queryParam("code_challenge", PkceUtil.challengeOf(codeVerifier));
            builder.queryParam("code_challenge_method", "S256");
        }

        return builder.build().toUriString();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Two calls: the code becomes an access token, the access token becomes an identity. The
     * token is used here and dropped — we never act on the user's behalf at the provider, so
     * keeping it would be a stored credential with no purpose.
     */
    @Override
    public OAuthDTO exchangeCodeForIdentity(Provider provider, String code, String codeVerifier) throws Exception {
        Endpoints endpoints = Endpoints.of(provider);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", properties.registration(provider).getClientId());
        form.add("client_secret", properties.registration(provider).getClientSecret());
        // Byte-identical to the value sent on the authorization request. Providers compare the two
        // and reject the exchange if they differ, which is a defence against a stolen code being
        // redeemed against a different registration.
        form.add("redirect_uri", properties.redirectUri(provider));
        form.add("code", code);
        if (endpoints.supportsPkce() && codeVerifier != null) form.add("code_verifier", codeVerifier);

        Map<?, ?> tokenResponse = restClient.post()
                .uri(endpoints.tokenUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(form)
                .retrieve()
                .body(Map.class);

        if (tokenResponse == null) throw new IllegalStateException("empty token response");

        Object accessToken = tokenResponse.get("access_token");
        if (accessToken == null) {
            // The body would carry the provider's error, but it can also echo the request — log the
            // key set only, never the values.
            log.warn("Token exchange for {} returned no access_token (keys={})", provider, tokenResponse.keySet());
            throw new IllegalStateException("no access_token");
        }

        Map<?, ?> userInfo = restClient.get()
                .uri(endpoints.userInfoUrl())
                .header("Authorization", "Bearer " + accessToken)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Map.class);

        if (userInfo == null) throw new IllegalStateException("empty userinfo response");

        String subject = readPath(userInfo, endpoints.subjectPath());
        if (subject == null || subject.isBlank()) throw new IllegalStateException("no subject in userinfo");

        OAuthDTO result = OAuthDTO.of(provider, subject);
        result.setProviderEmail(readPath(userInfo, Endpoints.emailPath(provider)));
        return result;
    }

    /**
     * Walks a dotted path through a decoded JSON object.
     *
     * <p>Needed because the three providers nest the identifier differently — Google answers with
     * {@code sub} at the top level, Kakao with {@code id}, Naver with {@code response.id}. The
     * value is stringified rather than cast: Kakao's id arrives as a JSON number and would fail a
     * direct cast to String, and T_OAUTH.ID is a varchar in any case.
     */
    private String readPath(Map<?, ?> source, String[] path) {
        Object current = source;
        for (String key : path) {
            if (!(current instanceof Map<?, ?> map)) return null;
            current = map.get(key);
            if (current == null) return null;
        }
        return String.valueOf(current);
    }

    @Override
    public OAuthDTO getLink(Provider provider, String subjectId) throws Exception {
        return oauthMapper.getLink(OAuthDTO.of(provider, subjectId));
    }

    @Override
    public List<OAuthDTO> getLinksByUser(String userId) throws Exception {
        OAuthDTO pDTO = new OAuthDTO();
        pDTO.setUserId(userId);
        return oauthMapper.getLinksByUser(pDTO);
    }

    @Override
    public int insertLink(OAuthDTO pDTO) throws Exception {
        return oauthMapper.insertLink(pDTO);
    }

    @Override
    public int deleteLink(Provider provider, String userId) throws Exception {
        return oauthMapper.deleteLink(OAuthDTO.ofUser(provider, userId));
    }
}
