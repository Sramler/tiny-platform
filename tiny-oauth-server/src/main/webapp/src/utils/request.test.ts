import type { InternalAxiosRequestConfig } from 'axios'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

type RequestFulfilled = (config: InternalAxiosRequestConfig) => Promise<InternalAxiosRequestConfig>
type ResponseFulfilled = (response: any) => any
type ResponseRejected = (error: any) => Promise<any>

const axiosState = vi.hoisted(() => {
  const service = {
    interceptors: {
      request: {
        use: vi.fn(),
      },
      response: {
        use: vi.fn(),
      },
    },
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
    patch: vi.fn(),
  }

  return {
    service,
    create: vi.fn(() => service),
    requestFulfilled: undefined as RequestFulfilled | undefined,
    responseFulfilled: undefined as ResponseFulfilled | undefined,
    responseRejected: undefined as ResponseRejected | undefined,
  }
})

const mocks = vi.hoisted(() => ({
  getAccessToken: vi.fn(),
  refreshAccessTokenOnce: vi.fn(),
  logout: vi.fn(),
  routerPush: vi.fn(),
  routerReplace: vi.fn(),
  getOrCreateTraceId: vi.fn(),
  generateRequestId: vi.fn(),
  getCurrentTraceId: vi.fn(),
  clearTenantContext: vi.fn(),
  getActiveTenantId: vi.fn(),
  getLoginMode: vi.fn(),
  getTenantId: vi.fn(),
  syncTenantContextFromAccessToken: vi.fn(),
  createIdempotencyHeaders: vi.fn(),
  createIdempotencyFingerprint: vi.fn(),
  createSubmitIdempotencyKey: vi.fn(),
  extractErrorFromAxios: vi.fn(),
  extractErrorInfo: vi.fn(),
  persistentWarn: vi.fn(),
  ensureCsrfToken: vi.fn(),
  currentRouteValue: {
    path: '/dashboard',
    fullPath: '/dashboard?tab=security',
    query: {} as Record<string, unknown>,
  },
}))

vi.mock('axios', () => {
  axiosState.service.interceptors.request.use.mockImplementation((fulfilled: RequestFulfilled) => {
    axiosState.requestFulfilled = fulfilled
    return 0
  })
  axiosState.service.interceptors.response.use.mockImplementation(
    (_fulfilled: unknown, rejected: ResponseRejected) => {
      axiosState.responseFulfilled = _fulfilled as ResponseFulfilled
      axiosState.responseRejected = rejected
      return 0
    },
  )

  return {
    default: {
      create: axiosState.create,
    },
    create: axiosState.create,
  }
})

vi.mock('@/auth/auth', () => ({
  useAuth: () => ({
    getAccessToken: mocks.getAccessToken,
  }),
  refreshAccessTokenOnce: mocks.refreshAccessTokenOnce,
  logout: mocks.logout,
}))

vi.mock('@/auth/config', () => ({
  authRuntimeConfig: { sessionOnly: true },
}))

