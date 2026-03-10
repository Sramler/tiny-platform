package com.tiny.platform.infrastructure.export.demo;

import com.tiny.platform.infrastructure.core.dto.PageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DemoExportUsageControllerTest {

    @Test
    void should_cover_crud_generate_clear_and_list() {
        DemoExportUsageService service = mock(DemoExportUsageService.class);
        ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);
        DemoExportUsageController controller = new DemoExportUsageController(service, executor);

        DemoExportUsageEntity entity = new DemoExportUsageEntity();
        entity.setId(1L);
        entity.setTenantId(10L);
        entity.setUsageDate(LocalDate.of(2026, 3, 1));
        entity.setProductCode("P-1");
        entity.setProductName("Product 1");
        entity.setPlanTier("pro");
        entity.setUnit("count");
        entity.setStatus("OK");

        when(service.create(entity)).thenReturn(entity);
        when(service.update(1L, entity)).thenReturn(entity);
        when(service.findById(1L)).thenReturn(Optional.of(entity));
        when(service.findById(404L)).thenReturn(Optional.empty());

        Pageable pageable = PageRequest.of(0, 10);
        when(service.search(eq(10L), eq("P-1"), eq("OK"), eq(LocalDate.of(2026, 3, 1)), eq(LocalDate.of(2026, 3, 2)), eq(pageable)))
            .thenReturn(new PageImpl<>(List.of(entity), pageable, 1));

        ResponseEntity<DemoExportUsageEntity> createResp = controller.create(entity);
        assertThat(createResp.getStatusCode().value()).isEqualTo(200);
        assertThat(createResp.getBody()).isEqualTo(entity);

        ResponseEntity<DemoExportUsageEntity> updateResp = controller.update(1L, entity);
        assertThat(updateResp.getStatusCode().value()).isEqualTo(200);
        assertThat(updateResp.getBody()).isEqualTo(entity);

        ResponseEntity<Void> deleteResp = controller.delete(1L);
        assertThat(deleteResp.getStatusCode().value()).isEqualTo(200);
        verify(service).delete(1L);

        ResponseEntity<DemoExportUsageEntity> getOk = controller.get(1L);
        assertThat(getOk.getStatusCode().value()).isEqualTo(200);
        assertThat(getOk.getBody()).isEqualTo(entity);
        assertThat(controller.get(404L).getStatusCode().value()).isEqualTo(404);

        ResponseEntity<PageResponse<DemoExportUsageEntity>> listResp = controller.list(
            10L,
            "P-1",
            "OK",
            LocalDate.of(2026, 3, 1),
            LocalDate.of(2026, 3, 2),
            pageable);
        assertThat(listResp.getStatusCode().value()).isEqualTo(200);
        assertThat(listResp.getBody()).isNotNull();
        assertThat(listResp.getBody().getContent()).containsExactly(entity);

        ResponseEntity<Map<String, Object>> clearResp = controller.clear(10L);
        assertThat(clearResp.getStatusCode().value()).isEqualTo(200);
        assertThat(clearResp.getBody()).containsEntry("tenantId", 10L);
        verify(service).clearByTenantId(10L);

        ResponseEntity<Map<String, Object>> generateResp = controller.generate(10L, 7, 2000, 0, false);
        assertThat(generateResp.getStatusCode().value()).isEqualTo(200);
        assertThat(generateResp.getBody()).containsEntry("tenantId", 10L);
        assertThat(generateResp.getBody()).containsEntry("days", 7);
        assertThat(generateResp.getBody()).containsEntry("rowsPerDay", 2000);
        assertThat(generateResp.getBody()).containsEntry("targetRows", 0);
        assertThat(generateResp.getBody()).containsEntry("clearExisting", false);
        assertThat(generateResp.getBody()).containsKey("elapsedMs");
        verify(service).generateDemoData(10L, 7, 2000, 0, false);
    }
}

