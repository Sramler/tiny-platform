/**
 * BPMN 翻译工具类
 * 提供类型安全和性能优化的翻译功能
 * 支持官方翻译兜底机制
 */

import {
  getOfficialTranslations,
  loadOfficialTranslations as loadBpmnJsOfficialTranslations,
  isOfficialTranslationsLoaded,
} from '../i18n/bpmn-js'
import allTranslations, {
  bpmnJsTranslations,
  propertiesPanelTranslations,
  camundaPropertiesPanelTranslations,
  zeebePropertiesPanelTranslations,
} from '../i18n'
import type { TranslationMap } from '../i18n'

type ResolvedTranslation = {
  translation: string
  source: 'custom' | 'local' | 'official' | 'case-insensitive' | 'none'
  translated: boolean
}

const isDevMode = Boolean(import.meta.env?.DEV)

/**
 * 翻译工具类
 */
export class TranslateUtils {
  private static instance: TranslateUtils
  private customTranslations: TranslationMap = {}
  private officialTranslations: TranslationMap = {}
  private translationCache = new Map<string, ResolvedTranslation>()
  private enableOfficialFallback: boolean = true
  private enableDebugLogs: boolean = false

  private constructor() {
    // 构造函数中不加载翻译，等待显式初始化
  }

  /**
   * 获取单例实例
   */
  public static getInstance(): TranslateUtils {
    if (!TranslateUtils.instance) {
      TranslateUtils.instance = new TranslateUtils()
    }
    return TranslateUtils.instance
  }

  /**
   * 调试日志输出
   */
  private debugLog(message: string, ...args: unknown[]): void {
    if (this.enableDebugLogs) {
      console.debug(message, ...args)
    }
  }

  private infoLog(message: string, ...args: unknown[]): void {
    if (this.enableDebugLogs || isDevMode) {
      console.info(message, ...args)
    }
  }

  private warnLog(message: string, ...args: unknown[]): void {
    if (this.enableDebugLogs || isDevMode) {
      console.warn(message, ...args)
    }
  }

  private clearTranslationCache(): void {
    this.translationCache.clear()
  }

  /**
   * 初始化翻译系统
   */
  public async initialize(): Promise<void> {
    try {
      await this.loadOfficialTranslations()
      this.infoLog('✅ 翻译系统已初始化')
    } catch (error) {
      console.error('❌ 翻译系统初始化失败:', error)
    }
  }

  /**
   * 加载官方翻译
   */
  private async loadOfficialTranslations(): Promise<void> {
    this.debugLog('🔄 开始加载官方翻译...')
    try {
      // 尝试同步加载官方翻译
      this.debugLog('📥 尝试同步加载官方翻译...')
      const syncLoaded = this.loadOfficialTranslationsSync()

      if (syncLoaded) {
        this.debugLog('✅ 官方翻译同步加载成功')
      } else {
        // 即使调用方曾手动追加过少量官方词条，也仍需加载完整官方中文包。
        this.debugLog('📥 同步加载失败，尝试异步加载官方翻译...')
        await this.loadOfficialTranslationsAsync()
        this.debugLog('✅ 官方翻译异步加载成功')
      }

      const count = Object.keys(this.officialTranslations).length
      this.debugLog(`📊 官方翻译加载完成，共 ${count} 个翻译`)
    } catch (error) {
      console.error('❌ 加载官方翻译过程中出现错误:', error)
    }
  }

  /**
   * 同步加载官方翻译
   */
  private loadOfficialTranslationsSync(): boolean {
    try {
      // 方案1: 从全局变量加载
      if (typeof window !== 'undefined' && (window as any).bpmnOfficialTranslations) {
        Object.assign(this.officialTranslations, (window as any).bpmnOfficialTranslations)
        this.clearTranslationCache()
        this.debugLog('✅ 从全局变量同步加载官方翻译成功')
        return true
      }

      // 方案2: 检查是否已经通过其他方式加载
      if (isOfficialTranslationsLoaded()) {
        const officialTranslations = getOfficialTranslations()
        Object.assign(this.officialTranslations, officialTranslations)
        this.clearTranslationCache()
        this.debugLog('✅ 从已加载的官方翻译同步获取成功')
        return true
      }

      // 方案3: 尝试直接导入（同步方式）
      try {
        // 在浏览器环境中，尝试从全局变量获取预加载的翻译
        if (typeof window !== 'undefined' && (window as any).bpmnOfficialTranslationsPreloaded) {
          Object.assign(
            this.officialTranslations,
            (window as any).bpmnOfficialTranslationsPreloaded,
          )
          this.clearTranslationCache()
          this.debugLog('✅ 从预加载的官方翻译同步获取成功')
          return true
        }
      } catch (importError) {
        this.debugLog('⚠️ 同步导入官方翻译失败:', importError)
      }

      this.debugLog('⚠️ 同步加载官方翻译不可用，将使用异步加载')
      return false
    } catch (error) {
      this.debugLog('❌ 同步加载官方翻译失败，将尝试异步加载:', error)
      return false
    }
  }

