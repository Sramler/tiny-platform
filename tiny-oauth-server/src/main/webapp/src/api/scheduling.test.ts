import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  delete: vi.fn(),
}))

vi.mock('@/utils/request', () => ({
  default: {
    get: mocks.get,
    post: mocks.post,
    put: mocks.put,
    delete: mocks.delete,
  },
}))

describe('scheduling API', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.clearAllMocks()
  })

  describe('TaskType', () => {
    it('should request task type list with pagination', async () => {
      mocks.get.mockResolvedValue({ content: [{ id: 1, code: 'billing' }], totalElements: 1 })
      const { taskTypeList } = await import('@/api/scheduling')

      const result = await taskTypeList({ current: 1, pageSize: 10, tenantId: 88 })

      expect(mocks.get).toHaveBeenCalledWith('/scheduling/task-type/list', expect.objectContaining({
        params: { page: 0, size: 10 },
      }))
      expect(result.records).toEqual([{ id: 1, code: 'billing' }])
      expect(result.total).toBe(1)
    })

    it('should request single task type detail', async () => {
      mocks.get.mockResolvedValue({ id: 1, code: 'billing', name: 'Billing' })
      const { getTaskType } = await import('@/api/scheduling')

      const result = await getTaskType(1)

      expect(mocks.get).toHaveBeenCalledWith('/scheduling/task-type/1')
      expect(result).toEqual({ id: 1, code: 'billing', name: 'Billing' })
    })

    it('should request executor identifiers', async () => {
      mocks.get.mockResolvedValue(['logging', 'shell'])
      const { getExecutors } = await import('@/api/scheduling')

      const result = await getExecutors()

      expect(mocks.get).toHaveBeenCalledWith('/scheduling/executors')
      expect(result).toEqual(['logging', 'shell'])
    })
  })

  describe('Task', () => {
    it('should request task list with pagination', async () => {
      mocks.get.mockResolvedValue({ content: [{ id: 1, code: 'daily' }], totalElements: 1 })
      const { taskList } = await import('@/api/scheduling')

      const result = await taskList({ current: 1, pageSize: 10, tenantId: 88 })

      expect(mocks.get).toHaveBeenCalledWith('/scheduling/task/list', expect.objectContaining({
        params: { page: 0, size: 10 },
      }))
      expect(result.records).toEqual([{ id: 1, code: 'daily' }])
    })

    it('should request single task detail', async () => {
      mocks.get.mockResolvedValue({ id: 1, code: 'daily', name: 'Daily Job' })
      const { getTask } = await import('@/api/scheduling')

      const result = await getTask(1)

      expect(mocks.get).toHaveBeenCalledWith('/scheduling/task/1')
      expect(result).toEqual({ id: 1, code: 'daily', name: 'Daily Job' })
    })
  })

  describe('DAG', () => {
    it('should request DAG list with pagination', async () => {
      mocks.get.mockResolvedValue({ content: [{ id: 1, code: 'etl' }], totalElements: 1 })
      const { dagList } = await import('@/api/scheduling')

      const result = await dagList({ current: 1, pageSize: 10, tenantId: 88 })

      expect(mocks.get).toHaveBeenCalledWith('/scheduling/dag/list', expect.objectContaining({
        params: { page: 0, size: 10 },
      }))
      expect(result.records).toEqual([{ id: 1, code: 'etl' }])
    })

    it('should request single DAG detail', async () => {
      mocks.get.mockResolvedValue({ id: 1, code: 'etl', name: 'ETL Pipeline' })
      const { getDag } = await import('@/api/scheduling')

      const result = await getDag(1)

      expect(mocks.get).toHaveBeenCalledWith('/scheduling/dag/1')
      expect(result).toEqual({ id: 1, code: 'etl', name: 'ETL Pipeline' })
    })

    it('should request DAG version list', async () => {
      mocks.get.mockResolvedValue([{ id: 1, versionName: 'v1' }])
      const { listDagVersions } = await import('@/api/scheduling')

      const result = await listDagVersions(10)

      expect(mocks.get).toHaveBeenCalledWith('/scheduling/dag/10/version/list')
      expect(result).toEqual([{ id: 1, versionName: 'v1' }])
    })
  })

  describe('DAG trigger and control', () => {
    it('should trigger DAG execution', async () => {
      mocks.post.mockResolvedValue({ id: 1, status: 'RUNNING' })
      const { triggerDag } = await import('@/api/scheduling')

      const result = await triggerDag(10)

      expect(mocks.post).toHaveBeenCalledWith(
        '/scheduling/dag/10/trigger',
        null,
        expect.objectContaining({
          idempotency: {
            scope: 'scheduling-dag:trigger:10',
            payload: { dagId: 10 },
            mode: 'submit',
          },
        }),
      )
      expect(result).toEqual({ id: 1, status: 'RUNNING' })
    })

    it('should stop specified DAG run', async () => {
      mocks.post.mockResolvedValue({ ok: true })
      const { stopDagRun } = await import('@/api/scheduling')

      const result = await stopDagRun(10, 77)

      expect(mocks.post).toHaveBeenCalledWith(
        '/scheduling/dag/10/run/77/stop',
        null,
        {
          idempotency: {
            scope: 'scheduling-dag-run:stop:10:77',
            payload: { dagId: 10, runId: 77 },
            mode: 'submit',
          },
        },
      )
      expect(result).toEqual({ ok: true })
    })

    it('should retry specified DAG run', async () => {
      mocks.post.mockResolvedValue({ ok: true })
      const { retryDagRun } = await import('@/api/scheduling')

      const result = await retryDagRun(10, 77)

      expect(mocks.post).toHaveBeenCalledWith(
        '/scheduling/dag/10/run/77/retry',
        null,
        {
          idempotency: {
            scope: 'scheduling-dag-run:retry:10:77',
            payload: { dagId: 10, runId: 77 },
            mode: 'submit',
          },
        },
      )
      expect(result).toEqual({ ok: true })
    })
  })

  describe('DAG runs and stats', () => {
    it('should request DAG runs with pagination', async () => {
      mocks.get.mockResolvedValue({ content: [{ id: 1, status: 'SUCCESS' }], totalElements: 1 })
      const { getDagRuns } = await import('@/api/scheduling')

      const result = await getDagRuns(10, { current: 1, pageSize: 10 })

      expect(mocks.get).toHaveBeenCalledWith('/scheduling/dag/10/runs', expect.objectContaining({
        params: expect.objectContaining({ page: 0, size: 10 }),
      }))
      expect(result.records).toEqual([{ id: 1, status: 'SUCCESS' }])
    })

    it('should request audit list without tenant override', async () => {
      mocks.get.mockResolvedValue({ content: [{ id: 1, action: 'TRIGGER' }], totalElements: 1 })
      const { auditList } = await import('@/api/scheduling')

      const result = await auditList({ current: 1, pageSize: 10, tenantId: 88, action: 'TRIGGER' })

      expect(mocks.get).toHaveBeenCalledWith('/scheduling/audit/list', {
        params: { page: 0, size: 10, action: 'TRIGGER' },
      })
      expect(result.records).toEqual([{ id: 1, action: 'TRIGGER' }])
      expect(result.total).toBe(1)
    })

    it('should request DAG stats', async () => {
      mocks.get.mockResolvedValue({ total: 10, success: 8, failed: 2, avgDurationMs: 1200 })
      const { getDagStats } = await import('@/api/scheduling')

      const result = await getDagStats(10)

      expect(mocks.get).toHaveBeenCalledWith('/scheduling/dag/10/stats')
      expect(result).toEqual({ total: 10, success: 8, failed: 2, avgDurationMs: 1200 })
    })

    it('should request DAG run detail', async () => {
      mocks.get.mockResolvedValue({ id: 1, runId: 77, status: 'SUCCESS' })
      const { getDagRun } = await import('@/api/scheduling')

      const result = await getDagRun(10, 77)

      expect(mocks.get).toHaveBeenCalledWith('/scheduling/dag/10/run/77')
      expect(result).toEqual({ id: 1, runId: 77, status: 'SUCCESS' })
    })
  })

  describe('DAG node and edge', () => {
    it('should request DAG nodes', async () => {
      mocks.get.mockResolvedValue([{ id: 1, nodeCode: 'extract' }])
      const { getDagNodes } = await import('@/api/scheduling')

      const result = await getDagNodes(10, 4)

      expect(mocks.get).toHaveBeenCalledWith('/scheduling/dag/10/version/4/nodes')
      expect(result).toEqual([{ id: 1, nodeCode: 'extract' }])
    })

    it('should request DAG edges', async () => {
      mocks.get.mockResolvedValue([{ fromNodeCode: 'extract', toNodeCode: 'load' }])
      const { getDagEdges } = await import('@/api/scheduling')

      const result = await getDagEdges(10, 4)

      expect(mocks.get).toHaveBeenCalledWith('/scheduling/dag/10/version/4/edges')
      expect(result).toEqual([{ fromNodeCode: 'extract', toNodeCode: 'load' }])
    })

    it('should trigger/retry/pause/resume node in specified DAG run', async () => {
      mocks.post.mockResolvedValue({ ok: true })
      const {
        triggerDagRunNode,
        retryDagRunNode,
        pauseDagRunNode,
        resumeDagRunNode,
      } = await import('@/api/scheduling')

      await triggerDagRunNode(10, 77, 11)
      await retryDagRunNode(10, 77, 11)
      await pauseDagRunNode(10, 77, 11)
      await resumeDagRunNode(10, 77, 11)

      expect(mocks.post).toHaveBeenCalledWith(
        '/scheduling/dag/10/run/77/node/11/trigger',
        null,
        expect.objectContaining({
          idempotency: {
            scope: 'scheduling-dag-run-node:trigger:10:77:11',
            payload: { dagId: 10, runId: 77, nodeId: 11 },
            mode: 'submit',
          },
        }),
      )
      expect(mocks.post).toHaveBeenCalledWith(
        '/scheduling/dag/10/run/77/node/11/retry',
        null,
        expect.objectContaining({
          idempotency: {
            scope: 'scheduling-dag-run-node:retry:10:77:11',
            payload: { dagId: 10, runId: 77, nodeId: 11 },
            mode: 'submit',
          },
        }),
      )
      expect(mocks.post).toHaveBeenCalledWith(
        '/scheduling/dag/10/run/77/node/11/pause',
        null,
        expect.objectContaining({
          idempotency: {
            scope: 'scheduling-dag-run-node:pause:10:77:11',
            payload: { dagId: 10, runId: 77, nodeId: 11 },
            mode: 'submit',
          },
        }),
      )
      expect(mocks.post).toHaveBeenCalledWith(
        '/scheduling/dag/10/run/77/node/11/resume',
        null,
        expect.objectContaining({
          idempotency: {
            scope: 'scheduling-dag-run-node:resume:10:77:11',
            payload: { dagId: 10, runId: 77, nodeId: 11 },
            mode: 'submit',
          },
        }),
      )
    })
  })
})
