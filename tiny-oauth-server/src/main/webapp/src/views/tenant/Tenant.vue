<template>
  <div class="content-container" style="position: relative;">
    <div class="content-card">
      <div v-if="!canRead" class="platform-guard-card">
        <div class="platform-guard-kicker">Platform Only</div>
        <h3>租户管理仅对平台管理员开放</h3>
        <p>
          当前页面属于平台级控制面。只有默认平台租户下具备
          <code>system:tenant:list</code> 权限的用户才可查看租户管理。
        </p>
      </div>

      <template v-else>
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
              <a-select-option value="true">启用</a-select-option>
              <a-select-option value="false">禁用</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="生命周期">
            <a-select v-model:value="query.lifecycleStatus" allow-clear placeholder="全部">
              <a-select-option value="ACTIVE">运行中</a-select-option>
              <a-select-option value="FROZEN">已冻结</a-select-option>
              <a-select-option value="DECOMMISSIONED">已下线</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="包含已删除">
            <a-switch v-model:checked="query.includeDeleted" />
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
          <div v-if="selectedRowKeys.length > 0 && canDelete" class="batch-actions">
            <a-button type="primary" danger @click="throttledBatchDelete" class="toolbar-btn">
              <template #icon>
                <DeleteOutlined />
              </template>
              批量删除 ({{ selectedRowKeys.length }})
            </a-button>
            <a-button @click="clearSelection" class="toolbar-btn">取消选择</a-button>
          </div>
          <a-button
            v-if="canInitializePlatformTemplate"
            type="link"
            @click="throttledInitializePlatformTemplate"
            class="toolbar-btn"
          >
            初始化平台模板
          </a-button>
          <a-button v-if="canCreate" type="link" @click="throttledCreate" class="toolbar-btn">
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
              <template v-else-if="column.dataIndex === 'lifecycleStatus'">
                <a-tag :color="lifecycleStatusColor(record.lifecycleStatus)">
                  {{ lifecycleStatusLabel(record.lifecycleStatus) }}
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
                  <a-button
                    type="link"
                    size="small"
                    @click.stop="throttledViewDetail(record)"
                    class="action-btn"
                  >
                    详情
                  </a-button>
                  <a-button
                    v-if="canEdit && record.lifecycleStatus !== 'DECOMMISSIONED'"
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
                  <a-button
                    v-if="canFreeze && record.lifecycleStatus === 'ACTIVE'"
                    type="link"
                    size="small"
                    @click.stop="throttledFreeze(record)"
                    class="action-btn"
                  >
                    冻结
                  </a-button>
                  <a-button
                    v-if="canUnfreeze && record.lifecycleStatus === 'FROZEN'"
                    type="link"
                    size="small"
                    @click.stop="throttledUnfreeze(record)"
                    class="action-btn"
                  >
                    解冻
                  </a-button>
                  <a-button
                    v-if="canDecommission && record.lifecycleStatus !== 'DECOMMISSIONED'"
                    type="link"
                    size="small"
                    danger
                    @click.stop="throttledDecommission(record)"
                    class="action-btn"
                  >
                    下线
                  </a-button>
                  <a-button
                    v-if="canDelete"
                    type="link"
                    size="small"
                    danger
                    @click.stop="throttledDelete(record)"
                    class="action-btn"
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
      </template>
    </div>

    <a-drawer
      v-if="canRead"
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

    <a-drawer
      v-if="canRead"
      v-model:open="detailVisible"
      title="租户详情"
      width="520"
      :get-container="false"
      :style="{ position: 'absolute' }"
      @close="handleDetailClose"
    >
      <div v-if="detailLoading" class="detail-loading">加载中...</div>
      <div v-else-if="detailTenant" class="tenant-detail">
        <div class="tenant-detail-grid">
          <div class="tenant-detail-item"><span class="label">租户编码</span><span>{{ detailTenant.code || '-' }}</span></div>
          <div class="tenant-detail-item"><span class="label">租户名称</span><span>{{ detailTenant.name || '-' }}</span></div>
          <div class="tenant-detail-item"><span class="label">生命周期</span><span>{{ lifecycleStatusLabel(detailTenant.lifecycleStatus) }}</span></div>
          <div class="tenant-detail-item"><span class="label">启用状态</span><span>{{ detailTenant.enabled ? '启用' : '禁用' }}</span></div>
          <div class="tenant-detail-item"><span class="label">域名</span><span>{{ detailTenant.domain || '-' }}</span></div>
          <div class="tenant-detail-item"><span class="label">套餐</span><span>{{ detailTenant.planCode || '-' }}</span></div>
          <div class="tenant-detail-item"><span class="label">到期时间</span><span>{{ formatDateTime(detailTenant.expiresAt) }}</span></div>
          <div class="tenant-detail-item"><span class="label">最大用户数</span><span>{{ detailTenant.maxUsers ?? '-' }}</span></div>
          <div class="tenant-detail-item"><span class="label">存储配额(GB)</span><span>{{ detailTenant.maxStorageGb ?? '-' }}</span></div>
          <div class="tenant-detail-item"><span class="label">联系人</span><span>{{ detailTenant.contactName || '-' }}</span></div>
          <div class="tenant-detail-item"><span class="label">联系电话</span><span>{{ detailTenant.contactPhone || '-' }}</span></div>
          <div class="tenant-detail-item"><span class="label">联系邮箱</span><span>{{ detailTenant.contactEmail || '-' }}</span></div>
          <div class="tenant-detail-item full"><span class="label">备注</span><span>{{ detailTenant.remark || '-' }}</span></div>
          <div class="tenant-detail-item"><span class="label">创建时间</span><span>{{ formatDateTime(detailTenant.createdAt) }}</span></div>
          <div class="tenant-detail-item"><span class="label">更新时间</span><span>{{ formatDateTime(detailTenant.updatedAt) }}</span></div>
        </div>
      </div>
      <div v-else class="detail-empty">暂无租户详情</div>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick, computed, watch } from 'vue'
