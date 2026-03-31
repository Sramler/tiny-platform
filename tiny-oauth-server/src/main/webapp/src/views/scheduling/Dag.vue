<template>
  <div class="content-container" style="position: relative;">
    <div class="content-card">
      <div class="form-container">
        <a-form layout="inline" :model="query">
          <a-form-item label="编码">
            <a-input v-model:value="query.code" placeholder="请输入编码" />
          </a-form-item>
          <a-form-item label="名称">
            <a-input v-model:value="query.name" placeholder="请输入名称" />
          </a-form-item>
          <a-form-item>
            <a-button type="primary" @click="handleSearch">搜索</a-button>
            <a-button class="ml-2" @click="handleReset">重置</a-button>
          </a-form-item>
        </a-form>
      </div>

      <div class="toolbar-container">
        <div class="table-title">DAG 列表</div>
        <div class="table-actions">
          <a-button type="link" @click="handleCreate" class="toolbar-btn">
            <template #icon>
              <PlusOutlined />
            </template>
            新建
          </a-button>
          <a-tooltip title="刷新">
            <span class="action-icon" @click="handleRefresh">
              <ReloadOutlined :spin="refreshing" />
            </span>
          </a-tooltip>
        </div>
      </div>

      <a-table
        :columns="columns"
        :data-source="dataSource"
        :loading="loading"
        :pagination="pagination"
        :row-selection="{ selectedRowKeys, onChange: onSelectChange }"
        @change="handleTableChange"
        row-key="id"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'enabled'">
            <a-tag :color="record.enabled ? 'green' : 'red'">
              {{ record.enabled ? '启用' : '禁用' }}
            </a-tag>
          </template>
          <template v-if="column.key === 'cronState'">
            <a-tooltip :title="getCronEffectiveReason(record)">
              <a-tag v-if="isCronConfigured(record)" :color="isCronEffective(record) ? 'green' : 'red'">
                {{ isCronEffective(record) ? 'Cron 生效' : 'Cron 未生效' }}
              </a-tag>
              <span v-else style="color: #999;">-</span>
            </a-tooltip>
          </template>
          <template v-if="column.key === 'runState'">
            <a-space size="small">
              <a-tag v-if="record.hasRunningRun" color="processing">运行中</a-tag>
              <a-tag v-if="record.hasRetryableRun" color="orange">可重试</a-tag>
              <span v-if="!record.hasRunningRun && !record.hasRetryableRun" style="color: #999;">-</span>
            </a-space>
          </template>
          <template v-if="column.key === 'currentVersionId'">
            {{ record.currentVersionId || '-' }}
          </template>
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="handleEdit(record)">编辑</a-button>
              <a-button type="link" size="small" @click="handleDetail(record)">详情</a-button>
              <a-button type="link" size="small" @click="handleHistory(record)">历史</a-button>
              <a-tooltip :title="getTriggerDisabledReason(record)">
                <span>
                  <a-popconfirm
                    title="确认立即按当前 ACTIVE 版本创建一条新的手动运行吗？"
                    ok-text="确认触发"
                    cancel-text="取消"
                    :disabled="!canTriggerDag(record)"
                    @confirm="handleTrigger(record)"
                  >
                    <a-button type="link" size="small" :disabled="!canTriggerDag(record)">触发</a-button>
                  </a-popconfirm>
                </span>
              </a-tooltip>
              <a-tooltip :title="getStopDisabledReason(record)">
                <span>
                  <a-popconfirm
                    title="确认停止该 DAG 当前所有 RUNNING 运行，并暂停 Quartz 调度吗？如需只处理单次运行，请前往历史页。"
                    ok-text="确认停止"
                    cancel-text="取消"
                    :disabled="!canStopDag(record)"
                    @confirm="handleStop(record)"
                  >
                    <a-button type="link" size="small" :disabled="!canStopDag(record)">停止 DAG</a-button>
                  </a-popconfirm>
                </span>
              </a-tooltip>
              <a-tooltip :title="getRetryDisabledReason(record)">
                <span>
                  <a-popconfirm
                    title="确认重试该 DAG 最近一次失败运行吗？系统会创建新的 Run。如需指定某次运行，请前往历史页。"
                    ok-text="确认重试"
                    cancel-text="取消"
                    :disabled="!canRetryDag(record)"
                    @confirm="handleRetry(record)"
                  >
                    <a-button type="link" size="small" :disabled="!canRetryDag(record)">重试最近失败运行</a-button>
                  </a-popconfirm>
                </span>
              </a-tooltip>
              <a-popconfirm title="确定要删除吗？" @confirm="handleDelete(record.id)">
                <a-button type="link" danger size="small">删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </div>

    <!-- DAG 表单弹窗 -->
    <a-modal
      v-model:open="formVisible"
      :title="formTitle"
      :width="700"
      @ok="handleSubmit"
      @cancel="handleCancel"
    >
      <a-form :model="formData" :label-col="{ span: 6 }" :wrapper-col="{ span: 18 }">
        <a-form-item label="编码">
          <a-input v-model:value="formData.code" placeholder="请输入编码（可选）" />
        </a-form-item>
        <a-form-item label="名称" required>
          <a-input v-model:value="formData.name" placeholder="请输入名称" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="formData.description" :rows="3" placeholder="请输入描述" />
        </a-form-item>
        <a-form-item label="Cron 表达式">
          <a-input-group compact>
            <a-input
              v-model:value="formData.cronExpression"
              placeholder="可选，如 0 0 2 * * ? 表示每日 2 点"
              allow-clear
              style="width: calc(100% - 64px)"
            />
            <a-button type="default" @click="openCronDesigner">设计</a-button>
          </a-input-group>
        </a-form-item>
        <a-form-item label="Cron 时区">
          <a-select
            v-model:value="formData.cronTimezone"
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
          <a-switch v-model:checked="formData.cronEnabled" />
          <span class="ml-2" style="color: #999; font-size: 12px">与 DAG 启用状态独立控制</span>
        </a-form-item>
        <a-form-item label="是否启用">
          <a-switch v-model:checked="formData.enabled" />
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
import { ref, reactive, onMounted, onBeforeUnmount, computed } from 'vue'
import CronDesigner from '@/components/scheduling/CronDesigner.vue'
import { message } from 'ant-design-vue'
import type { ColumnsType } from 'ant-design-vue/es/table'
import type { Key } from 'ant-design-vue/es/_util/type'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { dagList, createDag, updateDag, deleteDag, triggerDag, stopDag, retryDag } from '@/api/scheduling'
import { throttle } from '@/utils/debounce'
import { useAuth } from '@/auth/auth'
import { extractAuthoritiesFromJwt } from '@/utils/jwt'
import { SCHEDULING_CONSOLE_CONFIG, SCHEDULING_RUN_CONTROL, SCHEDULING_WILDCARD } from '@/constants/permission'
import { getActiveTenantId, resolveActiveTenantQueryValue, withActiveTenantQuery } from '@/utils/tenant'
import { ACTIVE_SCOPE_CHANGED_EVENT } from '@/utils/activeScopeEvents'

