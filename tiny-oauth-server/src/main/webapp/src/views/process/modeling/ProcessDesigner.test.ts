import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

// Avoid pulling in heavy BPMN libs in unit tests.
vi.mock('bpmn-js/lib/Modeler', () => ({
  default: class {
    importXML = vi.fn(() => Promise.resolve())
    saveXML = vi.fn(() => Promise.resolve({ xml: '<bpmn />' }))
    saveSVG = vi.fn(() => Promise.resolve({ svg: '<svg />' }))
    get = vi.fn(() => ({ zoom: vi.fn() }))
    destroy = vi.fn()
  },
}))
vi.mock('bpmn-js-properties-panel', () => ({
  BpmnPropertiesPanelModule: {},
  BpmnPropertiesProviderModule: {},
  CamundaPlatformPropertiesProviderModule: {},
}))
vi.mock('diagram-js-minimap', () => ({ default: {} }))
vi.mock('camunda-bpmn-moddle/resources/camunda.json', () => ({ default: {} }))
vi.mock('@/utils/bpmn/utils/translateUtils', () => ({
  getTranslateModule: () => ({}),
  translateUtils: {
    addCustomTranslations: vi.fn(),
  },
}))

vi.mock('ant-design-vue', () => ({
  message: {
    error: vi.fn(),
    info: vi.fn(),
    loading: vi.fn(() => vi.fn()),
    success: vi.fn(),
    warning: vi.fn(),
  },
}))

const tenantContextMocks = vi.hoisted(() => ({
  getActiveTenantId: vi.fn(),
}))

const routerMocks = vi.hoisted(() => ({
  routeQuery: {} as Record<string, unknown>,
  routePath: '/process/modeling',
  routerPush: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({
    path: routerMocks.routePath,
    query: routerMocks.routeQuery,
  }),
  useRouter: () => ({
    push: routerMocks.routerPush,
  }),
}))

vi.mock('@/utils/tenant', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/utils/tenant')>()
  return {
    ...actual,
    getActiveTenantId: tenantContextMocks.getActiveTenantId,
  }
})

const PassThrough = defineComponent({ template: '<div><slot /></div>' })
const ButtonStub = defineComponent({
  emits: ['click'],
  template: '<button @click="$emit(\'click\')"><slot /></button>',
})
const ProcessDeployResultModalStub = defineComponent({
  emits: ['go-deployment', 'go-definition'],
  template: `
    <div>
      <button class="go-deployment" @click="$emit('go-deployment')">go-deployment</button>
      <button class="go-definition" @click="$emit('go-definition')">go-definition</button>
    </div>
  `,
})

import ProcessDesigner from '@/views/process/modeling/ProcessDesigner.vue'

function mountDesigner() {
  return mount(ProcessDesigner, {
    global: {
      stubs: {
        'a-button': ButtonStub,
        'a-modal': PassThrough,
        'a-form': PassThrough,
        'a-form-item': PassThrough,
        'a-input': PassThrough,
        ProcessDeployResultModal: ProcessDeployResultModalStub,
        PlusOutlined: PassThrough,
        FolderOpenOutlined: PassThrough,
        RocketOutlined: PassThrough,
        DownloadOutlined: PassThrough,
        FileImageOutlined: PassThrough,
        SaveOutlined: PassThrough,
      },
    },
  })
}

describe('process ProcessDesigner.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    Object.keys(routerMocks.routeQuery).forEach((key) => {
      delete routerMocks.routeQuery[key]
    })
    routerMocks.routePath = '/process/modeling'
    tenantContextMocks.getActiveTenantId.mockReturnValue('9')
  })

  it('should render designer shell actions', () => {
    const wrapper = mountDesigner()

    expect(wrapper.text()).toContain('创建BPMN')
    expect(wrapper.text()).toContain('保存草稿')
  })

  it('should preserve activeTenantId when navigating from deploy result modal', async () => {
    routerMocks.routeQuery.activeTenantId = '11'

    const wrapper = mountDesigner()

    await wrapper.get('.go-deployment').trigger('click')
    expect(routerMocks.routerPush).toHaveBeenCalledWith({
      path: '/deployment',
      query: { activeTenantId: '11' },
    })

    await wrapper.get('.go-definition').trigger('click')
    expect(routerMocks.routerPush).toHaveBeenCalledWith({
      path: '/process/definition',
      query: { activeTenantId: '11' },
    })
  })
})
