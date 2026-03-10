import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { describe, expect, it, vi } from 'vitest'

// Avoid pulling in heavy BPMN libs in unit tests.
vi.mock('bpmn-js/lib/Modeler', () => ({ default: class {} }))
vi.mock('bpmn-js-properties-panel', () => ({
  BpmnPropertiesPanelModule: {},
  BpmnPropertiesProviderModule: {},
  CamundaPlatformPropertiesProviderModule: {},
}))
vi.mock('diagram-js-minimap', () => ({ default: {} }))
vi.mock('camunda-bpmn-moddle/resources/camunda.json', () => ({}))
vi.mock('@/utils/bpmn/utils/translateUtils', () => ({ getTranslateModule: () => ({}), translateUtils: {} }))

vi.mock('ant-design-vue', () => ({
  message: { error: vi.fn(), success: vi.fn(), warning: vi.fn() },
}))

const PassThrough = defineComponent({ template: '<div><slot /></div>' })

import Modeling from '@/views/process/Modeling.vue'

describe('process Modeling.vue', () => {
  it('should render modeling shell', () => {
    const wrapper = mount(Modeling, {
      global: {
        stubs: {
          'a-button': PassThrough,
          'a-modal': PassThrough,
          'a-form': PassThrough,
          'a-form-item': PassThrough,
          'a-input': PassThrough,
          ProcessDeployResultModal: PassThrough,
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
})

