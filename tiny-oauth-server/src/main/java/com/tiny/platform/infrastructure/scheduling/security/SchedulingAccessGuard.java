package com.tiny.platform.infrastructure.scheduling.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * 调度模块权限访问控制。
 *
 * <p>权限边界：</p>
 * <ul>
 *     <li>READ：查看调度控制面的任务类型/任务/DAG/运行历史等只读信息</li>
 *     <li>MANAGE_CONFIG：管理调度控制面的任务类型/任务/DAG/版本/节点/依赖等配置</li>
 *     <li>OPERATE_RUN：控制 DAG 或节点运行，包括触发/停止/重试/暂停/恢复</li>
 *     <li>VIEW_AUDIT：查看调度审计记录</li>
 *     <li>VIEW_CLUSTER_STATUS：查看 Quartz 集群运行状态</li>
 * </ul>
 *
 * <p>约定：</p>
 * <ul>
 *     <li>权限标识使用 domain:resource:action 形式，例如 scheduling:console:view</li>
 *     <li>当 authentication 为空或未认证时，一律拒绝访问</li>
 * </ul>
 */
@Component("schedulingAccessGuard")
public class SchedulingAccessGuard {

    public static final String READ_AUTHORITY = "scheduling:console:view";
    public static final String MANAGE_CONFIG_AUTHORITY = "scheduling:console:config";
    public static final String OPERATE_RUN_AUTHORITY = "scheduling:run:control";
    public static final String VIEW_AUDIT_AUTHORITY = "scheduling:audit:view";
    public static final String VIEW_CLUSTER_STATUS_AUTHORITY = "scheduling:cluster:view";

    private static final String WILDCARD_AUTHORITY = "scheduling:*";

    public boolean canRead(Authentication authentication) {
        return hasAnyAuthority(authentication, READ_AUTHORITY);
    }

    public boolean canManageConfig(Authentication authentication) {
        return hasAnyAuthority(authentication, MANAGE_CONFIG_AUTHORITY);
    }

    public boolean canOperateRun(Authentication authentication) {
        return hasAnyAuthority(authentication, OPERATE_RUN_AUTHORITY);
    }

    public boolean canViewAudit(Authentication authentication) {
        return hasAnyAuthority(authentication, VIEW_AUDIT_AUTHORITY);
    }

    public boolean canViewClusterStatus(Authentication authentication) {
        return hasAnyAuthority(authentication, VIEW_CLUSTER_STATUS_AUTHORITY);
    }

    private boolean hasAnyAuthority(Authentication authentication, String required) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        if (authorities == null) {
            return false;
        }
        for (GrantedAuthority authority : authorities) {
            String value = authority.getAuthority();
            if (required.equals(value) || WILDCARD_AUTHORITY.equals(value)) {
                return true;
            }
        }
        return false;
    }
}
