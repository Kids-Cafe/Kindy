package org.kidscafe.kindy.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kidscafe.kindy.config.OAuthProperties;
import org.kidscafe.kindy.config.OAuthProperties.Endpoints;
import org.kidscafe.kindy.dto.OAuthDTO;
import org.kidscafe.kindy.dto.OAuthDTO.Provider;
import org.kidscafe.kindy.dto.ResultDTO;
import org.kidscafe.kindy.dto.UserDTO;
import org.kidscafe.kindy.service.IOAuthService;
import org.kidscafe.kindy.service.IUserService;
import org.kidscafe.kindy.util.PkceUtil;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Social login with Kakao, Naver and Google.
 *
 * <h2>Linking, not signing up</h2>
 * A Kindy account is always created with an email address and a password. A provider is a second
 * way to log in to an account that already exists — {@code mode=link} attaches one, and
 * {@code mode=login} uses one. Arriving with an unlinked provider account is refused
 * (NO_LINKED_ACCOUNT) rather than quietly provisioning anything, and no path here writes to
 * T_USER. This also means unlinking can never lock anyone out: T_USER.PASSWORD is NOT NULL, so
 * password login always remains.
 *
 * <h2>Two of these endpoints are not JSON</h2>
 * {@code authorize} and {@code callback} are browser navigations and answer with a 302, not the
 * {@code ResultDTO} envelope every other endpoint uses. Nothing else would work: the browser
 * follows them as page loads, and the provider decides what to call back. This is the same kind of
 * exception as ChatController's audio responses. {@code links} and {@code unlink} are ordinary XHR
 * and keep the envelope. Because the first two are navigations rather than fetches, CORS does not
 * apply to them and {@code CorsConfig} needs nothing added.
 *
 * <h2>What travels in the URL</h2>
 * The redirect back to the SPA carries an outcome code and nothing else — no tokens, no user id,
 * no provider subject. Authentication rides the session cookie exactly as it does after a password
 * login, so a URL that ends up in a browser history or a server log grants nobody anything.
 */
@Slf4j
@RequestMapping(value = "/api/user/oauth")
@RequiredArgsConstructor
@RestController
public class OAuthController {
    private final IOAuthService oauthService;
    private final IUserService userService;
    private final OAuthProperties properties;

    /** How long an authorization round trip may take before its state is considered stale. */
    private static final long OAUTH_TTL_MS = 10 * 60 * 1000L;

    private static final String ATTR_STATE = "SESSION_OAUTH_STATE";
    private static final String ATTR_PROVIDER = "SESSION_OAUTH_PROVIDER";
    private static final String ATTR_MODE = "SESSION_OAUTH_MODE";
    private static final String ATTR_VERIFIER = "SESSION_OAUTH_VERIFIER";
    private static final String ATTR_RETURN_TO = "SESSION_OAUTH_RETURN_TO";
    private static final String ATTR_CREATED_AT = "SESSION_OAUTH_CREATED_AT";

    private static final String MODE_LOGIN = "login";
    private static final String MODE_LINK = "link";

