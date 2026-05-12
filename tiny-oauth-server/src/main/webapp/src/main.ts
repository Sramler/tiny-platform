import './assets/main.css'
// 导入全局主题变量，确保在这里定义的 CSS 变量在整个应用的所有组件中都可用。
import './assets/theme.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import 'ant-design-vue/dist/reset.css'

import App from './App.vue'
import router from './router'
function bootstrap() {
  const app = createApp(App)
  app.use(createPinia())
  app.use(router)
  app.mount('#app')
  ;(window as Window & { __TINY_APP_MOUNTED__?: boolean }).__TINY_APP_MOUNTED__ = true
}

bootstrap()
