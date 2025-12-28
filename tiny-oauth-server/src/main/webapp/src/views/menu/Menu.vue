<template>
  <!-- 外层内容容器，统一风格 -->
  <div class="content-container" style="position: relative;">
    <div class="content-card">
      <!-- 查询表单，风格与用户管理一致 -->
      <div class="form-container">
        <a-form layout="inline" :model="query">
          <a-form-item label="菜单名称">
            <a-input v-model:value="query.name" placeholder="请输入菜单名称" />
          </a-form-item>
          <a-form-item label="菜单标题">
            <a-input v-model:value="query.title" placeholder="请输入菜单标题" />
          </a-form-item>
          <a-form-item label="权限标识">
            <a-input v-model:value="query.permission" placeholder="请输入权限标识" />
          </a-form-item>
          <a-form-item label="是否启用">
            <a-select v-model:value="query.enabled" allow-clear placeholder="全部">
              <a-select-option :value="true">启用</a-select-option>
              <a-select-option :value="false">禁用</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item>
            <a-button type="primary" @click="throttledSearch">搜索</a-button>
            <a-button class="ml-2" @click="throttledReset">重置</a-button>
          </a-form-item>
        </a-form>
      </div>

      <!-- 工具栏，包含批量操作、新建、刷新、列设置等 -->
      <div class="toolbar-container">
        <div class="table-title">菜单列表</div>
        <div class="table-actions">
          <div v-if="selectedRowKeys.length > 0" class="batch-actions">
            <a-button type="primary" danger @click="throttledBatchDelete" class="toolbar-btn">
              批量删除 ({{ selectedRowKeys.length }})
            </a-button>
            <a-button @click="clearSelection" class="toolbar-btn">取消选择</a-button>
          </div>
          <a-button type="link" @click="throttledCreate" class="toolbar-btn">
            <template #icon>
              <PlusOutlined />
            </template>
            新建
          </a-button>
          <a-tooltip title="刷新">
            <span class="action-icon" @click="throttledRefresh">
              <ReloadOutlined :spin="refreshing" />
            </span>
          </a-tooltip>
          <a-popover placement="bottomRight" trigger="click">
            <template #content>
              <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px;">
                <div style="display: flex; align-items: center;">
                  <a-checkbox :checked="showColumnKeys.length === allColumns.length"
                    :indeterminate="showColumnKeys.length > 0 && showColumnKeys.length < allColumns.length"
                    @change="onCheckAllChange" />
                  <span style="font-weight: bold; margin-left: 8px;">列展示/排序</span>
                </div>
                <span style="font-weight: bold; color: #1677ff; cursor: pointer;" @click="resetColumnOrder">
                  重置
                </span>
              </div>
              <VueDraggable v-model="draggableColumns"
                :item-key="(item: any) => item?.dataIndex || ('col_' + Math.random())" handle=".drag-handle"
                @end="onDragEnd" class="draggable-columns" ghost-class="sortable-ghost" chosen-class="sortable-chosen"
                tag="div">
                <template #item="{ element }">
                  <div class="draggable-column-item">
                    <HolderOutlined class="drag-handle" />
                    <a-checkbox :checked="showColumnKeys.includes(element.dataIndex)"
                      @change="(e: any) => onCheckboxChange(element.dataIndex, e.target.checked)">
                      {{ element.title }}
                    </a-checkbox>
                  </div>
                </template>
              </VueDraggable>
            </template>
            <a-tooltip title="列设置">
              <SettingOutlined class="action-icon" />
            </a-tooltip>
          </a-popover>
        </div>
      </div>

      <!-- 表格区域，支持多选、动态列 -->
      <div class="table-container" ref="tableContentRef">
        <div class="table-scroll-container" ref="tableScrollContainerRef">
          <a-table :columns="columns" :data-source="tableData" :row-key="(record: any) => String(record.id)" bordered
            :loading="loading" :row-selection="rowSelection" :custom-row="onCustomRow" :row-class-name="getRowClassName"
            :scroll="{ x: 'max-content', y: tableBodyHeight }" :expandable="expandableConfig" :pagination="false">
            <template #expandIcon="{ record }">
              <span v-if="!record.leaf" style="cursor:pointer; color:#1890ff; margin-right:4px;"
                @click.stop="() => onExpandIconClick(record)">
                <MinusOutlined v-if="expandedRowKeys.includes(String(record.id))" />
                <PlusOutlined v-else />
              </span>
            </template>
            <template #bodyCell="{ column, record }">
              <template v-if="column.dataIndex === 'icon'">
                <div class="icon-cell">
                  <Icon v-if="record.icon && record.showIcon" :icon="record.icon" className="menu-icon" />
                  <span v-else class="no-icon">-</span>
                </div>
              </template>
              <template v-else-if="column.dataIndex === 'hidden'">
                <a-tag :color="record.hidden ? 'red' : 'green'">
                  {{ record.hidden ? '隐藏' : '显示' }}
                </a-tag>
              </template>
              <template v-else-if="column.dataIndex === 'keepAlive'">
                <a-tag :color="record.keepAlive ? 'blue' : 'default'">
                  {{ record.keepAlive ? '缓存' : '不缓存' }}
                </a-tag>
              </template>
              <template v-else-if="column.dataIndex === 'type'">
                <a-tag :color="getTypeColor(record.type)">
                  {{ getTypeText(record.type) }}
                </a-tag>
              </template>
              <template v-else-if="column.dataIndex === 'enabled'">
                <a-tag :color="record.enabled ? 'green' : 'red'">
                  {{ record.enabled ? '启用' : '禁用' }}
                </a-tag>
              </template>
              <template v-else-if="column.dataIndex === 'title'">
                {{ record.title }}
              </template>
              <template v-else-if="column.dataIndex === 'action'">
                <div class="action-buttons">
                  <a-button type="link" size="small" @click.stop="throttledEdit(record)" class="action-btn">
                    <template #icon>
                      <EditOutlined />
                    </template>
                    编辑
                  </a-button>
                  <a-tooltip v-if="record.leaf" title="叶子节点不可添加子菜单">
                    <a-button type="link" size="small" :disabled="true" class="action-btn">
                      <template #icon>
                        <PlusOutlined />
                      </template>
                      子菜单
                    </a-button>
                  </a-tooltip>
                  <a-button v-else type="link" size="small" @click.stop="throttledAddChild(record)" class="action-btn">
                    <template #icon>
                      <PlusOutlined />
                    </template>
                    子菜单
                  </a-button>
                  <a-button type="link" size="small" danger @click.stop="throttledDelete(record)" class="action-btn">
                    <template #icon>
                      <DeleteOutlined />
                    </template>
                    删除
                  </a-button>
                </div>
              </template>
            </template>
          </a-table>
        </div>
      </div>
    </div>

    <!-- 抽屉表单，编辑/新建菜单 -->
    <a-drawer v-model:open="drawerVisible" :title="drawerMode === 'create' ? '新建菜单' : '编辑菜单'" width="600px"
      :get-container="false" :style="{ position: 'absolute' }" @close="handleDrawerClose">
      <MenuForm v-if="drawerVisible" :mode="drawerMode" :menu-data="currentMenu" :parent-menu="parentMenu"
        @submit="handleFormSubmit" @cancel="handleDrawerClose" />
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
// 引入Vue相关API
import { ref, computed, onMounted, watch, nextTick, onBeforeUnmount } from 'vue'
// 引入菜单API
import { getMenusByParentId, createMenu, updateMenu, deleteMenu, batchDeleteMenus, type MenuItem, type MenuQuery, menuList } from '@/api/menu'
// 引入Antd组件和图标
import { message, Modal } from 'ant-design-vue'
import {
  ReloadOutlined,
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  SettingOutlined,
  HolderOutlined,
  MinusOutlined
} from '@ant-design/icons-vue'
import VueDraggable from 'vuedraggable'
import MenuForm from './MenuForm.vue'
import Icon from '@/components/Icon.vue' // 通用图标回显组件

