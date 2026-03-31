<template>
  <!-- 外层内容容器，统一风格 -->
  <div class="content-container" style="position: relative;">
    <div v-if="!canReadResourceManagement" class="content-card">
      <div class="platform-guard-card">
        <div class="platform-guard-kicker">Permission Required</div>
        <h3>资源管理需要额外授权</h3>
        <p>
          当前页面属于后台配置面。只有具备 <code>system:resource:list</code> 或管理员权限的用户，才会请求
          <code>/sys/resources</code> 并展示资源管理数据。
        </p>
      </div>
    </div>

    <div v-else class="content-card">
      <!-- 查询表单 -->
      <div class="form-container">
        <a-form layout="inline" :model="query">
          <a-form-item label="资源名称">
            <a-input v-model:value="query.name" placeholder="请输入资源名称" />
          </a-form-item>
          <a-form-item label="资源标题">
            <a-input v-model:value="query.title" placeholder="请输入资源标题" />
          </a-form-item>
          <a-form-item label="URL(路由路径)">
            <a-input v-model:value="query.url" placeholder="请输入路由路径" style="width: 160px;" />
          </a-form-item>
          <a-form-item label="URI(API路径)">
            <a-input v-model:value="query.uri" placeholder="请输入API路径" style="width: 160px;" />
          </a-form-item>
          <a-form-item label="权限标识">
            <a-input v-model:value="query.permission" placeholder="请输入权限标识" />
          </a-form-item>
          <a-form-item label="资源类型">
            <a-select v-model:value="query.type" placeholder="请选择资源类型">
              <a-select-option :value="undefined">全部</a-select-option>
              <a-select-option :value="ResourceType.DIRECTORY">目录</a-select-option>
              <a-select-option :value="ResourceType.MENU">菜单</a-select-option>
              <a-select-option :value="ResourceType.BUTTON">按钮</a-select-option>
              <a-select-option :value="ResourceType.API">API</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item>
            <a-button type="primary" @click="throttledSearch">搜索</a-button>
            <a-button class="ml-2" @click="throttledReset">重置</a-button>
          </a-form-item>
        </a-form>
      </div>
      
      <!-- 工具栏 -->
      <div class="toolbar-container">
        <div class="table-title">资源管理</div>
        <div class="table-actions">
          <div v-if="selectedRowKeys.length > 0" class="batch-actions">
            <a-button v-if="canBatchDeleteResourceManagement" type="primary" danger @click="throttledBatchDelete" class="toolbar-btn">
              批量删除 ({{ selectedRowKeys.length }})
            </a-button>
            <a-button @click="clearSelection" class="toolbar-btn">取消选择</a-button>
          </div>
          <a-button v-if="canCreateResourceManagement" type="link" @click="throttledCreate" class="toolbar-btn">
            <template #icon>
              <PlusOutlined />
            </template>
            新建资源
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

      <div class="carrier-transition-note">
        当前管理面已进入拆分载体过渡期：目录/菜单来自 <code>menu</code>，按钮来自 <code>ui_action</code>，接口来自
        <code>api_endpoint</code>。
      </div>
      
      <!-- 表格区域 -->
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
            :scroll="{ x: 1400, y: tableBodyHeight }"
            defaultExpandAllRows
            :row-expandable="(record: ResourceItem) => !record.leaf"
          >
            <template #expandIcon="props">
              <span
                v-if="props && !props.record?.leaf"
                class="custom-expand-icon"
                @click="(e) => { e.stopPropagation(); props.onExpand && props.onExpand(props.record, e) }"
                style="cursor: pointer; color: #1677ff; font-size: 16px;"
              >
                <template v-if="props?.expanded">
                  <MinusOutlined style="margin-right: 2px;" />
                </template>
                <template v-else>
                  <PlusOutlined style="margin-right: 2px;" />
                </template>
              </span>
            </template>
            <template #bodyCell="{ column, record }">
              <template v-if="column.dataIndex === 'method'">
                <a-tag :color="getMethodColor(record.method)">
                  {{ record.method }}
                </a-tag>
              </template>
              <template v-else-if="column.dataIndex === 'type'">
                <a-tag :color="getTypeColor(record.type)">
                  {{ getTypeText(record.type) }}
                </a-tag>
              </template>
              <template v-else-if="column.dataIndex === 'carrierKind'">
                <a-tag :color="getCarrierKindColor(record.carrierKind)">
                  {{ getCarrierKindText(record.carrierKind, record.type) }}
                </a-tag>
              </template>
              <template v-else-if="column.dataIndex === 'icon'">
                <Icon :icon="record.icon" />
              </template>
              <template v-else-if="column.dataIndex === 'action'">
                <div class="action-buttons">
                  <a-button v-if="canUpdateResourceManagement" type="link" size="small" @click.stop="throttledEdit(record)" class="action-btn">
                    <template #icon>
                      <EditOutlined />
                    </template>
                    编辑
                  </a-button>
                  <a-button v-if="canDeleteResourceManagement" type="link" size="small" danger @click.stop="throttledDelete(record)" class="action-btn">
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
      </div>
    </div>
    
    <!-- 抽屉表单 -->
    <a-drawer
      v-model:open="drawerVisible"
      :title="drawerMode === 'create' ? '新建资源' : '编辑资源'"
      width="600px"
      :get-container="false"
      :style="{ position: 'absolute' }"
      @close="handleDrawerClose"
    >
      <ResourceForm
        v-if="drawerVisible"
        :mode="drawerMode"
        :resource-data="currentResource"
        @submit="handleFormSubmit"
        @cancel="handleDrawerClose"
      />
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
// 引入Vue相关API
import { ref, computed, onMounted, watch, nextTick, onBeforeUnmount } from 'vue'
import { useAuth } from '@/auth/auth'
// 引入资源API
import { getResourceTree, getRuntimeUiActions, createResource, updateResource, deleteResource, batchDeleteResources, type ResourceItem, type ResourceQuery, ResourceType } from '@/api/resource'
import { ACTIVE_SCOPE_CHANGED_EVENT } from '@/utils/activeScopeEvents'
// 引入Antd组件和图标
import { message, Modal } from 'ant-design-vue'
import { 
  ReloadOutlined, 
  PlusOutlined, 
  EditOutlined, 
  DeleteOutlined,
  SettingOutlined,
  HolderOutlined,
  MinusOutlined
} from '@ant-design/icons-vue'
import VueDraggable from 'vuedraggable'
import ResourceForm from './ResourceForm.vue'
import Icon from '@/components/Icon.vue'
import { extractAuthoritiesFromJwt } from '@/utils/jwt'
import { RESOURCE_MANAGEMENT_READ_AUTHORITIES } from '@/constants/permission'

