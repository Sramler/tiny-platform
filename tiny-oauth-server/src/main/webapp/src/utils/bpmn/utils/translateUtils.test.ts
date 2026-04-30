import { describe, expect, it } from 'vitest'

import { translateUtils } from './translateUtils'

describe('translateUtils', () => {
  it('translates common properties panel list actions', () => {
    expect(translateUtils.translate('Create')).toBe('新增')
    expect(translateUtils.translate('Create new list item')).toBe('新增列表项')
    expect(translateUtils.translate('List contains {numOfItems} items', { numOfItems: 2 })).toBe(
      '列表包含 2 项',
    )
    expect(translateUtils.translate('<empty>')).toBe('空')
    expect(translateUtils.translate('Close minimap')).toBe('关闭缩略图')
  })
})
