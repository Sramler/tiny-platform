# 85 文档规范

## 适用范围

- 适用于：`**/*.md`、`**/README.md`、`**/CHANGELOG.md`、代码注释（Javadoc/TSDoc）
- 不适用于：第三方库文档（但使用时应参考其文档）

## 总体策略

1. **文档即代码**：文档与代码同等重要，应同步更新。
2. **清晰简洁**：文档应清晰、简洁、易于理解。
3. **及时更新**：代码变更时同步更新相关文档。

---

## 禁止（Must Not）

### 1) 文档内容

- ❌ 文档中包含过时信息（应及时更新或删除）。
- ❌ 文档中包含敏感信息（密码、密钥、Token 等）。
- ❌ 文档中硬编码环境特定信息（应使用占位符或说明）。

### 2) 文档格式

- ❌ 文档格式不规范（应使用 Markdown，保持格式一致）。
- ❌ 文档缺少必要的章节（如 README 应包含概述、安装、使用等）。

### 3) 代码注释

- ❌ 代码注释与代码不一致（应及时更新）。
- ❌ 代码注释只描述"是什么"而不解释"为什么"。

---

## 必须（Must）

### 1) README 文档

- ✅ README 必须包含：项目概述、快速开始、安装说明、使用示例。
- ✅ README 必须包含：环境要求、配置说明、常见问题。
- ✅ README 必须包含：贡献指南、许可证信息。

### 2) API 文档

- ✅ API 文档：使用 Swagger/OpenAPI 标注接口，生成 API 文档。
- ✅ API 文档必须包含：接口说明、参数说明、返回值说明、错误码说明。

### 3) 代码注释

- ✅ 公共 API：所有公共类、方法、接口必须有文档注释（Javadoc/TSDoc）。
- ✅ 复杂逻辑：复杂业务逻辑必须有注释，解释"为什么"而非"是什么"。

### 4) 变更日志

- ✅ CHANGELOG：重要变更应记录在 CHANGELOG.md 中。
- ✅ 变更记录：变更记录应包含版本号、变更日期、变更内容。

---

## 应该（Should）

### 1) 文档组织

- ⚠️ 文档结构：文档应结构清晰，使用目录导航。
- ⚠️ 文档分类：按功能分类组织文档（如 API、配置、部署等）。

### 2) 文档示例

- ⚠️ 代码示例：文档应包含可运行的代码示例。
- ⚠️ 示例更新：代码示例应与代码同步更新。

### 3) 文档维护

- ⚠️ 文档审查：文档变更应经过审查，确保准确性。
- ⚠️ 文档版本：文档应标注版本号，便于追踪。

---

## 可以（May）

- 💡 使用文档生成工具：如 JSDoc、Swagger、GitBook 等。
- 💡 文档国际化：支持多语言文档（如中英文）。

---

## 例外与裁决

### 内部文档

- 内部文档：内部文档可简化格式，但应保持清晰。

### 第三方库

- 第三方库：第三方库文档遵循其规范，不强制修改。

### 冲突裁决

- 平台特定规则（90+）优先于本规范。
- 文档规范与业务需求冲突时，优先保证文档实用性。

---

## 示例

### ✅ 正例：README 文档结构

```markdown
# Tiny Platform

## 项目概述

Tiny Platform 是一个插件化单体 + All-in-One + 多租户的企业级平台。

## 快速开始

### 环境要求

- Java 21+
- Maven 3.8+
- MySQL 8.0+
- Node.js 18+

### 安装步骤

1. 克隆项目
   ```bash
   git clone https://github.com/example/tiny-platform.git
   cd tiny-platform
   ```

2. 配置数据库
   ```bash
   # 修改 application-dev.yaml 中的数据库配置
   ```

3. 启动服务
   ```bash
   mvn spring-boot:run
   ```

## 配置说明

详见 [配置文档](./docs/CONFIG.md)

## API 文档

访问 http://localhost:9000/swagger-ui.html 查看 API 文档

## 常见问题

### Q: 如何配置多租户？
A: 详见 [多租户配置文档](./docs/MULTI_TENANT.md)

## 贡献指南

详见 [贡献指南](./CONTRIBUTING.md)

## 许可证

MIT License
```

### ✅ 正例：Javadoc 注释

```java
/**
 * 用户服务接口
 * 
 * <p>提供用户相关的业务操作，包括用户查询、创建、更新、删除等。
 * 所有操作都包含多租户隔离，确保数据安全。
 * 
 * @author Tiny Platform
 * @since 1.0.0
 */
@Service
public class UserService {
    
    /**
     * 根据ID获取用户
     * 
     * <p>该方法会自动从 {@link TenantContext} 获取当前租户ID，
     * 确保只返回当前租户的用户数据。
     * 
     * @param id 用户ID，不能为 null
     * @return 用户DTO，如果用户不存在则返回 null
     * @throws BusinessException 如果用户不存在或不属于当前租户
     * @see TenantContext#getCurrentTenantId()
     */
    public UserDTO getUserById(Long id) {
        // 实现逻辑
    }
}
```

### ✅ 正例：TSDoc 注释

```typescript
/**
 * 用户服务
 * 
 * 提供用户相关的业务操作，包括用户查询、创建、更新、删除等。
 * 所有操作都包含多租户隔离，确保数据安全。
 * 
 * @example
 * ```typescript
 * const userService = new UserService()
 * const user = await userService.getUserById(1)
 * ```
 */
export class UserService {
    /**
     * 根据ID获取用户
     * 
     * @param id - 用户ID，不能为 null
     * @returns 用户DTO，如果用户不存在则返回 null
     * @throws {BusinessException} 如果用户不存在或不属于当前租户
     */
    async getUserById(id: number): Promise<UserDTO | null> {
        // 实现逻辑
    }
}
```

### ✅ 正例：API 文档（Swagger）

```java
@RestController
@RequestMapping("/api/users")
@Api(tags = "用户管理")
public class UserController {
    
    @GetMapping("/{id}")
    @ApiOperation(value = "获取用户", notes = "根据ID获取用户详情")
    @ApiResponses({
        @ApiResponse(code = 200, message = "成功"),
        @ApiResponse(code = 404, message = "用户不存在"),
        @ApiResponse(code = 403, message = "无权限")
    })
    public ResponseEntity<GlobalResponse<UserDTO>> getUser(
            @ApiParam(value = "用户ID", required = true, example = "1")
            @PathVariable Long id) {
        // 实现逻辑
    }
}
```

### ✅ 正例：CHANGELOG

```markdown
# Changelog

All notable changes to this project will be documented in this file.

## [1.1.0] - 2024-01-15

### Added
- 添加用户头像上传功能
- 支持图片格式：jpg, png, webp
- 图片大小限制：5MB

### Changed
- 优化用户查询性能，使用缓存

### Fixed
- 修复用户列表分页问题
- 修复多租户数据隔离问题

## [1.0.0] - 2024-01-01

### Added
- 初始版本发布
- 用户管理功能
- 多租户支持
```

### ✅ 正例：代码注释（解释为什么）

```java
// 使用乐观锁 version 字段防止并发修改
// 风险：并发修改可能导致数据不一致
// 策略：使用乐观锁 version 字段，更新时检查 version
public void updateUser(User user) {
    // 实现逻辑
}
```

### ❌ 反例：代码注释只描述是什么

```java
// 更新用户 ❌ 只描述是什么，不解释为什么
public void updateUser(User user) {
    // 实现逻辑
}
```
