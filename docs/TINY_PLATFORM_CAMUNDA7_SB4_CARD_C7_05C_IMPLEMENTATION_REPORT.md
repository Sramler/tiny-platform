# Tiny Platform CARD-C7-05C 实施报告

最后更新：2026-04-16

## 1. 实施结论

`CARD-C7-05C` 已完成首批落地实现，但仍保持为 `CARD-C7-05B` 失败时才启用的临时兜底路径。

当前已完成：

- 新增本地 action：
  - `.github/actions/install-camunda-fork-local/action.yml`
- 已接入 fallback 的首批 workflow：
  - `.github/workflows/verify-auth-backend.yml`
  - `.github/workflows/verify-migration-smoke-mysql.yml`
- 已扩面接入的第 3 条后端 workflow：
  - `.github/workflows/verify-auth-db-residuals.yml`
- 默认行为保持不变：
  - 未开启 `CAMUNDA_FORK_LOCAL_INSTALL_ENABLED` 时，仍走 `CARD-C7-05B` 的 GitHub Packages 主路径

## 2. 修改文件清单

- 新增：
  - `.github/actions/install-camunda-fork-local/action.yml`
  - `docs/TINY_PLATFORM_CAMUNDA7_SB4_CARD_C7_05C_CURSOR_TASK_CARD.md`
  - `docs/TINY_PLATFORM_CAMUNDA7_SB4_GITHUB_PACKAGES_RUNBOOK.md`
  - `docs/TINY_PLATFORM_CAMUNDA7_SB4_CARD_C7_05C_IMPLEMENTATION_REPORT.md`
- 更新：
  - `.github/workflows/verify-auth-backend.yml`
  - `.github/workflows/verify-auth-db-residuals.yml`
  - `.github/workflows/verify-migration-smoke-mysql.yml`
  - `docs/TINY_PLATFORM_CAMUNDA7_SB4_FORK_TASK_CARDS.md`
  - `docs/TINY_PLATFORM_CAMUNDA7_SB4_GITHUB_PACKAGES_RUNBOOK.md`
  - `docs/TINY_PLATFORM_CAMUNDA7_SB4_CARD_C7_05C_IMPLEMENTATION_REPORT.md`

## 3. 实现策略

本轮实现遵循以下约束：

- `CARD-C7-05B` 保持为默认主路径
- 仅当 `CAMUNDA_FORK_LOCAL_INSTALL_ENABLED=true` 时切入 `CARD-C7-05C`
- 固定版 `7.24.0-tiny-sb4-01` 的 fallback 安装改为两段式：
  - 先本地安装 `spin/core`，产出 `camunda-spin-core:tests`
  - 再安装主最小制品集，并保留 `-Dmaven.test.skip=true`
- fallback 分支显式归一化并校验：
  - `CAMUNDA_FORK_REF`
  - `CAMUNDA_FORK_CHECKOUT_TOKEN`
  - `MAVEN_REPO_LOCAL`
- fallback 分支使用 `actions/checkout` 拉取：
  - `Sramler/camunda-bpm-platform`
- fallback 分支通过共享 `MAVEN_OPTS=-Dmaven.repo.local=...`，让后续 workflow 内 Maven 命令与脚本复用同一 Maven 本地仓库

本地 action 当前采用以下安装顺序：

- 预安装：
  - `spin/core`
- 主最小制品集：
  - `bom/camunda-bom`
  - `bom/camunda-only-bom`
  - `spin/bom`
  - `commons/bom`
  - `connect/bom`
  - `spin/dataformat-all`
  - `spring-boot-starter/starter`
  - `spring-boot-starter/starter-rest`

说明：

- `spin/dataformat-json-jackson` 会拉取 `camunda-spin-core:tests`
- 如果直接对主最小制品集使用 `-Dmaven.test.skip=true`，该 `tests.jar` 不会被产出
- 因此 fixed-version fallback 必须先执行一次 `spin/core` 本地安装

## 4. 外部前置

启用 `CARD-C7-05C` fallback 前，需要在 GitHub 仓库侧配置：

- repository variable：
  - `CAMUNDA_FORK_LOCAL_INSTALL_ENABLED`
  - `CAMUNDA_FORK_REF`
- repository secret：
  - `CAMUNDA_FORK_CHECKOUT_TOKEN`

说明：

- `CAMUNDA_FORK_LOCAL_INSTALL_ENABLED` 推荐默认值为 `false`
- `CAMUNDA_FORK_REF` 必须是远端可 checkout 的 ref
- `CAMUNDA_FORK_CHECKOUT_TOKEN` 必须具备跨仓库读取 `Sramler/camunda-bpm-platform` 的权限

## 5. 实际执行命令

静态校验：

