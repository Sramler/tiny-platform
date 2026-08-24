import { fetchMenuTreeSnapshot, type MenuItem } from '@/api/menu'
import { useAuth } from '@/auth/auth'
import {
  readMenuTreeCacheEntry,
  readMenuTreeCacheIndex,
  resolveMenuTreeRuntimeContext,
  writeMenuTreeCache,
} from './menuTreeCache'

export interface VerifiedMenuTreeResult {
  menus: MenuItem[]
  source: 'network' | 'verified_cache'
  etag?: string
}

export async function loadVerifiedMenuTree(): Promise<VerifiedMenuTreeResult> {
  const context = resolveMenuTreeRuntimeContext(useAuth().user.value)
  const index = context ? readMenuTreeCacheIndex(context) : null
  const snapshot = await fetchMenuTreeSnapshot({ ifNoneMatch: index?.etag })

  if (snapshot.status === 'not_modified') {
    const cacheIndex = index
    const cachedMenus = cacheIndex ? readMenuTreeCacheEntry(cacheIndex) : null
    if (cachedMenus) {
      return {
        menus: cachedMenus,
        source: 'verified_cache',
        etag: snapshot.etag || cacheIndex?.etag,
      }
    }
    const refetch = await fetchMenuTreeSnapshot()
    if (refetch.status === 'ok') {
      if (context) {
        writeMenuTreeCache(context, refetch)
      }
      return {
        menus: refetch.menus,
        source: 'network',
        etag: refetch.etag,
      }
    }
  }

  if (snapshot.status === 'ok') {
    if (context) {
      writeMenuTreeCache(context, snapshot)
    }
    return {
      menus: snapshot.menus,
      source: 'network',
      etag: snapshot.etag,
    }
  }

  throw new Error('菜单缓存校验失败')
}
