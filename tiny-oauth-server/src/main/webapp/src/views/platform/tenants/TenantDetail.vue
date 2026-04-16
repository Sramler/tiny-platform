<template>
  <div class="content-container">
    <div class="content-card">
      <div class="toolbar">
        <a-button type="link" @click="goBack">{{ backButtonLabel }}</a-button>
        <a-button type="primary" ghost @click="reload">刷新</a-button>
      </div>

      <a-spin :spinning="loading">
        <template v-if="tenant">
          <div class="section">
            <h3>基础信息</h3>
            <div class="grid">
              <div><span class="label">租户编码</span><span>{{ tenant.code || '-' }}</span></div>
              <div><span class="label">租户名称</span><span>{{ tenant.name || '-' }}</span></div>
              <div><span class="label">域名</span><span>{{ tenant.domain || '-' }}</span></div>
              <div><span class="label">套餐</span><span>{{ tenant.planCode || '-' }}</span></div>
              <div><span class="label">联系人</span><span>{{ tenant.contactName || '-' }}</span></div>
              <div><span class="label">联系邮箱</span><span>{{ tenant.contactEmail || '-' }}</span></div>
              <div><span class="label">联系电话</span><span>{{ tenant.contactPhone || '-' }}</span></div>
              <div><span class="label">备注</span><span>{{ tenant.remark || '-' }}</span></div>
            </div>
          </div>

          <div class="section">
            <h3>生命周期</h3>
            <div class="grid">
              <div><span class="label">生命周期状态</span><a-tag :color="statusColor(tenant.lifecycleStatus)">{{ statusLabel(tenant.lifecycleStatus) }}</a-tag></div>
              <div><span class="label">启用状态</span><a-tag :color="tenant.enabled ? 'green' : 'red'">{{ tenant.enabled ? '启用' : '禁用' }}</a-tag></div>
              <div><span class="label">到期时间</span><span>{{ tenant.expiresAt || '-' }}</span></div>
              <div><span class="label">更新时间</span><span>{{ tenant.updatedAt || '-' }}</span></div>
            </div>
          </div>

          <div class="section">
            <h3>权限摘要</h3>
            <div v-if="permissionSummary" class="grid">
              <div><span class="label">角色总数</span><span>{{ permissionSummary.totalRoles }}</span></div>
              <div><span class="label">启用角色数</span><span>{{ permissionSummary.enabledRoles }}</span></div>
              <div><span class="label">权限总数</span><span>{{ permissionSummary.totalPermissions }}</span></div>
              <div><span class="label">已分配权限</span><span>{{ permissionSummary.assignedPermissions }}</span></div>
              <div><span class="label">载体总数</span><span>{{ permissionSummary.totalCarriers }}</span></div>
              <div><span class="label">已绑定 permission 载体</span><span>{{ permissionSummary.boundCarriers }}</span></div>
              <div><span class="label">菜单载体</span><span>{{ permissionSummary.menuCarriers }}</span></div>
              <div><span class="label">按钮载体</span><span>{{ permissionSummary.uiActionCarriers }}</span></div>
              <div><span class="label">API 载体</span><span>{{ permissionSummary.apiEndpointCarriers }}</span></div>
            </div>
          </div>

          <div class="section">
            <h3>模板差异</h3>
            <div v-if="diffResult" class="diff-summary">
              <a-tag color="gold">MISSING_IN_TENANT {{ diffResult.summary.missingInTenant }}</a-tag>
              <a-tag color="blue">EXTRA_IN_TENANT {{ diffResult.summary.extraInTenant }}</a-tag>
              <a-tag color="orange">CHANGED {{ diffResult.summary.changed }}</a-tag>
            </div>
            <a-table
              :columns="diffColumns"
              :data-source="displayDiffs"
              :pagination="{ pageSize: 20 }"
              :row-key="(record: DisplayDiffRow) => record.key + record.diffType"
              size="small"
            />
          </div>
        </template>
      </a-spin>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import type { ColumnsType } from 'ant-design-vue/es/table'
