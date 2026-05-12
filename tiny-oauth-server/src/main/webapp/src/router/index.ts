// src/router/index.ts
import { createRouter, createWebHistory } from 'vue-router'
import type { NavigationGuard, RouteLocationRaw } from 'vue-router'
import { useAuth, consumePostLogoutRedirectMarker, completePostLogoutRedirect } from '@/auth/auth'
import logger from '@/utils/logger' // 引入日志工具
import { getCurrentTraceId } from '@/utils/traceId'
import { useMenuRouteState } from './menuState'
import { getLoginMode, syncTenantContextFromAccessToken } from '@/utils/tenant'
import { isBootReady } from '@/bootstrap/bootState'
import {
  buildBootstrapRoute,
  buildLoginRoute,
  isBootstrapBypassPath,
  isLoginPath,
} from './routePolicy'
import {
  buildPlatformAuditTabPath,
  buildPlatformDictTabPath,
  buildPlatformProcessTabPath,
  buildPlatformRoleConstraintTabPath,
  buildPlatformUserTabPath,
  buildPlatformSchedulingQuery,
  buildPlatformSchedulingTabPath,
  inferPlatformSchedulingTabFromPath,
  inferPlatformProcessTabFromPath,
} from '@/utils/platformRuntime'

const menuRouteState = useMenuRouteState()
const platformAuditComponent = () => import('@/views/platform/audit/PlatformAudit.vue')
const platformDictModuleComponent = () => import('@/views/platform/dicts/index.vue')
const platformProcessModuleComponent = () =>
  import('@/views/platform/runtime/PlatformProcessModule.vue')
const platformSchedulingModuleComponent = () =>
  import('@/views/platform/runtime/PlatformSchedulingModule.vue')
const platformUsersComponent = () => import('@/views/platform/users/PlatformUsers.vue')

