<template>
  <div class="content-container">
    <div class="content-card runtime-console-shell">
      <div v-if="!isPlatformScope" class="platform-guard-card">
        <div class="platform-guard-kicker">Platform Scope Required</div>
        <h3>当前页面只支持 PLATFORM 作用域</h3>
        <p>
          {{ consoleMeta.title }}属于平台控制台。当前会话不在
          <code>PLATFORM</code> 作用域，因此已阻止页面继续加载。
        </p>
      </div>

      <div v-else-if="!canAccessModule" class="platform-guard-card">
        <div class="platform-guard-kicker">Permission Required</div>
        <h3>当前会话缺少进入该模块平台控制台的权限</h3>
        <p>
          当前账号至少需要具备以下权限之一：
          <code>{{ consoleMeta.permissionHint }}</code>
        </p>
      </div>

      <template v-else>
        <div class="runtime-console-header">
          <div class="runtime-console-copy">
            <div class="platform-guard-kicker">{{ consoleMeta.kicker }}</div>
            <h2>{{ consoleMeta.title }}</h2>
            <p>{{ consoleMeta.description }}</p>
          </div>
          <a-button type="primary" :loading="loadingConsole" @click="loadConsoleData">
            刷新控制台
          </a-button>
        </div>

        <a-alert
          v-if="attemptedTargetNotice"
          class="runtime-alert"
          type="info"
          show-icon
          :message="attemptedTargetNotice"
        />
        <a-alert
          v-if="consoleWarnings.length > 0"
          class="runtime-alert"
          type="warning"
          show-icon
          :message="`部分平台数据加载失败：${consoleWarnings.join('；')}`"
        />

        <div class="overview-grid">
          <div v-for="card in overviewCards" :key="card.label" class="overview-card">
            <div class="overview-label">{{ card.label }}</div>
            <div class="overview-value">{{ card.value }}</div>
            <div v-if="card.hint" class="overview-hint">{{ card.hint }}</div>
          </div>
        </div>

        <div class="runtime-panels">
          <a-card class="runtime-panel" :bordered="false">
            <template #title>
              {{ consoleMeta.overviewTitle }}
            </template>

            <div v-if="moduleKey === 'process'" class="runtime-section-stack">
              <a-form layout="inline" class="tenant-filter-form">
                <a-form-item label="记录租户">
                  <a-select
                    v-model:value="selectedProcessTenantId"
                    allow-clear
                    show-search
                    placeholder="全部租户"
                    style="min-width: 240px"
                    :filter-option="filterTenantOption"
                  >
                    <a-select-option
                      v-for="tenant in tenantEntries"
                      :key="tenant.id"
                      :value="String(tenant.id)"
                      :label="tenantOptionLabel(tenant)"
                    >
                      {{ tenantOptionLabel(tenant) }}
                    </a-select-option>
                  </a-select>
                </a-form-item>
              </a-form>

              <section class="runtime-section">
                <div class="runtime-section-title">引擎状态</div>
                <pre class="runtime-json">{{ formatObject(processEngineInfo) }}</pre>
              </section>

              <section class="runtime-section">
                <div class="runtime-section-title">最近部署</div>
                <ul class="record-list">
                  <li v-for="deployment in recentDeployments" :key="deployment.id">
                    <strong>{{ deployment.name || deployment.id }}</strong>
                    <span>租户 {{ deployment.recordTenantId || '-' }}</span>
                  </li>
                  <li v-if="recentDeployments.length === 0" class="empty-item">暂无部署数据</li>
                </ul>
              </section>

              <section class="runtime-section">
                <div class="runtime-section-title">最近流程定义</div>
                <ul class="record-list">
                  <li v-for="definition in recentDefinitions" :key="definition.id">
                    <strong>{{ definition.name || definition.key || definition.id }}</strong>
                    <span>租户 {{ definition.recordTenantId || '-' }}</span>
                  </li>
                  <li v-if="recentDefinitions.length === 0" class="empty-item">暂无流程定义数据</li>
                </ul>
              </section>

              <section class="runtime-section">
                <div class="runtime-section-title">最近实例</div>
                <ul class="record-list">
                  <li v-for="instance in recentInstances" :key="instance.id">
                    <strong>{{
                      instance.processDefinitionName || instance.processKey || instance.id
                    }}</strong>
                    <span>租户 {{ instance.recordTenantId || '-' }}</span>
                  </li>
                  <li v-if="recentInstances.length === 0" class="empty-item">暂无流程实例数据</li>
                </ul>
              </section>
            </div>

            <div v-else class="runtime-section-stack">
              <section class="runtime-section">
                <div class="runtime-section-title">Quartz 集群状态</div>
                <pre class="runtime-json">{{ formatObject(schedulingClusterStatus) }}</pre>
              </section>

              <section class="runtime-section">
                <div class="runtime-section-title">已注册执行器</div>
                <div v-if="schedulingExecutors.length > 0" class="pill-list">
                  <span v-for="executor in schedulingExecutors" :key="executor" class="pill">
                    {{ executor }}
                  </span>
                </div>
                <div v-else class="empty-item">暂无执行器数据</div>
              </section>

              <section class="runtime-section">
                <div class="runtime-section-title">平台调度说明</div>
                <p class="runtime-body-copy">
                  当前平台控制台先承载调度集群状态、执行器注册与租户运行入口。具体
                  DAG、任务、运行编排仍保留在租户运行面中，由平台用户显式选择租户后进入。
                </p>
              </section>
            </div>
          </a-card>

          <a-card class="runtime-panel" :bordered="false">
            <template #title>
              {{ consoleMeta.runtimeEntryTitle }}
            </template>

            <p class="runtime-body-copy">
              {{ consoleMeta.runtimeEntryDescription }}
            </p>

            <a-form layout="vertical" class="entry-form">
              <a-form-item label="目标租户">
                <a-select
                  v-model:value="selectedTenantId"
                  allow-clear
                  show-search
                  placeholder="请选择目标租户"
                  :filter-option="filterTenantOption"
                >
                  <a-select-option
                    v-for="tenant in tenantEntries"
                    :key="tenant.id"
                    :value="tenant.id"
                    :label="tenantOptionLabel(tenant)"
                    :disabled="!canEnterTenant(tenant)"
                  >
                    {{ tenantOptionLabel(tenant) }}
                  </a-select-option>
                </a-select>
              </a-form-item>

              <a-form-item label="进入路径">
                <a-input :value="resolvedTargetPath" readonly />
              </a-form-item>

              <a-space>
                <a-button type="primary" :loading="entering" @click="enterTenantModule">
                  {{ consoleMeta.enterButtonText }}
                </a-button>
                <a-button :loading="loadingConsole" @click="loadTenants">刷新租户列表</a-button>
              </a-space>
            </a-form>

            <section class="runtime-section tenant-preview-section">
              <div class="runtime-section-title">租户状态速览</div>
              <ul class="record-list">
                <li v-for="tenant in previewTenants" :key="tenant.id">
                  <strong>{{ tenant.name || `租户 ${tenant.id}` }}</strong>
                  <span>{{ tenantOptionLabel(tenant) }}</span>
                </li>
                <li v-if="previewTenants.length === 0" class="empty-item">暂无可展示的租户</li>
              </ul>
            </section>
          </a-card>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import type { DefaultOptionType } from 'ant-design-vue/es/select'
