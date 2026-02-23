import request from '@/utils/request'
import type { UserSummary } from '@/api/user'

export interface ProcessDefinition {
  id: string
  key: string
  name: string
  version: number
  deploymentId: string
  deploymentTime: string
  tenantId?: string
  suspended?: boolean
  created?: string
  description?: string
}

export interface ProcessInstance {
  id: string
  processDefinitionId: string
  processDefinitionName?: string
  processKey?: string
  suspended?: boolean
  startTime: string
  endTime?: string
  state?: string
  tenantId?: string
  variables?: Record<string, unknown>
}

export interface Task {
  id: string
  name: string
  processInstanceId: string
  assignee?: string
  createTime: string
  dueDate?: string
  priority?: number
  tenantId?: string
}

export interface Deployment {
  id: string
  name: string
  deploymentTime: string
  tenantId?: string
  status?: string
  source?: string
  processDefinitionCount?: number
  processDefinitions?: ProcessDefinition[]

  // 新增字段
  activeProcessDefinitionCount?: number // 活跃流程定义数量
  suspendedProcessDefinitionCount?: number // 暂停流程定义数量
  totalProcessInstances?: number // 总流程实例数
  activeProcessInstances?: number // 活跃流程实例数
  completedProcessInstances?: number // 已完成流程实例数
  deploymentTimeFormatted?: string // 格式化的部署时间
  deploymentTimeAgo?: string // 相对时间（如：2小时前）
  deployer?: string // 部署者
  environment?: string // 部署环境
  version?: string // 部署版本
  description?: string // 部署描述
  tags?: string[] // 部署标签
}

export interface ProcessInfo {
  deploymentName: string
  key: string
}

export interface StartProcessRequest {
  processKey: string
  variables?: Record<string, unknown>
}

export interface CompleteTaskRequest {
  taskId: string
  variables?: Record<string, unknown>
}

// 流程定义管理
export const processApi = {
  // 获取流程定义列表
  getProcessDefinitions: (tenantId?: string) =>
    request.get<ProcessDefinition[]>('/process/definitions', { params: { tenantId } }),

  // 获取流程定义 XML
  getProcessDefinitionXml: (processDefinitionId: string) =>
    request.get<{ bpmnXml: string }>(`/process/definition/${processDefinitionId}/xml`),

  // 删除流程定义
  deleteProcessDefinition: (processDefinitionId: string) =>
    request.delete<{ message: string }>(`/process/definition/${processDefinitionId}`),

  // 验证 BPMN XML
  validateBpmnXml: (bpmnXml: string) =>
    request.post<{ valid: boolean; message: string }>('/process/validate', bpmnXml, {
      headers: { 'Content-Type': 'application/xml' },
    }),
}

// 流程部署管理
export const deploymentApi = {
  // 部署流程
  deployProcess: (bpmnXml: string | FormData) => {
    const isFormData = typeof FormData !== 'undefined' && bpmnXml instanceof FormData
    return request.post<{ deploymentId: string; message: string }>(
      '/process/deploy',
      bpmnXml,
      isFormData ? {} : { headers: { 'Content-Type': 'application/xml' } },
    )
  },

  // 部署流程（带信息）
  deployProcessWithInfo: (data: { bpmnXml: string } & ProcessInfo) =>
    request.post<{ deploymentId: string; message: string }>('/process/deploy-with-info', data),

  // 获取部署列表
  getDeployments: (tenantId?: string) =>
    request.get<Deployment[]>('/process/deployments', { params: { tenantId } }),

  // 删除部署
  deleteDeployment: (deploymentId: string) =>
    request.delete<{ message: string }>(`/process/deployment/${deploymentId}`),
}

// 流程实例管理
export const instanceApi = {
  // 启动流程实例
  startProcess: (data: StartProcessRequest) =>
    request.post<{ instanceId: string; message: string }>('/process/start', data.variables, {
      params: { processKey: data.processKey },
    }),

  // 获取流程实例列表
  getProcessInstances: (tenantId?: string, state?: string) =>
    request.get<ProcessInstance[]>('/process/instances', { params: { tenantId, state } }),

  // 挂起流程实例
  suspendInstance: (instanceId: string) =>
    request.post<{ message: string }>(`/process/instance/${instanceId}/suspend`),

  // 激活流程实例
  activateInstance: (instanceId: string) =>
    request.post<{ message: string }>(`/process/instance/${instanceId}/activate`),

  // 删除流程实例
  deleteInstance: (instanceId: string) =>
    request.delete<{ message: string }>(`/process/instance/${instanceId}`),

  // 获取任务列表
  getTasks: (processInstanceId: string) =>
    request.get<Task[]>(`/process/instance/${processInstanceId}/tasks`),

  // 领取任务
  claimTask: (taskId: string, userId: string) =>
    request.post<{ message: string }>(`/process/task/${taskId}/claim`, null, {
      params: { userId },
    }),

  // 完成任务
  completeTask: (taskId: string, variables: Record<string, unknown>) =>
    request.post<{ message: string }>(`/process/task/${taskId}/complete`, variables),
}

// 任务管理
export const taskApi = {
  // 获取任务列表
  getTasks: (assignee?: string, tenantId?: string) =>
    request.get<Task[]>('/process/tasks', { params: { assignee, tenantId } }),

  // 领取任务
  claimTask: (taskId: string, userId: string) =>
    request.post<{ message: string }>(`/process/task/${taskId}/claim`, null, {
      params: { userId },
    }),

  // 完成任务
  completeTask: (data: CompleteTaskRequest) =>
    request.post<{ message: string }>(`/process/task/${data.taskId}/complete`, data.variables),
}

// 历史数据查询
export const historyApi = {
  // 获取历史流程实例
  getHistoricInstances: (tenantId?: string) =>
    request.get('/process/history/instances', { params: { tenantId } }),

  // 获取历史任务记录
  getHistoricTasks: (processInstanceId: string) =>
    request.get('/process/history/tasks', { params: { processInstanceId } }),
}

// 租户管理
export const tenantApi = {
  // 创建租户
  createTenant: (tenantInfo: { id: string; name: string }) =>
    request.post<{ tenantId: string; message: string }>('/process/tenant', tenantInfo),

  // 获取租户列表
  getTenants: () => request.get('/process/tenants'),
}

// 用户管理
export const userApi = {
  // 获取当前用户信息
  getCurrentUser: () => request.get<UserSummary>('/sys/users/current'),
}

// 运维管理
export const maintenanceApi = {
  // 获取引擎信息
  getEngineInfo: () => request.get('/process/engine/info'),

  // 健康检查
  healthCheck: () =>
    request.get<{ status: string; message: string; timestamp: number }>('/process/health'),
}
