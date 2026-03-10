import { getTenantId } from '@/utils/tenant'

function normalizeValue(value: unknown): unknown {
  if (value === null || value === undefined) {
    return null
  }

  if (value instanceof Date) {
    return value.toISOString()
  }

  if (Array.isArray(value)) {
    return value.map((item) => normalizeValue(item))
  }

  if (typeof value === 'object') {
    const entries = Object.entries(value as Record<string, unknown>)
      .filter(([, entryValue]) => entryValue !== undefined)
      .sort(([left], [right]) => left.localeCompare(right))
      .map(([key, entryValue]) => [key, normalizeValue(entryValue)])

    return Object.fromEntries(entries)
  }

  return value
}

function stableStringify(value: unknown): string {
  return JSON.stringify(normalizeValue(value)) ?? 'null'
}

function fnv1a64(input: string): string {
  let hash = 0xcbf29ce484222325n
  const prime = 0x100000001b3n
  const mask = 0xffffffffffffffffn

  for (const char of input) {
    hash ^= BigInt(char.codePointAt(0) ?? 0)
    hash = (hash * prime) & mask
  }

  return hash.toString(16).padStart(16, '0')
}

export function createIdempotencyKey(scope: string, payload?: unknown): string {
  const tenantId = getTenantId() ?? 'anonymous'
  return fnv1a64(`${tenantId}|${scope}|${stableStringify(payload)}`)
}

export function createIdempotencyFingerprint(value: unknown): string {
  return stableStringify(value)
}

export function createSubmitIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID().replace(/-/g, '')
  }

  return `${Date.now().toString(16)}${Math.random().toString(16).slice(2)}`.slice(0, 32).padEnd(32, '0')
}

export function createIdempotencyHeaders(scope: string, payload?: unknown): Record<string, string> {
  return {
    'X-Idempotency-Key': createIdempotencyKey(scope, payload),
  }
}
