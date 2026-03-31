<template>
  <div class="content-container" style="position: relative;">
    <div v-if="!canRead" class="content-card">
      <div class="platform-guard-card">
        <div class="platform-guard-kicker">Permission Required</div>
        <h3>组织管理需要额外授权</h3>
        <p>
          当前页面属于后台配置面。只有具备 <code>system:org:list</code> 或管理员权限的用户，才会请求
          <code>/sys/org</code> 并展示组织管理数据。
        </p>
      </div>
    </div>
    <div v-else class="content-card org-layout">
      <div class="org-tree-panel">
        <div class="panel-header">
          <span class="panel-title">组织架构</span>
          <div class="panel-actions">
            <a-tooltip title="刷新">
              <span class="action-icon" @click="handleRefresh">
                <ReloadOutlined :spin="refreshing" />
              </span>
            </a-tooltip>
            <a-button v-if="canCreate" type="link" size="small" @click="handleCreate(null)">
              <template #icon><PlusOutlined /></template>
              新建
            </a-button>
          </div>
        </div>
        <div class="tree-container">
          <a-spin :spinning="treeLoading">
            <a-tree
              v-if="treeData.length > 0"
              :tree-data="treeData"
              :field-names="{ title: 'name', key: 'id', children: 'children' }"
              :selected-keys="selectedKeys"
              default-expand-all
              block-node
              @select="onTreeSelect"
            >
              <template #title="{ name, status }">
                <span>{{ name }}</span>
                <a-tag v-if="status === 'INACTIVE'" color="red" style="margin-left: 6px; font-size: 11px;">禁用</a-tag>
              </template>
            </a-tree>
            <a-empty v-else description="暂无组织数据" />
          </a-spin>
        </div>
      </div>

      <div class="org-detail-panel">
        <template v-if="selectedOrg">
          <div class="detail-header">
            <div class="detail-title">
              <span>{{ selectedOrg.name }}</span>
              <a-tag :color="selectedOrg.status === 'ACTIVE' ? 'green' : 'red'" style="margin-left: 8px;">
                {{ selectedOrg.status === 'ACTIVE' ? '启用' : '禁用' }}
              </a-tag>
            </div>
            <div class="detail-actions">
              <a-button v-if="canCreate" type="link" size="small" @click="handleCreate(selectedOrg)">
                <template #icon><PlusOutlined /></template>
                新建子组织
              </a-button>
              <a-button v-if="canUpdate" type="link" size="small" @click="handleEdit">
                <template #icon><EditOutlined /></template>
                编辑
              </a-button>
              <a-button v-if="canDelete" type="link" size="small" danger @click="handleDelete">
                <template #icon><DeleteOutlined /></template>
                删除
              </a-button>
            </div>
          </div>

          <a-descriptions bordered :column="2" size="small" style="margin-bottom: 16px;">
            <a-descriptions-item label="组织编码">{{ selectedOrg.code }}</a-descriptions-item>
            <a-descriptions-item label="组织类型">{{ formatUnitType(selectedOrg.unitType) }}</a-descriptions-item>
            <a-descriptions-item label="排序">{{ selectedOrg.sortOrder ?? '-' }}</a-descriptions-item>
            <a-descriptions-item label="创建时间">{{ formatDateTime(selectedOrg.createdAt) }}</a-descriptions-item>
          </a-descriptions>

          <div class="member-section">
            <div class="member-header">
              <span class="member-title">成员列表</span>
              <a-button v-if="canAssignUser" type="link" size="small" @click="showAddMember = true">
                <template #icon><UserAddOutlined /></template>
                添加成员
              </a-button>
            </div>
            <a-table
              :columns="memberColumns"
              :data-source="members"
              :pagination="false"
              :loading="membersLoading"
              :row-key="(record: any) => String(record.id)"
              bordered
              size="small"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.dataIndex === 'isPrimary'">
                  <a-tag :color="record.isPrimary ? 'blue' : 'default'">
                    {{ record.isPrimary ? '是' : '否' }}
                  </a-tag>
                </template>
                <template v-else-if="column.dataIndex === 'createdAt'">
                  {{ formatDateTime(record.createdAt) }}
                </template>
                <template v-else-if="column.dataIndex === 'action'">
                  <a-button
                    v-if="canRemoveUser"
                    type="link"
                    size="small"
                    danger
                    @click="handleRemoveMember(record)"
                  >
                    <template #icon><DeleteOutlined /></template>
                    移除
                  </a-button>
                </template>
              </template>
            </a-table>
          </div>
        </template>
        <template v-else>
          <div class="empty-detail">
            <a-empty description="请在左侧选择一个组织节点" />
          </div>
        </template>
      </div>
    </div>

    <OrgForm
      v-if="formVisible"
      :open="formVisible"
      :mode="formMode"
      :org-data="formOrgData"
      :tree-data="treeData"
      @update:open="formVisible = $event"
      @success="onFormSuccess"
    />

    <a-modal
      v-model:open="showAddMember"
      title="添加成员"
      @ok="handleAddMemberSubmit"
      :confirm-loading="addMemberLoading"
      :destroy-on-close="true"
    >
      <a-form layout="vertical" style="margin-top: 16px;">
        <a-form-item label="用户 ID">
          <a-input-number v-model:value="addMemberUserId" placeholder="请输入用户 ID" style="width: 100%;" :min="1" />
        </a-form-item>
        <a-form-item label="设为主组织">
          <a-switch v-model:checked="addMemberPrimary" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useAuth } from '@/auth/auth'
