import { execFileSync } from 'node:child_process'
import fs from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const webappRoot = path.resolve(__dirname, '..', '..')
const backendRoot = path.resolve(webappRoot, '..', '..', '..')
const authDir = path.resolve(webappRoot, 'e2e/.auth')
const authStatePath = path.resolve(authDir, 'scheduling-user.json')
const secondaryAuthStatePath = path.resolve(authDir, 'tenant-b-user.json')
const readonlyAuthStatePath = path.resolve(authDir, 'scheduling-readonly-user.json')
const platformAuthStatePath = path.resolve(authDir, 'platform-admin-user.json')
const seedSqlPath = path.resolve(backendRoot, 'scripts/e2e/seed-scheduling-orchestration.sql')
const ensureAuthScriptPath = path.resolve(backendRoot, 'scripts/e2e/ensure-scheduling-e2e-auth.sh')
const generateAuthStateScriptPath = path.resolve(__dirname, 'generate-auth-state.mjs')
const EMPTY_STORAGE_STATE = JSON.stringify({ cookies: [], origins: [] }, null, 2)
const backendPort = Number(readEnv(['E2E_BACKEND_PORT'], '9000'))
const backendBaseURL = readEnv(['E2E_BACKEND_BASE_URL'], `http://localhost:${backendPort}`)!

type StorageStateOrigin = {
  localStorage?: Array<{
    name: string
    value: string
  }>
}

type StorageState = {
  origins?: StorageStateOrigin[]
}

type ResolvedIdentityEnv = {
  E2E_TENANT_CODE: string
  E2E_USERNAME: string
  E2E_PASSWORD: string
  E2E_TOTP_SECRET: string
  E2E_TOTP_CODE?: string
}

function readEnv(names: string[], fallback: string): string
function readEnv(names: string[], fallback?: string): string | undefined
function readEnv(names: string[], fallback?: string) {
  for (const name of names) {
    const value = process.env[name]
    if (value && value.trim() !== '') {
      return value
    }
  }
  return fallback
}

export function extractAccessTokenFromStorageState(storageState: StorageState): string {
  for (const origin of storageState.origins ?? []) {
    for (const entry of origin.localStorage ?? []) {
      if (!entry.name.startsWith('oidc.user:')) {
        continue
      }
      try {
        const parsed = JSON.parse(entry.value)
        if (parsed?.access_token) {
          return parsed.access_token as string
        }
      } catch {
        // ignore malformed storage entries and continue searching
      }
    }
  }

  throw new Error('未在 storageState 中找到可用的 OIDC access_token')
}

export function shouldCreateTenantViaApi(
  primaryTenantCode: string | undefined,
  targetTenantCode: string | undefined,
) {
  const normalizedPrimary = primaryTenantCode?.trim().toLowerCase()
  const normalizedTarget = targetTenantCode?.trim().toLowerCase()
  return Boolean(normalizedPrimary && normalizedTarget && normalizedPrimary !== normalizedTarget)
}

async function prepareAuthState() {
  await fs.mkdir(authDir, { recursive: true })
  await fs.rm(authStatePath, { force: true })
  await fs.rm(secondaryAuthStatePath, { force: true })
  await fs.rm(readonlyAuthStatePath, { force: true })
  await fs.rm(platformAuthStatePath, { force: true })
  await fs.writeFile(secondaryAuthStatePath, EMPTY_STORAGE_STATE, 'utf8')
  await fs.writeFile(readonlyAuthStatePath, EMPTY_STORAGE_STATE, 'utf8')
  await fs.writeFile(platformAuthStatePath, EMPTY_STORAGE_STATE, 'utf8')
}