// MenuItem 类型补充 expanded 和 _childrenLoaded 字段，消除TS报错
type MenuItemEx = MenuItem & {
  expanded?: boolean;
  _childrenLoaded?: boolean;
  leaf?: boolean | number; // 添加 leaf 属性，支持 boolean 或 number 类型
  _loading?: boolean; // 添加加载状态属性
}

// 查询条件，包含parentId用于按层级查询
const query = ref<MenuQuery & { parentId: number | null }>({
  name: '',
  title: '',
  permission: '',
  enabled: undefined,
  parentId: null // 默认查询顶级菜单，允许 null
})

// 表格数据
const tableData = ref<MenuItemEx[]>([])

// 加载状态
const loading = ref(false)

// 选中行key
const selectedRowKeys = ref<string[]>([])

// 所有列定义
const INITIAL_COLUMNS = [
  { title: '菜单名称', dataIndex: 'name' },
  { title: '菜单标题', dataIndex: 'title' },
  { title: '图标', dataIndex: 'icon', align: 'center' },
  { title: '路径', dataIndex: 'url' },
  { title: '权限标识', dataIndex: 'permission' },
  { title: '排序', dataIndex: 'sort', align: 'center' },
  { title: '菜单类型', dataIndex: 'type', align: 'center' },
  { title: '是否启用', dataIndex: 'enabled', align: 'center' },
  { title: '显示', dataIndex: 'hidden', align: 'center' },
  { title: '缓存', dataIndex: 'keepAlive', align: 'center' },
  { title: '操作', dataIndex: 'action', width: 200, fixed: 'right', align: 'center' }
]

