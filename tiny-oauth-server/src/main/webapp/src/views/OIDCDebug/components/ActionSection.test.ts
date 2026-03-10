import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { describe, expect, it } from 'vitest'

import ActionSection from '@/views/OIDCDebug/components/ActionSection.vue'

const ButtonStub = defineComponent({
  emits: ['click'],
  template: `<button @click="$emit('click')"><slot /></button>`,
})

describe('ActionSection.vue', () => {
  it('should emit refresh and clear-cache', async () => {
    const wrapper = mount(ActionSection, {
      props: { refreshing: false, clearing: false },
      global: { stubs: { 'a-button': ButtonStub } },
    })

    await wrapper.findAll('button')[0]!.trigger('click')
    await wrapper.findAll('button')[1]!.trigger('click')

    expect(wrapper.emitted('refresh')).toBeTruthy()
    expect(wrapper.emitted('clear-cache')).toBeTruthy()
  })
})

