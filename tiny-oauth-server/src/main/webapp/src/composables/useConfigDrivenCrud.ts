import { ref, computed, nextTick, watch } from 'vue'
import dayjs, { Dayjs } from 'dayjs'
import { message, Modal } from 'ant-design-vue'

/* =========================
 * 基础类型定义
 * ========================= */

export type DrawerMode = 'create' | 'edit' | 'view'

export type SearchFieldType = 'input' | 'select' | 'date' | 'dateRange'
export type DrawerFieldType = 'input' | 'number' | 'date' | 'switch' | 'select'

/** 查询字段配置 */
export interface SearchFieldConfig {
  field: string
  label: string
  type: SearchFieldType
  placeholder?: string
  allowClear?: boolean
  style?: any
  options?: Array<{ label: string; value: any }>
  format?: string
  rangeFields?: { start: string; end: string }
}

/** Drawer 字段配置 */
export interface DrawerFieldDef<T = any> {
  field: keyof T
  label: string
  type: DrawerFieldType

  required?: boolean
  placeholder?: string
  min?: number
  max?: number
  step?: number
  precision?: number
  options?: Array<{ label: string; value: any }>

  visible?: boolean | ((mode: DrawerMode) => boolean)
  readonly?: boolean | ((mode: DrawerMode) => boolean)

  rules?: any[] | ((mode: DrawerMode) => any[])
  trigger?: string | string[] | ((mode: DrawerMode) => string | string[])
  trim?: boolean | ((mode: DrawerMode) => boolean)

  componentProps?: Record<string, any> | ((mode: DrawerMode) => Record<string, any>)
  normalize?: (value: any, ctx: any) => any
  submitTransform?: (value: any, ctx: any) => any
}

/** FieldDef：单一数据源 */
export interface FieldDef {
  field: string
  label: string

  search?: {
    enabled: boolean
    type: SearchFieldType
    placeholder?: string
    allowClear?: boolean
    style?: any
    options?: any[]
    format?: string
    rangeFields?: { start: string; end: string }
  }

  table?: {
    enabled: boolean
    title?: string
    dataIndex?: string
    width?: number
    sorter?: boolean
    fixed?: 'left' | 'right'
    align?: 'left' | 'center' | 'right'
  }

  export?: boolean | { enabled: boolean; title?: string }
  initialValue?: any

  drawer?: {
    enabled: boolean
    type: DrawerFieldType
  } & Omit<DrawerFieldDef, 'field' | 'label' | 'type'>
}

/* =========================
 * useConfigDrivenCrud 入参
 * ========================= */

export interface UseConfigDrivenCrudOptions<T = any> {
  fieldDefs: FieldDef[]

  /** 列表查询 */
  listFn: (params: any) => Promise<{ records: T[]; total: number }>

  /** CRUD（可选） */
  createFn?: (payload: any) => Promise<any>
  updateFn?: (id: any, payload: any) => Promise<any>
  deleteFn?: (id: any) => Promise<any>

  /** 非通用能力：测试/初始化数据（可选） */
  generateFn?: (payload: any) => Promise<any>
  clearFn?: () => Promise<any>

  /** 行操作权限判断（可选） */
  hasPerm?: (perm?: string) => boolean
}

/* =========================
 * useConfigDrivenCrud 主体
 * ========================= */