// 路由配置
const routes = [
  // 登录页和回调页不使用主布局
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录', requiresAuth: false, requiresPermission: false },
  },
  {
    path: '/bootstrap',
    name: 'Bootstrap',
    component: () => import('@/views/bootstrap/BootstrapView.vue'),
    meta: {
      title: '系统启动',
      requiresAuth: false,
      requiresPermission: false,
      bootstrapRoute: true,
    },
  },
  {
    path: '/self/security/totp-bind',
    name: 'TotpBind',
    component: () => import('@/views/security/TotpBind.vue'),
    meta: {
      title: '绑定二步验证',
      requiresAuth: true,
      requiresPermission: false,
      requiresCompletedSecurity: false,
      securityRoute: true,
    },
  },
  {
    path: '/self/security/totp-verify',
    name: 'TotpVerify',
    component: () => import('@/views/security/TotpVerify.vue'),
    meta: {
      title: '二步验证',
      requiresAuth: true,
      requiresPermission: false,
      requiresCompletedSecurity: false,
      securityRoute: true,
    },
  },
  {
    path: '/callback',
    name: 'OidcCallback',
    component: () => import('@/views/OidcCallback.vue'),
    meta: { title: '登录回调', requiresAuth: false, requiresPermission: false },
  },
  // 错误页面保持独立（不需要主布局，全屏显示）
  {
    path: '/exception/401',
    name: 'Error401',
    component: () => import('@/views/exception/401.vue'),
    meta: { title: '401', requiresAuth: false },
  },
  {
    path: '/exception/400',
    name: 'Error400',
    component: () => import('@/views/exception/400.vue'),
    meta: { title: '400', requiresAuth: false }, // 允许未登录用户看到 400 错误
  },
  {
    path: '/exception/403',
    name: 'Error403',
    component: () => import('@/views/exception/403.vue'),
    meta: { title: '403', requiresAuth: false }, // 允许未登录用户看到 403 错误
  },
  {
    path: '/exception/404',
    name: 'Error404',
    component: () => import('@/views/exception/404.vue'),
    meta: { title: '404', requiresAuth: false }, // 允许未登录用户看到 404 错误
  },
  {
    path: '/exception/500',
    name: 'Error500',
    component: () => import('@/views/exception/500.vue'),
    meta: { title: '500', requiresAuth: false }, // 允许未登录用户看到 500 错误
  },
  {
    path: '/exception/503',
    name: 'Error503',
    component: () => import('@/views/exception/503.vue'),
    meta: { title: '503', requiresAuth: false }, // 允许未登录用户看到 503 错误
  },
  // 主框架路由，所有需要布局的页面作为子路由
  {
    path: '/',
    name: 'mainLayout', // 给主布局路由命名，便于动态添加子路由
    component: () => import('@/layouts/BasicLayout.vue'),
    children: [
      {
        path: '',
        name: 'Home',
        component: () => import('@/views/HomeView.vue'),
        meta: { requiresAuth: true, title: '工作台' },
      },
      {
        path: 'ops/idempotent',
        name: 'IdempotentOverview',
        component: () => import('@/views/idempotent/Overview.vue'),
        meta: { requiresAuth: true, title: '幂等治理' },
      },
      {
        path: 'system/audit/authentication',
        name: 'AuthenticationAudit',
        component: () => import('@/views/audit/AuthenticationAudit.vue'),
        meta: { requiresAuth: true, title: '认证审计' },
      },
      {
        path: 'platform/audit',
        name: 'PlatformAudit',
        redirect: () => ({
          path: buildPlatformAuditTabPath('authentication'),
          query: {},
        }),
        meta: { requiresAuth: true, title: '平台审计治理' },
      },
      {
        path: 'platform/audit/authentication',
        name: 'PlatformAuthenticationAudit',
        component: platformAuditComponent,
        meta: { requiresAuth: true, title: '平台登录审计' },
      },
      {
        path: 'platform/audit/authorization',
        name: 'PlatformAuthorizationAudit',
        component: platformAuditComponent,
        meta: { requiresAuth: true, title: '平台授权审计' },
      },
      {
        path: 'platform/token-debug',
        name: 'PlatformTokenDebug',
        component: () => import('@/views/platform/token-debug/TokenDebug.vue'),
        meta: { requiresAuth: true, title: '平台 Token Decode 工具' },
      },
      {
        path: 'platform/tenants',
        name: 'PlatformTenants',
        component: () => import('@/views/tenant/Tenant.vue'),
        meta: { requiresAuth: true, title: '平台租户治理' },
      },
      {
        path: 'platform/tenants/:id',
        name: 'PlatformTenantDetail',
        component: () => import('@/views/platform/tenants/TenantDetail.vue'),
        meta: { requiresAuth: true, title: '平台租户详情' },
      },
      {
        path: 'platform/permissions',
        name: 'PlatformPermissions',
        component: () => import('@/views/platform/permissions/PermissionControl.vue'),
        meta: { requiresAuth: true, title: '平台权限主数据' },
      },
      {
        path: 'platform/roles',
        alias: ['platform/template-roles'],
        name: 'PlatformRoles',
        component: () => import('@/views/platform/template-roles/TemplateRoles.vue'),
        meta: { requiresAuth: true, title: '平台角色治理' },
      },
      {
        path: 'platform/users',
        name: 'PlatformUsers',
        redirect: () => ({
          path: buildPlatformUserTabPath('platformUsers'),
          query: {},
        }),
        meta: { requiresAuth: true, title: '平台用户治理' },
      },
      {
        path: 'platform/users/governance',
        name: 'PlatformUserGovernance',
        component: platformUsersComponent,
        meta: { requiresAuth: true, title: '平台用户治理' },
      },
      {
        path: 'platform/users/tenant-stewardship',
        name: 'PlatformTenantStewardship',
        component: platformUsersComponent,
        meta: { requiresAuth: true, title: '租户用户代管' },
      },
      {
        path: 'platform/role-assignment-requests',
        name: 'PlatformRoleAssignmentRequests',
        component: () =>
          import('@/views/platform/role-assignment-requests/PlatformRoleApprovals.vue'),
        meta: { requiresAuth: true, title: '平台角色赋权审批' },
      },
      {
        path: 'platform/role-constraints',
        name: 'PlatformRoleConstraints',
        redirect: () => ({
          path: buildPlatformRoleConstraintTabPath('hierarchy'),
          query: {},
        }),
        meta: { requiresAuth: true, title: '平台 RBAC3 约束' },
      },
      {
        path: 'platform/role-constraints/hierarchy',
        name: 'PlatformRoleConstraintHierarchy',
        component: () => import('@/views/platform/role-constraints/PlatformRoleConstraints.vue'),
        meta: { requiresAuth: true, title: '平台角色继承' },
      },
      {
        path: 'platform/role-constraints/mutex',
        name: 'PlatformRoleConstraintMutex',
        component: () => import('@/views/platform/role-constraints/PlatformRoleConstraints.vue'),
        meta: { requiresAuth: true, title: '平台互斥约束' },
      },
      {
        path: 'platform/role-constraints/prerequisite',
        name: 'PlatformRoleConstraintPrerequisite',
        component: () => import('@/views/platform/role-constraints/PlatformRoleConstraints.vue'),
        meta: { requiresAuth: true, title: '平台先决条件' },
      },
      {
        path: 'platform/role-constraints/cardinality',
        name: 'PlatformRoleConstraintCardinality',
        component: () => import('@/views/platform/role-constraints/PlatformRoleConstraints.vue'),
        meta: { requiresAuth: true, title: '平台基数限制' },
      },
      {
        path: 'platform/role-constraints/violations',
        name: 'PlatformRoleConstraintViolations',
        component: () => import('@/views/platform/role-constraints/PlatformRoleConstraints.vue'),
        meta: { requiresAuth: true, title: '平台违规记录' },
      },
      // {
      //   path: 'about',
      //   name: 'About',
      //   component: AboutView,
      //   meta: { requiresAuth: true, title: '分析页' },
      // },
      // {
      //   path: 'modeling',
      //   name: 'modeling',
      //   component: Modeling,
      //   meta: { requiresAuth: true, title: '流程建模' },
      // },
      // {
      //   path: 'definition',
      //   name: 'definition',
      //   component: Definition,
      //   meta: { requiresAuth: true, title: '流程定义' },
      // },
      // {
      //   path: 'deployment',
      //   name: 'deployment',
      //   component: Deployment,
      //   meta: { requiresAuth: true, title: '流程部署' },
      // },
      // {
      //   path: 'instance',
      //   name: 'instance',
      //   component: () => import('@/views/process/Instance.vue'),
      //   meta: { requiresAuth: true, title: '流程实例' },
      // },

      {
        path: 'OIDCDebug',
        name: 'OIDCDebug',
        component: () => import('@/views/OIDCDebug.vue'),
        meta: { requiresAuth: true, title: 'OIDC 调试工具' },
      },
      {
        path: 'platform/dicts',
        name: 'PlatformDicts',
        redirect: () => ({
          path: buildPlatformDictTabPath('type'),
          query: {},
        }),
        meta: { requiresAuth: true, title: '平台字典管理' },
      },
      {
        path: 'platform/dicts/type',
        name: 'PlatformDictTypes',
        component: platformDictModuleComponent,
        meta: { requiresAuth: true, title: '平台字典类型' },
      },
      {
        path: 'platform/dicts/item',
        name: 'PlatformDictItems',
        component: platformDictModuleComponent,
        meta: { requiresAuth: true, title: '平台字典项' },
      },
      {
        path: 'platform/dicts/overrides',
        name: 'PlatformDictOverrides',
        component: platformDictModuleComponent,
        meta: { requiresAuth: true, title: '平台字典覆盖关系' },
      },
      {
        path: 'platform/scheduling',
        name: 'PlatformSchedulingModule',
        redirect: () => ({
          path: buildPlatformSchedulingTabPath('dag'),
          query: {},
        }),
        meta: { requiresAuth: true, title: '平台调度管理' },
      },
      {
        path: 'platform/scheduling/dag',
        name: 'PlatformSchedulingDag',
        component: platformSchedulingModuleComponent,
        meta: { requiresAuth: true, title: '平台 DAG 管理' },
      },
      {
        path: 'platform/scheduling/task',
        name: 'PlatformSchedulingTask',
        component: platformSchedulingModuleComponent,
        meta: { requiresAuth: true, title: '平台任务管理' },
      },
      {
        path: 'platform/scheduling/task-type',
        name: 'PlatformSchedulingTaskType',
        component: platformSchedulingModuleComponent,
        meta: { requiresAuth: true, title: '平台任务类型' },
      },
      {
        path: 'platform/scheduling/history',
        name: 'PlatformSchedulingHistory',
        component: platformSchedulingModuleComponent,
        meta: { requiresAuth: true, title: '平台运行历史' },
      },
      {
        path: 'platform/scheduling/audit',
        name: 'PlatformSchedulingAudit',
        component: platformSchedulingModuleComponent,
        meta: { requiresAuth: true, title: '平台审计日志' },
      },
      {
        path: 'platform/process',
        name: 'PlatformProcessModule',
        redirect: () => ({
          path: buildPlatformProcessTabPath('definition'),
          query: {},
        }),
        meta: { requiresAuth: true, title: '平台流程管理' },
      },
      {
        path: 'platform/process/modeling',
        name: 'PlatformProcessModeling',
        component: platformProcessModuleComponent,
        meta: { requiresAuth: true, title: '平台流程建模' },
      },
      {
        path: 'platform/process/deployment',
        name: 'PlatformProcessDeployment',
        component: platformProcessModuleComponent,
        meta: { requiresAuth: true, title: '平台流程部署' },
      },
      {
        path: 'platform/process/definition',
        name: 'PlatformProcessDefinition',
        component: platformProcessModuleComponent,
        meta: { requiresAuth: true, title: '平台流程定义' },
      },
      {
        path: 'platform/process/instance',
        name: 'PlatformProcessInstance',
        component: platformProcessModuleComponent,
        meta: { requiresAuth: true, title: '平台流程实例' },
      },
      {
        path: 'platform/process/task',
        name: 'PlatformProcessTask',
        component: platformProcessModuleComponent,
        meta: { requiresAuth: true, title: '平台流程任务管理' },
      },
      {
        path: 'platform/scheduling/dag/detail',
        name: 'PlatformDagDetail',
        component: () => import('@/views/scheduling/DagDetail.vue'),
        meta: { requiresAuth: true, title: '平台 DAG 详情' },
      },
      {
        path: 'platform/scheduling/dag/history',
        name: 'PlatformDagHistory',
        component: () => import('@/views/scheduling/DagHistory.vue'),
        meta: { requiresAuth: true, title: '平台 DAG 运行历史' },
      },
      // 调度 DAG 详情/历史（子页无菜单项，需静态注册避免 404）
      {
        path: 'scheduling/dag/detail',
        name: 'DagDetail',
        component: () => import('@/views/scheduling/DagDetail.vue'),
        meta: { requiresAuth: true, title: 'DAG 详情' },
      },
      {
        path: 'scheduling/dag/history',
        name: 'DagHistory',
        component: () => import('@/views/scheduling/DagHistory.vue'),
        meta: { requiresAuth: true, title: 'DAG 运行历史' },
      },
      // 菜单路由将在动态加载时添加，这里先留空
    ],
  },
  // 全局兜底占位路由：保留守卫重试动态菜单路由的机会，避免首次刷新动态页时被 redirect 抢先吞掉。
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/exception/404.vue'),
    meta: { requiresAuth: true, title: '404' },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

