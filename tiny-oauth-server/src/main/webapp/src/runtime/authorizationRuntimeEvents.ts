export type AuthorizationRuntimeResetReason =
  | 'logout'
  | 'user_unloaded'
  | 'user_signed_out'
  | 'missing_tenant'
  | 'stale_permissions'
  | 'renew_failed'
  | 'unauthorized'
  | 'manual'

export interface AuthorizationRuntimeResetEventDetail {
  reason: AuthorizationRuntimeResetReason
  message?: string
  traceId?: string | null
  createdAt: number
  eventId: string
}

export const AUTHORIZATION_RUNTIME_RESET_EVENT = 'tiny-platform:authorization-runtime-reset'
export const AUTHORIZATION_RUNTIME_RESET_STORAGE_KEY =
  'tiny-platform:authorization-runtime-reset:broadcast'

function createEventId(): string {
  try {
    if (window.crypto?.randomUUID) {
      return window.crypto.randomUUID()
    }
  } catch {
    // fall through
  }
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`
}

export function dispatchAuthorizationRuntimeReset(
  reason: AuthorizationRuntimeResetReason,
  options: {
    message?: string
    traceId?: string | null
    broadcast?: boolean
  } = {},
): void {
  if (typeof window === 'undefined') {
    return
  }

  const detail: AuthorizationRuntimeResetEventDetail = {
    reason,
    message: options.message,
    traceId: options.traceId,
    createdAt: Date.now(),
    eventId: createEventId(),
  }

  window.dispatchEvent(new CustomEvent(AUTHORIZATION_RUNTIME_RESET_EVENT, { detail }))

  if (options.broadcast === false) {
    return
  }
  try {
    window.localStorage.setItem(AUTHORIZATION_RUNTIME_RESET_STORAGE_KEY, JSON.stringify(detail))
  } catch {
    // localStorage may be disabled; same-tab custom event has already been dispatched.
  }
}

export function parseAuthorizationRuntimeResetEvent(
  raw: string | null,
): AuthorizationRuntimeResetEventDetail | null {
  if (!raw) {
    return null
  }
  try {
    const parsed = JSON.parse(raw) as Partial<AuthorizationRuntimeResetEventDetail>
    if (!parsed || typeof parsed.reason !== 'string' || typeof parsed.eventId !== 'string') {
      return null
    }
    return {
      reason: parsed.reason as AuthorizationRuntimeResetReason,
      message: typeof parsed.message === 'string' ? parsed.message : undefined,
      traceId: typeof parsed.traceId === 'string' ? parsed.traceId : null,
      createdAt: typeof parsed.createdAt === 'number' ? parsed.createdAt : Date.now(),
      eventId: parsed.eventId,
    }
  } catch {
    return null
  }
}
