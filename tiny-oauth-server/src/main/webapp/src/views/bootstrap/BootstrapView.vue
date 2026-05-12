<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useBootState } from '@/bootstrap/bootState'
import { cancelAppBootstrap, runAppBootstrap } from '@/bootstrap/appBootstrap'
import { normalizeBootstrapRedirect } from '@/router/routePolicy'

const route = useRoute()
const router = useRouter()
const boot = useBootState()
const running = ref(false)
let localRunSerial = 0

const redirectTarget = computed(() => normalizeBootstrapRedirect(route.query.redirect, '/'))
const canRetry = computed(() => boot.status === 'error' && !running.value)

const stageLabels: Record<string, string> = {
  idle: '准备启动',
  checking_auth: '检查登录状态',
  restoring_session: '恢复登录态',
  checking_security: '检查安全状态',
  loading_permission: '加载菜单权限',
  registering_routes: '注册动态路由',
  redirecting: '进入目标页面',
  ready: '启动完成',
  error: '启动失败',
}

function formatStageLabel(status: string) {
  return stageLabels[status] || status
}

async function startBootstrap() {
  const serial = ++localRunSerial
  running.value = true
  try {
    await runAppBootstrap(router, redirectTarget.value)
  } finally {
    if (serial === localRunSerial) {
      running.value = false
    }
  }
}

function retry() {
  cancelAppBootstrap()
  startBootstrap()
}

function goLogin() {
  cancelAppBootstrap()
  router.replace({
    path: '/login',
    query: { redirect: redirectTarget.value },
  })
}

onMounted(() => {
  startBootstrap()
})

watch(
  () => route.query.redirect,
  () => {
    startBootstrap()
  },
)
</script>

<template>
  <main class="bootstrap-page">
    <section class="bootstrap-card" role="status" aria-live="polite">
      <div class="bootstrap-orbit" aria-hidden="true">
        <span />
        <span />
      </div>
      <p class="bootstrap-kicker">Tiny Platform</p>
      <h1>{{ boot.message || '正在进入系统' }}</h1>
      <p class="bootstrap-detail">
        {{ boot.detail || '正在准备运行环境，请稍候' }}
      </p>
      <p class="bootstrap-meta">启动批次 #{{ boot.runId }} · 目标 {{ redirectTarget }}</p>

      <div class="bootstrap-progress">
        <div
          v-for="stage in boot.stages"
          :key="`${stage.status}-${stage.startedAt}`"
          class="bootstrap-progress__item"
          :class="{ finished: !!stage.endedAt }"
        >
          <span class="bootstrap-progress__dot" />
          <span>{{ formatStageLabel(stage.status) }}</span>
          <em v-if="stage.durationMs">{{ stage.durationMs }}ms</em>
        </div>
      </div>

      <div v-if="boot.error" class="bootstrap-error">
        <strong>{{ boot.error.code }}</strong>
        <p>{{ boot.error.message }}</p>
        <p v-if="boot.error.detail">{{ boot.error.detail }}</p>
      </div>

      <div v-if="boot.error" class="bootstrap-actions">
        <button type="button" class="bootstrap-button" :disabled="!canRetry" @click="retry">
          重新尝试
        </button>
        <button type="button" class="bootstrap-button bootstrap-button--ghost" @click="goLogin">
          返回登录
        </button>
      </div>
    </section>
  </main>
</template>

<style scoped>
.bootstrap-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
  background:
    radial-gradient(circle at 18% 20%, rgba(24, 119, 242, 0.18), transparent 30%),
    radial-gradient(circle at 82% 80%, rgba(0, 168, 132, 0.16), transparent 32%),
    linear-gradient(135deg, #f7fbff 0%, #eef6f2 100%);
  color: #17324d;
}

.bootstrap-card {
  width: min(520px, 100%);
  padding: 42px 38px;
  border: 1px solid rgba(23, 50, 77, 0.08);
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 28px 80px rgba(26, 55, 84, 0.16);
  text-align: center;
  backdrop-filter: blur(18px);
}

.bootstrap-orbit {
  position: relative;
  width: 74px;
  height: 74px;
  margin: 0 auto 22px;
  border-radius: 999px;
  background: linear-gradient(135deg, #1877f2, #00a884);
  box-shadow: 0 18px 34px rgba(24, 119, 242, 0.22);
}

.bootstrap-orbit::before {
  content: '';
  position: absolute;
  inset: 14px;
  border-radius: inherit;
  background: #fff;
}

.bootstrap-orbit span {
  position: absolute;
  inset: -7px;
  border: 2px solid rgba(24, 119, 242, 0.35);
  border-left-color: transparent;
  border-radius: inherit;
  animation: bootstrap-spin 1.35s linear infinite;
}

.bootstrap-orbit span + span {
  inset: 8px;
  border-color: rgba(0, 168, 132, 0.35);
  border-right-color: transparent;
  animation-duration: 1.8s;
  animation-direction: reverse;
}

.bootstrap-kicker {
  margin: 0 0 10px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.18em;
  color: #29735f;
  text-transform: uppercase;
}

.bootstrap-card h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 750;
  color: #14314f;
}

.bootstrap-detail {
  margin: 12px 0 0;
  font-size: 14px;
  line-height: 1.8;
  color: #637487;
}

.bootstrap-meta {
  margin: 8px 0 0;
  color: #8a98a8;
  font-size: 12px;
  word-break: break-all;
}

.bootstrap-progress {
  display: grid;
  gap: 8px;
  margin-top: 26px;
  text-align: left;
}

.bootstrap-progress__item {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #6a7a8c;
  font-size: 13px;
}

.bootstrap-progress__item.finished {
  color: #28755f;
}

.bootstrap-progress__dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: currentColor;
}

.bootstrap-progress__item em {
  margin-left: auto;
  font-style: normal;
  color: #90a0af;
}

.bootstrap-error {
  margin-top: 22px;
  padding: 14px 16px;
  border: 1px solid #ffd7d7;
  border-radius: 14px;
  background: #fff5f5;
  color: #9f2a2a;
  text-align: left;
}

.bootstrap-error p {
  margin: 6px 0 0;
}

.bootstrap-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-top: 20px;
}

.bootstrap-button {
  border: 0;
  border-radius: 999px;
  padding: 9px 18px;
  background: #1877f2;
  color: #fff;
  cursor: pointer;
}

.bootstrap-button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.bootstrap-button--ghost {
  background: #edf3f9;
  color: #17324d;
}

@keyframes bootstrap-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