import { message } from 'ant-design-vue'
import { useRoute, useRouter } from 'vue-router'
import { refreshTokenAfterActiveScopeSwitch, useAuth } from '@/auth/auth'
import { getCurrentUser, switchActiveScope } from '@/api/user'
import { getTenantById, tenantList, type Tenant } from '@/api/tenant'
import {
  deploymentApi,
  historyApi,
  instanceApi,
  maintenanceApi,
  processApi,
  type Deployment,
  type ProcessDefinition,
  type ProcessInstance,
} from '@/api/process'
import { getExecutors, getQuartzClusterStatus, type QuartzClusterStatus } from '@/api/scheduling'
import { notifyActiveScopeChanged } from '@/utils/activeScopeEvents'
import { usePlatformScope } from '@/composables/usePlatformScope'
import { extractAuthoritiesFromJwt } from '@/utils/jwt'
import { sanitizeInternalRedirect } from '@/utils/redirect'
import {
  setActiveTenantId,
  setLoginMode,
  setTenantCode,
  withActiveTenantQuery,
} from '@/utils/tenant'
import {
  SCHEDULING_AUDIT_VIEW,
  SCHEDULING_ENTRY_VIEW,
  SCHEDULING_CLUSTER_VIEW,
  SCHEDULING_CONSOLE_CONFIG,
  SCHEDULING_CONSOLE_VIEW,
  SCHEDULING_RUN_CONTROL,
  SCHEDULING_WILDCARD,
  WORKFLOW_CONSOLE_CONFIG,
  WORKFLOW_CONSOLE_VIEW,
  WORKFLOW_INSTANCE_CONTROL,
  WORKFLOW_TENANT_MANAGE,
} from '@/constants/permission'

