import request from '@/utils/request'

export interface IdempotentMetricsSnapshot {
  windowMinutes: number
  windowStartEpochMillis: number
  windowEndEpochMillis: number
  passCount: number
  hitCount: number
  successCount: number
  failureCount: number
  storeErrorCount: number
  validationRejectCount: number
  rejectCount: number
  totalCheckCount: number
  conflictRate: number
  storageErrorRate: number
}

export interface IdempotentTopKey {
  key: string
  count: number
}

export interface IdempotentMqMetricsSnapshot {
  windowMinutes: number
  windowStartEpochMillis: number
  windowEndEpochMillis: number
  successCount: number
  failureCount: number
  duplicateRate: number
}

function buildTenantParams(tenantId?: number) {
  if (typeof tenantId === 'number' && tenantId > 0) {
    return { params: { tenantId } }
  }
  return undefined
}

export async function getIdempotentMetrics(tenantId?: number): Promise<IdempotentMetricsSnapshot> {
  const tenantParams = buildTenantParams(tenantId)
  const response = tenantParams
    ? await request.get<any>('/metrics/idempotent', tenantParams)
    : await request.get<any>('/metrics/idempotent')
  return {
    windowMinutes: Number(response?.windowMinutes ?? 60),
    windowStartEpochMillis: Number(response?.windowStartEpochMillis ?? 0),
    windowEndEpochMillis: Number(response?.windowEndEpochMillis ?? 0),
    passCount: Number(response?.passCount ?? 0),
    hitCount: Number(response?.hitCount ?? 0),
    successCount: Number(response?.successCount ?? 0),
    failureCount: Number(response?.failureCount ?? 0),
    storeErrorCount: Number(response?.storeErrorCount ?? 0),
    validationRejectCount: Number(response?.validationRejectCount ?? 0),
    rejectCount: Number(response?.rejectCount ?? 0),
    totalCheckCount: Number(response?.totalCheckCount ?? 0),
    conflictRate: Number(response?.conflictRate ?? 0),
    storageErrorRate: Number(response?.storageErrorRate ?? 0),
  }
}

export async function getIdempotentTopKeys(limit = 5, tenantId?: number): Promise<IdempotentTopKey[]> {
  const query = buildTenantParams(tenantId)
  const response = await request.get<any>('/metrics/idempotent/top-keys', {
    params: {
      limit,
      ...(query?.params ?? {}),
    },
  })
  const topKeys = Array.isArray(response?.topKeys) ? response.topKeys : []
  return topKeys.map((item: any) => ({
    key: String(item?.key ?? ''),
    count: Number(item?.count ?? 0),
  }))
}

export async function getIdempotentMqMetrics(tenantId?: number): Promise<IdempotentMqMetricsSnapshot> {
  const tenantParams = buildTenantParams(tenantId)
  const response = tenantParams
    ? await request.get<any>('/metrics/idempotent/mq', tenantParams)
    : await request.get<any>('/metrics/idempotent/mq')
  return {
    windowMinutes: Number(response?.windowMinutes ?? 60),
    windowStartEpochMillis: Number(response?.windowStartEpochMillis ?? 0),
    windowEndEpochMillis: Number(response?.windowEndEpochMillis ?? 0),
    successCount: Number(response?.successCount ?? 0),
    failureCount: Number(response?.failureCount ?? 0),
    duplicateRate: Number(response?.duplicateRate ?? 0),
  }
}
