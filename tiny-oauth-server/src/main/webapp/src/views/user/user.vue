<template>
  <div class="content-container" style="position: relative;">
    <div class="content-card">
      <div class="form-container">
        <a-form layout="inline" :model="query">
          <a-form-item label="用户名">
            <a-input v-model:value="query.username" placeholder="请输入用户名" />
          </a-form-item>
          <a-form-item label="昵称">
            <a-input v-model:value="query.nickname" placeholder="请输入昵称" />
          </a-form-item>
          <a-form-item>
            <a-button type="primary" @click="throttledSearch">搜索</a-button>
            <a-button class="ml-2" @click="throttledReset">重置</a-button>
          </a-form-item>
        </a-form>
      </div>

      <div class="toolbar-container">
        <div class="table-title">
          用户列表
        </div>
        <div class="table-actions">
          <div v-if="selectedRowKeys.length > 0" class="batch-actions">
            <a-button type="primary" danger @click="throttledBatchDelete" class="toolbar-btn">
              <template #icon>
                <DeleteOutlined />
              </template>
              批量删除 ({{ selectedRowKeys.length }})
            </a-button>
            <a-tooltip v-if="!allDisabled" title="只有全部未启用用户时才可批量启用">
              <span>
                <a-button type="primary" class="toolbar-btn" :disabled="!allDisabled">
                  <template #icon>
                    <CheckCircleOutlined />
                  </template>
                  批量启用
                </a-button>
              </span>
            </a-tooltip>
            <a-button v-else type="primary" class="toolbar-btn" @click="throttledBatchEnable">
              <template #icon>
                <CheckCircleOutlined />
              </template>
              批量启用
            </a-button>
            <a-tooltip v-if="!allEnabled" title="只有全部已启用用户时才可批量禁用">
              <span>
                <a-button class="toolbar-btn" :disabled="!allEnabled">
                  <template #icon>
                    <StopOutlined />
                  </template>
                  批量禁用
                </a-button>
              </span>
            </a-tooltip>
            <a-button v-else class="toolbar-btn" @click="throttledBatchDisable">
              <template #icon>
                <StopOutlined />
              </template>
              批量禁用
            </a-button>
            <a-button @click="clearSelection" class="toolbar-btn">
              <template #icon>
                <CloseOutlined />
              </template>
              取消选择
            </a-button>
            <!-- 分配角色按钮，仅单选时可用，否则禁用并提示 -->
            <a-tooltip v-if="selectedRowKeys.length !== 1" title="请仅选择一个用户进行角色分配">
              <span>
                <a-button type="primary" class="toolbar-btn" disabled style="pointer-events: auto;">
                  <template #icon>
                    <SettingOutlined />
                  </template>
                  分配角色
                </a-button>
              </span>
            </a-tooltip>
            <a-button v-else type="primary" class="toolbar-btn" @click="openBatchRoleTransfer">
              <template #icon>
                <SettingOutlined />
              </template>
              分配角色
            </a-button>
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
          <a-tooltip :title="showSortTooltip ? '关闭排序提示' : '开启排序提示'">
            <PoweroffOutlined :class="['action-icon', { active: showSortTooltip }]"
              @click="showSortTooltip = !showSortTooltip" />
          </a-tooltip>
          <a-tooltip :title="cellCopyEnabled ? '关闭单元格复制' : '开启单元格复制'">
            <CopyOutlined :class="['action-icon', { active: cellCopyEnabled }]" @click="cellCopyEnabled = !cellCopyEnabled" />
          </a-tooltip>
          <a-dropdown placement="bottomRight" trigger="click">
            <a-tooltip title="表格密度">
              <ColumnHeightOutlined class="action-icon" />
            </a-tooltip>
            <template #overlay>
              <a-menu @click="handleDensityMenuClick" :selected-keys="[tableSize]">
                <a-menu-item key="default">
                  <span>默认</span>
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
                  <a-checkbox :checked="showColumnKeys.length === allColumns.length"
                    :indeterminate="showColumnKeys.length > 0 && showColumnKeys.length < allColumns.length"
                    @change="(e: any) => onCheckAllChange(e)" />
                  <span style="font-weight: bold; margin-left: 8px;">列展示/排序</span>
                </div>
                <span style="font-weight: bold; color: #1677ff; cursor: pointer;" @click="resetColumnOrder">
                  重置
                </span>
              </div>
              <VueDraggable v-model="draggableColumns"
                :item-key="(item: any) => item?.dataIndex || `col_${Math.random()}`" handle=".drag-handle"
                @end="onDragEnd" class="draggable-columns" ghost-class="sortable-ghost" chosen-class="sortable-chosen"
                tag="div">
                <template #item="{ element: col }">
                  <div class="draggable-column-item">
                    <HolderOutlined class="drag-handle" />
                    <a-checkbox :checked="showColumnKeys.includes(col.dataIndex)"
                      @change="(e: any) => onCheckboxChange(col.dataIndex, e.target.checked)">
                      {{ col.title }}
                    </a-checkbox>
                  </div>
                </template>
              </VueDraggable>
            </template>
            <a-tooltip title="列设置">
              <SettingOutlined class="action-icon" />
            </a-tooltip>
          </a-popover>
        </div>
      </div>
      <div class="table-container" ref="tableContentRef">
        <div class="table-scroll-container" ref="tableScrollContainerRef">
          <a-table :columns="columns" :data-source="tableData" :pagination="false"
            :row-key="(record: any) => String(record.id)" bordered :loading="loading" @change="handleTableChange"
            :row-selection="rowSelection" :custom-row="onCustomRow" :row-class-name="getRowClassName"
            :scroll="{ x: 1500, y: tableBodyHeight }" :locale="tableLocale" :show-sorter-tooltip="showSortTooltip"
            :size="tableSize === 'default' ? undefined : tableSize">
            <template #bodyCell="{ column, record, index }">
              <template v-if="column.dataIndex === 'index'">
                {{
                  ((Number(pagination.current) || 1) - 1) *
                    (Number(pagination.pageSize) || 10) +
                  index + 1
                }}
              </template>
              <template
                v-else-if="['enabled', 'accountNonExpired', 'accountNonLocked', 'credentialsNonExpired'].includes(column.dataIndex)">
                <a-tag :color="record[column.dataIndex] ? 'green' : 'red'">
                  {{ record[column.dataIndex] ? '是' : '否' }}
                </a-tag>
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
                <span v-else-if="column.dataIndex">{{ record[column.dataIndex as string] }}</span>
                <span v-else>-</span>
              </template>
            </template>
          </a-table>
        </div>
        <div class="pagination-container" ref="paginationRef">
          <div class="pagination-left">
            <div class="export-group">
              <a-button type="primary" :loading="exporting" @click="handleExportSync" class="export-btn">
                <template #icon>
                  <DownloadOutlined />
                </template>
                导出当前页
              </a-button>
              <a-button :loading="exportingAsync" @click="handleExportAsync" class="export-btn">
                <template #icon>
                  <DownloadOutlined />
                </template>
                导出全部（异步）
              </a-button>
            </div>
          </div>
          <a-pagination v-model:current="pagination.current" :page-size="pagination.pageSize" :total="pagination.total"
            :show-size-changer="pagination.showSizeChanger" :page-size-options="paginationConfig.pageSizeOptions"
            :show-total="pagination.showTotal" @change="handlePageChange" @showSizeChange="handlePageSizeChange"
            :locale="{ items_per_page: '条/页' }" />
        </div>
      </div>
    </div>

    <a-drawer v-model:open="drawerVisible"
      :title="drawerMode === 'create' ? '新建用户' : drawerMode === 'edit' ? '编辑用户' : '查看用户'" width="50%"
      :get-container="false" :style="{ position: 'absolute' }" @close="handleDrawerClose">
      <UserForm v-if="drawerVisible" :mode="drawerMode" :user-data="currentUser" @submit="handleFormSubmit"
        @cancel="handleDrawerClose" />
    </a-drawer>

    <RoleTransfer v-if="showBatchRoleTransfer" :open="showBatchRoleTransfer" :all-roles="allRoles"
      :model-value="batchSelectedRoleIds" @update:open="showBatchRoleTransfer = $event"
      @update:modelValue="handleBatchRoleAssign" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch, h, onBeforeUnmount, nextTick } from 'vue'
