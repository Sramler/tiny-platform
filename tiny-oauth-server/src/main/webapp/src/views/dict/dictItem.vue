<template>
  <div class="dict-item-container">
    <!-- 阶段7: 查询表单 -->
    <div class="form-container">
      <a-form layout="inline" :model="query">
        <a-form-item label="字典类型">
          <a-select v-model:value="query.dictTypeId" placeholder="请选择字典类型" style="width: 200px;" allow-clear>
            <a-select-option v-for="type in dictTypeOptions" :key="type.id" :value="type.id">
              {{ type.dictName }} ({{ type.dictCode }})
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="字典值">
          <a-input v-model:value="query.value" placeholder="请输入字典值" allow-clear />
        </a-form-item>
        <a-form-item label="字典标签">
          <a-input v-model:value="query.label" placeholder="请输入字典标签" allow-clear />
        </a-form-item>
        <a-form-item label="状态">
          <a-select v-model:value="query.enabled" allow-clear placeholder="全部状态" style="width: 160px">
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

    <div class="table-container" ref="tableContentRef">
      <!-- 阶段8: 工具栏 -->
      <div class="toolbar-container">
        <div class="table-title">
          字典项管理
        </div>
        <div class="table-actions">
          <a-button type="link" @click="openCreateDrawer" class="toolbar-btn" :disabled="!query.dictTypeId">
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

      <div class="table-scroll-container">
        <!-- 阶段8: 表格 -->
        <a-table :columns="columns" :data-source="tableData" :pagination="false"
          :row-key="(record: any) => String(record.id)" bordered :loading="loading"
          :scroll="{ x: tableScrollX, y: tableBodyHeight }" :locale="tableLocale" :show-sorter-tooltip="showSortTooltip"
          :row-class-name="getRowClassName" :size="tableSize === 'default' ? undefined : tableSize">
          <template #bodyCell="{ column, record }">
            <!-- 阶段9: 表格自定义渲染 -->
            <template v-if="column && column.dataIndex === 'enabled'">
              <a-tag :color="record.enabled ? 'green' : 'red'">
                {{ record.enabled ? '启用' : '禁用' }}
              </a-tag>
            </template>
            <template v-else-if="column && column.dataIndex === 'isBuiltin'">
              <a-tag :color="record.isBuiltin ? 'blue' : 'default'">
                {{ record.isBuiltin ? '是' : '否' }}
              </a-tag>
            </template>
            <template v-else-if="column && (column.dataIndex === 'description')">
              <a-tooltip v-if="record[column.dataIndex]">
                <template #title>{{ record[column.dataIndex] }}</template>
                <span
                  style="display: block; max-width: 200px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">
                  {{ record[column.dataIndex] }}
                </span>
              </a-tooltip>
              <span v-else>-</span>
            </template>
            <template v-else-if="column && column.dataIndex === 'action'">
              <div class="action-buttons">
                <a-button type="link" size="small" @click.stop="openEditDrawer(record)" class="action-btn">
                  <template #icon>
                    <EditOutlined />
                  </template>
                  编辑
                </a-button>
                <a-tooltip v-if="record.isBuiltin" title="内置字典项不允许删除">
                  <a-button type="link" size="small" danger disabled class="action-btn">
                    <template #icon>
                      <DeleteOutlined />
                    </template>
                    删除
                  </a-button>
                </a-tooltip>
                <a-button v-else type="link" size="small" danger @click.stop="handleDelete(record)" class="action-btn">
                  <template #icon>
                    <DeleteOutlined />
                  </template>
                  删除
                </a-button>
                <a-button type="link" size="small" @click.stop="throttledView(record)" class="action-btn">
                  <template #icon>
                    <EyeOutlined />
                  </template>
                  查看
                </a-button>
              </div>
            </template>
            <template v-else>
              <span v-if="column && column.dataIndex">{{ record[column.dataIndex as string] }}</span>
              <span v-else>-</span>
            </template>
          </template>
        </a-table>
      </div>
      <div class="pagination-container" ref="paginationRef">
        <div class="pagination-left">
          <!-- 导出功能可选 -->
        </div>
        <a-pagination v-model:current="pagination.current" :page-size="pagination.pageSize"
          :total="paginationConfig.total" :show-size-changer="pagination.showSizeChanger"
          :page-size-options="paginationConfig.pageSizeOptions" :show-total="pagination.showTotal"
          @change="handlePageChange" @showSizeChange="handlePageSizeChange" :locale="{ items_per_page: '条/页' }" />
      </div>
    </div>

    <!-- 阶段9: Drawer 表单 -->
    <a-drawer v-model:open="drawerVisible"
      :title="drawerMode === 'create' ? '新建字典项' : drawerMode === 'edit' ? '编辑字典项' : '查看字典项'" width="50%"
      :get-container="false" :style="{ position: 'absolute' }" @close="handleDrawerClose">
      <a-form :model="formState" layout="vertical">
        <a-form-item label="字典类型" name="dictTypeId">
          <a-select v-model:value="formState.dictTypeId" :disabled="drawerMode === 'view'" placeholder="请选择字典类型"
            style="width: 100%">
            <a-select-option v-for="type in dictTypeOptions" :key="type.id" :value="type.id">
              {{ type.dictName }} ({{ type.dictCode }})
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="字典值" name="value">
          <a-input v-model:value="formState.value" :disabled="drawerMode === 'view'" placeholder="请输入字典值" />
        </a-form-item>
        <a-form-item label="字典标签" name="label">
          <a-input v-model:value="formState.label" :disabled="drawerMode === 'view'" placeholder="请输入字典标签" />
        </a-form-item>
        <a-form-item label="描述" name="description">
          <a-textarea v-model:value="formState.description" :rows="3" :disabled="drawerMode === 'view'"
            placeholder="请输入描述信息" />
        </a-form-item>
        <a-form-item label="租户ID" name="tenantId">
          <a-input-number v-model:value="formState.tenantId" :min="0" style="width: 100%"
            :disabled="drawerMode === 'view'" placeholder="0表示平台字典项" />
        </a-form-item>
        <a-form-item label="排序" name="sortOrder">
          <a-input-number v-model:value="formState.sortOrder" :min="0" style="width: 100%"
            :disabled="drawerMode === 'view'" placeholder="排序值，数字越小越靠前" />
        </a-form-item>
        <a-form-item label="是否内置" name="isBuiltin">
          <a-switch v-model:checked="formState.isBuiltin" :disabled="drawerMode === 'view' || drawerMode === 'edit'" />
          <span style="margin-left: 8px; color: #999; font-size: 12px;">平台内置字典项，创建后不可修改</span>
        </a-form-item>
        <a-form-item label="状态" name="enabled">
          <a-switch v-model:checked="formState.enabled" :disabled="drawerMode === 'view'" />
        </a-form-item>
      </a-form>
      <template #footer>
        <div style="text-align: right;">
          <a-button style="margin-right: 8px" @click="handleDrawerClose">{{ drawerMode === 'view' ? '关闭' : '取消'
          }}</a-button>
          <a-button v-if="drawerMode !== 'view'" type="primary" @click="handleSubmit">保存</a-button>
        </div>
      </template>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { PlusOutlined, ReloadOutlined, EditOutlined, DeleteOutlined, EyeOutlined } from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import { useThrottle } from '@/utils/debounce'
