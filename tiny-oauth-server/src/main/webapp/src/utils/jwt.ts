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

export function extractAuthoritiesFromJwt(token?: string | null): string[] {
  const claims = decodeJwtPayload<{ authorities?: unknown }>(token)
  const authorities = claims?.authorities

  if (Array.isArray(authorities)) {
    return authorities.filter((value): value is string => typeof value === 'string' && value.length > 0)
  }

  if (typeof authorities === 'string') {
    return authorities
      .split(/[\s,]+/)
      .map((value) => value.trim())
      .filter(Boolean)
  }

  return []
}
