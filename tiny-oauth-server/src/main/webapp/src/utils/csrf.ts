import {
  ServiceRequestError,
  classifyServiceHttpFailure,
  classifyServiceThrownError,
  getServiceFailureMessage,
  type ServiceRequestErrorCode,
} from '@/utils/serviceRequestError'

interface CsrfPayload {
  token: string
  parameterName: string
  headerName: string
}

export type CsrfErrorCode = Exclude<ServiceRequestErrorCode, 'unknown'>

export class CsrfTokenError extends ServiceRequestError {
  declare readonly code: CsrfErrorCode

  constructor(code: CsrfErrorCode, message: string, options: { status?: number; cause?: unknown } = {}) {
    super(code, message, {
      ...options,
      serviceName: '认证服务',
      operation: '获取 CSRF token',
    })
    this.name = 'CsrfTokenError'
    Object.setPrototypeOf(this, CsrfTokenError.prototype)
  }
}

let inFlightCsrf: Promise<CsrfPayload> | null = null
const DEFAULT_CSRF_TIMEOUT_MS = 8_000

function normalizeBaseUrl(apiBaseUrl: string): string {
  return apiBaseUrl.endsWith('/') ? apiBaseUrl.slice(0, -1) : apiBaseUrl
}

export function isUnsafeHttpMethod(method?: string): boolean {
  const normalized = (method || 'GET').toUpperCase()
  return normalized !== 'GET' && normalized !== 'HEAD' && normalized !== 'OPTIONS' && normalized !== 'TRACE'
}

function toCsrfTokenError(error: ServiceRequestError): CsrfTokenError {
  const code = error.code === 'unknown' ? 'network_error' : error.code
  return new CsrfTokenError(code, error.message, {
    status: error.status,
    cause: error.cause,
  })
}

export function getCsrfFailureMessage(error: unknown): string {
  return getServiceFailureMessage(error, {
    serviceName: '认证服务',
    timeoutMessage: '认证服务响应超时，请稍后重试',
    networkErrorMessage: '认证服务暂不可用，请确认后端服务已启动后重试',
    unauthorizedMessage: '登录安全校验已失效，请刷新页面后重试',
    forbiddenMessage: '认证服务拒绝安全校验请求，请确认访问来源或登录配置',
    serverErrorMessage: '认证服务异常，请稍后重试或联系管理员',
    badResponseMessage: '认证服务返回异常，请稍后重试',
    unexpectedStatusMessage: '认证服务安全校验失败，请稍后重试',
  })
}

export async function ensureCsrfToken(
  apiBaseUrl: string,
  options: { timeoutMs?: number } = {},
): Promise<CsrfPayload> {
  if (inFlightCsrf) {
    return inFlightCsrf
  }

  const csrfUrl = `${normalizeBaseUrl(apiBaseUrl)}/csrf`
  const controller = new AbortController()
  const timeoutMs = options.timeoutMs ?? DEFAULT_CSRF_TIMEOUT_MS
  const timeoutId = globalThis.setTimeout(() => controller.abort(), timeoutMs)
  inFlightCsrf = fetch(csrfUrl, {
    method: 'GET',
    credentials: 'include',
    signal: controller.signal,
    headers: {
      Accept: 'application/json',
    },
  })
    .then(async (response) => {
      if (!response.ok) {
        throw toCsrfTokenError(
          classifyServiceHttpFailure(response, {
            serviceName: '认证服务',
            operation: '获取 CSRF token',
          }),
        )
      }
      let data: Partial<CsrfPayload>
      try {
        data = (await response.json()) as Partial<CsrfPayload>
      } catch (error) {
        throw new CsrfTokenError('bad_response', 'CSRF token 响应无法解析', { cause: error })
      }
      if (!data.token || !data.parameterName || !data.headerName) {
        throw new CsrfTokenError('bad_response', 'CSRF token 响应不完整')
      }
      return {
        token: data.token,
        parameterName: data.parameterName,
        headerName: data.headerName,
      }
    })
    .catch((error) => {
      if (error instanceof CsrfTokenError) {
        throw error
      }
      throw toCsrfTokenError(
        classifyServiceThrownError(error, {
          serviceName: '认证服务',
          operation: '获取 CSRF token',
          timeoutMs,
        }),
      )
    })
    .finally(() => {
      globalThis.clearTimeout(timeoutId)
      inFlightCsrf = null
    })

  return inFlightCsrf
}

export function clearCsrfTokenCache(): void {
  inFlightCsrf = null
}
