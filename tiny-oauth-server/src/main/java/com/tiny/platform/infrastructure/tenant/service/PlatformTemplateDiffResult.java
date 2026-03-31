package com.tiny.platform.infrastructure.tenant.service;

import java.util.List;
import java.util.Map;

/**
 * 平台模板与租户副本的差异检测结果。
 *
 * <p>说明：该结果只用于治理可观测与审计，不作为运行时授权真相源。</p>
 */
public record PlatformTemplateDiffResult(
    Long tenantId,
    Summary summary,
    List<EntryDiff> diffs
) {
    public record Summary(
        int totalPlatformEntries,
        int totalTenantEntries,
        int missingInTenant,
        int extraInTenant,
        int changed
    ) {}

    /**
     * 单条载体差异。
     *
     * <p>key 使用稳定逻辑标识（优先 permissionCode），避免依赖数据库主键。</p>
     */
    public record EntryDiff(
        String carrierType,
        String key,
        Long platformCarrierId,
        Long tenantCarrierId,
        DiffType diffType,
        Map<String, FieldDiff> fieldDiffs
    ) {}

    public enum DiffType {
        MISSING_IN_TENANT,
        EXTRA_IN_TENANT,
        CHANGED
    }

    public record FieldDiff(String platformValue, String tenantValue) {}
}

