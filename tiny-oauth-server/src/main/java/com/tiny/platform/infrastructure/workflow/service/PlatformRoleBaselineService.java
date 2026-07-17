package com.tiny.platform.infrastructure.workflow.service;

import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.role.dto.RoleCreateUpdateDto;
import com.tiny.platform.infrastructure.auth.role.dto.RoleResponseDto;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import com.tiny.platform.infrastructure.auth.role.service.RoleService;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import com.tiny.platform.infrastructure.workflow.model.ProcessModelScopeType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class PlatformRoleBaselineService {

    private final RoleService roleService;
    private final RoleRepository roleRepository;

    public PlatformRoleBaselineService(RoleService roleService, RoleRepository roleRepository) {
        this.roleService = roleService;
        this.roleRepository = roleRepository;
    }

    @Transactional
    public Map<String, Object> publish(Map<String, Object> variables, Authentication authentication) {
        ScopeContext scope = currentScope();
        return publish(variables, authentication, scope.scopeType(), scope.tenantId());
    }

    @Transactional
    public Map<String, Object> publish(
        Map<String, Object> variables,
        Authentication authentication,
        ProcessModelScopeType scopeType,
        Long tenantId
    ) {
        ScopeContext scope = new ScopeContext(scopeType, tenantId);
        String changeType = normalize(requiredText(variables, "changeType"));
        String roleCode = requiredText(variables, "roleCode");
        String roleName = firstText(variables, "roleName", "roleCode");
        String description = firstText(variables, "description", "baselineReason");
        List<Long> permissionIds = normalizeIdList(variables.get("permissionIds"));

        Optional<Role> existing = roleRepository.findByCodeAndTenantId(roleCode, scope.tenantId());
        if ("CREATE".equals(changeType) && existing.isPresent()) {
            throw BusinessException.alreadyExists("平台角色已存在: " + roleCode);
        }
        if (("UPDATE".equals(changeType) || "DEPRECATE".equals(changeType)) && existing.isEmpty()) {
            throw BusinessException.notFound("平台角色不存在: " + roleCode);
        }

        RoleCreateUpdateDto dto = new RoleCreateUpdateDto();
        dto.setCode(roleCode);
        dto.setName(roleName);
        dto.setDescription(description);
        dto.setBuiltin(booleanValue(variables.get("builtin"), true));
        dto.setEnabled(!"DEPRECATE".equals(changeType) && booleanValue(variables.get("enabled"), true));
        dto.setRiskLevel(defaultText(variables, "riskLevel", "NORMAL"));
        dto.setApprovalMode(defaultText(variables, "approvalMode", "NONE"));
        dto.setPermissionIds("DEPRECATE".equals(changeType) ? List.of() : permissionIds);

        RoleResponseDto response = withScope(scope, () -> "CREATE".equals(changeType)
            ? roleService.create(dto)
            : roleService.update(existing.get().getId(), dto));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("roleId", response.getId());
        result.put("roleCode", response.getCode());
        result.put("roleName", response.getName());
        result.put("changeType", changeType);
        result.put("enabled", response.isEnabled());
        result.put("riskLevel", response.getRiskLevel());
        result.put("approvalMode", response.getApprovalMode());
        result.put("permissionIds", response.getPermissionIds());
        return result;
    }

    private ScopeContext currentScope() {
        if (TenantContext.isPlatformScope()) {
            return new ScopeContext(ProcessModelScopeType.PLATFORM, null);
        }
        Long activeTenantId = TenantContext.getActiveTenantId();
        if (activeTenantId == null || activeTenantId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "当前请求未解析到有效租户上下文");
        }
        return new ScopeContext(ProcessModelScopeType.TENANT, activeTenantId);
    }

    private RoleResponseDto withScope(ScopeContext scope, java.util.function.Supplier<RoleResponseDto> action) {
        Long previousTenantId = TenantContext.getActiveTenantId();
        String previousScopeType = TenantContext.getActiveScopeType();
        Long previousScopeId = TenantContext.getActiveScopeId();
        try {
            if (ProcessModelScopeType.PLATFORM.equals(scope.scopeType())) {
                TenantContext.setActiveTenantId(null);
                TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);
                TenantContext.setActiveScopeId(null);
            } else {
                TenantContext.setActiveTenantId(scope.tenantId());
                TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_TENANT);
                TenantContext.setActiveScopeId(scope.tenantId());
            }
            return action.get();
        } finally {
            TenantContext.setActiveTenantId(previousTenantId);
            TenantContext.setActiveScopeType(previousScopeType);
            TenantContext.setActiveScopeId(previousScopeId);
        }
    }

    private static String requiredText(Map<String, Object> variables, String field) {
        String value = optionalText(variables, field);
        if (value == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "缺少必填业务字段: " + field);
        }
        return value;
    }

    private static String firstText(Map<String, Object> variables, String firstField, String secondField) {
        String first = optionalText(variables, firstField);
        return first != null ? first : optionalText(variables, secondField);
    }

    private static String firstText(Map<String, Object> variables, String firstField, String secondField, String thirdField) {
        String first = optionalText(variables, firstField);
        if (first != null) {
            return first;
        }
        String second = optionalText(variables, secondField);
        return second != null ? second : optionalText(variables, thirdField);
    }

    private static String defaultText(Map<String, Object> variables, String field, String defaultValue) {
        String first = optionalText(variables, field);
        return first != null ? first : defaultValue;
    }

    private static String optionalText(Map<String, Object> variables, String field) {
        if (variables == null) {
            return null;
        }
        Object value = variables.get(field);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static boolean booleanValue(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static List<Long> normalizeIdList(Object value) {
        if (!(value instanceof List<?> rawValues)) {
            return List.of();
        }
        return rawValues.stream()
            .map(PlatformRoleBaselineService::parseLong)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
    }

    private static Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private record ScopeContext(ProcessModelScopeType scopeType, Long tenantId) {
    }
}
