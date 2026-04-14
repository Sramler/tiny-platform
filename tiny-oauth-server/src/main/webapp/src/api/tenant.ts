import request from '@/utils/request'

export type Tenant = {
  id: number
  code: string
  name: string
  domain?: string
  enabled?: boolean
  lifecycleStatus?: string
  planCode?: string
  expiresAt?: string
  maxUsers?: number
  maxStorageGb?: number
  contactName?: string
  contactEmail?: string
  contactPhone?: string
  remark?: string
  createdAt?: string
  updatedAt?: string
}

export type TenantListParams = {
  code?: string
  name?: string
  domain?: string
  enabled?: boolean
  lifecycleStatus?: string
  includeDeleted?: boolean
  page?: number
  size?: number
}

type PageResponse<T> = {
  content?: T[]
  totalElements?: number
}

export type PlatformTemplateInitializationResult = {
  initialized: boolean
  message: string
}

export type TenantPermissionSummary = {
  tenantId: number
  totalRoles: number
  enabledRoles: number
  totalPermissions: number
  assignedPermissions: number
  totalCarriers: number
  boundCarriers: number
  menuCarriers: number
  uiActionCarriers: number
  apiEndpointCarriers: number
}

export type PlatformTemplateDiffType = 'MISSING_IN_TENANT' | 'EXTRA_IN_TENANT' | 'CHANGED'

export type PlatformTemplateFieldDiff = {
  platformValue: string
  tenantValue: string
}

export type PlatformTemplateEntryDiff = {
  carrierType: string
  key: string
  platformCarrierId?: number
  tenantCarrierId?: number
  diffType: PlatformTemplateDiffType
  fieldDiffs: Record<string, PlatformTemplateFieldDiff>
}

export type PlatformTemplateDiffResult = {
  tenantId: number
  summary: {
    totalPlatformEntries: number
    totalTenantEntries: number
    missingInTenant: number
    extraInTenant: number
    changed: number
  }
  diffs: PlatformTemplateEntryDiff[]
}

export function tenantList(params: TenantListParams) {
  return request.get<PageResponse<Tenant>>('/sys/tenants', { params })
}

export function getTenantById(id: string | number) {
  return request.get<Tenant>(`/sys/tenants/${id}`)
}

export function getTenantPermissionSummary(id: string | number) {
  return request.get<TenantPermissionSummary>(`/sys/tenants/${id}/permission-summary`)
}

export function diffTenantPlatformTemplate(id: string | number) {
  return request.get<PlatformTemplateDiffResult>(`/sys/tenants/${id}/platform-template/diff`)
}

export function createTenant(data: Partial<Tenant> & Record<string, unknown>) {
  return request.post('/sys/tenants', data, {
    idempotency: {
      scope: 'sys-tenants:create',
      payload: data,
    },
  })
}

export function updateTenant(id: string | number, data: Partial<Tenant> & Record<string, unknown>) {
  return request.put(`/sys/tenants/${id}`, data, {
    idempotency: {
      scope: `sys-tenants:update:${id}`,
      payload: data,
    },
  })
}

export function initializePlatformTemplate() {
  return request.post<PlatformTemplateInitializationResult>('/sys/tenants/platform-template/initialize', null, {
    idempotency: {
      scope: 'sys-tenants:platform-template:initialize',
      payload: {},
    },
  })
}

export function freezeTenant(id: string | number) {
  return request.post(`/sys/tenants/${id}/freeze`, null, {
    idempotency: {
      scope: `sys-tenants:freeze:${id}`,
      payload: { id },
    },
  })
}

export function unfreezeTenant(id: string | number) {
  return request.post(`/sys/tenants/${id}/unfreeze`, null, {
    idempotency: {
      scope: `sys-tenants:unfreeze:${id}`,
      payload: { id },
    },
  })
}

export function decommissionTenant(id: string | number) {
  return request.post(`/sys/tenants/${id}/decommission`, null, {
    idempotency: {
      scope: `sys-tenants:decommission:${id}`,
      payload: { id },
    },
  })
}

export function deleteTenant(id: string | number) {
  return request.delete(`/sys/tenants/${id}`, {
    idempotency: {
      scope: `sys-tenants:delete:${id}`,
      payload: { id },
    },
  })
}
