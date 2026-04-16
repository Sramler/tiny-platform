<template>
  <div class="content-container">
    <div v-if="!canView" class="content-card">
      <div class="platform-guard-card">
        <div class="platform-guard-kicker">Permission Required</div>
        <h3>RBAC3 约束管理需要额外授权</h3>
        <p>
          当前页面属于后台配置面。只有具备 <code>system:role:constraint:view</code> 或管理员权限的用户，才能查看角色约束规则。
        </p>
      </div>
    </div>
    <div v-else class="content-card">
      <a-tabs v-model:activeKey="activeTab" @change="handleTabChange">
        <a-tab-pane key="hierarchy" tab="角色继承">
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
              <template v-if="column.dataIndex === 'action'">
                <a-button v-if="canEditConstraint" type="link" size="small" danger @click="confirmDelete('hierarchy', record)">
                  <template #icon><DeleteOutlined /></template>
                  删除
                </a-button>
              </template>
            </template>
          </a-table>
        </a-tab-pane>

        <a-tab-pane key="mutex" tab="互斥约束">
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
              <template v-if="column.dataIndex === 'action'">
                <a-button v-if="canEditConstraint" type="link" size="small" danger @click="confirmDelete('mutex', record)">
                  <template #icon><DeleteOutlined /></template>
                  删除
                </a-button>
              </template>
            </template>
          </a-table>
        </a-tab-pane>

        <a-tab-pane key="prerequisite" tab="先决条件">
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
              <template v-if="column.dataIndex === 'action'">
                <a-button v-if="canEditConstraint" type="link" size="small" danger @click="confirmDelete('prerequisite', record)">
                  <template #icon><DeleteOutlined /></template>
                  删除
                </a-button>
              </template>
            </template>
          </a-table>
        </a-tab-pane>

        <a-tab-pane key="cardinality" tab="基数限制">
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
              <template v-if="column.dataIndex === 'scopeType'">
                <a-tag color="blue">{{ record.scopeType }}</a-tag>
              </template>
              <template v-else-if="column.dataIndex === 'action'">
                <a-button v-if="canEditConstraint" type="link" size="small" danger @click="confirmDelete('cardinality', record)">
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

    <!-- Hierarchy Modal -->
    <a-modal v-model:open="hierarchyModalVisible" title="新建角色继承" @ok="submitHierarchy" :confirm-loading="submitting">
      <a-form layout="vertical">
        <a-form-item label="父角色ID" required>
          <a-input-number v-model:value="hierarchyForm.parentRoleId" :min="1" style="width: 100%" placeholder="请输入父角色ID" />
        </a-form-item>
        <a-form-item label="子角色ID" required>
          <a-input-number v-model:value="hierarchyForm.childRoleId" :min="1" style="width: 100%" placeholder="请输入子角色ID" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- Mutex Modal -->
    <a-modal v-model:open="mutexModalVisible" title="新建互斥约束" @ok="submitMutex" :confirm-loading="submitting">
      <a-form layout="vertical">
        <a-form-item label="角色A ID" required>
          <a-input-number v-model:value="mutexForm.roleIdA" :min="1" style="width: 100%" placeholder="请输入角色A ID" />
        </a-form-item>
        <a-form-item label="角色B ID" required>
          <a-input-number v-model:value="mutexForm.roleIdB" :min="1" style="width: 100%" placeholder="请输入角色B ID" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- Prerequisite Modal -->
    <a-modal v-model:open="prerequisiteModalVisible" title="新建先决条件" @ok="submitPrerequisite" :confirm-loading="submitting">
      <a-form layout="vertical">
        <a-form-item label="角色ID" required>
          <a-input-number v-model:value="prerequisiteForm.roleId" :min="1" style="width: 100%" placeholder="请输入角色ID" />
        </a-form-item>
        <a-form-item label="前置角色ID" required>
          <a-input-number v-model:value="prerequisiteForm.requiredRoleId" :min="1" style="width: 100%" placeholder="请输入前置角色ID" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- Cardinality Modal -->
    <a-modal v-model:open="cardinalityModalVisible" title="新建基数限制" @ok="submitCardinality" :confirm-loading="submitting">
      <a-form layout="vertical">
        <a-form-item label="角色ID" required>
          <a-input-number v-model:value="cardinalityForm.roleId" :min="1" style="width: 100%" placeholder="请输入角色ID" />
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
import { ref, computed, onMounted } from 'vue'
import { useAuth } from '@/auth/auth'
import { message, Modal } from 'ant-design-vue'
import { ReloadOutlined, PlusOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import type { Key } from 'ant-design-vue/es/_util/type'
import { extractAuthoritiesFromJwt } from '@/utils/jwt'
import {
  ROLE_CONSTRAINT_VIEW,
  ROLE_CONSTRAINT_EDIT,
  ROLE_CONSTRAINT_VIOLATION_VIEW,
} from '@/constants/permission'
import {
  listHierarchies, createHierarchy, deleteHierarchy,
  listMutexes, createMutex, deleteMutex,
  listPrerequisites, createPrerequisite, deletePrerequisite,
  listCardinalities, createCardinality, deleteCardinality,
  listViolations,
} from '@/api/roleConstraint'
import type { RoleHierarchy, RoleMutex, RolePrerequisite, RoleCardinality, RoleViolation } from '@/api/roleConstraint'

const { user } = useAuth()
const authorities = computed(() => new Set(extractAuthoritiesFromJwt(user.value?.access_token)))

function hasAuthority(perm: string) {
  return authorities.value.has(perm)
}

const canView = computed(() => hasAuthority(ROLE_CONSTRAINT_VIEW))
const canEditConstraint = computed(() => hasAuthority(ROLE_CONSTRAINT_EDIT))
const canViewViolations = computed(() => hasAuthority(ROLE_CONSTRAINT_VIOLATION_VIEW))

const activeTab = ref('hierarchy')
const submitting = ref(false)

// ─── Hierarchy ───
const hierarchies = ref<RoleHierarchy[]>([])
const hierarchyLoading = ref(false)
const hierarchyModalVisible = ref(false)
const hierarchyForm = ref({ parentRoleId: undefined as number | undefined, childRoleId: undefined as number | undefined })
const hierarchyColumns = [
  { title: '父角色ID', dataIndex: 'parentRoleId', width: 120 },
  { title: '子角色ID', dataIndex: 'childRoleId', width: 120 },
  { title: '操作', dataIndex: 'action', width: 100, align: 'center' as const },
]
const hierarchyRowKey = (record: RoleHierarchy) => `${record.childRoleId}-${record.parentRoleId}`

async function loadHierarchies() {
  hierarchyLoading.value = true
  try {
    const res = await listHierarchies()
    hierarchies.value = Array.isArray(res) ? res : []
  } catch { hierarchies.value = [] }
  finally { hierarchyLoading.value = false }
}

function openHierarchyModal() {
  hierarchyForm.value = { parentRoleId: undefined, childRoleId: undefined }
  hierarchyModalVisible.value = true
}

async function submitHierarchy() {
  if (!hierarchyForm.value.parentRoleId || !hierarchyForm.value.childRoleId) {
    message.warning('请填写完整信息')
    return
  }
  submitting.value = true
  try {
    await createHierarchy({ parentRoleId: hierarchyForm.value.parentRoleId, childRoleId: hierarchyForm.value.childRoleId })
    message.success('创建成功')
    hierarchyModalVisible.value = false
    loadHierarchies()
  } catch (e: any) { message.error('创建失败: ' + (e.message || '未知错误')) }
  finally { submitting.value = false }
}

// ─── Mutex ───
const mutexes = ref<RoleMutex[]>([])
const mutexLoading = ref(false)
const mutexModalVisible = ref(false)
const mutexForm = ref({ roleIdA: undefined as number | undefined, roleIdB: undefined as number | undefined })
const mutexColumns = [
  { title: '角色A ID', dataIndex: 'leftRoleId', width: 120 },
  { title: '角色B ID', dataIndex: 'rightRoleId', width: 120 },
  { title: '操作', dataIndex: 'action', width: 100, align: 'center' as const },
]
const mutexRowKey = (record: RoleMutex) => `${record.leftRoleId}-${record.rightRoleId}`

async function loadMutexes() {
  mutexLoading.value = true
  try {
    const res = await listMutexes()
    mutexes.value = Array.isArray(res) ? res : []
  } catch { mutexes.value = [] }
  finally { mutexLoading.value = false }
}

function openMutexModal() {
  mutexForm.value = { roleIdA: undefined, roleIdB: undefined }
  mutexModalVisible.value = true
}

async function submitMutex() {
  if (!mutexForm.value.roleIdA || !mutexForm.value.roleIdB) {
    message.warning('请填写完整信息')
    return
  }
  submitting.value = true
  try {
    await createMutex({ roleIdA: mutexForm.value.roleIdA, roleIdB: mutexForm.value.roleIdB })
    message.success('创建成功')
    mutexModalVisible.value = false
    loadMutexes()
  } catch (e: any) { message.error('创建失败: ' + (e.message || '未知错误')) }
  finally { submitting.value = false }
}

// ─── Prerequisite ───
const prerequisites = ref<RolePrerequisite[]>([])
const prerequisiteLoading = ref(false)
const prerequisiteModalVisible = ref(false)
const prerequisiteForm = ref({ roleId: undefined as number | undefined, requiredRoleId: undefined as number | undefined })
const prerequisiteColumns = [
  { title: '角色ID', dataIndex: 'roleId', width: 120 },
  { title: '前置角色ID', dataIndex: 'requiredRoleId', width: 140 },
  { title: '操作', dataIndex: 'action', width: 100, align: 'center' as const },
]
const prerequisiteRowKey = (record: RolePrerequisite) => `${record.roleId}-${record.requiredRoleId}`

async function loadPrerequisites() {
  prerequisiteLoading.value = true
  try {
    const res = await listPrerequisites()
    prerequisites.value = Array.isArray(res) ? res : []
  } catch { prerequisites.value = [] }
  finally { prerequisiteLoading.value = false }
}

function openPrerequisiteModal() {
  prerequisiteForm.value = { roleId: undefined, requiredRoleId: undefined }
  prerequisiteModalVisible.value = true
}

async function submitPrerequisite() {
  if (!prerequisiteForm.value.roleId || !prerequisiteForm.value.requiredRoleId) {
    message.warning('请填写完整信息')
    return
  }
  submitting.value = true
  try {
    await createPrerequisite({ roleId: prerequisiteForm.value.roleId, requiredRoleId: prerequisiteForm.value.requiredRoleId })
    message.success('创建成功')
    prerequisiteModalVisible.value = false
    loadPrerequisites()
  } catch (e: any) { message.error('创建失败: ' + (e.message || '未知错误')) }
  finally { submitting.value = false }
}

// ─── Cardinality ───
const cardinalities = ref<RoleCardinality[]>([])
const cardinalityLoading = ref(false)
const cardinalityModalVisible = ref(false)
const scopeTypeOptions = ['TENANT', 'ORG', 'DEPT']
const cardinalityForm = ref({
  roleId: undefined as number | undefined,
  scopeType: 'TENANT',
  maxAssignments: undefined as number | undefined,
})
const cardinalityColumns = [
  { title: '角色ID', dataIndex: 'roleId', width: 120 },
  { title: '作用域类型', dataIndex: 'scopeType', width: 140 },
  { title: '最大分配数', dataIndex: 'maxAssignments', width: 120 },
  { title: '操作', dataIndex: 'action', width: 100, align: 'center' as const },
]
const cardinalityRowKey = (record: RoleCardinality) => `${record.roleId}-${record.scopeType}`

async function loadCardinalities() {
  cardinalityLoading.value = true
  try {
    const res = await listCardinalities()
    cardinalities.value = Array.isArray(res) ? res : []
  } catch { cardinalities.value = [] }
  finally { cardinalityLoading.value = false }
}

function openCardinalityModal() {
  cardinalityForm.value = { roleId: undefined, scopeType: 'TENANT', maxAssignments: undefined }
  cardinalityModalVisible.value = true
}

async function submitCardinality() {
  if (!cardinalityForm.value.roleId || !cardinalityForm.value.scopeType || !cardinalityForm.value.maxAssignments) {
    message.warning('请填写完整信息')
    return
  }
  submitting.value = true
  try {
    await createCardinality({
      roleId: cardinalityForm.value.roleId,
      scopeType: cardinalityForm.value.scopeType,
      maxAssignments: cardinalityForm.value.maxAssignments,
    })
    message.success('创建成功')
    cardinalityModalVisible.value = false
    loadCardinalities()
  } catch (e: any) { message.error('创建失败: ' + (e.message || '未知错误')) }
  finally { submitting.value = false }
}

// ─── Violations ───
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
  violationLoading.value = true
  try {
    const res = await listViolations({
      page: violationPagination.value.current - 1,
      size: violationPagination.value.pageSize,
    })
    violations.value = Array.isArray(res.content) ? res.content : []
    violationPagination.value.total = Number(res.totalElements) || 0
  } catch { violations.value = [] }
  finally { violationLoading.value = false }
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

// ─── Shared ───
type ConstraintType = 'hierarchy' | 'mutex' | 'prerequisite' | 'cardinality'
const deleteActions: Record<ConstraintType, (record: any) => Promise<any>> = {
  hierarchy: (record: RoleHierarchy) => deleteHierarchy({
    childRoleId: record.childRoleId,
    parentRoleId: record.parentRoleId,
  }),
  mutex: (record: RoleMutex) => deleteMutex({
    roleIdA: record.leftRoleId,
    roleIdB: record.rightRoleId,
  }),
  prerequisite: (record: RolePrerequisite) => deletePrerequisite({
    roleId: record.roleId,
    requiredRoleId: record.requiredRoleId,
  }),
  cardinality: (record: RoleCardinality) => deleteCardinality({
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
      return deleteActions[type](record).then(() => {
        message.success('删除成功')
        loadActions[type]()
      }).catch((e: any) => {
        message.error('删除失败: ' + (e.message || '未知错误'))
        return Promise.reject(e)
      })
    },
  })
}

function typeLabel(type: ConstraintType): string {
  const labels: Record<ConstraintType, string> = { hierarchy: '角色继承', mutex: '互斥约束', prerequisite: '先决条件', cardinality: '基数限制' }
  return labels[type]
}

function handleTabChange(key: Key) {
  if (String(key) === 'violations' && !canViewViolations.value) {
    activeTab.value = 'hierarchy'
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

onMounted(() => {
  if (canView.value) loadHierarchies()
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
  padding: 0 24px;
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
  transition: color 0.2s, background 0.2s;
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
