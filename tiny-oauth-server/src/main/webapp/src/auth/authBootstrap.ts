import { ensureAuthInitialized, useAuth } from './auth'

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
  errorCode?: string
}

export async function restoreAuthState(): Promise<AuthBootstrapResult> {
  try {
    await ensureAuthInitialized()
    if (useAuth().isAuthenticated.value) return { status: 'authenticated' }
    return {
      status: 'login_required',
      errorCode: 'login_required',
      message: '没有可用的服务端登录会话',
    }
  } catch (error) {
    return {
      status: 'error',
      errorCode: 'unknown',
      message: error instanceof Error ? error.message : '认证状态恢复失败',
    }
  }
}
