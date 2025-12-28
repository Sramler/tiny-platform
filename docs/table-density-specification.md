# 表格密度规格说明

## 问题

用户询问：**标准、中等、紧凑是否有重写 middle、small 等默认规格？**

## 检查结果

### ✅ 结论：**没有重写，完全使用 Ant Design Vue 的默认规格**

## 代码分析

### 1. 表格密度设置

**位置**：`testData.vue` 模板部分

```vue
<a-table
  :size="tableSize === 'default' ? undefined : tableSize"
  ...
>
```

**说明**：
- `default` → `undefined`（使用组件默认值）
- `middle` → `'middle'`
- `small` → `'small'`
- `large` → `'large'`（可选）

### 2. 样式检查

**检查项**：是否有自定义 padding 样式覆盖默认规格

**结果**：✅ **没有覆盖**

代码中只有以下样式优化，**没有覆盖单元格 padding**：

```css
/* 表头样式优化（仅背景色和边框） */
:deep(.ant-table-thead > tr > th) {
  background-color: #fafafa;
  font-weight: 600;
  border-bottom: 2px solid #e8e8e8;
  /* 注意：没有设置 padding */
}

/* 表格体样式优化（仅边框） */
:deep(.ant-table-tbody > tr > td) {
  border-bottom: 1px solid #f0f0f0;
  /* 注意：没有设置 padding */
  /* 注释说明：单元格内边距由 size 属性控制，移除固定 padding 以支持密度配置 */
}
```

### 3. Ant Design Vue 4.x 默认规格

根据 Ant Design Vue 4.x 官方文档，表格密度规格如下：

| 密度 | size 值 | 单元格 padding | 行高（约） | 说明 |
|------|---------|----------------|------------|------|
| **标准** | `default` 或 `undefined` | `16px 16px` | ~54px | 默认密度，适合大多数场景 |
| **中等** | `middle` | `12px 16px` | ~46px | 平衡密度和可读性 |
| **紧凑** | `small` | `8px 16px` | ~38px | 高密度，适合数据密集场景 |
| **大号** | `large` | `24px 16px` | ~62px | 低密度，适合需要更多空间的场景 |

### 4. 当前实现

**代码位置**：`testData.vue` script 部分

```typescript
const tableSize = ref<'default' | 'small' | 'middle' | 'large'>('middle') // 默认使用中等密度
```

**菜单配置**：

```vue
<a-menu @click="handleDensityMenuClick" :selected-keys="[tableSize]">
  <a-menu-item key="default">
    <span>标准</span>
  </a-menu-item>
  <a-menu-item key="middle">
    <span>中等</span>
  </a-menu-item>
  <a-menu-item key="small">
    <span>紧凑</span>
  </a-menu-item>
</a-menu>
```

## 验证方法

### 浏览器开发者工具检查

1. 打开浏览器开发者工具（F12）
2. 切换到"标准"密度
3. 检查表格单元格的 computed styles：
   ```css
   padding: 16px 16px;  /* 标准密度 */
   ```
4. 切换到"中等"密度
5. 检查表格单元格的 computed styles：
   ```css
   padding: 12px 16px;  /* 中等密度 */
   ```
6. 切换到"紧凑"密度
7. 检查表格单元格的 computed styles：
   ```css
   padding: 8px 16px;   /* 紧凑密度 */
   ```

### 代码检查

使用以下命令检查是否有自定义 padding：

```bash
# 检查是否有覆盖 padding 的样式
grep -r "padding.*!important" tiny-oauth-server/src/main/webapp/src/views/export/testData.vue
grep -r "ant-table.*td.*padding" tiny-oauth-server/src/main/webapp/src/views/export/testData.vue
grep -r "ant-table.*th.*padding" tiny-oauth-server/src/main/webapp/src/views/export/testData.vue
```

**结果**：✅ 没有找到覆盖 padding 的样式

## 总结

### ✅ 关键结论

1. **没有重写**：代码完全使用 Ant Design Vue 的默认规格
2. **完全依赖组件**：通过 `:size` 属性控制密度，不自定义 padding
3. **符合最佳实践**：遵循组件库的设计规范，便于维护和升级

### 📋 规格对照表

| 菜单显示 | size 值 | Ant Design Vue 规格 | padding | 行高 |
|---------|---------|---------------------|----------|------|
| 标准 | `default` | 默认 | `16px 16px` | ~54px |
| 中等 | `middle` | 中等 | `12px 16px` | ~46px |
| 紧凑 | `small` | 紧凑 | `8px 16px` | ~38px |

### 💡 设计说明

代码中明确注释：

```css
/* 单元格内边距由 size 属性控制，移除固定 padding 以支持密度配置 */
:deep(.ant-table-tbody > tr > td) {
  border-bottom: 1px solid #f0f0f0;
  /* 注意：没有设置 padding，完全依赖 size 属性 */
}
```

这确保了：
- ✅ 密度切换正常工作
- ✅ 符合 Ant Design Vue 设计规范
- ✅ 便于后续维护和升级

## 如果需要自定义规格

如果未来需要自定义表格密度规格，可以这样实现：

```css
/* 自定义标准密度 */
:deep(.ant-table-size-default .ant-table-tbody > tr > td),
:deep(.ant-table-size-default .ant-table-thead > tr > th) {
  padding: 18px 16px; /* 自定义 padding */
}

/* 自定义中等密度 */
:deep(.ant-table-size-middle .ant-table-tbody > tr > td),
:deep(.ant-table-size-middle .ant-table-thead > tr > th) {
  padding: 14px 16px; /* 自定义 padding */
}

/* 自定义紧凑密度 */
:deep(.ant-table-size-small .ant-table-tbody > tr > td),
:deep(.ant-table-size-small .ant-table-thead > tr > th) {
  padding: 6px 16px; /* 自定义 padding */
}
```

**注意**：当前代码**没有**使用自定义规格，完全依赖 Ant Design Vue 的默认值。

