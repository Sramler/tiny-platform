# 10 Java 编码规范

## 适用范围

- 适用于：`**/*.java`
- 不适用于：第三方/生成代码（OpenAPI、jOOQ、QueryDSL 等生成物），但建议通过格式化保持一致。

## 总体策略（必须统一口径）

1. **格式由工具决定，不靠人记忆**

- 代码排版（缩进、换行、import 排序等）必须由统一工具固化，避免"手工对齐"导致的脆弱格式与冲突扩散。

2. **冲突裁决（Google vs Alibaba）**

- 若存在冲突：
  - **正确性与安全性 > 风格**
  - **Google** 优先用于"格式/排版/结构化写法"
  - **Alibaba** 优先用于"工程实践/安全/性能/可维护性"

3. **可自动化优先自动化**

- 能被 formatter / lint / 静态检查替代的人肉规范，一律以工具为准。

---

## 禁止（Must Not）

### 1) 结构与可维护性（偏 Google）

- ❌ 禁止省略花括号：`if/else/for/while/do` 即使单行也必须使用 `{}`，降低维护期误改风险。
- ❌ 禁止星号导入：`import xxx.*;`（包括静态导入）。
- ❌ 禁止"手工列对齐/手工表格式排版"（例如用空格对齐多行字段/参数），交由 formatter。
- ❌ 禁止在一个文件中混用多种格式风格（例如部分 2 空格、部分 4 空格）。
- ❌ 禁止数组声明使用 C 风格：`String args[]`，必须使用 `String[] args`。
- ❌ 禁止在 switch 语句中隐式 fall-through（除非明确注释 `// fall through`）。
- ❌ 禁止使用 `long` 字面量小写 `l`：必须使用大写 `L`（如 `3000L` 而非 `3000l`）。

### 2) 异常与日志（偏 Alibaba）

- ❌ 禁止吞异常：`catch (Exception e) {}` / 只打印不处理 / 无脑返回默认值掩盖错误。
- ❌ 禁止 `printStackTrace()`；必须使用统一日志体系（便于 traceId/tenantId 关联）。
- ❌ 禁止捕获过宽异常并静默继续：除非你能给出明确的降级策略与可观测性（日志/指标/告警）。
- ❌ 禁止在日志中输出敏感信息：密码、密钥、Token、Cookie、私钥、完整证件号/银行卡号等。
- ❌ 禁止使用异常做流程控制：可预测的异常（如 `NullPointerException`）应提前校验而非捕获。
- ❌ 禁止滥用异常：不要用 try-catch 捕获可预测的 RuntimeException，应提前校验。

### 3) 数值、集合与并发（偏 Alibaba）

- ❌ 禁止 `new BigDecimal(double)`；使用 `new BigDecimal(String)` 或 `BigDecimal.valueOf(double)`。
- ❌ 禁止返回 `null` 表示空集合；集合/列表返回必须返回空集合（除非契约明确允许 null 并有文档说明）。
- ❌ 禁止无界线程创建：`new Thread()` / `Executors.newCachedThreadPool()` 等不受控方式（除非在极明确的边界且有审查）。
- ❌ 禁止在并发环境共享可变对象而无保护（锁/并发容器/不可变对象/线程封闭）。
- ❌ 禁止在循环中使用 `+` 拼接字符串；必须使用 `StringBuilder` 或 `StringBuffer`。
- ❌ 禁止使用 `substring` 截取字符串后与原字符串共享 char 数组（Java 7+ 已修复，但需注意性能）。

### 4) 安全与输入处理（工程底线）

- ❌ 禁止信任外部输入（HTTP 参数、Header、JSON、文件内容、MQ 消息等）；必须做合法性校验与边界控制。
- ❌ 禁止将"权限判断/租户隔离"放到 UI 或客户端；服务端必须兜底。
- ❌ 禁止在 SQL 中使用 `${}` 动态拼接；必须使用 `#{}` 参数化查询防止 SQL 注入。

### 5) 日期时间处理（偏 Alibaba）

- ❌ 禁止使用 `java.util.Date` 和 `java.util.Calendar`；必须使用 `java.time.*`（Java 8+）。
- ❌ 禁止在日期格式化时使用 `SimpleDateFormat`（非线程安全）；使用 `DateTimeFormatter`（线程安全）。

### 6) 命名与代码风格（偏 Alibaba）

- ❌ 禁止命名以下划线 `_` 或美元符号 `$` 开始或结束。
- ❌ 禁止中英文混杂或直接使用中文变量/方法名；纯拼音亦推荐避免。
- ❌ 禁止使用魔法值：数字、字符串字面量必须提炼为常量或枚举。