// 查询条件
const query = ref<ResourceQuery>({ 
  name: '', 
  uri: '',
  permission: '',
  type: undefined
})
const { user } = useAuth()
const resourceAuthorities = computed(() => new Set(extractAuthoritiesFromJwt(user.value?.access_token)))

function hasAnyResourceAuthority(requiredAuthorities: string[]) {
  return requiredAuthorities.some((authority) => resourceAuthorities.value.has(authority))
}

const canReadResourceManagement = computed(() => hasAnyResourceAuthority(RESOURCE_MANAGEMENT_READ_AUTHORITIES))
const runtimeUiActionPermissions = ref<Set<string>>(new Set())
const runtimeUiActionsLoaded = ref(false)

function hasRuntimeUiAction(permission: string) {
  if (!runtimeUiActionsLoaded.value) {
    return false
  }
  return runtimeUiActionPermissions.value.has(permission)
}

const canCreateResourceManagement = computed(() => hasRuntimeUiAction('system:resource:create'))
const canUpdateResourceManagement = computed(() => hasRuntimeUiAction('system:resource:edit'))
const canDeleteResourceManagement = computed(() => hasRuntimeUiAction('system:resource:delete'))
const canBatchDeleteResourceManagement = computed(() => hasRuntimeUiAction('system:resource:batch-delete'))

// 表格数据（树形结构）
const tableData = ref<ResourceItem[]>([])

