import { mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMocks = vi.hoisted(() => ({
  post: vi.fn(),
}))

const uiMocks = vi.hoisted(() => ({
  modalInfo: vi.fn(),
  messageSuccess: vi.fn(),
  messageError: vi.fn(),
}))

const tenantMocks = vi.hoisted(() => ({
  getActiveTenantId: vi.fn(),
}))

vi.mock('@/utils/request', () => ({
  default: {
    post: requestMocks.post,
  },
}))

vi.mock('@/utils/tenant', () => ({
  getActiveTenantId: tenantMocks.getActiveTenantId,
}))

vi.mock('ant-design-vue', () => ({
  Modal: {
    info: uiMocks.modalInfo,
  },
  message: {
    success: uiMocks.messageSuccess,
    error: uiMocks.messageError,
  },
}))

import ExportTaskExamples from '@/views/export/ExportTaskExamples.vue'

const PassThrough = defineComponent({
  template: '<div><slot /></div>',
})

const ButtonStub = defineComponent({
  emits: ['click'],
  template: '<button @click="$emit(\'click\', $event)"><slot /></button>',
})

async function flushPromises() {
  await Promise.resolve()
  await nextTick()
}

describe('ExportTaskExamples.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    tenantMocks.getActiveTenantId.mockReturnValue('1')
  })

  function mountView() {
    return mount(ExportTaskExamples, {
      global: {
        stubs: {
          'a-typography-title': PassThrough,
          'a-typography-paragraph': PassThrough,
          'a-tag': PassThrough,
          'a-descriptions': PassThrough,
          'a-descriptions-item': PassThrough,
          'a-space': PassThrough,
          'a-button': ButtonStub,
          'a-alert': PassThrough,
        },
      },
    })
  }

  it('should post sync export request and trigger browser download', async () => {
    const wrapper = mountView()
    const blob = new Blob(['demo'])
    requestMocks.post.mockResolvedValue(blob)

    const createObjectURL = vi.fn(() => 'blob:demo')
    const revokeObjectURL = vi.fn()
    vi.stubGlobal('URL', {
      createObjectURL,
      revokeObjectURL,
    } as unknown as typeof URL)

    const clickSpy = vi.fn()
    const anchor = {
      href: '',
      download: '',
      click: clickSpy,
    }
    const createElementSpy = vi.spyOn(document, 'createElement').mockImplementation(((tagName: string) => {
      if (tagName.toLowerCase() === 'a') {
        return anchor as unknown as HTMLAnchorElement
      }
      return document.createElementNS('http://www.w3.org/1999/xhtml', tagName)
    }) as typeof document.createElement)

    const syncButton = wrapper.findAll('button').find((button) => button.text().includes('发起同步导出'))
    await syncButton!.trigger('click')
    await flushPromises()

    expect(requestMocks.post).toHaveBeenCalledWith(
      '/export/sync',
      expect.objectContaining({
        fileName: 'demo_export_usage',
        async: false,
        sheets: [
          expect.objectContaining({
            exportType: 'demo_export_usage',
            filters: expect.objectContaining({
              is_billable: true,
              activeTenantId: '1',
            }),
          }),
        ],
      }),
      expect.objectContaining({
        responseType: 'blob',
      }),
    )
    expect(createObjectURL).toHaveBeenCalledWith(blob)
    expect(anchor.download).toBe('demo_export_usage.xlsx')
    expect(anchor.href).toBe('blob:demo')
    expect(clickSpy).toHaveBeenCalledTimes(1)
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:demo')
    expect(uiMocks.messageSuccess).toHaveBeenCalledWith(
      '已发起同步导出（demo_export_usage），请查看下载的文件',
    )

    createElementSpy.mockRestore()
  })

  it('should post async export request and show task id message', async () => {
    const wrapper = mountView()
    requestMocks.post.mockResolvedValue({ taskId: 'task-1' })

    const asyncButton = wrapper.findAll('button').find((button) => button.text().includes('发起异步导出任务'))
    await asyncButton!.trigger('click')
    await flushPromises()

    expect(requestMocks.post).toHaveBeenCalledWith(
      '/export/async',
      expect.objectContaining({
        fileName: 'demo_export_usage',
        async: true,
      }),
    )
    expect(uiMocks.messageSuccess).toHaveBeenCalledWith(
      '异步导出任务已创建，taskId=task-1，可在列表中刷新查看',
    )
  })

  it('should show sync export error when request fails', async () => {
    const wrapper = mountView()
    requestMocks.post.mockRejectedValue(new Error('network down'))

    const syncButton = wrapper.findAll('button').find((button) => button.text().includes('发起同步导出'))
    await syncButton!.trigger('click')
    await flushPromises()

    expect(uiMocks.messageError).toHaveBeenCalledWith('同步导出示例调用失败：network down')
  })

  it('should show async export error when request fails', async () => {
    const wrapper = mountView()
    requestMocks.post.mockRejectedValue(new Error('submit failed'))

    const asyncButton = wrapper.findAll('button').find((button) => button.text().includes('发起异步导出任务'))
    await asyncButton!.trigger('click')
    await flushPromises()

    expect(uiMocks.messageError).toHaveBeenCalledWith('异步导出示例调用失败：submit failed')
  })

  it('should fall back to generic async success message when task id is absent', async () => {
    const wrapper = mountView()
    requestMocks.post.mockResolvedValue({})

    const asyncButton = wrapper.findAll('button').find((button) => button.text().includes('发起异步导出任务'))
    await asyncButton!.trigger('click')
    await flushPromises()

    expect(uiMocks.messageSuccess).toHaveBeenCalledWith('异步导出任务已提交，请在列表中刷新查看')
  })

  it('should open sync demo modal with current tenant in payload', async () => {
    const wrapper = mountView()

    const demoButton = wrapper.findAll('button').find((button) => button.text().includes('查看请求示例'))
    await demoButton!.trigger('click')

    expect(uiMocks.modalInfo).toHaveBeenCalledWith(
      expect.objectContaining({
        title: '同步导出示例（demo_export_usage）',
      }),
    )

    const modalConfig = uiMocks.modalInfo.mock.calls[0]?.[0]
    const contentVNode = modalConfig.content()
    expect(String(contentVNode.children)).toContain('"activeTenantId": "1"')
    expect(String(contentVNode.children)).toContain('"recordTenantId"')
  })
})
