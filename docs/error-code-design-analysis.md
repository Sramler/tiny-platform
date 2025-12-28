# 错误码设计分析：RESOURCE_HAS_CHILDREN

## 问题背景

用户提出了一个很好的问题：
1. **"无法删除有子资源的资源"** 属于一种需要前端确认的状态
2. 声明 `RESOURCE_HAS_CHILDREN` 一种新的类型是否符合预期？
3. 这样是否会加重状态码解析的负担？

## 当前方案分析

### 方案 1：为每个业务场景定义错误码（当前实现）

**优点**：
- ✅ 错误码明确，便于前端精确处理
- ✅ 便于监控和统计（可以统计每种错误的发生频率）
- ✅ 错误信息更清晰

**缺点**：
- ❌ 错误码数量会快速增长（每个业务场景一个）
- ❌ 前端需要维护错误码映射表
- ❌ 增加前后端耦合（前端必须知道所有错误码）
- ❌ 维护成本高（新增业务场景需要修改错误码枚举）

### 方案 2：使用通用错误码 + 详细错误信息

**优点**：
- ✅ 错误码数量可控（只定义大类）
- ✅ 前端可以根据错误信息灵活处理
- ✅ 降低前后端耦合
- ✅ 维护成本低

**缺点**：
- ❌ 前端需要解析错误信息（可能不够稳定）
- ❌ 错误统计不够精确（所有业务错误都归类为 `BUSINESS_ERROR`）

## 推荐方案：分层错误码设计

### 设计原则

1. **粗粒度分类**：按错误类型分类（如：资源冲突、数据验证、权限等）
2. **细粒度信息**：在 `detail` 中提供详细的错误信息
3. **前端智能识别**：前端可以根据错误信息关键词或错误码组合来判断处理方式

### 错误码分类建议

```java
// ==================== 业务错误 (409) ====================
IDEMPOTENT_CONFLICT(4001, "请勿重复提交", HttpStatus.CONFLICT),
BUSINESS_ERROR(4002, "业务处理失败", HttpStatus.CONFLICT),

// 资源相关冲突（4xxx 系列）
RESOURCE_CONFLICT(4003, "资源冲突", HttpStatus.CONFLICT),  // 通用资源冲突
// 可以用于：
// - 资源有子资源
// - 资源被其他资源引用
// - 资源状态不允许操作
// - 等等

// 数据验证相关（5xxx 系列）
DATA_VALIDATION_ERROR(5001, "数据验证失败", HttpStatus.BAD_REQUEST),
// 可以用于：
// - 名称重复
// - 格式不正确
// - 必填项缺失
// 等等
```

### 错误响应格式

```json
{
  "type": "https://example.org/problems/business-error",
  "title": "资源冲突",
  "status": 409,
  "detail": "无法删除有子资源的资源，请先删除子资源。资源ID: 123，子资源数量: 3",
  "code": 4003,
  "instance": "/sys/resources/menus/batch/delete",
  "metadata": {
    "resourceId": 123,
    "childrenCount": 3,
    "action": "confirm",  // 可选：提示前端需要确认
    "confirmMessage": "该资源有 3 个子资源，是否要级联删除？"
  }
}
```

### 前端处理策略

```typescript
// 前端可以根据错误码和错误信息灵活处理
if (error.code === 4003) {  // RESOURCE_CONFLICT
  const detail = error.detail || ''
  
  if (detail.includes('子资源')) {
    // 显示确认对话框
    Modal.confirm({
      title: '确认删除',
      content: error.metadata?.confirmMessage || detail,
      onOk: () => {
        // 调用级联删除接口
        deleteResourceCascade(resourceId)
      }
    })
  } else if (detail.includes('被引用')) {
    // 显示不同的提示
    message.warning(detail)
  }
}
```

## 最终建议

### 推荐方案：使用 `RESOURCE_CONFLICT` 通用错误码

1. **移除** `RESOURCE_HAS_CHILDREN` 专用错误码
2. **使用** `RESOURCE_CONFLICT` 通用错误码
3. **在 `detail` 中提供详细错误信息**，包含：
   - 具体原因（有子资源、被引用等）
   - 资源ID
   - 相关数据（子资源数量等）
4. **可选：在 `metadata` 中提供结构化信息**，便于前端解析

### 代码修改

```java
// ErrorCode.java
RESOURCE_CONFLICT(4003, "资源冲突", HttpStatus.CONFLICT),

// ResourceServiceImpl.java
throw new BusinessException(
    ErrorCode.RESOURCE_CONFLICT,
    String.format("无法删除有子资源的资源，请先删除子资源。资源ID: %d，子资源数量: %d", id, children.size())
);
```

## 总结

| 方案 | 错误码数量 | 前端负担 | 可维护性 | 推荐度 |
|------|-----------|---------|---------|--------|
| 每个场景一个错误码 | 高 | 高 | 低 | ⭐⭐ |
| 通用错误码 + 详细信息 | 低 | 中 | 高 | ⭐⭐⭐⭐⭐ |
| 分层错误码设计 | 中 | 中 | 高 | ⭐⭐⭐⭐ |

**结论**：使用通用错误码 + 详细错误信息，既能满足业务需求，又不会增加过多的维护负担。

