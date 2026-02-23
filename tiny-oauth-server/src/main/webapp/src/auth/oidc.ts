/**
 * OIDC 客户端配置
 * 所有可调项均通过环境变量注入，详见 docs/ENV_CONFIG.md。
 * 该文件在模块加载阶段完成配置校验，确保生产环境必须显式提供关键地址。
 */
import { OidcClient, UserManager, WebStorageStateStore } from 'oidc-client-ts'
import type { UserManagerSettings } from 'oidc-client-ts'
import { logger } from '@/utils/logger'
import { addTraceIdToFetchOptions } from '@/utils/traceId'
import { getTenantCode } from '@/utils/tenant'

type Env = {
  VITE_OIDC_AUTHORITY?: string
  VITE_OIDC_CLIENT_ID?: string
  VITE_OIDC_REDIRECT_URI?: string
  VITE_OIDC_POST_LOGOUT_REDIRECT_URI?: string
  VITE_OIDC_SILENT_REDIRECT_URI?: string
  VITE_OIDC_SCOPES?: string
  VITE_OIDC_STORAGE?: 'local' | 'session'
}

const env = import.meta.env as Env
const isProd = import.meta.env.PROD

/**
 * 统一解析 env，支持默认值与生产环境强制校验。
 */
const resolveEnvValue = (
  value: string | undefined,
  fallback: string,
  options: { key: keyof Env; requiredInProd?: boolean } | null = null,
): string => {
  if (value) {
    return value
  }

  const key = options?.key
  if (key) {
    const warning = `[OIDC][config] ${key} 未配置，使用默认值 ${fallback}`
    if (isProd && options?.requiredInProd) {
      logger.error(`${warning}（生产环境必须显式配置）`)
      throw new Error(warning)
    }
    logger.warn(warning)
  }

  return fallback
}

const authorityBase = resolveEnvValue(env.VITE_OIDC_AUTHORITY, 'http://localhost:9000', {
  key: 'VITE_OIDC_AUTHORITY',
  requiredInProd: true,
})
const clientId = resolveEnvValue(env.VITE_OIDC_CLIENT_ID, 'vue-client', {
  key: 'VITE_OIDC_CLIENT_ID',
  requiredInProd: true,
})
const redirectUri = resolveEnvValue(env.VITE_OIDC_REDIRECT_URI, 'http://localhost:5173/callback', {
  key: 'VITE_OIDC_REDIRECT_URI',
  requiredInProd: true,
})
const postLogoutRedirectUri = resolveEnvValue(
  env.VITE_OIDC_POST_LOGOUT_REDIRECT_URI,
  'http://localhost:5173/',
  {
    key: 'VITE_OIDC_POST_LOGOUT_REDIRECT_URI',
    requiredInProd: true,
  },
)
const silentRedirectUri = resolveEnvValue(
  env.VITE_OIDC_SILENT_REDIRECT_URI,
  'http://localhost:5173/silent-renew.html',
  {
    key: 'VITE_OIDC_SILENT_REDIRECT_URI',
    requiredInProd: true,
  },
)
const scopes = resolveEnvValue(env.VITE_OIDC_SCOPES, 'openid profile offline_access', {
  key: 'VITE_OIDC_SCOPES',
})

function resolveTenantAuthority(baseAuthority: string): string {
  const tenantCode = getTenantCode()
  if (!tenantCode) {
    return baseAuthority
  }
  try {
    const baseUrl = new URL(baseAuthority)
    const normalizedPath = baseUrl.pathname.replace(/\/+$/, '')
    if (normalizedPath.endsWith(`/${tenantCode}`)) {
      return baseUrl.toString().replace(/\/+$/, '')
    }
    baseUrl.pathname = `${normalizedPath}/${tenantCode}`
    return baseUrl.toString().replace(/\/+$/, '')
  } catch {
    const normalizedBase = baseAuthority.replace(/\/+$/, '')
    return `${normalizedBase}/${tenantCode}`
  }
}

const authority = resolveTenantAuthority(authorityBase)

/**
 * 为 oidc-client-ts 使用的 fetch 安装 TRACE_ID 支持
 *
 * 说明：
 * - oidc-client-ts 内部通过全局 fetch 访问：
 *   - /.well-known/openid-configuration
 *   - /oauth2/authorize
 *   - /connect/logout
 *   - /userinfo 等端点
 * - 这些请求不会经过我们封装的 fetchWithTraceId/axios 拦截器
 * - 这里通过包装 window.fetch，在访问 authority 域名下的 OIDC 相关路径时自动注入 X-Trace-Id / X-Request-Id
 */
function installOidcFetchWithTraceId(oidcAuthority: string): void {
  if (typeof window === 'undefined' || typeof window.fetch !== 'function') {
    return
  }

  // 避免重复安装
  const anyWindow = window as any
  if (anyWindow.__oidcTraceFetchInstalled) {
    return
  }
  anyWindow.__oidcTraceFetchInstalled = true

  const originalFetch = window.fetch.bind(window)
  const authorityUrl = new URL(oidcAuthority)

  window.fetch = (input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
    try {
      const urlString =
        typeof input === 'string' || input instanceof URL ? input.toString() : input.url

      // 仅对指向同一 authority 的请求注入 traceId，避免影响其他第三方域名
      // 例如 authority = http://localhost:9000
      const requestUrl = new URL(urlString, window.location.origin)
      const isSameAuthority = requestUrl.origin === authorityUrl.origin

      if (isSameAuthority) {
        const optionsWithTrace = addTraceIdToFetchOptions(init ?? {})
        return originalFetch(input, {
          ...optionsWithTrace,
        })
      }
    } catch (e) {
      // 失败时退回原始 fetch，避免影响正常功能
      logger.warn('[OIDC][trace] 安装 OIDC fetch traceId 包装时出错，回退到原始 fetch', e)
    }

    return originalFetch(input, init as any)
  }
}

/**
 * 根据 `VITE_OIDC_STORAGE` 选择 localStorage 或 sessionStorage。
 * SSR/单测场景下 window 不存在时自动跳过，避免构建失败。
 */
const createOidcStore = () => {
  if (typeof window === 'undefined') {
    logger.warn('[OIDC][config] window 未定义，跳过 WebStorageStateStore 初始化')
    return undefined
  }
  const storagePreference = env.VITE_OIDC_STORAGE === 'session' ? 'session' : 'local'
  const store = storagePreference === 'session' ? window.sessionStorage : window.localStorage
  logger.info(`[OIDC][config] 使用 ${storagePreference}Storage 作为 OIDC state store`)
  return new WebStorageStateStore({ store })
}

const userStore = createOidcStore()

const baseSettings: UserManagerSettings = {
  authority,
  client_id: clientId,
  redirect_uri: redirectUri,
  post_logout_redirect_uri: postLogoutRedirectUri,
  response_type: 'code',
  scope: scopes,
  // 如果后端未开启 UserInfo 端点，或 profile 信息已有 ID Token/claims，关闭该项可减少一次请求
  loadUserInfo: true,
  automaticSilentRenew: true,
  silent_redirect_uri: silentRedirectUri,
}

if (userStore) {
  baseSettings.userStore = userStore
}

// 在创建 UserManager 之前安装带 TRACE_ID 的 fetch 包装
installOidcFetchWithTraceId(authority)

export type OidcSettings = Readonly<UserManagerSettings>

export const settings: OidcSettings = Object.freeze(baseSettings)

export const userManager = new UserManager(settings)
export const oidcClient = new OidcClient(settings)
