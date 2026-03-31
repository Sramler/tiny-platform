<template>
  <a-modal v-model:open="visible" title="分配角色" @ok="handleOk" @cancel="handleCancel">
    <a-form layout="vertical" class="scope-form">
      <a-form-item label="赋权作用域">
        <a-select v-model:value="localScopeType" @change="handleScopeTypeChange">
          <a-select-option value="TENANT">租户级</a-select-option>
          <a-select-option value="ORG">组织级</a-select-option>
          <a-select-option value="DEPT">部门级</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item v-if="localScopeType !== 'TENANT'" label="作用域节点">
        <a-select
          v-model:value="localScopeId"
          :options="scopeOptions"
          placeholder="请选择作用域节点"
          @change="handleScopeIdChange"
        />
      </a-form-item>
    </a-form>
    <a-transfer
      v-model:target-keys="localRoleIds"
      :data-source="allRoles"
      :titles="['可选角色', '已分配角色']"
      :render="renderRoleItem"
      :list-style="{ width: '200px', height: '300px' }"
    />
  </a-modal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
const props = defineProps({
  modelValue: { type: Array as () => string[], default: () => [] },
  allRoles: { type: Array as () => { key: string, name: string, code: string }[], default: () => [] },
  open: Boolean,
  scopeType: { type: String as () => 'TENANT' | 'ORG' | 'DEPT', default: 'TENANT' },
  scopeId: { type: Number, default: undefined },
  orgOptions: { type: Array as () => { value: number, label: string }[], default: () => [] },
  deptOptions: { type: Array as () => { value: number, label: string }[], default: () => [] },
})
const emit = defineEmits(['update:modelValue', 'update:open', 'update:scopeType', 'update:scopeId'])

const visible = ref(props.open)
watch(() => props.open, v => visible.value = v)
watch(visible, v => emit('update:open', v))

const localRoleIds = ref<string[]>([...(props.modelValue || [])])
watch(() => props.modelValue, v => localRoleIds.value = [...(v || [])])

const localScopeType = ref<'TENANT' | 'ORG' | 'DEPT'>(props.scopeType)
const localScopeId = ref<number | undefined>(props.scopeId ?? undefined)
watch(() => props.scopeType, v => localScopeType.value = v)
watch(() => props.scopeId, v => localScopeId.value = v ?? undefined)

const scopeOptions = computed(() => (
  localScopeType.value === 'ORG' ? props.orgOptions : props.deptOptions
))

function renderRoleItem(item: Record<string, unknown>) {
  return `${String(item.name ?? '')}（${String(item.code ?? '')}）`
}

function handleScopeTypeChange(value: unknown) {
  const normalized = value === 'ORG' || value === 'DEPT' ? value : 'TENANT'
  localScopeType.value = normalized
  emit('update:scopeType', normalized)
}

function handleScopeIdChange(value: unknown) {
  if (value === undefined || value === null || value === '') {
    localScopeId.value = undefined
    emit('update:scopeId', undefined)
    return
  }
  const normalized = Number(value)
  if (Number.isNaN(normalized)) return
  localScopeId.value = normalized
  emit('update:scopeId', normalized)
}

function handleOk() {
  emit('update:modelValue', localRoleIds.value)
  visible.value = false
}
function handleCancel() {
  visible.value = false
}
</script>

<style scoped>
.scope-form {
  margin-bottom: 12px;
}
</style>
