<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  CloseOutlined,
  ColumnHeightOutlined,
  CopyOutlined,
  DownloadOutlined,
  EyeOutlined,
  HolderOutlined,
  PlusOutlined,
  PoweroffOutlined,
  ReloadOutlined,
  SettingOutlined,
} from '@ant-design/icons-vue'
import VueDraggable from 'vuedraggable'
import { useAuth } from '@/auth/auth'
import { extractAuthoritiesFromJwt } from '@/utils/jwt'
import {
  createPlatformUser,
  getPlatformUserDetail,
  getPlatformUserRoles,
  listPlatformUsers,
  replacePlatformUserRoles,
  updatePlatformUserStatus,
  type PlatformUserCreatePayload,
  type PlatformUserDetail,
  type PlatformUserListItem,
  type PlatformUserRole,
  type PlatformUserStatus,
} from '@/api/platform-user'
import { listPlatformRoleOptions, type PlatformRoleOption } from '@/api/platform-role'
import { listPlatformRoleAssignmentRequests } from '@/api/platform-role-approval'
import {
  PLATFORM_ROLE_APPROVAL_PAGE_AUTHORITIES,
  PLATFORM_USER_MANAGEMENT_CREATE_AUTHORITIES,
  PLATFORM_USER_MANAGEMENT_READ_AUTHORITIES,
  PLATFORM_USER_MANAGEMENT_UPDATE_AUTHORITIES,
} from '@/constants/permission'
import { usePlatformScope } from '@/composables/usePlatformScope'

type TableDensity = 'default' | 'middle' | 'small'
type ColumnAlign = 'left' | 'center' | 'right'
type FixedSide = 'left' | 'right'
type PlatformUserColumnKey =
  | 'userId'
  | 'username'
  | 'displayName'
  | 'nickname'
  | 'userEnabled'
  | 'platformStatus'
  | 'hasPlatformRoleAssignment'
  | 'updatedAt'
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

const PLATFORM_USER_INITIAL_COLUMNS: Array<TableColumnConfig<PlatformUserColumnKey>> = [
  { title: '用户 ID', dataIndex: 'userId', sorter: true, width: 120 },
  { title: '用户名', dataIndex: 'username', sorter: true, width: 180 },
  { title: '展示名', dataIndex: 'displayName', sorter: true, width: 180 },
  { title: '昵称', dataIndex: 'nickname', sorter: true, width: 160 },
  { title: '是否启用', dataIndex: 'userEnabled', sorter: true, width: 120 },
  { title: '平台档案状态', dataIndex: 'platformStatus', sorter: true, width: 140 },
  { title: '平台角色绑定', dataIndex: 'hasPlatformRoleAssignment', width: 140 },
  { title: '更新时间', dataIndex: 'updatedAt', sorter: true, width: 180 },
  { title: '操作', dataIndex: 'action', width: 220, fixed: 'right', align: 'center' },
]

const { user } = useAuth()
const { isPlatformScope } = usePlatformScope()
const router = useRouter()
const authorities = computed(() => new Set(extractAuthoritiesFromJwt(user.value?.access_token)))

const query = ref<{
  keyword: string
  enabled?: boolean
  status?: PlatformUserStatus
}>({
  keyword: '',
  enabled: undefined,
  status: undefined,
})

