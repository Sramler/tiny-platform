import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

// Avoid pulling in heavy BPMN libs in unit tests.
vi.mock('bpmn-js/lib/Modeler', () => ({
  default: class {
    importXML = vi.fn(() => Promise.resolve())
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
  translateUtils: (() => {
    const translations: Record<string, string> = {
      Create: '新增',
      Start: '开始',
      start: '开始',
      'Java class': 'Java 类',
      '<empty>': '空',
      '<no type>': '无类型',
      'Create new list item': '新增列表项',
      'List contains {numOfItems} item': '列表包含 {numOfItems} 项',
      'List contains {numOfItems} items': '列表包含 {numOfItems} 项',
      'Toggle list item': '展开/折叠列表项',
      'Delete item': '删除项',
    }

    return {
      addCustomTranslations: vi.fn(),
      hasTranslation: (template: string) => template in translations,
      translate: (template: string, replacements?: Record<string, unknown>) => {
        return (translations[template] || template).replace(
          /{([^}]+)}/g,
          (_, key) => String(replacements?.[key] ?? `{${key}}`),
        )
      },
    }
  })(),
}))

vi.mock('ant-design-vue', () => ({
  message: { error: vi.fn(), success: vi.fn(), warning: vi.fn() },
}))

const tenantContextMocks = vi.hoisted(() => ({
  getActiveTenantId: vi.fn(),
}))

const routerMocks = vi.hoisted(() => ({
  routeQuery: {} as Record<string, unknown>,
  routerPush: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({
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

import Modeling from '@/views/process/Modeling.vue'

describe('process Modeling.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    Object.keys(routerMocks.routeQuery).forEach((key) => {
      delete routerMocks.routeQuery[key]
    })
    tenantContextMocks.getActiveTenantId.mockReturnValue('9')
  })

  it('should render modeling shell', () => {
    const wrapper = mount(Modeling, {
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
        },
      },
    })

    expect(wrapper.text()).toContain('创建BPMN')
  })

  it('should preserve activeTenantId when navigating from deploy result modal', async () => {
    routerMocks.routeQuery.activeTenantId = '11'

    const wrapper = mount(Modeling, {
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
        },
      },
    })

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

  it('should localize static list controls rendered by the properties panel', async () => {
    const wrapper = mount(Modeling, {
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
        },
      },
    })

    await new Promise((resolve) => setTimeout(resolve, 150))

    const panel = wrapper.element.querySelector('.properties-panel')
    expect(panel).not.toBeNull()

    const addButton = document.createElement('button')
    addButton.className = 'bio-properties-panel-add-entry'
    addButton.setAttribute('title', 'Create new list item')
    const addLabel = document.createElement('span')
    addLabel.className = 'bio-properties-panel-add-entry-label'
    addLabel.textContent = 'Create'
    addButton.appendChild(addLabel)

    const listBadge = document.createElement('div')
    listBadge.className = 'bio-properties-panel-list-badge'
    listBadge.setAttribute('title', 'List contains 2 items')

    const arrowButton = document.createElement('button')
    arrowButton.className = 'bio-properties-panel-arrow'
    arrowButton.setAttribute('title', 'Toggle list item')

    const deleteButton = document.createElement('button')
    deleteButton.setAttribute('title', 'Delete item')

    const emptyTitle = document.createElement('div')
    emptyTitle.className = 'bio-properties-panel-list-entry-header-title'
    emptyTitle.textContent = '<empty>'

    const listenerTitle = document.createElement('div')
    listenerTitle.className = 'bio-properties-panel-list-entry-header-title'
    listenerTitle.textContent = 'Start: Java class'

    const collapsibleEmptyTitle = document.createElement('div')
    collapsibleEmptyTitle.className = 'bio-properties-panel-collapsible-entry-header-title'
    collapsibleEmptyTitle.textContent = '<empty>'

    panel!.append(
      addButton,
      listBadge,
      arrowButton,
      deleteButton,
      emptyTitle,
      listenerTitle,
      collapsibleEmptyTitle,
    )
    await new Promise((resolve) => setTimeout(resolve, 0))

    expect(addButton.getAttribute('title')).toBe('新增列表项')
    expect(addLabel.textContent).toBe('新增')
    expect(listBadge.getAttribute('title')).toBe('列表包含 2 项')
    expect(arrowButton.getAttribute('title')).toBe('展开/折叠列表项')
    expect(deleteButton.getAttribute('title')).toBe('删除项')
    expect(emptyTitle.textContent).toBe('空')
    expect(listenerTitle.textContent).toBe('开始: Java 类')
    expect(collapsibleEmptyTitle.textContent).toBe('空')
  })
})
