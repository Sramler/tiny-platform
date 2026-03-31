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
          <a-form-item label="任务类型">
            <a-select v-model:value="query.typeId" placeholder="请选择任务类型" style="width: 200px" allow-clear>
              <a-select-option v-for="type in taskTypes" :key="type.id" :value="type.id">
                {{ type.name }}
              </a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item>
            <a-button type="primary" @click="handleSearch">搜索</a-button>
            <a-button class="ml-2" @click="handleReset">重置</a-button>
          </a-form-item>
        </a-form>
      </div>

      <div class="toolbar-container">
        <div class="table-title">任务列表</div>
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
          <template v-if="column.key === 'typeId'">
            {{ taskTypeName(record.typeId) }}
          </template>
          <template v-if="column.key === 'enabled'">
            <a-tag :color="record.enabled ? 'green' : 'red'">
              {{ record.enabled ? '启用' : '禁用' }}
            </a-tag>
          </template>
          <template v-if="column.key === 'concurrencyPolicy'">
            <a-tag>{{ record.concurrencyPolicy || 'PARALLEL' }}</a-tag>
          </template>
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="handleEdit(record)">编辑</a-button>
              <a-button type="link" size="small" @click="handleView(record)">查看</a-button>
              <a-popconfirm title="确定要删除吗？" @confirm="handleDelete(record.id)">
                <a-button type="link" danger size="small">删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </div>

    <!-- 任务表单弹窗 -->
    <a-modal
      v-model:open="formVisible"
      :title="formTitle"
      :width="800"
      @ok="handleSubmit"
      @cancel="handleCancel"
    >
      <a-form ref="formRef" :model="formData" :label-col="{ span: 6 }" :wrapper-col="{ span: 18 }">
        <a-form-item label="编码">
          <a-input v-model:value="formData.code" placeholder="请输入编码（可选）" />
        </a-form-item>
        <a-form-item label="名称" name="name" :rules="[{ required: true, message: '请输入任务名称' }]">
          <a-input v-model:value="formData.name" placeholder="请输入名称" />
        </a-form-item>
        <a-form-item label="任务类型" required>
          <a-select v-model:value="formData.typeId" placeholder="请选择任务类型" @change="handleTypeChange">
            <a-select-option v-for="type in taskTypes" :key="type.id" :value="type.id">
              {{ type.name }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="formData.description" :rows="3" placeholder="请输入描述" />
        </a-form-item>
        <a-form-item label="参数" name="params" :rules="paramsRules">
          <a-textarea v-model:value="formData.params" :rows="4" placeholder="请输入JSON格式的参数（将按所选任务类型的 Schema 校验）" />
        </a-form-item>
        <a-form-item label="超时(秒)">
          <a-input-number v-model:value="formData.timeoutSec" :min="0" style="width: 100%" />
        </a-form-item>
        <a-form-item label="最大重试">
          <a-input-number v-model:value="formData.maxRetry" :min="0" style="width: 100%" />
        </a-form-item>
        <a-form-item label="重试策略" extra='当前仅支持 JSON 格式：{"delaySec": 60}，表示失败后延迟秒数再重试；不填或格式无效时默认 60 秒。'>
          <a-textarea v-model:value="formData.retryPolicy" :rows="2" placeholder='例如：{"delaySec":60}' />
        </a-form-item>
        <a-form-item label="并发策略">
          <a-select v-model:value="formData.concurrencyPolicy">
            <a-select-option value="SINGLETON">单例</a-select-option>
            <a-select-option value="PARALLEL">并行</a-select-option>
            <a-select-option value="KEYED">键控</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="是否启用">
          <a-switch v-model:checked="formData.enabled" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 任务详情弹窗 -->
    <a-modal
      v-model:open="detailVisible"
      title="任务详情"
      :width="800"
      :footer="null"
    >
      <a-descriptions :column="2" bordered v-if="currentRecord">
        <a-descriptions-item label="ID">{{ currentRecord.id }}</a-descriptions-item>
        <a-descriptions-item label="编码">{{ currentRecord.code || '-' }}</a-descriptions-item>
        <a-descriptions-item label="名称">{{ currentRecord.name }}</a-descriptions-item>
        <a-descriptions-item label="任务类型">{{ taskTypeName(currentRecord.typeId) }}</a-descriptions-item>
        <a-descriptions-item label="描述" :span="2">{{ currentRecord.description || '-' }}</a-descriptions-item>
        <a-descriptions-item label="超时(秒)">{{ currentRecord.timeoutSec || '-' }}</a-descriptions-item>
        <a-descriptions-item label="最大重试">{{ currentRecord.maxRetry || 0 }}</a-descriptions-item>
        <a-descriptions-item label="并发策略">{{ currentRecord.concurrencyPolicy || 'PARALLEL' }}</a-descriptions-item>
        <a-descriptions-item label="状态">
          <a-tag :color="currentRecord.enabled ? 'green' : 'red'">
            {{ currentRecord.enabled ? '启用' : '禁用' }}
          </a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="参数" :span="2">
          <pre style="max-height: 200px; overflow: auto;">{{ formatJson(currentRecord.params) }}</pre>
        </a-descriptions-item>
        <a-descriptions-item label="重试策略" :span="2">
          <pre style="max-height: 200px; overflow: auto;">{{ formatJson(currentRecord.retryPolicy) }}</pre>
        </a-descriptions-item>
      </a-descriptions>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { message } from 'ant-design-vue'
import type { ColumnsType } from 'ant-design-vue/es/table'
import type { Key } from 'ant-design-vue/es/_util/type'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import Ajv from 'ajv'
import { taskList, createTask, updateTask, deleteTask, getTask, taskTypeList } from '@/api/scheduling'
import { throttle } from '@/utils/debounce'
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
const detailVisible = ref(false)
const formTitle = ref('新建任务')
const selectedRowKeys = ref<number[]>([])
const dataSource = ref<any[]>([])
const taskTypes = ref<any[]>([])
const currentRecord = ref<any>(null)
const formRef = ref<any>(null)

const query = reactive({
  code: '',
  name: '',
  typeId: undefined as number | undefined,
})

const formData = reactive({
  id: undefined as number | undefined,
  typeId: undefined as number | undefined,
  code: '',
  name: '',
  description: '',
  params: '',
  timeoutSec: undefined as number | undefined,
  maxRetry: 0,
  retryPolicy: '',
  concurrencyPolicy: 'PARALLEL',
  enabled: true,
})

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showTotal: (total: number) => `共 ${total} 条`,
})

