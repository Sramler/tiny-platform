package com.tiny.platform.core.oauth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;

class SpaCsrfTokenRequestHandlerTest {

    @Test
    void shouldAcceptPlainSpaHeaderValue() {
        CsrfToken token = mock(CsrfToken.class);
        when(token.getHeaderName()).thenReturn("X-XSRF-TOKEN");
        when(token.getToken()).thenReturn("plain-cookie-token");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/sys/tenants/precheck");
        request.addHeader("X-XSRF-TOKEN", "plain-cookie-token");

        String resolved = new SpaCsrfTokenRequestHandler().resolveCsrfTokenValue(request, token);

        assertThat(resolved).isEqualTo("plain-cookie-token");
    }
}
