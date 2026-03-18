package com.tiny.platform.infrastructure.auth.role.service;

import java.util.List;

/**
 * RBAC3 角色约束统一校验入口。
 *
 * <p>职责与契约见授权模型文档：
 * <ul>
 *   <li>{@code docs/TINY_PLATFORM_AUTHORIZATION_MODEL.md} §5.9</li>
 *   <li>{@code docs/TINY_PLATFORM_AUTHORIZATION_PHASE2_RBAC3_TECHNICAL_DESIGN.md} 第 3 章</li>
 * </ul>
 *
 * <p>该接口负责在写入 {@code role_assignment} 之前，对即将授予的角色集合执行：
 * <ul>
 *   <li>role_hierarchy 展开（用于冲突检测）</li>
 *   <li>role_mutex 冲突检查</li>
 *   <li>role_prerequisite 先决条件检查</li>
 *   <li>role_cardinality 基数限制检查</li>
 * </ul>
 *
 * <p>当前实现阶段仅定义接口与调用约定，具体约束表与逻辑在 Phase 2 落地时补充。</p>
 */
public interface RoleConstraintService {

    /**
     * 在授予角色之前校验角色约束。
     *
     * @param principalType  授权主体类型（当前阶段固定为 USER，后续可扩展 POST/GROUP 等）
     * @param principalId    授权主体 ID（如 user.id）
     * @param tenantId       当前租户 ID（用于限定 role_* 约束表范围）
     * @param scopeType      授权作用域类型（PLATFORM/TENANT/ORG/DEPT 等）
     * @param scopeId        授权作用域 ID（scope_type=TENANT 时为 tenant_id 等）
     * @param roleIdsToGrant 即将授予的角色 ID 列表（模板层 role.id）
     *
     * @throws IllegalStateException 当检测到互斥、先决条件或基数限制冲突时抛出，
     *                               具体错误码与异常类型由调用方或后续实现按文档约定映射。
     */
    void validateAssignmentsBeforeGrant(
        String principalType,
        Long principalId,
        Long tenantId,
        String scopeType,
        Long scopeId,
        List<Long> roleIdsToGrant
    );
}

