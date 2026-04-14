<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { createRole, deleteRole, roleList, updateRole, updateRolePermissions } from '@/api/role'
import { tenantList } from '@/api/tenant'
import ResourceTransfer from '@/views/role/ResourceTransfer.vue'
import router from '@/router'
import { usePlatformScope } from '@/composables/usePlatformScope'

type TemplateRole = {
  id: number
  name: string
  code: string
  description?: string
  enabled?: boolean
  builtin?: boolean
}

const loading = ref(false)
const roles = ref<TemplateRole[]>([])
const allTenantCodes = ref<string[]>([])
const selectedRole = ref<TemplateRole | null>(null)
const formVisible = ref(false)
const permissionVisible = ref(false)
const formModel = ref<Partial<TemplateRole>>({
  name: '',
  code: '',
  description: '',
  enabled: true,
  builtin: false,
})
const { isPlatformScope } = usePlatformScope()

const derivedTenantCount = computed(() => {
  const roleCode = selectedRole.value?.code
  if (!roleCode) {
    return 0
  }
  return allTenantCodes.value.length
})

async function loadRoles() {
  loading.value = true
  try {
    const result = await roleList({ current: 1, pageSize: 200 })
    roles.value = (result?.content || []).map((item: any) => ({
      id: Number(item.id),
      name: item.name,
      code: item.code,
      description: item.description,
      enabled: item.enabled,
      builtin: item.builtin,
    }))
    if (!selectedRole.value && roles.value.length > 0) {
      selectedRole.value = roles.value[0] || null
    }
  } catch (error: any) {
    message.error(error?.message || '模板角色列表加载失败')
  } finally {
    loading.value = false
  }
}

async function loadTenants() {
  try {
    const result = await tenantList({ page: 0, size: 200 })
    allTenantCodes.value = (result?.content || []).map((tenant: any) => tenant.code).filter(Boolean)
  } catch {
    allTenantCodes.value = []
  }
}

function openCreateForm() {
  formModel.value = { name: '', code: '', description: '', enabled: true, builtin: false }
  selectedRole.value = null
  formVisible.value = true
}

function openEditForm(role: TemplateRole) {
  selectedRole.value = role
  formModel.value = { ...role }
  formVisible.value = true
}

async function submitForm() {
  try {
    if (selectedRole.value?.id) {
      await updateRole(String(selectedRole.value.id), formModel.value as any)
      message.success('模板角色更新成功')
    } else {
      await createRole(formModel.value as any)
      message.success('模板角色创建成功')
    }
    formVisible.value = false
    await loadRoles()
  } catch (error: any) {
    message.error(error?.message || '模板角色保存失败')
  }
}

function confirmDelete(role: TemplateRole) {
  Modal.confirm({
    title: '确认删除模板角色',
    content: `确认删除 ${role.name} (${role.code}) 吗？`,
    onOk: async () => {
      await deleteRole(String(role.id))
      message.success('模板角色删除成功')
      await loadRoles()
    },
  })
}

function openPermissionBinding(role: TemplateRole) {
  selectedRole.value = role
  permissionVisible.value = true
}

async function onPermissionSaved(payload: { permissionIds: number[] }) {
  if (!selectedRole.value) {
    message.warning('未选择模板角色')
    return
  }
  try {
    await updateRolePermissions(selectedRole.value.id, { permissionIds: payload.permissionIds })
    permissionVisible.value = false
    message.success('模板角色权限绑定已更新')
  } catch (error: any) {
    message.error(error?.message || '模板角色权限绑定失败')
  }
}

function goTenantTemplateDiff() {
  router.push('/platform/tenants')
}

onMounted(async () => {
  if (!isPlatformScope.value) {
    return
  }
  await Promise.all([loadRoles(), loadTenants()])
})
</script>

<template>
  <div class="template-role-page">
    <a-card v-if="!isPlatformScope" title="平台作用域限制">
      当前会话不是 PLATFORM 作用域，已阻止加载模板角色治理数据。请切换到平台作用域后重试。
    </a-card>
    <template v-else>
    <a-card title="平台模板角色治理" :loading="loading">
      <div class="toolbar">
        <a-button type="primary" @click="openCreateForm">新建模板角色</a-button>
        <a-button @click="goTenantTemplateDiff">查看 Tenant Template Diff</a-button>
      </div>
      <a-table :data-source="roles" :pagination="false" row-key="id">
        <a-table-column title="名称" data-index="name" key="name" />
        <a-table-column title="编码" data-index="code" key="code" />
        <a-table-column title="描述" data-index="description" key="description" />
        <a-table-column title="状态" key="enabled">
          <template #default="{ record }">
            <a-tag :color="record.enabled ? 'green' : 'red'">{{ record.enabled ? '启用' : '禁用' }}</a-tag>
          </template>
        </a-table-column>
        <a-table-column title="操作" key="action" width="320">
          <template #default="{ record }">
            <a-space>
              <a-button type="link" @click="openEditForm(record)">编辑</a-button>
              <a-button type="link" @click="openPermissionBinding(record)">绑定权限</a-button>
              <a-button type="link" danger @click="confirmDelete(record)">删除</a-button>
            </a-space>
          </template>
        </a-table-column>
      </a-table>
    </a-card>

    <a-card title="派生影响范围（最小可用）">
      <p v-if="selectedRole">
        当前选中模板角色：<b>{{ selectedRole.name }}</b> ({{ selectedRole.code }})。
      </p>
      <p>
        当前已加载租户样本数（tenantList size=200 上限）：<b>{{ derivedTenantCount }}</b>。
      </p>
      <p>模板角色不能直接分配用户；如需租户侧生效，请通过租户模板派生链路处理。</p>
    </a-card>

    <a-modal v-model:open="formVisible" title="模板角色" @ok="submitForm" @cancel="formVisible = false">
      <a-form :model="formModel" layout="vertical">
        <a-form-item label="名称" required>
          <a-input v-model:value="formModel.name" />
        </a-form-item>
        <a-form-item label="编码" required>
          <a-input v-model:value="formModel.code" />
        </a-form-item>
        <a-form-item label="描述">
          <a-input v-model:value="formModel.description" />
        </a-form-item>
        <a-form-item label="启用">
          <a-switch v-model:checked="formModel.enabled" />
        </a-form-item>
      </a-form>
    </a-modal>

    <ResourceTransfer
      v-if="permissionVisible && selectedRole"
      :open="permissionVisible"
      :role-id="selectedRole.id"
      title="绑定模板角色权限"
      @update:open="permissionVisible = $event"
      @submit="onPermissionSaved"
    />
    </template>
  </div>
</template>

<style scoped>
.template-role-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
</style>
