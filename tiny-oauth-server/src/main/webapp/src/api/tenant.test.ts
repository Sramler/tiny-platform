import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  delete: vi.fn(),
}))

vi.mock('@/utils/request', () => ({
  default: {
    get: requestMocks.get,
    post: requestMocks.post,
    put: requestMocks.put,
    delete: requestMocks.delete,
  },
}))

describe('tenant API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should request tenant list with params', async () => {
    requestMocks.get.mockResolvedValue({
      content: [{ id: 1, code: 't1', name: 'Tenant 1' }],
      totalElements: 1,
    })
    const { tenantList } = await import('@/api/tenant')

    const result = await tenantList({ code: 't1', lifecycleStatus: 'FROZEN', page: 1, size: 10 })

    expect(requestMocks.get).toHaveBeenCalledWith('/sys/tenants', {
      params: { code: 't1', lifecycleStatus: 'FROZEN', page: 1, size: 10 },
    })
    expect(result.content).toHaveLength(1)
    expect(result.content?.[0]?.code).toBe('t1')
  })

  it('should request tenant by id', async () => {
    requestMocks.get.mockResolvedValue({ id: 2, code: 't2', name: 'Tenant 2' })
    const { getTenantById } = await import('@/api/tenant')

    const result = await getTenantById(2)

    expect(requestMocks.get).toHaveBeenCalledWith('/sys/tenants/2')
    expect(result.code).toBe('t2')
  })

  it('should create tenant with idempotency', async () => {
    requestMocks.post.mockResolvedValue({ id: 3, code: 't3' })
    const { createTenant } = await import('@/api/tenant')
    const data = {
      code: 't3',
      name: 'Tenant 3',
      initialAdminUsername: 'tenant3_admin',
      initialAdminPassword: 'Secret123',
      initialAdminConfirmPassword: 'Secret123',
    }

    await createTenant(data)

    expect(requestMocks.post).toHaveBeenCalledWith('/sys/tenants', data, {
      idempotency: {
        scope: 'sys-tenants:create',
        payload: data,
      },
    })
  })

  it('should update tenant with idempotency', async () => {
    requestMocks.put.mockResolvedValue({ id: 4, name: 'Tenant 4 Updated' })
    const { updateTenant } = await import('@/api/tenant')
    const data = { name: 'Tenant 4 Updated' }

    await updateTenant(4, data)

    expect(requestMocks.put).toHaveBeenCalledWith('/sys/tenants/4', data, {
      idempotency: {
        scope: 'sys-tenants:update:4',
        payload: data,
      },
    })
  })

  it('should initialize platform template with idempotency', async () => {
    requestMocks.post.mockResolvedValue({ initialized: true, message: 'ok' })
    const { initializePlatformTemplate } = await import('@/api/tenant')

    await initializePlatformTemplate()

    expect(requestMocks.post).toHaveBeenCalledWith('/sys/tenants/platform-template/initialize', null, {
      idempotency: {
        scope: 'sys-tenants:platform-template:initialize',
        payload: {},
      },
    })
  })

  it('should delete tenant with idempotency', async () => {
    requestMocks.delete.mockResolvedValue(undefined)
    const { deleteTenant } = await import('@/api/tenant')

    await deleteTenant(5)

    expect(requestMocks.delete).toHaveBeenCalledWith('/sys/tenants/5', {
      idempotency: {
        scope: 'sys-tenants:delete:5',
        payload: { id: 5 },
      },
    })
  })

  it('should freeze tenant with idempotency', async () => {
    requestMocks.post.mockResolvedValue({ id: 6, lifecycleStatus: 'FROZEN' })
    const { freezeTenant } = await import('@/api/tenant')

    await freezeTenant(6)

    expect(requestMocks.post).toHaveBeenCalledWith('/sys/tenants/6/freeze', null, {
      idempotency: {
        scope: 'sys-tenants:freeze:6',
        payload: { id: 6 },
      },
    })
  })

  it('should unfreeze tenant with idempotency', async () => {
    requestMocks.post.mockResolvedValue({ id: 7, lifecycleStatus: 'ACTIVE' })
    const { unfreezeTenant } = await import('@/api/tenant')

    await unfreezeTenant(7)

    expect(requestMocks.post).toHaveBeenCalledWith('/sys/tenants/7/unfreeze', null, {
      idempotency: {
        scope: 'sys-tenants:unfreeze:7',
        payload: { id: 7 },
      },
    })
  })

  it('should decommission tenant with idempotency', async () => {
    requestMocks.post.mockResolvedValue({ id: 8, lifecycleStatus: 'DECOMMISSIONED' })
    const { decommissionTenant } = await import('@/api/tenant')

    await decommissionTenant(8)

    expect(requestMocks.post).toHaveBeenCalledWith('/sys/tenants/8/decommission', null, {
      idempotency: {
        scope: 'sys-tenants:decommission:8',
        payload: { id: 8 },
      },
    })
  })
})
