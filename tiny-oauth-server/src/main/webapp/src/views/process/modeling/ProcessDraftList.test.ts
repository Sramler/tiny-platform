import { mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ProcessModel } from '@/api/process'

const processModelApiMocks = vi.hoisted(() => ({
  listModelGroups: vi.fn(),
  createModel: vi.fn(),
  validateModel: vi.fn(),
  deployModel: vi.fn(),
  deleteModel: vi.fn(),
}))

const messageMocks = vi.hoisted(() => ({
  error: vi.fn(),
  success: vi.fn(),
  warning: vi.fn(),
}))

const modalMocks = vi.hoisted(() => ({
  confirm: vi.fn(),
}))

vi.mock('@/api/process', () => ({
  processModelApi: processModelApiMocks,
}))

vi.mock('@/utils/bpmn/modeler', () => ({
  EMPTY_PROCESS_XML: '<bpmn:definitions><bpmn:process id="NewProcess" name="新建流程" /><bpmndi:BPMNPlane bpmnElement="NewProcess" /></bpmn:definitions>',
}))

vi.mock('ant-design-vue', () => ({
  Modal: modalMocks,
  message: messageMocks,
}))

const ButtonStub = defineComponent({
  emits: ['click'],
  template: '<button @click="$emit(\'click\')"><slot /></button>',
})

const PassThrough = defineComponent({
  template: '<div><slot /></div>',
})

const DropdownStub = defineComponent({
  template: '<div class="dropdown-stub"><slot /><slot name="overlay" /></div>',
})

const AlertStub = defineComponent({
  props: ['message'],
  template: '<div class="alert">{{ message }}</div>',
})

const EmptyStub = defineComponent({
  props: ['description'],
  template: '<div class="empty">{{ description }}</div>',
})

const TableStub = defineComponent({
  props: ['columns', 'dataSource', 'expandedRowKeys'],
  template: `
    <div class="table-stub" :class="$attrs.class">
      <div
        v-for="record in dataSource"
        :key="record.groupKey || record.id"
        :class="record.groupKey ? 'draft-asset-row' : 'draft-version-row'"
        :data-group-key="record.groupKey || undefined"
        :data-model-id="record.id || undefined"
      >
        <div v-for="column in columns" :key="column.key || column.dataIndex" class="table-cell-stub">
          <slot name="bodyCell" :column="column" :record="record">
            <span>{{ record[column.dataIndex || column.key] }}</span>
          </slot>
        </div>
        <div
          v-if="record.groupKey && expandedRowKeys && expandedRowKeys.includes(record.groupKey)"
          class="expanded-row"
        >
          <slot name="expandedRowRender" :record="record" />
        </div>
      </div>
    </div>
  `,
})

import ProcessDraftList from '@/views/process/modeling/ProcessDraftList.vue'

const draftModel: ProcessModel = {
  id: 7,
  modelKey: 'leave_process',
  name: 'Leave Process',
  scopeType: 'TENANT',
  recordTenantId: '11',
  status: 'DRAFT',
  runtimeState: 'NOT_DEPLOYED',
  version: 1,
  bpmnXml: '<bpmn />',
  validationStatus: 'NOT_VALIDATED',
  deploymentId: undefined as string | undefined,
  updatedBy: 'alice',
  updatedAt: '2026-05-11T10:00:00',
  lockVersion: 0,
}

function model(overrides: Partial<ProcessModel>): ProcessModel {
  return { ...draftModel, ...overrides }
}

const leaveV2 = model({
  id: 8,
  version: 2,
  updatedAt: '2026-05-11T11:00:00',
})

const leaveV1 = model({
  id: 7,
  deploymentId: 'dep-1',
  runtimeState: 'CURRENT_RUNTIME',
  version: 1,
  updatedAt: '2026-05-10T10:00:00',
})

const expenseV1 = model({
  id: 9,
  modelKey: 'expense_process',
  name: 'Expense Process',
  version: 1,
  updatedAt: '2026-05-09T10:00:00',
})

const platformConfigChangeV1 = model({
  id: 12,
  modelKey: 'platform_config_change',
  name: '生产配置变更审批',
  scopeType: 'PLATFORM',
  recordTenantId: null,
  status: 'VALIDATED',
  validationStatus: 'PASSED',
  version: 1,
  updatedAt: '2026-05-13T12:51:07',
})

