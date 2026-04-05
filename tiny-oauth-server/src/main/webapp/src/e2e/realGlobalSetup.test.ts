import { describe, expect, it } from 'vitest'
import {
  buildEnsureAuthEnv,
  buildAuthStateEnv,
  buildSecondaryAuthStateEnv,
  deriveTenantCodeForTenantScope,
  extractAccessTokenFromStorageState,
  readConfiguredValue,
  resolveBindTenantCode,
  resolveReadonlyTenantCode,
  shouldCreateTenantViaApi,
  shouldUseTenantScopedPrimaryAuthState,
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

describe('real.global.setup configured env resolution', () => {
  it('falls back from blank readonly tenant code to primary tenant code', () => {
    const tenantCode = readConfiguredValue(
      {
        E2E_TENANT_CODE_READONLY: '',
        E2E_TENANT_CODE: 'bench-1m',
      },
      ['E2E_TENANT_CODE_READONLY', 'E2E_TENANT_CODE'],
    )

    expect(tenantCode).toBe('bench-1m')
  })

  it('ignores placeholder env values when resolving tenant code fallback', () => {
    const tenantCode = readConfiguredValue(
      {
        E2E_TENANT_CODE_READONLY: '<readonly-tenant>',
        E2E_TENANT_CODE: 'bench-1m',
      },
      ['E2E_TENANT_CODE_READONLY', 'E2E_TENANT_CODE'],
    )

    expect(tenantCode).toBe('bench-1m')
  })

  it('derives readonly tenant code away from platform tenant when readonly tenant is not explicitly configured', () => {
    const tenantCode = resolveReadonlyTenantCode({
      E2E_TENANT_CODE: 'bench-1m',
      E2E_PLATFORM_TENANT_CODE: 'bench-1m',
      E2E_USERNAME_READONLY: 'e2e_scheduling_readonly',
    })

    expect(tenantCode).toBe('bench-1m-t')
  })

  it('prefers explicit readonly tenant code over derived tenant-scoped fallback', () => {
    const tenantCode = resolveReadonlyTenantCode({
      E2E_TENANT_CODE: 'bench-1m',
      E2E_PLATFORM_TENANT_CODE: 'bench-1m',
      E2E_TENANT_CODE_READONLY: 'readonly-bench',
    })

    expect(tenantCode).toBe('readonly-bench')
  })

  it('derives bind tenant code away from platform tenant when bind tenant is not explicitly configured', () => {
    const tenantCode = resolveBindTenantCode({
      E2E_TENANT_CODE: 'bench-1m',
      E2E_PLATFORM_TENANT_CODE: 'bench-1m',
      E2E_USERNAME_BIND: 'e2e_bind',
    })

    expect(tenantCode).toBe('bench-1m-t')
  })

  it('prefers explicit bind tenant code over derived tenant-scoped fallback', () => {
    const tenantCode = resolveBindTenantCode({
      E2E_TENANT_CODE: 'bench-1m',
      E2E_PLATFORM_TENANT_CODE: 'bench-1m',
      E2E_TENANT_CODE_BIND: 'bind-bench',
    })

    expect(tenantCode).toBe('bind-bench')
  })

  it('ignores explicit bind tenant code when it matches platform tenant and derives tenant-scoped fallback', () => {
    const tenantCode = resolveBindTenantCode({
      E2E_TENANT_CODE: 'bench-1m',
      E2E_PLATFORM_TENANT_CODE: 'bench-1m',
      E2E_TENANT_CODE_BIND: 'bench-1m',
    })

    expect(tenantCode).toBe('bench-1m-t')
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

describe('real.global.setup ensure auth env', () => {
  it('preserves bind and readonly companions from the primary env when overriding platform identity', () => {
    const env = buildEnsureAuthEnv(
      {
        E2E_TENANT_CODE: 'bench-1m',
        E2E_TENANT_CODE_BIND: 'bench-1m',
        E2E_USERNAME_BIND: 'e2e_bind',
        E2E_PASSWORD_BIND: 'bind-pass',
        E2E_USERNAME_READONLY: 'e2e_readonly',
        E2E_PASSWORD_READONLY: 'readonly-pass',
        E2E_TOTP_SECRET_READONLY: 'READONLYSECRET',
      },
      {
        E2E_TENANT_CODE: 'platform-main',
        E2E_USERNAME: 'e2e_platform_admin',
      },
    )

    expect(env.E2E_TENANT_CODE).toBe('platform-main')
    expect(env.E2E_TENANT_CODE_BIND).toBe('bench-1m')
    expect(env.E2E_USERNAME_BIND).toBe('e2e_bind')
    expect(env.E2E_USERNAME_READONLY).toBe('e2e_readonly')
    expect(env.E2E_TENANT_CODE_READONLY).toBe('bench-1m')
  })

  it('respects explicit readonly overrides when preparing readonly identity', () => {
    const env = buildEnsureAuthEnv(
      {
        E2E_TENANT_CODE: 'bench-1m',
        E2E_TENANT_CODE_READONLY: 'bench-1m',
        E2E_USERNAME_READONLY: 'primary_readonly',
      },
      {
        E2E_TENANT_CODE_READONLY: 'bench-2m',
        E2E_USERNAME_READONLY: 'secondary_readonly',
      },
    )

    expect(env.E2E_TENANT_CODE_READONLY).toBe('bench-2m')
    expect(env.E2E_USERNAME_READONLY).toBe('secondary_readonly')
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

  it('switches primary storageState generation to tenant-scoped auth when primary tenant is the platform tenant', () => {
    expect(shouldUseTenantScopedPrimaryAuthState('default', 'default')).toBe(true)
    expect(shouldUseTenantScopedPrimaryAuthState('platform-main', 'platform-main')).toBe(true)
    expect(shouldUseTenantScopedPrimaryAuthState('bench-1m', 'platform-main')).toBe(false)
  })

  it('does not switch primary storageState when the tenant-scoped fallback is unnecessary', () => {
    expect(shouldUseTenantScopedPrimaryAuthState('bench-1m', 'default')).toBe(false)
  })
})

describe('deriveTenantCodeForTenantScope', () => {
  it('derives a distinct tenant code when primary matches platform (openOidcDebug primary login must match globalSetup)', () => {
    expect(deriveTenantCodeForTenantScope('default', 'default')).toBe('default-t')
    expect(deriveTenantCodeForTenantScope('bench-1m', 'bench-1m')).toBe('bench-1m-t')
  })

  it('uses primary tenant code as-is when it already differs from platform tenant', () => {
    expect(deriveTenantCodeForTenantScope('bench-1m', 'default')).toBe('bench-1m')
  })
})
