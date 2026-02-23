import { ref, computed, watch } from 'vue'

type ColumnLike = {
  key?: string
  dataIndex?: string
  title?: string
  [key: string]: any
}

type ToolbarFeatures = {
  columnSetting?: boolean
  density?: boolean
  zebra?: boolean
  copy?: boolean
  refresh?: boolean
}

type UseToolbarsOptions = {
  enabled?: boolean
  storageKey?: string
  baseColumns?: ColumnLike[]
  refresh?: () => void
  features?: ToolbarFeatures
  loading?: { value: boolean } | boolean
}

type ColumnSettingItem = {
  key: string
  title: string
  visible: boolean
}

const DENSITY_VALUES = ['default', 'middle', 'small'] as const
const DENSITY_LABELS: Record<string, string> = {
  default: '默认',
  middle: '中等',
  small: '紧凑',
}

function resolveColumnKey(col: ColumnLike): string {
  return String(col.dataIndex ?? col.key ?? col.title ?? '')
}

function normalizeColumns(columns: ColumnLike[]): ColumnLike[] {
  return (columns || []).filter((col) => resolveColumnKey(col))
}

function safeParse<T>(raw: string | null, fallback: T): T {
  if (!raw) return fallback
  try {
    return JSON.parse(raw) as T
  } catch {
    return fallback
  }
}

export function useToolbars(options: UseToolbarsOptions = {}) {
  const enabled = options.enabled !== false
  const storageKey = options.storageKey || 'toolbars:default'
  const baseColumns = ref<ColumnLike[]>(normalizeColumns(options.baseColumns || []))

  const density = ref<(typeof DENSITY_VALUES)[number]>('default')
  const zebraEnabled = ref<boolean>(false)
  const copyEnabled = ref<boolean>(false)

  const columnSettingVisible = ref(false)
  const columnSettingItems = ref<ColumnSettingItem[]>([])

  const loading = computed(() => {
    if (typeof options.loading === 'boolean') return options.loading
    if (options.loading && typeof options.loading === 'object') return !!options.loading.value
    return false
  })

  function loadState() {
    if (!enabled) return
    const state = safeParse<{ density?: string; zebra?: boolean; copy?: boolean; columns?: ColumnSettingItem[] }>(
      window.localStorage.getItem(storageKey),
      {},
    )
    if (state.density && DENSITY_VALUES.includes(state.density as any)) {
      density.value = state.density as any
    }
    if (typeof state.zebra === 'boolean') zebraEnabled.value = state.zebra
    if (typeof state.copy === 'boolean') copyEnabled.value = state.copy
    if (Array.isArray(state.columns)) {
      columnSettingItems.value = state.columns
    }
  }

  function persistState() {
    if (!enabled) return
    const payload = {
      density: density.value,
      zebra: zebraEnabled.value,
      copy: copyEnabled.value,
      columns: columnSettingItems.value,
    }
    window.localStorage.setItem(storageKey, JSON.stringify(payload))
  }

  function rebuildColumnSettingItems() {
    const cols = normalizeColumns(baseColumns.value)
    const existing = new Map(columnSettingItems.value.map((i) => [i.key, i]))
    columnSettingItems.value = cols.map((col) => {
      const key = resolveColumnKey(col)
      const cached = existing.get(key)
      return {
        key,
        title: String(col.title ?? key),
        visible: cached ? cached.visible : true,
      }
    })
  }

  function openColumnSetting() {
    columnSettingVisible.value = true
  }

  function moveColumnUp(key: string) {
    const idx = columnSettingItems.value.findIndex((i) => i.key === key)
    if (idx > 0) {
      const prev = columnSettingItems.value[idx - 1]
      const current = columnSettingItems.value[idx]
      if (!prev || !current) return
      columnSettingItems.value[idx - 1] = current
      columnSettingItems.value[idx] = prev
    }
  }

  function moveColumnDown(key: string) {
    const idx = columnSettingItems.value.findIndex((i) => i.key === key)
    if (idx >= 0 && idx < columnSettingItems.value.length - 1) {
      const next = columnSettingItems.value[idx + 1]
      const current = columnSettingItems.value[idx]
      if (!next || !current) return
      columnSettingItems.value[idx + 1] = current
      columnSettingItems.value[idx] = next
    }
  }

  function resetColumnSetting() {
    columnSettingItems.value = columnSettingItems.value.map((i) => ({
      ...i,
      visible: true,
    }))
    rebuildColumnSettingItems()
  }

  function saveColumnSetting() {
    persistState()
  }

  function cycleDensity() {
    const idx = DENSITY_VALUES.indexOf(density.value)
    density.value = DENSITY_VALUES[(idx + 1) % DENSITY_VALUES.length] ?? 'default'
  }

  function toggleZebra() {
    zebraEnabled.value = !zebraEnabled.value
  }

  function toggleCopy() {
    copyEnabled.value = !copyEnabled.value
  }

  function refresh() {
    options.refresh?.()
  }

  const densityLabel = computed(() => DENSITY_LABELS[density.value] || '')
  const tableSize = computed(() => density.value ?? 'default')

  const enhancedColumns = computed(() => {
    const cols = normalizeColumns(baseColumns.value)
    const visibleKeys = new Set(
      columnSettingItems.value.filter((i) => i.visible).map((i) => i.key),
    )
    const orderedKeys = columnSettingItems.value.map((i) => i.key)
    const orderIndex = new Map(orderedKeys.map((k, i) => [k, i]))

    const filtered = cols.filter((c) => visibleKeys.has(resolveColumnKey(c)))
    return filtered.sort((a, b) => {
      const ka = resolveColumnKey(a)
      const kb = resolveColumnKey(b)
      return (orderIndex.get(ka) ?? 0) - (orderIndex.get(kb) ?? 0)
    })
  })

  function rowClassName(_record: any, index: number) {
    if (!zebraEnabled.value) return ''
    return index % 2 === 1 ? 'zebra-row' : ''
  }

  function canCopyColumn(dataIndex?: string) {
    if (!copyEnabled.value) return false
    if (!dataIndex) return false
    return true
  }

  async function copyCell(record: any, dataIndex: string) {
    try {
      const value = record?.[dataIndex]
      const text = value == null ? '' : String(value)
      await navigator.clipboard.writeText(text)
    } catch {
      // ignore
    }
  }

  watch(
    () => options.baseColumns,
    (v) => {
      baseColumns.value = normalizeColumns(v || [])
      rebuildColumnSettingItems()
    },
    { deep: true, immediate: true },
  )

  watch(columnSettingItems, () => {
    if (enabled) persistState()
  }, { deep: true })

  watch([density, zebraEnabled, copyEnabled], () => {
    if (enabled) persistState()
  })

  if (typeof window !== 'undefined') {
    loadState()
  }
  rebuildColumnSettingItems()

  return {
    features: options.features,
    loading,
    densityLabel,
    zebraEnabled,
    copyEnabled,
    columnSettingVisible,
    columnSettingItems,
    openColumnSetting,
    moveColumnUp,
    moveColumnDown,
    resetColumnSetting,
    saveColumnSetting,
    cycleDensity,
    toggleZebra,
    toggleCopy,
    refresh,
    tableSize,
    enhancedColumns,
    rowClassName,
    canCopyColumn,
    copyCell,
  }
}
