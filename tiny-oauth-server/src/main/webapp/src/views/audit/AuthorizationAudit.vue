<template>
  <div class="content-container">
    <div v-if="!canView" class="content-card">
      <div class="platform-guard-card">
        <div class="platform-guard-kicker">Permission Required</div>
        <h3>授权审计日志需要额外授权</h3>
        <p>
          当前页面属于后台配置面。只有具备 <code>system:audit:auth:view</code> 或管理员权限的用户，才能查看授权审计日志。
        </p>
      </div>
    </div>
    <div v-else class="content-card">
      <div class="form-container">
        <a-form layout="inline" :model="query">
          <a-form-item v-if="isPlatformScope" label="租户ID">
            <a-input v-model:value="query.tenantId" placeholder="请输入租户ID" style="width: 140px" />
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
          <a-form-item label="目标用户ID">
            <a-input v-model:value="query.targetUserId" placeholder="请输入用户ID" style="width: 160px" />
          </a-form-item>
          <a-form-item label="操作用户ID">
            <a-input v-model:value="query.actorUserId" placeholder="请输入操作用户ID" style="width: 160px" />
          </a-form-item>
          <a-form-item label="结果">
            <a-select
              v-model:value="query.result"
              placeholder="全部"
              style="width: 140px"
              allow-clear
              :options="resultOptions"
            />
          </a-form-item>
          <a-form-item label="权限码">
            <a-input v-model:value="query.resourcePermission" placeholder="请输入权限码" style="width: 220px" />
          </a-form-item>
          <a-form-item label="原因">
            <a-input v-model:value="query.detailReason" placeholder="请输入审计原因" style="width: 200px" />
          </a-form-item>
          <a-form-item label="载体类型">
            <a-select
              v-model:value="query.carrierType"
              placeholder="全部"
              style="width: 160px"
              allow-clear
              :options="carrierTypeOptions"
            />
          </a-form-item>
          <a-form-item label="requirementGroup">
            <a-input-number
              v-model:value="query.requirementGroup"
              placeholder="如 0"
              :min="0"
              style="width: 140px"
            />
          </a-form-item>
          <a-form-item label="决策(decision)">
            <a-select
              v-model:value="query.decision"
              placeholder="全部"
              style="width: 140px"
              allow-clear
              :options="decisionOptions"
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
        <div class="table-title">授权审计日志</div>
        <div class="table-actions">
          <a-button v-if="canExport" type="link" @click="handleExport" :loading="exporting">
            导出 CSV
          </a-button>
          <a-button v-if="canPurge" type="link" danger @click="openPurgeModal">
            <template #icon><DeleteOutlined /></template>
            清理日志
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
            <div class="summary-label">拒绝事件</div>
            <div class="summary-value danger">{{ summary.deniedCount }}</div>
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
            :row-key="(record: any) => String(record.id)"
            bordered
            :loading="loading"
            :scroll="{ x: 'max-content' }"
            :expandedRowKeys="expandedRowKeys"
            @expandedRowsChange="(keys) => (expandedRowKeys = keys)"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.dataIndex === 'eventType'">
                <a-tag>{{ record.eventType }}</a-tag>
              </template>
              <template v-else-if="column.dataIndex === 'result'">
                <a-tag :color="record.result === 'SUCCESS' ? 'green' : 'red'">
                  {{ record.result }}
                </a-tag>
              </template>
              <template v-else-if="column.dataIndex === 'createdAt'">
                {{ formatDateTime(record.createdAt) }}
              </template>
            </template>
            <template #expandedRowRender="{ record }">
              <div class="expanded-detail">
                <p><strong>事件详情：</strong></p>
                <pre class="detail-json">{{ formatJson(record.eventDetail) }}</pre>
                <p v-if="record.resultReason"><strong>结果原因：</strong>{{ record.resultReason }}</p>
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

    <a-modal
      v-model:open="purgeModalVisible"
      title="清理审计日志"
      @ok="handlePurge"
      @cancel="purgeModalVisible = false"
      :confirm-loading="purging"
    >
      <p>将删除指定保留天数之前的所有审计日志，此操作不可恢复。</p>
      <a-form layout="vertical">
        <a-form-item label="保留天数" required>
          <a-input-number v-model:value="retentionDays" :min="1" :max="3650" style="width: 200px" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useAuth } from '@/auth/auth'
