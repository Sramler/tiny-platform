import { computed } from 'vue'
import { useAuth } from '@/auth/auth'
import { decodeJwtPayload } from '@/utils/jwt'

export function usePlatformScope() {
  const { user } = useAuth()
  const claims = computed(() => decodeJwtPayload<{ activeScopeType?: unknown }>(user.value?.access_token))
  const isPlatformScope = computed(() => claims.value?.activeScopeType === 'PLATFORM')
  return { isPlatformScope }
}
