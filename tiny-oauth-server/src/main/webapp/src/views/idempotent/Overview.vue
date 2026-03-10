<script setup lang="ts">
import { ArrowLeftOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  getIdempotentMetrics,
  getIdempotentMqMetrics,
  getIdempotentTopKeys,
  type IdempotentMetricsSnapshot,
  type IdempotentMqMetricsSnapshot,
  type IdempotentTopKey,
} from '@/api/idempotent'
import { tenantList, type Tenant } from '@/api/tenant'

const router = useRouter()
const route = useRoute()
const ALL_TENANT_FILTER = 'all'
const TENANT_PAGE_SIZE = 200
const MAX_TENANT_FILTER_PAGES = 10

const emptyMetrics: IdempotentMetricsSnapshot = {
  windowMinutes: 60,
  windowStartEpochMillis: 0,
  windowEndEpochMillis: 0,
  passCount: 0,
  hitCount: 0,
  successCount: 0,
  failureCount: 0,
  storeErrorCount: 0,
  validationRejectCount: 0,
  rejectCount: 0,
  totalCheckCount: 0,
  conflictRate: 0,
  storageErrorRate: 0,
}

const emptyMqMetrics: IdempotentMqMetricsSnapshot = {
  windowMinutes: 60,
  windowStartEpochMillis: 0,
  windowEndEpochMillis: 0,
  successCount: 0,
  failureCount: 0,
  duplicateRate: 0,
}

const topScopeColumns = [
  {
    title: '热点 Scope',
    dataIndex: 'key',
    key: 'key',
    ellipsis: true,
  },
  {
    title: '命中次数',
    dataIndex: 'count',
    key: 'count',
    width: 120,
    align: 'right',
  },
]

const loading = ref(false)
const accessDenied = ref(false)
const tenantLoading = ref(false)
const tenantLoadFailed = ref(false)
const metrics = ref<IdempotentMetricsSnapshot>(emptyMetrics)
const mqMetrics = ref<IdempotentMqMetricsSnapshot>(emptyMqMetrics)
const topKeys = ref<IdempotentTopKey[]>([])
const tenantOptions = ref<Tenant[]>([])
const selectedTenantFilter = ref<number | string>(normalizeTenantFilterValue(route.query.tenantId))
const lastUpdatedAt = ref('')

const selectedTenantId = computed(() =>
  typeof selectedTenantFilter.value === 'number' && selectedTenantFilter.value > 0
    ? selectedTenantFilter.value
    : undefined,
)

const tenantFilterOptions = computed(() => [
  { label: '平台汇总', value: ALL_TENANT_FILTER },
  ...tenantOptions.value.map((tenant) => ({
    label: `${tenant.name} (${tenant.code})`,
    value: tenant.id,
  })),
])

const selectedTenantLabel = computed(() => {
  if (selectedTenantId.value == null) {
    return '平台汇总'
  }
  const tenant = tenantOptions.value.find((item) => item.id === selectedTenantId.value)
  if (tenant) {
    return `${tenant.name} (${tenant.code})`
  }
  return `租户 #${selectedTenantId.value}`
})

const successRate = computed(() => {
  if (metrics.value.passCount <= 0) {
    return 0
  }
  return metrics.value.successCount / metrics.value.passCount
})

const systemTone = computed(() => {
  if (metrics.value.storeErrorCount > 0) {
    return 'error'
  }
  if (metrics.value.validationRejectCount > 0 || mqMetrics.value.failureCount > 0) {
    return 'warning'
  }
  if (metrics.value.hitCount > 0) {
    return 'processing'
  }
  return 'success'
})

const metricsWindowLabel = computed(() => `最近 ${metrics.value.windowMinutes || 60} 分钟`)

