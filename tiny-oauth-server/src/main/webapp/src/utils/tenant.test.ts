import { beforeEach, describe, expect, it } from 'vitest'
import {
  clearTenantContext,
  getActiveTenantId,
  getLoginMode,
  getTenantCode,
  resolveActiveTenantQueryValue,
  setLoginMode,
  syncTenantContextFromClaims,
  withActiveTenantQuery,
} from '@/utils/tenant'

describe('tenant utils', () => {
  beforeEach(() => {
    window.localStorage.clear()
    clearTenantContext()
  })

  it('should sync tenant context from activeTenantId claims', () => {
    syncTenantContextFromClaims({
      activeTenantId: 8,
      iss: 'http://localhost:9000/issuer/tiny',
    })

    expect(getActiveTenantId()).toBe('8')
    expect(getTenantCode()).toBe('tiny')
  })

  it('should sync tenant context from Session principal using activeTenantId first', () => {
    syncTenantContextFromClaims({
      activeTenantId: 15,
      iss: 'http://localhost:9000/issuer/prod',
    })

    expect(getActiveTenantId()).toBe('15')
    expect(getTenantCode()).toBe('prod')
  })

  it('should replace conflicting stored tenant context with token tenant context', () => {
    syncTenantContextFromClaims({
      activeTenantId: 2,
      iss: 'http://localhost:9000/issuer/old',
    })

    syncTenantContextFromClaims({
      activeTenantId: 9,
      iss: 'http://localhost:9000/issuer/new',
    })

    expect(getActiveTenantId()).toBe('9')
    expect(getTenantCode()).toBe('new')
  })

  it('should treat platform issuer as platform scope and clear tenant code', () => {
    syncTenantContextFromClaims({
      activeScopeType: 'PLATFORM',
      iss: 'http://localhost:9000/platform',
    })

    expect(getLoginMode()).toBe('PLATFORM')
    expect(getActiveTenantId()).toBeNull()
    expect(getTenantCode()).toBeNull()
  })

  it('should resolve active tenant route query only from activeTenantId', () => {
    expect(resolveActiveTenantQueryValue({ activeTenantId: '9' })).toBe('9')
    expect(resolveActiveTenantQueryValue({ activeTenantId: '0' })).toBeNull()
    expect(resolveActiveTenantQueryValue({})).toBeNull()
  })

  it('should normalize route query to activeTenantId only', () => {
    expect(withActiveTenantQuery({ foo: 'bar' }, '9')).toEqual({
      foo: 'bar',
      activeTenantId: '9',
    })
    expect(withActiveTenantQuery({ foo: 'bar', activeTenantId: '9' }, null)).toEqual({
      foo: 'bar',
    })
  })
})
