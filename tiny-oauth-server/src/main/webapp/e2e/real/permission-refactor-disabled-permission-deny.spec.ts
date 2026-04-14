import { execFileSync } from 'node:child_process'
import { Buffer } from 'node:buffer'
import { expect, test } from '@playwright/test'
import { createHmac } from 'node:crypto'

import { requireRealLinkPlatformTenantCode } from '../setup/real.global.setup'

/**
 * Real-link end-to-end proof for permission.enabled fail-closed.
 *
 * Proves two properties together:
 * 1) disabled permissions are NOT included in JWT claims (authorities/permissions)
 * 2) protected interfaces do not allow disabled permissions (pre-authorization still denies)
 *
 * Guard used:
 * - OrganizationAccessGuard.canRead(authentication): system:org:list / system:org:view
 * - This proof uses the guaranteed tenant-admin permission `system:org:list` to avoid coupling the
 *   regression to optional `system:org:view` bootstrap shape.
 * Endpoint:
 * - GET /sys/org/tree
 */

function isPlaceholderValue(value: string): boolean {
  const normalized = value.trim()
  return normalized.startsWith('<') && normalized.endsWith('>')
}

function readEnv(name: string): string | undefined {
  const value = process.env[name]
  if (!value || !value.trim() || isPlaceholderValue(value)) return undefined
  return value.trim()
}

function requireEnv(name: string): string {
  const v = readEnv(name)
  if (!v) throw new Error(`Missing required env: ${name}`)
  return v
}

function decodeBase32(secret: string): Buffer {
  const normalized = secret.replace(/=+$/g, '').replace(/\s+/g, '').toUpperCase()
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'
  let bits = ''

  for (const character of normalized) {
    const index = alphabet.indexOf(character)
    if (index < 0) throw new Error(`Invalid TOTP secret: ${secret}`)
    bits += index.toString(2).padStart(5, '0')
  }

  const bytes: number[] = []
  for (let offset = 0; offset + 8 <= bits.length; offset += 8) {
    bytes.push(Number.parseInt(bits.slice(offset, offset + 8), 2))
  }
  return Buffer.from(bytes)
}

function generateTotpCode(secret: string, timestampMs = Date.now()): string {
  const counter = Math.floor(timestampMs / 30_000)
  const counterBuffer = Buffer.alloc(8)
  counterBuffer.writeBigUInt64BE(BigInt(counter))

  const hmac = createHmac('sha1', decodeBase32(secret)).update(counterBuffer).digest()
  const offset = hmac[hmac.length - 1] & 0x0f
  const binaryCode =
    ((hmac[offset] & 0x7f) << 24) |
    ((hmac[offset + 1] & 0xff) << 16) |
    ((hmac[offset + 2] & 0xff) << 8) |
    (hmac[offset + 3] & 0xff)
  return String(binaryCode % 1_000_000).padStart(6, '0')
}

type StorageStateOrigin = {
  localStorage?: Array<{ name: string; value: string }>
}

type StorageState = {
  origins?: StorageStateOrigin[]
}

function extractAccessTokenFromStorageState(storageState: StorageState): string {
  for (const origin of storageState.origins ?? []) {
    for (const entry of origin.localStorage ?? []) {
      if (!entry.name.startsWith('oidc.user:')) continue
      try {
        const parsed = JSON.parse(entry.value)
        if (parsed?.access_token) return parsed.access_token as string
      } catch {
        // ignore malformed entries
      }
    }
  }
  throw new Error('access_token not found in storageState localStorage (oidc.user:*)')
}

function decodeJwtPayload(token: string): any {
  const parts = token.split('.')
  if (parts.length < 2) throw new Error('Invalid JWT token format')
  const payload = parts[1]
  const base64 = payload.replace(/-/g, '+').replace(/_/g, '/')
  const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4)
  const json = Buffer.from(padded, 'base64').toString('utf8')
  return JSON.parse(json)
}

function readBackendBaseUrl(): string {
  return (
    process.env.E2E_BACKEND_BASE_URL ?? process.env.VITE_API_BASE_URL ?? 'http://localhost:9000'
  )
}

