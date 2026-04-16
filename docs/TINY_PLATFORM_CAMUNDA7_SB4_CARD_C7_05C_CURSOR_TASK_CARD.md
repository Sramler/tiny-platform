# Tiny Platform CARD-C7-05C Cursor 任务卡

> 状态：已执行首批落地，可复用任务卡  
> 适用仓库：`/Users/bliu/code/tiny-platform`  
> 关联 fork 仓库：`/Users/bliu/code/camunda-bpm-platform`  
> 目标：在 GitHub Packages 尚未就绪时，为 `tiny-platform` 的后端 GitHub Actions 提供“checkout Camunda fork 并本地 `mvn install`”的临时兜底路径  
> 非目标：本卡不替代 `CARD-C7-05B` 主路径，不处理固定版 `7.24.0-tiny-sb4-01` 切换，不扩展到所有 nightly workflow

当前执行结果（2026-04-16）：

- 已完成首批 2 条代表性 workflow 落地：
  - `.github/workflows/verify-auth-backend.yml`
  - `.github/workflows/verify-migration-smoke-mysql.yml`
- 已新增本地 action：
  - `.github/actions/install-camunda-fork-local/action.yml`
- `verify-auth-db-residuals.yml` 仍保持 `CARD-C7-05B` 主路径，未纳入本轮 `05C` 首批范围
- 实施报告：
  - `docs/TINY_PLATFORM_CAMUNDA7_SB4_CARD_C7_05C_IMPLEMENTATION_REPORT.md`

---

## 1. 使用方式

把本文件中的“复制给 Cursor 的提示词”整段交给 Cursor，并明确要求：

- 由 Cursor 直接实施；
- Codex 只在结果回传后做审计；
- 不要把本卡扩展成“顺手重写全部 workflow”；
- 优先做最小闭环、最容易回归验证的 fallback；
- 完成后必须返回：
  - 修改文件清单
  - 实际执行命令
  - 影响的 workflow 清单
  - fallback 开关与外部前置说明
  - 验证结果与剩余风险

---

## 2. 必读文档与规则

Cursor 在执行前必须先阅读：

- `AGENTS.md`
- `docs/TINY_PLATFORM_CAMUNDA7_SB4_FORK_TASK_CARDS.md`
- `docs/TINY_PLATFORM_CAMUNDA7_SB4_GITHUB_PACKAGES_RUNBOOK.md`
- `docs/TINY_PLATFORM_CAMUNDA7_SB4_COORDINATE_EXPLICIT_MIGRATION_CARD.md`
- `docs/TINY_PLATFORM_TESTING_PLAYBOOK.md`
- `.github/actions/setup-camunda-fork-java/action.yml`
- `.github/workflows/verify-auth-backend.yml`
- `.github/workflows/verify-migration-smoke-mysql.yml`
- 可选第三条：
  - `.github/workflows/verify-auth-db-residuals.yml`
- `/Users/bliu/code/camunda-bpm-platform/pom.xml`
- `/Users/bliu/code/camunda-bpm-platform/docs/github-packages-publishing.md`
- `.agent/src/rules/50-testing.rules.md`
- `.agent/src/rules/58-cicd.rules.md`
- `.agent/src/rules/60-git.rules.md`
- `.agent/src/rules/77-dependency.rules.md`
- `.agent/src/rules/85-documentation.rules.md`
- `.agent/src/rules/90-tiny-platform.rules.md`

---

## 3. 当前已知事实

执行本卡前，以下结论已经成立，不需要 Cursor 重复分析：

- `CARD-C7-05B` 主路径已经落地：
  - `camunda-bpm-platform/pom.xml` 已增加 GitHub Packages `distributionManagement`
  - `camunda-bpm-platform` 已新增手动发布 workflow
  - `tiny-platform/pom.xml` 已增加 `camunda-github-packages` 受控 profile
  - 多条后端 workflow 已切到 `.github/actions/setup-camunda-fork-java/action.yml`
- 当前风险不在代码编译，而在外部依赖：
  - GitHub Packages 可能尚未完成首轮发布
  - package 读取权限 / token 可能尚未配置
  - 一旦外部前置未满足，后端 workflow 会在依赖解析阶段失败