import type { TableColumnsType } from 'ant-design-vue'
import {
  getDictTypesByTenant,
  getDictItemList,
  createDictItem,
  updateDictItem,
  deleteDictItem,
  type DictTypeItem,
  type DictItemQuery,
  type DictItemCreateUpdateDto,
} from '@/api/dict'

// 阶段6: 基础响应式数据
const tableContentRef = ref<HTMLElement | null>(null)
const paginationRef = ref<HTMLElement | null>(null)

// 阶段7: 查询表单数据
const query = ref({
  dictTypeId: undefined as number | undefined,
  value: '',
  label: '',
  enabled: undefined as boolean | undefined,
})

// 字典类型选项
const dictTypeOptions = ref<DictTypeItem[]>([])

// 加载字典类型选项
async function loadDictTypeOptions() {
  try {
    const result = await getDictTypesByTenant(0)
    dictTypeOptions.value = result
  } catch (error) {
    console.error('加载字典类型选项失败:', error)
  }
}

// 阶段9: 数据加载和CRUD操作
async function loadData() {
  loading.value = true
  try {
    // 确保分页参数有效：page >= 0, size 在 1-100 范围内
    const currentPage = Math.max(1, pagination.value.current || 1)
    const pageSize = Math.max(1, Math.min(100, pagination.value.pageSize || 10))

    const params: DictItemQuery = {
      dictTypeId: query.value.dictTypeId,
      value: query.value.value?.trim() || undefined,
      label: query.value.label?.trim() || undefined,
      enabled: query.value.enabled,
      page: currentPage - 1, // 转换为 0-based
      size: pageSize,
    }
    const res = await getDictItemList(params)
    tableData.value = res.content || []
    pagination.value.total = res.totalElements || 0
  } catch (error: any) {
    console.error('加载字典项数据失败:', error)
    message.error('加载字典项数据失败: ' + (error?.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.value.current = 1
  loadData()
}

function handleReset() {
  query.value.dictTypeId = undefined
  query.value.value = ''
  query.value.label = ''
  query.value.enabled = undefined
  pagination.value.current = 1
  loadData()
}

const throttledSearch = useThrottle(handleSearch, 800)
const throttledReset = useThrottle(handleReset, 800)

// 阶段8: 工具栏相关状态
const refreshing = ref(false)
const showSortTooltip = ref(true)
const zebraStripeEnabled = ref(true)
const tableSize = ref<'default' | 'small' | 'middle' | 'large'>('middle')

// 阶段8: 表格数据
const tableData = ref<any[]>([])
const loading = ref(false)
const tableBodyHeight = ref(400)

// 阶段8: 分页配置
const pagination = ref({
  current: 1,
  pageSize: 10,
  showSizeChanger: true,
  pageSizeOptions: ['10', '20', '30', '40', '50'],
  total: 0,
  showTotal: (total: number) => `共 ${total} 条`,
})

const paginationConfig = computed(() => {
  const current = Number(pagination.value.current) || 1
  const pageSize = Number(pagination.value.pageSize) || 10
  return {
    ...pagination.value,
    current,
    pageSize,
    total: Number(pagination.value.total) || 0,
  }
})

// 阶段8: 列定义
const INITIAL_COLUMNS: TableColumnsType = [
  { title: 'ID', dataIndex: 'id', width: 80, sorter: true },
  { title: '字典编码', dataIndex: 'dictCode', width: 150 },
  { title: '字典值', dataIndex: 'value', width: 150 },
  { title: '字典标签', dataIndex: 'label', width: 150 },
  { title: '描述', dataIndex: 'description', width: 200 },
  { title: '租户ID', dataIndex: 'tenantId', width: 100 },
  { title: '是否内置', dataIndex: 'isBuiltin', width: 100 },
  { title: '排序', dataIndex: 'sortOrder', width: 80, sorter: true },
  { title: '状态', dataIndex: 'enabled', width: 100 },
  { title: '创建时间', dataIndex: 'createdAt', width: 180, sorter: true },
  { title: '更新时间', dataIndex: 'updatedAt', width: 180, sorter: true },
  { title: '操作', dataIndex: 'action', width: 200, fixed: 'right' },
]

// 默认显示的列
const DEFAULT_VISIBLE_COLUMNS = [
  'id', 'dictCode', 'value', 'label', 'description', 'isBuiltin', 'sortOrder', 'enabled', 'createdAt', 'action'
]

const allColumns = ref([...INITIAL_COLUMNS])
const showColumnKeys = ref(DEFAULT_VISIBLE_COLUMNS.filter((key): key is string => typeof key === 'string'))

// 阶段8: 计算列（过滤显示的列）
const columns = computed(() => {
  if (!allColumns.value || !Array.isArray(allColumns.value) || !showColumnKeys.value || !Array.isArray(showColumnKeys.value)) {
    return []
  }
  const filtered = allColumns.value.filter(
    (col) => col && col.dataIndex && typeof col.dataIndex === 'string' && showColumnKeys.value.includes(col.dataIndex),
  )
  return filtered.filter((col) => col && col.dataIndex)
})

// 阶段8: 计算表格横向滚动宽度
const tableScrollX = computed(() => {
  if (!columns.value || !Array.isArray(columns.value) || columns.value.length === 0) {
    return 1500
  }
  const totalWidth = columns.value.reduce((sum, col) => {
    if (!col) return sum
    return sum + (col.width || 100)
  }, 0)
  return totalWidth + 100
})

// 阶段8: 表格语言配置
const tableLocale = computed(() => {
  if (showSortTooltip.value) {
    return {
      triggerDesc: '点击降序',
      triggerAsc: '点击升序',
      cancelSort: '取消排序',
    }
  }
  return undefined
})

// 阶段8: 分页处理
function handlePageChange(page: number) {
  pagination.value.current = page || 1
  loadData()
}

function handlePageSizeChange(_current: number, size: number) {
  pagination.value.pageSize = size || 10
  pagination.value.current = 1
  loadData()
}

// 阶段8: 行类名（用于斑马纹）
function getRowClassName(_record: any, index: number) {
  if (!zebraStripeEnabled.value) {
    return ''
  }
  return index % 2 === 0 ? 'table-row-even' : 'table-row-odd'
}

// 阶段8: 更新表格高度
function updateTableBodyHeight() {
  nextTick(() => {
    if (!tableContentRef.value || !paginationRef.value) {
      return
    }
    try {
      const tableHeader = tableContentRef.value.querySelector('.ant-table-header') as HTMLElement
      const containerHeight = tableContentRef.value.clientHeight
      const paginationHeight = paginationRef.value.clientHeight
      const tableHeaderHeight = tableHeader ? tableHeader.clientHeight : 55
      const bodyHeight = containerHeight - paginationHeight - tableHeaderHeight
      tableBodyHeight.value = Math.max(bodyHeight, 200)
    } catch (error) {
      console.warn('updateTableBodyHeight error:', error)
    }
  })
}

// 阶段9: Drawer 相关状态
const drawerVisible = ref(false)
const drawerMode = ref<'create' | 'edit' | 'view'>('create')

interface DictItemFormState {
  id?: number
  dictTypeId: number
  value: string
  label: string
  description?: string
  tenantId?: number
  isBuiltin?: boolean
  enabled?: boolean
  sortOrder?: number
}

const formState = ref<DictItemFormState>({
  dictTypeId: 0,
  value: '',
  label: '',
  description: '',
  tenantId: 0,
  enabled: true,
  sortOrder: 0,
})

// 阶段9: Drawer 操作
function openCreateDrawer() {
  if (!query.value.dictTypeId) {
    message.warning('请先选择字典类型')
    return
  }
  drawerMode.value = 'create'
  formState.value = {
    dictTypeId: query.value.dictTypeId,
    value: '',
    label: '',
    description: '',
    tenantId: 0,
    isBuiltin: false,
    enabled: true,
    sortOrder: 0,
  }
  drawerVisible.value = true
}

function openEditDrawer(record: any) {
  drawerMode.value = 'edit'
  formState.value = {
    id: record.id,
    dictTypeId: record.dictTypeId || query.value.dictTypeId || 0,
    value: record.value || '',
    label: record.label || '',
    description: record.description || '',
    tenantId: record.tenantId ?? 0,
    isBuiltin: record.isBuiltin ?? false,
    enabled: record.enabled ?? true,
    sortOrder: record.sortOrder ?? 0,
  }
  drawerVisible.value = true
}

function handleView(record: any) {
  drawerMode.value = 'view'
  formState.value = {
    id: record.id,
    dictTypeId: record.dictTypeId || query.value.dictTypeId || 0,
    value: record.value || '',
    label: record.label || '',
    description: record.description || '',
    tenantId: record.tenantId ?? 0,
    isBuiltin: record.isBuiltin ?? false,
    enabled: record.enabled ?? true,
    sortOrder: record.sortOrder ?? 0,
  }
  drawerVisible.value = true
}

const throttledView = useThrottle(handleView, 500)

function handleDrawerClose() {
  drawerVisible.value = false
}

async function handleSubmit() {
  try {
    const payload: DictItemCreateUpdateDto = {
      dictTypeId: formState.value.dictTypeId,
      value: formState.value.value,
      label: formState.value.label,
      description: formState.value.description,
      tenantId: formState.value.tenantId ?? 0,
      enabled: formState.value.enabled ?? true,
      sortOrder: formState.value.sortOrder ?? 0,
    }
    if (drawerMode.value === 'create') {
      await createDictItem(payload)
      message.success('创建字典项成功')
    } else if (drawerMode.value === 'edit' && formState.value.id != null) {
      await updateDictItem(formState.value.id, payload)
      message.success('更新字典项成功')
    }
    drawerVisible.value = false
    loadData()
  } catch (error: any) {
    message.error('保存字典项失败: ' + (error?.message || '未知错误'))
  }
}

function handleDelete(record: any) {
  if (record.isBuiltin) {
    message.warning('内置字典项不允许删除')
    return
  }
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除字典项 "${record.label}" 吗？`,
    okText: '确认',
    cancelText: '取消',
    onOk: () => {
      return deleteDictItem(record.id)
        .then(() => {
          message.success('删除成功')
          loadData()
        })
        .catch((error: any) => {
          message.error('删除失败: ' + (error?.message || '未知错误'))
          return Promise.reject(error)
        })
    },
  })
}

// 监听字典类型变化，自动加载数据
watch(
  () => query.value.dictTypeId,
  (newVal) => {
    if (newVal) {
      pagination.value.current = 1
      loadData()
    }
  },
)

async function handleRefresh() {
  refreshing.value = true
  loading.value = true
  await loadData().catch(() => { })
  setTimeout(() => {
    refreshing.value = false
  }, 800)
  loading.value = false
}

const throttledRefresh = useThrottle(handleRefresh, 1000)

onMounted(() => {
  loadDictTypeOptions()
  updateTableBodyHeight()
  window.addEventListener('resize', updateTableBodyHeight)
  // 初始化时加载数据（如果没有选择字典类型，则加载所有字典项）
  loadData()
})

// 暴露方法给父组件
defineExpose({
  loadData,
  openCreateDrawer,
  setDictTypeId: (dictTypeId: number) => {
    query.value.dictTypeId = dictTypeId
    loadData()
  },
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateTableBodyHeight)
})

watch(
  () => pagination.value.pageSize,
  () => {
    updateTableBodyHeight()
  },
)
</script>

<style scoped>
/* 阶段6: 基础样式 */
.dict-item-container {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.form-container {
  padding: 24px;
  border-bottom: 1px solid #f0f0f0;
  background: transparent;
}

.table-container {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  width: 100%;
  overflow: hidden;
}

.toolbar-container {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #f0f0f0;
  padding: 8px 24px;
  flex-shrink: 0;
  background-color: #fff;
}

.table-title {
  font-size: 16px;
  font-weight: bold;
  color: #222;
}

.table-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.table-scroll-container {
  min-height: 0;
  overflow: auto;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.table-scroll-container::-webkit-scrollbar {
  display: none;
}

.pagination-container {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  padding: 12px 24px;
  min-height: 56px;
}

.ml-2 {
  margin-left: 8px;
}

.toolbar-btn {
  border-radius: 4px;
  height: 32px;
  padding: 0 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.action-icon {
  font-size: 18px;
  cursor: pointer;
  color: #595959;
  border-radius: 4px;
  padding: 8px;
  transition: color 0.2s, background 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 32px;
  min-height: 32px;
}

.action-icon:hover {
  color: #1890ff;
  background: #f5f5f5;
}

.pagination-left {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 100%;
}

/* 表格样式 */
:deep(.ant-table-cell) {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

:deep(.ant-table-body) {
  scrollbar-width: none;
  -ms-overflow-style: none;
}

:deep(.ant-table-body::-webkit-scrollbar) {
  display: none;
}

:deep(.ant-table-content) {
  scrollbar-width: none;
  -ms-overflow-style: none;
}

:deep(.ant-table-content::-webkit-scrollbar) {
  display: none;
}

/* 斑马纹和行悬停效果 */
:deep(.ant-table-tbody > tr) {
  transition: background-color 0.2s ease, box-shadow 0.2s ease;
  cursor: default;
}

:deep(.ant-table-tbody > tr.table-row-even) {
  background-color: #fafbfc;
}

:deep(.ant-table-tbody > tr.table-row-odd) {
  background-color: #fff;
}

:deep(.ant-table-tbody > tr:hover) {
  background-color: #f0f7ff !important;
  box-shadow: 0 1px 4px rgba(24, 144, 255, 0.1);
  transform: translateY(-1px);
}

:deep(.ant-table-thead > tr > th) {
  background-color: #fafafa;
  font-weight: 600;
  border-bottom: 2px solid #e8e8e8;
}

:deep(.ant-table) {
  border-radius: 8px;
  overflow: hidden;
}

:deep(.ant-table-tbody > tr > td) {
  border-bottom: 1px solid #f0f0f0;
}

/* 分页样式 */
:deep(.ant-pagination) {
  display: flex !important;
  flex-direction: row !important;
  align-items: center !important;
}

:deep(.ant-pagination),
:deep(.ant-pagination-item),
:deep(.ant-pagination-item-link) {
  height: 32px !important;
  line-height: 32px !important;
  min-width: 32px;
  box-sizing: border-box;
  vertical-align: middle;
}

:deep(.ant-pagination-item-container) {
  margin-right: 8px;
}

:deep(.ant-pagination-item-ellipsis) {
  line-height: 32px !important;
  vertical-align: middle !important;
  display: inline-block !important;
  font-size: 16px !important;
}

:deep(.ant-pagination) {
  min-height: 32px !important;
  height: 32px !important;
  line-height: 32px !important;
}

:deep(.ant-pagination-item),
:deep(.ant-pagination-item-link),
:deep(.ant-pagination-prev),
:deep(.ant-pagination-next),
:deep(.ant-pagination-jump-next),
:deep(.ant-pagination-jump-prev) {
  height: 32px !important;
  min-width: 32px !important;
  line-height: 32px !important;
  box-sizing: border-box;
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
  padding: 0 !important;
}

.action-buttons {
  display: flex;
  align-items: center;
  gap: 4px;
  justify-content: center;
}

.action-btn {
  padding: 2px 4px;
  height: auto;
  line-height: 1.2;
  font-size: 12px;
}
</style>