const draftGroups = [
  {
    modelKey: 'leave_process',
    name: 'Leave Process',
    scopeType: 'TENANT',
    recordTenantId: '11',
    latestVersion: 2,
    latestDesignVersion: 2,
    latestStatus: 'DRAFT',
    currentRuntimeVersion: 1,
    currentDeploymentId: 'dep-1',
    hasUndeployedChanges: true,
    versionCount: 2,
    updatedAt: '2026-05-11T11:00:00',
    updatedBy: 'alice',
    latestModel: leaveV2,
    versions: [leaveV2, leaveV1],
  },
  {
    modelKey: 'expense_process',
    name: 'Expense Process',
    scopeType: 'TENANT',
    recordTenantId: '11',
    latestVersion: 1,
    latestDesignVersion: 1,
    latestStatus: 'DRAFT',
    currentRuntimeVersion: null,
    currentDeploymentId: null,
    hasUndeployedChanges: true,
    versionCount: 1,
    updatedAt: '2026-05-09T10:00:00',
    updatedBy: 'alice',
    latestModel: expenseV1,
    versions: [expenseV1],
  },
]

const platformConfigChangeGroup = {
  modelKey: 'platform_config_change',
  name: '生产配置变更审批',
  scopeType: 'PLATFORM',
  recordTenantId: null,
  latestVersion: 1,
  latestDesignVersion: 1,
  latestStatus: 'VALIDATED',
  currentRuntimeVersion: null,
  currentDeploymentId: null,
  hasUndeployedChanges: true,
  versionCount: 1,
  updatedAt: '2026-05-13T12:51:07',
  updatedBy: 'alice',
  latestModel: platformConfigChangeV1,
  versions: [platformConfigChangeV1],
}

async function flushPromises() {
  for (let index = 0; index < 40; index += 1) {
    await Promise.resolve()
    await nextTick()
  }
}

function mountDraftList() {
  return mount(ProcessDraftList, {
    global: {
      stubs: {
        'a-button': ButtonStub,
        'a-dropdown': DropdownStub,
        'a-alert': AlertStub,
        'a-empty': EmptyStub,
        'a-table': TableStub,
        'a-tag': PassThrough,
        CheckCircleOutlined: PassThrough,
        DeleteOutlined: PassThrough,
        DownOutlined: PassThrough,
        EditOutlined: PassThrough,
        FileAddOutlined: PassThrough,
        PlusOutlined: PassThrough,
        ReloadOutlined: PassThrough,
        RocketOutlined: PassThrough,
        UpOutlined: PassThrough,
      },
    },
  })
}

