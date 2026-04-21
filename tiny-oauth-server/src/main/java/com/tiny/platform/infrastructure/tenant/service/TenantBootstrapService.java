package com.tiny.platform.infrastructure.tenant.service;

import com.tiny.platform.infrastructure.tenant.domain.Tenant;

/**
 * 新租户从平台模板（{@code tenant_id IS NULL}）派生角色与载体副本。
 *
 * <p><b>平台模板「重建 / 回退」产品边界</b>：当前<strong>不</strong>提供删除租户副本后重新派生或一键回退到模板快照的 HTTP 入口；
 * 见 {@code TINY_PLATFORM_TENANT_GOVERNANCE.md} §3.2（正式契约 B）。{@link #bootstrapFromPlatformTemplate} 仅应由<strong>新租户创建</strong>链路调用一次；
 * 目标租户已存在角色或 carrier 副本时重复调用将 fail-closed。</p>
 *
 * <p>平台模板缺失时的<strong>一次性</strong>回填由 {@link #ensurePlatformTemplatesInitialized()} 完成；租户级偏差观测由
 * {@link #diffPlatformTemplateForTenant(Long)} 提供。</p>
 */
public interface TenantBootstrapService {
    void bootstrapFromPlatformTemplate(Tenant targetTenant);
    boolean ensurePlatformTemplatesInitialized();
    TenantBootstrapPreview previewBootstrapForCreate();
    PlatformTemplateDiffResult diffPlatformTemplateForTenant(Long tenantId);
}