const recommendations = computed(() => {
  const items: Array<{ tone: string; title: string; detail: string }> = []

  if (metrics.value.storeErrorCount > 0) {
    items.push({
      tone: 'red',
      title: '优先排查仓储可用性',
      detail: '幂等存储已经发生异常，先确认数据库连接、索引和迁移状态是否稳定。',
    })
  }

  if (metrics.value.validationRejectCount > 0) {
    items.push({
      tone: 'gold',
      title: '检查客户端 Key 生成规则',
      detail: '存在非法 X-Idempotency-Key，被拒绝的请求需要回看前端封装或第三方调用方。',
    })
  }

  if (metrics.value.conflictRate >= 0.2) {
    items.push({
      tone: 'blue',
      title: '关注热点重复提交',
      detail: '冲突率偏高，优先检查热点 Scope 是否仍在使用固定 key 处理命令型操作。',
    })
  }

  if (mqMetrics.value.failureCount > 0) {
    items.push({
      tone: 'orange',
      title: '复核消息消费补偿',
      detail: 'MQ 幂等存在失败消费，建议核对重试、补偿和死信处理是否按预期工作。',
    })
  }

  if (items.length === 0) {
    items.push({
      tone: 'green',
      title: '当前链路整体稳定',
      detail: '没有明显异常指标，可持续关注热点 Scope 和冲突趋势变化。',
    })
  }

  return items
})

function formatPercent(value: number): string {
  return `${(value * 100).toFixed(value < 0.1 ? 1 : 0)}%`
}

function toProgress(value: number): number {
  return Math.max(0, Math.min(100, Number((value * 100).toFixed(1))))
}

function updateTimestamp() {
  lastUpdatedAt.value = new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).format(new Date())
}

function goBack() {
  void router.push('/')
}

function normalizeTenantFilterValue(value: unknown): number | string {
  const candidate = Array.isArray(value) ? value[0] : value
  if (typeof candidate === 'number' && Number.isInteger(candidate) && candidate > 0) {
    return candidate
  }
  if (typeof candidate === 'string' && /^\d+$/.test(candidate.trim())) {
    const tenantId = Number(candidate.trim())
    return tenantId > 0 ? tenantId : ALL_TENANT_FILTER
  }
  return ALL_TENANT_FILTER
}

async function syncTenantFilterToRoute(value: number | string) {
  const normalized = normalizeTenantFilterValue(value)
  const current = normalizeTenantFilterValue(route.query.tenantId)
  if (normalized === current) {
    return
  }

  const nextQuery = { ...route.query }
  if (normalized === ALL_TENANT_FILTER) {
    delete nextQuery.tenantId
  } else {
    nextQuery.tenantId = String(normalized)
  }
  await router.replace({ query: nextQuery })
}

async function loadTenantOptions() {
  tenantLoading.value = true
  tenantLoadFailed.value = false
  try {
    const tenants: Tenant[] = []
    let page = 0
    let total = Number.POSITIVE_INFINITY

    while (page < MAX_TENANT_FILTER_PAGES && tenants.length < total) {
      const response = await tenantList({ page, size: TENANT_PAGE_SIZE })
      const content = Array.isArray(response?.content) ? response.content : []
      tenants.push(...content)
      total = Number(response?.totalElements ?? content.length)
      if (content.length < TENANT_PAGE_SIZE) {
        break
      }
      page += 1
    }

    const uniqueTenants = new Map<number, Tenant>()
    tenants.forEach((tenant) => {
      if (typeof tenant?.id === 'number' && tenant.id > 0) {
        uniqueTenants.set(tenant.id, tenant)
      }
    })
    tenantOptions.value = Array.from(uniqueTenants.values()).sort((left, right) => left.id - right.id)
  } catch (error) {
    tenantLoadFailed.value = true
    tenantOptions.value = []
    console.warn('加载租户筛选列表失败:', error)
  } finally {
    tenantLoading.value = false
  }
}

async function handleTenantFilterChange(value: number | string | undefined) {
  selectedTenantFilter.value = normalizeTenantFilterValue(value)
  await syncTenantFilterToRoute(selectedTenantFilter.value)
  void loadOverview()
}