const authContext = useAuth()

function resolvePlatformRuntimeBridgePath(path: string): string | null {
  if (path.startsWith('/platform/scheduling') || path.startsWith('/platform/process')) {
    return null
  }
  if (path.startsWith('/scheduling')) {
    if (path.startsWith('/scheduling/dag/detail') || path.startsWith('/scheduling/dag/history')) {
      return `/platform${path}`
    }
    return buildPlatformSchedulingTabPath(inferPlatformSchedulingTabFromPath(path))
  }
  if (path.startsWith('/process')) {
    return buildPlatformProcessTabPath(inferPlatformProcessTabFromPath(path))
  }
  return null
}

export const authGuard: NavigationGuard = async (to) => {
  if (isBootstrapBypassPath(to.path)) {
    if (isLoginPath(to.path)) {
      const completedPostLogout = await completePostLogoutRedirect(window.location.href)
      if (!completedPostLogout) {
        consumePostLogoutRedirectMarker()
      }
      if (authContext.isAuthenticated.value) {
        return isBootReady() && menuRouteState.loaded ? '/' : buildBootstrapRoute('/')
      }
    }
    return true
  }

  const completedPostLogout = await completePostLogoutRedirect(window.location.href)
  if (completedPostLogout || consumePostLogoutRedirectMarker()) {
    return buildLoginRoute('/')
  }

  if (!isBootReady() || !menuRouteState.loaded) {
    return buildBootstrapRoute(to.fullPath || '/')
  }

  if (!authContext.isAuthenticated.value) {
    return buildLoginRoute(to.fullPath || '/')
  }

  return true
}

