<template>
  <div class="content-container" style="position: relative;">
    <div class="content-card">
      <!-- 字典类型管理 -->
      <a-tabs v-model:activeKey="activeTab" @change="handleTabChange">
        <a-tab-pane key="type" tab="字典类型">
          <!-- 查询表单 -->
          <div class="form-container">
            <a-form layout="inline" :model="typeQuery">
              <a-form-item label="字典编码">
                <a-input v-model:value="typeQuery.dictCode" placeholder="请输入字典编码" />
              </a-form-item>
              <a-form-item label="字典名称">
                <a-input v-model:value="typeQuery.dictName" placeholder="请输入字典名称" />
              </a-form-item>
              <a-form-item label="状态">
                <a-select v-model:value="typeQuery.enabled" placeholder="请选择状态" style="width: 120px;">
                  <a-select-option :value="undefined">全部</a-select-option>
                  <a-select-option :value="true">启用</a-select-option>
                  <a-select-option :value="false">禁用</a-select-option>
                </a-select>
              </a-form-item>
              <a-form-item>
                <a-button type="primary" @click="handleTypeSearch">搜索</a-button>
                <a-button class="ml-2" @click="handleTypeReset">重置</a-button>
              </a-form-item>
            </a-form>
          </div>

          <!-- 工具栏 -->
          <div class="toolbar-container">
            <div class="table-title">字典类型管理</div>
            <div class="table-actions">
              <a-button type="primary" @click="handleTypeCreate">
                <template #icon>
                  <PlusOutlined />
                </template>
                新建字典类型
              </a-button>
              <a-tooltip title="刷新">
                <span class="action-icon" @click="handleTypeRefresh">
                  <ReloadOutlined :spin="typeRefreshing" />
                </span>
              </a-tooltip>
            </div>
          </div>

          <!-- 表格 -->
          <a-table
            :columns="typeColumns"
            :data-source="typeData"
            :loading="typeLoading"
            :pagination="typePagination"
            :row-selection="{ selectedRowKeys: selectedTypeKeys, onChange: onTypeSelectChange }"
            row-key="id"
            @change="handleTypeTableChange"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.dataIndex === 'enabled'">
                <a-tag :color="record.enabled ? 'green' : 'red'">
                  {{ record.enabled ? '启用' : '禁用' }}
                </a-tag>
              </template>
              <template v-else-if="column.dataIndex === 'action'">
                <a-space>
                  <a-button type="link" size="small" @click="handleTypeEdit(record)">编辑</a-button>
                  <a-button type="link" size="small" danger @click="handleTypeDelete(record)">删除</a-button>
                  <a-button type="link" size="small" @click="handleViewItems(record)">查看字典项</a-button>
                </a-space>
              </template>
            </template>
          </a-table>
        </a-tab-pane>

        <a-tab-pane key="item" tab="字典项">
          <!-- 查询表单 -->
          <div class="form-container">
            <a-form layout="inline" :model="itemQuery">
              <a-form-item label="字典类型">
                <a-select
                  v-model:value="itemQuery.dictTypeId"
                  placeholder="请选择字典类型"
                  style="width: 200px;"
                  allow-clear
                >
                  <a-select-option v-for="type in dictTypeOptions" :key="type.id" :value="type.id">
                    {{ type.dictName }} ({{ type.dictCode }})
                  </a-select-option>
                </a-select>
              </a-form-item>
              <a-form-item label="字典值">
                <a-input v-model:value="itemQuery.value" placeholder="请输入字典值" />
              </a-form-item>
              <a-form-item label="字典标签">
                <a-input v-model:value="itemQuery.label" placeholder="请输入字典标签" />
              </a-form-item>
              <a-form-item label="状态">
                <a-select v-model:value="itemQuery.enabled" placeholder="请选择状态" style="width: 120px;">
                  <a-select-option :value="undefined">全部</a-select-option>
                  <a-select-option :value="true">启用</a-select-option>
                  <a-select-option :value="false">禁用</a-select-option>
                </a-select>
              </a-form-item>
              <a-form-item>
                <a-button type="primary" @click="handleItemSearch">搜索</a-button>
                <a-button class="ml-2" @click="handleItemReset">重置</a-button>
              </a-form-item>
            </a-form>
          </div>

          <!-- 工具栏 -->
          <div class="toolbar-container">
            <div class="table-title">字典项管理</div>
            <div class="table-actions">
              <a-button type="primary" @click="handleItemCreate" :disabled="!itemQuery.dictTypeId">
                <template #icon>
                  <PlusOutlined />
                </template>
                新建字典项
              </a-button>
              <a-tooltip title="刷新">
                <span class="action-icon" @click="handleItemRefresh">
                  <ReloadOutlined :spin="itemRefreshing" />
                </span>
              </a-tooltip>
            </div>
          </div>

          <!-- 表格 -->
          <a-table
            :columns="itemColumns"
            :data-source="itemData"
            :loading="itemLoading"
            :pagination="itemPagination"
            :row-selection="{ selectedRowKeys: selectedItemKeys, onChange: onItemSelectChange }"
            row-key="id"
            @change="handleItemTableChange"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.dataIndex === 'enabled'">
                <a-tag :color="record.enabled ? 'green' : 'red'">
                  {{ record.enabled ? '启用' : '禁用' }}
                </a-tag>
              </template>
              <template v-else-if="column.dataIndex === 'action'">
                <a-space>
                  <a-button type="link" size="small" @click="handleItemEdit(record)">编辑</a-button>
                  <a-button type="link" size="small" danger @click="handleItemDelete(record)">删除</a-button>
                </a-space>
              </template>
            </template>
          </a-table>
        </a-tab-pane>
      </a-tabs>

      <!-- 字典类型表单弹窗 -->
      <a-modal
        v-model:open="typeFormVisible"
        :title="typeFormTitle"
        :width="600"
        @ok="handleTypeSubmit"
        @cancel="handleTypeCancel"
      >
        <DictTypeForm ref="typeFormRef" :form-data="currentType" />
      </a-modal>

      <!-- 字典项表单弹窗 -->
      <a-modal
        v-model:open="itemFormVisible"
        :title="itemFormTitle"
        :width="600"
        @ok="handleItemSubmit"
        @cancel="handleItemCancel"
      >
        <DictItemForm ref="itemFormRef" :form-data="currentItem" :dict-type-id="itemQuery.dictTypeId" />
      </a-modal>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import {
  getDictTypeList,
  createDictType,
  updateDictType,
  deleteDictType,
  type DictTypeItem,
  type DictTypeQuery,
  type DictTypeCreateUpdateDto,
  getDictItemList,
  createDictItem,
  updateDictItem,
  deleteDictItem,
  type DictItem,
  type DictItemQuery,
  type DictItemCreateUpdateDto,
  getDictTypesByTenant,
} from '@/api/dict'
import DictTypeForm from './DictTypeForm.vue'
import DictItemForm from './DictItemForm.vue'