type RuntimeModuleKey = 'scheduling' | 'process'

type BridgeMeta = {
  kicker: string
  title: string
  description: string
  permissionHint: string
  defaultRuntimeTarget: string
  targetPrefix: string
  overviewTitle: string
  runtimeEntryTitle: string
  runtimeEntryDescription: string
  enterButtonText: string
}

type OverviewCard = {
  label: string
  value: string
  hint?: string
}

type ProcessHealth = {
  status?: string
  message?: string
  timestamp?: number
}

const route = useRoute()
const router = useRouter()
const { isPlatformScope } = usePlatformScope()
const { user } = useAuth()

const tenantEntries = ref<Tenant[]>([])
const loadingConsole = ref(false)
const entering = ref(false)
const selectedTenantId = ref<number | undefined>(undefined)
const selectedProcessTenantId = ref<string | undefined>(undefined)
const consoleWarnings = ref<string[]>([])
const schedulingExecutors = ref<string[]>([])
const schedulingClusterStatus = ref<QuartzClusterStatus | null>(null)
const processEngineInfo = ref<unknown>(null)
const processHealth = ref<ProcessHealth | null>(null)
const workflowDeployments = ref<Deployment[]>([])
const workflowDefinitions = ref<ProcessDefinition[]>([])
const workflowInstances = ref<ProcessInstance[]>([])
const workflowHistoricInstances = ref<Array<Record<string, unknown>>>([])

const bridgeMetaMap: Record<RuntimeModuleKey, BridgeMeta> = {
  scheduling: {
    kicker: 'Platform Scheduling',
    title: '平台调度控制台',
    description:
      '平台侧先承载调度模块的集群与治理入口，不再把平台菜单直接落到租户运行页。进入具体租户运行面必须由平台用户显式确认。',
    permissionHint:
      'scheduling:entry:view / scheduling:console:view / scheduling:console:config / scheduling:run:control / scheduling:audit:view / scheduling:cluster:view / scheduling:*',
    defaultRuntimeTarget: '/scheduling/dag',
    targetPrefix: '/scheduling',
    overviewTitle: '平台调度总览',
    runtimeEntryTitle: '租户调度运行面',
    runtimeEntryDescription:
      '若要查看或操作某个租户下的 DAG / 任务 / 运行历史，请在平台侧明确选择目标租户后再进入租户运行面。',
    enterButtonText: '进入租户调度运行面',
  },
  process: {
    kicker: 'Platform Workflow',
    title: '平台工作流控制台',
    description:
      '平台侧直接提供工作流模块的跨租户只读总览与租户运行入口，不再把平台菜单默认解释成“必须先切租户”。',
    permissionHint:
      'workflow:console:view / workflow:console:config / workflow:instance:control / workflow:tenant:manage / workflow:*',
    defaultRuntimeTarget: '/process/modeling',
    targetPrefix: '/process',
    overviewTitle: '平台工作流总览',
    runtimeEntryTitle: '租户工作流运行面',
    runtimeEntryDescription:
      '若要进入某个租户的建模、部署、实例或任务运行页，请明确选择目标租户；平台控制台本身继续保留总览职责。',
    enterButtonText: '进入租户工作流运行面',
  },
}

