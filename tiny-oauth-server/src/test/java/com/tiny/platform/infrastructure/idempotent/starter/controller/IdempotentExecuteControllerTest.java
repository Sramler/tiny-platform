package com.tiny.platform.infrastructure.idempotent.starter.controller;

import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.response.ErrorResponse;
import com.tiny.platform.infrastructure.idempotent.core.context.IdempotentContext;
import com.tiny.platform.infrastructure.idempotent.core.engine.IdempotentEngine;
import com.tiny.platform.infrastructure.idempotent.core.exception.IdempotentException;
import com.tiny.platform.infrastructure.idempotent.starter.properties.IdempotentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IdempotentExecuteControllerTest {

    private IdempotentEngine engine;
    private IdempotentProperties properties;
    private IdempotentExecuteController controller;

    @BeforeEach
    void setUp() {
        engine = mock(IdempotentEngine.class);
        properties = mock(IdempotentProperties.class);
        when(properties.getTtl()).thenReturn(60L);
        when(properties.isFailOpen()).thenReturn(false);
        controller = new IdempotentExecuteController(engine, properties);
    }

    @Test
    void execute_whenMissingKey_shouldReturnMissingParameter() {
        IdempotentExecuteController.ExecuteRequest req = new IdempotentExecuteController.ExecuteRequest();
        req.setKey("");

        ResponseEntity<?> resp = controller.execute(req);

        assertEquals(ErrorCode.MISSING_PARAMETER.getStatus(), resp.getStatusCode());
        assertTrue(resp.getBody() instanceof ErrorResponse);
        ErrorResponse body = (ErrorResponse) resp.getBody();
        assertEquals(ErrorCode.MISSING_PARAMETER.getCode(), body.getCode());
    }

    @Test
    void execute_whenInvalidKey_shouldReturnInvalidParameter() {
        IdempotentExecuteController.ExecuteRequest req = new IdempotentExecuteController.ExecuteRequest();
        req.setKey("bad-format");

        ResponseEntity<?> resp = controller.execute(req);

        assertEquals(ErrorCode.INVALID_PARAMETER.getStatus(), resp.getStatusCode());
        assertTrue(resp.getBody() instanceof ErrorResponse);
        ErrorResponse body = (ErrorResponse) resp.getBody();
        assertEquals(ErrorCode.INVALID_PARAMETER.getCode(), body.getCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_whenSuccess_shouldReturnSuccessMap() throws Throwable {
        IdempotentExecuteController.ExecuteRequest req = new IdempotentExecuteController.ExecuteRequest();
        req.setKey("http:test:1");
        req.setPayload(Map.of("k", "v"));

        when(engine.execute(any(IdempotentContext.class), any(Supplier.class)))
            .thenAnswer(inv -> ((Supplier<Object>) inv.getArgument(1)).get());

        ResponseEntity<?> resp = controller.execute(req);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getBody() instanceof Map);
        Map<?, ?> body = (Map<?, ?>) resp.getBody();
        assertEquals(Boolean.TRUE, body.get("success"));
        assertEquals("http:test:1", body.get("key"));
        assertEquals(Map.of("k", "v"), body.get("result"));
        verify(engine).execute(any(IdempotentContext.class), any(Supplier.class));
    }

    @Test
    void execute_whenIdempotentConflict_shouldReturnConflictError() throws Throwable {
        IdempotentExecuteController.ExecuteRequest req = new IdempotentExecuteController.ExecuteRequest();
        req.setKey("http:test:2");

        when(engine.execute(any(IdempotentContext.class), any(Supplier.class)))
            .thenThrow(new IdempotentException("dup"));

        ResponseEntity<?> resp = controller.execute(req);

        assertEquals(ErrorCode.IDEMPOTENT_CONFLICT.getStatus(), resp.getStatusCode());
        assertTrue(resp.getBody() instanceof ErrorResponse);
        ErrorResponse body = (ErrorResponse) resp.getBody();
        assertEquals(ErrorCode.IDEMPOTENT_CONFLICT.getCode(), body.getCode());
    }
}

