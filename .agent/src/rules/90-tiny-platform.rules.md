# 90 tiny-platform 平台特定规则（最高优先级）

## 适用范围

- 适用于：tiny-platform 全仓库

## 禁止（Must Not）

- ❌ 弱化安全/权限/多租户边界（任何"先放开再说"的临时方案必须被禁止）。
- ❌ 推翻既有模块边界做无收益的"架构大改"。
- ❌ 查询业务数据时缺少 `tenant_id` 过滤（导致跨租户数据泄露）。
- ❌ 在业务表中创建不包含 `tenant_id` 的唯一性约束（应使用 `tenant_id + 业务字段` 联合唯一）。

## 必须（Must）

- ✅ 平台特定规则优先级最高：与通用规则冲突时按裁决原则处理。
- ✅ 涉及认证授权/租户隔离：必须明确数据隔离维度与权限决策链路。
- ✅ 多租户数据隔离：所有业务表必须包含 `tenant_id` 字段；所有业务查询必须包含 `tenant_id` 过滤条件。
- ✅ 租户 ID 传递：从请求头（`X-Tenant-Id`）或 Token Claims（`tenant_id`）获取，存入 `SecurityContext` 或 `ThreadLocal`。
- ✅ 租户级别唯一性：使用 `UNIQUE KEY uk_表名_tenant_id_业务字段 (tenant_id, 业务字段)`。

## 应该（Should）

- ⚠️ 新增平台能力先给最小可运行版本（MVP），再逐步扩展。
- ⚠️ 租户级别资源限制：按租户设置配额（如用户数、存储空间、API 调用次数）。
- ⚠️ 租户数据迁移：提供租户数据导出/导入工具，支持租户间数据迁移。
- ⚠️ 插件化架构：模块边界清晰（auth/dict/workflow/plugin/tenant），接口定义明确。

## 例外与裁决

- 系统表：系统表（如 tenant、system_config）可不包含 tenant_id，但必须明确标注。
- 第三方集成：第三方系统集成可暂时放宽多租户隔离，但必须明确标注风险并制定迁移计划。
- 冲突时：本规范优先级最高，任何规则冲突时平台特定规则优先。

## 示例

### ✅ 正例

```java
// 查询时包含 tenant_id 过滤
@Service
public class UserService {
    public List<User> getUsers() {
        Long tenantId = TenantContext.getCurrentTenantId();
        return userRepository.findByTenantId(tenantId); // ✅ 包含 tenant_id 过滤
    }
}

// 租户级别唯一性约束
CREATE TABLE `user` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `username` VARCHAR(50) NOT NULL,
  UNIQUE KEY `uk_user_tenant_username` (`tenant_id`, `username`) -- ✅ 租户级别唯一
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### ❌ 反例

```java
// 错误：查询缺少 tenant_id 过滤、唯一性约束不包含 tenant_id
@Service
public class UserService {
    public List<User> getUsers() {
        return userRepository.findAll(); // ❌ 缺少 tenant_id 过滤，可能泄露跨租户数据
    }
}

CREATE TABLE `user` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT NOT NULL,
  `username` VARCHAR(50) NOT NULL,
  UNIQUE KEY `uk_user_username` (`username`) -- ❌ 唯一性约束不包含 tenant_id，不同租户无法使用相同用户名
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```