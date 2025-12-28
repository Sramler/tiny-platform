# ErrorCode 重构总结

## 已完成的修复

### 1. 后端修复

#### ✅ ResourceServiceImpl.java（7处）
- ✅ 资源名称/URL/URI已存在 → `ErrorCode.RESOURCE_ALREADY_EXISTS(40904)`
- ✅ 资源不存在 → `ErrorCode.NOT_FOUND(40401)`
- ✅ 资源被引用（有子资源）→ `ErrorCode.RESOURCE_CONFLICT(40903)`（已修复）

#### ✅ MenuServiceImpl.java（4处）
- ✅ 不能将自己设置为父菜单 → `ErrorCode.RESOURCE_CONFLICT(40903)`
- ✅ 父菜单不存在 → `ErrorCode.NOT_FOUND(40401)`
- ✅ 父菜单必须是目录类型 → `ErrorCode.VALIDATION_ERROR(40001)`
- ✅ 循环引用 → `ErrorCode.RESOURCE_CONFLICT(40903)`

#### ✅ UserServiceImpl.java（7处）
- ✅ 用户名已存在 → `ErrorCode.RESOURCE_ALREADY_EXISTS(40904)`
- ✅ 用户不存在 → `ErrorCode.NOT_FOUND(40401)`
- ✅ 部分角色不存在 → `ErrorCode.NOT_FOUND(40401)`
- ✅ 部分用户不存在 → `ErrorCode.NOT_FOUND(40401)`

#### ✅ DictTypeServiceImpl.java（2处）
- ✅ 字典编码已存在 → `ErrorCode.RESOURCE_ALREADY_EXISTS(40904)`
- ✅ 字典编码已被使用 → `ErrorCode.RESOURCE_ALREADY_EXISTS(40904)`

#### ✅ DictItemServiceImpl.java（2处）
- ✅ 字典项值已存在 → `ErrorCode.RESOURCE_ALREADY_EXISTS(40904)`
- ✅ 字典项值已被使用 → `ErrorCode.RESOURCE_ALREADY_EXISTS(40904)`

### 2. 前端修复

#### ✅ request.ts
- ✅ 添加了 409 冲突错误的处理逻辑
- ✅ 记录错误码和详细信息，便于调试

## 错误码使用统计

| ErrorCode | 使用场景 | 使用次数 |
|-----------|---------|---------|
| `RESOURCE_ALREADY_EXISTS(40904)` | 资源已存在（唯一性冲突） | 10+ |
| `NOT_FOUND(40401)` | 资源不存在 | 8+ |
| `RESOURCE_CONFLICT(40903)` | 资源冲突（有子资源、循环引用等） | 3+ |
| `VALIDATION_ERROR(40001)` | 参数验证失败 | 1+ |

## 待修复的问题

### 低优先级（可选）

1. **AvatarServiceImpl.java** - 3处 `RuntimeException`
   - 头像上传/删除失败
   - 建议：可以保持或改为 `BusinessException`

2. **CamundaProcessEngineServiceImpl.java** - 5处 `RuntimeException`
   - 流程引擎相关错误
   - 建议：可以保持或改为 `BusinessException`

3. **UserServiceImpl.java** - 批量操作中的部分错误
   - 批量启用/禁用/删除时的部分失败
   - 建议：可以保持或改为更详细的错误信息

## 前端优化建议

### 当前实现
- ✅ 处理了 400, 401, 403, 404, 409, 500 状态码
- ✅ 409 错误已添加处理逻辑

### 建议优化
1. **根据错误码进行细粒度处理**：
   ```typescript
   if (error.response?.status === 409) {
     const errorCode = error.response?.data?.code
     switch (errorCode) {
       case 40904: // RESOURCE_ALREADY_EXISTS
         message.error('资源已存在，请使用其他名称')
         break
       case 40903: // RESOURCE_CONFLICT
         if (errorDetail.includes('子资源')) {
           // 显示确认对话框
         }
         break
     }
   }
   ```

2. **统一错误提示**：
   - 创建统一的错误处理工具函数
   - 根据错误码显示不同的提示信息

## 总结

- ✅ **后端**：已修复 22 处关键错误处理
- ✅ **前端**：已添加 409 错误处理
- ⚠️ **待优化**：前端可以根据错误码进行更细粒度的处理

**重构完成度**：约 90%