async function loadOverview() {
  loading.value = true
  accessDenied.value = false
  try {
    const tenantId = selectedTenantId.value
    const [metricsSnapshot, hotScopes, mqSnapshot] = await Promise.all([
      getIdempotentMetrics(tenantId),
      getIdempotentTopKeys(10, tenantId),
      getIdempotentMqMetrics(tenantId),
    ])
    metrics.value = metricsSnapshot
    topKeys.value = hotScopes
    mqMetrics.value = mqSnapshot
    updateTimestamp()
  } catch (error) {
    if ((error as { response?: { status?: number } })?.response?.status === 403) {
      accessDenied.value = true
      metrics.value = { ...emptyMetrics }
      mqMetrics.value = { ...emptyMqMetrics }
      topKeys.value = []
      message.warning('幂等治理页仅对平台管理员开放')
      return
    }
    console.error('加载幂等治理页失败:', error)
    message.error('加载幂等治理页失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void Promise.all([loadTenantOptions(), loadOverview()])
})

watch(
  () => route.query.tenantId,
  (value) => {
    const normalized = normalizeTenantFilterValue(value)
    if (normalized === selectedTenantFilter.value) {
      return
    }
    selectedTenantFilter.value = normalized
    void loadOverview()
  },
)
</script>

<template>
  <div class="ops-shell">
    <section class="ops-hero">
      <div class="ops-copy">
        <p class="ops-kicker">Idempotency Ops</p>
        <h2>幂等治理页</h2>
        <p class="ops-summary">
          这里聚合 HTTP 与 MQ 两条链路的幂等健康度，用来快速定位非法 key、重复命中和仓储异常是否正在放大。
        </p>
        <div class="ops-filter-bar">
          <div class="ops-filter-copy">
            <span class="ops-filter-label">当前视角</span>
            <strong>{{ selectedTenantLabel }}</strong>
            <p v-if="tenantLoadFailed">租户列表加载失败，当前仍可查看平台汇总指标。</p>
          </div>
          <a-select
            class="tenant-filter"
            :value="selectedTenantFilter"
            :options="tenantFilterOptions"
            :loading="tenantLoading"
            :disabled="accessDenied"
            show-search
            option-filter-prop="label"
            data-testid="idempotent-tenant-filter"
            @change="handleTenantFilterChange"
          />
        </div>
        <div class="ops-meta">
          <a-tag :color="systemTone">
            {{
              systemTone === 'success'
                ? '稳定'
                : systemTone === 'processing'
                  ? '有命中流量'
                  : systemTone === 'warning'
                    ? '需关注'
                    : '高风险'
            }}
          </a-tag>
          <span>筛选视角：{{ selectedTenantLabel }}</span>
          <span>统计窗口：{{ metricsWindowLabel }}</span>
          <span>最近刷新：{{ lastUpdatedAt || '未刷新' }}</span>
        </div>
      </div>
      <a-space wrap class="ops-actions">
        <a-button @click="goBack">
          <template #icon>
            <ArrowLeftOutlined />
          </template>
          返回工作台
        </a-button>
        <a-button type="primary" :loading="loading" @click="loadOverview">
          <template #icon>
            <ReloadOutlined />
          </template>
          刷新治理数据
        </a-button>
      </a-space>
    </section>

    <a-card v-if="accessDenied" class="panel-card restricted-card" :bordered="false">
      <p class="restricted-title">无权查看平台级幂等治理指标</p>
      <p class="restricted-copy">
        当前页面承载的是平台级汇总指标，后端已限制为默认平台租户下具备 `ROLE_ADMIN + idempotentOps` 权限的管理员访问。
      </p>
    </a-card>

    <template v-else>
      <a-row :gutter="[16, 16]">
      <a-col :xs="24" :md="12" :xl="6">
        <a-card class="stat-card stat-card--pass" :loading="loading" :bordered="false">
          <a-statistic title="通过请求" :value="metrics.passCount" />
          <p class="stat-footnote">首次进入保护链路的请求量</p>
        </a-card>
      </a-col>
      <a-col :xs="24" :md="12" :xl="6">
        <a-card class="stat-card stat-card--hit" :loading="loading" :bordered="false">
          <a-statistic title="重复命中" :value="metrics.hitCount" />
          <p class="stat-footnote">被判定为重复提交的请求次数</p>
        </a-card>
      </a-col>
      <a-col :xs="24" :md="12" :xl="6">
        <a-card class="stat-card stat-card--warning" :loading="loading" :bordered="false">
          <a-statistic title="非法 Key" :value="metrics.validationRejectCount" />
          <p class="stat-footnote">服务端直接拒绝的脏键</p>
        </a-card>
      </a-col>
      <a-col :xs="24" :md="12" :xl="6">
        <a-card class="stat-card stat-card--danger" :loading="loading" :bordered="false">
          <a-statistic title="存储异常" :value="metrics.storeErrorCount" />
          <p class="stat-footnote">幂等仓储读写或可用性失败</p>
        </a-card>
      </a-col>
      </a-row>

      <a-row :gutter="[16, 16]" class="panel-grid">
      <a-col :xs="24" :xl="15">
        <a-card class="panel-card" :loading="loading" :bordered="false" title="链路质量">
          <div class="progress-stack">
            <div class="progress-item">
              <div class="progress-head">
                <span>执行成功率</span>
                <strong>{{ formatPercent(successRate) }}</strong>
              </div>
              <a-progress :percent="toProgress(successRate)" :show-info="false" stroke-color="#0b8c68" />
            </div>
            <div class="progress-item">
              <div class="progress-head">
                <span>冲突率</span>
                <strong>{{ formatPercent(metrics.conflictRate) }}</strong>
              </div>
              <a-progress :percent="toProgress(metrics.conflictRate)" :show-info="false" stroke-color="#1b6ac3" />
            </div>
            <div class="progress-item">
              <div class="progress-head">
                <span>存储异常率</span>
                <strong>{{ formatPercent(metrics.storageErrorRate) }}</strong>
              </div>
              <a-progress
                :percent="toProgress(metrics.storageErrorRate)"
                :show-info="false"
                stroke-color="#c1332d"
              />
            </div>
          </div>

          <div class="score-grid">
            <div class="score-item">
              <span>窗口校验量</span>
              <strong>{{ metrics.totalCheckCount }}</strong>
            </div>
            <div class="score-item">
              <span>执行失败</span>
              <strong>{{ metrics.failureCount }}</strong>
            </div>
            <div class="score-item">
              <span>窗口拒绝</span>
              <strong>{{ metrics.rejectCount }}</strong>
            </div>
            <div class="score-item">
              <span>业务成功</span>
              <strong>{{ metrics.successCount }}</strong>
            </div>
          </div>
        </a-card>
      </a-col>

      <a-col :xs="24" :xl="9">
        <a-card class="panel-card panel-card--mq" :loading="loading" :bordered="false" title="MQ 幂等">
          <div class="mq-grid">
            <div class="mq-item">
              <span>消费成功</span>
              <strong>{{ mqMetrics.successCount }}</strong>
            </div>
            <div class="mq-item">
              <span>消费失败</span>
              <strong>{{ mqMetrics.failureCount }}</strong>
            </div>
            <div class="mq-item">
              <span>重复率</span>
              <strong>{{ formatPercent(mqMetrics.duplicateRate) }}</strong>
            </div>
          </div>
          <a-alert
            class="mq-alert"
            :type="mqMetrics.failureCount > 0 ? 'warning' : 'success'"
            :message="
              mqMetrics.failureCount > 0
                ? '消费链路存在失败记录，建议同步查看重试与死信积压。'
                : '消费链路未观察到明显失败，可继续关注重复率趋势。'
            "
            show-icon
          />
        </a-card>
      </a-col>
      </a-row>

      <a-row :gutter="[16, 16]" class="panel-grid">
      <a-col :xs="24" :xl="14">
        <a-card class="panel-card" :loading="loading" :bordered="false" title="热点 Scope">
          <a-table
            size="small"
            :pagination="false"
            :columns="topScopeColumns"
            :data-source="topKeys"
            row-key="key"
            :locale="{ emptyText: '暂无热点 scope' }"
          />
        </a-card>
      </a-col>

      <a-col :xs="24" :xl="10">
        <a-card class="panel-card" :loading="loading" :bordered="false" title="治理建议">
          <div class="recommendation-list">
            <div v-for="item in recommendations" :key="item.title" class="recommendation-item">
              <div class="recommendation-head">
                <a-tag :color="item.tone">{{ item.title }}</a-tag>
              </div>
              <p>{{ item.detail }}</p>
            </div>
          </div>
        </a-card>
      </a-col>
      </a-row>
    </template>
  </div>
</template>

<style scoped>
.ops-shell {
  min-height: 100%;
  padding: 24px;
  background:
    radial-gradient(circle at top right, rgba(27, 106, 195, 0.15), transparent 30%),
    radial-gradient(circle at bottom left, rgba(12, 140, 104, 0.12), transparent 26%),
    linear-gradient(180deg, #f3f6fb 0%, #e9eef5 100%);
}

.ops-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 24px 28px;
  margin-bottom: 16px;
  border: 1px solid rgba(14, 42, 75, 0.08);
  border-radius: 22px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.98), rgba(236, 243, 251, 0.92));
  box-shadow: 0 24px 54px rgba(20, 40, 80, 0.08);
}

