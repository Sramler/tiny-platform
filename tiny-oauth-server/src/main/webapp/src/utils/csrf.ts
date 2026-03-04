interface CsrfPayload {
  token: string
  parameterName: string
  headerName: string
}

let cachedCsrf: CsrfPayload | null = null
let inFlightCsrf: Promise<CsrfPayload> | null = null

function normalizeBaseUrl(apiBaseUrl: string): string {
  return apiBaseUrl.endsWith('/') ? apiBaseUrl.slice(0, -1) : apiBaseUrl
}

export function isUnsafeHttpMethod(method?: string): boolean {
  const normalized = (method || 'GET').toUpperCase()
  return normalized !== 'GET' && normalized !== 'HEAD' && normalized !== 'OPTIONS' && normalized !== 'TRACE'
}

export async function ensureCsrfToken(apiBaseUrl: string): Promise<CsrfPayload> {
  if (cachedCsrf) {
    return cachedCsrf
  }
  if (inFlightCsrf) {
    return inFlightCsrf
  }

  const csrfUrl = `${normalizeBaseUrl(apiBaseUrl)}/csrf`
  inFlightCsrf = fetch(csrfUrl, {
    method: 'GET',
    credentials: 'include',
    headers: {
      Accept: 'application/json',
    },
  })
    .then(async (response) => {
      if (!response.ok) {
        throw new Error(`获取 CSRF token 失败: ${response.status}`)
      }
      const data = (await response.json()) as Partial<CsrfPayload>
      if (!data.token || !data.parameterName || !data.headerName) {
        throw new Error('CSRF token 响应不完整')
      }
      cachedCsrf = {
        token: data.token,
        parameterName: data.parameterName,
        headerName: data.headerName,
      }
      return cachedCsrf
    })
    .finally(() => {
      inFlightCsrf = null
    })

  return inFlightCsrf
}

export function clearCsrfTokenCache(): void {
  cachedCsrf = null
  inFlightCsrf = null
}
