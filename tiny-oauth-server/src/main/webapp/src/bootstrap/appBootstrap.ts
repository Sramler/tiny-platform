import type { Router, RouteLocationRaw } from 'vue-router'
import { useAuth } from '@/auth/auth'
import { restoreAuthState } from '@/auth/authBootstrap'
import { checkSecurityState } from '@/security/securityBootstrap'
import {
  loadAndRegisterPermission,
  type PermissionBootstrapDiagnostics,
} from '@/permission/permissionBootstrap'
import {
  beginBootRun,
  getBootRunId,
  invalidateBootRun,
  markBootError,
  markBootReady,
  markBootRedirecting,
  setBootStage,
} from './bootState'
import {
  buildBackendRedirectUrl,
  buildLoginRoute,
  isBackendOnlyRedirect,
  normalizeBootstrapRedirect,
} from '@/router/routePolicy'
import {
  classifyServiceThrownError,
  getServiceFailureMessage,
  type ServiceRequestError,
} from '@/utils/serviceRequestError'

export type BootstrapResultStatus =
  | 'ready'
  | 'redirecting'
  | 'stale'
  | 'auth_error'
  | 'security_error'
  | 'permission_error'

export interface BootstrapResult {
  status: BootstrapResultStatus
  message?: string
}

interface ClassifiedBootstrapError {
  code: string
  message: string
  detail: string
}

let activeBootstrap: Promise<BootstrapResult> | null = null
let activeRedirect = '/'

function formatPermissionDiagnostics(diagnostics?: PermissionBootstrapDiagnostics): string {
  if (!diagnostics) {
    return '业务区已按 fail-closed 策略阻断，请重试或重新登录。'
  }

  const parts: string[] = []
  if (diagnostics.invalidUrls.length) {
    parts.push(`无效路径 ${diagnostics.invalidUrls.length} 个`)
  }
  if (diagnostics.missingComponents.length) {
    parts.push(`缺失组件 ${diagnostics.missingComponents.length} 个`)
  }
  if (diagnostics.duplicatePaths.length) {
    parts.push(`重复路径 ${diagnostics.duplicatePaths.length} 个`)
  }
  if (diagnostics.staticRouteConflicts?.length) {
    parts.push(`静态路由冲突 ${diagnostics.staticRouteConflicts.length} 个`)
  }
  if (!parts.length) {
    return '业务区已按 fail-closed 策略阻断，请重试或重新登录。'
  }
  return `${parts.join('，')}。业务区已按 fail-closed 策略阻断，请修复菜单配置后重试。`
}

function isCurrentRun(runId: number): boolean {
  return getBootRunId() === runId
}

function classifyBootstrapError(error: unknown): ClassifiedBootstrapError {
  const anyError = error as { message?: unknown; name?: unknown; error?: unknown }
  const text = String(anyError?.error || anyError?.name || anyError?.message || error || '').toLowerCase()
  if (text.includes('missing tenant context')) {
    return {
      code: 'missing_tenant_context',
      message: '缺少租户上下文',
      detail: '请返回登录页重新选择租户或平台登录。',
    }
  }
  const serviceError = classifyServiceThrownError(error, {
    serviceName: '认证服务',
    operation: '创建 OIDC 授权请求',
  })
  if (serviceError.code !== 'unknown') {
    return {
      code: toAuthBootErrorCode(serviceError),
      message: getServiceFailureMessage(serviceError, {
        serviceName: '认证服务',
        timeoutMessage: '认证服务响应超时',
        networkErrorMessage: '认证服务暂不可用',
        unauthorizedMessage: '认证服务未认证，请返回登录页重新发起认证',
        forbiddenMessage: '认证服务拒绝授权请求，请确认租户或客户端配置',
        serverErrorMessage: '认证服务异常，请稍后重试或联系管理员',
        unexpectedStatusMessage: '认证服务授权请求失败，请稍后重试',
      }),
      detail:
        serviceError.code === 'network_error'
          ? '请确认后端认证服务已启动后重试，或返回登录页。'
          : '请稍后重试，或返回登录页重新发起认证。',
    }
  }
  return {
    code: 'bootstrap_error',
    message: '启动流程执行失败',
    detail: anyError?.message ? String(anyError.message) : '请重试，或返回登录页重新登录。',
  }
}

function toAuthBootErrorCode(error: ServiceRequestError): string {
  switch (error.code) {
    case 'timeout':
      return 'auth_timeout'
    case 'network_error':
      return 'auth_service_unavailable'
    case 'unauthorized':
      return 'auth_unauthorized'
    case 'forbidden':
      return 'auth_forbidden'
    case 'server_error':
      return 'auth_server_error'
    case 'bad_response':
      return 'auth_bad_response'
    case 'unexpected_status':
      return 'auth_unexpected_status'
    default:
      return 'auth_error'
  }
}

function markClassifiedBootstrapError(error: unknown): ClassifiedBootstrapError {
  const classified = classifyBootstrapError(error)
  markBootError(classified)
  return classified
}

async function navigateIfCurrent(
  router: Router,
  runId: number,
  target: RouteLocationRaw,
): Promise<BootstrapResult> {
  if (!isCurrentRun(runId)) {
    return { status: 'stale' }
  }
  markBootRedirecting('正在跳转', '正在进入下一步页面')
  await router.replace(target)
  return { status: 'redirecting' }
}

