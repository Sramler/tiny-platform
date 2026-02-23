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
import { ref, watch } from 'vue'
import { message } from 'ant-design-vue'

const props = defineProps<{
  mode: 'create' | 'edit'
  tenantData?: any
}>()

const emit = defineEmits(['submit', 'cancel'])

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
