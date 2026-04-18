<template>
  <div class="content-container">
    <a-card v-if="!isPlatformScope" title="平台作用域限制" class="scope-guard-card">
      当前会话不是 PLATFORM 作用域，已阻止加载平台角色赋权审批。请切换到平台作用域后访问本页。
    </a-card>

    <div v-else-if="!canAccessPage" class="content-card">
      <div class="platform-guard-card">
        <div class="platform-guard-kicker">Permission Required</div>
        <h3>缺少平台赋权审批权限</h3>
        <p>
          本页仅平台作用域可用，且至少需要
          <code>platform:role:approval:list</code>、
          <code>platform:role:approval:submit</code>、
          <code>platform:role:approval:approve</code>、
          <code>platform:role:approval:reject</code>、
          <code>platform:role:approval:cancel</code> 之一（与菜单 OR 组一致）。
        </p>
      </div>
    </div>

    <div v-else class="content-card">
      <div class="platform-page-shell">
        <div class="toolbar-container">
          <div class="table-title">
            <span>平台角色赋权审批</span>
            <p>高风险角色（<code>approval_mode=ONE_STEP</code>）须在此发起或审批；直写接口将拒绝此类绑定变更。</p>
          </div>
          <a-space>
            <a-button v-if="canSubmit" type="primary" @click="openSubmitModal">
              <template #icon><PlusOutlined /></template>
              发起申请
            </a-button>
            <a-tooltip title="刷新">
              <span class="action-icon" @click="loadTable">
                <ReloadOutlined :spin="loading" />
              </span>
            </a-tooltip>
          </a-space>
        </div>

        <div class="form-container">
          <a-form layout="inline" class="approval-filters">
            <a-form-item label="目标用户 ID">
              <a-input-number v-model:value="filterTargetUserId" :min="1" placeholder="可选" style="width: 160px" />
            </a-form-item>
            <a-form-item label="状态">
              <a-select v-model:value="filterStatus" allow-clear placeholder="全部" style="width: 160px">
                <a-select-option value="PENDING">PENDING</a-select-option>
                <a-select-option value="APPLIED">APPLIED</a-select-option>
                <a-select-option value="FAILED">FAILED</a-select-option>
                <a-select-option value="REJECTED">REJECTED</a-select-option>
                <a-select-option value="CANCELED">CANCELED</a-select-option>
              </a-select>
            </a-form-item>
            <a-form-item>
              <a-button type="primary" @click="applyFilters">查询</a-button>
            </a-form-item>
          </a-form>
        </div>

        <div class="table-container">
          <a-table
            :columns="columns"
            :data-source="rows"
            :loading="loading"
            :pagination="{
              current: pagination.current,
              pageSize: pagination.pageSize,
              total: pagination.total,
              showSizeChanger: true,
            }"
            :row-key="(r) => r.id"
            bordered
            @change="handleTableChange"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'role'">
                {{ record.roleCode || record.roleId }}
              </template>
              <template v-else-if="column.key === 'note'">
                {{ record.applyError || record.reason || record.reviewComment || '—' }}
              </template>
              <template v-else-if="column.dataIndex === 'status'">
                <a-tag :color="statusColor(record.status)">{{ record.status }}</a-tag>
              </template>
              <template v-else-if="column.key === 'actions'">
                <a-space>
                  <a-button
                    v-if="canApprove && record.status === 'PENDING'"
                    type="link"
                    size="small"
                    @click="() => openReviewModal('approve', record)"
                  >
                    通过
                  </a-button>
                  <a-button
                    v-if="canReject && record.status === 'PENDING'"
                    type="link"
                    size="small"
                    danger
                    @click="() => openReviewModal('reject', record)"
                  >
                    拒绝
                  </a-button>
                  <a-button
                    v-if="canCancel && record.status === 'PENDING' && isRequester(record)"
                    type="link"
                    size="small"
                    @click="() => confirmCancel(record)"
                  >
                    撤销
                  </a-button>
                </a-space>
              </template>
            </template>
          </a-table>
        </div>
      </div>
    </div>

    <a-modal
      v-model:open="submitOpen"
      title="发起平台角色赋权审批"
      :confirm-loading="submitLoading"
      ok-text="提交"
      @ok="submitRequest"
    >
      <a-form layout="vertical">
        <a-form-item label="目标平台用户 ID" required>
          <a-input-number v-model:value="submitForm.targetUserId" :min="1" style="width: 100%" />
        </a-form-item>
        <a-form-item label="平台角色" required>
          <a-select
            v-model:value="submitForm.roleId"
            show-search
            :filter-option="filterRoleOption"
            :options="oneStepRoleOptions"
            placeholder="仅列 ONE_STEP 角色（与后端校验一致）"
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="动作">
          <a-radio-group v-model:value="submitForm.actionType">
            <a-radio value="GRANT">授予 GRANT</a-radio>
            <a-radio value="REVOKE">回收 REVOKE</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="说明">
          <a-textarea v-model:value="submitForm.reason" :rows="3" placeholder="可选" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="reviewOpen"
      :title="reviewMode === 'approve' ? '审批通过' : '审批拒绝'"
      :confirm-loading="reviewLoading"
      ok-text="确认"
      @ok="submitReview"
    >
      <p class="review-hint">申请 #{{ reviewRecord?.id }} · 目标用户 {{ reviewRecord?.targetUserId }} · 角色 {{ reviewRecord?.roleCode }}</p>
      <a-form-item label="意见（可选）">
        <a-textarea v-model:value="reviewComment" :rows="3" />
      </a-form-item>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import { useAuth } from '@/auth/auth'