  /**
   * 异步加载官方翻译
   */
  private async loadOfficialTranslationsAsync(): Promise<void> {
    try {
      this.debugLog('📥 开始异步加载 BPMN.js 官方中文翻译包...')
      // 加载 BPMN.js 官方翻译包
      await loadBpmnJsOfficialTranslations()

      // 如果官方翻译已加载，更新本地存储
      if (isOfficialTranslationsLoaded()) {
        const officialTranslations = getOfficialTranslations()
        Object.assign(this.officialTranslations, officialTranslations)
        this.clearTranslationCache()
      } else {
        this.warnLog('⚠️ 异步加载官方中文翻译包完成，但翻译未正确加载')
      }
    } catch (error) {
      console.error('❌ 异步加载官方翻译失败:', error)
    }
  }

  /**
   * 翻译文本
   * @param template 翻译模板
   * @param replacements 替换参数
   * @param context 调用上下文（可选）
   * @returns 翻译后的文本
   */
  public translate(
    template: string,
    replacements?: Record<string, unknown>,
    context?: {
      source?: string
      component?: string
    },
  ): string {
    try {
      performanceStats.totalTranslations += 1
      replacements = replacements || {}
      if (context) {
        this.debugLog('🌐 翻译上下文:', context)
      }

      // 输入验证
      if (!template || typeof template !== 'string') {
        this.warnLog('❌ 无效的翻译键:', template)
        return String(template || '')
      }

      const cached = this.translationCache.get(template)
      if (cached) {
        performanceStats.cacheHits += 1
        notifyStatsUpdate()
        return this.replacePlaceholders(cached.translation, replacements)
      }

      performanceStats.cacheMisses += 1

      // 翻译优先级：
      // 1. 临时/业务自定义翻译：用于项目侧紧急补齐或覆盖第三方词条
      // 2. 本地模块翻译：项目维护的稳定中文词典
      // 3. 官方中文翻译兜底：只在项目词典没有覆盖时使用
      // 4. BPMN.js 默认英文：最后兜底
      const resolved = this.resolveTranslation(template)
      this.translationCache.set(template, resolved)

      if (resolved.source === 'custom') {
        performanceStats.temporaryTranslations += 1
        this.debugLog(`🎨 [临时] ${template} -> ${resolved.translation}`)
        notifyStatsUpdate()
        return this.replacePlaceholders(resolved.translation, replacements)
      }

      if (resolved.source === 'local') {
        performanceStats.moduleTranslations += 1

        // 确定具体是哪个模块提供的翻译
        let moduleName = 'unknown'
        if (template in bpmnJsTranslations) {
          moduleName = 'bpmn-js'
        } else if (template in propertiesPanelTranslations) {
          moduleName = 'properties-panel'
        } else if (template in camundaPropertiesPanelTranslations) {
          moduleName = 'camunda-properties-panel'
        } else if (template in zeebePropertiesPanelTranslations) {
          moduleName = 'zeebe-properties-panel'
        }

        this.debugLog(`📚 [${moduleName}] ${template} -> ${resolved.translation}`)
        notifyStatsUpdate()
        return this.replacePlaceholders(resolved.translation, replacements)
      }

      if (resolved.source === 'official') {
        performanceStats.officialFallbacks += 1
        this.debugLog(`🌍 [官方中文] ${template} -> ${resolved.translation}`)
        notifyStatsUpdate()
        return this.replacePlaceholders(resolved.translation, replacements)
      }

      if (resolved.source === 'case-insensitive') {
        this.debugLog(`🔍 使用大小写不敏感匹配: ${template} -> ${resolved.translation}`)
        notifyStatsUpdate()
        return this.replacePlaceholders(resolved.translation, replacements)
      }

      // 记录未翻译的键
      if (!resolved.translated) {
        this.warnLog(`❌ 翻译键未找到: ${template}`)
        performanceStats.untranslatedKeys.add(template)
        notifyStatsUpdate()
      }

      // 替换占位符
      return this.replacePlaceholders(resolved.translation, replacements)
    } catch (error) {
      console.error('❌ 翻译过程中发生错误:', error, '翻译键:', template)
      // 容错处理：返回原文
      return String(template || '')
    }
  }