const allColumns = ref([...INITIAL_COLUMNS])
const draggableColumns = ref([...INITIAL_COLUMNS])
const showColumnKeys = ref(
  INITIAL_COLUMNS.map(col => col.dataIndex).filter(key => typeof key === 'string' && key)
)

watch(allColumns, (val) => {
  try {
    showColumnKeys.value = showColumnKeys.value.filter(key => val.some(col => col.dataIndex === key))
  } catch (error) {
    console.warn('watch allColumns error:', error)
  }
})

watch(draggableColumns, (val) => {
  try {
    allColumns.value = val.filter(col => typeof col.dataIndex === 'string')
    showColumnKeys.value = showColumnKeys.value.filter(key => allColumns.value.some(col => col.dataIndex === key))
  } catch (error) {
    console.warn('watch draggableColumns error:', error)
  }
})

// 列复选框变化
function onCheckboxChange(dataIndex: string, checked: boolean) {
  try {
    if (!dataIndex) return
    if (checked) {
      if (!showColumnKeys.value.includes(dataIndex)) showColumnKeys.value.push(dataIndex)
    } else {
      showColumnKeys.value = showColumnKeys.value.filter(key => key !== dataIndex)
    }
  } catch (error) {
    console.warn('onCheckboxChange error:', error)
  }
}

// 列全选
function onCheckAllChange(e: any) {
  try {
    if (e.target.checked) {
      showColumnKeys.value = INITIAL_COLUMNS.map(col => col.dataIndex)
    } else {
      showColumnKeys.value = []
    }
  } catch (error) {
    console.warn('onCheckAllChange error:', error)
  }
}

const DEFAULT_COLUMN_KEYS = INITIAL_COLUMNS
  .filter(col => typeof col.dataIndex === 'string' && col.dataIndex)
  .map(col => col.dataIndex)

function resetColumnOrder() {
  try {
    allColumns.value = [...INITIAL_COLUMNS]
    draggableColumns.value = [...INITIAL_COLUMNS]
    showColumnKeys.value = [...DEFAULT_COLUMN_KEYS]
  } catch (error) {
    console.warn('resetColumnOrder error:', error)
  }
}

// 拖拽结束
function onDragEnd() {
  try {
    // 拖拽结束后的处理逻辑
  } catch (error) {
    console.warn('onDragEnd error:', error)
  }
}