import { message } from 'ant-design-vue'
import { ReloadOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import { decodeJwtPayload, extractAuthoritiesFromJwt } from '@/utils/jwt'
import {
  AUDIT_AUTH_EXPORT_AUTHORITIES,
  AUDIT_AUTH_PURGE_AUTHORITIES,
  AUDIT_AUTH_VIEW_AUTHORITIES,
} from '@/constants/permission'
import { AUTHORIZATION_AUDIT_EVENT_OPTIONS } from '@/constants/audit'
import {
  exportAuthorizationAuditLogs,
  getAuthorizationAuditSummary,
  listAuditLogs,
  purgeAuditLogs,
} from '@/api/audit'
import type { AuditLog, AuthorizationAuditQueryParams, AuthorizationAuditSummary } from '@/api/audit'
import type { Dayjs } from 'dayjs'
import type { Key } from 'ant-design-vue/es/_util/type'

const { user } = useAuth()
const claims = computed(() => decodeJwtPayload<{ activeScopeType?: unknown }>(user.value?.access_token))
const authorities = computed(() => new Set(extractAuthoritiesFromJwt(user.value?.access_token)))

function hasAnyAuthority(required: string[]) {
  return required.some((a) => authorities.value.has(a))
}

const canView = computed(() => hasAnyAuthority(AUDIT_AUTH_VIEW_AUTHORITIES))
const canExport = computed(() => hasAnyAuthority(AUDIT_AUTH_EXPORT_AUTHORITIES))
const canPurge = computed(() => hasAnyAuthority(AUDIT_AUTH_PURGE_AUTHORITIES))
const isPlatformScope = computed(() => claims.value?.activeScopeType === 'PLATFORM')
const eventTypeOptions = AUTHORIZATION_AUDIT_EVENT_OPTIONS
const resultOptions = [
  { label: 'SUCCESS', value: 'SUCCESS' },
  { label: 'DENIED', value: 'DENIED' },
]
const carrierTypeOptions = [
  { label: 'menu', value: 'menu' },
  { label: 'ui_action', value: 'ui_action' },
  { label: 'api_endpoint', value: 'api_endpoint' },
]
const decisionOptions = [
  { label: 'ALLOW', value: 'ALLOW' },
  { label: 'DENY', value: 'DENY' },
]

const query = ref<{
  tenantId: string
  eventType: string | undefined
  targetUserId: string
  actorUserId: string
  result: string | undefined
  resourcePermission: string
  detailReason: string
  carrierType: string | undefined
  requirementGroup: number | undefined
  decision: string | undefined
  dateRange: [Dayjs, Dayjs] | undefined
}>({
  tenantId: '',
  eventType: undefined,
  targetUserId: '',
  actorUserId: '',
  result: undefined,
  resourcePermission: '',
  detailReason: '',
  carrierType: undefined,
  requirementGroup: undefined,
  decision: undefined,
  dateRange: undefined,
})
const exportContext = ref({
  reason: '',
  ticketId: '',
})

const tableData = ref<AuditLog[]>([])
const summary = ref<AuthorizationAuditSummary>({
  totalCount: 0,
  successCount: 0,
  deniedCount: 0,
  eventTypeCounts: [],
})
const loading = ref(false)
const expandedRowKeys = ref<Key[]>([])
const pagination = ref({ current: 1, pageSize: 10, total: 0 })

const columns = [
  { title: 'ID', dataIndex: 'id', width: 80 },
  { title: '事件类型', dataIndex: 'eventType', width: 180 },
  { title: '操作用户', dataIndex: 'actorUserId', width: 100 },
  { title: '目标用户', dataIndex: 'targetUserId', width: 100 },
  { title: '角色ID', dataIndex: 'roleId', width: 80 },
  { title: '模块', dataIndex: 'module', width: 120 },
  { title: '权限', dataIndex: 'resourcePermission', width: 180 },
  { title: '结果', dataIndex: 'result', width: 100 },
  { title: 'IP地址', dataIndex: 'ipAddress', width: 140 },
  { title: '时间', dataIndex: 'createdAt', width: 180 },
]

const purgeModalVisible = ref(false)
const retentionDays = ref(90)
const purging = ref(false)
const exporting = ref(false)

function buildQueryParams(includePaging = false): AuthorizationAuditQueryParams {
  const params: AuthorizationAuditQueryParams = {}
  if (includePaging) {
    params.page = pagination.value.current - 1
    params.size = pagination.value.pageSize
  }
  if (isPlatformScope.value && query.value.tenantId) params.tenantId = query.value.tenantId
  if (query.value.eventType) params.eventType = query.value.eventType
  if (query.value.targetUserId) params.targetUserId = query.value.targetUserId
  if (query.value.actorUserId) params.actorUserId = query.value.actorUserId
  if (query.value.result) params.result = query.value.result
  if (query.value.resourcePermission) params.resourcePermission = query.value.resourcePermission
  if (query.value.detailReason) params.detailReason = query.value.detailReason
  if (query.value.carrierType) params.carrierType = query.value.carrierType
  if (query.value.requirementGroup !== undefined) {
    const value = Number(query.value.requirementGroup)
    if (!Number.isNaN(value)) params.requirementGroup = value
  }
  if (query.value.decision) params.decision = query.value.decision
  if (query.value.dateRange && query.value.dateRange[0]) {
    params.startTime = query.value.dateRange[0].toISOString()
    params.endTime = query.value.dateRange[1].toISOString()
  }
  return params
}

function buildExportParams(): AuthorizationAuditQueryParams {
  const params = buildQueryParams(false)
  if (exportContext.value.reason) params.reason = exportContext.value.reason
  if (exportContext.value.ticketId) params.ticketId = exportContext.value.ticketId
  return params
}

async function loadData() {
  if (!canView.value) return
  loading.value = true
  try {
    const res = await listAuditLogs(buildQueryParams(true))
    tableData.value = Array.isArray(res.content) ? res.content : []
    pagination.value.total = Number(res.totalElements) || 0
  } catch {
    tableData.value = []
    pagination.value.total = 0
  } finally {
    loading.value = false
  }
}

async function loadSummary() {
  if (!canView.value) return
  try {
    const res = await getAuthorizationAuditSummary(buildQueryParams(false))
    summary.value = {
      totalCount: Number(res.totalCount) || 0,
      successCount: Number(res.successCount) || 0,
      deniedCount: Number(res.deniedCount) || 0,
      eventTypeCounts: Array.isArray(res.eventTypeCounts) ? res.eventTypeCounts : [],
    }
  } catch {
    summary.value = {
      totalCount: 0,
      successCount: 0,
      deniedCount: 0,
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
    const blob = await exportAuthorizationAuditLogs(buildExportParams())
    const url = window.URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = buildCsvDownloadName('authorization_audit')
    anchor.click()
    window.URL.revokeObjectURL(url)
    message.success('导出成功，文件已开始下载')
  } catch (error: any) {
    message.error('导出失败: ' + (error?.message || '未知错误'))
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
    eventType: undefined,
    targetUserId: '',
    actorUserId: '',
    result: undefined,
    resourcePermission: '',
    detailReason: '',
    carrierType: undefined,
    requirementGroup: undefined,
    decision: undefined,
    dateRange: undefined,
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

function openPurgeModal() {
  retentionDays.value = 90
  purgeModalVisible.value = true
}

async function handlePurge() {
  if (!retentionDays.value || retentionDays.value < 1) {
    message.warning('请输入有效的保留天数')
    return
  }
  purging.value = true
  try {
    await purgeAuditLogs(retentionDays.value)
    message.success(`已清理 ${retentionDays.value} 天前的审计日志`)
    purgeModalVisible.value = false
    reloadAll()
  } catch (error: any) {
    message.error('清理失败: ' + (error.message || '未知错误'))
  } finally {
    purging.value = false
  }
}

function formatDateTime(dateTime: string | null | undefined): string {
  if (!dateTime) return '-'
  try {
    return new Date(dateTime).toLocaleString('zh-CN', {
      year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit', second: '2-digit',
    })
  } catch {
    return '-'
  }
}

function formatJson(value: string | null | undefined): string {
  if (!value) return '-'
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

onMounted(() => {
  if (canView.value) reloadAll()
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
  grid-template-columns: repeat(3, minmax(0, 1fr));
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
  font-size: 28px;
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
.detail-json {
  background: #f5f5f5;
  padding: 12px;
  border-radius: 4px;
  font-size: 13px;
  max-height: 300px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
@media (max-width: 960px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
