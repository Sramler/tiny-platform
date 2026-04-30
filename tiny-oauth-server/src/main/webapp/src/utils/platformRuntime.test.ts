import { describe, expect, it } from 'vitest'
import {
  buildPlatformAuditRouteQuery,
  buildPlatformAuditTabPath,
  buildPlatformDictRouteQuery,
  buildPlatformDictTabPath,
  buildPlatformProcessRouteQuery,
  buildPlatformProcessTabPath,
  buildPlatformRoleConstraintRouteQuery,
  buildPlatformRoleConstraintTabPath,
  buildPlatformSchedulingTabPath,
  buildPlatformSchedulingPath,
  buildPlatformSchedulingQuery,
  buildPlatformUserRouteQuery,
  buildPlatformUserTabPath,
  isPlatformRuntimePath,
  resolvePlatformAuditTabFromPath,
  resolvePlatformDictTabFromPath,
  resolvePlatformProcessTabFromPath,
  resolvePlatformRoleConstraintTabFromPath,
  resolvePlatformSchedulingTabFromPath,
  resolvePlatformUserTabFromPath,
} from '@/utils/platformRuntime'

describe('platformRuntime', () => {
  it('should detect platform runtime paths by module', () => {
    expect(isPlatformRuntimePath('/platform/process', 'process')).toBe(true)
    expect(isPlatformRuntimePath('/platform/process/modeling', 'process')).toBe(true)
    expect(isPlatformRuntimePath('/platform/scheduling/dag/detail', 'scheduling')).toBe(true)
    expect(isPlatformRuntimePath('/platform/dicts', 'dicts')).toBe(true)
    expect(isPlatformRuntimePath('/platform/dicts/item', 'dicts')).toBe(true)
    expect(isPlatformRuntimePath('/process/definition', 'process')).toBe(false)
  })

  it('should remove tenant query state from platform scheduling navigation', () => {
    expect(
      buildPlatformSchedulingQuery({
        tab: 'dag',
        target: '/scheduling/dag',
        activeTenantId: '9',
        targetTenantId: '12',
        keyword: 'daily',
      }),
    ).toEqual({ keyword: 'daily' })
  })

  it('should build and resolve platform scheduling tab child routes', () => {
    expect(buildPlatformSchedulingTabPath('taskType')).toBe('/platform/scheduling/task-type')
    expect(buildPlatformSchedulingTabPath('unknown')).toBe('/platform/scheduling/dag')
    expect(resolvePlatformSchedulingTabFromPath('/platform/scheduling/task-type')).toBe('taskType')
    expect(resolvePlatformSchedulingTabFromPath('/platform/scheduling/audit')).toBe('audit')
    expect(resolvePlatformSchedulingTabFromPath('/platform/scheduling/unknown')).toBeNull()
  })

  it('should build and resolve platform audit tab child routes', () => {
    expect(buildPlatformAuditTabPath('authorization')).toBe('/platform/audit/authorization')
    expect(buildPlatformAuditTabPath('unknown')).toBe('/platform/audit/authentication')
    expect(resolvePlatformAuditTabFromPath('/platform/audit/authentication')).toBe('authentication')
    expect(resolvePlatformAuditTabFromPath('/platform/audit/unknown')).toBeNull()
    expect(
      buildPlatformAuditRouteQuery({
        tab: 'authorization',
        activeTenantId: '9',
        keyword: 'login',
      }),
    ).toEqual({ keyword: 'login' })
  })

  it('should build and resolve platform role constraint tab child routes', () => {
    expect(buildPlatformRoleConstraintTabPath('violations')).toBe('/platform/role-constraints/violations')
    expect(buildPlatformRoleConstraintTabPath('unknown')).toBe('/platform/role-constraints/hierarchy')
    expect(resolvePlatformRoleConstraintTabFromPath('/platform/role-constraints/cardinality')).toBe(
      'cardinality',
    )
    expect(resolvePlatformRoleConstraintTabFromPath('/platform/role-constraints/unknown')).toBeNull()
    expect(
      buildPlatformRoleConstraintRouteQuery({
        tab: 'violations',
        targetTenantId: '12',
        keyword: 'rbac',
      }),
    ).toEqual({ keyword: 'rbac' })
  })

  it('should build platform process tab path and route query without tab or tenant state', () => {
    expect(buildPlatformProcessTabPath('instance')).toBe('/platform/process/instance')
    expect(buildPlatformProcessTabPath('unknown')).toBe('/platform/process/definition')
    expect(
      buildPlatformProcessRouteQuery({
        target: '/process/definition',
        activeTenantId: '9',
        targetTenantId: '12',
        tab: 'instance',
        keyword: 'approval',
      }),
    ).toEqual({ keyword: 'approval' })
  })

  it('should resolve platform process tab from child path only', () => {
    expect(resolvePlatformProcessTabFromPath('/platform/process/modeling')).toBe('modeling')
    expect(resolvePlatformProcessTabFromPath('/platform/process/unknown')).toBeNull()
  })

  it('should build and resolve platform dict tab child routes', () => {
    expect(buildPlatformDictTabPath('item')).toBe('/platform/dicts/item')
    expect(buildPlatformDictTabPath('unknown')).toBe('/platform/dicts/type')
    expect(resolvePlatformDictTabFromPath('/platform/dicts/overrides')).toBe('overrides')
    expect(resolvePlatformDictTabFromPath('/platform/dicts/unknown')).toBeNull()
    expect(
      buildPlatformDictRouteQuery({
        activeTenantId: '9',
        targetTenantId: '12',
        keyword: 'status',
        tab: 'item',
      }),
    ).toEqual({ keyword: 'status' })
  })

  it('should build and resolve platform user governance child routes', () => {
    expect(buildPlatformUserTabPath('platformUsers')).toBe('/platform/users/governance')
    expect(buildPlatformUserTabPath('tenantStewardship')).toBe('/platform/users/tenant-stewardship')
    expect(resolvePlatformUserTabFromPath('/platform/users/governance')).toBe('platformUsers')
    expect(resolvePlatformUserTabFromPath('/platform/users/tenant-stewardship')).toBe('tenantStewardship')
    expect(resolvePlatformUserTabFromPath('/platform/users/unknown')).toBeNull()
    expect(
      buildPlatformUserRouteQuery(
        {
          tab: 'tenantStewardship',
          tenantId: '9',
          activeTenantId: '1',
        },
        'tenantStewardship',
      ),
    ).toEqual({ tenantId: '9' })
    expect(buildPlatformUserRouteQuery({ tenantId: '9' }, 'platformUsers')).toEqual({})
  })

  it('should map tenant scheduling child route to platform child route', () => {
    expect(buildPlatformSchedulingPath('/platform/scheduling', '/scheduling/dag/detail')).toBe(
      '/platform/scheduling/dag/detail',
    )
  })
})
