import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMocks = vi.hoisted(() => ({
  get: vi.fn(),
}))

vi.mock('@/utils/request', () => ({
  default: {
    get: requestMocks.get,
  },
}))

describe('platform-role API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should request platform role options from platform domain lookup endpoint', async () => {
    requestMocks.get.mockResolvedValue([
      {
        roleId: 11,
        code: 'ROLE_PLATFORM_ADMIN',
        name: '平台管理员',
        enabled: true,
        builtin: true,
        riskLevel: 'NORMAL',
        approvalMode: 'NONE',
      },
    ])
    const { listPlatformRoleOptions } = await import('@/api/platform-role')

    const result = await listPlatformRoleOptions({ keyword: 'admin', limit: 50 })

    expect(requestMocks.get).toHaveBeenCalledWith('/platform/roles/options', {
      params: {
        keyword: 'admin',
        limit: 50,
      },
    })
    expect(result).toHaveLength(1)
  })
})
