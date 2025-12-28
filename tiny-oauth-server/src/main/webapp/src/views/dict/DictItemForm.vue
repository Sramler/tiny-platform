<template>
  <a-form
    ref="formRef"
    :model="formData"
    :rules="rules"
    :label-col="{ span: 6 }"
    :wrapper-col="{ span: 18 }"
  >
    <a-form-item label="字典类型" name="dictTypeId">
      <a-select
        v-model:value="formData.dictTypeId"
        placeholder="请选择字典类型"
        :disabled="!!formData.id || !!dictTypeId"
        style="width: 100%"
      >
        <a-select-option v-for="type in dictTypeOptions" :key="type.id" :value="type.id">
          {{ type.dictName }} ({{ type.dictCode }})
        </a-select-option>
      </a-select>
    </a-form-item>

    <a-form-item label="字典值" name="value">
      <a-input
        v-model:value="formData.value"
        placeholder="请输入字典值，如：MALE, FEMALE, PENDING, PAID"
        :disabled="!!formData.id"
        maxlength="64"
        show-count
      />
    </a-form-item>

    <a-form-item label="字典标签" name="label">
      <a-input
        v-model:value="formData.label"
        placeholder="请输入字典标签，如：男, 女, 待支付, 已支付"
        maxlength="128"
        show-count
      />
    </a-form-item>

    <a-form-item label="字典项描述" name="description">
      <a-textarea
        v-model:value="formData.description"
        placeholder="请输入字典项描述"
        :rows="3"
        maxlength="255"
        show-count
      />
    </a-form-item>

    <a-form-item label="租户ID" name="tenantId">
      <a-input-number
        v-model:value="formData.tenantId"
        :min="0"
        placeholder="0表示平台字典项，>0表示租户自定义字典项"
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
import { ref, reactive, watch, onMounted } from 'vue'
import type { FormInstance } from 'ant-design-vue'
import type { DictItem, DictItemCreateUpdateDto, DictTypeItem } from '@/api/dict'
import { getDictTypesByTenant } from '@/api/dict'

const props = defineProps<{
  formData?: DictItem | null
  dictTypeId?: number
}>()

const formRef = ref<FormInstance>()

const formData = reactive<DictItemCreateUpdateDto>({
  dictTypeId: 0,
  value: '',
  label: '',
  description: '',
  tenantId: 0,
  sortOrder: 0,
  enabled: true,
})

const dictTypeOptions = ref<DictTypeItem[]>([])

const rules = {
  dictTypeId: [
    { required: true, message: '请选择字典类型', trigger: 'change' },
  ],
  value: [
    { required: true, message: '请输入字典值', trigger: 'blur' },
  ],
  label: [
    { required: true, message: '请输入字典标签', trigger: 'blur' },
  ],
  sortOrder: [
    { type: 'number', min: 0, max: 9999, message: '排序顺序必须在0-9999之间', trigger: 'blur' },
  ],
}

// 加载字典类型选项
async function loadDictTypeOptions() {
  try {
    const result = await getDictTypesByTenant(0)
    dictTypeOptions.value = result
  } catch (error) {
    console.error('加载字典类型选项失败:', error)
  }
}

// 监听 props 变化，更新表单数据
watch(
  () => props.formData,
  (newVal) => {
    if (newVal) {
      Object.assign(formData, {
        id: newVal.id,
        dictTypeId: newVal.dictTypeId || props.dictTypeId || 0,
        value: newVal.value || '',
        label: newVal.label || '',
        description: newVal.description || '',
        tenantId: newVal.tenantId ?? 0,
        sortOrder: newVal.sortOrder ?? 0,
        enabled: newVal.enabled ?? true,
      })
    } else {
      Object.assign(formData, {
        id: undefined,
        dictTypeId: props.dictTypeId || 0,
        value: '',
        label: '',
        description: '',
        tenantId: 0,
        sortOrder: 0,
        enabled: true,
      })
    }
  },
  { immediate: true, deep: true }
)

// 监听 dictTypeId 变化
watch(
  () => props.dictTypeId,
  (newVal) => {
    if (newVal && !formData.id) {
      formData.dictTypeId = newVal
    }
  },
  { immediate: true }
)

// 验证表单
async function validate() {
  await formRef.value?.validate()
}

// 获取表单数据
function getFormData(): DictItemCreateUpdateDto {
  return { ...formData }
}

onMounted(() => {
  loadDictTypeOptions()
})

defineExpose({
  validate,
  getFormData,
})
</script>

