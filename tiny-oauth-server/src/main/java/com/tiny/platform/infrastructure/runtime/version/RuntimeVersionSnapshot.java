package com.tiny.platform.infrastructure.runtime.version;

import java.time.LocalDateTime;

public record RuntimeVersionSnapshot(
    Long id,
    RuntimeVersionKey key,
    String versionValue,
    long versionSeq,
    String reason,
    LocalDateTime updatedAt,
    Long updatedBy
) {
}
