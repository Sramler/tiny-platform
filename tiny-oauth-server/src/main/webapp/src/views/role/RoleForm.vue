<template>
  <a-form
    :model="form"
    :rules="rules"
    ref="formRef"
    layout="horizontal"
    :label-col="{ span: 6 }"
    :wrapper-col="{ span: 16 }"
  >
    <a-form-item label="角色名" name="name" required>
      <a-input v-model:value="form.name" placeholder="请输入角色名" />
    </a-form-item>
    <a-form-item label="角色标识" name="code" required>
      <a-input v-model:value="form.code" placeholder="请输入角色标识，如：ROLE_ADMIN" />
    </a-form-item>
    <a-form-item label="描述" name="description">
      <a-input v-model:value="form.description" placeholder="请输入描述" />
    </a-form-item>
    <a-form-item label="是否内置" name="builtin">
      <a-switch v-model:checked="form.builtin" />
    </a-form-item>
    <a-form-item label="是否启用" name="enabled">
      <a-switch v-model:checked="form.enabled" />
    </a-form-item>
    <a-form-item label="配置用户" name="users">
      <a-button @click="openUserTransfer">配置用户</a-button>
      <UserTransfer
        v-model:modelValue="selectedUserIds"
        :allUsers="userOptions"
        v-model:open="showUserTransfer"
        title="配置用户"
      />
    </a-form-item>
    <a-form-item :wrapper-col="{ offset: 6, span: 16 }">
      <a-button @click="onCancel">取消</a-button>
      <a-button type="primary" style="margin-left:8px;" @click="onSubmit">保存</a-button>
    </a-form-item>
  </a-form>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import type { FormInstance } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'
import { getRoleUsers, updateRoleUsers } from '@/api/role' // 角色相关API
import { userList } from '@/api/user' // 用户列表API
import UserTransfer from './UserTransfer.vue'

interface RoleFormModel {
  id: string
  name: string
  code: string
  description: string
  builtin: boolean
  enabled: boolean
}

// 定义props
const props = defineProps<{
  mode: 'create' | 'edit', // 表单模式
  roleData?: Partial<RoleFormModel> | null // 角色数据
}>()
// 定义emit
const emit = defineEmits(['submit', 'cancel'])
// 表单数据
const form = ref<RoleFormModel>({
  id: '',
  name: '',
  code: '',
  description: '',
  builtin: false,
  enabled: true
})
// 校验规则
const rules: Record<string, Rule[]> = {
  name: [
    { required: true, message: '角色名不能为空', trigger: 'blur' },
    { min: 2, max: 50, message: '长度2-50字符', trigger: 'blur' }
  ],
  code: [
    { required: true, message: '角色标识不能为空', trigger: 'blur' },
    { min: 2, max: 50, message: '长度2-50字符', trigger: 'blur' },
    { pattern: /^[A-Z_]+$/, message: '角色标识只能包含大写字母和下划线', trigger: 'blur' }
  ],
  description: [
    { max: 100, message: '描述最多100字符', trigger: 'blur' }
  ]
}
// 表单ref
const formRef = ref<FormInstance>()
// 用户Transfer相关响应式数据
const userOptions = ref<{ key: string, title: string, [key: string]: any }[]>([])
const selectedUserIds = ref<string[]>([])
// 控制分配用户弹窗显示
const showUserTransfer = ref(false)

// 监听props变化，回填数据
watch(() => props.roleData, (val) => {
  if (val) {
    form.value = {
      id: val.id ?? '',
      name: val.name ?? '',
      code: val.code ?? '',
      description: val.description ?? '',
      builtin: Boolean(val.builtin),
      enabled: val.enabled ?? true,
    }
  } else {
    form.value = { id: '', name: '', code: '', description: '', builtin: false, enabled: true }
  }
}, { immediate: true })

// 查询所有用户和当前角色已分配用户
async function loadUsers() {
  // 查询所有用户（用 userList，假设用户量不大，取前1000条）
  const res = await userList({ current: 1, pageSize: 1000 })
  userOptions.value = (res.records || []).map((u: any) => ({
    key: String(u.id),
    title: u.username + (u.nickname ? `（${u.nickname}）` : ''),
    ...u
  }))
  // 查询当前角色已分配用户
  if (form.value.id) {
    const userIds = await getRoleUsers(Number(form.value.id))
    // 兼容后端返回数组或对象数组
    if (Array.isArray(userIds)) {
      selectedUserIds.value = userIds.map((id: any) => String(typeof id === 'object' ? id.id : id))
    } else if (userIds && Array.isArray(userIds.data)) {
      selectedUserIds.value = userIds.data.map((id: any) => String(typeof id === 'object' ? id.id : id))
    } else {
      selectedUserIds.value = []
    }
  } else {
    selectedUserIds.value = []
  }
}

// 监听角色数据变化，自动加载用户
watch(() => form.value.id, (newId) => {
  if (props.mode !== 'create' && newId) {
    loadUsers()
  } else {
    selectedUserIds.value = []
  }
})

// 取消
function onCancel() {
  emit('cancel')
}
// 提交
function onSubmit() {
  console.log('保存按钮点击，开始处理...'); // 日志1: 函数被调用
  console.log('当前的表单引用(formRef):', formRef.value); // 日志2: 检查formRef

  if (!formRef.value) {
    message.error('表单实例未准备好，请稍后重试');
    console.error('formRef.value is null or undefined. Cannot validate form.');
    return;
  }

  formRef.value.validate()
    .then(() => {
      const submitData = {
        ...form.value,
        userIds: selectedUserIds.value.map(id => Number(id))
      }
      console.log('提交数据', submitData) // 这里应有 userIds
      emit('submit', submitData)
    })
    .catch((errorInfo: any) => {
      console.error('表单验证失败:', errorInfo); // 日志4: 验证失败
      message.warning('请检查表单，有必填项未填写或格式不正确');
    });
}

// 打开配置用户弹窗，先加载数据
function openUserTransfer() {
  loadUsers().then(() => {
    showUserTransfer.value = true
  })
}
</script>
