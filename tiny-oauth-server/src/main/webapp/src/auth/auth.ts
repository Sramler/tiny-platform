import { ref, computed } from 'vue'
import type { Ref, ComputedRef } from 'vue'
import { oidcClient, userManager, settings } from './oidc'
import { authRuntimeConfig } from './config'
import type { User } from 'oidc-client-ts'
import { jwtVerify, createRemoteJWKSet } from 'jose'
import { logger, persistentLogger } from '@/utils/logger'
import { sanitizeInternalRedirect } from '@/utils/redirect'
import { clearTraceId, createNewTraceId } from '@/utils/traceId'
import {
  clearActiveTenantId,
  getTenantCode,
  getActiveTenantId,
  syncTenantContextFromAccessToken,
  syncTenantContextFromClaims,
} from '@/utils/tenant'

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

async function getJWKS() {
  if (jwks) {
    return jwks
  }

  try {
    const metadata = await userManager.metadataService.getMetadata()
    if (!metadata.jwks_uri) {
      logger.warn('[OIDC] discovery 文档中未找到 jwks_uri，跳过前端 JWT 校验')
      throw new Error('jwks_uri not found in metadata')
    }

    jwks = createRemoteJWKSet(new URL(metadata.jwks_uri))
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

const appendTenantHeader = (headers: Headers): void => {
  const activeTenantId = getActiveTenantId()
  if (activeTenantId) {
    headers.set('X-Active-Tenant-Id', activeTenantId)
  }
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
    // 登录链路使用新的 traceId，避免复用上一个会话/失败流程的 traceId
    const traceId = createNewTraceId()
    oidcTrace('login.redirect', { redirect_uri: settings.redirect_uri, trace_id: traceId, redirectPath })
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
  try {
    const currentUser = await userManager.getUser()
    if (currentUser && currentUser.id_token) {
      const postLogoutRedirect = settings.post_logout_redirect_uri ?? window.location.origin
      // 为注销流程单独创建新的 traceId，避免跨会话复用
      const traceId = createNewTraceId()
      await userManager.signoutRedirect({
        id_token_hint: currentUser.id_token,
        // post_logout_redirect_uri 必须与后端注册值完全一致，禁止追加 query
        post_logout_redirect_uri: postLogoutRedirect,
        // 将 trace_id 作为额外查询参数传给注销端点，后端过滤器会读取
        extraQueryParams: {
          trace_id: traceId,
        },
      })
      // 最佳努力清理；若浏览器已开始跳转，此行可能来不及执行
      clearTraceId()
      return
    }
  } catch (error) {
    logger.error('[OIDC] 注销重定向失败，使用本地回退逻辑', error)
  }

  await userManager.removeUser()
  user.value = null
  clearActiveTenantId()
  clearTraceId()
  loginInProgress = false
  // 本地回退：使用与后端注册值一致的固定跳转地址，避免 OIDC 校验失败
  window.location.href = settings.post_logout_redirect_uri ?? window.location.origin
}

let renewInProgress = false

async function safeSilentRenew() {
  if (renewInProgress) return null
  renewInProgress = true
  try {
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
    if (authRuntimeConfig.forceLogoutOnRenewFail) {
      await userManager.removeUser()
      user.value = null
      clearTraceId()
      loginInProgress = false // 重置登录状态
      window.location.href = '/login'
    }
    return null
  } finally {
    renewInProgress = false
  }
}

// 初始化恢复用户状态
export async function initAuth() {
  try {
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
    clearActiveTenantId()
  }
}

// 提供 Vue 组件中使用的 Auth API
export function useAuth(): AuthContext {
  const getAccessToken = async () => {
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
    const token = await getAccessToken()
    if (!token) throw new Error('Not authenticated')

    const controller = new AbortController()
    const timeout = setTimeout(() => controller.abort(), authRuntimeConfig.fetchTimeoutMs)

    try {
      // 添加 TRACE_ID 和 Authorization headers
      const { addTraceIdToFetchOptions } = await import('@/utils/traceId')
      const headers = new Headers(options.headers)
      headers.set('Authorization', `Bearer ${token}`)
      appendTenantHeader(headers)

      const traceOptions = addTraceIdToFetchOptions({
        ...options,
        headers,
      })

      return await fetch(url, {
        ...traceOptions,
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

// 初始化调用（模块加载即执行）
export const initPromise = initAuth()

// OIDC 事件监听
userManager.events.addUserLoaded((u) => {
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
})

userManager.events.addUserUnloaded(() => {
  oidcTrace('event.userUnloaded')
  user.value = null
  clearActiveTenantId()
  loginInProgress = false // 重置登录状态
})

userManager.events.addSilentRenewError((err) => {
  logger.error('[OIDC] Silent renew 事件异常', err)
})

userManager.events.addUserSignedOut(() => {
  oidcTrace('event.userSignedOut')
  user.value = null
  clearActiveTenantId()
  loginInProgress = false // 重置登录状态
  // 可选：跳转登录页
  window.location.href = '/login'
})

userManager.events.addAccessTokenExpiring(async () => {
  const secondsLeft = user.value?.expires_in ?? 0
  oidcTrace('event.tokenExpiring', { secondsLeft })
  if (secondsLeft <= 60) {
    await safeSilentRenew()
  }
})
