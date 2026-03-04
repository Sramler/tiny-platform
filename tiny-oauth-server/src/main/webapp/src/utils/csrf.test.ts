import { afterEach, describe, expect, it, vi } from 'vitest'

import { clearCsrfTokenCache, ensureCsrfToken, isUnsafeHttpMethod } from '@/utils/csrf'

describe('csrf utils', () => {
  afterEach(() => {
    clearCsrfTokenCache()
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
    expect(fetchMock).toHaveBeenCalledWith('http://localhost:9000/csrf', {
      method: 'GET',
      credentials: 'include',
      headers: { Accept: 'application/json' },
    })
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

    await expect(ensureCsrfToken('http://localhost:9000')).rejects.toThrow('CSRF token 响应不完整')
  })
})
