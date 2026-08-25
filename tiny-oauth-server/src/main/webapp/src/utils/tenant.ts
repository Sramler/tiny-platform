const ACTIVE_TENANT_ID_STORAGE_KEY = 'app_active_tenant_id'
const LOGIN_MODE_STORAGE_KEY = 'app_login_mode'
const TENANT_CODE_STORAGE_KEY = 'app_tenant_code'
const TENANT_CODE_PATTERN = /^[a-z0-9][a-z0-9-]{1,31}$/
const PLATFORM_ISSUER_SEGMENT = 'platform'

type TenantClaims = {
  activeTenantId?: unknown
  activeScopeType?: unknown
  iss?: unknown
}

type TenantQueryLike = {
  activeTenantId?: unknown
  [key: string]: unknown
}

export type LoginMode = 'TENANT' | 'PLATFORM'

type ActiveScopeType = 'PLATFORM' | 'TENANT' | 'ORG' | 'DEPT'

function getStorageValue(key: string): string | null {
  if (typeof window === 'undefined') return null
  try {
    const value = window.localStorage.getItem(key)
    return value && value.trim() ? value.trim() : null
  } catch {
    return null
  }
}

function setStorageValue(key: string, value: string | null): void {
  if (typeof window === 'undefined') return
  try {
    if (!value) {
      window.localStorage.removeItem(key)
    } else {
      window.localStorage.setItem(key, value)
    }
  } catch {
    // ignore storage errors
  }
}

function normalizeTenantId(value: unknown): string | null {
  if (typeof value === 'number' && Number.isFinite(value)) {
    const numeric = Math.trunc(value)
    return numeric > 0 ? String(numeric) : null
  }
  if (typeof value === 'string') {
    const normalized = value.trim()
    if (!/^\d+$/.test(normalized)) return null
    return Number(normalized) > 0 ? normalized : null
  }
  return null
}

function normalizeLoginMode(value: unknown): LoginMode | null {
  return value === 'PLATFORM' || value === 'TENANT' ? value : null
}

function normalizeActiveScopeType(value: unknown): ActiveScopeType | null {
  return value === 'PLATFORM' || value === 'TENANT' || value === 'ORG' || value === 'DEPT'
    ? value
    : null
}

export function resolveActiveTenantQueryValue(query: TenantQueryLike | null | undefined): string | null {
  return normalizeTenantId(query?.activeTenantId)
}

export function withActiveTenantQuery<T extends TenantQueryLike>(query: T, activeTenantId: string | number | null | undefined): T {
  const normalizedTenantId = normalizeTenantId(activeTenantId)
  const nextQuery = { ...query } as Record<string, unknown>
  delete nextQuery.tenantId

  if (!normalizedTenantId) {
    delete nextQuery.activeTenantId
    return nextQuery as T
  }

  nextQuery.activeTenantId = normalizedTenantId
  return nextQuery as T
}

function extractIssuerContext(issuer: unknown): { loginMode: LoginMode | null; tenantCode: string | null } {
  if (typeof issuer !== 'string' || !issuer.trim()) {
    return { loginMode: null, tenantCode: null }
  }
  try {
    const url = new URL(issuer)
    const segments = url.pathname
      .split('/')
      .filter(Boolean)
      .map((segment) => segment.trim().toLowerCase())
      .filter(Boolean)
    if (segments.includes(PLATFORM_ISSUER_SEGMENT)) {
      return { loginMode: 'PLATFORM', tenantCode: null }
    }
    const candidate = segments.length > 0 ? segments[segments.length - 1] : null
    const tenantCode = normalizeTenantCode(candidate)
    return {
      loginMode: tenantCode ? 'TENANT' : null,
      tenantCode,
    }
  } catch {
    return { loginMode: null, tenantCode: null }
  }
}

export function normalizeTenantCode(value: string | null | undefined): string | null {
  if (!value) return null
  const normalized = value.trim().toLowerCase()
  if (!normalized) return null
  return TENANT_CODE_PATTERN.test(normalized) ? normalized : null
}

export function isValidTenantCode(value: string | null | undefined): boolean {
  return normalizeTenantCode(value) !== null
}

export function getLoginMode(): LoginMode | null {
  const value = getStorageValue(LOGIN_MODE_STORAGE_KEY)
  if (!value) return null
  const normalized = normalizeLoginMode(value)
  if (!normalized) {
    setStorageValue(LOGIN_MODE_STORAGE_KEY, null)
    return null
  }
  return normalized
}

export function setLoginMode(value: LoginMode): void {
  setStorageValue(LOGIN_MODE_STORAGE_KEY, value)
}

export function getTenantCode(): string | null {
  const value = getStorageValue(TENANT_CODE_STORAGE_KEY)
  if (!value) return null
  const normalized = normalizeTenantCode(value)
  if (!normalized) {
    setStorageValue(TENANT_CODE_STORAGE_KEY, null)
    return null
  }
  return normalized
}

export function setTenantCode(value: string): void {
  const normalized = normalizeTenantCode(value)
  if (!normalized) return
  setStorageValue(TENANT_CODE_STORAGE_KEY, normalized)
}

export function clearTenantCode(): void {
  setStorageValue(TENANT_CODE_STORAGE_KEY, null)
}

export function getActiveTenantId(): string | null {
  const storedActiveTenantId = getStorageValue(ACTIVE_TENANT_ID_STORAGE_KEY)
  const normalized = normalizeTenantId(storedActiveTenantId)
  if (!normalized) {
    clearActiveTenantId()
    return null
  }
  return normalized
}

export function setActiveTenantId(value: string | number): void {
  const normalized = normalizeTenantId(value)
  if (!normalized) return
  setStorageValue(ACTIVE_TENANT_ID_STORAGE_KEY, normalized)
}

export function clearActiveTenantId(): void {
  setStorageValue(ACTIVE_TENANT_ID_STORAGE_KEY, null)
}

export function clearTenantContext(): void {
  clearActiveTenantId()
  clearTenantCode()
}

export function syncTenantContextFromClaims(claims: TenantClaims | null | undefined): void {
  const tokenActiveTenantId = normalizeTenantId(claims?.activeTenantId)
  const activeScopeType = normalizeActiveScopeType(claims?.activeScopeType)
  const issuerContext = extractIssuerContext(claims?.iss)
  const tenantCodeFromIssuer = issuerContext.tenantCode
  const platformScope = activeScopeType === 'PLATFORM' || issuerContext.loginMode === 'PLATFORM'
  const localActiveTenantId = getActiveTenantId()
  if (localActiveTenantId && localActiveTenantId !== tokenActiveTenantId) {
    // 本地租户与当前 Session principal 冲突时，清理历史上下文后按服务端快照回填。
    clearTenantContext()
  }

  if (platformScope) {
    setLoginMode('PLATFORM')
    clearTenantCode()
    clearActiveTenantId()
    return
  }

  if (tenantCodeFromIssuer) {
    setLoginMode('TENANT')
    setTenantCode(tenantCodeFromIssuer)
  }
  if (!tenantCodeFromIssuer && tokenActiveTenantId) {
    setLoginMode('TENANT')
  }
  if (!tokenActiveTenantId) {
    clearActiveTenantId()
    return
  }

  setActiveTenantId(tokenActiveTenantId)
}
