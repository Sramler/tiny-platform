import fs from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { expect, test } from '@playwright/test'
import {
  createTaskType,
  fetchSchedulingApi,
  openOidcDebug,
} from './cross-tenant.helpers'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

const backendBaseUrl = process.env.E2E_BACKEND_BASE_URL ?? 'http://localhost:9000'
const frontendBaseUrl = process.env.E2E_FRONTEND_BASE_URL ?? 'http://localhost:5173'
const platformAuthPath = path.resolve(__dirname, '..', '.auth', 'platform-admin-user.json')
const primaryAuthPath = path.resolve(__dirname, '..', '.auth', 'scheduling-user.json')

type StorageState = {
  origins?: Array<{
    localStorage?: Array<{ name: string; value: string }>
  }>
}

async function getAccessTokenFromStatePath(statePath: string): Promise<string | null> {
  try {
    const raw = await fs.readFile(statePath, 'utf8')
    const state = JSON.parse(raw) as StorageState
    for (const origin of state.origins ?? []) {
      for (const entry of origin.localStorage ?? []) {
        if (!entry.name.startsWith('oidc.user:')) continue
        try {
          const parsed = JSON.parse(entry.value) as { access_token?: string }
          if (parsed?.access_token) return parsed.access_token
        } catch {
          // ignore
        }
      }
    }
  } catch {
    // file missing or unreadable
  }
  return null
}

async function getPlatformAccessToken(): Promise<string | null> {
  return getAccessTokenFromStatePath(platformAuthPath)
}

/** 主身份登录态（default 租户管理员通常具备 /sys/tenants 权限，可作 fallback） */
async function getPrimaryAccessToken(): Promise<string | null> {
  return getAccessTokenFromStatePath(primaryAuthPath)
}

type TenantLookupResult = {
  status: number
  tenantId: number | null
  body: string
}

async function getTenantIdByCode(accessToken: string, tenantCode: string): Promise<TenantLookupResult> {
  const url = `${backendBaseUrl}/sys/tenants?code=${encodeURIComponent(tenantCode)}&page=0&size=1`
  const res = await fetch(url, {
    headers: { Authorization: `Bearer ${accessToken}` },
  })
  const body = await res.text()
  if (!res.ok) {
    return {
      status: res.status,
      tenantId: null,
      body,
    }
  }
  const data = JSON.parse(body) as { content?: Array<{ id?: number }> }
  const first = data.content?.[0]
  return {
    status: res.status,
    tenantId: first?.id ?? null,
    body,
  }
}

async function getTenant(accessToken: string, tenantId: number): Promise<Record<string, unknown> | null> {
  const res = await fetch(`${backendBaseUrl}/sys/tenants/${tenantId}`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  })
  if (!res.ok) return null
  return (await res.json()) as Record<string, unknown>
}

async function transitionTenantLifecycle(
  accessToken: string,
  tenantId: number,
  action: 'freeze' | 'unfreeze',
): Promise<{ ok: boolean; status: number; body: string }> {
  const requestId = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
  const res = await fetch(`${backendBaseUrl}/sys/tenants/${tenantId}/${action}`, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      Authorization: `Bearer ${accessToken}`,
      'X-Idempotency-Key': `e2e-tenant-lifecycle:${tenantId}:${action}:${requestId}`,
    },
  })
  const body = await res.text()
  return {
    ok: res.ok,
    status: res.status,
    body,
  }
}

async function fetchAuditSummary(accessToken: string, tenantId: number) {
  return fetch(`${backendBaseUrl}/sys/audit/authentication/summary?tenantId=${tenantId}`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  })
}

async function exportAuthenticationAudit(accessToken: string, tenantId: number) {
  return fetch(
    `${backendBaseUrl}/sys/audit/authentication/export?tenantId=${tenantId}&reason=${encodeURIComponent('freeze-e2e-check')}&ticketId=${encodeURIComponent('E2E-FREEZE-1')}`,
    {
      headers: { Authorization: `Bearer ${accessToken}` },
    },
  )
}

