<!-- eslint-disable vue/multi-word-component-names -->
<template>
    <div class="content-container" style="position: relative;">
        <div class="content-card">
            <!-- 搜索表单 -->
            <div class="form-container">
                <a-form layout="inline" :model="query">
                    <a-form-item label="任务名称">
                        <a-input v-model:value="query.name" placeholder="请输入任务名称" />
                    </a-form-item>
                    <a-form-item label="流程实例ID">
                        <a-input v-model:value="query.processInstanceId" placeholder="请输入流程实例ID" />
                    </a-form-item>
                    <a-form-item label="指派人">
                        <a-input v-model:value="query.assignee" placeholder="请输入指派人" />
                    </a-form-item>
                    <a-form-item label="状态">
                        <a-select v-model:value="query.status" placeholder="选择状态" allow-clear style="width: 120px">
                            <a-select-option value="">全部状态</a-select-option>
                            <a-select-option value="unassigned">未分配</a-select-option>
                            <a-select-option value="assigned">已分配</a-select-option>
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
                    任务列表
                </div>
                <div class="table-actions">
                    <div v-if="selectedRowKeys.length > 0" class="batch-actions">
                        <a-button type="primary" @click="throttledBatchClaim" class="toolbar-btn">
                            <template #icon>
                                <UserAddOutlined />
                            </template>
                            批量领取 ({{ selectedRowKeys.length }})
                        </a-button>
                        <a-button type="primary" danger @click="throttledBatchComplete" class="toolbar-btn">
                            <template #icon>
                                <CheckCircleOutlined />
                            </template>
                            批量完成 ({{ selectedRowKeys.length }})
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
                </div>
            </div>

            <div class="table-container" ref="tableContentRef">
                <div class="table-scroll-container" ref="tableScrollContainerRef">
                    <a-table :columns="columns" :data-source="dataSource" :loading="loading" :pagination="false"
                        :row-selection="rowSelection" :scroll="{ x: 1200, y: tableBodyHeight }"
                        :row-key="(record: any) => String(record.id)" bordered :custom-row="onCustomRow"
                        :row-class-name="getRowClassName" @change="handleTableChange">
                        <template #bodyCell="{ column, record }">
                            <!-- 任务名称 -->
                            <template v-if="column.key === 'name'">
                                <a-button type="link" size="small" @click="viewTaskDetail(record)">
                                    {{ record.name }}
                                </a-button>
                            </template>

                            <!-- 状态 -->
                            <template v-if="column.key === 'status'">
                                <a-tag :color="getStatusColor(record.status)">
                                    {{ getStatusText(record.status) }}
                                </a-tag>
                            </template>

                            <!-- 优先级 -->
                            <template v-if="column.key === 'priority'">
                                <a-tag :color="getPriorityColor(record.priority)">
                                    {{ getPriorityText(record.priority) }}
                                </a-tag>
                            </template>

                            <!-- 指派人 -->
                            <template v-if="column.key === 'assignee'">
                                <span v-if="record.assignee">{{ record.assignee }}</span>
                                <a-tag v-else color="orange">未分配</a-tag>
                            </template>

                            <!-- 创建时间 -->
                            <template v-if="column.key === 'createTime'">
                                {{ formatDateTime(record.createTime) }}
                            </template>

                            <!-- 截止时间 -->
                            <template v-if="column.key === 'dueDate'">
                                <span v-if="record.dueDate" :class="getDueDateClass(record.dueDate)">
                                    {{ formatDateTime(record.dueDate) }}
                                </span>
                                <span v-else class="text-gray-400">无</span>
                            </template>

                            <!-- 操作列 -->
                            <template v-if="column.key === 'action'">
                                <a-space>
                                    <!-- 领取任务 -->
                                    <a-button type="link" size="small" @click="claimTask(record)"
                                        v-if="!record.assignee">
                                        领取
                                    </a-button>

                                    <!-- 完成任务 -->
                                    <a-button type="link" size="small" @click="completeTask(record)"
                                        v-if="record.assignee && record.assignee === currentUser">
                                        完成
                                    </a-button>

                                    <!-- 查看详情 -->
                                    <a-button type="link" size="small" @click="viewTaskDetail(record)">
                                        详情
                                    </a-button>

                                    <!-- 查看流程实例 -->
                                    <a-button type="link" size="small" @click="viewProcessInstance(record)">
                                        流程
                                    </a-button>
                                </a-space>
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

        <!-- 任务详情弹窗 -->
        <a-modal v-model:open="showTaskDetail" title="任务详情" :footer="null" width="800px">
            <div v-if="currentTask">
                <a-descriptions :column="2" bordered>
                    <a-descriptions-item label="任务ID">{{ currentTask.id }}</a-descriptions-item>
                    <a-descriptions-item label="任务名称">{{ currentTask.name }}</a-descriptions-item>
                    <a-descriptions-item label="流程实例ID">{{ currentTask.processInstanceId }}</a-descriptions-item>
                    <a-descriptions-item label="指派人">{{ currentTask.assignee || '未分配' }}</a-descriptions-item>
                    <a-descriptions-item label="创建时间">{{ formatDateTime(currentTask.createTime) }}</a-descriptions-item>
                    <a-descriptions-item label="租户ID">{{ currentTask.tenantId || '-' }}</a-descriptions-item>
                </a-descriptions>

                <!-- 任务表单 -->
                <div class="mt-4">
                    <h4>任务表单</h4>
                    <a-form :model="taskForm" :label-col="{ span: 6 }" :wrapper-col="{ span: 18 }"
                        @finish="submitTaskForm">
                        <a-form-item label="审批意见">
                            <a-textarea v-model:value="taskForm.comment" :rows="4" placeholder="请输入审批意见" />
                        </a-form-item>
                        <a-form-item label="审批结果">
                            <a-radio-group v-model:value="taskForm.result">
                                <a-radio value="approved">同意</a-radio>
                                <a-radio value="rejected">拒绝</a-radio>
                                <a-radio value="returned">退回</a-radio>
                            </a-radio-group>
                        </a-form-item>
                        <a-form-item :wrapper-col="{ offset: 6, span: 18 }">
                            <a-space>
                                <a-button type="primary" html-type="submit">提交</a-button>
                                <a-button @click="showTaskDetail = false">取消</a-button>
                            </a-space>
                        </a-form-item>
                    </a-form>
                </div>
            </div>
        </a-modal>
    </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount, nextTick, watch, computed } from 'vue'