const moduleKey = computed<RuntimeModuleKey>(() =>
  route.path.startsWith('/platform/process') ? 'process' : 'scheduling',
)
const consoleMeta = computed(() => bridgeMetaMap[moduleKey.value])

const authorities = computed(() => new Set(extractAuthoritiesFromJwt(user.value?.access_token)))

function hasAnyAuthority(requiredAuthorities: string[]) {
  return requiredAuthorities.some((authority) => authorities.value.has(authority))
}

const canAccessModule = computed(() =>
  moduleKey.value === 'scheduling'
    ? hasAnyAuthority([
        SCHEDULING_ENTRY_VIEW,
        SCHEDULING_CONSOLE_VIEW,
        SCHEDULING_CONSOLE_CONFIG,
        SCHEDULING_RUN_CONTROL,
        SCHEDULING_AUDIT_VIEW,
        SCHEDULING_CLUSTER_VIEW,
        SCHEDULING_WILDCARD,
      ])
    : hasAnyAuthority([
        WORKFLOW_CONSOLE_VIEW,
        WORKFLOW_CONSOLE_CONFIG,
        WORKFLOW_INSTANCE_CONTROL,
        WORKFLOW_TENANT_MANAGE,
        'workflow:*',
      ]),
)

const tenantSummary = computed(() => {
  const total = tenantEntries.value.length
  const active = tenantEntries.value.filter(
    (tenant) =>
      tenant.enabled !== false &&
      tenant.lifecycleStatus !== 'FROZEN' &&
      tenant.lifecycleStatus !== 'DECOMMISSIONED',
  ).length
  const frozen = tenantEntries.value.filter((tenant) => tenant.lifecycleStatus === 'FROZEN').length
  const disabled = tenantEntries.value.filter((tenant) => tenant.enabled === false).length
  return { total, active, frozen, disabled }
})

const recentDeployments = computed(() => workflowDeployments.value.slice(0, 5))
const recentDefinitions = computed(() => workflowDefinitions.value.slice(0, 5))
const recentInstances = computed(() => workflowInstances.value.slice(0, 5))
const previewTenants = computed(() => tenantEntries.value.slice(0, 8))

const overviewCards = computed<OverviewCard[]>(() => {
  if (moduleKey.value === 'process') {
    return [
      {
        label: '租户数量',
        value: String(tenantSummary.value.total),
        hint:
          tenantSummary.value.active > 0
            ? `${tenantSummary.value.active} 个可进入`
            : '暂无可进入租户',
      },
      {
        label: '引擎健康',
        value: processHealth.value?.status || 'UNKNOWN',
        hint: processHealth.value?.message || '尚未读取健康信息',
      },
      {
        label: '部署数量',
        value: String(workflowDeployments.value.length),
        hint: selectedProcessTenantId.value
          ? `租户 ${selectedProcessTenantId.value} 过滤`
          : '当前为跨租户只读总览',
      },
      {
        label: '运行中实例',
        value: String(workflowInstances.value.filter((instance) => !instance.endTime).length),
        hint: `历史实例 ${workflowHistoricInstances.value.length}`,
      },
    ]
  }

  return [
    {
      label: '租户数量',
      value: String(tenantSummary.value.total),
      hint:
        tenantSummary.value.active > 0
          ? `${tenantSummary.value.active} 个可进入`
          : '暂无可进入租户',
    },
    {
      label: 'Quartz 状态',
      value: schedulingClusterStatus.value?.status || 'UNKNOWN',
      hint: schedulingClusterStatus.value?.clusterMode || '尚未读取集群状态',
    },
    {
      label: '执行器数量',
      value: String(schedulingExecutors.value.length),
      hint: schedulingExecutors.value.length > 0 ? '已加载注册执行器' : '尚未读取执行器列表',
    },
    {
      label: '冻结租户',
      value: String(tenantSummary.value.frozen),
      hint:
        tenantSummary.value.disabled > 0
          ? `${tenantSummary.value.disabled} 个已禁用`
          : '无禁用租户',
    },
  ]
})

