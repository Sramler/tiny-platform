<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { ColumnHeightOutlined, ReloadOutlined, SettingOutlined } from '@ant-design/icons-vue'
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
  getPlatformTenantUserDetail,
  listPlatformTenantUsers,
  type PlatformTenantUserDetail,
  type PlatformTenantUserListItem,
} from '@/api/platform-tenant-user'
import { getTenantById, tenantList, type Tenant } from '@/api/tenant'
import {
  PLATFORM_USER_MANAGEMENT_CREATE_AUTHORITIES,
  PLATFORM_ROLE_APPROVAL_PAGE_AUTHORITIES,
  PLATFORM_USER_MANAGEMENT_READ_AUTHORITIES,
  PLATFORM_USER_MANAGEMENT_UPDATE_AUTHORITIES,
  TENANT_MANAGEMENT_READ_AUTHORITIES,
  USER_MANAGEMENT_READ_AUTHORITIES,
} from '@/constants/permission'
import { usePlatformScope } from '@/composables/usePlatformScope'

const { user, fetchWithAuth } = useAuth()
const { isPlatformScope } = usePlatformScope()
const route = useRoute()
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
const tenantEntries = ref<Tenant[]>([])
const loading = ref(false)
const tenantLoading = ref(false)
const detailLoading = ref(false)
const tenantUserLoading = ref(false)
const tenantUserDetailLoading = ref(false)
const saving = ref(false)
const tenantSwitchingId = ref<number | null>(null)
const detailVisible = ref(false)
const createVisible = ref(false)
const tenantUserDetailVisible = ref(false)
const activeDetail = ref<PlatformUserDetail | null>(null)
const platformRoleOptions = ref<PlatformRoleOption[]>([])
const roleEditorVisible = ref(false)
const roleEditorLoading = ref(false)
const roleEditorSaving = ref(false)
const roleEditorUserId = ref<number | null>(null)
const selectedRoleIds = ref<number[]>([])
const activeTenantUserDetail = ref<PlatformTenantUserDetail | null>(null)
const selectedStewardshipTenant = ref<Tenant | null>(null)
const activeTab = ref('platformUsers')
const pendingApprovalTotal = ref(0)
const createForm = ref<PlatformUserCreatePayload>({
  userId: 0,
  displayName: '',
  status: 'ACTIVE',
})
const pagination = ref({
  current: 1,
  pageSize: 10,
  total: 0,
})
const tenantQuery = ref({
  code: '',
  name: '',
})
const tenantUserQuery = ref({
  username: '',
  nickname: '',
})
const tenantPagination = ref({
  current: 1,
  pageSize: 5,
  total: 0,
})
const tenantUserPagination = ref({
  current: 1,
  pageSize: 10,
  total: 0,
})
const tenantUserTableData = ref<PlatformTenantUserListItem[]>([])
type TableDensity = 'default' | 'middle' | 'small'
type PlatformUserColumnKey =
  | 'userId'
  | 'username'
  | 'displayName'
  | 'nickname'
  | 'userEnabled'
  | 'platformStatus'
  | 'hasPlatformRoleAssignment'
  | 'updatedAt'
type TenantUserColumnKey = 'id' | 'username' | 'nickname' | 'enabled' | 'lastLoginAt' | 'failedLoginCount'

const tableDensity = ref<TableDensity>('default')
const platformUserColumnOptions: Array<{ key: PlatformUserColumnKey; label: string }> = [
  { key: 'userId', label: '用户 ID' },
  { key: 'username', label: '用户名' },
  { key: 'displayName', label: '展示名' },
  { key: 'nickname', label: '昵称' },
  { key: 'userEnabled', label: '账号状态' },
  { key: 'platformStatus', label: '平台档案状态' },
  { key: 'hasPlatformRoleAssignment', label: '平台角色绑定' },
  { key: 'updatedAt', label: '更新时间' },
]
const tenantUserColumnOptions: Array<{ key: TenantUserColumnKey; label: string }> = [
  { key: 'id', label: '用户 ID' },
  { key: 'username', label: '用户名' },
  { key: 'nickname', label: '昵称' },
  { key: 'enabled', label: '账号启用' },
  { key: 'lastLoginAt', label: '最近登录' },
  { key: 'failedLoginCount', label: '失败次数' },
]
const tenantEntryColumns = [
  { title: '租户', dataIndex: 'tenantInfo', key: 'tenantInfo', width: 320 },
  { title: '租户 ID', dataIndex: 'id', key: 'id', width: 120 },
  { title: '启用状态', dataIndex: 'enabled', key: 'enabled', width: 120 },
  { title: '生命周期', dataIndex: 'lifecycleStatus', key: 'lifecycleStatus', width: 140 },
  { title: '操作', dataIndex: 'action', key: 'action', width: 260, fixed: 'right' as const },
]
const visiblePlatformUserColumns = ref<PlatformUserColumnKey[]>(platformUserColumnOptions.map((item) => item.key))
const visibleTenantUserColumns = ref<TenantUserColumnKey[]>(tenantUserColumnOptions.map((item) => item.key))
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

