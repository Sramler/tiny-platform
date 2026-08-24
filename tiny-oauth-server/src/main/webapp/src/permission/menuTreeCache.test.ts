import { beforeEach, describe, expect, it } from 'vitest'
import {
  readMenuTreeCacheEntry,
  readMenuTreeCacheIndex,
  resolveMenuTreeRuntimeContext,
  writeMenuTreeCache,
} from './menuTreeCache'

describe('menuTreeCache', () => {
  beforeEach(() => {
    window.localStorage.clear()
  })

  it('builds a runtime context from the session principal', () => {
    const context = resolveMenuTreeRuntimeContext({
      userId: 7,
      activeTenantId: 9,
      activeScopeType: 'TENANT',
      activeScopeId: 9,
      permissionsVersion: 'perm-v1',
    })

    expect(context).toMatchObject({
      userId: '7',
      activeTenantId: '9',
      activeScopeType: 'TENANT',
      activeScopeId: '9',
      permissionsVersion: 'perm-v1',
    })
  })

  it('stores menus behind an index that is scoped by permissions version', () => {
    const context = resolveMenuTreeRuntimeContext({
      userId: 7,
      activeTenantId: 9,
      activeScopeType: 'TENANT',
      activeScopeId: 9,
      permissionsVersion: 'perm-v1',
    })!
    const menus = [{ id: 1, name: 'sys', title: '系统' }]

    writeMenuTreeCache(context, {
      status: 'ok',
      menus,
      etag: '"etag-1"',
      menuConfigVersion: 'menu-v1',
      permissionsVersion: 'perm-v1',
    })

    const index = readMenuTreeCacheIndex(context)
    expect(index?.etag).toBe('"etag-1"')
    expect(readMenuTreeCacheEntry(index!)).toEqual(menus)

    const changedPermissionContext = { ...context, permissionsVersion: 'perm-v2' }
    expect(readMenuTreeCacheIndex(changedPermissionContext)).toBeNull()
  })
})