const route = useRoute()
const router = useRouter()
const { user } = useAuth()
const schedulingAuthorities = computed(() =>
  extractAuthoritiesFromJwt(user.value?.access_token).filter((a) => a.startsWith('scheduling:')),
)
const canManageSchedulingConfig = computed(() =>
  schedulingAuthorities.value.includes(SCHEDULING_CONSOLE_CONFIG) ||
  schedulingAuthorities.value.includes(SCHEDULING_WILDCARD),
)
const canOperateSchedulingRun = computed(() =>
  schedulingAuthorities.value.includes(SCHEDULING_RUN_CONTROL) ||
  schedulingAuthorities.value.includes(SCHEDULING_WILDCARD),
)
const loading = ref(false)
const refreshing = ref(false)
const formVisible = ref(false)
const formTitle = ref('新建 DAG')
const selectedRowKeys = ref<number[]>([])
const dataSource = ref<any[]>([])

const query = reactive({
  code: '',
  name: '',
})

const formData = reactive({
  id: undefined as number | undefined,
  code: '',
  name: '',
  description: '',
  cronExpression: '' as string,
  cronTimezone: undefined as string | undefined,
  cronEnabled: true,
  enabled: true,
})

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showTotal: (total: number) => `共 ${total} 条`,
})

const cronDesignerVisible = ref(false)
const cronDesignerValue = ref('')
function openCronDesigner() {
  cronDesignerValue.value = formData.cronExpression || ''
  cronDesignerVisible.value = true
}
function applyCronExpression() {
  formData.cronExpression = cronDesignerValue.value.trim()
  cronDesignerVisible.value = false
}

