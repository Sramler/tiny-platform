# 30 Ant Design Vue 规范

## 适用范围

- 适用于：前端 UI（`**/*.vue`、`src/components/**`）、Ant Design Vue 组件使用
- 不适用于：第三方组件库内部代码（但使用时应遵循其 API 规范）

## 总体策略

1. **版本统一**：统一使用 Ant Design Vue 4.x，避免版本混用。
2. **类型安全**：所有组件 Props/Emits 必须有 TypeScript 类型定义。
3. **二次封装**：封装组件必须透传 attrs，保持 API 一致性。

---

## 禁止（Must Not）

### 1) 过时写法

- ❌ 继续使用过时写法（如 `.sync` 修饰符）导致风格混乱。
- ❌ 使用已废弃的 API（如 `v-model:value` 应使用 `v-model`）。

### 2) 类型与封装

- ❌ 二次封装组件缺少 Props/Emits 类型定义。
- ❌ 二次封装组件不透传 `$attrs`（导致样式和事件丢失）。
- ❌ 直接修改 Ant Design Vue 组件内部样式（应使用 CSS 变量或主题定制）。

### 3) 性能与使用

- ❌ 大数据表格不使用虚拟滚动（导致渲染性能问题）。
- ❌ 表单校验规则硬编码在模板中（应集中管理）。

---

## 必须（Must）

### 1) 版本与样式

- ✅ 版本：Ant Design Vue 4.x；样式使用 `ant-design-vue/dist/reset.css`。
- ✅ 主题定制：使用 CSS 变量或 Less 变量，不直接覆盖组件样式。

### 2) 表单规范

- ✅ 表单：统一 v-model 使用方式；校验规则集中管理。
- ✅ 表单校验：使用 `rules` 属性或 `Form.useForm()` API。
- ✅ 表单提交：使用 `@finish` 事件，不使用 `@submit`。

### 3) 表格规范

- ✅ 表格：选中行用 `v-model:selectedRowKeys`；大数据考虑 scroll/virtual。
- ✅ 表格列：使用 `columns` 配置，避免在模板中硬编码列定义。
- ✅ 表格分页：使用 `v-model:current` 和 `v-model:pageSize`。

### 4) 类型定义

- ✅ 二次封装组件：props/emits 必须有 TS 类型；透传 attrs；事件统一命名。
- ✅ 组件 Props 必须使用 `PropType<T>` 或泛型定义类型。

---

## 应该（Should）

### 1) 组件封装

- ⚠️ 二次封装组件：props/emits 必须有 TS 类型；透传 attrs；事件统一命名。
- ⚠️ 封装组件命名：使用业务语义命名（如 `UserSelect` 而非 `SelectWrapper`）。
- ⚠️ 封装组件文档：提供 Props/Emits/Slots 文档和使用示例。

### 2) 主题与样式

- ⚠️ 主题使用集中变量管理（CSS variables / less 等）。
- ⚠️ 自定义样式使用 `:deep()` 或 CSS Modules，避免全局污染。

### 3) 性能优化

- ⚠️ 大数据表格使用虚拟滚动（`scroll={{ y: 400 }}` 或 `virtual` 属性）。
- ⚠️ 表格列使用 `customRender` 时避免复杂计算（使用 `computed` 预处理）。

### 4) 表单最佳实践

- ⚠️ 表单校验规则集中管理（提取到单独文件或 composable）。
- ⚠️ 复杂表单拆分为多个子表单组件。

---

## 可以（May）

- 💡 使用 Ant Design Vue 的 `ConfigProvider` 统一配置主题、语言等。
- 💡 复杂业务场景封装为业务组件库（如 `UserPicker`、`DateRangePicker`）。

---

## 例外与裁决

- 第三方组件：直接使用 Ant Design Vue 组件时遵循其 API 规范。
- 自定义组件：二次封装组件必须符合本规范（props/emits 类型、事件命名）。
- 冲突时：平台特定规则（90+）优先于本规范。

---

