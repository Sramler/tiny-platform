import { expect, test, type Locator, type Page } from '@playwright/test'

const backendBaseUrl = process.env.E2E_BACKEND_BASE_URL ?? 'http://localhost:9000'
const parallelDagBaseCode = process.env.E2E_PARALLEL_DAG_CODE_BASE ?? 'sales-report-pipeline'
const serialDagBaseCode = process.env.E2E_SERIAL_DAG_CODE_BASE ?? 'serial-sales-pipeline'
const retryableFailureDagBaseCode =
  process.env.E2E_RETRYABLE_FAILURE_DAG_CODE_BASE ?? 'retryable-failure-pipeline'
const cancellableDagBaseCode =
  process.env.E2E_CANCELLABLE_DAG_CODE_BASE ?? 'cancellable-pipeline'
const pauseResumeDagBaseCode =
  process.env.E2E_PAUSE_RESUME_DAG_CODE_BASE ?? 'pause-resume-pipeline'
const triggerNodeDagBaseCode =
  process.env.E2E_TRIGGER_NODE_DAG_CODE_BASE ?? 'trigger-node-pipeline'

type NodeSnapshot = Record<string, string>
type NodeRecord = {
  instanceId: string
  nodeCode: string
  attemptNo: number
  status: string
}
type CreatedDag = {
  dagId: number
  versionId: number
  dagCode: string
}

type RetryableFailureDag = CreatedDag & {
  unstableTaskId: number
  unstableTaskUpdate: {
    typeId: number
    code: string
    name: string
    description: string
    params: string
    timeoutSec: number
    maxRetry: number
    concurrencyPolicy: string
    enabled: boolean
  }
}

function dagTableRow(page: Page, dagCode: string): Locator {
  return page.locator('.ant-table-tbody > tr').filter({ hasText: dagCode }).first()
}

function historyTableRows(page: Page): Locator {
  return page.locator('.ant-table-tbody > tr')
}

function nodeDialog(page: Page): Locator {
  return page.locator('.ant-modal-root .ant-modal').filter({ hasText: '节点执行记录' }).last()
}

async function openAuthenticatedShell(page: Page) {
  await page.goto('/OIDCDebug')
  await expect(page.getByRole('heading', { name: 'OIDC 调试工具' })).toBeVisible({
    timeout: 90_000,
  })
}

async function openDagList(page: Page) {
  await openAuthenticatedShell(page)
  await page.goto('/scheduling/dag')
  if (await page.getByText('DAG 列表').first().isVisible().catch(() => false)) {
    return
  }
  await openAuthenticatedShell(page)

  let schedulingMenu = page.getByText(/调度(中心|管理)/).first()
  if (!(await schedulingMenu.isVisible().catch(() => false))) {
    const advancedMenu = page.getByText('高级工具').first()
    if (await advancedMenu.isVisible().catch(() => false)) {
      await advancedMenu.click()
    }
    schedulingMenu = page.getByText(/调度(中心|管理)/).first()
  }

  if (await schedulingMenu.isVisible().catch(() => false)) {
    await schedulingMenu.click()
  }

  const dagMenu = page.getByText(/dag管理/i).first()
  await expect(dagMenu).toBeVisible({ timeout: 30_000 })
  await dagMenu.click()
  await expect(page.getByText('DAG 列表').first()).toBeVisible({ timeout: 90_000 })
}

async function filterDagByCode(page: Page, dagCode: string) {
  await page.getByPlaceholder('请输入编码').fill(dagCode)
  await page.getByRole('button', { name: /搜\s*索/ }).click()
  await expect(dagTableRow(page, dagCode)).toBeVisible()
}

async function triggerDagFromList(page: Page, dagCode: string) {
  const row = dagTableRow(page, dagCode)
  await row.getByRole('button', { name: '触发' }).click()
  await page.getByRole('button', { name: '确认触发' }).click()
  await expect(page.getByText('已创建新的手动运行')).toBeVisible()
}

async function callSchedulingApi<T>(
  page: Page,
  method: 'GET' | 'POST' | 'PUT' | 'DELETE',
  path: string,
  body?: unknown
): Promise<T> {
  return page.evaluate(
    async ({ apiMethod, apiPath, apiBody, apiBaseUrl }) => {
      const oidcKey = Object.keys(window.localStorage).find((key) => key.startsWith('oidc.user:'))
      if (!oidcKey) {
        throw new Error('未找到 OIDC 登录态，无法调用调度 API')
      }

      const rawUser = window.localStorage.getItem(oidcKey)
      if (!rawUser) {
        throw new Error(`OIDC 存储为空: ${oidcKey}`)
      }

      const user = JSON.parse(rawUser) as {
        access_token?: string
        profile?: { tenantId?: number | string }
      }
      const accessToken = user.access_token
      if (!accessToken) {
        throw new Error('OIDC 用户缺少 access_token')
      }

      const tenantId =
        window.localStorage.getItem('app_tenant_id') ?? String(user.profile?.tenantId ?? 1)
      const headers = new Headers({
        Accept: 'application/json',
        Authorization: `Bearer ${accessToken}`,
        'X-Tenant-Id': tenantId,
      })
      if (apiMethod !== 'GET') {
        const sanitizedPath = apiPath.replace(/[^A-Za-z0-9._:-]/g, '-')
        const uniqueSuffix =
          typeof crypto.randomUUID === 'function'
            ? crypto.randomUUID()
            : `${Date.now()}:${Math.random().toString(16).slice(2)}`
        headers.set('Content-Type', 'application/json')
        headers.set(
          'X-Idempotency-Key',
          `${uniqueSuffix}:${sanitizedPath}`
        )
      }

      const response = await fetch(`${apiBaseUrl}${apiPath}`, {
        method: apiMethod,
        headers,
        body: apiBody == null ? undefined : JSON.stringify(apiBody),
      })

      const rawText = await response.text()
      const contentType = response.headers.get('content-type') || ''
      const payload =
        rawText && contentType.includes('application/json') ? JSON.parse(rawText) : rawText || null

      if (!response.ok) {
        const message =
          typeof payload === 'string'
            ? payload
            : payload?.detail || payload?.message || JSON.stringify(payload)
        throw new Error(`${apiMethod} ${apiPath} failed: ${response.status} ${message}`)
      }

      return payload as T
    },
    {
      apiMethod: method,
      apiPath: path,
      apiBody: body,
      apiBaseUrl: backendBaseUrl,
    }
  )
}

function buildUniqueCode(base: string) {
  const suffix = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
  return `${base}-${suffix}`.toLowerCase()
}

