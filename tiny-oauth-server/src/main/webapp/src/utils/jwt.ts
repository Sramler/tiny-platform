export type JwtClaims = Record<string, unknown>

// Session/BFF 模式下，浏览器只保存由 `/sys/users/current` 返回的内存身份快照，
// 不再为了复用 JWT helper 而伪造三段式 access token。
let sessionClaimsSnapshot: JwtClaims | null = null

export function setSessionClaimsSnapshot(claims: JwtClaims | null): void {
  sessionClaimsSnapshot = claims ? { ...claims } : null
}

function decodeBase64Url(value: string): string {
  const normalized = value.replace(/-/g, '+').replace(/_/g, '/')
  const padding = normalized.length % 4 === 0 ? '' : '='.repeat(4 - (normalized.length % 4))
  return globalThis.atob(`${normalized}${padding}`)
}

export function decodeJwtPayload<T extends JwtClaims = JwtClaims>(token?: string | null): T | null {
  if (!token) {
    return sessionClaimsSnapshot as T | null
  }

  const parts = token.split('.')
  if (parts.length !== 3 || !parts[1]) {
    return null
  }

  try {
    return JSON.parse(decodeBase64Url(parts[1])) as T
  } catch {
    return null
  }
}

function extractStringList(value: unknown): string[] {
  if (Array.isArray(value)) {
    return value.filter((item): item is string => typeof item === 'string' && item.length > 0)
  }

  if (typeof value === 'string') {
    return value
      .split(/[\s,]+/)
      .map((item) => item.trim())
      .filter(Boolean)
  }

  return []
}

/**
 * 从 access token payload 读取数据库用户主键（与 JwtTokenCustomizer 的 userId claim 对齐）。
 */
export function extractUserIdFromJwt(token?: string | null): number | null {
  const claims = decodeJwtPayload<{ userId?: unknown; id?: unknown }>(token)
  const raw = claims?.userId ?? claims?.id
  if (typeof raw === 'number' && Number.isFinite(raw) && raw > 0) {
    return raw
  }
  if (typeof raw === 'string' && /^\d+$/.test(raw)) {
    const n = Number(raw)
    return n > 0 ? n : null
  }
  return null
}

export function extractAuthoritiesFromJwt(token?: string | null): string[] {
  const claims = decodeJwtPayload<{ permissions?: unknown; authorities?: unknown }>(token)
  const permissions = extractStringList(claims?.permissions)
  if (permissions.length > 0) {
    return permissions
  }
  return extractStringList(claims?.authorities)
}
