<template>
  <div class="content-container">
    <div v-if="!canView" class="content-card">
      <div class="platform-guard-card">
        <div class="platform-guard-kicker">Permission Required</div>
        <h3>认证审计需要额外授权</h3>
        <p>
          当前页面用于查看登录、登出、MFA 绑定和 Token 事件。只有具备
          <code>system:audit:authentication:view</code>
          或管理员权限的用户才能访问。
        </p>
      </div>
    </div>
    <div v-else class="content-card">
      <div class="form-container">
        <a-form layout="inline" :model="query">
          <a-form-item v-if="isPlatformScope" label="租户ID">
            <a-input v-model:value="query.tenantId" placeholder="请输入租户ID" style="width: 140px" />
          </a-form-item>
          <a-form-item label="用户ID">
            <a-input v-model:value="query.userId" placeholder="请输入用户ID" style="width: 140px" />
          </a-form-item>
          <a-form-item label="用户名">
            <a-input v-model:value="query.username" placeholder="请输入用户名" style="width: 180px" />
          </a-form-item>
          <a-form-item label="事件类型">
            <a-select
              v-model:value="query.eventType"
              placeholder="全部"
              style="width: 180px"
              allow-clear
              :options="eventTypeOptions"
            />
          </a-form-item>
          <a-form-item label="结果">
            <a-select
              v-model:value="query.success"
              placeholder="全部"
              style="width: 120px"
              allow-clear
              :options="successOptions"
            />
          </a-form-item>
          <a-form-item label="时间范围">
            <a-range-picker v-model:value="query.dateRange" show-time />
          </a-form-item>
          <a-form-item v-if="isPlatformScope && canExport" label="导出理由">
            <a-input v-model:value="exportContext.reason" placeholder="用于高敏感治理导出" style="width: 220px" />
          </a-form-item>
          <a-form-item v-if="isPlatformScope && canExport" label="工单号">
            <a-input v-model:value="exportContext.ticketId" placeholder="可选，用于审计追踪" style="width: 180px" />
          </a-form-item>
          <a-form-item>
            <a-button type="primary" @click="handleSearch">搜索</a-button>
            <a-button class="ml-2" @click="handleReset">重置</a-button>
          </a-form-item>
        </a-form>
      </div>
      <div class="toolbar-container">
        <div class="table-title">认证审计日志</div>
        <div class="table-actions">
          <a-button v-if="canExport" type="link" @click="handleExport" :loading="exporting">
            导出 CSV
          </a-button>
          <a-tooltip title="刷新">
            <span class="action-icon" @click="reloadAll">
              <ReloadOutlined :spin="loading" />
            </span>
          </a-tooltip>
        </div>
      </div>
      <div class="summary-container">
        <div class="summary-grid">
          <div class="summary-card">
            <div class="summary-label">日志总数</div>
            <div class="summary-value">{{ summary.totalCount }}</div>
          </div>
          <div class="summary-card">
            <div class="summary-label">成功事件</div>
            <div class="summary-value success">{{ summary.successCount }}</div>
          </div>
          <div class="summary-card">
            <div class="summary-label">失败事件</div>
            <div class="summary-value danger">{{ summary.failureCount }}</div>
          </div>
          <div class="summary-card">
            <div class="summary-label">登录成功</div>
            <div class="summary-value">{{ summary.loginSuccessCount }}</div>
          </div>
          <div class="summary-card">
            <div class="summary-label">登录失败</div>
            <div class="summary-value">{{ summary.loginFailureCount }}</div>
          </div>
        </div>
        <div v-if="summary.eventTypeCounts.length" class="summary-tags">
          <span class="summary-tags-label">高频事件</span>
          <span
            v-for="item in summary.eventTypeCounts.slice(0, 5)"
            :key="`${item.eventType}-${item.count}`"
            class="summary-tag"
          >
            {{ item.eventType }} · {{ item.count }}
          </span>
        </div>
      </div>
      <div class="table-container">
        <div class="table-scroll-area">
          <a-table
            :columns="columns"
            :data-source="tableData"
            :pagination="false"
            :row-key="(record: AuthenticationAuditLog) => String(record.id)"
            bordered
            :loading="loading"
            :scroll="{ x: 'max-content' }"
            :expandedRowKeys="expandedRowKeys"
            @expandedRowsChange="(keys: string[]) => (expandedRowKeys = keys)"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.dataIndex === 'eventType'">
                <a-tag>{{ record.eventType }}</a-tag>
              </template>
              <template v-else-if="column.dataIndex === 'success'">
                <a-tag :color="record.success ? 'green' : 'red'">
                  {{ record.success ? 'SUCCESS' : 'FAILED' }}
                </a-tag>
              </template>
              <template v-else-if="column.dataIndex === 'createdAt'">
                {{ formatDateTime(record.createdAt) }}
              </template>
            </template>
            <template #expandedRowRender="{ record }">
              <div class="expanded-detail">
                <p><strong>用户代理：</strong>{{ record.userAgent || '-' }}</p>
                <p><strong>Session ID：</strong>{{ record.sessionId || '-' }}</p>
                <p><strong>Token ID：</strong>{{ record.tokenId || '-' }}</p>
                <p><strong>租户解析：</strong>{{ record.tenantResolutionCode || '-' }} / {{ record.tenantResolutionSource || '-' }}</p>
              </div>
            </template>
          </a-table>
        </div>
        <div class="pagination-container">
          <a-pagination
            v-model:current="pagination.current"
            :page-size="pagination.pageSize"
            :total="pagination.total"
            show-size-changer
            :page-size-options="['10', '20', '50']"
            :show-total="(total: number) => `共 ${total} 条`"
            @change="handlePageChange"
            @showSizeChange="handlePageSizeChange"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import { useAuth } from '@/auth/auth'