function isConfigured(value: string | undefined): boolean {
  if (!value?.trim()) return false
  const t = value.trim()
  return !(t.startsWith('<') && t.endsWith('>'))
}

function isFreezeIdentityConfigured(): boolean {
  return (
    isConfigured(process.env.E2E_TENANT_CODE) &&
    isConfigured(process.env.E2E_USERNAME) &&
    isConfigured(process.env.E2E_PASSWORD) &&
    isConfigured(process.env.E2E_TOTP_SECRET)
  )
}

/**
 * real-link：租户生命周期冻结行为
 *
 * 1. 平台管理员将测试租户设为 ACTIVE（若需）
 * 2. 租户用户登录态下执行写操作（创建任务类型）→ 成功
 * 3. 平台管理员将测试租户设为 FROZEN
 * 4. 该租户用户再次登录 → 被拒绝（租户已冻结）
 * 5. 恢复租户为 ACTIVE，避免影响后续用例
 *
 * 依赖：E2E_PLATFORM_* 用于调用 /sys/tenants 更新状态；主身份 E2E_TENANT_CODE/USERNAME/PASSWORD 为被冻结租户。
 */
test.describe('real-link: tenant lifecycle freeze', () => {
  test('frozen tenant rejects login and write is denied or guarded', async ({
    page,
    browser,
  }) => {
    test.skip(
      !isFreezeIdentityConfigured(),
      '缺少 E2E_TENANT_CODE / E2E_USERNAME / E2E_PASSWORD / E2E_TOTP_SECRET，跳过租户冻结回归',
    )

    const adminToken = (await getPlatformAccessToken()) ?? (await getPrimaryAccessToken())
    test.skip(
      adminToken === null,
      '未找到可调用 /sys/tenants 的登录态（platform-admin-user.json 或 scheduling-user.json），跳过租户冻结回归',
    )

    const tenantCode = process.env.E2E_TENANT_CODE!.trim()
    const tenantLookup = await getTenantIdByCode(adminToken!, tenantCode)
    test.skip(
      tenantLookup.status === 403,
      '平台自动化身份缺少 /sys/tenants 访问能力，当前拿到的不是 PLATFORM scope 或缺少 system:tenant:view'
    )
    test.skip(
      tenantLookup.status >= 400,
      `查询租户失败: HTTP ${tenantLookup.status} ${tenantLookup.body.slice(0, 120)}`
    )
    const tenantId = tenantLookup.tenantId
    test.skip(tenantId === null, `未找到租户: ${tenantCode}`)

    const tenantPayload = await getTenant(adminToken!, tenantId!)
    test.skip(tenantPayload === null, `无法读取租户 ${tenantId} 详情`)
    test.skip(
      String(tenantPayload!.lifecycleStatus ?? 'ACTIVE').toUpperCase() === 'DECOMMISSIONED',
      '测试租户已下线，无法在 real-link 中恢复为 ACTIVE',
    )

    // 1) 确保 ACTIVE
    if (String(tenantPayload!.lifecycleStatus ?? 'ACTIVE').toUpperCase() === 'FROZEN') {
      const unfreezeResult = await transitionTenantLifecycle(adminToken!, tenantId!, 'unfreeze')
      expect(unfreezeResult.ok, `unfreeze failed: HTTP ${unfreezeResult.status} ${unfreezeResult.body}`).toBe(true)
    }

    // 2) 租户用户写操作成功
    await openOidcDebug(page, 'primary')
    const created = await createTaskType(page, 'freeze-smoke')
    expect(created.id).toBeTruthy()

    // 3) 冻结租户
    const freezeResult = await transitionTenantLifecycle(adminToken!, tenantId!, 'freeze')
    expect(freezeResult.ok, `freeze failed: HTTP ${freezeResult.status} ${freezeResult.body}`).toBe(true)

    try {
      // 3a) 平台治理只读白名单仍可用
      const tenantAfterFreeze = await getTenant(adminToken!, tenantId!)
      expect(tenantAfterFreeze?.id).toBe(tenantId)
      expect(String(tenantAfterFreeze?.lifecycleStatus ?? '')).toBe('FROZEN')

      const summaryAfterFreeze = await fetchAuditSummary(adminToken!, tenantId!)
      expect(summaryAfterFreeze.status).toBe(200)

      const exportAfterFreeze = await exportAuthenticationAudit(adminToken!, tenantId!)
      expect(exportAfterFreeze.status).toBe(200)

      // 4a) 调度写操作在 FROZEN 下必须被拒绝（403）
      const writeAfterFreeze = await fetchSchedulingApi<Record<string, unknown>>(page, '/scheduling/task-type', {
        method: 'POST',
        body: {
          code: `freeze-after-${Date.now()}`,
          name: 'after freeze',
          description: 'should be denied when tenant is frozen',
          executor: 'loggingTaskExecutor',
          enabled: true,
          defaultTimeoutSec: 0,
          defaultMaxRetry: 0,
        },
        idempotencyKey: `freeze-write-${Date.now()}`,
      })
      // FROZEN 租户写保护：通常由 TenantLifecycleGuard 抛 RESOURCE_STATE_INVALID(409)；
      // 若未来收口为权限拒绝，也允许 403。
      expect([403, 409]).toContain(writeAfterFreeze.status)

      // 4a-2) 用户管理写操作在 FROZEN 下也必须被拒绝（403）
      const freezeUserPassword = 'FreezeUser#2026'
      const freezeUserSuffix = String(Date.now() % 1_000_000_00)
      const createUserAfterFreeze = await fetchSchedulingApi<Record<string, unknown>>(page, '/sys/users', {
        method: 'POST',
        body: {
          // username: 3-20 chars, [a-zA-Z0-9_]+
          username: `fzu_${freezeUserSuffix}`,
          nickname: 'freeze user after tenant frozen',
          password: freezeUserPassword,
          confirmPassword: freezeUserPassword,
          enabled: true,
          accountNonExpired: true,
          accountNonLocked: true,
          credentialsNonExpired: true,
          roleIds: [],
        },
        idempotencyKey: `freeze-user-create-${Date.now()}`,
      })
      expect([403, 409]).toContain(createUserAfterFreeze.status)

      // 4b) 冻结后登录应被拒绝：无态访问登录页，提交该租户账号，应停留在登录或出现错误
      const loginContext = await browser.newContext({
        baseURL: frontendBaseUrl,
        storageState: undefined,
      })
      const loginPage = await loginContext.newPage()
      try {
        await loginPage.goto('/login')
        await loginPage.getByLabel(/租户编码/i).fill(tenantCode)
        await loginPage.getByLabel(/用户名/i).fill(process.env.E2E_USERNAME!.trim())
        await loginPage.getByLabel(/密码/i).fill(process.env.E2E_PASSWORD!.trim())
        await loginPage.getByRole('button', { name: /登录/ }).click()
        await loginPage.waitForURL(/\/(login|callback|error)/, { waitUntil: 'networkidle', timeout: 15_000 }).catch(() => {})
        const url = loginPage.url()
        const stillOnLogin = url.includes('/login')
        const hasError = await loginPage.locator('.error-message').isVisible().catch(() => false)
        expect(
          stillOnLogin || hasError || url.includes('error'),
          '冻结租户用户登录应被拒绝（停留在登录页或显示错误）',
        ).toBe(true)
      } finally {
        await loginContext.close()
      }
    } finally {
      // 5) 恢复 ACTIVE
      await transitionTenantLifecycle(adminToken!, tenantId!, 'unfreeze')
    }
  })
})
