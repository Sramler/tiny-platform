package com.tiny.platform.core.oauth.security;

import com.tiny.platform.infrastructure.auth.resource.service.ApiEndpointRequirementDecision;
import com.tiny.platform.infrastructure.auth.resource.service.ResourceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ApiEndpointRequirementFilterTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void should_skip_framework_endpoint_like_csrf_even_when_authenticated() throws Exception {
        ResourceService resourceService = mock(ResourceService.class);
        ApiEndpointRequirementFilter filter = new ApiEndpointRequirementFilter(resourceService);
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated("platform_admin", null, java.util.List.of())
        );

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/csrf");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(resourceService);
    }

    @Test
    void should_skip_self_security_endpoints_even_when_authenticated() throws Exception {
        ResourceService resourceService = mock(ResourceService.class);
        ApiEndpointRequirementFilter filter = new ApiEndpointRequirementFilter(resourceService);
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated("platform_admin", null, java.util.List.of())
        );

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/self/security/totp/pre-bind");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(resourceService);
    }

    @Test
    void should_skip_runtime_menu_tree_endpoint_even_when_authenticated() throws Exception {
        ResourceService resourceService = mock(ResourceService.class);
        ApiEndpointRequirementFilter filter = new ApiEndpointRequirementFilter(resourceService);
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated("platform_admin", null, java.util.List.of())
        );

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sys/menus/tree");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(resourceService);
    }

    @Test
    void should_skip_platform_token_debug_endpoint_even_when_authenticated() throws Exception {
        ResourceService resourceService = mock(ResourceService.class);
        ApiEndpointRequirementFilter filter = new ApiEndpointRequirementFilter(resourceService);
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated("platform_admin", null, java.util.List.of())
        );

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/sys/platform/token-debug/decode");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(resourceService);
    }

    @Test
    void should_evaluate_business_endpoint_when_authenticated() throws Exception {
        ResourceService resourceService = mock(ResourceService.class);
        ApiEndpointRequirementFilter filter = new ApiEndpointRequirementFilter(resourceService);
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated("platform_admin", null, java.util.List.of())
        );
        when(resourceService.evaluateApiEndpointRequirement("GET", "/sys/resources"))
            .thenReturn(ApiEndpointRequirementDecision.ALLOWED);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sys/resources");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        verify(resourceService).evaluateApiEndpointRequirement("GET", "/sys/resources");
    }

    @Test
    void should_return_forbidden_when_business_endpoint_denied() throws Exception {
        ResourceService resourceService = mock(ResourceService.class);
        ApiEndpointRequirementFilter filter = new ApiEndpointRequirementFilter(resourceService);
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated("platform_admin", null, java.util.List.of())
        );
        when(resourceService.evaluateApiEndpointRequirement("DELETE", "/sys/resources/9"))
            .thenReturn(ApiEndpointRequirementDecision.DENIED);

        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/sys/resources/9");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(MockHttpServletResponse.SC_FORBIDDEN);
        assertThat(response.getErrorMessage()).isEqualTo("api_endpoint requirement denied");
    }
}