// 计算最终表格列
const columns = computed(() => {
  try {
    return [
      {
        title: '序号',
        dataIndex: 'index',
        width: 80,
        align: 'left',
        fixed: 'left',
        customRender: ({ index }: { index?: number }) => {
          try {
            return (typeof index === 'number' ? index : 0) + 1
          } catch (error) {
            console.warn('columns customRender error:', error)
            return 0
          }
        }
      },
      ...INITIAL_COLUMNS.filter(col => showColumnKeys.value.includes(col.dataIndex))
    ]
  } catch (error) {
    console.warn('columns computed error:', error)
    return []
  }
})

// 多选配置
const rowSelection = computed(() => {
  try {
    return {
      selectedRowKeys: selectedRowKeys.value,
      onChange: (selectedKeys: (string | number)[]) => {
        try {
          selectedRowKeys.value = selectedKeys.map(String)
        } catch (error) {
          console.warn('rowSelection onChange error:', error)
        }
      },
      checkStrictly: false,
      preserveSelectedRowKeys: true,
      fixed: true
    }
  } catch (error) {
    console.warn('rowSelection computed error:', error)
    return {}
  }
})

// 展开的节点keys
const expandedRowKeys = ref<string[]>([])

// 计算菜单标题列的索引
const expandIconColumnIndex = computed(() => {
  return columns.value.findIndex(col => col.dataIndex === 'title')
})

// 展开配置（平铺结构专用）
const expandableConfig = computed(() => ({
  expandedRowKeys: expandedRowKeys.value,
  expandIconColumnIndex: expandIconColumnIndex.value,
  rowExpandable: (record: any) => !record.leaf,
  onExpand: async (expanded: boolean, record: any) => {
    console.log('[菜单调试] onExpand 触发', { expanded, record });
    if (expanded) {
      console.log('[菜单调试] 进入展开分支，准备请求子菜单', record.id);
      // 不判断 _childrenLoaded，每次都请求
      try {
        const childMenus = await getMenusByParentId(record.id);
        console.log('[菜单调试] getMenusByParentId 返回', childMenus);
        if (childMenus && Array.isArray(childMenus) && childMenus.length > 0) {
          const childMenusArray = childMenus as MenuItemEx[];
          childMenusArray.forEach(item => {
            item.expanded = false;
            item._childrenLoaded = false;
            item._loading = false;
            if (item.leaf === false || item.leaf === 0) {
              item.children = [];
            }
          });
          record._childrenLoaded = true;
          const parentIndex = tableData.value.findIndex(item => item.id === record.id);
          if (parentIndex !== -1) {
            const newData = [
              ...tableData.value.slice(0, parentIndex + 1),
              ...childMenusArray,
              ...tableData.value.slice(parentIndex + 1)
            ];
            tableData.value = newData;
          }
        } else {
          console.log('[菜单调试] 没有子菜单数据');
        }
      } catch (err) {
        console.error('[菜单调试] getMenusByParentId 请求异常', err);
      }
    } else {
      console.log('[菜单调试] 进入收起分支', record.id);
      collapseChildren(record);
      expandedRowKeys.value = expandedRowKeys.value.filter(key => key !== String(record.id));
      record._childrenLoaded = false;
    }
  },
  onExpandedRowsChange: (expandedRows: any[]) => {
    try {
      expandedRowKeys.value = expandedRows.map(row => String(row.id));
      console.log('[菜单调试] onExpandedRowsChange', expandedRows);
    } catch (error) {
      console.warn('expandable onExpandedRowsChange error:', error);
    }
  }
}))

// 表格内容区高度自适应
const tableContentRef = ref<HTMLElement | null>(null)
const tableScrollContainerRef = ref<HTMLElement | null>(null)
const tableBodyHeight = ref(400)

function updateTableBodyHeight() {
  try {
    nextTick(() => {
      if (tableContentRef.value && tableScrollContainerRef.value) {
        const tableHeader = tableContentRef.value.querySelector('.ant-table-header') as HTMLElement
        const containerHeight = tableContentRef.value.clientHeight
        const tableHeaderHeight = tableHeader ? tableHeader.clientHeight : 55
        const bodyHeight = containerHeight - tableHeaderHeight
        tableBodyHeight.value = Math.max(bodyHeight, 200)
      }
    })
  } catch (error) {
    console.warn('updateTableBodyHeight error:', error)
  }
}

