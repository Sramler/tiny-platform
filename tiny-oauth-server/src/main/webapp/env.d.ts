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
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

declare module '@ant-design/icons-vue/es/icons/*.js' {
  import type { Component } from 'vue'

  const component: Component
  export default component
}
