<template>
  <!-- 弹窗包裹 transfer，和 RoleTransfer.vue 结构一致 -->
  <a-modal v-model:open="visible" :title="title" @ok="handleOk" @cancel="handleCancel">
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
      v-model:target-keys="localUserIds"
      :data-source="allUsers"
      :titles="transferTitles"
      :render="renderUserItem"
      :row-key="rowKeyOfUser"
      show-search
      :filter-option="filterUser"
      :list-style="{ width: '220px', height: '320px' }"
    />
  </a-modal>
</template>
<script setup lang="ts">
import { ref, watch, computed } from 'vue'
// 定义props
const props = defineProps({
  modelValue: { type: Array as () => string[], default: () => [] },
  allUsers: { type: Array as () => { key: string, title: string }[], default: () => [] },
  open: Boolean,
  title: { type: String, default: '配置用户' },
  scopeType: { type: String as () => 'TENANT' | 'ORG' | 'DEPT', default: 'TENANT' },
  scopeId: { type: Number, default: undefined },
  orgOptions: { type: Array as () => { value: number, label: string }[], default: () => [] },
  deptOptions: { type: Array as () => { value: number, label: string }[], default: () => [] },
})
// 定义emit
const emit = defineEmits(['update:modelValue', 'update:open', 'update:scopeType', 'update:scopeId'])
// 控制弹窗显示
const visible = ref(props.open)
watch(() => props.open, v => visible.value = v)
watch(visible, v => emit('update:open', v))
// 本地用户ID数组
const localUserIds = ref<string[]>([...(props.modelValue || [])])
watch(() => props.modelValue, v => localUserIds.value = [...(v || [])])
const localScopeType = ref<'TENANT' | 'ORG' | 'DEPT'>(props.scopeType)
const localScopeId = ref<number | undefined>(props.scopeId ?? undefined)
watch(() => props.scopeType, v => localScopeType.value = v)
watch(() => props.scopeId, v => localScopeId.value = v ?? undefined)
const scopeOptions = computed(() => (
  localScopeType.value === 'ORG' ? props.orgOptions : props.deptOptions
))
// 动态标题：xxx个用户未配置角色/已配置角色
const transferTitles = computed(() => [
  `用户未配置角色`,
  `用户已配置角色`
])
// 未分配用户数量
const allUsersUnselected = computed(() => props.allUsers.length - localUserIds.value.length)
// 搜索过滤
function renderUserItem(item: Record<string, unknown>) {
  return String(item.title ?? '')
}
function rowKeyOfUser(item: Record<string, unknown>) {
  return String(item.key ?? '')
}
function filterUser(input: string, item: Record<string, unknown>) {
  return String(item.title ?? '').toLowerCase().includes(input.toLowerCase())
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
// 确认分配
function handleOk() {
  emit('update:modelValue', localUserIds.value)
  visible.value = false
}
// 取消分配
function handleCancel() {
  visible.value = false
}
</script>
<style scoped>
.scope-form {
  margin-bottom: 12px;
}
</style>