## 示例

### ✅ 正例：表单使用 v-model 和集中校验规则

```vue
<script setup lang="ts">
import { ref } from 'vue'
import { Form, Input, Button } from 'ant-design-vue'
import { useForm } from '@ant-design-vue/use'
import { userFormRules } from '@/rules/userFormRules'

interface FormData {
  username: string
  email: string
}

const formData = ref<FormData>({
  username: '',
  email: ''
})

const { validate, validateInfos } = useForm(formData, userFormRules)

const handleSubmit = async () => {
  try {
    await validate()
    // 提交逻辑
  } catch (error) {
    console.error('Validation failed', error)
  }
}
</script>

<template>
  <a-form @finish="handleSubmit">
    <a-form-item label="用户名" v-bind="validateInfos.username">
      <a-input v-model:value="formData.username" />
    </a-form-item>
    <a-form-item label="邮箱" v-bind="validateInfos.email">
      <a-input v-model:value="formData.email" />
    </a-form-item>
    <a-form-item>
      <a-button type="primary" html-type="submit">提交</a-button>
    </a-form-item>
  </a-form>
</template>
```

### ❌ 反例：使用过时写法、缺少类型定义

```vue
<!-- 错误：使用过时写法、缺少类型定义 -->
<script setup>
const selectedKeys = ref([]) // ❌ 缺少类型

// ❌ 使用过时的 .sync 修饰符
<a-table :selectedRowKeys.sync="selectedKeys" />
</script>
```

### ✅ 正例：表格使用 v-model:selectedRowKeys

```vue
<script setup lang="ts">
import { ref } from 'vue'
import { Table } from 'ant-design-vue'

interface User {
  id: number
  name: string
}

const selectedRowKeys = ref<number[]>([])
const users = ref<User[]>([])

const handleSelectionChange = (keys: number[]) => {
  selectedRowKeys.value = keys
}
</script>

<template>
  <a-table
    :data-source="users"
    v-model:selectedRowKeys="selectedRowKeys"
    @selection-change="handleSelectionChange"
    :row-selection="{ type: 'checkbox' }"
  >
    <a-table-column title="ID" data-index="id" />
    <a-table-column title="姓名" data-index="name" />
  </a-table>
</template>
```

### ✅ 正例：二次封装组件（透传 attrs，类型定义）

```vue
<script setup lang="ts">
import { Select } from 'ant-design-vue'
import type { SelectProps } from 'ant-design-vue'

interface Props extends SelectProps {
  // 扩展业务属性
  loadOnMount?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  loadOnMount: true
})

// 透传所有 attrs（包括 style、class、事件等）
</script>

<template>
  <a-select v-bind="$attrs" v-model:value="modelValue">
    <slot />
  </a-select>
</template>
```

### ✅ 正例：大数据表格使用虚拟滚动

```vue
<template>
  <a-table
    :columns="columns"
    :data-source="largeDataSet"
    :scroll="{ y: 400 }"
    :virtual="true"
    :pagination="false"
  />
</template>
```

### ✅ 正例：表单校验规则集中管理

```typescript
// rules/userFormRules.ts
import { Rule } from 'ant-design-vue/es/form'

export const userFormRules: Record<string, Rule[]> = {
  username: [
    { required: true, message: '请输入用户名' },
    { min: 3, max: 20, message: '用户名长度 3-20 字符' }
  ],
  email: [
    { required: true, message: '请输入邮箱' },
    { type: 'email', message: '邮箱格式不正确' }
  ]
}
```

### ✅ 正例：主题定制（CSS 变量）

```vue
<style scoped>
:deep(.ant-btn-primary) {
  background-color: var(--primary-color);
  border-color: var(--primary-color);
}
</style>
```

### ❌ 反例：直接修改组件样式

```vue
<style scoped>
/* ❌ 错误：直接覆盖组件内部样式，可能导致样式冲突 */
:deep(.ant-table-tbody > tr > td) {
  background-color: red !important;
}
</style>
```