const attemptedTargetNotice = computed(() => {
  const rawValue = Array.isArray(route.query.target) ? route.query.target[0] : route.query.target
  if (typeof rawValue !== 'string' || !rawValue.trim()) {
    return ''
  }
  return `你刚才尝试打开 ${resolvedTargetPath.value}。平台控制台已接管该入口；若要进入具体租户运行面，请在右侧明确选择租户。`
})

function normalizeTenantRecord(tenant: Tenant): Tenant {
  return {
    ...tenant,
    code: String(tenant.code || '').trim(),
    name: String(tenant.name || '').trim(),
  }
}

function parsePositiveInt(value: unknown): number | undefined {
  const candidate = Array.isArray(value) ? value[0] : value
  const parsed = Number(candidate)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : undefined
}

function tenantOptionLabel(tenant: Tenant): string {
  const baseLabel = tenant.code
    ? `${tenant.name || `租户 ${tenant.id}`} (${tenant.code})`
    : tenant.name || `租户 ${tenant.id}`
  const statusLabel = tenant.lifecycleStatus ? ` / ${tenant.lifecycleStatus}` : ''
  const enabledLabel = tenant.enabled === false ? ' / DISABLED' : ''
  return `${baseLabel}${statusLabel}${enabledLabel}`
}

function canEnterTenant(tenant: Tenant): boolean {
  if (tenant.enabled === false) {
    return false
  }
  return tenant.lifecycleStatus !== 'FROZEN' && tenant.lifecycleStatus !== 'DECOMMISSIONED'
}

function filterTenantOption(input: string, option?: DefaultOptionType) {
  const label = String(option?.label ?? '').toLowerCase()
  return label.includes((input || '').toLowerCase())
}

function resolveRawTarget(): string {
  const rawValue = Array.isArray(route.query.target) ? route.query.target[0] : route.query.target
  const fallback = consoleMeta.value.defaultRuntimeTarget
  if (typeof rawValue !== 'string' || !rawValue.trim()) {
    return fallback
  }
  const sanitized = sanitizeInternalRedirect(rawValue)
  return sanitized.startsWith(consoleMeta.value.targetPrefix) ? sanitized : fallback
}

const resolvedTargetPath = computed(() => resolveRawTarget())

function resolveTargetRoute(tenantId: number) {
  try {
    const url = new URL(resolvedTargetPath.value, window.location.origin)
    const query = Object.fromEntries(url.searchParams.entries())
    return {
      path: url.pathname,
      query: withActiveTenantQuery(query, tenantId),
    }
  } catch {
    return {
      path: consoleMeta.value.defaultRuntimeTarget,
      query: withActiveTenantQuery({}, tenantId),
    }
  }
}

async function applyRequestedTenantSelection() {
  const requestedTenantId = parsePositiveInt(route.query.tenantId)
  if (!requestedTenantId) {
    const onlyTenant = tenantEntries.value.length === 1 ? tenantEntries.value[0] : undefined
    if (onlyTenant) {
      selectedTenantId.value = onlyTenant.id
    }
    return
  }
  selectedTenantId.value = requestedTenantId
  selectedProcessTenantId.value = String(requestedTenantId)
  const found = tenantEntries.value.find((tenant) => tenant.id === requestedTenantId)
  if (found) {
    return
  }
  try {
    const tenant = normalizeTenantRecord(await getTenantById(requestedTenantId))
    tenantEntries.value = [...tenantEntries.value, tenant].sort((left, right) => left.id - right.id)
  } catch {
    pushWarning('目标租户未找到')
  }
}

function pushWarning(messageText: string) {
  if (!consoleWarnings.value.includes(messageText)) {
    consoleWarnings.value = [...consoleWarnings.value, messageText]
  }
}

