package com.tiny.platform.core.oauth.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class RedirectPathSanitizerTest {

    @Test
    void shouldAcceptInternalRelativePathsAndRejectExternalTargets() {
        MockHttpServletRequest request = request();

        assertThat(RedirectPathSanitizer.sanitize("/dashboard?tab=profile", request))
                .isEqualTo("/dashboard?tab=profile");
        assertThat(RedirectPathSanitizer.sanitize("//evil.com/path", request))
                .isEqualTo("/");
        assertThat(RedirectPathSanitizer.sanitize("https://evil.com/callback", request))
                .isEqualTo("/");
        assertThat(RedirectPathSanitizer.sanitize("javascript:alert(1)", request))
                .isEqualTo("/");
    }

    @Test
    void shouldConvertSameOriginAbsoluteUrlToRelativePath() {
        MockHttpServletRequest request = request();

        assertThat(RedirectPathSanitizer.sanitize(
                "http://localhost/oauth2/authorize?client_id=vue-client&redirect_uri=https://client.example.com/callback",
                request
        )).isEqualTo("/oauth2/authorize?client_id=vue-client&redirect_uri=https://client.example.com/callback");
    }

    @Test
    void shouldSanitizeRedirectParameterWhenRebuildingQueryString() {
        MockHttpServletRequest request = request();
        request.setParameter("redirect", "https://evil.com/callback");
        request.setParameter("error", "missing_tenant");

        String query = RedirectPathSanitizer.buildSanitizedQueryString(request, java.util.Set.of("redirect"));

        assertThat(query).contains("redirect=%2F");
        assertThat(query).contains("error=missing_tenant");
        assertThat(query).doesNotContain("evil.com");
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(80);
        return request;
    }
}