import { message, Modal } from 'ant-design-vue'
import type { ColumnsType } from 'ant-design-vue/es/table'
import type { Key } from 'ant-design-vue/es/_util/type'
import { PlusOutlined, ReloadOutlined, DeleteOutlined, EditOutlined } from '@ant-design/icons-vue'
import { useAuth } from '@/auth/auth'
import {
  tenantList,
  getTenantById,
  createTenant,
  updateTenant,
  deleteTenant,
  initializePlatformTemplate,
  freezeTenant,
  unfreezeTenant,
  decommissionTenant,
} from '@/api/tenant'
import { getRuntimeUiActions } from '@/api/resource'
import { extractAuthoritiesFromJwt } from '@/utils/jwt'
import {
  TENANT_MANAGEMENT_READ_AUTHORITIES,
} from '@/constants/permission'
import { getTenantCode } from '@/utils/tenant'
import TenantForm from './TenantForm.vue'

const query = ref({
  code: '',
  name: '',
  domain: '',
  enabled: undefined as string | undefined,
  lifecycleStatus: undefined as string | undefined,
  includeDeleted: false,
})

const loading = ref(false)
const tableData = ref<any[]>([])
const { user } = useAuth()
const authorities = computed(() => new Set(extractAuthoritiesFromJwt(user.value?.access_token)))
const isPlatformTenant = computed(() => getTenantCode() === 'default')

function hasAnyAuthority(requiredAuthorities: string[]) {
  return isPlatformTenant.value && requiredAuthorities.some((authority) => authorities.value.has(authority))
}

const canRead = computed(() => hasAnyAuthority(TENANT_MANAGEMENT_READ_AUTHORITIES))
const runtimeUiActionPermissions = ref<Set<string>>(new Set())
const runtimeUiActionsLoaded = ref(false)

function hasRuntimeUiAction(permission: string) {
  if (!runtimeUiActionsLoaded.value) {
    return false
  }
  return runtimeUiActionPermissions.value.has(permission)
}

const canCreate = computed(() => hasRuntimeUiAction('system:tenant:create'))
const canEdit = computed(() => hasRuntimeUiAction('system:tenant:edit'))
const canInitializePlatformTemplate = computed(() => hasRuntimeUiAction('system:tenant:template:initialize'))
const canDelete = computed(() => hasRuntimeUiAction('system:tenant:delete'))
const canFreeze = computed(() => hasRuntimeUiAction('system:tenant:freeze'))
const canUnfreeze = computed(() => hasRuntimeUiAction('system:tenant:unfreeze'))
const canDecommission = computed(() => hasRuntimeUiAction('system:tenant:decommission'))

const pagination = ref({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  pageSizeOptions: ['10', '20', '50', '100'],
  showTotal: (total: number) => `共 ${total} 条`
})

const columns: ColumnsType<any> = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '租户编码', dataIndex: 'code', key: 'code', width: 140 },
  { title: '租户名称', dataIndex: 'name', key: 'name', width: 180 },
  { title: '域名', dataIndex: 'domain', key: 'domain', width: 220 },
  { title: '套餐', dataIndex: 'planCode', key: 'planCode', width: 120 },
  { title: '到期时间', dataIndex: 'expiresAt', key: 'expiresAt', width: 180 },
  { title: '最大用户数', dataIndex: 'maxUsers', key: 'maxUsers', width: 120 },
  { title: '存储配额(GB)', dataIndex: 'maxStorageGb', key: 'maxStorageGb', width: 140 },
  { title: '生命周期', dataIndex: 'lifecycleStatus', key: 'lifecycleStatus', width: 120 },
  { title: '启用', dataIndex: 'enabled', key: 'enabled', width: 100 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 180 },
  { title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt', width: 180 },
  { title: '操作', dataIndex: 'action', key: 'action', width: 320, fixed: 'right' as const }
]

