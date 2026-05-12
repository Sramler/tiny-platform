import { authRuntimeConfig } from './config'
import {
  completePostLogoutRedirect,
  consumePostLogoutRedirectMarker,
  ensureAuthInitialized,
  trySilentLoginFromPlatformSessionDetailed,
  useAuth,
  type SilentLoginErrorCode,
} from './auth'
import { getTenantCode } from '@/utils/tenant'

export type AuthBootstrapStatus =
  | 'authenticated'
  | 'anonymous'
  | 'tenant_authorize_required'
  | 'login_required'
  | 'interaction_required'
  | 'consent_required'
  | 'timeout'
  | 'network_error'
  | 'invalid_state'
  | 'cookie_blocked'
  | 'post_logout'
  | 'error'

export interface AuthBootstrapResult {
  status: AuthBootstrapStatus
  message?: string
  errorCode?: SilentLoginErrorCode | 'post_logout' | 'unknown'
}

const LOGIN_REQUIRED_CODES = new Set<SilentLoginErrorCode>([
  'login_required',
  'interaction_required',
  'consent_required',
  'invalid_state',
])

export async function restoreAuthState(url = window.location.href): Promise<AuthBootstrapResult> {
  const auth = useAuth()

  try {
    const completedPostLogout = await completePostLogoutRedirect(url)
    if (completedPostLogout || consumePostLogoutRedirectMarker()) {
      return {
        status: 'post_logout',
        errorCode: 'post_logout',
        message: '已完成退出回跳',
      }
    }

    await ensureAuthInitialized()
    if (auth.isAuthenticated.value) {
      return { status: 'authenticated' }
    }

    const tenantCode = getTenantCode()
    if (tenantCode) {
      return {
        status: 'tenant_authorize_required',
        message: '需要跳转认证中心完成登录',
      }
    }

    if (!authRuntimeConfig.enablePlatformSessionSilentLogin) {
      return {
        status: 'anonymous',
        errorCode: 'login_required',
        message: '未启用平台 Session 静默桥接',
      }
    }

    const silentResult = await trySilentLoginFromPlatformSessionDetailed()
    if (silentResult.ok) {
      return { status: 'authenticated' }
    }

    if (LOGIN_REQUIRED_CODES.has(silentResult.errorCode)) {
      return {
        status:
          silentResult.errorCode === 'login_required'
            ? 'login_required'
            : silentResult.errorCode === 'interaction_required'
              ? 'interaction_required'
              : silentResult.errorCode === 'consent_required'
                ? 'consent_required'
                : 'invalid_state',
        errorCode: silentResult.errorCode,
        message: silentResult.message,
      }
    }
    if (silentResult.errorCode === 'timeout' || silentResult.errorCode === 'network_error') {
      return {
        status: silentResult.errorCode,
        errorCode: silentResult.errorCode,
        message: silentResult.message,
      }
    }
    if (silentResult.errorCode === 'cookie_blocked') {
      return {
        status: 'cookie_blocked',
        errorCode: 'cookie_blocked',
        message: silentResult.message,
      }
    }

    return {
      status: 'anonymous',
      errorCode: silentResult.errorCode,
      message: silentResult.message,
    }
  } catch (error) {
    return {
      status: 'error',
      errorCode: 'unknown',
      message: error instanceof Error ? error.message : '认证状态恢复失败',
    }
  }
}