import { extractAuthoritiesFromJwt, extractUserIdFromJwt } from '@/utils/jwt'
import { listPlatformRoleOptions, type PlatformRoleOption } from '@/api/platform-role'
import {
  approvePlatformRoleAssignmentRequest,
  cancelPlatformRoleAssignmentRequest,
  listPlatformRoleAssignmentRequests,
  rejectPlatformRoleAssignmentRequest,
  submitPlatformRoleAssignmentRequest,
  type PlatformRoleAssignmentRequestItem,
} from '@/api/platform-role-approval'
import {
  PLATFORM_ROLE_APPROVAL_APPROVE,
  PLATFORM_ROLE_APPROVAL_CANCEL,
  PLATFORM_ROLE_APPROVAL_PAGE_AUTHORITIES,
  PLATFORM_ROLE_APPROVAL_REJECT,
  PLATFORM_ROLE_APPROVAL_SUBMIT,
} from '@/constants/permission'
import { usePlatformScope } from '@/composables/usePlatformScope'

const { user } = useAuth()
const { isPlatformScope } = usePlatformScope()
const route = useRoute()

const authorities = computed(() => new Set(extractAuthoritiesFromJwt(user.value?.access_token)))

function hasAnyAuthority(codes: string[]) {
  return codes.some((c) => authorities.value.has(c))
}

const canAccessPage = computed(() => hasAnyAuthority(PLATFORM_ROLE_APPROVAL_PAGE_AUTHORITIES))
const canSubmit = computed(() => authorities.value.has(PLATFORM_ROLE_APPROVAL_SUBMIT))
const canApprove = computed(() => authorities.value.has(PLATFORM_ROLE_APPROVAL_APPROVE))
const canReject = computed(() => authorities.value.has(PLATFORM_ROLE_APPROVAL_REJECT))
const canCancel = computed(() => authorities.value.has(PLATFORM_ROLE_APPROVAL_CANCEL))

const loading = ref(false)
const rows = ref<PlatformRoleAssignmentRequestItem[]>([])
const pagination = ref({
  current: 1,
  pageSize: 20,
  total: 0,
  showSizeChanger: true,
})

const filterTargetUserId = ref<number | undefined>(undefined)
const filterStatus = ref<string | undefined>(undefined)

const roleOptions = ref<PlatformRoleOption[]>([])
const roleOptionsReady = ref(false)
const oneStepRoleOptions = computed(() =>
  roleOptions.value
    .filter((r) => (r.approvalMode || 'NONE').toUpperCase() === 'ONE_STEP')
    .map((r) => ({
      value: r.roleId,
      label: `${r.name} (${r.code})`,
    })),
)

