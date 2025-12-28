# ErrorCode 设计分析：CRUD 操作和系统提示的通用性

## 问题分析

当前 `ErrorCode` 设计是否覆盖了 CRUD 常规操作和系统提示的通用场景？

## CRUD 操作常见错误场景

### 1. Create (创建)

| 错误场景 | 当前覆盖 | 建议错误码 | HTTP状态码 |
|---------|---------|-----------|-----------|
| 参数验证失败 | ✅ 40001 VALIDATION_ERROR | 40001 | 400 |
| 缺少必填参数 | ✅ 40002 MISSING_PARAMETER | 40002 | 400 |
| 参数格式错误 | ✅ 40003 INVALID_PARAMETER | 40003 | 400 |
| **数据已存在（唯一性冲突）** | ❌ 无 | **40904 RESOURCE_ALREADY_EXISTS** | 409 |
| 无权限创建 | ✅ 40301 ACCESS_DENIED | 40301 | 403 |
| 未授权 | ✅ 40101 UNAUTHORIZED | 40101 | 401 |

### 2. Read (查询)

| 错误场景 | 当前覆盖 | 建议错误码 | HTTP状态码 |
|---------|---------|-----------|-----------|
| 资源不存在 | ✅ 40401 NOT_FOUND | 40401 | 404 |
| 参数错误（如ID格式错误） | ✅ 40003 INVALID_PARAMETER | 40003 | 400 |
| 无权限访问 | ✅ 40301 ACCESS_DENIED | 40301 | 403 |
| 未授权 | ✅ 40101 UNAUTHORIZED | 40101 | 401 |

### 3. Update (更新)

| 错误场景 | 当前覆盖 | 建议错误码 | HTTP状态码 |
|---------|---------|-----------|-----------|
| 资源不存在 | ✅ 40401 NOT_FOUND | 40401 | 404 |
| 参数验证失败 | ✅ 40001 VALIDATION_ERROR | 40001 | 400 |
| **数据冲突（名称/唯一字段被使用）** | ⚠️ 40903 RESOURCE_CONFLICT | 40903 | 409 |
| **资源状态不允许更新** | ⚠️ 40903 RESOURCE_CONFLICT | **40905 RESOURCE_STATE_INVALID** | 409 |
| 无权限更新 | ✅ 40301 ACCESS_DENIED | 40301 | 403 |
| 未授权 | ✅ 40101 UNAUTHORIZED | 40101 | 401 |

### 4. Delete (删除)

| 错误场景 | 当前覆盖 | 建议错误码 | HTTP状态码 |
|---------|---------|-----------|-----------|
| 资源不存在 | ✅ 40401 NOT_FOUND | 40401 | 404 |
| **资源被引用（有子资源、被关联）** | ✅ 40903 RESOURCE_CONFLICT | 40903 | 409 |
| **资源状态不允许删除** | ⚠️ 40903 RESOURCE_CONFLICT | **40905 RESOURCE_STATE_INVALID** | 409 |
| 无权限删除 | ✅ 40301 ACCESS_DENIED | 40301 | 403 |
| 未授权 | ✅ 40101 UNAUTHORIZED | 40101 | 401 |

## 当前代码中的错误场景（未使用 ErrorCode）

从代码扫描发现以下错误仍在使用 `RuntimeException`：

```java
// ResourceServiceImpl.java
throw new RuntimeException("资源名称已存在");        // → 40904 RESOURCE_ALREADY_EXISTS
throw new RuntimeException("资源URL已存在");          // → 40904 RESOURCE_ALREADY_EXISTS
throw new RuntimeException("资源URI已存在");         // → 40904 RESOURCE_ALREADY_EXISTS
throw new RuntimeException("资源名称已被其他资源使用"); // → 40903 RESOURCE_CONFLICT

// MenuServiceImpl.java
throw new RuntimeException("不能将自己设置为父菜单");  // → 40903 RESOURCE_CONFLICT
throw new RuntimeException("父菜单必须是目录类型");     // → 40001 VALIDATION_ERROR
throw new RuntimeException("设置此父菜单会造成循环引用"); // → 40903 RESOURCE_CONFLICT
```

## 缺失的通用错误码

### 1. 数据唯一性冲突（40904）

**场景**：
- 创建时：用户名已存在、邮箱已存在、资源名称已存在
- 更新时：新名称已被其他资源使用

