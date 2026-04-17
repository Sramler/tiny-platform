package com.tiny.platform.core.oauth.security;

import com.tiny.platform.core.oauth.config.FrontendProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationEndpointMfaEntryPointTest {

    @Test
    void shouldRedirectToBindWhenBindRequired() throws Exception {
        AuthorizationEndpointMfaEntryPoint entryPoint = newEntryPoint();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorize");
        request.setQueryString("client_id=a");
        request.setAttribute(AuthorizationEndpointMfaAuthorizationManager.BIND_REQUIRED_ATTRIBUTE, true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new InsufficientAuthenticationException("Missing Authorities FACTOR_TOTP"));

        assertThat(response.getRedirectedUrl())
                .contains("http://localhost:5173/totp-bind")
                .contains("redirect=%2Foauth2%2Fauthorize%3Fclient_id%3Da");
    }

    @Test
    void shouldRedirectToVerifyWhenBindNotRequired() throws Exception {
        AuthorizationEndpointMfaEntryPoint entryPoint = newEntryPoint();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/tenant1/oauth2/authorize");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new InsufficientAuthenticationException("Missing Authorities FACTOR_TOTP"));

        assertThat(response.getRedirectedUrl())
                .contains("http://localhost:5173/totp-verify")
                .contains("redirect=%2Ftenant1%2Foauth2%2Fauthorize");
    }

    private AuthorizationEndpointMfaEntryPoint newEntryPoint() {
        FrontendProperties frontendProperties = new FrontendProperties();
        frontendProperties.setTotpBindUrl("redirect:http://localhost:5173/totp-bind");
        frontendProperties.setTotpVerifyUrl("redirect:http://localhost:5173/totp-verify");
        return new AuthorizationEndpointMfaEntryPoint(frontendProperties);
    }
}
