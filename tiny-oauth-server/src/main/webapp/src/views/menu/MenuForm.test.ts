import { mount } from '@vue/test-utils'
import { defineComponent, h, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMocks = vi.hoisted(() => ({
  menuTreeAll: vi.fn(),
  getPermissionOptions: vi.fn(),
}))

vi.mock('@/api/menu', () => ({
  menuTreeAll: apiMocks.menuTreeAll,
}))

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

import MenuForm from '@/views/menu/MenuForm.vue'

async function flushPromises() {
  await Promise.resolve()
  await nextTick()
}

describe('MenuForm.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiMocks.menuTreeAll.mockResolvedValue([])
    apiMocks.getPermissionOptions.mockResolvedValue([])
  })

  it('should load menu tree on mount', async () => {
    const wrapper = mount(MenuForm, {
      props: {
        mode: 'create',
      },
      global: {
        stubs: {
          'a-form': FormStub,
          'a-form-item': PassThrough,
          'a-input': PassThrough,
          'a-input-number': PassThrough,
          'a-switch': PassThrough,
          'a-select': PassThrough,
          'a-divider': PassThrough,
          'a-tree-select': PassThrough,
          'a-button': defineComponent({
            emits: ['click'],
            template: `<button @click="$emit('click')"><slot /></button>`,
          }),
          'a-modal': PassThrough,
          IconSelect: PassThrough,
          Icon: PassThrough,
        },
      },
    })
    await flushPromises()

    expect(apiMocks.menuTreeAll).toHaveBeenCalled()
    expect(apiMocks.getPermissionOptions).toHaveBeenCalled()
    expect(wrapper.text()).toContain('基本信息')
  })

  it('should emit derived permission payload with requiredPermissionId', async () => {
    apiMocks.getPermissionOptions.mockResolvedValue([
      {
        id: 501,
        permissionCode: 'system:menu:list',
        permissionName: '菜单读取',
      },
    ])
    const wrapper = mount(MenuForm, {
      props: {
        mode: 'edit',
        menuData: {
          id: 9,
          name: 'system-menu',
          title: '菜单管理',
          permission: 'system:menu:list',
          requiredPermissionId: 501,
        },
      },
      global: {
        stubs: {
          'a-form': FormStub,
          'a-form-item': PassThrough,
          'a-input': PassThrough,
          'a-input-number': PassThrough,
          'a-switch': PassThrough,
          'a-select': PassThrough,
          'a-divider': PassThrough,
          'a-tree-select': PassThrough,
          'a-button': defineComponent({
            emits: ['click'],
            template: `<button @click="$emit('click')"><slot /></button>`,
          }),
          'a-modal': PassThrough,
          IconSelect: PassThrough,
          Icon: PassThrough,
        },
      },
    })
    await flushPromises()

    const buttons = wrapper.findAll('button')
    const submitButton = buttons.at(-1)
    await submitButton?.trigger('click')
    await flushPromises()

    expect(wrapper.emitted('submit')).toBeTruthy()
    expect(wrapper.emitted('submit')?.[0]?.[0]).toMatchObject({
      id: 9,
      name: 'system-menu',
      title: '菜单管理',
      permission: 'system:menu:list',
      requiredPermissionId: 501,
    })
  })
})
