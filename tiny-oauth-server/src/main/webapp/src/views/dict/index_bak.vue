<template>
  <div class="content-container" style="position: relative;">
    <div class="content-card">
      <!-- 阶段2: 查询表单 -->
      <div class="form-container">
        <a-form layout="inline" :model="query">
          <a-form-item label="字典编码">
            <a-input v-model:value="query.dictCode" placeholder="请输入字典编码" allow-clear />
          </a-form-item>
          <a-form-item label="字典名称">
            <a-input v-model:value="query.dictName" placeholder="请输入字典名称" allow-clear />
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
        <!-- 阶段3: 工具栏 -->
        <div class="toolbar-container">
          <div class="table-title">
            字典管理
          </div>
          <div class="table-actions">
            <a-button type="link" @click="openCreateDrawer" class="toolbar-btn">
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
            <a-tooltip :title="showSortTooltip ? '关闭排序提示' : '开启排序提示'">
              <PoweroffOutlined :class="['action-icon', { active: showSortTooltip }]"
                @click="showSortTooltip = !showSortTooltip" />
            </a-tooltip>
            <a-tooltip :title="zebraStripeEnabled ? '关闭斑马纹' : '开启斑马纹'">
              <div class="zebra-stripe-switch">
                <a-switch v-model:checked="zebraStripeEnabled" size="small" />
              </div>
            </a-tooltip>
            <a-tooltip :title="cellCopyEnabled ? '关闭单元格复制' : '开启单元格复制'">
              <CopyOutlined :class="['action-icon', { active: cellCopyEnabled }]"
                @click="cellCopyEnabled = !cellCopyEnabled" />
            </a-tooltip>
            <a-dropdown placement="bottomRight" trigger="click">
              <a-tooltip title="表格密度">
                <ColumnHeightOutlined class="action-icon" />
              </a-tooltip>
              <template #overlay>
                <a-menu @click="handleDensityMenuClick" :selected-keys="[tableSize]">
                  <a-menu-item key="default">
                    <span>标准</span>
                  </a-menu-item>
                  <a-menu-item key="middle">
                    <span>中等</span>
                  </a-menu-item>
                  <a-menu-item key="small">
                    <span>紧凑</span>
                  </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
            <a-popover placement="bottomRight" trigger="click" :destroyTooltipOnHide="false">
              <template #content>
                <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px;">
                  <div style="display: flex; align-items: center;">
                    <a-checkbox
                      :checked="showColumnKeys && showColumnKeys.length > 0 && allColumns && allColumns.length > 0 && showColumnKeys.length === allColumns.length"
                      :indeterminate="showColumnKeys && showColumnKeys.length > 0 && allColumns && allColumns.length > 0 && showColumnKeys.length < allColumns.length"
                      @change="onCheckAllChange" />
                    <span style="font-weight: bold; margin-left: 8px;">列展示/排序</span>
                  </div>
                  <span style="font-weight: bold; color: #1677ff; cursor: pointer;" @click="resetColumnOrder">
                    重置
                  </span>
                </div>
                <VueDraggable v-if="draggableColumns && Array.isArray(draggableColumns) && draggableColumns.length > 0"
                  v-model="draggableColumns" :item-key="(item: any) => item?.dataIndex || `col_${Math.random()}`"
                  handle=".drag-handle" @end="onDragEnd" class="draggable-columns" ghost-class="sortable-ghost"
                  chosen-class="sortable-chosen" tag="div">
                  <template #item="{ element: col }">
                    <div v-if="col && col.dataIndex" class="draggable-column-item">
                      <HolderOutlined class="drag-handle" />
                      <a-checkbox
                        :checked="showColumnKeys && Array.isArray(showColumnKeys) && showColumnKeys.includes(col.dataIndex)"
                        @change="(e: any) => {
                          if (e && e.target && col && col.dataIndex) {
                            onCheckboxChange(col.dataIndex, e.target.checked)
                          }
                        }">
                        {{ col.title }}
                      </a-checkbox>
                    </div>
                  </template>
                </VueDraggable>
                <div v-else style="padding: 16px; text-align: center; color: #999;">
                  暂无列数据
                </div>
              </template>
              <a-tooltip title="列设置">
                <SettingOutlined class="action-icon" />
              </a-tooltip>
            </a-popover>
          </div>
        </div>

        <div class="table-scroll-container">
          <!-- 阶段4: 表格基础结构 -->
          <a-table :columns="columns" :data-source="tableData" :pagination="false"
            :row-key="(record: any) => String(record.id)" bordered :loading="loading"
            :scroll="{ x: tableScrollX, y: tableBodyHeight }" :locale="tableLocale"
            :show-sorter-tooltip="showSortTooltip" :row-class-name="getRowClassName"
            :size="tableSize === 'default' ? undefined : tableSize">
            <template #bodyCell="{ column, record }">
              <!-- 阶段5: 表格自定义渲染 -->
              <template v-if="column && column.dataIndex === 'enabled'">
                <a-tag :color="record.enabled ? 'green' : 'red'">
                  {{ record.enabled ? '启用' : '禁用' }}
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
                  <a-button type="link" size="small" danger @click.stop="handleDelete(record)" class="action-btn">
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
                <template v-if="cellCopyEnabled && column.dataIndex && column.dataIndex !== 'action'">
                  <span class="cell-text">
                    {{ record[column.dataIndex as string] ?? '-' }}
                  </span>
                  <CopyOutlined class="cell-copy-icon"
                    @click.stop="handleCellCopy(record[column.dataIndex as string], (column.title as string) || '')" />
                </template>
                <span v-else-if="column && column.dataIndex">{{ record[column.dataIndex as string] }}</span>
                <span v-else>-</span>
              </template>
            </template>
          </a-table>
        </div>
        <div class="pagination-container" ref="paginationRef">
          <div class="pagination-left">
            <!-- 阶段10: 导出功能 -->
            <div class="export-group">
              <a-button type="primary" :loading="exporting" @click="handleExportSync" class="export-btn">
                <template #icon>
                  <DownloadOutlined />
                </template>
                导出当前页
              </a-button>
            </div>
          </div>
          <a-pagination v-model:current="pagination.current" :page-size="pagination.pageSize"
            :total="paginationConfig.total" :show-size-changer="pagination.showSizeChanger"
            :page-size-options="paginationConfig.pageSizeOptions" :show-total="pagination.showTotal"
            @change="handlePageChange" @showSizeChange="handlePageSizeChange" :locale="{ items_per_page: '条/页' }" />
        </div>
      </div>
    </div>

    <!-- 阶段6: Drawer 表单 -->
    <a-drawer v-model:open="drawerVisible"
      :title="drawerMode === 'create' ? '新建字典类型' : drawerMode === 'edit' ? '编辑字典类型' : '查看字典类型'" width="50%"
      :get-container="false" :style="{ position: 'absolute' }" @close="handleDrawerClose">
      <a-form :model="formState" layout="vertical">
        <a-form-item label="字典编码" name="dictCode">
          <a-input v-model:value="formState.dictCode" :disabled="drawerMode === 'view'" placeholder="请输入字典编码" />
        </a-form-item>
        <a-form-item label="字典名称" name="dictName">
          <a-input v-model:value="formState.dictName" :disabled="drawerMode === 'view'" placeholder="请输入字典名称" />
        </a-form-item>
        <a-form-item label="描述" name="description">
          <a-textarea v-model:value="formState.description" :rows="3" :disabled="drawerMode === 'view'"
            placeholder="请输入描述信息" />
        </a-form-item>
        <a-form-item label="租户ID" name="tenantId">
          <a-input-number v-model:value="formState.tenantId" :min="0" style="width: 100%"
            :disabled="drawerMode === 'view'" placeholder="0表示平台字典" />
        </a-form-item>
        <a-form-item label="分类ID" name="categoryId">
          <a-input-number v-model:value="formState.categoryId" :min="0" style="width: 100%"
            :disabled="drawerMode === 'view'" placeholder="分类ID，用于字典分组" />
        </a-form-item>
        <a-form-item label="排序" name="sortOrder">
          <a-input-number v-model:value="formState.sortOrder" :min="0" style="width: 100%"
            :disabled="drawerMode === 'view'" placeholder="排序值，数字越小越靠前" />
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
import { PlusOutlined, ReloadOutlined, PoweroffOutlined, SettingOutlined, HolderOutlined, ColumnHeightOutlined, CopyOutlined, EditOutlined, DeleteOutlined, EyeOutlined, DownloadOutlined } from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import VueDraggable from 'vuedraggable'
import { useThrottle } from '@/utils/debounce'
import type { TableColumnsType } from 'ant-design-vue'
import {
  getDictTypeList,
  createDictType,
  updateDictType,
  deleteDictType,
  type DictTypeItem,
  type DictTypeQuery,
  type DictTypeCreateUpdateDto,
} from '@/api/dict'
import request from '@/utils/request'
import dayjs from 'dayjs'

