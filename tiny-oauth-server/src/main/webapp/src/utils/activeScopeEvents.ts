import { getActiveTenantId } from '@/utils/tenant'

/** 与 HeaderBar 切换 active scope 后业务页刷新约定一致（单一名称，禁止第二套）。 */
export const ACTIVE_SCOPE_CHANGED_EVENT = 'active-scope-changed'

export function notifyActiveScopeChanged(): void {
  window.dispatchEvent(new CustomEvent(ACTIVE_SCOPE_CHANGED_EVENT))
}

/**
 * 租户控制面（依赖 `X-Tenant-Id` / 活动租户上下文的页面）是否应在收到 `active-scope-changed` 时重拉数据。
 *
 * **正式边界（非缺陷）**：`getActiveTenantId()` 为空（平台态、未选活动租户、或已清理）时返回 `false`，
 * 页面**不得**因 Header scope 切换而发起“租户数据面”重拉，以免用前端状态推断替代后端 `TenantContext` /
 * active scope 的单一真相源。
 *
 * 有活动租户时返回 `true`，与后端 `@DataScope` + `TenantContextFilter` 对齐。
 */
export function shouldReloadTenantControlPlaneOnActiveScopeChange(): boolean {
  const id = getActiveTenantId()
  return id != null && id !== ''
}
