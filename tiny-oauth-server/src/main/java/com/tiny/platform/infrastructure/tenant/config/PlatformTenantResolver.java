package com.tiny.platform.infrastructure.tenant.config;

import com.tiny.platform.infrastructure.tenant.domain.Tenant;
import com.tiny.platform.infrastructure.tenant.repository.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 解析并缓存平台租户 ID，避免每次请求都查库。
 *
 * <p>兼容边界：仅用于 bootstrap / 历史入口兼容，不作为新业务平台语义主链。
 * 平台主语义应优先使用 {@code scope_type=PLATFORM}。
 * 平台租户由配置 {@code tiny.platform.tenant.platform-tenant-code}（默认 "default"）决定。
 * 首次调用 {@link #getPlatformTenantId()} 时惰性解析并缓存；
 * 若配置变更或租户重建，调用 {@link #invalidate()} 刷新。</p>
 */
@Component
public class PlatformTenantResolver {

    private static final Logger logger = LoggerFactory.getLogger(PlatformTenantResolver.class);

    private final TenantRepository tenantRepository;
    private final PlatformTenantProperties platformTenantProperties;
    private final AtomicReference<Long> cachedPlatformTenantId = new AtomicReference<>();

    public PlatformTenantResolver(TenantRepository tenantRepository,
                                  PlatformTenantProperties platformTenantProperties) {
        this.tenantRepository = tenantRepository;
        this.platformTenantProperties = platformTenantProperties;
    }

    /**
     * 获取平台租户 ID（惰性解析，首次查库后缓存）。
     *
     * @return 平台租户 ID，若不存在返回 null
     */
    public Long getPlatformTenantId() {
        Long cached = cachedPlatformTenantId.get();
        if (cached != null) {
            return cached;
        }
        return resolveAndCache();
    }

    /**
     * 判断给定的 activeTenantId 是否为平台租户。
     */
    public boolean isPlatformTenant(Long activeTenantId) {
        if (activeTenantId == null) {
            return false;
        }
        Long platformId = getPlatformTenantId();
        return platformId != null && Objects.equals(platformId, activeTenantId);
    }

    public void invalidate() {
        cachedPlatformTenantId.set(null);
    }

    private synchronized Long resolveAndCache() {
        Long cached = cachedPlatformTenantId.get();
        if (cached != null) {
            return cached;
        }
        String code = platformTenantProperties.getPlatformTenantCode();
        Long resolved = tenantRepository.findByCode(code)
                .map(Tenant::getId)
                .orElse(null);
        if (resolved != null) {
            cachedPlatformTenantId.set(resolved);
            logger.info("平台租户已解析并缓存: code={}, id={}", code, resolved);
        } else {
            logger.warn("平台租户不存在: code={}", code);
        }
        return resolved;
    }
}
