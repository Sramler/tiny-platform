import { describe, expect, it } from 'vitest'

import type { ExportTask } from '@/api/export'
import {
  buildDownloadUrl,
  exportTaskRowKey,
  filterTasks,
  formatDateTime,
  paginateTasks,
  statusLabel,
} from '@/views/export/taskUtils'

describe('taskUtils', () => {
  const tasks: ExportTask[] = [
    { taskId: 'task-001', userId: '1001', username: 'Alice', status: 'SUCCESS' },
    { taskId: 'task-002', userId: '1002', username: 'Bob', status: 'RUNNING' },
    { taskId: 'task-003', userId: '2001', username: 'Carol', status: 'FAILED' },
  ]

  it('should filter tasks by taskId, user and status', () => {
    expect(filterTasks(tasks, { taskId: '002', username: '', status: undefined })).toEqual([tasks[1]])
    expect(filterTasks(tasks, { taskId: '', username: '1001', status: undefined })).toEqual([tasks[0]])
    expect(filterTasks(tasks, { taskId: '', username: 'bob', status: 'RUNNING' })).toEqual([tasks[1]])
    expect(filterTasks(tasks, { taskId: '', username: '', status: 'FAILED' })).toEqual([tasks[2]])
  })

  it('should paginate filtered tasks deterministically', () => {
    expect(paginateTasks(tasks, 1, 2)).toEqual(tasks.slice(0, 2))
    expect(paginateTasks(tasks, 2, 2)).toEqual(tasks.slice(2, 3))
  })

  it('should build download url with optional api base url', () => {
    expect(buildDownloadUrl('task-1', 'http://localhost:9000/')).toBe(
      'http://localhost:9000/export/task/task-1/download',
    )
    expect(buildDownloadUrl('task-1', '')).toBe('/export/task/task-1/download')
  })

  it('should format status label and row key', () => {
    expect(statusLabel('SUCCESS')).toBe('成功')
    expect(statusLabel('UNKNOWN')).toBe('UNKNOWN')
    expect(statusLabel(undefined)).toBe('-')
    expect(exportTaskRowKey({ taskId: 'task-9', userId: '1', status: 'SUCCESS' })).toBe('task-9')
    expect(exportTaskRowKey({ taskId: '', id: 12, userId: '1', status: 'SUCCESS' })).toBe('12')
  })

  it('should format date time safely', () => {
    expect(formatDateTime(undefined)).toBe('-')
    expect(formatDateTime('bad-time')).toBe('-')
    expect(formatDateTime('2026-03-06T09:30:15')).toContain('2026')
  })
})
