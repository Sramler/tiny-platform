<template>
  <div class="content-container" style="position: relative;">
    <div class="content-card">
      <div class="form-container">
        <a-form layout="inline" :model="query">
          <a-form-item label="DAG">
            <a-select
              v-model:value="query.dagId"
              placeholder="请选择 DAG"
              allow-clear
              show-search
              :filter-option="filterDagOption"
              style="width: 280px"
            >
              <a-select-option v-for="d in dagOptions" :key="d.id" :value="d.id">
                {{ d.name || d.code || d.id }} {{ d.code ? `(${d.code})` : '' }}
              </a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="状态">
            <a-select
              v-model:value="query.status"
              placeholder="全部"
              allow-clear
              style="width: 120px"
            >
              <a-select-option v-for="s in statusOptions" :key="s.value" :value="s.value">
                {{ s.label }}
              </a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="触发类型">
            <a-select
              v-model:value="query.triggerType"
              placeholder="全部"
              allow-clear
              style="width: 120px"
            >
              <a-select-option v-for="t in triggerTypeOptions" :key="t.value" :value="t.value">
                {{ t.label }}
              </a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="运行编号">
            <a-input v-model:value="query.runNo" placeholder="运行编号" allow-clear style="width: 160px" />
          </a-form-item>
          <a-form-item label="开始时间">
            <a-range-picker
              v-model:value="query.startTimeRange"
              :placeholder="['开始日期', '结束日期']"
              value-format="YYYY-MM-DD"
              style="width: 240px"
            />
          </a-form-item>
          <a-form-item>
            <a-button type="primary" @click="handleSearch">搜索</a-button>
            <a-button class="ml-2" @click="handleReset">重置</a-button>
          </a-form-item>
        </a-form>
      </div>

      <a-alert
        type="info"
        show-icon
        message="运行历史说明"
        description="本页为租户内运维/排障视图：展示所选 DAG 的全部运行记录，不按顶部「活动组织/部门」收缩。DAG 管理列表会随活动范围变化。"
        style="margin-bottom: 16px"
      />

      <div class="toolbar-container">
        <div class="table-title">运行历史列表</div>
        <div class="table-actions">
          <a-tooltip title="刷新">
            <span class="action-icon" @click="handleRefresh">
              <ReloadOutlined :spin="refreshing" />
            </span>
          </a-tooltip>
        </div>
      </div>

      <!-- DAG 运行统计（选中 DAG 时展示） -->
      <a-card v-if="effectiveDagId && dagStats" class="stats-card" size="small" title="运行统计">
        <a-row :gutter="16">
          <a-col :span="4">
            <a-statistic title="总运行次数" :value="dagStats.total" />
          </a-col>
          <a-col :span="4">
            <a-statistic title="成功" :value="dagStats.success" />
          </a-col>
          <a-col :span="4">
            <a-statistic title="失败" :value="dagStats.failed" />
          </a-col>
          <a-col :span="4">
            <a-statistic
              title="平均耗时"
              :value="formatDurationMs(dagStats.avgDurationMs)"
            />
          </a-col>
          <a-col :span="4">
            <a-statistic
              title="P95 耗时"
              :value="formatDurationMs(dagStats.p95DurationMs)"
            />
          </a-col>
          <a-col :span="4">
            <a-statistic
              title="P99 耗时"
              :value="formatDurationMs(dagStats.p99DurationMs)"
            />
          </a-col>
        </a-row>
      </a-card>

      <a-table
        :columns="columns"
        :data-source="effectiveDagId ? dataSource : []"
        :loading="loading"
        :pagination="pagination"
        @change="handleTableChange"
        row-key="id"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="getStatusColor(record.status)">
              {{ record.status }}
            </a-tag>
          </template>
          <template v-if="column.key === 'triggerType'">
            <a-tag>{{ record.triggerType }}</a-tag>
          </template>
          <template v-if="column.key === 'operability'">
            <a-space size="small">
              <a-tag v-if="canStopRun(record)" color="processing">可停止</a-tag>
              <a-tag v-if="canRetryRun(record)" color="orange">可重试</a-tag>
              <a-tag v-if="supportsNodeOperations(record)" color="blue">可看节点控制</a-tag>
              <span v-if="!canStopRun(record) && !canRetryRun(record) && !supportsNodeOperations(record)" style="color: #999;">
                -
              </span>
            </a-space>
          </template>
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="handleView(record)">查看详情</a-button>
              <a-button type="link" size="small" @click="handleViewNodes(record)">节点记录</a-button>
              <a-tooltip :title="getStopRunDisabledReason(record)">
                <span>
                  <a-popconfirm
                    title="确认停止当前这条运行吗？仅会取消本次 Run 及其未终态节点。"
                    ok-text="确认停止"
                    cancel-text="取消"
                    :disabled="!canStopRun(record)"
                    @confirm="handleStopRun(record)"
                  >
                    <a-button type="link" size="small" :disabled="!canStopRun(record)">
                      停止本次
                    </a-button>
                  </a-popconfirm>
                </span>
              </a-tooltip>
              <a-tooltip :title="getRetryRunDisabledReason(record)">
                <span>
                  <a-popconfirm
                    title="确认重试当前这条失败运行吗？系统会创建新的 Run。"
                    ok-text="确认重试"
                    cancel-text="取消"
                    :disabled="!canRetryRun(record)"
                    @confirm="handleRetryRun(record)"
                  >
                    <a-button type="link" size="small" :disabled="!canRetryRun(record)">
                      重试本次
                    </a-button>
                  </a-popconfirm>
                </span>
              </a-tooltip>
            </a-space>
          </template>
        </template>
      </a-table>
    </div>

    <!-- 运行详情弹窗 -->
    <a-modal
      v-model:open="detailVisible"
      title="运行详情"
      :width="900"
      :footer="null"
    >
      <a-descriptions :column="2" bordered v-if="currentRecord">
        <a-descriptions-item label="运行ID">{{ currentRecord.id }}</a-descriptions-item>
        <a-descriptions-item label="运行编号">{{ currentRecord.runNo }}</a-descriptions-item>
        <a-descriptions-item label="DAG ID">{{ currentRecord.dagId }}</a-descriptions-item>
        <a-descriptions-item label="版本ID">{{ currentRecord.dagVersionId }}</a-descriptions-item>
        <a-descriptions-item label="状态">
          <a-tag :color="getStatusColor(currentRecord.status)">
            {{ currentRecord.status }}
          </a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="触发类型">{{ currentRecord.triggerType }}</a-descriptions-item>
        <a-descriptions-item label="触发人">{{ currentRecord.triggeredBy || '-' }}</a-descriptions-item>
        <a-descriptions-item label="开始时间">{{ currentRecord.startTime || '-' }}</a-descriptions-item>
        <a-descriptions-item label="结束时间">{{ currentRecord.endTime || '-' }}</a-descriptions-item>
        <a-descriptions-item label="指标" :span="2">
          <pre style="max-height: 200px; overflow: auto;">{{ formatJson(currentRecord.metrics) }}</pre>
        </a-descriptions-item>
      </a-descriptions>
    </a-modal>

    <!-- 节点执行记录弹窗 -->
    <a-modal
      v-model:open="nodesVisible"
      title="节点执行记录"
      :width="1200"
      :footer="null"
    >
      <a-table
        :columns="nodeColumns"
        :data-source="nodeRecords"
        :loading="nodesLoading"
        :pagination="false"
        row-key="id"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="getStatusColor(record.status)">
              {{ record.status }}
            </a-tag>
          </template>
          <template v-if="column.key === 'operability'">
            <a-space size="small">
              <a-tag v-if="canTriggerNode(record)" color="cyan">可触发</a-tag>
              <a-tag v-if="canPauseNode(record)" color="warning">可暂停</a-tag>
              <a-tag v-if="canResumeNode(record)" color="purple">可恢复</a-tag>
              <a-tag v-if="canRetryNode(record)" color="orange">可重试</a-tag>
              <span
                v-if="!canTriggerNode(record) && !canPauseNode(record) && !canResumeNode(record) && !canRetryNode(record)"
                style="color: #999;"
              >
                -
              </span>
            </a-space>
          </template>
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="handleViewNodeDetail(record)">查看详情</a-button>
              <a-button type="link" size="small" @click="handleViewLog(record)">查看日志</a-button>
              <a-tooltip :title="getTriggerNodeDisabledReason(record)">
                <span>
                  <a-popconfirm
                    title="确认在当前这次运行里重新触发本节点吗？系统会直接重置当前节点实例并重新调度。"
                    ok-text="确认触发"
                    cancel-text="取消"
                    :disabled="!canTriggerNode(record)"
                    @confirm="handleTriggerNode(record)"
                  >
                    <a-button type="link" size="small" :disabled="!canTriggerNode(record)">触发本节点</a-button>
                  </a-popconfirm>
                </span>
              </a-tooltip>
              <a-tooltip :title="getPauseNodeDisabledReason(record)">
                <span>
                  <a-popconfirm
                    title="确认暂停当前这次运行里的本节点吗？仅影响当前 Run。"
                    ok-text="确认暂停"
                    cancel-text="取消"
                    :disabled="!canPauseNode(record)"
                    @confirm="handlePauseNode(record)"
                  >
                    <a-button type="link" size="small" :disabled="!canPauseNode(record)">暂停本节点</a-button>
                  </a-popconfirm>
                </span>
              </a-tooltip>
              <a-tooltip :title="getResumeNodeDisabledReason(record)">
                <span>
                  <a-popconfirm
                    title="确认恢复当前这次运行里的本节点吗？仅影响当前 Run。"
                    ok-text="确认恢复"
                    cancel-text="取消"
                    :disabled="!canResumeNode(record)"
                    @confirm="handleResumeNode(record)"
                  >
                    <a-button type="link" size="small" :disabled="!canResumeNode(record)">恢复本节点</a-button>
                  </a-popconfirm>
                </span>
              </a-tooltip>
              <a-tooltip :title="getRetryNodeDisabledReason(record)">
                <span>
                  <a-popconfirm
                    title="确认重试当前这次运行里的失败节点吗？系统会在当前 Run 内创建新的节点实例。"
                    ok-text="确认重试"
                    cancel-text="取消"
                    :disabled="!canRetryNode(record)"
                    @confirm="handleRetryNode(record)"
                  >
                    <a-button type="link" size="small" :disabled="!canRetryNode(record)">重试本节点</a-button>
                  </a-popconfirm>
                </span>
              </a-tooltip>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-modal>

    <!-- 节点详情弹窗 -->
    <a-modal
      v-model:open="nodeDetailVisible"
      title="节点执行详情"
      :width="900"
      :footer="null"
    >
      <a-descriptions :column="2" bordered v-if="currentNodeRecord">
        <a-descriptions-item label="实例ID">{{ currentNodeRecord.id }}</a-descriptions-item>
        <a-descriptions-item label="节点编码">{{ currentNodeRecord.nodeCode }}</a-descriptions-item>
        <a-descriptions-item label="任务ID">{{ currentNodeRecord.taskId }}</a-descriptions-item>
        <a-descriptions-item label="尝试次数">{{ currentNodeRecord.attemptNo }}</a-descriptions-item>
        <a-descriptions-item label="状态">
          <a-tag :color="getStatusColor(currentNodeRecord.status)">
            {{ currentNodeRecord.status }}
          </a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="调度时间">{{ currentNodeRecord.scheduledAt || '-' }}</a-descriptions-item>
        <a-descriptions-item label="下一次重试时间">{{ currentNodeRecord.nextRetryAt || '-' }}</a-descriptions-item>
        <a-descriptions-item label="锁定者">{{ currentNodeRecord.lockedBy || '-' }}</a-descriptions-item>
        <a-descriptions-item label="锁定时间">{{ currentNodeRecord.lockTime || '-' }}</a-descriptions-item>
        <a-descriptions-item label="参数" :span="2">
          <pre style="max-height: 200px; overflow: auto;">{{ formatJson(currentNodeRecord.params) }}</pre>
        </a-descriptions-item>
        <a-descriptions-item label="结果" :span="2">
          <pre style="max-height: 200px; overflow: auto;">{{ formatJson(currentNodeRecord.result) }}</pre>
        </a-descriptions-item>
        <a-descriptions-item label="错误原因" :span="2">
          <pre style="max-height: 200px; overflow: auto;">{{ currentNodeRecord.errorMessage || '-' }}</pre>
        </a-descriptions-item>
      </a-descriptions>
    </a-modal>

    <!-- 日志弹窗 -->
    <a-modal
      v-model:open="logVisible"
      title="执行日志"
      :width="900"
      :footer="null"
    >
      <pre style="max-height: 500px; overflow: auto; white-space: pre-wrap;">{{ logContent }}</pre>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { message } from 'ant-design-vue'
