<template>
  <div class="content-container" style="position: relative;">
    <div class="content-card">
      <div class="form-container">
        <a-form layout="inline" :model="query">
          <a-form-item label="租户编码">
            <a-input v-model:value="query.code" placeholder="请输入租户编码" />
          </a-form-item>
          <a-form-item label="租户名称">
            <a-input v-model:value="query.name" placeholder="请输入租户名称" />
          </a-form-item>
          <a-form-item label="域名">
            <a-input v-model:value="query.domain" placeholder="如: tenant.example.com" />
          </a-form-item>
          <a-form-item label="是否启用">
            <a-select v-model:value="query.enabled" allow-clear placeholder="全部">
              <a-select-option :value="true">启用</a-select-option>
              <a-select-option :value="false">禁用</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item>
            <a-button type="primary" @click="throttledSearch">搜索</a-button>
            <a-button class="ml-2" @click="throttledReset">重置</a-button>
          </a-form-item>
        </a-form>
      </div>

      <div class="toolbar-container">
        <div class="table-title">租户列表</div>
        <div class="table-actions">
          <div v-if="selectedRowKeys.length > 0" class="batch-actions">
            <a-button type="primary" danger @click="throttledBatchDelete" class="toolbar-btn">
              <template #icon>
                <DeleteOutlined />
              </template>
              批量删除 ({{ selectedRowKeys.length }})
            </a-button>
            <a-button @click="clearSelection" class="toolbar-btn">取消选择</a-button>
          </div>
          <a-button type="link" @click="throttledCreate" class="toolbar-btn">
            <template #icon>
              <PlusOutlined />
            </template>
            新建
          </a-button>
          <a-tooltip title="刷新">
            <span class="action-icon" @click="throttledRefresh">
              <ReloadOutlined :spin="refreshing" />
            </span>
          </a-tooltip>
        </div>
      </div>

      <div class="table-container" ref="tableContentRef">
        <div class="table-scroll-container" ref="tableScrollContainerRef">
          <a-table
            :columns="columns"
            :data-source="tableData"
            :pagination="false"
            :row-key="(record: any) => String(record.id)"
            bordered
            :loading="loading"
            :row-selection="rowSelection"
            :custom-row="onCustomRow"
            :row-class-name="getRowClassName"
            :scroll="{ x: 'max-content', y: tableBodyHeight }"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.dataIndex === 'enabled'">
                <a-tag :color="record.enabled ? 'green' : 'red'">
                  {{ record.enabled ? '启用' : '禁用' }}
                </a-tag>
              </template>
              <template v-else-if="column.dataIndex === 'expiresAt'">
                {{ formatDateTime(record.expiresAt) }}
              </template>
              <template v-else-if="column.dataIndex === 'createdAt'">
                {{ formatDateTime(record.createdAt) }}
              </template>
              <template v-else-if="column.dataIndex === 'updatedAt'">
                {{ formatDateTime(record.updatedAt) }}
              </template>
              <template v-else-if="column.dataIndex === 'action'">
                <div class="action-buttons">
                  <a-button type="link" size="small" @click.stop="throttledEdit(record)" class="action-btn">
                    <template #icon>
                      <EditOutlined />
                    </template>
                    编辑
                  </a-button>
                  <a-button type="link" size="small" danger @click.stop="throttledDelete(record)" class="action-btn">
                    <template #icon>
                      <DeleteOutlined />
                    </template>
                    删除
                  </a-button>
                </div>
              </template>
            </template>
          </a-table>
        </div>
        <div class="pagination-container" ref="paginationRef">
          <a-pagination
            v-model:current="pagination.current"
            :page-size="pagination.pageSize"
            :total="pagination.total"
            :show-size-changer="pagination.showSizeChanger"
            :page-size-options="pagination.pageSizeOptions"
            :show-total="pagination.showTotal"
            @change="handlePageChange"
            @showSizeChange="handlePageSizeChange"
            :locale="{ items_per_page: '条/页' }"
          />
        </div>
      </div>
    </div>

    <a-drawer
      v-model:open="drawerVisible"
      :title="drawerMode === 'create' ? '新建租户' : '编辑租户'"
      width="520"
      :get-container="false"
      :style="{ position: 'absolute' }"
      @close="handleDrawerClose"
    >
      <TenantForm
        :mode="drawerMode"
        :tenantData="currentTenant"
        @submit="handleFormSubmit"
        @cancel="handleDrawerClose"
      />
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick, computed, watch } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { PlusOutlined, ReloadOutlined, DeleteOutlined, EditOutlined } from '@ant-design/icons-vue'
import { tenantList, createTenant, updateTenant, deleteTenant } from '@/api/tenant'
import TenantForm from './TenantForm.vue'

