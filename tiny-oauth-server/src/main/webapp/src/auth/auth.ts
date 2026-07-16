import { ref, computed } from 'vue'
import type { Ref, ComputedRef } from 'vue'
import {
  bindUserManagerEvents,
  ensureOidcAuthoritySynced,
  oidcClient,
  settings,
  userManager,
} from './oidc'
import { authRuntimeConfig } from './config'
import type { User } from 'oidc-client-ts'
import { jwtVerify, createRemoteJWKSet } from 'jose'
import { logger, persistentLogger } from '@/utils/logger'
import { sanitizeInternalRedirect } from '@/utils/redirect'
import { addTraceIdToFetchOptions, clearTraceId, createNewTraceId } from '@/utils/traceId'
import {
  clearActiveTenantId,
  getTenantCode,
  getActiveTenantId,
  syncTenantContextFromAccessToken,
  syncTenantContextFromClaims,
} from '@/utils/tenant'
import { dispatchAuthorizationRuntimeReset } from '@/runtime/authorizationRuntimeEvents'
import { ensureCsrfToken, isUnsafeHttpMethod } from '@/utils/csrf'
import { setSessionClaimsSnapshot } from '@/utils/jwt'

export type ActiveScopePostSwitchRenewResult = { ok: true; user: User } | { ok: false }
export type SilentLoginErrorCode =
  | 'login_required'
  | 'interaction_required'
  | 'consent_required'
  | 'timeout'
  | 'network_error'
  | 'invalid_state'
  | 'cookie_blocked'
  | 'unknown'

export type PlatformSessionSilentLoginResult =
  | { ok: true; user: User }
  | { ok: false; errorCode: SilentLoginErrorCode; message: string }

const OIDC_TRACE_ENABLED =
  import.meta.env.VITE_ENABLE_OIDC_TRACE === 'true' || !import.meta.env.PROD
const oidcTrace = (step: string, payload?: unknown) => {
  if (!OIDC_TRACE_ENABLED) return
  if (payload !== undefined) {
    persistentLogger.debug(`[OIDC][${step}]`, payload)
  } else {
    persistentLogger.debug(`[OIDC][${step}]`)
  }
}

/**
 * 企业级前后端分离实践：
 *
 * - 访问 `/.well-known/openid-configuration` 的职责交给 OIDC 客户端库（oidc-client-ts）
 * - 业务代码不再硬编码 discovery 地址（如 http://localhost:9000/.well-known/openid-configuration）
 * - 如需在前端做 JWT 校验，仅作为调试/审计用途，且复用 OIDC 客户端内部的 metadata / jwks_uri
 *
 * 说明：
 * - `userManager.metadataService.getMetadata()` 会根据 authority 加载并缓存 discovery 文档
 * - 这样既避免了多余的一次 `fetch /.well-known/openid-configuration`，又不破坏企业级职责边界
 */
let jwks: ReturnType<typeof createRemoteJWKSet> | null = null
let jwksAuthority: string | null = null

async function getJWKS() {
  const oidcAuthority = ensureOidcAuthoritySynced()
  if (jwks && jwksAuthority === oidcAuthority) {
    return jwks
  }

  try {
    const metadata = await userManager.metadataService.getMetadata()
    if (!metadata.jwks_uri) {
      logger.warn('[OIDC] discovery 文档中未找到 jwks_uri，跳过前端 JWT 校验')
      throw new Error('jwks_uri not found in metadata')
    }

    jwks = createRemoteJWKSet(new URL(metadata.jwks_uri))
    jwksAuthority = oidcAuthority
    oidcTrace('jwks.initialized', { jwks_uri: metadata.jwks_uri })
    return jwks
  } catch (error) {
    logger.error('[OIDC] 获取 JWKS 失败，跳过前端 JWT 校验', error)
    throw error
  }
}

