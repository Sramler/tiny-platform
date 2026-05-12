import type { MenuItem } from '@/api/menu'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    requiresAuth?: boolean
    requiresPermission?: boolean
    requiresCompletedSecurity?: boolean
    bootstrapRoute?: boolean
    securityRoute?: boolean
    dynamicMenuRoute?: boolean
    menuInfo?: MenuItem
  }
}

export {}