.ops-kicker {
  margin: 0 0 8px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: #1b6ac3;
}

.ops-copy h2 {
  margin: 0;
  font-size: 32px;
  line-height: 1.08;
  color: #102848;
}

.ops-summary {
  max-width: 760px;
  margin: 12px 0 0;
  color: #52637a;
  line-height: 1.7;
}

.ops-filter-bar {
  display: flex;
  align-items: flex-end;
  gap: 16px;
  margin-top: 18px;
}

.ops-filter-copy {
  display: grid;
  gap: 4px;
}

.ops-filter-copy strong {
  color: #102848;
}

.ops-filter-copy p {
  margin: 0;
  color: #9a6b12;
}

.ops-filter-label {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #60748b;
}

.ops-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 16px;
  color: #60748b;
}

.ops-actions {
  display: inline-flex;
  align-items: center;
}

.tenant-filter {
  min-width: 240px;
}

.stat-card,
.panel-card {
  border-radius: 18px;
  box-shadow: 0 18px 40px rgba(17, 38, 68, 0.08);
}

.stat-card--pass {
  background: linear-gradient(160deg, rgba(12, 157, 112, 0.12), rgba(255, 255, 255, 0.95));
}

.stat-card--hit {
  background: linear-gradient(160deg, rgba(27, 106, 195, 0.12), rgba(255, 255, 255, 0.95));
}

