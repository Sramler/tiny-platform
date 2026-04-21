<template>
  <div class="login-page">
    <div class="login-container">
      <div class="login-header">
        <h1>欢迎登录</h1>
        <p>请输入您的账号信息</p>
      </div>

      <div v-if="errorText || errorMessage" class="error-message">{{ errorText || errorMessage }}</div>

      <form ref="formRef" :action="loginActionUrl" method="post" class="login-form" @submit="handleSubmit">
        <div class="form-group">
          <label>登录模式</label>
          <div class="scope-tabs">
            <button
              type="button"
              class="scope-tab"
              :class="{ active: loginMode === 'TENANT' }"
              @click="setLoginMode('TENANT')"
            >
              租户登录
            </button>
            <button
              type="button"
              class="scope-tab"
              :class="{ active: loginMode === 'PLATFORM' }"
              @click="setLoginMode('PLATFORM')"
            >
              平台登录
            </button>
          </div>
        </div>

        <div v-if="loginMode === 'TENANT'" class="form-group">
          <label for="tenantCode">租户编码</label>
          <input
            ref="tenantRef"
            id="tenantCode"
            name="tenantCode"
            type="text"
            placeholder="请输入租户编码（如 tiny-prod）"
            required
            autocomplete="organization"
            maxlength="32"
          />
        </div>

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
        <input type="hidden" name="redirect" :value="redirectParam" />
        <input v-if="csrfParameterName" type="hidden" :name="csrfParameterName" :value="csrfToken" />

        <button type="submit" class="login-button" :class="{ loading: isSubmitting }" :disabled="isSubmitting">
          {{ isSubmitting ? '登录中...' : (loginMode === 'PLATFORM' ? '登录平台' : '登录租户') }}
        </button>
      </form>

      <div class="footer">
        <p>© 2024 OAuth2 Server. All rights reserved.</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ensureCsrfToken } from '@/utils/csrf'
import { sanitizeInternalRedirect } from '@/utils/redirect'
import {
  clearActiveTenantId,
  clearTenantCode,
  getLoginMode,
  getTenantCode,
  isValidTenantCode,
  type LoginMode,
  setLoginMode as persistLoginMode,
  setTenantCode,
} from '@/utils/tenant'

defineOptions({
  name: 'LoginPage',
})

const route = useRoute()
const isSubmitting = ref(false)
const formRef = ref<HTMLFormElement | null>(null)
const tenantRef = ref<HTMLInputElement | null>(null)
const usernameRef = ref<HTMLInputElement | null>(null)
const passwordRef = ref<HTMLInputElement | null>(null)
const errorMessage = ref('')
const csrfToken = ref('')
const csrfParameterName = ref('_csrf')
const loginMode = ref<LoginMode>('TENANT')

/**
 * 仅用于本地/开发联调占位，与后端 seed 对齐：
 * - 租户：`data.sql` 等默认 admin
 * - 平台：`ensure-platform-admin.sh` 创建平台管理员；若存在 `.env.e2e.local` / `E2E_PLATFORM_*`，优先对齐 real-link 平台自动化身份，否则回退到 `platform_admin` / `admin`
 */
const tenantDevDefaults = { username: 'admin', password: 'admin' }
const platformDevDefaults = { username: 'platform_admin', password: 'admin' }

function applyDevDefaultsForMode(mode: 'TENANT' | 'PLATFORM') {
  const creds = mode === 'PLATFORM' ? platformDevDefaults : tenantDevDefaults
  if (usernameRef.value) {
    usernameRef.value.value = creds.username
  }
  if (passwordRef.value) {
    passwordRef.value.value = creds.password
  }
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

const redirectParam = computed(() => {
  const raw = route.query.redirect
  const value = Array.isArray(raw) ? raw[0] ?? '/' : raw ?? '/'
  const normalized = String(value)
  try {
    return sanitizeInternalRedirect(decodeURIComponent(normalized))
  } catch {
    return sanitizeInternalRedirect(normalized)
  }
})

const loadCsrfToken = async () => {
  const csrf = await ensureCsrfToken(baseUrl)
  csrfToken.value = csrf.token
  csrfParameterName.value = csrf.parameterName
}

const setLoginMode = async (mode: LoginMode) => {
  loginMode.value = mode
  persistLoginMode(mode)
  await nextTick()
  // 切回租户模式后 v-if 已挂载输入框，再回填本地记忆的租户编码
  if (mode === 'TENANT') {
    const stored = getTenantCode()
    if (tenantRef.value && stored) {
      tenantRef.value.value = stored
    }
  }
  applyDevDefaultsForMode(mode)
}

const handleSubmit = async (event: Event) => {
  errorMessage.value = ''
  event.preventDefault()
  if (loginMode.value === 'TENANT') {
    const rawTenantCode = tenantRef.value?.value?.trim() ?? ''
    if (!rawTenantCode) {
      errorMessage.value = '请先输入租户编码'
      return
    }
    if (!isValidTenantCode(rawTenantCode)) {
      errorMessage.value = '租户编码格式错误：仅支持小写字母、数字和中划线，长度 2-32'
      return
    }
    const normalizedTenantCode = rawTenantCode.toLowerCase()
    if (tenantRef.value) tenantRef.value.value = normalizedTenantCode
    setTenantCode(normalizedTenantCode)
  } else {
    if (tenantRef.value) tenantRef.value.value = ''
    // 平台登录不携带租户：同步清理本地 tenantCode，避免后续 OIDC/authorize 仍读到历史租户
    clearTenantCode()
  }
  // 登录前清理旧租户ID，避免沿用上一会话租户导致后续链路冲突（平台/租户都需要）
  clearActiveTenantId()

  // 提交前始终同步一次 CSRF：避免 onMounted 竞态导致 ref 仍为空、或缓存与 Cookie 会话不一致。
  // ensureCsrfToken 有模块级缓存，重复调用成本很低。
  try {
    await loadCsrfToken()
  } catch (error) {
    console.error('获取 CSRF token 失败:', error)
    errorMessage.value = '安全校验初始化失败，请刷新页面重试'
    return
  }
  if (!csrfToken.value) {
    errorMessage.value = '安全校验初始化失败，请刷新页面重试'
    return
  }

  // 等待隐藏域与 csrfToken ref 同步，避免原生 form.submit() 读到空的 _csrf。
  await nextTick()

  isSubmitting.value = true
  formRef.value?.submit()
}

onMounted(async () => {
  loadCsrfToken().catch((error) => {
    console.error('初始化 CSRF token 失败:', error)
  })
  const storedMode = getLoginMode()
  if (storedMode) {
    loginMode.value = storedMode
  }
  // 与 v-if 对齐：先等 DOM 更新再操作 tenantRef，避免 PLATFORM 仍短暂持有已卸载的租户输入框引用
  await nextTick()
  if (tenantRef.value && loginMode.value === 'TENANT') {
    const storedTenantCode = getTenantCode()
    if (storedTenantCode) {
      tenantRef.value.value = storedTenantCode
    }
  }
  applyDevDefaultsForMode(loginMode.value)
  if (usernameRef.value) {
    usernameRef.value.focus()
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

.scope-tabs {
  display: flex;
  border: 1px solid #d9d9d9;
  border-radius: 8px;
  overflow: hidden;
}

.scope-tab {
  flex: 1;
  border: none;
  padding: 10px 12px;
  background: #fff;
  color: #666;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.scope-tab + .scope-tab {
  border-left: 1px solid #d9d9d9;
}

.scope-tab.active {
  background: #667eea;
  color: #fff;
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