describe('ProcessDraftList.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    processModelApiMocks.listModelGroups.mockResolvedValue(draftGroups)
    processModelApiMocks.createModel.mockResolvedValue({ ...draftModel, id: 8, modelKey: 'Process_1700000000000' })
    processModelApiMocks.validateModel.mockResolvedValue({
      id: 7,
      valid: true,
      message: '校验通过',
      warnings: [],
      validationStatus: 'PASSED',
    })
    processModelApiMocks.deployModel.mockResolvedValue({
      id: 7,
      deploymentId: 'dep-1',
      processDefinitionKey: 'leave_process',
      status: 'DEPLOYED',
      message: '部署成功',
    })
    processModelApiMocks.deleteModel.mockResolvedValue(undefined)
  })

  it('should load and render process model drafts', async () => {
    const wrapper = mountDraftList()
    await flushPromises()

    expect(processModelApiMocks.listModelGroups).toHaveBeenCalled()
    expect(wrapper.text()).toContain('流程草稿')
    expect(wrapper.text()).toContain('2 个草稿流程 · 3 个版本 · 8 个可创建平台模板')
    expect(wrapper.text()).toContain('租户开通审批')
    expect(wrapper.text()).toContain('平台模板')
    expect(wrapper.text()).toContain('可创建')
    expect(wrapper.text()).toContain('Leave Process')
    expect(wrapper.text()).toContain('leave_process')
    expect(wrapper.text()).toContain('Expense Process')
    expect(wrapper.text()).toContain('租户 11')
    expect(wrapper.text()).toContain('v1')
    expect(wrapper.text()).toContain('有变更')
    expect(wrapper.find('.draft-table').exists()).toBe(true)
    expect(wrapper.find('.draft-asset-list').exists()).toBe(false)
    expect(wrapper.findAll('.draft-asset-row')).toHaveLength(10)
    expect(wrapper.findAll('.draft-version-row')).toHaveLength(0)
  })

  it('should expand process asset versions without creating extra asset rows', async () => {
    const wrapper = mountDraftList()
    await flushPromises()

    const versionsButton = wrapper.findAll('button').find((button) => button.text().includes('2 个版本'))
    expect(versionsButton).toBeDefined()
    await versionsButton!.trigger('click')
    await flushPromises()

    expect(wrapper.findAll('.draft-asset-row')).toHaveLength(10)
    expect(wrapper.findAll('.draft-version-row')).toHaveLength(2)
    expect(wrapper.text()).toContain('v2')
    expect(wrapper.text()).toContain('v1')
    expect(wrapper.text()).toContain('当前运行')
    expect(wrapper.text()).toContain('未部署')
  })

  it('should emit open-design for latest model of selected process asset', async () => {
    const wrapper = mountDraftList()
    await flushPromises()

    const leaveRow = wrapper.find('[data-group-key="TENANT:11:leave_process"]')
    const designButton = leaveRow.findAll('button').find((button) => button.text().includes('设计最新'))
    expect(designButton).toBeDefined()
    await designButton!.trigger('click')

    expect(wrapper.emitted('open-design')?.[0]?.[0]).toMatchObject({ id: 8, modelKey: 'leave_process', version: 2 })
  })

  it('should emit open-design for selected version from expanded versions', async () => {
    const wrapper = mountDraftList()
    await flushPromises()

    const versionsButton = wrapper.findAll('button').find((button) => button.text().includes('2 个版本'))
    await versionsButton!.trigger('click')
    await flushPromises()

    const versionOneRow = wrapper.find('[data-model-id="7"]')
    const designButton = versionOneRow.findAll('button').find((button) => button.text().includes('设计'))
    expect(designButton).toBeDefined()
    await designButton!.trigger('click')

    expect(wrapper.emitted('open-design')?.[0]?.[0]).toMatchObject({ id: 7, modelKey: 'leave_process', version: 1 })
  })

  it('should open an unsaved draft in designer without creating a database row', async () => {
    vi.spyOn(Date, 'now').mockReturnValue(1700000000000)
    const wrapper = mountDraftList()
    await flushPromises()

    const createButton = wrapper.findAll('button').find((button) => button.text().includes('新建草稿'))
    expect(createButton).toBeDefined()
    await createButton!.trigger('click')
    await flushPromises()

    expect(processModelApiMocks.createModel).not.toHaveBeenCalled()
    expect(messageMocks.success).not.toHaveBeenCalledWith('流程草稿已创建')
    expect(wrapper.emitted('open-new-draft')?.[0]?.[0]).toMatchObject({
      modelKey: 'Process_1700000000000',
      name: '新建流程草稿',
      bpmnXml: expect.stringContaining('id="Process_1700000000000"'),
    })
  })

  it('should open an unsaved draft from a platform template in designer', async () => {
    const wrapper = mountDraftList()
    await flushPromises()

    const templateButton = wrapper.findAll('button').find((button) => button.text().includes('租户开通审批'))
    expect(templateButton).toBeDefined()
    await templateButton!.trigger('click')
    await flushPromises()

    expect(processModelApiMocks.createModel).not.toHaveBeenCalled()
    expect(wrapper.emitted('open-new-draft')?.[0]?.[0]).toMatchObject({
      modelKey: 'platform_tenant_onboarding',
      name: '租户开通审批',
      description: expect.stringContaining('新租户入驻'),
      bpmnXml: expect.stringContaining('id="platform_tenant_onboarding"'),
    })
    expect(wrapper.emitted('open-new-draft')?.[0]?.[0]).toMatchObject({
      bpmnXml: expect.stringContaining('camunda:candidateGroups="PLATFORM_PRODUCT"'),
    })
    expect(wrapper.emitted('open-new-draft')?.[0]?.[0]).toMatchObject({
      bpmnXml: expect.stringContaining('name="tp:startPermission" value="workflow:platform:tenant-onboarding:start"'),
    })
  })

  it('should create and deploy a platform template from row action', async () => {
    processModelApiMocks.createModel.mockResolvedValueOnce({
      ...draftModel,
      id: 31,
      modelKey: 'platform_tenant_onboarding',
      name: '租户开通审批',
    })
    processModelApiMocks.validateModel.mockResolvedValueOnce({
      id: 31,
      valid: true,
      message: '校验通过',
      warnings: [],
      validationStatus: 'PASSED',
    })
    processModelApiMocks.deployModel.mockResolvedValueOnce({
      id: 31,
      deploymentId: 'dep-template-31',
      processDefinitionKey: 'platform_tenant_onboarding',
      status: 'DEPLOYED',
      message: '部署成功',
    })
    const wrapper = mountDraftList()
    await flushPromises()

    const templateRow = wrapper.find('[data-group-key="TEMPLATE:PLATFORM:0:platform_tenant_onboarding"]')
    const deployTemplateButton = templateRow.findAll('button').find((button) => button.text() === '部署')
    expect(deployTemplateButton).toBeDefined()
    await deployTemplateButton!.trigger('click')
    await flushPromises()

    expect(processModelApiMocks.createModel).toHaveBeenCalledWith(expect.objectContaining({
      modelKey: 'platform_tenant_onboarding',
      name: '租户开通审批',
    }))
    expect(processModelApiMocks.validateModel).toHaveBeenCalledWith(31)
    expect(processModelApiMocks.deployModel).toHaveBeenCalledWith(31)
    expect(messageMocks.success).toHaveBeenCalledWith('部署成功')
  })

  it('should validate a platform template through the unified row action', async () => {
    processModelApiMocks.createModel.mockResolvedValueOnce({
      ...draftModel,
      id: 32,
      modelKey: 'platform_tenant_onboarding',
      name: '租户开通审批',
    })
    processModelApiMocks.validateModel.mockResolvedValueOnce({
      id: 32,
      valid: true,
      message: '校验通过',
      warnings: [],
      validationStatus: 'PASSED',
    })
    const wrapper = mountDraftList()
    await flushPromises()

    const templateRow = wrapper.find('[data-group-key="TEMPLATE:PLATFORM:0:platform_tenant_onboarding"]')
    const validateTemplateButton = templateRow.findAll('button').find((button) => button.text() === '校验')
    expect(validateTemplateButton).toBeDefined()
    await validateTemplateButton!.trigger('click')
    await flushPromises()

    expect(processModelApiMocks.createModel).toHaveBeenCalledWith(expect.objectContaining({
      modelKey: 'platform_tenant_onboarding',
    }))
    expect(processModelApiMocks.validateModel).toHaveBeenCalledWith(32)
    expect(messageMocks.success).toHaveBeenCalledWith('校验通过')
  })

  it('should batch create and deploy platform templates that are not current runtime', async () => {
    processModelApiMocks.listModelGroups.mockResolvedValueOnce([])
    processModelApiMocks.createModel.mockImplementation((payload) => Promise.resolve({
      ...draftModel,
      id: processModelApiMocks.createModel.mock.calls.length + 100,
      modelKey: payload.modelKey,
      name: payload.name,
    }))
    const wrapper = mountDraftList()
    await flushPromises()

    const batchDeployButton = wrapper.findAll('button').find((button) => button.text().includes('部署平台模板'))
    expect(batchDeployButton).toBeDefined()
    await batchDeployButton!.trigger('click')
    await flushPromises()

    expect(processModelApiMocks.createModel).toHaveBeenCalledTimes(8)
    expect(processModelApiMocks.validateModel).toHaveBeenCalledTimes(8)
    expect(processModelApiMocks.deployModel).toHaveBeenCalledTimes(8)
    expect(messageMocks.success).toHaveBeenCalledWith('已创建并部署 8 个平台模板')
  })

  it('should render platform templates as verifiable rows when there are no persisted drafts', async () => {
    processModelApiMocks.listModelGroups.mockResolvedValueOnce([])
    const wrapper = mountDraftList()
    await flushPromises()

    expect(wrapper.text()).toContain('0 个草稿流程 · 0 个版本 · 8 个可创建平台模板')
    expect(wrapper.text()).toContain('租户开通审批')
    expect(wrapper.text()).toContain('生产配置变更审批')
    expect(wrapper.text()).toContain('平台模板')
    expect(wrapper.text()).toContain('设计最新')
    expect(wrapper.text()).toContain('校验')
    expect(wrapper.text()).toContain('部署')
    expect(wrapper.findAll('.draft-asset-row')).toHaveLength(8)
    expect(wrapper.find('.empty').exists()).toBe(false)
  })

  it('should not render create template action when matching process model already exists', async () => {
    processModelApiMocks.listModelGroups.mockResolvedValueOnce([platformConfigChangeGroup])
    const wrapper = mountDraftList()
    await flushPromises()

    expect(wrapper.text()).toContain('1 个草稿流程 · 1 个版本 · 7 个可创建平台模板')
    const configChangeRow = wrapper.find('[data-group-key="PLATFORM:0:platform_config_change"]')
    expect(configChangeRow.exists()).toBe(true)
    expect(configChangeRow.text()).toContain('生产配置变更审批')
    expect(configChangeRow.text()).not.toContain('创建草稿')
    expect(configChangeRow.text()).not.toContain('创建并部署')
    expect(configChangeRow.text()).toContain('设计最新')
    expect(configChangeRow.text()).toContain('校验')
    expect(configChangeRow.text()).toContain('部署')
  })

  it('should not show virtual template rows when draft list fails to load', async () => {
    processModelApiMocks.listModelGroups.mockRejectedValueOnce(new Error('接口失败'))
    const wrapper = mountDraftList()
    await flushPromises()

    expect(wrapper.text()).toContain('接口失败')
    expect(wrapper.text()).not.toContain('创建草稿')
    expect(wrapper.text()).not.toContain('租户开通审批')
    expect(wrapper.findAll('.draft-asset-row')).toHaveLength(0)
  })

  it('should validate and deploy draft from row actions', async () => {
    const wrapper = mountDraftList()
    await flushPromises()

    const leaveRow = wrapper.find('[data-group-key="TENANT:11:leave_process"]')
    const validateButton = leaveRow.findAll('button').find((button) => button.text().includes('校验'))
    const deployButton = leaveRow.findAll('button').find((button) => button.text() === '部署')
    expect(validateButton).toBeDefined()
    expect(deployButton).toBeDefined()

    await validateButton!.trigger('click')
    await flushPromises()
    await deployButton!.trigger('click')
    await flushPromises()

    expect(processModelApiMocks.validateModel).toHaveBeenCalledWith(8)
    expect(processModelApiMocks.deployModel).toHaveBeenCalledWith(8)
    expect(messageMocks.success).toHaveBeenCalledWith('校验通过')
    expect(messageMocks.success).toHaveBeenCalledWith('部署成功')
  })

  it('should delete a single-version undeployed draft from asset row after confirmation', async () => {
    processModelApiMocks.listModelGroups
      .mockResolvedValueOnce(draftGroups)
      .mockResolvedValueOnce([draftGroups[0]])
    const wrapper = mountDraftList()
    await flushPromises()

    const expenseRow = wrapper.find('[data-group-key="TENANT:11:expense_process"]')
    const deleteButton = expenseRow.findAll('button').find((button) => button.text() === '删除')
    expect(deleteButton).toBeDefined()
    await deleteButton!.trigger('click')

    expect(modalMocks.confirm).toHaveBeenCalledWith(expect.objectContaining({
      title: '删除流程草稿',
      okType: 'danger',
    }))

    const options = modalMocks.confirm.mock.calls[0]?.[0] as { onOk: () => Promise<void> }
    await options.onOk()
    await flushPromises()

    expect(processModelApiMocks.deleteModel).toHaveBeenCalledWith(9)
    expect(messageMocks.success).toHaveBeenCalledWith('流程草稿已删除')
    expect(processModelApiMocks.listModelGroups).toHaveBeenCalledTimes(2)
  })

  it('should only show delete for undeployed versions in a multi-version process asset', async () => {
    const wrapper = mountDraftList()
    await flushPromises()

    const versionsButton = wrapper.findAll('button').find((button) => button.text().includes('2 个版本'))
    await versionsButton!.trigger('click')
    await flushPromises()

    const draftVersionRow = wrapper.find('[data-model-id="8"]')
    const runtimeVersionRow = wrapper.find('[data-model-id="7"]')
    expect(draftVersionRow.findAll('button').some((button) => button.text() === '删除')).toBe(true)
    expect(runtimeVersionRow.findAll('button').some((button) => button.text() === '删除')).toBe(false)
  })
})
