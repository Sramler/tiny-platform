<!-- eslint-disable vue/multi-word-component-names -->
<template>
    <div class="content-container" style="position: relative;">
        <div class="content-card">
            <div class="form-container">
                <a-form layout="inline" :model="query">
                    <a-form-item label="流程实例ID">
                        <a-input v-model:value="query.instanceId" placeholder="请输入流程实例ID" />
                    </a-form-item>
                    <a-form-item label="流程定义Key">
                        <a-input v-model:value="query.processKey" placeholder="请输入流程定义Key" />
                    </a-form-item>
                    <a-form-item label="状态">
                        <a-select v-model:value="query.state" placeholder="选择状态" allow-clear style="width: 120px">
                            <a-select-option value="">全部状态</a-select-option>
                            <a-select-option value="active">活跃</a-select-option>
                            <a-select-option value="suspended">暂停</a-select-option>
                            <a-select-option value="completed">已完成</a-select-option>
                        </a-select>
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
                    流程实例列表
                </div>
                <div class="table-actions">
                    <div v-if="selectedRowKeys.length > 0" class="batch-actions">
                        <a-button type="primary" danger @click="throttledBatchSuspend" class="toolbar-btn">
                            <template #icon>
                                <PauseCircleOutlined />
                            </template>
                            批量暂停 ({{ selectedRowKeys.length }})
                        </a-button>
                        <a-button type="primary" @click="throttledBatchActivate" class="toolbar-btn">
                            <template #icon>
                                <PlayCircleOutlined />
                            </template>
                            批量激活 ({{ selectedRowKeys.length }})
                        </a-button>
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

                    <a-button type="link" @click="goToModeling" class="toolbar-btn">
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
                            <template v-if="column.dataIndex === 'processKey'">
                                <a-typography-text code>{{ record.processKey }}</a-typography-text>
                            </template>
                            <template v-else-if="column.dataIndex === 'state'">
                                <a-tag :color="getStateColor(record.state)">
                                    {{ getStateText(record.state) }}
                                </a-tag>
                            </template>
                            <template v-else-if="column.dataIndex === 'startTime'">
                                {{ formatDate(record.startTime) }}
                            </template>
                            <template v-else-if="column.dataIndex === 'endTime'">
                                {{ record.endTime ? formatDate(record.endTime) : '-' }}
                            </template>
                            <template v-else-if="column.dataIndex === 'duration'">
                                {{ getDuration(record.startTime, record.endTime) }}
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
                                    <a-button type="link" size="small" @click.stop="throttledSuspend(record)"
                                        v-if="record.state === 'active'" class="action-btn">
                                        <template #icon>
                                            <PauseCircleOutlined />
                                        </template>
                                        暂停
                                    </a-button>
                                    <a-button type="link" size="small" @click.stop="throttledActivate(record)"
                                        v-if="record.state === 'suspended'" class="action-btn">
                                        <template #icon>
                                            <PlayCircleOutlined />
                                        </template>
                                        激活
                                    </a-button>
                                    <a-button type="link" size="small" @click.stop="throttledViewTasks(record)"
                                        class="action-btn">
                                        <template #icon>
                                            <TagsOutlined />
                                        </template>
                                        任务
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

        <!-- 流程实例详情弹窗 -->
        <a-modal v-model:open="showInstanceDetail" title="流程实例详情" :footer="null" width="800px">
            <a-descriptions :column="2" bordered v-if="selectedInstance">
                <a-descriptions-item label="实例ID">
                    <a-typography-text code>{{ selectedInstance.id }}</a-typography-text>
                </a-descriptions-item>
                <a-descriptions-item label="流程定义Key">
                    <a-typography-text code>{{ selectedInstance.processKey }}</a-typography-text>
                </a-descriptions-item>
                <a-descriptions-item label="流程定义ID">
                    <a-typography-text code>{{ selectedInstance.processDefinitionId }}</a-typography-text>
                </a-descriptions-item>
                <a-descriptions-item label="状态">
                    <a-tag :color="getStateColor(selectedInstance.state)">
                        {{ getStateText(selectedInstance.state) }}
                    </a-tag>
                </a-descriptions-item>
                <a-descriptions-item label="开始时间">
                    {{ formatDate(selectedInstance.startTime) }}
                </a-descriptions-item>
                <a-descriptions-item label="结束时间">
                    {{ selectedInstance.endTime ? formatDate(selectedInstance.endTime) : '-' }}
                </a-descriptions-item>
                <a-descriptions-item label="持续时间">
                    {{ getDuration(selectedInstance.startTime, selectedInstance.endTime) }}
                </a-descriptions-item>
                <a-descriptions-item label="租户">
                    <a-tag v-if="selectedInstance.tenantId" color="green">{{ selectedInstance.tenantId }}</a-tag>
                    <a-tag v-else color="default">默认</a-tag>
                </a-descriptions-item>
                <a-descriptions-item label="流程变量" :span="2">
                    <a-list v-if="selectedInstance.variables && Object.keys(selectedInstance.variables).length > 0"
                        :data-source="Object.entries(selectedInstance.variables)" size="small">
                        <template #renderItem="{ item }">
                            <a-list-item>
                                <a-list-item-meta>
                                    <template #title>
                                        <a-typography-text strong>{{ item[0] }}</a-typography-text>
                                    </template>
                                    <template #description>
                                        {{ typeof item[1] === 'object' ? JSON.stringify(item[1]) : String(item[1]) }}
                                    </template>
                                </a-list-item-meta>
                            </a-list-item>
                        </template>
                    </a-list>
                    <a-typography-text v-else type="secondary">无</a-typography-text>
                </a-descriptions-item>
            </a-descriptions>
        </a-modal>

        <!-- 任务列表弹窗 -->
        <a-modal v-model:open="showTaskList" title="任务列表" :footer="null" width="1000px">
            <a-table :columns="taskColumns" :data-source="taskList" :pagination="false" bordered :loading="taskLoading"
                :scroll="{ x: 800, y: 400 }">
                <template #bodyCell="{ column, record }">
                    <template v-if="column.dataIndex === 'assignee'">
                        <a-tag v-if="record.assignee" color="blue">{{ record.assignee }}</a-tag>
                        <a-tag v-else color="default">未分配</a-tag>
                    </template>
                    <template v-else-if="column.dataIndex === 'createTime'">
                        {{ formatDate(record.createTime) }}
                    </template>
                    <template v-else-if="column.dataIndex === 'action'">
                        <div class="action-buttons">
                            <a-button type="link" size="small" @click="claimTask(record)" v-if="!record.assignee"
                                class="action-btn">
                                <template #icon>
                                    <UserOutlined />
                                </template>
                                领取
                            </a-button>
                            <a-button type="link" size="small" @click="completeTask(record)" v-if="record.assignee"
                                class="action-btn">
                                <template #icon>
                                    <CheckOutlined />
                                </template>
                                完成
                            </a-button>
                        </div>
                    </template>
                </template>
            </a-table>
        </a-modal>
    </div>
