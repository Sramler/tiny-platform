<template>
  <div class="edit-user-page">
    <a-card :title="props.mode === 'create' ? '新建用户' : props.mode === 'edit' ? '编辑用户' : '查看用户'" bordered>
      <a-form :model="form" :rules="rules" :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }" @finish="throttledSubmit">
        <a-form-item v-if="form.id" label="ID">
          <a-input v-model:value="form.id" disabled />
        </a-form-item>
        <a-form-item label="用户名" name="username" required>
          <a-input v-model:value="form.username" :disabled="props.mode === 'view'" />
        </a-form-item>
        <a-form-item label="密码" name="password" :required="props.mode === 'create'">
          <a-input
            v-model:value="form.password"
            type="password"
            :disabled="props.mode === 'view'"
            :placeholder="props.mode === 'edit' ? '留空表示不修改' : '请输入密码'"
          />
        </a-form-item>
        <a-form-item
          v-if="showConfirmPassword"
          label="确认密码"
          name="confirmPassword"
          :required="props.mode === 'create' || (props.mode === 'edit' && Boolean(form.password))"
        >
          <a-input
            v-model:value="form.confirmPassword"
            type="password"
            :disabled="props.mode === 'view'"
            :placeholder="props.mode === 'edit' ? '请再次输入密码' : '请再次输入密码'"
          />
        </a-form-item>
        <a-form-item label="昵称" name="nickname" required>
          <a-input v-model:value="form.nickname" :disabled="props.mode === 'view'" />
        </a-form-item>
        <a-form-item label="邮箱" name="email">
          <a-input v-model:value="form.email" :disabled="props.mode === 'view'" placeholder="请输入邮箱" />
        </a-form-item>
        <a-form-item label="手机号" name="phone">
          <a-input v-model:value="form.phone" :disabled="props.mode === 'view'" placeholder="请输入手机号" />
        </a-form-item>
        <a-form-item label="组织/部门" name="unitIds">
          <a-select
            v-model:value="form.unitIds"
            mode="multiple"
            :disabled="props.mode === 'view'"
            :options="unitOptions"
            placeholder="请选择组织/部门归属"
            option-filter-prop="label"
            allow-clear
          />
        </a-form-item>
        <a-form-item label="主组织/部门" name="primaryUnitId">
          <a-select
            v-model:value="form.primaryUnitId"
            :disabled="props.mode === 'view' || selectedUnitOptions.length === 0"
            :options="selectedUnitOptions"
            placeholder="请选择主组织/部门"
            allow-clear
          />
        </a-form-item>
        <a-form-item label="是否启用" name="enabled">
          <a-switch v-model:checked="form.enabled" :disabled="props.mode === 'view'" />
        </a-form-item>
        <a-form-item label="账号未过期" name="accountNonExpired">
          <a-switch v-model:checked="form.accountNonExpired" :disabled="props.mode === 'view'" />
        </a-form-item>
        <a-form-item label="账号未锁定" name="accountNonLocked">
          <a-switch v-model:checked="form.accountNonLocked" :disabled="props.mode === 'view'" />
        </a-form-item>
        <a-form-item label="密码未过期" name="credentialsNonExpired">
          <a-switch v-model:checked="form.credentialsNonExpired" :disabled="props.mode === 'view'" />
        </a-form-item>
        <a-form-item v-if="form.id" label="最后登录时间" name="lastLoginAt">
          <a-input v-model:value="formattedLastLoginAt" disabled />
        </a-form-item>
        <a-form-item v-if="props.mode === 'edit'" label="分配角色">
          <a-button @click="showRoleTransfer = true">分配角色</a-button>
          <RoleTransfer v-model:modelValue="selectedRoleIds" :allRoles="roleOptions" v-model:open="showRoleTransfer" />
        </a-form-item>
        <a-form-item :wrapper-col="{ offset: 6, span: 16 }">
          <a-button v-if="props.mode !== 'view'" type="primary" html-type="submit">保存</a-button>
          <a-button style="margin-left: 8px" @click="throttledCancel">返回</a-button>
        </a-form-item>
      </a-form>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import type { Rule } from 'ant-design-vue/es/form'
import { getAllRoles } from '@/api/role'
import { getUserById, getUserRoles } from '@/api/user'
import { getOrgList, listUserUnits, type OrgUnit } from '@/api/org'
import { useThrottle } from '@/utils/debounce'
import RoleTransfer from './RoleTransfer.vue'

