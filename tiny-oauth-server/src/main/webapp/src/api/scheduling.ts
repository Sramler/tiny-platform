// scheduling.ts 企业级 DAG 调度相关 API 封装
import request from '@/utils/request'

export interface SchedulingPageParams {
  current?: number
  pageSize?: number
}

export interface SchedulingTaskTypeListParams extends SchedulingPageParams {
  code?: string
  name?: string
}

export interface SchedulingTaskListParams extends SchedulingTaskTypeListParams {
  typeId?: number
}

export type SchedulingDagListParams = SchedulingTaskTypeListParams

export interface SchedulingAuditListParams extends SchedulingPageParams {
  objectType?: string
  action?: string
}

export interface SchedulingTaskTypePayload {
  id?: number
  code?: string
  name: string
  description?: string
  executor?: string
  paramSchema?: string
  defaultTimeoutSec?: number
  defaultMaxRetry?: number
  enabled?: boolean
  createdBy?: string
}

export interface SchedulingTaskPayload {
  id?: number
  typeId?: number
  code?: string
  name: string
  description?: string
  params?: string
  timeoutSec?: number
  maxRetry?: number
  retryPolicy?: string
  concurrencyPolicy?: string
  enabled?: boolean
  createdBy?: string
}

export interface SchedulingDagPayload {
  id?: number
  code?: string
  name: string
  description?: string
  enabled?: boolean
  cronExpression?: string
  cronTimezone?: string
  cronEnabled?: boolean
  createdBy?: string
}

export interface SchedulingAuditRecord {
  id: number
  recordTenantId?: number
  objectType?: string
  objectId?: string
  action?: string
  performedBy?: string
  detail?: string
  createdAt?: string
}

function withIdempotency(
  scope: string,
  payload?: unknown,
  config: Record<string, unknown> = {},
): Record<string, unknown> {
  return {
    ...config,
    idempotency: {
      scope,
      payload,
    },
  }
}

function withSubmitIdempotency(
  scope: string,
  payload?: unknown,
  config: Record<string, unknown> = {},
): Record<string, unknown> {
  return {
    ...config,
    idempotency: {
      scope,
      payload,
      mode: 'submit',
    },
  }
}

// ==================== TaskType - 任务类型 ====================

// 仅保留有值的查询参数，避免向后端传递 undefined / 空串。
function cleanParams(p: Record<string, any>): Record<string, any> {
  const out: Record<string, any> = {}
  for (const k of Object.keys(p)) {
    const v = p[k]
    if (v !== undefined && v !== null && v !== '') out[k] = v
  }
  return out
}

// 分页查询任务类型列表
export function taskTypeList(params: SchedulingTaskTypeListParams) {
  const apiParams = cleanParams({
    page: (params.current || 1) - 1,
    size: params.pageSize || 10,
    code: params.code,
    name: params.name,
  })
  return request.get('/scheduling/task-type/list', { params: apiParams }).then((res: any) => {
    return {
      records: res.content || [],
      total: res.totalElements || 0,
    }
  })
}

// 创建任务类型
export function createTaskType(data: SchedulingTaskTypePayload) {
  return request.post('/scheduling/task-type', data, withIdempotency('scheduling-task-type:create', data))
}

// 更新任务类型
export function updateTaskType(id: number, data: SchedulingTaskTypePayload) {
  return request.put(
    `/scheduling/task-type/${id}`,
    data,
    withIdempotency(`scheduling-task-type:update:${id}`, data),
  )
}

// 删除任务类型
export function deleteTaskType(id: number) {
  return request.delete(
    `/scheduling/task-type/${id}`,
    withIdempotency(`scheduling-task-type:delete:${id}`, { id }),
  )
}

// 查看任务类型详情
export function getTaskType(id: number) {
  return request.get(`/scheduling/task-type/${id}`)
}

// 获取已注册执行器标识列表（供任务类型表单下拉选择）
export function getExecutors() {
  return request.get<string[]>('/scheduling/executors')
}

// ==================== Task - 任务实例定义 ====================

// 分页查询任务列表
export function taskList(params: SchedulingTaskListParams) {
  const apiParams = cleanParams({
    page: (params.current || 1) - 1,
    size: params.pageSize || 10,
    typeId: params.typeId,
    code: params.code,
    name: params.name,
  })
  return request.get('/scheduling/task/list', { params: apiParams }).then((res: any) => {
    return {
      records: res.content || [],
      total: res.totalElements || 0,
    }
  })
}