import { userList, createUser, updateUser, deleteUser, batchDeleteUsers, batchEnableUsers, batchDisableUsers } from '@/api/user'
import { PlusOutlined, ReloadOutlined, SettingOutlined, HolderOutlined, DeleteOutlined, CheckCircleOutlined, StopOutlined, CloseOutlined, EditOutlined, EyeOutlined, InfoCircleOutlined, QuestionCircleOutlined, PoweroffOutlined, DownloadOutlined, ColumnHeightOutlined, CopyOutlined } from '@ant-design/icons-vue'
import VueDraggable from 'vuedraggable'
import UserForm from '@/views/user/UserForm.vue'
import { message, Modal } from 'ant-design-vue'
import { useThrottle } from '@/utils/debounce'
import RoleTransfer from './RoleTransfer.vue'
import { getAllRoles, getRoleById } from '@/api/role'
import { getUserRoles, updateUserRoles } from '@/api/user'
import request from '@/utils/request'
import dayjs from 'dayjs'

const query = ref({
  username: '',
  nickname: ''
})

const tableData = ref<any[]>([])

const loading = ref(false)
const exporting = ref(false)
const exportingAsync = ref(false)
const cellCopyEnabled = ref(false)
const tableSize = ref<'default' | 'small' | 'middle' | 'large'>('default')
const zebraStripeEnabled = ref(true)