export async function verifyAccessToken(token: string | null | undefined) {
  if (!token) {
    return null
  }

  try {
    const JWKS = await getJWKS()
    const { payload, protectedHeader } = await jwtVerify(token, JWKS, {
      algorithms: ['RS256'],
    })
    persistentLogger.debug('[OIDC] JWT header', protectedHeader)
    persistentLogger.debug('[OIDC] JWT payload', payload)
    return payload
  } catch (err) {
    // 注意：前端 JWT 验证仅用于调试，不影响实际认证与授权流程
    logger.error('[OIDC] JWT 验证失败（前端调试用，不影响正常流程）', err)
    return null
  }
}

export interface AuthContext {
  user: Ref<User | null>
  isAuthenticated: ComputedRef<boolean>
  login: (returnUrl?: string) => Promise<void>
  logout: () => Promise<void>
  getAccessToken: () => Promise<string | null>
  fetchWithAuth: (url: string, options?: RequestInit) => Promise<Response>
}

const user = ref<User | null>(null)
const isAuthenticated = computed(() => !!user.value && !user.value.expired)

// 防重复重定向标志
let loginInProgress = false
let lastLoginAttempt = 0
const LOGIN_COOLDOWN = 2000 // 2秒冷却时间
const POST_LOGOUT_REDIRECT_MARKER_KEY = 'tiny-platform:oidc:post-logout-redirect'
const POST_LOGOUT_REDIRECT_MARKER_TTL_MS = 30_000

type PostLogoutRedirectMarker = {
  nonce: string
  createdAt: number
}

type PostLogoutUserState = {
  postLogoutNonce?: unknown
}

const getSessionStorage = (): Storage | null => {
  try {
    return typeof window === 'undefined' ? null : window.sessionStorage
  } catch {
    return null
  }
}

const normalizeApiBaseUrl = (): string => {
  const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || ''
  return apiBaseUrl.endsWith('/') ? apiBaseUrl.slice(0, -1) : apiBaseUrl
}

const generateLogoutNonce = (): string => {
  try {
    if (typeof window !== 'undefined' && window.crypto?.randomUUID) {
      return window.crypto.randomUUID()
    }
  } catch {
    // ignore and use the deterministic fallback shape below
  }
  return `logout-${Date.now()}-${Math.random().toString(36).slice(2)}`
}

const readPostLogoutRedirectMarker = (): PostLogoutRedirectMarker | null => {
  const storage = getSessionStorage()
  if (!storage) return null

  const raw = storage.getItem(POST_LOGOUT_REDIRECT_MARKER_KEY)
  if (!raw) return null

  try {
    const parsed = JSON.parse(raw) as Partial<PostLogoutRedirectMarker> | number
    if (typeof parsed === 'object' && parsed !== null) {
      if (typeof parsed.nonce !== 'string' || typeof parsed.createdAt !== 'number') {
        return null
      }
      return {
        nonce: parsed.nonce,
        createdAt: parsed.createdAt,
      }
    }
  } catch {
    // fall through to legacy timestamp parsing
  }

  const legacyTimestamp = Number(raw)
  if (!Number.isFinite(legacyTimestamp)) return null
  return {
    nonce: '',
    createdAt: legacyTimestamp,
  }
}

const removePostLogoutRedirectMarker = (): void => {
  getSessionStorage()?.removeItem(POST_LOGOUT_REDIRECT_MARKER_KEY)
}

const isPostLogoutMarkerFresh = (marker: PostLogoutRedirectMarker): boolean =>
  Date.now() - marker.createdAt <= POST_LOGOUT_REDIRECT_MARKER_TTL_MS

const markPostLogoutRedirect = (): string => {
  const storage = getSessionStorage()
  const nonce = generateLogoutNonce()
  if (!storage) return nonce
  const marker: PostLogoutRedirectMarker = {
    nonce,
    createdAt: Date.now(),
  }
  storage.setItem(POST_LOGOUT_REDIRECT_MARKER_KEY, JSON.stringify(marker))
  return nonce
}

export const consumePostLogoutRedirectMarker = (): boolean => {
  const marker = readPostLogoutRedirectMarker()
  if (!marker) return false

  removePostLogoutRedirectMarker()
  return isPostLogoutMarkerFresh(marker)
}

