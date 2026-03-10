import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
}))

vi.mock('@/utils/request', () => ({
  default: {
    get: requestMocks.get,
    post: requestMocks.post,
  },
}))

describe('process API (read & validate)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('processApi', () => {
    it('should request process definitions with optional tenantId', async () => {
      requestMocks.get.mockResolvedValue([{ id: 'def-1', key: 'demo', name: 'Demo' }])
      const { processApi } = await import('@/api/process')

      const result = await processApi.getProcessDefinitions('tenant-1')

      expect(requestMocks.get).toHaveBeenCalledWith('/process/definitions', { params: { tenantId: 'tenant-1' } })
      expect(result).toHaveLength(1)
      expect(result[0]?.key).toBe('demo')
    })

    it('should request process definition xml', async () => {
      requestMocks.get.mockResolvedValue({ bpmnXml: '<bpmn/>' })
      const { processApi } = await import('@/api/process')

      const result = await processApi.getProcessDefinitionXml('def-1')

      expect(requestMocks.get).toHaveBeenCalledWith('/process/definition/def-1/xml')
      expect(result.bpmnXml).toBe('<bpmn/>')
    })

    it('should validate BPMN xml', async () => {
      requestMocks.post.mockResolvedValue({ valid: true, message: 'ok' })
      const { processApi } = await import('@/api/process')

      const result = await processApi.validateBpmnXml('<bpmn/>')

      expect(requestMocks.post).toHaveBeenCalledWith('/process/validate', '<bpmn/>', {
        headers: { 'Content-Type': 'application/xml' },
      })
      expect(result.valid).toBe(true)
    })
  })

  describe('deploymentApi', () => {
    it('should request deployments with optional tenantId', async () => {
      requestMocks.get.mockResolvedValue([{ id: 'dep-1', name: 'Deploy 1' }])
      const { deploymentApi } = await import('@/api/process')

      const result = await deploymentApi.getDeployments('tenant-1')

      expect(requestMocks.get).toHaveBeenCalledWith('/process/deployments', { params: { tenantId: 'tenant-1' } })
      expect(result).toHaveLength(1)
    })
  })

  describe('instanceApi', () => {
    it('should request process instances with optional params', async () => {
      requestMocks.get.mockResolvedValue([{ id: 'inst-1', state: 'active' }])
      const { instanceApi } = await import('@/api/process')

      const result = await instanceApi.getProcessInstances('tenant-1', 'active')

      expect(requestMocks.get).toHaveBeenCalledWith('/process/instances', {
        params: { tenantId: 'tenant-1', state: 'active' },
      })
      expect(result).toHaveLength(1)
    })

    it('should request instance tasks', async () => {
      requestMocks.get.mockResolvedValue([{ id: 'task-1', name: 'Approve' }])
      const { instanceApi } = await import('@/api/process')

      const result = await instanceApi.getTasks('inst-1')

      expect(requestMocks.get).toHaveBeenCalledWith('/process/instance/inst-1/tasks')
      expect(result).toHaveLength(1)
    })
  })

  describe('taskApi', () => {
    it('should request tasks with optional assignee and tenantId', async () => {
      requestMocks.get.mockResolvedValue([{ id: 'task-1', assignee: 'alice' }])
      const { taskApi } = await import('@/api/process')

      const result = await taskApi.getTasks('alice', 'tenant-1')

      expect(requestMocks.get).toHaveBeenCalledWith('/process/tasks', {
        params: { assignee: 'alice', tenantId: 'tenant-1' },
      })
      expect(result).toHaveLength(1)
    })
  })

  describe('historyApi', () => {
    it('should request historic instances', async () => {
      requestMocks.get.mockResolvedValue([{ id: 'inst-1', endTime: '2026-01-01' }])
      const { historyApi } = await import('@/api/process')

      await historyApi.getHistoricInstances('tenant-1')

      expect(requestMocks.get).toHaveBeenCalledWith('/process/history/instances', {
        params: { tenantId: 'tenant-1' },
      })
    })

    it('should request historic tasks by process instance id', async () => {
      requestMocks.get.mockResolvedValue([])
      const { historyApi } = await import('@/api/process')

      await historyApi.getHistoricTasks('inst-1')

      expect(requestMocks.get).toHaveBeenCalledWith('/process/history/tasks', {
        params: { processInstanceId: 'inst-1' },
      })
    })
  })

  describe('tenantApi', () => {
    it('should request tenant list', async () => {
      requestMocks.get.mockResolvedValue([{ id: 't1', name: 'Tenant 1' }])
      const { tenantApi } = await import('@/api/process')

      await tenantApi.getTenants()

      expect(requestMocks.get).toHaveBeenCalledWith('/process/tenants')
    })
  })

  describe('maintenanceApi', () => {
    it('should request engine info', async () => {
      requestMocks.get.mockResolvedValue({ name: 'Camunda', version: '7.20' })
      const { maintenanceApi } = await import('@/api/process')

      await maintenanceApi.getEngineInfo()

      expect(requestMocks.get).toHaveBeenCalledWith('/process/engine/info')
    })

    it('should request health check', async () => {
      requestMocks.get.mockResolvedValue({ status: 'UP', message: 'ok', timestamp: 123456 })
      const { maintenanceApi } = await import('@/api/process')

      const result = await maintenanceApi.healthCheck()

      expect(requestMocks.get).toHaveBeenCalledWith('/process/health')
      expect(result.status).toBe('UP')
    })
  })
})
