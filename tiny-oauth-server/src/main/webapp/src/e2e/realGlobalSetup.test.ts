import { describe, expect, it } from 'vitest'
import {
  buildAuthStateEnv,
  buildSecondaryAuthStateEnv,
  extractAccessTokenFromStorageState,
  shouldCreateTenantViaApi,
} from '../../e2e/setup/real.global.setup'

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

describe('real.global.setup readonly auth-state env', () => {
  it('clears inherited E2E_TOTP_CODE when readonly identity does not provide its own code', () => {
    const env = buildAuthStateEnv(
      {
        E2E_TOTP_CODE: '111111',
        E2E_TOTP_SECRET: 'PRIMARYSECRET',
      },
      {
        E2E_TENANT_CODE: 'default',
        E2E_USERNAME: 'e2e_scheduling_readonly',
        E2E_TOTP_SECRET: 'READONLYSECRET',
      },
      '/tmp/readonly.json',
    )

    expect(env.E2E_TOTP_CODE).toBe('')
    expect(env.E2E_TOTP_SECRET).toBe('READONLYSECRET')
    expect(env.E2E_AUTH_STATE_PATH).toBe('/tmp/readonly.json')
  })
})

describe('real.global.setup auth-state login mode', () => {
  it('defaults generated auth-state env to tenant login mode', () => {
    const env = buildAuthStateEnv(
      {
        E2E_TOTP_SECRET: 'PRIMARYSECRET',
      },
      {
        E2E_TENANT_CODE: 'bench-1m',
        E2E_USERNAME: 'e2e_admin',
      },
      '/tmp/auth.json',
    )

    expect(env.E2E_LOGIN_MODE).toBe('TENANT')
  })

  it('allows overriding auth-state env to platform login mode', () => {
    const env = buildAuthStateEnv(
      {
        E2E_TOTP_SECRET: 'PRIMARYSECRET',
      },
      {
        E2E_TENANT_CODE: 'platform-e2e',
        E2E_USERNAME: 'e2e_platform_admin',
        E2E_LOGIN_MODE: 'PLATFORM',
      },
      '/tmp/platform-auth.json',
    )

    expect(env.E2E_LOGIN_MODE).toBe('PLATFORM')
  })
})

describe('real.global.setup tenant bootstrap helpers', () => {
  it('extracts OIDC access token from Playwright storage state', () => {
    const accessToken = extractAccessTokenFromStorageState({
      origins: [
        {
          localStorage: [
            {
              name: 'oidc.user:http://localhost:9000:vue-client',
              value: JSON.stringify({ access_token: 'token-123' }),
            },
          ],
        },
      ],
    })

    expect(accessToken).toBe('token-123')
  })

  it('creates tenant via API only when target tenant differs from primary tenant', () => {
    expect(shouldCreateTenantViaApi('default', 'tenant-b')).toBe(true)
    expect(shouldCreateTenantViaApi('default', 'DEFAULT')).toBe(false)
    expect(shouldCreateTenantViaApi('default', undefined)).toBe(false)
  })

  it('does not require tenant bootstrap API when primary and readonly tenant are the same', () => {
    expect(shouldCreateTenantViaApi('tenant-a', 'tenant-a')).toBe(false)
  })
})