import { getOrgTree, listUnitMembers, deleteOrg, addUserToUnit, removeUserFromUnit } from '@/api/org'
import type { OrgUnit, UserUnit } from '@/api/org'
import { message, Modal } from 'ant-design-vue'
import { ReloadOutlined, PlusOutlined, EditOutlined, DeleteOutlined, UserAddOutlined } from '@ant-design/icons-vue'
import { extractAuthoritiesFromJwt } from '@/utils/jwt'
import {
  ORG_MANAGEMENT_READ_AUTHORITIES,
  ORG_MANAGEMENT_CREATE_AUTHORITIES,
  ORG_MANAGEMENT_UPDATE_AUTHORITIES,
  ORG_MANAGEMENT_DELETE_AUTHORITIES,
  ORG_MANAGEMENT_USER_ASSIGN_AUTHORITIES,
  ORG_MANAGEMENT_USER_REMOVE_AUTHORITIES,
} from '@/constants/permission'
import OrgForm from './OrgForm.vue'

const { user } = useAuth()
const authorities = computed(() => new Set(extractAuthoritiesFromJwt(user.value?.access_token)))

function hasAnyAuthority(requiredAuthorities: string[]) {
  return requiredAuthorities.some((a) => authorities.value.has(a))
}

const canRead = computed(() => hasAnyAuthority(ORG_MANAGEMENT_READ_AUTHORITIES))
const canCreate = computed(() => hasAnyAuthority(ORG_MANAGEMENT_CREATE_AUTHORITIES))
const canUpdate = computed(() => hasAnyAuthority(ORG_MANAGEMENT_UPDATE_AUTHORITIES))
const canDelete = computed(() => hasAnyAuthority(ORG_MANAGEMENT_DELETE_AUTHORITIES))
const canAssignUser = computed(() => hasAnyAuthority(ORG_MANAGEMENT_USER_ASSIGN_AUTHORITIES))
const canRemoveUser = computed(() => hasAnyAuthority(ORG_MANAGEMENT_USER_REMOVE_AUTHORITIES))

const treeData = ref<OrgUnit[]>([])
const treeLoading = ref(false)
const refreshing = ref(false)
const selectedKeys = ref<number[]>([])
const selectedOrg = ref<OrgUnit | null>(null)

const members = ref<UserUnit[]>([])
const membersLoading = ref(false)

const memberColumns = [
  { title: '用户 ID', dataIndex: 'userId', width: 100 },
  { title: '组织编码', dataIndex: 'unitCode', width: 120 },
  { title: '组织名称', dataIndex: 'unitName', width: 150 },
  { title: '主组织', dataIndex: 'isPrimary', width: 80, align: 'center' as const },
  { title: '加入时间', dataIndex: 'createdAt', width: 160 },
  { title: '操作', dataIndex: 'action', width: 80, align: 'center' as const },
]

const formVisible = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const formOrgData = ref<Partial<OrgUnit> | null>(null)

const showAddMember = ref(false)
const addMemberUserId = ref<number | null>(null)
const addMemberPrimary = ref(false)
const addMemberLoading = ref(false)

async function loadTree() {
  if (!canRead.value) {
    treeData.value = []
    return
  }
  treeLoading.value = true
  try {
    const data = await getOrgTree()
    treeData.value = Array.isArray(data) ? data : []
  } catch {
    treeData.value = []
  } finally {
    treeLoading.value = false
  }
}

async function loadMembers(unitId: number) {
  membersLoading.value = true
  try {
    const data = await listUnitMembers(unitId)
    members.value = Array.isArray(data) ? data : []
  } catch {
    members.value = []
  } finally {
    membersLoading.value = false
  }
}

function findOrgById(nodes: OrgUnit[], id: number): OrgUnit | null {
  for (const node of nodes) {
    if (node.id === id) {
      return node
    }
    if (node.children) {
      const found = findOrgById(node.children, id)
      if (found) {
        return found
      }
    }
  }
  return null
}

function onTreeSelect(keys: number[]) {
  if (keys.length === 0) {
    selectedKeys.value = []
    selectedOrg.value = null
    members.value = []
    return
  }
  const id = keys[0]
  selectedKeys.value = [id]
  selectedOrg.value = findOrgById(treeData.value, id)
  loadMembers(id)
}

async function handleRefresh() {
  refreshing.value = true
  await loadTree().finally(() => {
    setTimeout(() => { refreshing.value = false }, 1000)
  })
  if (selectedOrg.value) {
    const stillExists = findOrgById(treeData.value, selectedOrg.value.id)
    if (stillExists) {
      selectedOrg.value = stillExists
      loadMembers(stillExists.id)
    } else {
      selectedKeys.value = []
      selectedOrg.value = null
      members.value = []
    }
  }
}

