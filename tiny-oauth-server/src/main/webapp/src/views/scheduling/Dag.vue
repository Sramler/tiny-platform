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
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="handleEdit(record)">编辑</a-button>
              <a-button type="link" size="small" @click="handleDetail(record)">详情</a-button>
              <a-button type="link" size="small" @click="handleHistory(record)">历史</a-button>
              <a-button type="link" size="small" @click="handleTrigger(record)" :disabled="!record.enabled">触发</a-button>
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
import { ref, reactive, onMounted } from 'vue'
import CronDesigner from '@/components/scheduling/CronDesigner.vue'
import { message } from 'ant-design-vue'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import { useRouter } from 'vue-router'
import { dagList, createDag, updateDag, deleteDag, triggerDag } from '@/api/scheduling'
import { throttle } from '@/utils/debounce'

const router = useRouter()
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
  tenantId: undefined as number | undefined,
  code: '',
  name: '',
  description: '',
  cronExpression: '' as string,
  cronTimezone: undefined as string | undefined,
  cronEnabled: true,
  enabled: true,
  createdBy: '',
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

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '编码', dataIndex: 'code', key: 'code', width: 150 },
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '描述', dataIndex: 'description', key: 'description' },
  { title: '状态', key: 'enabled', width: 100 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 180 },
  { title: '操作', key: 'action', width: 350, fixed: 'right' },
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

const onSelectChange = (keys: number[]) => {
  selectedRowKeys.value = keys
}

const filterTimezoneOption = (input: string, option: any) => {
  return option.children[0].children.toLowerCase().includes(input.toLowerCase())
}

const handleCreate = () => {
  formTitle.value = '新建 DAG'
  Object.assign(formData, {
    id: undefined,
    tenantId: undefined,
    code: '',
    name: '',
    description: '',
    cronExpression: '',
    cronTimezone: undefined,
    cronEnabled: true,
    enabled: true,
    createdBy: '',
  })
  formVisible.value = true
}

const handleEdit = (record: any) => {
  formTitle.value = '编辑 DAG'
  Object.assign(formData, {
    id: record.id,
    tenantId: record.tenantId,
    code: record.code || '',
    name: record.name,
    description: record.description || '',
    cronExpression: record.cronExpression ?? '',
    cronTimezone: record.cronTimezone || undefined,
    cronEnabled: record.cronEnabled !== undefined ? record.cronEnabled : true,
    enabled: record.enabled !== undefined ? record.enabled : true,
    createdBy: record.createdBy || '',
  })
  formVisible.value = true
}

const handleDetail = (record: any) => {
  router.push({
    path: '/scheduling/dag/detail',
    query: { id: record.id },
  })
}

const handleHistory = (record: any) => {
  router.push({
    path: '/scheduling/dag/history',
    query: { dagId: record.id },
  })
}

const handleTrigger = async (record: any) => {
  try {
    await triggerDag(record.id)
    message.success('触发成功')
    loadData()
  } catch (error: any) {
    message.error(error.message || '触发失败')
  }
}

const handleSubmit = async () => {
  if (!formData.name) {
    message.error('请输入名称')
    return
  }
  try {
    if (formData.id) {
      await updateDag(formData.id, formData)
      message.success('更新成功')
    } else {
      await createDag(formData)
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
    await deleteDag(id)
    message.success('删除成功')
    loadData()
  } catch (error: any) {
    message.error(error.message || '删除失败')
  }
}

onMounted(() => {
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


