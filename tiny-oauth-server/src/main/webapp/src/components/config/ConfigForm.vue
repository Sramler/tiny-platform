<template>
  <a-form ref="formRef" :model="model" :rules="rules" layout="vertical">
    <a-form-item
      v-for="f in fields"
      :key="String(f.field)"
      :name="String(f.field)"
      :label="f.label"
      :required="!!f.drawer?.required"
    >
      <!-- input -->
      <a-input
        v-if="f.drawer?.type === 'input'"
        v-model:value="model[f.field]"
        :placeholder="f.drawer?.placeholder"
        :disabled="isReadonly(f)"
        v-bind="componentPropsForField(f, mode)"
      />

      <!-- date -->
      <a-date-picker
        v-else-if="f.drawer?.type === 'date'"
        v-model:value="model[f.field]"
        style="width: 100%"
        :disabled="isReadonly(f)"
        v-bind="componentPropsForField(f, mode)"
      />

      <!-- number -->
      <a-input-number
        v-else-if="f.drawer?.type === 'number'"
        v-model:value="model[f.field]"
        style="width: 100%"
        :disabled="isReadonly(f)"
        v-bind="componentPropsForField(f, mode)"
      />

      <!-- switch -->
      <a-switch
        v-else-if="f.drawer?.type === 'switch'"
        v-model:checked="model[f.field]"
        :disabled="isReadonly(f)"
        v-bind="componentPropsForField(f, mode)"
      />

      <!-- select -->
      <a-select
        v-else-if="f.drawer?.type === 'select'"
        v-model:value="model[f.field]"
        :options="f.drawer?.options"
        style="width: 100%"
        :disabled="isReadonly(f)"
        v-bind="componentPropsForField(f, mode)"
      />

      <!-- fallback -->
      <span v-else style="color: #999"> 未支持的字段类型：{{ f.drawer?.type }} </span>
    </a-form-item>
  </a-form>
</template>

<script setup lang="ts">
import { ref } from 'vue'

/**
 * ConfigForm
 * ----------
 * 一个完全 schema 驱动的表单渲染组件
 * - 不包含任何业务逻辑
 * - 只负责把 drawer field 定义渲染成表单
 */

const props = defineProps<{
  fields: any[]
  model: Record<string, any>
  mode: 'create' | 'edit' | 'view'
  rules?: Record<string, any[]>
  readonlyResolver?: (f: any) => boolean
  componentPropsForField: (f: any, mode: any) => Record<string, any>
}>()

const formRef = ref()

/**
 * 判断字段是否只读
 * - view 模式默认全只读
 * - 支持字段级 readonly 配置
 */
function isReadonly(f: any) {
  if (props.mode === 'view') return true
  return props.readonlyResolver ? props.readonlyResolver(f) : false
}

/**
 * 暴露 validate 方法给父组件（Drawer footer 用）
 */
defineExpose({
  validate: () => formRef.value?.validate(),
})
</script>