import { useRouter, useRoute } from 'vue-router'
import { ReloadOutlined } from '@ant-design/icons-vue'
import { throttle } from '@/utils/debounce'
import { getActiveTenantId, resolveActiveTenantQueryValue, withActiveTenantQuery } from '@/utils/tenant'
import {
  dagList,
  getDagRuns,
  getDagRun,
  getDagRunNodes,
  getDagNodes,
  getDagRunNode,
  getTaskInstanceLog,
  getDagStats,
  stopDagRun,
  retryDagRun,
  triggerDagRunNode,
  retryDagRunNode,
  pauseDagRunNode,
  resumeDagRunNode,
} from '@/api/scheduling'
import { useAuth } from '@/auth/auth'
import { extractAuthoritiesFromJwt } from '@/utils/jwt'
import { SCHEDULING_RUN_CONTROL, SCHEDULING_WILDCARD } from '@/constants/permission'

const router = useRouter()
const route = useRoute()
const { user } = useAuth()

const schedulingAuthorities = computed(() =>
  extractAuthoritiesFromJwt(user.value?.access_token).filter((a) => a.startsWith('scheduling:')),
)
const canOperateSchedulingRun = computed(() =>
  schedulingAuthorities.value.includes(SCHEDULING_RUN_CONTROL) ||
  schedulingAuthorities.value.includes(SCHEDULING_WILDCARD),
)