  private resolveTranslation(template: string): ResolvedTranslation {
    if (this.customTranslations[template]) {
      return {
        translation: this.customTranslations[template],
        source: 'custom',
        translated: true,
      }
    }

    if (allTranslations[template]) {
      return {
        translation: allTranslations[template],
        source: 'local',
        translated: true,
      }
    }

    if (this.enableOfficialFallback && this.officialTranslations[template]) {
      return {
        translation: this.officialTranslations[template],
        source: 'official',
        translated: true,
      }
    }

    const caseInsensitiveTranslation = this.findTranslationCaseInsensitive(template)
    if (caseInsensitiveTranslation) {
      return {
        translation: caseInsensitiveTranslation,
        source: 'case-insensitive',
        translated: true,
      }
    }

    return {
      translation: template,
      source: 'none',
      translated: false,
    }
  }

  /**
   * 替换占位符
   * @param text 文本
   * @param replacements 替换参数
   * @returns 替换后的文本
   */
  private replacePlaceholders(text: string, replacements: Record<string, unknown>): string {
    return text.replace(/{([^}]+)}/g, (_: string, key: string) => {
      const value = replacements[key]
      return value == null ? `{${key}}` : String(value)
    })
  }

  /**
   * 添加临时翻译
   * @param translations 临时翻译映射
   */
  public addCustomTranslations(translations: TranslationMap): void {
    this.infoLog('🎨 开始添加临时翻译...')
    const beforeCount = Object.keys(this.customTranslations).length
    Object.assign(this.customTranslations, translations)
    this.clearTranslationCache()
    const afterCount = Object.keys(this.customTranslations).length
    const addedCount = afterCount - beforeCount

    this.infoLog(`✅ 添加临时翻译: +${addedCount}, 总计${afterCount}个`)
  }

  /**
   * 移除临时翻译
   * @param keys 要移除的翻译键数组
   */
  public removeCustomTranslations(keys: string[]): void {
    this.infoLog('🗑️ 开始移除临时翻译...')
    const beforeCount = Object.keys(this.customTranslations).length
    keys.forEach((key) => {
      delete this.customTranslations[key]
    })
    this.clearTranslationCache()
    const afterCount = Object.keys(this.customTranslations).length
    const removedCount = beforeCount - afterCount

    this.infoLog(`✅ 移除临时翻译: -${removedCount}, 剩余${afterCount}个`)
  }

  /**
   * 检查翻译是否存在
   * @param key 翻译键
   * @returns 是否存在翻译
   */
  public hasTranslation(key: string): boolean {
    return (
      key in this.customTranslations || key in allTranslations || key in this.officialTranslations
    )
  }

  /**
   * 获取所有可用的翻译键
   * @returns 翻译键数组
   */
  public getAvailableKeys(): string[] {
    const allKeys = [
      ...Object.keys(allTranslations),
      ...Object.keys(this.customTranslations),
      ...Object.keys(this.officialTranslations),
    ]
    return Array.from(new Set(allKeys))
  }

  /**
   * 批量翻译
   * @param templates 翻译模板数组
   * @param replacements 替换参数
   * @returns 翻译后的文本数组
   */
  public translateBatch(templates: string[], replacements?: Record<string, unknown>): string[] {
    return templates.map((template) => this.translate(template, replacements))
  }

  /**
   * 获取翻译模块配置
   * @returns BPMN.js 翻译模块配置
   */
  public async getTranslateModule(showDebugInfo: boolean = false) {
    // 设置调试日志开关
    this.enableDebugLogs = showDebugInfo

    // 等待官方翻译加载完成
    await this.initialize().catch((error) => {
      if (this.enableDebugLogs) {
        console.error('自动初始化翻译系统失败:', error)
      }
    })

    return {
      translate: [
        'value',
        (template: string, replacements?: Record<string, unknown>) => {
          const result = this.translate(template, replacements)
          return result
        },
      ],
    }
  }

  /**
   * 启用/禁用官方翻译兜底
   * @param enable 是否启用
   */
  public setOfficialFallback(enable: boolean): void {
    if (this.enableOfficialFallback !== enable) {
      this.clearTranslationCache()
    }
    this.enableOfficialFallback = enable
  }

  /**
   * 获取官方翻译兜底状态
   * @returns 是否启用
   */
  public isOfficialFallbackEnabled(): boolean {
    return this.enableOfficialFallback
  }

  public getOfficialTranslationCount(): number {
    return Object.keys(this.officialTranslations).length
  }

  /**
   * 启用/禁用调试日志
   * @param enable 是否启用
   */
  public setDebugLogs(enable: boolean): void {
    this.enableDebugLogs = enable
    this.infoLog(`🔧 调试日志已${enable ? '启用' : '禁用'}`)
  }

  /**
   * 获取调试日志状态
   * @returns 是否启用
   */
  public isDebugLogsEnabled(): boolean {
    return this.enableDebugLogs
  }

  /**
   * 手动添加官方翻译
   * @param translations 官方翻译映射
   */
  public addOfficialTranslations(translations: TranslationMap): void {
    Object.assign(this.officialTranslations, translations)
    this.clearTranslationCache()
    this.debugLog(`手动添加了 ${Object.keys(translations).length} 个官方翻译`)
  }

  /**
   * 获取翻译来源
   * @param key 翻译键
   * @returns 翻译来源
   */
  public getTranslationSource(key: string): 'custom' | 'local' | 'official' | 'none' {
    // 按照 translate() 的实际优先级检查：自定义 > 本地 > 官方
    if (key in this.customTranslations) return 'custom'
    if (key in allTranslations) return 'local'
    if (this.enableOfficialFallback && key in this.officialTranslations) return 'official'
    return 'none'
  }

  /**
   * 大小写不敏感的翻译查找
   * @param key 翻译键
   * @returns 找到的翻译或 null
   */
  public findTranslationCaseInsensitive(key: string): string | null {
    // 按 translate() 的实际优先级查找：自定义 > 本地 > 官方
    const normalizedKey = key.toLowerCase()

    const customKeys = Object.keys(this.customTranslations)
    const customMatch = customKeys.find((k) => k.toLowerCase() === normalizedKey)
    if (customMatch) {
      return this.customTranslations[customMatch] ?? null
    }

    const localKeys = Object.keys(allTranslations)
    const localMatch = localKeys.find((k) => k.toLowerCase() === normalizedKey)
    if (localMatch) {
      return allTranslations[localMatch] ?? null
    }

    if (!this.enableOfficialFallback) {
      return null
    }

    const officialKeys = Object.keys(this.officialTranslations)
    const officialMatch = officialKeys.find((k) => k.toLowerCase() === normalizedKey)
    if (officialMatch) {
      return this.officialTranslations[officialMatch] ?? null
    }

    return null
  }

  /**
   * 获取合并后的翻译（同步方式）
   */
  public getMergedTranslations(): TranslationMap {
    return {
      ...this.officialTranslations, // 官方翻译作为兜底基础
      ...allTranslations, // 项目本地翻译覆盖官方兜底
      ...this.customTranslations, // 自定义翻译最高优先级
    }
  }

  /**
   * 导入翻译数据
   * @param data 翻译数据
   */
  public importTranslations(data: { official?: TranslationMap; custom?: TranslationMap }): void {
    if (data.official) {
      this.officialTranslations = { ...data.official }
    }
    if (data.custom) {
      this.customTranslations = { ...data.custom }
    }
    this.clearTranslationCache()

    this.infoLog('📥 翻译数据导入完成')
  }

  public clearCache(): void {
    this.clearTranslationCache()
  }
}

