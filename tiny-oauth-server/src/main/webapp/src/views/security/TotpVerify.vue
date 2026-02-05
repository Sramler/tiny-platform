<template>
  <div class="page">
    <div class="card">
      <div class="card__header">
        <h1>两步验证</h1>
        <p>请输入您的动态验证码以继续</p>
        <p class="card__tips">
          从您的身份验证器 App（谷歌验证器、iPhone 密码 App 等）获取 6 位动态码
        </p>
      </div>

      <div v-if="errorText" class="error-message">{{ errorText }}</div>

      <form
        class="form"
        :action="checkFormActionUrl"
        method="post"
        @submit="handleSubmit"
      >
        <div class="form-group">
          <label for="totpCode">动态验证码</label>
          <input
            id="totpCode"
            name="totpCode"
            type="text"
            maxlength="6"
            placeholder="输入 6 位动态码"
            required
            autocomplete="one-time-code"
          />
        </div>
        <input type="hidden" name="redirect" :value="redirectParam" />
        <button type="submit" class="btn" :class="{ loading: isSubmitting }" :disabled="isSubmitting">
          {{ isSubmitting ? '确认中...' : '确认' }}
        </button>
      </form>

      <div class="footer">
        <p>© 2024 OAuth2 Server. All rights reserved。</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const isSubmitting = ref(false)

// 获取后端 API 基础 URL，如果没有配置则使用默认值
const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:9000'
// 确保 URL 以 / 结尾
const baseUrl = apiBaseUrl.endsWith('/') ? apiBaseUrl.slice(0, -1) : apiBaseUrl
// 构建表单提交的完整 URL
const checkFormActionUrl = `${baseUrl}/self/security/totp/check-form`

const redirectParam = computed(() => {
  const value = route.query.redirect
  const raw = Array.isArray(value) ? value[0] ?? '/' : value ?? '/'
  const str = String(raw)
  try {
    return decodeURIComponent(str)
  } catch (error) {
    console.warn('无法解码 redirect 参数:', error)
    return str
  }
})

const errorText = computed(() => {
  const value = route.query.error ?? route.query.message
  if (!value) return ''
  if (Array.isArray(value)) return value[0] ? String(value[0]) : ''
  return String(value)
})

const handleSubmit = () => {
  isSubmitting.value = true
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  /* 固定为亮色主题，避免系统暗黑模式自动反色导致输入文字过浅 */
  color-scheme: light;
}

.card {
  width: 100%;
  max-width: 420px;
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.1);
  padding: 40px;
}

.card__header {
  text-align: center;
  margin-bottom: 30px;
}

.card__header h1 {
  font-size: 28px;
  color: #333;
  margin-bottom: 8px;
}

.card__header p {
  font-size: 14px;
  color: #666;
}

.card__tips {
  font-size: 12px;
  color: #888;
  margin-top: 8px;
  line-height: 1.6;
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
}

/* 确保在深色模式下文字和背景对比足够清晰 */
.form-group input {
  color: #222;
  background-color: #fff;
  caret-color: #222;
}

.form-group input:focus {
  outline: none;
  border-color: #667eea;
}

.form-group input::placeholder {
  color: #999;
  opacity: 1;
}

.btn {
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

.btn:hover {
  opacity: 0.9;
}

.btn.loading {
  pointer-events: none;
  opacity: 0.6;
}

.error-message {
  background: #fee;
  color: #c33;
  padding: 12px;
  border-radius: 6px;
  margin-bottom: 20px;
  font-size: 14px;
}

.footer {
  margin-top: 20px;
  text-align: center;
  font-size: 12px;
  color: #999;
}
</style>
