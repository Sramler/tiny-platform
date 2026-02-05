# 77 依赖管理规范

## 适用范围

- 适用于：`**/pom.xml`、`**/build.gradle`、`**/package.json`、依赖管理相关文件
- 不适用于：第三方库内部依赖（但使用时应遵循版本管理策略）

## 总体策略

1. **统一版本管理**：使用 `dependencyManagement`（Maven）或 `platform`（Gradle）统一管理依赖版本。
2. **版本锁定**：关键依赖版本锁定，避免自动升级导致的不兼容问题。
3. **安全优先**：及时更新有安全漏洞的依赖版本。

---

## 禁止（Must Not）

### 1) 依赖版本

- ❌ 引入有已知安全漏洞的依赖版本（必须及时更新或替换）。
- ❌ 使用 SNAPSHOT 版本依赖（除非是开发阶段，生产环境禁止使用）。
- ❌ 依赖版本硬编码在子模块（应使用父 POM 的 `dependencyManagement`）。

### 2) 依赖冲突

- ❌ 引入与现有依赖冲突的版本（必须解决冲突，明确版本选择）。
- ❌ 忽略依赖冲突警告（必须分析并解决）。

### 3) 依赖范围

- ❌ 引入与需求无关的依赖（只引入必要的依赖）。
- ❌ 引入重复功能的依赖（如同时引入多个 JSON 处理库）。

---

## 必须（Must）

### 1) 版本管理

- ✅ 统一版本管理：使用父 POM 的 `dependencyManagement` 统一管理依赖版本。
- ✅ 版本锁定：关键依赖（如 Spring Boot、Spring Cloud）版本在父 POM 中锁定。
- ✅ 版本号规范：使用语义化版本（SemVer），如 `1.0.0`、`2.1.3`。

### 2) 模块命名

- ✅ 模块命名：使用连字符（`-`），如 `tiny-oauth-server`、`tiny-common-exception`。
- ✅ 模块前缀：统一使用 `tiny-` 前缀（如 `tiny-oauth-server`），明确归属。
- ✅ groupId：统一使用 `com.tiny`。

### 3) 依赖声明

- ✅ 依赖声明：子模块只声明 `groupId` 和 `artifactId`，版本由父 POM 管理。
- ✅ 依赖分类：明确依赖类型（compile、test、provided、runtime）。

### 4) 安全更新

- ✅ 安全漏洞：定期检查依赖安全漏洞，及时更新有漏洞的依赖。
- ✅ 依赖扫描：使用工具（如 OWASP Dependency-Check、Snyk）扫描依赖漏洞。

---

## 应该（Should）

### 1) 依赖组织

- ⚠️ 依赖分组：按功能分组（Spring、数据库、工具类等），便于管理。
- ⚠️ 依赖注释：关键依赖添加注释说明引入原因。

### 2) 版本策略

- ⚠️ 版本更新：定期更新依赖版本，保持与最新稳定版本同步。
- ⚠️ 破坏性更新：大版本更新前评估影响，制定迁移计划。

### 3) 依赖分析

- ⚠️ 依赖分析：使用 `mvn dependency:tree` 分析依赖树，识别冲突和冗余。
- ⚠️ 依赖优化：移除未使用的依赖，减少依赖体积。

### 4) 模块依赖

- ⚠️ 模块依赖：模块间依赖应明确且最小化，避免循环依赖。
- ⚠️ 公共模块：公共功能提取到公共模块（如 `tiny-common-exception`），避免重复依赖。

---

## 可以（May）

- 💡 使用 BOM（Bill of Materials）：Spring Boot、Spring Cloud 等提供 BOM，简化版本管理。
- 💡 依赖排除：必要时排除传递依赖，避免冲突。

---

## 例外与裁决

### SNAPSHOT 版本

- 开发阶段：开发阶段可使用 SNAPSHOT 版本，但必须明确标注。
- 生产环境：生产环境禁止使用 SNAPSHOT 版本。

