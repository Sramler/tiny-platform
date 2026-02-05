# 20 Vue 3 编码规范

## 适用范围

- 适用于：`**/*.vue`、`**/*.ts`、`**/*.tsx`、Vue 3 相关代码
- 不适用于：第三方组件库代码（但使用时应遵循其 API 规范）

## 总体策略

1. **Composition API 优先**：新代码必须使用 Composition API，逐步迁移 Options API。
2. **TypeScript 优先**：所有新组件必须使用 TypeScript。
3. **组件单一职责**：组件保持小而专注，复杂逻辑提取到 composables。

---

## 禁止（Must Not）

### 1) API 混用

- ❌ 混用 Options API 与 Composition API（默认 Composition API）。
- ❌ 在同一个组件中混用 `<script setup>` 和 `<script>`（必须选择一种）。

### 2) 模板与逻辑

- ❌ 模板中堆叠复杂业务逻辑（应下沉到 composables/services）。
- ❌ 模板中使用 `any` 类型（必须明确类型）。
- ❌ `v-for` 中缺少 `key` 属性（必须提供唯一 key）。

### 3) 响应式与性能

- ❌ 在 `v-for` 中使用 `v-if`（应使用 `computed` 过滤数据）。
- ❌ 过度使用 `watch`（优先使用 `computed` 派生数据）。
- ❌ 未清理副作用（定时器、事件监听、订阅等必须在 `onUnmounted` 中清理）。

### 4) 类型安全

- ❌ 使用 `any` 类型（除非极特殊情况，必须明确类型）。
- ❌ Props/Emits 缺少类型定义（必须使用 TypeScript 类型）。

---

## 必须（Must）

### 1) 组件结构

- ✅ `<script setup lang="ts">` 优先；TypeScript 优先。
- ✅ 组件命名 PascalCase；基础组件 Base 前缀；单例组件 The 前缀。
- ✅ 文件结构顺序：`<script setup>` → `<template>` → `<style>`。

### 2) 响应式 API

- ✅ 响应式：`ref/reactive`；派生 `computed`；副作用 `watch`。
- ✅ 原始值使用 `ref()`；对象使用 `reactive()`（避免深度嵌套）。
- ✅ 复杂派生数据使用 `computed()`；需要副作用时使用 `watch()`。

### 3) Props 与 Emits

- ✅ Props 必须使用 `defineProps<T>()` 或 `defineProps()` 配合 `PropType<T>`。
- ✅ Emits 必须使用 `defineEmits<T>()` 定义类型。
- ✅ Props 必须提供默认值（使用 `withDefaults()`）或标记为可选。

### 4) 生命周期

- ✅ 生命周期钩子必须在 `setup()` 或 composables 中使用。
- ✅ 副作用清理必须在 `onUnmounted()` 中执行。

### 5) 类型定义

- ✅ 所有 Props、Emits、Composables 返回值必须有类型定义。
- ✅ 使用 `interface` 或 `type` 定义类型，避免内联类型。

---

## 应该（Should）

### 1) 模板规范

- ⚠️ 模板：组件 kebab-case；绑定 `:`；事件 `@`；slot `#`。
- ⚠️ `v-for` 必须提供唯一 `key`（使用 ID 而非索引）。
- ⚠️ 模板逻辑保持简单，复杂计算使用 `computed`。

### 2) 性能优化

- ⚠️ 性能：合理使用 `v-if/v-show`（频繁切换用 `v-show`，条件渲染用 `v-if`）。
- ⚠️ 大模块异步加载：路由和大型组件使用动态导入 `() => import()`。
- ⚠️ 避免不必要的 watch：优先使用 `computed` 派生数据。
- ⚠️ 大数据结构使用 `shallowRef` 或 `shallowReactive`（避免深度响应式开销）。

### 3) 代码组织

- ⚠️ 按功能/领域组织文件（而非按类型），composables、components、views 按功能分组。
- ⚠️ Composables 命名：`useXxx` 风格（如 `useAuth`、`useForm`）。
- ⚠️ Composables 单一职责：每个 composable 处理单一关注点。

### 4) 状态管理

- ⚠️ Pinia 按业务域拆 store，actions 处理异步。
- ⚠️ 避免大型单体 store，按功能模块拆分。

### 5) 类型检查

- ⚠️ 使用 `vue-tsc` 进行完整类型检查（CI 中运行）。
- ⚠️ 启用严格 TypeScript 选项：`strict`、`noImplicitAny`、`strictNullChecks`。

---

## 可以（May）