const selectedRowKeys = ref<string[]>([])
const rowSelection = computed(() => {
  if (!canDelete.value) {
    return undefined
  }
  return {
    selectedRowKeys: selectedRowKeys.value,
    onChange: (keys: Key[]) => {
      selectedRowKeys.value = keys.map(String)
    },
  }
})

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
  if (!canRead.value) {
    tableData.value = []
    pagination.value.total = 0
    runtimeUiActionsLoaded.value = false
    runtimeUiActionPermissions.value = new Set()
    loading.value = false
    return
  }
  loading.value = true
  try {
    await loadRuntimeUiActions()
    const params = {
      code: query.value.code.trim(),
      name: query.value.name.trim(),
      domain: query.value.domain.trim(),
      enabled: query.value.enabled === undefined ? undefined : query.value.enabled === 'true',
      lifecycleStatus: query.value.lifecycleStatus || undefined,
      includeDeleted: query.value.includeDeleted,
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

function resolveRuntimePagePath() {
  const currentPath = window?.location?.pathname
  if (currentPath && currentPath !== '/') {
    return currentPath
  }
  return '/system/tenant'
}

async function loadRuntimeUiActions() {
  if (!canRead.value) {
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
    console.error('加载租户页运行时按钮载体失败:', error)
    runtimeUiActionsLoaded.value = false
    runtimeUiActionPermissions.value = new Set()
  }
}

function handleSearch() {
  if (!canRead.value) return
  pagination.value.current = 1
  loadData()
}
const throttledSearch = handleSearch

function handleReset() {
  if (!canRead.value) return
  query.value.code = ''
  query.value.name = ''
  query.value.domain = ''
  query.value.enabled = undefined
  query.value.lifecycleStatus = undefined
  query.value.includeDeleted = false
  pagination.value.current = 1
  loadData()
}
const throttledReset = handleReset

const refreshing = ref(false)
async function handleRefresh() {
  if (!canRead.value) return
  refreshing.value = true
  await loadData().finally(() => {
    setTimeout(() => {
      refreshing.value = false
    }, 800)
  })
}
const throttledRefresh = handleRefresh

function handleInitializePlatformTemplate() {
  if (!canInitializePlatformTemplate.value) {
    message.warning('缺少平台模板初始化权限')
    return
  }
  Modal.confirm({
    title: '确认初始化平台模板',
    content: '该操作会在平台模板缺失时，从配置的平台租户回填角色和资源模板；若模板已存在，则不会重复写入。',
    okText: '确认',
    cancelText: '取消',
    onOk: () => {
      return initializePlatformTemplate()
        .then((result) => {
          message.success(result.message || (result.initialized ? '平台模板初始化成功' : '平台模板已存在'))
        })
        .catch((error: any) => {
          message.error('平台模板初始化失败: ' + (error?.message || '未知错误'))
          return Promise.reject(error)
        })
    },
  })
}
const throttledInitializePlatformTemplate = handleInitializePlatformTemplate

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
  if (!canDelete.value) {
    message.warning('缺少租户删除权限')
    return
  }
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
  if (!canDelete.value) {
    message.warning('缺少租户删除权限')
    return
  }
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

function handleFreeze(record: any) {
  if (!canFreeze.value) {
    message.warning('缺少租户冻结权限')
    return
  }
  Modal.confirm({
    title: '确认冻结',
    content: `确定要冻结租户 ${record.name} 吗？冻结后将禁止该租户登录和租户内写操作。`,
    okText: '确认',
    cancelText: '取消',
    onOk: () => {
      return freezeTenant(record.id)
        .then(() => {
          message.success('冻结成功')
          loadData()
        })
        .catch((error: any) => {
          message.error('冻结失败: ' + (error?.message || '未知错误'))
          return Promise.reject(error)
        })
    },
  })
}
const throttledFreeze = handleFreeze

function handleUnfreeze(record: any) {
  if (!canUnfreeze.value) {
    message.warning('缺少租户解冻权限')
    return
  }
  Modal.confirm({
    title: '确认解冻',
    content: `确定要解冻租户 ${record.name} 吗？`,
    okText: '确认',
    cancelText: '取消',
    onOk: () => {
      return unfreezeTenant(record.id)
        .then(() => {
          message.success('解冻成功')
          loadData()
        })
        .catch((error: any) => {
          message.error('解冻失败: ' + (error?.message || '未知错误'))
          return Promise.reject(error)
        })
    },
  })
}
const throttledUnfreeze = handleUnfreeze

function handleDecommission(record: any) {
  if (!canDecommission.value) {
    message.warning('缺少租户下线权限')
    return
  }
  Modal.confirm({
    title: '确认下线',
    content: `确定要下线租户 ${record.name} 吗？下线后将停用租户，且不再允许恢复为运行态。`,
    okText: '确认',
    cancelText: '取消',
    okButtonProps: { danger: true },
    onOk: () => {
      return decommissionTenant(record.id)
        .then(() => {
          message.success('下线成功')
          loadData()
        })
        .catch((error: any) => {
          message.error('下线失败: ' + (error?.message || '未知错误'))
          return Promise.reject(error)
        })
    },
  })
}
const throttledDecommission = handleDecommission

const drawerVisible = ref(false)
const drawerMode = ref<'create' | 'edit'>('create')
const currentTenant = ref<any | null>(null)
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailTenant = ref<any | null>(null)

function handleCreate() {
  if (!canCreate.value) {
    message.warning('缺少租户创建权限')
    return
  }
  drawerMode.value = 'create'
  currentTenant.value = { id: '', enabled: true, initialAdminNickname: '租户管理员' }
  drawerVisible.value = true
}
const throttledCreate = handleCreate

function handleEdit(record: any) {
  if (!canEdit.value) {
    message.warning('缺少租户编辑权限')
    return
  }
  drawerMode.value = 'edit'
  currentTenant.value = { ...record }
  drawerVisible.value = true
}
const throttledEdit = handleEdit

function handleDrawerClose() {
  drawerVisible.value = false
  currentTenant.value = null
}

async function handleViewDetail(record: any) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    detailTenant.value = await getTenantById(record.id)
  } catch {
    detailTenant.value = { ...record }
    message.warning('详情接口不可用，已回退展示列表快照')
  } finally {
    detailLoading.value = false
  }
}
const throttledViewDetail = handleViewDetail

