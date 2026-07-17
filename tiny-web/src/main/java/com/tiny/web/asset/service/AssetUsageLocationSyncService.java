package com.tiny.web.asset.service;

import com.tiny.web.asset.config.AssetSyncProperties;
import com.tiny.web.asset.dto.AssetSyncRequest;
import com.tiny.web.asset.dto.AssetSyncResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
public class AssetUsageLocationSyncService {

    private static final Logger log = LoggerFactory.getLogger(AssetUsageLocationSyncService.class);

    private final JdbcTemplate jdbcTemplate;
    private final AssetSyncProperties properties;

    public AssetUsageLocationSyncService(JdbcTemplate jdbcTemplate, AssetSyncProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    @Transactional
    public AssetSyncResult sync(AssetSyncRequest request) {
        String syncRunId = UUID.randomUUID().toString();
        LocalDate dataDate = resolveDataDate(request);
        boolean dryRun = request != null && Boolean.TRUE.equals(request.dryRun());

        long sourceRows = queryLong(countSourceRowsSql(), dataDate);
        long sourceDistinctAssets = queryLong(countDistinctAssetsSql(), dataDate);
        if (sourceRows != sourceDistinctAssets) {
            throw new IllegalStateException("A表在日期 " + dataDate + " 存在重复资产code，请先清洗数据后再同步");
        }

        long matchedRows = queryLong(countMatchedRowsSql(), dataDate);
        long targetDetailNotFoundRows = queryLong(countTargetDetailNotFoundRowsSql(), dataDate);
        long changedRows = queryLong(countChangedRowsSql(), dataDate);
        long targetNotFoundRows = sourceRows - matchedRows;
        long updatedRows = dryRun ? 0L : jdbcTemplate.update(updateTargetSql(), dataDate);
        long loggedRows = writeFailureLogs(syncRunId, dataDate, dryRun);

        return new AssetSyncResult(
                syncRunId,
                dataDate,
                dryRun,
                sourceRows,
                sourceDistinctAssets,
                matchedRows,
                targetNotFoundRows,
                targetDetailNotFoundRows,
                changedRows,
                updatedRows,
                loggedRows
        );
    }

    private LocalDate resolveDataDate(AssetSyncRequest request) {
        if (request != null && request.dataDate() != null) {
            return request.dataDate();
        }
        return LocalDate.now().minusDays(1);
    }

    private long queryLong(String sql, LocalDate dataDate) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, dataDate);
        return value == null ? 0L : value;
    }

    private String countSourceRowsSql() {
        return "SELECT COUNT(*) FROM " + properties.sourceTable()
                + " WHERE " + properties.sourceDateColumn() + " = ?";
    }

    private String countDistinctAssetsSql() {
        return "SELECT COUNT(DISTINCT " + properties.sourceCodeColumn() + ") FROM " + properties.sourceTable()
                + " WHERE " + properties.sourceDateColumn() + " = ?";
    }

    private String countMatchedRowsSql() {
        return """
                SELECT COUNT(*)
                FROM %s target
                JOIN %s source ON target.%s = source.%s
                WHERE source.%s = ?
                """.formatted(
                properties.targetTable(),
                properties.sourceTable(),
                properties.targetCodeColumn(),
                properties.sourceCodeColumn(),
                properties.sourceDateColumn()
        );
    }

    private String countChangedRowsSql() {
        return """
                SELECT COUNT(*)
                FROM %s source
                JOIN %s target ON target.%s = source.%s
                JOIN %s target_detail ON target_detail.%s = target.%s
                WHERE source.%s = ?
                  AND (
                    target_detail.%s IS DISTINCT FROM source.%s
                    OR target_detail.%s IS DISTINCT FROM source.%s
                  )
                """.formatted(
                properties.sourceTable(),
                properties.targetTable(),
                properties.targetCodeColumn(),
                properties.sourceCodeColumn(),
                properties.targetDetailTable(),
                properties.targetDetailIdColumn(),
                properties.targetDetailRefColumn(),
                properties.sourceDateColumn(),
                properties.targetUserColumn(),
                properties.sourceUserColumn(),
                properties.targetLocationColumn(),
                properties.sourceLocationColumn()
        );
    }

    private String countTargetDetailNotFoundRowsSql() {
        return """
                SELECT COUNT(*)
                FROM %s source
                JOIN %s target ON target.%s = source.%s
                LEFT JOIN %s target_detail ON target_detail.%s = target.%s
                WHERE source.%s = ?
                  AND target_detail.%s IS NULL
                """.formatted(
                properties.sourceTable(),
                properties.targetTable(),
                properties.targetCodeColumn(),
                properties.sourceCodeColumn(),
                properties.targetDetailTable(),
                properties.targetDetailIdColumn(),
                properties.targetDetailRefColumn(),
                properties.sourceDateColumn(),
                properties.targetDetailIdColumn()
        );
    }

    private long writeFailureLogs(String syncRunId, LocalDate dataDate, boolean dryRun) {
        Long loggedRowCount = jdbcTemplate.query(failureLogsSql(), ps -> ps.setObject(1, dataDate), rs -> {
            long count = 0L;
            while (rs.next()) {
                count++;
                writeFailureLog(syncRunId, dataDate, dryRun, rs.getString("asset_code"),
                        rs.getString("process_status"),
                        rs.getString("source_used_by"), rs.getString("source_storage_location"),
                        rs.getString("process_message"));
            }
            return count;
        });
        return loggedRowCount == null ? 0L : loggedRowCount;
    }

    private void writeFailureLog(String syncRunId,
                                 LocalDate dataDate,
                                 boolean dryRun,
                                 String assetCode,
                                 String status,
                                 String sourceUsedBy,
                                 String sourceStorageLocation,
                                 String message) {
        String logMessage = "asset-sync process syncRunId={} dataDate={} dryRun={} assetCode={} status={} sourceUsedBy={} "
                + "sourceStorageLocation={} message={}";
        log.error(
                logMessage,
                syncRunId,
                dataDate,
                dryRun,
                assetCode,
                status,
                sourceUsedBy,
                sourceStorageLocation,
                message
        );
    }

    private String failureLogsSql() {
        return """
                WITH source_rows AS (
                    SELECT
                        %s AS asset_code,
                        %s AS source_used_by,
                        %s AS source_storage_location
                    FROM %s
                    WHERE %s = ?
                )
                SELECT
                    source_rows.asset_code,
                    source_rows.source_used_by,
                    source_rows.source_storage_location,
                    'TARGET_NOT_FOUND' AS process_status,
                    'B表不存在该资产code，未更新' AS process_message
                FROM source_rows
                LEFT JOIN %s target ON target.%s = source_rows.asset_code
                WHERE target.%s IS NULL
                UNION ALL
                SELECT
                    source_rows.asset_code,
                    source_rows.source_used_by,
                    source_rows.source_storage_location,
                    'TARGET_DETAIL_NOT_FOUND' AS process_status,
                    'B表扩展记录不存在或B.user_defines为空，未更新' AS process_message
                FROM source_rows
                JOIN %s target ON target.%s = source_rows.asset_code
                LEFT JOIN %s target_detail ON target_detail.%s = target.%s
                WHERE target_detail.%s IS NULL
                """.formatted(
                properties.sourceCodeColumn(),
                properties.sourceUserColumn(),
                properties.sourceLocationColumn(),
                properties.sourceTable(),
                properties.sourceDateColumn(),
                properties.targetTable(),
                properties.targetCodeColumn(),
                properties.targetCodeColumn(),
                properties.targetTable(),
                properties.targetCodeColumn(),
                properties.targetDetailTable(),
                properties.targetDetailIdColumn(),
                properties.targetDetailRefColumn(),
                properties.targetDetailIdColumn()
        );
    }

    private String updateTargetSql() {
        return """
                UPDATE %s target_detail
                SET %s = source.%s,
                    %s = source.%s
                FROM %s source
                JOIN %s target ON target.%s = source.%s
                WHERE source.%s = ?
                  AND target_detail.%s = target.%s
                  AND (
                    target_detail.%s IS DISTINCT FROM source.%s
                    OR target_detail.%s IS DISTINCT FROM source.%s
                  )
                """.formatted(
                properties.targetDetailTable(),
                properties.targetUserColumn(),
                properties.sourceUserColumn(),
                properties.targetLocationColumn(),
                properties.sourceLocationColumn(),
                properties.sourceTable(),
                properties.targetTable(),
                properties.targetCodeColumn(),
                properties.sourceCodeColumn(),
                properties.sourceDateColumn(),
                properties.targetDetailIdColumn(),
                properties.targetDetailRefColumn(),
                properties.targetUserColumn(),
                properties.sourceUserColumn(),
                properties.targetLocationColumn(),
                properties.sourceLocationColumn()
        );
    }
}