// 加载数据 - 使用list结构加载
async function loadData() {
  try {
    loading.value = true
    const params: any = {
      name: query.value.name?.trim() || '',
      title: query.value.title?.trim() || '',
      permission: query.value.permission?.trim() || '',
      enabled: query.value.enabled
    }
    if (query.value.parentId !== undefined && query.value.parentId !== null) {
      params.parentId = query.value.parentId
    }
    const res = await menuList(params)
    // 验证响应数据
    if (res && Array.isArray(res)) {
      tableData.value = res.map(item => {
        const obj: MenuItemEx = {
          ...item,
          expanded: false,
          _childrenLoaded: false,
          // 确保 enabled 字段有默认值
          enabled: item.enabled !== undefined ? item.enabled : true
        }
        // 只要 leaf 为 false 或 0，就加 children: []
        if (item.leaf === false || item.leaf === 0) {
          obj.children = []
        }
        return obj
      })
    } else {
      tableData.value = []
    }
    // 数据刷新后重置展开状态，避免图标错乱
    expandedRowKeys.value = []
  } catch (error) {
    console.error('加载菜单数据失败:', error)
    tableData.value = []
    message.error('加载菜单数据失败')
  } finally {
    loading.value = false
  }
}

// 查询
function handleSearch() {
  try {
    loadData()
  } catch (error) {
    console.warn('handleSearch error:', error)
  }
}
const throttledSearch = handleSearch

// 重置
function handleReset() {
  try {
    query.value = { name: '', title: '', permission: '', enabled: undefined, parentId: null }
    loadData()
  } catch (error) {
    console.warn('handleReset error:', error)
  }
}
const throttledReset = handleReset

// 新建
function handleCreate() {
  try {
    drawerMode.value = 'create'
    currentMenu.value = {
      name: '',
      title: '',
      sort: 0,
      showIcon: true,
      hidden: false,
      keepAlive: false,
      icon: '',
      url: '',
      component: '',
      redirect: '',
      permission: '',
      parentId: null
    }
    parentMenu.value = null
    drawerVisible.value = true
  } catch (error) {
    console.warn('handleCreate error:', error)
  }
}
const throttledCreate = handleCreate

// 刷新动画状态
const refreshing = ref(false)
async function handleRefresh() {
  try {
    refreshing.value = true
    loading.value = true
    await loadData().catch((error) => {
      console.error('刷新数据失败:', error)
    }).finally(() => {
      setTimeout(() => {
        refreshing.value = false
      }, 1000)
      loading.value = false
    })
  } catch (error) {
    console.warn('handleRefresh error:', error)
    refreshing.value = false
    loading.value = false
  }
}
const throttledRefresh = handleRefresh

// 清除选择
function clearSelection() {
  try {
    selectedRowKeys.value = []
  } catch (error) {
    console.warn('clearSelection error:', error)
  }
}

// 批量删除
function handleBatchDelete() {
  try {
    if (selectedRowKeys.value.length === 0) {
      message.warning('请先选择要删除的菜单')
      return
    }
    Modal.confirm({
      title: '确认批量删除',
      content: `确定要删除选中的 ${selectedRowKeys.value.length} 个菜单吗？`,
      okText: '确认',
      cancelText: '取消',
      onOk: () => {
        return batchDeleteMenus(selectedRowKeys.value)
          .then(() => {
            message.success('批量删除成功')
            selectedRowKeys.value = []
            loadData()
          })
          .catch((error: any) => {
            // 优先使用 Problem 格式的 detail，否则使用 error.message
            const errorMessage = (error as any)?.errorInfo?.message || error?.message || '未知错误'
            message.error('批量删除失败: ' + errorMessage)
            return Promise.reject(error)
          })
      }
    })
  } catch (error) {
    console.warn('handleBatchDelete error:', error)
  }
}
const throttledBatchDelete = handleBatchDelete

