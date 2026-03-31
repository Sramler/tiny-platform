<template>
  <a-form
    :model="form"
    :rules="rules"
    ref="formRef"
    layout="horizontal"
    :label-col="{ span: 6 }"
    :wrapper-col="{ span: 16 }"
  >
    <a-form-item label="租户编码" name="code" required>
      <a-input v-model:value="form.code" placeholder="请输入租户编码" />
    </a-form-item>
    <a-form-item label="租户名称" name="name" required>
      <a-input v-model:value="form.name" placeholder="请输入租户名称" />
    </a-form-item>
    <a-form-item label="域名" name="domain">
      <a-input v-model:value="form.domain" placeholder="如: tenant.example.com" />
    </a-form-item>
    <a-form-item label="套餐" name="planCode">
      <a-input v-model:value="form.planCode" placeholder="如: pro/enterprise" />
    </a-form-item>
    <a-form-item label="到期时间" name="expiresAt">
      <a-input v-model:value="form.expiresAt" placeholder="YYYY-MM-DDTHH:mm:ss" />
    </a-form-item>
    <a-form-item label="最大用户数" name="maxUsers">
      <a-input-number v-model:value="form.maxUsers" :min="0" style="width: 100%" />
    </a-form-item>
    <a-form-item label="存储配额(GB)" name="maxStorageGb">
      <a-input-number v-model:value="form.maxStorageGb" :min="0" style="width: 100%" />
    </a-form-item>
    <a-form-item label="联系人" name="contactName">
      <a-input v-model:value="form.contactName" />
    </a-form-item>
    <a-form-item label="联系邮箱" name="contactEmail">
      <a-input v-model:value="form.contactEmail" />
    </a-form-item>
    <a-form-item label="联系电话" name="contactPhone">
      <a-input v-model:value="form.contactPhone" />
    </a-form-item>
    <template v-if="isCreateMode">
      <a-form-item label="管理员用户名" name="initialAdminUsername" required>
        <a-input v-model:value="form.initialAdminUsername" placeholder="请输入初始管理员用户名" />
      </a-form-item>
      <a-form-item label="管理员昵称" name="initialAdminNickname">
        <a-input v-model:value="form.initialAdminNickname" placeholder="默认：租户管理员" />
      </a-form-item>
      <a-form-item label="管理员邮箱" name="initialAdminEmail">
        <a-input v-model:value="form.initialAdminEmail" placeholder="请输入管理员邮箱" />
      </a-form-item>
      <a-form-item label="管理员手机" name="initialAdminPhone">
        <a-input v-model:value="form.initialAdminPhone" placeholder="请输入管理员手机号" />
      </a-form-item>
      <a-form-item label="初始密码" name="initialAdminPassword" required>
        <a-input-password v-model:value="form.initialAdminPassword" placeholder="请输入初始密码" />
      </a-form-item>
      <a-form-item label="确认密码" name="initialAdminConfirmPassword" required>
        <a-input-password v-model:value="form.initialAdminConfirmPassword" placeholder="请再次输入密码" />
      </a-form-item>
    </template>
    <a-form-item label="启用" name="enabled">
      <a-switch v-model:checked="form.enabled" />
    </a-form-item>
    <a-form-item label="备注" name="remark">
      <a-textarea v-model:value="form.remark" :rows="3" />
    </a-form-item>

    <a-form-item :wrapper-col="{ offset: 6, span: 16 }">
      <a-button @click="onCancel">取消</a-button>
      <a-button type="primary" style="margin-left:8px;" @click="onSubmit">保存</a-button>
    </a-form-item>
  </a-form>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { message } from 'ant-design-vue'

const props = defineProps<{
  mode: 'create' | 'edit'
  tenantData?: any
}>()

const emit = defineEmits(['submit', 'cancel'])
const isCreateMode = computed(() => props.mode === 'create')

