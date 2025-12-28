<template>
    <div class="content-container" style="position: relative;">
        <div class="content-card">
            <div class="form-container">
                <a-form layout="inline" :model="query">
                    <a-form-item label="流程名称">
                        <a-input v-model:value="query.name" placeholder="请输入流程名称" />
                    </a-form-item>
                    <a-form-item label="流程Key">
                        <a-input v-model:value="query.key" placeholder="请输入流程Key" />
                    </a-form-item>
                    <a-form-item label="租户">
                        <a-select v-model:value="query.tenantId" placeholder="选择租户" allow-clear style="width: 150px">
                            <a-select-option value="">全部租户</a-select-option>
                            <a-select-option v-for="tenant in tenants" :key="tenant.id" :value="tenant.id">
                                {{ tenant.name }}
                            </a-select-option>
                        </a-select>
                    </a-form-item>
                    <a-form-item>
                        <a-button type="primary" @click="throttledSearch">搜索</a-button>
                        <a-button class="ml-2" @click="throttledReset">重置</a-button>
                    </a-form-item>
                </a-form>
            </div>

            <div class="toolbar-container">
                <div class="table-title">
                    流程定义列表
                </div>
                <div class="table-actions">
                    <div v-if="selectedRowKeys.length > 0" class="batch-actions">
                        <a-button type="primary" danger @click="throttledBatchDelete" class="toolbar-btn">
                            <template #icon>
                                <DeleteOutlined />
                            </template>
                            批量删除 ({{ selectedRowKeys.length }})
                        </a-button>
                        <a-button @click="clearSelection" class="toolbar-btn">
                            <template #icon>
                                <CloseOutlined />
                            </template>
                            取消选择
                        </a-button>
                    </div>

                    <a-button type="link" @click="goToDesigner" class="toolbar-btn">
                        <template #icon>
                            <PlusOutlined />
                        </template>
                        新建流程
                    </a-button>
                    <a-tooltip title="刷新">
                        <span class="action-icon" @click="throttledRefresh">
                            <ReloadOutlined :spin="refreshing" />
                        </span>
                    </a-tooltip>
                    <a-tooltip :title="showSortTooltip ? '关闭排序提示' : '开启排序提示'">
                        <PoweroffOutlined :class="['action-icon', { active: showSortTooltip }]"
                            @click="showSortTooltip = !showSortTooltip" />
                    </a-tooltip>
                    <a-popover placement="bottomRight" trigger="click" :destroyTooltipOnHide="false">
                        <template #content>
                            <div
                                style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px;">
                                <div style="display: flex; align-items: center;">
                                    <a-checkbox :checked="showColumnKeys.length === allColumns.length"
                                        :indeterminate="showColumnKeys.length > 0 && showColumnKeys.length < allColumns.length"
                                        @change="(e: any) => onCheckAllChange(e)" />
                                    <span style="font-weight: bold; margin-left: 8px;">列展示/排序</span>
                                </div>
                                <span style="font-weight: bold; color: #1677ff; cursor: pointer;"
                                    @click="resetColumnOrder">
                                    重置
                                </span>
                            </div>
                            <VueDraggable v-model="draggableColumns"
                                :item-key="(item: any) => item?.dataIndex || 'col_' + Math.random()"
                                handle=".drag-handle" @end="onDragEnd" class="draggable-columns"
                                ghost-class="sortable-ghost" chosen-class="sortable-chosen" tag="div">
                                <template #item="{ element: col }">
                                    <div class="draggable-column-item">
                                        <HolderOutlined class="drag-handle" />
                                        <a-checkbox :checked="showColumnKeys.includes(col.dataIndex)"
                                            @change="(e: any) => onCheckboxChange(col.dataIndex, e.target.checked)">
                                            {{ col.title }}
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
            <div class="table-container" ref="tableContentRef">
                <div class="table-scroll-container" ref="tableScrollContainerRef">
                    <a-table :columns="columns" :data-source="tableData" :pagination="false"
                        :row-key="(record: any) => String(record.id)" bordered :loading="loading"
                        @change="handleTableChange" :row-selection="rowSelection" :custom-row="onCustomRow"
                        :row-class-name="getRowClassName" :scroll="{ x: 1500, y: tableBodyHeight }"
                        :locale="tableLocale" :show-sorter-tooltip="showSortTooltip">
                        <template #bodyCell="{ column, record }">
                            <template v-if="column.dataIndex === 'name'">
                                <a-typography-text strong>{{ record.name }}</a-typography-text>
                                <br>
                                <a-typography-text type="secondary" style="font-size: 12px">
                                    {{ record.description || '无描述' }}
                                </a-typography-text>
                            </template>
                            <template v-else-if="column.dataIndex === 'key'">
                                <a-typography-text code>{{ record.key }}</a-typography-text>
                            </template>
                            <template v-else-if="column.dataIndex === 'version'">
                                <a-tag color="blue">v{{ record.version }}</a-tag>
                            </template>
                            <template v-else-if="column.dataIndex === 'suspended'">
                                <a-tag :color="record.suspended ? 'red' : 'green'">
                                    {{ record.suspended ? '已暂停' : '活跃' }}
                                </a-tag>
                            </template>
                            <template v-else-if="column.dataIndex === 'created'">
                                {{ formatDate(record.created) }}
                            </template>
                            <template v-else-if="column.dataIndex === 'tenantId'">
                                <a-tag v-if="record.tenantId" color="green">{{ record.tenantId }}</a-tag>
                                <a-tag v-else color="default">默认</a-tag>
                            </template>
                            <template v-else-if="column.dataIndex === 'action'">
                                <div class="action-buttons">
                                    <a-button type="link" size="small" @click.stop="throttledView(record)"
                                        class="action-btn">
                                        <template #icon>
                                            <EyeOutlined />
                                        </template>
                                        查看
                                    </a-button>
                                    <a-button type="link" size="small" @click.stop="throttledPreview(record)"
                                        class="action-btn">
                                        <template #icon>
                                            <FileImageOutlined />
                                        </template>
                                        预览
                                    </a-button>
                                    <a-button type="link" size="small" @click.stop="throttledEdit(record)"
                                        class="action-btn">
                                        <template #icon>
                                            <EditOutlined />
                                        </template>
                                        编辑
                                    </a-button>
                                    <a-button type="link" size="small" @click.stop="throttledStart(record)"
                                        class="action-btn">
                                        <template #icon>
                                            <PlayCircleOutlined />
                                        </template>
                                        启动
                                    </a-button>
                                    <a-button type="link" size="small" @click.stop="throttledToggleSuspend(record)"
                                        class="action-btn">
                                        <template #icon>
                                            <PauseCircleOutlined v-if="!record.suspended" />
                                            <PlayCircleOutlined v-else />
                                        </template>
                                        {{ record.suspended ? '激活' : '暂停' }}
                                    </a-button>
                                </div>
                            </template>
                        </template>
                    </a-table>
                </div>
                <div class="pagination-container" ref="paginationRef">
                    <a-pagination v-model:current="pagination.current" :page-size="pagination.pageSize"
                        :total="pagination.total" :show-size-changer="pagination.showSizeChanger"
                        :page-size-options="paginationConfig.pageSizeOptions" :show-total="pagination.showTotal"
                        @change="handlePageChange" @showSizeChange="handlePageSizeChange"
                        :locale="{ items_per_page: '条/页' }" />
                </div>
            </div>
        </div>

        <!-- 流程保存结果弹窗（组件化） -->
        <ProcessDeployResultModal v-model:open="showSaveResult"
            :result="{ deploymentId: processInfo.deploymentId, deploymentName: processInfo.name, description: processInfo.description }"
            @go-deployment="goToManagement" @go-definition="goToDesigner" />

        <!-- 查看流程详情抽屉 -->
        <a-drawer v-model:open="viewOpen" title="流程详情" width="50%" :destroyOnClose="true" :zIndex="1200">
            <a-descriptions bordered :column="1" size="middle">
                <a-descriptions-item label="流程名称">
                    {{ viewRecord?.name || '-' }}
                </a-descriptions-item>
                <a-descriptions-item label="流程Key">
                    <a-typography-text code>{{ viewRecord?.key || '-' }}</a-typography-text>
                </a-descriptions-item>
                <a-descriptions-item label="版本">
                    <a-tag color="blue" v-if="viewRecord?.version">v{{ viewRecord?.version }}</a-tag>
                    <span v-else>-</span>
                </a-descriptions-item>
                <a-descriptions-item label="状态">
                    <a-tag :color="viewRecord?.suspended ? 'red' : 'green'">
                        {{ viewRecord?.suspended ? '已暂停' : '活跃' }}
                    </a-tag>
                </a-descriptions-item>
                <a-descriptions-item label="租户">
                    <a-tag v-if="viewRecord?.tenantId" color="green">{{ viewRecord?.tenantId }}</a-tag>
                    <a-tag v-else color="default">默认</a-tag>
                </a-descriptions-item>
                <a-descriptions-item label="创建时间">
                    {{ viewRecord?.created ? formatDate(viewRecord.created) : '-' }}
                </a-descriptions-item>
                <a-descriptions-item label="部署ID">
                    <a-typography-text code>{{ viewRecord?.deploymentId || '-' }}</a-typography-text>
                </a-descriptions-item>
                <a-descriptions-item label="描述">
                    {{ viewRecord?.description || '无描述' }}
                </a-descriptions-item>
            </a-descriptions>
            <template #footer>
                <a-space>
                    <a-button type="primary" @click="() => viewRecord && throttledPreview(viewRecord)">预览流程</a-button>
                    <a-button @click="closeView">关闭</a-button>
                </a-space>
            </template>
        </a-drawer>

        <!-- 流程预览抽屉组件 -->
        <ProcessPreviewDrawer v-model:open="previewOpen" :record="previewRecord" :zIndex="1300" width="70%"
            @start="startProcess" @close="() => { /* no-op */ }" />
    </div>
