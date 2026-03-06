import type { ExportTask } from '@/api/export'

export interface ExportTaskQuery {
  taskId: string
  username: string
  status?: string
}

export function filterTasks(tasks: ExportTask[], query: ExportTaskQuery): ExportTask[] {
  const taskId = (query.taskId || '').trim().toLowerCase()
  const username = (query.username || '').trim().toLowerCase()
  const status = query.status
  return tasks.filter((task) => {
    const matchTask = taskId ? (task.taskId || '').toLowerCase().includes(taskId) : true
    const matchUser = username
      ? (task.username || '').toLowerCase().includes(username) ||
        (task.userId || '').toLowerCase().includes(username)
      : true
    const matchStatus = status ? task.status === status : true
    return matchTask && matchUser && matchStatus
  })
}

export function paginateTasks(tasks: ExportTask[], current: number, pageSize: number): ExportTask[] {
  const safeCurrent = Number(current) || 1
  const safePageSize = Number(pageSize) || 10
  const start = (safeCurrent - 1) * safePageSize
  return tasks.slice(start, start + safePageSize)
}

export function exportTaskRowKey(record: ExportTask & { id?: string | number }): string {
  return String(record.taskId || record.id)
}

export function buildDownloadUrl(
  taskId: string,
  apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || '') as string,
): string {
  const base = apiBaseUrl.replace(/\/$/, '')
  if (base) {
    return `${base}/export/task/${taskId}/download`
  }
  return `/export/task/${taskId}/download`
}

export function statusLabel(status?: string): string {
  switch (status) {
    case 'PENDING':
      return '排队中'
    case 'RUNNING':
      return '运行中'
    case 'SUCCESS':
      return '成功'
    case 'FAILED':
      return '失败'
    case 'CANCELED':
      return '已取消'
    default:
      return status || '-'
  }
}

export function formatDateTime(val?: string): string {
  if (!val) return '-'
  const date = new Date(val)
  if (Number.isNaN(date.getTime())) return '-'
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  })
}
