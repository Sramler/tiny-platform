<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  CloseOutlined,
  ColumnHeightOutlined,
  CopyOutlined,
  DownloadOutlined,
  EyeOutlined,
  HolderOutlined,
  PoweroffOutlined,
  ReloadOutlined,
  SettingOutlined,
} from '@ant-design/icons-vue'
import VueDraggable from 'vuedraggable'
import { useAuth } from '@/auth/auth'
import { extractAuthoritiesFromJwt } from '@/utils/jwt'
import {
  getPlatformTenantUserDetail,
  listPlatformTenantUsers,
  type PlatformTenantUserDetail,
  type PlatformTenantUserListItem,
} from '@/api/platform-tenant-user'
import { getTenantById, tenantList, type Tenant } from '@/api/tenant'
import {
  TENANT_MANAGEMENT_READ_AUTHORITIES,
  USER_MANAGEMENT_READ_AUTHORITIES,
} from '@/constants/permission'
import { usePlatformScope } from '@/composables/usePlatformScope'

type TableDensity = 'default' | 'middle' | 'small'
type ColumnAlign = 'left' | 'center' | 'right'
type FixedSide = 'left' | 'right'
type TenantUserColumnKey =
  | 'id'
  | 'username'
  | 'nickname'
  | 'enabled'
  | 'accountNonExpired'
  | 'accountNonLocked'
  | 'lockStatus'
  | 'lockRemainingMinutes'
  | 'failedLoginCount'
  | 'lastFailedLoginAt'
  | 'credentialsNonExpired'
  | 'lastLoginAt'
  | 'action'

type TableColumnConfig<T extends string> = {
  title: string
  dataIndex: T
  sorter?: boolean
  width?: number
  minWidth?: number
  maxWidth?: number
  resizable?: boolean
  fixed?: FixedSide
  align?: ColumnAlign
}

const TENANT_USER_INITIAL_COLUMNS: Array<TableColumnConfig<TenantUserColumnKey>> = [
  { title: '用户 ID', dataIndex: 'id', sorter: true, width: 120 },
  { title: '用户名', dataIndex: 'username', sorter: true, width: 180 },
  { title: '昵称', dataIndex: 'nickname', sorter: true, width: 160 },
  { title: '是否启用', dataIndex: 'enabled', sorter: true, width: 120 },
  { title: '账号未过期', dataIndex: 'accountNonExpired', sorter: true, width: 130 },
  { title: '账号未锁定', dataIndex: 'accountNonLocked', sorter: true, width: 130 },
  { title: '锁定状态', dataIndex: 'lockStatus', width: 120 },
  { title: '剩余锁定时间', dataIndex: 'lockRemainingMinutes', width: 140 },
  { title: '失败登录次数', dataIndex: 'failedLoginCount', sorter: true, width: 140 },
  { title: '最后失败时间', dataIndex: 'lastFailedLoginAt', sorter: true, width: 180 },
  { title: '密码未过期', dataIndex: 'credentialsNonExpired', sorter: true, width: 130 },
  { title: '最后登录时间', dataIndex: 'lastLoginAt', sorter: true, width: 180 },
  { title: '操作', dataIndex: 'action', width: 120, fixed: 'right', align: 'center' },
]

const { user, fetchWithAuth } = useAuth()
const { isPlatformScope } = usePlatformScope()
const route = useRoute()
const router = useRouter()
const authorities = computed(() => new Set(extractAuthoritiesFromJwt(user.value?.access_token)))

const tenantEntries = ref<Tenant[]>([])
const tenantLoading = ref(false)
const tenantUserLoading = ref(false)
const tenantUserDetailLoading = ref(false)
const tenantUserExporting = ref(false)
const tenantUserExportingAsync = ref(false)
const cellCopyEnabled = ref(false)
const showSortTooltip = ref(true)
const tenantUserDetailVisible = ref(false)
const activeTenantUserDetail = ref<PlatformTenantUserDetail | null>(null)
const selectedStewardshipTenant = ref<Tenant | null>(null)
const tenantUserQuery = ref({
  username: '',
  nickname: '',
})
const tenantUserPagination = ref({
  current: 1,
  pageSize: 10,
  showSizeChanger: true,
  pageSizeOptions: ['10', '20', '30', '40', '50'],
  total: 0,
  showTotal: (total: number) => `共 ${total} 条`,
})
const tenantUserTableData = ref<PlatformTenantUserListItem[]>([])
const selectedRowKeys = ref<Array<string | number>>([])
const tableDensity = ref<TableDensity>('default')
const draggableColumns = ref<TableColumnConfig<TenantUserColumnKey>[]>([...TENANT_USER_INITIAL_COLUMNS])
const visibleColumns = ref<TenantUserColumnKey[]>(TENANT_USER_INITIAL_COLUMNS.map((item) => item.dataIndex))
const zebraStripeEnabled = ref(true)
const tableContentRef = ref<HTMLElement | null>(null)
const paginationRef = ref<HTMLElement | null>(null)
const tableBodyHeight = ref(400)