// 加载状态
const loading = ref(false)

// 选中行key
const selectedRowKeys = ref<string[]>([])

// 刷新动画状态
const refreshing = ref(false)

// 初始列定义
const INITIAL_COLUMNS = [
  { title: '资源ID', dataIndex: 'id', width: 120 },
  { title: '资源名称', dataIndex: 'name', width: 150 },
  { title: '资源标题', dataIndex: 'title', width: 150 },
  { title: 'URL(路由路径)', dataIndex: 'url', width: 200 },
  { title: 'URI(API路径)', dataIndex: 'uri', width: 200 },
  { title: '请求方法', dataIndex: 'method', width: 100, align: 'center' as const },
  { title: '权限标识', dataIndex: 'permission', width: 200 },
  { title: '资源类型', dataIndex: 'type', width: 100, align: 'center' as const },
  { title: '载体', dataIndex: 'carrierKind', width: 120, align: 'center' as const },
  { title: '排序', dataIndex: 'sort', width: 80, align: 'center' as const },
  { title: '图标', dataIndex: 'icon', width: 60, align: 'center' as const },
  { title: '操作', dataIndex: 'action', width: 160, fixed: 'right' as const, align: 'center' as const }
]

// 所有列定义
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

// 重置列顺序
function resetColumnOrder() {
  allColumns.value = [...INITIAL_COLUMNS]
  draggableColumns.value = [...INITIAL_COLUMNS]
  showColumnKeys.value = INITIAL_COLUMNS
    .filter(col => typeof col.dataIndex === 'string' && col.dataIndex)
    .map(col => col.dataIndex)
}

// 拖拽结束
function onDragEnd() {}

// 计算最终表格列（去除序号，树形结构不适合分页序号）
const columns = computed(() => INITIAL_COLUMNS.filter(col => showColumnKeys.value.includes(col.dataIndex)))

// 多选配置
const rowSelection = computed(() => ({
  selectedRowKeys: selectedRowKeys.value,
  onChange: (selectedKeys: (string|number)[]) => {
    selectedRowKeys.value = selectedKeys.map(String)
  },
  checkStrictly: true,
  preserveSelectedRowKeys: true,
  fixed: true
}))

// 表格内容区高度自适应
const tableContentRef = ref<HTMLElement | null>(null)
const tableScrollContainerRef = ref<HTMLElement | null>(null)
const tableBodyHeight = ref(400)

function updateTableBodyHeight() {
  nextTick(() => {
    if (tableContentRef.value) {
      const tableHeader = tableContentRef.value.querySelector('.ant-table-header') as HTMLElement
      const containerHeight = tableContentRef.value.clientHeight
      const tableHeaderHeight = tableHeader ? tableHeader.clientHeight : 55
      const bodyHeight = containerHeight - tableHeaderHeight
      tableBodyHeight.value = Math.max(bodyHeight, 200)
    }
  })
}

// 递归补全 leaf 字段，保证所有节点都有 leaf: true/false
function fixLeafAndChildren(nodes: ResourceItem[]) {
  if (!Array.isArray(nodes)) return
  nodes.forEach(node => {
    // children 必须为数组
    if (!Array.isArray(node.children)) {
      node.children = []
    }
    // leaf 字段补全
    node.leaf = node.children.length === 0
    // 递归处理子节点
    if (node.children.length > 0) {
      fixLeafAndChildren(node.children)
    }
  })
}

function resolveRuntimePagePath() {
  const currentPath = window?.location?.pathname
  if (currentPath && currentPath !== '/') {
    return currentPath
  }
  return '/system/resource'
}

async function loadRuntimeUiActions() {
  if (!canReadResourceManagement.value) {
    runtimeUiActionsLoaded.value = false
    runtimeUiActionPermissions.value = new Set()
    return
  }
  try {
    const actions = await getRuntimeUiActions(resolveRuntimePagePath())
    runtimeUiActionPermissions.value = new Set(
      (actions || [])
        .map((action) => action.permission)
        .filter((permission): permission is string => Boolean(permission)),
    )
    runtimeUiActionsLoaded.value = true
  } catch (error) {
    console.error('加载运行时按钮载体失败:', error)
    runtimeUiActionsLoaded.value = false
    runtimeUiActionPermissions.value = new Set()
  }
}

