<template>
  <div class="content-container" style="position: relative;">
    <div class="content-card">
      <div class="form-container">
        <a-form layout="inline" :model="query">
          <a-form-item label="对象类型">
            <a-select v-model:value="query.objectType" placeholder="请选择对象类型" style="width: 150px" allow-clear>
              <a-select-option value="dag">DAG</a-select-option>
              <a-select-option value="task">任务</a-select-option>
              <a-select-option value="task_instance">任务实例</a-select-option>
              <a-select-option value="task_history">任务历史</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="操作类型">
            <a-select v-model:value="query.action" placeholder="请选择操作类型" style="width: 150px" allow-clear>
              <a-select-option value="CREATE">创建</a-select-option>
              <a-select-option value="UPDATE">更新</a-select-option>
              <a-select-option value="DELETE">删除</a-select-option>
              <a-select-option value="TRIGGER">触发</a-select-option>
              <a-select-option value="RETRY">重试</a-select-option>
              <a-select-option value="CANCEL">取消</a-select-option>
              <a-select-option value="ACTIVATE">激活</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item>
            <a-button type="primary" @click="handleSearch">搜索</a-button>
            <a-button class="ml-2" @click="handleReset">重置</a-button>
          </a-form-item>
        </a-form>
      </div>

      <div class="toolbar-container">
        <div class="table-title">操作审计记录</div>
        <div class="table-actions">
          <a-tooltip title="刷新">
            <span class="action-icon" @click="handleRefresh">
              <ReloadOutlined :spin="refreshing" />
            </span>
          </a-tooltip>
        </div>
      </div>

      <div class="table-container">
        <div class="table-scroll-container">
          <a-table
            class="bottom-pagination-table"
            :columns="columns"
            :data-source="dataSource"
            :loading="loading"
            :pagination="false"
            :scroll="{ x: 'max-content' }"
            row-key="id"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'action'">
                <a-tag :color="getActionColor(record.action)">
                  {{ record.action }}
                </a-tag>
              </template>
              <template v-if="column.key === 'detail'">
                <a-button type="link" size="small" @click="handleViewDetail(record)"
                  >查看详情</a-button
                >
              </template>
            </template>
          </a-table>
        </div>
        <div class="pagination-container">
          <a-pagination
            v-model:current="pagination.current"
            :page-size="pagination.pageSize"
            :total="pagination.total"
            :show-size-changer="pagination.showSizeChanger"
            :page-size-options="pagination.pageSizeOptions"
            :show-total="pagination.showTotal"
            :locale="{ items_per_page: '条/页' }"
            @change="handlePageChange"
            @showSizeChange="handlePageSizeChange"
          />
        </div>
      </div>
    </div>

    <!-- 详情弹窗 -->
    <a-modal
      v-model:open="detailVisible"
      title="操作详情"
      :width="800"
      :footer="null"
    >
      <a-descriptions :column="1" bordered v-if="currentRecord">
        <a-descriptions-item label="ID">{{ currentRecord.id }}</a-descriptions-item>
        <a-descriptions-item label="记录所属租户ID">{{ currentRecord.recordTenantId || '-' }}</a-descriptions-item>
        <a-descriptions-item label="对象类型">{{ currentRecord.objectType }}</a-descriptions-item>
        <a-descriptions-item label="对象ID">{{ currentRecord.objectId || '-' }}</a-descriptions-item>
        <a-descriptions-item label="操作类型">
          <a-tag :color="getActionColor(currentRecord.action)">
            {{ currentRecord.action }}
          </a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="执行人">{{ currentRecord.performedBy || '-' }}</a-descriptions-item>
        <a-descriptions-item label="操作时间">{{ currentRecord.createdAt }}</a-descriptions-item>
        <a-descriptions-item label="详情">
          <pre style="max-height: 400px; overflow: auto;">{{ formatJson(currentRecord.detail) }}</pre>
        </a-descriptions-item>
      </a-descriptions>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { message } from 'ant-design-vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import type { ColumnsType } from 'ant-design-vue/es/table'