vi.mock('@/utils/csrf', () => ({
  isUnsafeHttpMethod: (method?: string) =>
    !['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes((method || 'GET').toUpperCase()),
  ensureCsrfToken: mocks.ensureCsrfToken,
}))

vi.mock('@/router', () => ({
  default: {
    currentRoute: {
      value: mocks.currentRouteValue,
    },
    push: mocks.routerPush,
    replace: mocks.routerReplace,
  },
}))

vi.mock('@/utils/traceId', () => ({
  getOrCreateTraceId: mocks.getOrCreateTraceId,
  generateRequestId: mocks.generateRequestId,
  getCurrentTraceId: mocks.getCurrentTraceId,
}))

vi.mock('@/utils/tenant', () => ({
  clearTenantContext: mocks.clearTenantContext,
  getActiveTenantId: mocks.getActiveTenantId,
  getLoginMode: mocks.getLoginMode,
  getTenantId: mocks.getTenantId,
  syncTenantContextFromAccessToken: mocks.syncTenantContextFromAccessToken,
}))

vi.mock('@/utils/idempotency', () => ({
  createIdempotencyHeaders: mocks.createIdempotencyHeaders,
  createIdempotencyFingerprint: mocks.createIdempotencyFingerprint,
  createSubmitIdempotencyKey: mocks.createSubmitIdempotencyKey,
}))

vi.mock('@/utils/problemParser', () => ({
  extractErrorFromAxios: mocks.extractErrorFromAxios,
  extractErrorInfo: mocks.extractErrorInfo,
}))

vi.mock('@/utils/logger', () => ({
  persistentLogger: {
    warn: mocks.persistentWarn,
  },
}))

describe('request.ts interceptors', () => {
  beforeEach(async () => {
    vi.resetModules()
    vi.clearAllMocks()
    axiosState.requestFulfilled = undefined
    axiosState.responseFulfilled = undefined
    axiosState.responseRejected = undefined
    mocks.getAccessToken.mockResolvedValue('access-token')
    mocks.ensureCsrfToken.mockResolvedValue({
      token: 'csrf-token',
      parameterName: '_csrf',
      headerName: 'X-XSRF-TOKEN',
    })
    mocks.refreshAccessTokenOnce.mockResolvedValue(false)
    mocks.getOrCreateTraceId.mockReturnValue('trace-id')
    mocks.generateRequestId.mockReturnValue('request-id')
    mocks.getCurrentTraceId.mockReturnValue('trace-current')
    mocks.getActiveTenantId.mockReturnValue('101')
    mocks.getLoginMode.mockReturnValue('TENANT')
    mocks.getTenantId.mockReturnValue('101')
    mocks.createIdempotencyHeaders.mockReturnValue({ 'X-Idempotency-Key': 'idem-key' })
    mocks.createIdempotencyFingerprint.mockImplementation((value: unknown) => JSON.stringify(value))
    mocks.createSubmitIdempotencyKey.mockReturnValue('submit-key')
    mocks.extractErrorFromAxios.mockReturnValue('conflict')
    mocks.extractErrorInfo.mockReturnValue({ code: 40903, message: 'conflict', status: 409 })
    mocks.currentRouteValue.path = '/dashboard'
    mocks.currentRouteValue.fullPath = '/dashboard?tab=security'
    mocks.currentRouteValue.query = {}

    await import('@/utils/request')
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('should use the HttpOnly session and omit bearer authorization', async () => {
    const fulfilled = axiosState.service.interceptors.request.use.mock.calls[0]?.[0] as
      | RequestFulfilled
      | undefined
    expect(fulfilled).toBeTypeOf('function')

    const config = await fulfilled!({
      headers: {} as any,
      url: '/api/secure',
      method: 'get',
    } as InternalAxiosRequestConfig)

    expect(config.headers['X-Trace-Id']).toBe('trace-id')
    expect(config.headers['X-Request-Id']).toBe('request-id')
    expect(config.headers.Authorization).toBeUndefined()
    expect(config.headers['X-Active-Tenant-Id']).toBe('101')
    expect(mocks.getAccessToken).not.toHaveBeenCalled()
    expect(mocks.syncTenantContextFromAccessToken).not.toHaveBeenCalled()
  })

  it('should not inject tenant headers for platform workflow runtime requests', async () => {
    const fulfilled = axiosState.service.interceptors.request.use.mock.calls[0]?.[0] as
      | RequestFulfilled
      | undefined
    expect(fulfilled).toBeTypeOf('function')

    mocks.getActiveTenantId.mockReturnValue('101')
    mocks.currentRouteValue.path = '/platform/process'
    mocks.currentRouteValue.fullPath = '/platform/process'
    mocks.currentRouteValue.query = {}

    const config = await fulfilled!({
      headers: {} as any,
      url: '/process/definitions',
      method: 'get',
    } as InternalAxiosRequestConfig)

    expect(config.headers['X-Active-Tenant-Id']).toBeUndefined()
  })

  it('should not inject tenant headers in platform login mode even when stale tenant remains', async () => {
    const fulfilled = axiosState.service.interceptors.request.use.mock.calls[0]?.[0] as
      | RequestFulfilled
      | undefined
    expect(fulfilled).toBeTypeOf('function')

    mocks.getLoginMode.mockReturnValue('PLATFORM')
    mocks.getActiveTenantId.mockReturnValue('101')

    const config = await fulfilled!({
      headers: {} as any,
      url: '/sys/users/current',
      method: 'get',
    } as InternalAxiosRequestConfig)

    expect(config.headers['X-Active-Tenant-Id']).toBeUndefined()
  })

  it('should inject idempotency header when config declares idempotency', async () => {
    const fulfilled = axiosState.service.interceptors.request.use.mock.calls[0]?.[0] as
      | RequestFulfilled
      | undefined
    expect(fulfilled).toBeTypeOf('function')

    const config = await fulfilled!({
      headers: {} as any,
      url: '/sys/users',
      method: 'post',
      idempotency: {
        scope: 'sys-users:create',
        payload: { username: 'alice' },
      },
    } as InternalAxiosRequestConfig)

    expect(mocks.createIdempotencyHeaders).toHaveBeenCalledWith('sys-users:create', {
      username: 'alice',
    })
    expect(config.headers['X-Idempotency-Key']).toBe('idem-key')
    expect(config.headers['X-XSRF-TOKEN']).toBe('csrf-token')
  })

  it('should reuse submit-mode key while duplicate requests are in flight and rotate after completion', async () => {
    const fulfilled = axiosState.service.interceptors.request.use.mock.calls[0]?.[0] as
      | RequestFulfilled
      | undefined
    const responseFulfilled = axiosState.service.interceptors.response.use.mock.calls[0]?.[0] as
      | ResponseFulfilled
      | undefined
    expect(fulfilled).toBeTypeOf('function')
    expect(responseFulfilled).toBeTypeOf('function')

    mocks.createSubmitIdempotencyKey
      .mockReturnValueOnce('submit-key-1')
      .mockReturnValueOnce('submit-key-2')

    const firstConfig = await fulfilled!({
      headers: {} as any,
      url: '/process/start',
      method: 'post',
      params: { processKey: 'demo' },
      idempotency: {
        scope: 'process-instance:start:demo',
        payload: { processKey: 'demo', variables: { orderId: 'A-1' } },
        mode: 'submit',
      },
    } as InternalAxiosRequestConfig)

    const secondConfig = await fulfilled!({
      headers: {} as any,
      url: '/process/start',
      method: 'post',
      params: { processKey: 'demo' },
      idempotency: {
        scope: 'process-instance:start:demo',
        payload: { processKey: 'demo', variables: { orderId: 'A-1' } },
        mode: 'submit',
      },
    } as InternalAxiosRequestConfig)

    expect(mocks.createIdempotencyHeaders).not.toHaveBeenCalled()
    expect(mocks.createSubmitIdempotencyKey).toHaveBeenCalledTimes(1)
    expect(firstConfig.headers['X-Idempotency-Key']).toBe('submit-key-1')
    expect(secondConfig.headers['X-Idempotency-Key']).toBe('submit-key-1')

    responseFulfilled!({
      status: 200,
      data: { ok: true },
      headers: {},
      config: firstConfig,
    })
    responseFulfilled!({
      status: 200,
      data: { ok: true },
      headers: {},
      config: secondConfig,
    })

    const thirdConfig = await fulfilled!({
      headers: {} as any,
      url: '/process/start',
      method: 'post',
      params: { processKey: 'demo' },
      idempotency: {
        scope: 'process-instance:start:demo',
        payload: { processKey: 'demo', variables: { orderId: 'A-1' } },
        mode: 'submit',
      },
    } as InternalAxiosRequestConfig)

    expect(mocks.createSubmitIdempotencyKey).toHaveBeenCalledTimes(2)
    expect(thirdConfig.headers['X-Idempotency-Key']).toBe('submit-key-2')
  })

  it('should rotate submit-mode key after an error response releases the in-flight entry', async () => {
    const fulfilled = axiosState.service.interceptors.request.use.mock.calls[0]?.[0] as
      | RequestFulfilled
      | undefined
    const rejected = axiosState.service.interceptors.response.use.mock.calls[0]?.[1] as
      | ResponseRejected
      | undefined
    expect(fulfilled).toBeTypeOf('function')
    expect(rejected).toBeTypeOf('function')

    mocks.createSubmitIdempotencyKey
      .mockReturnValueOnce('submit-key-1')
      .mockReturnValueOnce('submit-key-2')

    const firstConfig = await fulfilled!({
      headers: {} as any,
      url: '/scheduling/dag/3/trigger',
      method: 'post',
      params: { triggeredBy: 'alice' },
      idempotency: {
        scope: 'scheduling-dag:trigger:3',
        payload: { dagId: 3, triggeredBy: 'alice' },
        mode: 'submit',
      },
    } as InternalAxiosRequestConfig)

    const duplicateError = {
      response: {
        status: 409,
        data: {
          title: 'duplicate',
        },
        headers: {},
      },
      config: firstConfig,
      message: 'original',
    }

    await expect(rejected!(duplicateError)).rejects.toBe(duplicateError)

    const secondConfig = await fulfilled!({
      headers: {} as any,
      url: '/scheduling/dag/3/trigger',
      method: 'post',
      params: { triggeredBy: 'alice' },
      idempotency: {
        scope: 'scheduling-dag:trigger:3',
        payload: { dagId: 3, triggeredBy: 'alice' },
        mode: 'submit',
      },
    } as InternalAxiosRequestConfig)

    expect(mocks.createSubmitIdempotencyKey).toHaveBeenCalledTimes(2)
    expect(firstConfig.headers['X-Idempotency-Key']).toBe('submit-key-1')
    expect(secondConfig.headers['X-Idempotency-Key']).toBe('submit-key-2')
  })

  it('should redirect to login and clear tenant context for missing_tenant 400 errors', async () => {
    const rejected = axiosState.service.interceptors.response.use.mock.calls[0]?.[1] as
      | ResponseRejected
      | undefined
    expect(rejected).toBeTypeOf('function')

    const error = {
      response: {
        status: 400,
        data: {
          error: 'missing_tenant',
          error_description: 'tenant required',
        },
      },
      config: {
        url: '/api/secure',
      },
    }

    await expect(rejected!(error)).rejects.toBe(error)

    expect(mocks.clearTenantContext).toHaveBeenCalledTimes(1)
    expect(mocks.routerPush).toHaveBeenCalledWith({
      path: '/login',
      query: {
        redirect: '/dashboard?tab=security',
        error: 'tenant required',
      },
    })
  })

  it('should redirect to login for token_revoked 401 without silent retry', async () => {
    const rejected = axiosState.service.interceptors.response.use.mock.calls[0]?.[1] as
      | ResponseRejected
      | undefined
    expect(rejected).toBeTypeOf('function')

    const error = {
      response: {
        status: 401,
        data: {
          error: 'token_revoked',
          error_description: 'token security state is outdated',
        },
        headers: {
          'x-trace-id': 'trace-token-revoked',
        },
      },
      config: {
        url: '/sys/users/current',
      },
    }

    await expect(rejected!(error)).rejects.toBe(error)

    expect(mocks.refreshAccessTokenOnce).not.toHaveBeenCalled()
    expect(mocks.routerReplace).toHaveBeenCalledWith({
      path: '/login',
      query: {
        redirect: '/dashboard?tab=security',
        error: 'token_revoked',
        message: 'token security state is outdated',
        traceId: 'trace-token-revoked',
      },
    })
  })

  it('should route 403 errors to exception page with trace id', async () => {
    const rejected = axiosState.service.interceptors.response.use.mock.calls[0]?.[1] as
      | ResponseRejected
      | undefined
    expect(rejected).toBeTypeOf('function')

    const error = {
      response: {
        status: 403,
        data: {
          detail: 'forbidden',
        },
        headers: {
          'x-trace-id': 'trace-403',
        },
      },
      config: {
        url: '/api/admin',
      },
    }

    await expect(rejected!(error)).rejects.toBe(error)

    expect(mocks.routerPush).toHaveBeenCalledWith({
      path: '/exception/403',
      query: {
        from: '/dashboard?tab=security',
        path: '/api/admin',
        message: 'forbidden',
        traceId: 'trace-403',
      },
    })
  })

  it('should route 404 errors to exception page with current trace id fallback', async () => {
    const rejected = axiosState.service.interceptors.response.use.mock.calls[0]?.[1] as
      | ResponseRejected
      | undefined
    expect(rejected).toBeTypeOf('function')

    const error = {
      response: {
        status: 404,
        data: {
          detail: 'not found',
        },
        headers: {},
      },
      config: {
        url: '/api/missing',
      },
    }

    await expect(rejected!(error)).rejects.toBe(error)

    expect(mocks.routerPush).toHaveBeenCalledWith({
      path: '/exception/404',
      query: {
        from: '/dashboard?tab=security',
        path: '/api/missing',
        message: 'not found',
        traceId: 'trace-current',
      },
    })
  })

  it('should route 500 errors to exception page with response trace id', async () => {
    const rejected = axiosState.service.interceptors.response.use.mock.calls[0]?.[1] as
      | ResponseRejected
      | undefined
    expect(rejected).toBeTypeOf('function')

    const error = {
      response: {
        status: 500,
        data: {
          message: 'server exploded',
        },
        headers: {
          'x-trace-id': 'trace-500',
        },
      },
      config: {
        url: '/api/system',
      },
    }

    await expect(rejected!(error)).rejects.toBe(error)

    expect(mocks.routerPush).toHaveBeenCalledWith({
      path: '/exception/500',
      query: {
        from: '/dashboard?tab=security',
        path: '/api/system',
        message: 'server exploded',
        traceId: 'trace-500',
      },
    })
  })

  it('should route 503 errors to service unavailable exception page', async () => {
    const rejected = axiosState.service.interceptors.response.use.mock.calls[0]?.[1] as
      | ResponseRejected
      | undefined
    expect(rejected).toBeTypeOf('function')

    const error = {
      response: {
        status: 503,
        data: {
          error: 'workflow_engine_disabled',
          message: '当前运行环境未启用流程引擎，流程管理接口不可用',
        },
        headers: {
          'x-trace-id': 'trace-503',
        },
      },
      config: {
        url: '/process/definitions',
      },
    }

    await expect(rejected!(error)).rejects.toBe(error)

    expect(mocks.routerPush).toHaveBeenCalledWith({
      path: '/exception/503',
      query: {
        from: '/dashboard?tab=security',
        path: '/process/definitions',
        message: '当前运行环境未启用流程引擎，流程管理接口不可用',
        traceId: 'trace-503',
      },
    })
  })

  it('should enrich 409 conflicts without redirecting', async () => {
    const rejected = axiosState.service.interceptors.response.use.mock.calls[0]?.[1] as
      | ResponseRejected
      | undefined
    expect(rejected).toBeTypeOf('function')

    const error: any = {
      response: {
        status: 409,
        data: {
          title: 'conflict',
        },
        headers: {},
      },
      config: {
        url: '/api/resource',
      },
      message: 'original',
    }

    await expect(rejected!(error)).rejects.toBe(error)

    expect(mocks.extractErrorInfo).toHaveBeenCalledWith(error)
    expect(mocks.extractErrorFromAxios).toHaveBeenCalledWith(error, '操作失败')
    expect(error.message).toBe('conflict')
    expect(error.errorInfo).toEqual({ code: 40903, message: 'conflict', status: 409 })
    expect(mocks.routerPush).not.toHaveBeenCalled()
    expect(mocks.routerReplace).not.toHaveBeenCalled()
  })

  it('should debounce network errors and redirect to login once', async () => {
    vi.useFakeTimers()

    const rejected = axiosState.service.interceptors.response.use.mock.calls[0]?.[1] as
      | ResponseRejected
      | undefined
    expect(rejected).toBeTypeOf('function')

    const firstError = {
      code: 'ERR_NETWORK',
      message: 'Network Error',
      config: {
        url: '/api/first',
      },
    }
    const secondError = {
      code: 'ERR_NETWORK',
      message: 'Network Error',
      config: {
        url: '/api/second',
      },
    }

    await expect(rejected!(firstError)).rejects.toBe(firstError)
    await expect(rejected!(secondError)).rejects.toBe(secondError)

    expect(mocks.routerReplace).not.toHaveBeenCalled()

    vi.advanceTimersByTime(200)

    expect(mocks.routerReplace).toHaveBeenCalledTimes(1)
    expect(mocks.routerReplace).toHaveBeenCalledWith('/login')
  })
})