const isMatchingPostLogoutUserState = (
  userState: unknown,
  marker: PostLogoutRedirectMarker,
): boolean => {
  if (!marker.nonce || typeof userState !== 'object' || userState === null) {
    return false
  }
  return (userState as PostLogoutUserState).postLogoutNonce === marker.nonce
}

export const completePostLogoutRedirect = async (url = window.location.href): Promise<boolean> => {
  const marker = readPostLogoutRedirectMarker()
  if (!marker) return false

  removePostLogoutRedirectMarker()
  if (!isPostLogoutMarkerFresh(marker)) {
    persistentLogger.warn('[OIDC] 忽略过期的退出回跳标记')
    return false
  }

  const callbackUrl = new URL(url, window.location.origin)
  if (!callbackUrl.searchParams.has('state')) {
    persistentLogger.warn('[OIDC] 退出回跳未携带 state，按本地退出标记完成兜底处理')
    return true
  }

  try {
    const response = await userManager.signoutRedirectCallback(url)
    if (isMatchingPostLogoutUserState(response.userState, marker)) {
      oidcTrace('logout.callback.validated')
      return true
    }

    persistentLogger.warn('[OIDC] 退出回跳 state 校验未通过，已停止自动授权并回登录页')
    return true
  } catch (error) {
    persistentLogger.warn('[OIDC] 退出回跳 state 处理失败，已停止自动授权并回登录页', error)
    return true
  }
}

const performServerLogoutFallback = async (): Promise<void> => {
  try {
    const csrf = await ensureCsrfToken(normalizeApiBaseUrl())
    const response = await fetch(
      `${normalizeApiBaseUrl()}/auth/logout`,
      addTraceIdToFetchOptions({
        method: 'POST',
        credentials: 'include',
        redirect: 'manual',
        headers: {
          Accept: 'application/json',
          [csrf.headerName]: csrf.token,
        },
      }),
    )

    if (!response.ok && response.status !== 0) {
      logger.warn(`[OIDC] 服务端 logout fallback 返回非成功状态: ${response.status}`)
    }
  } catch (error) {
    logger.warn('[OIDC] 服务端 logout fallback 调用失败，继续执行本地退出跳转', error)
  }
}

const appendTenantHeader = (headers: Headers): void => {
  const activeTenantId = getActiveTenantId()
  if (activeTenantId) {
    headers.set('X-Active-Tenant-Id', activeTenantId)
  }
}

type SessionUserSnapshot = Record<string, unknown> & {
  id?: string
  username?: string
  permissions?: string[]
  authorities?: string[]
  roleCodes?: string[]
}

async function restoreUserFromHttpSession(): Promise<User | null> {
  const response = await fetch(`${normalizeApiBaseUrl()}/sys/users/current`, {
    method: 'GET',
    credentials: 'include',
    headers: { Accept: 'application/json' },
  })
  if (response.status === 401 || response.status === 403) return null
  if (!response.ok) throw new Error(`session bootstrap failed with status ${response.status}`)
  const snapshot = (await response.json()) as SessionUserSnapshot
  setSessionClaimsSnapshot(snapshot)
  syncTenantContextFromClaims(snapshot as any)
  return {
    profile: snapshot,
    access_token: '',
    token_type: 'Session',
    scope: '',
    state: null,
    expired: false,
  } as unknown as User
}

