<template>
  <div class="icon-select">
    <!-- 图标搜索输入框 -->
    <a-input
      v-model:value="iconSearch"
      placeholder="输入图标名称搜索"
      allow-clear
      @pressEnter="handleIconSearchEnter"
      style="margin-bottom: 12px;"
    />
    <div class="icon-grid">
      <div
        v-for="icon in filteredIconList"
        :key="icon.name"
        class="icon-item"
        :class="{ active: modelValue === icon.name, highlight: filteredIconList.length === 1 }"
        @click="selectIcon(icon.name)"
      >
        <component :is="icon.component" class="grid-icon" />
        <span class="icon-name">{{ icon.name }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onBeforeUnmount } from 'vue'
import * as allIcons from '@ant-design/icons-vue'

// 组件props
const props = defineProps<{ modelValue: string }>()
const emit = defineEmits(['update:modelValue', 'select'])

// 图标搜索关键字
const iconSearch = ref('')

// 黑名单：新版已无效或无SVG的图标
const iconBlacklist: string[] = [
  // 可根据实际情况继续补充
]

// 动态获取所有官方图标，排除黑名单 - 移到计算属性外部避免重复计算
const iconList = ref(() => {
  try {
    return Object.keys(allIcons)
  .filter(name => /Outlined$|TwoTone$|Filled$/.test(name))
  .filter(name => !iconBlacklist.includes(name)) // 黑名单过滤
  .map(name => ({
    name,
    component: (allIcons as any)[name]
  }))
  .filter(icon => typeof icon.component === 'object' || typeof icon.component === 'function')
      .slice(0, 1000) // 限制图标数量，避免性能问题
  } catch (error) {
    console.warn('IconSelect iconList error:', error)
    return []
  }
})

// 过滤后的图标列表 - 优化计算属性，避免无限递归
const filteredIconList = computed(() => {
  try {
    const searchValue = iconSearch.value?.trim() || ''
    const icons = iconList.value()
    
    if (!searchValue) {
      return icons.slice(0, 200) // 默认只显示前200个图标
    }
    
    return icons
      .filter(icon => icon.name.toLowerCase().includes(searchValue.toLowerCase()))
      .slice(0, 200) // 搜索结果也限制数量
  } catch (error) {
    console.warn('IconSelect filter error:', error)
    return []
  }
})

// 回车时自动选中唯一匹配
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

// 选中图标
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

// 组件卸载时清理数据
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
