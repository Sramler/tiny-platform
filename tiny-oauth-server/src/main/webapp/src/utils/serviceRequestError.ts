export type ServiceRequestErrorCode =
  | 'timeout'
  | 'network_error'
  | 'unauthorized'
  | 'forbidden'
  | 'server_error'
  | 'unexpected_status'
  | 'bad_response'
  | 'unknown'

export interface ServiceRequestErrorOptions {
  status?: number
  cause?: unknown
  serviceName?: string
  operation?: string
}

export class ServiceRequestError extends Error {
  readonly code: ServiceRequestErrorCode
  readonly status?: number
  readonly cause?: unknown
  readonly serviceName?: string
  readonly operation?: string

  constructor(code: ServiceRequestErrorCode, message: string, options: ServiceRequestErrorOptions = {}) {
    super(message)
    this.name = 'ServiceRequestError'
    this.code = code
    this.status = options.status
    this.cause = options.cause
    this.serviceName = options.serviceName
    this.operation = options.operation
  }
}

export interface ServiceFailureMessageOptions {
  serviceName?: string
  timeoutMessage?: string
  networkErrorMessage?: string
  unauthorizedMessage?: string
  forbiddenMessage?: string
  serverErrorMessage?: string
  badResponseMessage?: string
  unexpectedStatusMessage?: string
  unknownMessage?: string
}

export function isAbortError(error: unknown): boolean {
  return (
    (error instanceof DOMException && error.name === 'AbortError') ||
    (typeof error === 'object' &&
      error !== null &&
      'name' in error &&
      String((error as { name?: unknown }).name) === 'AbortError')
  )
}

export function classifyServiceHttpFailure(
  response: Response,
  options: ServiceRequestErrorOptions = {},
): ServiceRequestError {
  if (response.status === 401) {
    return new ServiceRequestError('unauthorized', `${options.operation || '请求'}未认证`, {
      ...options,
      status: response.status,
    })
  }
  if (response.status === 403) {
    return new ServiceRequestError('forbidden', `${options.operation || '请求'}被拒绝`, {
      ...options,
      status: response.status,
    })
  }
  if (response.status >= 500) {
    return new ServiceRequestError('server_error', `${options.operation || '请求'}服务异常: ${response.status}`, {
      ...options,
      status: response.status,
    })
  }
  return new ServiceRequestError('unexpected_status', `${options.operation || '请求'}失败: ${response.status}`, {
    ...options,
    status: response.status,
  })
}

export function classifyServiceThrownError(
  error: unknown,
  options: ServiceRequestErrorOptions & { timeoutMs?: number } = {},
): ServiceRequestError {
  if (error instanceof ServiceRequestError) {
    return error
  }
  const anyError = error as {
    code?: unknown
    message?: unknown
    name?: unknown
    error?: unknown
    status?: unknown
    response?: { status?: unknown }
  }
  const text = [
    anyError?.code,
    anyError?.error,
    anyError?.name,
    anyError?.message,
    error,
  ]
    .filter((value) => value != null)
    .map((value) => String(value))
    .join(' ')
    .toLowerCase()
  const status =
    typeof anyError?.status === 'number'
      ? anyError.status
      : typeof anyError?.response?.status === 'number'
        ? anyError.response.status
        : undefined

  if (status === 401) {
    return new ServiceRequestError('unauthorized', `${options.operation || '请求'}未认证`, {
      ...options,
      status,
      cause: error,
    })
  }
  if (status === 403) {
    return new ServiceRequestError('forbidden', `${options.operation || '请求'}被拒绝`, {
      ...options,
      status,
      cause: error,
    })
  }
  if (status && status >= 500) {
    return new ServiceRequestError('server_error', `${options.operation || '请求'}服务异常: ${status}`, {
      ...options,
      status,
      cause: error,
    })
  }
  if (
    isAbortError(error) ||
    text.includes('timeout') ||
    text.includes('timed out') ||
    text.includes('econnaborted') ||
    text.includes('etimedout')
  ) {
    const suffix = options.timeoutMs ? `（${options.timeoutMs}ms）` : ''
    return new ServiceRequestError('timeout', `${options.operation || '请求'}超时${suffix}`, {
      ...options,
      cause: error,
    })
  }
  if (
    text.includes('failed to fetch') ||
    text.includes('network') ||
    text.includes('load failed') ||
    text.includes('err_connection_refused') ||
    text.includes('connection refused') ||
    text.includes('err_network') ||
    text.includes('econnrefused') ||
    text.includes('enotfound')
  ) {
    return new ServiceRequestError('network_error', `${options.serviceName || '服务'}暂不可用`, {
      ...options,
      cause: error,
    })
  }
  return new ServiceRequestError('unknown', `${options.operation || '请求'}失败`, {
    ...options,
    cause: error,
  })
}

export function getServiceFailureMessage(
  error: unknown,
  options: ServiceFailureMessageOptions = {},
): string {
  const serviceName = options.serviceName || '服务'
  const requestError =
    error instanceof ServiceRequestError
      ? error
      : classifyServiceThrownError(error, { serviceName })

  switch (requestError.code) {
    case 'timeout':
      return options.timeoutMessage || `${serviceName}响应超时，请稍后重试`
    case 'network_error':
      return options.networkErrorMessage || `${serviceName}暂不可用，请确认服务已启动后重试`
    case 'unauthorized':
      return options.unauthorizedMessage || `${serviceName}认证已失效，请刷新页面后重试`
    case 'forbidden':
      return options.forbiddenMessage || `${serviceName}拒绝当前请求，请确认访问权限或系统配置`
    case 'server_error':
      return options.serverErrorMessage || `${serviceName}异常，请稍后重试或联系管理员`
    case 'bad_response':
      return options.badResponseMessage || `${serviceName}返回异常，请稍后重试`
    case 'unexpected_status':
      return options.unexpectedStatusMessage || `${serviceName}请求失败，请稍后重试`
    default:
      return options.unknownMessage || `${serviceName}请求失败，请稍后重试`
  }
}
