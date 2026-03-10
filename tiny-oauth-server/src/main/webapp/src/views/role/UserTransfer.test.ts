import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { describe, expect, it } from 'vitest'

import UserTransfer from '@/views/role/UserTransfer.vue'

const ModalStub = defineComponent({
  emits: ['ok', 'cancel', 'update:open'],
  template: `<div>
    <button class="ok" @click="$emit('ok')">ok</button>
    <button class="cancel" @click="$emit('cancel')">cancel</button>
    <slot />
  </div>`,
})

const TransferStub = defineComponent({
  emits: ['update:targetKeys'],
  template: `<button class="select" @click="$emit('update:targetKeys',['2'])">select</button>`,
})

describe('UserTransfer.vue', () => {
  it('should emit selected user ids on ok', async () => {
    const wrapper = mount(UserTransfer, {
      props: {
        modelValue: ['1'],
        allUsers: [
          { key: '1', title: 'alice' },
          { key: '2', title: 'bob' },
        ],
        open: true,
      },
      global: {
        stubs: {
          'a-modal': ModalStub,
          'a-transfer': TransferStub,
        },
      },
    })

    await wrapper.find('button.select').trigger('click')
    await wrapper.find('button.ok').trigger('click')

    const emitted = wrapper.emitted('update:modelValue')
    expect(emitted).toBeTruthy()
    expect(emitted?.[0]?.[0]).toEqual(['2'])
  })
})

