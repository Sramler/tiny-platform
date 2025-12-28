# 错误码设计：HTTP 状态码前缀方案分析

## 问题背景

当前 `ErrorCode` 的设计：
- 400 系列：1001, 1002, 1003, 1004, 1005, 1006
- 401 系列：2001, 2002, 2003, 2004
- 403 系列：3001, 3002
- 409 系列：4001, 4002, 4003
- 500 系列：5001, 5002, 5003

**问题**：错误码与 HTTP 状态码没有直接关联，不够直观。

## 方案对比

### 方案 1：当前设计（非 HTTP 状态码前缀）

```java
VALIDATION_ERROR(1001, "参数校验失败", HttpStatus.BAD_REQUEST),
UNAUTHORIZED(2001, "未授权", HttpStatus.UNAUTHORIZED),
ACCESS_DENIED(3001, "拒绝访问", HttpStatus.FORBIDDEN),
IDEMPOTENT_CONFLICT(4001, "请勿重复提交", HttpStatus.CONFLICT),
INTERNAL_ERROR(5001, "服务器内部错误", HttpStatus.INTERNAL_SERVER_ERROR),
```

**优点**：
- ✅ 错误码简短（4位数字）
- ✅ 已有代码无需大量修改

**缺点**：
- ❌ 错误码与 HTTP 状态码没有直接关联
- ❌ 从错误码无法直接看出对应的 HTTP 状态码
- ❌ 不够直观，需要查表才能知道对应关系
- ❌ 不符合 REST API 设计规范

### 方案 2：HTTP 状态码前缀设计（推荐 ⭐⭐⭐⭐⭐）

```java
VALIDATION_ERROR(40001, "参数校验失败", HttpStatus.BAD_REQUEST),
MISSING_PARAMETER(40002, "缺少参数", HttpStatus.BAD_REQUEST),
INVALID_PARAMETER(40003, "无效的参数", HttpStatus.BAD_REQUEST),
METHOD_NOT_SUPPORTED(40501, "请求方法不支持", HttpStatus.METHOD_NOT_ALLOWED),
NOT_FOUND(40401, "资源不存在", HttpStatus.NOT_FOUND),

UNAUTHORIZED(40101, "未授权", HttpStatus.UNAUTHORIZED),
TOKEN_EXPIRED(40102, "令牌已过期", HttpStatus.UNAUTHORIZED),
INVALID_TOKEN(40103, "无效的令牌", HttpStatus.UNAUTHORIZED),

ACCESS_DENIED(40301, "拒绝访问", HttpStatus.FORBIDDEN),
FORBIDDEN(40302, "没有权限", HttpStatus.FORBIDDEN),

IDEMPOTENT_CONFLICT(40901, "请勿重复提交", HttpStatus.CONFLICT),
BUSINESS_ERROR(40902, "业务处理失败", HttpStatus.CONFLICT),
RESOURCE_CONFLICT(40903, "资源冲突", HttpStatus.CONFLICT),

INTERNAL_ERROR(50001, "服务器内部错误", HttpStatus.INTERNAL_SERVER_ERROR),
SERVICE_UNAVAILABLE(50301, "服务不可用", HttpStatus.SERVICE_UNAVAILABLE),
```

**优点**：
- ✅ **直观性**：从错误码直接看出对应的 HTTP 状态码（如 40001 → 400）
- ✅ **符合规范**：符合 REST API 设计最佳实践
- ✅ **便于调试**：日志中看到错误码就能知道 HTTP 状态码
- ✅ **前端友好**：前端可以根据错误码前缀快速判断错误类型
- ✅ **可扩展性**：每个 HTTP 状态码可以支持最多 99 个子错误码（40001-40099）
- ✅ **自文档化**：错误码本身就是文档

**缺点**：
- ❌ 错误码变长（5位数字）
- ⚠️ 需要修改现有代码（但影响范围可控）

## 设计规范

### 错误码格式

```
HTTP状态码 + 2位序号 = 5位错误码
```

**示例**：
- `40001` = HTTP 400 + 序号 01（参数校验失败）
- `40101` = HTTP 401 + 序号 01（未授权）
- `40301` = HTTP 403 + 序号 01（拒绝访问）
- `40901` = HTTP 409 + 序号 01（幂等性冲突）
- `50001` = HTTP 500 + 序号 01（服务器内部错误）

### 序号分配规则

1. **01-09**：通用错误（如 40001 参数校验失败）
2. **10-19**：参数相关错误（如 40010 缺少参数）
3. **20-29**：数据验证错误（如 40020 数据格式错误）
4. **30-39**：业务逻辑错误（如 40930 资源冲突）
5. **90-99**：预留扩展

### 特殊状态码处理

- **405 Method Not Allowed**：`40501`, `40502`, ...
- **415 Unsupported Media Type**：`41501`, `41502`, ...
- **422 Unprocessable Entity**：`42201`, `42202`, ...
- **503 Service Unavailable**：`50301`, `50302`, ...

## 迁移方案

### 阶段 1：添加新错误码（向后兼容）

```java
// 保留旧错误码，添加新错误码
@Deprecated
VALIDATION_ERROR_OLD(1001, "参数校验失败", HttpStatus.BAD_REQUEST),
VALIDATION_ERROR(40001, "参数校验失败", HttpStatus.BAD_REQUEST),
```

### 阶段 2：逐步迁移

1. 新代码使用新错误码
2. 旧代码逐步迁移
3. 前端同时支持新旧错误码

### 阶段 3：移除旧错误码

在所有代码迁移完成后，移除旧错误码。

## 前端处理示例

```typescript
// 前端可以根据错误码前缀快速判断错误类型
function handleError(error: any) {
  const code = error.code || error.response?.data?.code
  const httpStatus = Math.floor(code / 100)  // 40001 → 400
  
  switch (httpStatus) {
    case 400:
      // 客户端错误，显示错误提示
      message.error(error.message)
      break
    case 401:
      // 未授权，跳转登录页
      router.push('/login')
      break
    case 403:
      // 无权限，显示 403 页面
      router.push('/exception/403')
      break
    case 409:
      // 资源冲突，可能需要确认
      if (code === 40903) {  // RESOURCE_CONFLICT
        Modal.confirm({
          title: '确认操作',
          content: error.detail,
          onOk: () => handleConfirm()
        })
      }
      break
    case 500:
      // 服务器错误，显示 500 页面
      router.push('/exception/500')
      break
  }
}
```

## 总结

| 方案 | 直观性 | 规范性 | 可维护性 | 推荐度 |
|------|--------|--------|---------|--------|
| 当前设计（非前缀） | ⭐⭐ | ⭐⭐ | ⭐⭐⭐ | ⭐⭐ |
| HTTP 状态码前缀 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |

**结论**：使用 HTTP 状态码前缀设计更合理，符合 REST API 最佳实践，提高代码可读性和可维护性。