async function provisionParallelDag(page: Page): Promise<CreatedDag> {
  const dagCode = buildUniqueCode(parallelDagBaseCode)
  const reportStatType = await callSchedulingApi<{ id: number }>(page, 'POST', '/scheduling/task-type', {
    code: buildUniqueCode('e2e-report-stat'),
    name: 'E2E-报表统计',
    description: 'real e2e 并行统计节点',
    executor: 'delayTaskExecutor',
    defaultTimeoutSec: 60,
    defaultMaxRetry: 0,
    enabled: true,
  })
  const reportSummaryType = await callSchedulingApi<{ id: number }>(page, 'POST', '/scheduling/task-type', {
    code: buildUniqueCode('e2e-report-summary'),
    name: 'E2E-报表汇总',
    description: 'real e2e 归并汇总节点',
    executor: 'loggingTaskExecutor',
    defaultTimeoutSec: 60,
    defaultMaxRetry: 0,
    enabled: true,
  })

  const userTask = await callSchedulingApi<{ id: number }>(page, 'POST', '/scheduling/task', {
    typeId: reportStatType.id,
    code: buildUniqueCode('e2e-report-user'),
    name: 'E2E-用户统计',
    description: '并行统计分支：用户',
    params: JSON.stringify({ delayMs: 4000, message: '用户统计' }),
    timeoutSec: 60,
    maxRetry: 0,
    concurrencyPolicy: 'PARALLEL',
    enabled: true,
  })
  const orderTask = await callSchedulingApi<{ id: number }>(page, 'POST', '/scheduling/task', {
    typeId: reportStatType.id,
    code: buildUniqueCode('e2e-report-order'),
    name: 'E2E-订单统计',
    description: '并行统计分支：订单',
    params: JSON.stringify({ delayMs: 3500, message: '订单统计' }),
    timeoutSec: 60,
    maxRetry: 0,
    concurrencyPolicy: 'PARALLEL',
    enabled: true,
  })
  const summaryTask = await callSchedulingApi<{ id: number }>(page, 'POST', '/scheduling/task', {
    typeId: reportSummaryType.id,
    code: buildUniqueCode('e2e-report-summary-task'),
    name: 'E2E-汇总输出',
    description: '并行统计后的归并节点',
    params: JSON.stringify({ message: '日报汇总完成', step: 'summary' }),
    timeoutSec: 60,
    maxRetry: 0,
    concurrencyPolicy: 'PARALLEL',
    enabled: true,
  })

  const dag = await callSchedulingApi<{ id: number }>(page, 'POST', '/scheduling/dag', {
    code: dagCode,
    name: `E2E 销售报表流水线 ${dagCode}`,
    description: '并行子统计 -> 归并汇总',
    enabled: true,
    cronExpression: '',
    cronEnabled: false,
  })
  const version = await callSchedulingApi<{ id: number; versionNo: number }>(
    page,
    'POST',
    `/scheduling/dag/${dag.id}/version`,
    {
      status: 'DRAFT',
      definition: JSON.stringify({ nodes: [], edges: [] }),
    }
  )

  await callSchedulingApi(page, 'POST', `/scheduling/dag/${dag.id}/version/${version.id}/node`, {
    nodeCode: 'user_stat',
    taskId: userTask.id,
    name: '用户统计',
    parallelGroup: 'report-root',
    meta: JSON.stringify({ e2e: true }),
  })
  await callSchedulingApi(page, 'POST', `/scheduling/dag/${dag.id}/version/${version.id}/node`, {
    nodeCode: 'order_stat',
    taskId: orderTask.id,
    name: '订单统计',
    parallelGroup: 'report-root',
    meta: JSON.stringify({ e2e: true }),
  })
  await callSchedulingApi(page, 'POST', `/scheduling/dag/${dag.id}/version/${version.id}/node`, {
    nodeCode: 'merge_report',
    taskId: summaryTask.id,
    name: '汇总输出',
    meta: JSON.stringify({ e2e: true }),
  })
  await callSchedulingApi(page, 'POST', `/scheduling/dag/${dag.id}/version/${version.id}/edge`, {
    fromNodeCode: 'user_stat',
    toNodeCode: 'merge_report',
  })
  await callSchedulingApi(page, 'POST', `/scheduling/dag/${dag.id}/version/${version.id}/edge`, {
    fromNodeCode: 'order_stat',
    toNodeCode: 'merge_report',
  })
  await callSchedulingApi(page, 'PUT', `/scheduling/dag/${dag.id}/version/${version.id}`, {
    versionNo: version.versionNo,
    status: 'ACTIVE',
    definition: JSON.stringify({
      nodes: ['user_stat', 'order_stat', 'merge_report'],
      edges: [
        { from: 'user_stat', to: 'merge_report' },
        { from: 'order_stat', to: 'merge_report' },
      ],
    }),
  })

  return { dagId: dag.id, versionId: version.id, dagCode }
}

async function provisionSerialDag(page: Page): Promise<CreatedDag> {
  const dagCode = buildUniqueCode(serialDagBaseCode)
  const serialType = await callSchedulingApi<{ id: number }>(page, 'POST', '/scheduling/task-type', {
    code: buildUniqueCode('e2e-serial-stage'),
    name: 'E2E-串行阶段',
    description: 'real e2e 串行链路阶段',
    executor: 'delayTaskExecutor',
    defaultTimeoutSec: 60,
    defaultMaxRetry: 0,
    enabled: true,
  })

  const extractTask = await callSchedulingApi<{ id: number }>(page, 'POST', '/scheduling/task', {
    typeId: serialType.id,
    code: buildUniqueCode('e2e-serial-extract'),
    name: 'E2E-提取',
    description: '串行阶段 1',
    params: JSON.stringify({ delayMs: 2500, message: 'extract' }),
    timeoutSec: 60,
    maxRetry: 0,
    concurrencyPolicy: 'SEQUENTIAL',
    enabled: true,
  })
  const normalizeTask = await callSchedulingApi<{ id: number }>(page, 'POST', '/scheduling/task', {
    typeId: serialType.id,
    code: buildUniqueCode('e2e-serial-normalize'),
    name: 'E2E-标准化',
    description: '串行阶段 2',
    params: JSON.stringify({ delayMs: 2000, message: 'normalize' }),
    timeoutSec: 60,
    maxRetry: 0,
    concurrencyPolicy: 'SEQUENTIAL',
    enabled: true,
  })
  const aggregateTask = await callSchedulingApi<{ id: number }>(page, 'POST', '/scheduling/task', {
    typeId: serialType.id,
    code: buildUniqueCode('e2e-serial-aggregate'),
    name: 'E2E-聚合',
    description: '串行阶段 3',
    params: JSON.stringify({ delayMs: 1500, message: 'aggregate' }),
    timeoutSec: 60,
    maxRetry: 0,
    concurrencyPolicy: 'SEQUENTIAL',
    enabled: true,
  })
  const finalizeTask = await callSchedulingApi<{ id: number }>(page, 'POST', '/scheduling/task', {
    typeId: serialType.id,
    code: buildUniqueCode('e2e-serial-finalize'),
    name: 'E2E-收尾',
    description: '串行阶段 4',
    params: JSON.stringify({ delayMs: 1000, message: 'finalize' }),
    timeoutSec: 60,
    maxRetry: 0,
    concurrencyPolicy: 'SEQUENTIAL',
    enabled: true,
  })

  const dag = await callSchedulingApi<{ id: number }>(page, 'POST', '/scheduling/dag', {
    code: dagCode,
    name: `E2E 串行流水线 ${dagCode}`,
    description: '串行阶段推进',
    enabled: true,
    cronExpression: '',
    cronEnabled: false,
  })
  const version = await callSchedulingApi<{ id: number; versionNo: number }>(
    page,
    'POST',
    `/scheduling/dag/${dag.id}/version`,
    {
      status: 'DRAFT',
      definition: JSON.stringify({ nodes: [], edges: [] }),
    }
  )

  await callSchedulingApi(page, 'POST', `/scheduling/dag/${dag.id}/version/${version.id}/node`, {
    nodeCode: 'extract',
    taskId: extractTask.id,
    name: '提取',
    meta: JSON.stringify({ e2e: true }),
  })
  await callSchedulingApi(page, 'POST', `/scheduling/dag/${dag.id}/version/${version.id}/node`, {
    nodeCode: 'normalize',
    taskId: normalizeTask.id,
    name: '标准化',
    meta: JSON.stringify({ e2e: true }),
  })
  await callSchedulingApi(page, 'POST', `/scheduling/dag/${dag.id}/version/${version.id}/node`, {
    nodeCode: 'aggregate',
    taskId: aggregateTask.id,
    name: '聚合',
    meta: JSON.stringify({ e2e: true }),
  })
  await callSchedulingApi(page, 'POST', `/scheduling/dag/${dag.id}/version/${version.id}/node`, {
    nodeCode: 'finalize',
    taskId: finalizeTask.id,
    name: '收尾',
    meta: JSON.stringify({ e2e: true }),
  })
  await callSchedulingApi(page, 'POST', `/scheduling/dag/${dag.id}/version/${version.id}/edge`, {
    fromNodeCode: 'extract',
    toNodeCode: 'normalize',
  })
  await callSchedulingApi(page, 'POST', `/scheduling/dag/${dag.id}/version/${version.id}/edge`, {
    fromNodeCode: 'normalize',
    toNodeCode: 'aggregate',
  })
  await callSchedulingApi(page, 'POST', `/scheduling/dag/${dag.id}/version/${version.id}/edge`, {
    fromNodeCode: 'aggregate',
    toNodeCode: 'finalize',
  })
  await callSchedulingApi(page, 'PUT', `/scheduling/dag/${dag.id}/version/${version.id}`, {
    versionNo: version.versionNo,
    status: 'ACTIVE',
    definition: JSON.stringify({
      nodes: ['extract', 'normalize', 'aggregate', 'finalize'],
      edges: [
        { from: 'extract', to: 'normalize' },
        { from: 'normalize', to: 'aggregate' },
        { from: 'aggregate', to: 'finalize' },
      ],
    }),
  })

  return { dagId: dag.id, versionId: version.id, dagCode }
}

