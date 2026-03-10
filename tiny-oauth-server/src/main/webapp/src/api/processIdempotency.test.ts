import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  post: vi.fn(),
  put: vi.fn(),
  get: vi.fn(),
  delete: vi.fn(),
}))

vi.mock('@/utils/request', () => ({
  default: {
    post: mocks.post,
    put: mocks.put,
    get: mocks.get,
    delete: mocks.delete,
  },
}))

describe('process API idempotency', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.clearAllMocks()
  })

  it('should attach idempotency config for process mutations', async () => {
    const { processApi, deploymentApi, instanceApi, taskApi, tenantApi } = await import('@/api/process')
    const deployInfo = { bpmnXml: '<xml />', deploymentName: 'demo', key: 'demo' }
    const startData = { processKey: 'demo', variables: { orderId: 'A-1' } }
    const completeData = { taskId: 'task-2', variables: { approved: true } }
    const tenantInfo = { id: 'tenant-a', name: 'Tenant A' }

    await processApi.deleteProcessDefinition('def-1')
    await deploymentApi.deployProcess('<xml />')
    await deploymentApi.deployProcessWithInfo(deployInfo)
    await deploymentApi.deleteDeployment('dep-1')
    await instanceApi.startProcess(startData)
    await instanceApi.suspendInstance('inst-1')
    await instanceApi.activateInstance('inst-1')
    await instanceApi.deleteInstance('inst-1')
    await instanceApi.claimTask('task-1', 'alice')
    await instanceApi.completeTask('task-1', { approved: true })
    await taskApi.claimTask('task-2', 'bob')
    await taskApi.completeTask(completeData)
    await tenantApi.createTenant(tenantInfo)

    expect(mocks.delete).toHaveBeenCalledWith('/process/definition/def-1', {
      idempotency: {
        scope: 'process-definition:delete:def-1',
        payload: { processDefinitionId: 'def-1' },
      },
    })
    expect(mocks.post).toHaveBeenCalledWith('/process/deploy', '<xml />', {
      headers: { 'Content-Type': 'application/xml' },
      idempotency: {
        scope: 'process-deploy:create',
        payload: '<xml />',
        mode: 'submit',
      },
    })
    expect(mocks.post).toHaveBeenCalledWith('/process/deploy-with-info', deployInfo, {
      idempotency: {
        scope: 'process-deploy-with-info:create',
        payload: deployInfo,
        mode: 'submit',
      },
    })
    expect(mocks.delete).toHaveBeenCalledWith('/process/deployment/dep-1', {
      idempotency: {
        scope: 'process-deployment:delete:dep-1',
        payload: { deploymentId: 'dep-1' },
      },
    })
    expect(mocks.post).toHaveBeenCalledWith('/process/start', startData.variables, {
      params: { processKey: 'demo' },
      idempotency: {
        scope: 'process-instance:start:demo',
        payload: startData,
        mode: 'submit',
      },
    })
    expect(mocks.post).toHaveBeenCalledWith('/process/instance/inst-1/suspend', null, {
      idempotency: {
        scope: 'process-instance:suspend:inst-1',
        payload: { instanceId: 'inst-1' },
        mode: 'submit',
      },
    })
    expect(mocks.post).toHaveBeenCalledWith('/process/task/task-1/claim', null, {
      params: { userId: 'alice' },
      idempotency: {
        scope: 'process-task:claim:task-1',
        payload: { taskId: 'task-1', userId: 'alice' },
        mode: 'submit',
      },
    })
    expect(mocks.post).toHaveBeenCalledWith('/process/task/task-2/complete', completeData.variables, {
      idempotency: {
        scope: 'process-task:complete:task-2',
        payload: completeData,
        mode: 'submit',
      },
    })
    expect(mocks.post).toHaveBeenCalledWith('/process/tenant', tenantInfo, {
      idempotency: {
        scope: 'process-tenant:create:tenant-a',
        payload: tenantInfo,
      },
    })
  })
})
