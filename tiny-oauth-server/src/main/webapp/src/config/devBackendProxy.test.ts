import { describe, expect, it } from 'vitest'

import {
  DEV_BACKEND_PROXY_PATHS,
  DEV_SPA_BACKEND_COLLISIONS,
  isDevBackendProxyPath,
  resolveDevBackendProxyBypass,
} from './devBackendProxy'

describe('Vite 同源后端代理闭包', () => {
  it.each([
    '/sys/users/current',
    '/self/security/status',
    '/platform/users',
    '/dict/types',
    '/demo/export-usage',
    '/metrics/idempotent',
    '/scheduling/task/list',
    '/workflow/runtime',
    '/process/models',
    '/export/task',
    '/idempotent/status',
    '/auth/logout',
    '/csrf',
    '/oauth2/authorize',
    '/connect/logout',
    '/.well-known/openid-configuration',
  ])('代理真实后端路径 %s', (pathname) => {
    expect(isDevBackendProxyPath(pathname)).toBe(true)
  })

  it.each(['/system/resource', '/self/security/totp-bind', '/self/security/totp-verify'])(
    '保留 Vue 页面路径 %s',
    (pathname) => {
      expect(isDevBackendProxyPath(pathname)).toBe(false)
    },
  )

  it('不引入 /api 伪前缀', () => {
    expect(DEV_BACKEND_PROXY_PATHS).not.toContain('/api')
    expect(isDevBackendProxyPath('/api/sys/users/current')).toBe(false)
  })

  it.each(['/system/resource', '/platformish/users', '/dictionary/types', '/csrf-token'])(
    '代理根路径必须按 segment 边界匹配 %s',
    (pathname) => {
      expect(isDevBackendProxyPath(pathname)).toBe(false)
    },
  )

  it('显式维护四组 SPA/后端路径冲突', () => {
    expect(DEV_SPA_BACKEND_COLLISIONS.map(({ backendRoot }) => backendRoot)).toEqual([
      '/platform',
      '/scheduling',
      '/process',
      '/export',
    ])
  })

  it.each([
    '/platform/users',
    '/platform/tenants/42?from=%2Fsystem%2Ftenant',
    '/platform/dicts/overrides',
    '/platform/process/definition',
    '/scheduling/dag/detail?id=7',
    '/process/modeling',
    '/export/task',
    '/export/testData',
  ])('文档导航保留 SPA 深链 %s', (requestUrl) => {
    expect(
      resolveDevBackendProxyBypass(requestUrl, 'GET', {
        accept: 'text/html,application/xhtml+xml',
        'sec-fetch-dest': 'document',
        'sec-fetch-mode': 'navigate',
      }),
    ).toBe('/index.html')
  })

  it.each([
    '/platform/users',
    '/platform/roles/options',
    '/dict/types',
    '/demo/export-usage',
    '/metrics/idempotent',
    '/scheduling/dag',
    '/process/definition',
    '/export/task',
  ])('fetch/API 请求继续代理后端 %s', (requestUrl) => {
    expect(
      resolveDevBackendProxyBypass(requestUrl, 'GET', {
        accept: 'application/json',
        'sec-fetch-dest': 'empty',
        'sec-fetch-mode': 'cors',
      }),
    ).toBeUndefined()
  })

  it.each([
    '/export/task/task-1/download',
    '/process/definition/process-1/xml',
    '/platform/tenants/42/users',
    '/scheduling/task-instance/9/log',
  ])('下载或 API 子路径不被文档回退吞掉 %s', (requestUrl) => {
    expect(
      resolveDevBackendProxyBypass(requestUrl, 'GET', {
        accept: 'text/html,application/xhtml+xml',
        'sec-fetch-dest': 'document',
        'sec-fetch-mode': 'navigate',
      }),
    ).toBeUndefined()
  })

  it('无 Fetch Metadata 时以 HTML Accept 兼容直接深链', () => {
    expect(
      resolveDevBackendProxyBypass('/platform/users', 'GET', {
        accept: 'text/html',
      }),
    ).toBe('/index.html')
  })

  it('写请求即使 Accept HTML 也必须代理后端', () => {
    expect(
      resolveDevBackendProxyBypass('/scheduling/dag', 'POST', {
        accept: 'text/html',
        'sec-fetch-dest': 'document',
      }),
    ).toBeUndefined()
  })

  it('下载目的地不会因 HTML Accept 被误判为 SPA', () => {
    expect(
      resolveDevBackendProxyBypass('/export/task', 'GET', {
        accept: 'text/html',
        'sec-fetch-dest': 'empty',
        'sec-fetch-mode': 'navigate',
      }),
    ).toBeUndefined()
  })
})