function normalizeTenantRecord(tenant: Tenant | Record<string, any>): Tenant {
  return {
    id: Number(tenant.id),
    code: typeof tenant.code === 'string' ? tenant.code : '',
    name: typeof tenant.name === 'string' ? tenant.name : '',
    domain: typeof tenant.domain === 'string' ? tenant.domain : undefined,
    enabled: typeof tenant.enabled === 'boolean' ? tenant.enabled : undefined,
    lifecycleStatus: typeof tenant.lifecycleStatus === 'string' ? tenant.lifecycleStatus : undefined,
  }
}

function hasAnyAuthority(requiredAuthorities: string[]) {
  return requiredAuthorities.some((authority) => authorities.value.has(authority))
}

const canReadTenants = computed(() => hasAnyAuthority(TENANT_MANAGEMENT_READ_AUTHORITIES))
const canReadTenantUsers = computed(() => hasAnyAuthority(USER_MANAGEMENT_READ_AUTHORITIES))
const canAccessTenantStewardship = computed(() =>
  isPlatformScope.value && canReadTenants.value && canReadTenantUsers.value,
)

const tenantSelectOptions = computed(() => {
  const mergedTenants = new Map<number, Tenant>()
  if (selectedStewardshipTenant.value?.id) {
    mergedTenants.set(selectedStewardshipTenant.value.id, normalizeTenantRecord(selectedStewardshipTenant.value))
  }
  tenantEntries.value.forEach((tenant) => {
    const normalized = normalizeTenantRecord(tenant)
    if (normalized.id > 0) {
      mergedTenants.set(normalized.id, normalized)
    }
  })
  return Array.from(mergedTenants.values()).map((tenant) => ({
    value: tenant.id,
    label: tenant.code ? `${tenant.name || `租户 ${tenant.id}`} (${tenant.code})` : tenant.name || `租户 ${tenant.id}`,
  }))
})

const selectedStewardshipTenantId = computed<number | undefined>({
  get: () => selectedStewardshipTenant.value?.id,
  set: (value) => {
    void handleStewardshipTenantChange(value)
  },
})

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

const columnOptions = TENANT_USER_INITIAL_COLUMNS.map((column) => ({
  key: column.dataIndex,
  label: column.title,
}))

const columns = computed(() => {
  const filtered = draggableColumns.value.filter((column) => visibleColumns.value.includes(column.dataIndex))
  return [
    {
      title: '序号',
      dataIndex: 'index',
      width: 80,
      align: 'center' as const,
      fixed: 'left' as const,
      resizable: false,
    },
    ...normalizeColumns(filtered),
  ]
})

function resolveRequestedTab() {
  return route.query.tab === 'tenantStewardship' ? 'tenantStewardship' : 'platformUsers'
}

function buildTenantStewardshipReturnPath(tenantId: number) {
  return `/platform/users?tab=tenantStewardship&tenantId=${tenantId}`
}

function syncTenantStewardshipRoute(tenantId?: number | null) {
  router.replace({
    path: '/platform/users',
    query: tenantId
      ? {
          tab: 'tenantStewardship',
          tenantId: String(tenantId),
        }
      : {
          tab: 'tenantStewardship',
        },
  })
}

async function resolveStewardshipTenant(tenantId: number) {
  const localTenant = tenantEntries.value.find((tenant) => tenant.id === tenantId)
  if (localTenant) {
    return normalizeTenantRecord(localTenant)
  }
  return normalizeTenantRecord(await getTenantById(tenantId))
}

async function restoreTenantStewardshipFromRoute() {
  if (resolveRequestedTab() !== 'tenantStewardship' || !canReadTenants.value) {
    return
  }
  const requestedTenantId = Number(route.query.tenantId)
  if (!Number.isInteger(requestedTenantId) || requestedTenantId <= 0) {
    return
  }
  try {
    selectedStewardshipTenant.value = await resolveStewardshipTenant(requestedTenantId)
    tenantUserPagination.value.current = 1
    await loadStewardshipUsers()
  } catch (error: any) {
    message.warning(error?.message || '无法恢复租户代管上下文，请重新选择租户')
  }
}

function getApiBaseUrl() {
  const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:9000'
  return apiBaseUrl.endsWith('/') ? apiBaseUrl.slice(0, -1) : apiBaseUrl
}

async function extractResponseMessage(response: Response, fallback: string) {
  try {
    const data = await response.json()
    if (typeof data?.message === 'string' && data.message.trim()) {
      return data.message
    }
    if (typeof data?.detail === 'string' && data.detail.trim()) {
      return data.detail
    }
    if (typeof data?.error === 'string' && data.error.trim()) {
      return data.error
    }
  } catch {
    // ignore non-json responses
  }
  return fallback
}

function formatDateTime(value?: string | null) {
  if (!value) {
    return '-'
  }
  try {
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) {
      return '-'
    }
    return date.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false,
    })
  } catch {
    return '-'
  }
}

function displayValue(value?: string | number | null) {
  if (value === null || value === undefined) {
    return '-'
  }
  const text = String(value).trim()
  return text ? text : '-'
}

function yesNoLabel(value?: boolean) {
  return value ? '是' : '否'
}

