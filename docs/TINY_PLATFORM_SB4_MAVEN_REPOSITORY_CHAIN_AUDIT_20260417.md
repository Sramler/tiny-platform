# Tiny Platform SB4 Maven 仓库链路审计

最后更新：2026-04-17

状态：

- `已完成首轮盘点`
- 当前仅记录证据与治理结论
- 当前不修改业务代码
- 当前不修改 Maven mirror / credentials 配置

适用仓库：

- `/Users/bliu/code/tiny-platform`

## 1. 目的

本审计用于收口 `CARD-SAAS-02`：

- 判断 `sb4` 上 snapshot / metadata `401` 是否来自项目 POM 主链
- 区分“当前 effective settings / effective POM”与“本地仓库中的历史解析痕迹”
- 给后续 Maven 仓库链路治理提供一份可复核、可复用的起点

当前已落地的诊断脚本：

- `bash tiny-oauth-server/scripts/diagnose-sb4-maven-repository-chain.sh`

## 2. 本次使用的命令

### 2.1 生效配置盘点

```bash
mvn -q help:effective-settings -Doutput=/tmp/tiny-platform-effective-settings.xml
CAMUNDA_GITHUB_PACKAGES_ENABLED=true mvn -q help:effective-pom -Doutput=/tmp/tiny-platform-effective-pom-camunda.xml
mvn -version
```

### 2.2 本地仓库痕迹盘点

```bash
rg -n "camunda-nexus|repo.camunda|camunda.*nexus" ~/.m2 /usr/local/data/repo -S
sed -n '1,40p' /usr/local/data/repo/org/springframework/spring-core/7.0.7-SNAPSHOT/resolver-status.properties
sed -n '1,80p' /usr/local/data/repo/org/springframework/spring-core/7.0.7-SNAPSHOT/_remote.repositories
sed -n '1,80p' /usr/local/data/repo/com/fasterxml/jackson/jackson-bom/3.1.2/jackson-bom-3.1.2.pom.lastUpdated
sed -n '1,80p' /usr/local/data/repo/tools/jackson/datatype/jackson-datatype-jdk8/3.1.2/jackson-datatype-jdk8-3.1.2.pom.lastUpdated
```

说明：

- 本次只记录脱敏后的结果，不展开任何凭证
- 当前本机未发现额外 `settings-security.xml`
- 后续复查优先使用：
  - `bash tiny-oauth-server/scripts/diagnose-sb4-maven-repository-chain.sh`

## 3. 环境基线

- Maven：
  - `Apache Maven 3.9.12`
- Java：
  - `21.0.5`
- 当前本机 effective settings 指向本地仓库：
  - `/usr/local/data/repo`

## 4. Effective Settings 发现

当前本机 effective settings 的关键结论如下：

1. 未发现 `camunda-nexus`
2. 未发现 `artifacts.camunda.com` 相关 mirror / repository
3. 未发现额外 `<server>` 凭证条目
4. 当前 active profile 只有：
   - `default`
5. 当前 settings 自带仓库只有：
   - `central`
   - `aliyun-maven`
6. 当前唯一 mirror 是 Maven 默认的：
   - `maven-default-http-blocker`

结论：

- 以“当前这台机器此刻的 effective settings”来看，`camunda-nexus` 不是由本机当前用户层 settings 直接注入的默认源

参考：

- `/tmp/tiny-platform-effective-settings.xml`

## 5. Effective POM 发现

在开启 `CAMUNDA_GITHUB_PACKAGES_ENABLED=true` 后，根项目 effective POM 中生效的主仓库链为：

1. `central`
2. `aliyun-maven`
3. `github-camunda-fork`
4. `apache-releases`
5. `spring-snapshots`
6. `aliyun`

插件仓库链为：

1. `spring-snapshots`
2. `aliyun-plugin`
3. `central`

同时确认：

- `github-camunda-fork` 来自受控 profile：
  - `camunda-github-packages`
- 当前 effective POM 中未出现：
  - `camunda-nexus`
  - `camunda-public-repository`
  - `JBoss public`
  - `repo.spring.io/milestone`
  - `artifacts.camunda.com/artifactory/public`

结论：

- 从项目 POM 主链角度看，`camunda-nexus` 不是 tiny-platform 当前显式声明的仓库
- `github-camunda-fork` 是当前受控的 Camunda SB4 fork 消费路径

参考：

- `/tmp/tiny-platform-effective-pom-camunda.xml`
- [pom.xml](/Users/bliu/code/tiny-platform/pom.xml:257)
- [pom.xml](/Users/bliu/code/tiny-platform/pom.xml:309)
- [pom.xml](/Users/bliu/code/tiny-platform/pom.xml:348)

## 6. 本地 Maven 仓库痕迹发现