// 单条删除
function handleDelete(record: any) {
  try {
    if (!record || !record.title) {
      message.warning('无效的菜单数据')
      return
    }

    Modal.confirm({
      title: '确认删除',
      content: `确定要删除菜单 ${record.title} 吗？`,
      okText: '确认',
      cancelText: '取消',
      onOk: () => {
        return deleteMenu(record.id).then(() => {
          message.success('删除成功')
          loadData()
        }).catch((error: any) => {
          message.error('删除菜单失败: ' + (error.message || '未知错误'))
          return Promise.reject(error)
        })
      }
    })
  } catch (error) {
    console.warn('handleDelete error:', error)
  }
}
const throttledDelete = handleDelete

// 编辑
function handleEdit(record: any) {
  try {
    if (!record || !record.id) {
      message.warning('无效的菜单数据')
      return
    }

    console.log('编辑菜单，原始数据:', record)

    drawerMode.value = 'edit'
    // 深拷贝数据，避免直接引用
    currentMenu.value = {
      ...record,
      id: record.id,
      name: record.name || '',
      title: record.title || '',
      url: record.url || '',
      icon: record.icon || '',
      showIcon: Boolean(record.showIcon),
      sort: Number(record.sort) || 0,
      component: record.component || '',
      redirect: record.redirect || '',
      hidden: Boolean(record.hidden),
      keepAlive: Boolean(record.keepAlive),
      permission: record.permission || '',
      parentId: record.parentId || null
    }

    console.log('编辑菜单，处理后的数据:', currentMenu.value)
    if (currentMenu.value) {
      console.log('父级菜单ID:', currentMenu.value.parentId)
    }

    parentMenu.value = null
    drawerVisible.value = true
  } catch (error) {
    console.error('编辑菜单失败:', error)
    message.error('编辑菜单失败')
  }
}
const throttledEdit = handleEdit

// 添加子菜单
function handleAddChild(record: any) {
  try {
    if (!record || !record.id) {
      message.warning('无效的父级菜单数据')
      return
    }

    drawerMode.value = 'create'
    currentMenu.value = {
      name: '',
      title: '',
      sort: 0,
      showIcon: true,
      hidden: false,
      keepAlive: false,
      icon: '',
      url: '',
      component: '',
      redirect: '',
      permission: '',
      parentId: null
    }
    parentMenu.value = { ...record }
    drawerVisible.value = true
  } catch (error) {
    console.error('添加子菜单失败:', error)
    message.error('添加子菜单失败')
  }
}
const throttledAddChild = handleAddChild

// 抽屉关闭
function handleDrawerClose() {
  try {
    drawerVisible.value = false
    // 延迟清理数据，确保抽屉完全关闭
    setTimeout(() => {
      currentMenu.value = null
      parentMenu.value = null
    }, 300)
  } catch (error) {
    console.warn('抽屉关闭错误:', error)
  }
}

// 保存（新建/编辑）
async function handleFormSubmit(formData: any) {
  try {
    // 验证表单数据
    if (!formData || typeof formData !== 'object') {
      message.error('无效的表单数据')
      return
    }

    if (!formData.name || !formData.title) {
      message.error('请填写必填字段')
      return
    }

    // 构建提交数据，确保包含所有必要字段
    const submitData = {
      ...formData,
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
      // 根据是否有组件路径判断菜单类型
      type: formData.component ? 1 : 0, // 0-目录，1-菜单
      uri: formData.url || '',
      method: 'GET'
    }

    if (drawerMode.value === 'edit' && formData.id) {
      await updateMenu(formData.id, submitData)
      message.success('更新成功')
    } else {
      await createMenu(submitData)
      message.success('创建成功')
    }

    handleDrawerClose()
    await loadData() // 等待数据加载完成
    // 通知侧边栏刷新菜单树，实现局部重载
    if (typeof window !== 'undefined') {
      window.dispatchEvent(new CustomEvent('reload-menu-tree'))
    }
  } catch (error: any) {
    console.error('保存菜单失败:', error)
    message.error('保存失败: ' + (error.message || '未知错误'))
  }
}

// 生命周期钩子
onMounted(() => {
  try {
    loadData()
    updateTableBodyHeight()
    window.addEventListener('resize', updateTableBodyHeight)
  } catch (error) {
    console.warn('Menu onMounted error:', error)
  }
})