function resolveNavigationTenantId() {
  return resolveActiveTenantQueryValue(route.query) ?? getActiveTenantId()
}

const query = reactive<{
  dagId: number | undefined
  status?: string
  triggerType?: string
  runNo?: string
  startTimeRange?: [string, string]
}>({
  dagId: undefined,
  status: undefined,
  triggerType: undefined,
  runNo: undefined,
  startTimeRange: undefined,
})

const statusOptions = [
  { value: 'SCHEDULED', label: '已调度' },
  { value: 'RUNNING', label: '运行中' },
  { value: 'SUCCESS', label: '成功' },
  { value: 'FAILED', label: '失败' },
  { value: 'PARTIAL_FAILED', label: '部分失败' },
  { value: 'CANCELLED', label: '已取消' },
  { value: 'PENDING', label: '待执行' },
  { value: 'SKIPPED', label: '已跳过' },
  { value: 'PAUSED', label: '已暂停' },
]
const triggerTypeOptions = [
  { value: 'MANUAL', label: '手动' },
  { value: 'SCHEDULE', label: '定时' },
  { value: 'RETRY', label: '重试' },
]
const dagOptions = ref<any[]>([])
const selectedDagId = ref<number | undefined>(undefined)
const loading = ref(false)
const refreshing = ref(false)
const dataSource = ref<any[]>([])
const detailVisible = ref(false)
const currentRecord = ref<any>(null)
const nodesVisible = ref(false)
const nodeRecords = ref<any[]>([])
const nodesLoading = ref(false)
const currentRunIdForNodes = ref<number | null>(null)
const currentRunStatusForNodes = ref<string | null>(null)
const dagNodeIdMap = ref<Record<string, number>>({})
const nodeDetailVisible = ref(false)
const currentNodeRecord = ref<any>(null)
const logVisible = ref(false)
const logContent = ref('')

