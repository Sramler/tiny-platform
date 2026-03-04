/**
 * BPMN 翻译调试功能模块
 * 提供调试面板控制、性能统计和配置选项
 */

import { ref, computed } from 'vue'
import {
  getPerformanceStats,
  resetPerformanceStats,
  exportUntranslatedKeys,
  onStatsUpdate,
} from '../utils/translateUtils'

export interface DebugConfig {
  enabled: boolean
  showPanel: boolean
  showButton: boolean // 添加是否显示调试按钮配置
  autoRefresh: boolean
  refreshInterval: number
  parent?: HTMLElement | string // 添加父容器配置
}

export interface DebugStats {
  totalTranslations: number
  cacheHits: number
  cacheMisses: number
  cacheHitRate: string
  officialFallbacks: number
  officialFallbackRate: string
  moduleTranslations: number
  moduleTranslationRate: string
  temporaryTranslations: number
  temporaryTranslationRate: string
  untranslatedCount: number
  untranslatedRate: string
  untranslatedKeys: string[]
}

// 默认配置
const defaultConfig: DebugConfig = {
  enabled: false,
  showPanel: false,
  showButton: true, // 默认显示调试按钮
  autoRefresh: false,
  refreshInterval: 5000,
}

// 调试配置
const debugConfig = ref<DebugConfig>({ ...defaultConfig })

// 性能统计
const performanceStats = ref<DebugStats>({
  totalTranslations: 0,
  cacheHits: 0,
  cacheMisses: 0,
  cacheHitRate: '0%',
  officialFallbacks: 0,
  officialFallbackRate: '0%',
  moduleTranslations: 0,
  moduleTranslationRate: '0%',
  temporaryTranslations: 0,
  temporaryTranslationRate: '0%',
  untranslatedCount: 0,
  untranslatedRate: '0%',
  untranslatedKeys: [],
})

// 自动刷新定时器。使用 globalThis 保持与当前类型环境一致。
let refreshTimer: ReturnType<typeof globalThis.setInterval> | null = null

/**
 * 初始化调试功能
 * @param config 调试配置
 */
export function initDebug(config?: Partial<DebugConfig>): void {
  if (config) {
    // 使用展开运算符确保正确合并配置
    debugConfig.value = { ...debugConfig.value, ...config }
  }

  if (debugConfig.value.enabled) {
    refreshStats()
    if (debugConfig.value.autoRefresh) {
      startAutoRefresh()
    }
  }

  // 注册统计更新回调，实现实时更新
  onStatsUpdate(() => {
    if (debugConfig.value.enabled) {
      refreshStats()
    }
  })
}

/**
 * 启用/禁用调试功能
 * @param enabled 是否启用
 */
export function setDebugEnabled(enabled: boolean): void {
  debugConfig.value.enabled = enabled

  if (enabled) {
    refreshStats()
    if (debugConfig.value.autoRefresh) {
      startAutoRefresh()
    }
  } else {
    stopAutoRefresh()
    debugConfig.value.showPanel = false
  }
}

/**
 * 显示/隐藏调试面板
 * @param show 是否显示
 */
export function setDebugPanelVisible(show: boolean): void {
  if (debugConfig.value.enabled) {
    debugConfig.value.showPanel = show
  }
}

/**
 * 切换调试面板显示状态
 */
export function toggleDebugPanel(): void {
  if (debugConfig.value.enabled) {
    debugConfig.value.showPanel = !debugConfig.value.showPanel
  }
}

/**
 * 刷新性能统计
 */
export function refreshStats(): void {
  if (debugConfig.value.enabled) {
    const stats = getPerformanceStats()
    performanceStats.value = stats
  }
}

/**
 * 重置性能统计
 */
export function resetStats(): void {
  if (debugConfig.value.enabled) {
    resetPerformanceStats()
    refreshStats()
  }
}

/**
 * 导出未翻译的键
 */
export function exportUntranslated(): void {
  if (debugConfig.value.enabled) {
    exportUntranslatedKeys()
  }
}

/**
 * 开始自动刷新
 */
function startAutoRefresh(): void {
  if (refreshTimer) {
    clearInterval(refreshTimer)
  }

  refreshTimer = globalThis.setInterval(() => {
    if (debugConfig.value.enabled && debugConfig.value.autoRefresh) {
      refreshStats()
    }
  }, debugConfig.value.refreshInterval)
}

/**
 * 停止自动刷新
 */
function stopAutoRefresh(): void {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}

/**
 * 设置自动刷新
 * @param enabled 是否启用自动刷新
 * @param interval 刷新间隔（毫秒）
 */
export function setAutoRefresh(enabled: boolean, interval?: number): void {
  debugConfig.value.autoRefresh = enabled
  if (interval) {
    debugConfig.value.refreshInterval = interval
  }

  if (enabled && debugConfig.value.enabled) {
    startAutoRefresh()
  } else {
    stopAutoRefresh()
  }
}

/**
 * 设置调试按钮可见性
 * @param show 是否显示调试按钮
 */
export function setDebugButtonVisible(show: boolean): void {
  debugConfig.value.showButton = show
}

/**
 * 获取调试配置
 */
export function getDebugConfig(): DebugConfig {
  return { ...debugConfig.value }
}

/**
 * 获取性能统计
 */
export function getDebugStats(): DebugStats {
  return { ...performanceStats.value }
}

/**
 * 检查调试功能是否启用
 */
export function isDebugEnabled(): boolean {
  return debugConfig.value.enabled
}

/**
 * 检查调试面板是否可见
 */
export function isDebugPanelVisible(): boolean {
  return debugConfig.value.enabled && debugConfig.value.showPanel
}

// 导出响应式数据供 Vue 组件使用
export const useDebug = () => {
  return {
    config: debugConfig,
    stats: performanceStats,
    isEnabled: computed(() => debugConfig.value.enabled),
    isPanelVisible: computed(() => debugConfig.value.enabled && debugConfig.value.showPanel),
    isButtonVisible: computed(() => debugConfig.value.enabled && debugConfig.value.showButton),
    togglePanel: toggleDebugPanel,
    refreshStats,
    resetStats,
    exportUntranslated,
    setDebugButtonVisible,
  }
}