async function provisionRetryableFailureDag(page: Page): Promise<RetryableFailureDag> {
  const dagCode = buildUniqueCode(retryableFailureDagBaseCode)
  const serialType = await callSchedulingApi<{ id: number }>(page, 'POST', '/scheduling/task-type', {
    code: buildUniqueCode('e2e-retryable-stage'),
    name: 'E2E-可恢复失败阶段',
    description: 'real e2e 失败后可修复并重试的阶段',
    executor: 'delayTaskExecutor',
    defaultTimeoutSec: 60,
    defaultMaxRetry: 0,
    enabled: true,
  })

  const prepareTask = await callSchedulingApi<{ id: number }>(page, 'POST', '/scheduling/task', {
    typeId: serialType.id,
    code: buildUniqueCode('e2e-retryable-prepare'),
    name: 'E2E-准备阶段',
    description: '失败前的串行准备节点',
    params: JSON.stringify({ delayMs: 1000, message: 'prepare' }),
    timeoutSec: 60,
    maxRetry: 0,
    concurrencyPolicy: 'SEQUENTIAL',
    enabled: true,
  })
  const unstableTaskCode = buildUniqueCode('e2e-retryable-unstable')
  const unstableTask = await callSchedulingApi<{ id: number }>(page, 'POST', '/scheduling/task', {
    typeId: serialType.id,
    code: unstableTaskCode,
    name: 'E2E-不稳定阶段',
    description: '首次执行确定失败，修复后可重试',
    params: JSON.stringify({ delayMs: 800, message: 'unstable', fail: true }),
    timeoutSec: 60,
    maxRetry: 0,
    concurrencyPolicy: 'SEQUENTIAL',
    enabled: true,
  })
  const finalizeTask = await callSchedulingApi<{ id: number }>(page, 'POST', '/scheduling/task', {
    typeId: serialType.id,
    code: buildUniqueCode('e2e-retryable-finalize'),
    name: 'E2E-收尾阶段',
    description: '失败节点后的收尾节点',
    params: JSON.stringify({ delayMs: 500, message: 'finalize' }),
    timeoutSec: 60,
    maxRetry: 0,
    concurrencyPolicy: 'SEQUENTIAL',
    enabled: true,
  })

  const dag = await callSchedulingApi<{ id: number }>(page, 'POST', '/scheduling/dag', {
    code: dagCode,
    name: `E2E 可恢复失败流水线 ${dagCode}`,
    description: '串行阶段失败后通过 run 级重试恢复',
    enabled: true,
    cronExpression: '',
    cronEnabled: false,
  })
  const version = await callSchedulingApi<{ id: number; versionNo: number }>(
    page,
    'POST',
    `/scheduling/dag/${dag.id}/version`,
    {
      status: 'DRAFT',
      definition: JSON.stringify({ nodes: [], edges: [] }),
    }
  )

  await callSchedulingApi(page, 'POST', `/scheduling/dag/${dag.id}/version/${version.id}/node`, {
    nodeCode: 'prepare',
    taskId: prepareTask.id,
    name: '准备',
    meta: JSON.stringify({ e2e: true }),
  })
  await callSchedulingApi(page, 'POST', `/scheduling/dag/${dag.id}/version/${version.id}/node`, {
    nodeCode: 'unstable_stage',
    taskId: unstableTask.id,
    name: '不稳定阶段',
    meta: JSON.stringify({ e2e: true }),
  })
  await callSchedulingApi(page, 'POST', `/scheduling/dag/${dag.id}/version/${version.id}/node`, {
    nodeCode: 'finalize',
    taskId: finalizeTask.id,
    name: '收尾',
    meta: JSON.stringify({ e2e: true }),
  })
  await callSchedulingApi(page, 'POST', `/scheduling/dag/${dag.id}/version/${version.id}/edge`, {
    fromNodeCode: 'prepare',
    toNodeCode: 'unstable_stage',
  })
  await callSchedulingApi(page, 'POST', `/scheduling/dag/${dag.id}/version/${version.id}/edge`, {
    fromNodeCode: 'unstable_stage',
    toNodeCode: 'finalize',
  })
  await callSchedulingApi(page, 'PUT', `/scheduling/dag/${dag.id}/version/${version.id}`, {
    versionNo: version.versionNo,
    status: 'ACTIVE',
    definition: JSON.stringify({
      nodes: ['prepare', 'unstable_stage', 'finalize'],
      edges: [
        { from: 'prepare', to: 'unstable_stage' },
        { from: 'unstable_stage', to: 'finalize' },
      ],
    }),
  })

  return {
    dagId: dag.id,
    versionId: version.id,
    dagCode,
    unstableTaskId: unstableTask.id,
    unstableTaskUpdate: {
      typeId: serialType.id,
      code: unstableTaskCode,
      name: 'E2E-不稳定阶段',
      description: '首次执行确定失败，修复后可重试',
      params: JSON.stringify({ delayMs: 800, message: 'unstable-recovered', fail: false }),
      timeoutSec: 60,
      maxRetry: 0,
      concurrencyPolicy: 'SEQUENTIAL',
      enabled: true,
    },
  }
}