// 当前激活的标签页
const activeTab = ref('type')

// 字典类型相关
const typeQuery = ref<DictTypeQuery>({})
const typeData = ref<DictTypeItem[]>([])
const typeLoading = ref(false)
const typeRefreshing = ref(false)
const selectedTypeKeys = ref<string[]>([])
const typePagination = ref({
  current: 1,
  pageSize: 10,
  total: 0,
  showTotal: (total: number) => `共 ${total} 条`,
})
const typeFormVisible = ref(false)
const typeFormTitle = ref('新建字典类型')
const typeFormRef = ref()
const currentType = ref<DictTypeItem | null>(null)

// 字典项相关
const itemQuery = ref<DictItemQuery>({})
const itemData = ref<DictItem[]>([])
const itemLoading = ref(false)
const itemRefreshing = ref(false)
const selectedItemKeys = ref<string[]>([])
const itemPagination = ref({
  current: 1,
  pageSize: 10,
  total: 0,
  showTotal: (total: number) => `共 ${total} 条`,
})
const itemFormVisible = ref(false)
const itemFormTitle = ref('新建字典项')
const itemFormRef = ref()
const currentItem = ref<DictItem | null>(null)
const dictTypeOptions = ref<DictTypeItem[]>([])

// 表格列定义
const typeColumns = [
  { title: 'ID', dataIndex: 'id', width: 80 },
  { title: '字典编码', dataIndex: 'dictCode', width: 150 },
  { title: '字典名称', dataIndex: 'dictName', width: 150 },
  { title: '描述', dataIndex: 'description', width: 200 },
  { title: '排序', dataIndex: 'sortOrder', width: 80 },
  { title: '状态', dataIndex: 'enabled', width: 100 },
  { title: '操作', dataIndex: 'action', width: 200, fixed: 'right' },
]

const itemColumns = [
  { title: 'ID', dataIndex: 'id', width: 80 },
  { title: '字典编码', dataIndex: 'dictCode', width: 150 },
  { title: '字典值', dataIndex: 'value', width: 150 },
  { title: '字典标签', dataIndex: 'label', width: 150 },
  { title: '描述', dataIndex: 'description', width: 200 },
  { title: '排序', dataIndex: 'sortOrder', width: 80 },
  { title: '状态', dataIndex: 'enabled', width: 100 },
  { title: '操作', dataIndex: 'action', width: 150, fixed: 'right' },
]

