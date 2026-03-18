package com.tiny.platform.application.controller.idempotent.controller;

import com.tiny.platform.infrastructure.idempotent.metrics.IdempotentMetricsService;
import com.tiny.platform.infrastructure.idempotent.metrics.IdempotentMetricsSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdempotentMetricsControllerTest {

    private IdempotentMetricsService metricsService;
    private IdempotentMetricsController controller;

    @BeforeEach
    void setUp() {
        metricsService = mock(IdempotentMetricsService.class);
        controller = new IdempotentMetricsController(metricsService);
    }

    @Test
    void getMetrics_shouldReturnSnapshotFields() {
        IdempotentMetricsSnapshot snap = new IdempotentMetricsSnapshot(
            5, 1L, 2L,
            1, 2, 3, 4,
            5, 6, 7,
            8, 0.1, 0.2
        );
        when(metricsService.snapshot(9L)).thenReturn(snap);

        Map<String, Object> resp = controller.getMetrics(9L).getBody();

        assertEquals(Boolean.TRUE, resp.get("success"));
        assertEquals(9L, resp.get("activeTenantId"));
        assertEquals(5L, resp.get("windowMinutes"));
        assertEquals(8L, resp.get("totalCheckCount"));
        assertEquals(0.1, (Double) resp.get("conflictRate"), 1e-9);
    }

    @Test
    void getTopKeys_shouldClampLimitAndDelegate() {
        IdempotentMetricsSnapshot snap = new IdempotentMetricsSnapshot(
            5, 1L, 2L,
            0, 0, 0, 0,
            0, 0, 0,
            0, 0.0, 0.0
        );
        when(metricsService.snapshot(null)).thenReturn(snap);
        when(metricsService.topScopes(1000, null)).thenReturn(List.of(Map.of("key", "a", "count", 1L)));

        Map<String, Object> resp = controller.getTopKeys(1000, null).getBody();

        assertEquals(100, resp.get("limit"));
        assertEquals(List.of(Map.of("key", "a", "count", 1L)), resp.get("topKeys"));
        verify(metricsService).topScopes(1000, null);
    }
}
