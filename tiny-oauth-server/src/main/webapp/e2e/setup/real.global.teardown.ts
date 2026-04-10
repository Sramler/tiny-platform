import { execFileSync } from 'node:child_process'
import fs from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { buildDerivedAssetGovernanceEnv } from './real.global.setup'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const webappRoot = path.resolve(__dirname, '..', '..')
const backendRoot = path.resolve(webappRoot, '..', '..', '..')
const authDir = path.resolve(webappRoot, 'e2e/.auth')
const derivedAssetStatePath = path.resolve(authDir, 'real-derived-assets.json')
const derivedAssetGovernanceScriptPath = path.resolve(
  backendRoot,
  'scripts/verify-real-e2e-derived-assets.sh',
)

type DerivedAssetState = {
  targetGeneratedTenantCodes?: string[]
}

export default async function globalTeardown() {
  if (process.env.E2E_SKIP_REAL_DERIVED_ASSET_CLEANUP === 'true') {
    return
  }

  const rawState = await fs.readFile(derivedAssetStatePath, 'utf8').catch(() => '')
  if (!rawState.trim()) {
    return
  }

  const parsedState = JSON.parse(rawState) as DerivedAssetState
  const tenantCodes = Array.from(
    new Set(
      (parsedState.targetGeneratedTenantCodes ?? [])
        .map((value) => value.trim())
        .filter((value) => value !== ''),
    ),
  )
  const args = ['--apply', '--fail-on-stale']
  if (tenantCodes.length > 0) {
    args.push('--target-generated-tenant-codes', tenantCodes.join(','))
  }

  execFileSync('bash', [derivedAssetGovernanceScriptPath, ...args], {
    cwd: backendRoot,
    stdio: 'inherit',
    env: buildDerivedAssetGovernanceEnv(process.env),
  })

  await fs.rm(derivedAssetStatePath, { force: true })
}
