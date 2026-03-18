<template>
  <div class="menu-form">
    <a-form ref="formRef" :model="formData" :rules="rules" :label-col="{ span: 6 }" :wrapper-col="{ span: 18 }"
      layout="horizontal">
      <!-- 基本信息 -->
      <a-divider>基本信息</a-divider>

      <a-form-item label="菜单名称" name="name">
        <a-input v-model:value="formData.name" placeholder="请输入菜单名称" maxlength="100" show-count />
      </a-form-item>

      <a-form-item label="菜单标题" name="title">
        <a-input v-model:value="formData.title" placeholder="请输入菜单显示标题" maxlength="100" show-count />
      </a-form-item>

      <a-form-item label="父级菜单" name="parentId">
        <a-tree-select v-model:value="formData.parentId" :tree-data="menuTreeData" placeholder="请选择父级菜单" allow-clear
          tree-default-expand-all :field-names="{ children: 'children', label: 'title', value: 'id' }" />
      </a-form-item>

      <a-form-item label="排序权重" name="sort">
        <a-input-number v-model:value="formData.sort" :min="0" :max="9999" placeholder="数字越小越靠前" style="width: 100%" />
      </a-form-item>

      <!-- 路由配置 -->
      <a-divider>路由配置</a-divider>

      <a-form-item label="前端路径" name="url">
        <a-input v-model:value="formData.url" placeholder="请输入前端路由路径，如：/user" maxlength="200" show-count />
      </a-form-item>

      <a-form-item label="组件路径" name="component">
        <a-input v-model:value="formData.component" placeholder="请输入Vue组件路径，如：@/views/user/User.vue" maxlength="200"
          show-count />
      </a-form-item>

      <a-form-item label="重定向地址" name="redirect">
        <a-input v-model:value="formData.redirect" placeholder="父菜单重定向地址，如：/user/list" maxlength="200" show-count />
      </a-form-item>

      <a-form-item label="权限标识" name="permission">
        <a-input v-model:value="formData.permission" placeholder="请输入权限标识，如：system:user:list" maxlength="100" show-count />
      </a-form-item>

      <!-- 显示配置 -->
      <a-divider>显示配置</a-divider>

      <a-form-item label="是否启用" name="enabled">
        <a-switch v-model:checked="formData.enabled" />
      </a-form-item>

      <a-form-item label="菜单图标" name="icon">
        <div class="icon-selector-row">
          <a-input v-model:value="formData.icon" placeholder="请输入图标名称" maxlength="200" style="flex: 1;">
            <template #suffix>
              <Icon v-if="formData.icon" :icon="formData.icon" className="input-suffix-icon" />
            </template>
          </a-input>
          <a-button @click="showIconSelector = true" type="primary" style="margin-left: 8px;">
            选择图标
          </a-button>
        </div>
      </a-form-item>
      <a-modal v-model:open="showIconSelector" title="选择图标" width="800px" :footer="null">
        <IconSelect v-model="formData.icon" />
      </a-modal>

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
import { ref, reactive, computed, onMounted, watch, onBeforeUnmount, nextTick } from 'vue'
import { message } from 'ant-design-vue'
import type { FormInstance } from 'ant-design-vue'
// 引入菜单API
import { menuTreeAll, type MenuItem } from '@/api/menu'
import IconSelect from '../../components/IconSelect.vue' // 新增
import * as allIcons from '@ant-design/icons-vue' // 新增
import Icon from '@/components/Icon.vue' // 通用图标回显组件

// 定义组件属性
interface Props {
  mode: 'create' | 'edit'
  menuData?: MenuItem | null
  parentMenu?: MenuItem | null
}

const props = withDefaults(defineProps<Props>(), {
  menuData: null,
  parentMenu: null
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
  url: '',
  icon: '',
  showIcon: true,
  sort: 0,
  component: '',
  redirect: '',
  hidden: false,
  keepAlive: false,
  permission: '',
  parentId: null as number | null,
  enabled: true
})

// 表单验证规则
const rules = {
  name: [
    { required: true, message: '请输入菜单名称', trigger: 'blur' },
    { min: 2, max: 100, message: '长度在 2 到 100 个字符', trigger: 'blur' }
  ],
  title: [
    { required: true, message: '请输入菜单标题', trigger: 'blur' },
    { min: 2, max: 100, message: '长度在 2 到 100 个字符', trigger: 'blur' }
  ],
  sort: [
    { required: true, message: '请输入排序权重', trigger: 'blur' },
    { type: 'number', min: 0, max: 9999, message: '排序权重必须在 0-9999 之间', trigger: 'blur' }
  ]
}

// 菜单树数据
const menuTreeData = ref<MenuItem[]>([])

// 图标选择器
const showIconSelector = ref(false)

