import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  ensureCsrfToken: vi.fn(),
  fetch: vi.fn(),
}))

const routeState: { query: Record<string, unknown> } = {
  query: {},
}

vi.mock('vue-router', () => ({
  useRoute: () => routeState,
}))

vi.mock('@/utils/csrf', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/utils/csrf')>()
  return {
    ...actual,
    ensureCsrfToken: mocks.ensureCsrfToken,
  }
})

import TotpBind from '@/views/security/TotpBind.vue'

async function flushPromises() {
  for (let index = 0; index < 5; index += 1) {
    await Promise.resolve()
    await new Promise((resolve) => setTimeout(resolve, 0))
    await nextTick()
  }
}

async function waitFor(assertion: () => void) {
  let lastError: unknown
  for (let index = 0; index < 20; index += 1) {
    try {
      assertion()
      return
    } catch (error) {
      lastError = error
      await flushPromises()
    }
  }
  throw lastError
}

describe('TotpBind.vue', () => {
  beforeEach(() => {
    routeState.query = {}
    mocks.ensureCsrfToken.mockReset()
    mocks.fetch.mockReset()

    mocks.ensureCsrfToken.mockResolvedValue({
      token: 'csrf-token',
      parameterName: '_csrf',
      headerName: 'X-XSRF-TOKEN',
    })
    mocks.fetch.mockImplementation(async (input: string | URL | Request) => {
      const url = typeof input === 'string' ? input : input instanceof URL ? input.toString() : input.url
      if (url.includes('/self/security/status')) {
        return new Response(JSON.stringify({ disableMfa: false, forceMfa: false }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        })
      }
      if (url.includes('/self/security/totp/pre-bind')) {
        return new Response(JSON.stringify({
          success: true,
          secretKey: 'ABC123',
          qrCodeDataUrl: 'data:image/png;base64,totp',
        }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        })
      }
      throw new Error(`unexpected fetch url: ${url}`)
    })
    vi.stubGlobal('fetch', mocks.fetch)
  })

  it('should sanitize redirect and render fetched totp info', async () => {
    routeState.query = {
      redirect: 'https://evil.com/callback',
    }

    const wrapper = mount(TotpBind)
    await flushPromises()
    await waitFor(() => {
      expect(wrapper.find('.secret-value').text()).toBe('ABC123')
      expect(wrapper.find('img.qrcode-img').attributes('src')).toBe('data:image/png;base64,totp')
    })

    const redirectInput = wrapper.find('input[name="redirect"]')
    const csrfInput = wrapper.find('input[name="_csrf"]')
    const qrImage = wrapper.find('img.qrcode-img')

    expect((redirectInput.element as HTMLInputElement).value).toBe('/')
    expect((csrfInput.element as HTMLInputElement).value).toBe('csrf-token')
    expect(qrImage.exists()).toBe(true)
    expect(qrImage.attributes('src')).toBe('data:image/png;base64,totp')
  })

  it('should show load error and disable bind submit when status request fails', async () => {
    mocks.fetch.mockImplementation(async (input: string | URL | Request) => {
      const url = typeof input === 'string' ? input : input instanceof URL ? input.toString() : input.url
      if (url.includes('/self/security/status')) {
        throw new Error('boom')
      }
      if (url.includes('/self/security/totp/pre-bind')) {
        return new Response(JSON.stringify({
          success: true,
          secretKey: 'ABC123',
          qrCodeDataUrl: 'data:image/png;base64,totp',
        }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        })
      }
      throw new Error(`unexpected fetch url: ${url}`)
    })

    const wrapper = mount(TotpBind)
    await flushPromises()

    expect(wrapper.text()).toContain('无法获取安全状态，请稍后重试')
    expect(wrapper.find('button[type="submit"]').attributes('disabled')).toBeDefined()
  })

  it('should submit skip form after csrf is loaded lazily', async () => {
    mocks.ensureCsrfToken
      .mockRejectedValueOnce(new Error('init failed'))
      .mockResolvedValueOnce({
        token: 'csrf-lazy',
        parameterName: '_csrf',
        headerName: 'X-XSRF-TOKEN',
      })

    const wrapper = mount(TotpBind)
    await flushPromises()

    const forms = wrapper.findAll('form')
    expect(forms).toHaveLength(2)
    const skipForm = forms[1]!
    const submitSpy = vi.fn()
    Object.defineProperty(skipForm.element, 'submit', {
      value: submitSpy,
      configurable: true,
    })

    await skipForm.trigger('submit')
    await flushPromises()

    expect(submitSpy).toHaveBeenCalledTimes(1)
  })
})