### 6.1 `camunda-nexus` 401 不是猜测，确有历史解析痕迹

在 `/usr/local/data/repo` 内，存在多份 `.lastUpdated` 与 `resolver-status.properties`，明确记录：

- repository id：
  - `camunda-nexus`
- URL：
  - `https://artifacts.camunda.com/artifactory/internal`
- 错误：
  - `401`

样本包括：

- `org/springframework/spring-core/7.0.7-SNAPSHOT/resolver-status.properties`
- `org/springframework/spring-core/6.2.18-SNAPSHOT/resolver-status.properties`
- `com/fasterxml/jackson/jackson-bom/3.1.2/*.lastUpdated`
- `tools/jackson/datatype/jackson-datatype-jdk8/3.1.2/*.lastUpdated`
- `tools/jackson/datatype/jackson-datatype-jsr310/3.1.2/*.lastUpdated`

### 6.2 实际成功拉下来的 Spring snapshot 仍来自 `spring-snapshots`

以 `spring-core:7.0.7-SNAPSHOT` 为例：

- `_remote.repositories` 明确记录实际成功来源是：
  - `spring-snapshots`

这说明：

- `camunda-nexus` 401 出现在候选仓库集合里
- 但当前真正成功的 Spring snapshot 仍然是从 `spring-snapshots` 取得的

### 6.3 候选仓库集合比项目 POM 声明更大

以 `spring-core:7.0.7-SNAPSHOT` 和 `jackson-bom:3.1.2` 的本地状态文件为例，可以看到候选集合中还出现了：

- `camunda-public-repository`
- `JBoss public`
- `repo.spring.io/milestone`
- `artifacts.camunda.com/artifactory/public`
- `repository.jboss.org/nexus/content/groups/public`

而这些来源在当前：

- effective settings 中没有
- effective POM 中也没有

这说明：

- 它们不是 tiny-platform 当前项目仓库声明的直接产物
- 更像是历史解析过程中引入的附加仓库来源

## 7. 审计结论

### 7.1 关于 `camunda-nexus` 的来源判断

当前可确认：

1. `camunda-nexus` 不是 tiny-platform 当前根 POM 显式声明的仓库
2. `camunda-nexus` 也不是当前本机 effective settings 中可见的 mirror / repository
3. 但它明确存在于本地 Maven 仓库的历史解析状态文件中

当前最合理的治理判断是：

- `camunda-nexus` 属于“非项目主链的额外候选仓库来源”
- 它可能来自：
  - 历史环境中的另一套 Maven settings / CI action 注入
  - 上游依赖描述符解析时带入的附加仓库集合
- 在未进一步做 full debug resolve trace 前，不把它定性为当前项目默认配置

### 7.2 关于 `401 metadata` 的风险判断

当前可确认：

1. `401` 确实存在，不是误报
2. 但当前 `sb4` 仍可编译，且关键 MFA / `prompt=none` 定向测试已通过
3. Spring snapshot 的成功来源仍是 `spring-snapshots`

因此本轮结论保持为：

- `401 metadata` 当前是可接受噪音
- 但它属于应治理的仓库链路风险

### 7.3 关于凭证

当前本机 effective settings 未展示任何 `<server>` 条目，因此可推断：

- 本机如果在冷仓状态下需要直接访问 `github-camunda-fork`
  - 仍应显式提供 GitHub Packages 读取凭证
- 当前已成功构建并不代表“本机凭证配置已完备”
  - 更可能是本地仓库已有缓存制品

## 8. 治理口径

基于本轮审计，建议固定以下口径：

1. 不把 `camunda-nexus` 反写进项目 POM
2. 不为“消除 401 文本”而优先做静默 suppress
3. 继续维持当前项目主仓库路径：
   - `spring-snapshots`
   - `github-camunda-fork`
   - `apache-releases`
   - `aliyun`
   - `central`
4. 后续若继续治理，应优先做：
   - 脱敏的 effective repositories / mirrors 诊断脚本
   - CI / 本机 settings 注入来源盘点

## 9. 下一步建议

建议把后续动作限定为一张小治理卡，不直接扩大为业务改造：

1. 增加一个脱敏诊断脚本
   - 输出 effective settings / effective POM 的仓库链摘要
   - 输出本地仓库中 `camunda-nexus` / `camunda-public-repository` / `JBoss public` 痕迹摘要
   - 当前已落地首版：
     `bash tiny-oauth-server/scripts/diagnose-sb4-maven-repository-chain.sh`
2. 在 runbook 或技术债台账里补一条正式约束：
   - `camunda-nexus` 不是项目当前主线路径
3. 后续若需要判断“新一次构建是否还会打到 `camunda-nexus`”
   - 应在隔离或清理过相关 `.lastUpdated` / resolver 状态后做一次定向验证
