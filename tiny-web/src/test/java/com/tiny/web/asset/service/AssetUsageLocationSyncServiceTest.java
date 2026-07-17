package com.tiny.web.asset.service;

import com.tiny.web.asset.config.AssetSyncProperties;
import com.tiny.web.asset.dto.AssetSyncRequest;
import com.tiny.web.asset.dto.AssetSyncResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class AssetUsageLocationSyncServiceTest {

    private JdbcTemplate jdbcTemplate;
    private AssetUsageLocationSyncService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        AssetSyncProperties properties = new AssetSyncProperties();
        properties.validateConfiguredIdentifiers();
        service = new AssetUsageLocationSyncService(jdbcTemplate, properties);
    }

    @Test
    void syncDryRunOnlyCountsRows() {
        LocalDate dataDate = LocalDate.of(2026, 5, 28);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq(dataDate)))
                .thenReturn(100L, 100L, 90L, 5L, 12L);
        when(jdbcTemplate.query(anyString(), any(PreparedStatementSetter.class), any(ResultSetExtractor.class)))
                .thenReturn(15L);

        AssetSyncResult result = service.sync(new AssetSyncRequest(dataDate, true));

        assertThat(result.syncRunId()).isNotBlank();
        assertThat(result.dataDate()).isEqualTo(dataDate);
        assertThat(result.dryRun()).isTrue();
        assertThat(result.sourceRows()).isEqualTo(100L);
        assertThat(result.sourceDistinctAssets()).isEqualTo(100L);
        assertThat(result.matchedRows()).isEqualTo(90L);
        assertThat(result.targetNotFoundRows()).isEqualTo(10L);
        assertThat(result.targetDetailNotFoundRows()).isEqualTo(5L);
        assertThat(result.changedRows()).isEqualTo(12L);
        assertThat(result.updatedRows()).isZero();
        assertThat(result.loggedRows()).isEqualTo(15L);
        verify(jdbcTemplate, never()).update(anyString(), eq(dataDate));
    }

    @Test
    void syncUpdatesChangedRows() {
        LocalDate dataDate = LocalDate.of(2026, 5, 28);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq(dataDate)))
                .thenReturn(100L, 100L, 90L, 5L, 12L);
        when(jdbcTemplate.update(anyString(), eq(dataDate))).thenReturn(12);
        when(jdbcTemplate.query(anyString(), any(PreparedStatementSetter.class), any(ResultSetExtractor.class)))
                .thenReturn(15L);

        AssetSyncResult result = service.sync(new AssetSyncRequest(dataDate, false));

        assertThat(result.updatedRows()).isEqualTo(12L);
        assertThat(result.targetDetailNotFoundRows()).isEqualTo(5L);
        assertThat(result.loggedRows()).isEqualTo(15L);
        verify(jdbcTemplate).update(anyString(), eq(dataDate));
        verify(jdbcTemplate).query(anyString(), any(PreparedStatementSetter.class), any(ResultSetExtractor.class));
    }

    @Test
    void syncRejectsDuplicateAssetCodesInSourceTable() {
        LocalDate dataDate = LocalDate.of(2026, 5, 28);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq(dataDate)))
                .thenReturn(2L, 1L);

        assertThatThrownBy(() -> service.sync(new AssetSyncRequest(dataDate, false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("重复资产code");
        verify(jdbcTemplate, never()).update(anyString(), eq(dataDate));
        verify(jdbcTemplate, never()).query(anyString(), any(PreparedStatementSetter.class), any(ResultSetExtractor.class));
    }
}