function deriveTenantCodeForTenantScope(
  primaryTenantCode: string,
  platformTenantCode: string,
): string {
  if (primaryTenantCode.trim().toLowerCase() !== platformTenantCode.trim().toLowerCase()) {
    return primaryTenantCode.trim()
  }
  const base = primaryTenantCode.trim().toLowerCase()
  const candidate = `${base}-t`
  return candidate.length <= 32 ? candidate : candidate.slice(0, 32)
}

function resolveEffectiveTenantCode(): string {
  const primaryTenantCode = requireEnv('E2E_TENANT_CODE')
  const platformTenantCode = requireRealLinkPlatformTenantCode(process.env)
  return deriveTenantCodeForTenantScope(primaryTenantCode, platformTenantCode)
}

function sanitizeSqlLiteral(raw: string): string {
  // E2E-only input, keep simple escaping for safe literal embedding
  return raw.replace(/'/g, "''")
}

function mysqlExec(sql: string): string {
  const mysqlBin = readEnv('E2E_MYSQL_BIN') ?? 'mysql'
  const host = readEnv('E2E_DB_HOST') ?? readEnv('E2E_MYSQL_HOST') ?? '127.0.0.1'
  const port = readEnv('E2E_DB_PORT') ?? readEnv('E2E_MYSQL_PORT') ?? '3306'
  const user = readEnv('E2E_DB_USER') ?? readEnv('E2E_MYSQL_USER') ?? 'root'
  const dbName = readEnv('E2E_DB_NAME') ?? readEnv('E2E_MYSQL_DATABASE') ?? 'tiny_web'
  const password =
    readEnv('E2E_DB_PASSWORD') ??
    readEnv('E2E_MYSQL_PASSWORD') ??
    readEnv('MYSQL_ROOT_PASSWORD') ??
    ''

  // Use MYSQL_PWD env to avoid leaking password in argv/logs.
  const env = { ...process.env, MYSQL_PWD: password }

  // -N: skip column names, -s: silent, -e: execute query
  const args = ['-h', host, '-P', port, '-u', user, '-D', dbName, '-Nse', sql]
  return execFileSync(mysqlBin, args, { env, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] })
}

function mysqlCheckEnabled(permissionCode: string, tenantCode: string): boolean {
  const sql = `
    SELECT enabled
    FROM permission
    WHERE permission_code = '${sanitizeSqlLiteral(permissionCode)}'
      AND tenant_id = (SELECT id FROM tenant WHERE code = '${sanitizeSqlLiteral(tenantCode)}' LIMIT 1)
    LIMIT 1;
  `
  const out = mysqlExec(sql).trim()
  if (!out)
    throw new Error(`permission row not found: code=${permissionCode}, tenant=${tenantCode}`)
  // mysql returns 0/1 or true/false depending on column type; handle both.
  return out === '1' || out.toLowerCase() === 'true'
}

function mysqlSetEnabled(permissionCodes: string[], tenantCode: string, enabled: boolean): void {
  const codes = permissionCodes.map((c) => `'${sanitizeSqlLiteral(c)}'`).join(',')
  const enabledInt = enabled ? 1 : 0
  const sql = `
    UPDATE permission
    SET enabled = ${enabledInt},
        updated_by = -999001,
        updated_at = NOW()
    WHERE permission_code IN (${codes})
      AND tenant_id = (SELECT id FROM tenant WHERE code = '${sanitizeSqlLiteral(tenantCode)}' LIMIT 1);
  `
  mysqlExec(sql)
}

function resolveLoginConfig() {
  const tenantCode = resolveEffectiveTenantCode()
  const username = requireEnv('E2E_USERNAME')
  const password = requireEnv('E2E_PASSWORD')
  const totpSecret = readEnv('E2E_TOTP_SECRET')
  const totpCode = readEnv('E2E_TOTP_CODE')
  if (!totpSecret && !totpCode) {
    throw new Error('MFA real-link needs E2E_TOTP_SECRET or E2E_TOTP_CODE for this test')
  }

  return {
    tenantCode,
    username,
    password,
    totpCode: totpCode ?? generateTotpCode(totpSecret!),
  }
}

