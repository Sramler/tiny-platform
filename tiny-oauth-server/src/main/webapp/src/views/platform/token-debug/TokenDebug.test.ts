import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const auditMocks = vi.hoisted(() => ({
  decodePlatformToken: vi.fn(),
}))

const uiMocks = vi.hoisted(() => ({
  messageWarning: vi.fn(),
  messageError: vi.fn(),
}))

const authMocks = vi.hoisted(() => ({
  token: 'platform-token',
}))

vi.mock('@/api/audit', () => ({
  decodePlatformToken: auditMocks.decodePlatformToken,
}))

vi.mock('ant-design-vue', () => ({
  message: {
    warning: uiMocks.messageWarning,
    error: uiMocks.messageError,
  },
}))

vi.mock('@/auth/auth', () => ({
  useAuth: () => ({
    user: { value: { access_token: authMocks.token } },
  }),
}))

vi.mock('@/utils/jwt', () => ({
  decodeJwtPayload: (token?: string) => {
    if (token === 'platform-token') {
      return { activeScopeType: 'PLATFORM' }
    }
    return { activeScopeType: 'TENANT' }
  },
}))

import TokenDebug from '@/views/platform/token-debug/TokenDebug.vue'

const PassThrough = defineComponent({
  template: '<div><slot /></div>',
})

const TextareaStub = defineComponent({
  props: { value: String, rows: Number, placeholder: String },
  emits: ['update:value'],
  template:
    '<textarea :value="value" :placeholder="placeholder" @input="$emit(\'update:value\', $event.target.value)" />',
})

const ButtonStub = defineComponent({
  props: { loading: Boolean, type: String },
  emits: ['click'],
  template: '<button @click="$emit(\'click\', $event)"><slot /></button>',
})

function mountView() {
  return mount(TokenDebug, {
    global: {
      stubs: {
        'a-form': PassThrough,
        'a-form-item': PassThrough,
        'a-card': PassThrough,
        'a-space': PassThrough,
        'a-textarea': TextareaStub,
        'a-button': ButtonStub,
        'a-descriptions': PassThrough,
        'a-descriptions-item': PassThrough,
        'a-tag': PassThrough,
      },
    },
  })
}

describe('TokenDebug.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    authMocks.token = 'platform-token'
    auditMocks.decodePlatformToken.mockResolvedValue({
      authorities: ['system:audit:auth:view'],
      permissions: ['system:audit:auth:view'],
      roleCodes: ['ROLE_ADMIN'],
      permissionsVersion: 'perm-v-2',
      activeScopeType: 'PLATFORM',
      activeTenantId: 9,
      claims: { userId: 1 },
    })
  })

  it('should warn when token is empty', async () => {
    const wrapper = mountView()
    const decodeButton = wrapper.findAll('button').find((btn) => btn.text().includes('Decode'))
    await decodeButton?.trigger('click')

    expect(uiMocks.messageWarning).toHaveBeenCalledWith('请输入 token')
    expect(auditMocks.decodePlatformToken).not.toHaveBeenCalled()
  })

  it('should decode token and render key claims', async () => {
    const wrapper = mountView()
    const textarea = wrapper.find('textarea')
    await textarea.setValue('token-value')
    const decodeButton = wrapper.findAll('button').find((btn) => btn.text().includes('Decode'))
    await decodeButton?.trigger('click')
    await Promise.resolve()

    expect(auditMocks.decodePlatformToken).toHaveBeenCalledWith('token-value')
    expect(wrapper.text()).toContain('perm-v-2')
    expect(wrapper.text()).toContain('PLATFORM')
    expect(wrapper.text()).toContain('ROLE_ADMIN')
  })

  it('should clear stale decode result when decode fails', async () => {
    const wrapper = mountView()
    const textarea = wrapper.find('textarea')
    await textarea.setValue('token-a')
    const decodeButton = wrapper.findAll('button').find((btn) => btn.text().includes('Decode'))
    await decodeButton?.trigger('click')
    await Promise.resolve()
    expect(wrapper.text()).toContain('perm-v-2')

    auditMocks.decodePlatformToken.mockRejectedValueOnce(new Error('invalid token'))
    await textarea.setValue('bad-token')
    await decodeButton?.trigger('click')
    await Promise.resolve()

    expect(uiMocks.messageError).toHaveBeenCalled()
    expect(wrapper.text()).not.toContain('perm-v-2')
    expect(wrapper.text()).not.toContain('ROLE_ADMIN')
  })

  it('should block platform token debug under tenant scope', async () => {
    authMocks.token = 'tenant-token'
    const wrapper = mountView()
    const textarea = wrapper.find('textarea')

    expect(textarea.exists()).toBe(false)
    expect(wrapper.text()).toContain('当前会话不是 PLATFORM 作用域')
    expect(auditMocks.decodePlatformToken).not.toHaveBeenCalled()
  })
})

