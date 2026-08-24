/**
 * HeaderBar：作用域切换编排单测（`confirmSwitchScope`）。
 * - Web 作用域切换后重读 Session principal，不存在浏览器 token renew。
 */
import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  logout: vi.fn(),
  fetchWithAuth: vi.fn(),
  refreshSessionPrincipal: vi.fn(),
  switchActiveScope: vi.fn(),
  getCurrentUser: vi.fn(),
  notifyActiveScopeChanged: vi.fn(),
  getOrgList: vi.fn(),
  getActiveTenantId: vi.fn(),
  setActiveTenantId: vi.fn(),
  clearActiveTenantId: vi.fn(),
  clearTenantCode: vi.fn(),
  setLoginMode: vi.fn(),
}))

const messageMocks = vi.hoisted(() => ({
  success: vi.fn(),
  warning: vi.fn(),
  error: vi.fn(),
  info: vi.fn(),
  loading: vi.fn(),
}))

vi.mock('@/auth/auth', () => ({
  useAuth: () => ({
    logout: mocks.logout,
    fetchWithAuth: mocks.fetchWithAuth,
    refreshSessionPrincipal: mocks.refreshSessionPrincipal,
  }),
}))

vi.mock('@/api/user', () => ({
  getCurrentUser: mocks.getCurrentUser,
  switchActiveScope: mocks.switchActiveScope,
}))

vi.mock('@/utils/activeScopeEvents', () => ({
  notifyActiveScopeChanged: mocks.notifyActiveScopeChanged,
  ACTIVE_SCOPE_CHANGED_EVENT: 'active-scope-changed',
}))

vi.mock('@/api/org', () => ({
  getOrgList: mocks.getOrgList,
}))

vi.mock('@/utils/tenant', () => ({
  getActiveTenantId: mocks.getActiveTenantId,
  setActiveTenantId: mocks.setActiveTenantId,
  clearActiveTenantId: mocks.clearActiveTenantId,
  clearTenantCode: mocks.clearTenantCode,
  setLoginMode: mocks.setLoginMode,
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
}))

vi.mock('ant-design-vue', async (importOriginal) => {
  const mod = await importOriginal<typeof import('ant-design-vue')>()
  return {
    ...mod,
    message: messageMocks,
  }
})

import HeaderBar from '@/layouts/HeaderBar.vue'

const currentUserPayload = {
  id: '1',
  username: 'alice',
  nickname: 'Alice',
  activeScopeType: 'TENANT',
}

async function mountHeaderBar() {
  const wrapper = mount(HeaderBar, {
    global: {
      stubs: {
        UserOutlined: true,
        SettingOutlined: true,
        LogoutOutlined: true,
        DownOutlined: true,
      },
    },
  })
  await flushPromises()
  return wrapper
}