// 提交状态
const submitting = ref(false)
// 获取图标组件（支持所有官方风格，找不到时兜底）
function getIconComponent(iconName: string) {
  return (allIcons as any)[iconName] || allIcons.MenuOutlined
}

// 选择图标
function selectIcon(iconName: string) {
  formData.icon = iconName
  showIconSelector.value = false
}

// 加载菜单树数据
async function loadMenuTree() {
  try {
    console.log('开始加载菜单树数据...')
    const data = await menuTreeAll()
    console.log('菜单树原始数据:', data)

    // 验证返回的数据
    if (Array.isArray(data)) {
      // 转换数据格式，确保 children 是数组格式
      const convertedData = convertTreeData(data)
      menuTreeData.value = convertedData
      console.log('菜单树数据加载成功，共', data.length, '项')
    } else if (data && typeof data === 'object' && Array.isArray((data as any).content)) {
      // 如果返回的是分页格式，取 content
      const convertedData = convertTreeData((data as any).content)
      menuTreeData.value = convertedData
      console.log('菜单树数据加载成功（分页格式），共', (data as any).content.length, '项')
    } else {
      console.warn('菜单树数据格式异常:', data)
      menuTreeData.value = []
    }

    // 打印菜单树结构，便于调试
    console.log('最终菜单树数据:', menuTreeData.value)
  } catch (error) {
    console.error('加载菜单树失败:', error)
    message.error('加载菜单树失败')
    menuTreeData.value = []
  }
}

// 工具函数：递归把 children 从 Set 转成 Array
function convertTreeData(tree: any[]): any[] {
  if (!Array.isArray(tree)) return []
  return tree.map(node => ({
    ...node,
    children: node.children ? convertTreeData(Array.from(node.children)) : [],
  }))
}

// 初始化表单数据
function initFormData() {
  try {
    console.log('初始化表单数据，当前模式:', props.mode)
    console.log('菜单数据:', props.menuData)
    console.log('父级菜单:', props.parentMenu)

    if (props.menuData && typeof props.menuData === 'object') {
      // 安全地复制数据，避免直接引用
      const menuData = props.menuData
      Object.assign(formData, {
        id: menuData.id || '',
        name: menuData.name || '',
        title: menuData.title || '',
        url: menuData.url || '',
        icon: menuData.icon || '',
        showIcon: Boolean(menuData.showIcon),
        sort: Number(menuData.sort) || 0,
        component: menuData.component || '',
        redirect: menuData.redirect || '',
        hidden: Boolean(menuData.hidden),
        keepAlive: Boolean(menuData.keepAlive),
        permission: menuData.permission || '',
        parentId: menuData.parentId || null,
        enabled: menuData.enabled !== undefined ? menuData.enabled : true
      })

      console.log('编辑模式 - 表单数据已设置:', formData)
      console.log('父级菜单ID:', formData.parentId)
    } else {
      // 重置表单数据
      Object.assign(formData, {
        id: '',
        name: '',
        title: '',
        url: '',
        icon: '',
        showIcon: true,
        sort: 0,
        component: '',
        redirect: '',
        hidden: false,
        keepAlive: false,
        permission: '',
        parentId: null,
        enabled: true
      })
      console.log('新建模式 - 表单数据已重置')
    }

    // 如果是添加子菜单，设置父级菜单ID
    if (props.parentMenu && typeof props.parentMenu === 'object') {
      formData.parentId = props.parentMenu.id || null
      console.log('添加子菜单模式 - 父级菜单ID已设置:', formData.parentId)
    }

    // 验证父级菜单ID是否正确设置
    console.log('最终父级菜单ID:', formData.parentId)
    console.log('菜单树数据是否可用:', menuTreeData.value.length > 0)

    // 如果菜单树数据已加载，验证父级菜单是否存在
    if (menuTreeData.value.length > 0 && formData.parentId) {
      const parentExists = findMenuInTree(menuTreeData.value, formData.parentId)
      console.log('父级菜单在树中是否存在:', parentExists)
    }
  } catch (error) {
    console.warn('initFormData error:', error)
    // 重置为默认值
    Object.assign(formData, {
      id: '', name: '', title: '', url: '', icon: '',
      showIcon: true, sort: 0, component: '', redirect: '',
      hidden: false, keepAlive: false, permission: '', parentId: null,
      enabled: true
    })
  }
}

// 递归查找菜单在树中的位置
function findMenuInTree(tree: MenuItem[], targetId: number | null): boolean {
  if (!targetId) return false

  for (const item of tree) {
    if (item.id === targetId) {
      return true
    }
    if (item.children && item.children.length > 0) {
      if (findMenuInTree(item.children, targetId)) {
        return true
      }
    }
  }
  return false
}

