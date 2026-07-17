import { mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const modalMocks = vi.hoisted(() => ({
  confirm: vi.fn(),
}))

const messageMocks = vi.hoisted(() => ({
  warning: vi.fn(),
}))

const routerMocks = vi.hoisted(() => ({
  route: {
    path: '/process/modeling',
    query: {} as Record<string, unknown>,
  },
  routerReplace: vi.fn(),
  beforeRouteLeave: vi.fn(),
}))

vi.mock('ant-design-vue', () => ({
  Modal: { confirm: modalMocks.confirm },
  message: { warning: messageMocks.warning },
}))

vi.mock('vue-router', () => ({
  useRoute: () => routerMocks.route,
  useRouter: () => ({
    replace: routerMocks.routerReplace,
  }),
  onBeforeRouteLeave: routerMocks.beforeRouteLeave,
}))

vi.mock('@/views/process/modeling/ProcessDraftList.vue', () => ({
  default: {
    name: 'ProcessDraftList',
    emits: ['open-design', 'open-new-draft'],
    template: `
      <div class="process-draft-list-stub">
        流程草稿
        <button class="open-design" @click="$emit('open-design', { id: 7, modelKey: 'leave_process', name: 'Leave Process' })">
          打开设计
        </button>
        <button
          class="open-new-draft"
          @click="$emit('open-new-draft', {
            modelKey: 'Process_1700000000000',
            name: '新建流程草稿',
            bpmnXml: '<bpmn />'
          })"
        >
          新建草稿
        </button>
      </div>
    `,
  },
}))

vi.mock('@/views/process/modeling/ProcessDesigner.vue', () => ({
  default: {
    name: 'ProcessDesigner',
    props: ['initialDraft'],
    emits: ['dirty-change', 'saved'],
    template: `
      <div class="process-designer-stub" :data-initial-model-key="initialDraft?.modelKey || ''">
        流程设计
        <button class="mark-dirty" @click="$emit('dirty-change', true)">dirty</button>
        <button
          class="emit-saved"
          @click="$emit('saved', { id: 19, modelKey: 'Process_1700000000000', name: '新建流程草稿' })"
        >
          saved
        </button>
      </div>
    `,
  },
}))

const TabsStub = defineComponent({
  props: ['activeKey'],
  emits: ['change'],
  template: '<div class="tabs" :data-active-key="activeKey"><slot /></div>',
})

const TabPaneStub = defineComponent({
  props: ['tab', 'disabled'],
  template: '<section class="tab-pane" :data-tab="tab" :data-disabled="disabled ? \'true\' : \'false\'"><slot /></section>',
})

import Modeling from '@/views/process/Modeling.vue'

async function flushPromises() {
  await Promise.resolve()
  await nextTick()
  await Promise.resolve()
  await nextTick()
}

function resetRouteQuery(query: Record<string, unknown> = {}) {
  routerMocks.route.query = query
}

function mountModeling() {
  return mount(Modeling, {
    global: {
      stubs: {
        'a-tabs': TabsStub,
        'a-tab-pane': TabPaneStub,
      },
    },
  })
}

describe('process Modeling.vue workspace', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    resetRouteQuery()
  })

  it('should default to draft list tab', async () => {
    const wrapper = mountModeling()
    await flushPromises()

    expect(wrapper.attributes('data-active-tab')).toBe('drafts')
    expect(wrapper.text()).toContain('流程草稿')
    expect(wrapper.find('.process-draft-list-stub').exists()).toBe(true)
  })

  it('should open selected draft in design tab and preserve active tenant query', async () => {
    resetRouteQuery({ activeTenantId: '11' })
    const wrapper = mountModeling()

    await wrapper.get('.open-design').trigger('click')

    expect(routerMocks.routerReplace).toHaveBeenCalledWith({
      path: '/process/modeling',
      query: {
        activeTenantId: '11',
        tab: 'design',
        modelId: '7',
      },
    })
  })

  it('should open a new draft in design tab without a persisted model id', async () => {
    vi.spyOn(Date, 'now').mockReturnValue(1700000000001)
    resetRouteQuery({ activeTenantId: '11' })
    const wrapper = mountModeling()

    await wrapper.get('.open-new-draft').trigger('click')

    expect(routerMocks.routerReplace).toHaveBeenCalledWith({
      path: '/process/modeling',
      query: {
        activeTenantId: '11',
        tab: 'design',
        draft: 'new',
      },
    })
    expect(routerMocks.routerReplace.mock.calls[0]?.[0]?.query).not.toHaveProperty('modelId')
  })

  it('should render design tab when modelId is present', async () => {
    resetRouteQuery({ tab: 'design', modelId: '7' })
    const wrapper = mountModeling()
    await flushPromises()

    expect(wrapper.attributes('data-active-tab')).toBe('design')
    expect(wrapper.attributes('data-process-model-id')).toBe('7')
    expect(wrapper.find('.process-designer-stub').exists()).toBe(true)
  })

  it('should replace unsaved draft route with persisted model id after save', async () => {
    resetRouteQuery({ tab: 'design', modelId: '5', draft: 'new', activeTenantId: '11' })
    const wrapper = mountModeling()
    await flushPromises()

    await wrapper.get('.emit-saved').trigger('click')

    expect(routerMocks.routerReplace).toHaveBeenCalledWith({
      path: '/process/modeling',
      query: {
        activeTenantId: '11',
        tab: 'design',
        modelId: '19',
      },
    })
  })

  it('should confirm before leaving dirty designer for another draft', async () => {
    resetRouteQuery({ tab: 'design', modelId: '5', activeTenantId: '11' })
    const wrapper = mountModeling()
    await wrapper.get('.mark-dirty').trigger('click')
    await wrapper.get('.open-design').trigger('click')

    expect(modalMocks.confirm).toHaveBeenCalledWith(expect.objectContaining({
      title: '存在未保存的流程设计',
    }))

    const options = modalMocks.confirm.mock.calls[0]?.[0] as { onOk: () => void }
    options.onOk()

    expect(routerMocks.routerReplace).toHaveBeenCalledWith({
      path: '/process/modeling',
      query: {
        activeTenantId: '11',
        tab: 'design',
        modelId: '7',
      },
    })
  })

  it('should normalize invalid design query back to drafts', async () => {
    resetRouteQuery({ tab: 'design', activeTenantId: '11' })
    mountModeling()
    await flushPromises()

    expect(messageMocks.warning).toHaveBeenCalledWith('请先选择流程草稿')
    expect(routerMocks.routerReplace).toHaveBeenCalledWith({
      path: '/process/modeling',
      query: {
        activeTenantId: '11',
        tab: 'drafts',
      },
    })
  })

  it('should normalize lost unsaved draft route back to drafts', async () => {
    resetRouteQuery({ tab: 'design', draft: 'new', activeTenantId: '11' })
    mountModeling()
    await flushPromises()

    expect(messageMocks.warning).toHaveBeenCalledWith('未保存草稿不存在，请重新新建或选择已有草稿')
    expect(routerMocks.routerReplace).toHaveBeenCalledWith({
      path: '/process/modeling',
      query: {
        activeTenantId: '11',
        tab: 'drafts',
      },
    })
  })
})
