<template>
  <div class="dag-detail-container">
    <a-page-header
      :title="dagInfo.name || 'DAG详情'"
      :sub-title="dagInfo.description"
      @back="handleBack"
    >
      <template #extra>
        <a-space>
          <a-tooltip title="查看并操作单次运行记录">
            <a-button @click="handleHistory">运行历史</a-button>
          </a-tooltip>
          <a-tooltip :title="triggerDisabledReason">
            <span>
              <a-popconfirm
                title="确认立即按当前 ACTIVE 版本创建一条新的手动运行吗？"
                ok-text="确认触发"
                cancel-text="取消"
                :disabled="!canTriggerDag || !canOperateSchedulingRun"
                @confirm="handleTrigger"
              >
                <a-button :loading="triggering" :disabled="!canTriggerDag || !canOperateSchedulingRun">
                  触发执行
                </a-button>
              </a-popconfirm>
            </span>
          </a-tooltip>
          <a-tooltip :title="stopDisabledReason">
            <span>
              <a-popconfirm
                title="确认停止该 DAG 当前所有 RUNNING 运行，并暂停 Quartz 调度吗？如需只处理单次运行，请前往运行历史。"
                ok-text="确认停止"
                cancel-text="取消"
                :disabled="!canStopDag || !canOperateSchedulingRun"
                @confirm="handleStop"
              >
                <a-button :loading="stopping" :disabled="!canStopDag || !canOperateSchedulingRun">
                  停止 DAG
                </a-button>
              </a-popconfirm>
            </span>
          </a-tooltip>
          <a-tooltip :title="retryDisabledReason">
            <span>
              <a-popconfirm
                title="确认重试该 DAG 最近一次失败运行吗？系统会创建新的 Run。如需指定某次运行，请前往运行历史。"
                ok-text="确认重试"
                cancel-text="取消"
                :disabled="!canRetryDag || !canOperateSchedulingRun"
                @confirm="handleRetry"
              >
                <a-button :loading="retrying" :disabled="!canRetryDag || !canOperateSchedulingRun">
                  重试最近失败运行
                </a-button>
              </a-popconfirm>
            </span>
          </a-tooltip>
          <a-popconfirm
            v-if="dagInfo.enabled"
            title="确认禁用该 DAG 并暂停后续定时调度吗？"
            ok-text="确认暂停"
            cancel-text="取消"
            :disabled="!canOperateSchedulingRun"
            @confirm="handlePause"
          >
            <a-button :disabled="!canOperateSchedulingRun">暂停 DAG</a-button>
          </a-popconfirm>
          <a-popconfirm
            v-else
            title="确认重新启用该 DAG 并恢复后续定时调度吗？"
            ok-text="确认恢复"
            cancel-text="取消"
            :disabled="!canOperateSchedulingRun"
            @confirm="handleResume"
          >
            <a-button :disabled="!canOperateSchedulingRun">恢复 DAG</a-button>
          </a-popconfirm>
          <a-button :disabled="!canManageSchedulingConfig" @click="handleEdit">编辑</a-button>
        </a-space>
      </template>
    </a-page-header>

    <a-tabs v-model:activeKey="activeTab">
      <!-- 基本信息 -->
      <a-tab-pane key="info" tab="基本信息">
        <a-card class="info-card">
          <a-descriptions :column="2" bordered>
            <a-descriptions-item label="DAG ID">{{ dagInfo.id }}</a-descriptions-item>
            <a-descriptions-item label="编码">{{ dagInfo.code || '-' }}</a-descriptions-item>
            <a-descriptions-item label="名称">{{ dagInfo.name }}</a-descriptions-item>
            <a-descriptions-item label="状态">
              <a-tag :color="dagInfo.enabled ? 'green' : 'red'">
                {{ dagInfo.enabled ? '启用' : '禁用' }}
              </a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="描述" :span="2">{{ dagInfo.description || '-' }}</a-descriptions-item>
            <a-descriptions-item label="Cron 表达式">{{ dagInfo.cronExpression || '-' }}</a-descriptions-item>
            <a-descriptions-item label="Cron 时区">{{ dagInfo.cronTimezone || '系统默认' }}</a-descriptions-item>
            <a-descriptions-item label="Cron 调度">
              <a-tag :color="dagInfo.cronEnabled ? 'green' : 'red'">
                {{ dagInfo.cronEnabled ? '启用' : '禁用' }}
              </a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="Cron 生效">
              <a-tooltip :title="getCronEffectiveReason(dagInfo)">
                <a-tag v-if="isCronConfigured(dagInfo)" :color="isCronEffective(dagInfo) ? 'green' : 'red'">
                  {{ isCronEffective(dagInfo) ? '生效' : '未生效' }}
                </a-tag>
                <span v-else style="color: #999;">-</span>
              </a-tooltip>
            </a-descriptions-item>
            <a-descriptions-item label="当前版本ID">{{ dagInfo.currentVersionId || '-' }}</a-descriptions-item>
            <a-descriptions-item label="创建时间">{{ dagInfo.createdAt }}</a-descriptions-item>
          </a-descriptions>
        </a-card>
      </a-tab-pane>

      <!-- 版本管理 -->
      <a-tab-pane key="versions" tab="版本管理">
        <a-card>
          <template #extra>
            <a-button type="primary" @click="handleCreateVersion">
              <template #icon>
                <PlusOutlined />
              </template>
              创建新版本
            </a-button>
          </template>
          <a-table
            :columns="versionColumns"
            :data-source="versions"
            :loading="versionsLoading"
            :pagination="false"
            row-key="id"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'status'">
                <a-tag :color="getVersionStatusColor(record.status)">
                  {{ record.status }}
                </a-tag>
              </template>
              <template v-if="column.key === 'action'">
                <a-space>
                  <a-button type="link" size="small" @click="handleViewVersion(record)">查看</a-button>
                  <a-button type="link" size="small" @click="handleEditVersion(record)">编辑</a-button>
                  <a-button
                    type="link"
                    size="small"
                    @click="handleActivateVersion(record)"
                    v-if="record.status !== 'ACTIVE'"
                  >
                    激活
                  </a-button>
                  <a-button type="link" size="small" @click="handleSwitchVersion(record)">切换到此版本</a-button>
                </a-space>
              </template>
            </template>
          </a-table>
        </a-card>
      </a-tab-pane>

      <!-- 节点管理 -->
      <a-tab-pane key="nodes" tab="节点管理">
        <a-card>
          <template #extra>
            <a-space>
              <a-select
                v-model:value="selectedVersionId"
                placeholder="选择版本"
                style="width: 200px"
                @change="loadNodes"
              >
                <a-select-option v-for="v in versions" :key="v.id" :value="v.id">
                  v{{ v.versionNo }} - {{ v.status }}
                </a-select-option>
              </a-select>
              <a-button type="primary" @click="handleAddNode" :disabled="!selectedVersionId">
                <template #icon>
                  <PlusOutlined />
                </template>
                添加节点
              </a-button>
            </a-space>
          </template>
          <a-table
            :columns="nodeColumns"
            :data-source="nodes"
            :loading="nodesLoading"
            :pagination="false"
            row-key="id"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'action'">
                <a-space>
                  <a-button type="link" size="small" @click="handleEditNode(record)">编辑</a-button>
                  <a-button type="link" size="small" @click="handleViewUpstream(record)">上游</a-button>
                  <a-button type="link" size="small" @click="handleViewDownstream(record)">下游</a-button>
                  <a-popconfirm title="确定要删除吗？" @confirm="handleDeleteNode(record.id)">
                    <a-button type="link" danger size="small">删除</a-button>
                  </a-popconfirm>
                </a-space>
              </template>
            </template>
          </a-table>
        </a-card>
      </a-tab-pane>

      <!-- 依赖关系 -->
      <a-tab-pane key="edges" tab="依赖关系">
        <a-card>
          <template #extra>
            <a-space>
              <a-select
                v-model:value="selectedVersionIdForEdge"
                placeholder="选择版本"
                style="width: 200px"
                @change="loadEdges"
              >
                <a-select-option v-for="v in versions" :key="v.id" :value="v.id">
                  v{{ v.versionNo }} - {{ v.status }}
                </a-select-option>
              </a-select>
              <a-button type="primary" @click="handleAddEdge" :disabled="!selectedVersionIdForEdge">
                <template #icon>
                  <PlusOutlined />
                </template>
                添加依赖
              </a-button>
            </a-space>
          </template>
          <a-table
            :columns="edgeColumns"
            :data-source="edges"
            :loading="edgesLoading"
            :pagination="false"
            row-key="id"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'action'">
                <a-popconfirm title="确定要删除吗？" @confirm="handleDeleteEdge(record.id)">
                  <a-button type="link" danger size="small">删除</a-button>
                </a-popconfirm>
              </template>
            </template>
          </a-table>
        </a-card>
      </a-tab-pane>
    </a-tabs>

    <!-- DAG 编辑弹窗 -->
    <a-modal
      v-model:open="dagFormVisible"
      title="编辑 DAG"
      :width="700"
      @ok="handleSubmitDag"
      @cancel="handleCancelDag"
    >
      <a-form :model="dagFormData" :label-col="{ span: 6 }" :wrapper-col="{ span: 18 }">
        <a-form-item label="编码">
          <a-input v-model:value="dagFormData.code" placeholder="请输入编码" />
        </a-form-item>
        <a-form-item label="名称" required>
          <a-input v-model:value="dagFormData.name" placeholder="请输入名称" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="dagFormData.description" :rows="3" placeholder="请输入描述" />
        </a-form-item>
        <a-form-item label="Cron 表达式">
          <a-input-group compact>
            <a-input
              v-model:value="dagFormData.cronExpression"
              placeholder="可选，如 0 0 2 * * ? 表示每日 2 点"
              allow-clear
              style="width: calc(100% - 64px)"
            />
            <a-button type="default" @click="openCronDesigner">设计</a-button>
          </a-input-group>
        </a-form-item>
        <a-form-item label="Cron 时区">
          <a-select
            v-model:value="dagFormData.cronTimezone"
            placeholder="可选，默认使用系统时区"
            allow-clear
            show-search
            :filter-option="filterTimezoneOption"
          >
            <a-select-option value="Asia/Shanghai">Asia/Shanghai (中国标准时间)</a-select-option>
            <a-select-option value="UTC">UTC (协调世界时)</a-select-option>
            <a-select-option value="America/New_York">America/New_York (美国东部时间)</a-select-option>
            <a-select-option value="Europe/London">Europe/London (英国时间)</a-select-option>
            <a-select-option value="Asia/Tokyo">Asia/Tokyo (日本标准时间)</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="启用 Cron 调度">
          <a-switch v-model:checked="dagFormData.cronEnabled" />
          <span class="ml-2" style="color: #999; font-size: 12px">与 DAG 启用状态独立控制</span>
        </a-form-item>
        <a-form-item label="是否启用">
          <a-switch v-model:checked="dagFormData.enabled" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 版本表单弹窗 -->
    <a-modal
      v-model:open="versionFormVisible"
      :title="versionFormTitle"
      :width="700"
      @ok="handleSubmitVersion"
      @cancel="handleCancelVersion"
    >
      <a-form :model="versionFormData" :label-col="{ span: 6 }" :wrapper-col="{ span: 18 }">
        <a-form-item label="状态">
          <a-select v-model:value="versionFormData.status">
            <a-select-option value="DRAFT">草稿</a-select-option>
            <a-select-option value="ACTIVE">激活</a-select-option>
            <a-select-option value="ARCHIVED">归档</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="定义(JSON)">
          <a-textarea v-model:value="versionFormData.definition" :rows="8" placeholder="请输入JSON格式的定义" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 节点表单弹窗 -->
    <a-modal
      v-model:open="nodeFormVisible"
      :title="nodeFormTitle"
      :width="800"
      @ok="handleSubmitNode"
      @cancel="handleCancelNode"
    >
      <a-form :model="nodeFormData" :label-col="{ span: 6 }" :wrapper-col="{ span: 18 }">
        <a-form-item label="节点编码" required>
          <a-input v-model:value="nodeFormData.nodeCode" placeholder="请输入节点编码（唯一）" />
        </a-form-item>
        <a-form-item label="任务" required>
          <a-select
            v-model:value="nodeFormData.taskId"
            placeholder="请选择任务"
            show-search
            :filter-option="filterTaskOption"
          >
            <a-select-option v-for="task in tasks" :key="task.id" :value="task.id">
              {{ task.name }} ({{ task.code || task.id }})
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="节点名称">
          <a-input v-model:value="nodeFormData.name" placeholder="请输入节点名称" />
        </a-form-item>
        <a-form-item label="覆盖参数">
          <a-textarea v-model:value="nodeFormData.overrideParams" :rows="4" placeholder="请输入JSON格式的覆盖参数" />
        </a-form-item>
        <a-form-item label="超时(秒)">
          <a-input-number v-model:value="nodeFormData.timeoutSec" :min="0" style="width: 100%" />
        </a-form-item>
        <a-form-item label="最大重试">
          <a-input-number v-model:value="nodeFormData.maxRetry" :min="0" style="width: 100%" />
        </a-form-item>
        <a-form-item label="并行组">
          <a-input v-model:value="nodeFormData.parallelGroup" placeholder="请输入并行组标识" />
        </a-form-item>
        <a-form-item label="元数据">
          <a-textarea v-model:value="nodeFormData.meta" :rows="3" placeholder="请输入JSON格式的元数据" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 边表单弹窗 -->
    <a-modal
      v-model:open="edgeFormVisible"
      title="添加依赖"
      :width="600"
      @ok="handleSubmitEdge"
      @cancel="handleCancelEdge"
    >
      <a-form :model="edgeFormData" :label-col="{ span: 6 }" :wrapper-col="{ span: 18 }">
        <a-form-item label="上游节点" required>
          <a-select v-model:value="edgeFormData.fromNodeCode" placeholder="请选择上游节点">
            <a-select-option v-for="node in nodes" :key="node.nodeCode" :value="node.nodeCode">
              {{ node.nodeCode }} - {{ node.name || '-' }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="下游节点" required>
          <a-select v-model:value="edgeFormData.toNodeCode" placeholder="请选择下游节点">
            <a-select-option v-for="node in nodes" :key="node.nodeCode" :value="node.nodeCode">
              {{ node.nodeCode }} - {{ node.name || '-' }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="条件">
          <a-textarea v-model:value="edgeFormData.condition" :rows="3" placeholder="请输入JSON格式的条件" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- Cron 表达式设计器弹窗 -->
    <a-modal
      v-model:open="cronDesignerVisible"
      title="Cron 表达式设计"
      :width="480"
      @ok="applyCronExpression"
      @cancel="cronDesignerVisible = false"
    >
      <CronDesigner v-model="cronDesignerValue" />
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import CronDesigner from '@/components/scheduling/CronDesigner.vue'
import { message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuth } from '@/auth/auth'
import { extractAuthoritiesFromJwt } from '@/utils/jwt'
import { SCHEDULING_CONSOLE_VIEW, SCHEDULING_CONSOLE_CONFIG, SCHEDULING_RUN_CONTROL, SCHEDULING_WILDCARD } from '@/constants/permission'
import { getActiveTenantId, resolveActiveTenantQueryValue, withActiveTenantQuery } from '@/utils/tenant'
import {
  getDag,
  updateDag,
  triggerDag,
  stopDag,
  retryDag,
  pauseDag,
  resumeDag,
  listDagVersions,
  createDagVersion,
  updateDagVersion,
  getDagNodes as getDagNodesApi,
  createDagNode,
  updateDagNode,
  deleteDagNode,
  getUpstreamNodes,
  getDownstreamNodes,
  getDagEdges as getDagEdgesApi,
  createDagEdge,
  deleteDagEdge,
  taskList,
} from '@/api/scheduling'

const router = useRouter()
const route = useRoute()
const { user } = useAuth()

const schedulingAuthorities = computed(() =>
  extractAuthoritiesFromJwt(user.value?.access_token).filter((a) => a.startsWith('scheduling:')),
)
const canReadScheduling = computed(() =>
  schedulingAuthorities.value.includes(SCHEDULING_CONSOLE_VIEW) || schedulingAuthorities.value.includes(SCHEDULING_WILDCARD),
)
const canManageSchedulingConfig = computed(() =>
  schedulingAuthorities.value.includes(SCHEDULING_CONSOLE_CONFIG) ||
  schedulingAuthorities.value.includes(SCHEDULING_WILDCARD),
)
const canOperateSchedulingRun = computed(() =>
  schedulingAuthorities.value.includes(SCHEDULING_RUN_CONTROL) ||
  schedulingAuthorities.value.includes(SCHEDULING_WILDCARD),
)

// 支持 query.id 或 query.dagId（与 DAG 列表跳转及菜单链接保持一致）
const dagId = computed(() => Number(route.query.id) || Number(route.query.dagId))
const activeTab = ref('info')
const triggering = ref(false)
const stopping = ref(false)
const retrying = ref(false)
const dagInfo = ref<any>({})
const dagFormVisible = ref(false)
const dagFormData = reactive({
  id: undefined as number | undefined,
  code: '',
  name: '',
  description: '',
  cronExpression: '' as string,
  cronTimezone: undefined as string | undefined,
  cronEnabled: true,
  enabled: true,
})

const isCronConfigured = (record: { cronExpression?: string | null }) => {
  return Boolean(record.cronExpression && String(record.cronExpression).trim())
}

const isCronEffective = (record: {
  enabled?: boolean
  cronEnabled?: boolean | null
  cronExpression?: string | null
  currentVersionId?: number | null
}) => {
  if (!Boolean(record.enabled)) return false
  if (record.cronEnabled === false) return false
  if (!isCronConfigured(record)) return false
  return Boolean(record.currentVersionId)
}

const getCronEffectiveReason = (record: {
  enabled?: boolean
  cronEnabled?: boolean | null
  cronExpression?: string | null
  currentVersionId?: number | null
}) => {
  if (!isCronConfigured(record)) return undefined
  if (record.enabled === false) return 'DAG 已禁用'
  if (record.cronEnabled === false) return 'Cron 已禁用'
  if (!record.currentVersionId) return '无 ACTIVE 版本，Cron 不会生效'
  return 'Cron 已生效（以 ACTIVE 版本执行）'
}

const cronDesignerVisible = ref(false)
const cronDesignerValue = ref('')
function openCronDesigner() {
  cronDesignerValue.value = dagFormData.cronExpression || ''
  cronDesignerVisible.value = true
}
function applyCronExpression() {
  dagFormData.cronExpression = cronDesignerValue.value.trim()
  cronDesignerVisible.value = false
}

// 版本相关
const versions = ref<any[]>([])
const versionsLoading = ref(false)
const versionFormVisible = ref(false)
const versionFormTitle = ref('创建版本')
const versionFormData = reactive({
  id: undefined as number | undefined,
  status: 'DRAFT',
  definition: '',
})

// 节点相关
const selectedVersionId = ref<number | undefined>(undefined)
const nodes = ref<any[]>([])
const nodesLoading = ref(false)
const tasks = ref<any[]>([])
const nodeFormVisible = ref(false)
const nodeFormTitle = ref('添加节点')
const nodeFormData = reactive({
  id: undefined as number | undefined,
  nodeCode: '',
  taskId: undefined as number | undefined,
  name: '',
  overrideParams: '',
  timeoutSec: undefined as number | undefined,
  maxRetry: undefined as number | undefined,
  parallelGroup: '',
  meta: '',
})

// 边相关
const selectedVersionIdForEdge = ref<number | undefined>(undefined)
const edges = ref<any[]>([])
const edgesLoading = ref(false)
const edgeFormVisible = ref(false)
const edgeFormData = reactive({
  fromNodeCode: '',
  toNodeCode: '',
  condition: '',
})

const versionColumns = [
  { title: '版本号', dataIndex: 'versionNo', key: 'versionNo', width: 100 },
  { title: '状态', key: 'status', width: 120 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 180 },
  { title: '操作', key: 'action', width: 300 },
]

const nodeColumns = [
  { title: '节点编码', dataIndex: 'nodeCode', key: 'nodeCode', width: 150 },
  { title: '节点名称', dataIndex: 'name', key: 'name' },
  { title: '任务ID', dataIndex: 'taskId', key: 'taskId', width: 100 },
  { title: '超时(秒)', dataIndex: 'timeoutSec', key: 'timeoutSec', width: 100 },
  { title: '最大重试', dataIndex: 'maxRetry', key: 'maxRetry', width: 100 },
  { title: '操作', key: 'action', width: 250, fixed: 'right' },
]

const edgeColumns = [
  { title: '上游节点', dataIndex: 'fromNodeCode', key: 'fromNodeCode' },
  { title: '下游节点', dataIndex: 'toNodeCode', key: 'toNodeCode' },
  { title: '条件', dataIndex: 'condition', key: 'condition' },
  { title: '操作', key: 'action', width: 100, fixed: 'right' },
]

const canTriggerDag = computed(() => Boolean(dagInfo.value.enabled && dagInfo.value.currentVersionId))
const canStopDag = computed(() => Boolean(dagInfo.value.enabled && dagInfo.value.hasRunningRun))
const canRetryDag = computed(() => Boolean(dagInfo.value.enabled && dagInfo.value.hasRetryableRun))

const triggerDisabledReason = computed(() => {
  if (dagInfo.value.enabled === false) {
    return 'DAG 已禁用'
  }
  if (!dagInfo.value.currentVersionId) {
    return '请先创建并激活版本'
  }
  return undefined
})

const stopDisabledReason = computed(() => {
  if (dagInfo.value.enabled === false) {
    return 'DAG 已禁用'
  }
  if (!dagInfo.value.hasRunningRun) {
    return '当前没有运行中的 Run'
  }
  return undefined
})

const retryDisabledReason = computed(() => {
  if (dagInfo.value.enabled === false) {
    return 'DAG 已禁用'
  }
  if (!dagInfo.value.hasRetryableRun) {
    return '当前没有可重试的失败运行'
  }
  return undefined
})

const getVersionStatusColor = (status: string) => {
  const map: Record<string, string> = {
    DRAFT: 'default',
    ACTIVE: 'green',
    ARCHIVED: 'gray',
  }
  return map[status] || 'default'
}

const filterTaskOption = (input: string, option: any) => {
  return option.children.toLowerCase().indexOf(input.toLowerCase()) >= 0
}

const loadDag = async () => {
  try {
    dagInfo.value = await getDag(dagId.value)
  } catch (error: any) {
    message.error(error.message || '加载DAG信息失败')
  }
}

const loadVersions = async () => {
  versionsLoading.value = true
  try {
    versions.value = await listDagVersions(dagId.value)
    // 自动选择激活版本或第一个版本
    const activeVersion = versions.value.find((v) => v.status === 'ACTIVE')
    if (activeVersion) {
      selectedVersionId.value = activeVersion.id
      selectedVersionIdForEdge.value = activeVersion.id
      loadNodes()
      loadEdges()
    } else if (versions.value.length > 0) {
      selectedVersionId.value = versions.value[0].id
      selectedVersionIdForEdge.value = versions.value[0].id
      loadNodes()
      loadEdges()
    }
  } catch (error: any) {
    message.error(error.message || '加载版本列表失败')
  } finally {
    versionsLoading.value = false
  }
}

const loadNodes = async () => {
  if (!selectedVersionId.value) return
  nodesLoading.value = true
  try {
    nodes.value = await getDagNodesApi(dagId.value, selectedVersionId.value)
  } catch (error: any) {
    message.error(error.message || '加载节点列表失败')
  } finally {
    nodesLoading.value = false
  }
}

const loadEdges = async () => {
  if (!selectedVersionIdForEdge.value) return
  edgesLoading.value = true
  try {
    edges.value = await getDagEdgesApi(dagId.value, selectedVersionIdForEdge.value)
  } catch (error: any) {
    message.error(error.message || '加载依赖列表失败')
  } finally {
    edgesLoading.value = false
  }
}

const loadTasks = async () => {
  try {
    const res = await taskList({ current: 1, pageSize: 1000 })
    tasks.value = res.records
  } catch (error: any) {
    console.error('加载任务列表失败:', error)
  }
}

const handleBack = () => {
  router.back()
}

const handleHistory = () => {
  router.push({
    path: '/scheduling/dag/history',
    query: withActiveTenantQuery(
      { dagId: String(dagId.value) },
      resolveActiveTenantQueryValue(route.query) ?? getActiveTenantId(),
    ),
  })
}

const filterTimezoneOption = (input: string, option: any) => {
  return option.children[0].children.toLowerCase().includes(input.toLowerCase())
}

const handleEdit = () => {
  Object.assign(dagFormData, {
    id: dagInfo.value.id,
    code: dagInfo.value.code || '',
    name: dagInfo.value.name,
    description: dagInfo.value.description || '',
    cronExpression: dagInfo.value.cronExpression ?? '',
    cronTimezone: dagInfo.value.cronTimezone || undefined,
    cronEnabled: dagInfo.value.cronEnabled !== undefined ? dagInfo.value.cronEnabled : true,
    enabled: dagInfo.value.enabled !== undefined ? dagInfo.value.enabled : true,
  })
  dagFormVisible.value = true
}

const handleSubmitDag = async () => {
  try {
    await updateDag(dagFormData.id!, dagFormData)
    message.success('更新成功')
    dagFormVisible.value = false
    loadDag()
  } catch (error: any) {
    message.error(error.message || '更新失败')
  }
}

const handleCancelDag = () => {
  dagFormVisible.value = false
}

const handleTrigger = async () => {
  if (!canTriggerDag.value) {
    message.warning(triggerDisabledReason.value || '当前 DAG 不可触发')
    return
  }
  triggering.value = true
  try {
    await triggerDag(dagId.value)
    message.success('已创建新的手动运行')
    loadDag()
  } catch (error: any) {
    message.error(error.message || '触发失败')
  } finally {
    triggering.value = false
  }
}

const handlePause = async () => {
  try {
    await pauseDag(dagId.value)
    message.success('已暂停 DAG 调度')
    loadDag()
  } catch (error: any) {
    message.error(error.message || '暂停失败')
  }
}

const handleResume = async () => {
  try {
    await resumeDag(dagId.value)
    message.success('已恢复 DAG 调度')
    loadDag()
  } catch (error: any) {
    message.error(error.message || '恢复失败')
  }
}

const handleStop = async () => {
  if (!canStopDag.value) {
    message.warning(stopDisabledReason.value || '当前 DAG 不可停止')
    return
  }
  stopping.value = true
  try {
    await stopDag(dagId.value)
    message.success('已停止当前 DAG 的运行中任务')
    loadDag()
  } catch (error: any) {
    message.error(error.message || '停止失败')
  } finally {
    stopping.value = false
  }
}

const handleRetry = async () => {
  if (!canRetryDag.value) {
    message.warning(retryDisabledReason.value || '当前 DAG 不可重试')
    return
  }
  retrying.value = true
  try {
    await retryDag(dagId.value)
    message.success('已提交最近失败运行的重试')
    loadDag()
  } catch (error: any) {
    message.error(error.message || '重试失败')
  } finally {
    retrying.value = false
  }
}

const handleCreateVersion = () => {
  versionFormTitle.value = '创建版本'
  Object.assign(versionFormData, {
    id: undefined,
    status: 'DRAFT',
    definition: '',
  })
  versionFormVisible.value = true
}

const handleViewVersion = (record: any) => {
  // 切换到该版本
  selectedVersionId.value = record.id
  selectedVersionIdForEdge.value = record.id
  loadNodes()
  loadEdges()
  activeTab.value = 'nodes'
}

const handleEditVersion = (record: any) => {
  versionFormTitle.value = '编辑版本'
  Object.assign(versionFormData, {
    id: record.id,
    status: record.status,
    definition: record.definition || '',
  })
  versionFormVisible.value = true
}

const handleActivateVersion = async (record: any) => {
  try {
    await updateDagVersion(dagId.value, record.id, { status: 'ACTIVE' })
    message.success('激活成功')
    loadVersions()
  } catch (error: any) {
    message.error(error.message || '激活失败')
  }
}

const handleSwitchVersion = (record: any) => {
  selectedVersionId.value = record.id
  selectedVersionIdForEdge.value = record.id
  loadNodes()
  loadEdges()
  activeTab.value = 'nodes'
}

const handleSubmitVersion = async () => {
  try {
    if (versionFormData.id) {
      await updateDagVersion(dagId.value, versionFormData.id, versionFormData)
      message.success('更新成功')
    } else {
      await createDagVersion(dagId.value, versionFormData)
      message.success('创建成功')
    }
    versionFormVisible.value = false
    loadVersions()
  } catch (error: any) {
    const msg = error?.message ?? ''
    if (msg.includes('版本号已被其他请求占用')) {
      message.error('当前版本列表已被其他请求更新，请刷新后重试')
    } else {
      message.error(msg || '操作失败')
    }
  }
}

const handleCancelVersion = () => {
  versionFormVisible.value = false
}

const handleAddNode = () => {
  if (!selectedVersionId.value) {
    message.warning('请先选择版本')
    return
  }
  nodeFormTitle.value = '添加节点'
  Object.assign(nodeFormData, {
    id: undefined,
    nodeCode: '',
    taskId: undefined,
    name: '',
    overrideParams: '',
    timeoutSec: undefined,
    maxRetry: undefined,
    parallelGroup: '',
    meta: '',
  })
  nodeFormVisible.value = true
}

const handleEditNode = (record: any) => {
  nodeFormTitle.value = '编辑节点'
  Object.assign(nodeFormData, {
    id: record.id,
    nodeCode: record.nodeCode,
    taskId: record.taskId,
    name: record.name || '',
    overrideParams: record.overrideParams || '',
    timeoutSec: record.timeoutSec,
    maxRetry: record.maxRetry,
    parallelGroup: record.parallelGroup || '',
    meta: record.meta || '',
  })
  nodeFormVisible.value = true
}

const handleSubmitNode = async () => {
  if (!nodeFormData.nodeCode) {
    message.error('请输入节点编码')
    return
  }
  if (!nodeFormData.taskId) {
    message.error('请选择任务')
    return
  }
  if (!selectedVersionId.value) {
    message.error('请先选择版本')
    return
  }
  try {
    if (nodeFormData.id) {
      await updateDagNode(dagId.value, selectedVersionId.value, nodeFormData.id, nodeFormData)
      message.success('更新成功')
    } else {
      await createDagNode(dagId.value, selectedVersionId.value, nodeFormData)
      message.success('创建成功')
    }
    nodeFormVisible.value = false
    loadNodes()
  } catch (error: any) {
    message.error(error.message || '操作失败')
  }
}

const handleCancelNode = () => {
  nodeFormVisible.value = false
}

const handleDeleteNode = async (nodeId: number) => {
  if (!selectedVersionId.value) return
  try {
    await deleteDagNode(dagId.value, selectedVersionId.value, nodeId)
    message.success('删除成功')
    loadNodes()
  } catch (error: any) {
    message.error(error.message || '删除失败')
  }
}

const handleViewUpstream = async (record: any) => {
  if (!selectedVersionId.value) return
  try {
    const upstream = await getUpstreamNodes(dagId.value, selectedVersionId.value, record.id)
    message.info(`上游节点: ${upstream.map((n: any) => n.nodeCode).join(', ') || '无'}`)
  } catch (error: any) {
    message.error(error.message || '查询失败')
  }
}

const handleViewDownstream = async (record: any) => {
  if (!selectedVersionId.value) return
  try {
    const downstream = await getDownstreamNodes(dagId.value, selectedVersionId.value, record.id)
    message.info(`下游节点: ${downstream.map((n: any) => n.nodeCode).join(', ') || '无'}`)
  } catch (error: any) {
    message.error(error.message || '查询失败')
  }
}

const handleAddEdge = () => {
  if (!selectedVersionIdForEdge.value) {
    message.warning('请先选择版本')
    return
  }
  if (nodes.value.length === 0) {
    message.warning('请先添加节点')
    return
  }
  Object.assign(edgeFormData, {
    fromNodeCode: '',
    toNodeCode: '',
    condition: '',
  })
  edgeFormVisible.value = true
}

const handleSubmitEdge = async () => {
  if (!edgeFormData.fromNodeCode || !edgeFormData.toNodeCode) {
    message.error('请选择上游和下游节点')
    return
  }
  if (edgeFormData.fromNodeCode === edgeFormData.toNodeCode) {
    message.error('节点不能依赖自身')
    return
  }
  if (!selectedVersionIdForEdge.value) {
    message.error('请先选择版本')
    return
  }
  try {
    await createDagEdge(dagId.value, selectedVersionIdForEdge.value, edgeFormData)
    message.success('创建成功')
    edgeFormVisible.value = false
    loadEdges()
  } catch (error: any) {
    message.error(error.message || '创建失败')
  }
}

const handleCancelEdge = () => {
  edgeFormVisible.value = false
}

const handleDeleteEdge = async (edgeId: number) => {
  if (!selectedVersionIdForEdge.value) return
  try {
    await deleteDagEdge(dagId.value, selectedVersionIdForEdge.value, edgeId)
    message.success('删除成功')
    loadEdges()
  } catch (error: any) {
    message.error(error.message || '删除失败')
  }
}

const DAG_LIST_PATH = '/scheduling/dag'
const buildDagListQuery = () =>
  withActiveTenantQuery({}, resolveActiveTenantQueryValue(route.query) ?? getActiveTenantId())

onMounted(() => {
  const id = dagId.value
  if (!id || Number.isNaN(id)) {
    message.warning('请先选择 DAG：从 DAG 列表中点击「详情」进入，或使用链接带参数 ?id=xxx 或 ?dagId=xxx')
    router.replace({
      path: DAG_LIST_PATH,
      query: buildDagListQuery(),
    })
    return
  }
  loadDag()
  loadVersions()
  loadTasks()
})
</script>

<style scoped>
.dag-detail-container {
  padding: 16px;
}

.info-card {
  margin-bottom: 16px;
}
</style>