- `tiny-platform` 与 `camunda-bpm-platform` 的 GitHub remote 都位于：
  - `Sramler/*`
- 当前 fork 工作分支是本地分支：
  - `camunda-bpm-platform`: `codex/camunda7-sb4-fork`
- GitHub Actions 不能读取你本机本地分支状态，因此：
  - fallback 方案必须依赖“远端可 checkout 的 ref”
  - 不能假设 runner 能看到本机未 push 的分支

因此，本卡的目标不是“优化正式仓库分发”，而是：

在 `CARD-C7-05B` 主路径暂未就绪时，给部分关键后端 workflow 增加一个可控的临时兜底路径。

---

## 4. 固定边界

### 4.1 本卡允许改动

- `.github/actions/**`
- `.github/workflows/verify-auth-backend.yml`
- `.github/workflows/verify-migration-smoke-mysql.yml`
- 如实现足够稳定，可追加：
  - `.github/workflows/verify-auth-db-residuals.yml`
- `docs/TINY_PLATFORM_CAMUNDA7_SB4_GITHUB_PACKAGES_RUNBOOK.md`
- `docs/TINY_PLATFORM_CAMUNDA7_SB4_FORK_TASK_CARDS.md`
- 必要时新增：
  - `docs/TINY_PLATFORM_CAMUNDA7_SB4_CARD_C7_05C_IMPLEMENTATION_REPORT.md`

### 4.2 本卡禁止改动

- 不改业务源码
- 不改 `tiny-oauth-server` 运行逻辑
- 不改 `camunda-bpm-platform` 源码
- 不把 fallback 方案作为长期默认主路径
- 不无差别改所有 nightly / webapp / 前端 workflow
- 不读取 `~/.zprofile` / `~/.zshrc` / `~/.bashrc`

### 4.3 推荐实施范围

本卡推荐先只覆盖 2 条代表性 workflow：

1. `.github/workflows/verify-auth-backend.yml`
2. `.github/workflows/verify-migration-smoke-mysql.yml`

理由：

- 一条偏“纯后端测试”
- 一条偏“真实启动 + MySQL + migration smoke”
- 两者都能证明 fallback 路径是否真正可用

只有在实现已经足够抽象、稳定、低重复时，才允许追加：

3. `.github/workflows/verify-auth-db-residuals.yml`

---

## 5. 成功标准

完成本卡后，必须满足：

1. 至少 2 条代表性后端 workflow 支持“checkout fork + 本地 install”兜底
2. fallback 路径必须是显式开关控制，不能默认常开
3. 当 fallback 开关开启时：
   - workflow 必须显式使用跨仓库 checkout 凭证
   - workflow 能 checkout `Sramler/camunda-bpm-platform`
   - 能在 runner 上执行最小 fork 产物安装
   - fork 安装与后续 `tiny-platform` 构建/启动命令使用同一个 `MAVEN_REPO_LOCAL`
4. 当 fallback 开关关闭时：
   - 当前 `CARD-C7-05B` GitHub Packages 主路径行为不受破坏
   - 默认实现不得强行改变 `setup-camunda-fork-java` 主路径的 Maven cache 语义
5. 如果 fallback 开启但缺少远端 ref 或 checkout token：
   - workflow 必须 fail-fast，并给出明确错误说明
6. workflow 中对 repo variable / secret 的读取必须显式归一化，不能把“文档中的默认值”当作 GitHub 自动生效
7. 文档必须明确：
   - 这是临时兜底
   - 远端 ref / repo variable / secret 属于仓库外前置
8. 至少完成：
   - YAML 语法校验
   - 关键 workflow 静态审阅
   - 本地最小 smoke：
     - 先在临时 `MAVEN_REPO_LOCAL` 中完成 fork install
     - 再在同一个 `MAVEN_REPO_LOCAL` 上验证 `tiny-oauth-server` 至少可 `test-compile`
   - 文档同步

---

## 6. 推荐实现方案

### 6.1 统一开关与共享本地仓库

推荐在目标 workflow 中增加以下环境变量：

- `CAMUNDA_FORK_LOCAL_INSTALL_ENABLED`
  - 推荐来源：GitHub repository variable
  - 默认值：`false`
