<template>
  <div
    class="process-modeling-workspace"
    :data-active-tab="activeTab"
    :data-process-model-id="activeModelId || ''"
    :data-dirty="designerDirty ? 'true' : 'false'"
  >
    <a-tabs
      :active-key="activeTab"
      class="modeling-tabs"
      destroy-inactive-tab-pane
      @change="handleTabChange"
    >
      <a-tab-pane key="drafts" tab="流程草稿">
        <ProcessDraftList
          @open-design="openDesign"
          @open-new-draft="openNewDraft"
        />
      </a-tab-pane>

      <a-tab-pane key="design" tab="流程设计" :disabled="!activeModelId && !isNewDraftRoute">
        <ProcessDesigner
          v-if="activeTab === 'design' && (activeModelId || isNewDraftRoute)"
          :key="activeModelId || unsavedDraft?.draftKey || 'new-draft'"
          :initial-draft="unsavedDraft"
          @dirty-change="handleDesignerDirtyChange"
          @saved="handleDesignerSaved"
        />
        <div v-else class="design-placeholder">
          <h3>请选择流程草稿</h3>
          <p>从流程草稿列表打开一个模型后进入设计。</p>
        </div>
      </a-tab-pane>
    </a-tabs>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { Modal, message } from 'ant-design-vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import type { LocationQueryRaw } from 'vue-router'
import type { ProcessModel } from '@/api/process'
import ProcessDesigner from '@/views/process/modeling/ProcessDesigner.vue'
import ProcessDraftList from '@/views/process/modeling/ProcessDraftList.vue'

type ModelingTab = 'drafts' | 'design'
type UnsavedProcessDraft = {
  draftKey: string
  modelKey: string
  name: string
  description?: string
  bpmnXml: string
}

const route = useRoute()
const router = useRouter()
const designerDirty = ref(false)
const unsavedDraft = ref<UnsavedProcessDraft | null>(null)

function parseModelId(value: unknown): number | null {
  const rawValue = Array.isArray(value) ? value[0] : value
  const modelId = typeof rawValue === 'string' || typeof rawValue === 'number'
    ? Number(rawValue)
    : NaN
  return Number.isSafeInteger(modelId) && modelId > 0 ? modelId : null
}

const activeModelId = computed(() => parseModelId(route.query.modelId))
const isNewDraftRoute = computed(() => route.query.draft === 'new')

const activeTab = computed<ModelingTab>(() =>
  route.query.tab === 'design' && (activeModelId.value || isNewDraftRoute.value) ? 'design' : 'drafts',
)

function buildQuery(tab: ModelingTab, modelId?: number, draftMode?: 'new') {
  const nextQuery: LocationQueryRaw = { ...route.query, tab }
  if (tab === 'design' && modelId) {
    nextQuery.modelId = String(modelId)
    delete nextQuery.draft
  } else if (tab === 'design' && draftMode === 'new') {
    nextQuery.draft = 'new'
    delete nextQuery.modelId
  } else {
    delete nextQuery.modelId
    delete nextQuery.draft
  }
  return nextQuery
}

function navigateTo(tab: ModelingTab, modelId?: number, draftMode?: 'new') {
  router.replace({
    path: route.path,
    query: buildQuery(tab, modelId, draftMode),
  })
}

function confirmDiscardDirty(onOk: () => void) {
  if (!designerDirty.value) {
    onOk()
    return
  }
  Modal.confirm({
    title: '存在未保存的流程设计',
    content: '离开当前设计前请确认是否放弃未保存修改。',
    okText: '放弃修改',
    cancelText: '继续编辑',
    onOk,
  })
}

function handleTabChange(key: string | number) {
  const nextTab = String(key) as ModelingTab
  if (nextTab === activeTab.value) {
    return
  }
  if (nextTab === 'design') {
    if (!activeModelId.value && !(isNewDraftRoute.value && unsavedDraft.value)) {
      message.warning('请先选择流程草稿')
      return
    }
    navigateTo('design', activeModelId.value ?? undefined, isNewDraftRoute.value ? 'new' : undefined)
    return
  }
  confirmDiscardDirty(() => {
    designerDirty.value = false
    unsavedDraft.value = null
    navigateTo('drafts')
  })
}

function openDesign(model: ProcessModel) {
  confirmDiscardDirty(() => {
    designerDirty.value = false
    unsavedDraft.value = null
    navigateTo('design', model.id)
  })
}

function openNewDraft(draft: Omit<UnsavedProcessDraft, 'draftKey'>) {
  confirmDiscardDirty(() => {
    designerDirty.value = false
    unsavedDraft.value = {
      ...draft,
      draftKey: `${draft.modelKey}:${Date.now()}`,
    }
    navigateTo('design', undefined, 'new')
  })
}

function handleDesignerDirtyChange(dirty: boolean) {
  designerDirty.value = dirty
}

function handleDesignerSaved(model: ProcessModel) {
  designerDirty.value = false
  unsavedDraft.value = null
  navigateTo('design', model.id)
}

function normalizeRouteQuery() {
  if (route.query.tab === 'design' && activeModelId.value && isNewDraftRoute.value) {
    navigateTo('design', activeModelId.value)
    return
  }
  if (route.query.tab === 'design' && isNewDraftRoute.value && !unsavedDraft.value) {
    message.warning('未保存草稿不存在，请重新新建或选择已有草稿')
    navigateTo('drafts')
    return
  }
  if (route.query.tab === 'design' && !activeModelId.value && !isNewDraftRoute.value) {
    message.warning('请先选择流程草稿')
    navigateTo('drafts')
  }
}

watch(
  () => [route.query.tab, route.query.modelId, route.query.draft],
  normalizeRouteQuery,
)

onMounted(normalizeRouteQuery)

onBeforeRouteLeave(() => {
  if (!designerDirty.value) {
    return true
  }
  return window.confirm('当前流程设计存在未保存修改，确认离开吗？')
})
</script>

<style scoped>
.process-modeling-workspace {
  display: flex;
  flex: 1;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: #fff;
}

:deep(.modeling-tabs) {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

:deep(.modeling-tabs > .ant-tabs-nav) {
  margin-bottom: 0;
  padding: 0 20px;
}

:deep(.modeling-tabs > .ant-tabs-content-holder) {
  flex: 1;
  min-height: 0;
}

:deep(.modeling-tabs .ant-tabs-content),
:deep(.modeling-tabs .ant-tabs-tabpane),
:deep(.modeling-tabs .ant-tabs-tabpane-active) {
  height: 100%;
  min-height: 0;
}

:deep(.modeling-tabs .ant-tabs-tabpane-active) {
  display: flex;
  flex-direction: column;
}

.design-placeholder {
  padding: 32px 24px;
}

.design-placeholder h3 {
  margin: 0 0 8px;
  font-size: 16px;
}

.design-placeholder p {
  margin: 0;
  color: #667085;
}
</style>
