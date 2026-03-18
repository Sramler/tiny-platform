import { fetchWithTraceId } from '@/utils/traceId'
import { syncTenantContextFromClaims } from '@/utils/tenant'

function getApiBaseUrl(): string {
  const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:9000'
  return apiBaseUrl.endsWith('/') ? apiBaseUrl.slice(0, -1) : apiBaseUrl
}

export interface SecurityStatusResponse {
  success?: boolean
  /**
   * 当前活动租户主字段。
   * 安全中心相关页面应优先消费该字段。
   */
  activeTenantId?: number
  disableMfa?: boolean
  forceMfa?: boolean
  totpBound?: boolean
  totpActivated?: boolean
  [key: string]: unknown
}

export async function getSecurityStatus(): Promise<SecurityStatusResponse> {
  const response = await fetchWithTraceId(`${getApiBaseUrl()}/self/security/status`, {
    method: 'GET',
    credentials: 'include',
    headers: { Accept: 'application/json' },
  })
  if (!response.ok) {
    throw new Error('无法获取安全状态')
  }
  const data = await response.json() as SecurityStatusResponse
  syncTenantContextFromClaims(data)
  return data
}