const columns: ColumnsType<any> = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '编码', dataIndex: 'code', key: 'code', width: 150 },
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '描述', dataIndex: 'description', key: 'description' },
  { title: '状态', key: 'enabled', width: 100 },
  { title: 'Cron', key: 'cronState', width: 120 },
  { title: '运行态', key: 'runState', width: 150 },
  { title: '当前版本ID', key: 'currentVersionId', width: 120 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 180 },
  { title: '操作', key: 'action', width: 350, fixed: 'right' as const },
]

const loadData = async () => {
  loading.value = true
  try {
    const params = {
      current: pagination.current,
      pageSize: pagination.pageSize,
      ...query,
    }
    const res = await dagList(params)
    dataSource.value = res.records
    pagination.total = res.total
  } catch (error: any) {
    message.error(error.message || '加载数据失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = throttle(() => {
  pagination.current = 1
  loadData()
}, 500)

const handleReset = throttle(() => {
  query.code = ''
  query.name = ''
  pagination.current = 1
  loadData()
}, 500)

const handleRefresh = throttle(() => {
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

const onSelectChange = (keys: Key[]) => {
  selectedRowKeys.value = keys.map((k) => Number(k)).filter((k) => !Number.isNaN(k))
}

const filterTimezoneOption = (input: string, option: any) => {
  return option.children[0].children.toLowerCase().includes(input.toLowerCase())
}

const handleCreate = () => {
  if (!canManageSchedulingConfig.value) {
    message.warning('当前账户没有调度配置管理权限')
    return
  }
  formTitle.value = '新建 DAG'
  Object.assign(formData, {
    id: undefined,
    code: '',
    name: '',
    description: '',
    cronExpression: '',
    cronTimezone: undefined,
    cronEnabled: true,
    enabled: true,
  })
  formVisible.value = true
}

const handleEdit = (record: any) => {
  if (!canManageSchedulingConfig.value) {
    message.warning('当前账户没有调度配置管理权限')
    return
  }
  formTitle.value = '编辑 DAG'
  Object.assign(formData, {
    id: record.id,
    code: record.code || '',
    name: record.name,
    description: record.description || '',
    cronExpression: record.cronExpression ?? '',
    cronTimezone: record.cronTimezone || undefined,
    cronEnabled: record.cronEnabled !== undefined ? record.cronEnabled : true,
    enabled: record.enabled !== undefined ? record.enabled : true,
  })
  formVisible.value = true
}

const handleDetail = (record: any) => {
  router.push({
    path: '/scheduling/dag/detail',
    query: withActiveTenantQuery(
      { id: record.id },
      resolveActiveTenantQueryValue(route.query) ?? getActiveTenantId(),
    ),
  })
}

const handleHistory = (record: any) => {
  router.push({
    path: '/scheduling/dag/history',
    query: withActiveTenantQuery(
      { dagId: record.id },
      resolveActiveTenantQueryValue(route.query) ?? getActiveTenantId(),
    ),
  })
}

const canTriggerDag = (record: { enabled?: boolean; currentVersionId?: number | null }) => {
  return Boolean(record.enabled && record.currentVersionId)
}

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

const getTriggerDisabledReason = (record: { enabled?: boolean; currentVersionId?: number | null }) => {
  if (record.enabled === false) {
    return 'DAG 已禁用'
  }
  if (!record.currentVersionId) {
    return '请先创建并激活版本'
  }
  return undefined
}

const canStopDag = (record: { enabled?: boolean; hasRunningRun?: boolean | null }) => {
  return Boolean(record.enabled && record.hasRunningRun)
}

const getStopDisabledReason = (record: { enabled?: boolean; hasRunningRun?: boolean | null }) => {
  if (record.enabled === false) {
    return 'DAG 已禁用'
  }
  if (!record.hasRunningRun) {
    return '当前没有运行中的 Run'
  }
  return undefined
}

const canRetryDag = (record: { enabled?: boolean; hasRetryableRun?: boolean | null }) => {
  return Boolean(record.enabled && record.hasRetryableRun)
}

const getRetryDisabledReason = (record: { enabled?: boolean; hasRetryableRun?: boolean | null }) => {
  if (record.enabled === false) {
    return 'DAG 已禁用'
  }
  if (!record.hasRetryableRun) {
    return '当前没有可重试的失败运行'
  }
  return undefined
}

const handleTrigger = async (record: any) => {
  if (!canTriggerDag(record)) {
    message.warning(getTriggerDisabledReason(record) || '当前 DAG 不可触发')
    return
  }
  if (!canOperateSchedulingRun.value) {
    message.warning('当前账户没有调度运行操作权限')
    return
  }
  try {
    await triggerDag(record.id)
    message.success('已创建新的手动运行')
    loadData()
  } catch (error: any) {
    message.error(error.message || '触发失败')
  }
}

const handleStop = async (record: any) => {
  if (!canStopDag(record)) {
    message.warning(getStopDisabledReason(record) || '当前 DAG 不可停止')
    return
  }
  if (!canOperateSchedulingRun.value) {
    message.warning('当前账户没有调度运行操作权限')
    return
  }
  try {
    await stopDag(record.id)
    message.success('已停止当前 DAG 的运行中任务')
    loadData()
  } catch (error: any) {
    message.error(error.message || '停止失败')
  }
}

const handleRetry = async (record: any) => {
  if (!canRetryDag(record)) {
    message.warning(getRetryDisabledReason(record) || '当前 DAG 不可重试')
    return
  }
  if (!canOperateSchedulingRun.value) {
    message.warning('当前账户没有调度运行操作权限')
    return
  }
  try {
    await retryDag(record.id)
    message.success('已提交最近失败运行的重试')
    loadData()
  } catch (error: any) {
    message.error(error.message || '重试失败')
  }
}

const buildDagPayload = () => ({
  code: String(formData.code ?? '').trim(),
  name: String(formData.name ?? '').trim(),
  description: formData.description ?? '',
  cronExpression: formData.cronExpression ? String(formData.cronExpression).trim() : '',
  cronTimezone: formData.cronTimezone,
  cronEnabled: Boolean(formData.cronEnabled),
  enabled: Boolean(formData.enabled),
})

const handleSubmit = async () => {
  if (!formData.name) {
    message.error('请输入名称')
    return
  }
  if (!canManageSchedulingConfig.value) {
    message.error('当前账户没有调度配置管理权限')
    return
  }
  try {
    const payload = buildDagPayload()
    if (formData.id) {
      await updateDag(formData.id, payload)
      message.success('更新成功')
    } else {
      await createDag(payload)
      message.success('创建成功')
    }
    formVisible.value = false
    loadData()
  } catch (error: any) {
    message.error(error.message || '操作失败')
  }
}

const handleCancel = () => {
  formVisible.value = false
}

const handleDelete = async (id: number) => {
  try {
    if (!canManageSchedulingConfig.value) {
      message.error('当前账户没有调度配置管理权限')
      return
    }
    await deleteDag(id)
    message.success('删除成功')
    loadData()
  } catch (error: any) {
    message.error(error.message || '删除失败')
  }
}

function handleActiveScopeChanged() {
  void loadData()
}

onMounted(() => {
  loadData()
  window.addEventListener(ACTIVE_SCOPE_CHANGED_EVENT, handleActiveScopeChanged)
})

onBeforeUnmount(() => {
  window.removeEventListener(ACTIVE_SCOPE_CHANGED_EVENT, handleActiveScopeChanged)
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

.table-title {
  font-size: 16px;
  font-weight: 500;
}

.table-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.toolbar-btn {
  padding: 0;
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