// 提交表单
async function handleSubmit() {
  try {
    // 验证表单
    await formRef.value?.validate()

    // 验证必填字段
    if (!formData.name || !formData.name.trim()) {
      message.error('请输入菜单名称')
      return
    }

    if (!formData.title || !formData.title.trim()) {
      message.error('请输入菜单标题')
      return
    }

    submitting.value = true

    // 深拷贝表单数据，避免直接引用
    const submitData = {
      id: formData.id,
      name: formData.name.trim(),
      title: formData.title.trim(),
      url: formData.url || '',
      icon: formData.icon || '',
      showIcon: Boolean(formData.showIcon),
      sort: Number(formData.sort) || 0,
      component: formData.component || '',
      redirect: formData.redirect || '',
      hidden: Boolean(formData.hidden),
      keepAlive: Boolean(formData.keepAlive),
      permission: formData.permission || '',
      parentId: formData.parentId || null,
      enabled: formData.enabled
    }

    emit('submit', submitData)
  } catch (error) {
    console.error('表单验证失败:', error)
    if (error && typeof error === 'object' && 'errorFields' in error) {
      message.error('请检查表单填写是否正确')
    } else {
      message.error('表单验证失败')
    }
  } finally {
    submitting.value = false
  }
}

// 取消操作
function handleCancel() {
  emit('cancel')
}

// 监听菜单数据变化
watch(() => props.menuData, () => {
  try {
    console.log('菜单数据变化，重新初始化表单')
    initFormData()
  } catch (error) {
    console.warn('MenuForm watch menuData error:', error)
  }
}, { immediate: true })

// 监听父级菜单变化
watch(() => props.parentMenu, () => {
  try {
    console.log('父级菜单变化:', props.parentMenu)
    if (props.parentMenu) {
      formData.parentId = props.parentMenu.id || null
      console.log('父级菜单ID已更新:', formData.parentId)
    }
  } catch (error) {
    console.warn('MenuForm watch parentMenu error:', error)
  }
}, { immediate: true })

// 监听菜单树数据变化，确保数据加载完成后再初始化表单
watch(() => menuTreeData.value, (newTreeData) => {
  try {
    console.log('菜单树数据变化，重新初始化表单数据')
    if (newTreeData && newTreeData.length > 0) {
      // 菜单树数据加载完成，重新初始化表单以确保父级菜单正确回显
      nextTick(() => {
        initFormData()
      })
    }
  } catch (error) {
    console.warn('MenuForm watch menuTreeData error:', error)
  }
}, { deep: true })

// 组件挂载时加载菜单树
onMounted(async () => {
  try {
    console.log('MenuForm 组件挂载，开始加载菜单树')
    await loadMenuTree()
    console.log('菜单树加载完成，初始化表单数据')
    // 确保菜单树加载完成后再初始化表单
    nextTick(() => {
      initFormData()
    })
  } catch (error) {
    console.warn('MenuForm onMounted error:', error)
  }
})

// 组件卸载时清理数据
onBeforeUnmount(() => {
  try {
    // 清理表单数据
    Object.assign(formData, {
      id: '', name: '', title: '', url: '', icon: '',
      showIcon: true, sort: 0, component: '', redirect: '',
      hidden: false, keepAlive: false, permission: '', parentId: null,
      enabled: true
    })

    // 清理其他响应式数据
    menuTreeData.value = []
    showIconSelector.value = false
    submitting.value = false
  } catch (error) {
    console.warn('MenuForm onBeforeUnmount error:', error)
  }
})

// 测试菜单树API
async function testMenuTreeApi() {
  try {
    console.log('开始测试菜单树API...')
    const data = await menuTreeAll()
    console.log('菜单树API返回数据:', data)

    if (Array.isArray(data)) {
      console.log('数据是数组格式，长度:', data.length)
      data.forEach((item, index) => {
        console.log(`菜单 ${index + 1}:`, {
          id: item.id,
          title: item.title,
          parentId: item.parentId,
          children: item.children?.length || 0
        })
      })
    } else {
      console.log('数据不是数组格式:', typeof data, data)
    }

    message.success('菜单树API测试完成，请查看控制台')
  } catch (error) {
    console.error('菜单树API测试失败:', error)
    message.error('菜单树API测试失败: ' + (error instanceof Error ? error.message : '未知错误'))
  }
}
</script>

<style scoped>
.menu-form {
  padding: 0;
}

.icon-selector-row {
  display: flex;
  align-items: center;
  width: 100%;
}

.icon-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 12px;
  max-height: 400px;
  overflow-y: auto;
}

.icon-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 12px;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.icon-item:hover {
  border-color: #1890ff;
  background: #f0f8ff;
}

.icon-item.active {
  border-color: #1890ff;
  background: #e6f7ff;
}

.grid-icon {
  font-size: 18px;
  color: #1890ff;
  margin-bottom: 8px;
}

.icon-name {
  font-size: 12px;
  color: #666;
  text-align: center;
  word-break: break-all;
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

.input-suffix-icon {
  font-size: 18px;
  color: #1890ff;
  vertical-align: middle;
}
</style>
