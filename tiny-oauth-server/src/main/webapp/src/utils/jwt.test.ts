import { describe, expect, it } from 'vitest'
import { decodeJwtPayload, extractAuthoritiesFromJwt } from '@/utils/jwt'

function createToken(payload: Record<string, unknown>) {
  const header = Buffer.from(JSON.stringify({ alg: 'none', typ: 'JWT' })).toString('base64url')
  const encodedPayload = Buffer.from(JSON.stringify(payload)).toString('base64url')
  return `${header}.${encodedPayload}.signature`
}

describe('jwt utils', () => {
  it('should decode payload and extract authorities from arrays or strings', () => {
    const arrayToken = createToken({
      tenantId: 1,
      authorities: ['ROLE_ADMIN', 'idempotentOps'],
    })
    const stringToken = createToken({
      authorities: 'ROLE_ADMIN idempotentOps',
    })

    expect(decodeJwtPayload<{ tenantId: number }>(arrayToken)).toEqual({
      tenantId: 1,
      authorities: ['ROLE_ADMIN', 'idempotentOps'],
    })
    expect(extractAuthoritiesFromJwt(arrayToken)).toEqual(['ROLE_ADMIN', 'idempotentOps'])
    expect(extractAuthoritiesFromJwt(stringToken)).toEqual(['ROLE_ADMIN', 'idempotentOps'])
    expect(extractAuthoritiesFromJwt('bad-token')).toEqual([])
  })
})
