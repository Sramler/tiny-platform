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

async function getTenantIdByCode(accessToken: string, tenantCode: string): Promise<number | null> {
  const url = `${backendBaseUrl}/sys/tenants?code=${encodeURIComponent(tenantCode)}&page=0&size=1`
  const res = await fetch(url, {
    headers: { Authorization: `Bearer ${accessToken}` },
  })
  if (!res.ok) return null
  const data = (await res.json()) as { content?: Array<{ id?: number }> }
  const first = data.content?.[0]
  return first?.id ?? null
}

async function getTenant(accessToken: string, tenantId: number): Promise<Record<string, unknown> | null> {
  const res = await fetch(`${backendBaseUrl}/sys/tenants/${tenantId}`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  })
  if (!res.ok) return null
  return (await res.json()) as Record<string, unknown>
}

async function setTenantLifecycleStatus(
  accessToken: string,
  tenantId: number,
  tenantPayload: Record<string, unknown>,
  lifecycleStatus: string,
): Promise<boolean> {
  const body = { ...tenantPayload, lifecycleStatus }
  const res = await fetch(`${backendBaseUrl}/sys/tenants/${tenantId}`, {
    method: 'PUT',
    headers: {
      Authorization: `Bearer ${accessToken}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(body),
  })
  return res.ok
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
    const tenantId = await getTenantIdByCode(adminToken!, tenantCode)
    test.skip(tenantId === null, `未找到租户: ${tenantCode}`)

    const tenantPayload = await getTenant(adminToken!, tenantId!)
    test.skip(tenantPayload === null, `无法读取租户 ${tenantId} 详情`)

    // 1) 确保 ACTIVE
    await setTenantLifecycleStatus(adminToken!, tenantId!, tenantPayload!, 'ACTIVE')

    // 2) 租户用户写操作成功
    await openOidcDebug(page)
    const created = await createTaskType(page, 'freeze-smoke')
    expect(created.id).toBeTruthy()

    // 3) 冻结租户
    const frozenOk = await setTenantLifecycleStatus(adminToken!, tenantId!, tenantPayload!, 'FROZEN')
    expect(frozenOk).toBe(true)

    try {
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
      await setTenantLifecycleStatus(adminToken!, tenantId!, tenantPayload!, 'ACTIVE')
    }
  })
})
