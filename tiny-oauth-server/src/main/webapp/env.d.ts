/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_APP_ENV: 'dev' | 'test' | 'prod'
  readonly VITE_API_BASE_URL: string
  readonly VITE_ENABLE_CONSOLE_LOG: string
  readonly VITE_ENABLE_CONSOLE_DEBUG: string
  readonly VITE_ENABLE_CONSOLE_WARN: string
  readonly VITE_ENABLE_CONSOLE_ERROR: string
  readonly VITE_LOG_LEVEL: 'debug' | 'info' | 'warn' | 'error' | 'none'
  readonly VITE_ENABLE_PERSISTENT_LOG: string
  readonly VITE_PERSISTENT_LOG_LEVELS?: string
  readonly VITE_PERSISTENT_LOG_SAMPLE_RATE?: string
  readonly VITE_ENABLE_OIDC_TRACE?: string
  readonly VITE_OIDC_AUTHORITY?: string
  readonly VITE_OIDC_CLIENT_ID?: string
  readonly VITE_OIDC_REDIRECT_URI?: string
  readonly VITE_OIDC_POST_LOGOUT_REDIRECT_URI?: string
  readonly VITE_OIDC_SILENT_REDIRECT_URI?: string
  readonly VITE_OIDC_SILENT_REQUEST_TIMEOUT_SECONDS?: string
  readonly VITE_OIDC_SCOPES?: string
  readonly VITE_OIDC_STORAGE?: 'local' | 'session'
  readonly VITE_AUTH_FORCE_LOGOUT_ON_RENEW_FAIL?: string
  readonly VITE_AUTH_FETCH_TIMEOUT_MS?: string
  readonly VITE_AUTH_ENABLE_PLATFORM_SESSION_SILENT_LOGIN?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

declare module '@ant-design/icons-vue/es/icons/*.js' {
  import type { Component } from 'vue'

  const component: Component
  export default component
}