---

## 必须（Must）

### 1) 文件与导入（偏 Google）

- ✅ 源码文件编码统一为 UTF-8。
- ✅ import 必须显式、无 `*`；import 分组与排序必须一致（交由工具/IDE 自动化）。
  - 分组顺序：静态导入（ASCII 排序）→ 空行 → 非静态导入（ASCII 排序）。
- ✅ 一个 `.java` 文件通常只包含一个顶级类；如存在多个，仅允许"极强相关"的同域类型。
- ✅ 文件结构顺序：package → import → 类声明（各段之间空一行）。

### 2) 格式与排版（偏 Google）

- ✅ 缩进：使用 **2 个空格**（Google 标准）或 **4 个空格**（Alibaba 标准，需项目统一）；不使用 tab。
- ✅ 行宽：单行建议 ≤ 100 字符（Google）或 ≤ 120 字符（Alibaba，需项目统一）。
- ✅ 花括号：使用 K&R 风格（左花括号与语句同行），非空块必须使用花括号。
- ✅ 运算符两侧必须有空格：`a + b`、`if (condition)`。
- ✅ 每行一个语句：禁止在同一行写多个语句。
- ✅ 修饰符顺序：遵循 Java 语言规范顺序：`public protected private abstract default static final sealed non-sealed transient volatile synchronized native strictfp`。

### 3) 命名与可读性（基础约束）

- ✅ 命名：类 PascalCase；方法/变量 camelCase；常量全大写下划线；包名全小写。
- ✅ 避免无语义缩写：公共 API、领域对象字段、核心变量必须可读可懂。
- ✅ Magic number / magic string 必须提炼为常量或枚举；不得散落重复字面量。
- ✅ 类名：UpperCamelCase，常见缩写（DO、BO、DTO、VO、AO、PO）可保留大写。
- ✅ 方法名/参数名/成员变量/局部变量：lowerCamelCase。

### 4) 异常处理（偏 Alibaba）

- ✅ 异常必须可定位：错误信息应包含关键业务标识（例如 tenantId/userId/orderId 等）与上下文。
- ✅ 捕获异常后必须选择一种：
  - 处理并恢复；或
  - 转换为业务异常并补充上下文；或
  - 记录并重新抛出（保留 cause）。
- ✅ 对外接口（Controller）不得返回内部堆栈；必须做统一错误响应与脱敏。
- ✅ 异常分类：使用 `BusinessException`（业务异常）、`ValidationException`（参数校验异常）、`SystemException`（系统异常）。
- ✅ 异常链：保留原始异常（`cause`），便于排查问题。

### 5) 资源管理

- ✅ IO/JDBC/Lock 等资源必须对称释放；优先使用 `try-with-resources`。
- ✅ 事务边界必须清晰：避免在事务内执行远程调用/长耗时操作；避免在循环中频繁开启事务。

### 6) 线程池与并发

- ✅ 线程池必须受控：明确 core/max、队列、拒绝策略、线程命名；必须可观测（指标/日志）。
- ✅ 共享数据必须明确线程安全策略：不可变对象优先，其次并发容器，再次锁。
- ✅ 单例获取需线程安全；多个线程/线程池名字要有意义，便于调试追踪。
- ✅ 锁顺序：多个资源锁顺序必须一致以防止死锁。

### 7) 集合与字符串

- ✅ 集合初始化：明确指定初始容量（如 `new ArrayList<>(16)`），避免频繁扩容。
- ✅ 字符串拼接：循环中使用 `StringBuilder`；单次拼接可使用 `+`。
- ✅ 集合判空：使用 `Collection.isEmpty()` 而非 `size() == 0`。

### 8) 日期时间（Java 8+）

- ✅ 使用 `java.time.*` 包：`LocalDateTime`、`ZonedDateTime`、`Instant` 等。
- ✅ 日期格式化：使用 `DateTimeFormatter`（线程安全），不使用 `SimpleDateFormat`。
- ✅ 时区处理：明确指定时区，避免隐式使用系统默认时区。

### 9) Switch 语句（偏 Google）

- ✅ 优先使用新式 switch（箭头语法 `->`），特别是 switch 表达式。
- ✅ 每个 switch 必须有 `default` 分支（除非是枚举且已覆盖所有值）。
- ✅ 旧式 switch 中 fall-through 必须明确注释 `// fall through`。

---

## 应该（Should）

### 1) 代码组织与可测试性