import { AUTHENTICATION_AUDIT_EVENT_OPTIONS } from '@/constants/audit'
import {
  exportAuthenticationAuditLogs,
  getAuthenticationAuditSummary,
  listAuthenticationAuditLogs,
} from '@/api/audit'
import type {
  AuthenticationAuditLog,
  AuthenticationAuditQueryParams,
  AuthenticationAuditSummary,
} from '@/api/audit'
import {
  AUDIT_AUTHENTICATION_EXPORT_AUTHORITIES,
  AUDIT_AUTHENTICATION_VIEW_AUTHORITIES,
} from '@/constants/permission'
import { decodeJwtPayload, extractAuthoritiesFromJwt } from '@/utils/jwt'
import type { Dayjs } from 'dayjs'

type QueryModel = {
  tenantId: string
  userId: string
  username: string
  eventType: string | undefined
  success: boolean | undefined
  dateRange: [Dayjs, Dayjs] | null
}

const { user } = useAuth()
const claims = computed(() => decodeJwtPayload<{ activeScopeType?: unknown }>(user.value?.access_token))
const authorities = computed(() => new Set(extractAuthoritiesFromJwt(user.value?.access_token)))

function hasAnyAuthority(required: string[]) {
  return required.some((authority) => authorities.value.has(authority))
}

const canView = computed(() => hasAnyAuthority(AUDIT_AUTHENTICATION_VIEW_AUTHORITIES))
const canExport = computed(() => hasAnyAuthority(AUDIT_AUTHENTICATION_EXPORT_AUTHORITIES))
const isPlatformScope = computed(() => claims.value?.activeScopeType === 'PLATFORM')

const eventTypeOptions = AUTHENTICATION_AUDIT_EVENT_OPTIONS

const successOptions = [
  { label: '成功', value: true },
  { label: '失败', value: false },
]

const query = ref<QueryModel>({
  tenantId: '',
  userId: '',
  username: '',
  eventType: undefined,
  success: undefined,
  dateRange: null,
})
const exportContext = ref({
  reason: '',
  ticketId: '',
})

const tableData = ref<AuthenticationAuditLog[]>([])
const summary = ref<AuthenticationAuditSummary>({
  totalCount: 0,
  successCount: 0,
  failureCount: 0,
  loginSuccessCount: 0,
  loginFailureCount: 0,
  eventTypeCounts: [],
})
const loading = ref(false)
const exporting = ref(false)
const expandedRowKeys = ref<string[]>([])
const pagination = ref({ current: 1, pageSize: 10, total: 0 })

const columns = computed(() => {
  const baseColumns = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '用户ID', dataIndex: 'userId', width: 100 },
    { title: '用户名', dataIndex: 'username', width: 140 },
    { title: '事件类型', dataIndex: 'eventType', width: 150 },
    { title: '结果', dataIndex: 'success', width: 110 },
    { title: '认证提供方', dataIndex: 'authenticationProvider', width: 140 },
    { title: '认证因子', dataIndex: 'authenticationFactor', width: 140 },
    { title: 'IP地址', dataIndex: 'ipAddress', width: 140 },
    { title: '时间', dataIndex: 'createdAt', width: 180 },
  ]
  if (isPlatformScope.value) {
    baseColumns.splice(1, 0, { title: '租户ID', dataIndex: 'tenantId', width: 100 })
  }
  return baseColumns
})

function buildQueryParams(includePaging = false): AuthenticationAuditQueryParams {
  const params: AuthenticationAuditQueryParams = {}
  if (includePaging) {
    params.page = pagination.value.current - 1
    params.size = pagination.value.pageSize
  }
  if (isPlatformScope.value && query.value.tenantId) {
    params.tenantId = query.value.tenantId
  }
  if (query.value.userId) params.userId = query.value.userId
  if (query.value.username) params.username = query.value.username
  if (query.value.eventType) params.eventType = query.value.eventType
  if (typeof query.value.success === 'boolean') params.success = query.value.success
  if (query.value.dateRange && query.value.dateRange[0]) {
    params.startTime = query.value.dateRange[0].toISOString()
    params.endTime = query.value.dateRange[1].toISOString()
  }
  return params
}

