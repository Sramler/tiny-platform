import type { SessionPrincipal } from './auth'

function strings(value: unknown): string[] {
  if (Array.isArray(value)) {
    return value.filter((item): item is string => typeof item === 'string' && item.length > 0)
  }
  if (typeof value === 'string') {
    return value.split(/[\s,]+/).map((item) => item.trim()).filter(Boolean)
  }
  return []
}

export function runtimeAuthorities(principal?: SessionPrincipal | null): string[] {
  const permissions = strings(principal?.permissions)
  return permissions.length > 0 ? permissions : strings(principal?.authorities)
}

export function runtimeUserId(principal?: SessionPrincipal | null): number | null {
  const raw = principal?.userId ?? principal?.id
  if (typeof raw === 'number' && Number.isFinite(raw) && raw > 0) return raw
  if (typeof raw === 'string' && /^\d+$/.test(raw)) {
    const value = Number(raw)
    return value > 0 ? value : null
  }
  return null
}

export function isPlatformPrincipal(principal?: SessionPrincipal | null): boolean {
  return principal?.activeScopeType === 'PLATFORM'
}
