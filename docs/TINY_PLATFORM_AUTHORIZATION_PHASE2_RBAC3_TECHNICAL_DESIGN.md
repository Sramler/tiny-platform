# Tiny Platform 授权模型第二阶段：RBAC3 角色治理技术设计

> 状态更新：本文件保留为 **Phase2 历史技术设计草案 / 参考文档 / 非当前运行态真相源**。  
> 当前仓库的授权主链、当前完成度与文档读取入口，请优先以 [TINY_PLATFORM_AUTHORIZATION_DOC_MAP.md](./TINY_PLATFORM_AUTHORIZATION_DOC_MAP.md)、[TINY_PLATFORM_AUTHORIZATION_MODEL.md](./TINY_PLATFORM_AUTHORIZATION_MODEL.md) 与 [TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md](./TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md) 为准。  
> 本文中若出现 `resource.permission`、阶段性非目标或历史演进口径，应按“Phase2 当时设计假设”理解，不作为当前运行态默认结论。  
>
> 状态：技术设计草案（Phase 2）  
> 适用范围：`auth / role / resource / tenant / security`
>
> 关联文档：
>
> - [TINY_PLATFORM_AUTHORIZATION_DOC_MAP.md](./TINY_PLATFORM_AUTHORIZATION_DOC_MAP.md)
> - `docs/TINY_PLATFORM_AUTHORIZATION_MODEL.md`（总设计，§4.2、§4.3、§7.2、§8.4）
> - `docs/TINY_PLATFORM_AUTHORIZATION_PHASE1_TECHNICAL_DESIGN.md`（Phase1）
> - `.agent/src/rules/93-tiny-platform-authorization-model.rules.md`

---

## 1. 目标与非目标

### 1.1 目标

- 把现有“平面角色列表”升级为可治理的角色体系，覆盖：
  - 角色继承：`role_hierarchy`
  - 角色互斥：`role_mutex`
  - 先决条件：`role_prerequisite`
  - 基数限制：`role_cardinality`
- 所有约束在“授权/分配阶段”校验，而不是运行态静默容错。
- 与 Phase1 的 `role_assignment` / `tenant_user` 完整对齐，不破坏现有授权链路。

### 1.2 非目标

- 不在本阶段引入新的主体类型（仍仅 `principal_type=USER`）。
- 不在本阶段改变 `resource.permission` 作为权限真相源的定位。
- 不在本阶段实现 Data Scope（`role_data_scope` 系列），仅为后续预留。

---

## 2. 新增表结构（DDL 草案）

> 说明：本节为逻辑 DDL，实际落库将在后续 Liquibase changelog 中实现，文件命名建议区间为 `05x-role-rbac3-*.yaml`。

### 2.1 `role_hierarchy`（角色继承）

用途：表达“子角色包含父角色所有权限”，用于菜单/权限展开和冲突分析。

```sql
CREATE TABLE role_hierarchy (
  id             BIGINT      PRIMARY KEY AUTO_INCREMENT,
  tenant_id      BIGINT      NOT NULL,
  parent_role_id BIGINT      NOT NULL,
  child_role_id  BIGINT      NOT NULL,
  created_at     DATETIME    NOT NULL,
  created_by     BIGINT      NULL,
  updated_at     DATETIME    NOT NULL,
  CONSTRAINT uk_role_hierarchy UNIQUE (tenant_id, parent_role_id, child_role_id)
  -- 如目标 MySQL 版本支持，可补充：
  -- ,CONSTRAINT chk_role_hierarchy_no_self CHECK (parent_role_id <> child_role_id)
);
```

约束与约定：

- 仅在同一 `tenant_id` 内建立继承关系（平台模板另行约定，可用 `tenant_id IS NULL` 时约束为平台层级）。
- 不允许出现 `parent_role_id = child_role_id` 的自环；即使数据库不支持 CHECK 约束，也必须在 Service 层拒绝这种配置。
- Service 层在保存时必须检测环路，使 `role_hierarchy` 逻辑上保持为有向无环图（DAG）。最小要求是：
  - 直接环路（A→A）与两级环路（A→B→A）在保存时拒绝；
  - 多级复杂环路至少在治理工具或审计脚本中能够被检测并阻止继续新增冲突配置。

### 2.2 `role_mutex`（角色互斥）

用途：表达“这两个角色不能同时授予同一主体/同一作用域”。