async function ensureTenantViaApi(authStateFilePath: string, targetTenantCode: string) {
  const normalizedTargetTenantCode = targetTenantCode.trim().toLowerCase()
  const storageState = JSON.parse(await fs.readFile(authStateFilePath, 'utf8')) as StorageState
  const accessToken = extractAccessTokenFromStorageState(storageState)

  const listResponse = await fetch(
    `${backendBaseURL}/sys/tenants?code=${encodeURIComponent(normalizedTargetTenantCode)}&page=0&size=20`,
    {
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    },
  )
  if (!listResponse.ok) {
    throw new Error(`查询租户失败(${normalizedTargetTenantCode}): HTTP ${listResponse.status}`)
  }

  const listPayload = (await listResponse.json()) as {
    content?: Array<{ code?: string }>
  }
  const alreadyExists = (listPayload.content ?? []).some(
    (tenant) => tenant.code?.trim().toLowerCase() === normalizedTargetTenantCode,
  )
  if (alreadyExists) {
    return
  }

  const createResponse = await fetch(`${backendBaseURL}/sys/tenants`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${accessToken}`,
      'Content-Type': 'application/json',
      'X-Idempotency-Key': `e2e-tenant-create:${normalizedTargetTenantCode}`,
    },
    body: JSON.stringify({
      code: normalizedTargetTenantCode,
      name: `E2E租户(${normalizedTargetTenantCode})`,
      enabled: true,
    }),
  })

  if (!createResponse.ok) {
    const responseText = await createResponse.text().catch(() => '')
    throw new Error(
      `通过 /sys/tenants 创建租户失败(${normalizedTargetTenantCode}): HTTP ${createResponse.status} ${responseText}`.trim(),
    )
  }
}

function resolvePlatformIdentityEnv(): ResolvedIdentityEnv | null {
  const tenantCode = process.env.E2E_PLATFORM_TENANT_CODE
  const username = process.env.E2E_PLATFORM_USERNAME
  const password = process.env.E2E_PLATFORM_PASSWORD
  const totpSecret = process.env.E2E_PLATFORM_TOTP_SECRET
  const totpCode = process.env.E2E_PLATFORM_TOTP_CODE

  const hasAnyPlatformValue = [username, password, totpSecret, totpCode].some(isConfiguredValue)
  if (!hasAnyPlatformValue) {
    return null
  }

  const missing: string[] = []
  if (!isConfiguredValue(username)) missing.push('E2E_PLATFORM_USERNAME')
  if (!isConfiguredValue(password)) missing.push('E2E_PLATFORM_PASSWORD')
  if (!isConfiguredValue(totpSecret)) missing.push('E2E_PLATFORM_TOTP_SECRET')

  if (missing.length > 0) {
    throw new Error(
      `租户管理 real-link 需要完整的平台自动化身份配置，缺少: ${missing.join(', ')}`,
    )
  }

  if (!isConfiguredValue(tenantCode)) {
    throw new Error('缺少 E2E_PLATFORM_TENANT_CODE（请勿默认回退到 default，default 租户可能被冻结）')
  }
  if (tenantCode!.trim().toLowerCase() === 'default') {
    throw new Error('E2E_PLATFORM_TENANT_CODE 不允许使用 default：default 租户可能被冻结导致平台身份无法登录')
  }

  return {
    E2E_TENANT_CODE: tenantCode!.trim(),
    E2E_USERNAME: username!.trim(),
    E2E_PASSWORD: password!.trim(),
    E2E_TOTP_SECRET: totpSecret!.trim(),
    ...(isConfiguredValue(totpCode) ? { E2E_TOTP_CODE: totpCode!.trim() } : {}),
  }
}

function hasMysqlClient() {
  const mysqlBinary = readEnv(['E2E_MYSQL_BIN'], 'mysql')
  try {
    execFileSync(mysqlBinary, ['-V'], {
      stdio: 'ignore',
    })
    return true
  } catch {
    return false
  }
}

async function runMysqlSeed() {
  if (process.env.E2E_USE_SQL_SEED !== 'true') {
    return
  }

  if (!hasMysqlClient()) {
    throw new Error(
      'E2E_USE_SQL_SEED=true 但当前环境未检测到可用 mysql 客户端，请确认 PATH 或设置 E2E_MYSQL_BIN 为 mysql 可执行文件路径',
    )
  }

  const mysqlPassword = readEnv(['E2E_DB_PASSWORD', 'E2E_MYSQL_PASSWORD', 'MYSQL_ROOT_PASSWORD'], '')
  if (!mysqlPassword) {
    throw new Error(
      'real scheduling e2e 需要设置 E2E_DB_PASSWORD、E2E_MYSQL_PASSWORD 或 MYSQL_ROOT_PASSWORD，以便执行种子 SQL'
    )
  }

  const mysqlBinary = readEnv(['E2E_MYSQL_BIN'], 'mysql')
  const mysqlHost = readEnv(['E2E_DB_HOST', 'E2E_MYSQL_HOST'], '127.0.0.1')
  const mysqlPort = readEnv(['E2E_DB_PORT', 'E2E_MYSQL_PORT'], '3306')
  const mysqlUser = readEnv(['E2E_DB_USER', 'E2E_MYSQL_USER'], 'root')
  const mysqlDatabase = readEnv(['E2E_DB_NAME', 'E2E_MYSQL_DATABASE'], 'tiny_web')

  return fs.readFile(seedSqlPath, 'utf8').then((sql) => {
    execFileSync(
      mysqlBinary,
      ['-h', mysqlHost, '-P', mysqlPort, '-u', mysqlUser, `-p${mysqlPassword}`, mysqlDatabase],
      {
        input: sql,
        stdio: ['pipe', 'inherit', 'inherit'],
      }
    )
  })
}

function ensureDeterministicE2EAuth() {
  execFileSync('bash', [ensureAuthScriptPath], {
    cwd: backendRoot,
    stdio: 'inherit',
    env: process.env,
  })
}

function ensureDeterministicE2EAuthFor(envOverrides: Record<string, string>) {
  execFileSync('bash', [ensureAuthScriptPath], {
    cwd: backendRoot,
    stdio: 'inherit',
    env: {
      ...process.env,
      ...envOverrides,
    },
  })
}

function generateAuthState() {
  execFileSync('node', [generateAuthStateScriptPath], {
    cwd: webappRoot,
    stdio: 'inherit',
    env: {
      ...process.env,
      E2E_AUTH_STATE_PATH: authStatePath,
    },
  })
}

export function buildAuthStateEnv(
  baseEnv: NodeJS.ProcessEnv,
  envOverrides: Record<string, string>,
  outputPath: string,
): NodeJS.ProcessEnv {
  return {
    ...baseEnv,
    ...envOverrides,
    // 避免沿用主身份注入的 E2E_TOTP_CODE；tenant B 未显式提供 code 时应回退到自己的 secret。
    E2E_TOTP_CODE: envOverrides.E2E_TOTP_CODE ?? '',
    E2E_AUTH_STATE_PATH: outputPath,
  }
}

export function buildSecondaryAuthStateEnv(
  baseEnv: NodeJS.ProcessEnv,
  envOverrides: Record<string, string>,
  outputPath: string,
): NodeJS.ProcessEnv {
  return buildAuthStateEnv(baseEnv, envOverrides, outputPath)
}

function generateAuthStateFor(envOverrides: Record<string, string>, outputPath: string) {
  execFileSync('node', [generateAuthStateScriptPath], {
    cwd: webappRoot,
    stdio: 'inherit',
    env: buildAuthStateEnv(process.env, envOverrides, outputPath),
  })
}

function isConfiguredValue(value: string | undefined) {
  if (!value || value.trim() === '') {
    return false
  }
  const normalized = value.trim()
  return !(normalized.startsWith('<') && normalized.endsWith('>'))
}

function resolveSecondaryIdentityEnv(): ResolvedIdentityEnv | null {
  const tenantCode = process.env.E2E_TENANT_CODE_B
  const username = process.env.E2E_USERNAME_B
  const password = process.env.E2E_PASSWORD_B
  const totpSecret = process.env.E2E_TOTP_SECRET_B
  const totpCode = process.env.E2E_TOTP_CODE_B

  const hasAnySecondaryValue = [tenantCode, username, password, totpSecret, totpCode].some(
    isConfiguredValue,
  )
  if (!hasAnySecondaryValue) {
    return null
  }

  const missing: string[] = []
  if (!isConfiguredValue(tenantCode)) missing.push('E2E_TENANT_CODE_B')
  if (!isConfiguredValue(username)) missing.push('E2E_USERNAME_B')
  if (!isConfiguredValue(password)) missing.push('E2E_PASSWORD_B')
  if (!isConfiguredValue(totpSecret)) missing.push('E2E_TOTP_SECRET_B')

  if (missing.length > 0) {
    throw new Error(
      `跨租户 real-link 需要完整的第二租户身份配置，缺少: ${missing.join(', ')}`
    )
  }

  return {
    E2E_TENANT_CODE: tenantCode!.trim(),
    E2E_USERNAME: username!.trim(),
    E2E_PASSWORD: password!.trim(),
    E2E_TOTP_SECRET: totpSecret!.trim(),
    ...(isConfiguredValue(totpCode) ? { E2E_TOTP_CODE: totpCode!.trim() } : {}),
  }
}

function resolveReadonlyIdentityEnv(): ResolvedIdentityEnv | null {
  const tenantCode = process.env.E2E_TENANT_CODE_READONLY ?? process.env.E2E_TENANT_CODE
  const username = process.env.E2E_USERNAME_READONLY
  const password = process.env.E2E_PASSWORD_READONLY
  const totpSecret = process.env.E2E_TOTP_SECRET_READONLY
  const totpCode = process.env.E2E_TOTP_CODE_READONLY

  const hasAnyReadonlyValue = [tenantCode, username, password, totpSecret, totpCode].some(
    isConfiguredValue,
  )
  if (!hasAnyReadonlyValue) {
    return null
  }

  const missing: string[] = []
  if (!isConfiguredValue(username)) missing.push('E2E_USERNAME_READONLY')
  if (!isConfiguredValue(password)) missing.push('E2E_PASSWORD_READONLY')
  if (!isConfiguredValue(totpSecret)) missing.push('E2E_TOTP_SECRET_READONLY')

  if (missing.length > 0) {
    return null
  }

  return {
    E2E_TENANT_CODE: (tenantCode ?? process.env.E2E_TENANT_CODE ?? '').trim(),
    E2E_USERNAME: username!.trim(),
    E2E_PASSWORD: password!.trim(),
    E2E_TOTP_SECRET: totpSecret!.trim(),
    ...(isConfiguredValue(totpCode) ? { E2E_TOTP_CODE: totpCode!.trim() } : {}),
  }
}

export default async function globalSetup() {
  await prepareAuthState()
  ensureDeterministicE2EAuth()
  await runMysqlSeed()
  generateAuthState()

  const primaryAuthExists = await fs
    .access(authStatePath)
    .then(() => true)
    .catch(() => false)
  if (!primaryAuthExists) {
    throw new Error(
      `real-link globalSetup: 未生成主身份登录态 ${authStatePath}。请确认后端与前端已启动且 E2E_TENANT_CODE/E2E_USERNAME/E2E_PASSWORD/E2E_TOTP_SECRET 已配置，并查看 generate-auth-state.mjs 的登录输出。`,
    )
  }

  const primaryTenantCode = process.env.E2E_TENANT_CODE
  const platformTenantCode = (process.env.E2E_PLATFORM_TENANT_CODE ?? 'default').trim()
  const requiresPlatformBootstrapIdentity =
    shouldCreateTenantViaApi(primaryTenantCode, process.env.E2E_TENANT_CODE_B) ||
    shouldCreateTenantViaApi(
      primaryTenantCode,
      process.env.E2E_TENANT_CODE_READONLY ?? process.env.E2E_TENANT_CODE,
    )
  let tenantBootstrapAuthStatePath = authStatePath
  let platformAuthGenerated = false

  if (
    requiresPlatformBootstrapIdentity &&
    primaryTenantCode?.trim().toLowerCase() !== platformTenantCode.toLowerCase()
  ) {
    const platformIdentityEnv = resolvePlatformIdentityEnv()
    if (!platformIdentityEnv) {
      throw new Error(
        'secondary/readonly real-link 需要平台自动化身份去调用 /sys/tenants，请提供 E2E_PLATFORM_USERNAME / E2E_PLATFORM_PASSWORD / E2E_PLATFORM_TOTP_SECRET',
      )
    }
    ensureDeterministicE2EAuthFor(platformIdentityEnv)
    generateAuthStateFor(platformIdentityEnv, platformAuthStatePath)
    tenantBootstrapAuthStatePath = platformAuthStatePath
    platformAuthGenerated = true
  }

  const platformIdentityEnv = resolvePlatformIdentityEnv()
  if (platformIdentityEnv && !platformAuthGenerated) {
    ensureDeterministicE2EAuthFor(platformIdentityEnv)
    generateAuthStateFor(platformIdentityEnv, platformAuthStatePath)
  }

  const secondaryIdentityEnv = resolveSecondaryIdentityEnv()
  if (secondaryIdentityEnv) {
    const secondaryTenantCode = secondaryIdentityEnv.E2E_TENANT_CODE
    if (shouldCreateTenantViaApi(process.env.E2E_TENANT_CODE, secondaryTenantCode)) {
      await ensureTenantViaApi(tenantBootstrapAuthStatePath, secondaryTenantCode)
    }
    ensureDeterministicE2EAuthFor(secondaryIdentityEnv)
    generateAuthStateFor(secondaryIdentityEnv, secondaryAuthStatePath)
  }

  const readonlyIdentityEnv = resolveReadonlyIdentityEnv()
  if (readonlyIdentityEnv) {
    const readonlyTenantCode = readonlyIdentityEnv.E2E_TENANT_CODE
    if (shouldCreateTenantViaApi(process.env.E2E_TENANT_CODE, readonlyTenantCode)) {
      await ensureTenantViaApi(tenantBootstrapAuthStatePath, readonlyTenantCode)
    }
    ensureDeterministicE2EAuthFor({
      E2E_TENANT_CODE_READONLY: readonlyTenantCode,
      E2E_USERNAME_READONLY: readonlyIdentityEnv.E2E_USERNAME,
      E2E_PASSWORD_READONLY: readonlyIdentityEnv.E2E_PASSWORD,
      E2E_TOTP_SECRET_READONLY: readonlyIdentityEnv.E2E_TOTP_SECRET,
      ...(readonlyIdentityEnv.E2E_TOTP_CODE
        ? { E2E_TOTP_CODE_READONLY: readonlyIdentityEnv.E2E_TOTP_CODE }
        : {}),
    })
    generateAuthStateFor(readonlyIdentityEnv, readonlyAuthStatePath)
  }
}