const submitOpen = ref(false)
const submitLoading = ref(false)
const submitForm = ref({
  targetUserId: undefined as number | undefined,
  roleId: undefined as number | undefined,
  actionType: 'GRANT' as 'GRANT' | 'REVOKE',
  reason: '' as string | undefined,
})

const reviewOpen = ref(false)
const reviewLoading = ref(false)
const reviewMode = ref<'approve' | 'reject'>('approve')
const reviewRecord = ref<PlatformRoleAssignmentRequestItem | null>(null)
const reviewComment = ref('')

const currentUserId = computed(() => extractUserIdFromJwt(user.value?.access_token))

const columns = [
  { title: 'ID', dataIndex: 'id', width: 72 },
  { title: '目标用户', dataIndex: 'targetUserId', width: 96 },
  { title: '角色', key: 'role', width: 160 },
  { title: '动作', dataIndex: 'actionType', width: 88 },
  { title: '状态', dataIndex: 'status', width: 104 },
  { title: '申请人', dataIndex: 'requestedBy', width: 88 },
  { title: '申请时间', dataIndex: 'requestedAt', width: 168 },
  { title: '备注', key: 'note', ellipsis: true },
  { title: '操作', key: 'actions', width: 220, fixed: 'right' as const },
]

function statusColor(status: string) {
  switch (status) {
    case 'APPLIED':
      return 'success'
    case 'PENDING':
      return 'processing'
    case 'FAILED':
      return 'error'
    case 'REJECTED':
    case 'CANCELED':
      return 'default'
    default:
      return 'blue'
  }
}

function isRequester(record: PlatformRoleAssignmentRequestItem) {
  const self = currentUserId.value
  return self != null && record.requestedBy === self
}

function filterRoleOption(input: string, option?: { label?: string }) {
  return (option?.label || '').toLowerCase().includes(input.toLowerCase())
}

function syncFiltersFromRoute() {
  const q = route.query
  const tid = q.targetUserId
  if (tid != null && tid !== '') {
    const n = Number(tid)
    filterTargetUserId.value = Number.isFinite(n) && n > 0 ? n : undefined
  }
  const st = q.status
  filterStatus.value = typeof st === 'string' && st.length > 0 ? st : undefined
}

async function loadRoleOptions() {
  if (!isPlatformScope.value || !canSubmit.value) {
    roleOptions.value = []
    roleOptionsReady.value = false
    return false
  }
  try {
    roleOptions.value = await listPlatformRoleOptions({ limit: 500 })
    roleOptionsReady.value = true
    return true
  } catch {
    roleOptions.value = []
    roleOptionsReady.value = false
    return false
  }
}

async function ensureRoleOptionsReady() {
  if (roleOptionsReady.value) {
    return true
  }
  const loaded = await loadRoleOptions()
  if (!loaded) {
    message.warning('无法加载可申请的平台角色候选，请确认当前会话具备平台角色审批提交权限后重试')
    return false
  }
  return true
}

async function loadTable() {
  if (!isPlatformScope.value || !canAccessPage.value) {
    return
  }
  loading.value = true
  try {
    const res = await listPlatformRoleAssignmentRequests({
      targetUserId: filterTargetUserId.value,
      status: filterStatus.value,
      current: pagination.value.current,
      pageSize: pagination.value.pageSize,
    })
    rows.value = res.records
    pagination.value.total = res.total
  } catch (e: any) {
    message.error(e?.message || '加载审批列表失败')
  } finally {
    loading.value = false
  }
}

function applyFilters() {
  pagination.value.current = 1
  loadTable()
}

function handleTableChange(pag: { current?: number; pageSize?: number }) {
  if (pag?.current != null) {
    pagination.value.current = pag.current
  }
  if (pag?.pageSize != null) {
    pagination.value.pageSize = pag.pageSize
  }
  loadTable()
}

