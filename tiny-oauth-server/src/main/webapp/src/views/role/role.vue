<template>
  <!-- 外层内容容器，统一风格 -->
  <div class="content-container" style="position: relative;">
    <div class="content-card">
      <!-- 查询表单，风格与用户管理一致 -->
      <div class="form-container">
        <a-form layout="inline" :model="query">
          <a-form-item label="角色名">
            <a-input v-model:value="query.name" placeholder="请输入角色名" />
          </a-form-item>
          <a-form-item label="角色标识">
            <a-input v-model:value="query.code" placeholder="请输入角色标识" />
          </a-form-item>
          <a-form-item>
            <a-button type="primary" @click="throttledSearch">搜索</a-button>
            <a-button class="ml-2" @click="throttledReset">重置</a-button>
          </a-form-item>
        </a-form>
      </div>
      <!-- 工具栏，包含批量操作、新建、刷新、列设置等 -->
      <div class="toolbar-container">
        <div class="table-title">角色列表</div>
        <div class="table-actions">
          <div v-if="selectedRowKeys.length > 0" class="batch-actions">
            <a-tooltip
              v-if="hasBuiltinSelected && selectedRowKeys.length > 0"
              title="选中项包含内置角色，无法批量删除"
            >
              <span>
                <a-button
                  type="primary"
                  danger
                  disabled
                  class="toolbar-btn danger"
                  style="pointer-events: auto;"
                >
                  <template #icon>
                    <DeleteOutlined />
                  </template>
                  批量删除 ({{ selectedRowKeys.length }})
                </a-button>
              </span>
            </a-tooltip>
            <a-button
              v-else
              type="primary"
              danger
              @click="throttledBatchDelete"
              class="toolbar-btn danger"
            >
              <template #icon>
                <DeleteOutlined />
              </template>
              批量删除 ({{ selectedRowKeys.length }})
            </a-button>
            <a-button @click="clearSelection" class="toolbar-btn">取消选择</a-button>
            <a-tooltip v-if="selectedRowKeys.length !== 1" title="请仅选择一个角色进行用户配置">
              <span>
                <a-button type="primary" class="toolbar-btn" disabled style="pointer-events: auto;">
                  <template #icon>
                    <SettingOutlined />
                  </template>
                  配置用户
                </a-button>
              </span>
            </a-tooltip>
            <a-button v-else type="primary" class="toolbar-btn" @click="openBatchUserTransfer">
              <template #icon>
                <SettingOutlined />
              </template>
              配置用户
            </a-button>
            <!-- 新增：配置资源按钮 -->
            <a-tooltip v-if="selectedRowKeys.length !== 1" title="请仅选择一个角色进行资源配置">
              <span>
                <a-button type="primary" class="toolbar-btn" disabled style="pointer-events: auto;">
                  <template #icon>
                    <SettingOutlined />
                  </template>
                  配置资源
                </a-button>
              </span>
            </a-tooltip>
            <a-button v-else type="primary" class="toolbar-btn" @click="openResourceTransfer">
              <template #icon>
                <SettingOutlined />
              </template>
              配置资源
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
          <a-popover placement="bottomRight" trigger="click">
            <template #content>
              <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px;">
                <div style="display: flex; align-items: center;">
                  <a-checkbox
                    :checked="showColumnKeys.length === allColumns.length"
                    :indeterminate="showColumnKeys.length > 0 && showColumnKeys.length < allColumns.length"
                    @change="onCheckAllChange"
                  />
                  <span style="font-weight: bold; margin-left: 8px;">列展示/排序</span>
                </div>
                <span
                  style="font-weight: bold; color: #1677ff; cursor: pointer;"
                  @click="resetColumnOrder"
                >
                  重置
                </span>
              </div>
              <VueDraggable
                v-model="draggableColumns"
                :item-key="(item: any) => item?.dataIndex || ('col_' + Math.random())"
                handle=".drag-handle"
                @end="onDragEnd"
                class="draggable-columns"
                ghost-class="sortable-ghost"
                chosen-class="sortable-chosen"
                tag="div"
              >
                <template #item="{ element }">
                  <div class="draggable-column-item">
                    <HolderOutlined class="drag-handle" />
                    <a-checkbox
                      :checked="showColumnKeys.includes(element.dataIndex)"
                      @change="(e: any) => onCheckboxChange(element.dataIndex, e.target.checked)"
                    >
                      {{ element.title }}
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
      <!-- 表格区域，支持多选、动态列、分页 -->
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
              <template v-if="column.dataIndex === 'builtin'">
                <a-tag :color="record.builtin ? 'blue' : 'default'">
                  {{ record.builtin ? '是' : '否' }}
                </a-tag>
              </template>
              <template v-else-if="column.dataIndex === 'enabled'">
                <a-tag :color="record.enabled ? 'green' : 'red'">
                  {{ record.enabled ? '启用' : '禁用' }}
                </a-tag>
              </template>
              <template v-else-if="column.dataIndex === 'createdAt'">
                {{ formatDateTime(record.createdAt) }}
              </template>
              <template v-else-if="column.dataIndex === 'updatedAt'">
                {{ formatDateTime(record.updatedAt) }}
              </template>
              <template v-else-if="column.dataIndex === 'action'">
                <div class="action-buttons">
                  <a-button
                    type="link"
                    size="small"
                    @click.stop="throttledEdit(record)"
                    class="action-btn"
                  >
                    <template #icon>
                      <EditOutlined />
                    </template>
                    编辑
                  </a-button>
                  <a-tooltip v-if="record.builtin" title="内置角色不允许删除">
                    <span>
                      <a-button
                        type="link"
                        size="small"
                        danger
                        disabled
                        class="action-btn danger"
                        style="pointer-events: auto;"
                      >
                        <template #icon>
                          <DeleteOutlined />
                        </template>
                        删除
                      </a-button>
                    </span>
                  </a-tooltip>
                  <a-button
                    v-else
                    type="link"
                    size="small"
                    danger
                    @click.stop="throttledDelete(record)"
                    class="action-btn danger"
                  >
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
            :page-size="Number(pagination.pageSize)"
            :total="Number(pagination.total)"
            :show-size-changer="pagination.showSizeChanger"
            :page-size-options="paginationConfig.pageSizeOptions"
            :show-total="pagination.showTotal"
            @change="handlePageChange"
            @showSizeChange="handlePageSizeChange"
            :locale="{ items_per_page: '条/页' }"
          />
        </div>
      </div>
    </div>
    <!-- 抽屉表单，编辑/新建角色 -->
    <a-drawer
      v-if="drawerVisible"
      v-model:open="drawerVisible"
      :title="drawerMode === 'create' ? '新建角色' : '编辑角色'"
      width="50%"
      :get-container="false"
      :style="{ position: 'absolute' }"
      :destroy-on-close="true"
      @close="handleDrawerClose"
    >
      <RoleForm
        :mode="drawerMode"
        :role-data="currentRole"
        @submit="handleFormSubmit"
        @cancel="handleDrawerClose"
      />
    </a-drawer>
    <!-- 新增：批量配置用户弹窗 -->
    <UserTransfer
      v-if="showBatchUserTransfer"
      :open="showBatchUserTransfer"
      :all-users="allUsers"
      :model-value="batchSelectedUserIds"
      title="批量配置用户"
      @update:open="showBatchUserTransfer = $event"
      @update:modelValue="handleBatchUserAssign"
    />
    <!-- 新增：资源分配弹窗 -->
    <ResourceTransfer
      v-if="showResourceTransfer"
      :open="showResourceTransfer"
      :role-id="currentRoleId"
      title="配置资源"
      @update:open="showResourceTransfer = $event"
      @submit="handleResourceAssign"
    />
  </div>
