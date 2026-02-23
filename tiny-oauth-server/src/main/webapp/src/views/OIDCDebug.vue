<template>
  <div class="debug-container">
    <div class="debug-content">
      <h1>OIDC 调试工具</h1>

      <StatusSection :is-authenticated="isAuthenticated" :user-info="userInfo" :token-expiry="tokenExpiry" />

      <TokenInfoSection :access-token="accessToken" :id-token="idToken" :refresh-token="refreshToken" :scopes="scopes"
        :session-info="sessionInfo" :decoded-access-token="decodedAccessToken" :decoded-id-token="decodedIdToken" />

      <UrlParamsSection :params="urlParams" />

      <div class="responsive-grid">
        <StorageSection :local-storage-count="localStorageCount" :session-storage-count="sessionStorageCount"
          :oidc-keys="oidcKeys" />
        <EnvironmentSection :user-claims="userClaims" :environment-info="environmentInfo" />
      </div>

      <ActionSection :refreshing="refreshing" :clearing="clearing" @refresh="refreshStatus" @clear-cache="clearCache"
        @force-logout="forceLogout" @go-login="goToLogin" @go-home="goHome" />

      <LogSection :logs="logs" @clear="clearLogs" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useAuth } from '@/auth/auth'
import { userManager } from '@/auth/oidc'
import { clearOidcCache, isInOidcFlow } from '@/utils/auth-utils'
import StatusSection from './OIDCDebug/components/StatusSection.vue'
import TokenInfoSection from './OIDCDebug/components/TokenInfoSection.vue'
import UrlParamsSection from './OIDCDebug/components/UrlParamsSection.vue'
import StorageSection from './OIDCDebug/components/StorageSection.vue'
import EnvironmentSection from './OIDCDebug/components/EnvironmentSection.vue'
import ActionSection from './OIDCDebug/components/ActionSection.vue'
import LogSection from './OIDCDebug/components/LogSection.vue'

const router = useRouter()
const { user, isAuthenticated } = useAuth()

type DebugLog = { time: string; level: string; message: string }

const refreshing = ref(false)
const clearing = ref(false)
const logs = ref<DebugLog[]>([])

const userInfo = computed(() => {
  if (!user.value) return '无用户信息'
  return `${user.value.profile?.name || user.value.profile?.preferred_username || '未知用户'} (${user.value.profile?.sub || '无ID'})`
})

const tokenExpiry = computed(() => {
  if (!user.value || !user.value.expires_at) return '无过期时间'
  const expiry = new Date(user.value.expires_at * 1000)
  const now = new Date()
  const diff = expiry.getTime() - now.getTime()
  const minutes = Math.floor(diff / 60000)
  return `${expiry.toLocaleString()} (${minutes > 0 ? `${minutes}分钟后过期` : '已过期'})`
})

const urlParams = computed(() => {
  const params = new URLSearchParams(window.location.search)
  const result: Record<string, string> = {}
  params.forEach((value, key) => {
    result[key] = value
  })
  return result
})

const localStorageCount = computed(() => localStorage.length)
const sessionStorageCount = computed(() => sessionStorage.length)

const oidcKeys = computed(() => {
  const keys: string[] = []
  for (let i = 0; i < localStorage.length; i++) {
    const key = localStorage.key(i)
    if (key && (key.includes('oidc') || key.includes('user') || key.includes('auth'))) {
      keys.push(key)
    }
  }
  return keys
})

const accessToken = computed(() => user.value?.access_token || '')
const idToken = computed(() => user.value?.id_token || '')
const refreshToken = computed(() => user.value?.refresh_token || '')
const scopes = computed(() => (user.value?.scopes?.length ? user.value.scopes.join(', ') : '无'))

const sessionInfo = computed(() => ({
  tokenType: user.value?.token_type || 'unknown',
  sessionState: user.value?.session_state || 'unknown',
}))

const userClaims = computed(() => user.value?.profile || {})

const decodedAccessToken = computed(() => decodeJwt(accessToken.value))
const decodedIdToken = computed(() => decodeJwt(idToken.value))

const environmentInfo = computed(() => ({
  origin: window.location.origin,
  href: window.location.href,
  userAgent: navigator.userAgent,
  online: navigator.onLine,
  cookieSize: document.cookie.length,
}))

function decodeJwt(token?: string | null) {
  if (!token) return null
  const parts = token.split('.')
  if (parts.length !== 3 || !parts[1]) return null
  try {
    const payload = atob(parts[1].replace(/-/g, '+').replace(/_/g, '/'))
    return JSON.parse(decodeURIComponent(escape(payload)))
  } catch {
    return null
  }
}

function addLog(level: string, message: string) {
  logs.value.unshift({
    time: new Date().toLocaleTimeString(),
    level,
    message,
  })
  if (logs.value.length > 50) {
    logs.value = logs.value.slice(0, 50)
  }
}

async function refreshStatus() {
  refreshing.value = true
  try {
    addLog('info', '刷新认证状态...')
    const currentUser = await userManager.getUser()
    addLog('info', `当前用户状态: ${currentUser ? '已登录' : '未登录'}`)
    if (currentUser) {
      addLog('info', `用户ID: ${currentUser.profile?.sub}`)
    }
    message.success('状态刷新完成')
  } catch (error) {
    addLog('error', `刷新状态失败: ${error}`)
    message.error('刷新状态失败')
  } finally {
    refreshing.value = false
  }
}

