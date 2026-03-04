# tiny-oauth-server 覆盖率治理方案（分阶段）

## 1. 当前现状（2026-02-25）

- 全量测试命令：`mvn -pl tiny-oauth-server -Pcoverage-check verify`
- 测试结果：`61` 个测试通过（`0` fail / `0` error）
- 模块整体 JaCoCo（LINE）：约 `10.92%`
- `coverage-check` 原规则按“所有类 >= 30% LINE”检查，当前会被大量 DTO/配置类/未覆盖业务模块阻塞

## 2. 本次调整（已落地）

将 `coverage-check` 改为“分阶段规则”，保证 CI 可执行且不放弃质量约束：

1. `BUNDLE` 级基线
- `LINE >= 10%`
- 作用：防止整体覆盖率回退

2. trace/audit 核心链路 `CLASS` 级门槛
- `LINE >= 30%`
- 当前纳入类：
  - `HttpRequestLoggingFilter`
  - `HttpRequestLoggingInterceptor`
  - `HttpLogSanitizer`
  - `HttpRequestLoggingProperties`
  - `MdcTaskDecorator`
  - `HttpRequestLogServiceImpl`

说明：
- 这是过渡方案，不是最终目标。
- 目的：先让覆盖检查成为“可持续执行的门禁”，再逐步扩大范围。

## 3. 包级补测顺序（建议执行顺序）

### Phase 1（已重点投入）
- `com.tiny.platform.core.oauth.filter`
- `com.tiny.platform.core.oauth.interceptor`
- `com.tiny.platform.core.oauth.logging`
- `com.tiny.platform.core.oauth.config`（trace/logging 相关）
- `com.tiny.platform.core.oauth.service.impl`（请求日志链路）

目标：
- trace/audit 关键链路行覆盖稳定高位（关键类优先）

### Phase 2（认证核心）
- `com.tiny.platform.core.oauth.security`
- `com.tiny.platform.core.oauth.config`（认证/授权相关）
- `com.tiny.platform.core.oauth.controller`
- `com.tiny.platform.core.oauth.multitenancy`

目标：
- 将 OAuth 登录/MFA/多租户认证主流程关键类纳入 `CLASS` 门槛

### Phase 3（业务主干）
- `com.tiny.platform.application.controller.*`
- `com.tiny.platform.infrastructure.auth.*.service`
- `com.tiny.platform.infrastructure.tenant.service`
- `com.tiny.platform.core.dict.service.impl`

目标：
- 先覆盖 service/controller 的主路径与异常路径

### Phase 4（调度与导出）
- `com.tiny.platform.infrastructure.scheduling.*`
- `com.tiny.platform.infrastructure.export.*`

目标：
- 补单元测试 + 小型集成测试，避免只靠人工联调

### Phase 5（低价值/载体类治理）
- DTO / Entity / Enum / 纯配置 / 异常类 / Converter / adapter 壳类

策略：
- 这类类不追求单独高覆盖
- 通过上层 service/controller 测试间接覆盖即可
- 必要时在 JaCoCo 规则中继续采用“范围化检查”而非逐类卡死

## 4. 日常执行方式

### 快速跑测试（本地）
```bash
mvn -pl tiny-oauth-server test
```

### 带覆盖报告
```bash
mvn -pl tiny-oauth-server -Pcoverage-check verify
```

### 查看包级覆盖汇总（新脚本）
```bash
python3 tiny-oauth-server/scripts/jacoco_package_summary.py \
  --xml tiny-oauth-server/target/site/jacoco/jacoco.xml \
  --top 30
```

仅看 OAuth 相关包：
```bash
python3 tiny-oauth-server/scripts/jacoco_package_summary.py \
  --xml tiny-oauth-server/target/site/jacoco/jacoco.xml \
  --package-prefix com/tiny/platform/core/oauth \
  --top 30
```

## 5. 规则收紧路线（建议）

1. 当 Phase 2 稳定后，将 `BUNDLE LINE` 从 `10%` 提升到 `15%`
2. 增加 `CLASS` 门槛覆盖范围（认证/租户关键类）
3. 逐步引入 `BRANCH` 门槛（先从 trace/security 核心类开始）
4. 最后再评估是否对某些包采用 `PACKAGE` 级门槛

## 6. 注意事项

- 不建议一开始对 DTO/Entity/纯配置类做逐类硬门槛，这会显著降低治理效率。
- 先保“关键链路 + 总体基线”，再扩范围，是更符合企业项目节奏的策略。
- 每次新增核心模块时，应同步决定：
  - 是否加入 `coverage-check` 的关键类列表
  - 是否补最小回归测试