</template>

<script setup lang="ts" name="ProcessDefinition">
import { ref, computed, onMounted, watch, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import {
    PlusOutlined,
    ReloadOutlined,
    EyeOutlined,
    EditOutlined,
    PlayCircleOutlined,
    PauseCircleOutlined,
    DeleteOutlined,
    SettingOutlined,
    HolderOutlined,
    CloseOutlined,
    PoweroffOutlined,
    FileImageOutlined
} from '@ant-design/icons-vue'
import VueDraggable from 'vuedraggable'
import { processApi, deploymentApi, tenantApi } from '@/api/process'
import type { ProcessDefinition } from '@/api/process'
import { useThrottle } from '@/utils/debounce'
import ProcessPreviewDrawer from '@/components/process/ProcessPreviewDrawer.vue'
import ProcessDeployResultModal from '@/components/process/ProcessDeployResultModal.vue'

const router = useRouter()

// 查询条件
const query = ref({
    name: '',
    key: '',
    tenantId: ''
})

const tableData = ref<any[]>([])
const loading = ref(false)
const refreshing = ref(false)
const selectedRowKeys = ref<string[]>([])
const showSaveResult = ref(false)
const showSortTooltip = ref(true)

const tenants = ref<Array<{ id: string; name: string }>>([])

// 流程信息
const processInfo = ref({
    name: '',
    key: '',
    description: '',
    deploymentId: ''
})

// 查看详情相关状态
const viewOpen = ref(false)
const viewRecord = ref<any | null>(null)

// 预览相关状态
const previewOpen = ref(false)
const previewRecord = ref<ProcessDefinition | null>(null)
// 由子组件负责渲染与销毁预览视图

// 后续操作列表
const nextActions = ref([
    {
        icon: '🚀',
        color: '#52c41a',
        title: '启动流程实例',
        description: '立即启动一个流程实例进行测试',
        action: () => startProcessInstance()
    },
    {
        icon: '👥',
        color: '#1890ff',
        title: '分配任务处理人',
        description: '为流程中的用户任务分配具体的处理人',
        action: () => assignTaskUsers()
    },
    {
        icon: '📊',
        color: '#722ed1',
        title: '查看流程监控',
        description: '监控流程实例的执行状态和性能指标',
        action: () => viewProcessMonitoring()
    },
    {
        icon: '⚙️',
        color: '#fa8c16',
        title: '配置流程参数',
        description: '设置流程的全局参数和业务规则',
        action: () => configureProcessParams()
    }
])

// 分页配置
const pagination = ref({
    current: 1,
    pageSize: 10,
    showSizeChanger: true,
    pageSizeOptions: ['10', '20', '30', '40', '50'],
    total: 0,
    showTotal: (total: number) => `共 ${total} 条`
})

const paginationConfig = computed(() => {
    const config = {
        current: Number(pagination.value.current) || 1,
        pageSize: Number(pagination.value.pageSize) || 10,
        showSizeChanger: pagination.value.showSizeChanger,
        pageSizeOptions: pagination.value.pageSizeOptions,
        total: Number(pagination.value.total) || 0,
        showTotal: pagination.value.showTotal
    }
    return config
})

// 定义初始列顺序常量
const INITIAL_COLUMNS = [
    { title: 'ID', dataIndex: 'id', sorter: true, width: 80 },
    { title: '流程名称', dataIndex: 'name', sorter: true, width: 200 },
    { title: '流程Key', dataIndex: 'key', sorter: true, width: 150 },
    { title: '版本', dataIndex: 'version', sorter: true, width: 80 },
    { title: '状态', dataIndex: 'suspended', sorter: true, width: 100 },
    { title: '创建时间', dataIndex: 'created', sorter: true, width: 150 },
    { title: '租户', dataIndex: 'tenantId', width: 100 },
    {
        title: '操作',
        dataIndex: 'action',
        width: 250,
        fixed: 'right',
        align: 'center'
    }
]

// 用初始列顺序初始化
const allColumns = ref([...INITIAL_COLUMNS])
const draggableColumns = ref([...INITIAL_COLUMNS])
const showColumnKeys = ref(
    INITIAL_COLUMNS.map(col => col.dataIndex).filter(key => typeof key === 'string' && key)
)

watch(allColumns, (val) => {
    showColumnKeys.value = showColumnKeys.value.filter(key =>
        val.some(col => col.dataIndex === key)
    );
});

watch(draggableColumns, (val) => {
    allColumns.value = val.filter(col => col && typeof col.dataIndex === 'string')
    showColumnKeys.value = showColumnKeys.value.filter(key =>
        allColumns.value.some(col => col.dataIndex === key)
    )
})

function onCheckboxChange(dataIndex: string, checked: boolean) {
    if (!dataIndex || typeof dataIndex !== 'string') {
        return
    }
    if (checked) {
        if (!showColumnKeys.value.includes(dataIndex)) {
            showColumnKeys.value.push(dataIndex)
        }
    } else {
        showColumnKeys.value = showColumnKeys.value.filter(key => key !== dataIndex)
    }
}

function onCheckAllChange(e: any) {
    if (e.target.checked) {
        showColumnKeys.value = INITIAL_COLUMNS.map(col => col.dataIndex)
    } else {
        showColumnKeys.value = []
    }
}

function resetColumnOrder() {
    allColumns.value = [...INITIAL_COLUMNS]
    draggableColumns.value = [...INITIAL_COLUMNS]
    showColumnKeys.value = INITIAL_COLUMNS
        .filter(col => typeof col.dataIndex === 'string' && col.dataIndex)
        .map(col => col.dataIndex)
}

function onDragEnd(event: any) {
    console.log('拖拽结束，新顺序:', draggableColumns.value.map(col => col.title))
}

const columns = computed(() => {
    const filtered = allColumns.value.filter(
        col =>
            col &&
            typeof col.dataIndex === 'string' &&
            col.dataIndex &&
            showColumnKeys.value.includes(col.dataIndex)
    )
    return [
        {
            title: '序号',
            dataIndex: 'index',
            width: 80,
            align: 'center',
            fixed: 'left',
            customRender: ({ index }: { index?: number }) => {
                const safeIndex = typeof index === 'number' && !isNaN(index) ? index : 0
                const current = Number(pagination.value.current) || 1
                const pageSize = Number(pagination.value.pageSize) || 10
                return (current - 1) * pageSize + safeIndex + 1
            }
        },
        ...filtered
    ]
})

const rowSelection = computed(() => ({
    selectedRowKeys: selectedRowKeys.value,
    onChange: (selectedKeys: string[], selectedRows: any[]) => {
        selectedRowKeys.value = selectedKeys;
    },
    checkStrictly: false,
    preserveSelectedRowKeys: true,
    fixed: true
}))

// 表格相关引用
const tableContentRef = ref<HTMLElement | null>(null)
const tableScrollContainerRef = ref<HTMLElement | null>(null)
const paginationRef = ref<HTMLElement | null>(null)
const tableBodyHeight = ref(400)

// 表格 locale 配置的计算属性
const tableLocale = computed(() => {
    if (showSortTooltip.value) {
        return {
            triggerDesc: '点击降序',
            triggerAsc: '点击升序',
            cancelSort: '取消排序'
        }
    }
    return undefined
})

// 方法
async function loadData() {
    loading.value = true
    try {
        const params = {
            name: query.value.name.trim(),
            key: query.value.key.trim(),
            tenantId: query.value.tenantId || undefined,
            current: Number(pagination.value.current) || 1,
            pageSize: Number(pagination.value.pageSize) || 10
        }
        const result = await processApi.getProcessDefinitions(params.tenantId)
        tableData.value = Array.isArray(result) ? result : []
        pagination.value.total = tableData.value.length
    } catch (error: unknown) {
        console.error('加载流程定义失败:', error)
        const errorMessage = error instanceof Error ? error.message : '未知错误'
        message.error('加载流程定义失败：' + errorMessage)
        tableData.value = []
        pagination.value.total = 0
    } finally {
        loading.value = false
    }
}

const loadTenants = async () => {
    try {
        const result = await tenantApi.getTenants()
        tenants.value = Array.isArray(result) ? result : []
    } catch (error: unknown) {
        console.error('加载租户列表失败:', error)
        // 租户加载失败不影响主功能
    }
}

function handleSearch() {
    pagination.value.current = 1
    loadData()
}

const throttledSearch = useThrottle(handleSearch, 1000)

function handleReset() {
    query.value.name = ''
    query.value.key = ''
    query.value.tenantId = ''
    pagination.value.current = 1
    loadData()
}

const throttledReset = useThrottle(handleReset, 1000)

function handleTableChange(pag: any, filters: any, sorter: any) {
    if (pag && typeof pag.current === 'number') {
        pagination.value.current = pag.current
    }
    if (pag && typeof pag.pageSize === 'number') {
        pagination.value.pageSize = pag.pageSize
    }
    loadData()
}

function handlePageChange(page: number) {
    pagination.value.current = page || 1
    loadData()
}

function handlePageSizeChange(current: number, size: number) {
    pagination.value.pageSize = size || 10
    pagination.value.current = 1
    loadData()
}

function clearSelection() {
    selectedRowKeys.value = []
}

function getRowClassName(record: any) {
    if (selectedRowKeys.value.includes(record.id)) {
        return 'checkbox-selected-row'
    }
    return ''
}

function onCustomRow(record: any) {
    return {
        onClick: (event: MouseEvent) => {
            if ((event.target as HTMLElement).closest('.ant-checkbox-wrapper')) return;

            const recordId = String(record.id)
            const isSelected = selectedRowKeys.value.includes(recordId)

            if (isSelected && selectedRowKeys.value.length === 1) {
                selectedRowKeys.value = []
            } else {
                selectedRowKeys.value = [recordId]
            }
        }
    }
}

async function handleRefresh() {
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
}

const throttledRefresh = useThrottle(handleRefresh, 1000)

function handleBatchDelete() {
    if (selectedRowKeys.value.length === 0) {
        message.warning('请先选择要删除的流程定义')
        return
    }
    Modal.confirm({
        title: '确认批量删除',
        content: `确定要删除选中的 ${selectedRowKeys.value.length} 个流程定义吗？`,
        okText: '确认',
        cancelText: '取消',
        onOk: () => {
            return Promise.all(
                selectedRowKeys.value.map(id =>
                    deploymentApi.deleteDeployment(id).catch(error => {
                        console.error(`删除流程定义 ${id} 失败:`, error)
                        throw error
                    })
                )
            ).then(() => {
                message.success('批量删除成功')
                selectedRowKeys.value = []
                loadData()
            }).catch((error: any) => {
                // 优先使用 Problem 格式的 detail，否则使用 error.message
            const errorMessage = (error as any)?.errorInfo?.message || error?.message || '未知错误'
            message.error('批量删除失败: ' + errorMessage)
                return Promise.reject(error)
            })
        }
    })
}

const throttledBatchDelete = useThrottle(handleBatchDelete, 1000)

function handleView(record: any) {
    viewRecord.value = record || null
    viewOpen.value = true
}

function closeView() {
    viewOpen.value = false
    viewRecord.value = null
}

const throttledView = useThrottle(handleView, 500)

async function handlePreview(record: any) {
    previewRecord.value = (record as ProcessDefinition) || null
    previewOpen.value = true
}

// 已改为在详情抽屉之上直接打开预览抽屉，无需中转函数

const throttledPreview = useThrottle(handlePreview, 500)

function handleEdit(record: any) {
    message.info(`编辑流程：${record.name}`)
    // TODO: 实现流程编辑功能
}

const throttledEdit = useThrottle(handleEdit, 500)

function handleStart(record: any) {
    startProcess(record)
}

const throttledStart = useThrottle(handleStart, 500)

function handleDelete(record: any) {
    Modal.confirm({
        title: '确认删除',
        content: `确定要删除流程定义 ${record.name} 吗？`,
        okText: '确认',
        cancelText: '取消',
        onOk: () => {
            return deploymentApi.deleteDeployment(record.deploymentId)
                .then(() => {
                    message.success('流程定义删除成功')
                    loadData()
                })
                .catch((error: any) => {
                    message.error('删除流程定义失败: ' + (error.message || '未知错误'))
                    return Promise.reject(error)
                })
        }
    })
}

const throttledDelete = useThrottle(handleDelete, 500)

function handleToggleSuspend(record: any) {
    const action = record.suspended ? '激活' : '暂停'
    Modal.confirm({
        title: `确认${action}流程`,
        content: `确定要${action}流程定义 ${record.name} 吗？`,
        okText: '确认',
        cancelText: '取消',
        onOk: () => {
            // TODO: 实现流程暂停/激活API调用
            message.success(`流程${action}成功`)
            loadData()
        }
    })
}

const throttledToggleSuspend = useThrottle(handleToggleSuspend, 500)

/**
 * 动态计算表格内容区（body）的高度
 */
function updateTableBodyHeight() {
    nextTick(() => {
        if (tableContentRef.value && paginationRef.value) {
            const tableHeader = tableContentRef.value.querySelector('.ant-table-header') as HTMLElement;
            const containerHeight = tableContentRef.value.clientHeight;
            const paginationHeight = paginationRef.value.clientHeight;
            const tableHeaderHeight = tableHeader ? tableHeader.clientHeight : 55;
            const bodyHeight = containerHeight - paginationHeight - tableHeaderHeight;
            tableBodyHeight.value = Math.max(bodyHeight, 200);
        }
    });
}

function closePreview() {
    previewOpen.value = false
    previewRecord.value = null
}

function onPreviewAfterOpenChange(open: boolean) {
    // 预览渲染由子组件负责
}

const formatDate = (dateString: string | Date) => {
    if (!dateString) return '-'
    const date = typeof dateString === 'string' ? new Date(dateString) : dateString
    return date.toLocaleString('zh-CN')
}

const goToDesigner = () => {
    router.push('/workflowDesign')
}

const goToManagement = () => {
    showSaveResult.value = false
    router.push('/deployment')
}

const closeSaveResult = () => {
    showSaveResult.value = false
}

const viewProcess = (process: ProcessDefinition) => {
    // 查看流程定义详情
    message.info(`查看流程：${process.name}`)
    // TODO: 实现流程查看功能
}

const editProcess = (process: ProcessDefinition) => {
    // 编辑流程定义
    message.info(`编辑流程：${process.name}`)
    // TODO: 实现流程编辑功能
}

const startProcess = async (process: ProcessDefinition) => {
    try {
        const { instanceApi } = await import('@/api/process')
        const result = await instanceApi.startProcess({
            processKey: process.key,
            variables: {}
        })
        message.success(`流程实例启动成功！实例ID: ${result.instanceId}`)
    } catch (error: unknown) {
        console.error('启动流程失败:', error)
        const errorMessage = error instanceof Error ? error.message : '未知错误'
        message.error('启动流程失败：' + errorMessage)
    }
}

const deleteProcess = async (process: ProcessDefinition) => {
    try {
        await deploymentApi.deleteDeployment(process.deploymentId)
        message.success('流程删除成功')
        refreshData()
    } catch (error: unknown) {
        console.error('删除流程失败:', error)
        const errorMessage = error instanceof Error ? error.message : '未知错误'
        message.error('删除流程失败：' + errorMessage)
    }
}

const startProcessInstance = async () => {
    try {
        const { instanceApi } = await import('@/api/process')
        const result = await instanceApi.startProcess({
            processKey: processInfo.value.key,
            variables: {}
        })
        message.success(`流程实例启动成功！实例ID: ${result.instanceId}`)
    } catch (error: unknown) {
        console.error('启动流程实例失败:', error)
        const errorMessage = error instanceof Error ? error.message : '未知错误'
        message.error('启动流程实例失败：' + errorMessage)
    }
}

const previewProcess = async (process: ProcessDefinition) => {
    try {
        // 简化预览功能 - 直接跳转到新窗口显示流程信息
        const previewWindow = window.open('', '_blank', 'width=1200,height=800')
        if (previewWindow) {
            const htmlContent = '<!DOCTYPE html>' +
                '<html>' +
                '<head>' +
                '<title>流程预览</title>' +
                '<style>' +
                'body { margin: 20px; font-family: Arial, sans-serif; }' +
                '.header { margin-bottom: 20px; padding: 20px; background: #f5f5f5; border-radius: 8px; }' +
                '.header h1 { margin: 0 0 10px 0; color: #333; }' +
                '.header p { margin: 5px 0; color: #666; }' +
                '.info { background: #e6f7ff; padding: 15px; border-radius: 6px; margin: 20px 0; }' +
                '</style>' +
                '</head>' +
                '<body>' +
                '<div class="header">' +
                '<h1>' + process.name + '</h1>' +
                '<p><strong>流程Key:</strong> ' + process.key + '</p>' +
                '<p><strong>版本:</strong> ' + process.version + '</p>' +
                '<p><strong>状态:</strong> 活跃</p>' +
                '</div>' +
                '<div class="info">' +
                '<h3>流程预览功能</h3>' +
                '<p>BPMN流程图预览功能正在开发中，当前显示流程基本信息。</p>' +
                '<p>您可以在建模页面查看和编辑完整的BPMN流程图。</p>' +
                '</div>' +
                '</body>' +
                '</html>'

            previewWindow.document.write(htmlContent)
            previewWindow.document.close()
        }
    } catch (error: unknown) {
        console.error('预览流程失败:', error)
        const errorMessage = error instanceof Error ? error.message : '未知错误'
        message.error('预览流程失败：' + errorMessage)
    }
}

const assignTaskUsers = () => {
    message.info('任务分配功能开发中...')
    // TODO: 实现任务分配功能
}

const viewProcessMonitoring = () => {
    message.info('流程监控功能开发中...')
    // TODO: 实现流程监控功能
}

const configureProcessParams = () => {
    message.info('流程参数配置功能开发中...')
    // TODO: 实现流程参数配置功能
}

const refreshData = () => {
    loadData()
}

// 暴露方法供外部调用
const showProcessSaveResult = (processData: {
    name?: string
    key?: string
    description?: string
    deploymentId?: string
}) => {
    processInfo.value = {
        name: processData.name || '',
        key: processData.key || '',
        description: processData.description || '',
        deploymentId: processData.deploymentId || ''
    }
    showSaveResult.value = true
}

// 供事件监听使用的顶层处理函数，确保在挂载和卸载时均可引用
const handleShowSaveResult = (event: Event) => {
    const customEvent = event as CustomEvent
    console.log('🔍 接收到显示保存结果事件:', customEvent.detail)
    showProcessSaveResult(customEvent.detail)
}

onMounted(() => {
    loadData()
    loadTenants()
    updateTableBodyHeight()
    window.addEventListener('resize', updateTableBodyHeight)

    // 监听来自 WorkflowDesign 的保存结果事件
    window.addEventListener('showProcessSaveResult', handleShowSaveResult as EventListener)
})

onBeforeUnmount(() => {
    window.removeEventListener('resize', updateTableBodyHeight)
    window.removeEventListener('showProcessSaveResult', handleShowSaveResult as EventListener)
})

watch(() => pagination.value.pageSize, () => {
    updateTableBodyHeight()
})

// 暴露方法
defineExpose({
    showProcessSaveResult
})
</script>

<style scoped>
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
    overflow: auto;
}

