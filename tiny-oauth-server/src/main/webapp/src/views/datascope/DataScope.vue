<template>
  <div class="content-container">
    <div v-if="!canView" class="content-card">
      <div class="platform-guard-card">
        <div class="platform-guard-kicker">Permission Required</div>
        <h3>数据范围管理需要额外授权</h3>
        <p>
          当前页面属于后台配置面。只有具备 <code>system:datascope:view</code> 或管理员权限的用户，才能查看数据范围配置。
        </p>
      </div>
    </div>
    <div v-else class="content-card">
      <div class="form-container">
        <a-form layout="inline">
          <a-form-item label="选择角色">
            <a-select
              v-model:value="selectedRoleId"
              placeholder="请选择角色"
              style="width: 240px"
              :options="roleOptions"
              show-search
              :filter-option="filterOption"
              @change="handleRoleChange"
            />
          </a-form-item>
        </a-form>
      </div>
      <div class="toolbar-container">
        <div class="table-title">数据范围规则</div>
        <div class="table-actions">
          <a-button v-if="canEdit && selectedRoleId" type="link" @click="handleCreate">
            <template #icon><PlusOutlined /></template>
            新建
          </a-button>
          <a-tooltip title="刷新">
            <span class="action-icon" @click="loadData">
              <ReloadOutlined :spin="loading" />
            </span>
          </a-tooltip>
        </div>
      </div>
      <div class="table-container">
        <a-table
          :columns="columns"
          :data-source="tableData"
          :pagination="false"
          :row-key="(record: any) => String(record.id)"
          bordered
          :loading="loading"
          :scroll="{ x: 'max-content' }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.dataIndex === 'scopeType'">
              <a-tag>{{ record.scopeType }}</a-tag>
            </template>
            <template v-else-if="column.dataIndex === 'accessType'">
              <a-tag :color="record.accessType === 'WRITE' ? 'orange' : 'blue'">
                {{ record.accessType }}
              </a-tag>
            </template>
            <template v-else-if="column.dataIndex === 'createdAt'">
              {{ formatDateTime(record.createdAt) }}
            </template>
            <template v-else-if="column.dataIndex === 'updatedAt'">
              {{ formatDateTime(record.updatedAt) }}
            </template>
            <template v-else-if="column.dataIndex === 'action'">
              <div class="action-buttons">
                <a-button v-if="canEdit" type="link" size="small" @click="handleEdit(record)">
                  <template #icon><EditOutlined /></template>
                  编辑
                </a-button>
                <a-button v-if="canEdit" type="link" size="small" danger @click="handleDelete(record)">
                  <template #icon><DeleteOutlined /></template>
                  删除
                </a-button>
              </div>
            </template>
          </template>
        </a-table>
      </div>
    </div>

    <a-modal
      v-model:open="modalVisible"
      :title="modalMode === 'create' ? '新建数据范围' : '编辑数据范围'"
      @ok="handleModalOk"
      @cancel="modalVisible = false"
      :confirm-loading="submitting"
    >
      <a-form :model="formData" layout="vertical">
        <a-form-item label="模块" required>
          <a-input v-model:value="formData.module" placeholder="请输入模块名" />
        </a-form-item>
        <a-form-item label="范围类型" required>
          <a-select v-model:value="formData.scopeType" placeholder="请选择范围类型">
            <a-select-option v-for="opt in scopeTypeOptions" :key="opt" :value="opt">{{ opt }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="访问类型" required>
          <a-select v-model:value="formData.accessType" placeholder="请选择访问类型">
            <a-select-option value="READ">READ</a-select-option>
            <a-select-option value="WRITE">WRITE</a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useAuth } from '@/auth/auth'