// 阶段1: 基础响应式数据
const tableContentRef = ref<HTMLElement | null>(null)
const paginationRef = ref<HTMLElement | null>(null)

// 阶段2: 查询表单数据
const query = ref({
  dictCode: '',
  dictName: '',
  enabled: undefined as boolean | undefined,
})

// 阶段7: 数据加载
async function loadData() {
  loading.value = true
  try {
    const params: DictTypeQuery = {
      dictCode: query.value.dictCode.trim() || undefined,
      dictName: query.value.dictName.trim() || undefined,
      enabled: query.value.enabled,
      page: pagination.value.current - 1,
      size: pagination.value.pageSize,
    }
    const res = await getDictTypeList(params)
    tableData.value = res.content || []
    pagination.value.total = res.totalElements || 0
  } catch (error: any) {
    message.error('加载字典数据失败: ' + (error?.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.value.current = 1
  loadData()
}

function handleReset() {
  query.value.dictCode = ''
  query.value.dictName = ''
  query.value.enabled = undefined
  pagination.value.current = 1
  loadData()
}

const throttledSearch = useThrottle(handleSearch, 800)
const throttledReset = useThrottle(handleReset, 800)

// 阶段3: 工具栏相关状态
const refreshing = ref(false)
const showSortTooltip = ref(true)
const zebraStripeEnabled = ref(true)
const cellCopyEnabled = ref(false)
const tableSize = ref<'default' | 'small' | 'middle' | 'large'>('middle')
const exporting = ref(false)

// 阶段4: 表格数据
const tableData = ref<any[]>([])
const loading = ref(false)
const tableBodyHeight = ref(400)

// 阶段4: 分页配置
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

// 阶段4: 列定义
const INITIAL_COLUMNS: TableColumnsType = [
  { title: 'ID', dataIndex: 'id', width: 80, sorter: true },
  { title: '字典编码', dataIndex: 'dictCode', width: 150 },
  { title: '字典名称', dataIndex: 'dictName', width: 150 },
  { title: '描述', dataIndex: 'description', width: 200 },
  { title: '租户ID', dataIndex: 'tenantId', width: 100 },
  { title: '分类ID', dataIndex: 'categoryId', width: 100 },
  { title: '排序', dataIndex: 'sortOrder', width: 80, sorter: true },
  { title: '状态', dataIndex: 'enabled', width: 100 },
  { title: '创建时间', dataIndex: 'createdAt', width: 180, sorter: true },
  { title: '更新时间', dataIndex: 'updatedAt', width: 180, sorter: true },
  { title: '操作', dataIndex: 'action', width: 200, fixed: 'right' },
]

// 默认显示的列
const DEFAULT_VISIBLE_COLUMNS = [
  'id', 'dictCode', 'dictName', 'description', 'sortOrder', 'enabled', 'createdAt', 'action'
]

// 列管理相关（将在阶段8完善）
const allColumns = ref([...INITIAL_COLUMNS])
const draggableColumns = ref([...INITIAL_COLUMNS])
const showColumnKeys = ref(DEFAULT_VISIBLE_COLUMNS.filter((key): key is string => typeof key === 'string'))
const isSyncingColumns = ref(false)

// 阶段4: 计算列（过滤显示的列）
const columns = computed(() => {
  if (!allColumns.value || !Array.isArray(allColumns.value) || !showColumnKeys.value || !Array.isArray(showColumnKeys.value)) {
    return []
  }
  const filtered = allColumns.value.filter(
    (col) => col && col.dataIndex && typeof col.dataIndex === 'string' && showColumnKeys.value.includes(col.dataIndex),
  )
  return filtered.filter((col) => col && col.dataIndex)
})

// 阶段4: 计算表格横向滚动宽度
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

// 阶段4: 表格语言配置
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

// 阶段4: 分页处理
function handlePageChange(page: number) {
  pagination.value.current = page || 1
  loadData()
}

function handlePageSizeChange(_current: number, size: number) {
  pagination.value.pageSize = size || 10
  pagination.value.current = 1
  loadData()
}

// 阶段4: 行类名（用于斑马纹）
function getRowClassName(_record: any, index: number) {
  if (!zebraStripeEnabled.value) {
    return ''
  }
  return index % 2 === 0 ? 'table-row-even' : 'table-row-odd'
}

// 阶段4: 更新表格高度
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

// 阶段6: Drawer 相关状态
const drawerVisible = ref(false)
const drawerMode = ref<'create' | 'edit' | 'view'>('create')

interface DictTypeFormState {
  id?: number
  dictCode: string
  dictName: string
  description?: string
  tenantId?: number
  categoryId?: number
  enabled?: boolean
  sortOrder?: number
}

const formState = ref<DictTypeFormState>({
  dictCode: '',
  dictName: '',
  description: '',
  tenantId: 0,
  categoryId: undefined,
  enabled: true,
  sortOrder: 0,
})

// 阶段6: Drawer 操作
function openCreateDrawer() {
  drawerMode.value = 'create'
  formState.value = {
    dictCode: '',
    dictName: '',
    description: '',
    tenantId: 0,
    categoryId: undefined,
    enabled: true,
    sortOrder: 0,
  }
  drawerVisible.value = true
}

function openEditDrawer(record: any) {
  drawerMode.value = 'edit'
  formState.value = {
    id: record.id,
    dictCode: record.dictCode || '',
    dictName: record.dictName || '',
    description: record.description || '',
    tenantId: record.tenantId ?? 0,
    categoryId: record.categoryId,
    enabled: record.enabled ?? true,
    sortOrder: record.sortOrder ?? 0,
  }
  drawerVisible.value = true
}

function handleView(record: any) {
  drawerMode.value = 'view'
  formState.value = {
    id: record.id,
    dictCode: record.dictCode || '',
    dictName: record.dictName || '',
    description: record.description || '',
    tenantId: record.tenantId ?? 0,
    categoryId: record.categoryId,
    enabled: record.enabled ?? true,
    sortOrder: record.sortOrder ?? 0,
  }
  drawerVisible.value = true
}

function handleDrawerClose() {
  drawerVisible.value = false
}

// 阶段7: CRUD 操作
async function handleSubmit() {
  try {
    const payload: DictTypeCreateUpdateDto = {
      dictCode: formState.value.dictCode,
      dictName: formState.value.dictName,
      description: formState.value.description,
      tenantId: formState.value.tenantId ?? 0,
      categoryId: formState.value.categoryId,
      enabled: formState.value.enabled ?? true,
      sortOrder: formState.value.sortOrder ?? 0,
    }
    if (drawerMode.value === 'create') {
      await createDictType(payload)
      message.success('创建字典类型成功')
    } else if (drawerMode.value === 'edit' && formState.value.id != null) {
      await updateDictType(formState.value.id, payload)
      message.success('更新字典类型成功')
    }
    drawerVisible.value = false
    loadData()
  } catch (error: any) {
    message.error('保存字典类型失败: ' + (error?.message || '未知错误'))
  }
}

function handleDelete(record: any) {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除字典类型 "${record.dictName}" 吗？删除后关联的字典项也会被删除。`,
    okText: '确认',
    cancelText: '取消',
    onOk: () => {
      return deleteDictType(record.id)
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

const throttledView = useThrottle(handleView, 500)

// 阶段5: 单元格复制功能
function handleCellCopy(value: any, columnTitle: string) {
  if (!cellCopyEnabled.value) {
    return
  }

  try {
    const textToCopy = value !== null && value !== undefined ? String(value) : ''

    if (!textToCopy.trim()) {
      message.warning('单元格内容为空，无法复制')
      return
    }

    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(textToCopy)
        .then(() => {
          const title = columnTitle || '单元格'
          message.success(`已复制 ${title}：${textToCopy}`)
        })
        .catch((error) => {
          console.error('复制失败:', error)
          fallbackCopyTextToClipboard(textToCopy, columnTitle)
        })
    } else {
      fallbackCopyTextToClipboard(textToCopy, columnTitle)
    }
  } catch (error) {
    console.error('复制处理错误:', error)
    message.error('复制失败：' + (error instanceof Error ? error.message : '未知错误'))
  }
}

function fallbackCopyTextToClipboard(text: string, columnTitle: string) {
  try {
    const textArea = document.createElement('textarea')
    textArea.value = text
    textArea.style.position = 'fixed'
    textArea.style.left = '-999999px'
    textArea.style.top = '-999999px'
    document.body.appendChild(textArea)
    textArea.focus()
    textArea.select()

    const successful = document.execCommand('copy')
    document.body.removeChild(textArea)

    if (successful) {
      const title = columnTitle || '单元格'
      message.success(`已复制 ${title}：${text}`)
    } else {
      message.error('复制失败，请手动复制')
    }
  } catch (err) {
    console.error('降级复制方案失败:', err)
    message.error('复制失败，请手动复制')
  }
}

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

function handleDensityMenuClick({ key }: { key: string }) {
  if (key === 'default' || key === 'small' || key === 'middle' || key === 'large') {
    tableSize.value = key as 'default' | 'small' | 'middle' | 'large'
  }
}

// 阶段8: 列管理功能
function onCheckAllChange(e: any) {
  if (!allColumns.value || !Array.isArray(allColumns.value)) {
    return
  }
  isSyncingColumns.value = true
  try {
    if (e.target.checked) {
      showColumnKeys.value = allColumns.value
        .filter((col) => col && col.dataIndex && typeof col.dataIndex === 'string')
        .map((col) => col.dataIndex as string)
    } else {
      showColumnKeys.value = []
    }
  } finally {
    nextTick(() => {
      isSyncingColumns.value = false
    })
  }
}

function onCheckboxChange(dataIndex: string, checked: boolean) {
  if (!dataIndex || !showColumnKeys.value || !Array.isArray(showColumnKeys.value)) {
    return
  }
  if (!allColumns.value || !Array.isArray(allColumns.value)) {
    return
  }

  const columnExists = allColumns.value.some((col) => col?.dataIndex === dataIndex)
  if (!columnExists) {
    console.warn(`列 ${dataIndex} 不存在于 allColumns 中`)
    return
  }

  nextTick(() => {
    if (checked) {
      if (!showColumnKeys.value.includes(dataIndex)) {
        showColumnKeys.value.push(dataIndex)
      }
    } else {
      showColumnKeys.value = showColumnKeys.value.filter((key) => key !== dataIndex)
    }
  })
}

function resetColumnOrder() {
  try {
    isSyncingColumns.value = true
    try {
      allColumns.value = [...INITIAL_COLUMNS]
      draggableColumns.value = [...INITIAL_COLUMNS]
      showColumnKeys.value = [...DEFAULT_VISIBLE_COLUMNS.filter((key): key is string => typeof key === 'string')]
    } finally {
      nextTick(() => {
        isSyncingColumns.value = false
      })
    }
  } catch (error) {
    console.error('重置列顺序失败:', error)
    message.error('重置列顺序失败')
    isSyncingColumns.value = false
  }
}

function onDragEnd(event: any) {
  isSyncingColumns.value = true
  try {
    const newAllColumns = draggableColumns.value.filter((col) => col && typeof col.dataIndex === 'string')

    const currentKeys = allColumns.value.map((col) => col?.dataIndex).join(',')
    const newKeys = newAllColumns.map((col) => col?.dataIndex).join(',')

    if (currentKeys !== newKeys) {
      allColumns.value = newAllColumns
      showColumnKeys.value = showColumnKeys.value.filter((key) =>
        allColumns.value.some((col) => col.dataIndex === key),
      )
    }
  } finally {
    nextTick(() => {
      isSyncingColumns.value = false
    })
  }
}

// 监听 allColumns 变化，同步到 draggableColumns
watch(
  allColumns,
  (val) => {
    if (isSyncingColumns.value) return
    isSyncingColumns.value = true
    try {
      const newDraggableColumns = val.filter((col) => col && typeof col.dataIndex === 'string')
      const currentKeys = draggableColumns.value.map((col) => col?.dataIndex).join(',')
      const newKeys = newDraggableColumns.map((col) => col?.dataIndex).join(',')
      if (currentKeys !== newKeys) {
        draggableColumns.value = newDraggableColumns
      }
    } finally {
      nextTick(() => {
        isSyncingColumns.value = false
      })
    }
  },
  { deep: true },
)

// 阶段10: 导出功能
const getExportColumns = () => {
  return allColumns.value
    .filter(col => col.dataIndex !== 'action' && showColumnKeys.value.includes(col.dataIndex))
    .map(col => ({
      title: col.title,
      field: col.dataIndex as string,
    }))
}

const getExportFilters = () => {
  const filters: Record<string, any> = {}
  if (query.value.dictCode?.trim()) {
    filters.dictCode = query.value.dictCode.trim()
  }
  if (query.value.dictName?.trim()) {
    filters.dictName = query.value.dictName.trim()
  }
  if (query.value.enabled !== undefined) {
    filters.enabled = query.value.enabled
  }
  return filters
}

async function handleExportSync() {
  exporting.value = true
  try {
    const baseFilters = getExportFilters()
    const pageFilters: Record<string, any> = {
      ...baseFilters,
      __mode: 'page',
      __page: pagination.value.current,
      __pageSize: pagination.value.pageSize,
    }

    const exportRequest = {
      fileName: 'dict_types',
      pageSize: pagination.value.pageSize,
      async: false,
      sheets: [
        {
          sheetName: '字典类型',
          exportType: 'dict_types',
          filters: pageFilters,
          columns: getExportColumns(),
        },
      ],
    }

    const blob = await request.post<Blob>('/export/sync', exportRequest, {
      responseType: 'blob' as any,
    })

    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `dict_types_${dayjs().format('YYYYMMDD_HHmmss')}.xlsx`
    a.click()
    window.URL.revokeObjectURL(url)
    message.success('导出成功，文件已开始下载')
  } catch (error: any) {
    message.error('导出失败: ' + (error?.message || '未知错误'))
  } finally {
    exporting.value = false
  }
}

onMounted(() => {
  updateTableBodyHeight()
  window.addEventListener('resize', updateTableBodyHeight)
  loadData()

  // 阶段9: 监听表格滚动，动态更新复制按钮显示状态
  if (tableContentRef.value) {
    const tableBody = tableContentRef.value.querySelector('.ant-table-body') as HTMLElement
    if (tableBody) {
      const handleScroll = () => {
        if (scrollCheckTimer) {
          clearTimeout(scrollCheckTimer)
        }
        scrollCheckTimer = window.setTimeout(() => {
          updateCopyIconVisibility()
        }, 50)
      }

      tableBody.addEventListener('scroll', handleScroll, { passive: true })

      setTimeout(() => {
        updateCopyIconVisibility()
      }, 200)

      resizeHandler = () => {
        setTimeout(() => {
          updateCopyIconVisibility()
        }, 100)
      }
      window.addEventListener('resize', resizeHandler)

      mutationObserver = new MutationObserver(() => {
        if (cellCopyEnabled.value) {
          setTimeout(() => {
            updateCopyIconVisibility()
          }, 100)
        }
      })

      if (tableBody) {
        mutationObserver.observe(tableBody, {
          childList: true,
          subtree: true,
        })
      }
    }
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateTableBodyHeight)
  if (resizeHandler) {
    window.removeEventListener('resize', resizeHandler)
    resizeHandler = null
  }
  if (scrollCheckTimer) {
    clearTimeout(scrollCheckTimer)
    scrollCheckTimer = null
  }
  if (mutationObserver) {
    mutationObserver.disconnect()
    mutationObserver = null
  }
})

watch(
  () => pagination.value.pageSize,
  () => {
    updateTableBodyHeight()
  },
)

// 阶段9: 复制功能显示控制
let scrollCheckTimer: number | null = null
let resizeHandler: (() => void) | null = null
let mutationObserver: MutationObserver | null = null

function updateCopyIconVisibility() {
  if (!cellCopyEnabled.value || !tableContentRef.value) {
    return
  }

  nextTick(() => {
    const cells = tableContentRef.value?.querySelectorAll('.ant-table-tbody > tr > td:not(.ant-table-cell-fix-right)') || []
    const fixedRightColumn = tableContentRef.value?.querySelector('.ant-table-fixed-right') as HTMLElement

    if (!fixedRightColumn) {
      cells.forEach((cell) => {
        const copyIcon = cell.querySelector('.cell-copy-icon') as HTMLElement
        if (copyIcon) {
          copyIcon.classList.remove('cell-copy-icon-hidden')
        }
      })
      return
    }

    const fixedColumnRect = fixedRightColumn.getBoundingClientRect()
    const fixedColumnLeft = fixedColumnRect.left

    const visibleCells = Array.from(cells).filter(cell => {
      const rect = cell.getBoundingClientRect()
      return rect.width > 0 && rect.height > 0
    })

    visibleCells.forEach((cell) => {
      const cellRect = cell.getBoundingClientRect()
      const cellRight = cellRect.right
      const copyIcon = cell.querySelector('.cell-copy-icon') as HTMLElement

      if (copyIcon) {
        if (cellRight >= fixedColumnLeft - 30) {
          copyIcon.classList.add('cell-copy-icon-hidden')
        } else {
          copyIcon.classList.remove('cell-copy-icon-hidden')
        }
      }
    })
  })
}

// 监听复制功能开关
watch(
  () => cellCopyEnabled.value,
  () => {
    if (cellCopyEnabled.value) {
      setTimeout(() => {
        updateCopyIconVisibility()
      }, 100)
    }
  },
)

// 监听列变化
watch(
  () => columns.value,
  () => {
    if (cellCopyEnabled.value) {
      setTimeout(() => {
        updateCopyIconVisibility()
      }, 100)
    }
  },
  { deep: true }
)
</script>

<style scoped>
/* 阶段1: 基础样式 */
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
  /* 启用flex布局 */
  align-items: center;
  /* 垂直居中 */
  justify-content: space-between;
  /* 左右分布 */
  background: #fff;
  /* 可选，分页条背景 */
  padding: 12px 24px;
  /* 上下留白，确保有足够空间垂直居中 */
  min-height: 56px;
  /* 最小高度，确保有足够的垂直空间 */
}

:deep(.ant-pagination) {
  display: flex !important;
  flex-direction: row !important;
  /* 强制横向排列 */
  align-items: center !important;
}

.pagination-left {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 100%;
}

.export-group {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  background: transparent;
}

.export-group .export-btn {
  height: 32px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.export-group .export-btn:hover {
  z-index: 1;
  position: relative;
}

.export-group .export-btn[type="primary"] {
  background: #1890ff;
  color: #fff;
  border-color: #1890ff;
}

.export-group .export-btn[type="primary"]:hover {
  background: #40a9ff;
  border-color: #40a9ff;
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

.action-icon.active {
  color: #1890ff;
  background: #e6f7ff;
}

.zebra-stripe-switch {
  display: flex;
  align-items: center;
  padding: 4px;
  cursor: pointer;
}

.draggable-columns {
  max-height: 300px;
  overflow-y: auto;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.draggable-columns::-webkit-scrollbar {
  display: none;
}

.draggable-column-item {
  display: flex;
  align-items: center;
  padding: 4px 2px;
  margin-bottom: 4px;
  background: transparent;
  border-radius: 4px;
  transition: background-color 0.2s ease;
  cursor: default;
}

.draggable-column-item:hover {
  background-color: #f5f5f5;
}

.draggable-column-item.sortable-ghost {
  opacity: 0.5;
  background: #e6f7ff;
}

.draggable-column-item.sortable-chosen {
  background: #e6f7ff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.drag-handle {
  margin-right: 8px;
  color: #bfbfbf;
  font-size: 16px;
  cursor: move;
  transition: color 0.2s;
}

.drag-handle:hover {
  color: #1890ff;
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

/* 单元格复制功能样式 */
.cell-text {
  display: block;
  width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: text;
  user-select: text;
  padding: 0;
  margin: 0;
  border-radius: 2px;
  box-sizing: border-box;
}

.cell-copy-icon {
  position: absolute;
  top: 4px;
  right: 4px;
  opacity: 0.4;
  font-size: 12px;
  color: #8c8c8c;
  transition: opacity 0.2s ease, color 0.2s ease, transform 0.2s ease;
  z-index: 1;
  background-color: rgba(255, 255, 255, 0.9);
  padding: 2px;
  border-radius: 2px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
  pointer-events: auto;
  line-height: 1;
  cursor: pointer;
  width: 16px;
  height: 16px;
  margin: 0;
}

:deep(.ant-table-tbody > tr > td:hover .cell-copy-icon) {
  opacity: 1;
  color: #1890ff;
  transform: scale(1.1);
  box-shadow: 0 2px 4px rgba(24, 144, 255, 0.2);
}

.cell-copy-icon:hover {
  opacity: 1 !important;
  color: #1890ff !important;
  transform: scale(1.15);
  box-shadow: 0 2px 6px rgba(24, 144, 255, 0.3);
}

:deep(.ant-table-tbody > tr > td:not(.ant-table-cell-fix-right):not(.ant-table-cell-fix-left)) {
  position: relative;
  overflow: hidden;
}

:deep(.ant-table-fixed-right .ant-table-tbody > tr > td),
:deep(.ant-table-fixed-left .ant-table-tbody > tr > td) {
  overflow: visible;
}

:deep(.ant-table-tbody > tr > td .cell-text) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 阶段9: 表格样式优化 */
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

:deep(.ant-table-tbody > tr:not(.table-row-even):not(.table-row-odd)) {
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

:deep(.ant-empty) {
  padding: 48px 0;
}

:deep(.ant-empty-description) {
  color: #8c8c8c;
  font-size: 14px;
}

:deep(.ant-spin-nested-loading) {
  min-height: 200px;
}

:deep(.ant-spin-container) {
  transition: opacity 0.3s ease;
}

.cell-copy-icon-hidden {
  display: none !important;
}

:deep(.ant-table-fixed-right),
:deep(.ant-table-fixed-left) {
  z-index: 100 !important;
  position: relative;
}

:deep(.ant-table-fixed-right .ant-table-tbody > tr > td),
:deep(.ant-table-fixed-left .ant-table-tbody > tr > td) {
  position: relative;
  z-index: 100;
}

:deep(.ant-table-fixed-right .ant-table-thead > tr > th),
:deep(.ant-table-fixed-left .ant-table-thead > tr > th) {
  z-index: 100;
  position: relative;
}

:deep(.ant-table-tbody > tr > td .cell-text) {
  width: 100%;
  box-sizing: border-box;
  min-width: 0;
  max-width: 100%;
}

:deep(.ant-table) {
  table-layout: fixed;
  width: 100%;
  max-width: 100%;
}

:deep(.ant-table-thead > tr > th),
:deep(.ant-table-tbody > tr > td) {
  max-width: 100%;
  box-sizing: border-box;
}

:deep(.ant-table-container) {
  width: 100%;
  max-width: 100%;
  overflow: hidden;
}

:deep(.ant-table-body) {
  width: 100%;
  max-width: 100%;
}

/* 分页样式 */
:deep(.ant-pagination),
:deep(.ant-pagination-item),
:deep(.ant-pagination-item-link) {
  height: 32px !important;
  /* 保证高度一致 */
  line-height: 32px !important;
  /* 保证内容垂直居中 */
  min-width: 32px;
  /* 保证宽度一致 */
  box-sizing: border-box;
  vertical-align: middle;
}

:deep(.ant-pagination-item-container) {
  /* 增加一点右边距，防止和"下一页"按钮重叠 */
  margin-right: 8px;
}

/* 修正省略号垂直居中 */
:deep(.ant-pagination-item-ellipsis) {
  line-height: 32px !important;
  /* AntD 默认高度 */
  vertical-align: middle !important;
  display: inline-block !important;
  font-size: 16px !important;
}

/* 保证分页条整体高度和内容一致 */
:deep(.ant-pagination) {
  min-height: 32px !important;
  height: 32px !important;
  line-height: 32px !important;
}

/* 保证每个分页项高度一致 */
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
</style>
