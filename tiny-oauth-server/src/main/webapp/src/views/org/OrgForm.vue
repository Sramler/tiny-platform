<template>
  <a-modal
    :open="open"
    :title="mode === 'create' ? '新建组织' : '编辑组织'"
    :confirm-loading="submitting"
    @ok="onSubmit"
    @cancel="onCancel"
    :destroy-on-close="true"
    width="560px"
  >
    <a-form
      :model="form"
      :rules="rules"
      ref="formRef"
      layout="horizontal"
      :label-col="{ span: 6 }"
      :wrapper-col="{ span: 16 }"
      style="margin-top: 16px;"
    >
      <a-form-item label="上级组织" name="parentId">
        <a-tree-select
          v-model:value="form.parentId"
          :tree-data="treeData"
          :field-names="{ label: 'name', value: 'id', children: 'children' }"
          placeholder="请选择上级组织（留空为顶级）"
          allow-clear
          tree-default-expand-all
        />
      </a-form-item>
      <a-form-item label="组织编码" name="code">
        <a-input v-model:value="form.code" placeholder="请输入组织编码" :disabled="mode === 'edit'" />
      </a-form-item>
      <a-form-item label="组织名称" name="name">
        <a-input v-model:value="form.name" placeholder="请输入组织名称" />
      </a-form-item>
      <a-form-item label="组织类型" name="unitType">
        <a-select v-model:value="form.unitType" placeholder="请选择组织类型">
          <a-select-option value="COMPANY">公司</a-select-option>
          <a-select-option value="DEPARTMENT">部门</a-select-option>
          <a-select-option value="GROUP">小组</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="排序" name="sortOrder">
        <a-input-number v-model:value="form.sortOrder" :min="0" placeholder="排序号" style="width: 100%;" />
      </a-form-item>
      <a-form-item v-if="mode === 'edit'" label="状态" name="status">
        <a-select v-model:value="form.status" placeholder="请选择状态">
          <a-select-option value="ACTIVE">启用</a-select-option>
          <a-select-option value="INACTIVE">禁用</a-select-option>
        </a-select>
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'
import type { OrgUnit } from '@/api/org'

interface Props {
  open: boolean
  mode: 'create' | 'edit'
  orgData?: Partial<OrgUnit> | null
  treeData: OrgUnit[]
}

const props = withDefaults(defineProps<Props>(), {
  orgData: null,
})

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'success'): void
}>()

const formRef = ref()
const submitting = ref(false)

const form = ref({
  parentId: undefined as number | undefined,
  code: '',
  name: '',
  unitType: 'DEPARTMENT',
  sortOrder: 0 as number | undefined,
  status: 'ACTIVE',
})

const rules: Record<string, Rule[]> = {
  code: [
    { required: true, message: '组织编码不能为空', trigger: 'blur' },
    { min: 2, max: 50, message: '长度 2-50 字符', trigger: 'blur' },
  ],
  name: [
    { required: true, message: '组织名称不能为空', trigger: 'blur' },
    { min: 1, max: 100, message: '长度 1-100 字符', trigger: 'blur' },
  ],
  unitType: [
    { required: true, message: '请选择组织类型', trigger: 'change' },
  ],
}

watch(() => props.orgData, (val) => {
  if (val) {
    form.value = {
      parentId: val.parentId ?? undefined,
      code: val.code ?? '',
      name: val.name ?? '',
      unitType: val.unitType ?? 'DEPARTMENT',
      sortOrder: val.sortOrder ?? 0,
      status: val.status ?? 'ACTIVE',
    }
  } else {
    form.value = {
      parentId: undefined,
      code: '',
      name: '',
      unitType: 'DEPARTMENT',
      sortOrder: 0,
      status: 'ACTIVE',
    }
  }
}, { immediate: true })

function onCancel() {
  emit('update:open', false)
}

async function onSubmit() {
  if (!formRef.value) {
    return
  }
  try {
    await formRef.value.validate()
  } catch {
    message.warning('请检查表单，有必填项未填写或格式不正确')
    return
  }

  submitting.value = true
  try {
    const { createOrg, updateOrg } = await import('@/api/org')
    if (props.mode === 'edit' && props.orgData?.id) {
      await updateOrg(props.orgData.id, {
        name: form.value.name,
        parentId: form.value.parentId,
        sortOrder: form.value.sortOrder,
        status: form.value.status,
      })
      message.success('更新成功')
    } else {
      await createOrg({
        parentId: form.value.parentId,
        code: form.value.code,
        name: form.value.name,
        unitType: form.value.unitType,
        sortOrder: form.value.sortOrder,
      })
      message.success('创建成功')
    }
    emit('update:open', false)
    emit('success')
  } catch (error: any) {
    const errorMessage = error?.errorInfo?.message || error?.message || '未知错误'
    message.error('保存失败: ' + errorMessage)
  } finally {
    submitting.value = false
  }
}
</script>
