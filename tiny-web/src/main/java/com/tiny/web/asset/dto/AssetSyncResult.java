package com.tiny.web.asset.dto;

import java.time.LocalDate;

public record AssetSyncResult(
        String syncRunId,
        LocalDate dataDate,
        boolean dryRun,
        long sourceRows,
        long sourceDistinctAssets,
        long matchedRows,
        long targetNotFoundRows,
        long targetDetailNotFoundRows,
        long changedRows,
        long updatedRows,
        long loggedRows
) {
}
