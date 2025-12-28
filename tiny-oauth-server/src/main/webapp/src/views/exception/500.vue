<template>
  <div class="error-page">
    <a-card class="error-card" :bordered="false">
      <div class="error-content">
        <div class="error-icon-wrapper">
          <ThunderboltOutlined class="error-icon" />
        </div>
        <h1 class="error-code">500</h1>
        <h2 class="error-title">服务器错误</h2>
        <p class="error-description">抱歉，服务器发生了错误。我们正在努力修复这个问题，请稍后再试。如果问题持续存在，请联系技术支持。</p>
        
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
          <a-button type="primary" size="large" @click="goHome">
            <template #icon>
              <HomeOutlined />
            </template>
            返回首页
          </a-button>
          <a-button v-if="errorInfo.from" size="large" @click="goBack">
            <template #icon>
              <ArrowLeftOutlined />
            </template>
            返回上一页
          </a-button>
        </div>
      </div>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ThunderboltOutlined, HomeOutlined, ArrowLeftOutlined, InfoCircleOutlined } from '@ant-design/icons-vue'

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

const goHome = () => {
  // 触发关闭当前标签页事件
  window.dispatchEvent(new CustomEvent('close-current-tab'))
  // 跳转到首页
  router.push('/')
}

const goBack = () => {
  if (errorInfo.value.from) {
    window.location.href = errorInfo.value.from
  } else {
    router.go(-1)
  }
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
  color: #d4380d;
  animation: shake 2s ease-in-out infinite;
}

@keyframes shake {
  0%, 100% {
    transform: rotate(0deg);
  }
  10%, 30%, 50%, 70%, 90% {
    transform: rotate(-5deg);
  }
  20%, 40%, 60%, 80% {
    transform: rotate(5deg);
  }
}

.error-code {
  font-size: 96px;
  font-weight: 700;
  line-height: 1;
  margin: 0 0 16px;
  background: linear-gradient(135deg, #d4380d 0%, #ff7a45 100%);
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
  margin: 0 0 32px;
}

.error-details {
  margin: 24px 0;
  text-align: left;
}

.error-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-top: 24px;
}

:deep(.ant-btn-primary) {
  height: 44px;
  padding: 0 32px;
  font-size: 16px;
  border-radius: 6px;
  box-shadow: 0 2px 4px rgba(212, 56, 13, 0.2);
  background: linear-gradient(135deg, #d4380d 0%, #ff7a45 100%);
  border: none;
}

:deep(.ant-btn-primary:hover) {
  box-shadow: 0 4px 8px rgba(212, 56, 13, 0.3);
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
}
</style>