function buildExportParams(): AuthenticationAuditQueryParams {
  const params = buildQueryParams(false)
  if (exportContext.value.reason) params.reason = exportContext.value.reason
  if (exportContext.value.ticketId) params.ticketId = exportContext.value.ticketId
  return params
}

async function loadData() {
  if (!canView.value) {
    return
  }
  loading.value = true
  try {
    const response = await listAuthenticationAuditLogs(buildQueryParams(true))
    tableData.value = Array.isArray(response.content) ? response.content : []
    pagination.value.total = Number(response.totalElements) || 0
  } catch (error: any) {
    tableData.value = []
    pagination.value.total = 0
    message.error(error?.message || '加载认证审计失败')
  } finally {
    loading.value = false
  }
}

async function loadSummary() {
  if (!canView.value) {
    return
  }
  try {
    const response = await getAuthenticationAuditSummary(buildQueryParams(false))
    summary.value = {
      totalCount: Number(response.totalCount) || 0,
      successCount: Number(response.successCount) || 0,
      failureCount: Number(response.failureCount) || 0,
      loginSuccessCount: Number(response.loginSuccessCount) || 0,
      loginFailureCount: Number(response.loginFailureCount) || 0,
      eventTypeCounts: Array.isArray(response.eventTypeCounts) ? response.eventTypeCounts : [],
    }
  } catch {
    summary.value = {
      totalCount: 0,
      successCount: 0,
      failureCount: 0,
      loginSuccessCount: 0,
      loginFailureCount: 0,
      eventTypeCounts: [],
    }
  }
}

async function reloadAll() {
  await Promise.all([loadData(), loadSummary()])
}

function buildCsvDownloadName(prefix: string) {
  const now = new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${prefix}_${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}_${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}.csv`
}

async function handleExport() {
  exporting.value = true
  try {
    const blob = await exportAuthenticationAuditLogs(buildExportParams())
    const url = window.URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = buildCsvDownloadName('authentication_audit')
    anchor.click()
    window.URL.revokeObjectURL(url)
    message.success('导出成功，文件已开始下载')
  } catch (error: any) {
    message.error(error?.message || '导出认证审计失败')
  } finally {
    exporting.value = false
  }
}

function handleSearch() {
  pagination.value.current = 1
  reloadAll()
}

function handleReset() {
  query.value = {
    tenantId: '',
    userId: '',
    username: '',
    eventType: undefined,
    success: undefined,
    dateRange: null,
  }
  exportContext.value = {
    reason: '',
    ticketId: '',
  }
  pagination.value.current = 1
  reloadAll()
}

function handlePageChange(page: number) {
  pagination.value.current = page
  loadData()
}

function handlePageSizeChange(_current: number, size: number) {
  pagination.value.pageSize = size
  pagination.value.current = 1
  loadData()
}

function formatDateTime(dateTime: string | null | undefined): string {
  if (!dateTime) return '-'
  try {
    return new Date(dateTime).toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    })
  } catch {
    return '-'
  }
}

onMounted(() => {
  if (canView.value) {
    reloadAll()
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

.content-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.form-container {
  padding: 24px;
  border-bottom: 1px solid #f0f0f0;
}

.toolbar-container {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #f0f0f0;
  padding: 8px 24px;
}
.summary-container {
  padding: 16px 24px;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.summary-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}
.summary-card {
  border: 1px solid #f0f0f0;
  border-radius: 12px;
  padding: 16px;
  background: linear-gradient(180deg, #ffffff 0%, #fafafa 100%);
}
.summary-label {
  color: #8c8c8c;
  font-size: 13px;
}
.summary-value {
  margin-top: 8px;
  font-size: 24px;
  line-height: 1;
  font-weight: 700;
  color: #1f1f1f;
}
.summary-value.success {
  color: #237804;
}
.summary-value.danger {
  color: #cf1322;
}
.summary-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
.summary-tags-label {
  color: #8c8c8c;
  font-size: 13px;
}
.summary-tag {
  border-radius: 999px;
  background: #f5f5f5;
  padding: 4px 10px;
  font-size: 12px;
  color: #434343;
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

.table-container {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}

.table-scroll-area {
  flex: 1;
  min-height: 0;
  overflow: auto;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.table-scroll-area::-webkit-scrollbar {
  display: none;
}

.pagination-container {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 12px 24px;
  min-height: 56px;
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
}

.action-icon:hover {
  color: #1890ff;
  background: #f5f5f5;
}

.ml-2 {
  margin-left: 8px;
}

.expanded-detail {
  padding: 12px 24px;
}
@media (max-width: 1200px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 720px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