</template>

<!-- eslint-disable vue/multi-word-component-names -->
<script setup lang="ts" name="ProcessInstancePage">
import { ref, computed, onMounted, watch, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import {
    PlusOutlined,
    ReloadOutlined,
    EyeOutlined,
    PauseCircleOutlined,
    PlayCircleOutlined,
    DeleteOutlined,
    SettingOutlined,
    HolderOutlined,
    CloseOutlined,
    PoweroffOutlined,
    TagsOutlined,
    UserOutlined,
    CheckOutlined
} from '@ant-design/icons-vue'
import VueDraggable from 'vuedraggable'
import { instanceApi, tenantApi, historyApi } from '@/api/process'
import type { ProcessInstance, Task } from '@/api/process'
import { useThrottle } from '@/utils/debounce'

const router = useRouter()

// 查询条件
const query = ref({
    instanceId: '',
    processKey: '',
    state: 'active',
    tenantId: ''
})

const tableData = ref<ProcessInstance[]>([])
const loading = ref(false)
const refreshing = ref(false)
const selectedRowKeys = ref<string[]>([])
const showInstanceDetail = ref(false)
const selectedInstance = ref<ProcessInstance | null>(null)
const showTaskList = ref(false)
const taskList = ref<Task[]>([])
const taskLoading = ref(false)
const showSortTooltip = ref(true)

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
    { title: '流程定义Key', dataIndex: 'processKey', sorter: true, width: 150 },
    { title: '流程定义ID', dataIndex: 'processDefinitionId', sorter: true, width: 150 },
    { title: '状态', dataIndex: 'state', sorter: true, width: 100 },
    { title: '开始时间', dataIndex: 'startTime', sorter: true, width: 150 },
    { title: '结束时间', dataIndex: 'endTime', sorter: true, width: 150 },
    { title: '持续时间', dataIndex: 'duration', width: 120 },
    { title: '租户', dataIndex: 'tenantId', width: 100 },
    {
        title: '操作',
        dataIndex: 'action',
        width: 300,
        fixed: 'right',
        align: 'center'
    }
]

