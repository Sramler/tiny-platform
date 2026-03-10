package com.tiny.platform.application.controller.idempotent.controller;

import com.tiny.platform.infrastructure.core.dto.PageResponse;
import com.tiny.platform.infrastructure.idempotent.console.IdempotentConsoleService;
import com.tiny.platform.infrastructure.idempotent.console.dto.IdempotentBlacklistDto;
import com.tiny.platform.infrastructure.idempotent.console.dto.IdempotentRecordDto;
import com.tiny.platform.infrastructure.idempotent.console.dto.IdempotentRuleDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.PageImpl;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdempotentConsoleControllerTest {

    private IdempotentConsoleService consoleService;
    private IdempotentConsoleController controller;

    @BeforeEach
    void setUp() {
        consoleService = mock(IdempotentConsoleService.class);
        controller = new IdempotentConsoleController(consoleService);
    }

    @Test
    void rules_crud_shouldDelegate() {
        PageResponse<IdempotentRuleDto> page = new PageResponse<>(new PageImpl<>(List.of()));
        when(consoleService.getRules(null, null, 0, 10)).thenReturn(page);
        ResponseEntity<PageResponse<IdempotentRuleDto>> listResp = controller.getRules(null, null, 0, 10);
        assertEquals(HttpStatus.OK, listResp.getStatusCode());

        IdempotentRuleDto dto = new IdempotentRuleDto();
        when(consoleService.createRule(dto)).thenReturn(dto);
        assertEquals(dto, controller.createRule(dto).getBody());

        when(consoleService.updateRule(1L, dto)).thenReturn(dto);
        assertEquals(dto, controller.updateRule(1L, dto).getBody());

        ResponseEntity<Void> deleteResp = controller.deleteRule(2L);
        assertEquals(HttpStatus.NO_CONTENT, deleteResp.getStatusCode());
        verify(consoleService).deleteRule(2L);
    }

    @Test
    void enable_disable_shouldParseIdsAndDelegate() {
        controller.enableRules(Map.of("ids", List.of(1, 2, 3)));
        verify(consoleService).enableRules(List.of(1L, 2L, 3L));

        controller.disableRules(Map.of("ids", List.of(7, 8)));
        verify(consoleService).disableRules(List.of(7L, 8L));
    }

    @Test
    void records_metrics_blacklist_shouldDelegate() {
        PageResponse<IdempotentRecordDto> records = new PageResponse<>(new PageImpl<>(List.of()));
        when(consoleService.getRecords("k", "SUCCESS", 0, 10)).thenReturn(records);
        assertEquals(records, controller.getRecords("k", "SUCCESS", 0, 10).getBody());

        when(consoleService.getMetricsMap("2026-01-01", "scene", 1L)).thenReturn(Map.of("success", true));
        assertEquals(Boolean.TRUE, controller.getMetrics("2026-01-01", "scene", 1L).getBody().get("success"));

        PageResponse<IdempotentBlacklistDto> blacklist = new PageResponse<>(new PageImpl<>(List.of()));
        when(consoleService.getBlacklist(0, 10)).thenReturn(blacklist);
        assertEquals(blacklist, controller.getBlacklist(0, 10).getBody());

        IdempotentBlacklistDto b = new IdempotentBlacklistDto();
        when(consoleService.addBlacklist(b)).thenReturn(b);
        assertEquals(b, controller.addBlacklist(b).getBody());

        ResponseEntity<Void> del = controller.deleteBlacklist(9L);
        assertEquals(HttpStatus.NO_CONTENT, del.getStatusCode());
        verify(consoleService).deleteBlacklist(9L);
    }

    @Test
    void retryRecord_shouldFallbackToIdWhenKeyMissing() {
        controller.retryRecord(Map.of("id", 123));
        verify(consoleService).retryRecord("123");
    }
}