function enabledLabel(value?: boolean) {
  return value ? '启用' : '禁用'
}

function enabledColor(value?: boolean) {
  return value ? 'green' : 'red'
}

function lockStatusLabel(detail?: PlatformTenantUserDetail | PlatformTenantUserListItem | Record<string, any> | null) {
  if (detail?.temporarilyLocked) {
    return '临时锁定'
  }
  if (detail?.accountNonLocked === false) {
    return '管理员锁定'
  }
  return '正常'
}

function lockStatusColor(detail?: PlatformTenantUserDetail | PlatformTenantUserListItem | Record<string, any> | null) {
  if (detail?.temporarilyLocked) {
    return 'orange'
  }
  if (detail?.accountNonLocked === false) {
    return 'red'
  }
  return 'green'
}

function normalizeColumns<T extends string>(columnDefs: Array<TableColumnConfig<T>>) {
  return columnDefs.map((column) => ({
    ...column,
    width: Number(column.width) > 0 ? Number(column.width) : 160,
    minWidth: Number(column.minWidth) > 0 ? Number(column.minWidth) : 100,
    maxWidth: Number(column.maxWidth) > 0 ? Number(column.maxWidth) : 640,
    resizable: column.resizable ?? column.dataIndex !== 'action',
  }))
}

function updateVisibleColumns<T extends string>(current: T[], key: T, checked: boolean, fallbackMessage: string) {
  if (checked) {
    return current.includes(key) ? current : [...current, key]
  }
  if (current.length <= 1) {
    message.warning(fallbackMessage)
    return current
  }
  return current.filter((item) => item !== key)
}

function handleColumnChange(columnKey: TenantUserColumnKey, checked: boolean) {
  visibleColumns.value = updateVisibleColumns(visibleColumns.value, columnKey, checked, '租户用户列表至少保留一列')
}

function handleCheckAllChange(event: any) {
  if (event?.target?.checked) {
    visibleColumns.value = draggableColumns.value.map((column) => column.dataIndex)
    return
  }
  const firstColumn = TENANT_USER_INITIAL_COLUMNS[0]
  if (firstColumn) {
    visibleColumns.value = [firstColumn.dataIndex]
  }
}

function resetColumnOrder() {
  draggableColumns.value = [...TENANT_USER_INITIAL_COLUMNS]
  visibleColumns.value = TENANT_USER_INITIAL_COLUMNS.map((column) => column.dataIndex)
}

function onDragEnd() {
  // v-model on VueDraggable keeps order in sync.
}

function handleResizeColumn(width: number, column: { dataIndex?: unknown }) {
  const dataIndex = typeof column?.dataIndex === 'string' ? column.dataIndex : undefined
  if (!dataIndex || !visibleColumns.value.includes(dataIndex as TenantUserColumnKey)) {
    return
  }
  const normalizedWidth = Math.max(100, Math.min(Number(width) || 160, 800))
  draggableColumns.value = draggableColumns.value.map((item) =>
    item.dataIndex === (dataIndex as TenantUserColumnKey) ? { ...item, width: normalizedWidth } : item,
  )
}

function handleDensityMenuClick({ key }: { key: string | number }) {
  const nextKey = String(key)
  if (nextKey === 'default' || nextKey === 'middle' || nextKey === 'small') {
    tableDensity.value = nextKey as TableDensity
    updateTableBodyHeight()
  }
}

function handleUnsupportedExport(scopeLabel: string, asyncMode = false) {
  const actionLabel = asyncMode ? '异步导出' : '导出'
  message.warning(`${scopeLabel}暂不支持${actionLabel}`)
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
      return
    }
  } catch {
    // ignore fallback errors
  }
  message.error('复制失败，请手动复制')
}

function handleCellCopy(value: unknown, columnTitle: string) {
  if (!cellCopyEnabled.value) {
    return
  }
  const textToCopy = value !== null && value !== undefined ? String(value) : ''
  if (!textToCopy.trim()) {
    message.warning('单元格内容为空，无法复制')
    return
  }
  if (navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard
      .writeText(textToCopy)
      .then(() => message.success(`已复制 ${columnTitle || '单元格'}：${textToCopy}`))
      .catch(() => fallbackCopy(textToCopy, columnTitle))
    return
  }
  fallbackCopy(textToCopy, columnTitle)
}

const rowSelection = computed(() => ({
  selectedRowKeys: selectedRowKeys.value,
  onChange: (keys: Array<string | number>) => {
    selectedRowKeys.value = [...keys]
  },
  checkStrictly: false,
  preserveSelectedRowKeys: true,
  fixed: true,
}))

function getRowClassName(record: PlatformTenantUserListItem, index: number) {
  if (selectedRowKeys.value.some((selectedKey) => String(selectedKey) === String(record.id))) {
    return 'checkbox-selected-row'
  }
  if (!zebraStripeEnabled.value) {
    return ''
  }
  return index % 2 === 0 ? 'table-row-even' : 'table-row-odd'
}