const columns: ColumnsType<any> = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '编码', dataIndex: 'code', key: 'code', width: 150 },
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '任务类型', dataIndex: 'typeId', key: 'typeId', width: 140 },
  { title: '超时(秒)', dataIndex: 'timeoutSec', key: 'timeoutSec', width: 100 },
  { title: '最大重试', dataIndex: 'maxRetry', key: 'maxRetry', width: 100 },
  { title: '并发策略', key: 'concurrencyPolicy', width: 120 },
  { title: '状态', key: 'enabled', width: 100 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 180 },
  { title: '操作', key: 'action', width: 200, fixed: 'right' as const },
]

const formatJson = (str: string | null | undefined) => {
  if (!str) return '-'
  try {
    return JSON.stringify(JSON.parse(str), null, 2)
  } catch {
    return str
  }
}

/** 根据 typeId 从已加载的 taskTypes 中取名称，用于列表/详情展示 */
const taskTypeName = (typeId: number | null | undefined): string => {
  if (typeId == null) return '-'
  const t = taskTypes.value.find((x: { id: number }) => x.id === typeId)
  return t?.name ?? String(typeId)
}

/** 参数按所选任务类型的 paramSchema 做 JSON Schema 校验 */
const validateParamsBySchema = (_rule: unknown, value: string) => {
  const v = value == null ? '' : String(value).trim()
  if (v === '') return Promise.resolve()
  let parsed: unknown
  try {
    parsed = JSON.parse(v)
  } catch {
    return Promise.reject(new Error('请输入合法的 JSON'))
  }
  const typeId = formData.typeId
  const type = taskTypes.value.find((t: { id: number }) => t.id === typeId) as { paramSchema?: string; param_schema?: string } | undefined
  const schemaRaw = type?.paramSchema ?? type?.param_schema
  if (schemaRaw == null || String(schemaRaw).trim() === '') return Promise.resolve()
  let schema: object
  try {
    schema = typeof schemaRaw === 'object' ? schemaRaw : JSON.parse(schemaRaw)
  } catch {
    return Promise.resolve()
  }
  const ajv = new Ajv()
  const valid = ajv.validate(schema, parsed)
  if (!valid) return Promise.reject(new Error(ajv.errorsText() ?? '参数不符合任务类型的 Schema'))
  return Promise.resolve()
}

