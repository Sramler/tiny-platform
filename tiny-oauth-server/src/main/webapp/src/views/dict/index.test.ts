import { mount } from '@vue/test-utils'
import { defineComponent, nextTick, h, computed } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  isPlatformScope: { value: false },
}))

vi.mock('@/composables/usePlatformScope', () => ({
  usePlatformScope: () => ({
    isPlatformScope: computed(() => mocks.isPlatformScope.value),
  }),
}))

vi.mock('@/views/platform/dicts/index.vue', () => ({
  default: defineComponent({
    template: '<div class="platform-dict-page">平台字典管理</div>',
  }),
}))

const TabsStub = defineComponent({
  setup(_, { slots }) {
    return () => h('div', { class: 'tabs' }, slots.default?.())
  },
})

const TabPaneStub = defineComponent({
  props: { tab: { type: String, default: '' } },
  setup(props, { slots }) {
    return () => h('div', { class: 'tab-pane' }, [h('div', props.tab), slots.default?.()])
  },
})

const PassThrough = defineComponent({ template: '<div><slot /></div>' })

import DictIndex from '@/views/dict/index.vue'

async function flushPromises() {
  await Promise.resolve()
  await nextTick()
}

describe('dict index.vue', () => {
  beforeEach(() => {
    mocks.isPlatformScope.value = false
  })

  it('should render tabs', async () => {
    const wrapper = mount(DictIndex, {
      global: {
        stubs: {
          'a-tabs': TabsStub,
          'a-tab-pane': TabPaneStub,
          DictType: PassThrough,
          DictItem: PassThrough,
        },
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('字典类型')
    expect(wrapper.text()).toContain('字典项')
  })

  it('should render platform dict page when current scope is platform', async () => {
    mocks.isPlatformScope.value = true

    const wrapper = mount(DictIndex, {
      global: {
        stubs: {
          'a-tabs': TabsStub,
          'a-tab-pane': TabPaneStub,
          DictType: PassThrough,
          DictItem: PassThrough,
        },
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('平台字典管理')
    expect(wrapper.text()).not.toContain('字典类型')
    expect(wrapper.text()).not.toContain('字典项')
  })
})
