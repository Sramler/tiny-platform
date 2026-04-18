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
- ✅ 新增权限标识、权限字典、菜单资源权限、`hasAuthority` / `@PreAuthorize` 命名必须遵循 `92-tiny-platform-permission.rules.md`，不得继续扩散历史别名风格。
- ✅ 涉及认证授权/租户隔离：必须明确数据隔离维度与权限决策链路。
- ✅ 多租户数据隔离：所有业务表必须包含 `tenant_id` 字段；所有业务查询必须包含 `tenant_id` 过滤条件。
- ✅ 租户 ID 传递：从请求头（`X-Tenant-Id`）或 Token Claims（`tenant_id`）获取，存入 `SecurityContext` 或 `ThreadLocal`。
- ✅ 租户级别唯一性：使用 `UNIQUE KEY uk_表名_tenant_id_业务字段 (tenant_id, 业务字段)`。
- ✅ 涉及认证、权限、租户隔离、插件边界、数据库迁移的改动，必须提供自动化回归验证，至少覆盖允许路径、拒绝路径和跨租户误用路径。
- ✅ 涉及 `tenant_id` 过滤、租户级唯一约束、权限判定缺陷的修复，必须补回归测试，防止再次出现越权或串租户。
- ✅ 涉及前端主入口路由 path/name 收口时，必须同步处理兼容 alias、菜单/跳转影响与全局硬编码引用核查；不得只改页面标题或单一路由声明后宣称已完成语义收口。
- ✅ `/platform/**` 控制面不得默认把 `/sys/**` 或 tenant-only 控制面接口当作核心读写主链；若平台侧缺最小 lookup / query / action 能力，必须优先补平台语义下的最小接口，而不是继续借道租户/系统控制面。
- ✅ `/platform/**` 页面、接口与前端权限守卫不得把 `system:*` 权限作为本域核心能力的必备前置；若存在历史桥接，必须明确标注为临时方案，并给出退出条件与后续收口任务。
- ✅ 允许复用 service / repository / domain 逻辑，但 controller path、permission、menu、route、前端 API contract 与 scope guard 必须保持平台/租户分流；涉及平台路径时，`tenant_id IS NULL` 必须被显式建模或校验，禁止把 `NULL tenantId` 直接塞进 tenant-only 查询宣称完成。
- ✅ 平台治理任务卡若触及 `db/changelog/**`、`db.changelog-master.yaml`、`api_endpoint` / `menu_permission_requirement` / `role_permission` 回填、权限/菜单 seed 或 DDL，完成条件必须包含一次真实 `SpringLiquibase` / 应用启动验证；只跑单测、组件测试或静态检查不得标记完成。
- ✅ 平台治理任务卡交付必须显式说明“本卡负责什么、不负责什么”，尤其统一守卫回填、菜单回填、Liquibase include、real-controller 测试、启动验证这些责任不得默认外推到下一张卡或留给用户首次启动时发现。
- ✅ tiny-platform 本地 AI 验证默认应先执行 `tiny-oauth-server/scripts/verify-platform-local-dev-stack.sh`；只有在明确不需要前端联动时，才降级使用 `verify-platform-dev-bootstrap.sh`，并且不得把脚本 `exit 2` 记成代码失败。
- ✅ 凡仓库内已有脚本、编译命令、数据库查询或自动化测试能够直接产出结论，助手必须优先**自行执行并报告结果**；不能把“请用户先手工启动数据库/前端/后端、手工查库、手工跑脚本”作为默认工作流。
- ✅ 只有在环境前置缺失、权限不足、存在明确破坏性风险，或仓库内确实没有可自动化路径时，助手才可以请求用户介入；此时必须明确指出缺的前置条件，而不是笼统地让用户“先操作一下再说”。
- ✅ 涉及调度、工作流、任务编排、统计 DAG 等编排型能力时，测试必须覆盖拓扑语义而不仅是接口可用性：至少包含并行分叉后归并、串行推进、失败后重试/取消/暂停恢复中的相关场景。
- ✅ 调度/工作流前端测试必须区分 DAG 级、run 级、node 级操作语义，禁止把全局操作误测为单次运行操作，或把单次运行操作误测为全局操作。
- ✅ 涉及平台控制面主链的测试，除正向通过外，还必须补至少一条反向断言：确认未调用 `/sys/**` 旧入口、未依赖 `system:*` 权限、tenant scope 下会明确阻断，而不是静默回落到租户/系统控制面。
- ✅ 真实认证链路、MFA、租户上下文、run 级/节点级调度操作的 E2E 必须优先使用专用自动化身份和专用测试租户，不允许用开发者个人账号或伪造权限状态替代。
- ✅ tiny-platform 的 E2E 必须按环境等级明确分类，至少区分：本地 mock-assisted UI、隔离环境 real-link、共享环境 smoke、Nightly/full-chain；不同等级的断言、seed/reset、允许的 mock 边界必须清楚说明。
- ✅ 当测试目标涉及 Session/JWT 切换、OIDC、MFA、租户上下文、真实数据库约束、插件装载、调度编排等平台能力时，必须保留至少一条 real-link E2E，不得全部退化为 mock-assisted 测试。
- ✅ 平台级 E2E 必须优先验证最终业务结果与边界安全：权限拒绝、跨租户拒绝、审计留痕、状态收敛、插件生效/失效，而不是只验证请求成功。
- ✅ first-party 业务 API 在 real-link E2E 中不得再被 `page.route()` 或等价手段整体替代；确需隔离第三方依赖时，必须明确限定 mock 边界并说明原因。
- ✅ 平台级 real-link CI 一旦引入第二租户、第二身份、首绑用户或其它动态创建的测试主体，必须在 fresh DB 上验证“身份/租户 bootstrap -> 真实登录 -> 业务回归”的完整链路，不能只依赖本地已预热数据库。
- ✅ 多租户 OIDC / OAuth2 / 调度等平台能力在测试环境中如果允许 late-created tenant 或运行时补创建租户，必须补回归测试或兼容策略，确保不会因为启动时 registry、cache 或 delegate 尚未预注册而直接阻断真实链路。
- ✅ 共享测试库中的集成测试临时账号清理必须使用“受控前缀白名单 + `_user_` 结构约束”；禁止依赖模糊用户名匹配删除账号，避免误删 Seed/E2E 固定资产。
- ✅ 涉及测试账号命名治理时，必须明确“现状兼容口径（白名单）”与“未来推荐规范（统一模板）”并行策略；新增临时账号前缀时，必须同步更新清理脚本与统计校验脚本。

## 应该（Should）

- ⚠️ 新增平台能力先给最小可运行版本（MVP），再逐步扩展。
- ⚠️ 租户级别资源限制：按租户设置配额（如用户数、存储空间、API 调用次数）。
- ⚠️ 租户数据迁移：提供租户数据导出/导入工具，支持租户间数据迁移。
- ⚠️ 插件化架构：模块边界清晰（auth/dict/workflow/plugin/tenant），接口定义明确。
- ⚠️ 认证方式切换（Session/JWT）、租户切换、插件装载/卸载建议提供端到端或集成冒烟场景。
- ⚠️ 调度模块应保持一套稳定的前后端联动用例矩阵，至少包含：并行归并、串行推进、run 级停止、run 级重试、node 级 trigger/retry/pause/resume、跨租户拒绝。
- ⚠️ 平台级 E2E 建议维护统一用例矩阵，至少覆盖：认证登录、MFA、租户拒绝、权限拒绝、关键写操作、关键只读查询、一个跨模块编排流程。
- ⚠️ Nightly/full-chain E2E 建议复用专用测试租户、专用 OAuth client、固定 seed 脚本和固定环境开关，避免各模块各自发明一套初始化方式。

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
