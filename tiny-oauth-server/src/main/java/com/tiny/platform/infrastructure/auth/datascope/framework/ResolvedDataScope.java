package com.tiny.platform.infrastructure.auth.datascope.framework;

import java.util.Collections;
import java.util.Set;

/**
 * 解析后的有效数据范围（不可变值对象）。
 *
 * <p>对于同一用户同一模块同一 accessType，多个角色的数据范围取最宽覆盖。
 * 最终解析为以下几种过滤语义之一：</p>
 * <ul>
 *   <li>{@code scopeAll = true} — 无需过滤，可见全部数据</li>
 *   <li>{@code selfOnly = true} — 仅可见自己创建/拥有的数据</li>
 *   <li>{@code visibleUnitIds} 非空 — 可见指定组织/部门节点及其数据</li>
 *   <li>{@code visibleUserIds} 非空 — 可见指定用户的数据（CUSTOM + target_type=USER）</li>
 * </ul>
 *
 * <p>多种条件可以叠加（OR 合并），最终由 {@code DataScopeSpecification} 翻译为 JPA 查询谓词。</p>
 */
public final class ResolvedDataScope {

    private final boolean scopeAll;
    private final boolean selfOnly;
    private final Set<Long> visibleUnitIds;
    private final Set<Long> visibleUserIds;

    private ResolvedDataScope(boolean scopeAll, boolean selfOnly,
                              Set<Long> visibleUnitIds, Set<Long> visibleUserIds) {
        this.scopeAll = scopeAll;
        this.selfOnly = selfOnly;
        this.visibleUnitIds = visibleUnitIds == null ? Collections.emptySet() : Collections.unmodifiableSet(visibleUnitIds);
        this.visibleUserIds = visibleUserIds == null ? Collections.emptySet() : Collections.unmodifiableSet(visibleUserIds);
    }

    public static ResolvedDataScope all() {
        return new ResolvedDataScope(true, false, null, null);
    }

    public static ResolvedDataScope selfOnly() {
        return new ResolvedDataScope(false, true, null, null);
    }

    public static ResolvedDataScope ofUnitsAndUsers(Set<Long> unitIds, Set<Long> userIds, boolean includeSelf) {
        return new ResolvedDataScope(false, includeSelf, unitIds, userIds);
    }

    public boolean isScopeAll() { return scopeAll; }
    public boolean isSelfOnly() { return selfOnly; }
    public Set<Long> getVisibleUnitIds() { return visibleUnitIds; }
    public Set<Long> getVisibleUserIds() { return visibleUserIds; }

    /**
     * 是否不需要过滤（ALL 或 TENANT 范围）。
     */
    public boolean isUnrestricted() {
        return scopeAll;
    }

    @Override
    public String toString() {
        if (scopeAll) return "ResolvedDataScope[ALL]";
        if (selfOnly && visibleUnitIds.isEmpty() && visibleUserIds.isEmpty()) return "ResolvedDataScope[SELF]";
        return "ResolvedDataScope[units=" + visibleUnitIds.size()
            + ", users=" + visibleUserIds.size()
            + ", selfOnly=" + selfOnly + "]";
    }
}