    /**
     * Starts the flow: mints state (and a PKCE verifier where the provider supports one), remembers
     * both in the session, and sends the browser to the provider.
     */
    @GetMapping(value = "{provider}/authorize")
    public ResponseEntity<Void> authorize(@PathVariable String provider,
                                          HttpServletRequest request,
                                          HttpSession session) {
        log.debug("Calling oauth authorize: {}", provider);

        Provider parsed = parseProvider(provider);
        if (parsed == null || !oauthService.isConfigured(parsed)) return redirectToApp("NOT_AVAILABLE", provider, "/");

        String mode = MODE_LINK.equals(request.getParameter("mode")) ? MODE_LINK : MODE_LOGIN;
        String returnTo = safeReturnTo(request.getParameter("returnTo"));

        // Linking is an action taken by someone already signed in. Checking here means the user is
        // told before being sent off to a provider; the callback checks again, because the session
        // can lapse during the round trip.
        if (MODE_LINK.equals(mode) && session.getAttribute("SESSION_USER_ID") == null) {
            return redirectToApp("INVALID_ACCESS", provider, returnTo);
        }

        String state = PkceUtil.createState();
        String verifier = Endpoints.of(parsed).supportsPkce() ? PkceUtil.createCodeVerifier() : null;

        session.setAttribute(ATTR_STATE, state);
        session.setAttribute(ATTR_PROVIDER, parsed.name());
        session.setAttribute(ATTR_MODE, mode);
        session.setAttribute(ATTR_VERIFIER, verifier);
        session.setAttribute(ATTR_RETURN_TO, returnTo);
        session.setAttribute(ATTR_CREATED_AT, System.currentTimeMillis());

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(oauthService.buildAuthorizeUrl(parsed, state, verifier)))
                .build();
    }

    /**
     * The provider sends the browser here. Validates the request against what {@code authorize}
     * stored, exchanges the code, and either signs the user in or records a link.
     *
     * <p>This request arrives cross-site, straight from the authorization server, which is why the
     * session cookie must be SameSite=Lax rather than Strict — see application.properties. It is
     * also why {@code CsrfFilter} exempts this path: the one-time {@code state} below is the
     * protection appropriate to this hop.
     */
    @GetMapping(value = "{provider}/callback")
    public ResponseEntity<Void> callback(@PathVariable String provider,
                                         HttpServletRequest request,
                                         HttpSession session) {
        log.debug("Calling oauth callback: {}", provider);

        // Read everything, then wipe — before any branch can return early. State is single use, so
        // a replayed callback finds nothing and fails closed, and an abandoned attempt leaves
        // nothing behind for a later one to trip over.
        String expectedState = (String) session.getAttribute(ATTR_STATE);
        String expectedProvider = (String) session.getAttribute(ATTR_PROVIDER);
        String mode = (String) session.getAttribute(ATTR_MODE);
        String verifier = (String) session.getAttribute(ATTR_VERIFIER);
        String returnTo = safeReturnTo((String) session.getAttribute(ATTR_RETURN_TO));
        Long startedAt = (Long) session.getAttribute(ATTR_CREATED_AT);
        clearOAuthAttributes(session);

        Provider parsed = parseProvider(provider);
        if (parsed == null) return redirectToApp("OAUTH_FAILED", provider, returnTo);

        String error = request.getParameter("error");
        if (error != null) {
            // The provider's own words about what went wrong. Safe to log; the code is not.
            log.debug("Provider {} refused: error={} description={}",
                    provider, error, request.getParameter("error_description"));
            return redirectToApp("OAUTH_FAILED", provider, returnTo);
        }

        if (expectedState == null || startedAt == null || System.currentTimeMillis() - startedAt > OAUTH_TTL_MS) {
            return redirectToApp("INVALID_STATE", provider, returnTo);
        }
        if (!PkceUtil.matches(expectedState, request.getParameter("state"))) {
            return redirectToApp("INVALID_STATE", provider, returnTo);
        }
        // A state minted for one provider must not be redeemable at another.
        if (!parsed.name().equals(expectedProvider)) {
            return redirectToApp("INVALID_STATE", provider, returnTo);
        }

        String code = request.getParameter("code");
        if (code == null) return redirectToApp("OAUTH_FAILED", provider, returnTo);

        try {
            OAuthDTO identity = oauthService.exchangeCodeForIdentity(parsed, code, verifier);

            return MODE_LINK.equals(mode)
                    ? link(identity, session, provider, returnTo)
                    : login(identity, request, session, provider, returnTo);
        } catch (Exception e) {
            log.warn("OAuth callback failed for {}", provider, e);
            return redirectToApp("OAUTH_FAILED", provider, returnTo);
        }
    }

    /**
     * Signs in as the account this provider identity is linked to.
     *
     * <p>Refuses when there is no link. That is the whole of the "sign up with email, add social
     * later" decision expressed in code: no row here means no account, and we do not make one.
     */
    private ResponseEntity<Void> login(OAuthDTO identity, HttpServletRequest request,
                                       HttpSession session, String provider, String returnTo) throws Exception {
        OAuthDTO link = oauthService.getLink(identity.getProvider(), identity.getId());
        if (link == null) return redirectToApp("NO_LINKED_ACCOUNT", provider, returnTo);

        UserDTO user = userService.getInfo(link.getUserId());
        if (user == null) return redirectToApp("OAUTH_FAILED", provider, returnTo);

        // Everything needed has been read out of the old session already — including the state
        // validated above — so it can be discarded now. Recreating it is the session fixation
        // defence UserController.login performs for the same reason.
        session.invalidate();
        HttpSession fresh = request.getSession(true);
        fresh.setMaxInactiveInterval(3600);
        fresh.setAttribute("SESSION_USER_ID", user.getId());
        fresh.setAttribute("SESSION_USER_NAME", user.getName());

        return redirectToApp("SIGNIN_COMPLETE", provider, returnTo);
    }

    /**
     * Attaches this provider identity to the signed-in account.
     *
     * <p>The session is left alone — the user is already logged in and part-way through a task, and
     * re-issuing it here would be a fixation defence against nothing.
     */
    private ResponseEntity<Void> link(OAuthDTO identity, HttpSession session,
                                      String provider, String returnTo) throws Exception {
        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return redirectToApp("INVALID_ACCESS", provider, returnTo);

        identity.setUserId(userId);

        try {
            oauthService.insertLink(identity);
            return redirectToApp("LINK_COMPLETE", provider, returnTo);
        } catch (DuplicateKeyException e) {
            // Two constraints can land here: the primary key (this social account is already
            // attached somewhere) and the unique key (this account already has this provider).
            OAuthDTO existing = oauthService.getLink(identity.getProvider(), identity.getId());

            // Already linked to the person doing the linking — a double submit, not a failure.
            if (existing != null && userId.equals(existing.getUserId())) {
                return redirectToApp("LINK_COMPLETE", provider, returnTo);
            }

            // Otherwise it belongs to someone else, or this account already has this provider. Both
            // answer the same way, and neither says whose it is: confirming that a given social
            // account has a Kindy account would be a disclosure in itself.
            return redirectToApp("ALREADY_LINKED", provider, returnTo);
        }
    }

    /** The providers linked to the signed-in account. */
    @GetMapping(value = "links")
    public ResultDTO<List<OAuthDTO>> links(HttpSession session) {
        log.debug("Calling oauth links");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        try {
            return ResultDTO.success("QUERY_COMPLETE", oauthService.getLinksByUser(userId));
        } catch (Exception e) {
            log.warn("links failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    /**
     * Detaches a provider from the signed-in account.
     *
     * <p>No "last login method" guard is needed, and its absence is deliberate rather than
     * overlooked: T_USER.PASSWORD is NOT NULL, so every account can always log in with a password
     * and removing a social link cannot strand anyone.
     */
    @PostMapping(value = "unlink")
    public ResultDTO<Void> unlink(HttpServletRequest request, HttpSession session) {
        log.debug("Calling oauth unlink");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        Provider parsed = parseProvider(request.getParameter("provider"));
        if (parsed == null) return ResultDTO.error("INVALID_PARAMETER");

        try {
            oauthService.deleteLink(parsed, userId);
            return ResultDTO.success("DELETE_COMPLETE");
        } catch (Exception e) {
            log.warn("unlink failed", e);
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    /** Accepts the lowercase names the frontend uses, and refuses anything else — including APPLE. */
    private Provider parseProvider(String provider) {
        if (provider == null) return null;
        try {
            Provider parsed = Provider.valueOf(provider.toUpperCase());
            return parsed == Provider.APPLE ? null : parsed;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Constrains where the browser may be sent after the flow.
     *
     * <p>An unchecked value here is an open redirect: this endpoint would happily bounce anyone to
     * an attacker's page, wrapped in the credibility of our own domain, and a login page there is
     * a convincing thing to arrive at. Only a path within the app is allowed. Rejected in
     * particular are {@code //host} and {@code /\host}, which browsers read as protocol-relative
     * URLs to another site despite starting with a slash, and any value with a scheme in it.
     */
    private String safeReturnTo(String returnTo) {
        if (returnTo == null || returnTo.isBlank()) return "/";
        if (!returnTo.startsWith("/")) return "/";
        if (returnTo.startsWith("//") || returnTo.startsWith("/\\")) return "/";
        if (returnTo.contains(":")) return "/";
        return returnTo;
    }

    private void clearOAuthAttributes(HttpSession session) {
        session.removeAttribute(ATTR_STATE);
        session.removeAttribute(ATTR_PROVIDER);
        session.removeAttribute(ATTR_MODE);
        session.removeAttribute(ATTR_VERIFIER);
        session.removeAttribute(ATTR_RETURN_TO);
        session.removeAttribute(ATTR_CREATED_AT);
    }

    /** Sends the browser back to the SPA with the outcome, and nothing more. */
    private ResponseEntity<Void> redirectToApp(String result, String provider, String returnTo) {
        String location = UriComponentsBuilder.fromUriString(properties.getReturnUri())
                .queryParam("result", result)
                .queryParam("provider", provider == null ? "" : provider.toLowerCase())
                .queryParam("returnTo", returnTo)
                .build()
                .toUriString();

        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(location)).build();
    }
}
