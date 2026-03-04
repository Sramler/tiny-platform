package com.tiny.platform.core.oauth.controller;

import com.tiny.platform.core.oauth.config.FrontendProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class LoginControllerTest {

    @Test
    void shouldSanitizeExternalRedirectWhenForwardingToFrontendLoginPage() {
        FrontendProperties properties = new FrontendProperties();
        properties.setLoginUrl("redirect:http://localhost:5173/login");
        LoginController controller = new LoginController(properties);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login");
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(80);
        request.setParameter("redirect", "https://evil.com/callback");
        request.setParameter("error", "missing_tenant");

        String view = controller.login(request);

        assertThat(view).startsWith("redirect:http://localhost:5173/login?");
        assertThat(view).contains("redirect=%2F");
        assertThat(view).contains("error=missing_tenant");
        assertThat(view).doesNotContain("evil.com");
    }
}