### 第三方库

- 第三方库：第三方库内部依赖遵循其版本管理策略，不强制修改。

### 冲突裁决

- 平台特定规则（90+）优先于本规范。
- 依赖管理与安全规范冲突时，优先保证安全（及时更新有漏洞的依赖）。

---

## 示例

### ✅ 正例：父 POM 统一版本管理

```xml
<!-- 父 POM -->
<project>
    <groupId>com.tiny</groupId>
    <artifactId>tiny-platform</artifactId>
    <version>1.0.0</version>
    <packaging>pom</packaging>
    
    <properties>
        <spring-boot.version>3.5.8</spring-boot.version>
        <spring-cloud.version>2025.0.0</spring-cloud.version>
        <mybatis.version>3.0.3</mybatis.version>
    </properties>
    
    <dependencyManagement>
        <dependencies>
            <!-- Spring Boot BOM -->
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring-boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            
            <!-- 项目内部模块 -->
            <dependency>
                <groupId>com.tiny</groupId>
                <artifactId>tiny-common-exception</artifactId>
                <version>${project.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

### ✅ 正例：子模块依赖声明（不指定版本）

```xml
<!-- 子模块 POM -->
<project>
    <parent>
        <groupId>com.tiny</groupId>
        <artifactId>tiny-platform</artifactId>
        <version>1.0.0</version>
    </parent>
    
    <artifactId>tiny-oauth-server</artifactId>
    
    <dependencies>
        <!-- ✅ 不指定版本，由父 POM 管理 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        
        <!-- ✅ 项目内部模块 -->
        <dependency>
            <groupId>com.tiny</groupId>
            <artifactId>tiny-common-exception</artifactId>
        </dependency>
    </dependencies>
</project>
```

### ❌ 反例：子模块硬编码版本

```xml
<!-- ❌ 错误：子模块硬编码版本 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
    <version>3.5.8</version> <!-- ❌ 版本应由父 POM 管理 -->
</dependency>
```

### ✅ 正例：模块命名规范

```xml
<!-- ✅ 使用连字符和 tiny- 前缀 -->
<artifactId>tiny-oauth-server</artifactId>
<artifactId>tiny-common-exception</artifactId>
<artifactId>tiny-idempotent-platform</artifactId>
```

### ❌ 反例：模块命名不规范

```xml
<!-- ❌ 错误：使用下划线、缺少前缀 -->
<artifactId>tiny_web</artifactId> <!-- ❌ 应使用 tiny-web -->
<artifactId>oauth-server</artifactId> <!-- ❌ 应使用 tiny-oauth-server -->
```

### ✅ 正例：依赖冲突解决

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>example-lib</artifactId>
    <version>1.0.0</version>
    <exclusions>
        <!-- ✅ 排除冲突的传递依赖 -->
        <exclusion>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

### ✅ 正例：依赖分析命令

```bash
# ✅ 分析依赖树
mvn dependency:tree

# ✅ 分析依赖冲突
mvn dependency:analyze

# ✅ 检查依赖更新
mvn versions:display-dependency-updates
```

### ✅ 正例：安全漏洞扫描

```bash
# ✅ 使用 OWASP Dependency-Check 扫描漏洞
mvn org.owasp:dependency-check-maven:check

# ✅ 使用 Snyk 扫描漏洞
snyk test
```

### ✅ 正例：依赖注释

```xml
<dependencies>
    <!-- 统一异常处理模块 -->
    <dependency>
        <groupId>com.tiny</groupId>
        <artifactId>tiny-common-exception</artifactId>
    </dependency>
    
    <!-- MyBatis 持久层框架 -->
    <dependency>
        <groupId>org.mybatis.spring.boot</groupId>
        <artifactId>mybatis-spring-boot-starter</artifactId>
    </dependency>
</dependencies>
```