- ⚠️ 方法保持单一职责：单方法过长必须拆分，复杂流程拆到私有方法/领域服务。
- ⚠️ 超过 3-4 个入参优先封装为 Command/Query DTO，提升可演进性。
- ⚠️ 公共方法、复杂算法、关键边界必须写 Javadoc（目的/边界/异常/线程安全）。
- ⚠️ 类职责单一：超大类、过多职责或方法数的类要拆分。

### 2) API 契约与返回

- ⚠️ 对外 API 必须有明确契约：参数约束、返回语义、异常语义。
- ⚠️ Optional 只用于返回值表达"可能不存在"；不用于字段序列化 DTO（除非有明确策略）。
- ⚠️ 接口返回中，不建议直接暴露枚举或者含枚举的 POJO；枚举可以用于入参或内部逻辑。

### 3) 性能与资源

- ⚠️ 热点路径避免重复创建昂贵对象（如 ObjectMapper、正则 Pattern）；应复用或缓存。
- ⚠️ 大数据处理必须分页/流式：避免一次性加载全量集合。
- ⚠️ 正则表达式：复杂正则应预编译为 `Pattern` 对象并复用。

### 4) 日志与可观测性

- ⚠️ 日志必须分级；error 必须携带 throwable。
- ⚠️ 关键链路建议统一打印：tenantId、traceId、userId（按项目日志规范）。
- ⚠️ 日志统一使用 SLF4J 等门面接口，不直接使用具体日志框架。
- ⚠️ 日志文件命名应包含应用名、日志类型、描述，有助于归类管理。
- ⚠️ 日志保留周期至少 15 天。

### 5) 并发与锁

- ⚠️ 减少锁粒度：能锁区块就不锁方法，能用无锁结构就避免锁。
- ⚠️ 高并发更新场景：使用乐观锁（version）或悲观锁处理，乐观锁重试次数建议 ≥ 3 次。

### 6) 数据库与 SQL

- ⚠️ Join 不要超过三张表；被联表字段类型要一致；join 的字段必须有索引。
- ⚠️ 在 varchar 类型字段上建立索引时要指定前缀长度，不要索引整个超长字段。

### 7) 依赖管理

- ⚠️ 二方库依赖要统一版本控制；添加或升级必须评估可能带来的依赖冲突。
- ⚠️ 线上应用不要使用 SNAPSHOT 版本的依赖（除特例如安全补丁包）。

---

## 可以（May）

- 💡 DTO 可使用 Lombok 减少样板代码；Entity/核心领域对象谨慎使用 `@Data`（注意 equals/hashCode 风险）。
- 💡 对热点路径可引入不可变对象（record/immutable DTO），提升线程安全与可推理性（取决于运行时版本）。
- 💡 对映射可使用 MapStruct，但核心链路需权衡可读性与性能。
- 💡 复杂模块先写接口与测试再实现。
- 💡 合理使用设计模式；对系统复杂度要有控制。

---

## 工具链落地建议（用于"把规则变门禁"）

> 本节是建议，不替代你们已有的构建体系；目标是让"规范"可自动化。

### Formatter

- 统一格式化工具（建议 Google Java Format），并在 CI 做 format check。
- IDE 配置：统一使用项目配置的格式化规则，避免个人风格差异。

### 静态检查

- **Checkstyle**：风格与结构检查（基于 Google 或 Alibaba 规则集）。
- **P3C（Alibaba Java Coding Guidelines）**：工程实践检查。
- **SpotBugs/PMD**：潜在缺陷检测。
- **Error Prone**：编译期错误检测（Google 推荐）。

### CI Gate

- PR 必跑（format + lint + unit test），避免"本地格式化但忘提交"。
- 静态检查失败阻止合并。

### 依赖管理

- 使用依赖分析工具（如 `mvn dependency:tree`）检查冲突。
- 定期更新依赖，关注安全漏洞。

---

## 例外与裁决

- 第三方库/生成代码：尽量只做格式化，不强行改语义。
- 遗留代码：允许逐步治理；但新增/改动的代码必须符合本规范。
- 冲突裁决：平台特定规则（90+）优先于本规范；安全规范（40-security）优先于一切。
- 格式冲突：Google 与 Alibaba 在缩进（2 vs 4 空格）、行宽（100 vs 120 字符）上存在差异，需项目统一选择其一。

---

## 示例

### ✅ 正例：必加花括号（可维护）

```java
if (enabled) {
    return;
}
```

### ❌ 反例：省略花括号（维护期易误改）

```java
if (enabled) return;
```

### ✅ 正例：BigDecimal 安全构造

```java
BigDecimal amount = new BigDecimal("0.1");
BigDecimal rate = BigDecimal.valueOf(0.1);
```

