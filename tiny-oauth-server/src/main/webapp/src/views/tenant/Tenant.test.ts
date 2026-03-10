import { mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMocks = vi.hoisted(() => ({
  tenantList: vi.fn(),
}))

vi.mock('@/api/tenant', () => ({
  tenantList: apiMocks.tenantList,
  getTenantById: vi.fn(),
  createTenant: vi.fn(),
  updateTenant: vi.fn(),
  deleteTenant: vi.fn(),
}))

vi.mock('@/utils/debounce', () => ({
  useThrottle: (fn: (...args: unknown[]) => unknown) => fn,
}))

const PassThrough = defineComponent({ template: '<div><slot /></div>' })

import Tenant from '@/views/tenant/Tenant.vue'

async function flushPromises() {
  await Promise.resolve()
  await nextTick()
  await Promise.resolve()
  await nextTick()
}

describe('Tenant.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiMocks.tenantList.mockResolvedValue({ content: [], totalElements: 0 })
  })

  it('should render title and call tenantList on mount', async () => {
    const wrapper = mount(Tenant, {
      global: {
        stubs: {
          'a-form': PassThrough,
          'a-form-item': PassThrough,
          'a-input': PassThrough,
          'a-select': PassThrough,
          'a-select-option': PassThrough,
          'a-button': PassThrough,
          'a-tooltip': PassThrough,
          'a-table': defineComponent({ props: ['dataSource'], template: '<div class="table" />' }),
          'a-tag': PassThrough,
          'a-pagination': PassThrough,
          'a-drawer': PassThrough,
          'a-input-number': PassThrough,
          'a-switch': PassThrough,
          'a-textarea': PassThrough,
          PlusOutlined: PassThrough,
          ReloadOutlined: PassThrough,
          EditOutlined: PassThrough,
          DeleteOutlined: PassThrough,
        },
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('租户列表')
    expect(apiMocks.tenantList).toHaveBeenCalled()
  })
})