async function loginWithMfa(page: import('@playwright/test').Page) {
  const { tenantCode, username, password, totpCode } = resolveLoginConfig()

  await page.goto('/login')
  await page.getByLabel('租户编码').fill(tenantCode)
  await page.getByLabel('用户名').fill(username)
  await page.getByLabel('密码').fill(password)
  // Avoid strict-mode ambiguity: the login page has both scope tabs ("租户登录"/"平台登录")
  // and the real submit button ("登录租户").
  await page
    .getByRole('button', { name: /登录租户/ })
    .first()
    .click()

  await page.waitForURL('**/self/security/totp-verify**', { timeout: 60_000 })
  await expect(page.getByRole('heading', { name: /两步验证/ })).toBeVisible({ timeout: 30_000 })

  await page.getByLabel('动态验证码').fill(totpCode)
  await page.getByRole('button', { name: '确认' }).click()

  await page.waitForURL(
    (url) =>
      !url.pathname.includes('/self/security/totp-verify') &&
      !url.pathname.includes('/callback') &&
      !url.pathname.includes('/login'),
    { timeout: 60_000 },
  )
  await page.waitForLoadState('networkidle').catch(() => {})
}

test.describe('real-link: disabled permission deny (JWT + interface) ', () => {
  test('disabled permissions are removed from JWT and cannot be used to access /sys/org/tree', async ({
    browser,
  }) => {
    const backendBaseUrl = readBackendBaseUrl()

    // Confirm mysql client exists early; otherwise skip rather than fail noisy.
    try {
      mysqlExec('SELECT 1;')
    } catch {
      test.skip('mysql client/DB not available for real-link disabled-permission proof')
    }

    const permissionCodes = ['system:org:list']
    const tenantCode = resolveEffectiveTenantCode()

    // Snapshot original enabled states to restore after this test.
    const originalEnabled: Record<string, boolean> = {}
    for (const code of permissionCodes) {
      originalEnabled[code] = mysqlCheckEnabled(code, tenantCode)
    }

    const emptyStorageState = { cookies: [], origins: [] }

    try {
      // Baseline: ensure disabled permissions are enabled to prove token contains them.
      mysqlSetEnabled(permissionCodes, tenantCode, true)

      // Force a clean (logged-out) context. The Playwright project config uses storageState,
      // and without overriding it /login may redirect to an already-authenticated page.
      const context1 = await browser.newContext({ storageState: emptyStorageState })
      const page1 = await context1.newPage()
      await loginWithMfa(page1)

      const storage1 = await context1.storageState()
      const accessToken1 = extractAccessTokenFromStorageState(storage1)
      const payload1 = decodeJwtPayload(accessToken1)
      const authorities1: string[] = payload1.authorities ?? []
      const permissions1: string[] = payload1.permissions ?? []

      const beforeFound = permissionCodes.filter(
        (c) => authorities1.includes(c) || permissions1.includes(c),
      )
      expect(beforeFound.length).toBeGreaterThan(0)

      // Disable them (fail-closed should remove from JWT and deny access).
      mysqlSetEnabled(permissionCodes, tenantCode, false)

      const context2 = await browser.newContext({ storageState: emptyStorageState })
      const page2 = await context2.newPage()
      await loginWithMfa(page2)

      const storage2 = await context2.storageState()
      const accessToken2 = extractAccessTokenFromStorageState(storage2)
      const payload2 = decodeJwtPayload(accessToken2)

      const authorities2: string[] = payload2.authorities ?? []
      const permissions2: string[] = payload2.permissions ?? []

      for (const code of permissionCodes) {
        expect(authorities2.includes(code)).toBe(false)
        expect(permissions2.includes(code)).toBe(false)
      }

      // Verify protected interface denies disabled permission via claims/recovery chain.
      const resp = await page2.request.get(`${backendBaseUrl}/sys/org/tree`, {
        headers: {
          Authorization: `Bearer ${accessToken2}`,
        },
      })

      // For method security denial, most commonly 403. If env differs, fail with body.
      expect(resp.status(), await resp.text()).toBe(403)
    } finally {
      // Restore original DB state even if assertions fail.
      for (const code of permissionCodes) {
        if (originalEnabled[code] !== undefined) {
          mysqlSetEnabled([code], tenantCode, originalEnabled[code])
        }
      }
    }
  })
})
