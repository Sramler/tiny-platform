<template>
  <div class="toolbars">
    <!-- 列设置（Dropdown） -->
    <a-dropdown
      v-if="features.columnSetting"
      :trigger="['click']"
      :open="columnSettingOpen"
      @update:open="setColumnSettingOpen"
      placement="bottomRight"
      overlayClassName="toolbar-colsetting-overlay"
    >
      <a-tooltip title="列设置">
        <span class="action-icon" @click.stop="onOpenColumnSetting">
          <SettingOutlined />
        </span>
      </a-tooltip>

      <!-- overlay：列设置面板 -->
      <template #overlay>
        <div class="colsetting-panel" @click.stop>
          <div class="colsetting-header">
            <span class="colsetting-title">列设置</span>
            <div class="colsetting-actions">
              <a-button size="small" @click="onResetColumnSetting">重置</a-button>
              <a-button type="primary" size="small" @click="onSaveColumnSetting">保存</a-button>
            </div>
          </div>

          <div class="colsetting-body">
            <div v-for="item in columnSettingItems" :key="item.key" class="colsetting-row">
              <a-checkbox v-model:checked="item.visible">
                <span class="colsetting-label">{{ item.title }}</span>
              </a-checkbox>

              <div class="colsetting-move">
                <a-button size="small" type="text" @click="toolbar.moveColumnUp?.(item.key)">
                  <UpOutlined />
                </a-button>
                <a-button size="small" type="text" @click="toolbar.moveColumnDown?.(item.key)">
                  <DownOutlined />
                </a-button>
              </div>
            </div>

            <div v-if="!columnSettingItems.length" class="colsetting-empty">暂无可配置列</div>
          </div>
        </div>
      </template>
    </a-dropdown>

    <!-- 密度 -->
    <a-tooltip v-if="features.density" :title="`密度：${toolbar.densityLabel ?? ''}`">
      <span class="action-icon" @click="toolbar.cycleDensity?.()">
        <ColumnHeightOutlined />
      </span>
    </a-tooltip>

    <!-- 斑马纹 -->
    <a-tooltip v-if="features.zebra" :title="toolbar.zebraEnabled ? '关闭斑马纹' : '开启斑马纹'">
      <span
        class="action-icon"
        :class="{ active: !!toolbar.zebraEnabled }"
        @click="toolbar.toggleZebra?.()"
      >
        <BgColorsOutlined />
      </span>
    </a-tooltip>

    <!-- 复制 -->
    <a-tooltip
      v-if="features.copy"
      :title="toolbar.copyEnabled ? '关闭单元格复制' : '开启单元格复制'"
    >
      <span
        class="action-icon"
        :class="{ active: !!toolbar.copyEnabled }"
        @click="toolbar.toggleCopy?.()"
      >
        <CopyOutlined />
      </span>
    </a-tooltip>

    <!-- 刷新 -->
    <a-tooltip v-if="features.refresh" title="刷新">
      <span class="action-icon" @click="toolbar.refresh?.()">
        <ReloadOutlined :spin="!!toolbar.loading" />
      </span>
    </a-tooltip>

    <!-- 右侧扩展插槽（业务按钮/批量操作等） -->
    <slot name="extra"></slot>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  SettingOutlined,
  ColumnHeightOutlined,
  BgColorsOutlined,
  CopyOutlined,
  ReloadOutlined,
  UpOutlined,
  DownOutlined,
} from '@ant-design/icons-vue'

type ToolbarVm = {
  features?: {
    columnSetting?: boolean
    density?: boolean
    zebra?: boolean
    copy?: boolean
    refresh?: boolean
  }

  densityLabel?: string
  zebraEnabled?: boolean
  copyEnabled?: boolean
  loading?: boolean

  // column setting (来自 useToolbars)
  columnSettingVisible?: { value: boolean }
  columnSettingItems?: any
  openColumnSetting?: () => void
  moveColumnUp?: (key: string) => void
  moveColumnDown?: (key: string) => void
  resetColumnSetting?: () => void
  saveColumnSetting?: () => void

  // actions
  cycleDensity?: () => void
  toggleZebra?: () => void
  toggleCopy?: () => void
  refresh?: () => void
}

const props = defineProps<{ toolbar: ToolbarVm }>()

/** 默认 features 全开；页面可按用户配置/权限关掉某些按钮 */
const features = computed(() => ({
  columnSetting: props.toolbar.features?.columnSetting !== false,
  density: props.toolbar.features?.density !== false,
  zebra: props.toolbar.features?.zebra !== false,
  copy: props.toolbar.features?.copy !== false,
  refresh: props.toolbar.features?.refresh !== false,
}))

/** 列设置 Dropdown 的开关：优先绑定 useToolbars 的 columnSettingVisible(ref) */
const columnSettingOpen = computed(() => {
  return !!props.toolbar.columnSettingVisible?.value
})

function setColumnSettingOpen(v: boolean) {
  if (props.toolbar.columnSettingVisible) {
    props.toolbar.columnSettingVisible.value = v
  }
}

const columnSettingItems = computed(() => {
  const v = props.toolbar.columnSettingItems
  // 支持 computed/ref/普通数组
  return Array.isArray(v) ? v : (v?.value ?? [])
})

function onOpenColumnSetting() {
  // 兼容：如果外部有 openColumnSetting（useToolbars 自带），就调用
  props.toolbar.openColumnSetting?.()
  // 同步打开 dropdown
  setColumnSettingOpen(true)
}

function onResetColumnSetting() {
  props.toolbar.resetColumnSetting?.()
  // 重置后不强制关闭，方便继续操作
}

function onSaveColumnSetting() {
  props.toolbar.saveColumnSetting?.()
  // 保存后关闭 dropdown（体验更像“应用”）
  setColumnSettingOpen(false)
}
</script>

<style scoped>
.toolbars {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.action-icon {
  font-size: 18px;
  cursor: pointer;
  color: #595959;
  border-radius: 6px;
  padding: 6px;
  transition:
    color 0.15s ease,
    background 0.15s ease;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  user-select: none;
}

.action-icon:hover {
  color: #1677ff;
  background: #f5f5f5;
}

.action-icon.active {
  color: #1677ff;
  background: #e6f4ff;
}
</style>

<!-- overlay 不受 scoped 影响：用 :global -->
<style>
.toolbar-colsetting-overlay .ant-dropdown-menu {
  padding: 0;
}

.colsetting-panel {
  width: 320px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.12);
  overflow: hidden;
}

.colsetting-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid #f0f0f0;
}

.colsetting-title {
  font-weight: 600;
}

.colsetting-actions {
  display: inline-flex;
  gap: 8px;
}

.colsetting-body {
  max-height: 320px;
  overflow: auto;
  padding: 8px 12px;
}

.colsetting-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 0;
}

.colsetting-label {
  display: inline-block;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.colsetting-move {
  display: inline-flex;
  gap: 4px;
}

.colsetting-empty {
  padding: 16px 0;
  color: #999;
  text-align: center;
}
</style>