</template>

<script setup lang="ts">
// 引入Vue相关API
import { ref, computed, onMounted, watch, nextTick, onBeforeUnmount } from 'vue'
// 引入角色API
import { roleList, createRole, updateRole, deleteRole } from '@/api/role'
// 引入Antd组件和图标
import { message, Modal } from 'ant-design-vue'
import { ReloadOutlined, SettingOutlined, HolderOutlined, PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import VueDraggable from 'vuedraggable'
import RoleForm from './RoleForm.vue'
import UserTransfer from './UserTransfer.vue' // 引入用户分配弹窗组件
import { userList } from '@/api/user' // 引入用户API
import ResourceTransfer from './ResourceTransfer.vue' // 资源分配弹窗组件

// 查询条件
const query = ref({ name: '', code: '' })
// 表格数据
const tableData = ref<any[]>([])
// 加载状态
const loading = ref(false)
// 选中行key
const selectedRowKeys = ref<string[]>([])
// 分页配置
const pagination = ref({
  current: 1,
  pageSize: 10,
  showSizeChanger: true,
  pageSizeOptions: ['10', '20', '30', '40', '50'],
  total: 0,
  showTotal: (total: number) => `共 ${total} 条`
})
const paginationConfig = computed(() => ({
  current: Number(pagination.value.current) || 1,
  pageSize: Number(pagination.value.pageSize) || 10,
  showSizeChanger: pagination.value.showSizeChanger,
  pageSizeOptions: pagination.value.pageSizeOptions,
  total: Number(pagination.value.total) || 0,
  showTotal: pagination.value.showTotal
}))
// 所有列定义
const INITIAL_COLUMNS = [
  { title: 'ID', dataIndex: 'id', width: 80 },
  { title: '角色名', dataIndex: 'name', width: 120 },
  { title: '角色标识', dataIndex: 'code', width: 150 },
  { title: '是否内置', dataIndex: 'builtin', width: 100 },
  { title: '是否启用', dataIndex: 'enabled', width: 100 },
  { title: '描述', dataIndex: 'description', width: 200 },
  { title: '创建时间', dataIndex: 'createdAt', width: 160 },
  { title: '更新时间', dataIndex: 'updatedAt', width: 160 },
  { title: '操作', dataIndex: 'action', width: 160, fixed: 'right', align: 'center' }
]
const allColumns = ref([...INITIAL_COLUMNS])
// 可拖拽列
const draggableColumns = ref([...INITIAL_COLUMNS])
// 当前显示的列
const showColumnKeys = ref(
  INITIAL_COLUMNS.map(col => col.dataIndex).filter(key => typeof key === 'string' && key)
)
// 列变化时同步
watch(allColumns, (val) => {
  showColumnKeys.value = showColumnKeys.value.filter(key => val.some(col => col.dataIndex === key))
})
watch(draggableColumns, (val) => {
  allColumns.value = val.filter(col => typeof col.dataIndex === 'string')
  showColumnKeys.value = showColumnKeys.value.filter(key => allColumns.value.some(col => col.dataIndex === key))
})
// 列复选框变化
function onCheckboxChange(dataIndex: string, checked: boolean) {
  if (!dataIndex) return
  if (checked) {
    if (!showColumnKeys.value.includes(dataIndex)) showColumnKeys.value.push(dataIndex)
  } else {
    showColumnKeys.value = showColumnKeys.value.filter(key => key !== dataIndex)
  }
}
// 列全选
function onCheckAllChange(e: any) {
  if (e.target.checked) {
    showColumnKeys.value = INITIAL_COLUMNS.map(col => col.dataIndex)
  } else {
    showColumnKeys.value = []
  }
}
const DEFAULT_COLUMN_KEYS = INITIAL_COLUMNS
  .filter(col => typeof col.dataIndex === 'string' && col.dataIndex)
  .map(col => col.dataIndex)

function resetColumnOrder() {
  allColumns.value = [...INITIAL_COLUMNS]
  draggableColumns.value = [...INITIAL_COLUMNS]
  showColumnKeys.value = [...DEFAULT_COLUMN_KEYS]
}
// 拖拽结束
function onDragEnd() {}
// 计算最终表格列
const columns = computed(() => [
  {
    title: '序号',
    dataIndex: 'index',
    width: 80,
    align: 'center',
    fixed: 'left',
    customRender: ({ index }: { index?: number }) => {
      const current = Number(pagination.value.current) || 1
      const pageSize = Number(pagination.value.pageSize) || 10
      return (current - 1) * pageSize + (typeof index === 'number' ? index : 0) + 1
    }
  },
  ...INITIAL_COLUMNS.filter(col => showColumnKeys.value.includes(col.dataIndex))
])
// 多选配置，所有 id 强制转字符串，保证类型一致
const rowSelection = computed(() => ({
  selectedRowKeys: selectedRowKeys.value,
  onChange: (selectedKeys: (string|number)[]) => {
    // 全部转为字符串，防止类型不一致
    selectedRowKeys.value = selectedKeys.map(String);
  },
  checkStrictly: false,
  preserveSelectedRowKeys: true,
  fixed: true
}))
// 表格内容区高度自适应
const tableContentRef = ref<HTMLElement | null>(null)
const tableScrollContainerRef = ref<HTMLElement | null>(null)
const paginationRef = ref<HTMLElement | null>(null)
const tableBodyHeight = ref(400)
function updateTableBodyHeight() {
  nextTick(() => {
    if (tableContentRef.value && paginationRef.value) {
      const tableHeader = tableContentRef.value.querySelector('.ant-table-header') as HTMLElement;
      const containerHeight = tableContentRef.value.clientHeight;
      const paginationHeight = paginationRef.value.clientHeight;
      const tableHeaderHeight = tableHeader ? tableHeader.clientHeight : 55;
      const bodyHeight = containerHeight - paginationHeight - tableHeaderHeight;
      tableBodyHeight.value = Math.max(bodyHeight, 200);
    }
  });
}
// 加载数据
async function loadData() {
  loading.value = true
  try {
    const params = {
      name: query.value.name.trim(),
      code: query.value.code.trim(),
      page: (Number(pagination.value.current) || 1) - 1,
      size: Number(pagination.value.pageSize) || 10
    }
    const res = await roleList(params)
    tableData.value = Array.isArray(res.content) ? res.content : []
    // 确保 total 是数字类型，避免类型检查警告
    pagination.value.total = Number(res.totalElements) || 0
  } catch (error) {
    tableData.value = []
    pagination.value.total = 0
  } finally {
    loading.value = false
  }
}
// 查询
function handleSearch() {
  pagination.value.current = 1
  loadData()
}
const throttledSearch = handleSearch
// 重置
function handleReset() {
  query.value.name = ''
  query.value.code = ''
  pagination.value.current = 1
  loadData()
}
const throttledReset = handleReset
// 新建
function handleCreate() {
  drawerMode.value = 'create'
  currentRole.value = { id: '', name: '', code: '', description: '', builtin: false, enabled: true }
  drawerVisible.value = true
}
const throttledCreate = handleCreate
// 刷新动画状态，控制刷新按钮旋转
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
const throttledRefresh = handleRefresh
// 清除选择
function clearSelection() { selectedRowKeys.value = [] }
// 分页变化
function handlePageChange(page: number) {
  pagination.value.current = page || 1
  loadData()
}
function handlePageSizeChange(current: number, size: number) {
  pagination.value.pageSize = size || 10
  pagination.value.current = 1
  loadData()
}
// 批量删除
function handleBatchDelete() {
  if (selectedRowKeys.value.length === 0) {
    message.warning('请先选择要删除的角色')
    return
  }
  Modal.confirm({
    title: '确认批量删除',
    content: `确定要删除选中的 ${selectedRowKeys.value.length} 个角色吗？`,
    okText: '确认',
    cancelText: '取消',
    onOk: () => {
      return Promise.all(selectedRowKeys.value.map(id => deleteRole(id)))
        .then(() => {
          message.success('批量删除成功')
          selectedRowKeys.value = []
          loadData()
        })
        .catch((error: any) => {
          // 优先使用 Problem 格式的 detail，否则使用 error.message
            const errorMessage = (error as any)?.errorInfo?.message || error?.message || '未知错误'
            message.error('批量删除失败: ' + errorMessage)
          return Promise.reject(error);
        })
    }
  })
}
const throttledBatchDelete = handleBatchDelete
// 单条删除
function handleDelete(record: any) {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除角色 ${record.name} 吗？`,
    okText: '确认',
    cancelText: '取消',
    onOk: () => {
      return deleteRole(record.id).then(() => {
        message.success('删除成功')
        loadData()
      }).catch((error: any) => {
        message.error('删除角色失败: ' + (error.message || '未知错误'))
        return Promise.reject(error);
      });
    }
  })
}
const throttledDelete = handleDelete
// 编辑
function handleEdit(record: any) {
  drawerMode.value = 'edit'
  currentRole.value = { ...record }
  drawerVisible.value = true
}
const throttledEdit = handleEdit
// 抽屉关闭
function handleDrawerClose() {
  drawerVisible.value = false
  currentRole.value = null
}
// 保存（新建/编辑）
async function handleFormSubmit(formData: any) {
  console.log('handleFormSubmit收到', formData) // 这里应有 userIds
  try {
    if (drawerMode.value === 'edit' && formData.id) {
      await updateRole(formData.id, formData)
      message.success('更新成功')
    } else {
      await createRole(formData)
      message.success('创建成功')
    }
    handleDrawerClose()
    loadData()
  } catch (error: any) {
    message.error('保存失败: ' + (error.message || '未知错误'))
  }
}
// 格式化日期时间函数
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
  } catch (error) {
    return '-'
  }
}

// 生命周期钩子
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
// 抽屉相关
const drawerVisible = ref(false)
const drawerMode = ref<'create' | 'edit'>('edit')
const currentRole = ref<any | null>(null)

// 行点击事件，单选/取消唯一选中，和用户管理完全一致，所有 id 统一用字符串
function onCustomRow(record: any) {
  return {
    onClick: (event: MouseEvent) => {
      // 如果点击的是复选框本身则不处理
      if ((event.target as HTMLElement).closest('.ant-checkbox-wrapper')) return;
      // 统一用字符串类型的 id
      const recordId = String(record.id)
      const isSelected = selectedRowKeys.value.includes(recordId)
      if (isSelected && selectedRowKeys.value.length === 1) {
        // 只选中当前行时，点击取消选中
        selectedRowKeys.value = []
      } else {
        // 只选中当前行
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

// 是否选中项包含内置角色
const hasBuiltinSelected = computed(() =>
  tableData.value.some(
    (row: any) => selectedRowKeys.value.includes(String(row.id)) && row.builtin
  )
)

// 批量配置用户弹窗相关响应式变量
const showBatchUserTransfer = ref(false) // 控制弹窗显示
const allUsers = ref<any[]>([]) // 所有可选用户
const batchSelectedUserIds = ref<string[]>([]) // 已分配用户ID

// 打开批量配置用户弹窗
async function openBatchUserTransfer() {
  // 查询所有用户（假设不超过1000条）
  const res = await userList({ current: 1, pageSize: 1000 })
  allUsers.value = (res.records || []).map((u: any) => ({
    key: String(u.id),
    title: u.username + (u.nickname ? `（${u.nickname}）` : ''),
    ...u
  }))
  // 这里只取第一个选中角色的用户作为默认（如需交集/并集可自定义）
  if (selectedRowKeys.value.length > 0) {
    // 取第一个角色ID
    const roleId = selectedRowKeys.value[0]
    // 查询该角色已分配用户
    const { getRoleUsers } = await import('@/api/role')
    const userIds = await getRoleUsers(Number(roleId))
    batchSelectedUserIds.value = (userIds || []).map((id: any) => String(id))
  } else {
    batchSelectedUserIds.value = []
  }
  showBatchUserTransfer.value = true
}
// 批量保存分配用户
async function handleBatchUserAssign(newUserIds: string[]) {
  // 对每个选中角色调用 updateRoleUsers
  const { updateRoleUsers } = await import('@/api/role')
  for (const roleIdStr of selectedRowKeys.value) {
    const roleId = Number(roleIdStr)
    await updateRoleUsers(roleId, newUserIds.map(id => Number(id)))
  }
  message.success('批量配置用户成功')
  showBatchUserTransfer.value = false
  loadData() // 刷新表格
}

// 控制资源分配弹窗显示
const showResourceTransfer = ref(false)
// 当前角色ID
const currentRoleId = ref<number>(0)
// 所有资源列表
const allResources = ref<any[]>([])
// 已分配资源ID
const batchSelectedResourceIds = ref<string[]>([])

// 打开资源分配弹窗
async function openResourceTransfer() {
  // 设置当前角色ID
  if (selectedRowKeys.value.length > 0) {
    currentRoleId.value = Number(selectedRowKeys.value[0])
  }
  showResourceTransfer.value = true
}
// 保存分配资源
async function handleResourceAssign(newResourceIds: number[]) {
  try {
    // 调用 updateRoleResources 保存角色资源分配
    const { updateRoleResources } = await import('@/api/role')
    await updateRoleResources(currentRoleId.value, newResourceIds)
    message.success('配置资源成功')
    showResourceTransfer.value = false
    loadData() // 刷新表格
  } catch (error: any) {
    message.error('配置资源失败: ' + (error.message || '未知错误'))
  }
}
</script>

<style scoped>
/* 复用用户管理页面样式，保证风格一致 */
.content-container {
  height: 100%;
  display: flex;
  flex-direction: column;
  /* background: #f0f2f5; */
  background: #fff; /* 与 user.vue 保持一致 */
}

.content-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0; /* 关键，防止撑开 */
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
  display: flex;              /* 启用flex布局 */
  flex-direction: column;     /* 垂直排列子元素 */
  flex: 1;                    /* 占满父容器剩余空间 */
  min-height: 0;              /* 关键，防止撑开 */
}

.table-scroll-container {
  /* 不要设置 flex: 1; */
  min-height: 0; /* 可选，防止撑开 */
  overflow: auto; /* 内容多时滚动 */
  /* 隐藏滚动条但保持滚动功能 */
  scrollbar-width: none; /* Firefox */
  -ms-overflow-style: none; /* IE and Edge */
}

/* 隐藏 Webkit 浏览器的滚动条 */
.table-scroll-container::-webkit-scrollbar {
  display: none;
}

.pagination-container {
  display: flex;                /* 启用flex布局 */
  align-items: center;          /* 垂直居中 */
  justify-content: flex-end;    /* 右对齐 */
  background: #fff;             /* 可选，分页条背景 */
  padding: 12px 24px;
  /* 上下留白，确保有足够空间垂直居中 */
  min-height: 56px;
  /* 最小高度，确保有足够的垂直空间 */
}

:deep(.ant-pagination) {
  min-height: 32px !important;
  height: 32px !important;
  line-height: 32px !important;
  display: flex !important;
  flex-direction: row !important;
  align-items: center !important;
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

:deep(.ant-pagination-item-ellipsis) {
  line-height: 32px !important;
  vertical-align: middle !important;
  display: inline-block !important;
  font-size: 16px !important;
}

.ml-2 { margin-left: 8px; }

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

.action-buttons {
  display: flex;
  align-items: center;
  gap: 8px;
  justify-content: center;
  min-width: 120px;
}

.action-btn {
  padding: 0 8px;
  height: 28px;
  line-height: 28px;
  font-size: 13px;
  border-radius: 4px;
  transition: background 0.2s;
  color: #1677ff;
  background: none;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
}

.action-btn:hover {
  background-color: #f0f5ff;
  color: #0958d9;
}

/* 删除按钮特殊色 */
.action-btn.danger,
.action-btn[danger] {
  color: #ff4d4f;
}

.action-btn.danger:hover,
.action-btn[danger]:hover {
  background-color: #fff1f0;
  color: #cf1322;
}

/* 禁用状态的删除按钮变灰色，保留边框 */
.action-btn.danger[disabled],
.action-btn.danger[disabled]:hover,
.action-btn.danger[disabled]:focus {
  color: #bfbfbf !important;
  background: #fff !important;
  border: 1px solid #d9d9d9 !important; /* 保留灰色边框 */
  cursor: not-allowed !important;
  box-shadow: none !important;
}

/* 隐藏所有滚动条但保持滚动功能 */
:deep(.ant-table-body),
:deep(.ant-table-content),
:deep(.ant-table-scroll) {
  scrollbar-width: none !important; /* Firefox */
  -ms-overflow-style: none !important; /* IE and Edge */
}

:deep(.ant-table-body::-webkit-scrollbar),
:deep(.ant-table-content::-webkit-scrollbar),
:deep(.ant-table-scroll::-webkit-scrollbar) {
  display: none !important;
}

/* 确保表格容器也不显示滚动条 */
.table-scroll-container :deep(.ant-table) {
  scrollbar-width: none !important;
  -ms-overflow-style: none !important;
}

.table-scroll-container :deep(.ant-table::-webkit-scrollbar) {
  display: none !important;
}

/* 表头单行显示控制 */
:deep(.ant-table-thead > tr > th) {
  white-space: nowrap !important; /* 防止换行 */
  overflow: hidden !important; /* 隐藏溢出内容 */
  text-overflow: ellipsis !important; /* 显示省略号 */
  word-break: keep-all !important; /* 防止单词断开 */
  line-height: 1.2 !important; /* 控制行高 */
  padding: 12px 8px !important; /* 调整内边距 */
  height: 48px !important; /* 固定表头高度 */
  vertical-align: middle !important; /* 垂直居中 */
}

/* 表头文字样式 */
:deep(.ant-table-thead > tr > th .ant-table-column-title) {
  white-space: nowrap !important;
  overflow: hidden !important;
  text-overflow: ellipsis !important;
  display: block !important;
  width: 100% !important;
}

/* 表头排序图标位置调整 */
:deep(.ant-table-thead > tr > th .ant-table-column-sorter) {
  margin-left: 4px !important;
}

/* 表头复选框位置调整 */
:deep(.ant-table-thead > tr > th .ant-checkbox-wrapper) {
  margin: 0 !important;
}

/* 表头单元格内容布局 */
:deep(.ant-table-thead > tr > th > div) {
  white-space: nowrap !important;
  overflow: hidden !important;
  text-overflow: ellipsis !important;
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
  height: 100% !important;
}

/* 表头文字容器 */
:deep(.ant-table-thead > tr > th .ant-table-column-title) {
  flex: 1 !important;
  min-width: 0 !important;
  white-space: nowrap !important;
  overflow: hidden !important;
  text-overflow: ellipsis !important;
}

/* 表头排序图标容器 */
:deep(.ant-table-thead > tr > th .ant-table-column-sorter) {
  flex-shrink: 0 !important;
  margin-left: 4px !important;
}

/* 表头复选框容器 */
:deep(.ant-table-thead > tr > th .ant-checkbox-wrapper) {
  flex-shrink: 0 !important;
}

/* 确保表头行高度固定 */
:deep(.ant-table-thead > tr) {
  height: 48px !important;
}

/* 表头单元格最小宽度控制 */
:deep(.ant-table-thead > tr > th) {
  min-width: 80px !important;
  max-width: none !important;
}

/* 复用user/index.vue的隔行换色和高亮样式 */
:deep(.ant-table-tbody > tr:nth-child(odd)) {
  background-color: #fafbfc;
}
:deep(.ant-table-tbody > tr:nth-child(even)) {
  background-color: #fff;
}
:deep(.ant-table-tbody > tr.checkbox-selected-row) {
  background-color: #e6f7ff !important;
}
:deep(.ant-table-tbody > tr.checkbox-selected-row:hover) {
  background-color: #bae7ff !important;
}

/* 表格内容单元格单行省略号 */
:deep(.ant-table-tbody > tr > td) {
  white-space: nowrap !important;      /* 不换行 */
  overflow: hidden !important;         /* 超出隐藏 */
  text-overflow: ellipsis !important;  /* 超出显示省略号 */
  word-break: keep-all !important;     /* 防止单词断开 */
  max-width: 100%;                     /* 防止撑破表格 */
}

/* 禁用状态的批量删除按钮变灰色，保留边框 */
.toolbar-btn.danger[disabled],
.toolbar-btn.danger[disabled]:hover,
.toolbar-btn.danger[disabled]:focus {
  color: #bfbfbf !important;
  background: #fff !important;
  border: 1px solid #d9d9d9 !important; /* 保留灰色边框 */
  cursor: not-allowed !important;
  box-shadow: none !important;
}

/* 操作列禁用删除按钮无边框，仅变灰色，背景透明 */
:deep(.action-buttons .action-btn.danger[disabled]),
:deep(.action-buttons .action-btn.danger[disabled]:hover),
:deep(.action-buttons .action-btn.danger[disabled]:focus) {
  color: #bfbfbf !important;
  background: none !important;
  border: none !important;
  box-shadow: none !important;
  cursor: not-allowed !important;
  outline: none !important;
}
</style> 