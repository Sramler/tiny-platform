import BpmnModeler from 'bpmn-js/lib/Modeler'
import {
  BpmnPropertiesPanelModule,
  BpmnPropertiesProviderModule,
  CamundaPlatformPropertiesProviderModule,
} from 'bpmn-js-properties-panel'
import minimapModule from 'diagram-js-minimap'
import camundaModdleDescriptor from 'camunda-bpmn-moddle/resources/camunda.json'

import { getTranslateModule } from '@/utils/bpmn/utils/translateUtils'

import type {
  BpmnModelerLike,
  BpmnModelerService,
  CreateTinyBpmnModelerOptions,
  TinyBpmnImportOptions,
  TinyBpmnModelerHandle,
  TinyBpmnSaveOptions,
  TinyBpmnSelectionContext,
} from './modelerTypes'

import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-codes.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-embedded.css'
import '@bpmn-io/properties-panel/dist/assets/properties-panel.css'
import 'diagram-js-minimap/assets/diagram-js-minimap.css'

export async function createModeler(
  options: CreateTinyBpmnModelerOptions,
): Promise<TinyBpmnModelerHandle> {
  const additionalModules = [
    BpmnPropertiesPanelModule,
    BpmnPropertiesProviderModule,
    CamundaPlatformPropertiesProviderModule,
  ]

  if (options.enableMinimap !== false) {
    additionalModules.push(minimapModule)
  }

  const translateModule = await loadTranslateModule()
  if (translateModule) {
    additionalModules.push(translateModule)
  }

  const rawModeler = new BpmnModeler({
    container: options.container,
    propertiesPanel: options.propertiesPanel
      ? {
          parent: options.propertiesPanel,
        }
      : undefined,
    additionalModules,
    moddleExtensions: {
      camunda: camundaModdleDescriptor,
    },
    minimap:
      options.enableMinimap === false
        ? undefined
        : {
            open: true,
            height: 280,
            width: 280,
          },
  }) as BpmnModelerLike

  let dirty = false
  let destroyed = false
  const cleanupCallbacks: Array<() => void> = []

  const eventBus = rawModeler.get?.('eventBus')
  const markDirty = () => {
    dirty = true
    options.onDirtyChange?.(true)
  }
  const notifySelection = (event?: unknown) => {
    options.onSelectionChange?.(resolveSelectionContext(event))
  }

  listen(eventBus, 'commandStack.changed', markDirty, cleanupCallbacks)
  listen(eventBus, 'selection.changed', notifySelection, cleanupCallbacks)

  const markClean = () => {
    dirty = false
    options.onDirtyChange?.(false)
  }

  const handle: TinyBpmnModelerHandle = {
    async importXml(xml: string, importOptions: TinyBpmnImportOptions = {}) {
      assertAlive(destroyed)
      await rawModeler.importXML(xml)
      if (importOptions.fitViewport !== false) {
        handle.fitViewport()
      }
      if (importOptions.markClean !== false) {
        markClean()
      }
    },

    async saveXml(saveOptions: TinyBpmnSaveOptions = {}) {
      assertAlive(destroyed)
      const result = await rawModeler.saveXML({ format: saveOptions.format ?? true })
      if (!result.xml) {
        throw new Error('BPMN XML 为空')
      }
      return result.xml
    },

    async saveSvg() {
      assertAlive(destroyed)
      const result = await rawModeler.saveSVG()
      if (!result.svg) {
        throw new Error('BPMN SVG 为空')
      }
      return result.svg
    },

    destroy() {
      if (destroyed) {
        return
      }
      destroyed = true
      cleanupCallbacks.splice(0).forEach((cleanup) => cleanup())
      rawModeler.destroy()
    },

    fitViewport() {
      const canvas = rawModeler.get?.('canvas')
      if (typeof canvas?.zoom === 'function') {
        canvas.zoom('fit-viewport')
      }
    },

    markClean,

    isDirty() {
      return dirty
    },

    getRawModeler() {
      return rawModeler
    },
  }

  return handle
}

async function loadTranslateModule(): Promise<unknown | null> {
  try {
    return await getTranslateModule()
  } catch {
    return null
  }
}

function listen(
  service: BpmnModelerService | undefined,
  eventName: string,
  callback: (event?: unknown) => void,
  cleanupCallbacks: Array<() => void>,
) {
  if (typeof service?.on !== 'function') {
    return
  }
  service.on(eventName, callback)
  cleanupCallbacks.push(() => {
    if (typeof service.off === 'function') {
      service.off(eventName, callback)
    }
  })
}

function resolveSelectionContext(event?: unknown): TinyBpmnSelectionContext {
  const selectionEvent = event as
    | {
        newSelection?: unknown[]
        element?: unknown
      }
    | undefined
  const rawElement = selectionEvent?.newSelection?.[0] ?? selectionEvent?.element
  if (!rawElement || typeof rawElement !== 'object') {
    return {}
  }

  const element = rawElement as {
    id?: string
    type?: string
    businessObject?: {
      id?: string
      name?: string
      $type?: string
    }
  }
  const businessObject = element.businessObject

  return {
    elementId: element.id,
    elementType: element.type ?? businessObject?.$type,
    businessObjectId: businessObject?.id,
    businessObjectName: businessObject?.name,
    rawElement,
  }
}

function assertAlive(destroyed: boolean) {
  if (destroyed) {
    throw new Error('BPMN Modeler 已销毁')
  }
}
