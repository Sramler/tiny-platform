package com.tiny.platform.infrastructure.idempotent.starter.controller;

import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.response.ErrorResponse;
import com.tiny.platform.infrastructure.idempotent.core.context.IdempotentContext;
import com.tiny.platform.infrastructure.idempotent.core.engine.IdempotentEngine;
import com.tiny.platform.infrastructure.idempotent.core.key.IdempotentKey;
import com.tiny.platform.infrastructure.idempotent.core.strategy.IdempotentStrategy;
import com.tiny.platform.infrastructure.idempotent.starter.properties.IdempotentProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotentControllersTest {

    @Test
    void execute_request_dto_should_cover_getters_and_setters() {
        IdempotentExecuteController.ExecuteRequest request = new IdempotentExecuteController.ExecuteRequest();
        Map<String, Object> bizContext = new HashMap<>();
        bizContext.put("activeTenantId", "t1");

        request.setKey("http:demo:1");
        request.setTtl(33L);
        request.setFailOpen(Boolean.FALSE);
        request.setPayload(Map.of("x", 1));
        request.setBizContext(bizContext);

        assertThat(request.getKey()).isEqualTo("http:demo:1");
        assertThat(request.getTtl()).isEqualTo(33L);
        assertThat(request.getFailOpen()).isFalse();
        assertThat(request.getPayload()).isEqualTo(Map.of("x", 1));
        assertThat(request.getBizContext()).containsEntry("activeTenantId", "t1");
    }

    @Test
    void execute_should_return_bad_request_for_missing_and_invalid_key() {
        IdempotentEngine engine = mock(IdempotentEngine.class);
        IdempotentExecuteController controller = new IdempotentExecuteController(engine, properties());

        IdempotentExecuteController.ExecuteRequest missing = new IdempotentExecuteController.ExecuteRequest();
        ResponseEntity<?> missingResponse = controller.execute(missing);
        assertError(missingResponse, ErrorCode.MISSING_PARAMETER);

        IdempotentExecuteController.ExecuteRequest invalid = new IdempotentExecuteController.ExecuteRequest();
        invalid.setKey("bad-key");
        ResponseEntity<?> invalidResponse = controller.execute(invalid);
        assertError(invalidResponse, ErrorCode.INVALID_PARAMETER);
    }

    @Test
    void execute_should_use_request_values_and_payload_on_success() throws Throwable {
        IdempotentEngine engine = mock(IdempotentEngine.class);
        IdempotentExecuteController controller = new IdempotentExecuteController(engine, properties());
        IdempotentExecuteController.ExecuteRequest request = new IdempotentExecuteController.ExecuteRequest();
        request.setKey("http:orders:abc");
        request.setTtl(55L);
        request.setFailOpen(Boolean.FALSE);
        request.setPayload(Map.of("ok", true));

        doAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(1);
            return supplier.get();
        }).when(engine).execute(any(IdempotentContext.class), anySupplier());

        ResponseEntity<?> response = controller.execute(request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        Map<String, Object> body = asMap(response.getBody());
        assertThat(body).containsEntry("success", true);
        assertThat(body).containsEntry("result", Map.of("ok", true));
        assertThat(body).containsEntry("key", "http:orders:abc");

        ArgumentCaptor<IdempotentContext> captor = ArgumentCaptor.forClass(IdempotentContext.class);
        verify(engine).execute(captor.capture(), anySupplier());
        IdempotentContext context = captor.getValue();
        assertThat(context.getKey().getFullKey()).isEqualTo("http:orders:abc");
        assertThat(context.getStrategy().getTtlSeconds()).isEqualTo(55);
        assertThat(context.getStrategy().isFailOpen()).isFalse();
    }

    @Test
    void execute_should_use_defaults_and_default_payload_when_request_omits_values() throws Throwable {
        IdempotentEngine engine = mock(IdempotentEngine.class);
        IdempotentProperties properties = properties();
        properties.setTtl(120);
        properties.setFailOpen(true);
        IdempotentExecuteController controller = new IdempotentExecuteController(engine, properties);
        IdempotentExecuteController.ExecuteRequest request = new IdempotentExecuteController.ExecuteRequest();
        request.setKey("http:orders:def");

        doAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(1);
            return supplier.get();
        }).when(engine).execute(any(IdempotentContext.class), anySupplier());

        ResponseEntity<?> response = controller.execute(request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        Map<String, Object> body = asMap(response.getBody());
        assertThat(body).containsEntry("success", true);
        assertThat(body).containsEntry("key", "http:orders:def");
        assertThat(asMap(body.get("result"))).containsEntry("message", "执行成功");

        ArgumentCaptor<IdempotentContext> captor = ArgumentCaptor.forClass(IdempotentContext.class);
        verify(engine).execute(captor.capture(), anySupplier());
        IdempotentContext context = captor.getValue();
        assertThat(context.getStrategy().getTtlSeconds()).isEqualTo(120);
        assertThat(context.getStrategy().isFailOpen()).isTrue();
    }

    @Test
    void execute_should_handle_idempotent_conflict_and_internal_error() throws Throwable {
        IdempotentEngine conflictEngine = mock(IdempotentEngine.class);
        IdempotentExecuteController conflictController = new IdempotentExecuteController(conflictEngine, properties());
        IdempotentExecuteController.ExecuteRequest request = new IdempotentExecuteController.ExecuteRequest();
        request.setKey("http:orders:x");

        doThrow(new com.tiny.platform.infrastructure.idempotent.core.exception.IdempotentException("重复"))
            .when(conflictEngine).execute(any(IdempotentContext.class), anySupplier());
        assertError(conflictController.execute(request), ErrorCode.IDEMPOTENT_CONFLICT);

        IdempotentEngine errorEngine = mock(IdempotentEngine.class);
        IdempotentExecuteController errorController = new IdempotentExecuteController(errorEngine, properties());
        doThrow(new RuntimeException("boom"))
            .when(errorEngine).execute(any(IdempotentContext.class), anySupplier());
        assertError(errorController.execute(request), ErrorCode.INTERNAL_ERROR);
    }

    @Test
    void token_controller_get_token_should_cover_success_and_error() {
        IdempotentEngine engine = mock(IdempotentEngine.class);
        IdempotentProperties properties = properties();
        properties.setTtl(45);
        properties.setFailOpen(false);
        IdempotentTokenController controller = new IdempotentTokenController(engine, properties);

        when(engine.process(any(IdempotentKey.class), eq(45L), eq(false))).thenAnswer(invocation -> {
            IdempotentKey key = invocation.getArgument(0);
            long ttl = invocation.getArgument(1);
            boolean failOpen = invocation.getArgument(2);
            return new IdempotentContext(key, new IdempotentStrategy(ttl, failOpen));
        });

        ResponseEntity<?> defaultScope = controller.getToken(null);
        assertThat(defaultScope.getStatusCode().is2xxSuccessful()).isTrue();
        Map<String, Object> defaultBody = asMap(defaultScope.getBody());
        assertThat(defaultBody).containsEntry("success", true);
        assertThat(defaultBody).containsEntry("ttl", 45L);
        assertThat(defaultBody).containsKey("expireAt");
        IdempotentKey parsedDefault = IdempotentKey.parse((String) defaultBody.get("token"));
        assertThat(parsedDefault.getNamespace()).isEqualTo("http");
        assertThat(parsedDefault.getScope()).isEqualTo("default");
        assertThat(parsedDefault.getUniqueKey()).hasSize(32);

        ResponseEntity<?> customScope = controller.getToken("checkout");
        IdempotentKey parsedCustom = IdempotentKey.parse((String) asMap(customScope.getBody()).get("token"));
        assertThat(parsedCustom.getScope()).isEqualTo("checkout");

        when(engine.process(any(IdempotentKey.class), anyLong(), anyBoolean())).thenThrow(new RuntimeException("x"));
        assertError(controller.getToken("checkout"), ErrorCode.INTERNAL_ERROR);
    }

    @Test
    void token_controller_validate_should_cover_all_paths() {
        IdempotentEngine engine = mock(IdempotentEngine.class);
        IdempotentTokenController controller = new IdempotentTokenController(engine, properties());

        assertError(controller.validateToken(Map.of()), ErrorCode.MISSING_PARAMETER);
        assertError(controller.validateToken(Map.of("token", "")), ErrorCode.MISSING_PARAMETER);
        assertError(controller.validateToken(Map.of("token", "bad-token")), ErrorCode.INVALID_PARAMETER);

        when(engine.exists(any(IdempotentKey.class))).thenReturn(true);
        ResponseEntity<?> ok = controller.validateToken(Map.of("token", "http:scope:abc"));
        assertThat(ok.getStatusCode().is2xxSuccessful()).isTrue();
        Map<String, Object> okBody = asMap(ok.getBody());
        assertThat(okBody).containsEntry("success", true);
        assertThat(okBody).containsEntry("valid", true);
        assertThat(okBody).containsEntry("token", "http:scope:abc");

        when(engine.exists(any(IdempotentKey.class))).thenThrow(new RuntimeException("db"));
        assertError(controller.validateToken(Map.of("token", "http:scope:abc")), ErrorCode.INTERNAL_ERROR);
    }

    @Test
    void token_controller_release_should_cover_all_paths() {
        IdempotentEngine engine = mock(IdempotentEngine.class);
        IdempotentTokenController controller = new IdempotentTokenController(engine, properties());

        assertError(controller.releaseToken(Map.of()), ErrorCode.MISSING_PARAMETER);
        assertError(controller.releaseToken(Map.of("token", "")), ErrorCode.MISSING_PARAMETER);
        assertError(controller.releaseToken(Map.of("token", "bad-token")), ErrorCode.INVALID_PARAMETER);

        doNothing().when(engine).delete(any(IdempotentKey.class));
        ResponseEntity<?> ok = controller.releaseToken(Map.of("token", "http:scope:abc"));
        assertThat(ok.getStatusCode().is2xxSuccessful()).isTrue();
        Map<String, Object> okBody = asMap(ok.getBody());
        assertThat(okBody).containsEntry("success", true);
        assertThat(okBody).containsEntry("message", "Token 已释放");
        assertThat(okBody).containsEntry("token", "http:scope:abc");

        doThrow(new RuntimeException("db")).when(engine).delete(any(IdempotentKey.class));
        assertError(controller.releaseToken(Map.of("token", "http:scope:abc")), ErrorCode.INTERNAL_ERROR);
    }

    private static IdempotentProperties properties() {
        return new IdempotentProperties();
    }

    @SuppressWarnings("unchecked")
    private static Supplier<Object> anySupplier() {
        return (Supplier<Object>) any(Supplier.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    private static void assertError(ResponseEntity<?> response, ErrorCode errorCode) {
        assertThat(response.getStatusCode()).isEqualTo(errorCode.getStatus());
        ErrorResponse body = (ErrorResponse) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getCode()).isEqualTo(errorCode.getCode());
        assertThat(body.getMessage()).isEqualTo(errorCode.getMessage());
        assertThat(body.getStatus()).isEqualTo(errorCode.getStatusValue());
    }
}
