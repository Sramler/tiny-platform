package com.tiny.platform.infrastructure.workflow.service;

import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import com.tiny.platform.infrastructure.workflow.model.ProcessModelScopeType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class PlatformPermissionPublishService {

    private static final String PERMISSION_TYPE_BUTTON = "BUTTON";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final RoleRepository roleRepository;

    public PlatformPermissionPublishService(NamedParameterJdbcTemplate jdbcTemplate, RoleRepository roleRepository) {
        this.jdbcTemplate = jdbcTemplate;
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
        String permissionCode = requiredText(variables, "permissionCode");
        String permissionName = firstText(variables, "permissionName", "permissionCode");
        String moduleCode = optionalText(variables, "moduleCode");
        if (moduleCode == null) {
            moduleCode = derivedModuleCode(permissionCode);
        }
        String actionCode = optionalText(variables, "actionCode");
        if (actionCode == null) {
            actionCode = derivedActionCode(permissionCode);
        }
        String permissionType = optionalText(variables, "permissionType");
        if (permissionType == null) {
            permissionType = PERMISSION_TYPE_BUTTON;
        }
        String description = firstText(variables, "description", "impactScope");
        boolean enabled = switch (changeType) {
            case "DEPRECATE" -> false;
            case "UPDATE" -> booleanValue(variables.get("enabled"), true);
            default -> booleanValue(variables.get("enabled"), true);
        };
        boolean builtInFlag = booleanValue(variables.get("builtInFlag"), false);
        List<Long> roleIds = normalizeIdList(variables.get("roleIds"));

        Optional<PermissionRow> existing = findPermission(scope.tenantId(), permissionCode);
        if ("CREATE".equals(changeType) && existing.isPresent()) {
            throw BusinessException.alreadyExists("权限码已存在: " + permissionCode);
        }
        if (("UPDATE".equals(changeType) || "DEPRECATE".equals(changeType)) && existing.isEmpty()) {
            throw BusinessException.notFound("权限码不存在: " + permissionCode);
        }

        Long permissionId;
        if (existing.isPresent()) {
            permissionId = existing.get().id();
            updatePermission(scope.tenantId(), permissionId, permissionCode, permissionName, moduleCode, actionCode, permissionType, description, enabled, builtInFlag, authentication);
        } else {
            permissionId = insertPermission(scope.tenantId(), permissionCode, permissionName, moduleCode, actionCode, permissionType, description, enabled, builtInFlag, authentication);
        }

        if ("DEPRECATE".equals(changeType)) {
            jdbcTemplate.update(
                """
                DELETE FROM `role_permission`
                WHERE `permission_id` = :permissionId
                  AND `tenant_id` <=> :tenantId
                """,
                new MapSqlParameterSource()
                    .addValue("permissionId", permissionId)
                    .addValue("tenantId", scope.tenantId())
            );
        } else if (!roleIds.isEmpty()) {
            replaceRoleBindings(scope, permissionId, roleIds);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("permissionId", permissionId);
        result.put("permissionCode", permissionCode);
        result.put("permissionName", permissionName);
        result.put("changeType", changeType);
        result.put("enabled", enabled);
        result.put("roleIds", roleIds);
        return result;
    }

    private Long insertPermission(
        Long tenantId,
        String permissionCode,
        String permissionName,
        String moduleCode,
        String actionCode,
        String permissionType,
        String description,
        boolean enabled,
        boolean builtInFlag,
        Authentication authentication
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO `permission` (
              `permission_code`,
              `permission_name`,
              `module_code`,
              `action_code`,
              `permission_type`,
              `description`,
              `enabled`,
              `built_in_flag`,
              `tenant_id`,
              `created_by`,
              `created_at`,
              `updated_by`,
              `updated_at`
            ) VALUES (
              :permissionCode,
              :permissionName,
              :moduleCode,
              :actionCode,
              :permissionType,
              :description,
              :enabled,
              :builtInFlag,
              :tenantId,
              :actorUserId,
              NOW(),
              :actorUserId,
              NOW()
            )
            """,
            permissionParams(tenantId, permissionCode, permissionName, moduleCode, actionCode, permissionType, description, enabled, builtInFlag, authentication)
        );
        return findPermission(tenantId, permissionCode)
            .map(PermissionRow::id)
            .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, "权限创建失败: " + permissionCode));
    }

    private void updatePermission(
        Long tenantId,
        Long permissionId,
        String permissionCode,
        String permissionName,
        String moduleCode,
        String actionCode,
        String permissionType,
        String description,
        boolean enabled,
        boolean builtInFlag,
        Authentication authentication
    ) {
        int updated = jdbcTemplate.update(
            """
            UPDATE `permission`
            SET `permission_code` = :permissionCode,
                `permission_name` = :permissionName,
                `module_code` = :moduleCode,
                `action_code` = :actionCode,
                `permission_type` = :permissionType,
                `description` = :description,
                `enabled` = :enabled,
                `built_in_flag` = :builtInFlag,
                `updated_by` = :actorUserId,
                `updated_at` = NOW()
            WHERE `id` = :permissionId
              AND `normalized_tenant_id` = IFNULL(:tenantId, 0)
            """,
            permissionParams(tenantId, permissionCode, permissionName, moduleCode, actionCode, permissionType, description, enabled, builtInFlag, authentication)
                .addValue("permissionId", permissionId)
        );
        if (updated <= 0) {
            throw BusinessException.notFound("权限码不存在: " + permissionCode);
        }
    }

    private void replaceRoleBindings(ScopeContext scope, Long permissionId, List<Long> roleIds) {
        List<Role> visibleRoles = scope.tenantId() == null
            ? roleRepository.findByIdInAndTenantIdIsNullOrderByIdAsc(roleIds)
            : roleRepository.findByIdInAndTenantIdOrderByIdAsc(roleIds, scope.tenantId());
        if (visibleRoles.size() != new LinkedHashSet<>(roleIds).size()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "存在不属于当前作用域的角色 ID");
        }

        jdbcTemplate.update(
            """
            DELETE FROM `role_permission`
            WHERE `permission_id` = :permissionId
              AND `tenant_id` <=> :tenantId
            """,
            new MapSqlParameterSource()
                .addValue("permissionId", permissionId)
                .addValue("tenantId", scope.tenantId())
        );
        for (Long roleId : new LinkedHashSet<>(roleIds)) {
            jdbcTemplate.update(
                """
                INSERT IGNORE INTO `role_permission` (
                  `tenant_id`, `role_id`, `permission_id`, `created_by`, `created_at`
                ) VALUES (
                  :tenantId, :roleId, :permissionId, :actorUserId, NOW()
                )
                """,
                new MapSqlParameterSource()
                    .addValue("tenantId", scope.tenantId())
                    .addValue("roleId", roleId)
                    .addValue("permissionId", permissionId)
                    .addValue("actorUserId", null, Types.BIGINT)
            );
        }
    }

    private Optional<PermissionRow> findPermission(Long tenantId, String permissionCode) {
        List<PermissionRow> rows = jdbcTemplate.query(
            """
            SELECT
              p.`id`,
              p.`permission_code`
            FROM `permission` p
            WHERE p.`normalized_tenant_id` = IFNULL(:tenantId, 0)
              AND p.`permission_code` = :permissionCode
            LIMIT 1
            """,
            new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("permissionCode", permissionCode),
            (rs, rowNum) -> new PermissionRow(rs.getLong("id"), rs.getString("permission_code"))
        );
        return rows.stream().findFirst();
    }

    private MapSqlParameterSource permissionParams(
        Long tenantId,
        String permissionCode,
        String permissionName,
        String moduleCode,
        String actionCode,
        String permissionType,
        String description,
        boolean enabled,
        boolean builtInFlag,
        Authentication authentication
    ) {
        return new MapSqlParameterSource()
            .addValue("tenantId", tenantId)
            .addValue("permissionCode", permissionCode)
            .addValue("permissionName", permissionName)
            .addValue("moduleCode", moduleCode)
            .addValue("actionCode", actionCode)
            .addValue("permissionType", permissionType)
            .addValue("description", description)
            .addValue("enabled", enabled)
            .addValue("builtInFlag", builtInFlag)
            .addValue("actorUserId", null, Types.BIGINT);
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
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (Object rawValue : rawValues) {
            Long id = parseLong(rawValue);
            if (id != null) {
                ids.add(id);
            }
        }
        return new ArrayList<>(ids);
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

    private static String derivedModuleCode(String permissionCode) {
        if (!StringUtils.hasText(permissionCode) || !permissionCode.contains(":")) {
            return "workflow";
        }
        return permissionCode.trim().split(":")[0];
    }

    private static String derivedActionCode(String permissionCode) {
        if (!StringUtils.hasText(permissionCode) || !permissionCode.contains(":")) {
            return "publish";
        }
        String[] parts = permissionCode.trim().split(":");
        return parts[parts.length - 1];
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private record ScopeContext(ProcessModelScopeType scopeType, Long tenantId) {
    }

    private record PermissionRow(Long id, String permissionCode) {
    }
}