const tableData = ref<PlatformUserListItem[]>([])
const loading = ref(false)
const detailLoading = ref(false)
const saving = ref(false)
const exporting = ref(false)
const exportingAsync = ref(false)
const cellCopyEnabled = ref(false)
const showSortTooltip = ref(true)
const detailVisible = ref(false)
const createVisible = ref(false)
const activeDetail = ref<PlatformUserDetail | null>(null)
const platformRoleOptions = ref<PlatformRoleOption[]>([])
const roleEditorVisible = ref(false)
const roleEditorLoading = ref(false)
const roleEditorSaving = ref(false)
const roleEditorUserId = ref<number | null>(null)
const selectedRoleIds = ref<number[]>([])
const pendingApprovalTotal = ref(0)
const createForm = ref<PlatformUserCreatePayload>({
  userId: 0,
  displayName: '',
  status: 'ACTIVE',
})
const pagination = ref({
  current: 1,
  pageSize: 10,
  showSizeChanger: true,
  pageSizeOptions: ['10', '20', '30', '40', '50'],
  total: 0,
  showTotal: (total: number) => `共 ${total} 条`,
})
const selectedRowKeys = ref<Array<string | number>>([])
const tableDensity = ref<TableDensity>('default')
const draggableColumns = ref<TableColumnConfig<PlatformUserColumnKey>[]>([...PLATFORM_USER_INITIAL_COLUMNS])
const visibleColumns = ref<PlatformUserColumnKey[]>(PLATFORM_USER_INITIAL_COLUMNS.map((item) => item.dataIndex))
const zebraStripeEnabled = ref(true)
const queryEnabledValue = computed<string | undefined>({
  get: () => {
    if (query.value.enabled === undefined) {
      return undefined
    }
    return query.value.enabled ? 'true' : 'false'
  },
  set: (value) => {
    if (value === undefined) {
      query.value.enabled = undefined
      return
    }
    query.value.enabled = value === 'true'
  },
})

const tableContentRef = ref<HTMLElement | null>(null)
const paginationRef = ref<HTMLElement | null>(null)
const tableBodyHeight = ref(400)

function hasAnyAuthority(requiredAuthorities: string[]) {
  return requiredAuthorities.some((authority) => authorities.value.has(authority))
}

const canRead = computed(() => hasAnyAuthority(PLATFORM_USER_MANAGEMENT_READ_AUTHORITIES))
const canCreate = computed(() => hasAnyAuthority(PLATFORM_USER_MANAGEMENT_CREATE_AUTHORITIES))
const canUpdate = computed(() => hasAnyAuthority(PLATFORM_USER_MANAGEMENT_UPDATE_AUTHORITIES))
const canEditPlatformUserRoles = computed(() => canUpdate.value)
const canViewApprovalCenter = computed(() => hasAnyAuthority(PLATFORM_ROLE_APPROVAL_PAGE_AUTHORITIES))
const canAccessPlatformUsers = computed(() => isPlatformScope.value && canRead.value)

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

function statusLabel(status?: string) {
  return status === 'DISABLED' ? '已禁用' : '启用中'
}

function statusColor(status?: string) {
  return status === 'DISABLED' ? 'red' : 'green'
}

function enabledLabel(value?: boolean) {
  return value ? '启用' : '禁用'
}

function enabledColor(value?: boolean) {
  return value ? 'green' : 'red'
}

function yesNoLabel(value?: boolean) {
  return value ? '是' : '否'
}

function platformRoleBindingLabel(value?: boolean) {
  return value ? '已绑定平台角色' : '缺少平台角色绑定'
}

function platformRoleBindingColor(value?: boolean) {
  return value ? 'blue' : 'default'
}

function roleTagLabel(role: PlatformUserRole) {
  if (role.code && role.name) {
    return `${role.name} (${role.code})`
  }
  return role.name || role.code || `角色#${role.roleId}`
}

