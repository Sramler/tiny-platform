import type { LocationQuery, LocationQueryRaw, LocationQueryValueRaw } from 'vue-router'

export type PlatformRuntimeModule = 'scheduling' | 'process' | 'dicts'
export type PlatformAuditTab = 'authentication' | 'authorization'
export type PlatformRoleConstraintTab =
  | 'hierarchy'
  | 'mutex'
  | 'prerequisite'
  | 'cardinality'
  | 'violations'
export type PlatformSchedulingTab = 'dag' | 'task' | 'taskType' | 'history' | 'audit'
export type PlatformProcessTab = 'modeling' | 'deployment' | 'definition' | 'instance' | 'task'
export type PlatformDictTab = 'type' | 'item' | 'overrides'
export type PlatformUserTab = 'platformUsers' | 'tenantStewardship'

const PLATFORM_AUDIT_TABS: PlatformAuditTab[] = ['authentication', 'authorization']
const PLATFORM_ROLE_CONSTRAINT_TABS: PlatformRoleConstraintTab[] = [
  'hierarchy',
  'mutex',
  'prerequisite',
  'cardinality',
  'violations',
]
const PLATFORM_SCHEDULING_TABS: PlatformSchedulingTab[] = [
  'dag',
  'task',
  'taskType',
  'history',
  'audit',
]
const PLATFORM_PROCESS_TABS: PlatformProcessTab[] = [
  'modeling',
  'deployment',
  'definition',
  'instance',
  'task',
]
const PLATFORM_DICT_TABS: PlatformDictTab[] = ['type', 'item', 'overrides']
const PLATFORM_USER_TABS: PlatformUserTab[] = ['platformUsers', 'tenantStewardship']

type QueryLike = LocationQuery | Record<string, unknown>

function isQueryValueRaw(value: unknown): value is LocationQueryValueRaw {
  return value == null || typeof value === 'string' || typeof value === 'number'
}

function normalizeQueryValue(value: unknown): LocationQueryRaw[string] | undefined {
  if (Array.isArray(value)) {
    return value.filter(isQueryValueRaw)
  }
  if (isQueryValueRaw(value)) {
    return value
  }
  return undefined
}

function normalizeQueryRaw(query: QueryLike | null | undefined): LocationQueryRaw {
  const nextQuery: LocationQueryRaw = {}
  for (const [key, value] of Object.entries(query ?? {})) {
    const normalizedValue = normalizeQueryValue(value)
    if (normalizedValue !== undefined) {
      nextQuery[key] = normalizedValue
    }
  }
  return nextQuery
}

function withoutTenantRuntimeQuery(query: QueryLike | null | undefined): LocationQueryRaw {
  const nextQuery = normalizeQueryRaw(query)
  delete nextQuery.activeTenantId
  delete nextQuery.targetTenantId
  return nextQuery
}

export function isPlatformSchedulingTab(
  value: string | null | undefined,
): value is PlatformSchedulingTab {
  return PLATFORM_SCHEDULING_TABS.includes(value as PlatformSchedulingTab)
}

export function isPlatformAuditTab(value: string | null | undefined): value is PlatformAuditTab {
  return PLATFORM_AUDIT_TABS.includes(value as PlatformAuditTab)
}

export function isPlatformRoleConstraintTab(
  value: string | null | undefined,
): value is PlatformRoleConstraintTab {
  return PLATFORM_ROLE_CONSTRAINT_TABS.includes(value as PlatformRoleConstraintTab)
}

export function isPlatformProcessTab(value: string | null | undefined): value is PlatformProcessTab {
  return PLATFORM_PROCESS_TABS.includes(value as PlatformProcessTab)
}

export function isPlatformDictTab(value: string | null | undefined): value is PlatformDictTab {
  return PLATFORM_DICT_TABS.includes(value as PlatformDictTab)
}

export function isPlatformUserTab(value: string | null | undefined): value is PlatformUserTab {
  return PLATFORM_USER_TABS.includes(value as PlatformUserTab)
}

export function resolvePlatformSchedulingTabFromPath(
  path: string | null | undefined,
): PlatformSchedulingTab | null {
  if (!path || !path.startsWith('/platform/scheduling/')) {
    return null
  }
  const tab = path.slice('/platform/scheduling/'.length).split('/')[0]
  if (tab === 'task-type') {
    return 'taskType'
  }
  return isPlatformSchedulingTab(tab) ? tab : null
}

export function resolvePlatformAuditTabFromPath(
  path: string | null | undefined,
): PlatformAuditTab | null {
  if (!path || !path.startsWith('/platform/audit/')) {
    return null
  }
  const tab = path.slice('/platform/audit/'.length).split('/')[0]
  return isPlatformAuditTab(tab) ? tab : null
}

export function resolvePlatformRoleConstraintTabFromPath(
  path: string | null | undefined,
): PlatformRoleConstraintTab | null {
  if (!path || !path.startsWith('/platform/role-constraints/')) {
    return null
  }
  const tab = path.slice('/platform/role-constraints/'.length).split('/')[0]
  return isPlatformRoleConstraintTab(tab) ? tab : null
}

export function inferPlatformSchedulingTabFromPath(
  path: string | null | undefined,
): PlatformSchedulingTab {
  if (!path) {
    return 'dag'
  }
  if (path.includes('/scheduling/task-type')) {
    return 'taskType'
  }
  if (path.includes('/scheduling/task')) {
    return 'task'
  }
  if (path.includes('/scheduling/dag/history')) {
    return 'history'
  }
  if (path.includes('/scheduling/audit')) {
    return 'audit'
  }
  return 'dag'
}