// 标签页切换
function handleTabChange(key: string) {
  if (key === 'item' && dictTypeOptions.value.length === 0) {
    loadDictTypeOptions()
  }
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

// 字典类型相关方法
async function loadTypeData() {
  typeLoading.value = true
  try {
    const params = {
      ...typeQuery.value,
      page: typePagination.value.current - 1,
      size: typePagination.value.pageSize,
    }
    const result = await getDictTypeList(params)
    typeData.value = result.content
    typePagination.value.total = result.totalElements
  } catch (error) {
    message.error('加载字典类型数据失败')
  } finally {
    typeLoading.value = false
  }
}

function handleTypeSearch() {
  typePagination.value.current = 1
  loadTypeData()
}

function handleTypeReset() {
  typeQuery.value = {}
  typePagination.value.current = 1
  loadTypeData()
}

function handleTypeRefresh() {
  typeRefreshing.value = true
  loadTypeData()
  setTimeout(() => {
    typeRefreshing.value = false
  }, 500)
}

function handleTypeTableChange(pagination: any) {
  typePagination.value.current = pagination.current
  typePagination.value.pageSize = pagination.pageSize
  loadTypeData()
}

function onTypeSelectChange(keys: string[]) {
  selectedTypeKeys.value = keys
}

function handleTypeCreate() {
  currentType.value = null
  typeFormTitle.value = '新建字典类型'
  typeFormVisible.value = true
}

function handleTypeEdit(record: DictTypeItem) {
  currentType.value = { ...record }
  typeFormTitle.value = '编辑字典类型'
  typeFormVisible.value = true
}

async function handleTypeSubmit() {
  try {
    await typeFormRef.value?.validate()
    const formData = typeFormRef.value?.getFormData()
    if (currentType.value?.id) {
      await updateDictType(currentType.value.id, formData)
      message.success('更新成功')
    } else {
      await createDictType(formData)
      message.success('创建成功')
    }
    typeFormVisible.value = false
    loadTypeData()
  } catch (error: any) {
    if (error?.errorFields) {
      return
    }
    message.error(error?.message || '操作失败')
  }
}

function handleTypeCancel() {
  typeFormVisible.value = false
  currentType.value = null
}

function handleTypeDelete(record: DictTypeItem) {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除字典类型 "${record.dictName}" 吗？删除后关联的字典项也会被删除。`,
    onOk: async () => {
      try {
        await deleteDictType(record.id!)
        message.success('删除成功')
        loadTypeData()
      } catch (error) {
        message.error('删除失败')
      }
    },
  })
}

function handleViewItems(record: DictTypeItem) {
  activeTab.value = 'item'
  itemQuery.value.dictTypeId = record.id
  loadItemData()
}

// 字典项相关方法
async function loadItemData() {
  itemLoading.value = true
  try {
    const params = {
      ...itemQuery.value,
      page: itemPagination.value.current - 1,
      size: itemPagination.value.pageSize,
    }
    const result = await getDictItemList(params)
    itemData.value = result.content
    itemPagination.value.total = result.totalElements
  } catch (error) {
    message.error('加载字典项数据失败')
  } finally {
    itemLoading.value = false
  }
}

function handleItemSearch() {
  itemPagination.value.current = 1
  loadItemData()
}

function handleItemReset() {
  itemQuery.value = {}
  itemPagination.value.current = 1
  loadItemData()
}

function handleItemRefresh() {
  itemRefreshing.value = true
  loadItemData()
  setTimeout(() => {
    itemRefreshing.value = false
  }, 500)
}

function handleItemTableChange(pagination: any) {
  itemPagination.value.current = pagination.current
  itemPagination.value.pageSize = pagination.pageSize
  loadItemData()
}

function onItemSelectChange(keys: string[]) {
  selectedItemKeys.value = keys
}

function handleItemCreate() {
  if (!itemQuery.value.dictTypeId) {
    message.warning('请先选择字典类型')
    return
  }
  currentItem.value = null
  itemFormTitle.value = '新建字典项'
  itemFormVisible.value = true
}

function handleItemEdit(record: DictItem) {
  currentItem.value = { ...record }
  itemFormTitle.value = '编辑字典项'
  itemFormVisible.value = true
}

async function handleItemSubmit() {
  try {
    await itemFormRef.value?.validate()
    const formData = itemFormRef.value?.getFormData()
    if (currentItem.value?.id) {
      await updateDictItem(currentItem.value.id, formData)
      message.success('更新成功')
    } else {
      await createDictItem(formData)
      message.success('创建成功')
    }
    itemFormVisible.value = false
    loadItemData()
  } catch (error: any) {
    if (error?.errorFields) {
      return
    }
    message.error(error?.message || '操作失败')
  }
}

function handleItemCancel() {
  itemFormVisible.value = false
  currentItem.value = null
}

function handleItemDelete(record: DictItem) {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除字典项 "${record.label}" 吗？`,
    onOk: async () => {
      try {
        await deleteDictItem(record.id!)
        message.success('删除成功')
        loadItemData()
      } catch (error) {
        message.error('删除失败')
      }
    },
  })
}

// 初始化
onMounted(() => {
  loadTypeData()
})
</script>

<style scoped>
.content-container {
  padding: 16px;
}

.content-card {
  background: #fff;
  border-radius: 4px;
  padding: 16px;
}

.form-container {
  margin-bottom: 16px;
  padding: 16px;
  background: #fafafa;
  border-radius: 4px;
}

.toolbar-container {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.table-title {
  font-size: 16px;
  font-weight: 500;
}

.table-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.action-icon {
  cursor: pointer;
  font-size: 16px;
  color: #1890ff;
  margin-left: 8px;
}

.ml-2 {
  margin-left: 8px;
}
</style>

