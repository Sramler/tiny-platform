import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import RoleTransfer from '@/views/user/RoleTransfer.vue'

describe('RoleTransfer.vue', () => {
  it('should emit selected role ids on ok', async () => {
    const wrapper = mount(RoleTransfer, {
      props: {
        modelValue: ['1'],
        allRoles: [
          { key: '1', name: 'Admin', code: 'ROLE_ADMIN' },
          { key: '2', name: 'User', code: 'ROLE_USER' },
        ],
        open: true,
      },
      global: {
        stubs: {
          'a-modal': {
            props: ['open', 'title'],
            emits: ['ok', 'cancel', 'update:open'],
            template: `<div><button class="ok" @click="$emit('ok')">ok</button><slot /></div>`,
          },
          'a-transfer': {
            props: ['targetKeys', 'dataSource'],
            emits: ['update:targetKeys'],
            template: `<div><button class="select-two" @click="$emit('update:targetKeys',['2'])">select</button></div>`,
          },
        },
      },
    })

    await wrapper.find('button.select-two').trigger('click')
    await wrapper.find('button.ok').trigger('click')

    const emitted = wrapper.emitted('update:modelValue')
    expect(emitted).toBeTruthy()
    expect(emitted?.[0]?.[0]).toEqual(['2'])
  })
})