/** DAG 运行统计（总/成功/失败/平均耗时/P95/P99） */
const dagStats = ref<{
  total: number
  success: number
  failed: number
  avgDurationMs: number | null
  p95DurationMs: number | null
  p99DurationMs: number | null
} | null>(null)

const effectiveDagId = computed(() => {
  const fromQuery = Number(route.query.dagId) || Number(route.query.id)
  if (fromQuery && !Number.isNaN(fromQuery)) return fromQuery
  const fromSelect = selectedDagId.value
  return fromSelect && !Number.isNaN(fromSelect) ? fromSelect : undefined
})

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showTotal: (total: number) => `共 ${total} 条`,
})

const columns = [
  { title: '运行ID', dataIndex: 'id', key: 'id', width: 100 },
  { title: '运行编号', dataIndex: 'runNo', key: 'runNo', width: 200 },
  { title: '版本ID', dataIndex: 'dagVersionId', key: 'dagVersionId', width: 100 },
  { title: '状态', key: 'status', width: 120 },
  { title: '可操作', key: 'operability', width: 200 },
  { title: '触发类型', key: 'triggerType', width: 120 },
  { title: '触发人', dataIndex: 'triggeredBy', key: 'triggeredBy', width: 120 },
  { title: '开始时间', dataIndex: 'startTime', key: 'startTime', width: 180 },
  { title: '结束时间', dataIndex: 'endTime', key: 'endTime', width: 180 },
  { title: '操作', key: 'action', width: 260, fixed: 'right' },
]

