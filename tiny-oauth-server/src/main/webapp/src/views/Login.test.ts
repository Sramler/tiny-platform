import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  ensureCsrfToken: vi.fn(),
  clearActiveTenantId: vi.fn(),
  getTenantCode: vi.fn(),
  isValidTenantCode: vi.fn(),
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
  getTenantCode: mocks.getTenantCode,
  isValidTenantCode: mocks.isValidTenantCode,
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
    mocks.ensureCsrfToken.mockReset()
    mocks.clearActiveTenantId.mockReset()
    mocks.getTenantCode.mockReset()
    mocks.isValidTenantCode.mockReset()
    mocks.setTenantCode.mockReset()

    mocks.ensureCsrfToken.mockResolvedValue({
      token: 'csrf-token',
      parameterName: '_csrf',
      headerName: 'X-XSRF-TOKEN',
    })
    mocks.getTenantCode.mockReturnValue(null)
    mocks.isValidTenantCode.mockReturnValue(true)
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
})
