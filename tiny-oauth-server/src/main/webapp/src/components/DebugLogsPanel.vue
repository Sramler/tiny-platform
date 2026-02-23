<template>
  <div v-if="isPersistentLogEnabled" class="debug-panel" style="margin: 24px 0; text-align: left;">
    <a-button type="link" @click="showDebugLogs = !showDebugLogs" style="padding: 0;">
      {{ showDebugLogs ? '隐藏' : '显示' }}调试日志 ({{ debugLogs.length }})
    </a-button>
    <div v-if="showDebugLogs" class="debug-logs-container"
      style="margin-top: 12px; max-height: 300px; overflow-y: auto; background: #f5f5f5; padding: 12px; border-radius: 4px; font-size: 12px;">
      <div style="display: flex; justify-content: space-between; margin-bottom: 8px;">
        <span style="font-weight: bold;">最近 {{ debugLogs.length }} 条日志</span>
        <div>
          <a-button type="link" size="small" @click="loadDebugLogs">刷新</a-button>
          <a-button type="link" size="small" @click="handleExportLogs">导出</a-button>
          <a-button type="link" size="small" @click="handleClearLogs">清空</a-button>
        </div>
      </div>
      <div v-if="debugLogs.length === 0" style="color: #999; text-align: center; padding: 20px;">
        暂无日志
      </div>
      <div v-for="(log, index) in debugLogs.slice(0, 20)" :key="index"
        style="margin-bottom: 8px; padding: 8px; background: white; border-radius: 4px; border-left: 3px solid #1890ff;">
        <div style="display: flex; gap: 8px; margin-bottom: 4px;">
          <span style="color: #999; font-size: 11px;">{{ new Date(log.timestamp).toLocaleTimeString() }}</span>
          <span
            :style="{ color: log.level === 'error' ? '#ff4d4f' : log.level === 'warn' ? '#faad14' : '#1890ff', fontWeight: 'bold' }">
            [{{ log.level.toUpperCase() }}]
          </span>
          <span v-if="log.url"
            style="color: #666; font-size: 11px; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">
            {{ log.url }}
          </span>
          <span v-if="log.status" :style="{ color: log.status === 401 ? '#ff4d4f' : '#52c41a' }">
            {{ log.status }}
          </span>
        </div>
        <div style="color: #333; word-break: break-all;">{{ log.message }}</div>
        <pre v-if="log.data"
          style="margin: 4px 0 0 0; padding: 4px; background: #fafafa; border-radius: 2px; font-size: 11px; max-height: 100px; overflow-y: auto;">{{ log.data }}</pre>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getPersistentLogs, clearPersistentLogs, exportPersistentLogs, type LogEntry } from '@/utils/logger'
import { message } from 'ant-design-vue'

// 检查持久化日志是否启用
// 如果设置了 VITE_ENABLE_PERSISTENT_LOG，使用该值；否则默认在非生产环境启用
const isPersistentLogEnabled = computed(() => {
  const value = import.meta.env.VITE_ENABLE_PERSISTENT_LOG
  const isProd = import.meta.env.PROD
  if (value === undefined) return !isProd // 默认在非生产环境启用（开发、测试、预发布等）
  return value === 'true'
})

// 调试日志
const showDebugLogs = ref(false)
const debugLogs = ref<LogEntry[]>([])

// 加载调试日志
const loadDebugLogs = () => {
  debugLogs.value = getPersistentLogs()
}

// 清空调试日志
const handleClearLogs = () => {
  clearPersistentLogs()
  debugLogs.value = []
  message.success('日志已清空')
}

// 导出调试日志
const handleExportLogs = () => {
  const logs = exportPersistentLogs()
  const blob = new Blob([logs], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `debug-logs-${new Date().toISOString()}.json`
  a.click()
  URL.revokeObjectURL(url)
  message.success('日志已导出')
}

onMounted(() => {
  // 组件加载时自动加载最近的日志
  loadDebugLogs()
})
</script>

<style scoped>
.debug-panel {
  /* 可以添加自定义样式 */
}
</style>
