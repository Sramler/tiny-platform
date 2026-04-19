import { beforeEach, describe, expect, it, vi } from 'vitest'
import request from '@/utils/request'
import {
  createPlatformCardinality,
  createPlatformHierarchy,
  createPlatformMutex,
  createPlatformPrerequisite,
  deletePlatformCardinality,
  deletePlatformHierarchy,
  deletePlatformMutex,
  deletePlatformPrerequisite,
  listPlatformCardinalities,
  listPlatformHierarchies,
  listPlatformMutexes,
  listPlatformPrerequisites,
  listPlatformViolations,
} from './platformRoleConstraint'

vi.mock('@/utils/request', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    delete: vi.fn(),
  },
}))

const requestMocks = request as unknown as {
  get: ReturnType<typeof vi.fn>
  post: ReturnType<typeof vi.fn>
  delete: ReturnType<typeof vi.fn>
}

describe('platformRoleConstraint API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('listPlatformHierarchies calls platform path', async () => {
    requestMocks.get.mockResolvedValue([])
    await listPlatformHierarchies()
    expect(requestMocks.get).toHaveBeenCalledWith('/platform/role-constraints/hierarchy')
  })

  it('createPlatformHierarchy uses idempotency scope', async () => {
    requestMocks.post.mockResolvedValue({})
    await createPlatformHierarchy({ parentRoleId: 1, childRoleId: 2 })
    expect(requestMocks.post).toHaveBeenCalledWith(
      '/platform/role-constraints/hierarchy',
      { parentRoleId: 1, childRoleId: 2 },
      expect.objectContaining({
        idempotency: expect.objectContaining({ scope: 'platform-role-constraints:hierarchy:create' }),
      }),
    )
  })

  it('listPlatformViolations passes pagination', async () => {
    requestMocks.get.mockResolvedValue({ content: [], totalElements: 0 })
    await listPlatformViolations({ page: 0, size: 20 })
    expect(requestMocks.get).toHaveBeenCalledWith('/platform/role-constraints/violations', {
      params: { page: 0, size: 20 },
    })
  })

  it('deletePlatformMutex uses platform path', async () => {
    requestMocks.delete.mockResolvedValue({})
    await deletePlatformMutex({ roleIdA: 1, roleIdB: 2 })
    expect(requestMocks.delete).toHaveBeenCalledWith(
      '/platform/role-constraints/mutex',
      expect.objectContaining({
        params: { roleIdA: 1, roleIdB: 2 },
      }),
    )
  })

  it('list helpers use /platform/role-constraints base', async () => {
    requestMocks.get.mockResolvedValue([])
    await listPlatformMutexes()
    expect(requestMocks.get).toHaveBeenCalledWith('/platform/role-constraints/mutex')
    await listPlatformPrerequisites()
    expect(requestMocks.get).toHaveBeenCalledWith('/platform/role-constraints/prerequisite')
    await listPlatformCardinalities()
    expect(requestMocks.get).toHaveBeenCalledWith('/platform/role-constraints/cardinality')
  })

  it('deletePlatformCardinality uses scope in idempotency', async () => {
    requestMocks.delete.mockResolvedValue({})
    await deletePlatformCardinality({ roleId: 9, scopeType: 'PLATFORM' })
    expect(requestMocks.delete).toHaveBeenCalledWith(
      '/platform/role-constraints/cardinality',
      expect.objectContaining({
        idempotency: expect.objectContaining({
          scope: 'platform-role-constraints:cardinality:delete:9:PLATFORM',
        }),
      }),
    )
  })

  it('createPlatformCardinality posts body', async () => {
    requestMocks.post.mockResolvedValue({})
    await createPlatformCardinality({ roleId: 1, scopeType: 'PLATFORM', maxAssignments: 2 })
    expect(requestMocks.post).toHaveBeenCalledWith(
      '/platform/role-constraints/cardinality',
      { roleId: 1, scopeType: 'PLATFORM', maxAssignments: 2 },
      expect.any(Object),
    )
  })
})