function toggleSingleRowSelection(rowId: string | number) {
  const isSelected = selectedRowKeys.value.some((selectedKey) => String(selectedKey) === String(rowId))
  if (isSelected && selectedRowKeys.value.length === 1) {
    selectedRowKeys.value = []
    return
  }
  selectedRowKeys.value = [rowId]
}

function onCustomRow(record: PlatformTenantUserListItem) {
  return {
    onClick: (event: MouseEvent) => {
      if ((event.target as HTMLElement).closest('.ant-checkbox-wrapper')) {
        return
      }
      toggleSingleRowSelection(record.id)
    },
  }
}

function clearSelection() {
  selectedRowKeys.value = []
}

function updateTableBodyHeight() {
  nextTick(() => {
    if (tableContentRef.value && paginationRef.value) {
      const tableHeader = tableContentRef.value.querySelector('.ant-table-header') as HTMLElement | null
      const containerHeight = tableContentRef.value.clientHeight
      const paginationHeight = paginationRef.value.clientHeight
      const tableHeaderHeight = tableHeader ? tableHeader.clientHeight : 55
      const bodyHeight = containerHeight - paginationHeight - tableHeaderHeight
      tableBodyHeight.value = Math.max(bodyHeight, 200)
    }
  })
}

async function loadTenantEntries() {
  if (!isPlatformScope.value || !canReadTenants.value) {
    tenantEntries.value = []
    return
  }
  tenantLoading.value = true
  try {
    const result = await tenantList({
      page: 0,
      size: 200,
    })
    tenantEntries.value = Array.isArray(result.content) ? result.content.map((tenant) => normalizeTenantRecord(tenant)) : []
  } catch (error: any) {
    message.error(error?.message || '租户列表加载失败')
  } finally {
    tenantLoading.value = false
    updateTableBodyHeight()
  }
}

async function handleStewardshipTenantChange(value?: string | number | null) {
  if (value === undefined || value === null || value === '') {
    clearStewardshipTenant()
    return
  }
  const tenantId = Number(value)
  if (!Number.isInteger(tenantId) || tenantId <= 0) {
    message.warning('请选择有效租户')
    return
  }
  try {
    selectedStewardshipTenant.value = await resolveStewardshipTenant(tenantId)
    tenantUserPagination.value.current = 1
    selectedRowKeys.value = []
    syncTenantStewardshipRoute(tenantId)
    await loadStewardshipUsers()
  } catch (error: any) {
    message.error(error?.message || '加载租户上下文失败')
  }
}

async function refreshTenantStewardshipView() {
  await loadTenantEntries()
  if (selectedStewardshipTenant.value?.id) {
    await loadStewardshipUsers()
  }
}

async function loadStewardshipUsers() {
  const tenantId = selectedStewardshipTenant.value?.id
  if (!tenantId || tenantId <= 0 || !canAccessTenantStewardship.value) {
    tenantUserTableData.value = []
    tenantUserPagination.value.total = 0
    return
  }
  tenantUserLoading.value = true
  try {
    const result = await listPlatformTenantUsers({
      tenantId,
      current: tenantUserPagination.value.current,
      pageSize: tenantUserPagination.value.pageSize,
      username: tenantUserQuery.value.username || undefined,
      nickname: tenantUserQuery.value.nickname || undefined,
    })
    tenantUserTableData.value = result.records
    tenantUserPagination.value.total = result.total
  } catch (error: any) {
    message.error(error?.message || '租户代管用户列表加载失败')
  } finally {
    tenantUserLoading.value = false
    updateTableBodyHeight()
  }
}

function handleTenantUserSearch() {
  if (!selectedStewardshipTenant.value?.id) {
    message.warning('请先选择租户')
    return
  }
  tenantUserPagination.value.current = 1
  selectedRowKeys.value = []
  void loadStewardshipUsers()
}

function clearStewardshipTenant(options?: { resetFilters?: boolean }) {
  selectedStewardshipTenant.value = null
  tenantUserTableData.value = []
  tenantUserPagination.value.total = 0
  selectedRowKeys.value = []
  tenantUserPagination.value.current = 1
  if (options?.resetFilters) {
    tenantUserQuery.value = {
      username: '',
      nickname: '',
    }
  }
  syncTenantStewardshipRoute()
}

function handleTenantUserReset() {
  clearStewardshipTenant({ resetFilters: true })
}

function clearStewardshipTenantClick() {
  clearStewardshipTenant()
}

function handleTenantUserPageChange(page: number, pageSize: number) {
  tenantUserPagination.value.current = page
  tenantUserPagination.value.pageSize = pageSize
  selectedRowKeys.value = []
  void loadStewardshipUsers()
}

function handleTableChange(pag: { current?: number; pageSize?: number }) {
  if (typeof pag?.current === 'number') {
    tenantUserPagination.value.current = pag.current
  }
  if (typeof pag?.pageSize === 'number') {
    tenantUserPagination.value.pageSize = pag.pageSize
  }
  selectedRowKeys.value = []
  void loadStewardshipUsers()
}

