import { mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMocks = vi.hoisted(() => ({
  getVisibleDictTypes: vi.fn(),
}))

vi.mock('@/api/dict', () => ({
  getVisibleDictTypes: apiMocks.getVisibleDictTypes,
}))

const FormStub = defineComponent({
  setup(_, { slots, expose }) {
    expose({ validate: () => Promise.resolve() })
    return () => slots.default?.()
  },
})

const PassThrough = defineComponent({ template: '<div><slot /></div>' })

import DictItemForm from '@/views/dict/DictItemForm.vue'

async function flushPromises() {
  await Promise.resolve()
  await nextTick()
}

describe('DictItemForm.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiMocks.getVisibleDictTypes.mockResolvedValue([{ id: 1, dictCode: 'STATUS', dictName: '状态' }])
  })

  it('should load dict type options on mount and expose form helpers', async () => {
    const wrapper = mount(DictItemForm, {
      props: { formData: { id: 1, dictTypeId: 1, value: 'OK', label: '正常' } as any },
      global: {
        stubs: {
          'a-form': FormStub,
          'a-form-item': PassThrough,
          'a-input': PassThrough,
          'a-input-number': PassThrough,
          'a-textarea': PassThrough,
          'a-switch': PassThrough,
          'a-select': PassThrough,
          'a-select-option': PassThrough,
        },
      },
    })
    await flushPromises()

    expect(apiMocks.getVisibleDictTypes).toHaveBeenCalled()
    const exposed = wrapper.vm as unknown as { validate: () => Promise<void>; getFormData: () => any }
    await exposed.validate()
    expect(exposed.getFormData().value).toBe('OK')
  })
})

