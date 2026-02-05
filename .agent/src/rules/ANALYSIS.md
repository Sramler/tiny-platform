# .agent/src/rules/ 规则文件分析与完善建议

> 分析日期：2026-01-11  
> 规则系统版本：v2.3.1

---

## 📊 现有规则文件概览

### 已存在的规则文件（13 个）

| 文件 | 主题 | 行数 | 状态 |
|------|------|------|------|
| `00-base.rules.md` | 全局基础规范 | ~30 | ✅ 完整 |
| `10-java.rules.md` | Java 编码规范 | ~40 | ✅ 完整 |
| `20-vue.rules.md` | Vue 3 编码规范 | ~40 | ✅ 完整 |
| `30-antdv.rules.md` | Ant Design Vue 规范 | ~40 | ✅ 完整 |
| `40-security.rules.md` | 安全规范（全局红线） | ~40 | ✅ 完整 |
| `50-testing.rules.md` | 测试规范 | ~45 | ✅ 完整 |
| `60-git.rules.md` | Git 规范 | ~40 | ✅ 完整 |
| `70-database.rules.md` | 数据库/SQL 规范 | ~85 | ✅ 完整 |
| `75-exception.rules.md` | 异常处理规范 | ~70 | ✅ 完整 |
| `76-idempotent.rules.md` | 幂等性规范 | ~60 | ✅ 完整 |
| `80-api.rules.md` | REST API 设计规范 | ~80 | ✅ 完整 |
| `90-tiny-platform.rules.md` | 平台特定规则 | ~35 | ✅ 完整 |
| `91-tiny-platform-auth.rules.md` | OAuth2/认证规范 | ~70 | ✅ 完整 |

**总计**：~588 行，13 个规则文件

---

## ✅ 格式规范检查

### 符合 v2.3.1 规范 ✅

所有规则文件都符合以下要求：

1. ✅ **工具无关**：不包含 Cursor 专属 frontmatter（alwaysApply/globs/description）
2. ✅ **结构统一**：都包含"适用范围"、"禁止"、"必须"、"应该"、"可以"等章节
3. ✅ **命名规范**：使用 `XX-主题.rules.md` 格式
4. ✅ **编号规范**：使用 00/10/20/30/40/50/60/70/75/76/80/90/91 编号体系

---

## ✅ 已补充的重要领域

根据项目实际情况（tiny-platform：插件化单体 + OAuth2 + 多租户 + 幂等平台），以下领域**已完成补充**：

### 1. 数据库/SQL 规范（✅ 已完成）

**缺失原因**：
- 项目使用 MySQL，有大量数据库操作
- 有 Liquibase 迁移、数据库同步等需求
- 有 DAG 任务调度系统等复杂表结构

**文件**：`70-database.rules.md` ✅

**已包含内容**：
- SQL 命名规范（表名单数、字段命名、索引命名）
- 事务隔离级别规范（READ-COMMITTED）
- 外键约束策略（应用层控制 vs 数据库约束）
- 索引设计原则（外键索引、常用查询字段索引）
- 时间字段规范（DATETIME vs TIMESTAMP）
- 字符集规范（utf8mb4）
- 表结构注释规范
- Liquibase changelog 编写规范

**触发条件**：`globs: ["**/*.sql", "**/schema.sql", "**/changelog/**"]` ✅

---

### 2. REST API 设计规范（✅ 已完成）

**缺失原因**：
- 项目有 OAuth2 授权服务器、资源服务器
- 有统一异常响应格式需求（ErrorResponse）
- 有 API 版本化、路径设计等需求

**文件**：`80-api.rules.md` ✅

**已包含内容**：
- RESTful 路径设计规范（资源名词、HTTP 方法）
- 统一响应格式（ErrorResponse、统一错误码）
- HTTP 状态码使用规范
- 请求/响应参数命名规范（camelCase）
- API 版本化策略（路径版本 vs 头版本）
- 分页参数规范（page/size vs offset/limit）
- 异常响应格式（RFC 7807 Problem Details）
- OAuth2 端点规范（/oauth2/authorize, /oauth2/token）

**触发条件**：`globs: ["**/controller/**", "**/api/**", "**/*Controller.java"]` ✅

---

### 3. OAuth2/认证规范（✅ 已完成）

**缺失原因**：
- 项目有 OAuth2 授权服务器、客户端、资源服务器
- 有 JWT Token Claims 规范需求
- 有多认证方式（PASSWORD/TOTP）需求
- 有 RS256 JWT + JWK Set + MFA(TOTP) 安全策略

