<template>
  <!-- 用 a-modal 包裹穿梭组件 -->
  <a-modal v-model:open="visible" :title="title" @ok="handleOk" @cancel="handleCancel" width="900px">
    <div style="min-height: 400px;">
      <!-- 使用 a-transfer 组件实现树形穿梭 -->
      <a-transfer v-model:target-keys="rightKeys" class="tree-transfer" :data-source="transferDataSource"
        :render="(item: any) => item.title" :show-select-all="false" :disabled="loading" :titles="['可选资源', '已分配资源']"
        style="height: 400px;">
        <template #children="{ direction }">
          <!-- 左侧树形结构 -->
          <a-tree v-if="direction === 'left'" block-node checkable default-expand-all :checked-keys="[]"
            :tree-data="leftTreeData" :field-names="{ title: 'title', key: 'key', children: 'children' }"
            @check="onTreeCheck" @select="onTreeSelect" />
          <!-- 右侧树形结构 -->
          <a-tree v-if="direction === 'right'" block-node checkable default-expand-all :checked-keys="rightKeys"
            :tree-data="rightTreeData" :field-names="{ title: 'title', key: 'key', children: 'children' }"
            @check="onTreeCheck" @select="onTreeSelect" />
        </template>
      </a-transfer>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
// 引入Vue API
import { ref, computed, watch, onMounted } from 'vue';
import { getResourceTree } from '@/api/resource' // 引入资源API
import { getRoleResources } from '@/api/role' // 引入角色API

// props 和 emits
const props = defineProps({
  open: Boolean,
  title: { type: String, default: '配置资源' },
  roleId: { type: Number, required: true } // 角色ID
})
const emit = defineEmits(['update:open', 'submit'])

// 控制弹窗显示
const visible = ref(props.open)
watch(() => props.open, (v: boolean) => (visible.value = v))
watch(visible, (v: boolean) => emit('update:open', v))

// 加载状态
const loading = ref(false)

type ResourceTreeNode = {
  id?: number | string
  title?: string
  name?: string
  children?: ResourceTreeNode[]
}

type TransferTreeNode = {
  key: number
  title: string
  children?: TransferTreeNode[]
}

type TransferItem = {
  key: number
  title: string
  disabled: boolean
}

// 原始树形数据（从后端加载）
const originalTreeData = ref<TransferTreeNode[]>([]);

// 已分配资源key
const rightKeys = ref<number[]>([]);

// 扁平化的transfer数据源
const transferDataSource = ref<TransferItem[]>([]);

type TreeNodeInfo = {
  key: number
  parent?: number
  children: number[]
}

type TreeEventInfo = {
  node: { eventKey?: string | number }
  checked?: boolean
}

const treeNodeMap = new Map<number, TreeNodeInfo>()

const onTreeCheck = (_: unknown, info: unknown) => handleTreeCheck(info as TreeEventInfo)
const onTreeSelect = (_: unknown, info: unknown) => handleTreeSelect(info as TreeEventInfo)

// 加载资源树数据
async function loadResourceTree() {
  loading.value = true
  try {
    const tree = await getResourceTree()
    // 转换数据格式，确保key为number类型
    originalTreeData.value = transformTreeData(tree)
    // 扁平化数据用于transfer组件
    flattenTreeData(originalTreeData.value)
    treeNodeMap.clear()
    buildTreeMap(originalTreeData.value)
  } catch (error) {
    console.error('加载资源树失败:', error)
  } finally {
    loading.value = false
  }
}

// 加载角色已分配资源
async function loadRoleResources(targetRoleId?: number) {
  const roleId = Number(targetRoleId ?? props.roleId)
  if (!roleId) {
    rightKeys.value = []
    return
  }
  try {
    const resourceIds = await getRoleResources(roleId)
    // 确保 key 类型一致
    rightKeys.value = (resourceIds || []).map((id: number | string) => Number(id))
  } catch (error) {
    console.error('加载角色资源失败:', error)
    rightKeys.value = []
  }
}