export const platformRuntimeBridgeGuard: NavigationGuard = async (to) => {
  if (isBootstrapBypassPath(to.path)) {
    return true
  }
  if (!isBootReady() || !menuRouteState.loaded || !authContext.isAuthenticated.value) {
    return true
  }
  syncTenantContextFromAccessToken(authContext.user.value?.access_token)
  if (getLoginMode() !== 'PLATFORM') {
    return true
  }

  const bridgePath = resolvePlatformRuntimeBridgePath(to.path)
  if (!bridgePath) {
    return true
  }

  return {
    path: bridgePath,
    query: bridgePath.startsWith('/platform/scheduling')
      ? buildPlatformSchedulingQuery(to.query)
      : {},
    replace: true,
  } satisfies RouteLocationRaw
}

export const dynamicRoutesGuard: NavigationGuard = async (to, from) => {
  if (isBootstrapBypassPath(to.path)) {
    return true
  }

  if (!isBootReady() || !menuRouteState.loaded || !authContext.isAuthenticated.value) {
    return true
  }

  const needRetry = to.matched.length === 0 || to.name === 'NotFound'
  if (needRetry) {
    logger.warn('[Router] 路由未找到，跳转到 404 页面:', to.fullPath)
    const traceId = getCurrentTraceId()
    return {
      path: '/exception/404',
      query: {
        from: from.fullPath || from.path || document.referrer || undefined,
        path: to.fullPath || to.path,
        message: `路由未找到: ${to.fullPath}`,
        traceId: traceId || undefined,
      },
      replace: true,
    } satisfies RouteLocationRaw
  }

  return true
}

router.beforeEach(authGuard)
router.beforeEach(platformRuntimeBridgeGuard)
router.beforeEach(dynamicRoutesGuard)

export default router