// 加载树形数据
async function loadData() {
  if (!canReadResourceManagement.value) {
    tableData.value = []
    return
  }
  loading.value = true
  try {
    await loadRuntimeUiActions()
    const res = await getResourceTree()
    fixLeafAndChildren(res) // 递归修正 leaf 和 children 字段
    removeEmptyChildren(res) // 递归删除空 children 字段
    tableData.value = Array.isArray(res) ? res : []
  } catch (error) {
    console.error('加载资源树数据失败:', error)
    tableData.value = []
  } finally {
    loading.value = false
  }
}

// 查询
function handleSearch() {
  loadData()
}
const throttledSearch = handleSearch

// 重置
function handleReset() {
  query.value = { name: '', title: '', uri: '', permission: '', type: undefined }
  loadData()
}
const throttledReset = handleReset

// 刷新
async function handleRefresh() {
  refreshing.value = true
  loading.value = true
  await loadData().catch((error) => {
    console.error('刷新数据失败:', error)
  }).finally(() => {
    setTimeout(() => {
      refreshing.value = false
    }, 1000)
    loading.value = false
  })
}
const throttledRefresh = handleRefresh

// 新建
function handleCreate() {
  if (!canCreateResourceManagement.value) {
    message.warning('缺少资源创建权限')
    return
  }
  drawerMode.value = 'create'
  currentResource.value = { name: '', title: '', type: ResourceType.API, sort: 0 }
  drawerVisible.value = true
}
const throttledCreate = handleCreate

// 编辑
function handleEdit(record: any) {
  if (!canUpdateResourceManagement.value) {
    message.warning('缺少资源更新权限')
    return
  }
  drawerMode.value = 'edit'
  currentResource.value = { ...record }
  drawerVisible.value = true
}
const throttledEdit = handleEdit

