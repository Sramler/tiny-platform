# tiny-platform GitHub Actions real-link E2E 指南

> 适用范围：`tiny-oauth-server` 前后端联动 real-link E2E  
> 当前目标：解释为什么选择 GitHub Actions，并记录这次从引入到打通 `Verify webapp real-link E2E` 的完整排障思路，供后续维护、学习和排错复用。

## 1. 为什么使用 GitHub Actions

对 tiny-platform 来说，GitHub Actions 不是“再多一套 CI”，而是最贴近仓库治理和测试分层的执行面。

核心原因：

- 仓库原生。
  不需要额外维护独立的流水线平台，workflow、文档、PR 门禁、secrets、运行记录都和代码放在一起。
- 适合分层测试。
  可以把 `verify-webapp-auth` 这类 PR 快速链路和 `verify-webapp-real-e2e` 这类较慢的 real-link 链路明确拆开。
- 适合带身份和数据库的真实链路。
  GitHub Actions 原生支持 service container、secret、schedule、workflow_dispatch，足够承载 MySQL + OIDC + MFA + 多租户这类链路。
- 便于复盘。
  每次失败都能直接拿到 job、step、日志、运行环境、触发方式和提交点，不需要额外拼接上下文。
- 便于治理。
  当前仓库已经有 PR 描述门禁、waiver 流程、testing playbook；把 real-link E2E 也放到 GitHub Actions，治理边界最清晰。

## 2. 当前 workflow 拆分

### 2.1 快速链路

- [verify-webapp-auth.yml](../.github/workflows/verify-webapp-auth.yml)

定位：

- `push` / `pull_request` / `workflow_dispatch`
- 只跑前端类型检查、单测、mock-assisted E2E
- 用于 PR 快速反馈

### 2.2 real-link 链路

- [verify-webapp-real-e2e.yml](../.github/workflows/verify-webapp-real-e2e.yml)

定位：

- `workflow_dispatch`
- `schedule`
- 跑真实前端 + 真实后端 + MySQL service + 专用自动化身份 + Playwright real suite

当前覆盖：

- 调度编排真实链路
- post-login 安全中心 / TOTP 信息读取
- `/login -> totp-verify` 已绑定 MFA 链路
- 未绑定 TOTP 首绑链路
- 双身份 / 双租户跨租户隔离回归

## 3. workflow 执行结构

`verify-webapp-real-e2e` 当前结构可以概括为：

1. 校验 real-link secrets
2. 安装 Java / Node
3. 安装 webapp 依赖
4. 前端类型检查
5. 运行 real setup regression
6. 预热后端 Maven 依赖
7. 启动 backend
8. 启动 frontend
9. 安装 Playwright Chromium
10. 跑 real-link E2E

这么拆的目的，是让失败尽量在“最靠前、最可定位”的步骤暴露，而不是所有问题都堆在最终的 Playwright 失败里。

## 4. 这次从引入到打通的排查路径

下面按“症状 -> 判断 -> 修复”记录本次真实链路打通过程。顺序基本就是实际排查顺序。

### 阶段 A：先把本地 real-link 跑通

先确认本地不是空想：

- 补齐 `.env.e2e.local`
- 用真实 MySQL、本地后端、本地前端跑 `npm run test:e2e:real`
- 先把调度、MFA、双租户 real-link 在本地打通

目的：

- 先证明“产品链路可用”
- 再去区分“CI 问题”还是“业务问题”

### 阶段 B：CI 初版接入后，先暴露 workflow 基建问题

初期 CI 不是一上来就失败在业务用例，而是先暴露了一批 workflow / 环境问题：

- 无效 npm cache path
- TS env helper 类型问题
- 依赖 `webServer` 黑盒启动，失败时日志不够

对应修复：

- 去掉无效缓存路径
- 修正 `real.global.setup.ts` 的类型问题
- 改成 workflow 显式启动 backend/frontend，并在失败时打印日志

思路：

- 先让 CI 失败“可见”
- 日志不可见时，不要急着改业务代码

### 阶段 C：fresh DB 安装不自洽

在 CI 用 fresh DB 运行后，出现的不是 E2E 逻辑错误，而是安装链路本身不自洽：

- Liquibase changeSet 假设某些表已经存在
- MySQL 方言和 schema/comment/check constraint 不兼容
- dict / OAuth2 JDBC 表在 fresh install 上缺失

对应修复：

- 补 `dict` 核心表 changeSet
- 补 OAuth2 Authorization Server JDBC 基础表 changeSet
- 删除无效 MySQL `CHECK ... COMMENT`
- 对重复约束 / 重复列场景做 `mark-ran` 或幂等处理

思路：

- fresh DB 失败优先看 migration，不要先怀疑 Playwright
- local“跑过一次的旧库可用”不代表 CI 的 fresh install 可用

### 阶段 D：后端 profile 和非必须模块干扰

fresh DB 跑起来后，又碰到 e2e profile 下与主链路无关的启动噪声：

- Camunda 相关依赖和桥接逻辑会影响 e2e profile

对应修复：

- 在 `application-e2e.yaml` 下禁用相关非必须模块
- 对 workflow beans 做 profile 或存在性保护

思路：

- real-link E2E 要尽量减少与目标链路无关的系统噪声
- e2e profile 不是“生产 profile 直跑”，而是“真实链路最小可用配置”

### 阶段 E：frontend / readiness / host 细节问题

后端能起后，前端链路又暴露了几个纯环境问题：

