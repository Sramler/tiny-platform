<template>
    <div class="content-container" style="position: relative;">
        <div class="content-card">
            <div class="form-container">
                <a-form layout="inline" :model="query">
                    <a-form-item label="任务ID">
                        <a-input v-model:value="query.taskId" placeholder="请输入任务ID" allow-clear />
                    </a-form-item>
                    <a-form-item label="用户名">
                        <a-input v-model:value="query.username" placeholder="请输入用户名" allow-clear />
                    </a-form-item>
                    <a-form-item label="状态">
                        <a-select v-model:value="query.status" allow-clear placeholder="全部状态" style="width: 160px">
                            <a-select-option v-for="item in statusOptions" :key="item.value" :value="item.value">
                                {{ item.label }}
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
                    导出任务
                </div>
                <div class="table-actions">
                    <a-button type="link" @click="throttledCreate" class="toolbar-btn">
                        <template #icon>
                            <PlusOutlined />
                        </template>
                        示例
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
                                        @change="onCheckAllChange" />
                                    <span style="font-weight: bold; margin-left: 8px;">列展示/排序</span>
                                </div>
                                <span style="font-weight: bold; color: #1677ff; cursor: pointer;"
                                    @click="resetColumnOrder">
                                    重置
                                </span>
                            </div>
                            <VueDraggable v-model="draggableColumns"
                                :item-key="columnItemKey"
                                handle=".drag-handle" @end="onDragEnd" class="draggable-columns"
                                ghost-class="sortable-ghost" chosen-class="sortable-chosen" tag="div">
                                <template #item="{ element: col }">
                                    <div class="draggable-column-item">
                                        <HolderOutlined class="drag-handle" />
                                        <a-checkbox :checked="showColumnKeys.includes(col.dataIndex)"
                                            @change="onColumnCheckboxChange(col.dataIndex, $event)">
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
                    <a-table :columns="columns" :data-source="pagedTasks" :pagination="false" :row-key="rowKey" bordered
                        :loading="loading" @change="handleTableChange" :scroll="{ x: 1700, y: tableBodyHeight }"
                        :locale="tableLocale" :show-sorter-tooltip="showSortTooltip">
                        <template #bodyCell="{ column, record }">
                            <template v-if="column.dataIndex === 'status'">
                                <a-tag :color="statusColorMap[record.status] || 'default'">
                                    {{ statusLabel(record.status) }}
                                </a-tag>
                            </template>
                            <template v-else-if="column.dataIndex === 'progress'">
                                <a-progress :percent="record.progress ?? 0" size="small"
                                    :status="record.status === 'FAILED' ? 'exception' : record.status === 'SUCCESS' ? 'success' : 'active'" />
                            </template>
                            <template v-else-if="column.dataIndex === 'errorMsg'">
                                <a-tooltip :title="record.errorMsg || '-'">
                                    <span class="ellipsis-text">{{ record.errorMsg || '-' }}</span>
                                </a-tooltip>
                            </template>
                            <template v-else-if="isDateColumn(column.dataIndex)">
                                {{ formatDateTime(getRecordDateValue(record, column.dataIndex)) }}
                            </template>
                            <template v-else-if="isActionColumn(column.dataIndex)">
                                <div class="action-buttons">
                                    <a-button type="link" size="small" @click.stop="throttledView(record)"
                                        class="action-btn">
                                        <template #icon>
                                            <EyeOutlined />
                                        </template>
                                        详情
                                    </a-button>
                                    <a-button type="link" size="small" :disabled="record.status !== 'SUCCESS'"
                                        @click.stop="handleDownload(record)" class="action-btn">
                                        <template #icon>
                                            <DownloadOutlined />
                                        </template>
                                        下载
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

        <a-drawer v-model:open="drawerVisible" title="任务详情" width="50%" :get-container="false"
            :style="{ position: 'absolute' }" @close="handleDrawerClose">
            <a-descriptions v-if="currentTask" bordered :column="2" size="middle">
                <a-descriptions-item label="任务ID">{{ currentTask.taskId }}</a-descriptions-item>
                <a-descriptions-item label="用户ID">{{ currentTask.userId }}</a-descriptions-item>
                <a-descriptions-item label="用户名">{{ currentTask.username || '-' }}</a-descriptions-item>
                <a-descriptions-item label="状态">
                    <a-tag :color="statusColorMap[currentTask.status] || 'default'">
                        {{ statusLabel(currentTask.status) }}
                    </a-tag>
                </a-descriptions-item>
                <a-descriptions-item label="进度" :span="2">
                    <a-progress :percent="currentTask.progress ?? 0"
                        :status="currentTask.status === 'FAILED' ? 'exception' : currentTask.status === 'SUCCESS' ? 'success' : 'active'" />
                </a-descriptions-item>
                <a-descriptions-item label="总行数">{{ currentTask.totalRows ?? '-' }}</a-descriptions-item>
                <a-descriptions-item label="已处理行">{{ currentTask.processedRows ?? '-' }}</a-descriptions-item>
                <a-descriptions-item label="Sheet 数">{{ currentTask.sheetCount ?? '-' }}</a-descriptions-item>
                <a-descriptions-item label="Worker">{{ currentTask.workerId || '-' }}</a-descriptions-item>
                <a-descriptions-item label="尝试次数">{{ currentTask.attempt ?? '-' }}</a-descriptions-item>
                <a-descriptions-item label="最后心跳">{{ formatDateTime(currentTask.lastHeartbeat) }}</a-descriptions-item>
                <a-descriptions-item label="过期时间">{{ formatDateTime(currentTask.expireAt) }}</a-descriptions-item>
                <a-descriptions-item label="创建时间">{{ formatDateTime(currentTask.createdAt) }}</a-descriptions-item>
                <a-descriptions-item label="更新时间">{{ formatDateTime(currentTask.updatedAt) }}</a-descriptions-item>
                <a-descriptions-item label="错误码">{{ currentTask.errorCode || '-' }}</a-descriptions-item>
                <a-descriptions-item label="错误信息" :span="2">
                    {{ currentTask.errorMsg || '-' }}
                </a-descriptions-item>
                <a-descriptions-item label="下载地址" :span="2">
                    <a-typography-paragraph copyable v-if="currentTask.downloadUrl">
                        {{ currentTask.downloadUrl }}
                    </a-typography-paragraph>
                    <span v-else>-</span>
                </a-descriptions-item>
            </a-descriptions>
        </a-drawer>
        <a-drawer v-model:open="createDrawerVisible" title="导出任务示例" width="50%" :get-container="false"
            :style="{ position: 'absolute' }" @close="handleCreateDrawerClose">
            <ExportTaskExamples />
        </a-drawer>
    </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { exportApi, type ExportTask } from '@/api/export'
