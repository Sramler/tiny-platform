package com.tiny.platform.infrastructure.idempotent.starter.controller;

import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.response.ErrorResponse;
import com.tiny.platform.infrastructure.idempotent.core.context.IdempotentContext;
import com.tiny.platform.infrastructure.idempotent.core.engine.IdempotentEngine;
import com.tiny.platform.infrastructure.idempotent.core.key.IdempotentKey;
import com.tiny.platform.infrastructure.idempotent.starter.properties.IdempotentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class IdempotentTokenControllerTest {

    private IdempotentEngine engine;
    private IdempotentProperties properties;
    private IdempotentTokenController controller;

    @BeforeEach
    void setUp() {
        engine = mock(IdempotentEngine.class);
        properties = mock(IdempotentProperties.class);
        when(properties.getTtl()).thenReturn(60L);
        when(properties.isFailOpen()).thenReturn(false);
        controller = new IdempotentTokenController(engine, properties);
    }

    @Test
    void getToken_shouldReturnSuccessMap() {
        when(engine.process(any(IdempotentKey.class), anyLong(), anyBoolean()))
            .thenAnswer(inv -> new IdempotentContext(inv.getArgument(0), new com.tiny.platform.infrastructure.idempotent.core.strategy.IdempotentStrategy(60, false)));

        ResponseEntity<?> resp = controller.getToken("scope1");

        assertEquals(200, resp.getStatusCode().value());
        assertTrue(resp.getBody() instanceof Map);
        Map<?, ?> body = (Map<?, ?>) resp.getBody();
        assertEquals(Boolean.TRUE, body.get("success"));
        assertNotNull(body.get("token"));
        verify(engine).process(any(IdempotentKey.class), eq(60L), eq(false));
    }

    @Test
    void validateToken_whenMissing_shouldReturnMissingParameter() {
        ResponseEntity<?> resp = controller.validateToken(Map.of());

        assertEquals(ErrorCode.MISSING_PARAMETER.getStatus(), resp.getStatusCode());
        assertTrue(resp.getBody() instanceof ErrorResponse);
    }

    @Test
    void validateToken_whenInvalidFormat_shouldReturnInvalidParameter() {
        ResponseEntity<?> resp = controller.validateToken(Map.of("token", "bad"));

        assertEquals(ErrorCode.INVALID_PARAMETER.getStatus(), resp.getStatusCode());
        assertTrue(resp.getBody() instanceof ErrorResponse);
    }

    @Test
    void validateToken_whenExists_shouldReturnValidTrue() {
        when(engine.exists(any(IdempotentKey.class))).thenReturn(true);

        ResponseEntity<?> resp = controller.validateToken(Map.of("token", "http:s:1"));

        assertEquals(200, resp.getStatusCode().value());
        Map<?, ?> body = (Map<?, ?>) resp.getBody();
        assertEquals(Boolean.TRUE, body.get("success"));
        assertEquals(Boolean.TRUE, body.get("valid"));
        verify(engine).exists(any(IdempotentKey.class));
    }

    @Test
    void releaseToken_shouldDeleteKey() {
        ResponseEntity<?> resp = controller.releaseToken(Map.of("token", "http:s:2"));

        assertEquals(200, resp.getStatusCode().value());
        verify(engine).delete(any(IdempotentKey.class));
    }
}