- readiness 探测 URL 不合适
- `localhost` / `127.0.0.1` 行为不一致

对应修复：

- frontend readiness 从 `/login` 改成 `/`
- 明确把 dev server 绑定到 `127.0.0.1`

思路：

- CI 上 `localhost`/IPv6/重定向行为不一定和本地完全一致
- readiness 应尽量用最稳定的入口

### 阶段 F：真实身份 bootstrap 问题

随后，CI 真正进入 auth-state 生成阶段时，才暴露出自动化身份准备问题：

- 次身份可能继承主身份的 `E2E_TOTP_CODE`
- 首绑 MFA spec 错误依赖 `localStorage` 中的 OIDC token
- fresh DB 下配置的 tenant 并不存在

对应修复：

- 给次身份 env 拼装加回归测试，明确不能串用主身份 one-time code
- 让首绑流程按真实页面契约走 `credentials: include`
- `ensure-scheduling-e2e-auth.sh` 改成先 `ensure tenant`，再创建/修复用户

思路：

- 真实链路里，“身份准备”本身就是系统的一部分
- 不能只测业务页面，不测 auth bootstrap helper

### 阶段 G：late-created tenant 与 issuer delegate

fresh DB 下，CI secrets 里的第二租户是后创建的；但 OAuth2 issuer delegate 只认识启动时注册进 registry 的租户。

症状：

- secondary auth-state 生成阶段找不到 issuer 对应 delegate

对应修复：

- 给 issuer-based OAuth2 delegate 增加 default delegate 回退：
  - `IssuerDelegatingRegisteredClientRepository`
  - `IssuerDelegatingOAuth2AuthorizationService`
  - `IssuerDelegatingOAuth2AuthorizationConsentService`
- 补回归测试：
  - [IssuerDelegatingOAuth2ServicesTest.java](../tiny-oauth-server/src/test/java/com/tiny/platform/core/oauth/multitenancy/IssuerDelegatingOAuth2ServicesTest.java)

思路：

- 这里不是“多租户隔离失效”，而是“fresh DB + late-created tenant 的 OIDC 登录适配”
- registry miss 时优先回退到 default delegate，比直接抛异常更适合当前 JDBC 单数据源实现

### 阶段 H：backend 启动超时，但不是应用炸了

上面都修完后，workflow 仍失败在 `Start backend`，但这次不是 Spring Boot 自己炸掉，而是：

- `spring-boot:run` 首次启动时还在现场下载 Maven 依赖
- 4 分钟 readiness 窗口被下载耗尽

对应修复：

- 在 workflow 里新增 `Prime backend Maven dependencies`
- 先跑：

```bash
mvn -pl tiny-oauth-server -DskipTests test-compile
```

- 再启动 `spring-boot:run`

思路：

- 先确认“应用没 ready”究竟是启动失败，还是构建下载太慢
- 不要把所有慢启动都归因于业务代码

### 阶段 I：最终成功

最终成功 run：

- [22933706581](https://github.com/Sramler/tiny-platform/actions/runs/22933706581)

最终结论：

- MySQL service 可用
- fresh DB migration 自洽
- real identity bootstrap 正常
- backend / frontend readiness 正常
- Playwright real-link suite 正常

## 5. 后续排障建议

以后再遇到 `verify-webapp-real-e2e` 失败，建议按这个顺序看：

1. 先看失败停在哪个 step  
   - `Validate real-link secret set`：先看 secrets
   - `Run real-link setup regression`：先看 auth-state/helper
   - `Start backend`：先区分 Maven 下载、Liquibase、Spring 启动
   - `Start frontend`：先看 dev server / readiness / host
   - `Run real-link E2E`：再看具体 spec

2. 再看是否是 fresh DB 问题  
   - migration
   - data.sql
   - MySQL 方言

3. 再看是否是自动化身份问题  
   - tenant 是否存在
   - bind 用户是否被错误保留 TOTP
   - tenant B 是否串用了 tenant A 的 one-time code

4. 最后才看 Playwright 业务断言  
   - MFA
   - 调度编排
   - 跨租户拒绝

## 6. 常用命令

### 查看最近 workflow

```bash
token=$(printf 'protocol=https\nhost=github.com\n\n' | git credential fill | sed -n 's/^password=//p')
GH_TOKEN="$token" gh run list --repo Sramler/tiny-platform --workflow verify-webapp-real-e2e.yml --limit 5
```

### 查看失败日志

```bash
GH_TOKEN="$token" gh run view <run-id> --repo Sramler/tiny-platform --log-failed
```

### 本地重放核心回归

```bash
cd /Users/bliu/code/tiny-platform/tiny-oauth-server/src/main/webapp
npm run test:unit -- src/e2e/realGlobalSetup.test.ts
npm run test:e2e:real -- --reporter=line
```

### 校验 workflow YAML

```bash
ruby -e 'require "yaml"; YAML.load_file("/Users/bliu/code/tiny-platform/.github/workflows/verify-webapp-real-e2e.yml"); puts "OK"'
```

## 7. 当前仍需留意的事项

- GitHub 官方 Action 的 major 版本会继续演进，workflow 里要定期检查 runtime 兼容窗口。
- real-link 链路依赖专用测试身份，secrets 轮换时要同步验证：
  - 主身份
  - 第二租户身份
  - 首绑身份
- 新增 real-link spec 时，优先判断它属于：
  - 现有 `verify-webapp-real-e2e`
  - 还是应该独立为新的 nightly / full-chain workflow
