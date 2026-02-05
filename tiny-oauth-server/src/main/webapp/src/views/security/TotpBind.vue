<template>
  <div class="page">
    <div v-if="!disableMfa" class="card">
      <div class="card__header">
        <h1>开启两步验证</h1>
        <p>为账号添加一个安全层，提升账号安全保障</p>
        <p class="card__tips">
          • 使用谷歌验证器、iPhone 密码 App、Microsoft Authenticator 扫码绑定<br />
          • 开启后将显著提升登录与敏感操作的安全级别<br />
          • 您已登录，绑定 TOTP 时仅需输入动态验证码即可
        </p>
      </div>

      <div v-if="errorText" class="error-message">{{ errorText }}</div>
      <div v-else-if="loadError" class="error-message">{{ loadError }}</div>

      <div v-if="!loadError" class="qrcode-wrapper">
        <img v-if="qrcodeUrl" :src="qrcodeUrl" alt="二维码" class="qrcode-img" />
        <div v-else class="qrcode-img qrcode-img--loading">二维码加载中…</div>
      </div>

      <div v-if="secretKey" class="secret-row">
        <span>密钥：</span>
        <span class="secret-value">{{ secretKey }}</span>
      </div>

      <form
        ref="bindFormRef"
        class="form"
        :action="bindFormActionUrl"
        method="post"
        @submit="handleBindSubmit"
      >
        <div class="form-group">
          <label for="totpCode">验证码</label>
          <input
            id="totpCode"
            name="totpCode"
            type="text"
            maxlength="6"
            placeholder="输入 6 位动态码"
            required
            autocomplete="one-time-code"
          />
          <p class="hint">请从您的验证器 App 中输入 6 位动态验证码</p>
        </div>
        <input type="hidden" name="redirect" :value="redirectParam" />
        <button
          type="submit"
          class="btn"
          :class="{ loading: isBinding }"
          :disabled="isBinding || !!loadError"
        >
          {{ isBinding ? '确认中...' : '确认绑定' }}
        </button>
      </form>

      <form
        v-if="!forceMfa"
        class="form form--secondary"
        :action="skipFormActionUrl"
        method="post"
        @submit="handleSkipSubmit"
      >
        <input type="hidden" name="redirect" :value="redirectParam" />
        <button type="submit" class="btn btn--secondary" :class="{ loading: isSkipping }" :disabled="isSkipping">
          {{ isSkipping ? '正在跳过...' : '跳过' }}
        </button>
      </form>

      <div class="footer">
        <p>© 2024 OAuth2 Server. All rights reserved.</p>
      </div>
    </div>

    <div v-else class="card">
      <div class="card__header">
        <h1>两步验证已禁用</h1>
        <p>当前系统已禁用 MFA，无需进行绑定操作。</p>
      </div>
      <div class="footer">
        <p>© 2024 OAuth2 Server. All rights reserved。</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

// 获取后端 API 基础 URL，如果没有配置则使用默认值
const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:9000'
// 确保 URL 以 / 结尾
const baseUrl = apiBaseUrl.endsWith('/') ? apiBaseUrl.slice(0, -1) : apiBaseUrl
// 构建表单提交的完整 URL
const bindFormActionUrl = `${baseUrl}/self/security/totp/bind-form`
const skipFormActionUrl = `${baseUrl}/self/security/totp/skip`

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

const bindFormRef = ref<HTMLFormElement | null>(null)
const isBinding = ref(false)
const isSkipping = ref(false)
const disableMfa = ref(false)
const forceMfa = ref(false)
const secretKey = ref('')
const qrcodeUrl = ref('')
const loadError = ref('')

const handleBindSubmit = () => {
  isBinding.value = true
}

const handleSkipSubmit = () => {
  isSkipping.value = true
}

onMounted(async () => {
  await Promise.all([fetchSecurityStatus(), fetchTotpInfo()])
})

async function fetchSecurityStatus() {
  try {
    const { fetchWithTraceId } = await import('@/utils/traceId')
    const response = await fetchWithTraceId(`${baseUrl}/self/security/status`, {
      method: 'GET',
      credentials: 'include',
      headers: { Accept: 'application/json' },
    })
    if (!response.ok) {
      throw new Error('无法获取安全状态')
    }
    const data = await response.json()
    disableMfa.value = Boolean(data.disableMfa)
    forceMfa.value = Boolean(data.forceMfa)
  } catch (error) {
    console.error('获取安全状态失败:', error)
    loadError.value = '无法获取安全状态，请稍后重试'
  }
}

async function fetchTotpInfo() {
  try {
    const { fetchWithTraceId } = await import('@/utils/traceId')
    const response = await fetchWithTraceId(`${baseUrl}/self/security/totp/pre-bind`, {
      method: 'GET',
      credentials: 'include',
      headers: { Accept: 'application/json' },
    })
    if (!response.ok) {
      throw new Error('无法获取 TOTP 信息')
    }
    const data = await response.json()
    if (!data.success) {
      loadError.value = data.error || '无法获取 TOTP 信息'
      return
    }
    secretKey.value = data.secretKey || ''
    qrcodeUrl.value = data.qrCodeDataUrl || ''
  } catch (error) {
    console.error('获取 TOTP 信息失败:', error)
    loadError.value = '无法加载绑定信息，请稍后重试'
  }
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

.qrcode-wrapper {
  text-align: center;
  margin-bottom: 20px;
}

.qrcode-img {
  display: block;
  width: 160px;
  height: 160px;
  margin: 0 auto 20px;
  border-radius: 10px;
  box-shadow: 0 1px 8px #e6e6e6;
  background: #fff;
}

.qrcode-img--loading {
  display: flex;
  justify-content: center;
  align-items: center;
  color: #888;
  font-size: 14px;
}

.secret-row {
  text-align: center;
  margin-bottom: 16px;
}

.secret-value {
  display: inline-block;
  margin-left: 6px;
  padding: 5px 10px;
  border-radius: 6px;
  background: #faf7ff;
  color: #333;
  font-family: monospace;
  font-size: 17px;
}

.form {
  margin-bottom: 12px;
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

.hint {
  font-size: 12px;
  color: #888;
  margin-top: 4px;
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

.btn--secondary {
  background: #9aa4b1;
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
