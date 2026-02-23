const TENANT_ID_STORAGE_KEY = 'app_tenant_id'
const TENANT_CODE_STORAGE_KEY = 'app_tenant_code'
const TENANT_CODE_PATTERN = /^[a-z0-9][a-z0-9-]{1,31}$/

type TenantClaims = {
  tenantId?: unknown
  iss?: unknown
}

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

function extractTenantCodeFromIssuer(issuer: unknown): string | null {
  if (typeof issuer !== 'string' || !issuer.trim()) {
    return null
  }
  try {
    const url = new URL(issuer)
    const segments = url.pathname.split('/').filter(Boolean)
    const candidate = segments.length > 0 ? segments[segments.length - 1] : null
    return normalizeTenantCode(candidate)
  } catch {
    return null
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

export function getTenantId(): string | null {
  const tenantId = getStorageValue(TENANT_ID_STORAGE_KEY)
  const normalized = normalizeTenantId(tenantId)
  if (!normalized) {
    clearTenantId()
    return null
  }
  return normalized
}

export function setTenantId(value: string | number): void {
  const normalized = normalizeTenantId(value)
  if (!normalized) return
  setStorageValue(TENANT_ID_STORAGE_KEY, normalized)
}

export function clearTenantId(): void {
  setStorageValue(TENANT_ID_STORAGE_KEY, null)
}

export function clearTenantContext(): void {
  clearTenantId()
  clearTenantCode()
}

function parseBase64UrlSegment(segment: string): string | null {
  try {
    const normalized = segment.replace(/-/g, '+').replace(/_/g, '/')
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=')
    if (typeof window === 'undefined' || typeof window.atob !== 'function') {
      return null
    }
    return window.atob(padded)
  } catch {
    return null
  }
}

function extractTenantClaimsFromAccessToken(token: string | null | undefined): TenantClaims | null {
  if (!token) return null
  const segments = token.split('.')
  const payloadSegment = segments.length > 1 ? segments[1] : undefined
  if (typeof payloadSegment !== 'string' || !payloadSegment) return null
  const payloadJson = parseBase64UrlSegment(payloadSegment)
  if (!payloadJson) return null

  try {
    return JSON.parse(payloadJson) as TenantClaims
  } catch {
    return null
  }
}

export function syncTenantContextFromAccessToken(token: string | null | undefined): void {
  const claims = extractTenantClaimsFromAccessToken(token)
  if (!claims) return
  syncTenantContextFromClaims(claims)
}

export function syncTenantContextFromClaims(claims: TenantClaims | null | undefined): void {
  const tokenTenantId = normalizeTenantId(claims?.tenantId)
  const tenantCodeFromIssuer = extractTenantCodeFromIssuer(claims?.iss)
  if (tenantCodeFromIssuer) {
    setTenantCode(tenantCodeFromIssuer)
  }
  if (!tokenTenantId) {
    clearTenantId()
    return
  }

  const localTenantId = getTenantId()
  if (localTenantId && localTenantId !== tokenTenantId) {
    // 本地租户与 token 租户冲突时，清理历史上下文后按 token 回填。
    clearTenantContext()
  }

  setTenantId(tokenTenantId)
}
