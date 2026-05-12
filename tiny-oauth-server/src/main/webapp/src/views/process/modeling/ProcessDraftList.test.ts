import { mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const processModelApiMocks = vi.hoisted(() => ({
  listModelGroups: vi.fn(),
  createModel: vi.fn(),
  validateModel: vi.fn(),
  deployModel: vi.fn(),
}))

const messageMocks = vi.hoisted(() => ({
  error: vi.fn(),
  success: vi.fn(),
  warning: vi.fn(),
}))

vi.mock('@/api/process', () => ({
  processModelApi: processModelApiMocks,
}))

vi.mock('@/utils/bpmn/modeler', () => ({
  EMPTY_PROCESS_XML: '<bpmn:definitions><bpmn:process id="NewProcess" name="新建流程" /><bpmndi:BPMNPlane bpmnElement="NewProcess" /></bpmn:definitions>',
}))

vi.mock('ant-design-vue', () => ({
  message: messageMocks,
}))

const ButtonStub = defineComponent({
  emits: ['click'],
  template: '<button @click="$emit(\'click\')"><slot /></button>',
})

const PassThrough = defineComponent({
  template: '<div><slot /></div>',
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

const draftModel = {
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

function model(overrides: Partial<typeof draftModel>) {
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

async function flushPromises() {
  await Promise.resolve()
  await nextTick()
  await Promise.resolve()
  await nextTick()
}

function mountDraftList() {
  return mount(ProcessDraftList, {
    global: {
      stubs: {
        'a-button': ButtonStub,
        'a-alert': AlertStub,
        'a-empty': EmptyStub,
        'a-table': TableStub,
        'a-tag': PassThrough,
        CheckCircleOutlined: PassThrough,
        DownOutlined: PassThrough,
        EditOutlined: PassThrough,
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
  })

  it('should load and render process model drafts', async () => {
    const wrapper = mountDraftList()
    await flushPromises()

    expect(processModelApiMocks.listModelGroups).toHaveBeenCalled()
    expect(wrapper.text()).toContain('流程草稿')
    expect(wrapper.text()).toContain('2 个流程 · 3 个版本')
    expect(wrapper.text()).toContain('Leave Process')
    expect(wrapper.text()).toContain('leave_process')
    expect(wrapper.text()).toContain('Expense Process')
    expect(wrapper.text()).toContain('租户 11')
    expect(wrapper.text()).toContain('v1')
    expect(wrapper.text()).toContain('有变更')
    expect(wrapper.find('.draft-table').exists()).toBe(true)
    expect(wrapper.find('.draft-asset-list').exists()).toBe(false)
    expect(wrapper.findAll('.draft-asset-row')).toHaveLength(2)
    expect(wrapper.findAll('.draft-version-row')).toHaveLength(0)
  })

  it('should expand process asset versions without creating extra asset rows', async () => {
    const wrapper = mountDraftList()
    await flushPromises()

    const versionsButton = wrapper.findAll('button').find((button) => button.text().includes('2 个版本'))
    expect(versionsButton).toBeDefined()
    await versionsButton!.trigger('click')
    await flushPromises()

    expect(wrapper.findAll('.draft-asset-row')).toHaveLength(2)
    expect(wrapper.findAll('.draft-version-row')).toHaveLength(2)
    expect(wrapper.text()).toContain('v2')
    expect(wrapper.text()).toContain('v1')
    expect(wrapper.text()).toContain('当前运行')
    expect(wrapper.text()).toContain('未部署')
  })

  it('should emit open-design for latest model of selected process asset', async () => {
    const wrapper = mountDraftList()
    await flushPromises()

    const designButton = wrapper.findAll('button').find((button) => button.text().includes('设计最新'))
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

  it('should create draft and open it in designer', async () => {
    vi.spyOn(Date, 'now').mockReturnValue(1700000000000)
    const wrapper = mountDraftList()
    await flushPromises()

    const createButton = wrapper.findAll('button').find((button) => button.text().includes('新建草稿'))
    expect(createButton).toBeDefined()
    await createButton!.trigger('click')
    await flushPromises()

    expect(processModelApiMocks.createModel).toHaveBeenCalledWith(expect.objectContaining({
      modelKey: 'Process_1700000000000',
      name: '新建流程草稿',
    }))
    expect(messageMocks.success).toHaveBeenCalledWith('流程草稿已创建')
    expect(wrapper.emitted('open-design')?.[0]?.[0]).toMatchObject({ id: 8, modelKey: 'Process_1700000000000' })
  })

  it('should validate and deploy draft from row actions', async () => {
    const wrapper = mountDraftList()
    await flushPromises()

    const validateButton = wrapper.findAll('button').find((button) => button.text().includes('校验'))
    const deployButton = wrapper.findAll('button').find((button) => button.text().includes('部署'))
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
})
