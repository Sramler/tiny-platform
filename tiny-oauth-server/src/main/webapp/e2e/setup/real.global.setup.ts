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
const seedSqlPath = path.resolve(backendRoot, 'scripts/e2e/seed-scheduling-orchestration.sql')
const ensureAuthScriptPath = path.resolve(backendRoot, 'scripts/e2e/ensure-scheduling-e2e-auth.sh')
const generateAuthStateScriptPath = path.resolve(__dirname, 'generate-auth-state.mjs')

function readEnv(names: string[], fallback?: string) {
  for (const name of names) {
    const value = process.env[name]
    if (value && value.trim() !== '') {
      return value
    }
  }
  return fallback
}

async function prepareAuthState() {
  await fs.mkdir(authDir, { recursive: true })
  await fs.rm(authStatePath, { force: true })
}

function hasMysqlClient() {
  try {
    execFileSync('sh', ['-lc', 'command -v mysql >/dev/null 2>&1'], {
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
    throw new Error('E2E_USE_SQL_SEED=true 但当前环境未安装 mysql 客户端')
  }

  const mysqlPassword = readEnv(['E2E_DB_PASSWORD', 'E2E_MYSQL_PASSWORD', 'MYSQL_ROOT_PASSWORD'], '')
  if (!mysqlPassword) {
    throw new Error(
      'real scheduling e2e 需要设置 E2E_DB_PASSWORD、E2E_MYSQL_PASSWORD 或 MYSQL_ROOT_PASSWORD，以便执行种子 SQL'
    )
  }

  const mysqlHost = readEnv(['E2E_DB_HOST', 'E2E_MYSQL_HOST'], '127.0.0.1')
  const mysqlPort = readEnv(['E2E_DB_PORT', 'E2E_MYSQL_PORT'], '3306')
  const mysqlUser = readEnv(['E2E_DB_USER', 'E2E_MYSQL_USER'], 'root')
  const mysqlDatabase = readEnv(['E2E_DB_NAME', 'E2E_MYSQL_DATABASE'], 'tiny_web')

  return fs.readFile(seedSqlPath, 'utf8').then((sql) => {
    execFileSync(
      'mysql',
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

export default async function globalSetup() {
  await prepareAuthState()
  ensureDeterministicE2EAuth()
  await runMysqlSeed()
  generateAuthState()
}
