/**
 * Auth 运行期开关统一从环境变量读取，避免在多个文件里散落魔法常量。
 * 变量说明参见 docs/ENV_CONFIG.md。
 */
import { logger } from '@/utils/logger'

const boolFromEnv = (key: string, fallback: boolean) => {
  const value = import.meta.env[key as keyof ImportMetaEnv]
  if (value === undefined) return fallback
  if (value === 'true' || value === true) return true
  if (value === 'false' || value === false) return false
  logger.warn(`[Auth][config] ${key} 值 ${value} 无法解析为布尔型，使用默认值 ${fallback}`)
  return fallback
}

const numberFromEnv = (
  key: string,
  fallback: number,
  options: { min?: number; max?: number } = {},
) => {
  const raw = import.meta.env[key as keyof ImportMetaEnv]
  if (raw === undefined) return fallback
  const parsed = Number(raw)
  if (Number.isNaN(parsed)) {
    logger.warn(`[Auth][config] ${key} 值 ${raw} 不是有效数字，使用默认值 ${fallback}`)
    return fallback
  }

  if (options.min !== undefined && parsed < options.min) {
    return options.min
  }
  if (options.max !== undefined && parsed > options.max) {
    return options.max
  }
  return parsed
}

export const authRuntimeConfig = Object.freeze({
  forceLogoutOnRenewFail: boolFromEnv('VITE_AUTH_FORCE_LOGOUT_ON_RENEW_FAIL', true),
  fetchTimeoutMs: numberFromEnv('VITE_AUTH_FETCH_TIMEOUT_MS', 8000, { min: 3000, max: 60000 }),
  enablePlatformSessionSilentLogin: boolFromEnv('VITE_AUTH_ENABLE_PLATFORM_SESSION_SILENT_LOGIN', true),
})

export type AuthRuntimeConfig = typeof authRuntimeConfig