describe('HeaderBar.vue confirmSwitchScope', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    URL.revokeObjectURL = vi.fn()
    URL.createObjectURL = vi.fn(() => 'blob:avatar')
    mocks.getCurrentUser.mockResolvedValue(currentUserPayload)
    mocks.getOrgList.mockResolvedValue([])
    mocks.switchActiveScope.mockResolvedValue({ success: true, tokenRefreshRequired: false })
    mocks.refreshSessionPrincipal.mockResolvedValue({ username: 'alice' })
    mocks.getActiveTenantId.mockReturnValue('7')
  })

  it('when tokenRefreshRequired is not true: does not renew, loads user, success + broadcast', async () => {
    mocks.switchActiveScope.mockResolvedValue({ success: true, tokenRefreshRequired: false })

    const wrapper = await mountHeaderBar()
    mocks.getCurrentUser.mockClear()

    await wrapper.vm.confirmSwitchScope()
    await flushPromises()

    expect(mocks.refreshSessionPrincipal).toHaveBeenCalledTimes(1)
    expect(mocks.switchActiveScope).toHaveBeenCalledWith({
      scopeType: 'TENANT',
      scopeId: undefined,
    })
    expect(mocks.getCurrentUser).toHaveBeenCalledTimes(1)
    expect(messageMocks.success).toHaveBeenCalledWith('作用域已切换')
    expect(messageMocks.warning).not.toHaveBeenCalled()
    expect(mocks.notifyActiveScopeChanged).toHaveBeenCalledTimes(1)
  })

  it('ignores legacy token flags and orders switch → Session refresh → getCurrentUser', async () => {
    const order: string[] = []
    mocks.switchActiveScope.mockImplementation(async () => {
      order.push('switch')
      return { success: true, tokenRefreshRequired: true, newActiveScopeType: 'TENANT' }
    })
    mocks.refreshSessionPrincipal.mockImplementation(async () => {
      order.push('session')
      return { username: 'alice' }
    })
    mocks.getCurrentUser.mockImplementation(async () => {
      order.push('getCurrentUser')
      return currentUserPayload
    })

    const wrapper = await mountHeaderBar()
    mocks.getCurrentUser.mockClear()
    order.length = 0

    await wrapper.vm.confirmSwitchScope()
    await flushPromises()

    expect(order).toEqual(['switch', 'session', 'getCurrentUser'])
    expect(messageMocks.success).toHaveBeenCalledWith('作用域已切换')
    expect(messageMocks.warning).not.toHaveBeenCalled()
    expect(mocks.notifyActiveScopeChanged).toHaveBeenCalledTimes(1)
  })

  it('reports a Session refresh failure and does not rebuild the page runtime', async () => {
    mocks.switchActiveScope.mockResolvedValue({ success: true, tokenRefreshRequired: true })
    mocks.refreshSessionPrincipal.mockRejectedValue(new Error('session refresh failed'))

    const wrapper = await mountHeaderBar()
    mocks.getCurrentUser.mockClear()

    await wrapper.vm.confirmSwitchScope()
    await flushPromises()

    expect(mocks.refreshSessionPrincipal).toHaveBeenCalledTimes(1)
    expect(mocks.getCurrentUser).not.toHaveBeenCalled()
    expect(messageMocks.error).toHaveBeenCalledWith('切换作用域失败')
    expect(messageMocks.success).not.toHaveBeenCalled()
    expect(mocks.notifyActiveScopeChanged).not.toHaveBeenCalled()
  })

  it('when activeScopeType is PLATFORM and local activeTenantId missing: blocks scope switch', async () => {
    mocks.getActiveTenantId.mockReturnValue(null)
    mocks.getCurrentUser.mockResolvedValue({
      ...currentUserPayload,
      activeScopeType: 'PLATFORM',
      // activeTenantId 故意不提供：平台态不允许走 active-scope 写链路
    } as any)

    const wrapper = await mountHeaderBar()
    mocks.switchActiveScope.mockClear()

    await wrapper.vm.confirmSwitchScope()
    await flushPromises()

    expect(mocks.switchActiveScope).not.toHaveBeenCalled()
    expect(messageMocks.warning).toHaveBeenCalledWith('当前平台态不支持在此处切换作用域')
  })

  it('syncs local platform context before refreshing the Session principal', async () => {
    const order: string[] = []
    mocks.switchActiveScope.mockImplementation(async () => {
      order.push('switch')
      return { success: true, tokenRefreshRequired: true, newActiveScopeType: 'PLATFORM' }
    })
    mocks.setLoginMode.mockImplementation(() => {
      order.push('loginMode')
    })
    mocks.clearTenantCode.mockImplementation(() => {
      order.push('clearTenantCode')
    })
    mocks.clearActiveTenantId.mockImplementation(() => {
      order.push('clearActiveTenantId')
    })
    mocks.refreshSessionPrincipal.mockImplementation(async () => {
      order.push('session')
      return { username: 'alice', activeScopeType: 'PLATFORM' }
    })
    mocks.getCurrentUser.mockImplementation(async () => {
      order.push('getCurrentUser')
      return {
        ...currentUserPayload,
        activeScopeType: 'PLATFORM',
      }
    })

    const wrapper = await mountHeaderBar()
    mocks.getCurrentUser.mockClear()
    order.length = 0
    ;(wrapper.vm as any).nextScopeType = 'PLATFORM'

    await wrapper.vm.confirmSwitchScope()
    await flushPromises()

    expect(mocks.switchActiveScope).toHaveBeenCalledWith({
      scopeType: 'PLATFORM',
      scopeId: undefined,
    })
    expect(order).toEqual([
      'switch',
      'loginMode',
      'clearTenantCode',
      'clearActiveTenantId',
      'session',
      'getCurrentUser',
    ])
    expect(messageMocks.success).toHaveBeenCalledWith('作用域已切换')
  })

  it('shows a neutral skeleton before current user resolves to avoid TENANT/PLATFORM flicker', async () => {
    let resolveCurrentUser: (value: typeof currentUserPayload) => void = () => {}
    mocks.getCurrentUser.mockReturnValue(
      new Promise((resolve) => {
        resolveCurrentUser = resolve
      }),
    )

    const wrapper = mount(HeaderBar, {
      global: {
        stubs: {
          UserOutlined: true,
          SettingOutlined: true,
          LogoutOutlined: true,
          DownOutlined: true,
        },
      },
    })

    expect(wrapper.text()).toContain('用户信息加载中')
    expect(wrapper.text()).not.toContain('管理员')
    expect(wrapper.text()).not.toContain('TENANT')

    resolveCurrentUser(currentUserPayload)
    await flushPromises()

    expect(wrapper.text()).toContain('Alice')
    expect(wrapper.text()).toContain('TENANT')
  })

  it('does not request avatar when current user reports no avatar', async () => {
    mocks.getCurrentUser.mockResolvedValue({ ...currentUserPayload, hasAvatar: false })

    await mountHeaderBar()

    expect(mocks.fetchWithAuth).not.toHaveBeenCalled()
  })

  it('loads avatar through authenticated blob fetch when current user has avatar', async () => {
    const blob = new Blob(['avatar'], { type: 'image/png' })
    mocks.getCurrentUser.mockResolvedValue({ ...currentUserPayload, hasAvatar: true })
    mocks.fetchWithAuth.mockResolvedValue({
      ok: true,
      status: 200,
      headers: new Headers({ 'content-type': 'image/png' }),
      blob: vi.fn().mockResolvedValue(blob),
    })

    const wrapper = await mountHeaderBar()
    await flushPromises()

    expect(mocks.fetchWithAuth).toHaveBeenCalledWith(
      expect.stringContaining('/sys/users/current/avatar?'),
      expect.objectContaining({
        method: 'GET',
        cache: 'no-store',
      }),
    )
    expect(URL.createObjectURL).toHaveBeenCalledWith(blob)
    expect(wrapper.find('img.avatar').attributes('src')).toBe('blob:avatar')
  })
})
