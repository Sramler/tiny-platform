# Tiny Platform Camunda Fork GitHub Packages Runbook

最后更新：2026-04-16

适用仓库：

- `/Users/bliu/code/tiny-platform`
- `/Users/bliu/code/camunda-bpm-platform`

## 1. 目的

本说明用于落实 `CARD-C7-05B`：

把 Camunda 7 / Spring Boot 4 fork 产物从“仅本地 `/usr/local/data/repo` 可见”
推进到“GitHub Actions 和团队成员可直接解析”。

## 2. 发布端

发布仓库：

- `/Users/bliu/code/camunda-bpm-platform`

当前已落地：

- 根 POM 已增加 GitHub Packages `distributionManagement`
- 已新增手动发布 workflow：
  - `.github/workflows/publish-camunda-fork-github-packages.yml`
- 发布说明文档：
  - `docs/github-packages-publishing.md`

当前默认发布地址：

- `https://maven.pkg.github.com/sramler/camunda-bpm-platform`

当前主线版本：

- `7.24.0-tiny-sb4-01`
- `7.24.0-tiny-sb4-01-SNAPSHOT` 已完成开发阶段验证，不再作为默认消费版本

## 3. 消费端

消费仓库：

- `/Users/bliu/code/tiny-platform`

当前已落地：

- 根 POM 已增加受控 profile：
  - `camunda-github-packages`
- 激活条件：
  - `CAMUNDA_GITHUB_PACKAGES_ENABLED=true`
- 当前消费仓库地址：
  - `https://maven.pkg.github.com/sramler/camunda-bpm-platform`
- 已新增 GitHub Actions 本地 action：
  - `.github/actions/setup-camunda-fork-java/action.yml`

该 action 负责：

- 安装 JDK
- 写入 Maven `settings.xml`
- 配置 `github-camunda-fork` server 认证
- 自动打开 `CAMUNDA_GITHUB_PACKAGES_ENABLED=true`
- 当走 `05C` fallback 时，先本地安装 `spin/core`，再安装主最小制品集

## 4. CI 凭证策略

推荐 secrets：

- `CAMUNDA_PACKAGES_USERNAME`
- `CAMUNDA_PACKAGES_TOKEN`

推荐口径：

- `CAMUNDA_PACKAGES_USERNAME`：
  - GitHub 用户名
- `CAMUNDA_PACKAGES_TOKEN`：
  - classic PAT，至少带 `read:packages`

当前 workflow 还支持 fallback：

- `CAMUNDA_PACKAGES_USERNAME` 未配置时回退 `github.actor`
- `CAMUNDA_PACKAGES_TOKEN` 未配置时回退 `github.token`

注意：

- 如果走 `github.token` fallback，必须在 GitHub Packages 页面为 `tiny-platform` 仓库授予 Actions 读取权限
- 否则 consumer workflow 仍会在依赖解析阶段失败

## 5. 已接入的后端 workflow

以下 workflow 已切到 `setup-camunda-fork-java`：

- `.github/workflows/verify-auth-backend.yml`
- `.github/workflows/verify-auth-db-residuals.yml`
- `.github/workflows/verify-migration-smoke-mysql.yml`
- `.github/workflows/verify-scheduling-demo.yml`
- `.github/workflows/verify-scheduling-real-e2e-cross-tenant.yml`
- `.github/workflows/verify-scheduling-real-e2e.yml`
- `.github/workflows/verify-scheduling-seed-reset.yml`
- `.github/workflows/verify-webapp-real-e2e.yml`

说明：

- 这些 workflow 已统一声明 `packages: read`
- 一旦 package 发布完成并授予读取权限，就不再依赖开发机本地 Maven 仓库

## 6. `CARD-C7-05C` 当前落地状态

`CARD-C7-05B` 仍然是推荐主路径，`CARD-C7-05C` 仅作为发布仓库未就绪时的临时兜底。

当前已落地：

- 新增本地 action：
  - `.github/actions/install-camunda-fork-local/action.yml`
- 已接入 `05C` fallback 的首批 workflow：
  - `.github/workflows/verify-auth-backend.yml`
  - `.github/workflows/verify-migration-smoke-mysql.yml`
- 已扩面接入的第 3 条后端 workflow：
  - `.github/workflows/verify-auth-db-residuals.yml`

当前 fallback 约束：

- 默认关闭：
  - `CAMUNDA_FORK_LOCAL_INSTALL_ENABLED=false`
- 开启 fallback 时必须额外配置：
  - repository variable：`CAMUNDA_FORK_REF`
  - repository secret：`CAMUNDA_FORK_CHECKOUT_TOKEN`
- fallback 分支会：
  - checkout `Sramler/camunda-bpm-platform`
  - 先执行 `spin/core` 本地 install，补齐 `camunda-spin-core:tests`
  - 用共享 `MAVEN_REPO_LOCAL` 安装最小 Camunda fork 制品集
  - 让后续 `tiny-platform` Maven 命令继承同一 `maven.repo.local`

实施报告：

- `docs/TINY_PLATFORM_CAMUNDA7_SB4_CARD_C7_05C_IMPLEMENTATION_REPORT.md`

## 7. 推荐落地顺序

1. 在 `camunda-bpm-platform` 手动执行发布 workflow
2. 到 GitHub Packages 页面确认关键制品已发布
3. 为 `tiny-platform` 配置：
   - `CAMUNDA_PACKAGES_USERNAME`
   - `CAMUNDA_PACKAGES_TOKEN`
4. 或在 package 设置页为 `tiny-platform` 开启 Actions 访问权限
5. 先跑一条后端 workflow 验证
6. 再扩大到常规 CI 路径

## 8. 后续收口

`CARD-C7-05B` 解决的是“CI 可解析私有 fork 产物”。

后续仍需继续：

- `CARD-C7-05C`
  - 仅当共享 Maven 仓库暂未就绪时，作为 checkout fork + `mvn install` 的临时兜底
  - 额外仓库外前置：
    - `CAMUNDA_FORK_REF`
    - `CAMUNDA_FORK_CHECKOUT_TOKEN`
  - workflow 实施约束：
    - 对 repo variable / secret 读取要显式归一化
    - 默认只在 fallback 分支统一 `MAVEN_REPO_LOCAL`
    - `CARD-C7-05B` 主路径保持现有 cache 语义不变
  - 最小验证口径：
    - fork install 与 `tiny-oauth-server test-compile` 复用同一个 `MAVEN_REPO_LOCAL`
  - Cursor 执行卡：
    - `docs/TINY_PLATFORM_CAMUNDA7_SB4_CARD_C7_05C_CURSOR_TASK_CARD.md`
- `CARD-C7-05D`
  - 已完成：
    - 主线版本从 `7.24.0-tiny-sb4-01-SNAPSHOT` 收敛到 `7.24.0-tiny-sb4-01`
    - `tiny-oauth-server` 依赖树已解析到固定版：
      - `camunda-bpm-spring-boot-starter`
      - `camunda-engine`
      - `camunda-bpm-spring-boot-starter-rest`
      - `camunda-spin-dataformat-all`
    - `tiny-oauth-server` 全量测试：
      - `Tests run: 1166, Failures: 0, Errors: 0, Skipped: 2`
    - 最小 Camunda smoke verifier 已在固定版下执行成功
    - 发布 workflow 与 `05C` fallback action 已统一到两段式构建口径：
      - 先安装 `spin/core`
      - 再执行主最小制品集 install / deploy
  - 实施报告：
    - `docs/TINY_PLATFORM_CAMUNDA7_SB4_CARD_C7_05D_IMPLEMENTATION_REPORT.md`
