/**
 * OIDC 客户端配置
 * 所有可调项均通过环境变量注入，详见 docs/ENV_CONFIG.md。
 * 该文件在模块加载阶段完成配置校验，确保生产环境必须显式提供关键地址。
 */
import { OidcClient, UserManager, WebStorageStateStore } from 'oidc-client-ts'
import type { UserManagerSettings, User } from 'oidc-client-ts'
import { logger } from '@/utils/logger'
import { addTraceIdToFetchOptions } from '@/utils/traceId'
import { resolveOidcAuthority } from '@/utils/tenant'

type Env = {
  VITE_OIDC_AUTHORITY?: string
  VITE_OIDC_CLIENT_ID?: string
  VITE_OIDC_REDIRECT_URI?: string
  VITE_OIDC_POST_LOGOUT_REDIRECT_URI?: string
  VITE_OIDC_SILENT_REDIRECT_URI?: string
  VITE_OIDC_SCOPES?: string
  VITE_OIDC_STORAGE?: 'local' | 'session'
}

type OidcRuntime = {
  authority: string
  settings: OidcSettings
  userManager: UserManager
  oidcClient: OidcClient
}

type UserLoadedHandler = (user: User) => void | Promise<void>
type VoidHandler = () => void | Promise<void>
type ErrorHandler = (error: Error) => void | Promise<void>

