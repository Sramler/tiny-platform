<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuth } from '@/auth/auth'
import { extractAuthoritiesFromJwt } from '@/utils/jwt'
import {
  PLATFORM_USER_MANAGEMENT_READ_AUTHORITIES,
  TENANT_MANAGEMENT_READ_AUTHORITIES,
  USER_MANAGEMENT_READ_AUTHORITIES,
} from '@/constants/permission'
import { usePlatformScope } from '@/composables/usePlatformScope'
import PlatformTenantStewardshipTab from './components/PlatformTenantStewardshipTab.vue'
import PlatformUserGovernanceTab from './components/PlatformUserGovernanceTab.vue'

type TabKey = 'platformUsers' | 'tenantStewardship'

const PLATFORM_USERS_PATH = '/platform/users'

const { user } = useAuth()
const { isPlatformScope } = usePlatformScope()
const route = useRoute()
const router = useRouter()

const authorities = computed(() => new Set(extractAuthoritiesFromJwt(user.value?.access_token)))

function hasAnyAuthority(requiredAuthorities: string[]) {
  return requiredAuthorities.some((authority) => authorities.value.has(authority))
}

const hasPlatformUserReadAuthority = computed(() => hasAnyAuthority(PLATFORM_USER_MANAGEMENT_READ_AUTHORITIES))
const hasTenantReadAuthority = computed(() => hasAnyAuthority(TENANT_MANAGEMENT_READ_AUTHORITIES))
const hasTenantUserReadAuthority = computed(() => hasAnyAuthority(USER_MANAGEMENT_READ_AUTHORITIES))

const canAccessPlatformUsers = computed(() => isPlatformScope.value && hasPlatformUserReadAuthority.value)
const canAccessTenantStewardship = computed(
  () => isPlatformScope.value && hasTenantReadAuthority.value && hasTenantUserReadAuthority.value,
)
const hasAnyAccessibleTab = computed(() => canAccessPlatformUsers.value || canAccessTenantStewardship.value)

const activeTab = ref<TabKey>('platformUsers')

function resolveRequestedTab(): TabKey {
  return route.query.tab === 'tenantStewardship' ? 'tenantStewardship' : 'platformUsers'
}

function resolveAccessibleTab(requestedTab: TabKey): TabKey {
  if (requestedTab === 'tenantStewardship' && canAccessTenantStewardship.value) {
    return 'tenantStewardship'
  }
  if (requestedTab === 'platformUsers' && canAccessPlatformUsers.value) {
    return 'platformUsers'
  }
  if (canAccessPlatformUsers.value) {
    return 'platformUsers'
  }
  return 'tenantStewardship'
}

function syncActiveTabFromRoute() {
  if (!isPlatformScope.value || !hasAnyAccessibleTab.value) {
    activeTab.value = 'platformUsers'
    return
  }
  activeTab.value = resolveAccessibleTab(resolveRequestedTab())
}

watch(
  () => [
    route.query.tab,
    isPlatformScope.value,
    canAccessPlatformUsers.value,
    canAccessTenantStewardship.value,
  ],
  () => {
    syncActiveTabFromRoute()
  },
  { immediate: true },
)

watch(activeTab, (nextTab) => {
  if (!isPlatformScope.value || !hasAnyAccessibleTab.value) {
    return
  }
  syncRouteForTab(nextTab)
})

function syncRouteForTab(nextTab: TabKey) {
  const currentTab = typeof route.query.tab === 'string' ? route.query.tab : undefined
  const currentTenantId = typeof route.query.tenantId === 'string' ? route.query.tenantId : undefined

  if (nextTab === 'platformUsers') {
    if (currentTab !== undefined || currentTenantId !== undefined) {
      void router.replace({
        path: PLATFORM_USERS_PATH,
        query: {},
      })
    }
    return
  }

  if (currentTab === 'tenantStewardship') {
    return
  }

  void router.replace({
    path: PLATFORM_USERS_PATH,
    query: currentTenantId
      ? {
          tab: 'tenantStewardship',
          tenantId: currentTenantId,
        }
      : {
          tab: 'tenantStewardship',
        },
  })
}

function handleTabChange(key: string) {
  const requestedTab: TabKey = key === 'tenantStewardship' ? 'tenantStewardship' : 'platformUsers'
  activeTab.value = resolveAccessibleTab(requestedTab)
}