**文件**：`91-tiny-platform-auth.rules.md`（平台特定）✅

**已包含内容**：
- OAuth2 授权流程规范（authorization_code, refresh_token）
- JWT Token Claims 规范（标准字段 + 企业级字段）
- 认证方式选择规范（按客户端来源切换 JWT/Session）
- 多认证方式实现规范（PASSWORD/TOTP）
- 客户端配置规范（client_id, redirect_uris, scopes）
- Token 过期与刷新策略
- 安全规范（RS256、JWK Set、MFA）

**触发条件**：`globs: ["**/oauth2/**", "**/auth/**", "**/security/**"]` ✅

---

### 4. 多租户规范（✅ 已完成）

**缺失原因**：
- 项目强调"多租户"是核心能力
- 有租户隔离、租户级别权限控制需求
- 有租户级别的唯一性约束需求

**文件**：`90-tiny-platform.rules.md` ✅（已增强）

**已补充内容**：
- 租户隔离维度（数据隔离、权限隔离）
- 租户 ID 传递规范（请求头、Token Claims、上下文）
- 租户级别查询规范（必须包含 tenant_id 过滤）
- 租户级别唯一性约束规范（tenant_id + 业务字段）
- 租户数据迁移规范
- 租户级别资源限制规范

**当前状态**：已补充详细规范 ✅

---

### 5. 异常处理规范（✅ 已完成）

**缺失原因**：
- 项目有 `tiny-common-exception` 模块
- 有统一异常响应格式需求
- 有异常处理最佳实践需求

**文件**：`75-exception.rules.md` ✅

**已包含内容**：
- 异常分类规范（BusinessException, ValidationException, SystemException）
- 统一异常响应格式（ErrorResponse）
- 异常处理层次（Controller → Service → Repository）
- 异常信息脱敏规范（不泄漏敏感数据）
- 异常日志记录规范（级别、内容、上下文）
- 异常码规范（统一错误码体系）

**触发条件**：`globs: ["**/exception/**", "**/*Exception*.java", "**/handler/**"]` ✅

---

### 6. 幂等性规范（✅ 已完成）

**缺失原因**：
- 项目有 `tiny-idempotent-platform` 模块
- 有幂等性保证需求（防止重复提交）

**文件**：`76-idempotent.rules.md` ✅

**已包含内容**：
- 幂等性实现方式（Token、唯一键、状态机）
- 幂等性 Key 设计规范（业务维度 + 唯一标识）
- 幂等性过期时间规范
- 幂等性异常处理规范（IdempotentException）
- 幂等性测试规范

**触发条件**：`globs: ["**/idempotent/**", "**/*Idempotent*.java"]` ✅

---

## 📝 规则文件格式完善

### 已完成补充

所有规则文件已包含完整的章节结构：
- ✅ "适用范围"（包含"不适用于"说明）
- ✅ "禁止（Must Not）"
- ✅ "必须（Must）"
- ✅ "应该（Should）"
- ✅ "可以（May）"
- ✅ "例外与裁决"
- ✅ "示例"（正例/反例）

### 格式统一性

- ✅ 所有规则文件遵循统一的格式模板
- ✅ 新规则文件（70/75/76/80/91）已包含完整章节
- ✅ 旧规则文件（00/10/20/30/40/50/60/90）已补充完整章节

---

## 📋 规则文件格式模板

所有新增规则文件应遵循以下格式：

```markdown
# XX 主题规范

## 适用范围

- 适用于：`**/*.java`（或其他 globs 模式）
- 不适用于：xxx（如有）

## 禁止（Must Not）

- ❌ ...

## 必须（Must）

- ✅ ...

## 应该（Should）

- ⚠️ ...

## 可以（May）

- 💡 ...

## 例外与裁决

- 允许例外的条件
- 冲突时如何裁决（引用第 4 章裁决原则）

## 示例

- ✅ 正例
- ❌ 反例
```

---

## 🔧 下一步行动

1. **创建缺失的规则文件**（按优先级）
2. **更新 `rules-map.json`**（添加新规则的映射配置）
3. **运行构建和验证**：
   ```bash
   .agent/build/build.sh --target cursor --cursor-format mdc
   .agent/build/validate.sh --target cursor --cursor-format mdc
   ```
4. **更新 AGENTS.md**（如有需要）

---

## 📚 参考文档

- `.agent/src/map/rules-map.json` - 规则映射配置
- `docs/agent-rules-structure-analysis.md` - 规则系统架构文档
- `AGENTS.md` - 项目 AI 协作说明