```bash
ruby -e 'require "yaml"; ARGV.each { |f| YAML.load_file(f) }' \
  /Users/bliu/code/tiny-platform/.github/actions/install-camunda-fork-local/action.yml \
  /Users/bliu/code/tiny-platform/.github/workflows/verify-auth-backend.yml \
  /Users/bliu/code/tiny-platform/.github/workflows/verify-auth-db-residuals.yml \
  /Users/bliu/code/tiny-platform/.github/workflows/verify-migration-smoke-mysql.yml
```

```bash
git -C /Users/bliu/code/tiny-platform diff --check -- \
  .github/actions/install-camunda-fork-local/action.yml \
  .github/workflows/verify-auth-backend.yml \
  .github/workflows/verify-auth-db-residuals.yml \
  .github/workflows/verify-migration-smoke-mysql.yml \
  docs/TINY_PLATFORM_CAMUNDA7_SB4_CARD_C7_05C_CURSOR_TASK_CARD.md \
  docs/TINY_PLATFORM_CAMUNDA7_SB4_CARD_C7_05C_IMPLEMENTATION_REPORT.md \
  docs/TINY_PLATFORM_CAMUNDA7_SB4_FORK_TASK_CARDS.md \
  docs/TINY_PLATFORM_CAMUNDA7_SB4_GITHUB_PACKAGES_RUNBOOK.md
```

本地共享仓库 smoke：

```bash
mvn -B \
  -pl spin/core \
  -am \
  -Dmaven.repo.local=/tmp/tiny-platform-c7-05c-m2 \
  -DskipTests \
  -DskipITs \
  install
```

```bash
mvn -B \
  -pl bom/camunda-bom,bom/camunda-only-bom,spin/bom,commons/bom,connect/bom,spin/dataformat-all,spring-boot-starter/starter,spring-boot-starter/starter-rest \
  -am \
  -Dmaven.repo.local=/tmp/tiny-platform-c7-05c-m2 \
  -Dmaven.test.skip=true \
  -DskipTests \
  -DskipITs \
  install
```

最终闭环验证使用的共享 Maven 仓库命令：

```bash
mvn -B -o \
  -pl spin/core \
  -am \
  -Dmaven.repo.local=/usr/local/data/repo \
  -DskipTests \
  -DskipITs \
  install
```

```bash
mvn -B -o \
  -pl bom/camunda-bom,bom/camunda-only-bom,spin/bom,commons/bom,connect/bom,spin/dataformat-all,spring-boot-starter/starter,spring-boot-starter/starter-rest \
  -am \
  -Dmaven.repo.local=/usr/local/data/repo \
  -Dmaven.test.skip=true \
  -DskipTests \
  -DskipITs \
  install
```

```bash
mvn -o \
  -pl tiny-oauth-server \
  -am \
  -DskipTests \
  test-compile \
  -Dmaven.repo.local=/usr/local/data/repo
```

## 6. 当前验证结果

静态校验结果：

- YAML 语法校验通过
- `git diff --check` 通过

本地共享 `MAVEN_REPO_LOCAL` smoke：

- 冷启动 `/tmp/tiny-platform-c7-05c-m2` 观察：
  - 从空本地仓库启动后，预安装 `spin/core` 与主 install 均能真实推进
  - 未出现 `05C` 设计相关的早期失败，例如：
    - 错误的 checkout 目标
    - 缺少最小制品坐标
    - 共享 `maven.repo.local` 参数失效
  - 由于冷启动需要补齐大量历史依赖与 FEEL/DMN/Graal 相关下载，执行时间显著偏长，最终未作为收口证据继续跑完
- 最终闭环验证：
  - Camunda fork 离线 install：
    - 使用共享仓库 `/usr/local/data/repo`
    - `BUILD SUCCESS`
    - 总耗时：`22.530 s`
  - `tiny-oauth-server` 离线 `test-compile`：
    - 使用同一共享仓库 `/usr/local/data/repo`
    - `BUILD SUCCESS`
    - 总耗时：`8.839 s`
- 结论：
- `CARD-C7-05C` 的“共享 Maven 本地仓库 install -> tiny-oauth-server test-compile”闭环已验证通过
- `CARD-C7-05B` 仍应保持为默认主路径
- `CARD-C7-05C` 可以作为发布仓库未就绪时的临时 fallback 使用
- fallback 覆盖范围现已扩到 3 条代表性后端 workflow：
  - `verify-auth-backend.yml`
  - `verify-migration-smoke-mysql.yml`
  - `verify-auth-db-residuals.yml`

## 7. 剩余风险

- fallback 依赖跨仓库 checkout token，仓库外配置不到位时会 fail-fast
- 从空本地仓库冷启动时，fallback 会显著增加 runner 上的构建时间，因此不应替代 `CARD-C7-05B` 主路径
- 当前主线版本治理已进入 `CARD-C7-05D` 固定版收口阶段，`05C` 本身不承担版本稳定化职责
- fixed-version fallback 仍依赖两段式安装约束，后续若继续调整最小制品集，必须同步验证 `camunda-spin-core:tests` 链路