- 💡 使用 JSDoc 注释为公共 API 添加文档。
- 💡 复杂组件拆分为多个子组件提升可维护性。
- 💡 使用设计模式（如策略模式、工厂模式）处理复杂业务逻辑。

---

## 例外与裁决

- 第三方组件库：遵循组件库的 API 规范（如 Ant Design Vue）。
- 遗留代码：逐步迁移到 Composition API，新代码必须使用 Composition API。
- 冲突时：平台特定规则（90+）优先于本规范。

---

## 示例

### ✅ 正例：Composition API + TypeScript

```vue
<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useUserStore } from '@/stores/user'

// 类型定义
interface Props {
  userId: number
  title?: string
}

interface Emits {
  (e: 'update', value: number): void
  (e: 'delete'): void
}

// Props 和 Emits
const props = withDefaults(defineProps<Props>(), {
  title: '默认标题'
})

const emit = defineEmits<Emits>()

// 响应式数据
const count = ref(0)
const userStore = useUserStore()

// 计算属性
const doubleCount = computed(() => count.value * 2)
const displayTitle = computed(() => `${props.title} - ${count.value}`)

// 方法
const increment = () => {
  count.value++
  emit('update', count.value)
}

// 生命周期
let timer: number | null = null
onMounted(() => {
  timer = setInterval(() => {
    count.value++
  }, 1000)
})

onUnmounted(() => {
  if (timer) {
    clearInterval(timer)
  }
})
</script>

<template>
  <div>
    <h2>{{ displayTitle }}</h2>
    <p>{{ doubleCount }}</p>
    <button @click="increment">增加</button>
  </div>
</template>
```

### ❌ 反例：混用 API、缺少类型、模板中堆叠逻辑

```vue
<!-- 错误：混用 Options API 和 Composition API，缺少类型定义 -->
<script>
import { ref } from 'vue'

export default {
  data() {
    return { count: 0 }
  },
  computed: {
    doubleCount() {
      return this.count * 2
    }
  },
  methods: {
    increment() {
      this.count++
    }
  },
  setup() {
    const userStore = useUserStore() // ❌ 混用 setup 和 Options API
    return { userStore }
  }
}
</script>

<template>
  <div>
    <!-- ❌ 模板中堆叠复杂逻辑 -->
    <p>{{ count * 2 + userStore.user.id + Math.random() }}</p>
    <button @click="count++">增加</button>
  </div>
</template>
```

### ✅ 正例：Composables 提取逻辑

```typescript
// composables/useCounter.ts
import { ref, computed } from 'vue'

export function useCounter(initialValue = 0) {
  const count = ref(initialValue)
  const doubleCount = computed(() => count.value * 2)
  
  const increment = () => {
    count.value++
  }
  
  const decrement = () => {
    count.value--
  }
  
  return {
    count,
    doubleCount,
    increment,
    decrement
  }
}
```

```vue
<script setup lang="ts">
import { useCounter } from '@/composables/useCounter'

const { count, doubleCount, increment } = useCounter(10)
</script>

<template>
  <div>
    <p>{{ doubleCount }}</p>
    <button @click="increment">增加</button>
  </div>
</template>
```

### ✅ 正例：v-for 使用 key

```vue
<template>
  <!-- ✅ 使用唯一 ID 作为 key -->
  <div v-for="user in users" :key="user.id">
    {{ user.name }}
  </div>
  
  <!-- ❌ 错误：使用索引作为 key -->
  <div v-for="(user, index) in users" :key="index">
    {{ user.name }}
  </div>
</template>
```

### ✅ 正例：动态导入（代码分割）

```typescript
// 路由懒加载
const routes = [
  {
    path: '/user',
    component: () => import('@/views/UserView.vue') // ✅ 动态导入
  }
]

// 组件懒加载
<script setup lang="ts">
import { defineAsyncComponent } from 'vue'

const HeavyComponent = defineAsyncComponent(() => 
  import('@/components/HeavyComponent.vue')
)
</script>
```

### ✅ 正例：清理副作用

```vue
<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'

let subscription: Subscription | null = null

onMounted(() => {
  subscription = eventBus.subscribe('event', handleEvent)
})

onUnmounted(() => {
  if (subscription) {
    subscription.unsubscribe() // ✅ 清理订阅
  }
})
</script>
```

### ❌ 反例：未清理副作用

```vue
<script setup lang="ts">
import { onMounted } from 'vue'

onMounted(() => {
  setInterval(() => {
    // 定时器逻辑
  }, 1000)
  // ❌ 未清理定时器，可能导致内存泄漏
})
</script>
```
