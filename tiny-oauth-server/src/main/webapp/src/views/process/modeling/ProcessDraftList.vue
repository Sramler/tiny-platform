<template>
  <div class="process-draft-list" data-testid="process-draft-list">
    <div class="draft-toolbar">
      <div class="draft-toolbar-title">
        <h3>流程草稿</h3>
        <span class="draft-count">{{ draftGroups.length }} 个流程 · {{ totalVersionCount }} 个版本</span>
      </div>
      <div class="draft-toolbar-actions">
        <a-button size="small" @click="loadModels" :loading="loading">
          <template #icon>
            <ReloadOutlined />
          </template>
          刷新
        </a-button>
        <a-button type="primary" size="small" @click="createDraft" :loading="creating">
          <template #icon>
            <PlusOutlined />
          </template>
          新建草稿
        </a-button>
      </div>
    </div>

    <a-alert
      v-if="loadError"
      class="draft-alert"
      type="error"
      show-icon
      :message="loadError"
    />

    <a-empty v-else-if="!loading && draftGroups.length === 0" description="暂无流程草稿" />

    <a-table
      v-else
      class="draft-table"
      size="small"
      :columns="assetColumns"
      :data-source="draftGroups"
      :expanded-row-keys="expandedRowKeys"
      :loading="loading"
      :pagination="{ pageSize: 10, showSizeChanger: false }"
      :row-key="groupRowKey"
      :scroll="{ x: 1380 }"
      @expand="handleExpand"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'name'">
          <a-button type="link" class="draft-name-button" @click="openDesign(latestModelOf(record))">
            <span class="single-line-text">{{ groupName(record) }}</span>
          </a-button>
        </template>

        <template v-else-if="column.key === 'modelKey'">
          <span class="single-line-text">{{ toProcessModelGroup(record).modelKey }}</span>
        </template>

        <template v-else-if="column.key === 'latestVersion'">
          <span class="draft-version">v{{ toProcessModelGroup(record).latestDesignVersion }}</span>
        </template>

        <template v-else-if="column.key === 'currentRuntimeVersion'">
          <a-tag :color="toProcessModelGroup(record).currentRuntimeVersion ? 'green' : 'default'">
            {{ currentRuntimeLabel(toProcessModelGroup(record)) }}
          </a-tag>
        </template>

        <template v-else-if="column.key === 'versionCount'">
          <a-button type="link" size="small" class="draft-version-count" @click="toggleGroup(record)">
            <template #icon>
              <UpOutlined v-if="isExpanded(record)" />
              <DownOutlined v-else />
            </template>
            {{ toProcessModelGroup(record).versionCount }} 个版本
          </a-button>
        </template>

        <template v-else-if="column.key === 'status'">
          <a-tag :color="statusColor(latestModelOf(record).status)">
            {{ statusLabel(latestModelOf(record).status) }}
          </a-tag>
        </template>

        <template v-else-if="column.key === 'validationStatus'">
          <a-tag :color="validationColor(latestModelOf(record).validationStatus)">
            {{ validationLabel(latestModelOf(record).validationStatus) }}
          </a-tag>
        </template>

        <template v-else-if="column.key === 'hasUndeployedChanges'">
          <a-tag :color="toProcessModelGroup(record).hasUndeployedChanges ? 'orange' : 'default'">
            {{ toProcessModelGroup(record).hasUndeployedChanges ? '有变更' : '无差异' }}
          </a-tag>
        </template>

        <template v-else-if="column.key === 'scope'">
          <span class="single-line-text">{{ scopeLabel(latestModelOf(record)) }}</span>
        </template>

        <template v-else-if="column.key === 'updatedBy'">
          <span class="single-line-text">{{ toProcessModelGroup(record).updatedBy || '-' }}</span>
        </template>

        <template v-else-if="column.key === 'updatedAt'">
          <span class="single-line-text">{{ formatDateTime(toProcessModelGroup(record).updatedAt) }}</span>
        </template>

        <template v-else-if="column.key === 'actions'">
          <div class="draft-row-actions">
            <a-button type="link" size="small" @click="openDesign(latestModelOf(record))">
              <template #icon>
                <EditOutlined />
              </template>
              设计最新
            </a-button>
            <a-button
              type="link"
              size="small"
              @click="validateModel(latestModelOf(record))"
              :loading="validatingId === latestModelOf(record).id"
            >
              <template #icon>
                <CheckCircleOutlined />
              </template>
              校验
            </a-button>
            <a-button
              type="link"
              size="small"
              @click="deployModel(latestModelOf(record))"
              :loading="deployingId === latestModelOf(record).id"
            >
              <template #icon>
                <RocketOutlined />
              </template>
              部署
            </a-button>
          </div>
        </template>
      </template>

      <template #expandedRowRender="{ record }">
        <a-table
          class="draft-version-table"
          size="small"
          :columns="versionColumns"
          :data-source="toProcessModelGroup(record).versions"
          :pagination="false"
          :row-key="modelRowKey"
        >
          <template #bodyCell="{ column: versionColumn, record: versionRecord }">
            <template v-if="versionColumn.key === 'version'">
              <span class="draft-version">v{{ toProcessModel(versionRecord).version }}</span>
            </template>

            <template v-else-if="versionColumn.key === 'name'">
              <span class="single-line-text">{{ modelName(toProcessModel(versionRecord)) }}</span>
            </template>

            <template v-else-if="versionColumn.key === 'status'">
              <a-tag :color="statusColor(toProcessModel(versionRecord).status)">
                {{ statusLabel(toProcessModel(versionRecord).status) }}
              </a-tag>
            </template>

            <template v-else-if="versionColumn.key === 'runtimeState'">
              <a-tag :color="runtimeStateColor(toProcessModel(versionRecord).runtimeState)">
                {{ runtimeStateLabel(toProcessModel(versionRecord).runtimeState) }}
              </a-tag>
            </template>

            <template v-else-if="versionColumn.key === 'validationStatus'">
              <a-tag :color="validationColor(toProcessModel(versionRecord).validationStatus)">
                {{ validationLabel(toProcessModel(versionRecord).validationStatus) }}
              </a-tag>
            </template>

            <template v-else-if="versionColumn.key === 'updatedBy'">
              <span class="single-line-text">{{ toProcessModel(versionRecord).updatedBy || '-' }}</span>
            </template>

            <template v-else-if="versionColumn.key === 'updatedAt'">
              <span class="single-line-text">{{ formatDateTime(toProcessModel(versionRecord).updatedAt) }}</span>
            </template>

            <template v-else-if="versionColumn.key === 'deploymentId'">
              <span class="single-line-text">{{ toProcessModel(versionRecord).deploymentId || '-' }}</span>
            </template>

            <template v-else-if="versionColumn.key === 'actions'">
              <div class="draft-row-actions">
                <a-button type="link" size="small" @click="openDesign(toProcessModel(versionRecord))">
                  <template #icon>
                    <EditOutlined />
                  </template>
                  设计
                </a-button>
                <a-button
                  type="link"
                  size="small"
                  @click="validateModel(toProcessModel(versionRecord))"
                  :loading="validatingId === toProcessModel(versionRecord).id"
                >
                  <template #icon>
                    <CheckCircleOutlined />
                  </template>
                  校验
                </a-button>
                <a-button
                  type="link"
                  size="small"
                  @click="deployModel(toProcessModel(versionRecord))"
                  :loading="deployingId === toProcessModel(versionRecord).id"
                >
                  <template #icon>
                    <RocketOutlined />
                  </template>
                  部署
                </a-button>
              </div>
            </template>
          </template>
        </a-table>
      </template>
    </a-table>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import {
  CheckCircleOutlined,
  DownOutlined,
  EditOutlined,
  PlusOutlined,
  ReloadOutlined,
  RocketOutlined,
  UpOutlined,
} from '@ant-design/icons-vue'
import {
  processModelApi,
  type ProcessModel,
  type ProcessModelGroup,
  type ProcessModelRuntimeState,
  type ProcessModelStatus,
  type ProcessModelValidationStatus,
} from '@/api/process'
import { EMPTY_PROCESS_XML } from '@/utils/bpmn/modeler'

