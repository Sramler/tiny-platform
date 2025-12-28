<template>
    <div class="content-container" style="position: relative;">
        <div class="content-card">
            <div class="form-container">
                <a-form layout="inline" :model="query">
                    <a-form-item label="部署名称">
                        <a-input v-model:value="query.name" placeholder="请输入部署名称" />
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
                    部署记录列表
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

                    <a-upload :file-list="fileList" :before-upload="beforeUpload" :custom-request="handleUpload"
                        accept=".bpmn,.xml" :show-upload-list="false">
                        <a-button type="link" class="toolbar-btn">
                            <template #icon>
                                <UploadOutlined />
                            </template>
                            部署流程文件
                        </a-button>
                    </a-upload>
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
                                :item-key="(item: any) => item?.dataIndex || `col_${Math.random()}`"
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
                            <template v-if="column.dataIndex === 'deploymentTime'">
                                {{ formatDate(record.deploymentTime) }}
                            </template>
                            <template v-else-if="column.dataIndex === 'source'">
                                <a-tag :color="getSourceColor(record.source)">
                                    {{ record.source || '手动上传' }}
                                </a-tag>
                            </template>
                            <template v-else-if="column.dataIndex === 'processDefinitionCount'">
                                <a-badge :count="record.processDefinitionCount || 1"
                                    :number-style="{ backgroundColor: '#52c41a' }">
                                    <a-typography-text>个流程</a-typography-text>
                                </a-badge>
                            </template>
                            <template v-else-if="column.dataIndex === 'tenantId'">
                                <a-tag v-if="record.tenantId" color="green">{{ record.tenantId }}</a-tag>
                                <a-tag v-else color="default">默认</a-tag>
                            </template>
                            <template v-else-if="column.dataIndex === 'status'">
                                <a-tag :color="record.status === 'success' || !record.status ? 'green' : 'red'">
                                    {{ record.status === 'success' || !record.status ? '成功' : '失败' }}
                                </a-tag>
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
                                    <a-button type="link" size="small" @click.stop="throttledDownload(record)"
                                        class="action-btn">
                                        <template #icon>
                                            <DownloadOutlined />
                                        </template>
                                        下载
                                    </a-button>
                                    <a-button type="link" size="small" danger @click.stop="throttledDelete(record)"
                                        class="action-btn">
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
                <div class="pagination-container" ref="paginationRef">
                    <a-pagination v-model:current="pagination.current" :page-size="pagination.pageSize"
                        :total="pagination.total" :show-size-changer="pagination.showSizeChanger"
                        :page-size-options="paginationConfig.pageSizeOptions" :show-total="pagination.showTotal"
                        @change="handlePageChange" @showSizeChange="handlePageSizeChange"
                        :locale="{ items_per_page: '条/页' }" />
                </div>
            </div>
        </div>

        <!-- 部署详情弹窗 -->
        <a-modal v-model:open="showDeploymentDetail" title="部署详情" :footer="null" width="800px">
            <a-descriptions :column="2" bordered v-if="selectedDeployment">
                <a-descriptions-item label="部署ID">
                    <a-typography-text code>{{ selectedDeployment.id }}</a-typography-text>
                </a-descriptions-item>
                <a-descriptions-item label="部署名称">
                    {{ selectedDeployment.name }}
                </a-descriptions-item>
                <a-descriptions-item label="部署时间">
                    {{ formatDate(selectedDeployment.deploymentTime) }}
                </a-descriptions-item>
                <a-descriptions-item label="部署人">
                    {{ selectedDeployment.deployer || '-' }}
                </a-descriptions-item>
                <a-descriptions-item label="租户">
                    <a-tag v-if="selectedDeployment.tenantId" color="green">{{ selectedDeployment.tenantId }}</a-tag>
                    <a-tag v-else color="default">默认</a-tag>
                </a-descriptions-item>
                <a-descriptions-item label="状态">
                    <a-tag :color="selectedDeployment.status === 'success' ? 'green' : 'red'">
                        {{ selectedDeployment.status === 'success' ? '成功' : '失败' }}
                    </a-tag>
                </a-descriptions-item>
                <a-descriptions-item label="部署来源">
                    {{ selectedDeployment.source || '手动上传' }}
                </a-descriptions-item>
                <a-descriptions-item label="包含的流程定义" :span="2">
                    <a-list
                        v-if="selectedDeployment.processDefinitions && selectedDeployment.processDefinitions.length > 0"
                        :data-source="selectedDeployment.processDefinitions" size="small">
                        <template #renderItem="{ item }">
                            <a-list-item>
                                <a-list-item-meta>
                                    <template #title>
                                        <a-typography-text strong>{{ item.name }}</a-typography-text>
                                        <a-tag color="blue" style="margin-left: 8px;">v{{ item.version }}</a-tag>
                                    </template>
                                    <template #description>
                                        Key: {{ item.key }}
                                    </template>
                                </a-list-item-meta>
                            </a-list-item>
                        </template>
                    </a-list>
                    <a-typography-text v-else type="secondary">无</a-typography-text>
                </a-descriptions-item>
            </a-descriptions>
        </a-modal>
    </div>
</template>