### ❌ 反例：double 构造导致精度问题

```java
BigDecimal amount = new BigDecimal(0.1);
```

### ✅ 正例：异常补充上下文 + 统一日志

```java
try {
    service.doWork(cmd);
} catch (BizException e) {
    log.warn("tenantId={}, userId={}, work failed: {}", tenantId, userId, e.getMessage(), e);
    throw e;
} catch (Exception e) {
    log.error("tenantId={}, userId={}, unexpected error", tenantId, userId, e);
    throw new SystemException("WORK_FAILED", e);
}
```

### ❌ 反例：吞异常 + 返回 null

```java
public User get(Long id) {
    try {
        return repo.findById(id);
    } catch (Exception e) {
        return null;
    }
}
```

### ✅ 正例：日期时间处理（Java 8+）

```java
// 使用 java.time.*
LocalDateTime now = LocalDateTime.now();
ZonedDateTime zoned = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"));
DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
String formatted = now.format(formatter);
```

### ❌ 反例：使用过时的 Date/SimpleDateFormat

```java
// 错误：非线程安全、API 设计不佳
Date date = new Date();
SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
String formatted = sdf.format(date);
```

### ✅ 正例：字符串拼接（循环中使用 StringBuilder）

```java
StringBuilder sb = new StringBuilder();
for (String item : items) {
    sb.append(item).append(",");
}
String result = sb.toString();
```

### ❌ 反例：循环中使用 + 拼接

```java
String result = "";
for (String item : items) {
    result += item + ","; // ❌ 性能差，每次创建新对象
}
```

### ✅ 正例：集合初始化指定容量

```java
List<String> list = new ArrayList<>(16); // 明确初始容量
Map<String, Object> map = new HashMap<>(32);
```

### ❌ 反例：集合未指定初始容量

```java
List<String> list = new ArrayList<>(); // ❌ 默认容量小，可能频繁扩容
```

### ✅ 正例：Switch 语句（新式语法）

```java
String result = switch (status) {
    case "ACTIVE" -> "激活";
    case "INACTIVE" -> "未激活";
    default -> "未知";
};
```

### ✅ 正例：Switch 语句（旧式，带 fall-through 注释）

```java
switch (type) {
    case TYPE_A:
    case TYPE_B:
        handleTypeAB();
        // fall through
    case TYPE_C:
        handleTypeC();
        break;
    default:
        handleDefault();
}
```

### ✅ 正例：线程池配置

```java
ThreadPoolExecutor executor = new ThreadPoolExecutor(
    5,  // corePoolSize
    10, // maximumPoolSize
    60L, TimeUnit.SECONDS, // keepAliveTime
    new LinkedBlockingQueue<>(100), // workQueue
    new ThreadFactoryBuilder().setNameFormat("task-%d").build(), // threadFactory
    new ThreadPoolExecutor.CallerRunsPolicy() // rejectionPolicy
);
```

### ❌ 反例：无界线程池

```java
ExecutorService executor = Executors.newCachedThreadPool(); // ❌ 无界，可能 OOM
```

### ✅ 正例：资源管理（try-with-resources）

```java
try (BufferedReader reader = Files.newBufferedReader(Paths.get(path));
     BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputPath))) {
    // 使用资源
} catch (IOException e) {
    log.error("IO error", e);
    throw new SystemException("IO_FAILED", e);
}
```

### ❌ 反例：手动管理资源

```java
BufferedReader reader = null;
try {
    reader = Files.newBufferedReader(Paths.get(path));
    // 使用资源
} catch (IOException e) {
    // 处理异常
} finally {
    if (reader != null) {
        try {
            reader.close(); // ❌ 可能忘记关闭或异常处理复杂
        } catch (IOException e) {
            // 忽略
        }
    }
}
```

### ✅ 正例：集合判空

```java
if (collection.isEmpty()) {
    return Collections.emptyList();
}
```

### ❌ 反例：使用 size() 判空

```java
if (collection.size() == 0) { // ❌ 性能略差，语义不清晰
    return null;
}
```

### ✅ 正例：数组声明

```java
String[] args = new String[10]; // ✅ 类型声明在前
```

### ❌ 反例：C 风格数组声明

```java
String args[] = new String[10]; // ❌ C 风格，不符合 Java 习惯
```

### ✅ 正例：long 字面量

```java
long count = 3000L; // ✅ 使用大写 L
```

### ❌ 反例：long 字面量小写

```java
long count = 3000l; // ❌ 小写 l 容易与数字 1 混淆
```
