import { beforeEach, describe, expect, it } from 'vitest'

import {
  clearCache,
  getPerformanceStats,
  resetPerformanceStats,
  translateUtils,
} from './translateUtils'

describe('translateUtils', () => {
  beforeEach(() => {
    clearCache()
    resetPerformanceStats()
  })

  it('translates common properties panel list actions', () => {
    expect(translateUtils.translate('Create')).toBe('新增')
    expect(translateUtils.translate('Create new list item')).toBe('新增列表项')
    expect(translateUtils.translate('Section contains edits')).toBe('分组包含已编辑项')
    expect(translateUtils.translate('Toggle section')).toBe('展开/折叠分组')
    expect(translateUtils.translate('Start Event')).toBe('开始事件')
    expect(translateUtils.translate('Sequence Flow')).toBe('顺序流')
    expect(translateUtils.translate('Custom type')).toBe('自定义类型')
    expect(translateUtils.translate('<custom type>')).toBe('自定义类型')
    expect(translateUtils.translate('List contains {numOfItems} items', { numOfItems: 2 })).toBe(
      '列表包含 2 项',
    )
    expect(translateUtils.translate('<empty>')).toBe('空')
    expect(translateUtils.translate('Close minimap')).toBe('关闭缩略图')
  })

  it('keeps local translations before official fallback values', () => {
    translateUtils.addOfficialTranslations({
      Create: 'Create',
      'Create new list item': 'Create new list item',
    })

    expect(translateUtils.getTranslationSource('Create')).toBe('local')
    expect(translateUtils.translate('Create')).toBe('新增')
    expect(translateUtils.translate('Create new list item')).toBe('新增列表项')
  })

  it('uses official Chinese package as fallback for keys missing in local dictionaries', async () => {
    await translateUtils.initialize()
    clearCache()

    expect(translateUtils.getTranslationSource('Open {element}')).toBe('official')
    expect(translateUtils.translate('Open {element}', { element: '任务' })).toBe('打开 任务')
  })

  it('tracks cache hits for repeated translation templates', () => {
    expect(translateUtils.translate('Create')).toBe('新增')
    expect(translateUtils.translate('Create')).toBe('新增')

    const stats = getPerformanceStats()
    expect(stats.cacheMisses).toBe(1)
    expect(stats.cacheHits).toBe(1)
    expect(stats.cacheHitRate).toBe('50%')
  })
})
