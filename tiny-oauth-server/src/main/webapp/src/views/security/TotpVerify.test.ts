import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  ensureCsrfToken: vi.fn(),
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

import TotpVerify from '@/views/security/TotpVerify.vue'

async function flushPromises() {
  await Promise.resolve()
  await Promise.resolve()
  await nextTick()
}

describe('TotpVerify.vue', () => {
  beforeEach(() => {
    routeState.query = {}
    mocks.ensureCsrfToken.mockReset()
    mocks.ensureCsrfToken.mockResolvedValue({
      token: 'csrf-token',
      parameterName: '_csrf',
      headerName: 'X-XSRF-TOKEN',
    })
  })

  it('should sanitize redirect and render query error text', async () => {
    routeState.query = {
      redirect: 'https://evil.com/callback',
      error: '验证码错误',
    }

    const wrapper = mount(TotpVerify)
    await flushPromises()

    const redirectInput = wrapper.find('input[name="redirect"]')
    const csrfInput = wrapper.find('input[name="_csrf"]')

    expect(wrapper.text()).toContain('验证码错误')
    expect((redirectInput.element as HTMLInputElement).value).toBe('/')
    expect((csrfInput.element as HTMLInputElement).value).toBe('csrf-token')
  })

  it('should lazily reload csrf token before submit', async () => {
    mocks.ensureCsrfToken
      .mockRejectedValueOnce(new Error('init failed'))
      .mockResolvedValueOnce({
        token: 'csrf-lazy',
        parameterName: '_csrf',
        headerName: 'X-XSRF-TOKEN',
      })

    const wrapper = mount(TotpVerify)
    await flushPromises()

    const form = wrapper.find('form')
    const submitSpy = vi.fn()
    Object.defineProperty(form.element, 'submit', {
      value: submitSpy,
      configurable: true,
    })

    await form.trigger('submit')
    await flushPromises()

    expect(submitSpy).toHaveBeenCalledTimes(1)
  })

  it('should stop submit when csrf reload fails', async () => {
    mocks.ensureCsrfToken
      .mockRejectedValueOnce(new Error('init failed'))
      .mockRejectedValueOnce(new Error('submit failed'))

    const wrapper = mount(TotpVerify)
    await flushPromises()

    const form = wrapper.find('form')
    const submitSpy = vi.fn()
    Object.defineProperty(form.element, 'submit', {
      value: submitSpy,
      configurable: true,
    })

    await form.trigger('submit')
    await flushPromises()

    expect(submitSpy).not.toHaveBeenCalled()
  })
})