const query = ref({
  code: '',
  name: '',
  domain: '',
  enabled: undefined as boolean | undefined
})

const loading = ref(false)
const tableData = ref<any[]>([])

const pagination = ref({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  pageSizeOptions: ['10', '20', '50', '100'],
  showTotal: (total: number) => `共 ${total} 条`
})

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '租户编码', dataIndex: 'code', key: 'code', width: 140 },
  { title: '租户名称', dataIndex: 'name', key: 'name', width: 180 },
  { title: '域名', dataIndex: 'domain', key: 'domain', width: 220 },
  { title: '套餐', dataIndex: 'planCode', key: 'planCode', width: 120 },
  { title: '到期时间', dataIndex: 'expiresAt', key: 'expiresAt', width: 180 },
  { title: '最大用户数', dataIndex: 'maxUsers', key: 'maxUsers', width: 120 },
  { title: '存储配额(GB)', dataIndex: 'maxStorageGb', key: 'maxStorageGb', width: 140 },
  { title: '启用', dataIndex: 'enabled', key: 'enabled', width: 100 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 180 },
  { title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt', width: 180 },
  { title: '操作', dataIndex: 'action', key: 'action', width: 160, fixed: 'right' }
]

const selectedRowKeys = ref<string[]>([])
const rowSelection = computed(() => ({
  selectedRowKeys: selectedRowKeys.value,
  onChange: (keys: string[]) => {
    selectedRowKeys.value = keys
  }
}))

const tableContentRef = ref<HTMLElement | null>(null)
const tableScrollContainerRef = ref<HTMLElement | null>(null)
const paginationRef = ref<HTMLElement | null>(null)
const tableBodyHeight = ref(400)

function updateTableBodyHeight() {
  nextTick(() => {
    if (tableContentRef.value && paginationRef.value) {
      const tableHeader = tableContentRef.value.querySelector('.ant-table-header') as HTMLElement
      const containerHeight = tableContentRef.value.clientHeight
      const paginationHeight = paginationRef.value.clientHeight
      const tableHeaderHeight = tableHeader ? tableHeader.clientHeight : 55
      const bodyHeight = containerHeight - paginationHeight - tableHeaderHeight
      tableBodyHeight.value = Math.max(bodyHeight, 200)
    }
  })
}

