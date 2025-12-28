// src/router/index.ts
import { createRouter, createWebHistory } from 'vue-router'
import type { NavigationGuard } from 'vue-router'
import { watch } from 'vue'
import { message } from 'ant-design-vue'
import HomeView from '@/views/HomeView.vue'
import Login from '@/views/Login.vue'
import OidcCallback from '@/views/OidcCallback.vue'
import BasicLayout from '@/layouts/BasicLayout.vue'
import { useAuth, initPromise } from '@/auth/auth' // 确保路径正确
import DefaultView from '@/views/default.vue'
import Error401 from '@/views/exception/401.vue' // 引入 401 页面
import Error400 from '@/views/exception/400.vue' // 引入 400 页面
import Error403 from '@/views/exception/403.vue' // 引入 403 页面
import Error404 from '@/views/exception/404.vue' // 引入 404 页面
import Error500 from '@/views/exception/500.vue' // 引入 500 页面
import Debug from '@/views/OIDCDebug.vue' // 引入调试页面
import TotpBind from '@/views/security/TotpBind.vue'
import TotpVerify from '@/views/security/TotpVerify.vue'
import { menuTree, type MenuItem } from '@/api/menu' // 引入菜单 API
import logger from '@/utils/logger' // 引入日志工具
import { useMenuRouteState, updateMenuRouteState } from './menuState'

const MENU_LOAD_MESSAGE_KEY = 'menu-load-error'
const menuRouteState = useMenuRouteState()
let menuRoutesLoading: Promise<boolean> | null = null

/**
 * 递归生成菜单对应的路由配置，支持动态组件导入。
 */
function generateMenuRoutes(menuList: MenuItem[]) {
  const routes: any[] = []
  for (const item of menuList) {
    // 跳过隐藏的菜单项
    if (item.hidden) continue
    // 跳过未启用的菜单项
    if (item.enabled === false) continue
    // 跳过特殊错误页，避免 DefaultView 覆盖
    // 注意：403、404、500 已作为主框架子路由配置，这里跳过避免重复
    if (
      item.url &&
      [
        '/exception/401',
        '/exception/400',
        '/exception/403',
        '/exception/404',
        '/exception/500',
        '/401',
        '/400',
        '/403',
        '/404',
        '/500',
      ].includes(item.url)
    )
      continue
    if (item.url) {
      let component
      if (item.component) {
        // 只需要 ../views/xxx.vue
        const compPath = `../views${item.component.replace('/views', '')}`
        //console.log('注册路由:', item.url, '组件路径:', compPath) // 调试输出
        component = () => import(/* @vite-ignore */ compPath)
      } else {
        // 没有 component 字段时 fallback 到 DefaultView
        component = DefaultView
      }
      // 确保路由路径以 / 开头
      const routePath = item.url.startsWith('/') ? item.url : `/${item.url}`
      routes.push({
        path: routePath, // 使用 url 字段作为路由路径，确保以 / 开头
        component,
        meta: {
          menuInfo: item,
          requiresAuth: true, // 菜单路由默认需要认证
          title: item.title,
        },
      })
    }
    if (item.children && item.children.length > 0) {
      routes.push(...generateMenuRoutes(item.children))
    }
  }
  return routes
}

