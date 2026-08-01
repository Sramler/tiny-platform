package com.tiny.platform.core.oauth.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Supplier;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;

/**
 * Spring Security SPA CSRF contract: render BREACH-protected request attributes while accepting
 * the plain token that browser code reads from the {@code XSRF-TOKEN} cookie/JSON envelope and
 * sends in {@code X-XSRF-TOKEN}.
 */
final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

    private final CsrfTokenRequestHandler xorHandler = new XorCsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler plainHandler = new CsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       Supplier<CsrfToken> csrfToken) {
        xorHandler.handle(request, response, csrfToken);
        csrfToken.get();
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        String headerValue = request.getHeader(csrfToken.getHeaderName());
        // Cookie-aware SPA clients send the repository's plain token. Existing JSON-envelope
        // clients send the BREACH-protected value exposed by /csrf. Accept both representations
        // without weakening the repository-token comparison performed by CsrfFilter.
        if (headerValue != null && !headerValue.isBlank()
            && headerValue.equals(csrfToken.getToken())) {
            return plainHandler.resolveCsrfTokenValue(request, csrfToken);
        }
        return xorHandler.resolveCsrfTokenValue(request, csrfToken);
    }
}