const emit = defineEmits<{
  (e: 'open-design', model: ProcessModel): void
}>()

type ProcessModelGroupView = ProcessModelGroup & {
  groupKey: string
}
type ProcessModelGroupTableRecord = ProcessModelGroupView | Record<string, unknown>
type ProcessModelTableRecord = ProcessModel | Record<string, unknown>

const draftGroups = ref<ProcessModelGroupView[]>([])
const loading = ref(false)
const creating = ref(false)
const validatingId = ref<number | null>(null)
const deployingId = ref<number | null>(null)
const expandedGroupKey = ref<string | null>(null)
const loadError = ref('')

const totalVersionCount = computed(() =>
  draftGroups.value.reduce((total, group) => total + group.versionCount, 0),
)

const expandedRowKeys = computed(() => expandedGroupKey.value ? [expandedGroupKey.value] : [])

const assetColumns = [
  { title: '名称', key: 'name', width: 180 },
  { title: 'Key', key: 'modelKey', width: 180 },
  { title: '最新设计', key: 'latestVersion', width: 90 },
  { title: '当前运行', key: 'currentRuntimeVersion', width: 110 },
  { title: '版本数', key: 'versionCount', width: 110 },
  { title: '状态', key: 'status', width: 100 },
  { title: '校验', key: 'validationStatus', width: 110 },
  { title: '未部署变更', key: 'hasUndeployedChanges', width: 120 },
  { title: 'Scope', key: 'scope', width: 120 },
  { title: '更新人', key: 'updatedBy', width: 110 },
  { title: '更新时间', key: 'updatedAt', width: 170 },
  { title: '操作', key: 'actions', width: 250 },
]

