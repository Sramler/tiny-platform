import { mount } from '@vue/test-utils'
import { defineComponent, h, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMocks = vi.hoisted(() => ({
  getResourceTree: vi.fn(),
  getPermissionOptions: vi.fn(),
}))

vi.mock('@/api/resource', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/resource')>()
  return {
    ...actual,
    getResourceTree: apiMocks.getResourceTree,
  }
})
vi.mock('@/api/permission', () => ({
  getPermissionOptions: apiMocks.getPermissionOptions,
}))

vi.mock('ant-design-vue', () => ({
  message: { error: vi.fn(), warning: vi.fn() },
}))

const FormStub = defineComponent({
  inheritAttrs: false,
  setup(_, { slots, attrs, expose }) {
    expose({ validate: () => Promise.resolve() })
    return () => h('form', attrs, slots.default?.())
  },
})

const PassThrough = defineComponent({
  template: '<div><slot /></div>',
})

const InputStub = defineComponent({
  inheritAttrs: false,
  props: {
    value: {
      type: String,
      default: '',
    },
  },
  emits: ['update:value'],
  template: '<input v-bind="$attrs" :value="value" @input="$emit(\'update:value\', $event.target.value)" />',
})

import ResourceForm from '@/views/resource/ResourceForm.vue'

async function flushPromises() {
  await Promise.resolve()
  await nextTick()
}

describe('ResourceForm.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiMocks.getResourceTree.mockResolvedValue([])
    apiMocks.getPermissionOptions.mockResolvedValue([
      { id: 7001, permissionCode: 'system:resource:list', permissionName: '资源查看' },
      { id: 7002, permissionCode: 'system:resource:edit', permissionName: '资源编辑' },
    ])
  })

  it('should load resource tree on mount', async () => {
    const wrapper = mount(ResourceForm, {
      props: {
        mode: 'create',
      },
      global: {
        stubs: {
          'a-form': FormStub,
          'a-form-item': PassThrough,
          'a-input': InputStub,
          'a-input-number': PassThrough,
          'a-switch': PassThrough,
          'a-divider': PassThrough,
          'a-select': PassThrough,
          'a-select-option': PassThrough,
          'a-tree-select': PassThrough,
          'a-button': defineComponent({
            emits: ['click'],
            template: `<button @click="$emit('click')"><slot /></button>`,
          }),
        },
      },
    })
    await flushPromises()

    expect(apiMocks.getResourceTree).toHaveBeenCalled()
    expect(apiMocks.getPermissionOptions).toHaveBeenCalled()
    expect(wrapper.text()).toContain('基本信息')
  })

  it('should preserve requiredPermissionId on untouched edit submit', async () => {
    const wrapper = mount(ResourceForm, {
      props: {
        mode: 'edit',
        resourceData: {
          id: 11,
          name: 'resource:list',
          title: '资源查看',
          type: 3,
          permission: 'system:resource:list',
          requiredPermissionId: 7001,
        },
      },
      global: {
        stubs: {
          'a-form': FormStub,
          'a-form-item': PassThrough,
          'a-input': InputStub,
          'a-input-number': PassThrough,
          'a-switch': PassThrough,
          'a-divider': PassThrough,
          'a-select': PassThrough,
          'a-select-option': PassThrough,
          'a-tree-select': PassThrough,
          'a-button': defineComponent({
            emits: ['click'],
            template: `<button @click="$emit('click')"><slot /></button>`,
          }),
        },
      },
    })

    await flushPromises()
    await wrapper.findAll('button')[1]?.trigger('click')
    await flushPromises()

    const submitPayload = wrapper.emitted('submit')?.[0]?.[0]
    expect(submitPayload.requiredPermissionId).toBe(7001)
    expect(submitPayload.permission).toBe('system:resource:list')
  })

  it('should derive permission from requiredPermissionId selection', async () => {
    const wrapper = mount(ResourceForm, {
      props: {
        mode: 'edit',
        resourceData: {
          id: 11,
          name: 'resource:list',
          title: '资源查看',
          type: 3,
          permission: 'system:resource:list',
          requiredPermissionId: 7001,
        },
      },
      global: {
        stubs: {
          'a-form': FormStub,
          'a-form-item': PassThrough,
          'a-input': InputStub,
          'a-input-number': PassThrough,
          'a-switch': PassThrough,
          'a-divider': PassThrough,
          'a-select': PassThrough,
          'a-select-option': PassThrough,
          'a-tree-select': PassThrough,
          'a-button': defineComponent({
            emits: ['click'],
            template: `<button @click="$emit('click')"><slot /></button>`,
          }),
        },
      },
    })

    await flushPromises()
    const vm = wrapper.vm as unknown as {
      formData: { requiredPermissionId?: number; permission?: string }
      handleRequiredPermissionIdChange: (value?: number) => void
    }
    vm.formData.requiredPermissionId = 7002
    vm.handleRequiredPermissionIdChange(7002)
    await nextTick()
    await wrapper.findAll('button')[1]?.trigger('click')
    await flushPromises()

    const submitPayload = wrapper.emitted('submit')?.[0]?.[0]
    expect(submitPayload.requiredPermissionId).toBe(7002)
    expect(submitPayload.permission).toBe('system:resource:edit')
  })
})