- `CAMUNDA_FORK_REPOSITORY`
  - 固定值：`Sramler/camunda-bpm-platform`
- `CAMUNDA_FORK_REF`
  - 推荐来源：GitHub repository variable
  - 默认值：空字符串
- `CAMUNDA_FORK_CHECKOUT_TOKEN`
  - 推荐来源：GitHub Actions secret
  - 用途：跨仓库 checkout `Sramler/camunda-bpm-platform`
- `MAVEN_REPO_LOCAL`
  - 推荐值：`${{ runner.temp }}/m2-camunda-fork`

关键约束：

- workflow 中必须显式做变量归一化，例如：

```yaml
env:
  CAMUNDA_FORK_LOCAL_INSTALL_ENABLED: ${{ vars.CAMUNDA_FORK_LOCAL_INSTALL_ENABLED || 'false' }}
  CAMUNDA_FORK_REF: ${{ vars.CAMUNDA_FORK_REF || '' }}
  MAVEN_REPO_LOCAL: ${{ runner.temp }}/m2-camunda-fork
```

- fallback 开启时，`CAMUNDA_FORK_REF` 不能为空
- fallback 开启时，`CAMUNDA_FORK_CHECKOUT_TOKEN` 不能为空，除非结果文档明确声明 fork 仓库为公开可读并已人工验证无需该 secret
- 默认做法：
  - 只有 fallback 分支中的 fork install 与后续 `tiny-platform` Maven 命令统一追加 `-Dmaven.repo.local=${MAVEN_REPO_LOCAL}`
  - `CARD-C7-05B` 主路径保持当前默认 Maven cache 语义不变
- 如果实现决定在两条分支都统一 `MAVEN_REPO_LOCAL`，必须在结果文档中单列说明 cache 语义变化与风险

### 6.2 推荐新增本地 action

推荐新增：

- `.github/actions/install-camunda-fork-local/action.yml`

作用：

- 只负责在“fork 已 checkout 到某个 path 后”执行本地 install
- 不负责业务仓库构建
- 不直接处理 GitHub Packages 认证

推荐输入：

- `fork-path`
- `maven-repo-local`

推荐实现骨架：

```bash
cd "${FORK_PATH}"
mvn -B \
  -pl spin/core \
  -am \
  -Dmaven.repo.local="${MAVEN_REPO_LOCAL}" \
  -DskipTests \
  -DskipITs \
  install

mvn -B \
  -pl bom/camunda-bom,bom/camunda-only-bom,spin/bom,commons/bom,connect/bom,spin/dataformat-all,spring-boot-starter/starter,spring-boot-starter/starter-rest \
  -am \
  -Dmaven.repo.local="${MAVEN_REPO_LOCAL}" \
  -Dmaven.test.skip=true \
  -DskipTests \
  -DskipITs \
  install
```

说明：

- 固定版 `7.24.0-tiny-sb4-01` 需要先安装 `spin/core`，以便产出
  `org.camunda.spin:camunda-spin-core:tests`
- 主安装阶段仍应保留 `-Dmaven.test.skip=true`，避免 `starter` 的 Boot 4
  兼容测试源码阻塞 `testCompile`
- 本卡必须安装：
  - `camunda-bom`
  - `camunda-only-bom`
  - 二级 BOM
  - `camunda-spin-dataformat-all`
  - `starter`
  - `starter-rest`
- 不允许只装 `camunda-bom`

### 6.3 workflow 推荐控制流

对目标 workflow，推荐采用以下控制流：

1. `checkout tiny-platform`
2. 根据 `CAMUNDA_FORK_LOCAL_INSTALL_ENABLED` 分流：
   - `false`：
     - 继续走 `setup-camunda-fork-java` 主路径
     - 默认不改变当前 `actions/setup-java` 的 Maven cache 行为
   - `true`：
     - 用普通 `actions/setup-java@v5` 安装 JDK
     - fail-fast 校验 `CAMUNDA_FORK_REF`
     - fail-fast 校验 `CAMUNDA_FORK_CHECKOUT_TOKEN`
     - 用 `actions/checkout` + 显式 `token` checkout `Sramler/camunda-bpm-platform` 到子目录
     - 执行 `install-camunda-fork-local` action