// 导出单例实例
export const translateUtils = TranslateUtils.getInstance()

// 导出便捷函数
export const translate = (
  template: string,
  replacements?: Record<string, unknown>,
  context?: {
    source?: string
    component?: string
  },
) => translateUtils.translate(template, replacements, context)

export const addCustomTranslations = (translations: TranslationMap) =>
  translateUtils.addCustomTranslations(translations)

export const getTranslateModule = async (showDebugInfo?: boolean) =>
  await translateUtils.getTranslateModule(showDebugInfo)

export const setOfficialFallback = (enable: boolean) => translateUtils.setOfficialFallback(enable)

export const addOfficialTranslations = (translations: TranslationMap) =>
  translateUtils.addOfficialTranslations(translations)

export const getTranslationSource = (key: string) => translateUtils.getTranslationSource(key)

export const findTranslationCaseInsensitive = (key: string) =>
  translateUtils.findTranslationCaseInsensitive(key)

export const getAvailableKeys = () => translateUtils.getAvailableKeys()

export const setDebugLogs = (enable: boolean) => translateUtils.setDebugLogs(enable)

export const isDebugLogsEnabled = () => translateUtils.isDebugLogsEnabled()

export const importTranslations = (data: { official?: TranslationMap; custom?: TranslationMap }) =>
  translateUtils.importTranslations(data)