function displayValue(value?: string | number | null) {
  if (value === null || value === undefined) {
    return '-'
  }
  const text = String(value).trim()
  return text ? text : '-'
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

function handleColumnChange(columnKey: PlatformUserColumnKey, checked: boolean) {
  visibleColumns.value = updateVisibleColumns(visibleColumns.value, columnKey, checked, '平台用户列表至少保留一列')
}

function handleCheckAllChange(event: any) {
  if (event?.target?.checked) {
    visibleColumns.value = draggableColumns.value.map((column) => column.dataIndex)
    return
  }
  const firstColumn = PLATFORM_USER_INITIAL_COLUMNS[0]
  if (firstColumn) {
    visibleColumns.value = [firstColumn.dataIndex]
  }
}

function resetColumnOrder() {
  draggableColumns.value = [...PLATFORM_USER_INITIAL_COLUMNS]
  visibleColumns.value = PLATFORM_USER_INITIAL_COLUMNS.map((column) => column.dataIndex)
}

function onDragEnd() {
  // v-model on VueDraggable keeps order in sync.
}

function handleResizeColumn(width: number, column: { dataIndex?: unknown }) {
  const dataIndex = typeof column?.dataIndex === 'string' ? column.dataIndex : undefined
  if (!dataIndex || !visibleColumns.value.includes(dataIndex as PlatformUserColumnKey)) {
    return
  }
  const normalizedWidth = Math.max(100, Math.min(Number(width) || 160, 800))
  draggableColumns.value = draggableColumns.value.map((item) =>
    item.dataIndex === (dataIndex as PlatformUserColumnKey) ? { ...item, width: normalizedWidth } : item,
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

function getRowClassName(record: PlatformUserListItem, index: number) {
  if (selectedRowKeys.value.some((selectedKey) => String(selectedKey) === String(record.userId))) {
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

function onCustomRow(record: PlatformUserListItem) {
  return {
    onClick: (event: MouseEvent) => {
      if ((event.target as HTMLElement).closest('.ant-checkbox-wrapper')) {
        return
      }
      toggleSingleRowSelection(record.userId)
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

async function loadData() {
  if (!canAccessPlatformUsers.value) {
    tableData.value = []
    pagination.value.total = 0
    return
  }
  loading.value = true
  try {
    const result = await listPlatformUsers({
      current: pagination.value.current,
      pageSize: pagination.value.pageSize,
      keyword: query.value.keyword || undefined,
      enabled: query.value.enabled,
      status: query.value.status,
    })
    tableData.value = result.records
    pagination.value.total = result.total
  } catch (error: any) {
    message.error(error?.message || '平台用户列表加载失败')
  } finally {
    loading.value = false
    updateTableBodyHeight()
  }
}

async function loadPlatformRoleOptions() {
  try {
    const result = await listPlatformRoleOptions({
      limit: 200,
    })
    const content = Array.isArray(result) ? result : []
    platformRoleOptions.value = content
      .map((item: any) => ({
        roleId: Number(item.roleId),
        code: typeof item.code === 'string' ? item.code : '',
        name: typeof item.name === 'string' ? item.name : '',
        description: typeof item.description === 'string' ? item.description : undefined,
        enabled: typeof item.enabled === 'boolean' ? item.enabled : undefined,
        builtin: typeof item.builtin === 'boolean' ? item.builtin : undefined,
        riskLevel: typeof item.riskLevel === 'string' ? item.riskLevel : undefined,
        approvalMode: typeof item.approvalMode === 'string' ? item.approvalMode : undefined,
      }))
      .filter((role) => Number.isInteger(role.roleId) && role.roleId > 0)
  } catch (error: any) {
    platformRoleOptions.value = []
    message.warning(error?.message || '平台角色候选加载失败')
  }
}

function handleSearch() {
  pagination.value.current = 1
  selectedRowKeys.value = []
  void loadData()
}

function handleReset() {
  query.value = {
    keyword: '',
    enabled: undefined,
    status: undefined,
  }
  pagination.value.current = 1
  selectedRowKeys.value = []
  void loadData()
}

function handlePageChange(page: number, pageSize: number) {
  pagination.value.current = page
  pagination.value.pageSize = pageSize
  selectedRowKeys.value = []
  void loadData()
}

function handleTableChange(pag: { current?: number; pageSize?: number }) {
  if (typeof pag?.current === 'number') {
    pagination.value.current = pag.current
  }
  if (typeof pag?.pageSize === 'number') {
    pagination.value.pageSize = pag.pageSize
  }
  selectedRowKeys.value = []
  void loadData()
}

async function loadPendingApprovalCount(userId: number) {
  pendingApprovalTotal.value = 0
  if (!canViewApprovalCenter.value) {
    return
  }
  try {
    const res = await listPlatformRoleAssignmentRequests({
      targetUserId: userId,
      status: 'PENDING',
      current: 1,
      pageSize: 1,
    })
    pendingApprovalTotal.value = res.total
  } catch {
    pendingApprovalTotal.value = 0
  }
}

async function showDetail(record: PlatformUserListItem | Record<string, any>) {
  const userId = Number(record?.userId)
  if (!Number.isInteger(userId) || userId <= 0) {
    message.warning('缺少有效平台用户上下文，暂无法查看详情')
    return
  }
  detailVisible.value = true
  detailLoading.value = true
  activeDetail.value = null
  pendingApprovalTotal.value = 0
  try {
    const [detail, roles] = await Promise.all([
      getPlatformUserDetail(userId),
      getPlatformUserRoles(userId),
    ])
    activeDetail.value = {
      ...detail,
      roles,
    }
    await loadPendingApprovalCount(userId)
  } catch (error: any) {
    detailVisible.value = false
    message.error(error?.message || '平台用户详情加载失败')
  } finally {
    detailLoading.value = false
  }
}

function goToApprovalQueue(userId: number) {
  router.push({
    path: '/platform/role-assignment-requests',
    query: { targetUserId: String(userId), status: 'PENDING' },
  })
}

function platformRoleOptionLabel(role: PlatformRoleOption) {
  const label = roleTagLabel({
    roleId: role.roleId,
    code: role.code,
    name: role.name,
  } as PlatformUserRole)
  const mode = (role.approvalMode || 'NONE').toUpperCase()
  if (mode === 'ONE_STEP') {
    return `${label} [需审批]`
  }
  return label
}

watch(detailVisible, (open) => {
  if (!open) {
    pendingApprovalTotal.value = 0
  }
})

async function openRoleEditor(userId: number) {
  if (!canEditPlatformUserRoles.value) {
    message.warning('当前会话缺少平台用户更新权限，无法编辑平台角色')
    return
  }
  roleEditorVisible.value = true
  roleEditorUserId.value = userId
  roleEditorLoading.value = true
  try {
    const [assignedRoles] = await Promise.all([
      getPlatformUserRoles(userId),
      platformRoleOptions.value.length > 0 ? Promise.resolve() : loadPlatformRoleOptions(),
    ])
    selectedRoleIds.value = assignedRoles.map((role) => role.roleId)
  } catch (error: any) {
    roleEditorVisible.value = false
    message.error(error?.message || '平台角色编辑数据加载失败')
  } finally {
    roleEditorLoading.value = false
  }
}

async function submitRoleEditor() {
  if (!canUpdate.value) {
    message.warning('当前会话缺少平台用户更新权限')
    return
  }
  if (!roleEditorUserId.value) {
    message.warning('缺少平台用户上下文，暂无法保存角色绑定')
    return
  }
  roleEditorSaving.value = true
  try {
    const updatedRoles = await replacePlatformUserRoles(roleEditorUserId.value, selectedRoleIds.value)
    if (activeDetail.value?.userId === roleEditorUserId.value) {
      activeDetail.value = {
        ...activeDetail.value,
        hasPlatformRoleAssignment: updatedRoles.length > 0,
        roles: updatedRoles,
      }
    }
    const tableTarget = tableData.value.find((item) => item.userId === roleEditorUserId.value)
    if (tableTarget) {
      tableTarget.hasPlatformRoleAssignment = updatedRoles.length > 0
    }
    roleEditorVisible.value = false
    message.success('平台角色绑定已保存')
  } catch (error: any) {
    message.error(error?.message || '平台角色绑定保存失败')
  } finally {
    roleEditorSaving.value = false
  }
}

function openCreateModal() {
  createForm.value = {
    userId: 0,
    displayName: '',
    status: 'ACTIVE',
  }
  createVisible.value = true
}

async function submitCreate() {
  if (!canCreate.value) {
    message.warning('当前会话缺少平台用户创建权限')
    return
  }
  if (!createForm.value.userId || createForm.value.userId <= 0) {
    message.warning('请填写合法的用户 ID')
    return
  }
  saving.value = true
  try {
    await createPlatformUser({
      userId: Number(createForm.value.userId),
      displayName: createForm.value.displayName?.trim() || undefined,
      status: createForm.value.status || 'ACTIVE',
    })
    createVisible.value = false
    pagination.value.current = 1
    await loadData()
    message.success('平台用户档案创建成功')
  } catch (error: any) {
    message.error(error?.message || '平台用户档案创建失败')
  } finally {
    saving.value = false
  }
}

async function toggleStatus(record: PlatformUserListItem | Record<string, any>) {
  if (!canUpdate.value) {
    message.warning('当前会话缺少平台用户更新权限')
    return
  }
  const userId = Number(record?.userId)
  if (!Number.isInteger(userId) || userId <= 0) {
    message.warning('缺少有效平台用户上下文，暂无法更新状态')
    return
  }
  const nextStatus: PlatformUserStatus = record.platformStatus === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  try {
    await updatePlatformUserStatus(userId, nextStatus)
    record.platformStatus = nextStatus
    if (activeDetail.value?.userId === userId) {
      activeDetail.value = {
        ...activeDetail.value,
        platformStatus: nextStatus,
      }
    }
    message.success(`平台用户档案已${nextStatus === 'ACTIVE' ? '启用' : '禁用'}`)
  } catch (error: any) {
    message.error(error?.message || '平台用户状态更新失败')
  }
}

onMounted(() => {
  void loadData()
  updateTableBodyHeight()
  window.addEventListener('resize', updateTableBodyHeight)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateTableBodyHeight)
})

watch(
  () => [pagination.value.pageSize, tableDensity.value],
  () => {
    updateTableBodyHeight()
  },
)

defineExpose({
  showDetail,
  openRoleEditor,
  submitRoleEditor,
})
</script>

<template>
  <div class="tab-workspace">
    <div class="form-container">
      <a-form layout="inline" :model="query">
        <a-form-item label="用户名 / 昵称 / 展示名">
          <a-input v-model:value="query.keyword" placeholder="用户名 / 昵称 / 展示名" />
        </a-form-item>
        <a-form-item label="是否启用">
          <a-select v-model:value="queryEnabledValue" allow-clear style="width: 140px" placeholder="全部">
            <a-select-option value="true">启用</a-select-option>
            <a-select-option value="false">禁用</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="平台档案">
          <a-select v-model:value="query.status" allow-clear style="width: 140px" placeholder="全部">
            <a-select-option value="ACTIVE">ACTIVE</a-select-option>
            <a-select-option value="DISABLED">DISABLED</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item>
          <a-button type="primary" @click="handleSearch">搜索</a-button>
          <a-button class="ml-2" @click="handleReset">重置</a-button>
        </a-form-item>
      </a-form>
    </div>

    <div class="toolbar-container">
      <div class="table-title">用户列表</div>
      <div class="table-actions">
        <div v-if="selectedRowKeys.length > 0" class="batch-actions">
          <a-button @click="clearSelection" class="toolbar-btn">
            <template #icon>
              <CloseOutlined />
            </template>
            取消选择
          </a-button>
        </div>
        <a-button v-if="canCreate" type="link" class="toolbar-btn" @click="openCreateModal">
          <template #icon>
            <PlusOutlined />
          </template>
          新建
        </a-button>
        <a-tooltip title="刷新">
          <span class="action-icon" @click="loadData">
            <ReloadOutlined :spin="loading" />
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
              :item-key="(item: any) => item?.dataIndex || `platform_col_${Math.random()}`"
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

    <div class="table-container" ref="tableContentRef">
      <div class="table-scroll-container">
        <a-table
          class="selection-enabled-table"
          :columns="columns"
          :data-source="tableData"
          :pagination="false"
          :row-key="(record: PlatformUserListItem) => String(record.userId)"
          bordered
          :size="tableDensity === 'default' ? undefined : tableDensity"
          :loading="loading"
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
                ((Number(pagination.current) || 1) - 1) *
                (Number(pagination.pageSize) || 10) +
                index + 1
              }}
            </template>
            <template v-else-if="column.dataIndex === 'userEnabled'">
              <a-tag :color="record.userEnabled ? 'green' : 'red'">
                {{ record.userEnabled ? '是' : '否' }}
              </a-tag>
            </template>
            <template v-else-if="column.dataIndex === 'platformStatus'">
              <a-tag :color="statusColor(record.platformStatus)">
                {{ statusLabel(record.platformStatus) }}
              </a-tag>
            </template>
            <template v-else-if="column.dataIndex === 'hasPlatformRoleAssignment'">
              <a-tag :color="record.hasPlatformRoleAssignment ? 'blue' : 'default'">
                {{ record.hasPlatformRoleAssignment ? '已绑定' : '缺少绑定' }}
              </a-tag>
            </template>
            <template v-else-if="column.dataIndex === 'updatedAt'">
              {{ formatDateTime(record.updatedAt) }}
            </template>
            <template v-else-if="column.dataIndex === 'action'">
              <div class="action-buttons">
                <a-button type="link" size="small" class="action-btn" @click.stop="showDetail(record)">
                  <template #icon>
                    <EyeOutlined />
                  </template>
                  详情
                </a-button>
                <a-button
                  v-if="canEditPlatformUserRoles"
                  type="link"
                  size="small"
                  class="action-btn"
                  @click.stop="openRoleEditor(record.userId)"
                >
                  <template #icon>
                    <SettingOutlined />
                  </template>
                  角色绑定
                </a-button>
                <a-button v-if="canUpdate" type="link" size="small" class="action-btn" @click.stop="toggleStatus(record)">
                  {{ record.platformStatus === 'ACTIVE' ? '禁用' : '启用' }}
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

        <div v-if="tableData.length === 0 && !loading" class="table-empty-state">
          暂无平台用户档案，创建平台用户档案后会显示在这里。
        </div>
      </div>

      <div class="pagination-container" ref="paginationRef">
        <div class="pagination-left">
          <div class="export-group">
            <a-button type="primary" :loading="exporting" @click="handleUnsupportedExport('平台用户治理')" class="export-btn">
              <template #icon>
                <DownloadOutlined />
              </template>
              导出当前页
            </a-button>
            <a-button :loading="exportingAsync" @click="handleUnsupportedExport('平台用户治理', true)" class="export-btn">
              <template #icon>
                <DownloadOutlined />
              </template>
              导出全部（异步）
            </a-button>
          </div>
        </div>
        <a-pagination
          v-model:current="pagination.current"
          :page-size="pagination.pageSize"
          :total="pagination.total"
          :show-size-changer="pagination.showSizeChanger"
          :page-size-options="pagination.pageSizeOptions"
          :show-total="pagination.showTotal"
          :locale="{ items_per_page: '条/页' }"
          @change="handlePageChange"
          @showSizeChange="handlePageChange"
        />
      </div>
    </div>

    <a-drawer
      v-model:open="detailVisible"
      title="平台用户详情"
      width="42%"
      :get-container="false"
      :style="{ position: 'absolute' }"
    >
      <div class="drawer-body">
        <div class="drawer-intro">
          <div class="drawer-kicker">Platform Profile</div>
          <p>这里展示平台账号在平台侧控制面的档案状态、角色绑定情况和账号安全状态。</p>
        </div>
        <a-spin :spinning="detailLoading">
          <template v-if="activeDetail">
            <div class="drawer-summary">
              <a-tag :color="statusColor(activeDetail.platformStatus)">
                {{ statusLabel(activeDetail.platformStatus) }}
              </a-tag>
              <a-tag :color="enabledColor(activeDetail.userEnabled)">
                账号{{ enabledLabel(activeDetail.userEnabled) }}
              </a-tag>
              <a-tag :color="platformRoleBindingColor(activeDetail.hasPlatformRoleAssignment)">
                {{ platformRoleBindingLabel(activeDetail.hasPlatformRoleAssignment) }}
              </a-tag>
            </div>

            <a-alert
              v-if="pendingApprovalTotal > 0"
              type="warning"
              show-icon
              style="margin-bottom: 16px"
              :message="`该平台用户有 ${pendingApprovalTotal} 条待审批的平台角色赋权申请`"
            >
              <template #description>
                <span v-if="canViewApprovalCenter">
                  可在
                  <a class="approval-link" href="#" @click.prevent="goToApprovalQueue(activeDetail.userId)">平台角色赋权审批</a>
                  中查看并处理。
                </span>
                <span v-else>处理赋权审批需要具备 <code>platform:role:approval:*</code> 相关权限。</span>
              </template>
            </a-alert>

            <section class="drawer-section">
              <div class="drawer-section-header">
                <h4>基础身份</h4>
                <p>平台侧档案关联的基础用户信息。</p>
              </div>
              <div class="detail-grid">
                <div class="detail-field">
                  <span class="detail-label">用户 ID</span>
                  <span class="detail-value">{{ displayValue(activeDetail.userId) }}</span>
                </div>
                <div class="detail-field">
                  <span class="detail-label">用户名</span>
                  <span class="detail-value">{{ displayValue(activeDetail.username) }}</span>
                </div>
                <div class="detail-field">
                  <span class="detail-label">昵称</span>
                  <span class="detail-value">{{ displayValue(activeDetail.nickname) }}</span>
                </div>
                <div class="detail-field">
                  <span class="detail-label">展示名</span>
                  <span class="detail-value">{{ displayValue(activeDetail.displayName) }}</span>
                </div>
                <div class="detail-field">
                  <span class="detail-label">邮箱</span>
                  <span class="detail-value">{{ displayValue(activeDetail.email) }}</span>
                </div>
                <div class="detail-field">
                  <span class="detail-label">手机号</span>
                  <span class="detail-value">{{ displayValue(activeDetail.phone) }}</span>
                </div>
              </div>
            </section>

            <section class="drawer-section">
              <div class="drawer-section-header">
                <h4>账号与授权状态</h4>
                <p>这里确认平台登录是否满足账号安全链路和平台角色绑定要求。</p>
              </div>
              <div class="detail-grid">
                <div class="detail-field">
                  <span class="detail-label">账号启用</span>
                  <span class="detail-value">{{ yesNoLabel(activeDetail.userEnabled) }}</span>
                </div>
                <div class="detail-field">
                  <span class="detail-label">账号未过期</span>
                  <span class="detail-value">{{ yesNoLabel(activeDetail.accountNonExpired) }}</span>
                </div>
                <div class="detail-field">
                  <span class="detail-label">账号未锁定</span>
                  <span class="detail-value">{{ yesNoLabel(activeDetail.accountNonLocked) }}</span>
                </div>
                <div class="detail-field">
                  <span class="detail-label">凭据未过期</span>
                  <span class="detail-value">{{ yesNoLabel(activeDetail.credentialsNonExpired) }}</span>
                </div>
                <div class="detail-field">
                  <span class="detail-label">平台档案状态</span>
                  <span class="detail-value">{{ displayValue(activeDetail.platformStatus) }}</span>
                </div>
                <div class="detail-field">
                  <span class="detail-label">平台角色绑定</span>
                  <span class="detail-value">{{ platformRoleBindingLabel(activeDetail.hasPlatformRoleAssignment) }}</span>
                </div>
              </div>
            </section>

            <section class="drawer-section">
              <div class="drawer-section-header">
                <h4>平台角色绑定明细</h4>
                <p>平台用户角色绑定由 /platform/users 控制面承载，保持 PLATFORM 作用域语义不变。</p>
              </div>
              <div class="drawer-role-toolbar">
                <a-button v-if="canEditPlatformUserRoles" type="primary" @click="openRoleEditor(activeDetail.userId)">编辑平台角色</a-button>
              </div>
              <div v-if="activeDetail.roles && activeDetail.roles.length > 0" class="drawer-role-list">
                <a-tag v-for="role in activeDetail.roles" :key="role.roleId" color="blue">
                  {{ roleTagLabel(role) }}
                </a-tag>
              </div>
              <div v-else class="table-empty-state drawer-role-empty">
                当前平台用户暂无角色绑定。
              </div>
            </section>

            <section class="drawer-section">
              <div class="drawer-section-header">
                <h4>时间线</h4>
                <p>最近活动和档案更新时间。</p>
              </div>
              <div class="detail-grid">
                <div class="detail-field">
                  <span class="detail-label">最近登录</span>
                  <span class="detail-value">{{ displayValue(activeDetail.lastLoginAt) }}</span>
                </div>
                <div class="detail-field">
                  <span class="detail-label">创建时间</span>
                  <span class="detail-value">{{ displayValue(activeDetail.createdAt) }}</span>
                </div>
                <div class="detail-field">
                  <span class="detail-label">更新时间</span>
                  <span class="detail-value">{{ displayValue(activeDetail.updatedAt) }}</span>
                </div>
              </div>
            </section>
          </template>
        </a-spin>
      </div>
    </a-drawer>

    <a-drawer
      v-model:open="createVisible"
      title="创建平台用户档案"
      width="42%"
      :get-container="false"
      :style="{ position: 'absolute' }"
    >
      <div class="drawer-body">
        <div class="drawer-intro">
          <div class="drawer-kicker">Create Profile</div>
          <p>
            这里不会创建新的根用户，只会给已存在的 <code>user</code> 补建平台档案；平台角色绑定仍需走后续独立能力。
          </p>
        </div>
        <a-form :model="createForm" layout="vertical">
          <section class="drawer-section">
            <div class="drawer-section-header">
              <h4>关联基础用户</h4>
              <p>填写已存在的根用户 ID，平台侧只补建平台档案，不会新建基础用户账号。</p>
            </div>
            <div class="drawer-form-grid">
              <a-form-item label="用户 ID" required>
                <a-input-number v-model:value="createForm.userId" :min="1" style="width: 100%" />
              </a-form-item>
              <a-form-item label="展示名">
                <a-input v-model:value="createForm.displayName" placeholder="为空时回退 nickname / username" />
              </a-form-item>
            </div>
          </section>

          <section class="drawer-section">
            <div class="drawer-section-header">
              <h4>平台档案设置</h4>
              <p>这里决定平台档案的初始启用状态；角色绑定仍需通过后续独立能力处理。</p>
            </div>
            <div class="drawer-form-grid">
              <a-form-item label="平台档案状态">
                <a-select v-model:value="createForm.status">
                  <a-select-option value="ACTIVE">ACTIVE</a-select-option>
                  <a-select-option value="DISABLED">DISABLED</a-select-option>
                </a-select>
              </a-form-item>
            </div>
          </section>
        </a-form>
      </div>
      <template #footer>
        <div class="drawer-footer">
          <a-button @click="createVisible = false">取消</a-button>
          <a-button type="primary" :loading="saving" @click="submitCreate">创建平台用户档案</a-button>
        </div>
      </template>
    </a-drawer>

    <a-modal
      v-model:open="roleEditorVisible"
      title="编辑平台角色绑定"
      :confirm-loading="roleEditorSaving"
      @ok="submitRoleEditor"
      @cancel="roleEditorVisible = false"
    >
      <a-spin :spinning="roleEditorLoading">
        <p class="role-editor-hint">
          仅展示平台角色主链（/platform/roles 对应角色源），保存直连 GET/PUT /platform/users/{id}/roles。
          标记为「需审批」的角色须通过「平台角色赋权审批」发起申请；直写将拒绝变更此类绑定。
        </p>
        <a-select
          v-model:value="selectedRoleIds"
          mode="multiple"
          style="width: 100%"
          placeholder="请选择平台角色"
          :options="platformRoleOptions.map((role) => ({ value: role.roleId, label: platformRoleOptionLabel(role) }))"
        />
      </a-spin>
    </a-modal>
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

.drawer-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.drawer-role-toolbar {
  margin-bottom: 12px;
}

.drawer-role-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.drawer-role-empty {
  border: 1px dashed #f0f0f0;
  border-radius: 8px;
}

.role-editor-hint {
  margin: 0 0 12px;
  color: #595959;
}

.approval-link {
  color: #1677ff;
  cursor: pointer;
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

  .detail-grid,
  .drawer-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