function handleDetailClose() {
  detailVisible.value = false
  detailTenant.value = null
}

async function handleFormSubmit(formData: any) {
  if (drawerMode.value === 'edit' ? !canEdit.value : !canCreate.value) {
    message.warning(drawerMode.value === 'edit' ? '缺少租户编辑权限' : '缺少租户创建权限')
    return
  }
  try {
    if (drawerMode.value === 'edit' && formData.id) {
      await updateTenant(formData.id, formData)
      message.success('更新成功')
    } else {
      await createTenant(formData)
      message.success(`创建成功，初始管理员：${formData.initialAdminUsername}`)
    }
    handleDrawerClose()
    loadData()
  } catch (error: any) {
    message.error('保存失败: ' + (error?.message || '未知错误'))
  }
}

function lifecycleStatusLabel(status: string | null | undefined): string {
  switch (status) {
    case 'FROZEN':
      return '已冻结'
    case 'DECOMMISSIONED':
      return '已下线'
    case 'ACTIVE':
    default:
      return '运行中'
  }
}

function lifecycleStatusColor(status: string | null | undefined): string {
  switch (status) {
    case 'FROZEN':
      return 'orange'
    case 'DECOMMISSIONED':
      return 'red'
    case 'ACTIVE':
    default:
      return 'green'
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
  if (!canDelete.value) {
    return {}
  }
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
  updateTableBodyHeight()
  window.addEventListener('resize', updateTableBodyHeight)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateTableBodyHeight)
})

watch(() => pagination.value.pageSize, () => {
  updateTableBodyHeight()
})

watch(canRead, (enabled) => {
  if (!enabled) {
    tableData.value = []
    selectedRowKeys.value = []
    pagination.value.total = 0
    drawerVisible.value = false
    currentTenant.value = null
    detailVisible.value = false
    detailTenant.value = null
    runtimeUiActionsLoaded.value = false
    runtimeUiActionPermissions.value = new Set()
    return
  }
  void loadData()
}, { immediate: true })
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

.platform-guard-card {
  margin: 24px;
  padding: 24px;
  border: 1px solid #f0f0f0;
  border-radius: 12px;
  background: linear-gradient(135deg, #fffaf0 0%, #fff 100%);
}

.platform-guard-kicker {
  color: #b26a00;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
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
  flex-wrap: wrap;
  gap: 6px;
}

.checkbox-selected-row td {
  background-color: #e6f7ff !important;
}

.tenant-detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px 24px;
}

.tenant-detail-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.tenant-detail-item.full {
  grid-column: 1 / -1;
}

.tenant-detail-item .label {
  color: #8c8c8c;
  font-size: 12px;
}

.detail-loading,
.detail-empty {
  color: #8c8c8c;
}
</style>