const paramsRules = [{ validator: validateParamsBySchema }]

const loadTaskTypes = async () => {
  try {
    const res = await taskTypeList({ current: 1, pageSize: 1000 })
    taskTypes.value = res.records
  } catch (error: any) {
    console.error('加载任务类型失败:', error)
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
    const res = await taskList(params)
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
  query.typeId = undefined
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

const handleTypeChange = () => {
  // 可以根据任务类型自动填充默认值
}

const handleCreate = () => {
  if (!canManageSchedulingConfig.value) {
    message.warning('当前账户没有调度配置管理权限')
    return
  }
  formTitle.value = '新建任务'
  Object.assign(formData, {
    id: undefined,
    typeId: undefined,
    code: '',
    name: '',
    description: '',
    params: '',
    timeoutSec: undefined,
    maxRetry: 0,
    retryPolicy: '',
    concurrencyPolicy: 'PARALLEL',
    enabled: true,
  })
  formVisible.value = true
}

const handleEdit = (record: any) => {
  if (!canManageSchedulingConfig.value) {
    message.warning('当前账户没有调度配置管理权限')
    return
  }
  formTitle.value = '编辑任务'
  Object.assign(formData, {
    id: record.id,
    typeId: record.typeId,
    code: record.code || '',
    name: record.name || '',
    description: record.description || '',
    params: record.params || '',
    timeoutSec: record.timeoutSec,
    maxRetry: record.maxRetry || 0,
    retryPolicy: record.retryPolicy || '',
    concurrencyPolicy: record.concurrencyPolicy || 'PARALLEL',
    enabled: record.enabled !== undefined ? record.enabled : true,
  })
  formVisible.value = true
}

const handleView = async (record: any) => {
  try {
    const res = await getTask(record.id)
    currentRecord.value = res
    detailVisible.value = true
  } catch (error: any) {
    message.error(error.message || '获取详情失败')
  }
}

const buildTaskPayload = () => ({
  typeId: formData.typeId,
  code: String(formData.code ?? '').trim(),
  name: String(formData.name ?? '').trim(),
  description: formData.description ?? '',
  params: formData.params ?? '',
  timeoutSec: formData.timeoutSec,
  maxRetry: formData.maxRetry,
  retryPolicy: formData.retryPolicy ?? '',
  concurrencyPolicy: formData.concurrencyPolicy ?? 'PARALLEL',
  enabled: Boolean(formData.enabled),
})

const handleSubmit = async () => {
  if (!canManageSchedulingConfig.value) {
    message.error('当前账户没有调度配置管理权限')
    return
  }
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  if (!formData.typeId) {
    message.error('请选择任务类型')
    return
  }
  try {
    const payload = buildTaskPayload()
    if (formData.id) {
      await updateTask(formData.id, payload)
      message.success('更新成功')
    } else {
      await createTask(payload)
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
    await deleteTask(id)
    message.success('删除成功')
    loadData()
  } catch (error: any) {
    message.error(error.message || '删除失败')
  }
}

onMounted(async () => {
  await loadTaskTypes()
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