// 顶层定义，避免 useAuth() 调用循环引用
export const login = async (returnUrl?: string) => {
  const now = Date.now()
  const redirectPath = sanitizeInternalRedirect(
    returnUrl || `${window.location.pathname}${window.location.search}`,
  )
  oidcTrace('login.invoke', { href: window.location.href, redirectPath })

  // 防止重复重定向 - 检查冷却时间
  if (loginInProgress || now - lastLoginAttempt < LOGIN_COOLDOWN) {
    oidcTrace('login.skip', { reason: 'in-progress or cooldown' })
    return
  }

  // 检查是否已经在 OIDC 流程中
  const currentUser = await userManager.getUser()
  if (currentUser && !currentUser.expired) {
    oidcTrace('login.skip', { reason: 'already authenticated' })
    user.value = currentUser
    return
  }

  // 检查 URL 参数，避免重复重定向
  const urlParams = new URLSearchParams(window.location.search)
  if (urlParams.has('code') || urlParams.has('error')) {
    oidcTrace('login.skip', { reason: 'callback in url' })
    return
  }

  const tenantCode = getTenantCode()
  if (!tenantCode) {
    oidcTrace('login.skip', { reason: 'missing tenantCode before authorize redirect' })
    throw new Error('missing tenant context')
  }

  try {
    const oidcAuthority = ensureOidcAuthoritySynced()
    // 登录链路使用新的 traceId，避免复用上一个会话/失败流程的 traceId
    const traceId = createNewTraceId()
    oidcTrace('login.redirect', {
      authority: oidcAuthority,
      redirect_uri: settings.redirect_uri,
      trace_id: traceId,
      redirectPath,
    })
    loginInProgress = true
    lastLoginAttempt = now

    const signinRequest = await oidcClient.createSigninRequest({
      state: {
        returnUrl: redirectPath,
        trace_id: traceId,
      },
      extraQueryParams: {
        trace_id: traceId,
      },
    })

    const authorizeUrl = signinRequest.url
    if (!authorizeUrl) {
      throw new Error('failed to create authorize url')
    }
    window.location.assign(authorizeUrl)
  } catch (error) {
    logger.error('[OIDC] 登录重定向失败', error)
    oidcTrace('login.error', error)
    loginInProgress = false
    throw error
  }
}

export const logout = async () => {
  const postLogoutRedirect = settings.post_logout_redirect_uri ?? window.location.origin
  let localStateCleared = false

  const clearLocalLogoutState = async () => {
    if (localStateCleared) return
    await userManager.removeUser()
    user.value = null
    setSessionClaimsSnapshot(null)
    clearActiveTenantId()
    dispatchAuthorizationRuntimeReset('logout', { message: '用户退出登录，清理授权运行态' })
    loginInProgress = false
    localStateCleared = true
  }

  try {
    ensureOidcAuthoritySynced()
    const currentUser = await userManager.getUser()
    if (currentUser && currentUser.id_token) {
      // 为注销流程单独创建新的 traceId，避免跨会话复用
      const traceId = createNewTraceId()
      const postLogoutNonce = markPostLogoutRedirect()
      await clearLocalLogoutState()
      clearTraceId()
      await userManager.signoutRedirect({
        id_token_hint: currentUser.id_token,
        // post_logout_redirect_uri 必须与后端注册值完全一致，禁止追加 query
        post_logout_redirect_uri: postLogoutRedirect,
        state: {
          postLogoutNonce,
          trace_id: traceId,
        },
        // 将 trace_id 作为额外查询参数传给注销端点，后端过滤器会读取
        extraQueryParams: {
          trace_id: traceId,
        },
      })
      return
    }
  } catch (error) {
    logger.error('[OIDC] 注销重定向失败，使用本地回退逻辑', error)
  }

  markPostLogoutRedirect()
  await clearLocalLogoutState()
  await performServerLogoutFallback()
  clearTraceId()
  // 本地回退：使用与后端注册值一致的固定跳转地址，避免 OIDC 校验失败
  window.location.href = postLogoutRedirect
}

let renewInProgress = false

type SigninSilentOptions = {
  /**
   * 为 true 时：renew 异常不触发强制登出跳转（用于 active-scope 写后的受控 refresh，
   * 由调用方展示提示并决定是否引导重新登录）。
   */
  suppressForceLogoutOnError?: boolean
}