function handleCreate(parentOrg: OrgUnit | null) {
  if (!canCreate.value) {
    message.warning('缺少组织创建权限')
    return
  }
  formMode.value = 'create'
  formOrgData.value = parentOrg ? { parentId: parentOrg.id } : null
  formVisible.value = true
}

function handleEdit() {
  if (!canUpdate.value) {
    message.warning('缺少组织编辑权限')
    return
  }
  if (!selectedOrg.value) {
    return
  }
  formMode.value = 'edit'
  formOrgData.value = { ...selectedOrg.value }
  formVisible.value = true
}

function handleDelete() {
  if (!canDelete.value) {
    message.warning('缺少组织删除权限')
    return
  }
  if (!selectedOrg.value) {
    return
  }
  const org = selectedOrg.value
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除组织 "${org.name}" 吗？`,
    okText: '确认',
    cancelText: '取消',
    onOk: () => {
      return deleteOrg(org.id).then(() => {
        message.success('删除成功')
        selectedKeys.value = []
        selectedOrg.value = null
        members.value = []
        loadTree()
      }).catch((error: any) => {
        const errorMessage = error?.errorInfo?.message || error?.message || '未知错误'
        message.error('删除失败: ' + errorMessage)
        return Promise.reject(error)
      })
    },
  })
}

function onFormSuccess() {
  loadTree().then(() => {
    if (selectedOrg.value) {
      const updated = findOrgById(treeData.value, selectedOrg.value.id)
      if (updated) {
        selectedOrg.value = updated
      }
    }
  })
}

function handleRemoveMember(record: UserUnit) {
  if (!canRemoveUser.value) {
    message.warning('缺少成员移除权限')
    return
  }
  if (!selectedOrg.value) {
    return
  }
  const unitId = selectedOrg.value.id
  Modal.confirm({
    title: '确认移除',
    content: `确定要将用户 ${record.userId} 从组织中移除吗？`,
    okText: '确认',
    cancelText: '取消',
    onOk: () => {
      return removeUserFromUnit(unitId, record.userId).then(() => {
        message.success('移除成功')
        loadMembers(unitId)
      }).catch((error: any) => {
        const errorMessage = error?.errorInfo?.message || error?.message || '未知错误'
        message.error('移除失败: ' + errorMessage)
        return Promise.reject(error)
      })
    },
  })
}

async function handleAddMemberSubmit() {
  if (!selectedOrg.value || !addMemberUserId.value) {
    message.warning('请输入用户 ID')
    return
  }
  addMemberLoading.value = true
  try {
    await addUserToUnit(selectedOrg.value.id, addMemberUserId.value, addMemberPrimary.value)
    message.success('添加成功')
    showAddMember.value = false
    addMemberUserId.value = null
    addMemberPrimary.value = false
    loadMembers(selectedOrg.value.id)
  } catch (error: any) {
    const errorMessage = error?.errorInfo?.message || error?.message || '未知错误'
    message.error('添加失败: ' + errorMessage)
  } finally {
    addMemberLoading.value = false
  }
}

function formatDateTime(dateTime: string | null | undefined): string {
  if (!dateTime) {
    return '-'
  }
  try {
    const date = new Date(dateTime)
    return date.toLocaleString('zh-CN', {
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

function formatUnitType(unitType: string | undefined): string {
  const map: Record<string, string> = {
    COMPANY: '公司',
    DEPARTMENT: '部门',
    GROUP: '小组',
  }
  return map[unitType ?? ''] ?? unitType ?? '-'
}

onMounted(() => {
  if (canRead.value) {
    loadTree()
  }
})

watch(canRead, (enabled) => {
  if (enabled) {
    loadTree()
    return
  }
  treeData.value = []
  selectedKeys.value = []
  selectedOrg.value = null
  members.value = []
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

.org-layout {
  flex-direction: row;
}

.org-tree-panel {
  width: 320px;
  min-width: 260px;
  border-right: 1px solid #f0f0f0;
  display: flex;
  flex-direction: column;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #f0f0f0;
}

.panel-title {
  font-size: 15px;
  font-weight: 600;
  color: #222;
}

.panel-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.tree-container {
  flex: 1;
  overflow: auto;
  padding: 12px 8px;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.tree-container::-webkit-scrollbar {
  display: none;
}

.org-detail-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: auto;
  padding: 16px 24px;
}

.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.detail-title {
  font-size: 16px;
  font-weight: 600;
  color: #222;
  display: flex;
  align-items: center;
}

.detail-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.member-section {
  margin-top: 8px;
}

.member-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.member-title {
  font-size: 15px;
  font-weight: 600;
  color: #222;
}

.empty-detail {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.action-icon {
  font-size: 16px;
  cursor: pointer;
  color: #595959;
  border-radius: 4px;
  padding: 6px;
  transition: color 0.2s, background 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
}

.action-icon:hover {
  color: #1890ff;
  background: #f5f5f5;
}

:deep(.ant-tree .ant-tree-node-content-wrapper) {
  display: flex;
  align-items: center;
}

:deep(.ant-descriptions-item-label) {
  white-space: nowrap;
}
</style>
