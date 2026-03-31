package com.tiny.platform.application.oauth.workflow.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 工作流（BPM）控制面权限守卫。
 *
 * <p>权限码遵循 {@code workflow:<resource>:<action>} 三段式规范，
 * 与调度模块 scheduling 的分层策略对齐：</p>
 * <ul>
 *   <li>{@code workflow:console:view} — 查看部署、实例、任务、历史、定义、引擎信息</li>
 *   <li>{@code workflow:console:config} — 部署/删除部署/定义、BPMN 校验与修复</li>
 *   <li>{@code workflow:instance:control} — 启动/挂起/激活/删除实例、领取/完成任务</li>
 *   <li>{@code workflow:tenant:manage} — 工作流引擎租户管理</li>
 * </ul>
 *
 * <p>{@code /process/health} 为健康检查端点，仅要求已认证，不在本 Guard 管控范围。</p>
 */
@Component("workflowAccessGuard")
public class WorkflowAccessGuard {

    static final Set<String> CONSOLE_VIEW_AUTHORITIES = Set.of("workflow:console:view");
    static final Set<String> CONSOLE_CONFIG_AUTHORITIES = Set.of("workflow:console:config");
    static final Set<String> INSTANCE_CONTROL_AUTHORITIES = Set.of("workflow:instance:control");
    static final Set<String> TENANT_MANAGE_AUTHORITIES = Set.of("workflow:tenant:manage");

    /** 通配符：拥有此权限视为拥有 workflow 全部操作权限 */
    private static final String WILDCARD = "workflow:*";

    public boolean canView(Authentication authentication) {
        return hasAnyAuthority(authentication, CONSOLE_VIEW_AUTHORITIES);
    }

    public boolean canConfig(Authentication authentication) {
        return hasAnyAuthority(authentication, CONSOLE_CONFIG_AUTHORITIES);
    }

    public boolean canControlInstance(Authentication authentication) {
        return hasAnyAuthority(authentication, INSTANCE_CONTROL_AUTHORITIES);
    }

    public boolean canManageTenant(Authentication authentication) {
        return hasAnyAuthority(authentication, TENANT_MANAGE_AUTHORITIES);
    }

    private boolean hasAnyAuthority(Authentication authentication, Set<String> requiredAuthorities) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(grantedAuthority -> grantedAuthority.getAuthority())
                .anyMatch(authority -> WILDCARD.equals(authority)
                        || requiredAuthorities.contains(authority));
    }
}