async function signinSilentAndSyncUser(options?: SigninSilentOptions): Promise<User | null> {
  try {
    ensureOidcAuthoritySynced()
    const renewed = await userManager.signinSilent()
    if (!renewed) {
      user.value = null
      return null
    }
    user.value = renewed
    syncTenantContextFromClaims(renewed.profile as Record<string, unknown>)
    syncTenantContextFromAccessToken(renewed.access_token)
    oidcTrace('silentRenew.success', {
      hasRefreshToken: !!renewed?.refresh_token,
      scope: renewed?.scope,
      expires_at: renewed?.expires_at,
    })
    return renewed
  } catch (e) {
    logger.error('[OIDC] Silent renew 失败', e)
    oidcTrace('silentRenew.error', e)
    if (!options?.suppressForceLogoutOnError && authRuntimeConfig.forceLogoutOnRenewFail) {
      await userManager.removeUser()
      user.value = null
      clearTraceId()
      dispatchAuthorizationRuntimeReset('renew_failed', { message: '令牌续期失败，清理授权运行态' })
      loginInProgress = false // 重置登录状态
      window.location.href = '/login'
    }
    return null
  }
}

async function safeSilentRenew() {
  if (renewInProgress) return null
  renewInProgress = true
  try {
    return await signinSilentAndSyncUser()
  } finally {
    renewInProgress = false
  }
}

export async function refreshAccessTokenOnce(): Promise<boolean> {
  if (renewInProgress) return false
  renewInProgress = true
  try {
    const renewed = await signinSilentAndSyncUser({ suppressForceLogoutOnError: true })
    return !!renewed && !renewed.expired
  } finally {
    renewInProgress = false
  }
}

function classifySilentLoginError(error: unknown): PlatformSessionSilentLoginResult {
  const anyError = error as {
    error?: unknown
    error_description?: unknown
    message?: unknown
    name?: unknown
  }
  const raw = String(anyError?.error || anyError?.name || anyError?.message || error || '')
  const description = String(anyError?.error_description || anyError?.message || '')
  const text = `${raw} ${description}`.toLowerCase()

  if (text.includes('login_required')) {
    return { ok: false, errorCode: 'login_required', message: '登录会话已失效' }
  }
  if (text.includes('interaction_required')) {
    return { ok: false, errorCode: 'interaction_required', message: '需要用户交互完成登录' }
  }
  if (text.includes('consent_required')) {
    return { ok: false, errorCode: 'consent_required', message: '需要重新授权' }
  }
  if (text.includes('timeout') || text.includes('timed out')) {
    return { ok: false, errorCode: 'timeout', message: '静默恢复登录态超时' }
  }
  if (text.includes('state')) {
    return { ok: false, errorCode: 'invalid_state', message: '登录状态校验失败' }
  }
  if (text.includes('cookie') || text.includes('iframe')) {
    return { ok: false, errorCode: 'cookie_blocked', message: '浏览器限制导致静默登录不可用' }
  }
  if (text.includes('network') || text.includes('failed to fetch')) {
    return { ok: false, errorCode: 'network_error', message: '网络异常，无法恢复登录态' }
  }
  return { ok: false, errorCode: 'unknown', message: '静默恢复登录态失败' }
}

/**
 * 在 `POST /sys/users/current/active-scope` 返回 `tokenRefreshRequired: true` 后调用：
 * 通过 OIDC silent renew 获取与 Session 新作用域一致的 access token，并同步 `user` 与租户上下文。
 * 失败时不触发强制跳转登录页（与 {@link safeSilentRenew} 区分），由 UI 提示用户重新获取登录态。
 */
export async function refreshTokenAfterActiveScopeSwitch(): Promise<ActiveScopePostSwitchRenewResult> {
  if (renewInProgress) {
    oidcTrace('activeScopePostSwitchRenew.skip', { reason: 'renew_in_progress' })
    persistentLogger.warn('[Auth] active-scope 后续 renew 跳过：已有 renew 进行中')
    return { ok: false }
  }
  renewInProgress = true
  try {
    const renewed = await signinSilentAndSyncUser({ suppressForceLogoutOnError: true })
    if (renewed && !renewed.expired) {
      oidcTrace('activeScopePostSwitchRenew.success', {
        expires_at: renewed.expires_at,
        hasRefreshToken: !!renewed.refresh_token,
      })
      return { ok: true, user: renewed }
    }
    oidcTrace('activeScopePostSwitchRenew.miss', {})
    persistentLogger.warn('[Auth] active-scope 后续 silent renew 未获得有效访问令牌')
    return { ok: false }
  } finally {
    renewInProgress = false
  }
}

