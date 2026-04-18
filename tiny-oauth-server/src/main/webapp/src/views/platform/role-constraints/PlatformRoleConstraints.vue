<template>
  <div class="content-container">
    <a-card v-if="!isPlatformScope" title="平台作用域限制" class="scope-guard-card">
      当前会话不是 PLATFORM 作用域，已阻止加载平台 RBAC3 控制面。请切换到平台作用域后访问本页。
    </a-card>

    <div v-else-if="!canAccessPage" class="content-card">
      <div class="platform-guard-card">
        <div class="platform-guard-kicker">Permission Required</div>
        <h3>平台 RBAC3 需要额外授权</h3>
        <p>
          本页仅平台作用域可用，且至少需要
          <code>system:role:constraint:view</code>、
          <code>system:role:constraint:edit</code>、
          <code>system:role:constraint:violation:view</code> 之一。
        </p>
      </div>
    </div>

    <div v-else class="content-card">
      <a-tabs v-model:activeKey="activeTab" @change="handleTabChange">
        <a-tab-pane v-if="canAccessConstraintTabs" key="hierarchy" tab="角色继承">
          <div class="tab-toolbar">
            <a-button v-if="canEditConstraint" type="link" @click="openHierarchyModal">
              <template #icon><PlusOutlined /></template>
              新建
            </a-button>
            <a-tooltip title="刷新">
              <span class="action-icon" @click="loadHierarchies">
                <ReloadOutlined :spin="hierarchyLoading" />
              </span>
            </a-tooltip>
          </div>
          <a-table
            :columns="hierarchyColumns"
            :data-source="hierarchies"
            :pagination="false"
            :row-key="hierarchyRowKey"
            bordered
            :loading="hierarchyLoading"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.dataIndex === 'parentRoleId'">{{ formatRoleCell(record.parentRoleId) }}</template>
              <template v-else-if="column.dataIndex === 'childRoleId'">{{ formatRoleCell(record.childRoleId) }}</template>
              <template v-else-if="column.dataIndex === 'action'">
                <a-button
                  v-if="canEditConstraint"
                  type="link"
                  size="small"
                  danger
                  @click="confirmDelete('hierarchy', record)"
                >
                  <template #icon><DeleteOutlined /></template>
                  删除
                </a-button>
              </template>
            </template>
          </a-table>
        </a-tab-pane>

        <a-tab-pane v-if="canAccessConstraintTabs" key="mutex" tab="互斥约束">
          <div class="tab-toolbar">
            <a-button v-if="canEditConstraint" type="link" @click="openMutexModal">
              <template #icon><PlusOutlined /></template>
              新建
            </a-button>
            <a-tooltip title="刷新">
              <span class="action-icon" @click="loadMutexes">
                <ReloadOutlined :spin="mutexLoading" />
              </span>
            </a-tooltip>
          </div>
          <a-table
            :columns="mutexColumns"
            :data-source="mutexes"
            :pagination="false"
            :row-key="mutexRowKey"
            bordered
            :loading="mutexLoading"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.dataIndex === 'leftRoleId'">{{ formatRoleCell(record.leftRoleId) }}</template>
              <template v-else-if="column.dataIndex === 'rightRoleId'">{{ formatRoleCell(record.rightRoleId) }}</template>
              <template v-else-if="column.dataIndex === 'action'">
                <a-button
                  v-if="canEditConstraint"
                  type="link"
                  size="small"
                  danger
                  @click="confirmDelete('mutex', record)"
                >
                  <template #icon><DeleteOutlined /></template>
                  删除
                </a-button>
              </template>
            </template>
          </a-table>
        </a-tab-pane>

        <a-tab-pane v-if="canAccessConstraintTabs" key="prerequisite" tab="先决条件">
          <div class="tab-toolbar">
            <a-button v-if="canEditConstraint" type="link" @click="openPrerequisiteModal">
              <template #icon><PlusOutlined /></template>
              新建
            </a-button>
            <a-tooltip title="刷新">
              <span class="action-icon" @click="loadPrerequisites">
                <ReloadOutlined :spin="prerequisiteLoading" />
              </span>
            </a-tooltip>
          </div>
          <a-table
            :columns="prerequisiteColumns"
            :data-source="prerequisites"
            :pagination="false"
            :row-key="prerequisiteRowKey"
            bordered
            :loading="prerequisiteLoading"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.dataIndex === 'roleId'">{{ formatRoleCell(record.roleId) }}</template>
              <template v-else-if="column.dataIndex === 'requiredRoleId'">{{ formatRoleCell(record.requiredRoleId) }}</template>
              <template v-else-if="column.dataIndex === 'action'">
                <a-button
                  v-if="canEditConstraint"
                  type="link"
                  size="small"
                  danger
                  @click="confirmDelete('prerequisite', record)"
                >
                  <template #icon><DeleteOutlined /></template>
                  删除
                </a-button>
              </template>
            </template>
          </a-table>
        </a-tab-pane>

        <a-tab-pane v-if="canAccessConstraintTabs" key="cardinality" tab="基数限制">
          <div class="tab-toolbar">
            <a-button v-if="canEditConstraint" type="link" @click="openCardinalityModal">
              <template #icon><PlusOutlined /></template>
              新建
            </a-button>
            <a-tooltip title="刷新">
              <span class="action-icon" @click="loadCardinalities">
                <ReloadOutlined :spin="cardinalityLoading" />
              </span>
            </a-tooltip>
          </div>
          <a-table
            :columns="cardinalityColumns"
            :data-source="cardinalities"
            :pagination="false"
            :row-key="cardinalityRowKey"
            bordered
            :loading="cardinalityLoading"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.dataIndex === 'roleId'">{{ formatRoleCell(record.roleId) }}</template>
              <template v-else-if="column.dataIndex === 'scopeType'">
                <a-tag color="blue">{{ record.scopeType }}</a-tag>
              </template>
              <template v-else-if="column.dataIndex === 'action'">
                <a-button
                  v-if="canEditConstraint"
                  type="link"
                  size="small"
                  danger
                  @click="confirmDelete('cardinality', record)"
                >
                  <template #icon><DeleteOutlined /></template>
                  删除
                </a-button>
              </template>
            </template>
          </a-table>
        </a-tab-pane>

        <a-tab-pane v-if="canViewViolations" key="violations" tab="违规记录">
          <div class="tab-toolbar">
            <a-tooltip title="刷新">
              <span class="action-icon" @click="loadViolations">
                <ReloadOutlined :spin="violationLoading" />
              </span>
            </a-tooltip>
          </div>
          <a-table
            :columns="violationColumns"
            :data-source="violations"
            :pagination="false"
            :row-key="(r: any) => String(r.id)"
            bordered
            :loading="violationLoading"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.dataIndex === 'violationType'">
                <a-tag>{{ record.violationType }}</a-tag>
              </template>
              <template v-else-if="column.dataIndex === 'createdAt'">{{ formatDateTime(record.createdAt) }}</template>
            </template>
          </a-table>
          <div class="pagination-container">
            <a-pagination
              v-model:current="violationPagination.current"
              :page-size="violationPagination.pageSize"
              :total="violationPagination.total"
              show-size-changer
              :page-size-options="['10', '20', '50']"
              :show-total="(total: number) => `共 ${total} 条`"
              @change="handleViolationPageChange"
              @showSizeChange="handleViolationPageSizeChange"
            />
          </div>
        </a-tab-pane>
      </a-tabs>
    </div>

    <a-modal v-model:open="hierarchyModalVisible" title="新建角色继承（平台）" @ok="submitHierarchy" :confirm-loading="submitting">
      <a-form layout="vertical">
        <a-form-item label="父角色" required>
          <a-select
            v-model:value="hierarchyForm.parentRoleId"
            show-search
            :filter-option="filterRoleOption"
            :options="roleSelectOptions"
            placeholder="选择父角色"
            allow-clear
          />
        </a-form-item>
        <a-form-item label="子角色" required>
          <a-select
            v-model:value="hierarchyForm.childRoleId"
            show-search
            :filter-option="filterRoleOption"
            :options="roleSelectOptions"
            placeholder="选择子角色"
            allow-clear
          />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="mutexModalVisible" title="新建互斥约束（平台）" @ok="submitMutex" :confirm-loading="submitting">
      <a-form layout="vertical">
        <a-form-item label="角色 A" required>
          <a-select
            v-model:value="mutexForm.roleIdA"
            show-search
            :filter-option="filterRoleOption"
            :options="roleSelectOptions"
            placeholder="选择角色"
            allow-clear
          />
        </a-form-item>
        <a-form-item label="角色 B" required>
          <a-select
            v-model:value="mutexForm.roleIdB"
            show-search
            :filter-option="filterRoleOption"
            :options="roleSelectOptions"
            placeholder="选择角色"
            allow-clear
          />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="prerequisiteModalVisible"
      title="新建先决条件（平台）"
      @ok="submitPrerequisite"
      :confirm-loading="submitting"
    >
      <a-form layout="vertical">
        <a-form-item label="目标角色" required>
          <a-select
            v-model:value="prerequisiteForm.roleId"
            show-search
            :filter-option="filterRoleOption"
            :options="roleSelectOptions"
            placeholder="选择角色"
            allow-clear
          />
        </a-form-item>
        <a-form-item label="前置角色" required>
          <a-select
            v-model:value="prerequisiteForm.requiredRoleId"
            show-search
            :filter-option="filterRoleOption"
            :options="roleSelectOptions"
            placeholder="选择前置角色"
            allow-clear
          />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="cardinalityModalVisible" title="新建基数限制（平台）" @ok="submitCardinality" :confirm-loading="submitting">
      <a-form layout="vertical">
        <a-form-item label="角色" required>
          <a-select
            v-model:value="cardinalityForm.roleId"
            show-search
            :filter-option="filterRoleOption"
            :options="roleSelectOptions"
            placeholder="选择角色"
            allow-clear
          />
        </a-form-item>
        <a-form-item label="作用域类型" required>
          <a-select v-model:value="cardinalityForm.scopeType" placeholder="请选择作用域类型">
            <a-select-option v-for="scopeType in scopeTypeOptions" :key="scopeType" :value="scopeType">
              {{ scopeType }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="最大分配数" required>
          <a-input-number v-model:value="cardinalityForm.maxAssignments" :min="1" style="width: 100%" placeholder="请输入最大分配数" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useAuth } from '@/auth/auth'