import { message } from 'ant-design-vue'
import {
    UserAddOutlined,
    CheckCircleOutlined,
    ReloadOutlined,
    CloseOutlined,
    PlusOutlined
} from '@ant-design/icons-vue'
import { useThrottle } from '@/utils/debounce'
import { taskApi, userApi, type Task } from '@/api/process'
import { useAuth } from '@/auth/auth'

// 响应式数据
const loading = ref(false)
const dataSource = ref<Task[]>([])
const selectedRowKeys = ref<string[]>([])
const showTaskDetail = ref(false)
const currentTask = ref<Task | null>(null)
const refreshing = ref(false)
const tenants = ref([
    { id: 'tenant1', name: '租户1' },
    { id: 'tenant2', name: '租户2' }
])

// 查询参数
const query = reactive({
    name: '',
    processInstanceId: '',
    assignee: '',
    status: '',
    tenantId: ''
})

// 任务表单
const taskForm = reactive({
    comment: '',
    result: 'approved'
})

// 获取认证状态
const { user, isAuthenticated } = useAuth()

// 当前用户信息
const currentUserInfo = ref<{ id: string; username: string; nickname?: string } | null>(null)
const currentUser = computed(() => {
    // 优先使用从后端API获取的用户信息
    if (currentUserInfo.value) {
        return currentUserInfo.value.username
    }

    // 备用方案：从JWT token中获取用户信息
    if (user.value && isAuthenticated.value) {
        try {
            const token = user.value.access_token
            if (token) {
                const parts = token.split('.')
                if (!parts[1]) return 'admin'
                const payload = JSON.parse(atob(parts[1]))
                return payload.preferred_username || payload.sub || payload.username || 'admin'
            }
        } catch (error) {
            console.warn('解析用户token失败:', error)
        }
    }

    // 默认返回admin
    return 'admin'
})

