# Tiny Platform 权限文档阅读入口图

> 状态：阅读入口 / 文档职责索引  
> 目的：降低“文档入口过多、历史文档显眼、读错真相源”的风险。  
> 结论先行：**当前权限主线文档整体方向一致，不建议合并成一个大文档；应以本文收束入口，并把历史文档视为参考而不是当前真相。**

---

## 1. 先看什么

如果你现在要进入 tiny-platform 权限模型，请按下面顺序阅读：

1. [TINY_PLATFORM_AUTHORIZATION_MODEL.md](./TINY_PLATFORM_AUTHORIZATION_MODEL.md)  
   先理解总体模型、术语、边界和长期约束。
2. [TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md](./TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md)  
   再确认当前真实完成度、哪些已落地、哪些还没闭合。  
   **这是“当前状态/完成度”的唯一真相源。**
3. [TINY_PLATFORM_AUTHORIZATION_LAYERED_MODEL.md](./TINY_PLATFORM_AUTHORIZATION_LAYERED_MODEL.md)  
   需要理解“功能权限 / 载体层 / 数据权限”怎么分层时再看。
4. 按专题再继续：
   - `api_endpoint` 统一守卫覆盖： [TINY_PLATFORM_API_ENDPOINT_GUARD_COVERAGE.md](./TINY_PLATFORM_API_ENDPOINT_GUARD_COVERAGE.md)
   - Session / Bearer 认证来源、active scope 成对解析与冲突处理： [TINY_PLATFORM_SESSION_BEARER_AUTH_MATRIX.md](./TINY_PLATFORM_SESSION_BEARER_AUTH_MATRIX.md)
   - `@DataScope` 扩面实施 + 前端活动租户边界 + 构建卫生台账： [TINY_PLATFORM_DATASCOPE_EXPANSION_GUIDE.md](./TINY_PLATFORM_DATASCOPE_EXPANSION_GUIDE.md)（§10–§11）；全量构建技术债： [TINY_PLATFORM_BUILD_TECH_DEBT_LEDGER.md](./TINY_PLATFORM_BUILD_TECH_DEBT_LEDGER.md)
   - 平台模板「重建/回退」正式边界（契约 B）： [TINY_PLATFORM_TENANT_GOVERNANCE.md](./TINY_PLATFORM_TENANT_GOVERNANCE.md) §3.2
   - dev-bootstrap 前置条件 / exit 码、Maven 并发规避： [TINY_PLATFORM_TESTING_PLAYBOOK.md](./TINY_PLATFORM_TESTING_PLAYBOOK.md) §1.2–§1.3；构建技术债分级： [TINY_PLATFORM_BUILD_TECH_DEBT_LEDGER.md](./TINY_PLATFORM_BUILD_TECH_DEBT_LEDGER.md) §0
   - 组织/数据权限/划拨一体化目标： [TINY_PLATFORM_RBAC3_ORG_DATASCOPE_ALLOCATION_ER_MODEL.md](./TINY_PLATFORM_RBAC3_ORG_DATASCOPE_ALLOCATION_ER_MODEL.md)
   - 后续路线图： [TINY_PLATFORM_AUTHORIZATION_NEXT_PHASE_AND_IMPROVEMENTS.md](./TINY_PLATFORM_AUTHORIZATION_NEXT_PHASE_AND_IMPROVEMENTS.md)
   - 文档当前态漂移启发式守卫（非硬失败门禁）：`bash tiny-oauth-server/scripts/verify-authorization-doc-current-state-drift.sh`

---

## 2. 每份文档负责什么

