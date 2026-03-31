/**
 * HeaderBar：作用域切换编排单测（`confirmSwitchScope`）。
 * - 不覆盖真实 OIDC silent renew / 浏览器 iframe；该类风险见 real-link E2E 与 SESSION_BEARER 矩阵 §8.4。
 */
import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  logout: vi.fn(),
  refreshTokenAfterActiveScopeSwitch: vi.fn(),
  switchActiveScope: vi.fn(),
  getCurrentUser: vi.fn(),
  notifyActiveScopeChanged: vi.fn(),
  getOrgList: vi.fn(),
}))

const messageMocks = vi.hoisted(() => ({
  success: vi.fn(),
  warning: vi.fn(),
  error: vi.fn(),
  info: vi.fn(),
  loading: vi.fn(),
}))

vi.mock('@/auth/auth', () => ({
  useAuth: () => ({ logout: mocks.logout }),
  refreshTokenAfterActiveScopeSwitch: mocks.refreshTokenAfterActiveScopeSwitch,
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
    mocks.getCurrentUser.mockResolvedValue(currentUserPayload)
    mocks.getOrgList.mockResolvedValue([])
    mocks.switchActiveScope.mockResolvedValue({ success: true, tokenRefreshRequired: false })
    mocks.refreshTokenAfterActiveScopeSwitch.mockResolvedValue({
      ok: true,
      user: { expired: false, access_token: 'at', profile: {} },
    })
  })

  it('when tokenRefreshRequired is not true: does not renew, loads user, success + broadcast', async () => {
    mocks.switchActiveScope.mockResolvedValue({ success: true, tokenRefreshRequired: false })

    const wrapper = await mountHeaderBar()
    mocks.getCurrentUser.mockClear()

    await wrapper.vm.confirmSwitchScope()
    await flushPromises()

    expect(mocks.refreshTokenAfterActiveScopeSwitch).not.toHaveBeenCalled()
    expect(mocks.switchActiveScope).toHaveBeenCalledWith({
      scopeType: 'TENANT',
      scopeId: undefined,
    })
    expect(mocks.getCurrentUser).toHaveBeenCalledTimes(1)
    expect(messageMocks.success).toHaveBeenCalledWith('作用域已切换')
    expect(messageMocks.warning).not.toHaveBeenCalled()
    expect(mocks.notifyActiveScopeChanged).toHaveBeenCalledTimes(1)
  })

  it('when tokenRefreshRequired is true and renew succeeds: order switch → renew → getCurrentUser, then success + broadcast', async () => {
    const order: string[] = []
    mocks.switchActiveScope.mockImplementation(async () => {
      order.push('switch')
      return { success: true, tokenRefreshRequired: true, newActiveScopeType: 'TENANT' }
    })
    mocks.refreshTokenAfterActiveScopeSwitch.mockImplementation(async () => {
      order.push('renew')
      return { ok: true, user: { expired: false, access_token: 'at2', profile: {} } }
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

    expect(order).toEqual(['switch', 'renew', 'getCurrentUser'])
    expect(messageMocks.success).toHaveBeenCalledWith('作用域已切换')
    expect(messageMocks.warning).not.toHaveBeenCalled()
    expect(mocks.notifyActiveScopeChanged).toHaveBeenCalledTimes(1)
  })

  it('when tokenRefreshRequired is true and renew fails: warning only, no success, no broadcast', async () => {
    mocks.switchActiveScope.mockResolvedValue({ success: true, tokenRefreshRequired: true })
    mocks.refreshTokenAfterActiveScopeSwitch.mockResolvedValue({ ok: false })

    const wrapper = await mountHeaderBar()
    mocks.getCurrentUser.mockClear()

    await wrapper.vm.confirmSwitchScope()
    await flushPromises()

    expect(mocks.refreshTokenAfterActiveScopeSwitch).toHaveBeenCalledTimes(1)
    expect(mocks.getCurrentUser).not.toHaveBeenCalled()
    expect(messageMocks.warning).toHaveBeenCalledWith(
      '作用域已在服务端更新，但未能刷新访问令牌。请重新登录后再继续使用。',
    )
    expect(messageMocks.success).not.toHaveBeenCalled()
    expect(mocks.notifyActiveScopeChanged).not.toHaveBeenCalled()
  })

  it('when activeScopeType is PLATFORM and local activeTenantId missing: blocks scope switch', async () => {
    window.localStorage.removeItem('app_active_tenant_id')
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
})
