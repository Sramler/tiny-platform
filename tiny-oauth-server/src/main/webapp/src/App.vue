<script setup lang="ts">
import { ConfigProvider } from 'ant-design-vue'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import { RouterView } from 'vue-router' // App 入口文件，主布局由路由嵌套控制
import { onMounted, onUnmounted } from 'vue'
import { cancelAppBootstrap } from '@/bootstrap/appBootstrap'
import { resetPermissionBootstrapState } from '@/permission/permissionBootstrap'
import {
  AUTHORIZATION_RUNTIME_RESET_EVENT,
  AUTHORIZATION_RUNTIME_RESET_STORAGE_KEY,
  parseAuthorizationRuntimeResetEvent,
  type AuthorizationRuntimeResetEventDetail,
} from '@/runtime/authorizationRuntimeEvents'

const handledRuntimeResetEvents = new Set<string>()

function resetAuthorizationRuntime(detail?: AuthorizationRuntimeResetEventDetail | null) {
  if (detail?.eventId) {
    if (handledRuntimeResetEvents.has(detail.eventId)) {
      return
    }
    handledRuntimeResetEvents.add(detail.eventId)
  }
  cancelAppBootstrap()
  resetPermissionBootstrapState()
}

function handleRuntimeResetEvent(event: Event) {
  resetAuthorizationRuntime((event as CustomEvent<AuthorizationRuntimeResetEventDetail>).detail)
}

function handleStorageEvent(event: StorageEvent) {
  if (event.key !== AUTHORIZATION_RUNTIME_RESET_STORAGE_KEY) {
    return
  }
  resetAuthorizationRuntime(parseAuthorizationRuntimeResetEvent(event.newValue))
}

onMounted(() => {
  window.addEventListener(AUTHORIZATION_RUNTIME_RESET_EVENT, handleRuntimeResetEvent)
  window.addEventListener('storage', handleStorageEvent)
})

onUnmounted(() => {
  window.removeEventListener(AUTHORIZATION_RUNTIME_RESET_EVENT, handleRuntimeResetEvent)
  window.removeEventListener('storage', handleStorageEvent)
})
</script>
<template>
  <ConfigProvider :locale="zhCN">
    <!-- 路由视图，主布局由路由配置决定 -->
    <RouterView />
  </ConfigProvider>
</template>