// 转换树数据格式
function transformTreeData(nodes: ResourceTreeNode[]): TransferTreeNode[] {
  return nodes.map(node => ({
    key: Number(node.id), // 确保key为number类型
    title: node.title || node.name || '', // 优先使用title，备选使用name
    children: node.children ? transformTreeData(node.children) : undefined
  }))
}

// 扁平化树形数据为transfer组件需要的格式
function flattenTreeData(list: TransferTreeNode[] = []) {
  transferDataSource.value = [];
  list.forEach(item => {
    transferDataSource.value.push({
      key: item.key,
      title: item.title,
      disabled: false
    });
    if (item.children) {
      flattenTreeData(item.children);
    }
  });
}

function buildTreeMap(nodes: TransferTreeNode[] = [], parent?: number) {
  nodes.forEach(node => {
    const key = Number(node.key)
    const children = node.children ? node.children.map((child) => Number(child.key)) : []
    treeNodeMap.set(key, { key, parent, children })
    if (node.children) {
      buildTreeMap(node.children, key)
    }
  })
}

function getDescendantKeys(key: number): number[] {
  const node = treeNodeMap.get(key)
  if (!node) return []
  const result: number[] = []
  node.children.forEach(childKey => {
    result.push(childKey)
    result.push(...getDescendantKeys(childKey))
  })
  return result
}

function addAncestors(key: number, keySet: Set<number>) {
  const parentKey = treeNodeMap.get(key)?.parent
  if (parentKey === undefined) return
  if (!keySet.has(parentKey)) {
    keySet.add(parentKey)
  }
  addAncestors(parentKey, keySet)
}

function removeAncestorsIfNoChildren(key: number, keySet: Set<number>) {
  const parentKey = treeNodeMap.get(key)?.parent
  if (parentKey === undefined) return
  const parentNode = treeNodeMap.get(parentKey)
  if (!parentNode) return
  const hasSelectedChild = parentNode.children.some(childKey => keySet.has(childKey))
  if (!hasSelectedChild && keySet.has(parentKey)) {
    keySet.delete(parentKey)
    removeAncestorsIfNoChildren(parentKey, keySet)
  }
}

function addNodeCascade(key: number, keySet: Set<number>) {
  if (keySet.has(key)) return
  keySet.add(key)
  const children = treeNodeMap.get(key)?.children ?? []
  children.forEach(childKey => addNodeCascade(childKey, keySet))
}

function removeNodeCascade(key: number, keySet: Set<number>) {
  if (keySet.has(key)) {
    keySet.delete(key)
  }
  const children = treeNodeMap.get(key)?.children ?? []
  children.forEach(childKey => removeNodeCascade(childKey, keySet))
}

function toggleCascadeSelection(key: number, checked: boolean) {
  const nextKeys = new Set(rightKeys.value)
  if (checked) {
    addNodeCascade(key, nextKeys)
    addAncestors(key, nextKeys)
  } else {
    removeNodeCascade(key, nextKeys)
    removeAncestorsIfNoChildren(key, nextKeys)
  }
  rightKeys.value = Array.from(nextKeys)
}

// 处理树形数据，为已分配的节点设置disabled状态
function handleTreeData(treeNodes: any[], targetKeys: (string | number)[] = []): any[] {
  return treeNodes.map(({ children, ...props }) => ({
    ...props,
    disabled: targetKeys.includes(props.key as string | number),
    children: handleTreeData(children ?? [], targetKeys),
  }));
}

// 递归过滤树，仅保留key在rightKeys中的节点（已分配的资源）
function filterAssignedTree(nodes: TransferTreeNode[], assignedKeys: (string | number)[]): TransferTreeNode[] {
  const result: TransferTreeNode[] = []
  for (const node of nodes) {
    if (assignedKeys.includes(node.key)) {
      const children = node.children ? filterAssignedTree(node.children, assignedKeys) : []
      result.push({ ...node, children: children.length > 0 ? children : undefined })
      continue
    }

    if (node.children) {
      const children = filterAssignedTree(node.children, assignedKeys)
      if (children.length > 0) {
        result.push({ ...node, children })
      }
    }
  }
  return result
}

