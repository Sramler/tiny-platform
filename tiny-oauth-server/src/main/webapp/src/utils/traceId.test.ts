import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  ensureCsrfToken: vi.fn(),
  isUnsafeHttpMethod: vi.fn(),
  getTenantId: vi.fn(),
  persistentWarn: vi.fn(),
  persistentDebug: vi.fn(),
  persistentError: vi.fn(),
  routerReplace: vi.fn(),
}))

vi.mock('@/utils/csrf', () => ({
  ensureCsrfToken: mocks.ensureCsrfToken,
  isUnsafeHttpMethod: mocks.isUnsafeHttpMethod,
}))

vi.mock('@/utils/tenant', () => ({
  getTenantId: mocks.getTenantId,
}))

vi.mock('@/utils/logger', () => ({
  persistentLogger: {
    warn: mocks.persistentWarn,
    debug: mocks.persistentDebug,
    error: mocks.persistentError,
  },
}))

vi.mock('@/router', () => ({
  default: {
    replace: mocks.routerReplace,
  },
}))

describe('traceId.ts', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.clearAllMocks()
    sessionStorage.clear()
    vi.useFakeTimers()
    mocks.ensureCsrfToken.mockResolvedValue({
      token: 'csrf-token',
      parameterName: '_csrf',
      headerName: 'X-XSRF-TOKEN',
    })
    mocks.isUnsafeHttpMethod.mockReturnValue(false)
    mocks.getTenantId.mockReturnValue(null)
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('should add trace headers to fetch options', async () => {
    const traceIdModule = await import('@/utils/traceId')
    const options = traceIdModule.addTraceIdToFetchOptions({
      headers: {
        Accept: 'application/json',
      },
    })

    const headers = new Headers(options.headers)
    expect(headers.get('Accept')).toBe('application/json')
    expect(headers.get('X-Trace-Id')).toMatch(/^[0-9a-f]{32}$/)
    expect(headers.get('X-Request-Id')).toMatch(/^[0-9a-f]{16}$/)
  })

  it('should attach tenant and csrf headers for unsafe credentialed requests', async () => {
    mocks.isUnsafeHttpMethod.mockReturnValue(true)
    mocks.getTenantId.mockReturnValue('102')
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ success: true }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    const traceIdModule = await import('@/utils/traceId')
    await traceIdModule.fetchWithTraceId('http://localhost:9000/api/secure', {
      method: 'POST',
      credentials: 'include',
      headers: {
        Accept: 'application/json',
      },
    })

    const requestCall = fetchMock.mock.calls[0]
    expect(requestCall).toBeDefined()
    const requestInit = requestCall![1] as RequestInit
    const headers = new Headers(requestInit.headers)
    expect(headers.get('Accept')).toBe('application/json')
    expect(headers.get('X-Tenant-Id')).toBe('102')
    expect(headers.get('X-XSRF-TOKEN')).toBe('csrf-token')
    expect(headers.get('X-Trace-Id')).toMatch(/^[0-9a-f]{32}$/)
    expect(headers.get('X-Request-Id')).toMatch(/^[0-9a-f]{16}$/)
    expect(mocks.ensureCsrfToken).toHaveBeenCalledTimes(1)
  })

  it('should reuse session trace id once created', async () => {
    const traceIdModule = await import('@/utils/traceId')

    const first = traceIdModule.getOrCreateTraceId()
    const second = traceIdModule.getOrCreateTraceId()

    expect(second).toBe(first)
    expect(sessionStorage.getItem('app_trace_id')).toBe(first)
  })

  it('should redirect to 401 page and throw on unauthorized response', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(null, {
        status: 401,
        statusText: 'Unauthorized',
      }),
    )
    vi.stubGlobal('fetch', fetchMock)
    vi.stubGlobal('location', {
      ...window.location,
      href: 'http://localhost/dashboard',
      pathname: '/dashboard',
      search: '',
      origin: 'http://localhost',
      replace: vi.fn(),
    })

    const traceIdModule = await import('@/utils/traceId')
    const requestPromise = traceIdModule.fetchWithTraceId('http://localhost:9000/api/secure', {
      method: 'GET',
    })
    const rejection = expect(requestPromise).rejects.toThrow('未授权，请重新登录')

    await vi.runAllTimersAsync()
    await rejection
    expect(window.location.href).toBe('/exception/401')
    expect(mocks.persistentWarn).toHaveBeenCalled()
  })

  it('should skip unauthorized redirect when skipAuthError is enabled', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: false,
      status: 302,
      redirected: true,
      url: 'http://localhost:9000/login',
    })
    vi.stubGlobal('fetch', fetchMock)
    vi.stubGlobal('location', {
      ...window.location,
      href: 'http://localhost/dashboard',
      pathname: '/dashboard',
      search: '',
      origin: 'http://localhost',
      replace: vi.fn(),
    })

    const traceIdModule = await import('@/utils/traceId')
    const response = await traceIdModule.fetchWithTraceId('http://localhost:9000/api/secure', {
      method: 'GET',
      skipAuthError: true,
    })

    expect(response.status).toBe(302)
    expect(window.location.href).toBe('http://localhost/dashboard')
    expect(mocks.persistentWarn).not.toHaveBeenCalled()
  })
})