import { ReloadOutlined, SettingOutlined, HolderOutlined, EyeOutlined, DownloadOutlined, PoweroffOutlined, PlusOutlined } from '@ant-design/icons-vue'
import VueDraggable from 'vuedraggable'
import { message } from 'ant-design-vue'
import { useThrottle } from '@/utils/debounce'
import ExportTaskExamples from './ExportTaskExamples.vue'
import type { ColumnsType } from 'ant-design-vue/es/table'
import type { CheckboxChangeEvent } from 'ant-design-vue/es/checkbox/interface'
import {
    buildDownloadUrl,
    exportTaskRowKey,
    filterTasks,
    formatDateTime,
    paginateTasks,
    statusLabel
} from './taskUtils'
import { ACTIVE_SCOPE_CHANGED_EVENT } from '@/utils/activeScopeEvents'

const route = useRoute()

const statusOptions = [
    { label: '排队中', value: 'PENDING' },
    { label: '运行中', value: 'RUNNING' },
    { label: '成功', value: 'SUCCESS' },
    { label: '失败', value: 'FAILED' },
    { label: '已取消', value: 'CANCELED' }
]

const statusColorMap: Record<string, string> = {
    PENDING: 'blue',
    RUNNING: 'geekblue',
    SUCCESS: 'green',
    FAILED: 'red',
    CANCELED: 'orange'
}

const query = ref({
    taskId: '',
    username: '',
    status: undefined as string | undefined
})

const rawTasks = ref<ExportTask[]>([])
const loading = ref(false)
const refreshing = ref(false)
const showSortTooltip = ref(true)
const createDrawerVisible = ref(false)

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

const pagination = ref({
    current: 1,
    pageSize: 10,
    showSizeChanger: true,
    pageSizeOptions: ['10', '20', '30', '40', '50'],
    total: 0,
    showTotal: (total: number) => `共 ${total} 条`
})

const paginationConfig = computed(() => {
    const current = Number(pagination.value.current) || 1
    const pageSize = Number(pagination.value.pageSize) || 10
    return {
        ...pagination.value,
        current,
        pageSize,
        total: Number(pagination.value.total) || 0
    }
})

type ExportTaskRow = ExportTask & Record<string, unknown>
type ColumnConfig = { title: string; dataIndex: string; sorter?: boolean; width?: number; fixed?: 'left' | 'right'; align?: 'left' | 'right' | 'center' }

