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
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="handleView(record)">查看详情</a-button>
              <a-button type="link" size="small" @click="handleViewNodes(record)">节点记录</a-button>
              <a-tooltip title="停止该 DAG 下所有运行中的任务（整 DAG 级别，非仅本行 Run）">
                <a-button
                  v-if="record.status === 'RUNNING'"
                  type="link"
                  size="small"
                  @click="handleStopRun"
                >
                  停止 DAG
                </a-button>
              </a-tooltip>
              <a-tooltip title="对当前 DAG 整体重试（会创建新 Run，非仅本行）">
                <a-button
                  v-if="record.status === 'FAILED' || record.status === 'PARTIAL_FAILED'"
                  type="link"
                  size="small"
                  @click="handleRetryRun"
                >
                  重试 DAG
                </a-button>
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
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="handleViewNodeDetail(record)">查看详情</a-button>
              <a-button type="link" size="small" @click="handleViewLog(record)">查看日志</a-button>
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
import {
  dagList,
  getDagRuns,
  getDagRun,
  getDagRunNodes,
  getDagRunNode,
  getTaskInstanceLog,
  getDagStats,
  stopDag,
  retryDag,
} from '@/api/scheduling'

const router = useRouter()
const route = useRoute()

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
  { title: '触发类型', key: 'triggerType', width: 120 },
  { title: '触发人', dataIndex: 'triggeredBy', key: 'triggeredBy', width: 120 },
  { title: '开始时间', dataIndex: 'startTime', key: 'startTime', width: 180 },
  { title: '结束时间', dataIndex: 'endTime', key: 'endTime', width: 180 },
  { title: '操作', key: 'action', width: 200, fixed: 'right' },
]

const nodeColumns = [
  { title: '实例ID', dataIndex: 'id', key: 'id', width: 100 },
  { title: '节点编码', dataIndex: 'nodeCode', key: 'nodeCode', width: 150 },
  { title: '任务ID', dataIndex: 'taskId', key: 'taskId', width: 100 },
  { title: '尝试次数', dataIndex: 'attemptNo', key: 'attemptNo', width: 100 },
  { title: '状态', key: 'status', width: 120 },
  { title: '调度时间', dataIndex: 'scheduledAt', key: 'scheduledAt', width: 180 },
  { title: '下一次重试', dataIndex: 'nextRetryAt', key: 'nextRetryAt', width: 180 },
  { title: '错误原因', dataIndex: 'errorMessage', key: 'errorMessage', width: 200, ellipsis: true },
  { title: '操作', key: 'action', width: 200, fixed: 'right' },
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
  router.replace({ path: route.path, query: { ...route.query, dagId: String(id) } })
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
  router.replace({ path: route.path, query: {} })
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

const handleStopRun = async () => {
  if (!effectiveDagId.value) return
  try {
    await stopDag(effectiveDagId.value)
    message.success('已停止')
    loadData()
  } catch (error: any) {
    message.error(error.message || '停止失败')
  }
}

const handleRetryRun = async () => {
  if (!effectiveDagId.value) return
  try {
    await retryDag(effectiveDagId.value)
    message.success('已提交重试')
    loadData()
  } catch (error: any) {
    message.error(error.message || '重试失败')
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
  nodesLoading.value = true
  try {
    nodeRecords.value = await getDagRunNodes(effectiveDagId.value, record.id)
    nodesVisible.value = true
  } catch (error: any) {
    message.error(error.message || '获取节点记录失败')
  } finally {
    nodesLoading.value = false
  }
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
