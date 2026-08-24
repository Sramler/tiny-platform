<template>
  <div class="content-container">
    <div class="content-card platform-runtime-module">
      <div v-if="!isPlatformScope" class="platform-runtime-guard">
        <div class="platform-runtime-kicker">Platform Scope Required</div>
        <h3>当前页面只支持 PLATFORM 作用域</h3>
        <p>平台调度管理需要平台登录态。当前会话不是 PLATFORM 作用域，已阻止加载。</p>
      </div>

      <div v-else-if="!canAccessScheduling" class="platform-runtime-guard">
        <div class="platform-runtime-kicker">Permission Required</div>
        <h3>当前会话缺少平台调度管理权限</h3>
        <p>
          至少需要 <code>scheduling:entry:view</code>、<code>scheduling:console:view</code> 或
          <code>scheduling:*</code>。
        </p>
      </div>

      <template v-else>
        <a-tabs
          :active-key="activeTab"
          class="platform-runtime-tabs"
          destroy-inactive-tab-pane
          @change="handleTabChange"
        >
          <a-tab-pane v-for="tab in tabs" :key="tab.key" :tab="tab.title">
            <component v-if="tab.key === activeTab" :is="tab.component" :key="tab.key" />
          </a-tab-pane>
        </a-tabs>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuth } from '@/auth/auth'
import { runtimeAuthorities } from '@/auth/runtimeIdentity'
import { usePlatformScope } from '@/composables/usePlatformScope'
import {
  SCHEDULING_CONSOLE_VIEW,
  SCHEDULING_ENTRY_VIEW,
  SCHEDULING_WILDCARD,
} from '@/constants/permission'
import {
  buildPlatformSchedulingQuery,
  buildPlatformSchedulingTabPath,
  resolvePlatformSchedulingTabFromPath,
} from '@/utils/platformRuntime'
import SchedulingDag from '@/views/scheduling/Dag.vue'
import SchedulingTask from '@/views/scheduling/Task.vue'
import SchedulingTaskType from '@/views/scheduling/TaskType.vue'
import SchedulingDagHistory from '@/views/scheduling/DagHistory.vue'
import SchedulingAudit from '@/views/scheduling/Audit.vue'

const route = useRoute()
const router = useRouter()
const { user } = useAuth()
const { isPlatformScope } = usePlatformScope()

const schedulingAuthorities = computed(() =>
  runtimeAuthorities(user.value).filter((authority) =>
    authority.startsWith('scheduling:'),
  ),
)
const canAccessScheduling = computed(() => {
  const authorities = schedulingAuthorities.value
  return (
    authorities.includes(SCHEDULING_ENTRY_VIEW) ||
    authorities.includes(SCHEDULING_CONSOLE_VIEW) ||
    authorities.includes(SCHEDULING_WILDCARD)
  )
})

const tabs = [
  { key: 'dag', title: 'DAG 管理', component: SchedulingDag },
  { key: 'task', title: '任务管理', component: SchedulingTask },
  { key: 'taskType', title: '任务类型', component: SchedulingTaskType },
  { key: 'history', title: '运行历史', component: SchedulingDagHistory },
  { key: 'audit', title: '审计日志', component: SchedulingAudit },
]

function resolveActiveTab() {
  return resolvePlatformSchedulingTabFromPath(route.path) ?? 'dag'
}

const activeTab = computed(resolveActiveTab)

function handleTabChange(key: string | number) {
  const nextTab = String(key)
  if (!tabs.some((tab) => tab.key === nextTab)) {
    return
  }
  router.replace({
    path: buildPlatformSchedulingTabPath(nextTab),
    query: buildPlatformSchedulingQuery(route.query),
  })
}
</script>

<style scoped>
.platform-runtime-module {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.content-container,
.content-card {
  height: 100%;
}

.content-container {
  display: flex;
  flex-direction: column;
  background: #fff;
}

.content-card {
  background: #fff;
}

:deep(.platform-runtime-tabs) {
  display: flex;
  flex: 1;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

:deep(.platform-runtime-tabs > .ant-tabs-content-holder) {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

:deep(.platform-runtime-tabs > .ant-tabs-nav) {
  margin-bottom: 0;
  padding: 0 24px;
}

:deep(.platform-runtime-tabs > .ant-tabs-nav::before) {
  border-bottom-color: #e5e7eb;
}

:deep(.platform-runtime-tabs > .ant-tabs-nav .ant-tabs-tab) {
  padding: 10px 4px 14px;
  font-weight: 600;
}

:deep(.platform-runtime-tabs .ant-tabs-content),
:deep(.platform-runtime-tabs .ant-tabs-tabpane),
:deep(.platform-runtime-tabs .ant-tabs-tabpane-active) {
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

:deep(.platform-runtime-tabs .ant-tabs-tabpane-active) {
  display: flex;
  flex-direction: column;
}

:deep(.platform-runtime-tabs .ant-tabs-tabpane-hidden) {
  display: none !important;
}

:deep(.platform-runtime-tabs .ant-table-thead > tr > th),
:deep(.platform-runtime-tabs .ant-table-tbody > tr > td) {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

:deep(.platform-runtime-tabs .ant-table-cell .ant-typography) {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  vertical-align: bottom;
}

.platform-runtime-guard h3 {
  margin: 4px 0 8px;
}

.platform-runtime-kicker {
  color: #1677ff;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.platform-runtime-guard {
  border: 1px solid #e6f4ff;
  border-radius: 12px;
  background: linear-gradient(135deg, #f8fbff 0%, #ffffff 100%);
  padding: 24px;
}

@media (max-width: 960px) {
  :deep(.platform-runtime-tabs > .ant-tabs-nav) {
    padding: 0 16px;
  }
}
</style>
