package com.tiny.platform.core.oauth.session;

import com.tiny.platform.core.oauth.model.SecurityUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserSessionActivityFilterTest {

    private UserSessionService userSessionService;
    private UserSessionActivityFilter filter;

    @BeforeEach
    void setUp() {
        userSessionService = mock(UserSessionService.class);
        filter = new UserSessionActivityFilter(userSessionService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAllowActiveSession() throws Exception {
        when(userSessionService.registerOrTouch(any())).thenReturn(UserSessionState.ACTIVE);

        MockHttpServletRequest request = authenticatedRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(userSessionService).registerOrTouch(any());
    }

    @Test
    void shouldRejectRevokedSession() throws Exception {
        when(userSessionService.registerOrTouch(any())).thenReturn(UserSessionState.REVOKED);

        MockHttpServletRequest request = authenticatedRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("session_inactive");
    }

    private MockHttpServletRequest authenticatedRequest() {
        SecurityUser principal = new SecurityUser(
            1L,
            9L,
            "alice",
            "",
            List.of(),
            true,
            true,
            true,
            true
        );
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, "n/a", List.of())
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession(null, "sid-1"));
        request.addHeader("User-Agent", "Chrome");
        return request;
    }
}
