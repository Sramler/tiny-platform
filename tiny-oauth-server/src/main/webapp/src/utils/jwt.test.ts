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
      activeTenantId: 2,
      authorities: ['ROLE_ADMIN', 'idempotent:ops:view'],
    })
    const stringToken = createToken({
      authorities: 'ROLE_ADMIN idempotent:ops:view',
    })

    expect(decodeJwtPayload<{ activeTenantId: number }>(arrayToken)).toEqual({
      activeTenantId: 2,
      authorities: ['ROLE_ADMIN', 'idempotent:ops:view'],
    })
    expect(extractAuthoritiesFromJwt(arrayToken)).toEqual(['ROLE_ADMIN', 'idempotent:ops:view'])
    expect(extractAuthoritiesFromJwt(stringToken)).toEqual(['ROLE_ADMIN', 'idempotent:ops:view'])
    expect(extractAuthoritiesFromJwt('bad-token')).toEqual([])
  })

  it('should prioritize permissions claim over authorities', () => {
    const permissionsToken = createToken({
      authorities: ['ROLE_ADMIN', 'idempotent:ops:view'],
      permissions: ['system:user:list', 'system:user:view'],
    })
    const permissionsStringToken = createToken({
      permissions: 'system:user:list system:user:view',
    })

    expect(extractAuthoritiesFromJwt(permissionsToken)).toEqual([
      'system:user:list',
      'system:user:view',
    ])
    expect(extractAuthoritiesFromJwt(permissionsStringToken)).toEqual(['system:user:list', 'system:user:view'])
  })
})
