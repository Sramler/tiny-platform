<template>
  <a-form
    ref="formRef"
    :model="formData"
    :rules="rules"
    :label-col="{ span: 6 }"
    :wrapper-col="{ span: 18 }"
  >
    <a-form-item label="字典编码" name="dictCode">
      <a-input
        v-model:value="formData.dictCode"
        placeholder="请输入字典编码，如：GENDER, ORDER_STATUS"
        :disabled="!!formData.id"
        maxlength="64"
        show-count
      />
    </a-form-item>

    <a-form-item label="字典名称" name="dictName">
      <a-input
        v-model:value="formData.dictName"
        placeholder="请输入字典名称，如：性别, 订单状态"
        maxlength="128"
        show-count
      />
    </a-form-item>

    <a-form-item label="字典描述" name="description">
      <a-textarea
        v-model:value="formData.description"
        placeholder="请输入字典描述"
        :rows="3"
        maxlength="255"
        show-count
      />
    </a-form-item>

    <a-form-item label="租户ID" name="tenantId">
      <a-input-number
        v-model:value="formData.tenantId"
        :min="0"
        placeholder="0表示平台字典，>0表示租户自定义字典"
        style="width: 100%"
      />
    </a-form-item>

    <a-form-item label="分类ID" name="categoryId">
      <a-input-number
        v-model:value="formData.categoryId"
        :min="0"
        placeholder="用于字典分组（可选）"
        style="width: 100%"
      />
    </a-form-item>

    <a-form-item label="排序顺序" name="sortOrder">
      <a-input-number
        v-model:value="formData.sortOrder"
        :min="0"
        :max="9999"
        placeholder="数字越小越靠前"
        style="width: 100%"
      />
    </a-form-item>

    <a-form-item label="是否启用" name="enabled">
      <a-switch v-model:checked="formData.enabled" />
    </a-form-item>
  </a-form>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import type { FormInstance } from 'ant-design-vue'
import type { DictTypeItem, DictTypeCreateUpdateDto } from '@/api/dict'

const props = defineProps<{
  formData?: DictTypeItem | null
}>()

const formRef = ref<FormInstance>()

const formData = reactive<DictTypeCreateUpdateDto>({
  dictCode: '',
  dictName: '',
  description: '',
  tenantId: 0,
  categoryId: undefined,
  sortOrder: 0,
  enabled: true,
})

const rules = {
  dictCode: [
    { required: true, message: '请输入字典编码', trigger: 'blur' },
    { pattern: /^[A-Z_][A-Z0-9_]*$/, message: '字典编码只能包含大写字母、数字和下划线，且必须以字母或下划线开头', trigger: 'blur' },
  ],
  dictName: [
    { required: true, message: '请输入字典名称', trigger: 'blur' },
  ],
  sortOrder: [
    { type: 'number', min: 0, max: 9999, message: '排序顺序必须在0-9999之间', trigger: 'blur' },
  ],
}

// 监听 props 变化，更新表单数据
watch(
  () => props.formData,
  (newVal) => {
    if (newVal) {
      Object.assign(formData, {
        id: newVal.id,
        dictCode: newVal.dictCode || '',
        dictName: newVal.dictName || '',
        description: newVal.description || '',
        tenantId: newVal.tenantId ?? 0,
        categoryId: newVal.categoryId,
        sortOrder: newVal.sortOrder ?? 0,
        enabled: newVal.enabled ?? true,
      })
    } else {
      Object.assign(formData, {
        id: undefined,
        dictCode: '',
        dictName: '',
        description: '',
        tenantId: 0,
        categoryId: undefined,
        sortOrder: 0,
        enabled: true,
      })
    }
  },
  { immediate: true, deep: true }
)

// 验证表单
async function validate() {
  await formRef.value?.validate()
}

// 获取表单数据
function getFormData(): DictTypeCreateUpdateDto {
  return { ...formData }
}

defineExpose({
  validate,
  getFormData,
})
</script>

