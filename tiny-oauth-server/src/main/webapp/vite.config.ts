import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'
// Removed unplugin-vue-components as it's not used

const schedulingCoverageOnly = process.env.VITEST_SCHEDULING_COVERAGE === '1'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue(), vueDevTools()],
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
