package com.tiny.platform.infrastructure.auth.user.support;

/**
 * 认证作用域策略视图：描述某个作用域下认证方式是否启用、是否主方式及优先级。
 *
 * <p>当前桥接期仍沿用 {@code tenant_id} 作为旧策略载体，因此这里先显式命名为
 * {@code scopeTenantId}；后续拆出独立策略表时可平滑替换为真正的 scope 字段。</p>
 */
public record UserAuthenticationScopePolicy(
        Long userId,
        Long scopeTenantId,
        String authenticationProvider,
        String authenticationType,
        Boolean isPrimaryMethod,
        Boolean isMethodEnabled,
        Integer authenticationPriority) {
}