import { message, Modal } from 'ant-design-vue'
import { ReloadOutlined, PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import { extractAuthoritiesFromJwt } from '@/utils/jwt'
import {
  DATASCOPE_VIEW_AUTHORITIES,
  DATASCOPE_EDIT_AUTHORITIES,
} from '@/constants/permission'
import { getAllRoles } from '@/api/role'
import { getDataScopesByRole, upsertDataScope, deleteDataScope } from '@/api/datascope'
import type { DataScope } from '@/api/datascope'
import type { DefaultOptionType } from 'ant-design-vue/es/select'

const { user } = useAuth()
const authorities = computed(() => new Set(extractAuthoritiesFromJwt(user.value?.access_token)))

function hasAnyAuthority(required: string[]) {
  return required.some((a) => authorities.value.has(a))
}

const canView = computed(() => hasAnyAuthority(DATASCOPE_VIEW_AUTHORITIES))
const canEdit = computed(() => hasAnyAuthority(DATASCOPE_EDIT_AUTHORITIES))

const scopeTypeOptions = ['ALL', 'TENANT', 'ORG', 'ORG_AND_CHILD', 'DEPT', 'DEPT_AND_CHILD', 'SELF', 'CUSTOM']

const selectedRoleId = ref<number | undefined>(undefined)
const roleOptions = ref<{ label: string; value: number }[]>([])
const tableData = ref<DataScope[]>([])
const loading = ref(false)

const columns = [
  { title: 'ID', dataIndex: 'id', width: 80 },
  { title: '模块', dataIndex: 'module', width: 140 },
  { title: '范围类型', dataIndex: 'scopeType', width: 160 },
  { title: '访问类型', dataIndex: 'accessType', width: 120 },
  { title: '创建时间', dataIndex: 'createdAt', width: 180 },
  { title: '更新时间', dataIndex: 'updatedAt', width: 180 },
  { title: '操作', dataIndex: 'action', width: 160, fixed: 'right' as const, align: 'center' as const },
]

const modalVisible = ref(false)
const modalMode = ref<'create' | 'edit'>('create')
const submitting = ref(false)
type DataScopeFormModel = {
  id: number | undefined
  module: string
  scopeType: string
  accessType: string
}
const formData = ref<DataScopeFormModel>({
  id: undefined,
  module: '',
  scopeType: '',
  accessType: '',
})

function filterOption(input: string, option?: DefaultOptionType) {
  return String(option?.label ?? '').toLowerCase().includes(input.toLowerCase())
}

async function loadRoles() {
  try {
    const roles = await getAllRoles()
    roleOptions.value = (Array.isArray(roles) ? roles : []).map((r: any) => ({
      label: `${r.name} (${r.code})`,
      value: Number(r.id),
    }))
  } catch {
    roleOptions.value = []
  }
}

async function loadData() {
  if (!selectedRoleId.value || !canView.value) {
    tableData.value = []
    return
  }
  loading.value = true
  try {
    const res = await getDataScopesByRole(selectedRoleId.value)
    tableData.value = Array.isArray(res) ? res : []
  } catch {
    tableData.value = []
  } finally {
    loading.value = false
  }
}

function handleRoleChange() {
  loadData()
}

function handleCreate() {
  modalMode.value = 'create'
  formData.value = { id: undefined, module: '', scopeType: '', accessType: '' }
  modalVisible.value = true
}

function handleEdit(record: DataScope | Record<string, any>) {
  modalMode.value = 'edit'
  formData.value = {
    id: Number(record.id),
    module: String(record.module ?? ''),
    scopeType: String(record.scopeType ?? ''),
    accessType: String(record.accessType ?? ''),
  }
  modalVisible.value = true
}

async function handleModalOk() {
  if (!formData.value.module || !formData.value.scopeType || !formData.value.accessType) {
    message.warning('请填写完整信息')
    return
  }
  submitting.value = true
  try {
    const payload: Partial<DataScope> = {
      id: formData.value.id,
      roleId: selectedRoleId.value!,
      module: formData.value.module,
      scopeType: formData.value.scopeType,
      accessType: formData.value.accessType,
    }
    await upsertDataScope({
      ...payload,
    })
    message.success(modalMode.value === 'create' ? '创建成功' : '更新成功')
    modalVisible.value = false
    loadData()
  } catch (error: any) {
    message.error('保存失败: ' + (error.message || '未知错误'))
  } finally {
    submitting.value = false
  }
}

function handleDelete(record: DataScope | Record<string, any>) {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除模块 ${String(record.module ?? '')} 的数据范围规则吗？`,
    okText: '确认',
    cancelText: '取消',
    onOk: () => {
      return deleteDataScope(Number(record.id)).then(() => {
        message.success('删除成功')
        loadData()
      }).catch((error: any) => {
        message.error('删除失败: ' + (error.message || '未知错误'))
        return Promise.reject(error)
      })
    },
  })
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
  if (canView.value) {
    loadRoles()
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
  flex: 1;
  min-height: 0;
  padding: 0 24px;
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
.action-buttons {
  display: flex;
  align-items: center;
  gap: 8px;
  justify-content: center;
}
</style>
