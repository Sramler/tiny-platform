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
        request.setParameter("message", "登录失败次数过多，请 15 分钟后重试");

        String view = controller.login(request);

        assertThat(view).startsWith("redirect:http://localhost:5173/login?");
        assertThat(view).contains("redirect=%2F");
        assertThat(view).contains("error=missing_tenant");
        assertThat(view).contains("message=%E7%99%BB%E5%BD%95%E5%A4%B1%E8%B4%A5%E6%AC%A1%E6%95%B0%E8%BF%87%E5%A4%9A");
        assertThat(view).doesNotContain("evil.com");
    }
}