async function provisionCancellableDag(page: Page): Promise<CreatedDag> {
  const dagCode = buildUniqueCode(cancellableDagBaseCode)
  const serialType = await callSchedulingApi<{ id: number }>(page, 'POST', '/scheduling/task-type', {
    code: buildUniqueCode('e2e-cancel-stage'),
    name: 'E2E-可取消阶段',
    description: 'real e2e 运行中停止用阶段',
    executor: 'delayTaskExecutor',
    defaultTimeoutSec: 60,
    defaultMaxRetry: 0,
    enabled: true,
  })

  const longTask = await callSchedulingApi<{ id: number }>(page, 'POST', '/scheduling/task', {
    typeId: serialType.id,
    code: buildUniqueCode('e2e-cancel-long'),
    name: 'E2E-长耗时阶段',
    description: '运行中停止时应被取消',
    params: JSON.stringify({ delayMs: 6000, message: 'long-stage' }),
    timeoutSec: 60,
    maxRetry: 0,
    concurrencyPolicy: 'SEQUENTIAL',
    enabled: true,
  })
  const downstreamTask = await callSchedulingApi<{ id: number }>(page, 'POST', '/scheduling/task', {
    typeId: serialType.id,
    code: buildUniqueCode('e2e-cancel-downstream'),
    name: 'E2E-后继阶段',
    description: '停止后不应执行成功',
    params: JSON.stringify({ delayMs: 500, message: 'downstream' }),
    timeoutSec: 60,
    maxRetry: 0,
    concurrencyPolicy: 'SEQUENTIAL',
    enabled: true,
  })

  const dag = await callSchedulingApi<{ id: number }>(page, 'POST', '/scheduling/dag', {
    code: dagCode,
    name: `E2E 可取消流水线 ${dagCode}`,
    description: '运行中 stop 当前 run',
    enabled: true,
    cronExpression: '',
    cronEnabled: false,
  })
  const version = await callSchedulingApi<{ id: number; versionNo: number }>(
    page,
    'POST',
    `/scheduling/dag/${dag.id}/version`,
    {
      status: 'DRAFT',
      definition: JSON.stringify({ nodes: [], edges: [] }),
    }
  )

  await callSchedulingApi(page, 'POST', `/scheduling/dag/${dag.id}/version/${version.id}/node`, {
    nodeCode: 'long_stage',
    taskId: longTask.id,
    name: '长耗时阶段',
    meta: JSON.stringify({ e2e: true }),
  })
  await callSchedulingApi(page, 'POST', `/scheduling/dag/${dag.id}/version/${version.id}/node`, {
    nodeCode: 'downstream_stage',
    taskId: downstreamTask.id,
    name: '后继阶段',
    meta: JSON.stringify({ e2e: true }),
  })
  await callSchedulingApi(page, 'POST', `/scheduling/dag/${dag.id}/version/${version.id}/edge`, {
    fromNodeCode: 'long_stage',
    toNodeCode: 'downstream_stage',
  })
  await callSchedulingApi(page, 'PUT', `/scheduling/dag/${dag.id}/version/${version.id}`, {
    versionNo: version.versionNo,
    status: 'ACTIVE',
    definition: JSON.stringify({
      nodes: ['long_stage', 'downstream_stage'],
      edges: [{ from: 'long_stage', to: 'downstream_stage' }],
    }),
  })

  return { dagId: dag.id, versionId: version.id, dagCode }
}

async function provisionPauseResumeDag(page: Page): Promise<CreatedDag> {
  const dagCode = buildUniqueCode(pauseResumeDagBaseCode)
  const serialType = await callSchedulingApi<{ id: number }>(page, 'POST', '/scheduling/task-type', {
    code: buildUniqueCode('e2e-pause-resume-stage'),
    name: 'E2E-可暂停恢复阶段',
    description: 'real e2e 节点暂停/恢复阶段',
    executor: 'delayTaskExecutor',
    defaultTimeoutSec: 60,
    defaultMaxRetry: 0,
    enabled: true,
  })

  const prepareTask = await callSchedulingApi<{ id: number }>(page, 'POST', '/scheduling/task', {
    typeId: serialType.id,
    code: buildUniqueCode('e2e-pause-prepare'),
    name: 'E2E-长耗时准备',
    description: '给下游节点提供稳定的暂停窗口',
    params: JSON.stringify({ delayMs: 6000, message: 'prepare-for-pause' }),
    timeoutSec: 60,
    maxRetry: 0,
    concurrencyPolicy: 'SEQUENTIAL',
    enabled: true,
  })
  const pausedTask = await callSchedulingApi<{ id: number }>(page, 'POST', '/scheduling/task', {
    typeId: serialType.id,
    code: buildUniqueCode('e2e-pause-gate'),
    name: 'E2E-待暂停节点',
    description: '在当前 run 里暂停并恢复',
    params: JSON.stringify({ delayMs: 1200, message: 'approval-gate' }),
    timeoutSec: 60,
    maxRetry: 0,
    concurrencyPolicy: 'SEQUENTIAL',
    enabled: true,
  })
  const finalizeTask = await callSchedulingApi<{ id: number }>(page, 'POST', '/scheduling/task', {
    typeId: serialType.id,
    code: buildUniqueCode('e2e-pause-finalize'),
    name: 'E2E-恢复后收尾',
    description: '节点恢复后应继续执行成功',
    params: JSON.stringify({ delayMs: 600, message: 'finalize-after-resume' }),
    timeoutSec: 60,
    maxRetry: 0,
    concurrencyPolicy: 'SEQUENTIAL',
    enabled: true,
  })

  const dag = await callSchedulingApi<{ id: number }>(page, 'POST', '/scheduling/dag', {
    code: dagCode,
    name: `E2E 节点暂停恢复流水线 ${dagCode}`,
    description: '节点暂停后等待恢复再继续执行',
    enabled: true,
    cronExpression: '',
    cronEnabled: false,
  })
  const version = await callSchedulingApi<{ id: number; versionNo: number }>(
    page,
    'POST',
    `/scheduling/dag/${dag.id}/version`,
    {
      status: 'DRAFT',
      definition: JSON.stringify({ nodes: [], edges: [] }),
    }
  )

  await callSchedulingApi(page, 'POST', `/scheduling/dag/${dag.id}/version/${version.id}/node`, {
    nodeCode: 'prepare',
    taskId: prepareTask.id,
    name: '准备',
    meta: JSON.stringify({ e2e: true }),
  })
  await callSchedulingApi(page, 'POST', `/scheduling/dag/${dag.id}/version/${version.id}/node`, {
    nodeCode: 'approval_gate',
    taskId: pausedTask.id,
    name: '待暂停节点',
    meta: JSON.stringify({ e2e: true }),
  })
  await callSchedulingApi(page, 'POST', `/scheduling/dag/${dag.id}/version/${version.id}/node`, {
    nodeCode: 'finalize',
    taskId: finalizeTask.id,
    name: '收尾',
    meta: JSON.stringify({ e2e: true }),
  })
  await callSchedulingApi(page, 'POST', `/scheduling/dag/${dag.id}/version/${version.id}/edge`, {
    fromNodeCode: 'prepare',
    toNodeCode: 'approval_gate',
  })
  await callSchedulingApi(page, 'POST', `/scheduling/dag/${dag.id}/version/${version.id}/edge`, {
    fromNodeCode: 'approval_gate',
    toNodeCode: 'finalize',
  })
  await callSchedulingApi(page, 'PUT', `/scheduling/dag/${dag.id}/version/${version.id}`, {
    versionNo: version.versionNo,
    status: 'ACTIVE',
    definition: JSON.stringify({
      nodes: ['prepare', 'approval_gate', 'finalize'],
      edges: [
        { from: 'prepare', to: 'approval_gate' },
        { from: 'approval_gate', to: 'finalize' },
      ],
    }),
  })

  return { dagId: dag.id, versionId: version.id, dagCode }
}