<script setup lang="ts" name="Deployment">
import { ref, computed, onMounted, watch, onBeforeUnmount, nextTick } from 'vue'
import { message, Modal } from 'ant-design-vue'
import {
    UploadOutlined,
    ReloadOutlined,
    EyeOutlined,
    DownloadOutlined,
    DeleteOutlined,
    SettingOutlined,
    HolderOutlined,
    CloseOutlined,
    PoweroffOutlined
} from '@ant-design/icons-vue'
import VueDraggable from 'vuedraggable'
import { deploymentApi, tenantApi } from '@/api/process'
import type { Deployment } from '@/api/process'
import { useThrottle } from '@/utils/debounce'

// 查询条件
const query = ref({
    name: '',
    tenantId: ''
})

const tableData = ref<any[]>([])
const loading = ref(false)
const refreshing = ref(false)
const selectedRowKeys = ref<string[]>([])
const showDeploymentDetail = ref(false)
const selectedDeployment = ref<Deployment | null>(null)
const showSortTooltip = ref(true)
const fileList = ref([])

const tenants = ref<Array<{ id: string; name: string }>>([])

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
    { title: '部署名称', dataIndex: 'name', sorter: true, width: 200 },
    { title: '部署时间', dataIndex: 'deploymentTime', sorter: true, width: 150 },
    { title: '部署人', dataIndex: 'deployer', width: 120 },
    { title: '部署来源', dataIndex: 'source', width: 120 },
    { title: '包含流程数', dataIndex: 'processDefinitionCount', width: 120 },
    { title: '租户', dataIndex: 'tenantId', width: 100 },
    { title: '状态', dataIndex: 'status', width: 80 },
    {
        title: '操作',
        dataIndex: 'action',
        width: 200,
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
            tenantId: query.value.tenantId || undefined,
            current: Number(pagination.value.current) || 1,
            pageSize: Number(pagination.value.pageSize) || 10
        }
        const result = await deploymentApi.getDeployments(params.tenantId)
        tableData.value = Array.isArray(result) ? result : []
        pagination.value.total = tableData.value.length
    } catch (error: unknown) {
        console.error('加载部署记录失败:', error)
        const errorMessage = error instanceof Error ? error.message : '未知错误'
        message.error('加载部署记录失败：' + errorMessage)
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
        message.warning('请先选择要删除的部署记录')
        return
    }
    Modal.confirm({
        title: '确认批量删除',
        content: `确定要删除选中的 ${selectedRowKeys.value.length} 个部署记录吗？`,
        okText: '确认',
        cancelText: '取消',
        onOk: () => {
            return Promise.all(
                selectedRowKeys.value.map(id =>
                    deploymentApi.deleteDeployment(id, true).catch(error => {
                        console.error(`删除部署记录 ${id} 失败:`, error)
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
    selectedDeployment.value = record
    showDeploymentDetail.value = true
}

const throttledView = useThrottle(handleView, 500)

function handleDownload(record: any) {
    try {
        // 实现下载功能
        message.info('下载功能开发中...')
        // TODO: 实现部署文件下载功能
    } catch (error: any) {
        console.error('下载失败:', error)
        message.error('下载失败：' + (error.message || '未知错误'))
    }
}

const throttledDownload = useThrottle(handleDownload, 500)

function handleDelete(record: any) {
    Modal.confirm({
        title: '确认删除',
        content: `确定要删除部署记录 ${record.name} 吗？`,
        okText: '确认',
        cancelText: '取消',
        onOk: () => {
            return deploymentApi.deleteDeployment(record.id, true)
                .then(() => {
                    message.success('部署记录删除成功')
                    loadData()
                })
                .catch((error: any) => {
                    message.error('删除部署记录失败: ' + (error.message || '未知错误'))
                    return Promise.reject(error)
                })
        }
    })
}

const throttledDelete = useThrottle(handleDelete, 500)

const beforeUpload = (file: any) => {
    const isBpmn = file.type === 'application/xml' || file.name.endsWith('.bpmn') || file.name.endsWith('.xml')
    if (!isBpmn) {
        message.error('只能上传 BPMN 或 XML 文件!')
        return false
    }
    const isLt10M = file.size / 1024 / 1024 < 10
    if (!isLt10M) {
        message.error('文件大小不能超过 10MB!')
        return false
    }
    return true
}

const handleUpload = async (options: any) => {
    const { file } = options
    try {
        const formData = new FormData()
        formData.append('file', file)
        formData.append('name', file.name)
        if (query.value.tenantId) {
            formData.append('tenantId', query.value.tenantId)
        }

        const result = await deploymentApi.deployProcess(formData)
        message.success('流程部署成功!')
        loadData()
    } catch (error: any) {
        console.error('部署失败:', error)
        message.error('部署失败：' + (error.message || '未知错误'))
    }
}

const formatDate = (dateString: string | Date) => {
    if (!dateString) return '-'
    const date = typeof dateString === 'string' ? new Date(dateString) : dateString
    return date.toLocaleString('zh-CN')
}

const getSourceColor = (source: string) => {
    const sourceColors: Record<string, string> = {
        '流程建模': 'blue',
        '手动上传': 'cyan',
        'API部署': 'green',
        '定时部署': 'orange',
        'CI/CD': 'purple',
        '默认': 'default'
    }
    return sourceColors[source] || 'default'
}

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

onMounted(() => {
    loadData()
    loadTenants()
    updateTableBodyHeight()
    window.addEventListener('resize', updateTableBodyHeight)
})

onBeforeUnmount(() => {
    window.removeEventListener('resize', updateTableBodyHeight)
})

watch(() => pagination.value.pageSize, () => {
    updateTableBodyHeight()
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

:deep(.ant-descriptions-item-label) {
    font-weight: 600;
    color: #262626;
}
</style>