// 初始化恢复用户状态
/**
 * 平台 Session 登录（无本地 tenantCode、仅有 JSESSIONID）后，前端尚无 OIDC User。
 * 在访问需登录路由前调用：尝试 {@link UserManager.signinSilent}，利用授权服务器上已有会话换取 token，
 * 避免路由守卫误跳 `/login?redirect=/`（典型：平台账号 → totp-bind → 跳过 → 回首页）。
 */
export async function trySilentLoginFromPlatformSession(): Promise<boolean> {
  const result = await trySilentLoginFromPlatformSessionDetailed()
  return result.ok
}

export async function trySilentLoginFromPlatformSessionDetailed(): Promise<PlatformSessionSilentLoginResult> {
  if (getTenantCode()) {
    return {
      ok: false,
      errorCode: 'unknown',
      message: '当前存在租户上下文，不执行平台 Session 桥接',
    }
  }
  try {
    ensureOidcAuthoritySynced()
    const renewed = await userManager.signinSilent()
    if (renewed && !renewed.expired) {
      user.value = renewed
      syncTenantContextFromClaims(renewed.profile as Record<string, unknown>)
      syncTenantContextFromAccessToken(renewed.access_token)
      oidcTrace('trySilentLoginFromPlatformSession.success', {
        hasRefreshToken: !!renewed.refresh_token,
        expires_at: renewed.expires_at,
      })
      return { ok: true, user: renewed }
    }
  } catch (error) {
    oidcTrace('trySilentLoginFromPlatformSession.miss', { message: String(error) })
    return classifySilentLoginError(error)
  }
  return { ok: false, errorCode: 'login_required', message: '没有可用的平台登录会话' }
}

export async function initAuth() {
  try {
    if (authRuntimeConfig.sessionOnly) {
      user.value = await restoreUserFromHttpSession()
      if (!user.value) {
        setSessionClaimsSnapshot(null)
        clearActiveTenantId()
      }
      oidcTrace('initAuth.session', { authenticated: !!user.value })
      return
    }
    ensureOidcAuthoritySynced()
    oidcTrace('initAuth.start')

    // 检查是否在 OIDC 回调中
    const urlParams = new URLSearchParams(window.location.search)
    if (urlParams.has('code') || urlParams.has('error')) {
      oidcTrace('initAuth.skip', { reason: 'callback detected' })
      return
    }

    const u = await userManager.getUser()
    if (u && !u.expired) {
      user.value = u
      syncTenantContextFromClaims(u.profile as Record<string, unknown>)
      syncTenantContextFromAccessToken(u.access_token)
      oidcTrace('initAuth.restored', {
        hasRefreshToken: !!u.refresh_token,
        scope: u.scope,
        expires_at: u.expires_at,
      })
    } else if (u && u.expired) {
      oidcTrace('initAuth.expired', { expires_at: u.expires_at })
      await safeSilentRenew()
    } else {
      oidcTrace('initAuth.noState')
      user.value = null
      clearActiveTenantId()
    }
  } catch (error) {
    logger.error('[OIDC] 初始化认证状态失败', error)
    oidcTrace('initAuth.error', error)
    user.value = null
    setSessionClaimsSnapshot(null)
    clearActiveTenantId()
  }
}

let authInitializationPromise: Promise<void> | null = null

export function ensureAuthInitialized(options: { force?: boolean } = {}): Promise<void> {
  if (!authInitializationPromise || options.force) {
    authInitializationPromise = initAuth()
  }
  return authInitializationPromise
}

