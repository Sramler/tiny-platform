<template>
  <div class="resource-form">
    <a-form
      ref="formRef"
      :model="formData"
      :rules="rules"
      :label-col="{ span: 6 }"
      :wrapper-col="{ span: 18 }"
      layout="horizontal"
    >
      <!-- 基本信息 -->
      <a-divider>基本信息</a-divider>
      
      <a-form-item label="资源名称" name="name">
        <a-input 
          v-model:value="formData.name" 
          placeholder="请输入资源名称"
          maxlength="100"
          show-count
        />
      </a-form-item>
      
      <a-form-item label="资源标题" name="title">
        <a-input 
          v-model:value="formData.title" 
          placeholder="请输入资源标题"
          maxlength="100"
          show-count
        />
      </a-form-item>
      
      <a-form-item label="资源类型" name="type">
        <a-select v-model:value="formData.type" placeholder="请选择资源类型">
          <a-select-option :value="ResourceType.MENU">菜单</a-select-option>
          <a-select-option :value="ResourceType.BUTTON">按钮</a-select-option>
          <a-select-option :value="ResourceType.API">API</a-select-option>
        </a-select>
      </a-form-item>
      
      <a-form-item label="父级资源" name="parentId">
        <a-tree-select
          v-model:value="formData.parentId"
          :tree-data="resourceTreeData"
          placeholder="请选择父级资源"
          allow-clear
          tree-default-expand-all
          :field-names="{ children: 'children', label: 'title', value: 'id' }"
        />
      </a-form-item>
      
      <a-form-item label="排序权重" name="sort">
        <a-input-number 
          v-model:value="formData.sort" 
          :min="0" 
          :max="9999"
          placeholder="数字越小越靠前"
          style="width: 100%"
        />
      </a-form-item>
      
      <!-- API配置 -->
      <a-divider>API配置</a-divider>
      
      <a-form-item label="API路径" name="uri">
        <a-input 
          v-model:value="formData.uri" 
          placeholder="请输入后端API路径，如：/api/user"
          maxlength="200"
          show-count
        />
      </a-form-item>
      
      <a-form-item label="HTTP方法" name="method">
        <a-select v-model:value="formData.method" placeholder="请选择HTTP方法">
          <a-select-option value="GET">GET</a-select-option>
          <a-select-option value="POST">POST</a-select-option>
          <a-select-option value="PUT">PUT</a-select-option>
          <a-select-option value="DELETE">DELETE</a-select-option>
          <a-select-option value="PATCH">PATCH</a-select-option>
        </a-select>
      </a-form-item>
      
      <a-form-item label="权限标识" name="permission">
        <a-input 
          v-model:value="formData.permission" 
          placeholder="请输入权限标识，如：system:user:list"
          maxlength="100"
          show-count
        />
      </a-form-item>
      
      <!-- 路由配置 -->
      <a-divider>路由配置</a-divider>
      
      <a-form-item label="前端路径" name="path">
        <a-input 
          v-model:value="formData.path" 
          placeholder="请输入前端路由路径，如：/user"
          maxlength="200"
          show-count
        />
      </a-form-item>
      
      <a-form-item label="组件路径" name="component">
        <a-input 
          v-model:value="formData.component" 
          placeholder="请输入Vue组件路径，如：@/views/user/User.vue"
          maxlength="200"
          show-count
        />
      </a-form-item>
      
      <a-form-item label="重定向地址" name="redirect">
        <a-input 
          v-model:value="formData.redirect" 
          placeholder="父菜单重定向地址，如：/user/list"
          maxlength="200"
          show-count
        />
      </a-form-item>
      
      <!-- 显示配置 -->
      <a-divider>显示配置</a-divider>
      
      <a-form-item label="菜单图标" name="icon">
        <a-input 
          v-model:value="formData.icon" 
          placeholder="请输入图标名称，如：UserOutlined"
          maxlength="200"
          show-count
        />
      </a-form-item>
      
      <a-form-item label="显示图标" name="showIcon">
        <a-switch v-model:checked="formData.showIcon" />
      </a-form-item>
      
      <a-form-item label="侧边栏隐藏" name="hidden">
        <a-switch v-model:checked="formData.hidden" />
      </a-form-item>
      
      <a-form-item label="页面缓存" name="keepAlive">
        <a-switch v-model:checked="formData.keepAlive" />
      </a-form-item>
    </a-form>
    
    <!-- 表单操作按钮 -->
    <div class="form-actions">
      <a-button @click="handleCancel">取消</a-button>
      <a-button type="primary" @click="handleSubmit" :loading="submitting">
        {{ mode === 'create' ? '创建' : '更新' }}
      </a-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import type { FormInstance } from 'ant-design-vue'