const INITIAL_COLUMNS: ColumnConfig[] = [
    { title: '任务ID', dataIndex: 'taskId', sorter: true, width: 220 },
    { title: '用户ID', dataIndex: 'userId', width: 160 },
    { title: '用户名', dataIndex: 'username', width: 160 },
    { title: '状态', dataIndex: 'status', sorter: true, width: 120 },
    { title: '进度', dataIndex: 'progress', width: 160 },
    { title: '总行数', dataIndex: 'totalRows', width: 120 },
    { title: '已处理行', dataIndex: 'processedRows', width: 120 },
    { title: 'Sheet 数', dataIndex: 'sheetCount', width: 120 },
    { title: 'Worker', dataIndex: 'workerId', width: 140 },
    { title: '尝试次数', dataIndex: 'attempt', width: 100 },
    { title: '最后心跳', dataIndex: 'lastHeartbeat', sorter: true, width: 180 },
    { title: '过期时间', dataIndex: 'expireAt', width: 180 },
    { title: '创建时间', dataIndex: 'createdAt', sorter: true, width: 180 },
    { title: '更新时间', dataIndex: 'updatedAt', sorter: true, width: 180 },
    { title: '错误码', dataIndex: 'errorCode', width: 120 },
    { title: '错误信息', dataIndex: 'errorMsg', width: 200 },
    { title: '操作', dataIndex: 'action', width: 180, fixed: 'right', align: 'center' }
]

const allColumns = ref<ColumnConfig[]>([...INITIAL_COLUMNS])
const draggableColumns = ref<ColumnConfig[]>([...INITIAL_COLUMNS])
const showColumnKeys = ref(
    INITIAL_COLUMNS.map(col => col.dataIndex).filter(key => typeof key === 'string' && key)
)

function columnItemKey(item: ColumnConfig) {
    return item?.dataIndex || `col_${Math.random()}`
}

watch(allColumns, (val) => {
    showColumnKeys.value = showColumnKeys.value.filter(key =>
        val.some(col => col.dataIndex === key)
    )
})

watch(draggableColumns, (val) => {
    allColumns.value = val.filter(col => col && typeof col.dataIndex === 'string')
    showColumnKeys.value = showColumnKeys.value.filter(key =>
        allColumns.value.some(col => col.dataIndex === key)
    )
})

const filteredTasks = computed(() => {
    return filterTasks(rawTasks.value, query.value)
})

const pagedTasks = computed(() => {
    return paginateTasks(filteredTasks.value, pagination.value.current, pagination.value.pageSize)
})

watch(
    filteredTasks,
    (val) => {
        pagination.value.total = val.length
        const maxPage = Math.max(1, Math.ceil(val.length / (pagination.value.pageSize || 10)))
        if (pagination.value.current > maxPage) {
            pagination.value.current = maxPage
        }
    },
    { immediate: true }
)

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

