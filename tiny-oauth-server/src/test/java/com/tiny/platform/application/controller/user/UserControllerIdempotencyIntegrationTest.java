package com.tiny.platform.application.controller.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.auth.user.repository.UserAuthenticationAuditRepository;
import com.tiny.platform.infrastructure.auth.user.service.AvatarService;
import com.tiny.platform.infrastructure.auth.user.service.UserService;
import com.tiny.platform.infrastructure.core.exception.handler.OAuthServerExceptionHandler;
import com.tiny.platform.infrastructure.idempotent.core.engine.IdempotentEngine;
import com.tiny.platform.infrastructure.idempotent.repository.memory.MemoryIdempotentRepository;
import com.tiny.platform.infrastructure.idempotent.sdk.aspect.IdempotentAspect;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.RequestContextListener;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerIdempotencyIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = Jackson2ObjectMapperBuilder.json().build();

    @Test
    void create_should_reject_duplicate_request_with_same_key_in_same_tenant() throws Exception {
        UserService userService = mock(UserService.class);
        when(userService.createFromDto(any())).thenReturn(user(1L, "alice"));

        MockMvc mockMvc = buildMockMvc(userService);
        String body = createRequestBody("alice");

        mockMvc.perform(post("/sys/users")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Tenant-Id", "200")
                .header("X-Idempotency-Key", "same-key")
                .content(body))
            .andExpect(status().isOk());

        mockMvc.perform(post("/sys/users")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Tenant-Id", "200")
                .header("X-Idempotency-Key", "same-key")
                .content(body))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(40901))
            .andExpect(jsonPath("$.title").value("请勿重复提交"))
            .andExpect(jsonPath("$.detail").value("请勿重复提交"))
            .andExpect(jsonPath("$.status").value(409));

        verify(userService, times(1)).createFromDto(any());
    }

    @Test
    void create_should_isolate_same_key_across_tenants() throws Exception {
        UserService userService = mock(UserService.class);
        when(userService.createFromDto(any())).thenReturn(user(1L, "alice"));

        MockMvc mockMvc = buildMockMvc(userService);
        String body = createRequestBody("alice");

        mockMvc.perform(post("/sys/users")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Tenant-Id", "200")
                .header("X-Idempotency-Key", "same-key")
                .content(body))
            .andExpect(status().isOk());

        mockMvc.perform(post("/sys/users")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Tenant-Id", "201")
                .header("X-Idempotency-Key", "same-key")
                .content(body))
            .andExpect(status().isOk());

        verify(userService, times(2)).createFromDto(any());
    }

    private static MockMvc buildMockMvc(UserService userService) {
        UserController target = new UserController(
            userService,
            mock(UserAuthenticationAuditRepository.class),
            mock(AvatarService.class)
        );

        IdempotentAspect aspect = new IdempotentAspect(
            new IdempotentEngine(new MemoryIdempotentRepository()),
            java.util.List.of()
        );
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(target);
        proxyFactory.addAspect(aspect);
        UserController proxiedController = proxyFactory.getProxy();

        return MockMvcBuilders.standaloneSetup(proxiedController)
            .setControllerAdvice(new OAuthServerExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(OBJECT_MAPPER))
            .addFilters(new RequestContextListenerFilter(), new TenantHeaderFilter())
            .build();
    }

    private static String createRequestBody(String username) throws IOException {
        return OBJECT_MAPPER.writeValueAsString(java.util.Map.of(
            "username", username,
            "nickname", "Alice",
            "password", "secret123",
            "confirmPassword", "secret123",
            "enabled", true,
            "accountNonExpired", true,
            "accountNonLocked", true,
            "credentialsNonExpired", true
        ));
    }

    private static User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setNickname("Alice");
        user.setTenantId(200L);
        return user;
    }

    private static final class RequestContextListenerFilter extends OncePerRequestFilter {
        private final RequestContextListener requestContextListener = new RequestContextListener();

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
            requestContextListener.requestInitialized(new jakarta.servlet.ServletRequestEvent(request.getServletContext(), request));
            try {
                filterChain.doFilter(request, response);
            } finally {
                requestContextListener.requestDestroyed(new jakarta.servlet.ServletRequestEvent(request.getServletContext(), request));
            }
        }
    }

    private static final class TenantHeaderFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
            String tenantId = request.getHeader("X-Tenant-Id");
            if (tenantId != null && !tenantId.isBlank()) {
                TenantContext.setTenantId(Long.parseLong(tenantId));
            }
            try {
                filterChain.doFilter(request, response);
            } finally {
                TenantContext.clear();
            }
        }
    }
}