3. 仅在 fallback 分支中，后续所有 `tiny-platform` Maven 命令统一追加：
   - `-Dmaven.repo.local=${MAVEN_REPO_LOCAL}`
4. 如果实现者希望主路径也统一 `MAVEN_REPO_LOCAL`：
   - 必须在实现报告中单列说明
   - 必须明确这会改变 `CARD-C7-05B` 当前 cache 行为

### 6.4 推荐首批改造文件

第一批必须做：

- `.github/workflows/verify-auth-backend.yml`
- `.github/workflows/verify-migration-smoke-mysql.yml`
- `.github/actions/install-camunda-fork-local/action.yml`
- `docs/TINY_PLATFORM_CAMUNDA7_SB4_GITHUB_PACKAGES_RUNBOOK.md`
- `docs/TINY_PLATFORM_CAMUNDA7_SB4_FORK_TASK_CARDS.md`

只有在第一批足够稳定时，才允许追加：

- `.github/workflows/verify-auth-db-residuals.yml`

---

## 7. 推荐执行顺序

按以下顺序执行，不要并行扩 scope：

1. `CARD-C7-05C-A` 明确 fallback 开关、远端 ref 与共享 `MAVEN_REPO_LOCAL`
2. `CARD-C7-05C-B` 新增 `install-camunda-fork-local` action
3. `CARD-C7-05C-C` 接入 `verify-auth-backend.yml`
4. `CARD-C7-05C-D` 接入 `verify-migration-smoke-mysql.yml`
5. `CARD-C7-05C-E` 可选接入 `verify-auth-db-residuals.yml`
6. `CARD-C7-05C-F` 更新 runbook 与 fork 总任务卡
7. `CARD-C7-05C-G` 做本地共享 `MAVEN_REPO_LOCAL` smoke
8. `CARD-C7-05C-H` 做 YAML / 配置静态校验并形成报告

原因：

- 先证明 fallback 控制流成立，再考虑扩大到更多 workflow
- 先做轻量 workflow，再做真实启动 workflow，更容易定位失败点

---

## 8. 外部前置与 fail-fast 规则

本卡涉及以下“仓库外前置”，Cursor 不得伪装为已完成：

1. `Sramler/camunda-bpm-platform` 上必须存在远端可 checkout 的 ref
2. 该 ref 必须实际包含 Spring Boot 4 fork patch
3. GitHub Actions runner 必须能拉取该仓库
4. `tiny-platform` 必须具备跨仓库 checkout 所需凭证：
   - 推荐 secret：`CAMUNDA_FORK_CHECKOUT_TOKEN`

如果 Cursor 在本地无法验证这些前置，应在结果文档中明确写成：

- “仓库外前置待人工配置”

而不是写成：

- “fallback 已完整实证通过”

fail-fast 规则：

- 当 `CAMUNDA_FORK_LOCAL_INSTALL_ENABLED=true` 且 `CAMUNDA_FORK_REF` 为空时，workflow 必须立即失败
- 当 `CAMUNDA_FORK_LOCAL_INSTALL_ENABLED=true` 且 `CAMUNDA_FORK_CHECKOUT_TOKEN` 为空时，workflow 必须立即失败
  - 除非结果文档明确写明：
    - fork 仓库为公开可读
    - 已人工确认无需 checkout token
- 错误信息必须明确告诉维护者：
  - 需要配置哪个 variable
  - 需要配置哪个 secret
  - 当前 workflow 处于 `CARD-C7-05C` 兜底路径

---

## 9. 交付物要求

Cursor 最终至少交付：

1. 修改文件清单
2. 实际执行命令
3. 影响的 workflow 列表
4. fallback 触发条件说明
5. 远端 ref / variable / secret 的人工前置说明
6. 本地共享 `MAVEN_REPO_LOCAL` smoke 结果
7. 静态校验结果
8. 剩余风险

建议结果文档命名：

- `docs/TINY_PLATFORM_CAMUNDA7_SB4_CARD_C7_05C_IMPLEMENTATION_REPORT.md`

建议最小结果表头：