.stat-card--warning {
  background: linear-gradient(160deg, rgba(214, 141, 18, 0.14), rgba(255, 255, 255, 0.95));
}

.stat-card--danger {
  background: linear-gradient(160deg, rgba(193, 51, 45, 0.14), rgba(255, 255, 255, 0.95));
}

.stat-footnote {
  margin: 10px 0 0;
  color: #61748e;
}

.panel-grid {
  margin-top: 4px;
}

.progress-stack {
  display: grid;
  gap: 18px;
}

.progress-item {
  padding: 14px 16px;
  border-radius: 16px;
  background: rgba(242, 247, 252, 0.92);
}

.progress-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.progress-head span {
  color: #60748b;
}

.progress-head strong {
  color: #0f2745;
}

.score-grid,
.mq-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 18px;
}

.score-item,
.mq-item {
  padding: 16px;
  border-radius: 14px;
  background: rgba(244, 248, 253, 0.94);
}

.score-item span,
.mq-item span {
  color: #60748b;
}

.score-item strong,
.mq-item strong {
  display: block;
  margin-top: 8px;
  font-size: 24px;
  color: #0f2745;
}

.panel-card--mq {
  background: linear-gradient(180deg, rgba(247, 250, 255, 0.96), rgba(255, 255, 255, 0.98));
}

.mq-alert {
  margin-top: 18px;
}

.recommendation-list {
  display: grid;
  gap: 14px;
}

.recommendation-item {
  padding: 14px 16px;
  border-radius: 16px;
  background: rgba(242, 247, 252, 0.92);
}

.recommendation-head {
  margin-bottom: 6px;
}

.recommendation-item p {
  margin: 0;
  color: #52637a;
  line-height: 1.65;
}

.restricted-card {
  margin-top: 8px;
}

.restricted-title {
  margin: 0 0 8px;
  font-size: 18px;
  font-weight: 700;
  color: #102848;
}

.restricted-copy {
  margin: 0;
  color: #52637a;
  line-height: 1.75;
}

@media (max-width: 768px) {
  .ops-shell {
    padding: 16px;
  }

  .ops-hero {
    flex-direction: column;
    padding: 20px;
  }

  .ops-filter-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .tenant-filter {
    width: 100%;
    min-width: 0;
  }

  .ops-actions {
    width: 100%;
  }

  .ops-copy h2 {
    font-size: 28px;
  }

  .ops-meta,
  .score-grid,
  .mq-grid {
    grid-template-columns: 1fr;
  }
}
</style>