| 你要回答的问题 | 先看哪份文档 | 说明 |
| --- | --- | --- |
| 当前权限模型到底是什么？ | [TINY_PLATFORM_AUTHORIZATION_MODEL.md](./TINY_PLATFORM_AUTHORIZATION_MODEL.md) | 总设计、术语、边界、目标态 |
| 现在到底做到了哪一步？ | [TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md](./TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md) | 当前完成度唯一真相源 |
| 功能权限、载体层、数据权限怎么分层？ | [TINY_PLATFORM_AUTHORIZATION_LAYERED_MODEL.md](./TINY_PLATFORM_AUTHORIZATION_LAYERED_MODEL.md) | 分层总图与过渡口径 |
| 当前请求到底信 Session 还是 Bearer？`activeScopeType/id` 从哪里取？ | [TINY_PLATFORM_SESSION_BEARER_AUTH_MATRIX.md](./TINY_PLATFORM_SESSION_BEARER_AUTH_MATRIX.md) | 认证主体来源、active scope 成对解析、冲突 fail-closed；**user 端点**上 **M4 读** vs **M4 写** 分口径见 **§8**（`GET /sys/users/current` vs `POST /sys/users/current/active-scope`） |
| `api_endpoint` 统一守卫到底覆盖了哪些接口？ | [TINY_PLATFORM_API_ENDPOINT_GUARD_COVERAGE.md](./TINY_PLATFORM_API_ENDPOINT_GUARD_COVERAGE.md) | 覆盖清单与证据等级 |
| 某模块能不能接 `@DataScope`、怎么接？ | [TINY_PLATFORM_DATASCOPE_EXPANSION_GUIDE.md](./TINY_PLATFORM_DATASCOPE_EXPANSION_GUIDE.md) | 实施指南，不维护完成度 |
| ORG / DataScope / 数据归属 / 机构划拨目标态怎么定？ | [TINY_PLATFORM_RBAC3_ORG_DATASCOPE_ALLOCATION_ER_MODEL.md](./TINY_PLATFORM_RBAC3_ORG_DATASCOPE_ALLOCATION_ER_MODEL.md) | 一体化定型稿 |
| 后面还想继续怎么演进？ | [TINY_PLATFORM_AUTHORIZATION_NEXT_PHASE_AND_IMPROVEMENTS.md](./TINY_PLATFORM_AUTHORIZATION_NEXT_PHASE_AND_IMPROVEMENTS.md) | 路线图与改进池 |
| 历史兼容还剩什么、为什么还留着？ | [TINY_PLATFORM_LEGACY_COMPATIBILITY_INVENTORY.md](./TINY_PLATFORM_LEGACY_COMPATIBILITY_INVENTORY.md) | 兼容台账 |

---

## 3. 当前真相源 vs 参考文档

### 3.1 当前真相源

以下文档可以视为当前主线：

- [TINY_PLATFORM_AUTHORIZATION_MODEL.md](./TINY_PLATFORM_AUTHORIZATION_MODEL.md)
- [TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md](./TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md)
- [TINY_PLATFORM_AUTHORIZATION_LAYERED_MODEL.md](./TINY_PLATFORM_AUTHORIZATION_LAYERED_MODEL.md)
- [TINY_PLATFORM_SESSION_BEARER_AUTH_MATRIX.md](./TINY_PLATFORM_SESSION_BEARER_AUTH_MATRIX.md)
- [TINY_PLATFORM_API_ENDPOINT_GUARD_COVERAGE.md](./TINY_PLATFORM_API_ENDPOINT_GUARD_COVERAGE.md)
- [TINY_PLATFORM_RBAC3_ORG_DATASCOPE_ALLOCATION_ER_MODEL.md](./TINY_PLATFORM_RBAC3_ORG_DATASCOPE_ALLOCATION_ER_MODEL.md)
- [TINY_PLATFORM_DATASCOPE_EXPANSION_GUIDE.md](./TINY_PLATFORM_DATASCOPE_EXPANSION_GUIDE.md)

其中职责必须这样理解：

- **模型与边界**：`AUTHORIZATION_MODEL`
- **当前完成度**：`AUTHORIZATION_TASK_LIST`
- **分层口径**：`AUTHORIZATION_LAYERED_MODEL`
- **认证来源 / 冲突处理矩阵**：`SESSION_BEARER_AUTH_MATRIX`
- **`api_endpoint` 证据等级**：`API_ENDPOINT_GUARD_COVERAGE`
- **组织/数据权限/划拨定型**：`RBAC3_ORG_DATASCOPE_ALLOCATION_ER_MODEL`
- **`@DataScope` 接入方法**：`DATASCOPE_EXPANSION_GUIDE`

### 3.2 路线图 / 专题

以下文档仍然有用，但不是“当前完成度真相源”：

- [TINY_PLATFORM_AUTHORIZATION_NEXT_PHASE_AND_IMPROVEMENTS.md](./TINY_PLATFORM_AUTHORIZATION_NEXT_PHASE_AND_IMPROVEMENTS.md)
- [TINY_PLATFORM_TENANT_GOVERNANCE.md](./TINY_PLATFORM_TENANT_GOVERNANCE.md)
- [TINY_PLATFORM_RBAC3_ENFORCE_ROLLOUT_SOP.md](./TINY_PLATFORM_RBAC3_ENFORCE_ROLLOUT_SOP.md)

### 3.3 历史 / 阶段设计 / 归档参考

以下文档 / SQL 主要用于回溯、迁移背景或阶段设计，不应直接当作“当前运行态真相”：

