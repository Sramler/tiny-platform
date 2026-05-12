import { getSecurityStatus } from '@/api/security'

export type SecurityBootstrapStatus =
  | 'passed'
  | 'totp_bind_required'
  | 'totp_verify_required'
  | 'unauthenticated'
  | 'error'

export interface SecurityBootstrapResult {
  status: SecurityBootstrapStatus
  message?: string
}

export async function checkSecurityState(): Promise<SecurityBootstrapResult> {
  try {
    const status = await getSecurityStatus()
    const disableMfa = Boolean(status.disableMfa)
    const forceMfa = Boolean(status.forceMfa)
    const totpBound = Boolean(status.totpBound)
    const totpActivated = Boolean(status.totpActivated)
    const requireTotp = Boolean(status.requireTotp)

    if (disableMfa) {
      return { status: 'passed' }
    }
    if (forceMfa && (!totpBound || !totpActivated)) {
      return {
        status: 'totp_bind_required',
        message: '当前账号需要先绑定二步验证',
      }
    }
    if (requireTotp && totpActivated) {
      return {
        status: 'totp_verify_required',
        message: '当前会话需要完成二步验证',
      }
    }
    return { status: 'passed' }
  } catch (error) {
    const message = error instanceof Error ? error.message : '安全状态检查失败'
    if (/401|unauthorized|未登录/i.test(message)) {
      return { status: 'unauthenticated', message }
    }
    return { status: 'error', message }
  }
}