// 获取当前用户信息
async function fetchCurrentUser() {
    try {
        const userInfo = await userApi.getCurrentUser()
        currentUserInfo.value = userInfo
        console.log('获取当前用户信息成功:', userInfo)
    } catch (error) {
        console.warn('获取当前用户信息失败，使用默认值:', error)
        currentUserInfo.value = null
    }
}

// 表格相关引用
const tableContentRef = ref<HTMLElement | null>(null)
const tableScrollContainerRef = ref<HTMLElement | null>(null)
const paginationRef = ref<HTMLElement | null>(null)
const tableBodyHeight = ref(400)

// 分页配置
const pagination = ref({
    current: 1,
    pageSize: 10,
    total: 0,
    showSizeChanger: true,
    pageSizeOptions: ['10', '20', '30', '40', '50'],
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

// 表格列配置
const columns = [
    {
        title: '任务名称',
        dataIndex: 'name',
        key: 'name',
        width: 200,
        fixed: 'left'
    },
    {
        title: '优先级',
        dataIndex: 'priority',
        key: 'priority',
        width: 80,
        sorter: (a: Task, b: Task) => (b.priority || 0) - (a.priority || 0)
    },
    {
        title: '流程实例ID',
        dataIndex: 'processInstanceId',
        key: 'processInstanceId',
        width: 150
    },
    {
        title: '指派人',
        dataIndex: 'assignee',
        key: 'assignee',
        width: 120
    },
    {
        title: '状态',
        dataIndex: 'status',
        key: 'status',
        width: 100
    },
    {
        title: '创建时间',
        dataIndex: 'createTime',
        key: 'createTime',
        width: 160
    },
    {
        title: '截止时间',
        dataIndex: 'dueDate',
        key: 'dueDate',
        width: 160
    },
    {
        title: '租户ID',
        dataIndex: 'tenantId',
        key: 'tenantId',
        width: 120
    },
    {
        title: '操作',
        key: 'action',
        width: 200,
        fixed: 'right'
    }
]

// 行选择配置
const rowSelection = computed(() => ({
    selectedRowKeys: selectedRowKeys.value,
    onChange: (keys: string[]) => {
        selectedRowKeys.value = keys
    },
    getCheckboxProps: (record: Task) => ({
        disabled: record.assignee && record.assignee !== currentUser.value
    })
}))

// 获取任务列表
async function fetchTasks() {
    try {
        loading.value = true

        // 根据状态筛选决定查询参数
        let assignee: string | undefined
        if (query.status === 'unassigned') {
            assignee = undefined // 未分配的任务，不传assignee参数
        } else if (query.status === 'assigned') {
            assignee = currentUser.value // 已分配给当前用户的任务
        } else {
            assignee = currentUser.value // 默认获取当前用户的任务
        }

        // 尝试获取任务数据
        let result: Task[] = []

        console.log('开始获取任务数据...')
        console.log('查询参数:', {
            status: query.status,
            assignee: currentUser.value,
            tenantId: query.tenantId
        })

        try {
            if (query.status === 'unassigned') {
                // 获取未分配的任务
                console.log('尝试获取未分配的任务...')
                result = await taskApi.getTasks(undefined, query.tenantId)
                console.log('未分配任务API调用成功:', result)
            } else if (query.status === 'assigned') {
                // 获取已分配的任务
                console.log('尝试获取已分配的任务...')
                result = await taskApi.getTasks(currentUser.value, query.tenantId)
                console.log('已分配任务API调用成功:', result)
            } else {
                // 获取所有任务
                console.log('尝试获取所有任务...')
                result = await taskApi.getTasks('*', query.tenantId)
                console.log('所有任务API调用成功:', result)
            }
        } catch (apiError) {
            console.error('API调用失败:', apiError)

            // 显示详细的错误信息
            if (apiError instanceof Error) {
                message.error(`获取任务失败: ${apiError.message}`)
            } else {
                message.error('获取任务失败，请检查后端服务是否运行 (localhost:9000)')
            }

            // 返回空数组，不使用测试数据
            result = []
        }
        let tasks = Array.isArray(result) ? result : []

        // 调试信息
        console.log('API返回结果:', result)
        console.log('解析后的任务数组:', tasks)
        console.log('查询参数:', { assignee, tenantId: query.tenantId, status: query.status })

        // 客户端筛选
        if (query.name) {
            tasks = tasks.filter(task => task.name.includes(query.name))
        }
        if (query.processInstanceId) {
            tasks = tasks.filter(task => task.processInstanceId.includes(query.processInstanceId))
        }
        if (query.assignee) {
            tasks = tasks.filter(task => task.assignee?.includes(query.assignee))
        }

        // 状态筛选
        if (query.status === 'unassigned') {
            tasks = tasks.filter(task => !task.assignee)
        } else if (query.status === 'assigned') {
            tasks = tasks.filter(task => task.assignee)
        } else if (query.status === 'completed') {
            // 已完成的任务可能需要从历史API获取
            tasks = tasks.filter(task => task.assignee && task.assignee === currentUser.value)
        }

        dataSource.value = tasks
        pagination.value.total = tasks.length
    } catch (error) {
        console.error('获取任务列表失败:', error)
        message.error('获取任务列表失败')
        dataSource.value = []
        pagination.value.total = 0
    } finally {
        loading.value = false
    }
}

// 搜索
async function handleSearch() {
    pagination.value.current = 1
    await fetchTasks()
}

// 重置
async function handleReset() {
    Object.keys(query).forEach(key => {
        query[key as keyof typeof query] = ''
    })
    pagination.value.current = 1
    await fetchTasks()
}

// 领取任务
async function claimTask(task: Task) {
    try {
        await taskApi.claimTask(task.id, currentUser.value)
        message.success('任务领取成功')
        await fetchTasks()
    } catch (error) {
        console.error('领取任务失败:', error)
        message.error('领取任务失败')
    }
}

// 完成任务
async function completeTask(task: Task) {
    try {
        await taskApi.completeTask({
            taskId: task.id,
            variables: {
                result: taskForm.result,
                comment: taskForm.comment
            }
        })
        message.success('任务完成成功')
        showTaskDetail.value = false
        await fetchTasks()
    } catch (error) {
        console.error('完成任务失败:', error)
        message.error('完成任务失败')
    }
}

// 查看任务详情
function viewTaskDetail(task: Task) {
    currentTask.value = task
    showTaskDetail.value = true
    // 重置表单
    taskForm.comment = ''
    taskForm.result = 'approved'
}

// 查看流程实例
function viewProcessInstance(task: Task) {
    // 跳转到流程实例页面
    window.open(`/process/instance?instanceId=${task.processInstanceId}`, '_blank')
}

// 批量领取
async function batchClaim() {
    if (selectedRowKeys.value.length === 0) {
        message.warning('请选择要领取的任务')
        return
    }

    try {
        for (const taskId of selectedRowKeys.value) {
            await taskApi.claimTask(taskId, currentUser.value)
        }
        message.success(`成功领取 ${selectedRowKeys.value.length} 个任务`)
        selectedRowKeys.value = []
        await fetchTasks()
    } catch (error) {
        console.error('批量领取失败:', error)
        message.error('批量领取失败')
    }
}

// 批量完成
async function batchComplete() {
    if (selectedRowKeys.value.length === 0) {
        message.warning('请选择要完成的任务')
        return
    }

    try {
        for (const taskId of selectedRowKeys.value) {
            await taskApi.completeTask({
                taskId,
                variables: { result: 'approved' }
            })
        }
        message.success(`成功完成 ${selectedRowKeys.value.length} 个任务`)
        selectedRowKeys.value = []
        await fetchTasks()
    } catch (error) {
        console.error('批量完成失败:', error)
        message.error('批量完成失败')
    }
}

// 刷新
async function refresh() {
    refreshing.value = true
    try {
        await fetchTasks()
    } finally {
        refreshing.value = false
    }
}

// 清除选择
function clearSelection() {
    selectedRowKeys.value = []
}

// 跳转到流程建模
function goToModeling() {
    window.open('/process/modeling', '_blank')
}

// 表格行选择处理
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

// 表格变化处理
function handleTableChange(pag: { current?: number; pageSize?: number }) {
    if (pag && typeof pag.current === 'number') {
        pagination.value.current = pag.current
    }
    if (pag && typeof pag.pageSize === 'number') {
        pagination.value.pageSize = pag.pageSize
    }
    fetchTasks()
}

// 分页处理
function handlePageChange(page: number) {
    pagination.value.current = page || 1
    fetchTasks()
}

function handlePageSizeChange(current: number, size: number) {
    pagination.value.pageSize = size || 10
    pagination.value.current = 1
    fetchTasks()
}

// 动态计算表格高度
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

// 提交任务表单
async function submitTaskForm() {
    if (!currentTask.value) return

    try {
        await taskApi.completeTask({
            taskId: currentTask.value.id,
            variables: {
                result: taskForm.result,
                comment: taskForm.comment
            }
        })
        message.success('任务提交成功')
        showTaskDetail.value = false
        await fetchTasks()
    } catch (error) {
        console.error('提交任务失败:', error)
        message.error('提交任务失败')
    }
}

// 工具函数
function formatDateTime(value: string | number | Date) {
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) return ''
    return new Intl.DateTimeFormat('zh-CN', {
        year: 'numeric', month: '2-digit', day: '2-digit',
        hour: '2-digit', minute: '2-digit', second: '2-digit',
        hour12: false
    }).format(date).replace(/\//g, '-')
}

function getStatusColor(status: string) {
    const colorMap: Record<string, string> = {
        unassigned: 'orange',
        assigned: 'blue',
        completed: 'green'
    }
    return colorMap[status] || 'default'
}

function getStatusText(status: string) {
    const textMap: Record<string, string> = {
        unassigned: '未分配',
        assigned: '已分配',
        completed: '已完成'
    }
    return textMap[status] || status
}

function getPriorityColor(priority: number | undefined) {
    if (!priority) return 'default'
    if (priority >= 80) return 'red'
    if (priority >= 60) return 'orange'
    if (priority >= 40) return 'blue'
    return 'green'
}

function getPriorityText(priority: number | undefined) {
    if (!priority) return '普通'
    if (priority >= 80) return '紧急'
    if (priority >= 60) return '高'
    if (priority >= 40) return '中'
    return '低'
}

function getDueDateClass(dueDate: string) {
    if (!dueDate) return ''
    const now = new Date()
    const due = new Date(dueDate)
    const diffHours = (due.getTime() - now.getTime()) / (1000 * 60 * 60)

    if (diffHours < 0) return 'text-red-500 font-bold' // 已过期
    if (diffHours < 24) return 'text-orange-500 font-semibold' // 24小时内
    return 'text-gray-600' // 正常
}

// 节流函数
const throttledSearch = useThrottle(handleSearch, 500)
const throttledReset = useThrottle(handleReset, 500)
const throttledRefresh = useThrottle(refresh, 500)
const throttledBatchClaim = useThrottle(batchClaim, 1000)
const throttledBatchComplete = useThrottle(batchComplete, 1000)


// 自动刷新定时器
let autoRefreshTimer: number | null = null

// 启动自动刷新
function startAutoRefresh() {
    if (autoRefreshTimer) return
    autoRefreshTimer = setInterval(() => {
        console.log('自动刷新任务列表...')
        fetchTasks()
    }, 30000) // 30秒自动刷新一次
}

// 停止自动刷新
function stopAutoRefresh() {
    if (autoRefreshTimer) {
        clearInterval(autoRefreshTimer)
        autoRefreshTimer = null
    }
}

// 组件挂载
onMounted(async () => {
    console.log('任务页面组件挂载，开始获取任务数据')

    // 先获取用户信息，再获取任务数据
    await fetchCurrentUser()
    await fetchTasks()

    // 启动自动刷新
    startAutoRefresh()

    updateTableBodyHeight()
    window.addEventListener('resize', updateTableBodyHeight)
})

onBeforeUnmount(() => {
    // 停止自动刷新
    stopAutoRefresh()
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

.mt-4 {
    margin-top: 16px;
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
