<script setup lang="ts">
import { ReloadOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuth } from '@/auth/auth'
import {
  getIdempotentMetrics,
  getIdempotentTopKeys,
  type IdempotentMetricsSnapshot,
  type IdempotentTopKey,
} from '@/api/idempotent'
import { extractAuthoritiesFromJwt } from '@/utils/jwt'
import { getTenantId } from '@/utils/tenant'

const router = useRouter()
const { user } = useAuth()
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

const loading = ref(false)
const metrics = ref<IdempotentMetricsSnapshot>(emptyMetrics)
const topKeys = ref<IdempotentTopKey[]>([])

const canViewIdempotentOps = computed(() =>
  extractAuthoritiesFromJwt(user.value?.access_token).includes('idempotentOps'),
)

const successRate = computed(() => {
  if (metrics.value.passCount <= 0) {
    return 0
  }
  return metrics.value.successCount / metrics.value.passCount
})

const healthTone = computed(() => {
  if (metrics.value.storeErrorCount > 0 || metrics.value.validationRejectCount > 0) {
    return 'warning'
  }
  if (metrics.value.hitCount > 0) {
    return 'processing'
  }
  return 'success'
})

const metricsWindowLabel = computed(() => `最近 ${metrics.value.windowMinutes || 60} 分钟`)

function formatPercent(value: number): string {
  return `${(value * 100).toFixed(value < 0.1 ? 1 : 0)}%`
}

function openIdempotentOps() {
  if (!canViewIdempotentOps.value) {
    return
  }
  const tenantId = getTenantId()
  if (tenantId) {
    void router.push({
      path: '/ops/idempotent',
      query: { tenantId },
    })
    return
  }
  void router.push('/ops/idempotent')
}

async function loadIdempotentOverview() {
  if (!canViewIdempotentOps.value) {
    metrics.value = { ...emptyMetrics }
    topKeys.value = []
    return
  }

  loading.value = true
  try {
    const [metricsSnapshot, hotScopes] = await Promise.all([
      getIdempotentMetrics(),
      getIdempotentTopKeys(5),
    ])
    metrics.value = metricsSnapshot
    topKeys.value = hotScopes
  } catch (error) {
    console.error('加载幂等监控失败:', error)
    message.error('加载幂等监控失败')
  } finally {
    loading.value = false
  }
}

watch(canViewIdempotentOps, (enabled) => {
  if (enabled) {
    void loadIdempotentOverview()
    return
  }
  metrics.value = { ...emptyMetrics }
  topKeys.value = []
}, { immediate: true })
</script>

<template>
  <div class="workbench-shell">
    <section class="hero-panel">
      <div class="hero-copy">
        <p class="hero-kicker">Control Tower</p>
        <h2>工作台</h2>
        <p class="hero-summary">
          {{
            canViewIdempotentOps
              ? '这里直接展示幂等链路的实时健康度，方便判断重复提交拦截、非法 key 拒绝和存储异常是否在上升。'
              : '幂等治理指标仅对平台管理员开放，普通租户管理员和业务用户不会在首页拉取这些平台级统计。'
          }}
        </p>
      </div>
      <a-space wrap class="hero-actions">
        <a-tag v-if="!canViewIdempotentOps" color="default">平台管理员可见</a-tag>
        <a-button v-if="canViewIdempotentOps" @click="openIdempotentOps">进入治理页</a-button>
        <a-button v-if="canViewIdempotentOps" type="primary" :loading="loading" @click="loadIdempotentOverview">
          <template #icon>
            <ReloadOutlined />
          </template>
          刷新指标
        </a-button>
      </a-space>
    </section>

    <a-row v-if="canViewIdempotentOps" :gutter="[16, 16]">
      <a-col :xs="24" :md="12" :xl="6">
        <a-card class="stat-card stat-card--pass" :loading="loading" :bordered="false">
          <a-statistic title="通过请求" :value="metrics.passCount" />
          <p class="stat-footnote">成功进入幂等保护的首个请求</p>
        </a-card>
      </a-col>
      <a-col :xs="24" :md="12" :xl="6">
        <a-card class="stat-card stat-card--hit" :loading="loading" :bordered="false">
          <a-statistic title="重复命中" :value="metrics.hitCount" />
          <p class="stat-footnote">被判定为重复提交的次数</p>
        </a-card>
      </a-col>
      <a-col :xs="24" :md="12" :xl="6">
        <a-card class="stat-card stat-card--warning" :loading="loading" :bordered="false">
          <a-statistic title="非法 Key 拒绝" :value="metrics.validationRejectCount" />
          <p class="stat-footnote">服务端直接拦截的脏幂等键</p>
        </a-card>
      </a-col>
      <a-col :xs="24" :md="12" :xl="6">
        <a-card class="stat-card stat-card--danger" :loading="loading" :bordered="false">
          <a-statistic title="存储异常" :value="metrics.storeErrorCount" />
          <p class="stat-footnote">仓储不可用或读写失败次数</p>
        </a-card>
      </a-col>
    </a-row>

    <a-row v-if="canViewIdempotentOps" :gutter="[16, 16]" class="detail-grid">
      <a-col :xs="24" :xl="14">
        <a-card class="insight-card" :loading="loading" :bordered="false">
          <template #title>
            <div class="section-title">
              <span>链路概览</span>
              <a-tag :color="healthTone">
                {{ healthTone === 'success' ? '稳定' : healthTone === 'processing' ? '有拦截流量' : '需关注' }}
              </a-tag>
            </div>
          </template>

          <div class="insight-grid">
            <div class="insight-item">
              <span class="insight-label">执行成功率</span>
              <strong>{{ formatPercent(successRate) }}</strong>
            </div>
            <div class="insight-item">
              <span class="insight-label">冲突率</span>
              <strong>{{ formatPercent(metrics.conflictRate) }}</strong>
            </div>
            <div class="insight-item">
              <span class="insight-label">存储异常率</span>
              <strong>{{ formatPercent(metrics.storageErrorRate) }}</strong>
            </div>
            <div class="insight-item">
              <span class="insight-label">窗口校验量</span>
              <strong>{{ metrics.totalCheckCount }}</strong>
            </div>
            <div class="insight-item">
              <span class="insight-label">统计窗口</span>
              <strong>{{ metricsWindowLabel }}</strong>
            </div>
          </div>
        </a-card>
      </a-col>

      <a-col :xs="24" :xl="10">
        <a-card class="hot-card" :loading="loading" :bordered="false" title="热点 Scope">
          <a-list
            :data-source="topKeys"
            size="small"
            :locale="{ emptyText: '暂无热点 scope' }"
          >
            <template #renderItem="{ item, index }">
              <a-list-item>
                <div class="hot-key-line">
                  <span class="hot-key-rank">#{{ index + 1 }}</span>
                  <code class="hot-key-value">{{ item.key }}</code>
                </div>
                <a-tag color="blue">{{ item.count }}</a-tag>
              </a-list-item>
            </template>
          </a-list>
        </a-card>
      </a-col>
    </a-row>

    <a-card v-else class="restricted-card" :bordered="false">
      <p class="restricted-title">指标已按权限收口</p>
      <p class="restricted-copy">
        你当前没有平台级幂等治理权限，因此工作台不会主动请求 `/metrics/idempotent`。如需查看，请使用默认平台租户下具备 `idempotentOps` 资源授权的管理员账号。
      </p>
    </a-card>
  </div>