async function provisionTriggerNodeDag(page: Page): Promise<CreatedDag> {
  const dagCode = buildUniqueCode(triggerNodeDagBaseCode)
  const parallelType = await callSchedulingApi<{ id: number }>(page, 'POST', '/scheduling/task-type', {
    code: buildUniqueCode('e2e-trigger-node-stage'),
    name: 'E2E-可触发节点阶段',
    description: 'real e2e 节点手动触发阶段',
    executor: 'delayTaskExecutor',
    defaultTimeoutSec: 60,
    defaultMaxRetry: 0,
    enabled: true,
  })

  const unstableTask = await callSchedulingApi<{ id: number }>(page, 'POST', '/scheduling/task', {
    typeId: parallelType.id,
    code: buildUniqueCode('e2e-trigger-node-unstable'),
    name: 'E2E-失败后可手动触发节点',
    description: '先失败，再在同一 run 内手动重新触发',
    params: JSON.stringify({ delayMs: 4000, message: 'unstable-branch', fail: true }),
    timeoutSec: 60,
    maxRetry: 0,
    concurrencyPolicy: 'PARALLEL',
    enabled: true,
  })
  const guardTask = await callSchedulingApi<{ id: number }>(page, 'POST', '/scheduling/task', {
    typeId: parallelType.id,
    code: buildUniqueCode('e2e-trigger-node-guard'),
    name: 'E2E-长运行保护分支',
    description: '让 run 在失败节点重触发期间保持 RUNNING',
    params: JSON.stringify({ delayMs: 12000, message: 'guard-branch' }),
    timeoutSec: 60,
    maxRetry: 0,
    concurrencyPolicy: 'PARALLEL',
    enabled: true,
  })

  const dag = await callSchedulingApi<{ id: number }>(page, 'POST', '/scheduling/dag', {
    code: dagCode,
    name: `E2E 节点触发流水线 ${dagCode}`,
    description: '并行分支里失败节点在 RUNNING run 内被重新触发',
    enabled: true,
    cronExpression: '',
    cronEnabled: false,
  })
  const version = await callSchedulingApi<{ id: number; versionNo: number }>(
    page,
    'POST',
    `/scheduling/dag/${dag.id}/version`,
    {
      status: 'DRAFT',
      definition: JSON.stringify({ nodes: [], edges: [] }),
    }
  )

  await callSchedulingApi(page, 'POST', `/scheduling/dag/${dag.id}/version/${version.id}/node`, {
    nodeCode: 'unstable_stage',
    taskId: unstableTask.id,
    name: '失败后可重触发节点',
    parallelGroup: 'trigger-root',
    meta: JSON.stringify({ e2e: true }),
  })
  await callSchedulingApi(page, 'POST', `/scheduling/dag/${dag.id}/version/${version.id}/node`, {
    nodeCode: 'guard_stage',
    taskId: guardTask.id,
    name: '长运行保护分支',
    parallelGroup: 'trigger-root',
    meta: JSON.stringify({ e2e: true }),
  })
  await callSchedulingApi(page, 'PUT', `/scheduling/dag/${dag.id}/version/${version.id}`, {
    versionNo: version.versionNo,
    status: 'ACTIVE',
    definition: JSON.stringify({
      nodes: ['unstable_stage', 'guard_stage'],
      edges: [],
    }),
  })

  return { dagId: dag.id, versionId: version.id, dagCode }
}

async function openDagHistoryFromList(page: Page, dagCode: string) {
  const row = dagTableRow(page, dagCode)
  await row.getByRole('button', { name: '历史' }).click()
  await page.waitForURL(/\/scheduling\/dag\/history\?dagId=\d+/)
  await expect(page.getByText('运行历史列表').first()).toBeVisible()
}

async function triggerDagViaApi(page: Page, dagId: number) {
  await callSchedulingApi(page, 'POST', `/scheduling/dag/${dagId}/trigger`)
}

async function openDagHistory(page: Page, dagId: number) {
  await page.goto(`/scheduling/dag/history?dagId=${dagId}`)
  await expect(page.getByText('运行历史列表').first()).toBeVisible()
}

async function readLatestRunSummary(page: Page, dagId: number) {
  await page.goto(`/scheduling/dag/history?dagId=${dagId}`)
  await expect(page.getByText('运行历史列表').first()).toBeVisible()
  await expect(historyTableRows(page).first()).toBeVisible()
  const row = historyTableRows(page).first()
  return {
    runId: (await row.locator('td').nth(0).innerText()).trim(),
    runNo: (await row.locator('td').nth(1).innerText()).trim(),
    status: (await row.locator('td').nth(3).innerText()).trim(),
    triggerType: (await row.locator('td').nth(5).innerText()).trim(),
  }
}

async function waitForLatestRun(
  page: Page,
  dagId: number,
  predicate: (summary: Awaited<ReturnType<typeof readLatestRunSummary>>) => boolean,
  timeoutMs = 60_000
) {
  const deadline = Date.now() + timeoutMs
  let lastSummary: Awaited<ReturnType<typeof readLatestRunSummary>> | null = null

  while (Date.now() < deadline) {
    lastSummary = await readLatestRunSummary(page, dagId)
    if (predicate(lastSummary)) {
      return lastSummary
    }
    await page.waitForTimeout(1_000)
  }

  throw new Error(`运行摘要在 ${timeoutMs}ms 内未满足预期，最后结果: ${JSON.stringify(lastSummary)}`)
}

async function retryLatestRunFromHistory(page: Page, dagId: number) {
  await page.goto(`/scheduling/dag/history?dagId=${dagId}`)
  await expect(page.getByText('运行历史列表').first()).toBeVisible()
  const row = historyTableRows(page).first()
  const runNo = (await row.locator('td').nth(1).innerText()).trim()
  await row.getByRole('button', { name: '重试本次' }).click()
  await page.getByRole('button', { name: '确认重试' }).click()
  await expect(page.getByText(`已提交运行重试: ${runNo}`)).toBeVisible()
}

async function stopLatestRunFromHistory(page: Page, dagId: number) {
  await page.goto(`/scheduling/dag/history?dagId=${dagId}`)
  await expect(page.getByText('运行历史列表').first()).toBeVisible()
  const row = historyTableRows(page).first()
  const runNo = (await row.locator('td').nth(1).innerText()).trim()
  await row.getByRole('button', { name: '停止本次' }).click()
  await page.getByRole('button', { name: '确认停止' }).click()
  await expect(page.getByText(`已停止运行: ${runNo}`)).toBeVisible()
}

function extractDagIdFromHistoryUrl(page: Page): number {
  const dagId = Number(new URL(page.url()).searchParams.get('dagId'))
  if (!dagId || Number.isNaN(dagId)) {
    throw new Error(`无法从历史页 URL 解析 dagId: ${page.url()}`)
  }
  return dagId
}

async function openLatestRunNodes(page: Page) {
  await expect(historyTableRows(page).first()).toBeVisible()
  await historyTableRows(page).first().getByRole('button', { name: '节点记录' }).click()
  await expect(nodeDialog(page)).toBeVisible()
}

async function readNodeSnapshot(page: Page): Promise<NodeSnapshot> {
  const dialog = nodeDialog(page)
  const rows = dialog.locator('.ant-table-tbody > tr')
  const count = await rows.count()
  const snapshot: NodeSnapshot = {}
  for (let index = 0; index < count; index += 1) {
    const row = rows.nth(index)
    const nodeCode = (await row.locator('td').nth(1).innerText()).trim()
    const status = (await row.locator('td').nth(4).innerText()).trim()
    if (nodeCode) {
      snapshot[nodeCode] = status
    }
  }
  return snapshot
}

async function readNodeRecords(page: Page): Promise<NodeRecord[]> {
  const dialog = nodeDialog(page)
  const rows = dialog.locator('.ant-table-tbody > tr')
  const count = await rows.count()
  const records: NodeRecord[] = []
  for (let index = 0; index < count; index += 1) {
    const row = rows.nth(index)
    const instanceId = (await row.locator('td').nth(0).innerText()).trim()
    const nodeCode = (await row.locator('td').nth(1).innerText()).trim()
    const attemptText = (await row.locator('td').nth(3).innerText()).trim()
    const status = (await row.locator('td').nth(4).innerText()).trim()
    records.push({
      instanceId,
      nodeCode,
      attemptNo: Number(attemptText),
      status,
    })
  }
  return records
}