async function clearCache() {
  clearing.value = true
  try {
    addLog('info', '开始清理缓存...')
    const success = await clearOidcCache()
    if (success) {
      addLog('success', '缓存清理成功')
      message.success('缓存清理成功')
    } else {
      addLog('error', '缓存清理失败')
      message.error('缓存清理失败')
    }
  } catch (error) {
    addLog('error', `清理缓存异常: ${error}`)
    message.error('清理缓存异常')
  } finally {
    clearing.value = false
  }
}

async function forceLogout() {
  try {
    addLog('info', '执行强制登出...')
    await userManager.removeUser()
    addLog('success', '强制登出成功')
    message.success('强制登出成功')
    router.push('/login')
  } catch (error) {
    addLog('error', `强制登出失败: ${error}`)
    message.error('强制登出失败')
  }
}

function goToLogin() {
  addLog('info', '跳转到登录页')
  router.push('/login')
}

function goHome() {
  addLog('info', '返回首页')
  router.push('/')
}

function clearLogs() {
  logs.value = []
  addLog('info', '日志已清空')
}

onMounted(() => {
  addLog('info', '调试页面加载完成')
  addLog('info', `当前路径: ${window.location.pathname}`)
  addLog('info', `是否在 OIDC 流程中: ${isInOidcFlow()}`)
  addLog('info', `认证状态: ${isAuthenticated.value}`)
})
</script>

<style scoped>
.debug-container {
  padding: 24px;
  max-width: 1200px;
  margin: 0 auto;
}

.debug-content h1 {
  color: #1890ff;
  margin-bottom: 24px;
  text-align: center;
}

.responsive-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 16px;
}

:deep(.debug-section) {
  margin-bottom: 32px;
  padding: 20px;
  border: 1px solid #d9d9d9;
  border-radius: 8px;
  background: #fafafa;
}

:deep(.debug-section h2) {
  color: #262626;
  margin-bottom: 16px;
  font-size: 18px;
}

:deep(.status-grid) {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 12px;
}

:deep(.status-item) {
  display: flex;
  justify-content: space-between;
  padding: 8px 12px;
  background: white;
  border-radius: 4px;
  border: 1px solid #e8e8e8;
}

:deep(.label) {
  font-weight: 500;
  color: #595959;
}

:deep(.value) {
  color: #262626;
}

:deep(.value.success) {
  color: #52c41a;
}

:deep(.value.error) {
  color: #f5222d;
}

:deep(.url-params) {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 8px;
}

:deep(.param-item) {
  display: flex;
  justify-content: space-between;
  padding: 6px 10px;
  background: white;
  border-radius: 4px;
  border: 1px solid #e8e8e8;
}

:deep(.param-key) {
  font-weight: 500;
  color: #1890ff;
}

:deep(.param-value) {
  color: #262626;
  word-break: break-all;
}

:deep(.storage-info) {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

:deep(.storage-item) {
  display: flex;
  justify-content: space-between;
  padding: 8px 12px;
  background: white;
  border-radius: 4px;
  border: 1px solid #e8e8e8;
}

:deep(.storage-keys h3) {
  margin-bottom: 8px;
  color: #595959;
}

:deep(.storage-key) {
  padding: 4px 8px;
  margin-bottom: 4px;
  background: white;
  border-radius: 4px;
  border: 1px solid #e8e8e8;
  font-family: monospace;
  font-size: 12px;
  color: #262626;
}

:deep(.action-buttons) {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

:deep(.log-container) {
  max-height: 300px;
  overflow-y: auto;
  border: 1px solid #e8e8e8;
  border-radius: 4px;
  background: white;
  padding: 12px;
  margin-bottom: 12px;
}

:deep(.log-item) {
  display: flex;
  gap: 8px;
  margin-bottom: 4px;
  font-family: monospace;
  font-size: 12px;
}

:deep(.log-time) {
  color: #8c8c8c;
  min-width: 80px;
}

:deep(.log-level) {
  min-width: 60px;
  font-weight: 500;
}

:deep(.log-level.info) {
  color: #1890ff;
}

:deep(.log-level.success) {
  color: #52c41a;
}

:deep(.log-level.error) {
  color: #f5222d;
}

:deep(.token-grid) {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 12px;
}

:deep(.token-item) {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px;
  border: 1px solid #e8e8e8;
  border-radius: 6px;
  background: #fff;
}

:deep(.token-label) {
  font-weight: 600;
  color: #1890ff;
}

:deep(.token-value) {
  font-family: monospace;
  font-size: 12px;
  word-break: break-all;
  color: #262626;
}

:deep(.token-action) {
  font-size: 12px;
}

:deep(.token-json) {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 16px;
  margin-top: 16px;
}

:deep(.json-viewer) {
  min-height: 140px;
  max-height: 240px;
  overflow: auto;
  font-family: monospace;
  font-size: 12px;
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 12px;
  border-radius: 6px;
}

:deep(.env-grid) {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

:deep(.env-item) {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 8px 10px;
  border: 1px solid #e8e8e8;
  border-radius: 4px;
  background: #fff;
}

:deep(.log-message) {
  color: #262626;
  flex: 1;
}

:deep(.log-empty) {
  text-align: center;
  color: #8c8c8c;
  font-size: 12px;
  padding: 12px 0;
}
</style>
