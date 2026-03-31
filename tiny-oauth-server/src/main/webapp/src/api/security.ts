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

export interface UserSessionRecord {
  sessionId: string
  current: boolean
  authenticationProvider?: string | null
  authenticationFactor?: string | null
  ipAddress?: string | null
  userAgent?: string | null
  createdAt?: string | null
  lastSeenAt?: string | null
  expiresAt?: string | null
}

export interface SecuritySessionListResponse {
  success?: boolean
  activeTenantId?: number
  currentSessionId?: string | null
  content: UserSessionRecord[]
  [key: string]: unknown
}

async function requestSecurityJson<T>(path: string, options: RequestInit): Promise<T> {
  const response = await fetchWithTraceId(`${getApiBaseUrl()}${path}`, {
    credentials: 'include',
    headers: { Accept: 'application/json' },
    ...options,
  })
  if (!response.ok) {
    let errorMessage = '请求失败'
    try {
      const data = await response.json() as { error?: string }
      if (data?.error) {
        errorMessage = data.error
      }
    } catch {
      // ignore
    }
    throw new Error(errorMessage)
  }
  const data = await response.json() as T
  syncTenantContextFromClaims(data as Record<string, unknown>)
  return data
}

export async function getSecurityStatus(): Promise<SecurityStatusResponse> {
  return requestSecurityJson<SecurityStatusResponse>('/self/security/status', {
    method: 'GET',
  })
}

export async function getSecuritySessions(): Promise<SecuritySessionListResponse> {
  return requestSecurityJson<SecuritySessionListResponse>('/self/security/sessions', {
    method: 'GET',
  })
}

export async function revokeSecuritySession(sessionId: string): Promise<{ success?: boolean; message?: string }> {
  return requestSecurityJson<{ success?: boolean; message?: string }>(
    `/self/security/sessions/${encodeURIComponent(sessionId)}`,
    {
      method: 'DELETE',
    },
  )
}

export async function revokeOtherSecuritySessions(): Promise<{ success?: boolean; message?: string; revokedCount?: number }> {
  return requestSecurityJson<{ success?: boolean; message?: string; revokedCount?: number }>(
    '/self/security/sessions/revoke-others',
    {
      method: 'POST',
    },
  )
}