```md
| workflow | 已接入 fallback | 主路径是否保留 | fallback 开关 | 是否要求 CAMUNDA_FORK_REF | 验证结果 | 备注 |
| --- | --- | --- | --- | --- | --- | --- |
| verify-auth-backend.yml | 是 | 是 | CAMUNDA_FORK_LOCAL_INSTALL_ENABLED | 是 | YAML OK / 本地 smoke OK | ... |
| verify-migration-smoke-mysql.yml | 是 | 是 | CAMUNDA_FORK_LOCAL_INSTALL_ENABLED | 是 | YAML OK / 本地 smoke OK | ... |
```

---

## 10. 复制给 Cursor 的提示词

```text
请执行 tiny-platform 的 CARD-C7-05C：为 GitHub Packages 尚未就绪时补一个“workflow 内 checkout Camunda fork 并本地 mvn install”的临时兜底版，并严格遵守：

- AGENTS.md
- docs/TINY_PLATFORM_CAMUNDA7_SB4_FORK_TASK_CARDS.md
- docs/TINY_PLATFORM_CAMUNDA7_SB4_GITHUB_PACKAGES_RUNBOOK.md
- docs/TINY_PLATFORM_CAMUNDA7_SB4_COORDINATE_EXPLICIT_MIGRATION_CARD.md
- docs/TINY_PLATFORM_TESTING_PLAYBOOK.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/58-cicd.rules.md
- .agent/src/rules/60-git.rules.md
- .agent/src/rules/77-dependency.rules.md
- .agent/src/rules/85-documentation.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- docs/TINY_PLATFORM_CAMUNDA7_SB4_CARD_C7_05C_CURSOR_TASK_CARD.md

本卡只做：
- 为关键后端 workflow 增加本地 install 兜底
- 保留 CARD-C7-05B 的 GitHub Packages 主路径
- 在 fallback 分支中统一 runner Maven 本地仓库路径
- 更新 runbook 和总任务卡

本卡不要做：
- 不改业务代码
- 不改 tiny-oauth-server 运行逻辑
- 不改 camunda-bpm-platform 源码
- 不把 fallback 方案扩到全部 nightly workflow
- 不把 fallback 设成长期默认主路径
- 不读取 ~/.zprofile、~/.zshrc、~/.bashrc

推荐本轮只覆盖：
- .github/workflows/verify-auth-backend.yml
- .github/workflows/verify-migration-smoke-mysql.yml
- 如设计已经足够稳定，再考虑 .github/workflows/verify-auth-db-residuals.yml

关键约束：
- fallback 必须用显式开关控制
- 开启 fallback 时必须要求 CAMUNDA_FORK_REF 非空
- 开启 fallback 时必须要求 CAMUNDA_FORK_CHECKOUT_TOKEN 非空
- workflow 中对 vars/secrets 的读取必须显式归一化，不能只在文档里写“默认值”
- 默认只在 fallback 分支统一使用同一个 -Dmaven.repo.local；主路径不要顺手改 cache 语义
- 不允许只安装 camunda-bom，必须安装 camunda-only-bom、二级 BOM、spin-dataformat-all、starter、starter-rest
- 至少做一次本地共享 MAVEN_REPO_LOCAL smoke：fork install -> tiny-oauth-server test-compile
- 如果仓库外前置无法验证，必须明确标注“待人工配置”，不能伪装成已完整验证

输出要求：
- 修改文件清单
- 实际执行命令
- 影响的 workflow 列表
- fallback 开关与变量说明
- 本地 smoke 结果
- 静态校验结果
- 剩余风险
```

---

## 11. Codex 审计点

Codex 审计时重点检查：

1. 是否真的保留了 `CARD-C7-05B` 主路径，而不是直接替换掉
2. 是否把 fallback 范围控制在少数关键 workflow，而不是无差别扩散
3. 是否统一了 `MAVEN_REPO_LOCAL`
4. fallback 开启时是否强制要求 `CAMUNDA_FORK_REF` 与 `CAMUNDA_FORK_CHECKOUT_TOKEN`
5. 是否安装了完整最小 fork 产物集，而不是只装 `camunda-bom`
6. 是否显式归一化了 repo variable / secret，而不是把文档默认值当作 workflow 默认值
7. 是否保留了 `CARD-C7-05B` 主路径 cache 语义，或在报告中明确解释了变化
8. 结果文档里是否把“仓库外前置”与“代码内已完成”明确分开