const nodeColumns = [
  { title: '实例ID', dataIndex: 'id', key: 'id', width: 100 },
  { title: '节点编码', dataIndex: 'nodeCode', key: 'nodeCode', width: 150 },
  { title: '任务ID', dataIndex: 'taskId', key: 'taskId', width: 100 },
  { title: '尝试次数', dataIndex: 'attemptNo', key: 'attemptNo', width: 100 },
  { title: '状态', key: 'status', width: 120 },
  { title: '可操作', key: 'operability', width: 220 },
  { title: '调度时间', dataIndex: 'scheduledAt', key: 'scheduledAt', width: 180 },
  { title: '下一次重试', dataIndex: 'nextRetryAt', key: 'nextRetryAt', width: 180 },
  { title: '错误原因', dataIndex: 'errorMessage', key: 'errorMessage', width: 200, ellipsis: true },
  { title: '操作', key: 'action', width: 470, fixed: 'right' },
]

/** 将毫秒转为可读（如 1.2s、500ms） */
function formatDurationMs(ms: number | null | undefined): string {
  if (ms == null) return '-'
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(2)}s`
}

const getStatusColor = (status: string) => {
  const map: Record<string, string> = {
    // DAG Run 状态
    SCHEDULED: 'blue',
    RUNNING: 'processing',
    SUCCESS: 'success',
    FAILED: 'error',
    PARTIAL_FAILED: 'orange',
    CANCELLED: 'default',
    // Task Instance 状态
    PENDING: 'default',
    RESERVED: 'warning',
    SKIPPED: 'warning',
    PAUSED: 'purple',
  }
  return map[status] || 'default'
}

const canStopRun = (record: { status?: string }) => record.status === 'RUNNING'

const getStopRunDisabledReason = (record: { status?: string }) => {
  if (canStopRun(record)) return undefined
  return '仅 RUNNING 的运行实例支持停止'
}

const canRetryRun = (record: { status?: string }) => {
  return record.status === 'FAILED' || record.status === 'PARTIAL_FAILED'
}

const getRetryRunDisabledReason = (record: { status?: string }) => {
  if (canRetryRun(record)) return undefined
  return '仅失败或部分失败的运行实例支持重试'
}

const supportsNodeOperations = (record: { status?: string }) => {
  return record.status === 'RUNNING' || canRetryRun(record)
}

const formatJson = (str: string | null | undefined) => {
  if (!str) return '-'
  try {
    return JSON.stringify(JSON.parse(str), null, 2)
  } catch {
    return str
  }
}

const loadDagOptions = async () => {
  try {
    const res = await dagList({ current: 1, pageSize: 500 })
    dagOptions.value = res.records || []
  } catch (_) {
    dagOptions.value = []
  }
}

const filterDagOption = (input: string, option: any) => {
  const d = dagOptions.value.find((x) => x.id === option.value)
  if (!d) return false
  const text = `${d.name || ''} ${d.code || ''}`.toLowerCase()
  return text.includes(input.toLowerCase())
}

const dagNodeCacheKey = (dagVersionId: number, nodeCode: string) => `${dagVersionId}:${nodeCode}`

const resolveDagNodeId = (record: { dagVersionId?: number; nodeCode?: string }) => {
  if (!record.dagVersionId || !record.nodeCode) return undefined
  return dagNodeIdMap.value[dagNodeCacheKey(record.dagVersionId, record.nodeCode)]
}

const canTriggerNode = (record: { status?: string; dagVersionId?: number; nodeCode?: string }) => {
  return currentRunStatusForNodes.value === 'RUNNING'
    && ['PENDING', 'FAILED'].includes(record.status || '')
    && resolveDagNodeId(record) != null
}

const canPauseNode = (record: { status?: string; dagVersionId?: number; nodeCode?: string }) => {
  return ['PENDING', 'RESERVED'].includes(record.status || '') && resolveDagNodeId(record) != null
}

const canResumeNode = (record: { status?: string; dagVersionId?: number; nodeCode?: string }) => {
  return record.status === 'PAUSED' && resolveDagNodeId(record) != null
}

const canRetryNode = (record: { status?: string; dagVersionId?: number; nodeCode?: string }) => {
  return record.status === 'FAILED' && resolveDagNodeId(record) != null
}

const getTriggerNodeDisabledReason = (record: { status?: string; dagVersionId?: number; nodeCode?: string }) => {
  if (resolveDagNodeId(record) == null) {
    return '未找到对应 DAG 节点定义，请刷新后重试'
  }
  if (currentRunStatusForNodes.value !== 'RUNNING') {
    return '仅 RUNNING 的运行实例支持手动触发节点'
  }
  if (canTriggerNode(record)) {
    return undefined
  }
  return '仅 PENDING 或 FAILED 的节点实例支持手动触发'
}

const getPauseNodeDisabledReason = (record: { status?: string; dagVersionId?: number; nodeCode?: string }) => {
  if (resolveDagNodeId(record) == null) {
    return '未找到对应 DAG 节点定义，请刷新后重试'
  }
  if (canPauseNode(record)) {
    return undefined
  }
  return '仅 PENDING 或 RESERVED 的节点实例支持暂停'
}

const getResumeNodeDisabledReason = (record: { status?: string; dagVersionId?: number; nodeCode?: string }) => {
  if (resolveDagNodeId(record) == null) {
    return '未找到对应 DAG 节点定义，请刷新后重试'
  }
  if (canResumeNode(record)) {
    return undefined
  }
  return '仅 PAUSED 的节点实例支持恢复'
}

const getRetryNodeDisabledReason = (record: { status?: string; dagVersionId?: number; nodeCode?: string }) => {
  if (resolveDagNodeId(record) == null) {
    return '未找到对应 DAG 节点定义，请刷新后重试'
  }
  if (canRetryNode(record)) {
    return undefined
  }
  return '仅 FAILED 的节点实例支持重试'
}

const ensureDagNodeMappings = async (records: Array<{ dagVersionId?: number; nodeCode?: string }>) => {
  if (!effectiveDagId.value) return
  const missingVersionIds = Array.from(new Set(
    records
      .map(record => record.dagVersionId)
      .filter((versionId): versionId is number => Boolean(versionId))
      .filter(versionId =>
        records.some(record =>
          record.dagVersionId === versionId
          && record.nodeCode
          && dagNodeIdMap.value[dagNodeCacheKey(versionId, record.nodeCode)] == null,
        ),
      ),
  ))
  if (!missingVersionIds.length) return

  const nodeGroups = await Promise.all(
    missingVersionIds.map(versionId => getDagNodes(effectiveDagId.value!, versionId)),
  )
  const nextMap = { ...dagNodeIdMap.value }
  missingVersionIds.forEach((versionId, index) => {
    for (const node of nodeGroups[index] || []) {
      if (node?.nodeCode != null && node?.id != null) {
        nextMap[dagNodeCacheKey(versionId, node.nodeCode)] = node.id
      }
    }
  })
  dagNodeIdMap.value = nextMap
}

const loadData = async () => {
  if (!effectiveDagId.value) return
  loading.value = true
  try {
    const [startTimeFrom, startTimeTo] = query.startTimeRange || []
    const params = {
      current: pagination.current,
      pageSize: pagination.pageSize,
      status: query.status,
      triggerType: query.triggerType,
      runNo: query.runNo,
      startTimeFrom: startTimeFrom || undefined,
      startTimeTo: startTimeTo || undefined,
    }
    const [res, stats] = await Promise.all([
      getDagRuns(effectiveDagId.value, params),
      getDagStats(effectiveDagId.value).catch(() => null),
    ])
    dataSource.value = res.records
    if (currentRunIdForNodes.value != null) {
      currentRunStatusForNodes.value = res.records.find((record: any) => record.id === currentRunIdForNodes.value)?.status ?? null
    }
    pagination.total = res.total
    dagStats.value = stats
  } catch (error: any) {
    message.error(error.message || '加载数据失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = throttle(() => {
  const id = query.dagId
  if (id == null) {
    message.info('请先选择 DAG')
    return
  }
  selectedDagId.value = id
  router.replace({
    path: route.path,
    query: withActiveTenantQuery({ ...route.query, dagId: String(id) }, resolveNavigationTenantId()),
  })
  pagination.current = 1
  loadData()
}, 500)

const handleReset = throttle(() => {
  query.dagId = undefined
  query.status = undefined
  query.triggerType = undefined
  query.runNo = undefined
  query.startTimeRange = undefined
  selectedDagId.value = undefined
  dagStats.value = null
  router.replace({
    path: route.path,
    query: withActiveTenantQuery({}, resolveNavigationTenantId()),
  })
  dataSource.value = []
  pagination.current = 1
  pagination.total = 0
}, 500)

const handleRefresh = throttle(() => {
  if (!effectiveDagId.value) return
  refreshing.value = true
  loadData().finally(() => {
    refreshing.value = false
  })
}, 500)

const handleTableChange = (pag: any) => {
  pagination.current = pag.current
  pagination.pageSize = pag.pageSize
  loadData()
}

const handleStopRun = async (record: { id: number; runNo?: string; status?: string }) => {
  if (!effectiveDagId.value) return
  if (!canStopRun(record)) {
    message.warning(getStopRunDisabledReason(record) || '当前运行不可停止')
    return
  }
  if (!canOperateSchedulingRun.value) {
    message.warning('当前账户没有调度运行操作权限')
    return
  }
  try {
    await stopDagRun(effectiveDagId.value, record.id)
    message.success(`已停止运行${record.runNo ? `: ${record.runNo}` : ''}`)
    loadData()
  } catch (error: any) {
    message.error(error.message || '停止运行失败')
  }
}

const handleRetryRun = async (record: { id: number; runNo?: string; status?: string }) => {
  if (!effectiveDagId.value) return
  if (!canRetryRun(record)) {
    message.warning(getRetryRunDisabledReason(record) || '当前运行不可重试')
    return
  }
  if (!canOperateSchedulingRun.value) {
    message.warning('当前账户没有调度运行操作权限')
    return
  }
  try {
    await retryDagRun(effectiveDagId.value, record.id)
    message.success(`已提交运行重试${record.runNo ? `: ${record.runNo}` : ''}`)
    loadData()
  } catch (error: any) {
    message.error(error.message || '运行重试失败')
  }
}

const handleView = async (record: any) => {
  if (!effectiveDagId.value) return
  try {
    currentRecord.value = await getDagRun(effectiveDagId.value, record.id)
    detailVisible.value = true
  } catch (error: any) {
    message.error(error.message || '获取详情失败')
  }
}

const handleViewNodes = async (record: any) => {
  if (!effectiveDagId.value) return
  currentRunIdForNodes.value = record.id
  currentRunStatusForNodes.value = record.status ?? null
  nodesLoading.value = true
  try {
    const records = await getDagRunNodes(effectiveDagId.value, record.id)
    await ensureDagNodeMappings(records)
    nodeRecords.value = records
    nodesVisible.value = true
  } catch (error: any) {
    message.error(error.message || '获取节点记录失败')
  } finally {
    nodesLoading.value = false
  }
}

const refreshCurrentRunNodes = async () => {
  if (!effectiveDagId.value || currentRunIdForNodes.value == null) return
  const records = await getDagRunNodes(effectiveDagId.value, currentRunIdForNodes.value)
  await ensureDagNodeMappings(records)
  nodeRecords.value = records
}

const handleViewNodeDetail = async (record: any) => {
  if (!effectiveDagId.value || currentRunIdForNodes.value == null) return
  try {
    currentNodeRecord.value = await getDagRunNode(
      effectiveDagId.value,
      currentRunIdForNodes.value,
      record.id
    )
    nodeDetailVisible.value = true
  } catch (error: any) {
    message.error(error.message || '获取节点详情失败')
  }
}

const handleViewLog = async (record: any) => {
  try {
    logContent.value = await getTaskInstanceLog(record.id)
    logVisible.value = true
  } catch (error: any) {
    message.error(error.message || '获取日志失败')
  }
}

const requireDagNodeId = (record: { dagVersionId?: number; nodeCode?: string }) => {
  const nodeId = resolveDagNodeId(record)
  if (nodeId == null) {
    throw new Error('未找到对应的 DAG 节点定义，请刷新后重试')
  }
  return nodeId
}

const handleTriggerNode = async (record: { dagVersionId?: number; nodeCode?: string }) => {
  if (!effectiveDagId.value || currentRunIdForNodes.value == null) return
  if (!canTriggerNode(record)) {
    message.warning(getTriggerNodeDisabledReason(record) || '当前节点不可触发')
    return
  }
  try {
    await triggerDagRunNode(effectiveDagId.value, currentRunIdForNodes.value, requireDagNodeId(record))
    message.success(`已触发节点${record.nodeCode ? `: ${record.nodeCode}` : ''}`)
    await Promise.all([refreshCurrentRunNodes(), loadData()])
  } catch (error: any) {
    message.error(error.message || '触发节点失败')
  }
}

const handlePauseNode = async (record: { dagVersionId?: number; nodeCode?: string }) => {
  if (!effectiveDagId.value || currentRunIdForNodes.value == null) return
  if (!canPauseNode(record)) {
    message.warning(getPauseNodeDisabledReason(record) || '当前节点不可暂停')
    return
  }
  try {
    await pauseDagRunNode(effectiveDagId.value, currentRunIdForNodes.value, requireDagNodeId(record))
    message.success(`已暂停节点${record.nodeCode ? `: ${record.nodeCode}` : ''}`)
    await Promise.all([refreshCurrentRunNodes(), loadData()])
  } catch (error: any) {
    message.error(error.message || '暂停节点失败')
  }
}

const handleResumeNode = async (record: { dagVersionId?: number; nodeCode?: string }) => {
  if (!effectiveDagId.value || currentRunIdForNodes.value == null) return
  if (!canResumeNode(record)) {
    message.warning(getResumeNodeDisabledReason(record) || '当前节点不可恢复')
    return
  }
  try {
    await resumeDagRunNode(effectiveDagId.value, currentRunIdForNodes.value, requireDagNodeId(record))
    message.success(`已恢复节点${record.nodeCode ? `: ${record.nodeCode}` : ''}`)
    await Promise.all([refreshCurrentRunNodes(), loadData()])
  } catch (error: any) {
    message.error(error.message || '恢复节点失败')
  }
}

const handleRetryNode = async (record: { dagVersionId?: number; nodeCode?: string }) => {
  if (!effectiveDagId.value || currentRunIdForNodes.value == null) return
  if (!canRetryNode(record)) {
    message.warning(getRetryNodeDisabledReason(record) || '当前节点不可重试')
    return
  }
  try {
    await retryDagRunNode(effectiveDagId.value, currentRunIdForNodes.value, requireDagNodeId(record))
    message.success(`已提交节点重试${record.nodeCode ? `: ${record.nodeCode}` : ''}`)
    await Promise.all([refreshCurrentRunNodes(), loadData()])
  } catch (error: any) {
    message.error(error.message || '节点重试失败')
  }
}

onMounted(() => {
  loadDagOptions()
  const fromQuery = Number(route.query.dagId) || Number(route.query.id)
  if (fromQuery && !Number.isNaN(fromQuery)) {
    query.dagId = fromQuery
    selectedDagId.value = fromQuery
    loadData()
  }
})
</script>

<style scoped>
.content-container {
  padding: 16px;
}

.content-card {
  background: #fff;
  border-radius: 4px;
  padding: 16px;
}

.form-container {
  margin-bottom: 16px;
}

.toolbar-container {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.stats-card {
  margin-bottom: 16px;
}

.table-title {
  font-size: 16px;
  font-weight: 500;
}

.table-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.action-icon {
  cursor: pointer;
  font-size: 16px;
  color: #666;
  transition: color 0.3s;
}

.action-icon:hover {
  color: #1677ff;
}

.ml-2 {
  margin-left: 8px;
}
</style>
