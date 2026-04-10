/**
 * Login.vue 单测说明：
 * - 覆盖：模式切换、租户校验、平台模式 DOM、提交前租户工具函数调用、CSRF/redirect 渲染。
 * - 不覆盖：真实 POST /login、Session 粘性租户、OIDC 回调；这些见后端集成测试
 *   （如 AuthenticationFlowE2eProfileIntegrationTest）与 Playwright real-link（如 e2e/real/platform-vue-login.spec.ts）。
 * - `VITE_API_BASE_URL`：Vitest 使用 `.env.test`（见该文件），表单 action 与之一致而非硬编码 localhost。
 */
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  ensureCsrfToken: vi.fn(),
  clearActiveTenantId: vi.fn(),
  clearTenantCode: vi.fn(),
  getLoginMode: vi.fn(),
  getTenantCode: vi.fn(),
  isValidTenantCode: vi.fn(),
  setLoginMode: vi.fn(),
  setTenantCode: vi.fn(),
}))

const routeState: { query: Record<string, unknown> } = {
  query: {},
}

vi.mock('vue-router', () => ({
  useRoute: () => routeState,
}))

vi.mock('@/utils/csrf', () => ({
  ensureCsrfToken: mocks.ensureCsrfToken,
}))

vi.mock('@/utils/tenant', () => ({
  clearActiveTenantId: mocks.clearActiveTenantId,
  clearTenantCode: mocks.clearTenantCode,
  getLoginMode: mocks.getLoginMode,
  getTenantCode: mocks.getTenantCode,
  isValidTenantCode: mocks.isValidTenantCode,
  setLoginMode: mocks.setLoginMode,
  setTenantCode: mocks.setTenantCode,
}))

import Login from '@/views/Login.vue'

async function flushPromises() {
  await Promise.resolve()
  await nextTick()
}

