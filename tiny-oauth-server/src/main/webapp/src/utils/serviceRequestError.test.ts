import { describe, expect, it } from 'vitest'

import {
  ServiceRequestError,
  classifyServiceHttpFailure,
  classifyServiceThrownError,
  getServiceFailureMessage,
} from '@/utils/serviceRequestError'

describe('serviceRequestError utils', () => {
  it.each([
    [401, 'unauthorized'],
    [403, 'forbidden'],
    [500, 'server_error'],
    [503, 'server_error'],
    [418, 'unexpected_status'],
  ])('should classify http status %s as %s', (status, code) => {
    const error = classifyServiceHttpFailure(new Response('', { status }), {
      serviceName: '认证服务',
      operation: 'OIDC discovery',
    })

    expect(error).toMatchObject({ code, status })
  })

  it('should classify network and timeout failures', () => {
    expect(classifyServiceThrownError(new TypeError('Failed to fetch')).code).toBe('network_error')
    expect(classifyServiceThrownError(new DOMException('Aborted', 'AbortError')).code).toBe('timeout')
    expect(classifyServiceThrownError(new Error('request timed out')).code).toBe('timeout')
  })

  it.each([
    [{ response: { status: 401 } }, 'unauthorized'],
    [{ response: { status: 403 } }, 'forbidden'],
    [{ response: { status: 503 } }, 'server_error'],
    [{ code: 'ECONNABORTED', message: 'timeout of 5000ms exceeded' }, 'timeout'],
    [{ code: 'ERR_NETWORK', message: 'Network Error' }, 'network_error'],
  ])('should classify axios-like failure %o as %s', (failure, code) => {
    expect(classifyServiceThrownError(failure).code).toBe(code)
  })

  it('should map service failures to user-facing messages', () => {
    expect(
      getServiceFailureMessage(new ServiceRequestError('network_error', 'network'), {
        serviceName: '认证服务',
      }),
    ).toContain('认证服务暂不可用')
    expect(
      getServiceFailureMessage(new ServiceRequestError('server_error', '500'), {
        serviceName: '菜单服务',
      }),
    ).toContain('菜单服务异常')
  })
})
