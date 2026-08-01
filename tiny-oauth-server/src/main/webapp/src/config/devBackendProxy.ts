/**
 * 本地 Vite 的真实后端入口白名单。
 *
 * Web 默认采用同源 BFF/HttpOnly Session，业务请求必须保留真实领域路径，禁止通过
 * `/api` 伪前缀绕过代理闭包。以 `^` 开头的条目按正则匹配，其余条目遵循 Vite 的
 * 路径前缀匹配语义。
 */
export const DEV_BACKEND_PROXY_PATHS = [
  // 严格限定后端 `/sys` 边界；字符串前缀 `/sys` 会误代理 SPA `/system/**` 文档导航。
  '^/sys(?:/|$)',
  // `/self/security/totp-bind|verify` 是 Vue 页面；其余 `/self/**` 才代理后端。
  '^/self/(?!security/totp-(?:bind|verify)(?:[/?]|$))',
  '^/platform(?:/|$)',
  '^/dict(?:/|$)',
  '^/demo(?:/|$)',
  '^/metrics(?:/|$)',
  '^/scheduling(?:/|$)',
  '^/workflow(?:/|$)',
  '^/process(?:/|$)',
  '^/export(?:/|$)',
  '^/idempotent(?:/|$)',
  '^/auth(?:/|$)',
  '^/csrf(?:/|$)',
  '^/oauth2(?:/|$)',
  '^/connect(?:/|$)',
  '^/\\.well-known(?:/|$)',
] as const

export type DevProxyRequestHeaders = Record<string, string | string[] | undefined>

/**
 * 与真实后端根路径重叠的 SPA 深链矩阵。必须限定到已知前端路由；不能把整个根路径的
 * HTML Accept 请求都回退到 index，否则 `/export/task/{id}/download` 等下载导航会被吞掉。
 */
export const DEV_SPA_BACKEND_COLLISIONS = [
  {
    backendRoot: '/platform',
    spaPath:
      /^\/platform(?:\/(?:audit(?:\/(?:authentication|authorization))?|token-debug|tenants(?:\/[^/]+)?|permissions|(?:template-)?roles|users(?:\/(?:governance|tenant-stewardship))?|role-assignment-requests|role-constraints(?:\/(?:hierarchy|mutex|prerequisite|cardinality|violations))?|dicts(?:\/(?:type|item|overrides))?|scheduling(?:\/(?:dag(?:\/(?:detail|history))?|task|task-type|history|audit))?|process(?:\/(?:modeling|deployment|definition|instance|task))?))?$/,
  },
  {
    backendRoot: '/scheduling',
    spaPath: /^\/scheduling(?:\/(?:dag(?:\/(?:detail|history))?|task|task-type|history|audit))?$/,
  },
  {
    backendRoot: '/process',
    spaPath: /^\/process(?:\/(?:modeling|deployment|definition|instance|task))?$/,
  },
  {
    backendRoot: '/export',
    spaPath: /^\/export(?:\/(?:task|testData|examples?))?$/,
  },
] as const

function readHeader(headers: DevProxyRequestHeaders, name: string): string {
  const value = headers[name] ?? headers[name.toLowerCase()]
  return Array.isArray(value) ? value.join(',') : (value ?? '')
}

export function isHtmlDocumentNavigation(
  method: string | undefined,
  headers: DevProxyRequestHeaders,
): boolean {
  const normalizedMethod = (method ?? 'GET').toUpperCase()
  if (normalizedMethod !== 'GET' && normalizedMethod !== 'HEAD') {
    return false
  }

  const destination = readHeader(headers, 'sec-fetch-dest').trim().toLowerCase()
  if (destination) {
    // `empty` 是 fetch/XHR 和下载常见值，不能仅凭 Accept: text/html 误判为 SPA 文档。
    return destination === 'document' || destination === 'iframe'
  }

  const fetchMode = readHeader(headers, 'sec-fetch-mode').trim().toLowerCase()
  if (fetchMode && fetchMode !== 'navigate') {
    return false
  }

  const accept = readHeader(headers, 'accept').toLowerCase()
  return accept.includes('text/html') || accept.includes('application/xhtml+xml')
}

export function shouldServeSpaIndexFromDevProxy(
  requestUrl: string | undefined,
  method: string | undefined,
  headers: DevProxyRequestHeaders,
): boolean {
  if (!isHtmlDocumentNavigation(method, headers)) {
    return false
  }
  const pathname = new URL(requestUrl ?? '/', 'http://vite.local').pathname
  return DEV_SPA_BACKEND_COLLISIONS.some(({ spaPath }) => spaPath.test(pathname))
}

export function resolveDevBackendProxyBypass(
  requestUrl: string | undefined,
  method: string | undefined,
  headers: DevProxyRequestHeaders,
): string | undefined {
  return shouldServeSpaIndexFromDevProxy(requestUrl, method, headers) ? '/index.html' : undefined
}

export function isDevBackendProxyPath(pathname: string): boolean {
  return DEV_BACKEND_PROXY_PATHS.some((pattern) =>
    pattern.startsWith('^') ? new RegExp(pattern).test(pathname) : pathname.startsWith(pattern),
  )
}