```sql
CREATE TABLE role_mutex (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id    BIGINT     NOT NULL,
  left_role_id BIGINT     NOT NULL,
  right_role_id BIGINT    NOT NULL,
  created_at   DATETIME   NOT NULL,
  created_by   BIGINT     NULL,
  updated_at   DATETIME   NOT NULL,
  CONSTRAINT uk_role_mutex UNIQUE (tenant_id, left_role_id, right_role_id)
);
```

约束与约定：

- 逻辑上互斥是对称关系；表中可约定 `left_role_id < right_role_id`，避免重复记录（由 Service 保证）。
- 在授权阶段（写 `role_assignment`）发现将导致互斥冲突时，必须失败并返回明确错误码（例如 `ROLE_CONFLICT_MUTEX`）。

### 2.3 `role_prerequisite`（先决条件）

用途：表达“授予某个角色前，主体必须已经拥有另一个角色（在相同或更宽的 scope 内）”。

```sql
CREATE TABLE role_prerequisite (
  id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id          BIGINT     NOT NULL,
  role_id            BIGINT     NOT NULL,
  required_role_id   BIGINT     NOT NULL,
  created_at         DATETIME   NOT NULL,
  created_by         BIGINT     NULL,
  updated_at         DATETIME   NOT NULL,
  CONSTRAINT uk_role_prerequisite UNIQUE (tenant_id, role_id, required_role_id)
);
```

约束与约定：

- 先决条件同样限定在租户内；平台模板场景可通过 `tenant_id IS NULL` 表示平台层。
- 在授权阶段，若目标主体在相应 `scope_type/scope_id`（或更宽 scope，如 PLATFORM 对 TENANT）下不满足 required 角色，则拒绝授权并返回错误码（例如 `ROLE_CONFLICT_PREREQUISITE_MISSING`）。

### 2.4 `role_cardinality`（基数限制）

用途：限制某角色在给定作用域内的最大赋权数量，例如“每个租户只能有 3 个租户管理员”。

```sql
CREATE TABLE role_cardinality (
  id              BIGINT       PRIMARY KEY AUTO_INCREMENT,
  tenant_id       BIGINT       NOT NULL,
  role_id         BIGINT       NOT NULL,
  scope_type      VARCHAR(16)  NOT NULL COMMENT 'PLATFORM/TENANT/ORG/DEPT 等，Phase2 初期建议只支持 PLATFORM/TENANT',
  max_assignments INT          NOT NULL,
  created_at      DATETIME     NOT NULL,
  created_by      BIGINT       NULL,
  updated_at      DATETIME     NOT NULL,
  CONSTRAINT uk_role_cardinality UNIQUE (tenant_id, role_id, scope_type)
  -- 如目标 MySQL 版本支持，可补充：
  -- ,CONSTRAINT chk_role_cardinality_positive CHECK (max_assignments > 0)
);
```

约束与约定：

- Phase2 初期建议只支持 `scope_type IN ('PLATFORM','TENANT')`，组织/部门留待 Scope 阶段扩展。
- `max_assignments` 必须为正整数：
  - 数据库若不支持 CHECK，由 Service 或 Repository 在保存前强制校验 `max_assignments > 0`；
  - 不允许使用 0 或负值表示“无限制”或“禁用”。
- 授权阶段需基于 `role_assignment` 当前 ACTIVE 记录计数，若新增后将超过 `max_assignments`，应拒绝操作（例如错误码 `ROLE_CONFLICT_CARDINALITY_EXCEEDED`）。

---

## 3. 授权写入校验链路设计

### 3.1 校验入口：RoleConstraintService（新）

新增服务接口（示意）：

```java
public interface RoleConstraintService {

    void validateAssignmentsBeforeGrant(
        String principalType,
        Long principalId,
        Long tenantId,
        String scopeType,
        Long scopeId,
        List<Long> roleIdsToGrant
    );
}
```

职责：

1. 把 `role_hierarchy` 展开为有效角色集合（用于冲突检测和后续 Data Scope）。
2. 检查 `role_mutex`：本次新增角色与当前已授予角色之间是否存在互斥。
3. 检查 `role_prerequisite`：本次目标角色是否满足先决条件。
4. 检查 `role_cardinality`：在指定 scope 内授予该角色后，是否超出最大基数。

### 3.2 接入点：role_assignment 写入口

第一批接入：

- `RoleAssignmentSyncService.replaceUserTenantRoleAssignments`
- 后续新增的显式“授予角色” API（如 `RoleAssignmentController.grantToUserInTenant`）。

