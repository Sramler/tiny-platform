import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { describe, expect, it } from 'vitest'

import LogSection from '@/views/OIDCDebug/components/LogSection.vue'

const ButtonStub = defineComponent({
  emits: ['click'],
  template: `<button @click="$emit('click')"><slot /></button>`,
})

describe('LogSection.vue', () => {
  it('should render empty state and emit clear', async () => {
    const wrapper = mount(LogSection, {
      props: { logs: [] },
      global: { stubs: { 'a-button': ButtonStub } },
    })

    expect(wrapper.text()).toContain('暂无日志')
    await wrapper.find('button').trigger('click')
    expect(wrapper.emitted('clear')).toBeTruthy()
  })
})