// 删除
function handleDelete(record: any) {
  if (!canDeleteResourceManagement.value) {
    message.warning('缺少资源删除权限')
    return
  }
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除资源 ${record.title} 吗？`,
    okText: '确认',
    cancelText: '取消',
    onOk: () => {
      return deleteResource(record.id).then(() => {
        message.success('删除成功')
        loadData()
      }).catch((error: any) => {
        message.error('删除资源失败: ' + (error.message || '未知错误'))
        return Promise.reject(error)
      })
    }
  })
}
const throttledDelete = handleDelete

// 批量删除
function handleBatchDelete() {
  if (!canBatchDeleteResourceManagement.value) {
    message.warning('缺少资源批量删除权限')
    return
  }
  if (selectedRowKeys.value.length === 0) {
    message.warning('请先选择要删除的资源')
    return
  }
  Modal.confirm({
    title: '确认批量删除',
    content: `确定要删除选中的 ${selectedRowKeys.value.length} 个资源吗？`,
    okText: '确认',
    cancelText: '取消',
    onOk: () => {
      return batchDeleteResources(selectedRowKeys.value)
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
const throttledBatchDelete = handleBatchDelete

// 清除选择
function clearSelection() {
  selectedRowKeys.value = []
}

// 行点击事件
function onCustomRow(record: any) {
  return {
    onClick: (event: MouseEvent) => {
      // 排除点击复选框
      if ((event.target as HTMLElement).closest('.ant-checkbox-wrapper')) return
      // 排除点击自定义展开/折叠图标，防止点击展开时触发行选中
      if ((event.target as HTMLElement).closest('.custom-expand-icon')) return
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

// 抽屉相关
const drawerVisible = ref(false)
const drawerMode = ref<'create' | 'edit'>('create')
const currentResource = ref<ResourceItem | null>(null)

// 抽屉关闭
function handleDrawerClose() {
  drawerVisible.value = false
  currentResource.value = null
}

// 保存（新建/编辑）
async function handleFormSubmit(formData: any) {
  if (drawerMode.value === 'edit' && !canUpdateResourceManagement.value) {
    message.warning('缺少资源更新权限')
    return
  }
  if (drawerMode.value === 'create' && !canCreateResourceManagement.value) {
    message.warning('缺少资源创建权限')
    return
  }
  try {
    if (drawerMode.value === 'edit' && formData.id) {
      await updateResource(formData.id, formData)
      message.success('更新成功')
    } else {
      await createResource(formData)
      message.success('创建成功')
    }
    handleDrawerClose()
    loadData()
  } catch (error: any) {
    message.error('保存失败: ' + (error.message || '未知错误'))
  }
}

function handleActiveScopeChanged() {
  if (canReadResourceManagement.value) {
    loadData()
  }
}

// 生命周期钩子
onMounted(() => {
  if (canReadResourceManagement.value) {
    loadData()
  }
  updateTableBodyHeight()
  window.addEventListener('resize', updateTableBodyHeight)
  window.addEventListener(ACTIVE_SCOPE_CHANGED_EVENT, handleActiveScopeChanged)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateTableBodyHeight)
  window.removeEventListener(ACTIVE_SCOPE_CHANGED_EVENT, handleActiveScopeChanged)
})
watch(canReadResourceManagement, (enabled) => {
  if (enabled) {
    loadData()
    return
  }
  runtimeUiActionsLoaded.value = false
  runtimeUiActionPermissions.value = new Set()
  tableData.value = []
  selectedRowKeys.value = []
})

// 获取请求方法颜色
function getMethodColor(method: string) {
  const colorMap: Record<string, string> = {
    'GET': 'green',
    'POST': 'blue',
    'PUT': 'orange',
    'DELETE': 'red',
    'PATCH': 'purple'
  }
  return colorMap[method?.toUpperCase()] || 'default'
}

// 获取资源类型颜色
function getTypeColor(type: number) {
  const colorMap: Record<number, string> = {
    [ResourceType.DIRECTORY]: 'orange',
    [ResourceType.MENU]: 'blue',
    [ResourceType.BUTTON]: 'green',
    [ResourceType.API]: 'orange'
  }
  return colorMap[type] || 'default'
}

// 获取资源类型文本
function getTypeText(type: number) {
  const textMap: Record<number, string> = {
    [ResourceType.DIRECTORY]: '目录',
    [ResourceType.MENU]: '菜单',
    [ResourceType.BUTTON]: '按钮',
    [ResourceType.API]: 'API'
  }
  return textMap[type] || '未知'
}

function getCarrierKindText(carrierKind?: ResourceItem['carrierKind'], type?: number) {
  if (carrierKind === 'menu') return 'menu'
  if (carrierKind === 'ui_action') return 'ui_action'
  if (carrierKind === 'api_endpoint') return 'api_endpoint'
  if (type === ResourceType.DIRECTORY || type === ResourceType.MENU) return 'menu'
  if (type === ResourceType.BUTTON) return 'ui_action'
  if (type === ResourceType.API) return 'api_endpoint'
  return 'unknown'
}

function getCarrierKindColor(carrierKind?: ResourceItem['carrierKind']) {
  const colorMap: Record<string, string> = {
    menu: 'blue',
    ui_action: 'green',
    api_endpoint: 'purple',
  }
  return colorMap[carrierKind || ''] || 'default'
}

function removeEmptyChildren(nodes: ResourceItem[]) {
  if (!Array.isArray(nodes)) return
  nodes.forEach(node => {
    if (Array.isArray(node.children)) {
      if (node.children.length === 0) {
        delete node.children // 删除空 children 字段
      } else {
        removeEmptyChildren(node.children)
      }
    }
  })
}
</script>

<style scoped>
/* 复用用户管理页面样式，保证风格一致 */
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

.table-container {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}

.carrier-transition-note {
  padding: 10px 24px;
  font-size: 13px;
  color: #595959;
  background: #fafafa;
  border-bottom: 1px solid #f0f0f0;
}

.table-scroll-container {
  min-height: 0;
  overflow: auto;
}

.pagination-container {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  background: #fff;
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
</style> 
