import type { Router } from 'vue-router'
import type { MenuItem } from '@/api/menu'
import logger from '@/utils/logger'
import { updateMenuRouteState } from '@/router/menuState'
import { loadVerifiedMenuTree } from './menuTreeLoader'
import {
  clearDynamicMenuRoutes,
  generateMenuRoutes,
  registerDynamicMenuRoutes,
  type DynamicMenuRouteReport,
} from './dynamicRoutes'

export type PermissionResultStatus = 'ready' | 'empty_menu' | 'menu_error' | 'route_register_error'

export interface PermissionBootstrapResult {
  status: PermissionResultStatus
  message?: string
  menus?: MenuItem[]
  cacheSource?: 'network' | 'verified_cache'
  etag?: string
  addedCount?: number
  skippedCount?: number
  diagnostics?: PermissionBootstrapDiagnostics
}

export type PermissionBootstrapDiagnostics = DynamicMenuRouteReport & {
  staticRouteConflicts?: string[]
}

let permissionLoading: Promise<PermissionBootstrapResult> | null = null

async function doLoadAndRegisterPermission(router: Router): Promise<PermissionBootstrapResult> {
  updateMenuRouteState({ loading: true, loaded: false, error: null })

  try {
    const menuTreeResult = await loadVerifiedMenuTree()
    const menus = menuTreeResult.menus
    if (!Array.isArray(menus) || menus.length === 0) {
      const message = '菜单数据为空，已阻断业务区访问'
      updateMenuRouteState({
        loading: false,
        loaded: false,
        error: message,
        menus: [],
        lastLoadedAt: Date.now(),
        cacheSource: menuTreeResult.source,
        etag: menuTreeResult.etag,
      })
      return { status: 'empty_menu', message, menus: [], cacheSource: menuTreeResult.source, etag: menuTreeResult.etag }
    }

    const generated = generateMenuRoutes(menus)
    const routes = generated.routes
    const { report } = generated

    if (
      report.invalidUrls.length ||
      report.missingComponents.length ||
      report.duplicatePaths.length
    ) {
      const message = '菜单路由配置存在无效路径、缺失组件或重复路径，已阻断业务区访问'
      logger.error('[Permission] 菜单路由配置校验失败:', report)
      updateMenuRouteState({
        loading: false,
        loaded: false,
        error: message,
        menus,
        cacheSource: menuTreeResult.source,
        etag: menuTreeResult.etag,
      })
      return {
        status: 'route_register_error',
        message,
        menus,
        cacheSource: menuTreeResult.source,
        etag: menuTreeResult.etag,
        diagnostics: report,
      }
    }

    try {
      const { addedCount, skippedCount, staticRouteConflicts } = registerDynamicMenuRoutes(
        router,
        routes,
      )
      updateMenuRouteState({
        loading: false,
        loaded: true,
        error: null,
        menus,
        lastLoadedAt: Date.now(),
        cacheSource: menuTreeResult.source,
        etag: menuTreeResult.etag,
      })
      logger.info(`[Permission] 菜单权限加载完成: 新增 ${addedCount} 个，跳过 ${skippedCount} 个`)
      return {
        status: 'ready',
        menus,
        cacheSource: menuTreeResult.source,
        etag: menuTreeResult.etag,
        addedCount,
        skippedCount,
        diagnostics: {
          ...report,
          staticRouteConflicts,
        },
      }
    } catch (error) {
      const message = '动态路由注册失败，已阻断业务区访问'
      logger.error('[Permission] 动态路由注册失败:', error)
      updateMenuRouteState({
        loading: false,
        loaded: false,
        error: message,
        cacheSource: menuTreeResult.source,
        etag: menuTreeResult.etag,
      })
      return {
        status: 'route_register_error',
        message,
        menus,
        cacheSource: menuTreeResult.source,
        etag: menuTreeResult.etag,
      }
    }
  } catch (error) {
    const message = error instanceof Error ? error.message : '菜单权限加载失败'
    logger.error('[Permission] 菜单权限加载失败:', error)
    updateMenuRouteState({ loading: false, loaded: false, error: message })
    return { status: 'menu_error', message }
  }
}

export function loadAndRegisterPermission(
  router: Router,
  options: { force?: boolean } = {},
): Promise<PermissionBootstrapResult> {
  if (!options.force && permissionLoading) {
    return permissionLoading
  }
  permissionLoading = doLoadAndRegisterPermission(router).finally(() => {
    permissionLoading = null
  })
  return permissionLoading
}

export function reloadPermissionRoutes(router: Router): Promise<PermissionBootstrapResult> {
  return loadAndRegisterPermission(router, { force: true })
}

export function resetPermissionBootstrapState(): void {
  permissionLoading = null
  clearDynamicMenuRoutes()
  updateMenuRouteState({
    loading: false,
    loaded: false,
    error: null,
    menus: [],
    lastLoadedAt: undefined,
    cacheSource: undefined,
    etag: undefined,
  })
}