onBeforeUnmount(() => {
  try {
    // 清理事件监听器
    window.removeEventListener('resize', updateTableBodyHeight)

    // 清理响应式数据，避免卸载时访问已销毁的数据
    selectedRowKeys.value = []
    expandedRowKeys.value = []
    tableData.value = []

    // 清理抽屉相关数据
    drawerVisible.value = false
    currentMenu.value = null
    parentMenu.value = null

    // 清理加载状态
    loading.value = false
    refreshing.value = false
  } catch (error) {
    console.warn('Menu onBeforeUnmount error:', error)
  }
})

// 抽屉相关
const drawerVisible = ref(false)
const drawerMode = ref<'create' | 'edit'>('edit')
const currentMenu = ref<MenuItemEx | null>(null)
const parentMenu = ref<MenuItemEx | null>(null)

// 行点击事件
function onCustomRow(record: any) {
  return {
    onClick: (event: MouseEvent) => {
      try {
        if ((event.target as HTMLElement).closest('.ant-checkbox-wrapper')) return
        const recordId = String(record.id)
        const isSelected = selectedRowKeys.value.includes(recordId)
        if (isSelected && selectedRowKeys.value.length === 1) {
          selectedRowKeys.value = []
        } else {
          selectedRowKeys.value = [recordId]
        }
      } catch (error) {
        console.warn('onCustomRow onClick error:', error)
      }
    }
  }
}

function getRowClassName(record: any) {
  try {
    if (selectedRowKeys.value.includes(String(record.id))) {
      return 'checkbox-selected-row'
    }
    return ''
  } catch (error) {
    console.warn('getRowClassName error:', error)
    return ''
  }
}

// 获取类型颜色
function getTypeColor(type: number) {
  try {
    const colorMap: Record<number, string> = {
      0: 'blue',    // 目录
      1: 'green',   // 菜单
      2: 'orange'   // 按钮
    }
    return colorMap[type] || 'default'
  } catch (error) {
    console.warn('getTypeColor error:', error)
    return 'default'
  }
}

// 获取类型文本
function getTypeText(type: number) {
  try {
    const textMap: Record<number, string> = {
      0: '目录',
      1: '菜单',
      2: '按钮'
    }
    return textMap[type] || '未知'
  } catch (error) {
    console.warn('getTypeText error:', error)
    return '未知'
  }
}

// 递归移除所有子节点
function collapseChildren(record: any) {
  try {
    const parentId = record.id;
    // 收集所有要删除的 id
    const idsToDelete: any[] = [];
    function collectIds(id: any) {
      tableData.value.forEach(item => {
        if (item.parentId === id) {
          idsToDelete.push(item.id);
          collectIds(item.id);
        }
      });
    }
    collectIds(parentId);
    tableData.value = tableData.value.filter(item => !idsToDelete.includes(item.id));
  } catch (error) {
    console.warn('collapseChildren error:', error);
  }
}

// 新增的 handleExpand 函数
async function handleExpand(expanded: boolean, record: any) {
  console.log('[菜单调试] handleExpand 触发', { expanded, record });
  if (expanded) {
    // 展开逻辑
    try {
      const childMenus = await getMenusByParentId(record.id);
      console.log('[菜单调试] getMenusByParentId 返回', childMenus);
      if (childMenus && Array.isArray(childMenus) && childMenus.length > 0) {
        const childMenusArray = childMenus as MenuItemEx[];
        childMenusArray.forEach(item => {
          item.expanded = false;
          item._childrenLoaded = false;
          item._loading = false;
          if (item.leaf === false || item.leaf === 0) {
            item.children = [];
          }
        });
        record._childrenLoaded = true;
        const parentIndex = tableData.value.findIndex(item => item.id === record.id);
        if (parentIndex !== -1) {
          const newData = [
            ...tableData.value.slice(0, parentIndex + 1),
            ...childMenusArray,
            ...tableData.value.slice(parentIndex + 1)
          ];
          tableData.value = newData;
        }
      } else {
        console.log('[菜单调试] 没有子菜单数据');
      }
    } catch (err) {
      console.error('[菜单调试] getMenusByParentId 请求异常', err);
    }
  } else {
    // 收起逻辑
    console.log('[菜单调试] 进入收起分支', record.id);
    collapseChildren(record);
    record._childrenLoaded = false;
  }
}