import { usePlatformScope } from '@/composables/usePlatformScope'
import { message, Modal } from 'ant-design-vue'
import { ReloadOutlined, PlusOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import type { Key } from 'ant-design-vue/es/_util/type'
import { extractAuthoritiesFromJwt } from '@/utils/jwt'
import {
  ROLE_CONSTRAINT_VIEW,
  ROLE_CONSTRAINT_EDIT,
  ROLE_CONSTRAINT_VIOLATION_VIEW,
} from '@/constants/permission'
import { listPlatformRoleOptions, type PlatformRoleOption } from '@/api/platform-role'
import {
  listPlatformHierarchies,
  createPlatformHierarchy,
  deletePlatformHierarchy,
  listPlatformMutexes,
  createPlatformMutex,
  deletePlatformMutex,
  listPlatformPrerequisites,
  createPlatformPrerequisite,
  deletePlatformPrerequisite,
  listPlatformCardinalities,
  createPlatformCardinality,
  deletePlatformCardinality,
  listPlatformViolations,
  type RoleHierarchy,
  type RoleMutex,
  type RolePrerequisite,
  type RoleCardinality,
  type RoleViolation,
} from '@/api/platformRoleConstraint'

const { user } = useAuth()
const { isPlatformScope } = usePlatformScope()
const authorities = computed(() => new Set(extractAuthoritiesFromJwt(user.value?.access_token)))

function hasAuthority(perm: string) {
  return authorities.value.has(perm)
}

const canView = computed(() => hasAuthority(ROLE_CONSTRAINT_VIEW))
const canEditConstraint = computed(() => hasAuthority(ROLE_CONSTRAINT_EDIT))
const canViewViolations = computed(() => hasAuthority(ROLE_CONSTRAINT_VIOLATION_VIEW))
const canAccessConstraintTabs = computed(() => canView.value || canEditConstraint.value)
const canAccessPage = computed(() => canAccessConstraintTabs.value || canViewViolations.value)

const roleOptions = ref<PlatformRoleOption[]>([])
const roleOptionsReady = ref(false)
const roleSelectOptions = computed(() =>
  roleOptions.value.map((r) => ({
    value: r.roleId,
    label: `${r.code} — ${r.name} (#${r.roleId})`,
  })),
)

const roleById = computed(() => {
  const m = new Map<number, PlatformRoleOption>()
  for (const r of roleOptions.value) {
    m.set(r.roleId, r)
  }
  return m
})

function formatRoleCell(roleId: number) {
  const r = roleById.value.get(roleId)
  return r ? `${r.code} (#${roleId})` : String(roleId)
}

function filterRoleOption(input: string, option: { label?: string }) {
  return (option.label ?? '').toLowerCase().includes(input.toLowerCase())
}

async function loadRoleOptions() {
  if (!isPlatformScope.value || !canAccessConstraintTabs.value) {
    roleOptions.value = []
    roleOptionsReady.value = false
    return false
  }
  try {
    const res = await listPlatformRoleOptions({ limit: 500 })
    roleOptions.value = Array.isArray(res) ? res : []
    roleOptionsReady.value = true
    return true
  } catch {
    roleOptions.value = []
    roleOptionsReady.value = false
    return false
  }
}

async function ensureRoleOptionsReady() {
  if (roleOptionsReady.value && roleOptions.value.length > 0) {
    return true
  }
  const loaded = await loadRoleOptions()
  if (!loaded) {
    message.warning('无法加载平台角色候选，请确认已具备平台 RBAC3 或平台用户角色目录读取权限后重试')
    return false
  }
  return true
}

const activeTab = ref('hierarchy')
const submitting = ref(false)

const hierarchies = ref<RoleHierarchy[]>([])
const hierarchyLoading = ref(false)
const hierarchyModalVisible = ref(false)
const hierarchyForm = ref({ parentRoleId: undefined as number | undefined, childRoleId: undefined as number | undefined })
const hierarchyColumns = [
  { title: '父角色', dataIndex: 'parentRoleId', width: 220 },
  { title: '子角色', dataIndex: 'childRoleId', width: 220 },
  { title: '操作', dataIndex: 'action', width: 100, align: 'center' as const },
]
const hierarchyRowKey = (record: RoleHierarchy) => `${record.childRoleId}-${record.parentRoleId}`

async function loadHierarchies() {
  if (!isPlatformScope.value || !canView.value) {
    return
  }
  hierarchyLoading.value = true
  try {
    const res = await listPlatformHierarchies()
    hierarchies.value = Array.isArray(res) ? res : []
  } catch {
    hierarchies.value = []
  } finally {
    hierarchyLoading.value = false
  }
}

async function openHierarchyModal() {
  if (!(await ensureRoleOptionsReady())) {
    return
  }
  hierarchyForm.value = { parentRoleId: undefined, childRoleId: undefined }
  hierarchyModalVisible.value = true
}

async function submitHierarchy() {
  if (!hierarchyForm.value.parentRoleId || !hierarchyForm.value.childRoleId) {
    message.warning('请选择完整信息')
    return
  }
  submitting.value = true
  try {
    await createPlatformHierarchy({
      parentRoleId: hierarchyForm.value.parentRoleId,
      childRoleId: hierarchyForm.value.childRoleId,
    })
    message.success('创建成功')
    hierarchyModalVisible.value = false
    loadHierarchies()
  } catch (e: any) {
    message.error('创建失败: ' + (e.message || '未知错误'))
  } finally {
    submitting.value = false
  }
}

const mutexes = ref<RoleMutex[]>([])
const mutexLoading = ref(false)
const mutexModalVisible = ref(false)
const mutexForm = ref({ roleIdA: undefined as number | undefined, roleIdB: undefined as number | undefined })
const mutexColumns = [
  { title: '角色 A', dataIndex: 'leftRoleId', width: 220 },
  { title: '角色 B', dataIndex: 'rightRoleId', width: 220 },
  { title: '操作', dataIndex: 'action', width: 100, align: 'center' as const },
]
const mutexRowKey = (record: RoleMutex) => `${record.leftRoleId}-${record.rightRoleId}`

async function loadMutexes() {
  if (!isPlatformScope.value || !canView.value) {
    return
  }
  mutexLoading.value = true
  try {
    const res = await listPlatformMutexes()
    mutexes.value = Array.isArray(res) ? res : []
  } catch {
    mutexes.value = []
  } finally {
    mutexLoading.value = false
  }
}

async function openMutexModal() {
  if (!(await ensureRoleOptionsReady())) {
    return
  }
  mutexForm.value = { roleIdA: undefined, roleIdB: undefined }
  mutexModalVisible.value = true
}

async function submitMutex() {
  if (!mutexForm.value.roleIdA || !mutexForm.value.roleIdB) {
    message.warning('请选择完整信息')
    return
  }
  submitting.value = true
  try {
    await createPlatformMutex({ roleIdA: mutexForm.value.roleIdA, roleIdB: mutexForm.value.roleIdB })
    message.success('创建成功')
    mutexModalVisible.value = false
    loadMutexes()
  } catch (e: any) {
    message.error('创建失败: ' + (e.message || '未知错误'))
  } finally {
    submitting.value = false
  }
}

const prerequisites = ref<RolePrerequisite[]>([])
const prerequisiteLoading = ref(false)
const prerequisiteModalVisible = ref(false)
const prerequisiteForm = ref({ roleId: undefined as number | undefined, requiredRoleId: undefined as number | undefined })
const prerequisiteColumns = [
  { title: '目标角色', dataIndex: 'roleId', width: 220 },
  { title: '前置角色', dataIndex: 'requiredRoleId', width: 220 },
  { title: '操作', dataIndex: 'action', width: 100, align: 'center' as const },
]
const prerequisiteRowKey = (record: RolePrerequisite) => `${record.roleId}-${record.requiredRoleId}`

async function loadPrerequisites() {
  if (!isPlatformScope.value || !canView.value) {
    return
  }
  prerequisiteLoading.value = true
  try {
    const res = await listPlatformPrerequisites()
    prerequisites.value = Array.isArray(res) ? res : []
  } catch {
    prerequisites.value = []
  } finally {
    prerequisiteLoading.value = false
  }
}

async function openPrerequisiteModal() {
  if (!(await ensureRoleOptionsReady())) {
    return
  }
  prerequisiteForm.value = { roleId: undefined, requiredRoleId: undefined }
  prerequisiteModalVisible.value = true
}

async function submitPrerequisite() {
  if (!prerequisiteForm.value.roleId || !prerequisiteForm.value.requiredRoleId) {
    message.warning('请选择完整信息')
    return
  }
  submitting.value = true
  try {
    await createPlatformPrerequisite({
      roleId: prerequisiteForm.value.roleId,
      requiredRoleId: prerequisiteForm.value.requiredRoleId,
    })
    message.success('创建成功')
    prerequisiteModalVisible.value = false
    loadPrerequisites()
  } catch (e: any) {
    message.error('创建失败: ' + (e.message || '未知错误'))
  } finally {
    submitting.value = false
  }
}

const cardinalities = ref<RoleCardinality[]>([])
const cardinalityLoading = ref(false)
const cardinalityModalVisible = ref(false)
const scopeTypeOptions = ['PLATFORM', 'TENANT', 'ORG', 'DEPT']
const cardinalityForm = ref({
  roleId: undefined as number | undefined,
  scopeType: 'PLATFORM',
  maxAssignments: undefined as number | undefined,
})
const cardinalityColumns = [
  { title: '角色', dataIndex: 'roleId', width: 220 },
  { title: '作用域类型', dataIndex: 'scopeType', width: 140 },
  { title: '最大分配数', dataIndex: 'maxAssignments', width: 120 },
  { title: '操作', dataIndex: 'action', width: 100, align: 'center' as const },
]
const cardinalityRowKey = (record: RoleCardinality) => `${record.roleId}-${record.scopeType}`

async function loadCardinalities() {
  if (!isPlatformScope.value || !canView.value) {
    return
  }
  cardinalityLoading.value = true
  try {
    const res = await listPlatformCardinalities()
    cardinalities.value = Array.isArray(res) ? res : []
  } catch {
    cardinalities.value = []
  } finally {
    cardinalityLoading.value = false
  }
}

async function openCardinalityModal() {
  if (!(await ensureRoleOptionsReady())) {
    return
  }
  cardinalityForm.value = { roleId: undefined, scopeType: 'PLATFORM', maxAssignments: undefined }
  cardinalityModalVisible.value = true
}

async function submitCardinality() {
  if (!cardinalityForm.value.roleId || !cardinalityForm.value.scopeType || !cardinalityForm.value.maxAssignments) {
    message.warning('请填写完整信息')
    return
  }
  submitting.value = true
  try {
    await createPlatformCardinality({
      roleId: cardinalityForm.value.roleId,
      scopeType: cardinalityForm.value.scopeType,
      maxAssignments: cardinalityForm.value.maxAssignments,
    })
    message.success('创建成功')
    cardinalityModalVisible.value = false
    loadCardinalities()
  } catch (e: any) {
    message.error('创建失败: ' + (e.message || '未知错误'))
  } finally {
    submitting.value = false
  }
}

const violations = ref<RoleViolation[]>([])
const violationLoading = ref(false)
const violationPagination = ref({ current: 1, pageSize: 10, total: 0 })
const violationColumns = [
  { title: 'ID', dataIndex: 'id', width: 80 },
  { title: '违规类型', dataIndex: 'violationType', width: 160 },
  { title: '违规编码', dataIndex: 'violationCode', width: 200 },
  { title: '主体类型', dataIndex: 'principalType', width: 120 },
  { title: '主体ID', dataIndex: 'principalId', width: 100 },
  { title: '作用域类型', dataIndex: 'scopeType', width: 120 },
  { title: '作用域ID', dataIndex: 'scopeId', width: 100 },
  { title: '详情', dataIndex: 'details', width: 280 },
  { title: '时间', dataIndex: 'createdAt', width: 180 },
]

async function loadViolations() {
  if (!isPlatformScope.value || !canViewViolations.value) {
    return
  }
  violationLoading.value = true
  try {
    const res = await listPlatformViolations({
      page: violationPagination.value.current - 1,
      size: violationPagination.value.pageSize,
    })
    violations.value = Array.isArray(res.content) ? res.content : []
    violationPagination.value.total = Number(res.totalElements) || 0
  } catch {
    violations.value = []
  } finally {
    violationLoading.value = false
  }
}

function handleViolationPageChange(page: number) {
  violationPagination.value.current = page
  loadViolations()
}

function handleViolationPageSizeChange(_current: number, size: number) {
  violationPagination.value.pageSize = size
  violationPagination.value.current = 1
  loadViolations()
}

type ConstraintType = 'hierarchy' | 'mutex' | 'prerequisite' | 'cardinality'
const deleteActions: Record<ConstraintType, (record: any) => Promise<any>> = {
  hierarchy: (record: RoleHierarchy) =>
    deletePlatformHierarchy({
      childRoleId: record.childRoleId,
      parentRoleId: record.parentRoleId,
    }),
  mutex: (record: RoleMutex) =>
    deletePlatformMutex({
      roleIdA: record.leftRoleId,
      roleIdB: record.rightRoleId,
    }),
  prerequisite: (record: RolePrerequisite) =>
    deletePlatformPrerequisite({
      roleId: record.roleId,
      requiredRoleId: record.requiredRoleId,
    }),
  cardinality: (record: RoleCardinality) =>
    deletePlatformCardinality({
      roleId: record.roleId,
      scopeType: record.scopeType,
    }),
}
const loadActions: Record<ConstraintType, () => void> = {
  hierarchy: loadHierarchies,
  mutex: loadMutexes,
  prerequisite: loadPrerequisites,
  cardinality: loadCardinalities,
}

function confirmDelete(type: ConstraintType, record: any) {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除该${typeLabel(type)}规则吗？`,
    okText: '确认',
    cancelText: '取消',
    onOk: () => {
      return deleteActions[type](record)
        .then(() => {
          message.success('删除成功')
          loadActions[type]()
        })
        .catch((e: any) => {
          message.error('删除失败: ' + (e.message || '未知错误'))
          return Promise.reject(e)
        })
    },
  })
}

function typeLabel(type: ConstraintType): string {
  const labels: Record<ConstraintType, string> = {
    hierarchy: '角色继承',
    mutex: '互斥约束',
    prerequisite: '先决条件',
    cardinality: '基数限制',
  }
  return labels[type]
}

function handleTabChange(key: Key) {
  if (String(key) === 'violations' && !canViewViolations.value) {
    activeTab.value = canAccessConstraintTabs.value ? 'hierarchy' : 'violations'
    return
  }
  const loaders: Record<string, () => void> = {
    hierarchy: loadHierarchies,
    mutex: loadMutexes,
    prerequisite: loadPrerequisites,
    cardinality: loadCardinalities,
    violations: () => {
      if (canViewViolations.value) {
        loadViolations()
      }
    },
  }
  loaders[String(key)]?.()
}

function clearPageData() {
  roleOptions.value = []
  roleOptionsReady.value = false
  hierarchies.value = []
  mutexes.value = []
  prerequisites.value = []
  cardinalities.value = []
  violations.value = []
}

function normalizeActiveTab() {
  if (!canAccessConstraintTabs.value && canViewViolations.value) {
    activeTab.value = 'violations'
    return
  }
  if (activeTab.value === 'violations' && !canViewViolations.value) {
    activeTab.value = 'hierarchy'
  }
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

watch(
  [isPlatformScope, canView, canEditConstraint, canViewViolations],
  ([scope, view, edit, violation]) => {
    if (!scope || (!view && !edit && !violation)) {
      clearPageData()
      activeTab.value = 'hierarchy'
      return
    }

    normalizeActiveTab()

    if (view || edit) {
      void loadRoleOptions()
    }

    if (activeTab.value === 'violations') {
      if (violation) {
        loadViolations()
      }
      return
    }

    if (view) {
      loadHierarchies()
    }
  },
  { immediate: true },
)
</script>

<style scoped>
.content-container {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #fff;
}
.scope-guard-card {
  margin: 16px 24px;
}
.content-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 0 24px;
}
.platform-guard-card {
  padding: 24px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fafafa;
}
.platform-guard-kicker {
  font-size: 12px;
  color: #8c8c8c;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  margin-bottom: 8px;
}
.tab-toolbar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  margin-bottom: 12px;
}
.action-icon {
  font-size: 18px;
  cursor: pointer;
  color: #595959;
  border-radius: 4px;
  padding: 8px;
  transition:
    color 0.2s,
    background 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
}
.action-icon:hover {
  color: #1890ff;
  background: #f5f5f5;
}
.pagination-container {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 12px 0;
}
</style>
