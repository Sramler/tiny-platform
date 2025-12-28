# 为什么 Ant Design Vue 的 #title 插槽会纵向滚动？

## 问题

用户发现 `#title` 插槽会随着表格内容纵向滚动，不符合预期。

## 原因分析

### 1. `#title` 插槽的位置

`#title` 插槽位于 `a-table` 组件**内部**，属于表格内容区域的一部分：

```vue
<a-table>
  <template #title>
    <!-- 标题内容 -->
  </template>
  <!-- 表格数据 -->
</a-table>
```

### 2. Ant Design Vue 的设计机制

根据 Ant Design Vue 的设计：

- **`scroll.y` 固定的是表头（`thead`）**，不是 `#title` 插槽
- **`#title` 插槽和表格数据（`tbody`）一起滚动**
- `#title` 是表格的标题区域，不是固定区域

### 3. DOM 结构

当设置了 `scroll.y` 时，Ant Design Vue 的 DOM 结构如下：

```
.ant-table-container
  ├── .ant-table-title          ← #title 插槽内容（会滚动）
  ├── .ant-table-header          ← 表头（固定，不滚动）
  └── .ant-table-body            ← 表格数据（会滚动）
```

### 4. 滚动行为

- **固定区域**：表头（`thead`）- 通过 `scroll.y` 固定
- **滚动区域**：
  - `#title` 插槽内容
  - 表格数据（`tbody`）

## 解决方案

### ✅ 方案 1：将工具栏放在表格外部（推荐）

将工具栏放在 `a-table` 外部，不依赖 `#title` 插槽：

```vue
<div class="table-container">
  <!-- 工具栏固定在顶部，不随表格滚动 -->
  <div class="toolbar-container">
    <!-- 工具栏内容 -->
  </div>

  <div class="table-scroll-container">
    <a-table>
      <!-- 表格内容 -->
    </a-table>
  </div>
</div>
```

**优点**：
- ✅ 工具栏固定在顶部，不随表格滚动
- ✅ 完全控制工具栏的位置和行为
- ✅ 不依赖 Ant Design Vue 的内部机制

### ❌ 方案 2：使用 `#title` 插槽（不推荐）

如果使用 `#title` 插槽，工具栏会随表格内容一起滚动：

```vue
<a-table>
  <template #title>
    <!-- 工具栏会随表格滚动 -->
    <div class="toolbar-container">
      <!-- 工具栏内容 -->
    </div>
  </template>
</a-table>
```

**缺点**：
- ❌ 工具栏会随表格内容滚动
- ❌ 不符合固定工具栏的预期

## 当前实现

当前代码已经采用了**方案 1**，将工具栏放在表格外部：

```vue
<div class="table-container" ref="tableContentRef">
  <!-- 工具栏固定在顶部，不随表格滚动 -->
  <div class="toolbar-container">
    <div class="table-title">
      导出测试数据（demo_export_usage）
    </div>
    <div class="table-actions">
      <!-- 操作按钮 -->
    </div>
  </div>

  <div class="table-scroll-container">
    <a-table
      :scroll="{ x: tableScrollX, y: tableBodyHeight }"
      ...
    >
      <!-- 表格内容 -->
    </a-table>
  </div>
</div>
```

## 总结

### 为什么 `#title` 会滚动？

1. **`#title` 插槽位于表格内容区域**，不是固定区域
2. **`scroll.y` 只固定表头**，不固定 `#title` 插槽
3. **这是 Ant Design Vue 的设计行为**，不是 bug

### 如何实现固定工具栏？

✅ **将工具栏放在 `a-table` 外部**，使用独立的容器和样式控制

### 参考文档

- [Ant Design Vue 表格固定列和表头](https://antdv.com/components/table-cn#components-table-demo-fixed-columns-header)
- [Ant Design Vue 表格插槽](https://antdv.com/components/table-cn#API)

