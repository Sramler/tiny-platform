<script setup lang="ts">
import { ref, computed } from 'vue'
import { useConfigDrivenCrud } from '@/composables/useConfigDrivenCrud'
import { useToolbars } from '@/composables/useToolbars'
import Toolbars from '@/components/Toolbars.vue'

const { loading, loadData, baseColumns, enhancedColumns } = useConfigDrivenCrud()

/**
 * Toolbars（通用工具栏）- 使用 useToolbars 的完整返回对象
 * - Toolbars.vue 的“列设置 Dropdown”依赖这些字段：
 *   columnSettingVisible / columnSettingItems / moveColumnUp/down / reset/save
 * - 因此这里不能再用简化的 computed 对象，而要直接传 useToolbars() 的结果
 */
const toolbars = useToolbars({
  enabled: true,
  // 建议：按 route/user/tenant 隔离；这里先保证可用
  storageKey: 'testData:toolbars:v1',
  // 优先用 crud.baseColumns（由 FIELD_DEFS.table 派生）以保证“列设置”与字段定义一致
  baseColumns: (baseColumns?.value ?? enhancedColumns?.value ?? []) as any,
  refresh: () => loadData(),
  features: {
    columnSetting: true,
    density: true,
    zebra: true,
    copy: true,
    refresh: true,
  },
})

/**
 * 兼容：如果页面后续还引用了 densityLabel / zebraEnabled / copyEnabled 等旧变量，
 * 请统一改为 toolbars.xxx（避免双份状态）。
 */
const densityLabel = computed(() => toolbars.densityLabel)
const zebraEnabled = computed(() => toolbars.zebraEnabled)
const copyEnabled = computed(() => toolbars.copyEnabled)
</script>

<template>
  <Toolbars :toolbar="toolbars" />
  <a-table
    :columns="toolbars.enhancedColumns"
    :loading="loading"
    :size="toolbars.tableSize"
    :row-class-name="toolbars.rowClassName"
    ...
  >
    <!-- 复制图标点击调用 toolbars.copyCell -->
    <template #bodyCell="{ record, column }">
      <template v-if="toolbars.canCopyColumn(column.dataIndex)">
        <a-icon
          type="copy"
          @click="toolbars.copyCell(record, column.dataIndex)"
        />
      </template>
    </template>
  </a-table>
</template>
