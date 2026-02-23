<template>
  <div class="edit-user-page">
    <a-card :title="props.mode === 'create' ? '新建用户' : props.mode === 'edit' ? '编辑用户' : '查看用户'" bordered>
      <a-form :model="form" :rules="rules" :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }"
        @finish="throttledSubmit">
        <a-form-item label="ID" v-if="form.id">
          <a-input v-model:value="form.id" disabled />
        </a-form-item>
        <a-form-item label="用户名" name="username" required>
          <a-input v-model:value="form.username" :disabled="props.mode === 'view'" />
        </a-form-item>
        <a-form-item label="密码" name="password" :required="props.mode === 'create'">
          <a-input v-model:value="form.password" type="password" :disabled="props.mode === 'view'"
            :placeholder="props.mode === 'edit' ? '留空表示不修改' : '请输入密码'" />
        </a-form-item>
        <a-form-item label="确认密码" name="confirmPassword"
          :required="props.mode === 'create' || (props.mode === 'edit' && form.password)" v-if="showConfirmPassword">
          <a-input v-model:value="form.confirmPassword" type="password" :disabled="props.mode === 'view'"
            :placeholder="props.mode === 'edit' ? '请再次输入密码' : '请再次输入密码'" />
        </a-form-item>
        <a-form-item label="昵称" name="nickname" required>
          <a-input v-model:value="form.nickname" :disabled="props.mode === 'view'" />
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
        <a-form-item label="最后登录时间" name="lastLoginAt" v-if="form.id">
          <a-input v-model:value="formattedLastLoginAt" disabled />
        </a-form-item>
        <a-form-item label="分配角色" v-if="props.mode !== 'create'">
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
import { ref, watch, computed, onMounted, nextTick } from 'vue'
import type { Rule } from 'ant-design-vue/es/form'
import { useThrottle } from '@/utils/debounce'
import { getAllRoles } from '@/api/role'
import { getUserRoles, updateUserRoles } from '@/api/user'
import { message } from 'ant-design-vue'
import RoleTransfer from './RoleTransfer.vue'

const form = ref({
  id: '',
  username: '',
  password: '',
  confirmPassword: '',
  nickname: '',
  enabled: true,
  accountNonExpired: true,
  accountNonLocked: true,
  credentialsNonExpired: true,
  lastLoginAt: ''
})

const props = defineProps({
  mode: {
    type: String as () => 'create' | 'edit' | 'view',
    default: 'view'
  },
  userData: {
    type: Object as () => any | null,
    default: () => null
  }
})

// 控制确认密码输入框的显示
const showConfirmPassword = computed(() => {
  // 创建模式下始终显示
  if (props.mode === 'create') {
    return true
  }
  // 编辑模式下，只有输入了密码才显示
  if (props.mode === 'edit') {
    return form.value.password && form.value.password.trim() !== ''
  }
  // 查看模式下不显示
  return false
})

const rules: Record<string, Rule[]> = {
  username: [
    { required: true, message: '请输入用户名' },
    { min: 3, max: 20, message: '用户名长度为3-20个字符' }
  ],
  password: [
    {
      required: (props.mode === 'create'),
      message: '请输入密码'
    },
    {
      min: 6,
      max: 20,
      message: '密码长度为6-20个字符',
      validator: (rule: any, value: string) => {
        // 编辑模式下，密码可以为空
        if (props.mode === 'edit' && !value) {
          return Promise.resolve()
        }
        // 创建模式下，密码不能为空
        if (props.mode === 'create' && !value) {
          return Promise.reject('请输入密码')
        }
        // 如果输入了密码，检查长度
        if (value && (value.length < 6 || value.length > 20)) {
          return Promise.reject('密码长度为6-20个字符')
        }
        return Promise.resolve()
      }
    }
  ],
  confirmPassword: [
    {
      validator: (rule: any, value: string) => {
        // 如果确认密码输入框不显示，跳过验证
        if (!showConfirmPassword.value) {
          return Promise.resolve()
        }
        // 编辑模式下，如果密码为空，确认密码也可以为空
        if (props.mode === 'edit' && !form.value.password && !value) {
          return Promise.resolve()
        }
        // 创建模式下，确认密码不能为空
        if (props.mode === 'create' && !value) {
          return Promise.reject('请确认密码')
        }
        // 如果输入了密码，确认密码不能为空
        if (form.value.password && !value) {
          return Promise.reject('请确认密码')
        }
        // 如果输入了确认密码，检查是否与密码一致
        if (value && value !== form.value.password) {
          return Promise.reject('两次输入的密码不一致')
        }
        return Promise.resolve()
      }
    }
  ],
  nickname: [
    { required: true, message: '请输入昵称' }
  ]
}

