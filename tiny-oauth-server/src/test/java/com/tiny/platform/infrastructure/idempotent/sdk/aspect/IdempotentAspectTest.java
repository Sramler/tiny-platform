package com.tiny.platform.infrastructure.idempotent.sdk.aspect;

import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import com.tiny.platform.infrastructure.idempotent.core.context.IdempotentContext;
import com.tiny.platform.infrastructure.idempotent.core.engine.IdempotentEngine;
import com.tiny.platform.infrastructure.idempotent.core.key.IdempotentKey;
import com.tiny.platform.infrastructure.idempotent.sdk.annotation.Idempotent;
import com.tiny.platform.infrastructure.idempotent.sdk.resolver.IdempotentKeyResolver;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotentAspectTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void around_should_use_spel_key_and_delegate_successfully() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Idempotency-Key", "spel-key");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        IdempotentEngine engine = mock(IdempotentEngine.class);
        IdempotentAspect aspect = new IdempotentAspect(engine, null);
        ProceedingJoinPoint joinPoint = joinPointFor("spelMethod", new Object[]{"ignored"}, "OK");

        doAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(1);
            return supplier.get();
        }).when(engine).execute(any(IdempotentContext.class), anySupplier());

        Method method = SampleService.class.getDeclaredMethod("spelMethod", String.class);
        Object result = aspect.around(joinPoint, method.getAnnotation(Idempotent.class));

        assertThat(result).isEqualTo("OK");

        ArgumentCaptor<IdempotentContext> captor = ArgumentCaptor.forClass(IdempotentContext.class);
        verify(engine).execute(captor.capture(), anySupplier());
        IdempotentContext context = captor.getValue();
        assertThat(context.getKey().getNamespace()).isEqualTo("http");
        assertThat(context.getKey().getScope()).isEqualTo("SampleService.spelMethod");
        assertThat(context.getKey().getUniqueKey()).isEqualTo("spel-key");
        assertThat(context.getStrategy().getTtlSeconds()).isEqualTo(12);
        assertThat(context.getStrategy().isFailOpen()).isFalse();
    }

    @Test
    void around_should_use_resolver_and_skip_broken_resolver() throws Throwable {
        IdempotentEngine engine = mock(IdempotentEngine.class);
        IdempotentKeyResolver broken = (jp, method, args) -> {
            throw new RuntimeException("resolver-error");
        };
        IdempotentKeyResolver resolver = (jp, method, args) -> IdempotentKey.of("mq", "topic", "offset-1");
        IdempotentAspect aspect = new IdempotentAspect(engine, List.of(broken, resolver));
        ProceedingJoinPoint joinPoint = joinPointFor("resolverMethod", new Object[]{"arg"}, "RESOLVED");

        doAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(1);
            return supplier.get();
        }).when(engine).execute(any(IdempotentContext.class), anySupplier());

        Method method = SampleService.class.getDeclaredMethod("resolverMethod", String.class);
        Object result = aspect.around(joinPoint, method.getAnnotation(Idempotent.class));

        assertThat(result).isEqualTo("RESOLVED");
        ArgumentCaptor<IdempotentContext> captor = ArgumentCaptor.forClass(IdempotentContext.class);
        verify(engine).execute(captor.capture(), anySupplier());
        assertThat(captor.getValue().getKey().getFullKey()).isEqualTo("mq:topic:offset-1");
    }

    @Test
    void around_should_fallback_to_default_key_from_header_md5_and_non_servlet_context() throws Throwable {
        IdempotentEngine engine1 = mock(IdempotentEngine.class);
        IdempotentAspect aspect1 = new IdempotentAspect(engine1, List.of());
        MockHttpServletRequest headerRequest = new MockHttpServletRequest();
        headerRequest.addHeader("X-Idempotency-Key", "header-key");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(headerRequest));
        ProceedingJoinPoint headerJoinPoint = joinPointFor("defaultMethod", new Object[]{"a", 1}, "H");

        doAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(1);
            return supplier.get();
        }).when(engine1).execute(any(IdempotentContext.class), anySupplier());
        Method defaultMethod = SampleService.class.getDeclaredMethod("defaultMethod", String.class, Integer.class);
        aspect1.around(headerJoinPoint, defaultMethod.getAnnotation(Idempotent.class));

        ArgumentCaptor<IdempotentContext> headerCaptor = ArgumentCaptor.forClass(IdempotentContext.class);
        verify(engine1).execute(headerCaptor.capture(), anySupplier());
        assertThat(headerCaptor.getValue().getKey().getUniqueKey()).isEqualTo("header-key");

        IdempotentEngine engine2 = mock(IdempotentEngine.class);
        IdempotentAspect aspect2 = new IdempotentAspect(engine2, List.of());
        RequestContextHolder.resetRequestAttributes();
        ProceedingJoinPoint md5JoinPoint = joinPointFor("defaultMethod", new Object[]{"a", 1}, "M");
        doAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(1);
            return supplier.get();
        }).when(engine2).execute(any(IdempotentContext.class), anySupplier());
        aspect2.around(md5JoinPoint, defaultMethod.getAnnotation(Idempotent.class));
        ArgumentCaptor<IdempotentContext> md5Captor = ArgumentCaptor.forClass(IdempotentContext.class);
        verify(engine2).execute(md5Captor.capture(), anySupplier());
        assertThat(md5Captor.getValue().getKey().getUniqueKey()).hasSize(32);

        IdempotentEngine engine3 = mock(IdempotentEngine.class);
        IdempotentAspect aspect3 = new IdempotentAspect(engine3, List.of());
        RequestAttributes badAttributes = mock(RequestAttributes.class);
        RequestContextHolder.setRequestAttributes(badAttributes);
        ProceedingJoinPoint badCtxJoinPoint = joinPointFor("defaultMethod", new Object[]{"b", 2}, "N");
        doAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(1);
            return supplier.get();
        }).when(engine3).execute(any(IdempotentContext.class), anySupplier());
        aspect3.around(badCtxJoinPoint, defaultMethod.getAnnotation(Idempotent.class));
        ArgumentCaptor<IdempotentContext> badCtxCaptor = ArgumentCaptor.forClass(IdempotentContext.class);
        verify(engine3).execute(badCtxCaptor.capture(), anySupplier());
        assertThat(badCtxCaptor.getValue().getKey().getUniqueKey()).hasSize(32);
    }

    @Test
    void around_should_handle_invalid_spel_and_exception_translation() throws Throwable {
        IdempotentEngine invalidSpelEngine = mock(IdempotentEngine.class);
        IdempotentAspect invalidSpelAspect = new IdempotentAspect(invalidSpelEngine, List.of());
        ProceedingJoinPoint invalidSpelJoinPoint = joinPointFor("invalidSpelMethod", new Object[]{"x"}, "OK");

        doAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(1);
            return supplier.get();
        }).when(invalidSpelEngine).execute(any(IdempotentContext.class), anySupplier());

        Method invalidSpelMethod = SampleService.class.getDeclaredMethod("invalidSpelMethod", String.class);
        invalidSpelAspect.around(invalidSpelJoinPoint, invalidSpelMethod.getAnnotation(Idempotent.class));
        ArgumentCaptor<IdempotentContext> invalidSpelCaptor = ArgumentCaptor.forClass(IdempotentContext.class);
        verify(invalidSpelEngine).execute(invalidSpelCaptor.capture(), anySupplier());
        assertThat(invalidSpelCaptor.getValue().getKey().getUniqueKey()).hasSize(32);

        IdempotentEngine duplicateEngine = mock(IdempotentEngine.class);
        IdempotentAspect duplicateAspect = new IdempotentAspect(duplicateEngine, List.of());
        ProceedingJoinPoint duplicateJoinPoint = joinPointFor("duplicateMethod", new Object[]{"x"}, "IGNORED");
        Method duplicateMethod = SampleService.class.getDeclaredMethod("duplicateMethod", String.class);
        doThrow(new com.tiny.platform.infrastructure.idempotent.core.exception.IdempotentException("core-dup"))
            .when(duplicateEngine).execute(any(IdempotentContext.class), anySupplier());

        assertThatThrownBy(() -> duplicateAspect.around(duplicateJoinPoint, duplicateMethod.getAnnotation(Idempotent.class)))
            .isInstanceOf(com.tiny.platform.infrastructure.idempotent.sdk.exception.IdempotentException.class)
            .hasMessage("sdk-dup-msg");

        IdempotentEngine businessErrorEngine = mock(IdempotentEngine.class);
        IdempotentAspect businessErrorAspect = new IdempotentAspect(businessErrorEngine, List.of());
        ProceedingJoinPoint businessJoinPoint = joinPointFor("defaultMethod", new Object[]{"x", 1}, new IllegalStateException("biz"));
        doAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(1);
            return supplier.get();
        }).when(businessErrorEngine).execute(any(IdempotentContext.class), anySupplier());

        assertThatThrownBy(() -> businessErrorAspect.around(businessJoinPoint, duplicateMethod.getAnnotation(Idempotent.class)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("biz");

        IdempotentEngine runtimeEngine = mock(IdempotentEngine.class);
        IdempotentAspect runtimeAspect = new IdempotentAspect(runtimeEngine, List.of());
        ProceedingJoinPoint runtimeJoinPoint = joinPointFor("defaultMethod", new Object[]{"x", 1}, "ignored");
        RuntimeException bare = new RuntimeException("engine-runtime");
        doThrow(bare).when(runtimeEngine).execute(any(IdempotentContext.class), anySupplier());

        assertThatThrownBy(() -> runtimeAspect.around(runtimeJoinPoint, duplicateMethod.getAnnotation(Idempotent.class)))
            .isSameAs(bare);
    }

    @Test
    void around_should_include_tenant_user_and_request_path_in_scope() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/sys/users");
        request.addHeader("X-Idempotency-Key", "scope-key");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        TenantContext.setActiveTenantId(200L);
        SecurityUser currentUser = new SecurityUser(8L, 200L, "alice", "",
            List.of(), true, true, true, true);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(currentUser, "N/A", List.of())
        );

        IdempotentEngine engine = mock(IdempotentEngine.class);
        IdempotentAspect aspect = new IdempotentAspect(engine, List.of());
        ProceedingJoinPoint joinPoint = joinPointFor("defaultMethod", new Object[]{"a", 1}, "OK");

        doAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(1);
            return supplier.get();
        }).when(engine).execute(any(IdempotentContext.class), anySupplier());

        Method method = SampleService.class.getDeclaredMethod("defaultMethod", String.class, Integer.class);
        aspect.around(joinPoint, method.getAnnotation(Idempotent.class));

        ArgumentCaptor<IdempotentContext> captor = ArgumentCaptor.forClass(IdempotentContext.class);
        verify(engine).execute(captor.capture(), anySupplier());
        assertThat(captor.getValue().getKey().getScope()).isEqualTo("200|8|POST /sys/users");
        assertThat(captor.getValue().getKey().getUniqueKey()).isEqualTo("scope-key");
    }

    @Test
    void around_should_reject_invalid_header_key() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/sys/users");
        request.addHeader("X-Idempotency-Key", "invalid key!");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        IdempotentEngine engine = mock(IdempotentEngine.class);
        IdempotentAspect aspect = new IdempotentAspect(engine, List.of());
        ProceedingJoinPoint joinPoint = joinPointFor("spelMethod", new Object[]{"ignored"}, "OK");
        Method method = SampleService.class.getDeclaredMethod("spelMethod", String.class);

        assertThatThrownBy(() -> aspect.around(joinPoint, method.getAnnotation(Idempotent.class)))
            .isInstanceOf(BusinessException.class)
            .hasMessage("幂等键只允许字母、数字、点、短横线、下划线和冒号");
    }

    private static ProceedingJoinPoint joinPointFor(String methodName, Object[] args, Object proceedResultOrThrowable) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method;
        if ("defaultMethod".equals(methodName)) {
            method = SampleService.class.getDeclaredMethod(methodName, String.class, Integer.class);
        } else {
            method = SampleService.class.getDeclaredMethod(methodName, String.class);
        }

        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(args);
        if (proceedResultOrThrowable instanceof Throwable throwable) {
            when(joinPoint.proceed()).thenThrow(throwable);
        } else {
            when(joinPoint.proceed()).thenReturn(proceedResultOrThrowable);
        }
        return joinPoint;
    }

    @SuppressWarnings("unchecked")
    private static Supplier<Object> anySupplier() {
        return (Supplier<Object>) any(Supplier.class);
    }

    static class SampleService {
        @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", timeout = 12, failOpen = false, message = "spel-dup")
        String spelMethod(String input) {
            return input;
        }

        @Idempotent
        String resolverMethod(String input) {
            return input;
        }

        @Idempotent
        String defaultMethod(String a, Integer b) {
            return a + b;
        }

        @Idempotent(key = "#bad expression", message = "invalid-spel")
        String invalidSpelMethod(String input) {
            return input;
        }

        @Idempotent(message = "sdk-dup-msg")
        String duplicateMethod(String input) {
            return input;
        }
    }
}
