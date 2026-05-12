import { reactive, readonly } from 'vue'
import type { MenuItem } from '@/api/menu'

interface MenuRouteState {
  loading: boolean
  loaded: boolean
  error: string | null
  lastLoadedAt?: number
  cacheSource?: 'network' | 'verified_cache'
  etag?: string
  menus: MenuItem[]
}

const state = reactive<MenuRouteState>({
  loading: false,
  loaded: false,
  error: null,
  lastLoadedAt: undefined,
  menus: [],
})

export function useMenuRouteState() {
  return readonly(state)
}

export function updateMenuRouteState(patch: Partial<MenuRouteState>) {
  Object.assign(state, patch)
}
