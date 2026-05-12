import { reactive, readonly } from 'vue'

export type BootStatus =
  | 'idle'
  | 'checking_auth'
  | 'restoring_session'
  | 'checking_security'
  | 'loading_permission'
  | 'registering_routes'
  | 'redirecting'
  | 'ready'
  | 'error'

export interface BootStageRecord {
  status: BootStatus
  startedAt: number
  endedAt?: number
  durationMs?: number
  result?: string
  errorCode?: string
}

export interface BootErrorState {
  code: string
  message: string
  detail?: string
}

interface BootState {
  runId: number
  status: BootStatus
  message: string
  detail: string
  ready: boolean
  redirect: string
  startedAt?: number
  stageStartedAt?: number
  error: BootErrorState | null
  stages: BootStageRecord[]
}

const state = reactive<BootState>({
  runId: 0,
  status: 'idle',
  message: '准备启动',
  detail: '',
  ready: false,
  redirect: '/',
  error: null,
  stages: [],
})

function closeCurrentStage(result?: string, errorCode?: string): void {
  const last = state.stages[state.stages.length - 1]
  if (!last || last.endedAt) {
    return
  }
  last.endedAt = Date.now()
  last.durationMs = last.endedAt - last.startedAt
  if (result) {
    last.result = result
  }
  if (errorCode) {
    last.errorCode = errorCode
  }
}

export function useBootState() {
  return readonly(state)
}

export function getBootRunId(): number {
  return state.runId
}

export function isBootReady(): boolean {
  return state.ready && state.status === 'ready'
}

export function beginBootRun(redirect: string): number {
  closeCurrentStage('superseded')
  state.runId += 1
  state.status = 'idle'
  state.message = '准备启动'
  state.detail = ''
  state.ready = false
  state.redirect = redirect || '/'
  state.startedAt = Date.now()
  state.stageStartedAt = undefined
  state.error = null
  state.stages.splice(0, state.stages.length)
  return state.runId
}

export function invalidateBootRun(): void {
  closeCurrentStage('cancelled')
  state.runId += 1
  state.ready = false
  state.status = 'idle'
  state.message = '准备启动'
  state.detail = ''
}

export function setBootStage(
  status: BootStatus,
  message: string,
  detail = '',
  resultForPreviousStage?: string,
): void {
  closeCurrentStage(resultForPreviousStage)
  state.status = status
  state.message = message
  state.detail = detail
  state.error = null
  state.stageStartedAt = Date.now()
  state.stages.push({
    status,
    startedAt: state.stageStartedAt,
  })
}

export function markBootReady(message = '启动完成', detail = ''): void {
  closeCurrentStage('ok')
  state.status = 'ready'
  state.message = message
  state.detail = detail
  state.ready = true
  state.error = null
}

export function markBootRedirecting(message: string, detail = ''): void {
  closeCurrentStage('redirecting')
  state.status = 'redirecting'
  state.message = message
  state.detail = detail
  state.ready = false
  state.error = null
}

export function markBootError(error: BootErrorState): void {
  closeCurrentStage('error', error.code)
  state.status = 'error'
  state.message = error.message
  state.detail = error.detail || ''
  state.ready = false
  state.error = error
}

