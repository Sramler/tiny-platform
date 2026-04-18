<template>
  <div class="content-container">
    <a-tabs v-model:activeKey="activeTab">
      <a-tab-pane key="type" tab="平台字典类型">
        <DictType @view-items="handleViewItems" :platform-mode="true" />
      </a-tab-pane>
      <a-tab-pane key="item" tab="平台字典项">
        <DictItem ref="dictItemRef" :platform-mode="true" />
      </a-tab-pane>
      <a-tab-pane key="overrides" tab="租户覆盖关系">
        <div class="overrides-panel">
          <a-form layout="inline">
            <a-form-item label="平台字典类型">
              <a-select
                v-model:value="selectedDictTypeId"
                placeholder="请选择平台字典类型"
                style="width: 320px"
                allow-clear
                @change="handleTypeChanged"
              >
                <a-select-option v-for="item in dictTypeOptions" :key="item.id" :value="item.id">
                  {{ item.dictName }} ({{ item.dictCode }})
                </a-select-option>
              </a-select>
            </a-form-item>
            <a-form-item>
              <a-button type="primary" @click="loadOverrideSummary">刷新</a-button>
            </a-form-item>
          </a-form>

          <a-alert
            class="hint"
            type="info"
            show-icon
            message="摘要中覆盖项数仅统计命中平台基线的 override；孤儿覆盖单独计数。明细状态：INHERITED / OVERRIDDEN / ORPHAN_OVERLAY。"
          />

          <a-table
            :columns="summaryColumns"
            :data-source="overrideSummaries"
            :pagination="false"
            row-key="tenantId"
            :loading="summaryLoading"
            size="small"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.dataIndex === 'action'">
                <a @click.prevent="selectTenant(record)">查看差异</a>
              </template>
            </template>
          </a-table>

          <a-divider />

          <div v-if="selectedTenantId != null">
            <div class="detail-title">租户 {{ selectedTenantLabel }} 覆盖明细</div>
            <a-table
              :columns="detailColumns"
              :data-source="overrideDetails"
              :pagination="false"
              row-key="value"
              :loading="detailLoading"
              size="small"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.dataIndex === 'status'">
                  <a-tag :color="statusColor(record.status)">{{ record.status }}</a-tag>
                </template>
                <template v-else-if="column.dataIndex === 'labelChanged'">
                  <a-tag :color="record.labelChanged ? 'orange' : 'default'">
                    {{ record.labelChanged ? '已变化' : '无变化' }}
                  </a-tag>
                </template>
              </template>
            </a-table>
          </div>
        </div>
      </a-tab-pane>
    </a-tabs>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import DictType from '@/views/dict/dictType.vue'
import DictItem from '@/views/dict/dictItem.vue'
import {
  getPlatformVisibleDictTypes,
  getPlatformDictOverrides,
  getPlatformDictOverrideDetails,
  type DictTypeItem,
  type PlatformDictOverrideSummary,
  type PlatformDictOverrideDetail,
} from '@/api/dict'

const activeTab = ref('type')
const dictItemRef = ref<InstanceType<typeof DictItem> | null>(null)
const dictTypeOptions = ref<DictTypeItem[]>([])
const selectedDictTypeId = ref<number | undefined>(undefined)
const selectedTenantId = ref<number | undefined>(undefined)
const selectedTenantLabel = ref('')
const summaryLoading = ref(false)
const detailLoading = ref(false)
const overrideSummaries = ref<PlatformDictOverrideSummary[]>([])
const overrideDetails = ref<PlatformDictOverrideDetail[]>([])

const summaryColumns = [
  { title: '租户ID', dataIndex: 'tenantId' },
  { title: '租户编码', dataIndex: 'tenantCode' },
  { title: '租户名称', dataIndex: 'tenantName' },
  { title: '平台基线项数', dataIndex: 'baselineCount' },
  { title: '覆盖项数', dataIndex: 'overriddenCount' },
  { title: '继承项数', dataIndex: 'inheritedCount' },
  { title: '孤儿覆盖项数', dataIndex: 'orphanOverlayCount' },
  { title: '操作', dataIndex: 'action' },
]

const detailColumns = [
  { title: '字典值', dataIndex: 'value' },
  { title: '状态', dataIndex: 'status' },
  { title: '平台标签', dataIndex: 'baselineLabel' },
  { title: '租户标签', dataIndex: 'overlayLabel' },
  { title: '生效标签', dataIndex: 'effectiveLabel' },
  { title: '差异', dataIndex: 'labelChanged' },
]

function handleViewItems(dictTypeId: number) {
  activeTab.value = 'item'
  nextTick(() => dictItemRef.value?.setDictTypeId(dictTypeId))
}

async function loadTypeOptions() {
  dictTypeOptions.value = await getPlatformVisibleDictTypes()
}

async function loadOverrideSummary() {
  if (!selectedDictTypeId.value) {
    overrideSummaries.value = []
    return
  }
  summaryLoading.value = true
  try {
    overrideSummaries.value = await getPlatformDictOverrides(selectedDictTypeId.value)
  } catch (error: any) {
    message.error('加载租户覆盖摘要失败: ' + (error?.message || '未知错误'))
  } finally {
    summaryLoading.value = false
  }
}

async function selectTenant(record: PlatformDictOverrideSummary | Record<string, any>) {
  if (!selectedDictTypeId.value) {
    return
  }
  const tenantId = Number(record.tenantId)
  if (!Number.isFinite(tenantId)) {
    message.warning('租户覆盖摘要数据异常，缺少 tenantId')
    return
  }
  selectedTenantId.value = tenantId
  selectedTenantLabel.value = String(record.tenantName || record.tenantCode || tenantId)
  detailLoading.value = true
  try {
    overrideDetails.value = await getPlatformDictOverrideDetails(selectedDictTypeId.value, tenantId)
  } catch (error: any) {
    message.error('加载租户覆盖明细失败: ' + (error?.message || '未知错误'))
  } finally {
    detailLoading.value = false
  }
}

function handleTypeChanged() {
  selectedTenantId.value = undefined
  selectedTenantLabel.value = ''
  overrideDetails.value = []
  void loadOverrideSummary()
}

function statusColor(status: string) {
  if (status === 'OVERRIDDEN') return 'orange'
  if (status === 'INHERITED') return 'green'
  return 'red'
}

onMounted(async () => {
  await loadTypeOptions()
  selectedDictTypeId.value = dictTypeOptions.value[0]?.id
  if (selectedDictTypeId.value != null) {
    await loadOverrideSummary()
  }
})

defineExpose({
  loadOverrideSummary,
  selectTenant,
})
</script>

<style scoped>
.content-container {
  height: 100%;
  background: #fff;
}
.overrides-panel {
  padding: 8px 0;
}
.hint {
  margin: 12px 0;
}
.detail-title {
  margin-bottom: 8px;
  font-weight: 600;
}
</style>