type UserFormData = {
  id: string
  username: string
  password: string
  confirmPassword: string
  nickname: string
  email: string
  phone: string
  unitIds: number[]
  primaryUnitId: number | undefined
  enabled: boolean
  accountNonExpired: boolean
  accountNonLocked: boolean
  credentialsNonExpired: boolean
  lastLoginAt: string
}

type SelectOption = {
  label: string
  value: number
}

const props = defineProps({
  mode: {
    type: String as () => 'create' | 'edit' | 'view',
    default: 'view',
  },
  userData: {
    type: Object as () => Record<string, any> | null,
    default: () => null,
  },
})

const emit = defineEmits(['submit', 'cancel'])

function createDefaultForm(): UserFormData {
  return {
    id: '',
    username: '',
    password: '',
    confirmPassword: '',
    nickname: '',
    email: '',
    phone: '',
    unitIds: [],
    primaryUnitId: undefined,
    enabled: true,
    accountNonExpired: true,
    accountNonLocked: true,
    credentialsNonExpired: true,
    lastLoginAt: '',
  }
}

function normalizeUnitIds(unitIds: unknown): number[] {
  if (!Array.isArray(unitIds)) {
    return []
  }
  return Array.from(
    new Set(
      unitIds
        .map((unitId) => Number(unitId))
        .filter((unitId) => Number.isInteger(unitId) && unitId > 0),
    ),
  )
}

const form = ref<UserFormData>(createDefaultForm())
const roleOptions = ref<any[]>([])
const showRoleTransfer = ref(false)
const selectedRoleIds = ref<string[]>([])
const unitOptions = ref<SelectOption[]>([])

const selectedUnitOptions = computed(() =>
  unitOptions.value.filter((option) => form.value.unitIds.includes(option.value)),
)

const showConfirmPassword = computed(() => {
  if (props.mode === 'create') {
    return true
  }
  if (props.mode === 'edit') {
    return form.value.password.trim() !== ''
  }
  return false
})

const rules: Record<string, Rule[]> = {
  username: [
    { required: true, message: '请输入用户名' },
    { min: 3, max: 20, message: '用户名长度为3-20个字符' },
  ],
  password: [
    {
      required: props.mode === 'create',
      message: '请输入密码',
    },
    {
      validator: (_rule: Rule, value: string) => {
        if (props.mode === 'edit' && !value) {
          return Promise.resolve()
        }
        if (props.mode === 'create' && !value) {
          return Promise.reject('请输入密码')
        }
        if (value && (value.length < 6 || value.length > 20)) {
          return Promise.reject('密码长度为6-20个字符')
        }
        return Promise.resolve()
      },
    },
  ],
  confirmPassword: [
    {
      validator: (_rule: Rule, value: string) => {
        if (!showConfirmPassword.value) {
          return Promise.resolve()
        }
        if (!value) {
          return Promise.reject('请确认密码')
        }
        if (value !== form.value.password) {
          return Promise.reject('两次输入的密码不一致')
        }
        return Promise.resolve()
      },
    },
  ],
  nickname: [{ required: true, message: '请输入昵称' }],
  email: [{ type: 'email', message: '邮箱格式不正确' }],
  phone: [{ pattern: /^(1[3-9]\d{9})?$/, message: '手机号格式不正确' }],
  primaryUnitId: [
    {
      validator: (_rule: Rule, value: number | undefined) => {
        if (form.value.unitIds.length === 0) {
          return Promise.resolve()
        }
        if (!value) {
          return Promise.reject('请选择主组织/部门')
        }
        if (!form.value.unitIds.includes(value)) {
          return Promise.reject('主组织/部门必须属于已选归属')
        }
        return Promise.resolve()
      },
    },
  ],
}

function resetForm() {
  Object.assign(form.value, createDefaultForm())
}

function applyBaseUserData(userData: Record<string, any> | null) {
  resetForm()
  if (!userData) {
    return
  }
  Object.assign(form.value, {
    id: userData.id ? String(userData.id) : '',
    username: userData.username ?? '',
    nickname: userData.nickname ?? '',
    email: userData.email ?? '',
    phone: userData.phone ?? '',
    unitIds: normalizeUnitIds(userData.unitIds),
    primaryUnitId: userData.primaryUnitId ? Number(userData.primaryUnitId) : undefined,
    enabled: userData.enabled ?? true,
    accountNonExpired: userData.accountNonExpired ?? true,
    accountNonLocked: userData.accountNonLocked ?? true,
    credentialsNonExpired: userData.credentialsNonExpired ?? true,
    lastLoginAt: userData.lastLoginAt ?? '',
  })
}

