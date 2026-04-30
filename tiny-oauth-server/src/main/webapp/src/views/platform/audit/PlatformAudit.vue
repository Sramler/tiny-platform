<template>
  <div class="platform-audit-page">
    <a-card v-if="!isPlatformScope" title="平台作用域限制">
      当前会话不是 PLATFORM 作用域，已阻止加载平台审计控制面。请切换到平台作用域后重试。
    </a-card>
    <template v-else>
      <div class="platform-audit-header">
        <h2>平台审计治理</h2>
        <p>统一查看登录审计与授权审计，筛选字段与查询契约复用现有控制面实现。</p>
      </div>
      <a-tabs :active-key="activeKey" destroy-inactive-tab-pane @change="handleTabChange">
        <a-tab-pane key="authentication" tab="登录审计">
          <AuthenticationAudit v-if="activeKey === 'authentication'" />
        </a-tab-pane>
        <a-tab-pane key="authorization" tab="授权审计">
          <AuthorizationAudit v-if="activeKey === 'authorization'" />
        </a-tab-pane>
      </a-tabs>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { Key } from 'ant-design-vue/es/_util/type'
import AuthenticationAudit from '@/views/audit/AuthenticationAudit.vue'
import AuthorizationAudit from '@/views/audit/AuthorizationAudit.vue'
import { usePlatformScope } from '@/composables/usePlatformScope'
import {
  buildPlatformAuditRouteQuery,
  buildPlatformAuditTabPath,
  isPlatformAuditTab,
  resolvePlatformAuditTabFromPath,
  type PlatformAuditTab,
} from '@/utils/platformRuntime'

const { isPlatformScope } = usePlatformScope()
const route = useRoute()
const router = useRouter()

const activeKey = computed<PlatformAuditTab>(
  () => resolvePlatformAuditTabFromPath(route.path) ?? 'authentication',
)

function handleTabChange(key: Key) {
  const nextTab = String(key)
  if (!isPlatformAuditTab(nextTab)) {
    return
  }
  void router.replace({
    path: buildPlatformAuditTabPath(nextTab),
    query: buildPlatformAuditRouteQuery(route.query),
  })
}
</script>

<style scoped>
.platform-audit-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.platform-audit-header {
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fff;
  padding: 16px 20px;
}

.platform-audit-header h2 {
  margin: 0;
  font-size: 18px;
}

.platform-audit-header p {
  margin: 8px 0 0;
  color: #595959;
}
</style>
