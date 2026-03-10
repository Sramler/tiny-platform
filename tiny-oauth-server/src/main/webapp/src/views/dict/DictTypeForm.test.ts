import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { describe, expect, it } from 'vitest'

const FormStub = defineComponent({
  setup(_, { slots, expose }) {
    expose({ validate: () => Promise.resolve() })
    return () => slots.default?.()
  },
})

const PassThrough = defineComponent({ template: '<div><slot /></div>' })

import DictTypeForm from '@/views/dict/DictTypeForm.vue'

describe('DictTypeForm.vue', () => {
  it('should expose validate and getFormData', async () => {
    const wrapper = mount(DictTypeForm, {
      props: { formData: { id: 1, dictCode: 'STATUS', dictName: '状态' } as any },
      global: {
        stubs: {
          'a-form': FormStub,
          'a-form-item': PassThrough,
          'a-input': PassThrough,
          'a-input-number': PassThrough,
          'a-textarea': PassThrough,
          'a-switch': PassThrough,
        },
      },
    })

    const exposed = wrapper.vm as unknown as { validate: () => Promise<void>; getFormData: () => any }
    await exposed.validate()
    const data = exposed.getFormData()
    expect(data.dictCode).toBe('STATUS')
    expect(data.dictName).toBe('状态')
  })
})