async function openTenantDetail(tenantRecord: Tenant | Record<string, any>) {
  const tenant = normalizeTenantRecord(tenantRecord)
  if (!tenant.id || tenant.id <= 0) {
    message.warning('缺少有效租户 ID，暂无法进入平台详情')
    return
  }
  try {
    const response = await fetchWithAuth(`${getApiBaseUrl()}/sys/tenants/${tenant.id}`, {
      method: 'GET',
      headers: {
        Accept: 'application/json',
      },
    })
    if (!response.ok) {
      const fallback =
        response.status === 403 ? '当前会话暂不可查看该租户的平台详情' : '加载租户详情前置校验失败'
      message.warning(await extractResponseMessage(response, fallback))
      return
    }
    router.push({
      path: `/platform/tenants/${tenant.id}`,
      query: {
        from: buildTenantStewardshipReturnPath(tenant.id),
      },
    })
  } catch (error: any) {
    message.error(error?.message || '进入平台租户详情失败')
  }
}

async function showTenantUserDetail(record: PlatformTenantUserListItem | Record<string, any>) {
  const tenantId = selectedStewardshipTenant.value?.id
  const userId = Number(record?.id)
  if (!tenantId || !Number.isInteger(userId) || userId <= 0) {
    message.warning('缺少租户或用户上下文，暂无法查看详情')
    return
  }
  tenantUserDetailVisible.value = true
  tenantUserDetailLoading.value = true
  activeTenantUserDetail.value = null
  try {
    activeTenantUserDetail.value = await getPlatformTenantUserDetail(tenantId, userId)
  } catch (error: any) {
    tenantUserDetailVisible.value = false
    message.error(error?.message || '租户用户详情加载失败')
  } finally {
    tenantUserDetailLoading.value = false
  }
}

onMounted(() => {
  void loadTenantEntries().then(() => restoreTenantStewardshipFromRoute())
  updateTableBodyHeight()
  window.addEventListener('resize', updateTableBodyHeight)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateTableBodyHeight)
})

watch(
  () => [tenantUserPagination.value.pageSize, tableDensity.value, selectedStewardshipTenant.value?.id],
  () => {
    updateTableBodyHeight()
  },
)

defineExpose({
  handleStewardshipTenantChange,
  clearStewardshipTenant,
  showTenantUserDetail,
  openTenantDetail,
  tenantSelectOptions,
  selectedStewardshipTenant,
  columnOptions,
})
</script>

