<template>
  <!-- 动态渲染官方 icons-vue 图标，未找到时兜底为 MenuOutlined（按文件懒加载，不整包 import *） -->
  <component :is="resolvedIcon" :class="iconClass" v-if="resolvedIcon" />
</template>

<script setup lang="ts">
import { computed, shallowRef, watch } from 'vue'
import type { Component } from 'vue'

const props = defineProps<{
  icon: string
  className?: string
}>()

const resolvedIcon = shallowRef<Component | null>(null)

watch(
  () => props.icon,
  async (name) => {
    /** 动态导入含 import.meta.glob 的模块，避免把 glob 映射打进 BasicLayout 入口 chunk */
    const { getAntdIconLoaderOrFallback } = await import('@/utils/antdIconLoaders')
    const loader = getAntdIconLoaderOrFallback(name)
    const mod = await loader()
    resolvedIcon.value = mod.default
  },
  { immediate: true },
)

const iconClass = computed(() => {
  const baseClass = 'icon-vue'
  return props.className ? `${baseClass} ${props.className}` : baseClass
})
</script>

<style scoped>
.icon-vue {
  font-size: 18px;
  color: inherit;
  vertical-align: middle;
}
</style>