const missingCapabilityLabels = computed(() => {
  const labels: string[] = []
  if (!hasPlatformUserReadAuthority.value) {
    labels.push('platform:user:list / platform:user:view')
  }
  if (!hasTenantReadAuthority.value) {
    labels.push('system:tenant:list / system:tenant:view')
  }
  if (!hasTenantUserReadAuthority.value) {
    labels.push('system:user:list / system:user:view')
  }
  return labels
})
</script>

<template>
  <div class="content-container">
    <div class="content-card platform-page-shell">
      <div v-if="!isPlatformScope" class="platform-guard-card">
        <div class="platform-guard-kicker">Platform Scope Required</div>
        <h3>当前页面只支持 PLATFORM 作用域</h3>
        <p>
          <code>/platform/users</code> 承载平台用户治理与租户用户代管能力。当前会话不在
          <code>PLATFORM</code> 作用域，因此已阻止页面继续加载。
        </p>
      </div>

      <div v-else-if="!hasAnyAccessibleTab" class="platform-guard-card">
        <div class="platform-guard-kicker">Permission Required</div>
        <h3>当前会话缺少平台用户治理所需权限</h3>
        <p>
          当前账号至少需要具备以下权限组之一，页面才会加载对应 tab：
          <code>{{ missingCapabilityLabels.join('；') }}</code>
        </p>
      </div>

      <a-tabs v-else :active-key="activeTab" class="boundary-tabs" @change="handleTabChange">
        <a-tab-pane key="platformUsers" tab="平台用户治理" :disabled="!canAccessPlatformUsers">
          <PlatformUserGovernanceTab v-if="activeTab === 'platformUsers' && canAccessPlatformUsers" />
        </a-tab-pane>

        <a-tab-pane key="tenantStewardship" tab="租户用户代管" :disabled="!canAccessTenantStewardship">
          <PlatformTenantStewardshipTab v-if="activeTab === 'tenantStewardship' && canAccessTenantStewardship" />
        </a-tab-pane>
      </a-tabs>
    </div>
  </div>
</template>

<style scoped>
.content-container {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #fff;
}

.content-card {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  background: #fff;
}

.platform-page-shell {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.platform-guard-card {
  margin: 24px;
  min-height: 420px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 12px;
  padding: 40px 32px;
  border: 1px dashed #d0d7e2;
  border-radius: 18px;
  background:
    radial-gradient(circle at top left, rgba(22, 119, 255, 0.08), transparent 45%),
    linear-gradient(180deg, #fafcff 0%, #f5f7fb 100%);
}

.platform-guard-kicker {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #1677ff;
}

.platform-guard-card h3 {
  margin: 0;
  font-size: 24px;
  color: #1f2937;
}

.platform-guard-card p {
  margin: 0;
  font-size: 14px;
  line-height: 1.75;
  color: #4b5563;
}

.boundary-tabs {
  display: flex;
  flex: 1;
  flex-direction: column;
}

:deep(.boundary-tabs > .ant-tabs-nav) {
  margin-bottom: 0;
  padding: 0 24px;
}

:deep(.boundary-tabs > .ant-tabs-nav::before) {
  border-bottom-color: #e5e7eb;
}

:deep(.boundary-tabs > .ant-tabs-nav .ant-tabs-tab) {
  padding: 10px 4px 14px;
  font-weight: 600;
}

:deep(.boundary-tabs > .ant-tabs-content-holder) {
  flex: 1;
  min-height: 0;
}

:deep(.boundary-tabs .ant-tabs-content) {
  height: 100%;
  min-height: 0;
}

:deep(.boundary-tabs .ant-tabs-tabpane) {
  height: 100%;
  min-height: 0;
}

:deep(.boundary-tabs .ant-tabs-tabpane-active) {
  display: flex;
  flex-direction: column;
}

:deep(.boundary-tabs .ant-tabs-tabpane-hidden) {
  display: none !important;
}

@media (max-width: 960px) {
  :deep(.boundary-tabs > .ant-tabs-nav) {
    padding: 0 16px;
  }

  .platform-guard-card {
    margin: 16px;
    min-height: 320px;
    padding: 28px 20px;
  }
}
</style>