const selectedRowKeys = ref<string[]>([])

const pagination = ref({
  current: 1,
  pageSize: 10,
  showSizeChanger: true,
  pageSizeOptions: ['10', '20', '30', '40', '50'].filter(option => option && typeof option === 'string'),
  total: 0,
  showTotal: (total: number) => `共 ${total} 条`
})

const paginationConfig = computed(() => {
  const config = {
    current: Number(pagination.value.current) || 1,
    pageSize: Number(pagination.value.pageSize) || 10,
    showSizeChanger: pagination.value.showSizeChanger,
    pageSizeOptions: pagination.value.pageSizeOptions.filter(option => option && typeof option === 'string'),
    total: Number(pagination.value.total) || 0,
    showTotal: pagination.value.showTotal
  }
  console.log('paginationConfig computed:', config)
  return config
})

// 定义初始列顺序常量
const INITIAL_COLUMNS = [
  { title: 'ID', dataIndex: 'id', sorter: true },
  { title: '用户名', dataIndex: 'username', sorter: true },
  { title: '昵称', dataIndex: 'nickname', sorter: true },
  { title: '是否启用', dataIndex: 'enabled', sorter: true },
  { title: '账号未过期', dataIndex: 'accountNonExpired', sorter: true },
  { title: '账号未锁定', dataIndex: 'accountNonLocked', sorter: true },
  { title: '密码未过期', dataIndex: 'credentialsNonExpired', sorter: true },
  {
    title: '最后登录时间',
    dataIndex: 'lastLoginAt',
    width: 180,
    sorter: true,
    customRender: ({ text }: { text: any }) => {
      if (!text) return '-'
      try {
        const date = new Date(text)
        if (isNaN(date.getTime())) return '-'
        return date.toLocaleString('zh-CN', {
          year: 'numeric',
          month: '2-digit',
          day: '2-digit',
          hour: '2-digit',
          minute: '2-digit',
          second: '2-digit',
          hour12: false
        })
      } catch (error) {
        return '-'
      }
    }
  },
  {
    title: '操作',
    dataIndex: 'action',
    width: 200,
    fixed: 'right',
    align: 'center'
  }
]

// 用初始列顺序初始化
const allColumns = ref([...INITIAL_COLUMNS])
const draggableColumns = ref([...INITIAL_COLUMNS])
const showColumnKeys = ref(
  INITIAL_COLUMNS.map(col => col.dataIndex).filter(key => typeof key === 'string' && key)
)

watch(allColumns, (val) => {
  showColumnKeys.value = showColumnKeys.value.filter(key =>
    val.some(col => col.dataIndex === key)
  );
});

watch(draggableColumns, (val) => {
  // 只保留合法列
  allColumns.value = val.filter(col => col && typeof col.dataIndex === 'string')
  // showColumnKeys 只保留 allColumns 里存在的 dataIndex
  showColumnKeys.value = showColumnKeys.value.filter(key =>
    allColumns.value.some(col => col.dataIndex === key)
  )
})

function onCheckboxChange(dataIndex: string, checked: boolean) {
  console.log('复选框变化:', dataIndex, checked)
  if (!dataIndex || typeof dataIndex !== 'string') {
    console.warn('无效的 dataIndex:', dataIndex)
    return
  }
  if (checked) {
    if (!showColumnKeys.value.includes(dataIndex)) {
      showColumnKeys.value.push(dataIndex)
    }
  } else {
    showColumnKeys.value = showColumnKeys.value.filter(key => key !== dataIndex)
  }
  console.log('当前显示的列:', showColumnKeys.value)
}

