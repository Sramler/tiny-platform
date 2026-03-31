import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/tenant', () => ({
  getActiveTenantId: vi.fn(),
}))

import { getActiveTenantId } from '@/utils/tenant'
import {
  ACTIVE_SCOPE_CHANGED_EVENT,
  notifyActiveScopeChanged,
  shouldReloadTenantControlPlaneOnActiveScopeChange,
} from './activeScopeEvents'

describe('activeScopeEvents', () => {
  beforeEach(() => {
    vi.mocked(getActiveTenantId).mockReset()
  })

  it('dispatches a single well-known event for scope changes', () => {
    const spy = vi.spyOn(window, 'dispatchEvent')
    notifyActiveScopeChanged()
    expect(spy).toHaveBeenCalledTimes(1)
    expect(spy.mock.calls[0]).toBeDefined()
    const ev = spy.mock.calls[0]![0] as CustomEvent
    expect(ev.type).toBe(ACTIVE_SCOPE_CHANGED_EVENT)
    spy.mockRestore()
  })

  it('shouldReloadTenantControlPlaneOnActiveScopeChange is false when no active tenant', () => {
    vi.mocked(getActiveTenantId).mockReturnValue(null)
    expect(shouldReloadTenantControlPlaneOnActiveScopeChange()).toBe(false)
  })

  it('shouldReloadTenantControlPlaneOnActiveScopeChange is true when active tenant id is set', () => {
    vi.mocked(getActiveTenantId).mockReturnValue('7')
    expect(shouldReloadTenantControlPlaneOnActiveScopeChange()).toBe(true)
  })
})
