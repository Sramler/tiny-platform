<template>
  <div class="content-container" style="position: relative;">
    <div class="content-card">
      <!-- 阶段10: Tab 切换 -->
      <a-tabs v-model:activeKey="activeTab" @change="handleTabChange">
        <a-tab-pane key="type" tab="字典类型">
          <DictType ref="dictTypeRef" @view-items="handleViewItems" />
        </a-tab-pane>
        <a-tab-pane key="item" tab="字典项">
          <DictItem ref="dictItemRef" />
        </a-tab-pane>
      </a-tabs>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'
import DictType from './dictType.vue'
import DictItem from './dictItem.vue'

// Tab 切换
const activeTab = ref('type')
const dictTypeRef = ref<InstanceType<typeof DictType> | null>(null)
const dictItemRef = ref<InstanceType<typeof DictItem> | null>(null)

function handleTabChange(key: string) {
  activeTab.value = key
}

// 处理从字典类型跳转到字典项
function handleViewItems(dictTypeId: number) {
  activeTab.value = 'item'
  nextTick(() => {
    if (dictItemRef.value && typeof dictItemRef.value.setDictTypeId === 'function') {
      dictItemRef.value.setDictTypeId(dictTypeId)
    }
  })
}
</script>

<style scoped>
/* 阶段10: 基础样式 */
.content-container {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #fff;
}

.content-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

:deep(.ant-tabs) {
  height: 100%;
  display: flex;
  flex-direction: column;
}

:deep(.ant-tabs-content-holder) {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

:deep(.ant-tabs-tabpane) {
  height: 100%;
  display: flex;
  flex-direction: column;
}
</style>