async function loadTenants() {
  try {
    const result = await tenantList({
      page: 0,
      size: 200,
      includeDeleted: false,
    })
    tenantEntries.value = Array.isArray(result.content)
      ? result.content
          .map((tenant) => normalizeTenantRecord(tenant))
          .sort((left, right) => left.id - right.id)
      : []
    await applyRequestedTenantSelection()
  } catch {
    tenantEntries.value = []
    pushWarning('租户列表')
  }
}

async function loadProcessOverview() {
  const recordTenantId = selectedProcessTenantId.value
  const results = await Promise.allSettled([
    maintenanceApi.getEngineInfo(),
    maintenanceApi.healthCheck(),
    deploymentApi.getDeployments(recordTenantId),
    processApi.getProcessDefinitions(recordTenantId),
    instanceApi.getProcessInstances(recordTenantId),
    historyApi.getHistoricInstances(recordTenantId),
  ])

  const [
    engineInfoResult,
    healthResult,
    deploymentsResult,
    definitionsResult,
    instancesResult,
    historyResult,
  ] = results

  if (engineInfoResult.status === 'fulfilled') {
    processEngineInfo.value = engineInfoResult.value
  } else {
    processEngineInfo.value = null
    pushWarning('工作流引擎信息')
  }

  if (healthResult.status === 'fulfilled') {
    processHealth.value = healthResult.value
  } else {
    processHealth.value = null
    pushWarning('工作流健康状态')
  }

  if (deploymentsResult.status === 'fulfilled') {
    workflowDeployments.value = Array.isArray(deploymentsResult.value)
      ? deploymentsResult.value
      : []
  } else {
    workflowDeployments.value = []
    pushWarning('工作流部署列表')
  }

  if (definitionsResult.status === 'fulfilled') {
    workflowDefinitions.value = Array.isArray(definitionsResult.value)
      ? definitionsResult.value
      : []
  } else {
    workflowDefinitions.value = []
    pushWarning('工作流定义列表')
  }

  if (instancesResult.status === 'fulfilled') {
    workflowInstances.value = Array.isArray(instancesResult.value) ? instancesResult.value : []
  } else {
    workflowInstances.value = []
    pushWarning('工作流实例列表')
  }

  if (historyResult.status === 'fulfilled') {
    workflowHistoricInstances.value = Array.isArray(historyResult.value)
      ? (historyResult.value as Array<Record<string, unknown>>)
      : []
  } else {
    workflowHistoricInstances.value = []
    pushWarning('工作流历史实例')
  }
}

async function loadSchedulingOverview() {
  const results = await Promise.allSettled([getQuartzClusterStatus(), getExecutors()])
  const [clusterResult, executorsResult] = results

  if (clusterResult.status === 'fulfilled') {
    schedulingClusterStatus.value = clusterResult.value
  } else {
    schedulingClusterStatus.value = null
    pushWarning('Quartz 集群状态')
  }

  if (executorsResult.status === 'fulfilled') {
    schedulingExecutors.value = Array.isArray(executorsResult.value) ? executorsResult.value : []
  } else {
    schedulingExecutors.value = []
    pushWarning('调度执行器列表')
  }
}

async function loadConsoleData() {
  if (!isPlatformScope.value || !canAccessModule.value) {
    return
  }
  loadingConsole.value = true
  consoleWarnings.value = []
  try {
    await Promise.all([
      loadTenants(),
      moduleKey.value === 'process' ? loadProcessOverview() : loadSchedulingOverview(),
    ])
  } finally {
    loadingConsole.value = false
  }
}

function formatObject(value: unknown): string {
  if (value == null) {
    return '暂无数据'
  }
  if (typeof value === 'string') {
    return value
  }
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}

