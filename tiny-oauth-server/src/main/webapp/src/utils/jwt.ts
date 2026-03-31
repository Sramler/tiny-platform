export type JwtClaims = Record<string, unknown>

function decodeBase64Url(value: string): string {
  const normalized = value.replace(/-/g, '+').replace(/_/g, '/')
  const padding = normalized.length % 4 === 0 ? '' : '='.repeat(4 - (normalized.length % 4))
  return globalThis.atob(`${normalized}${padding}`)
}

export function decodeJwtPayload<T extends JwtClaims = JwtClaims>(token?: string | null): T | null {
  if (!token) {
    return null
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

export function extractAuthoritiesFromJwt(token?: string | null): string[] {
  const claims = decodeJwtPayload<{ permissions?: unknown; authorities?: unknown }>(token)
  const permissions = extractStringList(claims?.permissions)
  if (permissions.length > 0) {
    return permissions
  }
  return extractStringList(claims?.authorities)
}