async function closeNodeDialog(page: Page) {
  const closeButton = nodeDialog(page).locator('.ant-modal-close')
  if (await closeButton.isVisible().catch(() => false)) {
    await closeButton.click()
  }
}

async function reloadHistoryAndOpenNodes(page: Page, dagId: number) {
  await page.goto(`/scheduling/dag/history?dagId=${dagId}`)
  await expect(page.getByText('运行历史列表').first()).toBeVisible()
  await openLatestRunNodes(page)
}

async function waitForNodeSnapshot(
  page: Page,
  dagId: number,
  predicate: (snapshot: NodeSnapshot) => boolean,
  timeoutMs = 60_000
): Promise<NodeSnapshot> {
  const deadline = Date.now() + timeoutMs
  let lastSnapshot: NodeSnapshot = {}

  while (Date.now() < deadline) {
    await reloadHistoryAndOpenNodes(page, dagId)
    lastSnapshot = await readNodeSnapshot(page)
    if (predicate(lastSnapshot)) {
      return lastSnapshot
    }
    await closeNodeDialog(page)
    await page.waitForTimeout(1_000)
  }

  throw new Error(`节点状态在 ${timeoutMs}ms 内未满足预期，最后快照: ${JSON.stringify(lastSnapshot)}`)
}

async function waitForNodeRecords(
  page: Page,
  dagId: number,
  predicate: (records: NodeRecord[]) => boolean,
  timeoutMs = 60_000
): Promise<NodeRecord[]> {
  const deadline = Date.now() + timeoutMs
  let lastRecords: NodeRecord[] = []

  while (Date.now() < deadline) {
    await reloadHistoryAndOpenNodes(page, dagId)
    lastRecords = await readNodeRecords(page)
    if (predicate(lastRecords)) {
      return lastRecords
    }
    await closeNodeDialog(page)
    await page.waitForTimeout(1_000)
  }

  throw new Error(`节点明细在 ${timeoutMs}ms 内未满足预期，最后明细: ${JSON.stringify(lastRecords)}`)
}

async function retryNodeFromLatestRun(page: Page, dagId: number, nodeCode: string) {
  await page.goto(`/scheduling/dag/history?dagId=${dagId}`)
  await expect(page.getByText('运行历史列表').first()).toBeVisible()
  await openLatestRunNodes(page)
  const dialog = nodeDialog(page)
  const failedRow = dialog
    .locator('.ant-table-tbody > tr')
    .filter({ hasText: nodeCode })
    .filter({ hasText: 'FAILED' })
    .first()
  await expect(failedRow).toBeVisible()
  await failedRow.getByRole('button', { name: '重试本节点' }).click()
  await page.getByRole('button', { name: '确认重试' }).click()
  await expect(page.getByText(`已提交节点重试: ${nodeCode}`)).toBeVisible()
}

async function pauseNodeFromLatestRun(page: Page, dagId: number, nodeCode: string) {
  await page.goto(`/scheduling/dag/history?dagId=${dagId}`)
  await expect(page.getByText('运行历史列表').first()).toBeVisible()
  await openLatestRunNodes(page)
  const dialog = nodeDialog(page)
  const row = dialog.locator('.ant-table-tbody > tr').filter({ hasText: nodeCode }).first()
  await expect(row).toBeVisible()
  await row.getByRole('button', { name: '暂停本节点' }).click()
  await page.getByRole('button', { name: '确认暂停' }).click()
  await expect(page.getByText(`已暂停节点: ${nodeCode}`)).toBeVisible()
}

async function resumeNodeFromLatestRun(page: Page, dagId: number, nodeCode: string) {
  await page.goto(`/scheduling/dag/history?dagId=${dagId}`)
  await expect(page.getByText('运行历史列表').first()).toBeVisible()
  await openLatestRunNodes(page)
  const dialog = nodeDialog(page)
  const row = dialog.locator('.ant-table-tbody > tr').filter({ hasText: nodeCode }).first()
  await expect(row).toBeVisible()
  await row.getByRole('button', { name: '恢复本节点' }).click()
  await page.getByRole('button', { name: '确认恢复' }).click()
  await expect(page.getByText(`已恢复节点: ${nodeCode}`)).toBeVisible()
}

async function triggerNodeFromLatestRun(page: Page, dagId: number, nodeCode: string) {
  await page.goto(`/scheduling/dag/history?dagId=${dagId}`)
  await expect(page.getByText('运行历史列表').first()).toBeVisible()
  await openLatestRunNodes(page)
  const dialog = nodeDialog(page)
  const row = dialog.locator('.ant-table-tbody > tr').filter({ hasText: nodeCode }).first()
  await expect(row).toBeVisible()
  await row.getByRole('button', { name: '触发本节点' }).click()
  await page.getByRole('button', { name: '确认触发' }).click()
  await expect(page.getByText(`已触发节点: ${nodeCode}`)).toBeVisible()
}

async function waitForRunStatus(page: Page, dagId: number, expectedStatus: string, timeoutMs = 60_000) {
  const deadline = Date.now() + timeoutMs
  let lastStatus = ''

  while (Date.now() < deadline) {
    await page.goto(`/scheduling/dag/history?dagId=${dagId}`)
    await expect(page.getByText('运行历史列表').first()).toBeVisible()
    await expect(historyTableRows(page).first()).toBeVisible()
    lastStatus = (await historyTableRows(page).first().locator('td').nth(3).innerText()).trim()
    if (lastStatus === expectedStatus) {
      return
    }
    await page.waitForTimeout(1_000)
  }

  throw new Error(`运行状态在 ${timeoutMs}ms 内未收敛到 ${expectedStatus}，最后状态: ${lastStatus}`)
}