// 性能统计与未翻译键追踪
type PerformanceStats = {
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

const performanceStats = {
  totalTranslations: 0,
  cacheHits: 0,
  cacheMisses: 0,
  officialFallbacks: 0,
  moduleTranslations: 0,
  temporaryTranslations: 0,
  untranslatedKeys: new Set<string>(),
}

const statsListeners = new Set<() => void>()

function notifyStatsUpdate() {
  statsListeners.forEach((fn) => fn())
}

function computeRates(total: number, count: number): string {
  if (!total) return '0%'
  return `${Math.round((count / total) * 100)}%`
}

export function getPerformanceStats(): PerformanceStats {
  const total = performanceStats.totalTranslations
  const untranslatedCount = performanceStats.untranslatedKeys.size
  return {
    totalTranslations: total,
    cacheHits: performanceStats.cacheHits,
    cacheMisses: performanceStats.cacheMisses,
    cacheHitRate: computeRates(total, performanceStats.cacheHits),
    officialFallbacks: performanceStats.officialFallbacks,
    officialFallbackRate: computeRates(total, performanceStats.officialFallbacks),
    moduleTranslations: performanceStats.moduleTranslations,
    moduleTranslationRate: computeRates(total, performanceStats.moduleTranslations),
    temporaryTranslations: performanceStats.temporaryTranslations,
    temporaryTranslationRate: computeRates(total, performanceStats.temporaryTranslations),
    untranslatedCount,
    untranslatedRate: computeRates(total, untranslatedCount),
    untranslatedKeys: Array.from(performanceStats.untranslatedKeys),
  }
}

export function resetPerformanceStats(): void {
  performanceStats.totalTranslations = 0
  performanceStats.cacheHits = 0
  performanceStats.cacheMisses = 0
  performanceStats.officialFallbacks = 0
  performanceStats.moduleTranslations = 0
  performanceStats.temporaryTranslations = 0
  performanceStats.untranslatedKeys.clear()
  notifyStatsUpdate()
}

export function onStatsUpdate(cb: () => void): () => void {
  statsListeners.add(cb)
  return () => statsListeners.delete(cb)
}

export function getUntranslatedKeys(): string[] {
  return Array.from(performanceStats.untranslatedKeys)
}

export function getUntranslatedStats(): { count: number; keys: string[] } {
  const keys = getUntranslatedKeys()
  return { count: keys.length, keys }
}

export function exportUntranslatedKeys(): string[] {
  const keys = getUntranslatedKeys()
  if (keys.length) {
    console.log('未翻译键导出:', keys)
  }
  return keys
}

export function generateTranslationSuggestions(): Record<string, string> {
  return {}
}

export function getOfficialPackagesInfo(): Record<string, unknown> {
  return {}
}

export function getOfficialTranslationStatus(): { loaded: boolean; count: number } {
  const count = translateUtils.getOfficialTranslationCount()
  return { loaded: count > 0, count }
}

export async function initializeTranslationSystem(): Promise<void> {
  await translateUtils.initialize()
}

export function clearCache(): void {
  translateUtils.clearCache()
}

// 全局上下文管理
let globalTranslationContext = {
  source: 'unknown',
  component: 'Unknown',
  page: 'unknown',
}

/**
 * 设置全局翻译上下文
 */
export function setGlobalTranslationContext(context: {
  source?: string
  component?: string
  page?: string
}): void {
  Object.assign(globalTranslationContext, context)
}

/**
 * 获取全局翻译上下文
 */
export function getGlobalTranslationContext() {
  return { ...globalTranslationContext }
}

/**
 * 清除全局翻译上下文
 */
export function clearGlobalTranslationContext(): void {
  globalTranslationContext = {
    source: 'unknown',
    component: 'Unknown',
    page: 'unknown',
  }
}

// 简化的翻译对象
export const bpmnTranslations = {
  // 同步获取翻译（如果已初始化）
  get: () => translateUtils.getMergedTranslations(),

  // 异步初始化并获取翻译
  initialize: async () => {
    await translateUtils.initialize()
    return translateUtils.getMergedTranslations()
  },

  // 添加自定义翻译
  add: (translations: TranslationMap) => translateUtils.addCustomTranslations(translations),

  // 翻译单个文本
  translate: (template: string, replacements?: Record<string, unknown>) =>
    translateUtils.translate(template, replacements),
}
