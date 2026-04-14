import { mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const resourceMocks = vi.hoisted(() => ({
  getResourceTree: vi.fn(),
}))

const roleMocks = vi.hoisted(() => ({
  getRolePermissions: vi.fn(),
}))

vi.mock('@/api/resource', () => ({
  getResourceTree: resourceMocks.getResourceTree,
}))

vi.mock('@/api/role', () => ({
  getRolePermissions: roleMocks.getRolePermissions,
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
    resourceMocks.getResourceTree.mockResolvedValue([
      { id: 1, title: '菜单 1', requiredPermissionId: 101 },
      { id: 2, title: '菜单 2', requiredPermissionId: 102 },
    ])
    roleMocks.getRolePermissions.mockResolvedValue([101, 102])
  })

  it('should load data when opened and emit permissionIds-only submit payload on ok', async () => {
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
    expect(roleMocks.getRolePermissions).toHaveBeenCalledWith(9)

    await wrapper.find('button.ok').trigger('click')
    const submitEvents = wrapper.emitted('submit')
    expect(submitEvents).toBeTruthy()
    expect(submitEvents?.[0]).toEqual([{ permissionIds: [101, 102] }])
    expect(submitEvents?.[0]?.[0]).not.toHaveProperty('resourceIds')
  })
})