export function resolvePlatformProcessTabFromPath(
  path: string | null | undefined,
): PlatformProcessTab | null {
  if (!path || !path.startsWith('/platform/process/')) {
    return null
  }
  const tab = path.slice('/platform/process/'.length).split('/')[0]
  return isPlatformProcessTab(tab) ? tab : null
}

export function inferPlatformProcessTabFromPath(path: string | null | undefined): PlatformProcessTab {
  if (!path) {
    return 'definition'
  }
  if (
    path.includes('/process/modeling') ||
    path.includes('/workflowDesign') ||
    path.includes('/modeling')
  ) {
    return 'modeling'
  }
  if (path.includes('/deployment')) {
    return 'deployment'
  }
  if (path.includes('/process/instance') || path.includes('/instance')) {
    return 'instance'
  }
  if (path.includes('/process/task') || path.includes('/task')) {
    return 'task'
  }
  if (path.includes('/process/definition') || path.includes('/definition')) {
    return 'definition'
  }
  return 'definition'
}

export function resolvePlatformDictTabFromPath(
  path: string | null | undefined,
): PlatformDictTab | null {
  if (!path || !path.startsWith('/platform/dicts/')) {
    return null
  }
  const tab = path.slice('/platform/dicts/'.length).split('/')[0]
  return isPlatformDictTab(tab) ? tab : null
}

export function buildPlatformSchedulingTabPath(tab: string): string {
  if (tab === 'taskType') {
    return '/platform/scheduling/task-type'
  }
  const safeTab = isPlatformSchedulingTab(tab) ? tab : 'dag'
  return `/platform/scheduling/${safeTab}`
}

export function buildPlatformAuditTabPath(tab: string): string {
  const safeTab = isPlatformAuditTab(tab) ? tab : 'authentication'
  return `/platform/audit/${safeTab}`
}

export function buildPlatformRoleConstraintTabPath(tab: string): string {
  const safeTab = isPlatformRoleConstraintTab(tab) ? tab : 'hierarchy'
  return `/platform/role-constraints/${safeTab}`
}

export function buildPlatformProcessTabPath(tab: string): string {
  const safeTab = isPlatformProcessTab(tab) ? tab : 'definition'
  return `/platform/process/${safeTab}`
}

export function buildPlatformDictTabPath(tab: string): string {
  const safeTab = isPlatformDictTab(tab) ? tab : 'type'
  return `/platform/dicts/${safeTab}`
}

export function resolvePlatformUserTabFromPath(
  path: string | null | undefined,
): PlatformUserTab | null {
  if (!path || !path.startsWith('/platform/users/')) {
    return null
  }
  const tab = path.slice('/platform/users/'.length).split('/')[0]
  if (tab === 'governance') {
    return 'platformUsers'
  }
  if (tab === 'tenant-stewardship') {
    return 'tenantStewardship'
  }
  return null
}

export function buildPlatformUserTabPath(tab: string): string {
  return tab === 'tenantStewardship'
    ? '/platform/users/tenant-stewardship'
    : '/platform/users/governance'
}

export function buildPlatformProcessRouteQuery(
  query: QueryLike | null | undefined,
): LocationQueryRaw {
  const nextQuery = withoutTenantRuntimeQuery(query)
  delete nextQuery.tab
  delete nextQuery.target
  return nextQuery
}

export function buildPlatformDictRouteQuery(
  query: QueryLike | null | undefined,
): LocationQueryRaw {
  const nextQuery = withoutTenantRuntimeQuery(query)
  delete nextQuery.tab
  return nextQuery
}

export function buildPlatformUserRouteQuery(
  query: QueryLike | null | undefined,
  tab: PlatformUserTab,
): LocationQueryRaw {
  const nextQuery = withoutTenantRuntimeQuery(query)
  delete nextQuery.tab
  if (tab === 'platformUsers') {
    delete nextQuery.tenantId
  }
  return nextQuery
}

export function isPlatformRuntimePath(
  path: string | null | undefined,
  module?: PlatformRuntimeModule,
): boolean {
  if (!path) return false
  if (module === 'scheduling') {
    return path === '/platform/scheduling' || path.startsWith('/platform/scheduling/')
  }
  if (module === 'process') {
    return path === '/platform/process' || path.startsWith('/platform/process/')
  }
  if (module === 'dicts') {
    return path === '/platform/dicts' || path.startsWith('/platform/dicts/')
  }
  return (
    isPlatformRuntimePath(path, 'scheduling') ||
    isPlatformRuntimePath(path, 'process') ||
    isPlatformRuntimePath(path, 'dicts')
  )
}

export function buildPlatformSchedulingPath(
  currentPath: string,
  tenantRuntimePath: string,
): string {
  if (
    isPlatformRuntimePath(currentPath, 'scheduling') &&
    tenantRuntimePath.startsWith('/scheduling')
  ) {
    return `/platform${tenantRuntimePath}`
  }
  return tenantRuntimePath
}

export function buildPlatformSchedulingQuery(
  query: QueryLike | null | undefined,
): LocationQueryRaw {
  const nextQuery = withoutTenantRuntimeQuery(query)
  delete nextQuery.tab
  delete nextQuery.target
  return nextQuery
}

export function buildPlatformAuditRouteQuery(
  query: QueryLike | null | undefined,
): LocationQueryRaw {
  const nextQuery = withoutTenantRuntimeQuery(query)
  delete nextQuery.tab
  return nextQuery
}

export function buildPlatformRoleConstraintRouteQuery(
  query: QueryLike | null | undefined,
): LocationQueryRaw {
  const nextQuery = withoutTenantRuntimeQuery(query)
  delete nextQuery.tab
  return nextQuery
}