const emit = defineEmits(['submit', 'cancel'])

watch(() => props.userData, (newData) => {
  if (newData) {
    Object.assign(form.value, newData)
    // 编辑模式下清空密码字段，让用户选择是否修改
    if (props.mode === 'edit') {
      form.value.password = ''
      form.value.confirmPassword = ''
    }
  } else {
    // 新建模式或无数据时重置表单
    Object.assign(form.value, {
      id: '',
      username: '',
      password: '',
      confirmPassword: '',
      nickname: '',
      enabled: true,
      accountNonExpired: true,
      accountNonLocked: true,
      credentialsNonExpired: true,
      lastLoginAt: ''
    })
  }
}, { immediate: true, deep: true })

// 角色分配相关响应式数据
const roleOptions = ref<any[]>([]) // 所有可选角色
const showRoleTransfer = ref(false)
const selectedRoleIds = ref<string[]>([]) // 已分配角色ID

// 加载所有角色和当前用户已分配角色
async function loadRoles() {
  // 获取所有角色
  const all = await getAllRoles()
  roleOptions.value = (all || []).map((r: any) => ({
    key: String(r.id),
    title: r.name + (r.description ? `（${r.description}）` : ''),
    ...r
  }))
  // 获取当前用户已分配角色
  if (form.value.id) {
    const userRoleIds = await getUserRoles(Number(form.value.id))
    selectedRoleIds.value = (userRoleIds || []).map((id: any) => String(id))
  } else {
    selectedRoleIds.value = []
  }
}

// 监听用户数据变化，自动加载角色
watch(() => form.value.id, (newId) => {
  if (props.mode !== 'create' && newId) {
    loadRoles()
  } else {
    selectedRoleIds.value = []
  }
})

// 编辑/查看模式下，初始加载角色
onMounted(() => {
  if (props.mode !== 'create' && form.value.id) {
    loadRoles()
  }
})

// 保存时同步角色绑定
async function onSubmit() {
  const base = {
    id: form.value.id || undefined,
    username: form.value.username,
    nickname: form.value.nickname,
    enabled: form.value.enabled,
    accountNonExpired: form.value.accountNonExpired,
    accountNonLocked: form.value.accountNonLocked,
    credentialsNonExpired: form.value.credentialsNonExpired,
    roleIds: selectedRoleIds.value.map(id => Number(id))
  }

  const hasNewPassword = !!form.value.password && form.value.password.trim().length > 0

  const submitData = hasNewPassword
    ? {
      ...base,
      password: form.value.password,
      confirmPassword: form.value.confirmPassword
    }
    : base

  console.log('提交数据', submitData)
  emit('submit', submitData)
}

function onCancel() {
  emit('cancel')
}

const throttledSubmit = useThrottle(onSubmit, 1000)
const throttledCancel = useThrottle(onCancel, 500)

// 格式化最后登录时间显示
const formattedLastLoginAt = computed(() => {
  if (!form.value.lastLoginAt) return '-'
  try {
    const date = new Date(form.value.lastLoginAt)
    if (isNaN(date.getTime())) return '-'
    return date.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false
    })
  } catch (error) {
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