import { auditList } from '@/api/scheduling'
import { throttle } from '@/utils/debounce'
import { useAuth } from '@/auth/auth'
import { extractAuthoritiesFromJwt } from '@/utils/jwt'
import { SCHEDULING_AUDIT_VIEW, SCHEDULING_WILDCARD } from '@/constants/permission'

const { user } = useAuth()
const schedulingAuthorities = computed(() =>
  extractAuthoritiesFromJwt(user.value?.access_token).filter((a) => a.startsWith('scheduling:')),
)
const canViewSchedulingAudit = computed(() =>
  schedulingAuthorities.value.includes(SCHEDULING_AUDIT_VIEW) ||
  schedulingAuthorities.value.includes(SCHEDULING_WILDCARD),
)

const loading = ref(false)
const refreshing = ref(false)
const detailVisible = ref(false)
const currentRecord = ref<any>(null)
const dataSource = ref<any[]>([])

const query = reactive({
  objectType: '',
  action: '',
})

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  pageSizeOptions: ['10', '20', '50', '100'],
  showTotal: (total: number) => `共 ${total} 条`,
})

const columns: ColumnsType<any> = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '对象类型', dataIndex: 'objectType', key: 'objectType', width: 120 },
  { title: '对象ID', dataIndex: 'objectId', key: 'objectId', width: 150 },
  { title: '操作类型', key: 'action', width: 120 },
  { title: '执行人', dataIndex: 'performedBy', key: 'performedBy', width: 120 },
  { title: '操作时间', dataIndex: 'createdAt', key: 'createdAt', width: 180 },
  { title: '详情', key: 'detail', width: 100, fixed: 'right' as const },
]

const getActionColor = (action: string) => {
  const map: Record<string, string> = {
    CREATE: 'green',
    UPDATE: 'blue',
    DELETE: 'red',
    TRIGGER: 'cyan',
    RETRY: 'orange',
    CANCEL: 'default',
    ACTIVATE: 'purple',
  }
  return map[action] || 'default'
}

const formatJson = (str: string | null | undefined) => {
  if (!str) return '-'
  try {
    return JSON.stringify(JSON.parse(str), null, 2)
  } catch {
    return str
  }
}

const loadData = async () => {
  loading.value = true
  try {
    if (!canViewSchedulingAudit.value) {
      dataSource.value = []
      pagination.total = 0
      return
    }
    const params = {
      current: pagination.current,
      pageSize: pagination.pageSize,
      ...query,
    }
    const res = await auditList(params)
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
  query.objectType = ''
  query.action = ''
  pagination.current = 1
  loadData()
}, 500)

const handleRefresh = throttle(() => {
  refreshing.value = true
  loadData().finally(() => {
    refreshing.value = false
  })
}, 500)

const handlePageChange = (page: number) => {
  pagination.current = page || 1
  loadData()
}

const handlePageSizeChange = (_current: number, size: number) => {
  pagination.pageSize = size || 10
  pagination.current = 1
  loadData()
}

const handleViewDetail = (record: any) => {
  currentRecord.value = record
  detailVisible.value = true
}

onMounted(() => {
  loadData()
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
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: #fff;
}

.form-container {
  padding: 24px;
  border-bottom: 1px solid #f0f0f0;
}

.toolbar-container {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 24px;
  border-bottom: 1px solid #f0f0f0;
}

.table-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.table-scroll-container {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding-bottom: 12px;
}

:deep(.bottom-pagination-table) {
  min-width: 0;
  width: 100%;
}

:deep(.bottom-pagination-table .ant-table-thead > tr > th),
:deep(.bottom-pagination-table .ant-table-tbody > tr > td) {
  white-space: nowrap;
}

.pagination-container {
  position: sticky;
  bottom: 0;
  z-index: 2;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  min-height: 56px;
  padding: 12px 24px;
  border-top: 1px solid #f0f0f0;
  background: #fff;
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