<template>
  <div class="tab-workspace">
    <div class="form-container">
      <a-form layout="inline" :model="tenantUserQuery">
        <a-form-item label="目标租户">
          <a-select
            v-model:value="selectedStewardshipTenantId"
            allow-clear
            show-search
            option-filter-prop="label"
            style="width: 320px"
            placeholder="请选择租户"
            :options="tenantSelectOptions"
            :loading="tenantLoading"
          />
        </a-form-item>
        <a-form-item label="用户名">
          <a-input v-model:value="tenantUserQuery.username" placeholder="请输入用户名" />
        </a-form-item>
        <a-form-item label="昵称">
          <a-input v-model:value="tenantUserQuery.nickname" placeholder="请输入昵称" />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" @click="handleTenantUserSearch">搜索</a-button>
          <a-button class="ml-2" @click="handleTenantUserReset">重置</a-button>
        </a-form-item>
      </a-form>
    </div>

    <div class="toolbar-container">
      <div class="table-title">租户侧用户列表</div>
      <div class="table-actions">
        <div v-if="selectedRowKeys.length > 0" class="batch-actions">
          <a-button @click="clearSelection" class="toolbar-btn">
            <template #icon>
              <CloseOutlined />
            </template>
            取消选择
          </a-button>
        </div>
        <a-button v-if="selectedStewardshipTenant" @click="openTenantDetail(selectedStewardshipTenant)">
          平台详情
        </a-button>
        <a-button v-if="selectedStewardshipTenant" @click="clearStewardshipTenantClick">清空租户</a-button>
        <a-tooltip title="刷新">
          <span class="action-icon" @click="refreshTenantStewardshipView">
            <ReloadOutlined :spin="tenantLoading || tenantUserLoading" />
          </span>
        </a-tooltip>
        <a-tooltip :title="showSortTooltip ? '关闭排序提示' : '开启排序提示'">
          <PoweroffOutlined :class="['action-icon', { active: showSortTooltip }]" @click="showSortTooltip = !showSortTooltip" />
        </a-tooltip>
        <a-tooltip :title="cellCopyEnabled ? '关闭单元格复制' : '开启单元格复制'">
          <CopyOutlined :class="['action-icon', { active: cellCopyEnabled }]" @click="cellCopyEnabled = !cellCopyEnabled" />
        </a-tooltip>
        <a-dropdown placement="bottomRight" trigger="click">
          <a-tooltip title="表格密度">
            <ColumnHeightOutlined class="action-icon" />
          </a-tooltip>
          <template #overlay>
            <a-menu @click="handleDensityMenuClick" :selected-keys="[tableDensity]">
              <a-menu-item key="default">默认</a-menu-item>
              <a-menu-item key="middle">中等</a-menu-item>
              <a-menu-item key="small">紧凑</a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
        <a-popover placement="bottomRight" trigger="click" :destroyTooltipOnHide="false">
          <template #content>
            <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px;">
              <div style="display: flex; align-items: center;">
                <a-checkbox
                  :checked="visibleColumns.length === draggableColumns.length"
                  :indeterminate="visibleColumns.length > 0 && visibleColumns.length < draggableColumns.length"
                  @change="(e: any) => handleCheckAllChange(e)"
                />
                <span style="font-weight: bold; margin-left: 8px;">列展示/排序</span>
              </div>
              <span style="font-weight: bold; color: #1677ff; cursor: pointer;" @click="resetColumnOrder">重置</span>
            </div>
            <VueDraggable
              v-model="draggableColumns"
              :item-key="(item: any) => item?.dataIndex || `tenant_col_${Math.random()}`"
              handle=".drag-handle"
              @end="onDragEnd"
              class="draggable-columns"
              ghost-class="sortable-ghost"
              chosen-class="sortable-chosen"
              tag="div"
            >
              <template #item="{ element: col }">
                <div class="draggable-column-item">
                  <HolderOutlined class="drag-handle" />
                  <a-checkbox
                    :checked="visibleColumns.includes(col.dataIndex)"
                    @change="(e: any) => handleColumnChange(col.dataIndex, e.target.checked)"
                  >
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

    <div class="table-container tenant-stewardship-table-container" ref="tableContentRef">
      <div class="table-scroll-container">
        <div v-if="!canReadTenants" class="table-empty-state">
          当前会话缺少 <code>system:tenant:list</code> 或 <code>system:tenant:view</code>，因此无法选择租户。
        </div>

        <div v-else-if="!selectedStewardshipTenant" class="table-empty-state">
          请选择租户后查看租户侧用户列表。
        </div>

        <div v-else-if="!canReadTenantUsers" class="table-empty-state">
          当前会话缺少 <code>system:user:list</code> 或 <code>system:user:view</code>，因此无法加载租户用户列表。
        </div>

        <template v-else>
          <a-table
            class="selection-enabled-table"
            :columns="columns"
            :data-source="tenantUserTableData"
            :loading="tenantUserLoading"
            :pagination="false"
            :row-key="(record: PlatformTenantUserListItem) => String(record.id)"
            bordered
            :size="tableDensity === 'default' ? undefined : tableDensity"
            :row-selection="rowSelection"
            :custom-row="onCustomRow"
            :row-class-name="getRowClassName"
            :scroll="{ x: 1500, y: tableBodyHeight }"
            :locale="tableLocale"
            :show-sorter-tooltip="showSortTooltip"
            @change="handleTableChange"
            @resizeColumn="handleResizeColumn"
          >
            <template #bodyCell="{ column, record, index }">
              <template v-if="column.dataIndex === 'index'">
                {{
                  ((Number(tenantUserPagination.current) || 1) - 1) *
                  (Number(tenantUserPagination.pageSize) || 10) +
                  index + 1
                }}
              </template>
              <template
                v-else-if="
                  ['enabled', 'accountNonExpired', 'accountNonLocked', 'credentialsNonExpired'].includes(
                    String(column.dataIndex || ''),
                  )
                "
              >
                <a-tag :color="record[String(column.dataIndex || '')] ? 'green' : 'red'">
                  {{ record[String(column.dataIndex || '')] ? '是' : '否' }}
                </a-tag>
              </template>
              <template v-else-if="column.dataIndex === 'lockStatus'">
                <a-tag :color="lockStatusColor(record)">
                  {{ lockStatusLabel(record) }}
                </a-tag>
              </template>
              <template v-else-if="column.dataIndex === 'lockRemainingMinutes'">
                <span v-if="record.temporarilyLocked && record.lockRemainingMinutes">
                  约 {{ record.lockRemainingMinutes }} 分钟
                </span>
                <span v-else>-</span>
              </template>
              <template v-else-if="['lastLoginAt', 'lastFailedLoginAt'].includes(String(column.dataIndex || ''))">
                {{ formatDateTime(record[String(column.dataIndex || '')]) }}
              </template>
              <template v-else-if="column.dataIndex === 'action'">
                <div class="action-buttons">
                  <a-button type="link" size="small" class="action-btn" @click.stop="showTenantUserDetail(record)">
                    <template #icon>
                      <EyeOutlined />
                    </template>
                    详情
                  </a-button>
                </div>
              </template>
              <template v-else>
                <template v-if="cellCopyEnabled && column.dataIndex && column.dataIndex !== 'action'">
                  <span class="cell-text">{{ record[String(column.dataIndex || '')] ?? '-' }}</span>
                  <CopyOutlined
                    class="cell-copy-icon"
                    @click.stop="handleCellCopy(record[String(column.dataIndex || '')], String(column.title || ''))"
                  />
                </template>
                <span v-else-if="column.dataIndex" class="cell-text">{{ record[String(column.dataIndex || '')] ?? '-' }}</span>
                <span v-else>-</span>
              </template>
            </template>
          </a-table>

          <div v-if="tenantUserTableData.length === 0 && !tenantUserLoading" class="table-empty-state">
            当前租户下暂无匹配的用户记录。
          </div>
        </template>
      </div>

      <div class="pagination-container" ref="paginationRef">
        <div class="pagination-left">
          <div class="export-group">
            <a-button
              type="primary"
              :loading="tenantUserExporting"
              :disabled="!selectedStewardshipTenant || !canReadTenantUsers"
              @click="handleUnsupportedExport('租户侧用户列表')"
              class="export-btn"
            >
              <template #icon>
                <DownloadOutlined />
              </template>
              导出当前页
            </a-button>
            <a-button
              :loading="tenantUserExportingAsync"
              :disabled="!selectedStewardshipTenant || !canReadTenantUsers"
              @click="handleUnsupportedExport('租户侧用户列表', true)"
              class="export-btn"
            >
              <template #icon>
                <DownloadOutlined />
              </template>
              导出全部（异步）
            </a-button>
          </div>
        </div>
        <a-pagination
          v-model:current="tenantUserPagination.current"
          :page-size="tenantUserPagination.pageSize"
          :total="tenantUserPagination.total"
          :show-size-changer="tenantUserPagination.showSizeChanger"
          :page-size-options="tenantUserPagination.pageSizeOptions"
          :show-total="tenantUserPagination.showTotal"
          :locale="{ items_per_page: '条/页' }"
          @change="handleTenantUserPageChange"
          @showSizeChange="handleTenantUserPageChange"
        />
      </div>
    </div>

    <a-drawer
      v-model:open="tenantUserDetailVisible"
      title="租户用户详情"
      width="42%"
      :get-container="false"
      :style="{ position: 'absolute' }"
    >
      <div class="drawer-body">
        <div class="drawer-intro">
          <div class="drawer-kicker">Tenant Stewardship</div>
          <p>当前详情由平台桥接接口返回，只读取目标租户用户信息，不切换当前会话的 active scope。</p>
        </div>
        <a-spin :spinning="tenantUserDetailLoading">
          <template v-if="activeTenantUserDetail">
            <div class="drawer-summary">
              <a-tag :color="enabledColor(activeTenantUserDetail.enabled)">
                账号{{ enabledLabel(activeTenantUserDetail.enabled) }}
              </a-tag>
              <a-tag :color="lockStatusColor(activeTenantUserDetail)">
                {{ lockStatusLabel(activeTenantUserDetail) }}
              </a-tag>
              <a-tag :color="activeTenantUserDetail.credentialsNonExpired ? 'green' : 'red'">
                凭据{{ activeTenantUserDetail.credentialsNonExpired ? '有效' : '已过期' }}
              </a-tag>
            </div>

            <section class="drawer-section">
              <div class="drawer-section-header">
                <h4>租户用户身份</h4>
                <p>目标租户下的基础身份标识。</p>
              </div>
              <div class="detail-grid">
                <div class="detail-field">
                  <span class="detail-label">用户 ID</span>
                  <span class="detail-value">{{ displayValue(activeTenantUserDetail.id) }}</span>
                </div>
                <div class="detail-field">
                  <span class="detail-label">用户名</span>
                  <span class="detail-value">{{ displayValue(activeTenantUserDetail.username) }}</span>
                </div>
                <div class="detail-field">
                  <span class="detail-label">昵称</span>
                  <span class="detail-value">{{ displayValue(activeTenantUserDetail.nickname) }}</span>
                </div>
              </div>
            </section>

            <section class="drawer-section">
              <div class="drawer-section-header">
                <h4>账号安全状态</h4>
                <p>平台侧代管排障时，优先查看这一组状态。</p>
              </div>
              <div class="detail-grid">
                <div class="detail-field">
                  <span class="detail-label">账号启用</span>
                  <span class="detail-value">{{ yesNoLabel(activeTenantUserDetail.enabled) }}</span>
                </div>
                <div class="detail-field">
                  <span class="detail-label">账号未过期</span>
                  <span class="detail-value">{{ yesNoLabel(activeTenantUserDetail.accountNonExpired) }}</span>
                </div>
                <div class="detail-field">
                  <span class="detail-label">账号未锁定</span>
                  <span class="detail-value">{{ yesNoLabel(activeTenantUserDetail.accountNonLocked) }}</span>
                </div>
                <div class="detail-field">
                  <span class="detail-label">凭据未过期</span>
                  <span class="detail-value">{{ yesNoLabel(activeTenantUserDetail.credentialsNonExpired) }}</span>
                </div>
                <div class="detail-field">
                  <span class="detail-label">临时锁定</span>
                  <span class="detail-value">{{ yesNoLabel(activeTenantUserDetail.temporarilyLocked) }}</span>
                </div>
                <div class="detail-field">
                  <span class="detail-label">剩余锁定分钟</span>
                  <span class="detail-value">{{ displayValue(activeTenantUserDetail.lockRemainingMinutes) }}</span>
                </div>
              </div>
            </section>

            <section class="drawer-section">
              <div class="drawer-section-header">
                <h4>活动轨迹</h4>
                <p>登录与失败尝试信息。</p>
              </div>
              <div class="detail-grid">
                <div class="detail-field">
                  <span class="detail-label">最近登录</span>
                  <span class="detail-value">{{ displayValue(activeTenantUserDetail.lastLoginAt) }}</span>
                </div>
                <div class="detail-field">
                  <span class="detail-label">最近失败登录</span>
                  <span class="detail-value">{{ displayValue(activeTenantUserDetail.lastFailedLoginAt) }}</span>
                </div>
                <div class="detail-field">
                  <span class="detail-label">失败次数</span>
                  <span class="detail-value">{{ displayValue(activeTenantUserDetail.failedLoginCount ?? 0) }}</span>
                </div>
              </div>
            </section>
          </template>
        </a-spin>
      </div>
    </a-drawer>
  </div>
