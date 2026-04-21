import { mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const antMocks = vi.hoisted(() => ({
  message: {
    warning: vi.fn(),
  },
}))

const apiMocks = vi.hoisted(() => ({
  precheckTenantCreate: vi.fn(),
  createTenant: vi.fn(),
}))

const routerMocks = vi.hoisted(() => ({
  push: vi.fn(),
}))

const routeMocks = vi.hoisted(() => ({
  fullPath: '/system/tenant',
}))

vi.mock('ant-design-vue', () => antMocks)
vi.mock('@/api/tenant', () => ({
  precheckTenantCreate: apiMocks.precheckTenantCreate,
  createTenant: apiMocks.createTenant,
}))
vi.mock('vue-router', () => ({
  useRoute: () => ({
    fullPath: routeMocks.fullPath,
  }),
  useRouter: () => ({
    push: routerMocks.push,
  }),
}))

const PassThrough = defineComponent({ template: '<div><slot /></div>' })

const InputStub = defineComponent({
  props: {
    value: {
      type: [String, Number],
      default: '',
    },
    placeholder: {
      type: String,
      default: '',
    },
  },
  emits: ['update:value'],
  template:
    '<input :value="value ?? \'\'" :placeholder="placeholder" @input="$emit(\'update:value\', $event.target.value)" />',
})

const InputPasswordStub = defineComponent({
  props: {
    value: {
      type: String,
      default: '',
    },
    placeholder: {
      type: String,
      default: '',
    },
  },
  emits: ['update:value'],
  template:
    '<input type="password" :value="value" :placeholder="placeholder" @input="$emit(\'update:value\', $event.target.value)" />',
})

const TextareaStub = defineComponent({
  props: {
    value: {
      type: String,
      default: '',
    },
  },
  emits: ['update:value'],
  template: '<textarea :value="value" @input="$emit(\'update:value\', $event.target.value)" />',
})

const InputNumberStub = defineComponent({
  props: {
    value: {
      type: Number,
      default: undefined,
    },
  },
  emits: ['update:value'],
  template:
    '<input type="number" :value="value ?? \'\'" @input="$emit(\'update:value\', $event.target.value === \'\' ? undefined : Number($event.target.value))" />',
})

const SwitchStub = defineComponent({
  props: {
    checked: {
      type: Boolean,
      default: false,
    },
  },
  emits: ['update:checked'],
  template:
    '<input type="checkbox" :checked="checked" @change="$emit(\'update:checked\', $event.target.checked)" />',
})

const ButtonStub = defineComponent({
  props: {
    disabled: {
      type: Boolean,
      default: false,
    },
    loading: {
      type: Boolean,
      default: false,
    },
  },
  emits: ['click'],
  template:
    '<button :disabled="disabled || loading" @click="$emit(\'click\')"><slot /></button>',
})

import TenantCreateWizard from '@/views/tenant/TenantCreateWizard.vue'

function createDeferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

function mountWizard() {
  return mount(TenantCreateWizard, {
    global: {
      stubs: {
        'a-steps': PassThrough,
        'a-step': defineComponent({
          props: { title: { type: String, default: '' } },
          template: '<div class="step-title">{{ title }}</div>',
        }),
        'a-form': PassThrough,
        'a-form-item': PassThrough,
        'a-input': InputStub,
        'a-input-password': InputPasswordStub,
        'a-textarea': TextareaStub,
        'a-input-number': InputNumberStub,
        'a-switch': SwitchStub,
        'a-alert': defineComponent({
          props: {
            message: { type: String, default: '' },
            description: { type: String, default: '' },
          },
          template: '<div class="alert">{{ message }}{{ description }}</div>',
        }),
        'a-button': ButtonStub,
      },
    },
  })
}

async function flushPromises() {
  await Promise.resolve()
  await nextTick()
  await Promise.resolve()
  await nextTick()
}

async function setInputValue(wrapper: ReturnType<typeof mountWizard>, placeholder: string, value: string) {
  const input = wrapper.find(`input[placeholder="${placeholder}"]`)
  expect(input.exists()).toBe(true)
  await input.setValue(value)
}

async function clickButton(wrapper: ReturnType<typeof mountWizard>, text: string) {
  const button = wrapper.findAll('button').find((node) => node.text().includes(text))
  expect(button).toBeTruthy()
  await button!.trigger('click')
  await flushPromises()
}

async function gotoConfirmStep(wrapper: ReturnType<typeof mountWizard>) {
  await setInputValue(wrapper, '请输入租户编码', 'tenant_a')
  await setInputValue(wrapper, '请输入租户名称', 'Tenant A')
  await clickButton(wrapper, '下一步')
  await clickButton(wrapper, '下一步')
  await setInputValue(wrapper, '请输入初始管理员用户名', 'tenant_admin')
  await setInputValue(wrapper, '请输入初始密码', 'Secret123')
  await setInputValue(wrapper, '请再次输入密码', 'Secret123')
  await clickButton(wrapper, '下一步')
}

describe('TenantCreateWizard.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    routeMocks.fullPath = '/system/tenant'
    apiMocks.precheckTenantCreate.mockResolvedValue({
      ok: true,
      blockingIssues: [],
      warnings: [],
      initializationSummary: {
        tenantCode: 'tenant_a',
        tenantName: 'Tenant A',
        initialAdminUsername: 'tenant_admin',
        platformTemplateReady: true,
        defaultRoleCount: 1,
        defaultMenuCount: 2,
        defaultPermissionCount: 3,
        defaultUiActionCount: 4,
        defaultApiEndpointCount: 5,
      },
    })
    apiMocks.createTenant.mockResolvedValue({
      id: 101,
      code: 'tenant_a',
      name: 'Tenant A',
    })
  })

  it('should render four wizard step titles', () => {
    const wrapper = mountWizard()
    const stepTitles = wrapper.findAll('.step-title').map((node) => node.text())
    expect(stepTitles).toEqual(['基础信息', '初始化策略', '初始管理员', '确认'])
  })

  it('should call precheck when entering confirm step', async () => {
    const wrapper = mountWizard()
    await gotoConfirmStep(wrapper)
    expect(apiMocks.precheckTenantCreate).toHaveBeenCalledTimes(1)
  })

  it('should show blocking issues and disable submit when precheck blocks', async () => {
    apiMocks.precheckTenantCreate.mockResolvedValueOnce({
      ok: false,
      blockingIssues: [{ code: 'TENANT_CODE_CONFLICT', field: 'code', message: '租户编码已存在' }],
      warnings: [],
      initializationSummary: {
        tenantCode: 'tenant_a',
        tenantName: 'Tenant A',
        initialAdminUsername: 'tenant_admin',
        platformTemplateReady: true,
        defaultRoleCount: 1,
        defaultMenuCount: 2,
        defaultPermissionCount: 3,
        defaultUiActionCount: 4,
        defaultApiEndpointCount: 5,
      },
    })
    const wrapper = mountWizard()
    await gotoConfirmStep(wrapper)

    expect(wrapper.text()).toContain('阻断项')
    expect(wrapper.text()).toContain('租户编码已存在')
    const createButton = wrapper.findAll('button').find((node) => node.text().includes('创建租户'))
    expect(createButton?.attributes('disabled')).toBeDefined()
  })

  it('should show warnings and initialization summary', async () => {
    apiMocks.precheckTenantCreate.mockResolvedValueOnce({
      ok: true,
      blockingIssues: [],
      warnings: [{ code: 'TEMPLATE_INCOMPLETE', field: 'template', message: '模板不完整' }],
      initializationSummary: {
        tenantCode: 'tenant_a',
        tenantName: 'Tenant A',
        initialAdminUsername: 'tenant_admin',
        platformTemplateReady: false,
        defaultRoleCount: 1,
        defaultMenuCount: 2,
        defaultPermissionCount: 3,
        defaultUiActionCount: 4,
        defaultApiEndpointCount: 5,
      },
    })
    const wrapper = mountWizard()
    await gotoConfirmStep(wrapper)

    expect(wrapper.text()).toContain('警告项')
    expect(wrapper.text()).toContain('模板不完整')
    expect(wrapper.text()).toContain('初始化摘要')
    expect(wrapper.text()).toContain('默认角色数')
    expect(wrapper.text()).toContain('默认菜单数')
    expect(wrapper.text()).toContain('默认 UI Action 数')
    expect(wrapper.text()).toContain('默认 API Endpoint 数')
    expect(wrapper.text()).toContain('默认权限数')
  })

  it('should invalidate precheck result after editing fields and require recheck', async () => {
    const wrapper = mountWizard()
    await gotoConfirmStep(wrapper)
    expect(wrapper.findAll('button').find((node) => node.text().includes('创建租户'))?.attributes('disabled'))
      .toBeUndefined()

    await clickButton(wrapper, '上一步')
    await setInputValue(wrapper, '请输入初始管理员用户名', 'tenant_admin_2')
    await clickButton(wrapper, '下一步')

    expect(wrapper.text()).toContain('历史预检查结果已失效')
    expect(wrapper.text()).not.toContain('初始化摘要')
    const createButton = wrapper.findAll('button').find((node) => node.text().includes('创建租户'))
    expect(createButton?.attributes('disabled')).toBeDefined()
  })

  it('should ignore outdated in-flight precheck result after payload changes', async () => {
    const delayedResponse = {
      ok: true,
      blockingIssues: [],
      warnings: [],
      initializationSummary: {
        tenantCode: 'tenant_a',
        tenantName: 'Tenant A',
        initialAdminUsername: 'tenant_admin',
        platformTemplateReady: true,
        defaultRoleCount: 1,
        defaultMenuCount: 2,
        defaultPermissionCount: 3,
        defaultUiActionCount: 4,
        defaultApiEndpointCount: 5,
      },
    }
    const deferred = createDeferred<typeof delayedResponse>()
    apiMocks.precheckTenantCreate.mockReturnValueOnce(deferred.promise)

    const wrapper = mountWizard()
    await setInputValue(wrapper, '请输入租户编码', 'tenant_a')
    await setInputValue(wrapper, '请输入租户名称', 'Tenant A')
    await clickButton(wrapper, '下一步')
    await clickButton(wrapper, '下一步')
    await setInputValue(wrapper, '请输入初始管理员用户名', 'tenant_admin')
    await setInputValue(wrapper, '请输入初始密码', 'Secret123')
    await setInputValue(wrapper, '请再次输入密码', 'Secret123')
    await clickButton(wrapper, '下一步')

    expect(wrapper.text()).toContain('预检查进行中')
    await clickButton(wrapper, '上一步')
    await setInputValue(wrapper, '请输入初始管理员用户名', 'tenant_admin_2')
    await clickButton(wrapper, '下一步')

    expect(wrapper.text()).toContain('历史预检查结果已失效')
    deferred.resolve(delayedResponse)
    await flushPromises()

    expect(wrapper.text()).toContain('历史预检查结果已失效')
    expect(wrapper.text()).not.toContain('初始化摘要')
    const createButton = wrapper.findAll('button').find((node) => node.text().includes('创建租户'))
    expect(createButton?.attributes('disabled')).toBeDefined()
  })

  it('should not call create when precheck does not pass', async () => {
    apiMocks.precheckTenantCreate.mockRejectedValueOnce(new Error('network error'))
    const wrapper = mountWizard()
    await gotoConfirmStep(wrapper)
    await clickButton(wrapper, '创建租户')

    expect(apiMocks.createTenant).not.toHaveBeenCalled()
  })

  it('should call create once when precheck passes and lock duplicate clicks', async () => {
    const deferred = createDeferred<{ id: number; code: string; name: string }>()
    apiMocks.createTenant.mockReturnValueOnce(deferred.promise)
    const wrapper = mountWizard()
    await gotoConfirmStep(wrapper)
    const createButton = wrapper.findAll('button').find((node) => node.text().includes('创建租户'))
    expect(createButton).toBeTruthy()
    await createButton!.trigger('click')
    await createButton!.trigger('click')
    await flushPromises()

    expect(apiMocks.createTenant).toHaveBeenCalledTimes(1)
    deferred.resolve({ id: 101, code: 'tenant_a', name: 'Tenant A' })
    await flushPromises()
  })

  it('should render success result page without leaking password and not auto close', async () => {
    const wrapper = mountWizard()
    await gotoConfirmStep(wrapper)
    await clickButton(wrapper, '创建租户')

    expect(wrapper.text()).toContain('租户创建成功')
    expect(wrapper.text()).toContain('新租户 ID')
    expect(wrapper.text()).toContain('101')
    expect(wrapper.text()).toContain('初始管理员账号')
    expect(wrapper.text()).not.toContain('Secret123')
    expect(wrapper.emitted('completed')).toBeFalsy()
  })

  it('should stay on failure state and allow retry or back to editing', async () => {
    apiMocks.createTenant.mockRejectedValueOnce(new Error('create failed'))
    const wrapper = mountWizard()
    await gotoConfirmStep(wrapper)
    await clickButton(wrapper, '创建租户')

    expect(wrapper.text()).toContain('租户创建失败')
    expect(wrapper.text()).toContain('返回编辑')
    expect(wrapper.text()).toContain('重试创建')

    await clickButton(wrapper, '返回编辑')
    expect(wrapper.text()).toContain('确认')
  })

  it('should render three explicit entry buttons in result page', async () => {
    const wrapper = mountWizard()
    await gotoConfirmStep(wrapper)
    await clickButton(wrapper, '创建租户')

    expect(wrapper.text()).toContain('查看租户详情')
    expect(wrapper.text()).toContain('查看权限摘要')
    expect(wrapper.text()).toContain('查看模板差异')
  })

  it('should navigate to tenant detail with specific governance section targets', async () => {
    routeMocks.fullPath = '/system/tenant?code=tenant_a'
    const wrapper = mountWizard()
    await gotoConfirmStep(wrapper)
    await clickButton(wrapper, '创建租户')

    await clickButton(wrapper, '查看租户详情')
    await clickButton(wrapper, '查看权限摘要')
    await clickButton(wrapper, '查看模板差异')
    expect(routerMocks.push).toHaveBeenNthCalledWith(1, {
      path: '/platform/tenants/101',
      query: {
        from: '/system/tenant?code=tenant_a',
        section: 'overview',
      },
    })
    expect(routerMocks.push).toHaveBeenNthCalledWith(2, {
      path: '/platform/tenants/101',
      query: {
        from: '/system/tenant?code=tenant_a',
        section: 'permission-summary',
      },
    })
    expect(routerMocks.push).toHaveBeenNthCalledWith(3, {
      path: '/platform/tenants/101',
      query: {
        from: '/system/tenant?code=tenant_a',
        section: 'template-diff',
      },
    })
    expect(routerMocks.push).toHaveBeenCalledTimes(3)
  })

  it('should emit completed only when user closes success result page', async () => {
    const wrapper = mountWizard()
    await gotoConfirmStep(wrapper)
    await clickButton(wrapper, '创建租户')
    expect(wrapper.emitted('completed')).toBeFalsy()

    await clickButton(wrapper, '完成并关闭')
    expect(wrapper.emitted('completed')).toBeTruthy()
  })
})