// 新增的 onExpandIconClick 方法
function onExpandIconClick(record: any) {
  const key = String(record.id);
  const isExpanded = expandedRowKeys.value.includes(key);
  if (!isExpanded) {
    expandedRowKeys.value.push(key);
  } else {
    expandedRowKeys.value = expandedRowKeys.value.filter(k => k !== key);
  }
  handleExpand(!isExpanded, record);
}
</script>

<style scoped>
/* 复用用户管理页面样式，保证风格一致 */
.content-container {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #fff;
}

.content-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.form-container {
  padding: 24px;
  border-bottom: 1px solid #f0f0f0;
  background: transparent;
  border-radius: 0;
  box-shadow: none;
}

.toolbar-container {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #f0f0f0;
  background: transparent;
  border-radius: 0;
  box-shadow: none;
  padding: 8px 24px 8px 24px;
}

.table-container {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}

.table-scroll-container {
  min-height: 0;
  overflow-x: auto;
  overflow-y: auto;
}

.ml-2 {
  margin-left: 8px;
}

.table-title {
  font-size: 16px;
  font-weight: bold;
  color: #222;
}

.table-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.batch-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  background: none;
  border: none;
  border-radius: 0;
  padding: 0;
  margin-right: 0;
}

.toolbar-btn {
  border-radius: 4px;
  height: 32px;
  padding: 0 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.action-icon {
  font-size: 18px;
  cursor: pointer;
  color: #595959;
  border-radius: 4px;
  padding: 8px;
  transition: color 0.2s, background 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 32px;
  min-height: 32px;
}

.action-icon:hover {
  color: #1890ff;
  background: #f5f5f5;
}

.action-icon.active {
  color: #1890ff;
  background: #e6f7ff;
}

.draggable-columns {
  max-height: 300px;
  overflow-y: auto;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.draggable-columns::-webkit-scrollbar {
  display: none;
}

.draggable-column-item {
  display: flex;
  align-items: center;
  padding: 4px 2px;
  margin-bottom: 4px;
  background: transparent;
  border-radius: 4px;
  transition: background-color 0.2s ease;
  cursor: default;
}

.draggable-column-item:hover {
  background-color: #f5f5f5;
}

.draggable-column-item.sortable-ghost {
  opacity: 0.5;
  background: #e6f7ff;
}

.draggable-column-item.sortable-chosen {
  background: #e6f7ff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.drag-handle {
  margin-right: 8px;
  color: #bfbfbf;
  font-size: 16px;
  cursor: move;
  transition: color 0.2s;
}

.drag-handle:hover {
  color: #1890ff;
}

.sortable-ghost .drag-handle {
  color: #1890ff;
}

.action-buttons {
  display: flex;
  align-items: center;
  gap: 4px;
  justify-content: center;
}

.action-btn {
  padding: 2px 4px;
  height: auto;
  line-height: 1.2;
  font-size: 12px;
}

.action-btn:hover {
  background-color: #f5f5f5;
  border-radius: 4px;
}

.icon-cell {
  display: flex;
  align-items: center;
  justify-content: center;
}

.menu-icon {
  font-size: 16px;
  color: #1890ff;
}

.no-icon {
  color: #bfbfbf;
}

/* 复用user/index.vue的隔行换色和高亮样式 */
:deep(.ant-table-tbody > tr:nth-child(odd)) {
  background-color: #fafbfc;
}

:deep(.ant-table-tbody > tr:nth-child(even)) {
  background-color: #fff;
}

:deep(.ant-table-tbody > tr.checkbox-selected-row) {
  background-color: #e6f7ff !important;
}

:deep(.ant-table-tbody > tr.checkbox-selected-row:hover) {
  background-color: #bae7ff !important;
}

:deep(.ant-table-thead th) {
  white-space: nowrap;
}

:deep(.ant-table-cell) {
  white-space: nowrap;
  /* overflow: hidden; */
  /* text-overflow: ellipsis; */
}
</style>