</template>

<style scoped>
.tab-workspace {
  display: flex;
  flex: 1;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: transparent;
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
  gap: 12px;
  padding: 8px 24px;
  border-bottom: 1px solid #f0f0f0;
  background: transparent;
  border-radius: 0;
  box-shadow: none;
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

.table-container {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.table-scroll-container {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  overflow: auto;
  padding-bottom: 12px;
}

.table-empty-state {
  padding: 32px 24px;
  color: #8c8c8c;
  text-align: center;
  border-top: 1px solid #fafafa;
}

.ml-2 {
  margin-left: 8px;
}

.pagination-container {
  position: sticky;
  bottom: 0;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 24px;
  min-height: 56px;
  border-top: 1px solid #f0f0f0;
  background: #fff;
  box-shadow: none;
}

.pagination-left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 32px;
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

.export-group .export-btn[type='primary'] {
  background: #1890ff;
  color: #fff;
  border-color: #1890ff;
}

.export-group .export-btn[type='primary']:hover {
  background: #40a9ff;
  border-color: #40a9ff;
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

:deep(.selection-enabled-table .ant-table-tbody > tr) {
  cursor: pointer;
}

:deep(.ant-table-tbody > tr > td) {
  white-space: nowrap;
}

:deep(.selection-enabled-table .ant-table-tbody > tr > td) {
  transition: background-color 0.2s ease;
}

:deep(.selection-enabled-table .ant-table-tbody > tr:hover > td) {
  background: #f5f9ff;
}

:deep(.selection-enabled-table .ant-table-tbody > tr.checkbox-selected-row > td) {
  background: #e6f4ff !important;
}

:deep(.selection-enabled-table .ant-table-tbody > tr.checkbox-selected-row:hover > td) {
  background: #d4ebff !important;
}

:deep(.selection-enabled-table .ant-table-tbody > tr .ant-checkbox-wrapper) {
  pointer-events: auto;
}

:deep(.selection-enabled-table .ant-table-tbody > tr.table-row-even > td) {
  background: #fafbfc;
}

:deep(.selection-enabled-table .ant-table-tbody > tr.table-row-odd > td) {
  background: #fff;
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

.drawer-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.drawer-intro {
  padding: 16px;
  border: 1px solid #e6f4ff;
  border-radius: 12px;
  background: linear-gradient(135deg, #f7fbff 0%, #ffffff 100%);
}

.drawer-kicker {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #1677ff;
}

.drawer-intro p {
  margin: 8px 0 0;
  color: #595959;
  line-height: 1.7;
}

.drawer-summary {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.drawer-section {
  padding: 16px;
  border: 1px solid #f0f0f0;
  border-radius: 12px;
  background: #fff;
}

.drawer-section-header {
  margin-bottom: 16px;
}

.drawer-section-header h4 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #1f1f1f;
}

.drawer-section-header p {
  margin: 6px 0 0;
  color: #8c8c8c;
  line-height: 1.6;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.detail-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px;
  border-radius: 10px;
  background: #fafafa;
}

.detail-label {
  font-size: 12px;
  color: #8c8c8c;
}

.detail-value {
  font-size: 14px;
  font-weight: 500;
  color: #1f1f1f;
  line-height: 1.6;
  word-break: break-word;
}

.tenant-stewardship-table-container {
  min-height: 320px;
}

:deep(.ant-table-body) {
  scrollbar-width: none;
  -ms-overflow-style: none;
}

:deep(.ant-table-body::-webkit-scrollbar) {
  display: none;
}

:deep(.ant-table-tbody > tr > td) {
  position: relative;
  overflow: visible;
}

:deep(.ant-table-tbody > tr > td .cell-text) {
  width: 100%;
  box-sizing: border-box;
  min-width: 0;
  max-width: 100%;
}

:deep(.ant-table-thead > tr > th) {
  white-space: nowrap;
  text-overflow: clip;
  overflow: visible;
}

:deep(.ant-table-cell-resize-handle) {
  cursor: col-resize;
}

@media (max-width: 960px) {
  .form-container,
  .toolbar-container,
  .pagination-container {
    padding-left: 16px;
    padding-right: 16px;
  }

  .toolbar-container,
  .pagination-container {
    align-items: flex-start;
    flex-direction: column;
  }

  .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
