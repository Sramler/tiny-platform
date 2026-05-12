import type { Router, RouteRecordRaw } from 'vue-router'
import type { MenuItem } from '@/api/menu'
import logger from '@/utils/logger'

const ERROR_MENU_URLS = new Set([
  '/exception/401',
  '/exception/400',
  '/exception/403',
  '/exception/404',
  '/exception/500',
  '/exception/503',
  '/401',
  '/400',
  '/403',
  '/404',
  '/500',
  '/503',
])

let dynamicRouteRemovers: Array<() => void> = []

const viewModules = import.meta.glob('../views/**/*.vue') as Record<string, () => Promise<unknown>>
const lowerCaseViewModuleEntries = new Map<string, [string, () => Promise<unknown>]>(
  Object.entries(viewModules).map(([path, loader]) => [path.toLowerCase(), [path, loader]]),
)

export interface DynamicMenuRouteReport {
  duplicatePaths: string[]
  missingComponents: Array<{ menu: string; component: string }>
  invalidUrls: Array<{ menu: string; url: string }>
  hiddenSkippedCount: number
  disabledSkippedCount: number
  defaultComponentCount: number
}

export interface GeneratedMenuRoutes {
  routes: RouteRecordRaw[]
  report: DynamicMenuRouteReport
}

export interface RegisterDynamicMenuRoutesResult {
  addedCount: number
  skippedCount: number
  staticRouteConflicts: string[]
}

function normalizeRoutePath(path: string): string {
  return path.startsWith('/') ? path : `/${path}`
}

function isInternalRoutePath(path: string): boolean {
  return path.startsWith('/') && !path.startsWith('//')
}

function createEmptyReport(): DynamicMenuRouteReport {
  return {
    duplicatePaths: [],
    missingComponents: [],
    invalidUrls: [],
    hiddenSkippedCount: 0,
    disabledSkippedCount: 0,
    defaultComponentCount: 0,
  }
}

function normalizeComponentPath(component: string): string {
  const withoutViewsPrefix = component.trim().replace(/^\/?views\//, '')
  return `../views/${withoutViewsPrefix.startsWith('/') ? withoutViewsPrefix.slice(1) : withoutViewsPrefix}`
}

function resolveMenuComponent(
  item: MenuItem,
  report: DynamicMenuRouteReport,
): (() => Promise<unknown>) | null {
  const component = item.component?.trim()
  if (!component) {
    report.defaultComponentCount += 1
    return viewModules['../views/default.vue'] ?? null
  }

  const componentPath = normalizeComponentPath(component)
  const exact = viewModules[componentPath]
  if (exact) {
    return exact
  }

  const caseInsensitive = lowerCaseViewModuleEntries.get(componentPath.toLowerCase())
  if (caseInsensitive) {
    logger.warn('[Permission] 菜单组件大小写与文件系统不一致，已兼容解析:', {
      menu: item.name || item.title,
      configured: component,
      resolved: caseInsensitive[0],
    })
    return caseInsensitive[1]
  }

  report.missingComponents.push({
    menu: item.name || item.title || item.url || 'unknown',
    component,
  })
  return null
}

export function clearDynamicMenuRoutes(): void {
  dynamicRouteRemovers.forEach((remove) => {
    try {
      remove()
    } catch (error) {
      logger.warn('[Permission] 清理动态菜单路由失败，继续处理后续路由', error)
    }
  })
  dynamicRouteRemovers = []
}

export function generateMenuRoutes(menuList: MenuItem[]): GeneratedMenuRoutes {
  const routes: RouteRecordRaw[] = []
  const seenPaths = new Set<string>()
  const report = createEmptyReport()

  const walk = (items: MenuItem[]) => {
    for (const item of items) {
      if (item.hidden) {
        report.hiddenSkippedCount += 1
        continue
      }
      if (item.enabled === false) {
        report.disabledSkippedCount += 1
        continue
      }

      if (item.url && !ERROR_MENU_URLS.has(item.url)) {
        if (!isInternalRoutePath(item.url)) {
          report.invalidUrls.push({
            menu: item.name || item.title || item.url,
            url: item.url,
          })
          continue
        }

        const routePath = normalizeRoutePath(item.url)
        if (seenPaths.has(routePath)) {
          report.duplicatePaths.push(routePath)
          logger.warn('[Permission] 菜单存在重复路由，已跳过后续项:', routePath)
        } else {
          const component = resolveMenuComponent(item, report)
          if (!component) {
            continue
          }
          seenPaths.add(routePath)
          routes.push({
            path: routePath,
            component,
            meta: {
              menuInfo: item,
              requiresAuth: true,
              title: item.title,
              dynamicMenuRoute: true,
            },
          })
        }
      }

      if (item.children?.length) {
        walk(item.children)
      }
    }
  }

  walk(menuList)
  return { routes, report }
}

export function registerDynamicMenuRoutes(
  router: Router,
  routes: RouteRecordRaw[],
): {
  addedCount: number
  skippedCount: number
  staticRouteConflicts: string[]
} {
  clearDynamicMenuRoutes()

  let addedCount = 0
  let skippedCount = 0
  const staticRouteConflicts: string[] = []
  for (const route of routes) {
    const existingRoute = router.getRoutes().find((registered) => registered.path === route.path)
    if (existingRoute) {
      skippedCount += 1
      staticRouteConflicts.push(route.path)
      logger.debug('[Permission] 路由已存在，跳过动态注册:', route.path)
      continue
    }
    dynamicRouteRemovers.push(router.addRoute('mainLayout', route))
    addedCount += 1
    logger.debug('[Permission] 已注册动态菜单路由:', route.path, route.meta?.title)
  }

  return { addedCount, skippedCount, staticRouteConflicts }
}
