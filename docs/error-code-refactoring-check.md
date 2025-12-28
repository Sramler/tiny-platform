# ErrorCode 重构检查报告

## 检查范围
- 后端：`tiny-oauth-server/src/main/java`
- 前端：`tiny-oauth-server/src/main/webapp/src`

## 检查结果

### 1. 后端问题

#### ❌ 问题 1：大量使用 `RuntimeException` 而不是 `BusinessException`

**位置**：`ResourceServiceImpl.java`
```java
// 需要重构的地方
throw new RuntimeException("资源名称已存在");           // → ErrorCode.RESOURCE_ALREADY_EXISTS(40904)
throw new RuntimeException("资源URL已存在");            // → ErrorCode.RESOURCE_ALREADY_EXISTS(40904)
throw new RuntimeException("资源URI已存在");            // → ErrorCode.RESOURCE_ALREADY_EXISTS(40904)
throw new RuntimeException("资源名称已被其他资源使用");   // → ErrorCode.RESOURCE_ALREADY_EXISTS(40904)
throw new RuntimeException("资源URL已被其他资源使用");   // → ErrorCode.RESOURCE_ALREADY_EXISTS(40904)
throw new RuntimeException("资源URI已被其他资源使用");   // → ErrorCode.RESOURCE_ALREADY_EXISTS(40904)
throw new RuntimeException("资源不存在");               // → ErrorCode.NOT_FOUND(40401)
```

**位置**：`MenuServiceImpl.java`
```java
throw new RuntimeException("不能将自己设置为父菜单");    // → ErrorCode.RESOURCE_CONFLICT(40903)
throw new RuntimeException("父菜单必须是目录类型");       // → ErrorCode.VALIDATION_ERROR(40001)
throw new RuntimeException("设置此父菜单会造成循环引用");  // → ErrorCode.RESOURCE_CONFLICT(40903)
throw new RuntimeException("父菜单不存在");              // → ErrorCode.NOT_FOUND(40401)
```

**位置**：`UserServiceImpl.java`
```java
throw new RuntimeException("用户名已存在");             // → ErrorCode.RESOURCE_ALREADY_EXISTS(40904)
throw new RuntimeException("用户名已被其他用户使用");     // → ErrorCode.RESOURCE_ALREADY_EXISTS(40904)
throw new RuntimeException("部分角色不存在");            // → ErrorCode.NOT_FOUND(40401)
throw new RuntimeException("部分用户不存在");            // → ErrorCode.NOT_FOUND(40401)
```

#### ⚠️ 问题 2：使用了 `BusinessException` 但错误码不够精确

**位置**：`DictTypeServiceImpl.java`, `DictItemServiceImpl.java`
```java
// 当前使用
throw new BusinessException(ErrorCode.BUSINESS_ERROR, "字典编码已存在");

// 应该使用
throw new BusinessException(ErrorCode.RESOURCE_ALREADY_EXISTS, "字典编码已存在: " + dictCode);
```

### 2. 前端问题

#### ⚠️ 问题 1：只根据 HTTP 状态码处理错误，没有使用错误码

**当前实现**：`request.ts`
- ✅ 处理了 400, 401, 403, 404, 500 状态码
- ❌ 没有处理 409 冲突错误
- ❌ 没有根据错误码（`code` 字段）进行细粒度处理

**建议**：
```typescript
// 处理 409 冲突错误
if (error.response?.status === 409) {
  const errorCode = error.response?.data?.code
  const errorDetail = error.response?.data?.detail || error.response?.data?.message
  
  switch (errorCode) {
    case 40904: // RESOURCE_ALREADY_EXISTS
      message.error(errorDetail || '资源已存在')
      break
    case 40903: // RESOURCE_CONFLICT
      // 可能需要确认对话框
      if (errorDetail.includes('子资源')) {
        Modal.confirm({
          title: '确认删除',
          content: errorDetail,
          onOk: () => handleCascadeDelete()
        })
      } else {
        message.warning(errorDetail)
      }
      break
    case 40901: // IDEMPOTENT_CONFLICT
      message.warning('请勿重复提交')
      break
    default:
      message.error(errorDetail || '操作失败')
  }
}
```

## 重构优先级

### 高优先级（必须修复）

1. **ResourceServiceImpl.java** - 7 处 `RuntimeException`
   - 影响：资源创建/更新/删除操作
   - 修复：使用 `BusinessException` + 正确的 `ErrorCode`

2. **UserServiceImpl.java** - 7 处 `RuntimeException`
   - 影响：用户创建/更新操作
   - 修复：使用 `BusinessException` + 正确的 `ErrorCode`

3. **MenuServiceImpl.java** - 4 处 `RuntimeException`
   - 影响：菜单创建/更新操作
   - 修复：使用 `BusinessException` + 正确的 `ErrorCode`

### 中优先级（建议修复）

4. **DictTypeServiceImpl.java**, **DictItemServiceImpl.java**
   - 影响：错误码不够精确
   - 修复：将 `BUSINESS_ERROR` 改为 `RESOURCE_ALREADY_EXISTS`

5. **前端 request.ts**
   - 影响：409 冲突错误没有特殊处理
   - 修复：添加 409 错误处理和错误码判断

### 低优先级（可选）

6. **AvatarServiceImpl.java** - 3 处 `RuntimeException`
   - 影响：头像上传/删除操作
   - 修复：可以保持或改为 `BusinessException`

7. **CamundaProcessEngineServiceImpl.java** - 5 处 `RuntimeException`
   - 影响：流程引擎操作
   - 修复：可以保持或改为 `BusinessException`

## 修复统计

| 文件 | RuntimeException 数量 | BusinessException 数量 | 需要修复 |
|------|---------------------|---------------------|---------|
| ResourceServiceImpl.java | 7 | 1 | ✅ 7 |
| MenuServiceImpl.java | 4 | 0 | ✅ 4 |
| UserServiceImpl.java | 7 | 0 | ✅ 7 |
| DictTypeServiceImpl.java | 0 | 2 | ⚠️ 2 |
| DictItemServiceImpl.java | 0 | 2 | ⚠️ 2 |
| **总计** | **18** | **5** | **22** |

## 总结

- **后端**：18 处需要从 `RuntimeException` 改为 `BusinessException` + 正确的 `ErrorCode`
- **前端**：需要添加 409 错误处理和错误码判断逻辑
- **影响范围**：资源管理、菜单管理、用户管理、字典管理

