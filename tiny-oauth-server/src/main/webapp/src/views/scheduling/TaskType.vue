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
        <div class="table-title">任务类型列表</div>
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
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="handleEdit(record)">编辑</a-button>
              <a-popconfirm title="确定要删除吗？" @confirm="handleDelete(record.id)">
                <a-button type="link" danger size="small">删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </div>

    <!-- 任务类型表单弹窗 -->
    <a-modal
      v-model:open="formVisible"
      :title="formTitle"
      :width="700"
      @ok="handleSubmit"
      @cancel="handleCancel"
    >
      <a-form :model="formData" :label-col="{ span: 6 }" :wrapper-col="{ span: 18 }">
        <a-form-item label="编码" required>
          <a-input v-model:value="formData.code" placeholder="请输入编码（唯一）" />
        </a-form-item>
        <a-form-item label="名称" required>
          <a-input v-model:value="formData.name" placeholder="请输入名称" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="formData.description" :rows="3" placeholder="请输入描述" />
        </a-form-item>
        <a-form-item label="执行器" extra="填写已注册的 Bean 名（如 shellExecutor）或全类名，选项来自 GET /scheduling/executors">
          <a-select
            v-model:value="formData.executor"
            placeholder="请选择执行器"
            allow-clear
            style="width: 100%"
            :options="executorOptions"
          />
        </a-form-item>
        <a-form-item label="参数Schema">
          <a-textarea v-model:value="formData.paramSchema" :rows="4" placeholder="请输入JSON格式的参数Schema" />
        </a-form-item>
        <a-form-item label="默认超时(秒)">
          <a-input-number v-model:value="formData.defaultTimeoutSec" :min="0" style="width: 100%" />
        </a-form-item>
        <a-form-item label="默认最大重试">
          <a-input-number v-model:value="formData.defaultMaxRetry" :min="0" style="width: 100%" />
        </a-form-item>
        <a-form-item label="是否启用">
          <a-switch v-model:checked="formData.enabled" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import { taskTypeList, createTaskType, updateTaskType, deleteTaskType, getExecutors } from '@/api/scheduling'
import { throttle } from '@/utils/debounce'
import { extractErrorFromAxios } from '@/utils/problemParser'
import { useAuth } from '@/auth/auth'
import { extractAuthoritiesFromJwt } from '@/utils/jwt'
import { SCHEDULING_CONSOLE_CONFIG, SCHEDULING_WILDCARD } from '@/constants/permission'

const { user } = useAuth()
const schedulingAuthorities = computed(() =>
  extractAuthoritiesFromJwt(user.value?.access_token).filter((a) => a.startsWith('scheduling:')),
)
const canManageSchedulingConfig = computed(() =>
  schedulingAuthorities.value.includes(SCHEDULING_CONSOLE_CONFIG) ||
  schedulingAuthorities.value.includes(SCHEDULING_WILDCARD),
)

const loading = ref(false)
const refreshing = ref(false)
const formVisible = ref(false)
const formTitle = ref('新建任务类型')
const selectedRowKeys = ref<number[]>([])
const dataSource = ref<any[]>([])
const executorOptions = ref<{ value: string; label: string }[]>([])

const query = reactive({
  code: '',
  name: '',
})

const formData = reactive({
  id: undefined as number | undefined,
  code: '',
  name: '',
  description: '',
  executor: '',
  paramSchema: '',
  defaultTimeoutSec: 0,
  defaultMaxRetry: 0,
  enabled: true,
})

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showTotal: (total: number) => `共 ${total} 条`,
})

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '编码', dataIndex: 'code', key: 'code', width: 150 },
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '描述', dataIndex: 'description', key: 'description' },
  { title: '执行器', dataIndex: 'executor', key: 'executor', width: 150 },
  { title: '默认超时(秒)', dataIndex: 'defaultTimeoutSec', key: 'defaultTimeoutSec', width: 120 },
  { title: '默认最大重试', dataIndex: 'defaultMaxRetry', key: 'defaultMaxRetry', width: 120 },
  { title: '状态', key: 'enabled', width: 100 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 180 },
  { title: '操作', key: 'action', width: 150, fixed: 'right' },
]