const form = ref({
  id: '',
  code: '',
  name: '',
  domain: '',
  planCode: '',
  expiresAt: '',
  maxUsers: undefined as number | undefined,
  maxStorageGb: undefined as number | undefined,
  contactName: '',
  contactEmail: '',
  contactPhone: '',
  initialAdminUsername: '',
  initialAdminNickname: '租户管理员',
  initialAdminEmail: '',
  initialAdminPhone: '',
  initialAdminPassword: '',
  initialAdminConfirmPassword: '',
  enabled: true,
  remark: ''
})

const rules = {
  code: [
    { required: true, message: '租户编码不能为空', trigger: 'blur' },
    { min: 2, max: 64, message: '长度2-64字符', trigger: 'blur' }
  ],
  name: [
    { required: true, message: '租户名称不能为空', trigger: 'blur' },
    { min: 2, max: 128, message: '长度2-128字符', trigger: 'blur' }
  ],
  domain: [
    { max: 255, message: '域名长度最多255字符', trigger: 'blur' }
  ],
  planCode: [
    { max: 64, message: '套餐长度最多64字符', trigger: 'blur' }
  ],
  contactEmail: [
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  initialAdminUsername: [
    { required: true, message: '管理员用户名不能为空', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度3-20字符', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]+$/, message: '用户名只能包含字母、数字和下划线', trigger: 'blur' }
  ],
  initialAdminNickname: [
    { max: 50, message: '昵称长度最多50字符', trigger: 'blur' }
  ],
  initialAdminEmail: [
    { type: 'email', message: '管理员邮箱格式不正确', trigger: 'blur' }
  ],
  initialAdminPhone: [
    { pattern: /^(1[3-9]\d{9})?$/, message: '管理员手机号格式不正确', trigger: 'blur' }
  ],
  initialAdminPassword: [
    { required: true, message: '初始密码不能为空', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度6-20字符', trigger: 'blur' }
  ],
  initialAdminConfirmPassword: [
    { required: true, message: '确认密码不能为空', trigger: 'blur' },
    {
      validator: async (_rule: unknown, value: string) => {
        if (value !== form.value.initialAdminPassword) {
          return Promise.reject(new Error('两次输入的密码不一致'))
        }
        return Promise.resolve()
      },
      trigger: 'blur'
    }
  ]
}

const formRef = ref()

watch(() => props.tenantData, (val) => {
  if (val) {
    form.value = {
      id: val.id || '',
      code: val.code || '',
      name: val.name || '',
      domain: val.domain || '',
      planCode: val.planCode || '',
      expiresAt: val.expiresAt || '',
      maxUsers: val.maxUsers,
      maxStorageGb: val.maxStorageGb,
      contactName: val.contactName || '',
      contactEmail: val.contactEmail || '',
      contactPhone: val.contactPhone || '',
      initialAdminUsername: '',
      initialAdminNickname: '租户管理员',
      initialAdminEmail: '',
      initialAdminPhone: '',
      initialAdminPassword: '',
      initialAdminConfirmPassword: '',
      enabled: val.enabled !== false,
      remark: val.remark || ''
    }
  } else {
    form.value = {
      id: '',
      code: '',
      name: '',
      domain: '',
      planCode: '',
      expiresAt: '',
      maxUsers: undefined,
      maxStorageGb: undefined,
      contactName: '',
      contactEmail: '',
      contactPhone: '',
      initialAdminUsername: '',
      initialAdminNickname: '租户管理员',
      initialAdminEmail: '',
      initialAdminPhone: '',
      initialAdminPassword: '',
      initialAdminConfirmPassword: '',
      enabled: true,
      remark: ''
    }
  }
}, { immediate: true })

function onCancel() {
  emit('cancel')
}

function onSubmit() {
  if (!formRef.value) {
    message.error('表单实例未准备好，请稍后重试')
    return
  }
  formRef.value.validate()
    .then(() => {
      emit('submit', { ...form.value })
    })
    .catch(() => {
      message.warning('请检查表单，有必填项未填写或格式不正确')
    })
}
</script>