调用约定：

1. 在真正写入或替换 `role_assignment` 前，先调用 `RoleConstraintService.validateAssignmentsBeforeGrant(...)`。
2. 校验失败必须抛出带错误码的业务异常，不允许静默忽略或只打日志。

---

## 4. 错误码与审计建议

### 4.1 错误码建议

- `ROLE_CONFLICT_MUTEX`：互斥角色冲突。
- `ROLE_CONFLICT_PREREQUISITE_MISSING`：缺少先决角色。
- `ROLE_CONFLICT_CARDINALITY_EXCEEDED`：超过基数限制。

### 4.2 审计字段建议

- 主审计表（如现有授权审计表）中至少记录：
  - `principal_type/principal_id`
  - `tenant_id`
  - `scope_type/scope_id`
  - `role_id`（及从层级展开得到的有效角色集合）
  - 是否命中互斥/先决/基数限制及对应规则 ID。

---

## 5. 实施拆解建议

1. **DDL 与 Liquibase**：新增上述四张表的 changelog，先行落库但不接入运行逻辑（仅允许平台控制面管理）。
2. **管理接口与 UI（可选）**：增补简单的 CRUD 页面／接口，供平台管理员维护角色继承/互斥/先决/基数规则。
3. **RoleConstraintService 实现**：实现内存/SQL 组合校验逻辑，并添加单元测试覆盖典型冲突场景。
4. **与 RoleAssignmentSyncService 集成**：在写 `role_assignment` 前接入校验，先对平台和租户管理员等高风险角色启用，再逐步扩大覆盖范围。
5. **后续扩展（组织/部门 Scope 阶段）**：在 `scope_type` 扩展 `ORG/DEPT` 后，对 `role_cardinality` 和 `role_prerequisite` 的校验粒度按组织/部门维度细化。

---

## 6. 与现有文档和规则的对应关系

- 对应 `TINY_PLATFORM_AUTHORIZATION_MODEL.md`：
  - §4.2「目标模型组件」中的角色治理部分；
  - §8.4「RBAC3 约束」和 §11.3「对后续实现的硬约束」。
- 对应 `.agent/src/rules/93-tiny-platform-authorization-model.rules.md`：
  - “角色继承、互斥、先决条件、基数限制必须在授权时校验”的 Must 级规则。

本设计作为 Phase2 技术基线，不改变 Phase1 的运行契约，只为后续实施提供统一 schema 与职责划分。

---

## 7. 当前阶段进度小结（Phase2 准备状态）

截至本迭代，Phase2 / RBAC3 的准备工作进展如下：

- **模型与契约**
  - 在 `TINY_PLATFORM_AUTHORIZATION_MODEL.md` §5.9 中，已写死 RBAC3 语义、检查顺序和错误/审计约束。
  - 本文档第 2~3 章补充了 `role_hierarchy` / `role_mutex` / `role_prerequisite` / `role_cardinality` 的字段级设计与校验链路。

- **迁移与 schema 草案**
  - 已在 `tiny-oauth-server/src/main/resources/db/changelog/` 下起草（但未接入 `db.changelog-master.yaml`）：
    - `051-role-hierarchy.yaml`
    - `052-role-mutex.yaml`
    - `053-role-prerequisite.yaml`
    - `054-role-cardinality.yaml`
  - 这些 changelog 仅作为 Phase2 的候选迁移脚本，当前任何环境均不会执行。

- **服务接口与调用入口**
  - 新增 `RoleConstraintService` 接口与 `RoleConstraintServiceImpl` 空实现，定义统一校验入口：
    - `validateAssignmentsBeforeGrant(principalType, principalId, tenantId, scopeType, scopeId, roleIdsToGrant)`
  - 在 `RoleServiceImpl.update` / `updateRoleUsers` 中预留了对 `RoleConstraintService` 的调用 hook，当前实现传入空角色集合，属于 no-op，不改变现有授权行为。

> 结论：Phase2 / RBAC3 目前处于“设计 + schema 草案 + 接口 + hook 就绪”状态，尚未对运行时授权行为产生影响。后续若要正式启用，需要：
> 1）在评审通过后将相应 changelog include 进 `db.changelog-master.yaml`；  
> 2）在 `RoleConstraintServiceImpl` 中按第 3 章设计补齐互斥/先决条件/基数检查逻辑；  
> 3）为首批启用租户编写数据审计脚本和集成测试矩阵。
