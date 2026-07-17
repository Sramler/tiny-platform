package com.tiny.web.asset.dto;

import jakarta.validation.constraints.PastOrPresent;

import java.time.LocalDate;

public record AssetSyncRequest(
        @PastOrPresent(message = "dataDate不能晚于当前日期")
        LocalDate dataDate,
        Boolean dryRun
) {
}