console.log('allColumns.value:', allColumns.value)
console.log('showColumnKeys.value:', showColumnKeys.value)
const columns = computed(() => {
  console.log('columns computed 触发，allColumns:', allColumns.value)
  console.log('columns computed 触发，showColumnKeys:', showColumnKeys.value)

  // 只保留合法列
  const filtered = allColumns.value.filter(
    col =>
      col &&
      typeof col.dataIndex === 'string' &&
      col.dataIndex &&
      showColumnKeys.value.includes(col.dataIndex)
  )
  // 打印调试最终columns
  console.log('columns for table:', filtered)
  return [
    {
      title: '序号',
      dataIndex: 'index',
      width: 80,
      align: 'center',
      fixed: 'left',
      customRender: ({ index }: { index?: number }) => {
        const safeIndex = typeof index === 'number' && !isNaN(index) ? index : 0
        const current = Number(pagination.value.current) || 1
        const pageSize = Number(pagination.value.pageSize) || 10
        return (current - 1) * pageSize + safeIndex + 1
      }
    },
    ...filtered
  ]
})

const rowSelection = computed(() => ({
  selectedRowKeys: selectedRowKeys.value,
  onChange: (selectedKeys: string[], selectedRows: any[]) => {
    selectedRowKeys.value = selectedKeys;
    console.log('复选框选中变化后的行:', selectedRowKeys.value)
  },
  checkStrictly: false,
  preserveSelectedRowKeys: true,
  fixed: true
}))