const canRead = computed(() => hasAnyAuthority(PLATFORM_USER_MANAGEMENT_READ_AUTHORITIES))
const canCreate = computed(() => hasAnyAuthority(PLATFORM_USER_MANAGEMENT_CREATE_AUTHORITIES))
const canUpdate = computed(() => hasAnyAuthority(PLATFORM_USER_MANAGEMENT_UPDATE_AUTHORITIES))
const canEditPlatformUserRoles = computed(() => canUpdate.value)
const canViewApprovalCenter = computed(() => hasAnyAuthority(PLATFORM_ROLE_APPROVAL_PAGE_AUTHORITIES))
const canReadTenants = computed(() => hasAnyAuthority(TENANT_MANAGEMENT_READ_AUTHORITIES))
const canReadTenantUsers = computed(() => hasAnyAuthority(USER_MANAGEMENT_READ_AUTHORITIES))
const canAccessPlatformUsers = computed(() => isPlatformScope.value && canRead.value)
const canAccessTenantStewardship = computed(() =>
  isPlatformScope.value && canReadTenants.value && canReadTenantUsers.value,
)

function statusLabel(status?: string) {
  return status === 'DISABLED' ? '已禁用' : '启用中'
}

function statusColor(status?: string) {
  return status === 'DISABLED' ? 'red' : 'green'
}

function lifecycleLabel(status?: string) {
  if (status === 'FROZEN') {
    return '已冻结'
  }
  if (status === 'DECOMMISSIONED') {
    return '已下线'
  }
  return '运行中'
}

function lifecycleColor(status?: string) {
  if (status === 'FROZEN') {
    return 'orange'
  }
  if (status === 'DECOMMISSIONED') {
    return 'red'
  }
  return 'green'
}

function isSelectedStewardshipTenant(tenant?: Tenant | Record<string, any> | null) {
  if (!tenant) {
    return false
  }
  const normalizedTenant = normalizeTenantRecord(tenant)
  return Boolean(normalizedTenant.id && normalizedTenant.id === selectedStewardshipTenant.value?.id)
}

function getTenantEntryRowClassName(record: Tenant) {
  return isSelectedStewardshipTenant(record) ? 'tenant-entry-row--active' : ''
}

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

