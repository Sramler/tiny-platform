/**
 * bpmn-js-i18n 类型声明
 * 用于解决 bpmn-js-i18n 子路径导入时的 TypeScript 类型检查问题
 */

declare module 'bpmn-js-i18n' {
  interface TranslationMap {
    [key: string]: string
  }

  const translations: TranslationMap
  export default translations
}

declare module 'bpmn-js-i18n/translations/zn.js' {
  interface TranslationMap {
    [key: string]: string
  }

  const translations: TranslationMap
  export default translations
}