.pagination-container {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    background: #fff;
    padding: 12px 24px;
    /* 上下留白，确保有足够空间垂直居中 */
    min-height: 56px;
    /* 最小高度，确保有足够的垂直空间 */
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

:deep(.ant-table-tbody > tr) {
    cursor: pointer;
    transition: background-color 0.2s ease;
    user-select: none;
}

:deep(.ant-table-tbody > tr:hover) {
    background-color: #f5f5f5 !important;
}

:deep(.ant-table-tbody > tr.checkbox-selected-row) {
    background-color: #e6f7ff !important;
}

:deep(.ant-table-tbody > tr.checkbox-selected-row:hover) {
    background-color: #bae7ff !important;
}

:deep(.ant-table-tbody > tr .ant-checkbox-wrapper) {
    pointer-events: auto;
}

:deep(.ant-table-tbody > tr td) {
    pointer-events: auto;
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

:deep(.ant-table-tbody > tr:nth-child(odd)) {
    background-color: #fafbfc;
}

:deep(.ant-table-tbody > tr:nth-child(even)) {
    background-color: #fff;
}

:deep(.ant-table-body) {
    scrollbar-width: none;
    -ms-overflow-style: none;
}

:deep(.ant-table-body::-webkit-scrollbar) {
    display: none;
}

.save-details-card {
    margin: 24px 0;
}

.next-actions-card {
    margin: 24px 0;
}

.bpmn-preview-canvas {
    height: 60vh;
    min-height: 360px;
}

:deep(.ant-result-title) {
    color: #52c41a;
    font-size: 28px;
    font-weight: 600;
}

:deep(.ant-result-subtitle) {
    color: #666;
    font-size: 16px;
}

:deep(.ant-descriptions-item-label) {
    font-weight: 600;
    color: #262626;
}

:deep(.ant-list-item-meta-title) {
    margin-bottom: 4px;
}

:deep(.ant-list-item-meta-description) {
    color: #666;
    font-size: 14px;
}
</style>
