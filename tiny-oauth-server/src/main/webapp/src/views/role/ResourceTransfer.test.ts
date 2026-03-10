import { mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const resourceMocks = vi.hoisted(() => ({
  getResourceTree: vi.fn(),
}))

const roleMocks = vi.hoisted(() => ({
  getRoleResources: vi.fn(),
}))

vi.mock('@/api/resource', () => ({
  getResourceTree: resourceMocks.getResourceTree,
}))

vi.mock('@/api/role', () => ({
  getRoleResources: roleMocks.getRoleResources,
}))

const ModalStub = defineComponent({
  emits: ['ok', 'cancel', 'update:open'],
  template: `<div><button class="ok" @click="$emit('ok')">ok</button><slot /></div>`,
})

const PassThrough = defineComponent({ template: '<div><slot /></div>' })

import ResourceTransfer from '@/views/role/ResourceTransfer.vue'

async function flushPromises() {
  await Promise.resolve()
  await nextTick()
  await Promise.resolve()
  await nextTick()
}

describe('ResourceTransfer.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    resourceMocks.getResourceTree.mockResolvedValue([])
    roleMocks.getRoleResources.mockResolvedValue([1, 2])
  })

  it('should load data when opened and emit submit on ok', async () => {
    const wrapper = mount(ResourceTransfer, {
      props: { open: true, roleId: 9, title: '配置资源' },
      global: {
        stubs: {
          'a-modal': ModalStub,
          'a-transfer': PassThrough,
          'a-tree': PassThrough,
        },
      },
    })
    await flushPromises()

    expect(resourceMocks.getResourceTree).toHaveBeenCalled()
    expect(roleMocks.getRoleResources).toHaveBeenCalledWith(9)

    await wrapper.find('button.ok').trigger('click')
    expect(wrapper.emitted('submit')).toBeTruthy()
  })
})

