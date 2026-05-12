/**
 * BPMN.js 翻译主入口
 * 对应 bpmn-js-i18n-zh/lib/bpmn-js/index.js
 * 支持 bpmn-js-i18n 官方中文翻译包补充
 */

import elementTranslations from './elements'
import contextMenuTranslations from './context-menu'
import officialZhTranslations from 'bpmn-js-i18n/translations/zn.js'

export interface TranslationMap {
  [key: string]: string
}

const isDevMode = Boolean(import.meta.env?.DEV)

function debugLog(message: string, ...args: unknown[]): void {
  if (isDevMode) {
    console.debug(message, ...args)
  }
}

function normalizeTranslationModule(moduleValue: unknown): TranslationMap {
  const maybeDefault =
    moduleValue && typeof moduleValue === 'object' && 'default' in moduleValue
      ? (moduleValue as { default?: unknown }).default
      : moduleValue

  const maybeNestedDefault =
    maybeDefault && typeof maybeDefault === 'object' && 'default' in maybeDefault
      ? (maybeDefault as { default?: unknown }).default
      : maybeDefault

  if (maybeNestedDefault && typeof maybeNestedDefault === 'object') {
    return maybeNestedDefault as TranslationMap
  }

  return {}
}

// 本地翻译（基于 bpmn-js-i18n-zh 源码的 TypeScript 实现）
const localTranslations: TranslationMap = {
  ...elementTranslations,
  ...contextMenuTranslations,

  // importer 相关翻译
  'element {element} referenced by {referenced}#{property} not yet drawn':
    '元素 {element} 的引用 {referenced}#{property} 尚未绘制',
  'unknown di {di} for element {semantic}': '未知的 di {di} 对于元素 {semantic}',
  '{semantic}#{side} Ref not specified': '{semantic}#{side} Ref 未指定',
  'already rendered {element}': '{element} 已呈现',
  'failed to import {element}': '{element} 导入失败',
  'multiple DI elements defined for {element}': '为 {element} 定义了多个 DI 元素',
  'no bpmnElement referenced in {element}': '{element} 中没有引用 bpmnElement',
  'diagram not part of bpmn:Definitions': '图表不是 bpmn:Definitions 的一部分',
  'no diagram to display': '没有要显示的图表',
  'no process or collaboration to display': '没有可显示的流程或协作',
  'correcting missing bpmnElement on {plane} to {rootElement}':
    '在 {plane} 上更正缺失的 bpmnElement 为 {rootElement}',
  'unsupported bpmnElement for {plane}: {rootElement}':
    '不支持的 bpmnElement 元素 {plane}: {rootElement}',
  'unrecognized flowElement {element} in context {context}':
    '在上下文 {context} 中未识别的 flowElement {element}',
  'missing {semantic}#attachedToRef': '在 {element} 中缺少 {semantic}#attachedToRef',

  // modeling 相关翻译
  'out of bounds release': '越界释放',
  'no shape type specified': '未指定形状类型',
  'more than {count} child lanes': '超过 {count} 条通道',
  'element required': '需要元素',

  // 其他
  'no parent for {element} in {parent}': '在 {element} 中没有父元素 {parent}',
  'flow elements must be children of pools/participants': '元素必须是池/参与者的子级',
  'Open minimap': '打开缩略图',
  'Close minimap': '关闭缩略图',
}

// 官方翻译存储
let officialTranslations: TranslationMap = {}

// 简化的官方翻译加载器 - 像 Ant Design Vue 一样简单
class OfficialTranslationLoader {
  private static instance: OfficialTranslationLoader
  private isLoaded = false
  private loadPromise: Promise<void> | null = null

  private constructor() {}

  static getInstance(): OfficialTranslationLoader {
    if (!OfficialTranslationLoader.instance) {
      OfficialTranslationLoader.instance = new OfficialTranslationLoader()
    }
    return OfficialTranslationLoader.instance
  }

  // 同步获取官方翻译（如果已加载）
  getOfficialTranslations(): TranslationMap {
    return { ...officialTranslations }
  }

  // 检查是否已加载
  isOfficialTranslationsLoaded(): boolean {
    return this.isLoaded && Object.keys(officialTranslations).length > 0
  }

  // 异步加载官方翻译（一次性）
  async loadOfficialTranslations(): Promise<void> {
    if (this.isLoaded) {
      return
    }

    if (this.loadPromise) {
      return this.loadPromise
    }

    this.loadPromise = this._loadTranslations()
    await this.loadPromise
  }

  private async _loadTranslations(): Promise<void> {
    try {
      // 尝试从全局变量获取预加载的翻译
      if (typeof window !== 'undefined' && (window as any).bpmnOfficialTranslationsPreloaded) {
        officialTranslations = (window as any).bpmnOfficialTranslationsPreloaded
        debugLog('✅ 从全局变量同步加载官方中文翻译成功')
        this.isLoaded = true
        return
      }

      // bpmn-js-i18n 当前中文文件名是 zn.js。这里作为“官方中文兜底词典”，
      // 项目本地词典仍然拥有更高优先级，便于统一业务用语。
      const normalizedTranslations = normalizeTranslationModule(officialZhTranslations)

      if (Object.keys(normalizedTranslations).length > 0) {
        officialTranslations = normalizedTranslations
        debugLog('✅ 成功异步加载官方 bpmn-js-i18n 中文翻译包')
      }

      this.isLoaded = true
    } catch (error) {
      debugLog('官方 bpmn-js-i18n 中文翻译包未安装或加载失败，跳过加载:', error)
      this.isLoaded = true // 标记为已加载，避免重复尝试
    }
  }
}

// 创建单例实例
const translationLoader = OfficialTranslationLoader.getInstance()

/**
 * 加载官方翻译包（异步方式，作为兜底）
 */
export async function loadOfficialTranslations(): Promise<void> {
  await translationLoader.loadOfficialTranslations()
}

/**
 * 获取合并后的翻译（本地翻译 + 官方翻译补充）
 */
export function getMergedTranslations(): TranslationMap {
  return {
    ...officialTranslations, // 官方翻译作为基础
    ...localTranslations, // 本地翻译覆盖官方翻译
  }
}

/**
 * 获取本地翻译
 */
export function getLocalTranslations(): TranslationMap {
  return { ...localTranslations }
}

/**
 * 获取官方翻译
 */
export function getOfficialTranslations(): TranslationMap {
  return translationLoader.getOfficialTranslations()
}

/**
 * 检查官方翻译是否已加载
 */
export function isOfficialTranslationsLoaded(): boolean {
  return translationLoader.isOfficialTranslationsLoaded()
}

// 导出本地翻译（向后兼容）
export const bpmnJsTranslations = localTranslations
export default getMergedTranslations()