async function loadData() {
  loading.value = true
  try {
    const params = {
      code: query.value.code.trim(),
      name: query.value.name.trim(),
      domain: query.value.domain.trim(),
      enabled: query.value.enabled,
      page: (Number(pagination.value.current) || 1) - 1,
      size: Number(pagination.value.pageSize) || 10
    }
    const res = await tenantList(params)
    tableData.value = Array.isArray(res.content) ? res.content : []
    pagination.value.total = Number(res.totalElements) || 0
  } catch {
    tableData.value = []
    pagination.value.total = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.value.current = 1
  loadData()
}
const throttledSearch = handleSearch

function handleReset() {
  query.value.code = ''
  query.value.name = ''
  query.value.domain = ''
  query.value.enabled = undefined
  pagination.value.current = 1
  loadData()
}
const throttledReset = handleReset

const refreshing = ref(false)
async function handleRefresh() {
  refreshing.value = true
  await loadData().finally(() => {
    setTimeout(() => {
      refreshing.value = false
    }, 800)
  })
}
const throttledRefresh = handleRefresh

function clearSelection() {
  selectedRowKeys.value = []
}

function handlePageChange(page: number) {
  pagination.value.current = page || 1
  loadData()
}

function handlePageSizeChange(current: number, size: number) {
  pagination.value.pageSize = size || 10
  pagination.value.current = 1
  loadData()
}

function handleBatchDelete() {
  if (selectedRowKeys.value.length === 0) {
    message.warning('请先选择要删除的租户')
    return
  }
  Modal.confirm({
    title: '确认批量删除',
    content: `确定要删除选中的 ${selectedRowKeys.value.length} 个租户吗？`,
    okText: '确认',
    cancelText: '取消',
    onOk: () => {
      return Promise.all(selectedRowKeys.value.map(id => deleteTenant(id)))
        .then(() => {
          message.success('批量删除成功')
          selectedRowKeys.value = []
          loadData()
        })
        .catch((error: any) => {
          message.error('批量删除失败: ' + (error?.message || '未知错误'))
          return Promise.reject(error)
        })
    }
  })
}
const throttledBatchDelete = handleBatchDelete

function handleDelete(record: any) {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除租户 ${record.name} 吗？`,
    okText: '确认',
    cancelText: '取消',
    onOk: () => {
      return deleteTenant(record.id)
        .then(() => {
          message.success('删除成功')
          loadData()
        })
        .catch((error: any) => {
          message.error('删除失败: ' + (error?.message || '未知错误'))
          return Promise.reject(error)
        })
    }
  })
}
const throttledDelete = handleDelete

const drawerVisible = ref(false)
const drawerMode = ref<'create' | 'edit'>('create')
const currentTenant = ref<any | null>(null)

function handleCreate() {
  drawerMode.value = 'create'
  currentTenant.value = { id: '', enabled: true }
  drawerVisible.value = true
}
const throttledCreate = handleCreate

function handleEdit(record: any) {
  drawerMode.value = 'edit'
  currentTenant.value = { ...record }
  drawerVisible.value = true
}
const throttledEdit = handleEdit

function handleDrawerClose() {
  drawerVisible.value = false
  currentTenant.value = null
}

async function handleFormSubmit(formData: any) {
  try {
    if (drawerMode.value === 'edit' && formData.id) {
      await updateTenant(formData.id, formData)
      message.success('更新成功')
    } else {
      await createTenant(formData)
      message.success('创建成功')
    }
    handleDrawerClose()
    loadData()
  } catch (error: any) {
    message.error('保存失败: ' + (error?.message || '未知错误'))
  }
}

function formatDateTime(dateTime: string | null | undefined): string {
  if (!dateTime) return '-'
  try {
    const date = new Date(dateTime)
    return date.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    })
  } catch {
    return '-'
  }
}

function onCustomRow(record: any) {
  return {
    onClick: (event: MouseEvent) => {
      if ((event.target as HTMLElement).closest('.ant-checkbox-wrapper')) return
      const recordId = String(record.id)
      const isSelected = selectedRowKeys.value.includes(recordId)
      if (isSelected && selectedRowKeys.value.length === 1) {
        selectedRowKeys.value = []
      } else {
        selectedRowKeys.value = [recordId]
      }
    }
  }
}

function getRowClassName(record: any) {
  if (selectedRowKeys.value.includes(String(record.id))) {
    return 'checkbox-selected-row'
  }
  return ''
}

onMounted(() => {
  loadData()
  updateTableBodyHeight()
  window.addEventListener('resize', updateTableBodyHeight)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateTableBodyHeight)
})

watch(() => pagination.value.pageSize, () => {
  updateTableBodyHeight()
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
}

.form-container {
  padding: 24px;
  border-bottom: 1px solid #f0f0f0;
  background: transparent;
  border-radius: 0;
  box-shadow: none;
}

.toolbar-container {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #f0f0f0;
  background: transparent;
  border-radius: 0;
  box-shadow: none;
  padding: 8px 24px 8px 24px;
}

.table-title {
  font-size: 16px;
  font-weight: 600;
}

.table-actions {
  display: flex;
  align-items: center;
  gap: 8px;
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
}

.pagination-container {
  padding: 12px 24px;
  border-top: 1px solid #f0f0f0;
  display: flex;
  justify-content: flex-end;
}

.toolbar-btn {
  margin-right: 8px;
}

.action-icon {
  cursor: pointer;
  font-size: 16px;
  margin-left: 8px;
}

.batch-actions {
  display: flex;
  align-items: center;
}

.action-buttons {
  display: flex;
  align-items: center;
  gap: 6px;
}

.checkbox-selected-row td {
  background-color: #e6f7ff !important;
}
</style>
