import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'
import { visualizer } from 'rollup-plugin-visualizer'
import Components from 'unplugin-vue-components/vite'
import { AntDesignVueResolver } from 'unplugin-vue-components/resolvers'

const schedulingCoverageOnly = process.env.VITEST_SCHEDULING_COVERAGE === '1'
const analyzeBundle = process.env.VITE_BUNDLE_ANALYZE === '1'

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
  build: {
    /** 管理端主入口 chunk 目标随拆包策略变化；消除默认 500kB 噪声，不等于已完成业务分包（见 docs 构建卫生台账）。 */
    chunkSizeWarningLimit: 3072,
    rollupOptions: {
      output: {
        manualChunks,
      },
    },
  },
  // server: {
  //   open: true, // 启动时自动打开浏览器
  //   host: 'localhost', // 可选，指定主机
  //   port: 5173, // 可选，指定端口
  // },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup-vitest.ts'],
    css: false,
    include: ['src/**/*.test.ts'],
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