type UserManagerEventBinding =
  | { type: 'userLoaded'; handler: UserLoadedHandler }
  | { type: 'userUnloaded'; handler: VoidHandler }
  | { type: 'silentRenewError'; handler: ErrorHandler }
  | { type: 'userSignedOut'; handler: VoidHandler }
  | { type: 'accessTokenExpiring'; handler: VoidHandler }

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
      const requestUrl = new URL(urlString, window.location.origin)
      const isSameAuthority = requestUrl.origin === authorityUrl.origin

      if (isSameAuthority) {
        const optionsWithTrace = addTraceIdToFetchOptions(init ?? {})
        return originalFetch(input, {
          ...optionsWithTrace,
        })
      }
    } catch (e) {
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

function buildSettings(authority: string): UserManagerSettings {
  const nextSettings: UserManagerSettings = {
    authority,
    client_id: clientId,
    redirect_uri: redirectUri,
    post_logout_redirect_uri: postLogoutRedirectUri,
    response_type: 'code',
    scope: scopes,
    loadUserInfo: true,
    automaticSilentRenew: true,
    silent_redirect_uri: silentRedirectUri,
  }
  if (userStore) {
    nextSettings.userStore = userStore
  }
  return nextSettings
}

export type OidcSettings = Readonly<UserManagerSettings>

const eventBindings: UserManagerEventBinding[] = []

function attachBinding(runtime: OidcRuntime, binding: UserManagerEventBinding): void {
  switch (binding.type) {
    case 'userLoaded':
      runtime.userManager.events.addUserLoaded(binding.handler)
      return
    case 'userUnloaded':
      runtime.userManager.events.addUserUnloaded(binding.handler)
      return
    case 'silentRenewError':
      runtime.userManager.events.addSilentRenewError(binding.handler)
      return
    case 'userSignedOut':
      runtime.userManager.events.addUserSignedOut(binding.handler)
      return
    case 'accessTokenExpiring':
      runtime.userManager.events.addAccessTokenExpiring(binding.handler)
      return
  }
}

function detachBinding(runtime: OidcRuntime, binding: UserManagerEventBinding): void {
  switch (binding.type) {
    case 'userLoaded':
      runtime.userManager.events.removeUserLoaded(binding.handler)
      return
    case 'userUnloaded':
      runtime.userManager.events.removeUserUnloaded(binding.handler)
      return
    case 'silentRenewError':
      runtime.userManager.events.removeSilentRenewError(binding.handler)
      return
    case 'userSignedOut':
      runtime.userManager.events.removeUserSignedOut(binding.handler)
      return
    case 'accessTokenExpiring':
      runtime.userManager.events.removeAccessTokenExpiring(binding.handler)
      return
  }
}

function createRuntime(authority: string): OidcRuntime {
  const nextSettings = Object.freeze(buildSettings(authority)) as OidcSettings
  return {
    authority,
    settings: nextSettings,
    userManager: new UserManager(nextSettings),
    oidcClient: new OidcClient(nextSettings),
  }
}

function rebindEventHandlers(previousRuntime: OidcRuntime, nextRuntime: OidcRuntime): void {
  eventBindings.forEach((binding) => {
    detachBinding(previousRuntime, binding)
    attachBinding(nextRuntime, binding)
  })
}

let runtime = createRuntime(resolveOidcAuthority(authorityBase))

installOidcFetchWithTraceId(authorityBase)

function syncRuntimeIfNeeded(): OidcRuntime {
  const nextAuthority = resolveOidcAuthority(authorityBase)
  if (nextAuthority === runtime.authority) {
    return runtime
  }
  const previousRuntime = runtime
  runtime = createRuntime(nextAuthority)
  rebindEventHandlers(previousRuntime, runtime)
  logger.info('[OIDC][config] authority 已重绑', {
    previousAuthority: previousRuntime.authority,
    nextAuthority,
  })
  return runtime
}

function createRuntimeProxy<T extends object>(resolveTarget: () => T): T {
  return new Proxy({} as T, {
    get(_target, prop, _receiver) {
      const target = resolveTarget()
      const value = Reflect.get(target, prop, target)
      return typeof value === 'function' ? value.bind(target) : value
    },
    set(_target, prop, value, _receiver) {
      const target = resolveTarget()
      return Reflect.set(target, prop, value, target)
    },
    has(_target, prop) {
      return Reflect.has(resolveTarget(), prop)
    },
    ownKeys() {
      return Reflect.ownKeys(resolveTarget())
    },
    getOwnPropertyDescriptor(_target, prop) {
      return Reflect.getOwnPropertyDescriptor(resolveTarget(), prop)
    },
  })
}

export function getCurrentOidcAuthority(): string {
  return syncRuntimeIfNeeded().authority
}

export function ensureOidcAuthoritySynced(): string {
  return getCurrentOidcAuthority()
}

export function bindUserManagerEvents(handlers: {
  onUserLoaded?: UserLoadedHandler
  onUserUnloaded?: VoidHandler
  onSilentRenewError?: ErrorHandler
  onUserSignedOut?: VoidHandler
  onAccessTokenExpiring?: VoidHandler
}): () => void {
  const newBindings: UserManagerEventBinding[] = []
  if (handlers.onUserLoaded) {
    newBindings.push({ type: 'userLoaded', handler: handlers.onUserLoaded })
  }
  if (handlers.onUserUnloaded) {
    newBindings.push({ type: 'userUnloaded', handler: handlers.onUserUnloaded })
  }
  if (handlers.onSilentRenewError) {
    newBindings.push({ type: 'silentRenewError', handler: handlers.onSilentRenewError })
  }
  if (handlers.onUserSignedOut) {
    newBindings.push({ type: 'userSignedOut', handler: handlers.onUserSignedOut })
  }
  if (handlers.onAccessTokenExpiring) {
    newBindings.push({ type: 'accessTokenExpiring', handler: handlers.onAccessTokenExpiring })
  }

  newBindings.forEach((binding) => {
    eventBindings.push(binding)
    attachBinding(syncRuntimeIfNeeded(), binding)
  })

  return () => {
    const currentRuntime = syncRuntimeIfNeeded()
    newBindings.forEach((binding) => {
      const index = eventBindings.indexOf(binding)
      if (index >= 0) {
        eventBindings.splice(index, 1)
      }
      detachBinding(currentRuntime, binding)
    })
  }
}

export const settings = createRuntimeProxy<OidcSettings>(() => syncRuntimeIfNeeded().settings)
export const userManager = createRuntimeProxy<UserManager>(() => syncRuntimeIfNeeded().userManager)
export const oidcClient = createRuntimeProxy<OidcClient>(() => syncRuntimeIfNeeded().oidcClient)
