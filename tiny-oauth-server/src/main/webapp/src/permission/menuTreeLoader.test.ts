import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  getAccessToken: vi.fn(),
  fetchMenuTreeSnapshot: vi.fn(),
}))

vi.mock('@/auth/auth', () => ({
  useAuth: () => ({
    getAccessToken: mocks.getAccessToken,
  }),
}))

vi.mock('@/api/menu', () => ({
  fetchMenuTreeSnapshot: mocks.fetchMenuTreeSnapshot,
}))

function token(payload: Record<string, unknown>) {
  return [
    'header',
    Buffer.from(JSON.stringify(payload)).toString('base64url'),
    'signature',
  ].join('.')
}

describe('menuTreeLoader', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    window.localStorage.clear()
    mocks.getAccessToken.mockResolvedValue(token({
      userId: 7,
      activeTenantId: 9,
      activeScopeType: 'TENANT',
      activeScopeId: 9,
      permissionsVersion: 'perm-v1',
    }))
  })

  it('stores a fresh network result with its server ETag', async () => {
    const menus = [{ id: 1, name: 'sys', title: '系统' }]
    mocks.fetchMenuTreeSnapshot.mockResolvedValue({
      status: 'ok',
      menus,
      etag: '"etag-1"',
      menuConfigVersion: 'menu-v1',
      permissionsVersion: 'perm-v1',
    })
    const { loadVerifiedMenuTree } = await import('./menuTreeLoader')

    const result = await loadVerifiedMenuTree()

    expect(result).toEqual({ menus, source: 'network', etag: '"etag-1"' })
    expect(mocks.fetchMenuTreeSnapshot).toHaveBeenCalledWith({ ifNoneMatch: undefined })
  })

  it('reuses local menus only after the server returns 304 for the cached ETag', async () => {
    const menus = [{ id: 1, name: 'sys', title: '系统' }]
    mocks.fetchMenuTreeSnapshot.mockResolvedValueOnce({
      status: 'ok',
      menus,
      etag: '"etag-1"',
      menuConfigVersion: 'menu-v1',
      permissionsVersion: 'perm-v1',
    })
    const { loadVerifiedMenuTree } = await import('./menuTreeLoader')

    await loadVerifiedMenuTree()
    mocks.fetchMenuTreeSnapshot.mockResolvedValueOnce({
      status: 'not_modified',
      etag: '"etag-1"',
      menuConfigVersion: 'menu-v1',
      permissionsVersion: 'perm-v1',
    })

    const cached = await loadVerifiedMenuTree()

    expect(cached).toEqual({ menus, source: 'verified_cache', etag: '"etag-1"' })
    expect(mocks.fetchMenuTreeSnapshot).toHaveBeenLastCalledWith({ ifNoneMatch: '"etag-1"' })
  })
})