async function loadData() {
  loading.value = true
  try {
    const params = {
      username: query.value.username.trim(),
      nickname: query.value.nickname.trim(),
      current: Number(pagination.value.current) || 1,
      pageSize: Number(pagination.value.pageSize) || 10
    }
    const res = await userList(params)
    tableData.value = Array.isArray(res.records) ? res.records : []
    pagination.value.total = Number(res.total ?? res.totalElements ?? 0)
  } catch (error) {
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

const throttledSearch = useThrottle(handleSearch, 1000)

function handleReset() {
  query.value.username = ''
  query.value.nickname = ''
  pagination.value.current = 1
  loadData()
}

const throttledReset = useThrottle(handleReset, 1000)

function handleTableChange(pag: any, filters: any, sorter: any) {
  console.log('表格变化事件:', { pag, filters, sorter })
  console.log('变化前的 pagination:', pagination.value)
  console.log('变化前的 paginationConfig:', paginationConfig.value)
  console.log('变化前的 columns:', columns.value)

  // 添加安全检查
  if (pag && typeof pag.current === 'number') {
    pagination.value.current = pag.current
  }
  if (pag && typeof pag.pageSize === 'number') {
    pagination.value.pageSize = pag.pageSize
  }

  console.log('变化后的 pagination:', pagination.value)
  loadData()

  console.log('变化后的 columns:', columns.value)
}

function handleCreate() {
  drawerMode.value = 'create'
  currentUser.value = {
    username: '',
    nickname: '',
    enabled: true,
    accountNonExpired: true,
    accountNonLocked: true,
    credentialsNonExpired: true
  }
  drawerVisible.value = true
}

const throttledCreate = useThrottle(handleCreate, 500)

const refreshing = ref(false)
async function handleRefresh() {
  refreshing.value = true      // 开始旋转刷新按钮
  loading.value = true         // 显示表格 loading
  await loadData().catch((error) => {
    console.error('刷新数据失败:', error)
  }).finally(() => {
    setTimeout(() => {
      refreshing.value = false
    }, 1000) // 1秒后停止旋转
    loading.value = false      // 关闭表格 loading
  })
}

const throttledRefresh = useThrottle(handleRefresh, 1000)

function onCheckAllChange(e: any) {
  if (e.target.checked) {
    showColumnKeys.value = INITIAL_COLUMNS.map(col => col.dataIndex)
  } else {
    showColumnKeys.value = []
  }
}

function resetColumnOrder() {
  allColumns.value = [...INITIAL_COLUMNS]
  draggableColumns.value = [...INITIAL_COLUMNS]
  showColumnKeys.value = INITIAL_COLUMNS
    .filter(col => typeof col.dataIndex === 'string' && col.dataIndex)
    .map(col => col.dataIndex)
}

function onDragEnd(event: any) {
  console.log('拖拽结束，新顺序:', draggableColumns.value.map(col => col.title))
  console.log('拖拽事件详情:', event)
}

function handleCellClick(record: any, dataIndex: string) {
  console.log('单元格被点击:', record, dataIndex)

  const recordId = record.id
  const isSelected = selectedRowKeys.value.includes(recordId)

  if (isSelected && selectedRowKeys.value.length === 1) {
    selectedRowKeys.value = []
    console.log('单元格点击: 取消唯一选中行', recordId)
  } else {
    selectedRowKeys.value = [recordId]
    console.log('单元格点击: 仅选中当前行', recordId)
  }

  console.log('更新后的复选框选中行:', selectedRowKeys.value)
}

function onCustomRow(record: any) {
  return {
    onClick: (event: MouseEvent) => {
      if ((event.target as HTMLElement).closest('.ant-checkbox-wrapper')) return;

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

const tableContentRef = ref<HTMLElement | null>(null)
const tableScrollContainerRef = ref<HTMLElement | null>(null)
const paginationRef = ref<HTMLElement | null>(null)
const tableBodyHeight = ref(400)

/**
 * 动态计算表格内容区（body）的高度，确保分页和表头不会被遮挡，且表格内容区能自适应剩余空间。
 * 计算方式：
 * 1. 获取表格内容区的总高度（containerHeight）
 * 2. 获取分页组件的高度（paginationHeight）
 * 3. 获取表头的高度（tableHeaderHeight，若获取不到则用默认值55px）
 * 4. 用总高度减去分页和表头高度，得到表格body的可用高度
 * 5. 设置最小高度为200，防止表格过度压缩
 *
 * 该方法会在页面挂载、窗口尺寸变化、每页条数变化时自动调用。
 */
function updateTableBodyHeight() {
  nextTick(() => {
    // 确保DOM已渲染
    if (tableContentRef.value && paginationRef.value) {
      // 获取表头DOM元素
      const tableHeader = tableContentRef.value.querySelector('.ant-table-header') as HTMLElement;
      // 获取表格内容区总高度
      const containerHeight = tableContentRef.value.clientHeight;
      // 获取分页组件高度
      const paginationHeight = paginationRef.value.clientHeight;
      // 获取表头高度，若获取不到则用默认值
      const tableHeaderHeight = tableHeader ? tableHeader.clientHeight : 55;
      // 计算表格body的可用高度
      const bodyHeight = containerHeight - paginationHeight - tableHeaderHeight;
      // 设置最小高度，防止表格内容区过小
      tableBodyHeight.value = Math.max(bodyHeight, 200);
    }
  });
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

function handleBatchDelete() {
  if (selectedRowKeys.value.length === 0) {
    message.warning('请先选择要删除的用户')
    return
  }
  Modal.confirm({
    title: '确认批量删除',
    content: `确定要删除选中的 ${selectedRowKeys.value.length} 个用户吗？`,
    okText: '确认',
    cancelText: '取消',
    onOk: () => {
      return batchDeleteUsers(selectedRowKeys.value)
        .then(() => {
          message.success('批量删除成功')
          selectedRowKeys.value = []
          loadData()
        })
        .catch((error: any) => {
          // 优先使用 Problem 格式的 detail，否则使用 error.message
            const errorMessage = (error as any)?.errorInfo?.message || error?.message || '未知错误'
            message.error('批量删除失败: ' + errorMessage)
          return Promise.reject(error)
        })
    }
  })
}

const throttledBatchDelete = useThrottle(handleBatchDelete, 1000)

function handleBatchEnable() {
  if (selectedRowKeys.value.length === 0) {
    message.warning('请先选择要启用的用户')
    return
  }

  console.log('批量启用用户:', selectedRowKeys.value)

  batchEnableUsers(selectedRowKeys.value)
    .then(() => {
      message.success('批量启用成功')
      selectedRowKeys.value = [] // 清空选择
      loadData() // 重新加载数据
    })
    .catch((error: any) => {
      console.error('批量启用失败:', error)
      message.error('批量启用失败: ' + (error.message || '未知错误'))
    })
}

const throttledBatchEnable = useThrottle(handleBatchEnable, 1000)

function handleBatchDisable() {
  if (selectedRowKeys.value.length === 0) {
    message.warning('请先选择要禁用的用户')
    return
  }

  console.log('批量禁用用户:', selectedRowKeys.value)

  batchDisableUsers(selectedRowKeys.value)
    .then(() => {
      message.success('批量禁用成功')
      selectedRowKeys.value = [] // 清空选择
      loadData() // 重新加载数据
    })
    .catch((error: any) => {
      console.error('批量禁用失败:', error)
      message.error('批量禁用失败: ' + (error.message || '未知错误'))
    })
}

const throttledBatchDisable = useThrottle(handleBatchDisable, 1000)

function clearSelection() {
  selectedRowKeys.value = []
  console.log('清除选择')
}

function getRowClassName(record: any, index: number) {
  if (selectedRowKeys.value.includes(record.id)) {
    return 'checkbox-selected-row'
  }
  if (!zebraStripeEnabled.value) {
  return ''
  }
  return index % 2 === 0 ? 'table-row-even' : 'table-row-odd'
}

function handlePageChange(page: number) {
  console.log('页码变化:', page)
  pagination.value.current = page || 1
  loadData()
}

function handlePageSizeChange(current: number, size: number) {
  console.log('每页条数变化:', { current, size })
  pagination.value.pageSize = size || 10
  pagination.value.current = 1
  loadData()
}

function handleDelete(record: any) {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除用户 ${record.username} 吗？`,
    okText: '确认',
    cancelText: '取消',
    onOk: () => {
      return deleteUser(record.id)
        .then(() => {
          message.success('用户删除成功')
          loadData()
        })
        .catch((error: any) => {
          message.error('删除用户失败: ' + (error.message || '未知错误'))
          return Promise.reject(error)
        })
    }
  })
}

const throttledDelete = useThrottle(handleDelete, 500)

function handleView(record: any) {
  drawerMode.value = 'view'
  currentUser.value = record
  drawerVisible.value = true
}

const throttledView = useThrottle(handleView, 500)

const drawerVisible = ref(false)
const drawerMode = ref<'create' | 'edit' | 'view'>('edit')
const currentUser = ref<any | null>(null)

function handleEdit(record: any) {
  drawerMode.value = 'edit'
  currentUser.value = record
  drawerVisible.value = true
}

const throttledEdit = useThrottle(handleEdit, 500)

// 处理表单提交（创建或更新用户）
async function handleFormSubmit(formData: any) {
  try {
    if (drawerMode.value === 'create') {
      // 创建用户
      await createUser(formData)
      message.success('用户创建成功')
    } else if (drawerMode.value === 'edit') {
      // 更新用户
      await updateUser(formData.id, formData)
      message.success('用户更新成功')
    }
    // 关闭抽屉并刷新数据
    handleDrawerClose()
    loadData()
  } catch (error: any) {
    console.error('保存用户失败:', error)
    message.error('保存失败: ' + (error.message || '未知错误'))
  }
}

function handleDrawerClose() {
  drawerVisible.value = false
  currentUser.value = null
}

const showSortTooltip = ref(true)

// 表格 locale 配置的计算属性
const tableLocale = computed(() => {
  if (showSortTooltip.value) {
    return {
      triggerDesc: '点击降序',
      triggerAsc: '点击升序',
      cancelSort: '取消排序'
    }
  }
  return undefined // 不显示任何排序提示
})

const selectedRows = computed(() => {
  return tableData.value.filter(row => selectedRowKeys.value.includes(String(row.id)))
})

const getExportColumns = () => {
  return INITIAL_COLUMNS
    .filter(col => col.dataIndex !== 'action')
    .map(col => ({
      title: col.title,
      field: col.dataIndex as string
    }))
}

const getExportFilters = () => {
  const filters: Record<string, any> = {}
  if (query.value.username?.trim()) {
    filters.username = query.value.username.trim()
  }
  if (query.value.nickname?.trim()) {
    filters.nickname = query.value.nickname.trim()
  }
  return filters
}

const allDisabled = computed(() => {
  return selectedRows.value.length > 0 && selectedRows.value.every(row => row.enabled === false)
})

const allEnabled = computed(() => {
  return selectedRows.value.length > 0 && selectedRows.value.every(row => row.enabled === true)
})

function handleDensityMenuClick({ key }: { key: string }) {
  if (key === 'default' || key === 'small' || key === 'middle' || key === 'large') {
    tableSize.value = key as 'default' | 'small' | 'middle' | 'large'
    updateTableBodyHeight()
  }
}

function handleCellCopy(value: any, columnTitle: string) {
  if (!cellCopyEnabled.value) return
  const textToCopy = value !== null && value !== undefined ? String(value) : ''
  if (!textToCopy.trim()) {
    message.warning('单元格内容为空，无法复制')
    return
  }
  if (navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard.writeText(textToCopy)
      .then(() => message.success(`已复制 ${columnTitle || '单元格'}：${textToCopy}`))
      .catch(() => fallbackCopy(textToCopy, columnTitle))
  } else {
    fallbackCopy(textToCopy, columnTitle)
  }
}

function fallbackCopy(text: string, columnTitle: string) {
  try {
    const area = document.createElement('textarea')
    area.value = text
    area.style.position = 'fixed'
    area.style.left = '-9999px'
    document.body.appendChild(area)
    area.focus()
    area.select()
    const ok = document.execCommand('copy')
    document.body.removeChild(area)
    if (ok) {
      message.success(`已复制 ${columnTitle || '单元格'}：${text}`)
    } else {
      message.error('复制失败，请手动复制')
    }
  } catch (e) {
    message.error('复制失败，请手动复制')
  }
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
      fileName: 'user',
      pageSize: pagination.value.pageSize,
      async: false,
      sheets: [
        {
          sheetName: '用户列表',
          exportType: 'user',
          filters: pageFilters,
          columns: getExportColumns(),
        },
      ],
    }

    const blob = await request.post<Blob>('/export/sync', exportRequest, { responseType: 'blob' as any })
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `user_${dayjs().format('YYYYMMDD_HHmmss')}.xlsx`
    a.click()
    window.URL.revokeObjectURL(url)
    message.success('导出成功，文件已开始下载')
  } catch (error: any) {
    message.error('导出失败: ' + (error?.message || '未知错误'))
  } finally {
    exporting.value = false
  }
}

async function handleExportAsync() {
  exportingAsync.value = true
  try {
    const exportRequest = {
      fileName: 'user',
      pageSize: 5000,
      async: true,
      sheets: [
        {
          sheetName: '用户列表',
          exportType: 'user',
          filters: getExportFilters(),
          columns: getExportColumns(),
        },
      ],
    }

    const res = await request.post<{ taskId: string }>('/export/async', exportRequest)
    const taskId = (res as any)?.taskId
    if (taskId) {
      Modal.success({
        title: '异步导出任务已创建',
        content: `任务ID: ${taskId}，请在"导出任务"页面查看进度并下载文件`,
        okText: '前往导出任务',
        onOk: () => {
          window.location.href = '/export/task'
        },
      })
    } else {
      message.success('异步导出任务已提交，请在"导出任务"页面查看进度')
    }
  } catch (error: any) {
    message.error('异步导出失败: ' + (error?.message || '未知错误'))
  } finally {
    exportingAsync.value = false
  }
}

const showBatchRoleTransfer = ref(false)
const allRoles = ref<any[]>([])
const batchSelectedRoleIds = ref<string[]>([])

async function openBatchRoleTransfer() {
  // 获取所有角色
  const all = await getAllRoles()
  allRoles.value = (all || []).map((r: any) => ({
    key: String(r.id),
    title: r.name + (r.description ? `（${r.description}）` : ''),
    ...r
  }))
  // 获取选中用户的角色交集（如需自定义可改为并集或第一个用户的角色）
  if (selectedRowKeys.value.length > 0) {
    // 这里只取第一个用户的角色作为默认
    const userId = Number(selectedRowKeys.value[0])
    const userRoleIds = await getUserRoles(userId)
    batchSelectedRoleIds.value = (userRoleIds || []).map((id: any) => String(id))
  } else {
    batchSelectedRoleIds.value = []
  }
  showBatchRoleTransfer.value = true
}

async function handleBatchRoleAssign(newRoleIds: string[]) {
  // 这里可以实现批量分配角色逻辑，如循环调用后端接口
  // 示例：对每个选中用户调用 updateUser/updateUserRoles
  for (const userIdStr of selectedRowKeys.value) {
    const userId = Number(userIdStr)
    const userRoleIds = await getUserRoles(userId)
    await updateUserRoles(userId, newRoleIds.map(id => Number(id)))
  }
  message.success('批量分配角色成功')
  showBatchRoleTransfer.value = false
  // 可选：刷新表格数据
  loadData()
}
</script>

<style scoped>
.content-container {
  height: 100%;
  display: flex;
  flex-direction: column;
  /* background: #f0f2f5; */
  background: #fff;
}

.content-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  /* 关键，防止撑开 */
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

.table-container {
  display: flex;
  /* 启用flex布局 */
  flex-direction: column;
  /* 垂直排列子元素 */
  flex: 1;
  /* 占满父容器剩余空间 */
  min-height: 0;
  /* 关键，防止撑开 */
}

.table-scroll-container {
  /* 不要设置 flex: 1; */
  min-height: 0;
  /* 可选，防止撑开 */
  overflow: auto;
  /* 内容多时滚动 */
}

.pagination-container {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  padding: 12px 24px;
  min-height: 56px;
}

.pagination-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.export-group {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.export-group .export-btn {
  height: 32px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
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

:deep(.ant-pagination) {
  display: flex !important;
  flex-direction: row !important;
  /* 强制横向排列 */
  align-items: center !important;
}

.ml-2 {
  margin-left: 8px;
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
  flex-wrap: wrap;
}

.batch-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  background: none;
  border: none;
  border-radius: 0;
  padding: 0;
  margin-right: 0;
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

.sortable-ghost .drag-handle {
  color: #1890ff;
}

:deep(.ant-table-tbody > tr) {
  cursor: pointer;
  transition: background-color 0.2s ease;
  user-select: none;
}

:deep(.ant-table-tbody > tr:hover) {
  background-color: #f5f5f5 !important;
}

:deep(.ant-table-tbody > tr.checkbox-selected-row) {
  background-color: #e6f7ff !important;
}

:deep(.ant-table-tbody > tr.checkbox-selected-row:hover) {
  background-color: #bae7ff !important;
}

:deep(.ant-table-tbody > tr .ant-checkbox-wrapper) {
  pointer-events: auto;
}

:deep(.ant-table-tbody > tr td) {
  pointer-events: auto;
}

.cell-content {
  width: 100%;
  height: 100%;
  cursor: pointer;
  padding: 4px 0;
  transition: background-color 0.2s ease;
}

.cell-content:hover {
  background-color: rgba(24, 144, 255, 0.1);
  border-radius: 4px;
}

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

:deep(.ant-table-tbody > tr:nth-child(odd)) {
  /* 奇数行背景色，浅灰色 */
  background-color: #fafbfc;
}

:deep(.ant-table-tbody > tr:nth-child(even)) {
  /* 偶数行背景色，白色 */
  background-color: #fff;
}

/* 隐藏表格内容区的滚动条，但保留滚动功能 */
:deep(.ant-table-body) {
  scrollbar-width: none;
  /* Firefox */
  -ms-overflow-style: none;
  /* IE 10+ */
}

:deep(.ant-table-body::-webkit-scrollbar) {
  display: none;
  /* Chrome/Safari/Edge */
}

/* 操作按钮样式 */
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

.action-btn:hover {
  background-color: #f5f5f5;
  border-radius: 4px;
}

/* 模态框样式优化 */
:deep(.ant-modal-header) {
  border-bottom: 1px solid #f0f0f0;
  padding: 16px 24px;
}

:deep(.ant-modal-body) {
  padding: 24px;
}

:deep(.ant-modal-footer) {
  border-top: 1px solid #f0f0f0;
  padding: 16px 24px;
}

/* 表单样式优化 */
:deep(.ant-form-item-label > label) {
  font-weight: 500;
  color: #262626;
}

:deep(.ant-switch) {
  min-width: 44px;
}

/* 描述列表样式 */
:deep(.ant-descriptions-item-label) {
  font-weight: 500;
  color: #262626;
  background-color: #fafafa;
}

:deep(.ant-descriptions-item-content) {
  color: #595959;
}

/* 保证表头永远单行显示，不出现省略号，不换行 */
:deep(.ant-table-thead > tr > th) {
  white-space: nowrap;
  /* 不换行，单行显示 */
  text-overflow: clip;
  /* 不显示省略号 */
  overflow: visible;
  /* 超出内容不隐藏 */
}

.toolbar-switch {
  margin-left: 8px;
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

/* 修正省略号垂直居中 */
:deep(.ant-pagination-item-ellipsis) {
  line-height: 32px !important;
  vertical-align: middle !important;
  display: inline-block !important;
  font-size: 16px !important;
}

/* 斑马纹类名（由 getRowClassName 控制） */
.table-row-even {
  background-color: #fafbfc;
}

.table-row-odd {
  background-color: #fff;
}

/* 单元格复制相关样式 */
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
  z-index: 10;
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

::deep(.ant-table-tbody > tr > td:hover .cell-copy-icon) {
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

::deep(.ant-table-tbody > tr > td) {
  position: relative;
  overflow: visible;
}

::deep(.ant-table-tbody > tr > td .cell-text) {
  width: 100%;
  box-sizing: border-box;
  min-width: 0;
  max-width: 100%;
}
</style>