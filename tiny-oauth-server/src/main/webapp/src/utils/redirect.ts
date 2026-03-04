const DEFAULT_REDIRECT_PATH = '/'

const hasUnsafeChars = (value: string): boolean => /[\u0000-\u001f\u007f\\]/.test(value)

const isSafeRelativePath = (value: string): boolean =>
  value.startsWith('/') && !value.startsWith('//') && !hasUnsafeChars(value)

export function sanitizeInternalRedirect(
  candidate: string | null | undefined,
  fallback = DEFAULT_REDIRECT_PATH,
): string {
  const safeFallback = isSafeRelativePath(fallback) ? fallback : DEFAULT_REDIRECT_PATH
  if (!candidate) {
    return safeFallback
  }

  const trimmed = String(candidate).trim()
  if (!trimmed) {
    return safeFallback
  }

  const lower = trimmed.toLowerCase()
  if (lower.startsWith('javascript:') || lower.startsWith('data:') || hasUnsafeChars(trimmed)) {
    return safeFallback
  }

  if (isSafeRelativePath(trimmed)) {
    return trimmed
  }

  try {
    const baseOrigin = typeof window === 'undefined' ? 'http://localhost' : window.location.origin
    const parsed = new URL(trimmed, baseOrigin)
    if (parsed.origin !== baseOrigin) {
      return safeFallback
    }
    const relative = `${parsed.pathname}${parsed.search}${parsed.hash}`
    return isSafeRelativePath(relative) ? relative : safeFallback
  } catch {
    return safeFallback
  }
}