// 任务列表列配置
const taskColumns = [
    { title: '任务ID', dataIndex: 'id', width: 120 },
    { title: '任务名称', dataIndex: 'name', width: 150 },
    { title: '处理人', dataIndex: 'assignee', width: 120 },
    { title: '创建时间', dataIndex: 'createTime', width: 150 },
    { title: '操作', dataIndex: 'action', width: 120, fixed: 'right' }
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

function onCheckAllChange(e: { target: { checked: boolean } }) {
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

function onDragEnd() {
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
    onChange: (selectedKeys: string[]) => {
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
            instanceId: query.value.instanceId.trim(),
            processKey: query.value.processKey.trim(),
            state: query.value.state || 'active',
            tenantId: query.value.tenantId || undefined,
            current: Number(pagination.value.current) || 1,
            pageSize: Number(pagination.value.pageSize) || 10
        }
        if (params.state === 'completed') {
            const hist = await historyApi.getHistoricInstances(params.tenantId)
            const normalized = Array.isArray(hist)
                ? hist.map((h: any) => ({
                    id: String(h.id ?? h.instanceId ?? ''),
                    processKey: h.processKey ?? h.processDefinitionKey ?? h.key ?? '',
                    processDefinitionId: h.processDefinitionId ?? '',
                    state: 'completed',
                    startTime: h.startTime ?? h.start_time ?? h.start_date ?? '',
                    endTime: h.endTime ?? h.end_time ?? h.end_date ?? '',
                    tenantId: h.tenantId ?? h.tenant_id ?? undefined,
                    variables: h.variables ?? {}
                }))
                : []
            tableData.value = normalized
        } else {
            const result = await instanceApi.getProcessInstances(params.tenantId, params.state)
            tableData.value = Array.isArray(result) ? result : []
        }
        pagination.value.total = tableData.value.length
    } catch (error: unknown) {
        console.error('加载流程实例失败:', error)
        const errorMessage = error instanceof Error ? error.message : '未知错误'
        message.error('加载流程实例失败：' + errorMessage)
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
    query.value.instanceId = ''
    query.value.processKey = ''
    query.value.state = 'active'
    query.value.tenantId = ''
    pagination.value.current = 1
    loadData()
}

const throttledReset = useThrottle(handleReset, 1000)

function handleTableChange(pag: { current?: number; pageSize?: number }) {
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

function getRowClassName(record: { id: string }) {
    if (selectedRowKeys.value.includes(record.id)) {
        return 'checkbox-selected-row'
    }
    return ''
}

function onCustomRow(record: { id: string }) {
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

function handleBatchSuspend() {
    if (selectedRowKeys.value.length === 0) {
        message.warning('请先选择要暂停的流程实例')
        return
    }
    Modal.confirm({
        title: '确认批量暂停',
        content: `确定要暂停选中的 ${selectedRowKeys.value.length} 个流程实例吗？`,
        okText: '确认',
        cancelText: '取消',
        onOk: () => {
            return Promise.all(
                selectedRowKeys.value.map(id =>
                    instanceApi.suspendInstance(id).catch(error => {
                        console.error(`暂停流程实例 ${id} 失败:`, error)
                        throw error
                    })
                )
            ).then(() => {
                message.success('批量暂停成功')
                selectedRowKeys.value = []
                loadData()
            }).catch((error: unknown) => {
                const errorMessage = error instanceof Error ? error.message : '未知错误'
                message.error('批量暂停失败: ' + errorMessage)
                return Promise.reject(error)
            })
        }
    })
}

const throttledBatchSuspend = useThrottle(handleBatchSuspend, 1000)

function handleBatchActivate() {
    if (selectedRowKeys.value.length === 0) {
        message.warning('请先选择要激活的流程实例')
        return
    }
    Modal.confirm({
        title: '确认批量激活',
        content: `确定要激活选中的 ${selectedRowKeys.value.length} 个流程实例吗？`,
        okText: '确认',
        cancelText: '取消',
        onOk: () => {
            return Promise.all(
                selectedRowKeys.value.map(id =>
                    instanceApi.activateInstance(id).catch(error => {
                        console.error(`激活流程实例 ${id} 失败:`, error)
                        throw error
                    })
                )
            ).then(() => {
                message.success('批量激活成功')
                selectedRowKeys.value = []
                loadData()
            }).catch((error: unknown) => {
                const errorMessage = error instanceof Error ? error.message : '未知错误'
                message.error('批量激活失败: ' + errorMessage)
                return Promise.reject(error)
            })
        }
    })
}

const throttledBatchActivate = useThrottle(handleBatchActivate, 1000)

function handleBatchDelete() {
    if (selectedRowKeys.value.length === 0) {
        message.warning('请先选择要删除的流程实例')
        return
    }
    Modal.confirm({
        title: '确认批量删除',
        content: `确定要删除选中的 ${selectedRowKeys.value.length} 个流程实例吗？`,
        okText: '确认',
        cancelText: '取消',
        onOk: () => {
            return Promise.all(
                selectedRowKeys.value.map(id =>
                    instanceApi.deleteInstance(id).catch(error => {
                        console.error(`删除流程实例 ${id} 失败:`, error)
                        throw error
                    })
                )
            ).then(() => {
                message.success('批量删除成功')
                selectedRowKeys.value = []
                loadData()
            }).catch((error: unknown) => {
                // 优先使用 Problem 格式的 detail，否则使用 error.message
                const errorMessage = (error as any)?.errorInfo?.message || 
                                   (error instanceof Error ? error.message : '未知错误')
                message.error('批量删除失败: ' + errorMessage)
                return Promise.reject(error)
            })
        }
    })
}

const throttledBatchDelete = useThrottle(handleBatchDelete, 1000)

function handleView(record: ProcessInstance) {
    selectedInstance.value = record
    showInstanceDetail.value = true
}

const throttledView = useThrottle(handleView, 500)

function handleSuspend(record: ProcessInstance) {
    Modal.confirm({
        title: '确认暂停',
        content: `确定要暂停流程实例 ${record.id} 吗？`,
        okText: '确认',
        cancelText: '取消',
        onOk: () => {
            return instanceApi.suspendInstance(record.id)
                .then(() => {
                    message.success('流程实例暂停成功')
                    loadData()
                })
                .catch((error: unknown) => {
                    const errorMessage = error instanceof Error ? error.message : '未知错误'
                    message.error('暂停流程实例失败: ' + errorMessage)
                    return Promise.reject(error)
                })
        }
    })
}

const throttledSuspend = useThrottle(handleSuspend, 500)

function handleActivate(record: ProcessInstance) {
    Modal.confirm({
        title: '确认激活',
        content: `确定要激活流程实例 ${record.id} 吗？`,
        okText: '确认',
        cancelText: '取消',
        onOk: () => {
            return instanceApi.activateInstance(record.id)
                .then(() => {
                    message.success('流程实例激活成功')
                    loadData()
                })
                .catch((error: unknown) => {
                    const errorMessage = error instanceof Error ? error.message : '未知错误'
                    message.error('激活流程实例失败: ' + errorMessage)
                    return Promise.reject(error)
                })
        }
    })
}

const throttledActivate = useThrottle(handleActivate, 500)

async function handleViewTasks(record: ProcessInstance) {
    try {
        taskLoading.value = true
        const result = await instanceApi.getTasks(record.id)
        taskList.value = Array.isArray(result) ? result : []
        showTaskList.value = true
    } catch (error: unknown) {
        console.error('加载任务列表失败:', error)
        const errorMessage = error instanceof Error ? error.message : '未知错误'
        message.error('加载任务列表失败：' + errorMessage)
    } finally {
        taskLoading.value = false
    }
}

const throttledViewTasks = useThrottle(handleViewTasks, 500)

function handleDelete(record: ProcessInstance) {
    Modal.confirm({
        title: '确认删除',
        content: `确定要删除流程实例 ${record.id} 吗？`,
        okText: '确认',
        cancelText: '取消',
        onOk: () => {
            return instanceApi.deleteInstance(record.id)
                .then(() => {
                    message.success('流程实例删除成功')
                    loadData()
                })
                .catch((error: unknown) => {
                    const errorMessage = error instanceof Error ? error.message : '未知错误'
                    message.error('删除流程实例失败: ' + errorMessage)
                    return Promise.reject(error)
                })
        }
    })
}

const throttledDelete = useThrottle(handleDelete, 500)

async function claimTask(task: Task) {
    try {
        await instanceApi.claimTask(task.id, 'current-user') // 这里应该获取当前用户ID
        message.success('任务领取成功')
        // 重新加载任务列表
        await handleViewTasks({ id: task.processInstanceId } as ProcessInstance)
    } catch (error: unknown) {
        console.error('领取任务失败:', error)
        const errorMessage = error instanceof Error ? error.message : '未知错误'
        message.error('领取任务失败：' + errorMessage)
    }
}

async function completeTask(task: Task) {
    try {
        await instanceApi.completeTask(task.id, {})
        message.success('任务完成成功')
        // 重新加载任务列表
        await handleViewTasks({ id: task.processInstanceId } as ProcessInstance)
    } catch (error: unknown) {
        console.error('完成任务失败:', error)
        const errorMessage = error instanceof Error ? error.message : '未知错误'
        message.error('完成任务失败：' + errorMessage)
    }
}

const formatDate = (dateString: string | Date) => {
    if (!dateString) return '-'
    const date = typeof dateString === 'string' ? new Date(dateString) : dateString
    return date.toLocaleString('zh-CN')
}

const getStateColor = (state: string) => {
    const stateColors: Record<string, string> = {
        'active': 'green',
        'suspended': 'orange',
        'completed': 'blue',
        'cancelled': 'red'
    }
    return stateColors[state] || 'default'
}

const getStateText = (state: string) => {
    const stateTexts: Record<string, string> = {
        'active': '活跃',
        'suspended': '暂停',
        'completed': '已完成',
        'cancelled': '已取消'
    }
    return stateTexts[state] || state
}

const getDuration = (startTime: string | Date, endTime?: string | Date) => {
    if (!startTime) return '-'
    const start = typeof startTime === 'string' ? new Date(startTime) : startTime
    const end = endTime ? (typeof endTime === 'string' ? new Date(endTime) : endTime) : new Date()
    const diff = end.getTime() - start.getTime()

    if (diff < 0) return '-'

    const days = Math.floor(diff / (1000 * 60 * 60 * 24))
    const hours = Math.floor((diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60))
    const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60))

    if (days > 0) {
        return `${days}天${hours}小时`
    } else if (hours > 0) {
        return `${hours}小时${minutes}分钟`
    } else {
        return `${minutes}分钟`
    }
}

const goToModeling = () => {
    router.push('/modeling')
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