const loadExecutors = async () => {
  try {
    const list = await getExecutors()
    executorOptions.value = (Array.isArray(list) ? list : []).map((id: string) => ({ value: id, label: id }))
  } catch (e) {
    executorOptions.value = []
  }
}

const loadData = async () => {
  loading.value = true
  try {
    const params = {
      current: pagination.current,
      pageSize: pagination.pageSize,
      ...query,
    }
    const res = await taskTypeList(params)
    dataSource.value = res.records
    pagination.total = Number(res.total ?? 0)
  } catch (error: any) {
    message.error(extractErrorFromAxios(error, '加载数据失败'))
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

const onSelectChange = (keys: number[]) => {
  selectedRowKeys.value = keys
}

const handleCreate = () => {
  if (!canManageSchedulingConfig.value) {
    message.warning('当前账户没有调度配置管理权限')
    return
  }
  formTitle.value = '新建任务类型'
  Object.assign(formData, {
    id: undefined,
    code: '',
    name: '',
    description: '',
    executor: '',
    paramSchema: '',
    defaultTimeoutSec: 0,
    defaultMaxRetry: 0,
    enabled: true,
  })
  formVisible.value = true
}

const handleEdit = (record: any) => {
  if (!canManageSchedulingConfig.value) {
    message.warning('当前账户没有调度配置管理权限')
    return
  }
  formTitle.value = '编辑任务类型'
  // 兼容接口返回 camelCase / snake_case；数字字段统一为 number，避免 a-input-number 与后端 Integer 不一致
  const num = (v: unknown) => (v !== null && v !== undefined && v !== '' ? Number(v) : 0)
  const numOr = (v: unknown, def: number) => {
    const n = num(v)
    return Number.isNaN(n) ? def : n
  }
  Object.assign(formData, {
    id: record.id != null ? Number(record.id) : undefined,
    code: record.code ?? record['code'] ?? '',
    name: record.name ?? record['name'] ?? '',
    description: record.description ?? record['description'] ?? '',
    executor: record.executor ?? record['executor'] ?? '',
    paramSchema: record.paramSchema ?? record['param_schema'] ?? '',
    defaultTimeoutSec: numOr(record.defaultTimeoutSec ?? record['default_timeout_sec'], 0),
    defaultMaxRetry: numOr(record.defaultMaxRetry ?? record['default_max_retry'], 0),
    enabled: record.enabled !== undefined ? Boolean(record.enabled) : true,
  })
  formVisible.value = true
}

/** 构建创建/更新用 payload，保证数字与空串不导致后端 400 */
function buildTaskTypePayload() {
  const sec = Number(formData.defaultTimeoutSec)
  const retry = Number(formData.defaultMaxRetry)
  return {
    code: String(formData.code ?? '').trim(),
    name: String(formData.name ?? '').trim(),
    description: formData.description ?? '',
    executor: formData.executor ?? '',
    paramSchema: formData.paramSchema ?? '',
    defaultTimeoutSec: Number.isNaN(sec) || sec < 0 ? 0 : sec,
    defaultMaxRetry: Number.isNaN(retry) || retry < 0 ? 0 : retry,
    enabled: Boolean(formData.enabled),
  }
}

const handleSubmit = async () => {
  const code = String(formData.code ?? '').trim()
  const name = String(formData.name ?? '').trim()
  if (!code) {
    message.error('请输入编码')
    return
  }
  if (!name) {
    message.error('请输入名称')
    return
  }
  if (!canManageSchedulingConfig.value) {
    message.error('当前账户没有调度配置管理权限')
    return
  }
  try {
    const payload = buildTaskTypePayload()
    if (formData.id != null) {
      await updateTaskType(Number(formData.id), payload)
      message.success('更新成功')
    } else {
      await createTaskType(payload)
      message.success('创建成功')
    }
    formVisible.value = false
    loadData()
  } catch (error: any) {
    message.error(extractErrorFromAxios(error, '操作失败'))
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
    await deleteTaskType(id)
    message.success('删除成功')
    loadData()
  } catch (error: any) {
    message.error(error.message || '删除失败')
  }
}

onMounted(async () => {
  await loadExecutors()
  loadData()
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
