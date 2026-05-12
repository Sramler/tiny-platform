export interface TinyBpmnSelectionContext {
  elementId?: string
  elementType?: string
  businessObjectId?: string
  businessObjectName?: string
  rawElement?: unknown
}

export interface TinyBpmnImportOptions {
  fitViewport?: boolean
  markClean?: boolean
}

export interface TinyBpmnSaveOptions {
  format?: boolean
}

export interface TinyBpmnModelerHandle {
  importXml(xml: string, options?: TinyBpmnImportOptions): Promise<void>
  saveXml(options?: TinyBpmnSaveOptions): Promise<string>
  saveSvg(): Promise<string>
  destroy(): void
  fitViewport(): void
  markClean(): void
  isDirty(): boolean
  getRawModeler(): unknown
}

export interface CreateTinyBpmnModelerOptions {
  container: HTMLElement
  propertiesPanel?: HTMLElement
  enableMinimap?: boolean
  onDirtyChange?: (dirty: boolean) => void
  onSelectionChange?: (context: TinyBpmnSelectionContext) => void
}

export type BpmnModelerService = {
  on?: (eventName: string, callback: (event?: unknown) => void) => void
  off?: (eventName: string, callback: (event?: unknown) => void) => void
  zoom?: (value: string | number) => void
}

export type BpmnModelerLike = {
  importXML: (xml: string) => Promise<unknown>
  saveXML: (options?: TinyBpmnSaveOptions) => Promise<{ xml?: string }>
  saveSVG: () => Promise<{ svg?: string }>
  destroy: () => void
  get?: (serviceName: string) => BpmnModelerService | undefined
}