async function executeBootstrap(
  router: Router,
  runId: number,
  redirect: string,
): Promise<BootstrapResult> {
  const auth = useAuth()

  if (isBackendOnlyRedirect(redirect)) {
    markBootRedirecting('正在继续认证流程', '正在回到认证服务器完成原始授权请求')
    window.location.assign(buildBackendRedirectUrl(redirect))
    return { status: 'redirecting' }
  }

  setBootStage('checking_auth', '正在检查登录状态', '正在读取本地 OIDC 登录态')
  const authResult = await restoreAuthState()
  if (!isCurrentRun(runId)) {
    return { status: 'stale' }
  }

  if (authResult.status === 'authenticated') {
    setBootStage(
      'checking_security',
      '正在检查安全状态',
      '正在确认 TOTP / MFA 策略',
      'authenticated',
    )
  } else if (authResult.status === 'tenant_authorize_required') {
    markBootRedirecting('正在前往登录', '正在创建授权请求并跳转认证中心')
    try {
      await auth.login(redirect)
    } catch (error) {
      if (!isCurrentRun(runId)) {
        return { status: 'stale' }
      }
      const classified = markClassifiedBootstrapError(error)
      return { status: 'auth_error', message: classified.message }
    }
    return { status: 'redirecting' }
  } else if (
    authResult.status === 'login_required' ||
    authResult.status === 'interaction_required' ||
    authResult.status === 'consent_required' ||
    authResult.status === 'invalid_state' ||
    authResult.status === 'post_logout' ||
    authResult.status === 'anonymous'
  ) {
    return navigateIfCurrent(router, runId, buildLoginRoute(redirect))
  } else if (
    authResult.status === 'timeout' ||
    authResult.status === 'network_error' ||
    authResult.status === 'cookie_blocked'
  ) {
    markBootError({
      code: authResult.status,
      message: authResult.message || '登录态恢复失败',
      detail: '可重试恢复登录态，或返回登录页重新登录。',
    })
    return { status: 'auth_error', message: authResult.message }
  } else {
    markBootError({
      code: authResult.errorCode || 'auth_error',
      message: authResult.message || '登录态恢复失败',
    })
    return { status: 'auth_error', message: authResult.message }
  }

  const securityResult = await checkSecurityState()
  if (!isCurrentRun(runId)) {
    return { status: 'stale' }
  }
  if (securityResult.status === 'totp_bind_required') {
    return navigateIfCurrent(router, runId, {
      path: '/self/security/totp-bind',
      query: { redirect },
      replace: true,
    })
  }
  if (securityResult.status === 'totp_verify_required') {
    return navigateIfCurrent(router, runId, {
      path: '/self/security/totp-verify',
      query: { redirect },
      replace: true,
    })
  }
  if (securityResult.status === 'unauthenticated') {
    return navigateIfCurrent(router, runId, buildLoginRoute(redirect))
  }
  if (securityResult.status === 'error') {
    markBootError({
      code: 'security_error',
      message: securityResult.message || '安全状态检查失败',
    })
    return { status: 'security_error', message: securityResult.message }
  }

  setBootStage(
    'loading_permission',
    '正在加载菜单和权限',
    '正在读取 /sys/menus/tree',
    'security_passed',
  )
  const permissionResult = await loadAndRegisterPermission(router)
  if (!isCurrentRun(runId)) {
    return { status: 'stale' }
  }
  if (permissionResult.status !== 'ready') {
    markBootError({
      code: permissionResult.status,
      message: permissionResult.message || '菜单权限加载失败',
      detail: formatPermissionDiagnostics(permissionResult.diagnostics),
    })
    return { status: 'permission_error', message: permissionResult.message }
  }

  setBootStage('registering_routes', '正在注册路由', '正在重新匹配目标页面', 'permission_loaded')
  markBootReady('启动完成', '正在进入业务页面')

  const target = normalizeBootstrapRedirect(redirect)
  if (router.currentRoute.value.fullPath === target) {
    return { status: 'ready' }
  }
  await router.replace(target)
  return { status: 'ready' }
}

export function runAppBootstrap(router: Router, redirect: string): Promise<BootstrapResult> {
  const normalizedRedirect = normalizeBootstrapRedirect(redirect)
  if (activeBootstrap && activeRedirect === normalizedRedirect) {
    return activeBootstrap
  }
  if (activeBootstrap) {
    invalidateBootRun()
  }

  const runId = beginBootRun(normalizedRedirect)
  activeRedirect = normalizedRedirect
  const currentBootstrap = executeBootstrap(router, runId, normalizedRedirect)
    .catch((error) => {
      if (!isCurrentRun(runId)) {
        return { status: 'stale' as const }
      }
      const classified = markClassifiedBootstrapError(error)
      return { status: 'auth_error' as const, message: classified.message }
    })
    .finally(() => {
      if (activeBootstrap === currentBootstrap) {
        activeBootstrap = null
        activeRedirect = '/'
      }
    })
  activeBootstrap = currentBootstrap
  return activeBootstrap
}

export function cancelAppBootstrap(): void {
  activeBootstrap = null
  activeRedirect = '/'
  invalidateBootRun()
}
