<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { getPermissionById, getPermissionList, updatePermissionEnabled, type PermissionDetail, type PermissionListItem } from '@/api/permission'
import { getAllRoles, getRolePermissions, updateRolePermissions } from '@/api/role'
import { usePlatformScope } from '@/composables/usePlatformScope'

type RoleOption = { id: string; name: string; code?: string }

const loading = ref(false)
const saving = ref(false)
const permissionRows = ref<PermissionListItem[]>([])
const roleOptions = ref<RoleOption[]>([])
const selectedRoleId = ref<string>()
const selectedRolePermissionIds = ref<number[]>([])
const detailVisible = ref(false)
const activeDetail = ref<PermissionDetail | null>(null)
const { isPlatformScope } = usePlatformScope()

const groupedPermissions = computed(() => {
  const groups = new Map<string, PermissionListItem[]>()
  permissionRows.value.forEach((row) => {
    const key = row.moduleCode || 'ungrouped'
    const current = groups.get(key) || []
    current.push(row)
    groups.set(key, current)
  })
  return Array.from(groups.entries()).map(([moduleCode, items]) => ({
    moduleCode,
    items,
  }))
})

const selectedRoleName = computed(() => {
  const current = roleOptions.value.find((role) => role.id === selectedRoleId.value)
  return current ? `${current.name} (${current.code || '-'})` : '未选择'
})

async function loadPermissions() {
  loading.value = true
  try {
    permissionRows.value = await getPermissionList()
  } catch (error: any) {
    message.error(error?.message || '权限列表加载失败')
  } finally {
    loading.value = false
  }
}

async function loadRoles() {
  try {
    const roles = await getAllRoles()
    roleOptions.value = (roles || []).map((role: any) => ({
      id: String(role.id),
      name: role.name,
      code: role.code,
    }))
    if (!selectedRoleId.value && roleOptions.value.length > 0) {
      selectedRoleId.value = roleOptions.value[0]?.id
      await syncRolePermissions()
    }
  } catch (error: any) {
    message.error(error?.message || '角色列表加载失败')
  }
}

async function syncRolePermissions() {
  if (!selectedRoleId.value) {
    selectedRolePermissionIds.value = []
    return
  }
  try {
    selectedRolePermissionIds.value = await getRolePermissions(Number(selectedRoleId.value))
  } catch (error: any) {
    message.error(error?.message || '角色权限加载失败')
  }
}

async function handleToggleEnabled(row: PermissionListItem, checked: unknown) {
  const enabled = checked === true
  try {
    await updatePermissionEnabled(row.id, enabled)
    row.enabled = enabled
    message.success('权限状态已更新')
  } catch (error: any) {
    message.error(error?.message || '权限状态更新失败')
  }
}

async function showBoundRoles(permissionId: number) {
  try {
    activeDetail.value = await getPermissionById(permissionId)
    detailVisible.value = true
  } catch (error: any) {
    message.error(error?.message || '权限详情加载失败')
  }
}

async function saveRolePermissions() {
  if (!selectedRoleId.value) {
    message.warning('请先选择角色')
    return
  }
  saving.value = true
  try {
    await updateRolePermissions(Number(selectedRoleId.value), { permissionIds: selectedRolePermissionIds.value })
    message.success('角色权限保存成功')
  } catch (error: any) {
    message.error(error?.message || '角色权限保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  if (!isPlatformScope.value) {
    return
  }
  await Promise.all([loadPermissions(), loadRoles()])
})
</script>

<template>
  <div class="permission-control-page">
    <a-card v-if="!isPlatformScope" title="平台作用域限制">
      当前会话不是 PLATFORM 作用域，已阻止加载平台权限控制面数据。请切换到平台作用域后重试。
    </a-card>
    <template v-else>
    <a-card title="Permission 主数据管理" :loading="loading">
      <div class="group-list">
        <a-card
          v-for="group in groupedPermissions"
          :key="group.moduleCode"
          class="group-card"
          :title="`模块：${group.moduleCode}`"
          size="small"
        >
          <a-table
            :data-source="group.items"
            :pagination="false"
            row-key="id"
            size="small"
          >
            <a-table-column title="编码" data-index="permissionCode" key="permissionCode" />
            <a-table-column title="名称" data-index="permissionName" key="permissionName" />
            <a-table-column title="启用" key="enabled">
              <template #default="{ record }">
                <a-switch :checked="record.enabled" @change="(checked) => handleToggleEnabled(record, checked)" />
              </template>
            </a-table-column>
            <a-table-column title="绑定角色数" data-index="boundRoleCount" key="boundRoleCount" />
            <a-table-column title="操作" key="action">
              <template #default="{ record }">
                <a-button type="link" @click="showBoundRoles(record.id)">查看绑定角色</a-button>
              </template>
            </a-table-column>
          </a-table>
        </a-card>
      </div>
    </a-card>

    <a-card title="角色权限编辑" class="role-editor-card">
      <div class="role-editor-row">
        <span>当前角色：</span>
        <a-select
          v-model:value="selectedRoleId"
          style="width: 320px"
          placeholder="选择角色"
          @change="syncRolePermissions"
        >
          <a-select-option v-for="role in roleOptions" :key="role.id" :value="role.id">
            {{ role.name }} ({{ role.code || '-' }})
          </a-select-option>
        </a-select>
      </div>
      <div class="role-editor-row">
        <span>角色：{{ selectedRoleName }}</span>
      </div>
      <a-checkbox-group v-model:value="selectedRolePermissionIds" class="permission-checkbox-group">
        <a-row :gutter="[16, 8]">
          <a-col v-for="item in permissionRows" :key="item.id" :span="8">
            <a-checkbox :value="item.id" :disabled="!item.enabled && !selectedRolePermissionIds.includes(item.id)">
              {{ item.permissionCode }}
            </a-checkbox>
          </a-col>
        </a-row>
      </a-checkbox-group>
      <div class="editor-action">
        <a-button type="primary" :loading="saving" @click="saveRolePermissions">保存角色权限</a-button>
      </div>
    </a-card>

    <a-modal v-model:open="detailVisible" title="权限绑定角色" :footer="null">
      <template v-if="activeDetail">
        <p><b>权限：</b>{{ activeDetail.permissionCode }}</p>
        <a-list :data-source="activeDetail.boundRoles" bordered size="small">
          <template #renderItem="{ item }">
            <a-list-item>{{ item.roleCode }} - {{ item.roleName }}</a-list-item>
          </template>
        </a-list>
      </template>
    </a-modal>
    </template>
  </div>
</template>

<style scoped>
.permission-control-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.group-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.group-card {
  border: 1px solid #f0f0f0;
}

.role-editor-card {
  margin-bottom: 16px;
}

.role-editor-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.permission-checkbox-group {
  width: 100%;
}

.editor-action {
  margin-top: 16px;
}
</style>
