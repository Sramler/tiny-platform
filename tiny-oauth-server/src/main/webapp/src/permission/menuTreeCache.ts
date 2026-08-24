import type { MenuItem, MenuTreeSnapshot } from '@/api/menu'
import type { SessionPrincipal } from '@/auth/auth'

const CACHE_SCHEMA_VERSION = 'v1'
const CACHE_PREFIX = 'tiny-platform:menu-tree'
const APP_CACHE_VERSION = 'webapp-runtime-v1'
const MAX_CACHE_ENTRIES = 24

export interface MenuTreeRuntimeContext {
  userId: string
  activeTenantId: string
  activeScopeType: string
  activeScopeId: string
  permissionsVersion: string
  appVersion: string
}

interface MenuTreeCacheIndex {
  schemaVersion: string
  contextKey: string
  entryKey: string
  etag: string
  menuConfigVersion?: string
  permissionsVersion?: string
  updatedAt: number
}

interface MenuTreeCacheEntry {
  schemaVersion: string
  entryKey: string
  etag: string
  menuConfigVersion?: string
  permissionsVersion?: string
  menus: MenuItem[]
  updatedAt: number
}

function storage(): Storage | null {
  try {
    return typeof window === 'undefined' ? null : window.localStorage
  } catch {
    return null
  }
}

function stableString(value: unknown): string {
  if (value == null || value === '') return 'none'
  return String(value)
}

function encodeKey(value: string): string {
  return encodeURIComponent(value).replace(/[!'()*]/g, (char) => `%${char.charCodeAt(0).toString(16)}`)
}

export function resolveMenuTreeRuntimeContext(
  principal?: SessionPrincipal | null,
): MenuTreeRuntimeContext | null {
  if (!principal) return null
  const userId = stableString(principal.userId ?? principal.id)
  const permissionsVersion = stableString(principal.permissionsVersion)
  if (userId === 'none' || permissionsVersion === 'none') return null

  const activeTenantId = stableString(principal.activeTenantId)
  const activeScopeType = stableString(
    principal.activeScopeType ?? (activeTenantId === 'none' ? 'PLATFORM' : 'TENANT'),
  ).toUpperCase()
  const activeScopeId = stableString(
    principal.activeScopeId ?? (activeScopeType === 'TENANT' ? activeTenantId : null),
  )

  return {
    userId,
    activeTenantId,
    activeScopeType,
    activeScopeId,
    permissionsVersion,
    appVersion: APP_CACHE_VERSION,
  }
}

function contextKey(context: MenuTreeRuntimeContext): string {
  return [
    CACHE_SCHEMA_VERSION,
    context.appVersion,
    context.userId,
    context.activeTenantId,
    context.activeScopeType,
    context.activeScopeId,
    context.permissionsVersion,
  ].join('|')
}

function indexStorageKey(context: MenuTreeRuntimeContext): string {
  return `${CACHE_PREFIX}:index:${encodeKey(contextKey(context))}`
}

function entryStorageKey(entryKey: string): string {
  return `${CACHE_PREFIX}:entry:${encodeKey(entryKey)}`
}

function readJson<T>(key: string): T | null {
  const s = storage()
  if (!s) return null
  try {
    const raw = s.getItem(key)
    return raw ? (JSON.parse(raw) as T) : null
  } catch {
    return null
  }
}

function writeJson(key: string, value: unknown) {
  const s = storage()
  if (!s) return
  try {
    s.setItem(key, JSON.stringify(value))
  } catch {
    // 浏览器存储空间不足时只放弃前端加速缓存，不影响后端权威加载。
  }
}

function trimEntryCache() {
  const s = storage()
  if (!s) return
  const entryPrefix = `${CACHE_PREFIX}:entry:`
  const entries: Array<{ key: string; updatedAt: number }> = []
  for (let i = 0; i < s.length; i += 1) {
    const key = s.key(i)
    if (!key || !key.startsWith(entryPrefix)) continue
    const entry = readJson<{ updatedAt?: unknown }>(key)
    entries.push({
      key,
      updatedAt: typeof entry?.updatedAt === 'number' ? entry.updatedAt : 0,
    })
  }
  if (entries.length <= MAX_CACHE_ENTRIES) return
  entries
    .sort((left, right) => left.updatedAt - right.updatedAt)
    .slice(0, entries.length - MAX_CACHE_ENTRIES)
    .forEach((entry) => s.removeItem(entry.key))
}

export function readMenuTreeCacheIndex(context: MenuTreeRuntimeContext): MenuTreeCacheIndex | null {
  const key = indexStorageKey(context)
  const index = readJson<MenuTreeCacheIndex>(key)
  if (!index || index.schemaVersion !== CACHE_SCHEMA_VERSION || index.contextKey !== contextKey(context)) {
    return null
  }
  return index.etag && index.entryKey ? index : null
}

export function readMenuTreeCacheEntry(index: MenuTreeCacheIndex): MenuItem[] | null {
  const entry = readJson<MenuTreeCacheEntry>(entryStorageKey(index.entryKey))
  if (!entry || entry.schemaVersion !== CACHE_SCHEMA_VERSION) return null
  if (entry.etag !== index.etag || entry.entryKey !== index.entryKey) return null
  return Array.isArray(entry.menus) ? entry.menus : null
}

export function writeMenuTreeCache(context: MenuTreeRuntimeContext, snapshot: MenuTreeSnapshot): void {
  if (!snapshot.etag || !Array.isArray(snapshot.menus)) return
  const key = contextKey(context)
  const entryKey = [
    key,
    snapshot.permissionsVersion || context.permissionsVersion,
    snapshot.menuConfigVersion || 'none',
    snapshot.etag,
  ].join('|')
  const now = Date.now()
  writeJson(entryStorageKey(entryKey), {
    schemaVersion: CACHE_SCHEMA_VERSION,
    entryKey,
    etag: snapshot.etag,
    menuConfigVersion: snapshot.menuConfigVersion,
    permissionsVersion: snapshot.permissionsVersion,
    menus: snapshot.menus,
    updatedAt: now,
  } satisfies MenuTreeCacheEntry)
  writeJson(indexStorageKey(context), {
    schemaVersion: CACHE_SCHEMA_VERSION,
    contextKey: key,
    entryKey,
    etag: snapshot.etag,
    menuConfigVersion: snapshot.menuConfigVersion,
    permissionsVersion: snapshot.permissionsVersion,
    updatedAt: now,
  } satisfies MenuTreeCacheIndex)
  trimEntryCache()
}
