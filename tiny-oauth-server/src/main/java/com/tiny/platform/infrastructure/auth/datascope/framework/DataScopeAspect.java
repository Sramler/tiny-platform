package com.tiny.platform.infrastructure.auth.datascope.framework;

import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.auth.datascope.service.DataScopeResolverService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * AOP 切面：在进入 {@code @DataScope} 标注的方法前，解析用户的有效数据范围，
 * 写入 {@link DataScopeContext} ThreadLocal，方法执行后清理。
 *
 * <p>需要 {@link TenantContext} 和 Spring Security {@link Authentication} 都已就绪。
 * 如果缺少必要上下文（未登录、无租户），则跳过数据范围注入，
 * 业务方法需自行处理 {@link DataScopeContext#get()} 返回 null 的情况。</p>
 */
@Aspect
@Component
public class DataScopeAspect {

    private static final Logger logger = LoggerFactory.getLogger(DataScopeAspect.class);

    private final DataScopeResolverService resolverService;

    public DataScopeAspect(DataScopeResolverService resolverService) {
        this.resolverService = resolverService;
    }

    @Around("@annotation(dataScope)")
    public Object around(ProceedingJoinPoint joinPoint, DataScope dataScope) throws Throwable {
        Long tenantId = TenantContext.getActiveTenantId();
        Long userId = extractUserId();

        if (tenantId == null || userId == null) {
            logger.debug("DataScopeAspect: skipped (tenantId={}, userId={})", tenantId, userId);
            return joinPoint.proceed();
        }

        try {
            ResolvedDataScope resolved = resolverService.resolve(
                userId, tenantId, dataScope.module(), dataScope.accessType());
            DataScopeContext.set(resolved);
            return joinPoint.proceed();
        } finally {
            DataScopeContext.clear();
        }
    }

    private Long extractUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof com.tiny.platform.core.oauth.model.SecurityUser securityUser) {
            return securityUser.getUserId();
        }
        return null;
    }
}
