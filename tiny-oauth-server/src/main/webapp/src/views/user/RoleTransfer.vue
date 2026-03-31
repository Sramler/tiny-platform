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
      :render="(item: any) => `${item.name}（${item.code}）`"
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
  scopeId: { type: Number, default: null },
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
const localScopeId = ref<number | null>(props.scopeId ?? null)
watch(() => props.scopeType, v => localScopeType.value = v)
watch(() => props.scopeId, v => localScopeId.value = v ?? null)

const scopeOptions = computed(() => (
  localScopeType.value === 'ORG' ? props.orgOptions : props.deptOptions
))

function handleScopeTypeChange(value: 'TENANT' | 'ORG' | 'DEPT') {
  localScopeType.value = value
  emit('update:scopeType', value)
}

function handleScopeIdChange(value: number) {
  localScopeId.value = value
  emit('update:scopeId', value)
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