// 创建任务实例
export function createTask(data: SchedulingTaskPayload) {
  return request.post('/scheduling/task', data, withIdempotency('scheduling-task:create', data))
}

// 更新任务
export function updateTask(id: number, data: SchedulingTaskPayload) {
  return request.put(`/scheduling/task/${id}`, data, withIdempotency(`scheduling-task:update:${id}`, data))
}

// 删除任务
export function deleteTask(id: number) {
  return request.delete(`/scheduling/task/${id}`, withIdempotency(`scheduling-task:delete:${id}`, { id }))
}

// 查看任务详情
export function getTask(id: number) {
  return request.get(`/scheduling/task/${id}`)
}

// 查询任务默认参数
export function getTaskParam(taskId: number) {
  return request.get(`/scheduling/task-param/${taskId}`)
}

// ==================== DAG（编排流程） ====================

// 分页查询 DAG 列表
export function dagList(params: SchedulingDagListParams) {
  const apiParams = cleanParams({
    page: (params.current || 1) - 1,
    size: params.pageSize || 10,
    code: params.code,
    name: params.name,
  })
  return request.get('/scheduling/dag/list', { params: apiParams }).then((res: any) => {
    return {
      records: res.content || [],
      total: res.totalElements || 0,
    }
  })
}

// 创建 DAG
export function createDag(data: SchedulingDagPayload) {
  return request.post('/scheduling/dag', data, withIdempotency('scheduling-dag:create', data))
}

// 更新 DAG
export function updateDag(id: number, data: SchedulingDagPayload) {
  return request.put(`/scheduling/dag/${id}`, data, withIdempotency(`scheduling-dag:update:${id}`, data))
}

// 删除 DAG
export function deleteDag(id: number) {
  return request.delete(`/scheduling/dag/${id}`, withIdempotency(`scheduling-dag:delete:${id}`, { id }))
}

// 查看 DAG 详情
export function getDag(id: number) {
  return request.get(`/scheduling/dag/${id}`)
}

// ==================== DAG Version ====================

// 创建 DAG 新版本
export function createDagVersion(dagId: number, data: any) {
  return request.post(
    `/scheduling/dag/${dagId}/version`,
    data,
    withIdempotency(`scheduling-dag-version:create:${dagId}`, { dagId, data }),
  )
}

// 更新 DAG 版本
export function updateDagVersion(dagId: number, versionId: number, data: any) {
  return request.put(
    `/scheduling/dag/${dagId}/version/${versionId}`,
    data,
    withIdempotency(`scheduling-dag-version:update:${dagId}:${versionId}`, { dagId, versionId, data }),
  )
}

// 查看 DAG 版本详情
export function getDagVersion(dagId: number, versionId: number) {
  return request.get(`/scheduling/dag/${dagId}/version/${versionId}`)
}

// 查询 DAG 所有版本
export function listDagVersions(dagId: number) {
  return request.get(`/scheduling/dag/${dagId}/version/list`)
}

// ==================== DAG Node（节点） ====================

// 添加 DAG 节点
export function createDagNode(dagId: number, versionId: number, data: any) {
  return request.post(
    `/scheduling/dag/${dagId}/version/${versionId}/node`,
    data,
    withIdempotency(`scheduling-dag-node:create:${dagId}:${versionId}`, { dagId, versionId, data }),
  )
}

// 更新节点
export function updateDagNode(dagId: number, versionId: number, nodeId: number, data: any) {
  return request.put(
    `/scheduling/dag/${dagId}/version/${versionId}/node/${nodeId}`,
    data,
    withIdempotency(
      `scheduling-dag-node:update:${dagId}:${versionId}:${nodeId}`,
      { dagId, versionId, nodeId, data },
    ),
  )
}

// 删除节点
export function deleteDagNode(dagId: number, versionId: number, nodeId: number) {
  return request.delete(
    `/scheduling/dag/${dagId}/version/${versionId}/node/${nodeId}`,
    withIdempotency(`scheduling-dag-node:delete:${dagId}:${versionId}:${nodeId}`, { dagId, versionId, nodeId }),
  )
}

// 查看节点详情
export function getDagNode(dagId: number, versionId: number, nodeId: number) {
  return request.get(`/scheduling/dag/${dagId}/version/${versionId}/node/${nodeId}`)
}

// 查询上游节点
export function getUpstreamNodes(dagId: number, versionId: number, nodeId: number) {
  return request.get(`/scheduling/dag/${dagId}/version/${versionId}/node/${nodeId}/up`)
}

