import { computed } from 'vue'
import { useAuth } from '@/auth/auth'
import { isPlatformPrincipal } from '@/auth/runtimeIdentity'

export function usePlatformScope() {
  const { user } = useAuth()
  const isPlatformScope = computed(() => isPlatformPrincipal(user.value))
  return { isPlatformScope }
}