- [TINY_PLATFORM_AUTHORIZATION_PHASE1_TECHNICAL_DESIGN.md](./TINY_PLATFORM_AUTHORIZATION_PHASE1_TECHNICAL_DESIGN.md)
- [TINY_PLATFORM_AUTHORIZATION_PHASE2_RBAC3_TECHNICAL_DESIGN.md](./TINY_PLATFORM_AUTHORIZATION_PHASE2_RBAC3_TECHNICAL_DESIGN.md)
- [TINY_PLATFORM_PERMISSION_REFACTOR_FINAL_APPROVAL.md](./TINY_PLATFORM_PERMISSION_REFACTOR_FINAL_APPROVAL.md)
- [PERMISSION_REFACTOR_COMPATIBILITY_LAYER_ASSESSMENT.md](./PERMISSION_REFACTOR_COMPATIBILITY_LAYER_ASSESSMENT.md)
- [TINY_PLATFORM_AUTHORIZATION_LEGACY_REMOVAL_PLAN.md](./TINY_PLATFORM_AUTHORIZATION_LEGACY_REMOVAL_PLAN.md)
- [TINY_PLATFORM_MODULE_GAP_ANALYSIS.md](./TINY_PLATFORM_MODULE_GAP_ANALYSIS.md)
- [tiny-platform-saas-overall-design.md](./tiny-platform-saas-overall-design.md)
- 历史 schema / 参考 SQL：`tiny-oauth-server/src/main/resources/schema.sql`、`tiny-oauth-server/src/main/resources/menu_resource_data.sql`

阅读这些历史文档或历史 SQL 时，必须以主线文档校正：

- 当前主授权链：以 [TINY_PLATFORM_AUTHORIZATION_MODEL.md](./TINY_PLATFORM_AUTHORIZATION_MODEL.md) 为准
- 当前完成度：以 [TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md](./TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md) 为准
- 当前 `api_endpoint` 守卫覆盖等级：以 [TINY_PLATFORM_API_ENDPOINT_GUARD_COVERAGE.md](./TINY_PLATFORM_API_ENDPOINT_GUARD_COVERAGE.md) 为准

---

## 4. 发现冲突时怎么裁决

若不同文档表述不一致，按下面顺序裁决：

1. **当前完成度 / 是否已落地**  
   以 [TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md](./TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md) 为准。
2. **总体模型、术语、边界**  
   以 [TINY_PLATFORM_AUTHORIZATION_MODEL.md](./TINY_PLATFORM_AUTHORIZATION_MODEL.md) 为准。
3. **载体层 / requirement / 数据权限分层口径**  
   以 [TINY_PLATFORM_AUTHORIZATION_LAYERED_MODEL.md](./TINY_PLATFORM_AUTHORIZATION_LAYERED_MODEL.md) 为准。
4. **Session / Bearer 认证来源、active scope 成对解析与冲突处理**  
   以 [TINY_PLATFORM_SESSION_BEARER_AUTH_MATRIX.md](./TINY_PLATFORM_SESSION_BEARER_AUTH_MATRIX.md) 为准。
5. **`api_endpoint` 统一守卫覆盖与证据等级**  
   以 [TINY_PLATFORM_API_ENDPOINT_GUARD_COVERAGE.md](./TINY_PLATFORM_API_ENDPOINT_GUARD_COVERAGE.md) 为准。
6. **组织权限 + 数据权限 + 数据划拨一体化目标态**  
   以 [TINY_PLATFORM_RBAC3_ORG_DATASCOPE_ALLOCATION_ER_MODEL.md](./TINY_PLATFORM_RBAC3_ORG_DATASCOPE_ALLOCATION_ER_MODEL.md) 为准。

---

## 5. 当前审计结论

基于当前仓库文档交叉审计，现阶段更准确的判断是：

- **主线文档方向基本一致**，没有发现“两个当前态真相源互相打架”的高风险冲突。
- 当前主要问题不是“必须合并成一个大文档”，而是**入口偏多、历史文档仍显眼**。
- 因此当前更合理的策略是：
  - 保留主线文档分工；
  - 用本文收束入口；
  - 后续再逐步给历史文档降噪或归档。

一句话：

**现在不需要把权限模型文档全并成一个大文档；更需要的是统一入口、明确职责、弱化历史文档对当前态判断的干扰。**

---

## 6. 术语字典（固定口径）

为降低后续多人维护时的文案漂移风险，权限主线文档中的状态词建议固定按下面理解：

- **已落地**  
  结构、代码、迁移或基础运行时能力已经存在并可运行；  
  但**不代表**跨模块证据、统一消费或当前阶段闭环已经完成。

- **已闭合**  
  除“已落地”外，已经具备当前阶段要求的：
  - 运行时消费；
  - 证据链；
  - 文档口径一致性。  
  可以作为当前阶段的完成结论使用。

- **待闭合**  
  已部分落地，但仍有明确缺口；  
  典型缺口包括：
  - 覆盖证据不足；
  - 运行时统一消费未完成；
  - 兼容层未收口；
  - 治理脚本 / rollout / 门禁未对齐。  
  在这些缺口消除前，**不能宣称该项已完成**。
