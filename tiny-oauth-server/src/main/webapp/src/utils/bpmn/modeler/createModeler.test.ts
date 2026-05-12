import { beforeEach, describe, expect, it, vi } from 'vitest'

const eventHandlers = vi.hoisted(() => new Map<string, (event?: unknown) => void>())
const importXML = vi.hoisted(() => vi.fn(() => Promise.resolve()))
const saveXML = vi.hoisted(() => vi.fn(() => Promise.resolve({ xml: '<bpmn />' })))
const saveSVG = vi.hoisted(() => vi.fn(() => Promise.resolve({ svg: '<svg />' })))
const destroy = vi.hoisted(() => vi.fn())
const zoom = vi.hoisted(() => vi.fn())
const eventBus = vi.hoisted(() => ({
  on: vi.fn((eventName: string, callback: (event?: unknown) => void) => {
    eventHandlers.set(eventName, callback)
  }),
  off: vi.fn((eventName: string) => {
    eventHandlers.delete(eventName)
  }),
}))
const MockModeler = vi.hoisted(() =>
  class {
    importXML = importXML
    saveXML = saveXML
    saveSVG = saveSVG
    destroy = destroy
    get = vi.fn((serviceName: string) => {
      if (serviceName === 'eventBus') return eventBus
      if (serviceName === 'canvas') return { zoom }
      return undefined
    })
  },
)

vi.mock('bpmn-js/lib/Modeler', () => ({
  default: MockModeler,
}))
vi.mock('bpmn-js-properties-panel', () => ({
  BpmnPropertiesPanelModule: {},
  BpmnPropertiesProviderModule: {},
  CamundaPlatformPropertiesProviderModule: {},
}))
vi.mock('diagram-js-minimap', () => ({ default: {} }))
vi.mock('camunda-bpmn-moddle/resources/camunda.json', () => ({ default: {} }))
vi.mock('@/utils/bpmn/utils/translateUtils', () => ({
  getTranslateModule: vi.fn(() => Promise.resolve({ translate: ['value', vi.fn()] })),
}))

import { createModeler } from './createModeler'

describe('createModeler', () => {
  beforeEach(() => {
    eventHandlers.clear()
    vi.clearAllMocks()
  })

  it('imports XML, fits viewport, and marks the model clean', async () => {
    const onDirtyChange = vi.fn()
    const handle = await createModeler({
      container: document.createElement('div'),
      propertiesPanel: document.createElement('div'),
      onDirtyChange,
    })

    eventHandlers.get('commandStack.changed')?.()
    expect(handle.isDirty()).toBe(true)
    expect(onDirtyChange).toHaveBeenLastCalledWith(true)

    await handle.importXml('<xml />')

    expect(importXML).toHaveBeenCalledWith('<xml />')
    expect(zoom).toHaveBeenCalledWith('fit-viewport')
    expect(handle.isDirty()).toBe(false)
    expect(onDirtyChange).toHaveBeenLastCalledWith(false)
  })

  it('exposes save helpers', async () => {
    const handle = await createModeler({
      container: document.createElement('div'),
    })

    await expect(handle.saveXml()).resolves.toBe('<bpmn />')
    await expect(handle.saveSvg()).resolves.toBe('<svg />')
    expect(saveXML).toHaveBeenCalledWith({ format: true })
  })

  it('emits selection context and unregisters listeners on destroy', async () => {
    const onSelectionChange = vi.fn()
    const handle = await createModeler({
      container: document.createElement('div'),
      onSelectionChange,
    })

    eventHandlers.get('selection.changed')?.({
      newSelection: [
        {
          id: 'UserTask_1',
          type: 'bpmn:UserTask',
          businessObject: {
            id: 'UserTask_1',
            name: '审批',
            $type: 'bpmn:UserTask',
          },
        },
      ],
    })

    expect(onSelectionChange).toHaveBeenCalledWith(
      expect.objectContaining({
        elementId: 'UserTask_1',
        elementType: 'bpmn:UserTask',
        businessObjectId: 'UserTask_1',
        businessObjectName: '审批',
      }),
    )

    handle.destroy()
    handle.destroy()

    expect(eventBus.off).toHaveBeenCalledWith('commandStack.changed', expect.any(Function))
    expect(eventBus.off).toHaveBeenCalledWith('selection.changed', expect.any(Function))
    expect(destroy).toHaveBeenCalledTimes(1)
  })
})
