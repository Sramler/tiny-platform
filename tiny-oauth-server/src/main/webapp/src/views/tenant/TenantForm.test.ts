import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { describe, expect, it, vi } from 'vitest'

vi.mock('ant-design-vue', () => ({
  message: { error: vi.fn(), warning: vi.fn() },
}))

const FormStub = defineComponent({
  inheritAttrs: false,
  setup(_, { slots, expose, attrs }) {
    expose({ validate: () => Promise.resolve() })
    return () => h('form', attrs, slots.default?.())
  },
})

const PassThrough = defineComponent({
  template: '<div><slot /></div>',
})

import TenantForm from '@/views/tenant/TenantForm.vue'

describe('TenantForm.vue', () => {
  it('should emit submit after validation passes', async () => {
    const wrapper = mount(TenantForm, {
      props: {
        tenantData: { id: '1', code: 't1', name: 'Tenant 1' },
      },
      global: {
        stubs: {
          'a-form': FormStub,
          'a-form-item': PassThrough,
          'a-input': PassThrough,
          'a-input-password': PassThrough,
          'a-input-number': PassThrough,
          'a-textarea': PassThrough,
          'a-switch': PassThrough,
          'a-button': defineComponent({
            emits: ['click'],
            template: `<button @click="$emit('click')"><slot /></button>`,
          }),
        },
      },
    })

    await wrapper.find('button').trigger('click') // cancel
    await wrapper.findAll('button')[1]!.trigger('click') // save

    const emitted = wrapper.emitted('submit')
    expect(emitted).toBeTruthy()
    expect(emitted?.[0]?.[0]).toEqual(expect.objectContaining({ code: 't1', name: 'Tenant 1' }))
  })

  it('should only emit base tenant fields', async () => {
    const wrapper = mount(TenantForm, {
      props: {},
      global: {
        stubs: {
          'a-form': FormStub,
          'a-form-item': PassThrough,
          'a-input': PassThrough,
          'a-input-password': PassThrough,
          'a-input-number': PassThrough,
          'a-textarea': PassThrough,
          'a-switch': PassThrough,
          'a-button': defineComponent({
            emits: ['click'],
            template: `<button @click="$emit('click')"><slot /></button>`,
          }),
        },
      },
    })

    await wrapper.findAll('button')[1]!.trigger('click')

    const emitted = wrapper.emitted('submit')
    expect(emitted).toBeTruthy()
    expect(emitted?.[0]?.[0]).toEqual(expect.objectContaining({
      code: '',
      name: '',
      enabled: true,
    }))
    expect(emitted?.[0]?.[0]).not.toHaveProperty('initialAdminUsername')
    expect(emitted?.[0]?.[0]).not.toHaveProperty('initialAdminPassword')
  })
})