async function openSubmitModal() {
  if (!canSubmit.value) {
    message.warning('当前会话缺少平台角色审批提交权限，无法发起申请')
    return
  }
  if (!(await ensureRoleOptionsReady())) {
    return
  }
  submitForm.value = {
    targetUserId: filterTargetUserId.value,
    roleId: undefined,
    actionType: 'GRANT',
    reason: '',
  }
  submitOpen.value = true
}

async function submitRequest() {
  if (!submitForm.value.targetUserId || !submitForm.value.roleId) {
    message.warning('请填写目标用户与角色')
    return
  }
  submitLoading.value = true
  try {
    await submitPlatformRoleAssignmentRequest({
      targetUserId: submitForm.value.targetUserId,
      roleId: submitForm.value.roleId,
      actionType: submitForm.value.actionType,
      reason: submitForm.value.reason?.trim() || undefined,
    })
    message.success('申请已提交')
    submitOpen.value = false
    await loadTable()
  } catch (e: any) {
    message.error(e?.message || '提交失败')
  } finally {
    submitLoading.value = false
  }
}

function openReviewModal(mode: 'approve' | 'reject', record: PlatformRoleAssignmentRequestItem) {
  reviewMode.value = mode
  reviewRecord.value = record
  reviewComment.value = ''
  reviewOpen.value = true
}

async function submitReview() {
  const rec = reviewRecord.value
  if (!rec) {
    return
  }
  reviewLoading.value = true
  try {
    if (reviewMode.value === 'approve') {
      const res = await approvePlatformRoleAssignmentRequest(rec.id, reviewComment.value.trim() || undefined)
      if (res.status === 'FAILED') {
        message.warning(res.applyError || '审批已完成但 RBAC3 校验未通过')
      } else {
        message.success('已通过并写入角色绑定')
      }
    } else {
      await rejectPlatformRoleAssignmentRequest(rec.id, reviewComment.value.trim() || undefined)
      message.success('已拒绝')
    }
    reviewOpen.value = false
    await loadTable()
  } catch (e: any) {
    message.error(e?.message || '操作失败')
  } finally {
    reviewLoading.value = false
  }
}

async function confirmCancel(record: PlatformRoleAssignmentRequestItem) {
  try {
    await cancelPlatformRoleAssignmentRequest(record.id)
    message.success('已撤销')
    await loadTable()
  } catch (e: any) {
    message.error(e?.message || '撤销失败')
  }
}

watch(
  () => route.query,
  () => {
    syncFiltersFromRoute()
    if (canAccessPage.value && isPlatformScope.value) {
      pagination.value.current = 1
      loadTable()
    }
  },
  { deep: true },
)

watch(
  [isPlatformScope, canSubmit],
  ([scope, submit]) => {
    if (!scope || !submit) {
      roleOptions.value = []
      roleOptionsReady.value = false
    }
  },
  { immediate: true },
)

onMounted(async () => {
  syncFiltersFromRoute()
  if (isPlatformScope.value && canAccessPage.value) {
    await loadTable()
  }
})
</script>

<style scoped>
.content-container {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #fff;
}

.scope-guard-card {
  margin: 24px;
}

.content-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.platform-page-shell {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.toolbar-container {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 24px;
  border-bottom: 1px solid #f0f0f0;
}

.table-title > span {
  font-size: 16px;
  font-weight: 600;
}

.table-title p {
  margin: 4px 0 0;
  color: #595959;
  font-size: 13px;
}

.form-container {
  padding: 16px 24px 0;
}

.approval-filters {
  row-gap: 8px;
}

.table-container {
  padding: 16px 24px 24px;
  flex: 1;
  min-height: 0;
}

.action-icon {
  cursor: pointer;
  font-size: 18px;
  color: #1677ff;
}

.review-hint {
  margin-bottom: 12px;
  color: #595959;
}

.platform-guard-card {
  margin: 24px;
  padding: 24px;
  border: 1px solid #d9e8ff;
  border-radius: 12px;
  background: linear-gradient(135deg, #f7fbff 0%, #ffffff 100%);
}

.platform-guard-kicker {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #1677ff;
}
</style>