// 查询下游节点
export function getDownstreamNodes(dagId: number, versionId: number, nodeId: number) {
  return request.get(`/scheduling/dag/${dagId}/version/${versionId}/node/${nodeId}/down`)
}

// 查询版本下的所有节点
export function getDagNodes(dagId: number, versionId: number) {
  return request.get(`/scheduling/dag/${dagId}/version/${versionId}/nodes`)
}

// 查询版本下的所有依赖
export function getDagEdges(dagId: number, versionId: number) {
  return request.get(`/scheduling/dag/${dagId}/version/${versionId}/edges`)
}

// ==================== DAG Edge（节点依赖） ====================

// 新增节点依赖
export function createDagEdge(dagId: number, versionId: number, data: any) {
  return request.post(
    `/scheduling/dag/${dagId}/version/${versionId}/edge`,
    data,
    withIdempotency(`scheduling-dag-edge:create:${dagId}:${versionId}`, { dagId, versionId, data }),
  )
}

// 删除节点依赖
export function deleteDagEdge(dagId: number, versionId: number, edgeId: number) {
  return request.delete(
    `/scheduling/dag/${dagId}/version/${versionId}/edge/${edgeId}`,
    withIdempotency(`scheduling-dag-edge:delete:${dagId}:${versionId}:${edgeId}`, { dagId, versionId, edgeId }),
  )
}

// ==================== DAG 调度触发/控制 ====================

// 触发整个 DAG 执行
export function triggerDag(dagId: number) {
  return request.post(
    `/scheduling/dag/${dagId}/trigger`,
    null,
    withSubmitIdempotency(`scheduling-dag:trigger:${dagId}`, { dagId }),
  )
}

// 暂停 DAG 执行
export function pauseDag(dagId: number) {
  return request.post(
    `/scheduling/dag/${dagId}/pause`,
    null,
    withSubmitIdempotency(`scheduling-dag:pause:${dagId}`, { dagId }),
  )
}

// 恢复 DAG 执行
export function resumeDag(dagId: number) {
  return request.post(
    `/scheduling/dag/${dagId}/resume`,
    null,
    withSubmitIdempotency(`scheduling-dag:resume:${dagId}`, { dagId }),
  )
}

// 强制停止 DAG 执行
export function stopDag(dagId: number) {
  return request.post(
    `/scheduling/dag/${dagId}/stop`,
    null,
    withSubmitIdempotency(`scheduling-dag:stop:${dagId}`, { dagId }),
  )
}

export function stopDagRun(dagId: number, runId: number) {
  return request.post(
    `/scheduling/dag/${dagId}/run/${runId}/stop`,
    null,
    withSubmitIdempotency(`scheduling-dag-run:stop:${dagId}:${runId}`, { dagId, runId }),
  )
}

// 对失败的 DAG 进行整体重试
export function retryDag(dagId: number) {
  return request.post(
    `/scheduling/dag/${dagId}/retry`,
    null,
    withSubmitIdempotency(`scheduling-dag:retry:${dagId}`, { dagId }),
  )
}

// 对指定失败运行进行重试
export function retryDagRun(dagId: number, runId: number) {
  return request.post(
    `/scheduling/dag/${dagId}/run/${runId}/retry`,
    null,
    withSubmitIdempotency(`scheduling-dag-run:retry:${dagId}:${runId}`, { dagId, runId }),
  )
}

// ==================== DAG 节点调度 ====================

// 单独触发节点执行
export function triggerNode(dagId: number, nodeId: number) {
  return request.post(
    `/scheduling/dag/${dagId}/node/${nodeId}/trigger`,
    null,
    withSubmitIdempotency(`scheduling-dag-node:trigger:${dagId}:${nodeId}`, { dagId, nodeId }),
  )
}

export function triggerDagRunNode(dagId: number, runId: number, nodeId: number) {
  return request.post(
    `/scheduling/dag/${dagId}/run/${runId}/node/${nodeId}/trigger`,
    null,
    withSubmitIdempotency(`scheduling-dag-run-node:trigger:${dagId}:${runId}:${nodeId}`, { dagId, runId, nodeId }),
  )
}

// 对失败节点重试
export function retryNode(dagId: number, nodeId: number) {
  return request.post(
    `/scheduling/dag/${dagId}/node/${nodeId}/retry`,
    null,
    withSubmitIdempotency(`scheduling-dag-node:retry:${dagId}:${nodeId}`, { dagId, nodeId }),
  )
}