describe('Login.vue', () => {
  beforeEach(() => {
    routeState.query = {}
    try {
      window.localStorage.removeItem('app_login_mode')
    } catch {
      // ignore
    }
    mocks.ensureCsrfToken.mockReset()
    mocks.clearActiveTenantId.mockReset()
    mocks.clearTenantCode.mockReset()
    mocks.getLoginMode.mockReset()
    mocks.getTenantCode.mockReset()
    mocks.isValidTenantCode.mockReset()
    mocks.setLoginMode.mockReset()
    mocks.setTenantCode.mockReset()

    mocks.ensureCsrfToken.mockResolvedValue({
      token: 'csrf-token',
      parameterName: '_csrf',
      headerName: 'X-XSRF-TOKEN',
    })
    mocks.getLoginMode.mockReturnValue(null)
    mocks.getTenantCode.mockReturnValue(null)
    mocks.isValidTenantCode.mockReturnValue(true)
  })

  it('should prefill tenant dev defaults (admin) on mount', async () => {
    const wrapper = mount(Login)
    await flushPromises()
    expect((wrapper.find('input[name="username"]').element as HTMLInputElement).value).toBe('admin')
    expect((wrapper.find('input[name="password"]').element as HTMLInputElement).value).toBe('admin')
  })

  it('should prefill platform dev defaults when saved login mode is platform', async () => {
    mocks.getLoginMode.mockReturnValue('PLATFORM')
    const wrapper = mount(Login)
    await flushPromises()
    expect((wrapper.find('input[name="username"]').element as HTMLInputElement).value).toBe(
      'platform_admin',
    )
    expect((wrapper.find('input[name="password"]').element as HTMLInputElement).value).toBe('admin')
  })

  it('should swap dev default username when switching login mode tabs', async () => {
    const wrapper = mount(Login)
    await flushPromises()
    expect((wrapper.find('input[name="username"]').element as HTMLInputElement).value).toBe('admin')
    await wrapper.findAll('button.scope-tab')[1]?.trigger('click')
    await flushPromises()
    expect(mocks.setLoginMode).toHaveBeenCalledWith('PLATFORM')
    expect((wrapper.find('input[name="username"]').element as HTMLInputElement).value).toBe(
      'platform_admin',
    )
    await wrapper.findAll('button.scope-tab')[0]?.trigger('click')
    await flushPromises()
    expect(mocks.setLoginMode).toHaveBeenCalledWith('TENANT')
    expect((wrapper.find('input[name="username"]').element as HTMLInputElement).value).toBe('admin')
  })

  it('should sanitize redirect query and render csrf hidden field', async () => {
    routeState.query = {
      redirect: 'https://evil.com/callback',
    }

    const wrapper = mount(Login)
    await flushPromises()

    const redirectInput = wrapper.find('input[name="redirect"]')
    const csrfInput = wrapper.find('input[name="_csrf"]')

    expect(redirectInput.exists()).toBe(true)
    expect((redirectInput.element as HTMLInputElement).value).toBe('/')
    expect(csrfInput.exists()).toBe(true)
    expect((csrfInput.element as HTMLInputElement).value).toBe('csrf-token')
  })

  it('should block submit when tenant code is invalid', async () => {
    mocks.isValidTenantCode.mockReturnValue(false)

    const wrapper = mount(Login)
    await flushPromises()

    await wrapper.find('input[name="tenantCode"]').setValue('BAD_CODE')
    await wrapper.find('form').trigger('submit')

    expect(wrapper.text()).toContain('租户编码格式错误')
    expect(mocks.setTenantCode).not.toHaveBeenCalled()
    expect(mocks.clearActiveTenantId).not.toHaveBeenCalled()
  })

  it('should render backend failure message from query', async () => {
    routeState.query = {
      message: '登录失败次数过多，请 15 分钟后重试',
    }

    const wrapper = mount(Login)
    await flushPromises()

    expect(wrapper.text()).toContain('登录失败次数过多，请 15 分钟后重试')
  })

  it('should normalize tenant code and submit form for valid input', async () => {
    const wrapper = mount(Login)
    await flushPromises()

    const form = wrapper.find('form')
    const submitSpy = vi.fn()
    Object.defineProperty(form.element, 'submit', {
      value: submitSpy,
      configurable: true,
    })

    await wrapper.find('input[name="tenantCode"]').setValue('Tiny-Prod')
    await wrapper.find('form').trigger('submit')

    expect(mocks.setTenantCode).toHaveBeenCalledWith('tiny-prod')
    expect(mocks.clearActiveTenantId).toHaveBeenCalledTimes(1)
    expect(submitSpy).toHaveBeenCalledTimes(1)
  })

  it('should not render tenant field when saved login mode is platform', async () => {
    mocks.getTenantCode.mockReturnValue('default')
    mocks.getLoginMode.mockReturnValue('PLATFORM')
    const wrapper = mount(Login)
    await flushPromises()
    expect(wrapper.find('input[name="tenantCode"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('控制面治理')
  })

  it('should hide tenant field on platform tab and restore value when back to tenant', async () => {
    mocks.getTenantCode.mockReturnValue('default')
    const wrapper = mount(Login)
    await flushPromises()

    expect(wrapper.find('input[name="tenantCode"]').exists()).toBe(true)
    expect((wrapper.find('input[name="tenantCode"]').element as HTMLInputElement).value).toBe(
      'default',
    )

    await wrapper.findAll('button.scope-tab')[1]?.trigger('click')
    await flushPromises()
    expect(wrapper.find('input[name="tenantCode"]').exists()).toBe(false)

    await wrapper.findAll('button.scope-tab')[0]?.trigger('click')
    await flushPromises()
    const restored = wrapper.find('input[name="tenantCode"]')
    expect(restored.exists()).toBe(true)
    expect((restored.element as HTMLInputElement).value).toBe('default')
  })

  it('should allow platform mode submit without tenant code field in DOM', async () => {
    const wrapper = mount(Login)
    await flushPromises()

    await wrapper.findAll('button.scope-tab')[1]?.trigger('click')
    await flushPromises()

    expect(wrapper.find('input[name="tenantCode"]').exists()).toBe(false)

    const form = wrapper.find('form')
    const submitSpy = vi.fn()
    Object.defineProperty(form.element, 'submit', {
      value: submitSpy,
      configurable: true,
    })

    await wrapper.find('form').trigger('submit')

    expect(mocks.setTenantCode).not.toHaveBeenCalled()
    expect(mocks.clearTenantCode).toHaveBeenCalledTimes(1)
    expect(mocks.clearActiveTenantId).toHaveBeenCalledTimes(1)
    expect(submitSpy).toHaveBeenCalledTimes(1)
  })

  it('should set form action to VITE_API_BASE_URL + /login (see .env.test in Vitest)', async () => {
    const raw = import.meta.env.VITE_API_BASE_URL || 'http://localhost:9000'
    const base = raw.endsWith('/') ? raw.slice(0, -1) : raw
    const wrapper = mount(Login)
    await flushPromises()
    const form = wrapper.find('form').element as HTMLFormElement
    expect(form.getAttribute('action')).toBe(`${base}/login`)
  })

  it('should not include tenantCode in platform mode form control names', async () => {
    const wrapper = mount(Login)
    await flushPromises()
    await wrapper.findAll('button.scope-tab')[1]?.trigger('click')
    await flushPromises()

    const form = wrapper.find('form').element as HTMLFormElement
    const names = Array.from(form.elements)
      .map((el) => (el as HTMLInputElement).name)
      .filter(Boolean)
    expect(names).not.toContain('tenantCode')
    expect(names).toEqual(
      expect.arrayContaining([
        'username',
        'password',
        'authenticationProvider',
        'authenticationType',
        'redirect',
        '_csrf',
      ]),
    )
  })
})