// 引入资源API
import { getResourceTree, type ResourceItem, ResourceType } from '@/api/resource'

// 定义组件属性
interface Props {
  mode: 'create' | 'edit'
  resourceData?: ResourceItem | null
}

const props = withDefaults(defineProps<Props>(), {
  resourceData: null
})

// 定义事件
const emit = defineEmits<{
  submit: [data: any]
  cancel: []
}>()

// 表单引用
const formRef = ref<FormInstance>()

// 表单数据
const formData = reactive({
  id: '',
  name: '',
  title: '',
  path: '',
  uri: '',
  method: 'GET',
  icon: '',
  showIcon: true,
  sort: 0,
  component: '',
  redirect: '',
  hidden: false,
  keepAlive: false,
  permission: '',
  type: ResourceType.API,
  parentId: null as number | null
})

// 提交状态
const submitting = ref(false)

// 资源树数据
const resourceTreeData = ref<ResourceItem[]>([])

// 表单验证规则
const rules = {
  name: [
    { required: true, message: '请输入资源名称', trigger: 'blur' },
    { min: 2, max: 100, message: '长度在 2 到 100 个字符', trigger: 'blur' }
  ],
  title: [
    { required: true, message: '请输入资源标题', trigger: 'blur' },
    { min: 2, max: 100, message: '长度在 2 到 100 个字符', trigger: 'blur' }
  ],
  type: [
    { required: true, message: '请选择资源类型', trigger: 'change' }
  ],
  sort: [
    { required: true, message: '请输入排序权重', trigger: 'blur' },
    { type: 'number', min: 0, max: 9999, message: '排序权重必须在 0-9999 之间', trigger: 'blur' }
  ]
}

// 加载资源树数据
async function loadResourceTree() {
  try {
    const data = await getResourceTree()
    resourceTreeData.value = decorateCarrierLabels(data || [])
  } catch (error) {
    console.error('加载资源树失败:', error)
  }
}

function decorateCarrierLabels(nodes: ResourceItem[]): ResourceItem[] {
  return nodes.map((node) => ({
    ...node,
    title: `${node.title || node.name}${resolveCarrierSuffix(node)}`,
    children: Array.isArray(node.children) ? decorateCarrierLabels(node.children) : undefined,
  }))
}

function resolveCarrierSuffix(node: ResourceItem) {
  const carrierKind = node.carrierKind
    || (node.type === ResourceType.DIRECTORY || node.type === ResourceType.MENU
      ? 'menu'
      : node.type === ResourceType.BUTTON
        ? 'ui_action'
        : node.type === ResourceType.API
          ? 'api_endpoint'
          : undefined)
  return carrierKind ? ` [${carrierKind}]` : ''
}

// 初始化表单数据
function initFormData() {
  if (props.resourceData) {
    Object.assign(formData, props.resourceData)
  } else {
    // 重置表单数据
    Object.assign(formData, {
      id: '',
      name: '',
      title: '',
      path: '',
      uri: '',
      method: 'GET',
      icon: '',
      showIcon: true,
      sort: 0,
      component: '',
      redirect: '',
      hidden: false,
      keepAlive: false,
      permission: '',
      type: ResourceType.API,
      parentId: null
    })
  }
}

// 提交表单
async function handleSubmit() {
  try {
    await formRef.value?.validate()
    submitting.value = true
    
    const submitData = { ...formData }
    emit('submit', submitData)
  } catch (error) {
    console.error('表单验证失败:', error)
  } finally {
    submitting.value = false
  }
}

// 取消操作
function handleCancel() {
  emit('cancel')
}

// 监听资源数据变化
watch(() => props.resourceData, () => {
  initFormData()
}, { immediate: true })

// 组件挂载时加载资源树
onMounted(() => {
  loadResourceTree()
})
</script>

<style scoped>
.resource-form {
  padding: 0;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
}

:deep(.ant-form-item-label > label) {
  font-weight: 500;
}

:deep(.ant-divider) {
  margin: 24px 0 16px 0;
  font-weight: 500;
  color: #1890ff;
}
</style> 