// 递归过滤树，仅保留key不在rightKeys中的节点（未分配的资源）
function filterUnassignedTree(nodes: TransferTreeNode[], assignedKeys: (string | number)[]): TransferTreeNode[] {
  const result: TransferTreeNode[] = []
  for (const node of nodes) {
    if (!assignedKeys.includes(node.key)) {
      const children = node.children ? filterUnassignedTree(node.children, assignedKeys) : []
      result.push({ ...node, children: children.length > 0 ? children : undefined })
      continue
    }

    if (node.children) {
      const children = filterUnassignedTree(node.children, assignedKeys)
      if (children.length > 0) {
        result.push({ ...node, children })
      }
    }
  }
  return result
}

// 左侧树数据 (只显示未分配的资源)
const leftTreeData = computed(() => filterUnassignedTree(originalTreeData.value, rightKeys.value));

// 右侧树数据 (只显示已分配的资源)
const rightTreeData = computed(() => filterAssignedTree(originalTreeData.value, rightKeys.value));

// 处理树节点选中事件
const handleTreeCheck = (info: TreeEventInfo) => {
  const { eventKey } = info.node
  if (eventKey === undefined) return
  const checked = !!info.checked
  toggleCascadeSelection(Number(eventKey), checked)
}

// 处理树节点选择事件（与勾选行为保持一致）
const handleTreeSelect = (info: TreeEventInfo) => {
  const { eventKey } = info.node
  if (eventKey === undefined) return
  const isSelected = rightKeys.value.includes(Number(eventKey))
  toggleCascadeSelection(Number(eventKey), !isSelected)
}

// 点击确定
function handleOk() {
  emit('submit', rightKeys.value) // rightKeys.value 是已分配资源key数组
  visible.value = false
}

// 点击取消
function handleCancel() {
  visible.value = false
}

// 监听弹窗打开，加载数据
watch(
  () => props.open,
  async (newVal) => {
    if (newVal) {
      await loadResourceTree()
      await loadRoleResources()
    } else {
      // 关闭弹窗时清理状态，避免下次打开残留上一次的数据
      rightKeys.value = []
    }
  },
)

// 监听角色 ID 变化，重新加载角色已分配资源
watch(
  () => props.roleId,
  async (newRoleId) => {
    if (visible.value) {
      await loadRoleResources(Number(newRoleId))
    }
  },
)

// 组件挂载时也加载一次数据（当组件默认就是打开状态）
onMounted(async () => {
  if (props.open) {
    await loadResourceTree()
    await loadRoleResources()
  }
})
</script>

<style scoped>
/* 自定义transfer组件样式 */
.tree-transfer .ant-transfer-list:first-child {
  width: 50%;
  flex: none;
}

.tree-transfer .ant-transfer-list {
  height: 400px;
}

.tree-transfer .ant-transfer-list-body {
  height: 350px;
}

/* 树组件样式优化 */
:deep(.ant-tree) {
  background: transparent;
}

:deep(.ant-tree-node-content-wrapper) {
  padding: 4px 8px;
  border-radius: 4px;
}

:deep(.ant-tree-node-content-wrapper:hover) {
  background-color: #f5f5f5;
}

:deep(.ant-tree-checkbox) {
  margin-right: 8px;
}

/* 禁用状态的节点样式 */
:deep(.ant-tree-treenode-disabled .ant-tree-node-content-wrapper) {
  color: #bfbfbf;
  cursor: not-allowed;
}

:deep(.ant-tree-treenode-disabled .ant-tree-checkbox) {
  cursor: not-allowed;
}
</style>