const columns = computed<ColumnsType<ExportTaskRow>>(() => {
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

function onCheckAllChange(e: CheckboxChangeEvent) {
    if (e.target.checked) {
        showColumnKeys.value = INITIAL_COLUMNS.map(col => col.dataIndex)
    } else {
        showColumnKeys.value = []
    }
}

function onColumnCheckboxChange(dataIndex: string, e: CheckboxChangeEvent) {
    onCheckboxChange(dataIndex, Boolean(e.target?.checked))
}

function isDateColumn(dataIndex: unknown): dataIndex is string {
    return typeof dataIndex === 'string' && ['createdAt', 'updatedAt', 'lastHeartbeat', 'expireAt'].includes(dataIndex)
}

function isActionColumn(dataIndex: unknown): boolean {
    return dataIndex === 'action'
}

function getRecordDateValue(record: Record<string, any>, dataIndex: unknown) {
    if (typeof dataIndex !== 'string') return undefined
    const value = record[dataIndex] as string | null | undefined
    return value ?? undefined
}

function resetColumnOrder() {
    allColumns.value = [...INITIAL_COLUMNS]
    draggableColumns.value = [...INITIAL_COLUMNS]
    showColumnKeys.value = INITIAL_COLUMNS
        .filter(col => typeof col.dataIndex === 'string' && col.dataIndex)
        .map(col => col.dataIndex)
}

function onDragEnd() {
    // 拖拽后 columns/checkbox 会自动通过 watch 同步
}

async function loadData() {
    loading.value = true
    try {
        const res = await exportApi.listTasks()
        rawTasks.value = Array.isArray(res) ? res : []
        pagination.value.total = rawTasks.value.length
    } catch (error: any) {
        rawTasks.value = []
        pagination.value.total = 0
        message.error('加载导出任务失败: ' + (error?.message || '未知错误'))
    } finally {
        loading.value = false
    }
}

function handleSearch() {
    pagination.value.current = 1
}

const throttledSearch = useThrottle(handleSearch, 800)

function handleReset() {
    query.value.taskId = ''
    query.value.username = ''
    query.value.status = undefined
    pagination.value.current = 1
}

const throttledReset = useThrottle(handleReset, 800)

async function handleRefresh() {
    refreshing.value = true
    loading.value = true
    await loadData().catch(() => {
        /* 错误提示在 loadData 内处理 */
    }).finally(() => {
        setTimeout(() => {
            refreshing.value = false
        }, 800)
        loading.value = false
    })
}

const throttledRefresh = useThrottle(handleRefresh, 1000)

function handleCreate() {
    createDrawerVisible.value = true
}

function handleCreateDrawerClose() {
    createDrawerVisible.value = false
}

const throttledCreate = useThrottle(handleCreate, 500)

function handleTableChange(pag: any) {
    if (pag && typeof pag.current === 'number') {
        pagination.value.current = pag.current
    }
    if (pag && typeof pag.pageSize === 'number') {
        pagination.value.pageSize = pag.pageSize
    }
}

function handlePageChange(page: number) {
    pagination.value.current = page || 1
}

function handlePageSizeChange(_current: number, size: number) {
    pagination.value.pageSize = size || 10
    pagination.value.current = 1
}

const drawerVisible = ref(false)
const currentTask = ref<ExportTask | null>(null)

function handleView(record: ExportTask | unknown) {
    if (!record || typeof record !== 'object') {
        return
    }
    currentTask.value = record as ExportTask
    drawerVisible.value = true
}

const throttledView = useThrottle((record: ExportTask | unknown) => handleView(record), 400)

function handleDrawerClose() {
    drawerVisible.value = false
    currentTask.value = null
}

function rowKey(record: ExportTask & { id?: string | number }) {
    return exportTaskRowKey(record)
}

function handleDownload(record: ExportTask | Record<string, any>) {
    if (record.status !== 'SUCCESS') {
        message.warning('任务未成功，无法下载')
        return
    }
    const url = buildDownloadUrl(record.taskId)
    window.open(url, '_blank')
}

const tableContentRef = ref<HTMLElement | null>(null)
const paginationRef = ref<HTMLElement | null>(null)
const tableBodyHeight = ref(400)

function updateTableBodyHeight() {
    nextTick(() => {
        if (tableContentRef.value && paginationRef.value) {
            const tableHeader = tableContentRef.value.querySelector('.ant-table-header') as HTMLElement
            const containerHeight = tableContentRef.value.clientHeight
            const paginationHeight = paginationRef.value.clientHeight
            const tableHeaderHeight = tableHeader ? tableHeader.clientHeight : 55
            const bodyHeight = containerHeight - paginationHeight - tableHeaderHeight
            tableBodyHeight.value = Math.max(bodyHeight, 200)
        }
    })
}

function handleActiveScopeChanged() {
    void loadData()
}

onMounted(async () => {
    // 从路由查询参数中读取 taskId，如果存在则自动填充到搜索表单并过滤
    const taskIdFromQuery = route.query.taskId as string | undefined
    if (taskIdFromQuery) {
        query.value.taskId = taskIdFromQuery
    }
    
    // 加载数据
    await loadData()
    
    // 更新表格高度
    updateTableBodyHeight()
    window.addEventListener('resize', updateTableBodyHeight)
    window.addEventListener(ACTIVE_SCOPE_CHANGED_EVENT, handleActiveScopeChanged)
})

onBeforeUnmount(() => {
    window.removeEventListener('resize', updateTableBodyHeight)
    window.removeEventListener(ACTIVE_SCOPE_CHANGED_EVENT, handleActiveScopeChanged)
})

// 监听路由 query 参数变化，自动更新过滤条件
watch(() => route.query.taskId, (newTaskId) => {
    if (newTaskId) {
        query.value.taskId = newTaskId as string
    } else if (route.query.taskId === undefined) {
        // 如果路由参数被清除，也清除搜索条件（但保留用户手动输入的内容）
        // 这里不自动清除，因为用户可能已经手动修改了搜索条件
    }
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
    flex: 1;
    min-height: 0;
    overflow: auto;
    padding-bottom: 12px;
    /* 隐藏自身滚动条（含横向），但保留滚动能力，统一与 user.vue 体验 */
    scrollbar-width: none;
    -ms-overflow-style: none;
}

.table-scroll-container::-webkit-scrollbar {
    display: none;
}

/* 数据为空时，AntD 会通过 ant-table-content/placeholder 渲染，占用的仍是内容区域滚动条，继续隐藏 */
::deep(.ant-table-content) {
    scrollbar-width: none;
    -ms-overflow-style: none;
}

::deep(.ant-table-content::-webkit-scrollbar) {
    display: none;
}

.pagination-container {
    position: sticky;
    bottom: 0;
    z-index: 2;
    display: flex;
    align-items: center;
    justify-content: flex-end;
    background: #fff;
    padding: 12px 24px;
    /* 上下留白，确保有足够空间垂直居中 */
    min-height: 56px;
    /* 最小高度，确保有足够的垂直空间 */
    border-top: 1px solid #f0f0f0;
}

::deep(.ant-pagination) {
    display: flex !important;
    flex-direction: row !important;
    align-items: center !important;
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

::deep(.ant-table-tbody > tr) {
    cursor: pointer;
    transition: background-color 0.2s ease;
    user-select: none;
}

::deep(.ant-table-tbody > tr:hover) {
    background-color: #f5f5f5 !important;
}

::deep(.ant-table-tbody > tr .ant-checkbox-wrapper) {
    pointer-events: auto;
}

::deep(.ant-table-tbody > tr td) {
    pointer-events: auto;
}

.cell-content {
    width: 100%;
    height: 100%;
    cursor: pointer;
    padding: 4px 0;
    transition: background-color 0.2s ease;
}

.cell-content:hover {
    background-color: rgba(24, 144, 255, 0.1);
    border-radius: 4px;
}

::deep(.ant-pagination),
::deep(.ant-pagination-item),
::deep(.ant-pagination-item-link) {
    height: 32px !important;
    line-height: 32px !important;
    min-width: 32px;
    box-sizing: border-box;
    vertical-align: middle;
}

::deep(.ant-table-tbody > tr:nth-child(odd)) {
    background-color: #fafbfc;
}

::deep(.ant-table-tbody > tr:nth-child(even)) {
    background-color: #fff;
}

::deep(.ant-table-body) {
    scrollbar-width: none;
    -ms-overflow-style: none;
}

::deep(.ant-table-body::-webkit-scrollbar) {
    display: none;
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

::deep(.ant-modal-header) {
    border-bottom: 1px solid #f0f0f0;
    padding: 16px 24px;
}

::deep(.ant-modal-body) {
    padding: 24px;
}

::deep(.ant-modal-footer) {
    border-top: 1px solid #f0f0f0;
    padding: 16px 24px;
}

::deep(.ant-form-item-label > label) {
    font-weight: 500;
    color: #262626;
}

::deep(.ant-switch) {
    min-width: 44px;
}

::deep(.ant-descriptions-item-label) {
    font-weight: 500;
    color: #262626;
    background-color: #fafafa;
}

::deep(.ant-descriptions-item-content) {
    color: #595959;
}

::deep(.ant-table-thead > tr > th) {
    white-space: nowrap;
    text-overflow: clip;
    overflow: visible;
}

.toolbar-switch {
    margin-left: 8px;
    vertical-align: middle;
}

::deep(.ant-pagination-item-container) {
    margin-right: 8px;
}

::deep(.ant-pagination-item-ellipsis) {
    line-height: 32px !important;
    vertical-align: middle !important;
    display: inline-block !important;
    font-size: 16px !important;
}

::deep(.ant-pagination) {
    min-height: 32px !important;
    height: 32px !important;
    line-height: 32px !important;
}

::deep(.ant-pagination-item),
::deep(.ant-pagination-item-link),
::deep(.ant-pagination-prev),
::deep(.ant-pagination-next),
::deep(.ant-pagination-jump-next),
::deep(.ant-pagination-jump-prev) {
    height: 32px !important;
    min-width: 32px !important;
    line-height: 32px !important;
    box-sizing: border-box;
    display: flex !important;
    align-items: center !important;
    justify-content: center !important;
    padding: 0 !important;
}

::deep(.ant-pagination-item-ellipsis) {
    line-height: 32px !important;
    vertical-align: middle !important;
    display: inline-block !important;
    font-size: 16px !important;
}

.ellipsis-text {
    display: inline-block;
    max-width: 220px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}
::deep(.ant-table-tbody > tr > td) {
    white-space: nowrap;
}
</style>