// 提供 Vue 组件中使用的 Auth API
export function useAuth(): AuthContext {
  const getAccessToken = async () => {
    if (authRuntimeConfig.sessionOnly) return null
    if (!user.value) {
      const cachedUser = await userManager.getUser()
      if (cachedUser && !cachedUser.expired) {
        user.value = cachedUser
        syncTenantContextFromClaims(cachedUser.profile as Record<string, unknown>)
      }
    }

    if (!user.value || user.value.expired) {
      const renewed = await safeSilentRenew()
      if (!renewed) return null
    }

    const accessToken = user.value?.access_token || null
    syncTenantContextFromAccessToken(accessToken)
    return accessToken
  }

  const fetchWithAuth = async (url: string, options: RequestInit = {}) => {
    const token = authRuntimeConfig.sessionOnly ? null : await getAccessToken()
    if (!authRuntimeConfig.sessionOnly && !token) throw new Error('Not authenticated')

    const controller = new AbortController()
    const timeout = setTimeout(() => controller.abort(), authRuntimeConfig.fetchTimeoutMs)

    try {
      // 添加 TRACE_ID 和 Authorization headers
      const headers = new Headers(options.headers)
      if (token) headers.set('Authorization', `Bearer ${token}`)
      if (authRuntimeConfig.sessionOnly && isUnsafeHttpMethod(options.method)) {
        const csrf = await ensureCsrfToken(normalizeApiBaseUrl())
        headers.set(csrf.headerName, csrf.token)
      }
      appendTenantHeader(headers)

      const traceOptions = addTraceIdToFetchOptions({
        ...options,
        headers,
      })

      return await fetch(url, {
        ...traceOptions,
        credentials: 'include',
        signal: controller.signal,
      })
    } catch (err) {
      if (err instanceof DOMException && err.name === 'AbortError') {
        logger.warn('[Auth] 请求超时')
      } else if (!navigator.onLine) {
        logger.warn('[Auth] 网络离线')
      } else if (err instanceof Error) {
        logger.error('[Auth] 请求异常', err)
      } else {
        logger.error('[Auth] 未知请求异常', err)
      }
      throw err
    } finally {
      clearTimeout(timeout)
    }
  }

  return {
    user,
    isAuthenticated,
    login,
    logout,
    getAccessToken,
    fetchWithAuth,
  }
}

// 兼容历史测试/旧模块导入。启动链路不再依赖模块加载副作用，真实恢复由 authBootstrap 显式触发。
export const initPromise = Promise.resolve()

// OIDC 事件监听
bindUserManagerEvents({
  onUserLoaded: (u) => {
    oidcTrace('event.userLoaded', {
      hasRefreshToken: !!u.refresh_token,
      scope: u.scope,
      expires_at: u.expires_at,
    })
    user.value = u
    syncTenantContextFromClaims(u.profile as Record<string, unknown>)
    syncTenantContextFromAccessToken(u.access_token)
    loginInProgress = false // 重置登录状态
    verifyAccessToken(u.access_token)
  },
  onUserUnloaded: () => {
    oidcTrace('event.userUnloaded')
    user.value = null
    clearActiveTenantId()
    dispatchAuthorizationRuntimeReset('user_unloaded', { message: 'OIDC 用户状态已卸载' })
    loginInProgress = false // 重置登录状态
  },
  onSilentRenewError: (err) => {
    logger.error('[OIDC] Silent renew 事件异常', err)
  },
  onUserSignedOut: () => {
    oidcTrace('event.userSignedOut')
    user.value = null
    clearActiveTenantId()
    dispatchAuthorizationRuntimeReset('user_signed_out', { message: 'OIDC 会话已退出' })
    loginInProgress = false // 重置登录状态
    // 可选：跳转登录页
    window.location.href = '/login'
  },
  onAccessTokenExpiring: async () => {
    const secondsLeft = user.value?.expires_in ?? 0
    oidcTrace('event.tokenExpiring', { secondsLeft })
    if (secondsLeft <= 60) {
      await safeSilentRenew()
    }
  },
})