**建议**：
```java
RESOURCE_ALREADY_EXISTS(40904, "资源已存在", HttpStatus.CONFLICT),
```

### 2. 资源状态无效（40905）

**场景**：
- 资源状态不允许更新（如已归档的资源）
- 资源状态不允许删除（如已发布的资源）
- 资源状态不允许操作（如已锁定的资源）

**建议**：
```java
RESOURCE_STATE_INVALID(40905, "资源状态不允许此操作", HttpStatus.CONFLICT),
```

### 3. 数据完整性约束（40906）

**场景**：
- 循环引用（如菜单父子关系）
- 数据关联约束（如不能删除有子资源的资源）

**建议**：
```java
DATA_INTEGRITY_VIOLATION(40906, "数据完整性约束违反", HttpStatus.CONFLICT),
```

**注意**：这个可以合并到 `RESOURCE_CONFLICT(40903)` 中，通过详细错误信息区分。

## 系统提示的通用性

### 当前覆盖情况

| 系统提示类型 | 当前覆盖 | 建议 |
|------------|---------|------|
| 操作成功 | ✅ 20000 SUCCESS | 已覆盖 |
| 参数验证 | ✅ 40001-40003 | 已覆盖 |
| 资源不存在 | ✅ 40401 NOT_FOUND | 已覆盖 |
| 权限检查 | ✅ 40101-40104, 40301-40302 | 已覆盖 |
| 业务冲突 | ⚠️ 40901-40903 | **需要补充 40904-40906** |
| 服务器错误 | ✅ 50001, 50301, 50099 | 已覆盖 |

## 优化建议

### 方案 1：补充缺失的错误码（推荐 ⭐⭐⭐⭐⭐）

```java
// ==================== 业务错误 (409) ====================
IDEMPOTENT_CONFLICT(40901, "请勿重复提交", HttpStatus.CONFLICT),
BUSINESS_ERROR(40902, "业务处理失败", HttpStatus.CONFLICT),
RESOURCE_CONFLICT(40903, "资源冲突", HttpStatus.CONFLICT),
RESOURCE_ALREADY_EXISTS(40904, "资源已存在", HttpStatus.CONFLICT),  // 新增
RESOURCE_STATE_INVALID(40905, "资源状态不允许此操作", HttpStatus.CONFLICT),  // 新增
```

### 方案 2：使用通用错误码 + 详细错误信息（当前方案）

保持当前设计，通过 `RESOURCE_CONFLICT(40903)` + 详细错误信息来区分不同场景：

```java
// 资源已存在
throw new BusinessException(
    ErrorCode.RESOURCE_CONFLICT,
    "资源名称已存在: " + name
);

// 资源状态不允许
throw new BusinessException(
    ErrorCode.RESOURCE_CONFLICT,
    "资源状态不允许删除，当前状态: " + status
);
```

**优点**：
- 错误码数量可控
- 前端可以根据错误信息灵活处理

**缺点**：
- 前端需要解析错误信息（不够稳定）
- 错误统计不够精确

## 最终建议

### 推荐方案：补充关键错误码

1. **必须补充**：`RESOURCE_ALREADY_EXISTS(40904)` - 数据唯一性冲突是 CRUD 操作中最常见的错误之一
2. **建议补充**：`RESOURCE_STATE_INVALID(40905)` - 资源状态管理是常见业务场景
3. **可选补充**：`DATA_INTEGRITY_VIOLATION(40906)` - 如果数据完整性约束错误较多，可以单独定义

### 错误码分配建议

```
40901: 幂等性冲突
40902: 通用业务错误
40903: 资源冲突（通用）
40904: 资源已存在（唯一性冲突）
40905: 资源状态无效
40906-40909: 预留扩展
40910-40999: 业务特定错误
```

## 总结

| 维度 | 当前覆盖 | 建议 |
|------|---------|------|
| CRUD 基本操作 | ✅ 90% | 补充 40904, 40905 |
| 系统提示通用性 | ✅ 85% | 补充 40904, 40905 |
| 错误码数量 | 适中 | 增加 2-3 个 |
| 可维护性 | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |

**结论**：当前设计基本覆盖了 CRUD 操作和系统提示的通用场景，但建议补充 `RESOURCE_ALREADY_EXISTS` 和 `RESOURCE_STATE_INVALID` 两个关键错误码，以提高错误处理的精确性和可维护性。

