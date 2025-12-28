<!-- eslint-disable vue/multi-word-component-names -->
<template>
  <div class="error-page">
    <a-card class="error-card" :bordered="false">
      <div class="error-content">
        <div class="error-icon-wrapper">
          <LockOutlined class="error-icon" />
        </div>
        <h1 class="error-code">401</h1>
        <h2 class="error-title">登录状态已失效</h2>
        <p class="error-description">
          您的登录会话已过期或已失效，为了您的账户安全，请重新登录。
        </p>
        <!-- 倒计时提示 -->
        <div v-if="countdown > 0" class="countdown-tip">
          <a-alert :message="`${countdown} 秒后自动跳转到登录页面`" type="info" show-icon :closable="false"
            style="margin-bottom: 24px;" />
        </div>
        
        <!-- 错误详情信息 -->
        <div v-if="errorInfo.from || errorInfo.path || errorInfo.message || errorInfo.traceId" class="error-details">
          <a-divider>错误详情</a-divider>
          <a-descriptions :column="1" bordered size="small">
            <a-descriptions-item v-if="errorInfo.from" label="来源页面">
              <a-typography-text copyable>{{ errorInfo.from }}</a-typography-text>
            </a-descriptions-item>
            <a-descriptions-item v-if="errorInfo.path" label="访问路径">
              <a-typography-text copyable>{{ errorInfo.path }}</a-typography-text>
            </a-descriptions-item>
            <a-descriptions-item v-if="errorInfo.message" label="错误信息">
              <a-typography-text type="danger" copyable>{{ errorInfo.message }}</a-typography-text>
            </a-descriptions-item>
            <a-descriptions-item v-if="errorInfo.traceId" label="追踪ID">
              <a-typography-text copyable code>{{ errorInfo.traceId }}</a-typography-text>
              <a-tooltip title="用于追踪本次请求的日志，便于排查问题">
                <InfoCircleOutlined style="margin-left: 8px; color: #1890ff; cursor: help;" />
              </a-tooltip>
            </a-descriptions-item>
          </a-descriptions>
        </div>
        
        <div class="error-actions">
          <a-button type="primary" size="large" @click="goLogin">
            <template #icon>
              <LoginOutlined />
            </template>
            立即登录
          </a-button>
          <a-button size="large" @click="goHome">
            <template #icon>
              <HomeOutlined />
            </template>
            返回首页
          </a-button>
        </div>
      </div>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { LockOutlined, LoginOutlined, HomeOutlined, InfoCircleOutlined } from '@ant-design/icons-vue'

const router = useRouter()
const route = useRoute()

// 从路由参数中获取错误信息
const errorInfo = computed(() => {
  const query = route.query
  return {
    from: (query.from as string) || document.referrer || null,
    path: (query.path as string) || route.path || null,
    message: (query.message as string) || null,
    traceId: (query.traceId as string) || null,
  }
})

// 倒计时相关
const countdown = ref(5) // 5秒倒计时
let countdownTimer: number | null = null

// 开始倒计时
const startCountdown = () => {
  countdownTimer = window.setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) {
      stopCountdown()
      goLogin()
    }
  }, 1000)
}

// 停止倒计时
const stopCountdown = () => {
  if (countdownTimer !== null) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
}

onMounted(async () => {
  // ⚠️ 重要：在 401 页面加载后，清理认证状态
  // 这样可以避免 logout() 的跳转覆盖 401 页面的显示
  try {
    const { userManager } = await import('@/auth/oidc')
    const { useAuth } = await import('@/auth/auth')
    const { user } = useAuth()

    // 只清除本地状态，不触发 OIDC 登出重定向
    // 这样可以避免 logout() 的 window.location.href 覆盖当前页面
    if (user.value) {
      console.log('[401] 清除本地认证状态')
      await userManager.removeUser()
      // user.value 是响应式的，会在 useAuth 中自动更新
    }
  } catch (error) {
    console.error('[401] 清理认证状态失败:', error)
  }

  // 开始倒计时
  startCountdown()
})

onUnmounted(() => {
  // 组件卸载时清除倒计时
  stopCountdown()
})

const goLogin = async () => {
  // 停止倒计时
  stopCountdown()

  // 跳转到登录页（认证状态已在 onMounted 中清理）
  router.push('/login')
}

const goHome = () => {
  // 停止倒计时
  stopCountdown()

  // 触发关闭当前标签页事件
  window.dispatchEvent(new CustomEvent('close-current-tab'))
  // 跳转到首页
  router.push('/')
}
</script>

<style scoped>
.error-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: calc(100vh - 200px);
  padding: 24px;
  background: #f0f2f5;
}

.error-card {
  max-width: 600px;
  width: 100%;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  border-radius: 8px;
}

.error-content {
  text-align: center;
  padding: 40px 24px;
}

.error-icon-wrapper {
  margin-bottom: 24px;
  display: flex;
  justify-content: center;
}

.error-icon {
  font-size: 80px;
  color: #1890ff;
  animation: float 3s ease-in-out infinite;
}

@keyframes float {

  0%,
  100% {
    transform: translateY(0px);
  }

  50% {
    transform: translateY(-10px);
  }
}

.error-code {
  font-size: 96px;
  font-weight: 700;
  line-height: 1;
  margin: 0 0 16px;
  background: linear-gradient(135deg, #1890ff 0%, #40a9ff 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.error-title {
  font-size: 24px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.85);
  margin: 0 0 16px;
}

.error-description {
  font-size: 16px;
  color: rgba(0, 0, 0, 0.65);
  line-height: 1.6;
  margin: 0 0 24px;
}

.countdown-tip {
  margin-bottom: 24px;
}

.countdown-tip :deep(.ant-alert-message) {
  font-size: 14px;
  font-weight: 500;
}

.error-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
}

:deep(.ant-btn-primary) {
  height: 44px;
  padding: 0 32px;
  font-size: 16px;
  border-radius: 6px;
  box-shadow: 0 2px 4px rgba(24, 144, 255, 0.2);
}

:deep(.ant-btn-primary:hover) {
  box-shadow: 0 4px 8px rgba(24, 144, 255, 0.3);
  transform: translateY(-1px);
  transition: all 0.3s ease;
}

@media (max-width: 768px) {
  .error-code {
    font-size: 64px;
  }

  .error-icon {
    font-size: 60px;
  }

  .error-title {
    font-size: 20px;
  }

  .error-description {
    font-size: 14px;
  }

  .error-content {
    padding: 32px 16px;
  }

  .error-actions {
    flex-direction: column;
  }

  :deep(.ant-btn) {
    width: 100%;
  }
}
</style>
