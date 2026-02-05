<template>
  <div class="login-page">
    <div class="login-container">
      <div class="login-header">
        <h1>欢迎登录</h1>
        <p>请输入您的账号信息</p>
      </div>

      <div v-if="errorText" class="error-message">{{ errorText }}</div>

      <form ref="formRef" :action="loginActionUrl" method="post" class="login-form" @submit="handleSubmit">
        <div class="form-group">
          <label for="username">用户名</label>
          <input ref="usernameRef" id="username" name="username" type="text" placeholder="请输入用户名" required
            autocomplete="username" />
        </div>

        <div class="form-group">
          <label for="password">密码</label>
          <input ref="passwordRef" id="password" name="password" type="password" placeholder="请输入密码" required
            autocomplete="current-password" />
        </div>

        <!-- 认证参数 -->
        <input type="hidden" name="authenticationProvider" value="LOCAL" />
        <input type="hidden" name="authenticationType" value="PASSWORD" />

        <button type="submit" class="login-button" :class="{ loading: isSubmitting }" :disabled="isSubmitting">
          {{ isSubmitting ? '登录中...' : '登录' }}
        </button>
      </form>

      <div class="footer">
        <p>© 2024 OAuth2 Server. All rights reserved.</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

defineOptions({
  name: 'LoginPage',
})

const route = useRoute()
const isSubmitting = ref(false)
const formRef = ref<HTMLFormElement | null>(null)
const usernameRef = ref<HTMLInputElement | null>(null)
const passwordRef = ref<HTMLInputElement | null>(null)

const defaultCredentials = {
  username: 'admin',
  password: 'admin',
}

// 获取后端 API 基础 URL，如果没有配置则使用默认值
const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:9000'
// 确保 URL 以 / 结尾
const baseUrl = apiBaseUrl.endsWith('/') ? apiBaseUrl.slice(0, -1) : apiBaseUrl
// 构建登录表单提交的完整 URL
const loginActionUrl = `${baseUrl}/login`

const errorText = computed(() => {
  const raw = route.query.error ?? route.query.message
  if (!raw) return ''
  if (Array.isArray(raw)) return raw[0] ? String(raw[0]) : ''
  return String(raw)
})

const handleSubmit = () => {
  isSubmitting.value = true
}

onMounted(() => {
  if (usernameRef.value) {
    usernameRef.value.focus()
    usernameRef.value.value = defaultCredentials.username
  }
  if (passwordRef.value) {
    passwordRef.value.value = defaultCredentials.password
  }
})
</script>

<style scoped>
.login-page {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  padding: 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  /* 固定为亮色主题，避免系统暗黑模式自动反色导致输入文字过浅 */
  color-scheme: light;
}

.login-container {
  width: 100%;
  max-width: 400px;
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.1);
  padding: 40px;
}

.login-header {
  text-align: center;
  margin-bottom: 30px;
}

.login-header h1 {
  font-size: 28px;
  color: #333;
  margin-bottom: 8px;
}

.login-header p {
  font-size: 14px;
  color: #666;
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  font-size: 14px;
  color: #333;
  font-weight: 500;
  margin-bottom: 8px;
}

.form-group input {
  width: 100%;
  padding: 12px 15px;
  border: 1px solid #ddd;
  border-radius: 6px;
  font-size: 14px;
  transition: border-color 0.3s;
  /* 确保在深色模式下文字和背景对比足够清晰 */
  color: #222;
  background-color: #fff;
  caret-color: #222;
}

.form-group input::placeholder {
  color: #999;
  opacity: 1;
}

.form-group input:focus {
  outline: none;
  border-color: #667eea;
}

.error-message {
  background: #fee;
  color: #c33;
  padding: 12px;
  border-radius: 6px;
  margin-bottom: 20px;
  font-size: 14px;
}

.login-button {
  width: 100%;
  padding: 12px;
  border: none;
  border-radius: 6px;
  font-size: 16px;
  font-weight: 500;
  cursor: pointer;
  color: #fff;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  transition: opacity 0.3s;
}

.login-button:hover {
  opacity: 0.9;
}

.login-button.loading {
  pointer-events: none;
  opacity: 0.6;
}

.footer {
  text-align: center;
  margin-top: 20px;
  font-size: 12px;
  color: #999;
}
</style>