export function retryDagRunNode(dagId: number, runId: number, nodeId: number) {
  return request.post(
    `/scheduling/dag/${dagId}/run/${runId}/node/${nodeId}/retry`,
    null,
    withSubmitIdempotency(`scheduling-dag-run-node:retry:${dagId}:${runId}:${nodeId}`, { dagId, runId, nodeId }),
  )
}

// 暂停节点
export function pauseNode(dagId: number, nodeId: number) {
  return request.post(
    `/scheduling/dag/${dagId}/node/${nodeId}/pause`,
    null,
    withSubmitIdempotency(`scheduling-dag-node:pause:${dagId}:${nodeId}`, { dagId, nodeId }),
  )
}

export function pauseDagRunNode(dagId: number, runId: number, nodeId: number) {
  return request.post(
    `/scheduling/dag/${dagId}/run/${runId}/node/${nodeId}/pause`,
    null,
    withSubmitIdempotency(`scheduling-dag-run-node:pause:${dagId}:${runId}:${nodeId}`, { dagId, runId, nodeId }),
  )
}

// 恢复节点
export function resumeNode(dagId: number, nodeId: number) {
  return request.post(
    `/scheduling/dag/${dagId}/node/${nodeId}/resume`,
    null,
    withSubmitIdempotency(`scheduling-dag-node:resume:${dagId}:${nodeId}`, { dagId, nodeId }),
  )
}

export function resumeDagRunNode(dagId: number, runId: number, nodeId: number) {
  return request.post(
    `/scheduling/dag/${dagId}/run/${runId}/node/${nodeId}/resume`,
    null,
    withSubmitIdempotency(`scheduling-dag-run-node:resume:${dagId}:${runId}:${nodeId}`, { dagId, runId, nodeId }),
  )
}

// ==================== 运行历史 ====================

// 查询 DAG 运行历史（支持状态、触发类型、运行编号、开始时间范围）
export function getDagRuns(dagId: number, params: any) {
  const apiParams: { [key: string]: any } = {
    page: (params.current || 1) - 1,
    size: params.pageSize || 10,
  }
  if (params.status != null && params.status !== '') apiParams.status = params.status
  if (params.triggerType != null && params.triggerType !== '') apiParams.triggerType = params.triggerType
  if (params.runNo != null && params.runNo !== '') apiParams.runNo = params.runNo
  if (params.startTimeFrom != null && params.startTimeFrom !== '') apiParams.startTimeFrom = params.startTimeFrom
  if (params.startTimeTo != null && params.startTimeTo !== '') apiParams.startTimeTo = params.startTimeTo
  return request.get(`/scheduling/dag/${dagId}/runs`, { params: apiParams }).then((res: any) => {
    return {
      records: res.content || [],
      total: res.totalElements || 0,
    }
  })
}

// DAG 运行统计（Run 级别：total/success/failed/avgDurationMs/p95/p99）
export function getDagStats(dagId: number) {
  return request.get<{
    total: number
    success: number
    failed: number
    avgDurationMs: number | null
    p95DurationMs: number | null
    p99DurationMs: number | null
  }>(`/scheduling/dag/${dagId}/stats`)
}

// 查看 DAG 单次运行详情
export function getDagRun(dagId: number, runId: number) {
  return request.get(`/scheduling/dag/${dagId}/run/${runId}`)
}

// 查看该次运行的所有节点执行记录
export function getDagRunNodes(dagId: number, runId: number) {
  return request.get(`/scheduling/dag/${dagId}/run/${runId}/nodes`)
}

// 查看单节点执行详情
export function getDagRunNode(dagId: number, runId: number, nodeId: number) {
  return request.get(`/scheduling/dag/${dagId}/run/${runId}/node/${nodeId}`)
}

// 查看任务实例执行日志
export function getTaskInstanceLog(instanceId: number) {
  return request.get(`/scheduling/task-instance/${instanceId}/log`)
}

// 查看任务执行历史
export function getTaskHistory(historyId: number) {
  return request.get(`/scheduling/task-history/${historyId}`)
}

// ==================== 审计与监控 ====================

// 分页查询操作审计记录
export function auditList(params: SchedulingAuditListParams) {
  const apiParams = cleanParams({
    page: (params.current || 1) - 1,
    size: params.pageSize || 10,
    objectType: params.objectType,
    action: params.action,
  })
  return request.get('/scheduling/audit/list', { params: apiParams }).then((res: any) => {
    return {
      records: (res.content || []) as SchedulingAuditRecord[],
      total: res.totalElements || 0,
    }
  })
}
