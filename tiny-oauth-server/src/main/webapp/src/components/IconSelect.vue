<template>
  <div class="icon-select">
    <a-input
      v-model:value="iconSearch"
      placeholder="输入图标名称搜索"
      allow-clear
      @pressEnter="handleIconSearchEnter"
      style="margin-bottom: 12px;"
    />
    <div class="icon-grid">
      <div
        v-for="item in filteredIconList"
        :key="item.name"
        class="icon-item"
        :class="{ active: modelValue === item.name, highlight: filteredIconList.length === 1 }"
        @click="selectIcon(item.name)"
      >
        <component :is="item.asyncComp" class="grid-icon" />
        <span class="icon-name">{{ item.name }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onBeforeUnmount, defineAsyncComponent } from 'vue'
import { antdIconLoaders } from '@/utils/antdIconLoaders'

const props = defineProps<{ modelValue: string }>()
const emit = defineEmits(['update:modelValue', 'select'])

const iconSearch = ref('')

/** 黑名单：新版已无效或无 SVG 的图标 */
const iconBlacklist: string[] = []

type IconEntry = { name: string; loader: () => Promise<{ default: import('vue').Component }> }

const allIconEntries: IconEntry[] = Object.entries(antdIconLoaders)
  .map(([path, loader]) => {
    const name = path.match(/\/([^/]+)\.js$/)?.[1]
    return name ? { name, loader } : null
  })
  .filter((x): x is IconEntry => x !== null)
  .filter(({ name }) => /Outlined$|TwoTone$|Filled$/.test(name))
  .filter(({ name }) => !iconBlacklist.includes(name))

/** 每个名称只构建一次 defineAsyncComponent，避免重复包装 */
const asyncCompByName = new Map<string, ReturnType<typeof defineAsyncComponent>>()
function getAsyncIcon(loader: IconEntry['loader'], name: string) {
  let c = asyncCompByName.get(name)
  if (!c) {
    c = defineAsyncComponent(loader)
    asyncCompByName.set(name, c)
  }
  return c
}

const filteredIconList = computed(() => {
  try {
    const searchValue = iconSearch.value?.trim() || ''
    let list = allIconEntries
    if (searchValue) {
      const q = searchValue.toLowerCase()
      list = allIconEntries.filter((e) => e.name.toLowerCase().includes(q))
    }
    const sliced = list.slice(0, 200)
    return sliced.map((e) => ({
      name: e.name,
      asyncComp: getAsyncIcon(e.loader, e.name),
    }))
  } catch (error) {
    console.warn('IconSelect filter error:', error)
    return []
  }
})

function handleIconSearchEnter() {
  try {
    const filtered = filteredIconList.value
    if (filtered.length === 1) {
      const only = filtered[0]
      if (only) selectIcon(only.name)
    }
  } catch (error) {
    console.warn('IconSelect search error:', error)
  }
}

function selectIcon(name: string) {
  try {
    if (name && typeof name === 'string') {
      emit('update:modelValue', name)
      emit('select', name)
    }
  } catch (error) {
    console.warn('IconSelect select error:', error)
  }
}

onBeforeUnmount(() => {
  iconSearch.value = ''
})
</script>

<style scoped>
.icon-select {
  width: 100%;
}
.icon-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 12px;
  max-height: 400px;
  overflow-y: auto;
}
.icon-item {
  width: 120px;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 12px;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}
.icon-item:hover {
  border-color: #1890ff;
  background: #f0f8ff;
}
.icon-item.active {
  border-color: #1890ff;
  background: #e6f7ff;
}
.icon-item.highlight {
  border-color: #52c41a;
  background: #f6ffed;
}
.grid-icon {
  font-size: 14px;
  color: #1890ff;
  margin-bottom: 8px;
}
.icon-name {
  font-size: 12px;
  color: #666;
  text-align: center;
  word-break: break-all;
}
</style>
