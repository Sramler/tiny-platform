import { describe, expect, it } from 'vitest'
import { buildSecondaryAuthStateEnv } from '../../e2e/setup/real.global.setup'

describe('real.global.setup secondary auth-state env', () => {
  it('clears inherited E2E_TOTP_CODE when tenant B does not provide its own code', () => {
    const env = buildSecondaryAuthStateEnv(
      {
        E2E_TOTP_CODE: '111111',
        E2E_TOTP_SECRET: 'PRIMARYSECRET',
      },
      {
        E2E_TENANT_CODE: 'bench-1m',
        E2E_USERNAME: 'e2e_admin_b',
        E2E_TOTP_SECRET: 'SECONDARYSECRET',
      },
      '/tmp/tenant-b.json',
    )

    expect(env.E2E_TOTP_CODE).toBe('')
    expect(env.E2E_TOTP_SECRET).toBe('SECONDARYSECRET')
    expect(env.E2E_AUTH_STATE_PATH).toBe('/tmp/tenant-b.json')
  })

  it('keeps tenant B explicit E2E_TOTP_CODE when provided', () => {
    const env = buildSecondaryAuthStateEnv(
      {
        E2E_TOTP_CODE: '111111',
        E2E_TOTP_SECRET: 'PRIMARYSECRET',
      },
      {
        E2E_TENANT_CODE: 'bench-1m',
        E2E_USERNAME: 'e2e_admin_b',
        E2E_TOTP_SECRET: 'SECONDARYSECRET',
        E2E_TOTP_CODE: '222222',
      },
      '/tmp/tenant-b.json',
    )

    expect(env.E2E_TOTP_CODE).toBe('222222')
    expect(env.E2E_TOTP_SECRET).toBe('SECONDARYSECRET')
  })
})