import { sanitizeInternalRedirect } from '@/utils/redirect'
import {
  diffTenantPlatformTemplate,
  getTenantById,
  getTenantPermissionSummary,
  type PlatformTemplateEntryDiff,
  type Tenant,
  type TenantPermissionSummary,
} from '@/api/tenant'

type DisplayDiffRow = {
  key: string
  carrierType: string
  diffType: string
  fieldDiffText: string
}

const FIELD_ORDER = ['enabled', 'title', 'path', 'uri', 'method', 'requiredPermissionId', 'sort']
const route = useRoute()
const router = useRouter()
const loading = ref(false)
const tenant = ref<Tenant | null>(null)
const permissionSummary = ref<TenantPermissionSummary | null>(null)
const diffResult = ref<{ summary: { missingInTenant: number; extraInTenant: number; changed: number }; diffs: PlatformTemplateEntryDiff[] } | null>(null)

const diffColumns: ColumnsType<DisplayDiffRow> = [
  { title: '差异类型', dataIndex: 'diffType', key: 'diffType', width: 180 },
  { title: '载体类型', dataIndex: 'carrierType', key: 'carrierType', width: 140 },
  { title: '键', dataIndex: 'key', key: 'key', width: 320 },
  { title: '字段差异', dataIndex: 'fieldDiffText', key: 'fieldDiffText' },
]

const tenantId = computed(() => route.params.id as string)
const backTarget = computed(() =>
  sanitizeInternalRedirect(typeof route.query.from === 'string' ? route.query.from : null, '/platform/tenants'),
)
const backButtonLabel = computed(() =>
  backTarget.value.startsWith('/platform/users') ? '返回租户用户代管' : '返回租户列表',
)
const displayDiffs = computed<DisplayDiffRow[]>(() => {
  if (!diffResult.value) return []
  return diffResult.value.diffs.map((item) => ({
    key: item.key,
    carrierType: item.carrierType,
    diffType: item.diffType,
    fieldDiffText: formatFieldDiffs(item),
  }))
})

function formatFieldDiffs(entry: PlatformTemplateEntryDiff) {
  if (!entry.fieldDiffs || Object.keys(entry.fieldDiffs).length === 0) {
    return '-'
  }
  return FIELD_ORDER
    .filter((field) => entry.fieldDiffs[field])
    .map((field) => `${field}: ${entry.fieldDiffs[field].platformValue} -> ${entry.fieldDiffs[field].tenantValue}`)
    .join(' | ')
}

function statusLabel(status?: string) {
  if (status === 'FROZEN') return '已冻结'
  if (status === 'DECOMMISSIONED') return '已下线'
  return '运行中'
}

function statusColor(status?: string) {
  if (status === 'FROZEN') return 'orange'
  if (status === 'DECOMMISSIONED') return 'red'
  return 'green'
}

async function loadTenantControlPlaneData() {
  if (!tenantId.value) return
  loading.value = true
  try {
    const [tenantResp, permissionResp, diffResp] = await Promise.all([
      getTenantById(tenantId.value),
      getTenantPermissionSummary(tenantId.value),
      diffTenantPlatformTemplate(tenantId.value),
    ])
    tenant.value = tenantResp
    permissionSummary.value = permissionResp
    diffResult.value = diffResp
  } catch (error: any) {
    message.error(`加载租户控制面失败: ${error?.message || '未知错误'}`)
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.push(backTarget.value)
}

function reload() {
  void loadTenantControlPlaneData()
}

watch(tenantId, () => {
  tenant.value = null
  permissionSummary.value = null
  diffResult.value = null
  void loadTenantControlPlaneData()
}, { immediate: true })
</script>

<style scoped>
.content-container {
  height: 100%;
  background: #fff;
}

.content-card {
  padding: 16px 24px 24px;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  margin-bottom: 12px;
}

.section {
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 12px;
}

.grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(220px, 1fr));
  gap: 12px;
}

.label {
  display: inline-block;
  min-width: 120px;
  color: #8c8c8c;
}

.diff-summary {
  margin-bottom: 8px;
}
</style>