async function loadUnitOptions() {
  const units = await getOrgList()
  unitOptions.value = (units || [])
    .filter((unit: OrgUnit) => unit.status === 'ACTIVE')
    .map((unit: OrgUnit) => ({
      label: `${unit.name} (${unit.unitType})`,
      value: unit.id,
    }))
}

async function loadRoles(userId: number) {
  const all = await getAllRoles()
  roleOptions.value = (all || []).map((role: any) => ({
    key: String(role.id),
    title: role.name + (role.description ? `（${role.description}）` : ''),
    ...role,
  }))
  const userRoleIds = await getUserRoles(userId)
  selectedRoleIds.value = (userRoleIds || []).map((id: any) => String(id))
}

async function loadUserDetail(userId: number) {
  const detail = await getUserById(String(userId))
  Object.assign(form.value, {
    id: detail.id ? String(detail.id) : String(userId),
    username: detail.username ?? form.value.username,
    nickname: detail.nickname ?? '',
    email: detail.email ?? '',
    phone: detail.phone ?? '',
    enabled: detail.enabled ?? true,
    accountNonExpired: detail.accountNonExpired ?? true,
    accountNonLocked: detail.accountNonLocked ?? true,
    credentialsNonExpired: detail.credentialsNonExpired ?? true,
    lastLoginAt: detail.lastLoginAt ?? '',
  })
}

async function loadUserUnits(userId: number) {
  const memberships = await listUserUnits(userId)
  form.value.unitIds = normalizeUnitIds((memberships || []).map((membership) => membership.unitId))
  const primaryMembership = (memberships || []).find((membership) => membership.isPrimary)
  form.value.primaryUnitId = primaryMembership ? primaryMembership.unitId : undefined
}

async function hydrateForm(userData: Record<string, any> | null) {
  applyBaseUserData(userData)
  if (props.mode !== 'create') {
    form.value.password = ''
    form.value.confirmPassword = ''
  }
  if (!userData?.id) {
    selectedRoleIds.value = []
    return
  }
  const userId = Number(userData.id)
  if (!Number.isInteger(userId)) {
    return
  }
  await Promise.all([
    props.mode === 'edit' ? loadRoles(userId) : Promise.resolve(),
    loadUserDetail(userId),
    loadUserUnits(userId),
  ])
}

watch(
  () => props.userData,
  (newData) => {
    void hydrateForm(newData)
  },
  { immediate: true, deep: true },
)

watch(
  () => form.value.unitIds,
  (unitIds) => {
    const normalized = normalizeUnitIds(unitIds)
    if (normalized.length !== unitIds.length || normalized.some((unitId, index) => unitId !== unitIds[index])) {
      form.value.unitIds = normalized
      return
    }
    if (form.value.primaryUnitId && !normalized.includes(form.value.primaryUnitId)) {
      form.value.primaryUnitId = undefined
    }
  },
  { deep: true },
)

onMounted(() => {
  void loadUnitOptions()
})

async function onSubmit() {
  const base = {
    id: form.value.id || undefined,
    username: form.value.username,
    nickname: form.value.nickname,
    email: form.value.email || undefined,
    phone: form.value.phone || undefined,
    unitIds: form.value.unitIds,
    primaryUnitId: form.value.primaryUnitId,
    enabled: form.value.enabled,
    accountNonExpired: form.value.accountNonExpired,
    accountNonLocked: form.value.accountNonLocked,
    credentialsNonExpired: form.value.credentialsNonExpired,
    roleIds: selectedRoleIds.value.map((id) => Number(id)),
  }

  const hasNewPassword = form.value.password.trim().length > 0
  const submitData = hasNewPassword
    ? {
        ...base,
        password: form.value.password,
        confirmPassword: form.value.confirmPassword,
      }
    : base

  emit('submit', submitData)
}

function onCancel() {
  emit('cancel')
}

const throttledSubmit = useThrottle(onSubmit, 1000)
const throttledCancel = useThrottle(onCancel, 500)

const formattedLastLoginAt = computed(() => {
  if (!form.value.lastLoginAt) return '-'
  try {
    const date = new Date(form.value.lastLoginAt)
    if (Number.isNaN(date.getTime())) return '-'
    return date.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false,
    })
  } catch (_error) {
    return '-'
  }
})
</script>

<style scoped>
.edit-user-page {
  padding: 0;
  height: 100%;
  background: #fff;
}
</style>
