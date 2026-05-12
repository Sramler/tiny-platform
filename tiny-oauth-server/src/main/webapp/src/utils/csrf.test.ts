import { afterEach, describe, expect, it, vi } from 'vitest'

import {
  clearCsrfTokenCache,
  CsrfTokenError,
  ensureCsrfToken,
  getCsrfFailureMessage,
  isUnsafeHttpMethod,
} from '@/utils/csrf'

describe('csrf utils', () => {
  afterEach(() => {
    clearCsrfTokenCache()
    vi.unstubAllGlobals()
    vi.useRealTimers()
  })

  it('should classify unsafe http methods', () => {
    expect(isUnsafeHttpMethod('POST')).toBe(true)
    expect(isUnsafeHttpMethod('PUT')).toBe(true)
    expect(isUnsafeHttpMethod('GET')).toBe(false)
    expect(isUnsafeHttpMethod('HEAD')).toBe(false)
  })

  it('should fetch and cache csrf token', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        token: 'csrf-token',
        parameterName: '_csrf',
        headerName: 'X-XSRF-TOKEN',
      }),
    })
    vi.stubGlobal('fetch', fetchMock)

    const first = await ensureCsrfToken('http://localhost:9000/')
    const second = await ensureCsrfToken('http://localhost:9000/')

    expect(first).toEqual({
      token: 'csrf-token',
      parameterName: '_csrf',
      headerName: 'X-XSRF-TOKEN',
    })
    expect(second).toEqual(first)
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:9000/csrf',
      expect.objectContaining({
        method: 'GET',
        credentials: 'include',
        headers: { Accept: 'application/json' },
        signal: expect.any(AbortSignal),
      }),
    )
  })

  it('should reject incomplete csrf payload', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({
          token: 'csrf-token',
          parameterName: '_csrf',
        }),
      }),
    )

    await expect(ensureCsrfToken('http://localhost:9000')).rejects.toMatchObject({
      code: 'bad_response',
      message: 'CSRF token 响应不完整',
    })
  })

  it.each([
    [401, 'unauthorized'],
    [403, 'forbidden'],
    [500, 'server_error'],
    [503, 'server_error'],
  ])('should classify csrf http status %s as %s', async (status, code) => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status,
      }),
    )

    await expect(ensureCsrfToken('http://localhost:9000')).rejects.toMatchObject({
      code,
      status,
    })
  })

  it('should classify csrf network failure', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))

    await expect(ensureCsrfToken('http://localhost:9000')).rejects.toMatchObject({
      code: 'network_error',
    })
  })

  it('should classify csrf timeout', async () => {
    vi.useFakeTimers()
    const fetchMock = vi.fn((_url: string, init?: RequestInit) => {
      return new Promise((_resolve, reject) => {
        init?.signal?.addEventListener('abort', () => {
          reject(new DOMException('Aborted', 'AbortError'))
        })
      })
    })
    vi.stubGlobal('fetch', fetchMock)

    const promise = ensureCsrfToken('http://localhost:9000', { timeoutMs: 25 })
    const assertion = expect(promise).rejects.toMatchObject({
      code: 'timeout',
    })
    await vi.advanceTimersByTimeAsync(25)

    await assertion
  })

  it('should map csrf failures to user-facing messages', () => {
    expect(getCsrfFailureMessage(new CsrfTokenError('network_error', 'network'))).toContain(
      '认证服务暂不可用',
    )
    expect(getCsrfFailureMessage(new CsrfTokenError('timeout', 'timeout'))).toContain('响应超时')
    expect(getCsrfFailureMessage(new CsrfTokenError('unauthorized', '401'))).toContain(
      '登录安全校验已失效',
    )
    expect(getCsrfFailureMessage(new CsrfTokenError('forbidden', '403'))).toContain(
      '认证服务拒绝',
    )
    expect(getCsrfFailureMessage(new CsrfTokenError('server_error', '500'))).toContain(
      '认证服务异常',
    )
  })
})
