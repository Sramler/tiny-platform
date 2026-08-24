import { computed, ref } from 'vue'
import type { ComputedRef, Ref } from 'vue'
import { logger } from '@/utils/logger'
import { sanitizeInternalRedirect } from '@/utils/redirect'
import { addTraceIdToFetchOptions, clearTraceId } from '@/utils/traceId'
import { clearActiveTenantId, getActiveTenantId, syncTenantContextFromClaims } from '@/utils/tenant'
import { dispatchAuthorizationRuntimeReset } from '@/runtime/authorizationRuntimeEvents'
import { ensureCsrfToken, isUnsafeHttpMethod } from '@/utils/csrf'

export type SessionPrincipal = Record<string, unknown> & {
  id?: string | number
  userId?: string | number
  username?: string
  nickname?: string
  activeTenantId?: string | number
  activeScopeType?: 'PLATFORM' | 'TENANT' | 'ORG' | 'DEPT'
  activeScopeId?: string | number | null
  permissionsVersion?: string | null
  permissions?: string[]
  authorities?: string[]
  roleCodes?: string[]
}

export interface AuthContext {
  user: Ref<SessionPrincipal | null>
  isAuthenticated: ComputedRef<boolean>
  login: (returnUrl?: string) => Promise<void>
  logout: () => Promise<void>
  refreshSessionPrincipal: () => Promise<SessionPrincipal | null>
  fetchWithAuth: (url: string, options?: RequestInit) => Promise<Response>
}

const user = ref<SessionPrincipal | null>(null)
const isAuthenticated = computed(() => user.value !== null)
let authInitializationPromise: Promise<void> | null = null

const normalizeApiBaseUrl = (): string => {
  const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || ''
  return apiBaseUrl.endsWith('/') ? apiBaseUrl.slice(0, -1) : apiBaseUrl
}

const appendTenantHeader = (headers: Headers): void => {
  const activeTenantId = getActiveTenantId()
  if (activeTenantId) headers.set('X-Active-Tenant-Id', activeTenantId)
}

export async function refreshSessionPrincipal(): Promise<SessionPrincipal | null> {
  const response = await fetch(`${normalizeApiBaseUrl()}/sys/users/current`, {
    method: 'GET',
    credentials: 'include',
    headers: { Accept: 'application/json' },
  })
  if (response.status === 401 || response.status === 403) {
    user.value = null
    clearActiveTenantId()
    return null
  }
  if (!response.ok) throw new Error(`session bootstrap failed with status ${response.status}`)
  const principal = (await response.json()) as SessionPrincipal
  user.value = principal
  syncTenantContextFromClaims(principal)
  return principal
}

export const login = async (returnUrl?: string): Promise<void> => {
  const redirect = sanitizeInternalRedirect(
    returnUrl || `${window.location.pathname}${window.location.search}`,
  )
  const query = new URLSearchParams()
  if (redirect && redirect !== '/login') query.set('redirect', redirect)
  window.location.assign(`/login${query.size ? `?${query.toString()}` : ''}`)
}

export const logout = async (): Promise<void> => {
  try {
    const csrf = await ensureCsrfToken(normalizeApiBaseUrl())
    const response = await fetch(
      `${normalizeApiBaseUrl()}/auth/logout`,
      addTraceIdToFetchOptions({
        method: 'POST',
        credentials: 'include',
        redirect: 'manual',
        headers: { Accept: 'application/json', [csrf.headerName]: csrf.token },
      }),
    )
    if (!response.ok && response.status !== 0) {
      throw new Error(`logout failed with status ${response.status}`)
    }
  } finally {
    user.value = null
    clearActiveTenantId()
    clearTraceId()
    dispatchAuthorizationRuntimeReset('logout', { message: '用户退出登录，清理授权运行态' })
  }
  window.location.assign('/login')
}

export async function initAuth(): Promise<void> {
  try {
    await refreshSessionPrincipal()
  } catch (error) {
    logger.error('[Session] 初始化认证状态失败', error)
    user.value = null
    clearActiveTenantId()
  }
}

export function ensureAuthInitialized(options: { force?: boolean } = {}): Promise<void> {
  if (!authInitializationPromise || options.force) authInitializationPromise = initAuth()
  return authInitializationPromise
}

export function useAuth(): AuthContext {
  const fetchWithAuth = async (url: string, options: RequestInit = {}) => {
    const controller = new AbortController()
    const timeout = setTimeout(() => controller.abort(), 8000)
    try {
      const headers = new Headers(options.headers)
      headers.delete('Authorization')
      if (isUnsafeHttpMethod(options.method)) {
        const csrf = await ensureCsrfToken(normalizeApiBaseUrl())
        headers.set(csrf.headerName, csrf.token)
      }
      appendTenantHeader(headers)
      return await fetch(url, {
        ...addTraceIdToFetchOptions({ ...options, headers }),
        credentials: 'include',
        signal: controller.signal,
      })
    } finally {
      clearTimeout(timeout)
    }
  }

  return { user, isAuthenticated, login, logout, refreshSessionPrincipal, fetchWithAuth }
}

export const initPromise = Promise.resolve()