// 路由配置
const routes = [
  // 登录页和回调页不使用主布局
  { path: '/login', name: 'Login', component: Login, meta: { title: '登录', requiresAuth: false } },
  {
    path: '/self/security/totp-bind',
    name: 'TotpBind',
    component: TotpBind,
    meta: { title: '绑定二步验证', requiresAuth: false },
  },
  {
    path: '/self/security/totp-verify',
    name: 'TotpVerify',
    component: TotpVerify,
    meta: { title: '二步验证', requiresAuth: false },
  },
  { path: '/callback', name: 'OidcCallback', component: OidcCallback },
  // 错误页面保持独立（不需要主布局，全屏显示）
  {
    path: '/exception/401',
    name: 'Error401',
    component: Error401,
    meta: { title: '401', requiresAuth: false },
  },
  {
    path: '/exception/400',
    name: 'Error400',
    component: Error400,
    meta: { title: '400', requiresAuth: false }, // 允许未登录用户看到 400 错误
  },
  {
    path: '/exception/403',
    name: 'Error403',
    component: Error403,
    meta: { title: '403', requiresAuth: false }, // 允许未登录用户看到 403 错误
  },
  {
    path: '/exception/404',
    name: 'Error404',
    component: Error404,
    meta: { title: '404', requiresAuth: false }, // 允许未登录用户看到 404 错误
  },
  {
    path: '/exception/500',
    name: 'Error500',
    component: Error500,
    meta: { title: '500', requiresAuth: false }, // 允许未登录用户看到 500 错误
  },
  // 主框架路由，所有需要布局的页面作为子路由
  {
    path: '/',
    name: 'mainLayout', // 给主布局路由命名，便于动态添加子路由
    component: BasicLayout, // 使用主布局
    children: [
      {
        path: '',
        name: 'Home',
        component: HomeView,
        meta: { requiresAuth: true, title: '工作台' },
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
        component: Debug,
        meta: { requiresAuth: true, title: 'OIDC 调试工具' },
      },
      // 菜单路由将在动态加载时添加，这里先留空
    ],
  },
  // 全局兜底 404（重定向到主框架的 404 页面）
  { path: '/:pathMatch(.*)*', name: 'NotFound', redirect: '/exception/404' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 动态加载菜单路由
/**
 * 从后端加载菜单并动态注入路由。
 * @returns 是否成功加载
 */
async function loadMenuRoutes(): Promise<boolean> {
  if (menuRouteState.loaded) {
    return true
  }

  updateMenuRouteState({ loading: true, error: null })

  try {
    logger.log('🔄 开始从后端加载菜单路由...')
    const menuData = await menuTree()

    if (menuData && Array.isArray(menuData) && menuData.length > 0) {
      const generatedRoutes = generateMenuRoutes(menuData)
      let addedCount = 0
      let skippedCount = 0

      generatedRoutes.forEach((route) => {
        const existingRoute = router.getRoutes().find((r) => r.path === route.path)
        if (!existingRoute) {
          router.addRoute('mainLayout', route)
          addedCount++
          logger.debug('✅ 已添加菜单路由:', route.path, route.meta?.title)
        } else {
          skippedCount++
          logger.debug('ℹ️ 路由已存在（初始配置），跳过:', route.path)
        }
      })

      message.destroy(MENU_LOAD_MESSAGE_KEY)
      updateMenuRouteState({
        loading: false,
        loaded: true,
        error: null,
        lastLoadedAt: Date.now(),
      })
      logger.log(`✅ 菜单路由处理完成: 新增 ${addedCount} 个，跳过 ${skippedCount} 个`)
      return true
    }

    const warnMsg = '⚠️ 菜单数据为空，无法生成路由'
    logger.warn(warnMsg)
    updateMenuRouteState({ loading: false, error: warnMsg })
    message.warning({
      content: warnMsg,
      key: MENU_LOAD_MESSAGE_KEY,
      duration: 4,
    })
    return false
  } catch (error) {
    logger.error('❌ 加载菜单路由失败:', error)
    const errMsg = '菜单加载失败，请稍后重试'
    updateMenuRouteState({ loading: false, error: errMsg })
    message.error({
      content: errMsg,
      key: MENU_LOAD_MESSAGE_KEY,
      duration: 4,
    })
    return false
  }
}

/**
 * 确保菜单路由加载完毕，避免并发重复请求。
 */
async function ensureMenuRoutesLoaded(): Promise<boolean> {
  if (menuRouteState.loaded) {
    return true
  }
  if (!menuRoutesLoading) {
    menuRoutesLoading = loadMenuRoutes().finally(() => {
      menuRoutesLoading = null
    })
  }
  return menuRoutesLoading
}

const authContext = useAuth()

const authGuard: NavigationGuard = async (to, _from, next) => {
  // 错误页面直接放行，不需要认证检查
  if (to.path.startsWith('/exception/')) {
    logger.log('访问错误页面，直接放行:', to.path)
    next()
    return
  }

  try {
    await initPromise
  } catch (error) {
    logger.error('认证状态初始化失败:', error)
  }

  const requiresAuth = to.meta.requiresAuth !== false

  if (to.path === '/login' && authContext.isAuthenticated.value) {
    logger.log('用户已认证，重定向到首页')
    next('/')
    return
  }

  if (!requiresAuth) {
    next()
    return
  }

  if (authContext.isAuthenticated.value) {
    next()
    return
  }

  const urlParams = new URLSearchParams(window.location.search)
  if (urlParams.has('code') || urlParams.has('error')) {
    logger.log('检测到 OIDC 回调参数，放行当前导航')
    next()
    return
  }

  logger.log('用户未认证，直接交给后端控制登录与 MFA 路由')

  try {
    // 由后端根据 security.mfa.mode 与用户状态决定：
    // 1) 直接进入授权流程
    // 2) 重定向到 TOTP 绑定页
    // 3) 重定向到 TOTP 验证页
    await authContext.login()
    return
  } catch (error) {
    logger.error('登录重定向失败:', error)
    next('/login')
  }
}

const dynamicRoutesGuard: NavigationGuard = async (to, from, next) => {
  // 错误页面不需要动态路由检查
  if (to.path.startsWith('/exception/')) {
    next()
    return
  }
  
  if (!authContext.isAuthenticated.value || to.meta.requiresAuth === false) {
    next()
    return
  }

  const needRetry = to.matched.length === 0 || to.name === 'NotFound'
  const routesReady = await ensureMenuRoutesLoaded()

  if (!routesReady) {
    logger.error('[Router] 菜单路由加载失败，保留默认导航')
    next()
    return
  }

  if (needRetry) {
    logger.warn('[Router] 未匹配到路由，尝试重新解析:', to.fullPath)
    const retry = router.resolve(to.fullPath)
    if (retry.matched.length > 0 && retry.name !== 'NotFound') {
      logger.info('[Router] 兜底成功，重新跳转:', to.fullPath)
      next({
        path: to.fullPath,
        query: to.query,
        hash: to.hash,
        replace: true,
      })
      return
    }
    
    // 如果最终还是没有匹配到路由，跳转到 404 页面并传递错误信息
    if (to.path !== '/exception/404') {
      logger.warn('[Router] 路由未找到，跳转到 404 页面:', to.fullPath)
      // 获取当前 traceId
      const { getCurrentTraceId } = await import('@/utils/traceId')
      const traceId = getCurrentTraceId()
      next({
        path: '/exception/404',
        query: {
          from: from.fullPath || from.path || document.referrer || undefined,
          path: to.fullPath || to.path,
          message: `路由未找到: ${to.fullPath}`,
          traceId: traceId || undefined,
        },
        replace: true,
      })
      return
    }
  }

  next()
}

router.beforeEach(authGuard)
router.beforeEach(dynamicRoutesGuard)

/**
 * 监听认证状态，确保登录完成后尽快预加载菜单路由。
 */
watch(
  () => authContext.isAuthenticated.value,
  (authed) => {
    if (authed) {
      ensureMenuRoutesLoaded()
    } else {
      updateMenuRouteState({ loaded: false, loading: false, error: null, lastLoadedAt: undefined })
      menuRoutesLoading = null
    }
  },
  { immediate: true },
)

// 初始化完成后再次尝试预加载，防止冷启动阶段 missed
initPromise
  .then(() => {
    if (authContext.isAuthenticated.value) {
      ensureMenuRoutesLoaded()
    }
  })
  .catch((error) => {
    logger.error('[Router] 认证初始化失败，无法预加载菜单路由:', error)
  })

export default router