async function enterTenantModule() {
  const tenant = tenantEntries.value.find((item) => item.id === selectedTenantId.value)
  if (!tenant) {
    message.warning('请先选择目标租户')
    return
  }
  if (!canEnterTenant(tenant)) {
    message.warning('目标租户当前不可进入，请选择正常租户')
    return
  }

  entering.value = true
  try {
    const switchResult = await switchActiveScope({
      scopeType: 'TENANT',
      scopeId: tenant.id,
    })

    setLoginMode('TENANT')
    if (tenant.code) {
      setTenantCode(tenant.code)
    }
    setActiveTenantId(tenant.id)

    if (switchResult.tokenRefreshRequired === true) {
      const renew = await refreshTokenAfterActiveScopeSwitch()
      if (!renew.ok) {
        message.warning('作用域已切换到目标租户，但访问令牌刷新失败，请重新登录后再继续。')
        return
      }
    }

    await getCurrentUser()
    notifyActiveScopeChanged()
    window.dispatchEvent(new CustomEvent('reload-menu-tree'))
    await router.replace(resolveTargetRoute(tenant.id))
    message.success(`${consoleMeta.value.title}已切换到目标租户运行面`)
  } catch (error: any) {
    message.error(error?.message || '进入目标租户运行面失败')
  } finally {
    entering.value = false
  }
}

watch(
  () => moduleKey.value,
  () => {
    schedulingExecutors.value = []
    schedulingClusterStatus.value = null
    processEngineInfo.value = null
    processHealth.value = null
    workflowDeployments.value = []
    workflowDefinitions.value = []
    workflowInstances.value = []
    workflowHistoricInstances.value = []
    void loadConsoleData()
  },
  { immediate: true },
)

watch(selectedProcessTenantId, () => {
  if (moduleKey.value !== 'process' || !isPlatformScope.value || !canAccessModule.value) {
    return
  }
  void loadProcessOverview()
})

defineExpose({
  enterTenantModule,
  loadConsoleData,
  resolvedTargetPath,
  selectedTenantId,
})
</script>

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

.runtime-console-shell {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 18px;
  min-height: 0;
  padding: 24px;
}

.platform-guard-card {
  margin: auto 0;
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

.platform-guard-card h3,
.runtime-console-copy h2 {
  margin: 0;
}

.runtime-console-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.runtime-console-copy {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.runtime-console-copy p,
.runtime-body-copy {
  margin: 0;
  color: #4b5563;
  line-height: 1.65;
}

.runtime-alert {
  margin-bottom: 4px;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
}

.overview-card {
  padding: 18px;
  border-radius: 16px;
  background: linear-gradient(180deg, #fafcff 0%, #f4f7fb 100%);
  border: 1px solid #e5eaf2;
}

.overview-label {
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: #6b7280;
}

.overview-value {
  margin-top: 10px;
  font-size: 28px;
  font-weight: 700;
  color: #111827;
}

.overview-hint {
  margin-top: 8px;
  font-size: 12px;
  color: #6b7280;
}

.runtime-panels {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(320px, 0.9fr);
  gap: 16px;
}

.runtime-panel {
  min-height: 0;
}

.runtime-section-stack {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.tenant-filter-form {
  margin-bottom: 8px;
}

.runtime-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.runtime-section-title {
  font-size: 14px;
  font-weight: 600;
  color: #111827;
}

.runtime-json {
  margin: 0;
  padding: 12px;
  border-radius: 12px;
  background: #f7f8fa;
  border: 1px solid #eceff4;
  font-size: 12px;
  line-height: 1.55;
  overflow: auto;
}

.pill-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.pill {
  padding: 6px 10px;
  border-radius: 999px;
  background: #eef4ff;
  color: #1d4ed8;
  font-size: 12px;
}

.entry-form {
  margin-top: 16px;
}

.tenant-preview-section {
  margin-top: 20px;
}

.record-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.record-list li {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 12px;
  border-radius: 12px;
  background: #f9fafb;
  border: 1px solid #edf0f4;
  color: #4b5563;
}

.record-list li strong {
  color: #111827;
}

.empty-item {
  color: #6b7280;
}

@media (max-width: 1080px) {
  .runtime-panels {
    grid-template-columns: 1fr;
  }

  .runtime-console-header {
    flex-direction: column;
  }
}
</style>