export function useConfigDrivenCrud<T = any>(options: UseConfigDrivenCrudOptions<T>) {
  const {
    fieldDefs,
    listFn,
    createFn,
    updateFn,
    deleteFn,
    generateFn,
    clearFn,
    hasPerm = () => true,
  } = options

  /* =========================
   * Search（查询）
   * ========================= */

  const SEARCH_FIELDS: SearchFieldConfig[] = fieldDefs
    .filter((d) => d.search?.enabled)
    .map((d) => ({
      field: d.field,
      label: d.label,
      type: d.search!.type,
      placeholder: d.search!.placeholder,
      allowClear: d.search!.allowClear,
      style: d.search!.style,
      options: d.search!.options,
      format: d.search!.format,
      rangeFields: d.search!.rangeFields,
    }))

  const query = ref<Record<string, any>>(
    Object.fromEntries(
      SEARCH_FIELDS.map((f) => [
        f.field,
        fieldDefs.find((d) => d.field === f.field)?.initialValue ?? '',
      ]),
    ),
  )

  function buildFilters() {
    const filters: any = {}
    for (const f of SEARCH_FIELDS) {
      const v = query.value[f.field]
      if (v == null || v === '') continue

      if (f.type === 'input') filters[f.field] = String(v).trim()
      else if (f.type === 'select') filters[f.field] = v
      else if (f.type === 'date') filters[f.field] = (v as Dayjs).format(f.format || 'YYYY-MM-DD')
      else if (f.type === 'dateRange') {
        const [s, e] = v || []
        if (s && e) {
          filters[f.rangeFields?.start || `${f.field}Start`] = s.format(f.format || 'YYYY-MM-DD')
          filters[f.rangeFields?.end || `${f.field}End`] = e.format(f.format || 'YYYY-MM-DD')
        }
      }
    }
    return filters
  }

  /* =========================
   * Table（列表）
   * ========================= */

  const loading = ref(false)
  const tableData = ref<T[]>([])

  const pagination = ref({
    current: 1,
    pageSize: 10,
    total: 0,
  })

  async function loadData() {
    loading.value = true
    try {
      const res = await listFn({
        ...buildFilters(),
        current: pagination.value.current,
        pageSize: pagination.value.pageSize,
      })
      tableData.value = res.records || []
      pagination.value.total = Number(res.total) || 0
    } finally {
      loading.value = false
    }
  }

  /* =========================
   * Drawer（表单）
   * ========================= */

  const drawerVisible = ref(false)
  const drawerMode = ref<DrawerMode>('create')
  const isViewMode = computed(() => drawerMode.value === 'view')

  const drawerFields = computed<DrawerFieldDef[]>(() =>
    fieldDefs
      .filter((d) => d.drawer?.enabled)
      .map((d) => ({
        field: d.field as any,
        label: d.label,
        type: d.drawer!.type,
        ...d.drawer,
      })),
  )

  const formState = ref<any>({})

  function openCreate() {
    drawerMode.value = 'create'
    formState.value = buildInitialForm()
    drawerVisible.value = true
  }

  function openEdit(record: any) {
    drawerMode.value = 'edit'
    formState.value = buildInitialForm(record)
    drawerVisible.value = true
  }

  function openView(record: any) {
    drawerMode.value = 'view'
    formState.value = buildInitialForm(record)
    drawerVisible.value = true
  }

  function buildInitialForm(record?: any) {
    const form: any = {}
    for (const d of fieldDefs) {
      if (d.drawer?.enabled) {
        let v = record ? record[d.field] : d.initialValue
        if (d.drawer.type === 'date' && v) v = dayjs(v)
        form[d.field] = v ?? null
      }
    }
    return form
  }

  function buildSubmitPayload() {
    const payload: any = {}
    for (const f of drawerFields.value) {
      let v = formState.value[f.field]
      if (f.normalize) v = f.normalize(v, { mode: drawerMode.value, form: formState.value })
      if (f.submitTransform)
        v = f.submitTransform(v, { mode: drawerMode.value, form: formState.value })
      payload[f.field] = v
    }
    delete payload.id
    return payload
  }

  async function handleSubmit() {
    if (drawerMode.value === 'view') return
    const payload = buildSubmitPayload()

    if (drawerMode.value === 'create') {
      await createFn?.(payload)
      message.success('创建成功')
    } else {
      await updateFn?.(formState.value.id, payload)
      message.success('更新成功')
    }
    drawerVisible.value = false
    loadData()
  }

  async function handleDelete(record: any) {
    Modal.confirm({
      title: '确认删除？',
      onOk: async () => {
        await deleteFn?.(record.id)
        message.success('删除成功')
        loadData()
      },
    })
  }

  /* =========================
   * 非通用能力（可选）
   * ========================= */

  async function handleGenerate(payload: any) {
    if (!generateFn) return
    await generateFn(payload)
    message.success('生成完成')
    loadData()
  }

  async function handleClear() {
    if (!clearFn) return
    await clearFn()
    message.success('已清空')
    loadData()
  }

  /* =========================
   * 暴露能力
   * ========================= */

  return {
    // search
    SEARCH_FIELDS,
    query,
    buildFilters,

    // table
    loading,
    tableData,
    pagination,
    loadData,

    // drawer
    drawerVisible,
    drawerMode,
    isViewMode,
    drawerFields,
    formState,
    openCreate,
    openEdit,
    openView,
    handleSubmit,
    handleDelete,

    // optional
    handleGenerate,
    handleClear,

    // permission
    hasPerm,
  }
}