async function restoreTenantStewardshipFromRoute() {
  activeTab.value = resolveRequestedTab()
  if (activeTab.value !== 'tenantStewardship' || !canAccessTenantStewardship.value) {
    return
  }
  const requestedTenantId = Number(route.query.tenantId)
  if (!Number.isInteger(requestedTenantId) || requestedTenantId <= 0) {
    return
  }

  let tenant = tenantEntries.value.find((entry) => entry.id === requestedTenantId) || null
  if (!tenant) {
    try {
      tenant = await getTenantById(requestedTenantId)
    } catch (error: any) {
      message.warning(error?.message || '无法恢复租户代管上下文，请重新选择租户')
      return
    }
  }

  selectedStewardshipTenant.value = tenant
  tenantUserPagination.value.current = 1
  await loadStewardshipUsers()
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

function yesNoLabel(value?: boolean) {
  return value ? '是' : '否'
}

function enabledLabel(value?: boolean) {
  return value ? '启用' : '禁用'
}

function enabledColor(value?: boolean) {
  return value ? 'green' : 'red'
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

function lockStatusLabel(detail?: PlatformTenantUserDetail | null) {
  if (detail?.temporarilyLocked) {
    return '临时锁定'
  }
  if (detail?.accountNonLocked === false) {
    return '管理员锁定'
  }
  return '正常'
}

function lockStatusColor(detail?: PlatformTenantUserDetail | null) {
  if (detail?.temporarilyLocked) {
    return 'orange'
  }
  if (detail?.accountNonLocked === false) {
    return 'red'
  }
  return 'green'
}

function isPlatformUserColumnVisible(columnKey: PlatformUserColumnKey) {
  return visiblePlatformUserColumns.value.includes(columnKey)
}

function isTenantUserColumnVisible(columnKey: TenantUserColumnKey) {
  return visibleTenantUserColumns.value.includes(columnKey)
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

function handlePlatformUserColumnChange(columnKey: PlatformUserColumnKey, checked: boolean) {
  visiblePlatformUserColumns.value = updateVisibleColumns(
    visiblePlatformUserColumns.value,
    columnKey,
    checked,
    '平台用户列表至少保留一列',
  )
}

function handleTenantUserColumnChange(columnKey: TenantUserColumnKey, checked: boolean) {
  visibleTenantUserColumns.value = updateVisibleColumns(
    visibleTenantUserColumns.value,
    columnKey,
    checked,
    '租户用户列表至少保留一列',
  )
}

function handleDensityMenuClick({ key }: { key: string | number }) {
  const nextKey = String(key)
  if (nextKey === 'default' || nextKey === 'middle' || nextKey === 'small') {
    tableDensity.value = nextKey as TableDensity
  }
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

async function loadTenantEntries() {
  if (!isPlatformScope.value || !canReadTenants.value) {
    tenantEntries.value = []
    tenantPagination.value.total = 0
    return
  }
  tenantLoading.value = true
  try {
    const result = await tenantList({
      code: tenantQuery.value.code || undefined,
      name: tenantQuery.value.name || undefined,
      page: tenantPagination.value.current - 1,
      size: tenantPagination.value.pageSize,
    })
    tenantEntries.value = Array.isArray(result.content) ? result.content : []
    tenantPagination.value.total = result.totalElements || 0
  } catch (error: any) {
    message.error(error?.message || '租户代管入口加载失败')
  } finally {
    tenantLoading.value = false
  }
}

function handleSearch() {
  pagination.value.current = 1
  void loadData()
}

function handleReset() {
  query.value = {
    keyword: '',
    enabled: undefined,
    status: undefined,
  }
  pagination.value.current = 1
  void loadData()
}

function handlePageChange(page: number, pageSize: number) {
  pagination.value.current = page
  pagination.value.pageSize = pageSize
  void loadData()
}

function handleTenantSearch() {
  tenantPagination.value.current = 1
  void loadTenantEntries()
}

function handleTenantReset() {
  tenantQuery.value = {
    code: '',
    name: '',
  }
  tenantPagination.value.current = 1
  void loadTenantEntries()
}

function handleTenantPageChange(page: number, pageSize: number) {
  tenantPagination.value.current = page
  tenantPagination.value.pageSize = pageSize
  void loadTenantEntries()
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
  }
}

function handleTenantUserSearch() {
  tenantUserPagination.value.current = 1
  void loadStewardshipUsers()
}

function handleTenantUserReset() {
  tenantUserQuery.value = {
    username: '',
    nickname: '',
  }
  tenantUserPagination.value.current = 1
  void loadStewardshipUsers()
}

function handleTenantUserPageChange(page: number, pageSize: number) {
  tenantUserPagination.value.current = page
  tenantUserPagination.value.pageSize = pageSize
  void loadStewardshipUsers()
}

function clearStewardshipTenant() {
  selectedStewardshipTenant.value = null
  tenantUserTableData.value = []
  tenantUserPagination.value.total = 0
  tenantUserQuery.value = {
    username: '',
    nickname: '',
  }
  syncTenantStewardshipRoute()
}

const tenantStewardshipDrawerOpen = computed({
  get: () => Boolean(selectedStewardshipTenant.value),
  set: (open) => {
    if (!open) {
      clearStewardshipTenant()
    }
  },
})

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

async function enterTenantUserManagement(tenantRecord: Tenant | Record<string, any>) {
  const tenant = normalizeTenantRecord(tenantRecord)
  if (!tenant.id || tenant.id <= 0) {
    message.warning('缺少有效租户 ID，暂无法进入租户用户管理')
    return
  }
  if (!canAccessTenantStewardship.value) {
    message.warning('当前会话缺少租户用户读取权限，无法进入平台代管视图')
    return
  }
  tenantSwitchingId.value = tenant.id
  try {
    selectedStewardshipTenant.value = tenant
    activeTab.value = 'tenantStewardship'
    tenantUserPagination.value.current = 1
    syncTenantStewardshipRoute(tenant.id)
    await loadStewardshipUsers()
  } catch (error: any) {
    message.error(error?.message || '进入租户用户代管视图失败')
  } finally {
    tenantSwitchingId.value = null
  }
}

async function showTenantUserDetail(record: PlatformTenantUserListItem) {
  const tenantId = selectedStewardshipTenant.value?.id
  if (!tenantId || !record?.id) {
    message.warning('缺少租户或用户上下文，暂无法查看详情')
    return
  }
  tenantUserDetailVisible.value = true
  tenantUserDetailLoading.value = true
  activeTenantUserDetail.value = null
  try {
    activeTenantUserDetail.value = await getPlatformTenantUserDetail(tenantId, record.id)
  } catch (error: any) {
    tenantUserDetailVisible.value = false
    message.error(error?.message || '租户用户详情加载失败')
  } finally {
    tenantUserDetailLoading.value = false
  }
}

async function showDetail(record: PlatformUserListItem) {
  detailVisible.value = true
  detailLoading.value = true
  activeDetail.value = null
  pendingApprovalTotal.value = 0
  try {
    const [detail, roles] = await Promise.all([
      getPlatformUserDetail(record.userId),
      getPlatformUserRoles(record.userId),
    ])
    activeDetail.value = {
      ...detail,
      roles,
    }
    await loadPendingApprovalCount(record.userId)
  } catch (error: any) {
    detailVisible.value = false
    message.error(error?.message || '平台用户详情加载失败')
  } finally {
    detailLoading.value = false
  }
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

async function toggleStatus(record: PlatformUserListItem) {
  if (!canUpdate.value) {
    message.warning('当前会话缺少平台用户更新权限')
    return
  }
  const nextStatus: PlatformUserStatus = record.platformStatus === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  try {
    await updatePlatformUserStatus(record.userId, nextStatus)
    record.platformStatus = nextStatus
    if (activeDetail.value?.userId === record.userId) {
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
  activeTab.value = resolveRequestedTab()
  void Promise.all([loadData(), loadTenantEntries()]).then(() => restoreTenantStewardshipFromRoute())
})
</script>

<template>
  <div class="content-container">
    <div class="content-card">
      <div v-if="!isPlatformScope" class="platform-guard-card">
        <div class="platform-guard-kicker">Platform Scope Required</div>
        <h3>当前页面只支持 PLATFORM 作用域</h3>
        <p>已阻止加载平台用户控制面数据，请切换到平台作用域后重试。</p>
      </div>

      <div v-else-if="!canRead" class="platform-guard-card">
        <div class="platform-guard-kicker">Permission Required</div>
        <h3>平台用户治理需要额外授权</h3>
        <p>
          当前会话缺少 <code>platform:user:list</code> 或 <code>platform:user:view</code>，因此不会请求
          <code>/platform/users</code>。
        </p>
      </div>

      <template v-else>
        <div class="platform-page-shell">
          <a-tabs v-model:activeKey="activeTab" class="boundary-tabs">
            <a-tab-pane key="platformUsers" tab="平台用户治理">
              <div class="tab-workspace">
                <div class="form-container">
                  <a-form layout="inline" :model="query">
                    <a-form-item label="关键字">
                      <a-input v-model:value="query.keyword" placeholder="用户名 / 昵称 / 展示名" />
                    </a-form-item>
                    <a-form-item label="账号启用">
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
                      <a-space>
                        <a-button type="primary" @click="handleSearch">搜索</a-button>
                        <a-button @click="handleReset">重置</a-button>
                      </a-space>
                    </a-form-item>
                  </a-form>
                </div>

                <div class="toolbar-container">
                  <div class="table-title">
                    <span>平台用户列表</span>
                    <p>
                      当前页只管理 <code>platform_user_profile</code> 档案。平台登录仍要求平台档案为 ACTIVE 且存在有效
                      <code>PLATFORM</code> 角色分配。
                    </p>
                  </div>
                  <div class="table-actions">
                    <a-button v-if="canCreate" type="primary" @click="openCreateModal">创建平台用户档案</a-button>
                    <a-tooltip title="刷新平台用户">
                      <span class="action-icon" @click="loadData">
                        <ReloadOutlined :spin="loading" />
                      </span>
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
                    <a-popover placement="bottomRight" trigger="click">
                      <template #content>
                        <div class="column-settings-panel">
                          <div class="column-settings-header">平台用户列设置</div>
                          <div class="column-settings-list">
                            <div v-for="column in platformUserColumnOptions" :key="column.key" class="column-settings-item">
                              <a-checkbox
                                :checked="visiblePlatformUserColumns.includes(column.key)"
                                @change="(e: any) => handlePlatformUserColumnChange(column.key, e.target.checked)"
                              >
                                {{ column.label }}
                              </a-checkbox>
                            </div>
                          </div>
                        </div>
                      </template>
                      <a-tooltip title="列设置">
                        <SettingOutlined class="action-icon" />
                      </a-tooltip>
                    </a-popover>
                  </div>
                </div>

                <div class="table-container">
                  <div class="table-scroll-container">
                    <a-table
                      :data-source="tableData"
                      :pagination="false"
                      row-key="userId"
                      :size="tableDensity === 'default' ? undefined : tableDensity"
                      :loading="loading"
                      :scroll="{ x: 1100 }"
                    >
                      <a-table-column
                        v-if="isPlatformUserColumnVisible('userId')"
                        title="用户 ID"
                        data-index="userId"
                        key="userId"
                      />
                      <a-table-column
                        v-if="isPlatformUserColumnVisible('username')"
                        title="用户名"
                        data-index="username"
                        key="username"
                      />
                      <a-table-column
                        v-if="isPlatformUserColumnVisible('displayName')"
                        title="展示名"
                        data-index="displayName"
                        key="displayName"
                      />
                      <a-table-column
                        v-if="isPlatformUserColumnVisible('nickname')"
                        title="昵称"
                        data-index="nickname"
                        key="nickname"
                      />
                      <a-table-column v-if="isPlatformUserColumnVisible('userEnabled')" title="账号状态" key="userEnabled">
                        <template #default="{ record }">
                          <a-tag :color="record.userEnabled ? 'green' : 'red'">
                            {{ record.userEnabled ? '启用' : '禁用' }}
                          </a-tag>
                        </template>
                      </a-table-column>
                      <a-table-column
                        v-if="isPlatformUserColumnVisible('platformStatus')"
                        title="平台档案状态"
                        key="platformStatus"
                      >
                        <template #default="{ record }">
                          <a-tag :color="statusColor(record.platformStatus)">
                            {{ statusLabel(record.platformStatus) }}
                          </a-tag>
                        </template>
                      </a-table-column>
                      <a-table-column
                        v-if="isPlatformUserColumnVisible('hasPlatformRoleAssignment')"
                        title="平台角色绑定"
                        key="hasPlatformRoleAssignment"
                      >
                        <template #default="{ record }">
                          <a-tag :color="record.hasPlatformRoleAssignment ? 'blue' : 'default'">
                            {{ record.hasPlatformRoleAssignment ? '已绑定' : '缺少绑定' }}
                          </a-tag>
                        </template>
                      </a-table-column>
                      <a-table-column
                        v-if="isPlatformUserColumnVisible('updatedAt')"
                        title="更新时间"
                        data-index="updatedAt"
                        key="updatedAt"
                      />
                      <a-table-column title="操作" key="action" width="220">
                        <template #default="{ record }">
                          <a-space>
                            <a-button type="link" @click="showDetail(record)">详情</a-button>
                            <a-button v-if="canEditPlatformUserRoles" type="link" @click="openRoleEditor(record.userId)">角色绑定</a-button>
                            <a-button v-if="canUpdate" type="link" @click="toggleStatus(record)">
                              {{ record.platformStatus === 'ACTIVE' ? '禁用' : '启用' }}
                            </a-button>
                          </a-space>
                        </template>
                      </a-table-column>
                    </a-table>

                    <div v-if="tableData.length === 0 && !loading" class="table-empty-state">
                      暂无平台用户档案，创建平台用户档案后会显示在这里。
                    </div>
                  </div>

                  <div class="pagination-container">
                    <div class="pagination-summary">共 {{ pagination.total }} 条平台用户记录</div>
                    <a-pagination
                      :current="pagination.current"
                      :page-size="pagination.pageSize"
                      :total="pagination.total"
                      :show-size-changer="true"
                      @change="handlePageChange"
                      @showSizeChange="handlePageChange"
                    />
                  </div>
                </div>
              </div>
            </a-tab-pane>

            <a-tab-pane key="tenantStewardship" tab="租户用户代管">
              <div class="tab-workspace tab-workspace--stacked">
                <section class="workspace-section workspace-section--entry">
                  <div class="form-container">
                    <a-form layout="inline" :model="tenantQuery">
                      <a-form-item label="租户编码">
                        <a-input v-model:value="tenantQuery.code" placeholder="请输入租户编码" />
                      </a-form-item>
                      <a-form-item label="租户名称">
                        <a-input v-model:value="tenantQuery.name" placeholder="请输入租户名称" />
                      </a-form-item>
                      <a-form-item>
                        <a-space>
                          <a-button type="primary" @click="handleTenantSearch">搜索租户</a-button>
                          <a-button @click="handleTenantReset">重置</a-button>
                        </a-space>
                      </a-form-item>
                    </a-form>
                  </div>

                  <div class="toolbar-container">
                    <div class="table-title">
                      <span>租户代管入口</span>
                      <p>
                        平台侧保持 <code>PLATFORM</code> 作用域，通过桥接接口按显式 <code>tenantId</code>
                        读取租户用户，不要求当前账号先加入目标租户。
                      </p>
                      <div v-if="selectedStewardshipTenant" class="workspace-context-pill">
                        当前代管目标：<code>{{ selectedStewardshipTenant.code }}</code>
                      </div>
                    </div>
                    <div class="table-actions">
                      <a-button @click="router.push('/platform/tenants')">前往平台租户治理</a-button>
                      <a-tooltip title="刷新租户入口">
                        <span class="action-icon" @click="loadTenantEntries">
                          <ReloadOutlined :spin="tenantLoading" />
                        </span>
                      </a-tooltip>
                    </div>
                  </div>

                  <div class="table-container">
                    <div class="table-scroll-container">
                      <div v-if="!canReadTenants" class="table-empty-state">
                        当前会话缺少 <code>system:tenant:list</code> 或 <code>system:tenant:view</code>，因此不会加载代管入口列表。
                      </div>

                      <template v-else>
                        <a-table
                          v-if="tenantEntries.length > 0"
                          class="tenant-entry-table"
                          :columns="tenantEntryColumns"
                          :data-source="tenantEntries"
                          :loading="tenantLoading"
                          :pagination="false"
                          :row-key="(record: Tenant) => record.id"
                          :row-class-name="getTenantEntryRowClassName"
                          :scroll="{ x: 980 }"
                          :size="tableDensity === 'default' ? undefined : tableDensity"
                        >
                          <template #bodyCell="{ column, record }">
                            <template v-if="column.dataIndex === 'tenantInfo'">
                              <div class="tenant-entry-cell tenant-entry-cell--tenant">
                                <div class="tenant-entry-name">
                                  <span>{{ record.name || `租户 ${record.id}` }}</span>
                                  <a-tag v-if="isSelectedStewardshipTenant(record)" color="processing">当前代管目标</a-tag>
                                </div>
                                <div class="tenant-entry-code">
                                  <code>{{ record.code || '-' }}</code>
                                </div>
                              </div>
                            </template>
                            <template v-else-if="column.dataIndex === 'id'">
                              <div class="tenant-entry-cell tenant-entry-cell--id">
                                <span class="tenant-cell-mobile-label">租户 ID</span>
                                <span>{{ record.id }}</span>
                              </div>
                            </template>
                            <template v-else-if="column.dataIndex === 'enabled'">
                              <div class="tenant-entry-cell tenant-entry-cell--status">
                                <span class="tenant-cell-mobile-label">启用状态</span>
                                <a-tag :color="record.enabled ? 'green' : 'red'">
                                  {{ record.enabled ? '启用' : '禁用' }}
                                </a-tag>
                              </div>
                            </template>
                            <template v-else-if="column.dataIndex === 'lifecycleStatus'">
                              <div class="tenant-entry-cell tenant-entry-cell--lifecycle">
                                <span class="tenant-cell-mobile-label">生命周期</span>
                                <a-tag :color="lifecycleColor(record.lifecycleStatus)">
                                  {{ lifecycleLabel(record.lifecycleStatus) }}
                                </a-tag>
                              </div>
                            </template>
                            <template v-else-if="column.dataIndex === 'action'">
                              <div class="tenant-entry-actions tenant-entry-cell tenant-entry-cell--actions">
                                <span class="tenant-cell-mobile-label">操作</span>
                                <a-button type="link" @click="openTenantDetail(record)">平台详情</a-button>
                                <a-button
                                  type="primary"
                                  ghost
                                  :loading="tenantSwitchingId === record.id"
                                  @click="enterTenantUserManagement(record)"
                                >
                                  进入租户用户管理
                                </a-button>
                              </div>
                            </template>
                          </template>
                        </a-table>

                        <div v-if="tenantEntries.length === 0 && !tenantLoading" class="table-empty-state">
                          暂无可进入的租户代管入口。
                        </div>
                      </template>
                    </div>

                    <div v-if="canReadTenants" class="pagination-container">
                      <div class="pagination-summary">共 {{ tenantPagination.total }} 个租户代管入口</div>
                      <a-pagination
                        :current="tenantPagination.current"
                        :page-size="tenantPagination.pageSize"
                        :total="tenantPagination.total"
                        :show-size-changer="true"
                        @change="handleTenantPageChange"
                        @showSizeChange="handleTenantPageChange"
                      />
                    </div>
                  </div>
                </section>
              </div>
            </a-tab-pane>
          </a-tabs>
        </div>

        <a-drawer
          v-model:open="tenantStewardshipDrawerOpen"
          title="租户用户管理"
          width="72%"
          :get-container="false"
          :style="{ position: 'absolute' }"
        >
          <div v-if="selectedStewardshipTenant" class="tenant-stewardship-drawer-shell">
            <div class="selection-banner">
              <div class="selection-banner-label">已锁定代管租户</div>
              <div class="selection-banner-main">
                <span>{{ selectedStewardshipTenant.name || `租户 ${selectedStewardshipTenant.id}` }}</span>
                <code>{{ selectedStewardshipTenant.code }}</code>
                <span>租户 ID：{{ selectedStewardshipTenant.id }}</span>
              </div>
              <p>当前仍处于 PLATFORM 作用域，只展示显式目标租户的用户列表与详情，不会切换 active scope。</p>
              <div class="selection-banner-stats">
                <div class="selection-banner-stat">
                  <span class="selection-stat-label">治理模式</span>
                  <span class="selection-stat-value">平台代管</span>
                </div>
                <div class="selection-banner-stat">
                  <span class="selection-stat-label">作用域</span>
                  <span class="selection-stat-value">PLATFORM</span>
                </div>
                <div class="selection-banner-stat">
                  <span class="selection-stat-label">已加载用户</span>
                  <span class="selection-stat-value">{{ tenantUserPagination.total }}</span>
                </div>
              </div>
            </div>

            <div class="form-container">
              <a-form layout="inline" :model="tenantUserQuery">
                <a-form-item label="用户名">
                  <a-input v-model:value="tenantUserQuery.username" placeholder="请输入用户名" />
                </a-form-item>
                <a-form-item label="昵称">
                  <a-input v-model:value="tenantUserQuery.nickname" placeholder="请输入昵称" />
                </a-form-item>
                <a-form-item>
                  <a-space>
                    <a-button type="primary" @click="handleTenantUserSearch">搜索用户</a-button>
                    <a-button @click="handleTenantUserReset">重置</a-button>
                  </a-space>
                </a-form-item>
              </a-form>
            </div>

            <div class="toolbar-container">
              <div class="table-title">
                <span>租户用户列表</span>
                <p>
                  平台用户正在代管 <code>{{ selectedStewardshipTenant.code }}</code>
                  的租户用户，详情查询走平台桥接接口，不依赖当前用户属于目标 tenant。
                </p>
              </div>
              <div class="table-actions">
                <a-button @click="clearStewardshipTenant">关闭</a-button>
                <a-tooltip title="刷新租户用户">
                  <span class="action-icon" @click="loadStewardshipUsers">
                    <ReloadOutlined :spin="tenantUserLoading" />
                  </span>
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
                <a-popover placement="bottomRight" trigger="click">
                  <template #content>
                    <div class="column-settings-panel">
                      <div class="column-settings-header">租户用户列设置</div>
                      <div class="column-settings-list">
                        <div v-for="column in tenantUserColumnOptions" :key="column.key" class="column-settings-item">
                          <a-checkbox
                            :checked="visibleTenantUserColumns.includes(column.key)"
                            @change="(e: any) => handleTenantUserColumnChange(column.key, e.target.checked)"
                          >
                            {{ column.label }}
                          </a-checkbox>
                        </div>
                      </div>
                    </div>
                  </template>
                  <a-tooltip title="列设置">
                    <SettingOutlined class="action-icon" />
                  </a-tooltip>
                </a-popover>
              </div>
            </div>

            <div class="table-container tenant-stewardship-table-container">
              <div class="table-scroll-container">
                <div v-if="!canReadTenantUsers" class="table-empty-state">
                  当前会话缺少 <code>system:user:list</code> 或 <code>system:user:view</code>，因此无法加载租户用户列表。
                </div>

                <template v-else>
                  <a-table
                    :data-source="tenantUserTableData"
                    :loading="tenantUserLoading"
                    :pagination="false"
                    row-key="id"
                    :size="tableDensity === 'default' ? undefined : tableDensity"
                    :scroll="{ x: 900 }"
                  >
                    <a-table-column v-if="isTenantUserColumnVisible('id')" title="用户 ID" data-index="id" key="id" />
                    <a-table-column
                      v-if="isTenantUserColumnVisible('username')"
                      title="用户名"
                      data-index="username"
                      key="username"
                    />
                    <a-table-column
                      v-if="isTenantUserColumnVisible('nickname')"
                      title="昵称"
                      data-index="nickname"
                      key="nickname"
                    />
                    <a-table-column v-if="isTenantUserColumnVisible('enabled')" title="账号启用" key="enabled">
                      <template #default="{ record }">
                        <a-tag :color="record.enabled ? 'green' : 'red'">
                          {{ record.enabled ? '启用' : '禁用' }}
                        </a-tag>
                      </template>
                    </a-table-column>
                    <a-table-column
                      v-if="isTenantUserColumnVisible('lastLoginAt')"
                      title="最近登录"
                      data-index="lastLoginAt"
                      key="lastLoginAt"
                    />
                    <a-table-column
                      v-if="isTenantUserColumnVisible('failedLoginCount')"
                      title="失败次数"
                      data-index="failedLoginCount"
                      key="failedLoginCount"
                    />
                    <a-table-column title="操作" key="action" width="120">
                      <template #default="{ record }">
                        <a-button type="link" @click="showTenantUserDetail(record)">详情</a-button>
                      </template>
                    </a-table-column>
                  </a-table>

                  <div v-if="tenantUserTableData.length === 0 && !tenantUserLoading" class="table-empty-state">
                    当前租户下暂无匹配的用户记录。
                  </div>
                </template>
              </div>

              <div v-if="canReadTenantUsers" class="pagination-container">
                <div class="pagination-summary">共 {{ tenantUserPagination.total }} 条租户用户记录</div>
                <a-pagination
                  :current="tenantUserPagination.current"
                  :page-size="tenantUserPagination.pageSize"
                  :total="tenantUserPagination.total"
                  :show-size-changer="true"
                  @change="handleTenantUserPageChange"
                  @showSizeChange="handleTenantUserPageChange"
                />
              </div>
            </div>
          </div>
        </a-drawer>

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
      </template>
    </div>
  </div>
</template>

<style scoped>
.content-container {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #fff;
  position: relative;
}

.content-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.platform-page-shell {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: #fff;
}

.platform-guard-kicker {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #1677ff;
}

.platform-guard-card h3 {
  margin: 8px 0;
  font-size: 24px;
  font-weight: 600;
  color: #1f1f1f;
}

.platform-guard-card p {
  margin: 0;
  color: #595959;
  line-height: 1.7;
}

.platform-guard-card {
  margin: 24px;
  padding: 24px;
  border: 1px solid #d9e8ff;
  border-radius: 12px;
  background: linear-gradient(135deg, #f7fbff 0%, #ffffff 100%);
  box-shadow: 0 12px 32px rgba(22, 119, 255, 0.08);
}

.boundary-tabs {
  flex: 1;
  min-height: 0;
  padding: 0 24px 24px;
}

.tab-workspace {
  display: flex;
  flex-direction: column;
  min-height: 0;
  border: 1px solid #f0f0f0;
  border-radius: 12px;
  overflow: hidden;
  background: #fff;
}

.tab-workspace--stacked {
  gap: 16px;
  border: 0;
  border-radius: 0;
  overflow: visible;
  background: transparent;
}

.workspace-section {
  display: flex;
  flex-direction: column;
  min-height: 0;
  border: 1px solid #f0f0f0;
  border-radius: 12px;
  overflow: hidden;
  background: #fff;
}

.form-container {
  padding: 24px;
  border-bottom: 1px solid #f0f0f0;
  background: transparent;
}

.toolbar-container {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 24px;
  border-bottom: 1px solid #f0f0f0;
  background: transparent;
}

.table-title {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.table-title > span {
  font-size: 16px;
  font-weight: 600;
  color: #1f1f1f;
}

.table-title p {
  margin: 0;
  color: #595959;
  line-height: 1.6;
}

.workspace-context-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  padding: 4px 10px;
  width: fit-content;
  border-radius: 999px;
  background: #f0f7ff;
  color: #1677ff;
  font-size: 12px;
  font-weight: 500;
}

.table-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
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

.table-container {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.table-scroll-container {
  flex: 1;
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

.tenant-entry-row--active {
  background: linear-gradient(135deg, #f2f8ff 0%, #ffffff 100%);
}

:deep(.tenant-entry-table .ant-table-tbody > tr.tenant-entry-row--active > td) {
  background: linear-gradient(135deg, #f2f8ff 0%, #ffffff 100%) !important;
}

:deep(.tenant-entry-table .ant-table-tbody > tr.tenant-entry-row--active > td:first-child) {
  box-shadow: inset 4px 0 0 #1677ff;
}

.tenant-entry-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.tenant-entry-name {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  font-size: 16px;
  font-weight: 600;
  color: #1f1f1f;
}

.tenant-entry-code {
  color: #8c8c8c;
  font-size: 13px;
}

.tenant-entry-cell--id {
  color: #1f1f1f;
  font-weight: 500;
}

.tenant-entry-actions {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-self: end;
}

.tenant-cell-mobile-label {
  display: none;
  font-size: 12px;
  color: #8c8c8c;
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
}

::deep(.ant-table-tbody > tr > td) {
  white-space: nowrap;
}

.pagination-summary {
  color: #595959;
}

.selection-banner {
  padding: 18px 20px;
  border-bottom: 1px solid #f0f0f0;
  background: linear-gradient(135deg, #f8fbff 0%, #ffffff 100%);
}

.selection-banner-label {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #1677ff;
}

.selection-banner-main {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #1f1f1f;
}

.selection-banner p {
  margin: 8px 0 0;
  color: #595959;
  line-height: 1.6;
}

.selection-banner-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-top: 16px;
}

.selection-banner-stat {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid #e6f4ff;
}

.selection-stat-label {
  font-size: 12px;
  color: #8c8c8c;
}

.selection-stat-value {
  font-size: 15px;
  font-weight: 600;
  color: #1f1f1f;
}

.column-settings-panel {
  min-width: 180px;
}

.column-settings-header {
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #1f1f1f;
}

.column-settings-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.column-settings-item {
  display: flex;
  align-items: center;
}

.drawer-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.tenant-stewardship-drawer-shell {
  display: flex;
  flex-direction: column;
  min-height: 100%;
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

.tenant-stewardship-table-container {
  min-height: 320px;
}

@media (max-width: 960px) {
  .boundary-tabs {
    padding-left: 16px;
    padding-right: 16px;
  }

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

  .tenant-cell-mobile-label {
    display: inline-block;
  }

  .detail-grid,
  .drawer-form-grid {
    grid-template-columns: 1fr;
  }

  .selection-banner-stats {
    grid-template-columns: 1fr;
  }
}

:deep(.boundary-tabs .ant-tabs-content-holder) {
  min-height: 0;
}

:deep(.boundary-tabs .ant-tabs-tabpane) {
  min-height: 0;
}
</style>
