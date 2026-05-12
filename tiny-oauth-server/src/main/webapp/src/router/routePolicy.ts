import type { RouteLocationNormalizedLoaded, RouteLocationRaw } from 'vue-router'
import { sanitizeInternalRedirect } from '@/utils/redirect'

const BACKEND_ONLY_PREFIXES = [
  '/oauth2/',
  '/login',
  '/logout',
  '/error',
  '/actuator',
  '/self/security/status',
]

const BACKEND_ONLY_TENANT_OAUTH_PATTERN = /^\/[a-z0-9][a-z0-9-]{1,31}\/oauth2\//i

export function normalizeRedirectTarget(candidate: unknown, fallback = '/'): string {
  const raw = Array.isArray(candidate) ? candidate[0] : candidate
  return sanitizeInternalRedirect(typeof raw === 'string' ? raw : undefined, fallback)
}

export function normalizeBootstrapRedirect(candidate: unknown, fallback = '/'): string {
  const redirect = normalizeRedirectTarget(candidate, fallback)
  return isBootstrapPath(redirect) ? fallback : redirect
}

export function isBackendOnlyRedirect(path: string): boolean {
  if (!path.startsWith('/')) {
    return false
  }
  return (
    BACKEND_ONLY_PREFIXES.some((prefix) => path === prefix || path.startsWith(prefix)) ||
    BACKEND_ONLY_TENANT_OAUTH_PATTERN.test(path)
  )
}

export function buildBackendRedirectUrl(path: string): string {
  const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:9000'
  const base = apiBaseUrl.endsWith('/') ? apiBaseUrl.slice(0, -1) : apiBaseUrl
  return `${base}${path}`
}

export function isBootstrapPath(path: string): boolean {
  return path === '/bootstrap'
}

export function isExceptionPath(path: string): boolean {
  return path.startsWith('/exception/')
}

export function isOidcCallbackPath(path: string): boolean {
  return path === '/callback' || path === '/oidc/callback'
}

export function isSecurityPath(path: string): boolean {
  return path === '/self/security/totp-bind' || path === '/self/security/totp-verify'
}

export function isLoginPath(path: string): boolean {
  return path === '/login'
}

export function isBootstrapBypassPath(path: string): boolean {
  return (
    isBootstrapPath(path) ||
    isOidcCallbackPath(path) ||
    isLoginPath(path) ||
    isSecurityPath(path) ||
    isExceptionPath(path)
  )
}

export function buildBootstrapRoute(redirect: string): RouteLocationRaw {
  return {
    path: '/bootstrap',
    query: {
      redirect: normalizeBootstrapRedirect(redirect),
    },
    replace: true,
  }
}

export function buildLoginRoute(redirect: string): RouteLocationRaw {
  return {
    path: '/login',
    query: {
      redirect: normalizeBootstrapRedirect(redirect),
    },
    replace: true,
  }
}

export function sameFullPath(route: RouteLocationNormalizedLoaded, target: string): boolean {
  return route.fullPath === target
}