</template>

<style scoped>
.workbench-shell {
  min-height: 100%;
  padding: 24px;
  background:
    radial-gradient(circle at top left, rgba(27, 106, 195, 0.14), transparent 32%),
    linear-gradient(180deg, #f5f8fc 0%, #eef3f8 100%);
}

.hero-panel {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 24px 28px;
  margin-bottom: 16px;
  border: 1px solid rgba(16, 40, 72, 0.08);
  border-radius: 20px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.98), rgba(238, 245, 252, 0.92));
  box-shadow: 0 20px 48px rgba(20, 40, 80, 0.08);
}

.hero-copy h2 {
  margin: 0;
  font-size: 30px;
  line-height: 1.1;
  color: #102848;
}

.hero-kicker {
  margin: 0 0 8px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: #1b6ac3;
}

.hero-summary {
  max-width: 720px;
  margin: 12px 0 0;
  color: #52637a;
  line-height: 1.7;
}

.hero-actions {
  display: inline-flex;
  align-items: center;
}

.stat-card,
.insight-card,
.hot-card {
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

.detail-grid {
  margin-top: 4px;
}

.restricted-card {
  margin-top: 8px;
  border-radius: 18px;
  box-shadow: 0 18px 40px rgba(17, 38, 68, 0.08);
  background: linear-gradient(160deg, rgba(244, 248, 252, 0.96), rgba(255, 255, 255, 0.98));
}

.restricted-title {
  margin: 0 0 8px;
  font-size: 18px;
  font-weight: 700;
  color: #102848;
}

.restricted-copy {
  margin: 0;
  color: #5c6f87;
  line-height: 1.75;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.insight-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.insight-item {
  padding: 16px;
  border-radius: 14px;
  background: rgba(242, 247, 252, 0.9);
}

.insight-item strong {
  display: block;
  margin-top: 6px;
  font-size: 28px;
  color: #0f2745;
}

.insight-label {
  color: #60748b;
}

.hot-key-line {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.hot-key-rank {
  width: 28px;
  font-weight: 700;
  color: #1b6ac3;
}

.hot-key-value {
  overflow: hidden;
  color: #0f2745;
  white-space: nowrap;
  text-overflow: ellipsis;
}

@media (max-width: 768px) {
  .workbench-shell {
    padding: 16px;
  }

  .hero-panel {
    flex-direction: column;
    padding: 20px;
  }

  .hero-actions {
    width: 100%;
  }

  .hero-copy h2 {
    font-size: 26px;
  }

  .insight-grid {
    grid-template-columns: 1fr;
  }
}
</style>