test.describe.serial('real scheduling orchestration e2e', () => {
  test('parallel fan-out nodes should merge only after both statistics complete', async ({ page }) => {
    await openAuthenticatedShell(page)
    const { dagId } = await provisionParallelDag(page)
    await triggerDagViaApi(page, dagId)
    await openDagHistory(page, dagId)

    const initialSnapshot = await waitForNodeSnapshot(
      page,
      dagId,
      (snapshot) => ['user_stat', 'order_stat'].every((nodeCode) => nodeCode in snapshot),
      30_000
    )
    expect(initialSnapshot).toMatchObject({
      user_stat: expect.any(String),
      order_stat: expect.any(String),
    })
    expect(initialSnapshot.merge_report).not.toBe('RUNNING')
    expect(initialSnapshot.merge_report).not.toBe('SUCCESS')

    const mergeReleasedSnapshot = await waitForNodeSnapshot(
      page,
      dagId,
      (snapshot) =>
        snapshot.user_stat === 'SUCCESS' &&
        snapshot.order_stat === 'SUCCESS' &&
        (snapshot.merge_report === 'RUNNING' || snapshot.merge_report === 'SUCCESS'),
      60_000
    )
    expect(mergeReleasedSnapshot.user_stat).toBe('SUCCESS')
    expect(mergeReleasedSnapshot.order_stat).toBe('SUCCESS')
    expect(['RUNNING', 'SUCCESS']).toContain(mergeReleasedSnapshot.merge_report)

    const finalSnapshot = await waitForNodeSnapshot(
      page,
      dagId,
      (snapshot) =>
        snapshot.user_stat === 'SUCCESS' &&
        snapshot.order_stat === 'SUCCESS' &&
        snapshot.merge_report === 'SUCCESS',
      60_000
    )
    expect(finalSnapshot).toEqual({
      user_stat: 'SUCCESS',
      order_stat: 'SUCCESS',
      merge_report: 'SUCCESS',
    })

    await waitForRunStatus(page, dagId, 'SUCCESS', 60_000)
    await expect(page.getByText('运行统计')).toBeVisible()
    await expect(page.getByText('总运行次数')).toBeVisible()
    await expect(page.getByText('成功')).toBeVisible()
  })

  test('serial pipeline should release each stage strictly in order', async ({ page }) => {
    await openAuthenticatedShell(page)
    const { dagId } = await provisionSerialDag(page)
    await triggerDagViaApi(page, dagId)
    await openDagHistory(page, dagId)

    const firstStageSnapshot = await waitForNodeSnapshot(
      page,
      dagId,
      (snapshot) => 'extract' in snapshot,
      30_000
    )
    expect(firstStageSnapshot.extract).toBeTruthy()
    expect(firstStageSnapshot.normalize).not.toBe('RUNNING')
    expect(firstStageSnapshot.normalize).not.toBe('SUCCESS')

    const secondStageSnapshot = await waitForNodeSnapshot(
      page,
      dagId,
      (snapshot) =>
        snapshot.extract === 'SUCCESS' &&
        (snapshot.normalize === 'RUNNING' || snapshot.normalize === 'SUCCESS'),
      60_000
    )
    expect(secondStageSnapshot.aggregate).not.toBe('RUNNING')
    expect(secondStageSnapshot.aggregate).not.toBe('SUCCESS')

    const thirdStageSnapshot = await waitForNodeSnapshot(
      page,
      dagId,
      (snapshot) =>
        snapshot.normalize === 'SUCCESS' &&
        (snapshot.aggregate === 'RUNNING' || snapshot.aggregate === 'SUCCESS'),
      60_000
    )
    expect(thirdStageSnapshot.finalize).not.toBe('RUNNING')
    expect(thirdStageSnapshot.finalize).not.toBe('SUCCESS')

    const finalSnapshot = await waitForNodeSnapshot(
      page,
      dagId,
      (snapshot) =>
        snapshot.extract === 'SUCCESS' &&
        snapshot.normalize === 'SUCCESS' &&
        snapshot.aggregate === 'SUCCESS' &&
        snapshot.finalize === 'SUCCESS',
      60_000
    )
    expect(finalSnapshot).toEqual({
      extract: 'SUCCESS',
      normalize: 'SUCCESS',
      aggregate: 'SUCCESS',
      finalize: 'SUCCESS',
    })

    await waitForRunStatus(page, dagId, 'SUCCESS', 60_000)
  })

  test('failed run should be retryable from history after fixing task config', async ({ page }) => {
    await openAuthenticatedShell(page)
    const failureDag = await provisionRetryableFailureDag(page)
    await triggerDagViaApi(page, failureDag.dagId)
    await openDagHistory(page, failureDag.dagId)

    const failedRunSummary = await waitForLatestRun(
      page,
      failureDag.dagId,
      (summary) => ['FAILED', 'PARTIAL_FAILED'].includes(summary.status),
      60_000
    )
    expect(['FAILED', 'PARTIAL_FAILED']).toContain(failedRunSummary.status)
    expect(failedRunSummary.triggerType).toBe('MANUAL')

    const failedSnapshot = await waitForNodeSnapshot(
      page,
      failureDag.dagId,
      (snapshot) =>
        snapshot.prepare === 'SUCCESS' &&
        snapshot.unstable_stage === 'FAILED' &&
        snapshot.finalize !== 'RUNNING' &&
        snapshot.finalize !== 'SUCCESS',
      60_000
    )
    expect(failedSnapshot.prepare).toBe('SUCCESS')
    expect(failedSnapshot.unstable_stage).toBe('FAILED')
    expect(failedSnapshot.finalize).not.toBe('RUNNING')
    expect(failedSnapshot.finalize).not.toBe('SUCCESS')

    await callSchedulingApi(
      page,
      'PUT',
      `/scheduling/task/${failureDag.unstableTaskId}`,
      failureDag.unstableTaskUpdate
    )

    await retryLatestRunFromHistory(page, failureDag.dagId)

    const retryRunSummary = await waitForLatestRun(
      page,
      failureDag.dagId,
      (summary) => summary.triggerType === 'RETRY' && summary.status === 'SUCCESS',
      90_000
    )
    expect(retryRunSummary.triggerType).toBe('RETRY')
    expect(retryRunSummary.status).toBe('SUCCESS')

    const recoveredSnapshot = await waitForNodeSnapshot(
      page,
      failureDag.dagId,
      (snapshot) =>
        snapshot.prepare === 'SUCCESS' &&
        snapshot.unstable_stage === 'SUCCESS' &&
        snapshot.finalize === 'SUCCESS',
      90_000
    )
    expect(recoveredSnapshot).toEqual({
      prepare: 'SUCCESS',
      unstable_stage: 'SUCCESS',
      finalize: 'SUCCESS',
    })
  })

  test('failed node retry should stay within the same run and keep frozen snapshot semantics', async ({ page }) => {
    await openAuthenticatedShell(page)
    const failureDag = await provisionRetryableFailureDag(page)
    await triggerDagViaApi(page, failureDag.dagId)
    await openDagHistory(page, failureDag.dagId)

    const failedRunSummary = await waitForLatestRun(
      page,
      failureDag.dagId,
      (summary) => ['FAILED', 'PARTIAL_FAILED'].includes(summary.status),
      60_000
    )
    const failedRunId = failedRunSummary.runId

    const failedRecords = await waitForNodeRecords(
      page,
      failureDag.dagId,
      (records) =>
        records.some(
          (record) =>
            record.nodeCode === 'unstable_stage' &&
            record.status === 'FAILED' &&
            record.attemptNo === 1
        ),
      60_000
    )
    expect(
      failedRecords.some(
        (record) =>
          record.nodeCode === 'unstable_stage' &&
          record.status === 'FAILED' &&
          record.attemptNo === 1
      )
    ).toBeTruthy()

    await callSchedulingApi(
      page,
      'PUT',
      `/scheduling/task/${failureDag.unstableTaskId}`,
      failureDag.unstableTaskUpdate
    )

    await retryNodeFromLatestRun(page, failureDag.dagId, 'unstable_stage')

    const reopenedSummary = await waitForLatestRun(
      page,
      failureDag.dagId,
      (summary) => summary.runId === failedRunId && summary.status === 'RUNNING',
      30_000
    )
    expect(reopenedSummary.runId).toBe(failedRunId)
    expect(reopenedSummary.status).toBe('RUNNING')

    const retriedRecords = await waitForNodeRecords(
      page,
      failureDag.dagId,
      (records) => {
        const unstableAttempts = records.filter((record) => record.nodeCode === 'unstable_stage')
        return (
          unstableAttempts.some((record) => record.status === 'FAILED' && record.attemptNo === 1) &&
          unstableAttempts.some((record) => record.status === 'FAILED' && record.attemptNo === 2) &&
          records.some((record) => record.nodeCode === 'finalize' && record.status === 'SKIPPED')
        )
      },
      90_000
    )
    const unstableAttempts = retriedRecords.filter((record) => record.nodeCode === 'unstable_stage')
    expect(unstableAttempts).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ status: 'FAILED', attemptNo: 1 }),
        expect.objectContaining({ status: 'FAILED', attemptNo: 2 }),
      ])
    )
    expect(
      retriedRecords.some((record) => record.nodeCode === 'finalize' && record.status === 'SKIPPED')
    ).toBeTruthy()

    const failedAgainSummary = await waitForLatestRun(
      page,
      failureDag.dagId,
      (summary) =>
        summary.runId === failedRunId &&
        ['FAILED', 'PARTIAL_FAILED'].includes(summary.status),
      90_000
    )
    expect(failedAgainSummary.runId).toBe(failedRunId)
    expect(['FAILED', 'PARTIAL_FAILED']).toContain(failedAgainSummary.status)
  })

  test('failed node should be triggerable within a running run and rerun the same instance', async ({ page }) => {
    await openAuthenticatedShell(page)
    const dag = await provisionTriggerNodeDag(page)
    await triggerDagViaApi(page, dag.dagId)
    await openDagHistory(page, dag.dagId)

    const runningSummary = await waitForLatestRun(
      page,
      dag.dagId,
      (summary) => summary.status === 'RUNNING',
      30_000
    )
    const runId = runningSummary.runId

    const failedWhileRunningRecords = await waitForNodeRecords(
      page,
      dag.dagId,
      (records) =>
        records.some((record) => record.nodeCode === 'unstable_stage' && record.status === 'FAILED')
        && records.some((record) => record.nodeCode === 'guard_stage' && record.status === 'RUNNING'),
      60_000
    )
    const unstableBeforeTrigger = failedWhileRunningRecords.find((record) => record.nodeCode === 'unstable_stage')
    expect(unstableBeforeTrigger).toBeDefined()
    expect(unstableBeforeTrigger?.status).toBe('FAILED')
    expect(unstableBeforeTrigger?.attemptNo).toBe(1)

    await triggerNodeFromLatestRun(page, dag.dagId, 'unstable_stage')

    const rerunningRecords = await waitForNodeRecords(
      page,
      dag.dagId,
      (records) =>
        records.some(
          (record) =>
            record.nodeCode === 'unstable_stage'
            && record.instanceId === unstableBeforeTrigger?.instanceId
            && ['PENDING', 'RUNNING'].includes(record.status),
        ),
      30_000
    )
    const unstableDuringTrigger = rerunningRecords.find((record) => record.nodeCode === 'unstable_stage')
    expect(unstableDuringTrigger?.instanceId).toBe(unstableBeforeTrigger?.instanceId)
    expect(unstableDuringTrigger?.attemptNo).toBe(1)
    expect(['PENDING', 'RUNNING']).toContain(unstableDuringTrigger?.status ?? '')

    const failedAgainRecords = await waitForNodeRecords(
      page,
      dag.dagId,
      (records) =>
        records.some(
          (record) =>
            record.nodeCode === 'unstable_stage'
            && record.instanceId === unstableBeforeTrigger?.instanceId
            && record.status === 'FAILED',
        ),
      60_000
    )
    const unstableAfterTrigger = failedAgainRecords.find((record) => record.nodeCode === 'unstable_stage')
    expect(unstableAfterTrigger?.instanceId).toBe(unstableBeforeTrigger?.instanceId)
    expect(unstableAfterTrigger?.attemptNo).toBe(1)
    expect(unstableAfterTrigger?.status).toBe('FAILED')

    const finalSummary = await waitForLatestRun(
      page,
      dag.dagId,
      (summary) => summary.runId === runId && summary.status === 'PARTIAL_FAILED',
      90_000
    )
    expect(finalSummary.runId).toBe(runId)
    expect(finalSummary.status).toBe('PARTIAL_FAILED')
  })

  test('running run should be stoppable from history and converge to cancelled', async ({ page }) => {
    await openAuthenticatedShell(page)
    const dag = await provisionCancellableDag(page)
    await triggerDagViaApi(page, dag.dagId)
    await openDagHistory(page, dag.dagId)

    const runningSummary = await waitForLatestRun(
      page,
      dag.dagId,
      (summary) => summary.status === 'RUNNING',
      30_000
    )
    expect(runningSummary.status).toBe('RUNNING')
    expect(runningSummary.triggerType).toBe('MANUAL')

    await stopLatestRunFromHistory(page, dag.dagId)

    const cancelledSummary = await waitForLatestRun(
      page,
      dag.dagId,
      (summary) => summary.status === 'CANCELLED',
      60_000
    )
    expect(cancelledSummary.status).toBe('CANCELLED')

    const cancelledSnapshot = await waitForNodeSnapshot(
      page,
      dag.dagId,
      (snapshot) => snapshot.long_stage === 'CANCELLED' && snapshot.downstream_stage === 'CANCELLED',
      60_000
    )
    expect(cancelledSnapshot).toEqual({
      long_stage: 'CANCELLED',
      downstream_stage: 'CANCELLED',
    })
  })

  test('pending node should be pausable from node history and resume within the same run', async ({ page }) => {
    await openAuthenticatedShell(page)
    const dag = await provisionPauseResumeDag(page)
    await triggerDagViaApi(page, dag.dagId)
    await openDagHistory(page, dag.dagId)

    const runningSummary = await waitForLatestRun(
      page,
      dag.dagId,
      (summary) => summary.status === 'RUNNING',
      30_000
    )
    const runId = runningSummary.runId

    const initialSnapshot = await waitForNodeSnapshot(
      page,
      dag.dagId,
      (snapshot) => snapshot.prepare != null && snapshot.approval_gate === 'PENDING',
      30_000
    )
    expect(initialSnapshot.approval_gate).toBe('PENDING')
    expect(initialSnapshot.finalize).toBe('PENDING')

    await pauseNodeFromLatestRun(page, dag.dagId, 'approval_gate')

    const pausedSnapshot = await waitForNodeSnapshot(
      page,
      dag.dagId,
      (snapshot) => snapshot.approval_gate === 'PAUSED',
      30_000
    )
    expect(pausedSnapshot.approval_gate).toBe('PAUSED')

    const blockedSnapshot = await waitForNodeSnapshot(
      page,
      dag.dagId,
      (snapshot) =>
        snapshot.prepare === 'SUCCESS' &&
        snapshot.approval_gate === 'PAUSED' &&
        snapshot.finalize === 'PENDING',
      60_000
    )
    expect(blockedSnapshot.prepare).toBe('SUCCESS')
    expect(blockedSnapshot.approval_gate).toBe('PAUSED')
    expect(blockedSnapshot.finalize).toBe('PENDING')

    await resumeNodeFromLatestRun(page, dag.dagId, 'approval_gate')

    const resumedSnapshot = await waitForNodeSnapshot(
      page,
      dag.dagId,
      (snapshot) =>
        snapshot.prepare === 'SUCCESS' &&
        ['RUNNING', 'SUCCESS'].includes(snapshot.approval_gate ?? ''),
      60_000
    )
    expect(['RUNNING', 'SUCCESS']).toContain(resumedSnapshot.approval_gate)

    const finalSnapshot = await waitForNodeSnapshot(
      page,
      dag.dagId,
      (snapshot) =>
        snapshot.prepare === 'SUCCESS' &&
        snapshot.approval_gate === 'SUCCESS' &&
        snapshot.finalize === 'SUCCESS',
      90_000
    )
    expect(finalSnapshot).toEqual({
      prepare: 'SUCCESS',
      approval_gate: 'SUCCESS',
      finalize: 'SUCCESS',
    })

    const successSummary = await waitForLatestRun(
      page,
      dag.dagId,
      (summary) => summary.runId === runId && summary.status === 'SUCCESS',
      90_000
    )
    expect(successSummary.runId).toBe(runId)
    expect(successSummary.status).toBe('SUCCESS')
  })
})
