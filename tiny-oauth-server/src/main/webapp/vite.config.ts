import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'
import { visualizer } from 'rollup-plugin-visualizer'
import Components from 'unplugin-vue-components/vite'
import { AntDesignVueResolver } from 'unplugin-vue-components/resolvers'
import { DEV_BACKEND_PROXY_PATHS, resolveDevBackendProxyBypass } from './src/config/devBackendProxy'

const schedulingCoverageOnly = process.env.VITEST_SCHEDULING_COVERAGE === '1'
const analyzeBundle = process.env.VITE_BUNDLE_ANALYZE === '1'
const devBackendTarget = process.env.VITE_DEV_BACKEND_TARGET || 'http://localhost:9000'

/**
 * 将 node_modules 按稳定边界拆块，降低入口 index chunk 体积；顺序需避免误匹配（如 ant-design-vue 含 vue 字样）。
 * 见 docs/TINY_PLATFORM_BUILD_TECH_DEBT_LEDGER.md §1（主包策略）。
 */
function manualChunks(id: string): string | undefined {
  if (!id.includes('node_modules')) return undefined
  if (id.includes('ant-design-vue')) return 'vendor-antd'
  if (id.includes('@ant-design/icons-vue')) return 'vendor-antd-icons'
  if (id.includes('oidc-client-ts')) return 'vendor-oidc'
  if (id.includes('node_modules/jose')) return 'vendor-oidc'
  if (id.includes('axios')) return 'vendor-axios'
  if (
    id.includes('bpmn-js') ||
    id.includes('diagram-js') ||
    id.includes('camunda-bpmn') ||
    id.includes('bpmn-io') ||
    id.includes('properties-panel')
  ) {
    return 'vendor-bpmn'
  }
  if (id.includes('vue-router')) return 'vendor-vue-router'
  if (id.includes('pinia')) return 'vendor-pinia'
  if (id.includes('@vue/') || /node_modules[/\\]vue[/\\]/.test(id)) return 'vendor-vue'
  return 'vendor-other'
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    /**
     * 策略 B（唯一主策略）：模板中 a-* / AntD 组件按需解析，**禁止** `app.use(整包 Antd)`。
     * `importStyle: false`：全局已引入 `ant-design-vue/dist/reset.css`。
     * `resolveIcons: true`：模板内 `<XxxOutlined />` 从 `@ant-design/icons-vue` 按名解析。
     */
    Components({
      dts: 'src/components.d.ts',
      resolvers: [
        AntDesignVueResolver({
          importStyle: false,
          resolveIcons: true,
        }),
      ],
    }),
    vueDevTools(),
    analyzeBundle &&
      visualizer({
        filename: 'dist/bundle-stats.html',
        gzipSize: true,
        open: false,
        template: 'treemap',
      }),
  ].filter(Boolean),
  server: {
    proxy: Object.fromEntries(
      DEV_BACKEND_PROXY_PATHS.map((path) => [
        path,
        {
          target: devBackendTarget,
          changeOrigin: true,
          bypass(req) {
            // 仅已知 SPA 深链的 GET/HEAD 文档导航回退 index；API、写请求和下载仍代理后端。
            return resolveDevBackendProxyBypass(req.url, req.method, req.headers)
          },
        },
      ]),
    ),
  },
  build: {
    /** 管理端主入口 chunk 目标随拆包策略变化；消除默认 500kB 噪声，不等于已完成业务分包（见 docs 构建卫生台账）。 */
    chunkSizeWarningLimit: 3072,
    rollupOptions: {
      input: {
        index: fileURLToPath(new URL('./index.html', import.meta.url)),
        silentRenew: fileURLToPath(new URL('./silent-renew.html', import.meta.url)),
      },
      output: {
        manualChunks,
      },
    },
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  optimizeDeps: {
    /**
     * Vite 8 的 dev optimizer 会在冷启动后继续发现依赖并重写 node_modules/.vite/deps。
     * bpmn-js / diagram-js 依赖树较深且会生成多个共享 chunk，HMR 重优化时容易让浏览器请求到已被替换的旧 chunk。
     * 这里让 BPMN 建模相关包保持源码 ESM 加载，避免 dev 环境出现 “deps/*.js does not exist” 的预构建缓存错位。
     */
    exclude: [
      '@bpmn-io/properties-panel',
      'bpmn-js',
      'bpmn-js/lib/Modeler',
      'bpmn-js/lib/Viewer',
      'bpmn-js-i18n',
      'bpmn-js-i18n/translations/zn.js',
      'bpmn-js-properties-panel',
      'camunda-bpmn-moddle',
      'camunda-bpmn-moddle/resources/camunda.json',
      'diagram-js',
      'diagram-js-minimap',
    ],
    /**
     * Ant Design Vue 的按需组件会在启动后补充发现入口；提前 include 可减少二次优化和整页 reload。
     */
    include: [
      'ant-design-vue/es',
      'ant-design-vue/es/date-picker/dayjs',
      'ant-design-vue/es/time-picker/dayjs',
      'classnames',
      'dayjs',
    ],
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup-vitest.ts'],
    css: false,
    include: ['src/**/*.test.ts'],
    /** Vitest 4 + Vue SFC cold transforms can exceed the 5s default when several heavy AntDV pages run together. */
    testTimeout: 10_000,
    restoreMocks: true,
    clearMocks: true,
    ...(schedulingCoverageOnly
      ? {
          coverage: {
            include: ['src/views/scheduling/**/*.{ts,vue}', 'src/api/scheduling*.ts'],
            exclude: ['src/views/scheduling/**/*.test.ts', 'src/api/**/*.test.ts'],
          },
        }
      : {}),
  },
})