const versionColumns = [
  { title: '版本', key: 'version', width: 80 },
  { title: '名称', key: 'name', width: 200 },
  { title: '设计状态', key: 'status', width: 100 },
  { title: '部署态', key: 'runtimeState', width: 120 },
  { title: '校验', key: 'validationStatus', width: 110 },
  { title: '更新人', key: 'updatedBy', width: 110 },
  { title: '更新时间', key: 'updatedAt', width: 170 },
  { title: '部署 ID', key: 'deploymentId', width: 160 },
  { title: '操作', key: 'actions', width: 220 },
]

function groupKey(model: Pick<ProcessModel, 'modelKey' | 'scopeType' | 'recordTenantId'>) {
  return `${model.scopeType}:${model.recordTenantId ?? '0'}:${model.modelKey}`
}

function normalizeError(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback
}

function buildDraftKey() {
  return `Process_${Date.now()}`
}

function escapeXmlAttribute(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/"/g, '&quot;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

function buildDraftXml(modelKey: string, modelName: string) {
  return EMPTY_PROCESS_XML
    .replace('id="NewProcess" name="新建流程"', `id="${escapeXmlAttribute(modelKey)}" name="${escapeXmlAttribute(modelName)}"`)
    .replace('bpmnElement="NewProcess"', `bpmnElement="${escapeXmlAttribute(modelKey)}"`)
}

async function loadModels() {
  loading.value = true
  loadError.value = ''
  try {
    const groups = await processModelApi.listModelGroups()
    draftGroups.value = groups.map(normalizeGroup)
  } catch (error) {
    console.error('加载流程草稿失败:', error)
    loadError.value = normalizeError(error, '加载流程草稿失败')
  } finally {
    loading.value = false
  }
}

async function createDraft() {
  creating.value = true
  try {
    const modelKey = buildDraftKey()
    const name = '新建流程草稿'
    const model = await processModelApi.createModel({
      modelKey,
      name,
      bpmnXml: buildDraftXml(modelKey, name),
    })
    message.success('流程草稿已创建')
    await loadModels()
    emit('open-design', model)
  } catch (error) {
    console.error('创建流程草稿失败:', error)
    message.error('创建流程草稿失败：' + normalizeError(error, '未知错误'))
  } finally {
    creating.value = false
  }
}

function normalizeGroup(group: ProcessModelGroup): ProcessModelGroupView {
  const versions = [...group.versions].sort(compareVersionDesc)
  const latestModel = group.latestModel || versions[0]
  const currentRuntime = currentRuntimeModel(versions)
  const latestDesignVersion = group.latestDesignVersion || group.latestVersion || latestModel?.version || 1
  const currentRuntimeVersion = group.currentRuntimeVersion ?? currentRuntime?.version
  return {
    ...group,
    name: group.name || latestModel?.name || group.modelKey,
    latestVersion: group.latestVersion || latestModel?.version || 1,
    latestDesignVersion,
    latestStatus: group.latestStatus || latestModel?.status,
    currentRuntimeVersion,
    currentDeploymentId: group.currentDeploymentId ?? currentRuntime?.deploymentId,
    hasUndeployedChanges: group.hasUndeployedChanges ?? hasUndeployedChanges(latestDesignVersion, currentRuntimeVersion),
    versionCount: group.versionCount || versions.length,
    updatedAt: group.updatedAt || latestUpdatedModel(versions)?.updatedAt,
    updatedBy: group.updatedBy || latestUpdatedModel(versions)?.updatedBy,
    latestModel,
    versions,
    groupKey: groupKey(latestModel),
  }
}

function currentRuntimeModel(versions: ProcessModel[]) {
  return versions.find((model) => model.runtimeState === 'CURRENT_RUNTIME')
}

function hasUndeployedChanges(latestDesignVersion?: number, currentRuntimeVersion?: number | null) {
  if (!latestDesignVersion) {
    return false
  }
  return !currentRuntimeVersion || latestDesignVersion > currentRuntimeVersion
}

function toProcessModelGroup(record: ProcessModelGroupTableRecord) {
  return record as ProcessModelGroupView
}

function toProcessModel(record: ProcessModelTableRecord) {
  return record as ProcessModel
}

function latestModelOf(record: ProcessModelGroupTableRecord) {
  return toProcessModelGroup(record).latestModel
}

function groupName(record: ProcessModelGroupTableRecord) {
  const group = toProcessModelGroup(record)
  return group.name || group.modelKey
}

function modelName(model: ProcessModel) {
  return model.name || model.modelKey
}

function groupRowKey(record: ProcessModelGroupTableRecord) {
  return toProcessModelGroup(record).groupKey
}

function modelRowKey(record: ProcessModelTableRecord) {
  return toProcessModel(record).id
}

function compareVersionDesc(left: ProcessModel, right: ProcessModel) {
  if (right.version !== left.version) {
    return right.version - left.version
  }
  return right.id - left.id
}

function latestUpdatedModel(versions: ProcessModel[]) {
  return [...versions].sort((left, right) => {
    const leftTime = Date.parse(left.updatedAt || '')
    const rightTime = Date.parse(right.updatedAt || '')
    return (Number.isNaN(rightTime) ? 0 : rightTime) - (Number.isNaN(leftTime) ? 0 : leftTime)
  })[0]
}

function toggleVersions(key: string) {
  expandedGroupKey.value = expandedGroupKey.value === key ? null : key
}

function toggleGroup(record: ProcessModelGroupTableRecord) {
  toggleVersions(toProcessModelGroup(record).groupKey)
}

function isExpanded(record: ProcessModelGroupTableRecord) {
  return expandedGroupKey.value === toProcessModelGroup(record).groupKey
}

function handleExpand(expanded: boolean, record: ProcessModelGroupTableRecord) {
  expandedGroupKey.value = expanded ? toProcessModelGroup(record).groupKey : null
}

function openDesign(model: ProcessModel) {
  emit('open-design', model)
}

async function validateModel(model: ProcessModel) {
  validatingId.value = model.id
  try {
    const result = await processModelApi.validateModel(model.id)
    if (result.valid) {
      message.success(result.message || '流程模型校验完成')
    } else {
      message.warning(result.message || '流程模型校验完成')
    }
    await loadModels()
  } catch (error) {
    console.error('校验流程草稿失败:', error)
    message.error('校验流程草稿失败：' + normalizeError(error, '未知错误'))
  } finally {
    validatingId.value = null
  }
}

async function deployModel(model: ProcessModel) {
  deployingId.value = model.id
  try {
    const result = await processModelApi.deployModel(model.id)
    message.success(result.message || '流程模型部署成功')
    await loadModels()
  } catch (error) {
    console.error('部署流程草稿失败:', error)
    message.error('部署流程草稿失败：' + normalizeError(error, '未知错误'))
  } finally {
    deployingId.value = null
  }
}

function statusColor(status: ProcessModelStatus) {
  return {
    DRAFT: 'blue',
    VALIDATED: 'green',
    DEPLOYED: 'purple',
    ARCHIVED: 'default',
  }[status] ?? 'default'
}

function statusLabel(status: ProcessModelStatus) {
  return {
    DRAFT: '草稿',
    VALIDATED: '已校验',
    DEPLOYED: '已部署',
    ARCHIVED: '已归档',
  }[status] ?? status
}

function validationColor(status: ProcessModelValidationStatus) {
  return {
    NOT_VALIDATED: 'default',
    PASSED: 'green',
    FAILED: 'red',
  }[status] ?? 'default'
}

function validationLabel(status: ProcessModelValidationStatus) {
  return {
    NOT_VALIDATED: '未校验',
    PASSED: '通过',
    FAILED: '失败',
  }[status] ?? status
}

function runtimeStateColor(state: ProcessModelRuntimeState) {
  return {
    NOT_DEPLOYED: 'default',
    CURRENT_RUNTIME: 'green',
    HISTORICAL_DEPLOYED: 'purple',
  }[state] ?? 'default'
}

function runtimeStateLabel(state: ProcessModelRuntimeState) {
  return {
    NOT_DEPLOYED: '未部署',
    CURRENT_RUNTIME: '当前运行',
    HISTORICAL_DEPLOYED: '历史已部署',
  }[state] ?? state
}

function currentRuntimeLabel(group: ProcessModelGroupView) {
  return group.currentRuntimeVersion ? `v${group.currentRuntimeVersion}` : '未部署'
}

function scopeLabel(model: Pick<ProcessModel, 'scopeType' | 'recordTenantId'>) {
  return model.scopeType === 'PLATFORM'
    ? '平台'
    : `租户 ${model.recordTenantId ?? ''}`.trim()
}

function formatDateTime(value?: string) {
  if (!value) {
    return '-'
  }
  return value.replace('T', ' ').slice(0, 19)
}

onMounted(loadModels)
</script>

<style scoped>
.process-draft-list {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  padding: 16px 20px;
  background: #fff;
}

.draft-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}

.draft-toolbar-title {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.draft-toolbar-title h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 650;
}

.draft-count {
  color: #667085;
  font-size: 12px;
}

.draft-toolbar-actions,
.draft-row-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: nowrap;
  white-space: nowrap;
}

.draft-alert {
  margin-bottom: 12px;
}

:deep(.draft-table .ant-table-cell),
:deep(.draft-version-table .ant-table-cell) {
  white-space: nowrap;
}

.draft-name-button {
  max-width: 100%;
  height: auto;
  padding: 0;
  font-weight: 600;
}

.single-line-text {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  vertical-align: bottom;
  white-space: nowrap;
}

.draft-version {
  color: #344054;
  font-weight: 600;
  white-space: nowrap;
}

.draft-version-count {
  padding: 0;
}

.draft-row-actions {
  justify-content: flex-end;
}

:deep(.draft-table .ant-table-expanded-row > .ant-table-cell) {
  padding: 10px 16px;
  background: #fafafa;
}

.draft-version-table {
  margin: 0;
}
</style>
