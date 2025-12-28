<template>
  <div class="content-container" style="position: relative;">
    <div class="content-card">
      <div class="form-container">
        <a-form layout="inline" :model="query">
          <a-form-item label="租户编码">
            <a-input v-model:value="query.tenantCode" placeholder="请输入租户编码" allow-clear />
          </a-form-item>
          <a-form-item label="产品编码">
            <a-input v-model:value="query.productCode" placeholder="请输入产品编码" allow-clear />
          </a-form-item>
          <a-form-item label="状态">
            <a-select v-model:value="query.status" allow-clear placeholder="全部状态" style="width: 160px">
              <a-select-option value="UNBILLED">UNBILLED</a-select-option>
              <a-select-option value="BILLED">BILLED</a-select-option>
              <a-select-option value="ADJUSTED">ADJUSTED</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item>
            <a-button type="primary" @click="throttledSearch">搜索</a-button>
            <a-button class="ml-2" @click="throttledReset">重置</a-button>
          </a-form-item>
        </a-form>
      </div>

      <div class="table-container" ref="tableContentRef">
        <!-- 工具栏固定在顶部，不随表格滚动 -->
        <div class="toolbar-container">
          <div class="table-title">
            导出测试数据（demo_export_usage）
          </div>
          <div class="table-actions">
            <a-button type="link" @click="openCreateDrawer" class="toolbar-btn">
              <template #icon>
                <PlusOutlined />
              </template>
              新建
            </a-button>
            <a-button type="primary" @click="openGenerateDrawer" class="toolbar-btn">
              生成测试数据
            </a-button>
            <a-button danger @click="handleClearDemo" class="toolbar-btn">
              清空测试数据
            </a-button>
            <a-tooltip title="刷新">
              <span class="action-icon" @click="throttledRefresh">
                <ReloadOutlined :spin="refreshing" />
              </span>
            </a-tooltip>
            <a-tooltip :title="showSortTooltip ? '关闭排序提示' : '开启排序提示'">
              <PoweroffOutlined :class="['action-icon', { active: showSortTooltip }]"
                @click="showSortTooltip = !showSortTooltip" />
            </a-tooltip>
            <a-tooltip :title="zebraStripeEnabled ? '关闭斑马纹' : '开启斑马纹'">
              <div class="zebra-stripe-switch">
                <a-switch v-model:checked="zebraStripeEnabled" size="small" />
              </div>
            </a-tooltip>
            <a-tooltip :title="cellCopyEnabled ? '关闭单元格复制' : '开启单元格复制'">
              <CopyOutlined :class="['action-icon', { active: cellCopyEnabled }]"
                @click="cellCopyEnabled = !cellCopyEnabled" />
            </a-tooltip>
            <a-dropdown placement="bottomRight" trigger="click">
              <a-tooltip title="表格密度">
                <ColumnHeightOutlined class="action-icon" />
              </a-tooltip>
              <template #overlay>
                <a-menu @click="handleDensityMenuClick" :selected-keys="[tableSize]">
                  <a-menu-item key="default">
                    <span>标准</span>
                  </a-menu-item>
                  <a-menu-item key="middle">
                    <span>中等</span>
                  </a-menu-item>
                  <a-menu-item key="small">
                    <span>紧凑</span>
                  </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
            <a-popover placement="bottomRight" trigger="click" :destroyTooltipOnHide="false">
              <template #content>
                <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px;">
                  <div style="display: flex; align-items: center;">
                    <a-checkbox
                      :checked="showColumnKeys && showColumnKeys.length > 0 && allColumns && allColumns.length > 0 && showColumnKeys.length === allColumns.length"
                      :indeterminate="showColumnKeys && showColumnKeys.length > 0 && allColumns && allColumns.length > 0 && showColumnKeys.length < allColumns.length"
                      @change="onCheckAllChange" />
                    <span style="font-weight: bold; margin-left: 8px;">列展示/排序</span>
                  </div>
                  <span style="font-weight: bold; color: #1677ff; cursor: pointer;" @click="resetColumnOrder">
                    重置
                  </span>
                </div>
                <VueDraggable v-if="draggableColumns && Array.isArray(draggableColumns) && draggableColumns.length > 0"
                  v-model="draggableColumns" :item-key="(item: any) => item?.dataIndex || `col_${Math.random()}`"
                  handle=".drag-handle" @end="onDragEnd" class="draggable-columns" ghost-class="sortable-ghost"
                  chosen-class="sortable-chosen" tag="div">
                  <template #item="{ element: col }">
                    <div v-if="col && col.dataIndex" class="draggable-column-item">
                      <HolderOutlined class="drag-handle" />
                      <a-checkbox
                        :checked="showColumnKeys && Array.isArray(showColumnKeys) && showColumnKeys.includes(col.dataIndex)"
                        @change="(e: any) => {
                          if (e && e.target && col && col.dataIndex) {
                            onCheckboxChange(col.dataIndex, e.target.checked)
                          }
                        }">
                        {{ col.title }}
                      </a-checkbox>
                    </div>
                  </template>
                </VueDraggable>
                <div v-else style="padding: 16px; text-align: center; color: #999;">
                  暂无列数据
                </div>
              </template>
              <a-tooltip title="列设置">
                <SettingOutlined class="action-icon" />
              </a-tooltip>
            </a-popover>
          </div>
        </div>

        <div class="table-scroll-container">
          <a-table :columns="columns" :data-source="tableData" :pagination="false"
            :row-key="(record: any) => String(record.id)" bordered :loading="loading"
            :scroll="{ x: tableScrollX, y: tableBodyHeight }" :locale="tableLocale"
            :show-sorter-tooltip="showSortTooltip" :row-class-name="getRowClassName"
            :size="tableSize === 'default' ? undefined : tableSize">
            <template #bodyCell="{ column, record }">
              <template v-if="column && column.dataIndex === 'status'">
                <a-tag :color="statusColorMap[record.status] || 'default'">
                  {{ record.status }}
                </a-tag>
              </template>
              <template v-else-if="column && column.dataIndex === 'billable'">
                <a-tag :color="record.billable ? 'green' : 'red'">
                  {{ record.billable ? '是' : '否' }}
                </a-tag>
              </template>
              <template v-else-if="column && column.dataIndex === 'priority'">
                <a-tag :color="getPriorityColor(record.priority)">
                  {{ getPriorityText(record.priority) }}
                </a-tag>
              </template>
              <template v-else-if="column && column.dataIndex === 'tags'">
                <a-space v-if="record.tags && Array.isArray(record.tags) && record.tags.length > 0" size="small">
                  <a-tag v-for="(tag, idx) in record.tags" :key="idx" color="blue">{{ tag }}</a-tag>
                </a-space>
                <span v-else-if="record.tags && typeof record.tags === 'string'">
                  <a-space size="small">
                    <a-tag v-for="(tag, idx) in JSON.parse(record.tags)" :key="idx" color="blue">{{ tag }}</a-tag>
                  </a-space>
                </span>
                <span v-else>-</span>
              </template>
              <template v-else-if="column && column.dataIndex === 'qualityScore'">
                <div class="quality-score-cell">
                  <a-rate :value="record.qualityScore || 0" disabled :count="5" />
                </div>
              </template>
              <template v-else-if="column && column.dataIndex === 'discountPercentage'">
                <span>{{ record.discountPercentage ?? 0 }}%</span>
              </template>
              <template v-else-if="column && column.dataIndex === 'categoryPath'">
                {{ formatCategoryPath(record.categoryPath) }}
              </template>
              <template v-else-if="column && column.dataIndex === 'themeColor'">
                <div style="display: flex; align-items: center; gap: 8px;">
                  <div
                    :style="{ width: '20px', height: '20px', backgroundColor: record.themeColor || '#1890ff', border: '1px solid #d9d9d9', borderRadius: '4px' }">
                  </div>
                  <span>{{ record.themeColor || '-' }}</span>
                </div>
              </template>
              <template v-else-if="column && column.dataIndex === 'apiKey'">
                <span v-if="record.apiKey">{{ record.apiKey.substring(0, 8) }}***</span>
                <span v-else>-</span>
              </template>
              <template v-else-if="column && column.dataIndex === 'selectedFeatures'">
                <a-space
                  v-if="record.selectedFeatures && Array.isArray(record.selectedFeatures) && record.selectedFeatures.length > 0"
                  size="small">
                  <a-tag v-for="(feature, idx) in record.selectedFeatures" :key="idx" color="green">{{ feature
                  }}</a-tag>
                </a-space>
                <span v-else-if="record.selectedFeatures && typeof record.selectedFeatures === 'string'">
                  <a-space size="small">
                    <a-tag v-for="(feature, idx) in JSON.parse(record.selectedFeatures)" :key="idx" color="green">{{
                      feature }}</a-tag>
                  </a-space>
                </span>
                <span v-else>-</span>
              </template>
              <template v-else-if="column && column.dataIndex === 'paymentMethod'">
                <a-tag :color="getPaymentMethodColor(record.paymentMethod)">
                  {{ getPaymentMethodText(record.paymentMethod) }}
                </a-tag>
              </template>
              <template v-else-if="column && column.dataIndex === 'attachmentInfo'">
                <span
                  v-if="record.attachmentInfo && Array.isArray(record.attachmentInfo) && record.attachmentInfo.length > 0">
                  {{ record.attachmentInfo.length }} 个文件
                </span>
                <span v-else-if="record.attachmentInfo && typeof record.attachmentInfo === 'string'">
                  {{ JSON.parse(record.attachmentInfo).length }} 个文件
                </span>
                <span v-else>-</span>
              </template>
              <template v-else-if="column && (column.dataIndex === 'description' || column.dataIndex === 'notes')">
                <a-tooltip v-if="record[column.dataIndex]">
                  <template #title>{{ record[column.dataIndex] }}</template>
                  <span
                    style="display: block; max-width: 200px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">
                    {{ record[column.dataIndex] }}
                  </span>
                </a-tooltip>
                <span v-else>-</span>
              </template>
              <template v-else-if="column && column.dataIndex === 'action'">
                <div class="action-buttons">
                  <a-button type="link" size="small" @click.stop="openEditDrawer(record)" class="action-btn">
                    <template #icon>
                      <EditOutlined />
                    </template>
                    编辑
                  </a-button>
                  <a-button type="link" size="small" danger @click.stop="handleDelete(record)" class="action-btn">
                    <template #icon>
                      <DeleteOutlined />
                    </template>
                    删除
                  </a-button>
                  <a-button type="link" size="small" @click.stop="throttledView(record)" class="action-btn">
                    <template #icon>
                      <EyeOutlined />
                    </template>
                    查看
                  </a-button>
                </div>
              </template>
              <template v-else>
                <template v-if="cellCopyEnabled && column.dataIndex && column.dataIndex !== 'action'">
                  <span class="cell-text">
                    {{ record[column.dataIndex as string] ?? '-' }}
                  </span>
                  <CopyOutlined class="cell-copy-icon"
                    @click.stop="handleCellCopy(record[column.dataIndex as string], (column.title as string) || '')" />
                </template>
                <span v-else-if="column && column.dataIndex">{{ record[column.dataIndex as string] }}</span>
                <span v-else>-</span>
              </template>
            </template>
          </a-table>
        </div>
        <div class="pagination-container" ref="paginationRef">
          <div class="pagination-left">
            <div class="export-group">
              <a-button type="primary" :loading="exporting" @click="handleExportSync" class="export-btn">
                <template #icon>
                  <DownloadOutlined />
                </template>
                导出当前页
              </a-button>
              <a-button :loading="exportingAsync" @click="handleExportAsync" class="export-btn">
                <template #icon>
                  <DownloadOutlined />
                </template>
                导出全部（异步）
              </a-button>
            </div>
          </div>
          <a-pagination v-model:current="pagination.current" :page-size="pagination.pageSize"
            :total="paginationConfig.total" :show-size-changer="pagination.showSizeChanger"
            :page-size-options="paginationConfig.pageSizeOptions" :show-total="pagination.showTotal"
            @change="handlePageChange" @showSizeChange="handlePageSizeChange" :locale="{ items_per_page: '条/页' }" />
        </div>
      </div>
    </div>

    <a-drawer v-model:open="drawerVisible"
      :title="drawerMode === 'create' ? '新建测试数据' : drawerMode === 'edit' ? '编辑测试数据' : '查看测试数据'" width="50%"
      :get-container="false" :style="{ position: 'absolute' }" @close="handleDrawerClose">
      <a-form :model="formState" layout="vertical">
        <a-form-item label="租户编码">
          <a-input v-model:value="formState.tenantCode" :disabled="drawerMode === 'view'" />
        </a-form-item>
        <a-form-item label="用量日期">
          <a-date-picker v-model:value="formState.usageDate" style="width: 100%" :disabled="drawerMode === 'view'" />
        </a-form-item>
        <a-form-item label="产品编码">
          <a-input v-model:value="formState.productCode" :disabled="drawerMode === 'view'" />
        </a-form-item>
        <a-form-item label="产品名称">
          <a-input v-model:value="formState.productName" :disabled="drawerMode === 'view'" />
        </a-form-item>
        <a-form-item label="套餐档位">
          <a-input v-model:value="formState.planTier" :disabled="drawerMode === 'view'" />
        </a-form-item>
        <a-form-item label="区域">
          <a-input v-model:value="formState.region" :disabled="drawerMode === 'view'" />
        </a-form-item>
        <a-form-item label="用量数量">
          <a-input-number v-model:value="formState.usageQty" :min="0" style="width: 100%"
            :disabled="drawerMode === 'view'" />
        </a-form-item>
        <a-form-item label="单位">
          <a-input v-model:value="formState.unit" :disabled="drawerMode === 'view'" />
        </a-form-item>
        <a-form-item label="单价">
          <a-input-number v-model:value="formState.unitPrice" :min="0" style="width: 100%"
            :disabled="drawerMode === 'view'" />
        </a-form-item>
        <a-form-item label="金额">
          <a-input-number v-model:value="formState.amount" :min="0" style="width: 100%"
            :disabled="drawerMode === 'view'" />
        </a-form-item>
        <a-form-item label="币种">
          <a-input v-model:value="formState.currency" :disabled="drawerMode === 'view'" />
        </a-form-item>
        <a-form-item label="税率">
          <a-input-number v-model:value="formState.taxRate" :min="0" :max="1" :step="0.0001" style="width: 100%"
            :disabled="drawerMode === 'view'" />
        </a-form-item>
        <a-form-item label="是否计费">
          <a-switch v-model:checked="formState.billable" :disabled="drawerMode === 'view'" />
        </a-form-item>
        <a-form-item label="状态">
          <a-select v-model:value="formState.status" :disabled="drawerMode === 'view'">
            <a-select-option value="UNBILLED">UNBILLED</a-select-option>
            <a-select-option value="BILLED">BILLED</a-select-option>
            <a-select-option value="ADJUSTED">ADJUSTED</a-select-option>
          </a-select>
        </a-form-item>

        <a-divider>扩展字段（表单组件示例）</a-divider>

        <a-form-item label="描述信息（TextArea）">
          <a-textarea v-model:value="formState.description" :rows="3" :disabled="drawerMode === 'view'"
            placeholder="请输入描述信息" />
        </a-form-item>

        <a-form-item label="优先级（Radio）">
          <a-radio-group v-model:value="formState.priority" :disabled="drawerMode === 'view'">
            <a-radio value="LOW">低</a-radio>
            <a-radio value="MEDIUM">中</a-radio>
            <a-radio value="HIGH">高</a-radio>
            <a-radio value="URGENT">紧急</a-radio>
          </a-radio-group>
        </a-form-item>

        <a-form-item label="标签（Checkbox）">
          <a-checkbox-group v-model:value="formState.tags" :disabled="drawerMode === 'view'">
            <a-checkbox value="重要">重要</a-checkbox>
            <a-checkbox value="VIP">VIP</a-checkbox>
            <a-checkbox value="测试">测试</a-checkbox>
            <a-checkbox value="生产">生产</a-checkbox>
          </a-checkbox-group>
        </a-form-item>

        <a-form-item label="使用时间（TimePicker）">
          <a-time-picker v-model:value="formState.usageTime" style="width: 100%" :disabled="drawerMode === 'view'"
            format="HH:mm:ss" />
        </a-form-item>

        <a-form-item label="质量评分（Rate）">
          <a-rate v-model:value="formState.qualityScore" :disabled="drawerMode === 'view'" />
        </a-form-item>

        <a-form-item label="折扣百分比（Slider）">
          <a-slider v-model:value="formState.discountPercentage" :min="0" :max="100"
            :disabled="drawerMode === 'view'" />
          <span style="margin-left: 8px">{{ formState.discountPercentage }}%</span>
        </a-form-item>

        <a-form-item label="分类路径（Cascader）">
          <a-cascader v-model:value="formState.categoryPath" :options="cascaderOptions"
            :disabled="drawerMode === 'view'" placeholder="请选择分类路径" style="width: 100%" />
        </a-form-item>

        <a-form-item label="部门（TreeSelect）">
          <a-tree-select v-model:value="formState.departmentId" :tree-data="departmentTreeData"
            :disabled="drawerMode === 'view'" placeholder="请选择部门" allow-clear style="width: 100%" />
        </a-form-item>

        <a-form-item label="客户名称（AutoComplete）">
          <a-auto-complete v-model:value="formState.customerName" :options="customerOptions"
            :disabled="drawerMode === 'view'" placeholder="请输入客户名称" style="width: 100%" />
        </a-form-item>

        <a-form-item label="主题颜色（ColorPicker）">
          <a-input v-model:value="formState.themeColor" :disabled="drawerMode === 'view'" placeholder="#1890ff">
            <template #prefix>
              <div
                :style="{ width: '20px', height: '20px', backgroundColor: formState.themeColor, border: '1px solid #d9d9d9', borderRadius: '4px' }">
              </div>
            </template>
          </a-input>
        </a-form-item>

        <a-form-item label="API密钥（Input.Password）">
          <a-input-password v-model:value="formState.apiKey" :disabled="drawerMode === 'view'" placeholder="请输入API密钥" />
        </a-form-item>

        <a-form-item label="搜索关键词（Input.Search）">
          <a-input-search v-model:value="formState.searchKeyword" :disabled="drawerMode === 'view'"
            placeholder="请输入搜索关键词" @search="handleSearchKeyword" />
        </a-form-item>

        <a-form-item label="日期范围（RangePicker）">
          <a-range-picker v-model:value="formState.dateRange" style="width: 100%" :disabled="drawerMode === 'view'" />
        </a-form-item>

        <a-form-item label="选中功能（Select Multiple）">
          <a-select v-model:value="formState.selectedFeatures" mode="multiple" :disabled="drawerMode === 'view'"
            placeholder="请选择功能" style="width: 100%">
            <a-select-option value="feature1">功能1</a-select-option>
            <a-select-option value="feature2">功能2</a-select-option>
            <a-select-option value="feature3">功能3</a-select-option>
            <a-select-option value="feature4">功能4</a-select-option>
          </a-select>
        </a-form-item>

        <a-form-item label="数值范围">
          <a-space>
            <a-input-number v-model:value="formState.minValue" :min="0" placeholder="最小值"
              :disabled="drawerMode === 'view'" />
            <span>~</span>
            <a-input-number v-model:value="formState.maxValue" :min="0" placeholder="最大值"
              :disabled="drawerMode === 'view'" />
          </a-space>
        </a-form-item>

        <a-form-item label="时间范围">
          <a-range-picker v-model:value="formState.timeRange" picker="time" style="width: 100%"
            :disabled="drawerMode === 'view'" />
        </a-form-item>

        <a-form-item label="支付方式（Radio.Group）">
          <a-radio-group v-model:value="formState.paymentMethod" :disabled="drawerMode === 'view'">
            <a-radio value="ALIPAY">支付宝</a-radio>
            <a-radio value="WECHAT">微信支付</a-radio>
            <a-radio value="CREDIT_CARD">信用卡</a-radio>
            <a-radio value="BANK_TRANSFER">银行转账</a-radio>
          </a-radio-group>
        </a-form-item>

        <a-form-item label="备注（TextArea）">
          <a-textarea v-model:value="formState.notes" :rows="4" :disabled="drawerMode === 'view'"
            placeholder="请输入备注信息" />
        </a-form-item>

        <a-form-item label="附件（Upload）">
          <a-upload v-model:file-list="formState.attachmentInfo" :disabled="drawerMode === 'view'"
            :before-upload="beforeUpload" @remove="handleRemoveFile">
            <a-button>
              <template #icon>
                <UploadOutlined />
              </template>
              上传文件
            </a-button>
          </a-upload>
        </a-form-item>
      </a-form>
      <template #footer>
        <div style="text-align: right;">
          <a-button style="margin-right: 8px" @click="handleDrawerClose">{{ drawerMode === 'view' ? '关闭' : '取消'
          }}</a-button>
          <a-button v-if="drawerMode !== 'view'" type="primary" @click="handleSubmit">保存</a-button>
        </div>
      </template>
    </a-drawer>

    <a-drawer v-model:open="generateDrawerVisible" title="生成测试数据" width="50%" :get-container="false"
      :style="{ position: 'absolute' }" @close="handleGenerateDrawerClose">
      <a-alert message="提示" description="将通过存储过程批量生成 demo_export_usage 测试数据，仅建议在开发/测试环境使用。" type="warning" show-icon
        style="margin-bottom: 24px" />
      <a-form :model="generateFormState" layout="vertical">
        <a-form-item label="生成天数" required>
          <a-input-number v-model:value="generateFormState.days" :min="1" :max="365" style="width: 100%"
            placeholder="请输入生成天数（1-365）" />
          <div style="color: #999; font-size: 12px; margin-top: 4px">
            指定生成多少天的数据，默认 7 天
          </div>
        </a-form-item>
        <a-form-item label="每天生成行数" required>
          <a-input-number v-model:value="generateFormState.rowsPerDay" :min="1" :max="10000" style="width: 100%"
            placeholder="请输入每天生成的行数（1-10000）" />
          <div style="color: #999; font-size: 12px; margin-top: 4px">
            指定每天生成多少条记录，默认 2000 条
          </div>
        </a-form-item>
        <a-form-item label="目标总行数（可选）">
          <a-input-number v-model:value="generateFormState.targetRows" :min="0" style="width: 100%"
            placeholder="请输入目标总行数（0 表示不限制）" />
          <div style="color: #999; font-size: 12px; margin-top: 4px">
            如果设置了目标总行数，将优先满足总行数要求。设置为 0 表示不限制，按天数 × 每天行数生成
          </div>
        </a-form-item>
        <a-form-item label="是否清空现有数据">
          <a-switch v-model:checked="generateFormState.clearExisting" />
          <div style="color: #999; font-size: 12px; margin-top: 4px">
            开启后将先清空表中的所有数据，再生成新数据，确保数据量与预期一致。关闭则保留现有数据，在现有数据基础上追加。
          </div>
        </a-form-item>
      </a-form>
      <template #footer>
        <div style="text-align: right;">
          <a-button style="margin-right: 8px" @click="handleGenerateDrawerClose">取消</a-button>
          <a-button type="primary" @click="handleGenerateSubmit" :loading="generating">
            生成
          </a-button>
        </div>
      </template>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { PlusOutlined, ReloadOutlined, EditOutlined, DeleteOutlined, PoweroffOutlined, SettingOutlined, HolderOutlined, DownloadOutlined, DownOutlined, ColumnHeightOutlined, CopyOutlined, EyeOutlined, UploadOutlined } from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import dayjs, { Dayjs } from 'dayjs'
import VueDraggable from 'vuedraggable'
import {
  demoExportUsageList,
  createDemoExportUsage,
  updateDemoExportUsage,
  deleteDemoExportUsage,
  generateDemoExportUsage,
  clearDemoExportUsage,
} from '@/api/demoExportUsage'
import { useThrottle } from '@/utils/debounce'
import request from '@/utils/request'

const router = useRouter()

interface DemoUsageFormState {
  id?: number
  tenantCode: string
  usageDate: Dayjs | null
  productCode: string
  productName: string
  planTier: string
  region: string
  usageQty: number | null
  unit: string
  unitPrice: number | null
  amount: number | null
  currency: string
  taxRate: number | null
  billable: boolean
  status: string
  // 新增字段
  description?: string
  priority?: string
  tags?: string[]
  usageTime?: Dayjs | null
  qualityScore?: number
  discountPercentage?: number
  categoryPath?: string[]
  departmentId?: number
  customerName?: string
  themeColor?: string
  apiKey?: string
  searchKeyword?: string
  dateRange?: [Dayjs | null, Dayjs | null]
  selectedFeatures?: string[]
  minValue?: number | null
  maxValue?: number | null
  timeRange?: [Dayjs | null, Dayjs | null]
  paymentMethod?: string
  notes?: string
  attachmentInfo?: any[]
}

const statusColorMap: Record<string, string> = {
  UNBILLED: 'orange',
  BILLED: 'green',
  ADJUSTED: 'blue',
}

const query = ref({
  tenantCode: '',
  productCode: '',
  status: undefined as string | undefined,
})

const tableData = ref<any[]>([])
const loading = ref(false)
const refreshing = ref(false)
const showSortTooltip = ref(true)
const zebraStripeEnabled = ref(true) // 斑马纹开关，默认开启
const cellCopyEnabled = ref(false) // 单元格复制开关，默认关闭
const tableSize = ref<'default' | 'small' | 'middle' | 'large'>('middle') // 表格密度，default 对应 undefined（组件默认值），middle 对应 'middle'，默认使用中等密度
const exporting = ref(false)
const exportingAsync = ref(false)

const pagination = ref({
  current: 1,
  pageSize: 10,
  showSizeChanger: true,
  pageSizeOptions: ['10', '20', '30', '40', '50'],
  total: 0,
  showTotal: (total: number) => `共 ${total} 条`,
})

const paginationConfig = computed(() => {
  const current = Number(pagination.value.current) || 1
  const pageSize = Number(pagination.value.pageSize) || 10
  return {
    ...pagination.value,
    current,
    pageSize,
    total: Number(pagination.value.total) || 0,
  }
})

const INITIAL_COLUMNS = [
  { title: 'ID', dataIndex: 'id', width: 80, sorter: true },
  { title: '租户编码', dataIndex: 'tenantCode', width: 140 },
  { title: '用量日期', dataIndex: 'usageDate', width: 120, sorter: true },
  { title: '产品编码', dataIndex: 'productCode', width: 120 },
  { title: '产品名称', dataIndex: 'productName', width: 160 },
  { title: '用量', dataIndex: 'usageQty', width: 100, sorter: true },
  { title: '单位', dataIndex: 'unit', width: 80 },
  { title: '金额', dataIndex: 'amount', width: 100, sorter: true },
  { title: '币种', dataIndex: 'currency', width: 80 },
  { title: '是否计费', dataIndex: 'billable', width: 100 },
  { title: '状态', dataIndex: 'status', width: 120 },
  // 新增字段列（默认隐藏，可通过列设置显示）
  { title: '描述', dataIndex: 'description', width: 200 },
  { title: '优先级', dataIndex: 'priority', width: 100 },
  { title: '标签', dataIndex: 'tags', width: 150 },
  { title: '使用时间', dataIndex: 'usageTime', width: 100 },
  { title: '质量评分', dataIndex: 'qualityScore', width: 120 },
  { title: '折扣百分比', dataIndex: 'discountPercentage', width: 120 },
  { title: '分类路径', dataIndex: 'categoryPath', width: 200 },
  { title: '部门ID', dataIndex: 'departmentId', width: 100 },
  { title: '客户名称', dataIndex: 'customerName', width: 150 },
  { title: '主题颜色', dataIndex: 'themeColor', width: 100 },
  { title: 'API密钥', dataIndex: 'apiKey', width: 150 },
  { title: '搜索关键词', dataIndex: 'searchKeyword', width: 150 },
  { title: '开始日期', dataIndex: 'startDate', width: 120 },
  { title: '结束日期', dataIndex: 'endDate', width: 120 },
  { title: '选中功能', dataIndex: 'selectedFeatures', width: 150 },
  { title: '最小值', dataIndex: 'minValue', width: 100 },
  { title: '最大值', dataIndex: 'maxValue', width: 100 },
  { title: '开始时间', dataIndex: 'startTime', width: 100 },
  { title: '结束时间', dataIndex: 'endTime', width: 100 },
  { title: '支付方式', dataIndex: 'paymentMethod', width: 120 },
  { title: '备注', dataIndex: 'notes', width: 200 },
  { title: '附件信息', dataIndex: 'attachmentInfo', width: 150 },
  { title: '操作', dataIndex: 'action', width: 160, fixed: 'right' },
]

// 默认显示的列（基础字段 + 部分重要新字段）
const DEFAULT_VISIBLE_COLUMNS = [
  'id', 'tenantCode', 'usageDate', 'productCode', 'productName',
  'usageQty', 'unit', 'amount', 'currency', 'billable', 'status',
  'priority', 'qualityScore', 'customerName', 'action'
]

const allColumns = ref([...INITIAL_COLUMNS])
const draggableColumns = ref([...INITIAL_COLUMNS])
const showColumnKeys = ref(DEFAULT_VISIBLE_COLUMNS.filter((key): key is string => typeof key === 'string'))

const columns = computed(() => {
  // 安全检查：确保 allColumns.value 和 showColumnKeys.value 存在
  if (!allColumns.value || !Array.isArray(allColumns.value) || !showColumnKeys.value || !Array.isArray(showColumnKeys.value)) {
    return []
  }
  // 过滤掉无效的列，并确保列在 showColumnKeys 中
  const filtered = allColumns.value.filter(
    (col) => col && col.dataIndex && typeof col.dataIndex === 'string' && showColumnKeys.value.includes(col.dataIndex),
  )
  // 确保返回的列都有有效的 dataIndex
  return filtered.filter((col) => col && col.dataIndex)
})

// 计算表格横向滚动宽度（所有可见列宽度总和，包含固定列）
const tableScrollX = computed(() => {
  // 安全检查：确保 columns.value 存在且为数组
  if (!columns.value || !Array.isArray(columns.value) || columns.value.length === 0) {
    return 1500 // 返回默认最小宽度
  }
  const totalWidth = columns.value.reduce((sum, col) => {
    if (!col) return sum
    return sum + (col.width || 100) // 如果没有设置宽度，默认 100
  }, 0)
  // 当显示所有列时，总宽度约为 4480px（11个基础列 + 20个新字段列 + 1个操作列）
  // 确保 scroll.x 大于等于所有列的总宽度，才能正确触发横向滚动
  // 添加 100px 缓冲，确保滚动条正常显示
  return totalWidth + 100
})

const tableLocale = computed(() => {
  if (showSortTooltip.value) {
    return {
      triggerDesc: '点击降序',
      triggerAsc: '点击升序',
      cancelSort: '取消排序',
    }
  }
  return undefined
})

async function loadData() {
  loading.value = true
  try {
    const params = {
      tenantCode: query.value.tenantCode.trim(),
      productCode: query.value.productCode.trim(),
      status: query.value.status,
      current: pagination.value.current,
      pageSize: pagination.value.pageSize,
    }
    const res = await demoExportUsageList(params)
    tableData.value = res.records || []
    pagination.value.total = Number(res.total) || 0
  } catch (error: any) {
    message.error('加载测试数据失败: ' + (error?.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.value.current = 1
  loadData()
}

const throttledSearch = useThrottle(handleSearch, 800)

function handleReset() {
  query.value.tenantCode = ''
  query.value.productCode = ''
  query.value.status = undefined
  pagination.value.current = 1
  loadData()
}

const throttledReset = useThrottle(handleReset, 800)

async function handleRefresh() {
  refreshing.value = true
  loading.value = true
  await loadData().catch(() => { })
  setTimeout(() => {
    refreshing.value = false
  }, 800)
  loading.value = false
}

const throttledRefresh = useThrottle(handleRefresh, 1000)

function handlePageChange(page: number) {
  pagination.value.current = page || 1
  loadData()
}

function handlePageSizeChange(_current: number, size: number) {
  pagination.value.pageSize = size || 10
  pagination.value.current = 1
  loadData()
}

const drawerVisible = ref(false)
const drawerMode = ref<'create' | 'edit' | 'view'>('create')
const formState = ref<DemoUsageFormState>({
  tenantCode: '',
  usageDate: null,
  productCode: '',
  productName: '',
  planTier: 'standard',
  region: '',
  usageQty: null,
  unit: '',
  unitPrice: null,
  amount: null,
  currency: 'CNY',
  taxRate: null,
  billable: true,
  status: 'UNBILLED',
  description: '',
  priority: 'MEDIUM',
  tags: [],
  usageTime: null,
  qualityScore: 0,
  discountPercentage: 0,
  categoryPath: [],
  departmentId: undefined,
  customerName: '',
  themeColor: '#1890ff',
  apiKey: '',
  searchKeyword: '',
  dateRange: [null, null],
  selectedFeatures: [],
  minValue: null,
  maxValue: null,
  timeRange: [null, null],
  paymentMethod: 'ALIPAY',
  notes: '',
  attachmentInfo: [],
})

// Cascader 选项数据
const cascaderOptions = [
  {
    value: 'cloud',
    label: '云服务',
    children: [
      {
        value: 'storage',
        label: '存储',
        children: [
          { value: 'oss', label: '对象存储' },
          { value: 'nas', label: '文件存储' },
        ],
      },
      {
        value: 'compute',
        label: '计算',
        children: [
          { value: 'ecs', label: '云服务器' },
          { value: 'container', label: '容器服务' },
        ],
      },
    ],
  },
  {
    value: 'network',
    label: '网络',
    children: [
      { value: 'cdn', label: 'CDN' },
      { value: 'vpc', label: 'VPC' },
    ],
  },
]

// 部门树形数据
const departmentTreeData = [
  {
    title: '技术部',
    value: 1,
    children: [
      { title: '前端组', value: 11 },
      { title: '后端组', value: 12 },
      { title: '测试组', value: 13 },
    ],
  },
  {
    title: '产品部',
    value: 2,
    children: [
      { title: '产品组', value: 21 },
      { title: '设计组', value: 22 },
    ],
  },
  {
    title: '运营部',
    value: 3,
  },
]

// 客户自动完成选项
const customerOptions = ref([
  { value: '阿里巴巴' },
  { value: '腾讯' },
  { value: '百度' },
  { value: '字节跳动' },
  { value: '美团' },
])

// 文件上传处理
function beforeUpload(file: any) {
  const isLt10M = file.size / 1024 / 1024 < 10
  if (!isLt10M) {
    message.error('文件大小不能超过 10MB!')
    return false
  }
  return false // 阻止自动上传，手动处理
}

function handleRemoveFile(file: any) {
  const index = formState.value.attachmentInfo?.findIndex((item: any) => item.uid === file.uid)
  if (index !== undefined && index > -1) {
    formState.value.attachmentInfo?.splice(index, 1)
  }
}

function handleSearchKeyword(value: string) {
  console.log('搜索关键词:', value)
}

// 优先级颜色和文本映射
function getPriorityColor(priority?: string): string {
  const colorMap: Record<string, string> = {
    LOW: 'default',
    MEDIUM: 'blue',
    HIGH: 'orange',
    URGENT: 'red',
  }
  return colorMap[priority || 'MEDIUM'] || 'default'
}

function getPriorityText(priority?: string): string {
  const textMap: Record<string, string> = {
    LOW: '低',
    MEDIUM: '中',
    HIGH: '高',
    URGENT: '紧急',
  }
  return textMap[priority || 'MEDIUM'] || priority || '中'
}

// 支付方式颜色和文本映射
function getPaymentMethodColor(method?: string): string {
  const colorMap: Record<string, string> = {
    ALIPAY: 'blue',
    WECHAT: 'green',
    CREDIT_CARD: 'purple',
    BANK_TRANSFER: 'orange',
  }
  return colorMap[method || 'ALIPAY'] || 'default'
}

function getPaymentMethodText(method?: string): string {
  const textMap: Record<string, string> = {
    ALIPAY: '支付宝',
    WECHAT: '微信支付',
    CREDIT_CARD: '信用卡',
    BANK_TRANSFER: '银行转账',
  }
  return textMap[method || 'ALIPAY'] || method || '支付宝'
}

const generateDrawerVisible = ref(false)
const generating = ref(false)
const generateFormState = ref({
  days: 7,
  rowsPerDay: 2000,
  targetRows: 0,
  clearExisting: false,
})

function openCreateDrawer() {
  drawerMode.value = 'create'
  formState.value = {
    tenantCode: '',
    usageDate: dayjs(),
    productCode: '',
    productName: '',
    planTier: 'standard',
    region: '',
    usageQty: null,
    unit: '',
    unitPrice: null,
    amount: null,
    currency: 'CNY',
    taxRate: null,
    billable: true,
    status: 'UNBILLED',
    description: '',
    priority: 'MEDIUM',
    tags: [],
    usageTime: null,
    qualityScore: 0,
    discountPercentage: 0,
    categoryPath: [],
    departmentId: undefined,
    customerName: '',
    themeColor: '#1890ff',
    apiKey: '',
    searchKeyword: '',
    dateRange: [null, null],
    selectedFeatures: [],
    minValue: null,
    maxValue: null,
    timeRange: [null, null],
    paymentMethod: 'ALIPAY',
    notes: '',
    attachmentInfo: [],
  }
  drawerVisible.value = true
}

function openEditDrawer(record: any) {
  drawerMode.value = 'edit'
  formState.value = {
    id: record.id,
    tenantCode: record.tenantCode || '',
    usageDate: record.usageDate ? dayjs(record.usageDate) : null,
    productCode: record.productCode || '',
    productName: record.productName || '',
    planTier: record.planTier || 'standard',
    region: record.region || '',
    usageQty: record.usageQty,
    unit: record.unit || '',
    unitPrice: record.unitPrice,
    amount: record.amount,
    currency: record.currency || 'CNY',
    taxRate: record.taxRate,
    billable: record.billable ?? true,
    status: record.status || 'UNBILLED',
    description: record.description || '',
    priority: record.priority || 'MEDIUM',
    tags: Array.isArray(record.tags) ? record.tags : (record.tags ? JSON.parse(record.tags) : []),
    usageTime: record.usageTime ? dayjs(record.usageTime, 'HH:mm:ss') : null,
    qualityScore: record.qualityScore ?? 0,
    discountPercentage: record.discountPercentage ?? 0,
    categoryPath: parseCategoryPath(record.categoryPath),
    departmentId: record.departmentId,
    customerName: record.customerName || '',
    themeColor: record.themeColor || '#1890ff',
    apiKey: record.apiKey || '',
    searchKeyword: record.searchKeyword || '',
    dateRange: record.startDate && record.endDate ? [dayjs(record.startDate), dayjs(record.endDate)] : [null, null],
    selectedFeatures: record.selectedFeatures ? (Array.isArray(record.selectedFeatures) ? record.selectedFeatures : JSON.parse(record.selectedFeatures)) : [],
    minValue: record.minValue,
    maxValue: record.maxValue,
    timeRange: record.startTime && record.endTime ? [dayjs(record.startTime, 'HH:mm:ss'), dayjs(record.endTime, 'HH:mm:ss')] : [null, null],
    paymentMethod: record.paymentMethod || 'ALIPAY',
    notes: record.notes || '',
    attachmentInfo: record.attachmentInfo ? (Array.isArray(record.attachmentInfo) ? record.attachmentInfo : JSON.parse(record.attachmentInfo)) : [],
  }
  drawerVisible.value = true
}

function handleView(record: any) {
  drawerMode.value = 'view'
  formState.value = {
    id: record.id,
    tenantCode: record.tenantCode || '',
    usageDate: record.usageDate ? dayjs(record.usageDate) : null,
    productCode: record.productCode || '',
    productName: record.productName || '',
    planTier: record.planTier || 'standard',
    region: record.region || '',
    usageQty: record.usageQty,
    unit: record.unit || '',
    unitPrice: record.unitPrice,
    amount: record.amount,
    currency: record.currency || 'CNY',
    taxRate: record.taxRate,
    billable: record.billable ?? true,
    status: record.status || 'UNBILLED',
    description: record.description || '',
    priority: record.priority || 'MEDIUM',
    tags: Array.isArray(record.tags) ? record.tags : (record.tags ? JSON.parse(record.tags) : []),
    usageTime: record.usageTime ? dayjs(record.usageTime, 'HH:mm:ss') : null,
    qualityScore: record.qualityScore ?? 0,
    discountPercentage: record.discountPercentage ?? 0,
    categoryPath: parseCategoryPath(record.categoryPath),
    departmentId: record.departmentId,
    customerName: record.customerName || '',
    themeColor: record.themeColor || '#1890ff',
    apiKey: record.apiKey || '',
    searchKeyword: record.searchKeyword || '',
    dateRange: record.startDate && record.endDate ? [dayjs(record.startDate), dayjs(record.endDate)] : [null, null],
    selectedFeatures: record.selectedFeatures ? (Array.isArray(record.selectedFeatures) ? record.selectedFeatures : JSON.parse(record.selectedFeatures)) : [],
    minValue: record.minValue,
    maxValue: record.maxValue,
    timeRange: record.startTime && record.endTime ? [dayjs(record.startTime, 'HH:mm:ss'), dayjs(record.endTime, 'HH:mm:ss')] : [null, null],
    paymentMethod: record.paymentMethod || 'ALIPAY',
    notes: record.notes || '',
    attachmentInfo: record.attachmentInfo ? (Array.isArray(record.attachmentInfo) ? record.attachmentInfo : JSON.parse(record.attachmentInfo)) : [],
  }
  drawerVisible.value = true
}

const throttledView = useThrottle(handleView, 500)

function handleDrawerClose() {
  drawerVisible.value = false
}

async function handleSubmit() {
  const payload: any = {
    tenantCode: formState.value.tenantCode,
    usageDate: formState.value.usageDate ? formState.value.usageDate.format('YYYY-MM-DD') : null,
    productCode: formState.value.productCode,
    productName: formState.value.productName,
    planTier: formState.value.planTier,
    region: formState.value.region,
    usageQty: formState.value.usageQty,
    unit: formState.value.unit,
    unitPrice: formState.value.unitPrice,
    amount: formState.value.amount,
    currency: formState.value.currency,
    taxRate: formState.value.taxRate,
    billable: formState.value.billable,
    status: formState.value.status,
    // 新增字段
    description: formState.value.description,
    priority: formState.value.priority,
    tags: formState.value.tags ? JSON.stringify(formState.value.tags) : null,
    usageTime: formState.value.usageTime ? formState.value.usageTime.format('HH:mm:ss') : null,
    qualityScore: formState.value.qualityScore,
    discountPercentage: formState.value.discountPercentage,
    categoryPath: formState.value.categoryPath ? JSON.stringify(formState.value.categoryPath) : null,
    departmentId: formState.value.departmentId,
    customerName: formState.value.customerName,
    themeColor: formState.value.themeColor,
    apiKey: formState.value.apiKey,
    searchKeyword: formState.value.searchKeyword,
    startDate: formState.value.dateRange?.[0] ? formState.value.dateRange[0].format('YYYY-MM-DD') : null,
    endDate: formState.value.dateRange?.[1] ? formState.value.dateRange[1].format('YYYY-MM-DD') : null,
    selectedFeatures: formState.value.selectedFeatures ? JSON.stringify(formState.value.selectedFeatures) : null,
    minValue: formState.value.minValue,
    maxValue: formState.value.maxValue,
    startTime: formState.value.timeRange?.[0] ? formState.value.timeRange[0].format('HH:mm:ss') : null,
    endTime: formState.value.timeRange?.[1] ? formState.value.timeRange[1].format('HH:mm:ss') : null,
    paymentMethod: formState.value.paymentMethod,
    notes: formState.value.notes,
    attachmentInfo: formState.value.attachmentInfo ? JSON.stringify(formState.value.attachmentInfo) : null,
  }
  try {
    if (drawerMode.value === 'create') {
      await createDemoExportUsage(payload)
      message.success('创建测试数据成功')
    } else if (drawerMode.value === 'edit' && formState.value.id != null) {
      await updateDemoExportUsage(formState.value.id, payload)
      message.success('更新测试数据成功')
    }
    drawerVisible.value = false
    loadData()
  } catch (error: any) {
    message.error('保存测试数据失败: ' + (error?.message || '未知错误'))
  }
}

function handleDelete(record: any) {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除记录 ${record.id} 吗？`,
    okText: '确认',
    cancelText: '取消',
    onOk: () => {
      return deleteDemoExportUsage(record.id)
        .then(() => {
          message.success('删除成功')
          loadData()
        })
        .catch((error: any) => {
          message.error('删除失败: ' + (error?.message || '未知错误'))
          return Promise.reject(error)
        })
    },
  })
}

function openGenerateDrawer() {
  generateFormState.value = {
    days: 7,
    rowsPerDay: 2000,
    targetRows: 0,
    clearExisting: false,
  }
  generateDrawerVisible.value = true
}

function handleGenerateDrawerClose() {
  generateDrawerVisible.value = false
}

async function handleGenerateSubmit() {
  if (!generateFormState.value.days || generateFormState.value.days <= 0) {
    message.warning('请输入有效的生成天数')
    return
  }
  if (!generateFormState.value.rowsPerDay || generateFormState.value.rowsPerDay <= 0) {
    message.warning('请输入有效的每天生成行数')
    return
  }

  generating.value = true
  try {
    await generateDemoExportUsage(
      {
        days: generateFormState.value.days,
        rowsPerDay: generateFormState.value.rowsPerDay,
        targetRows: generateFormState.value.targetRows || 0,
        clearExisting: generateFormState.value.clearExisting || false,
      },
      // 单次调用将超时提高到 30 秒，覆盖 axios 实例默认 5 秒
      { timeout: 30000 },
    )
    message.success('已生成测试数据')
    generateDrawerVisible.value = false
    await loadData()
  } catch (error: any) {
    message.error('生成测试数据失败: ' + (error?.message || '未知错误'))
  } finally {
    generating.value = false
  }
}

// 格式化分类路径显示
function formatCategoryPath(categoryPath: any): string {
  if (!categoryPath) {
    return '-'
  }
  if (Array.isArray(categoryPath)) {
    return categoryPath.join(' / ')
  }
  if (typeof categoryPath === 'string') {
    // 检查是否是 JSON 格式
    if (categoryPath.startsWith('[') || categoryPath.startsWith('{')) {
      try {
        const parsed = JSON.parse(categoryPath)
        return Array.isArray(parsed) ? parsed.join(' / ') : String(parsed)
      } catch {
        // JSON 解析失败，返回原字符串
        return categoryPath
      }
    }
    // 普通字符串，直接返回
    return categoryPath
  }
  return '-'
}

// 解析分类路径为数组（用于表单）
function parseCategoryPath(categoryPath: any): string[] {
  if (!categoryPath) {
    return []
  }
  if (Array.isArray(categoryPath)) {
    return categoryPath
  }
  if (typeof categoryPath === 'string') {
    // 检查是否是 JSON 格式
    if (categoryPath.startsWith('[') || categoryPath.startsWith('{')) {
      try {
        const parsed = JSON.parse(categoryPath)
        return Array.isArray(parsed) ? parsed : []
      } catch {
        // JSON 解析失败，尝试按 '/' 分割
        return categoryPath.includes('/') ? categoryPath.split('/') : []
      }
    }
    // 普通字符串，按 '/' 分割
    return categoryPath.includes('/') ? categoryPath.split('/') : [categoryPath]
  }
  return []
}

async function handleClearDemo() {
  Modal.confirm({
    title: '清空测试数据',
    content: '此操作将删除 demo_export_usage 表中的所有记录，仅建议在开发/测试环境使用，确认继续？',
    okText: '确认清空',
    okButtonProps: { danger: true },
    cancelText: '取消',
    onOk: async () => {
      try {
        await clearDemoExportUsage()
        message.success('已清空测试数据')
        loadData()
      } catch (error: any) {
        message.error('清空测试数据失败: ' + (error?.message || '未知错误'))
        return Promise.reject(error)
      }
    },
  })
}

function onCheckAllChange(e: any) {
  // 安全检查：确保 allColumns 存在且为数组
  if (!allColumns.value || !Array.isArray(allColumns.value)) {
    return
  }
  // 设置同步标志，防止触发 watch 监听器
  isSyncingColumns.value = true
  try {
    if (e.target.checked) {
      // 使用 allColumns.value 而不是 INITIAL_COLUMNS，确保与当前列状态一致
      // 过滤掉无效的列（null、undefined 或没有 dataIndex）
      showColumnKeys.value = allColumns.value
        .filter((col) => col && col.dataIndex && typeof col.dataIndex === 'string')
        .map((col) => col.dataIndex as string)
    } else {
      showColumnKeys.value = []
    }
  } finally {
    // 使用 nextTick 确保所有更新完成后再重置标志
    nextTick(() => {
      isSyncingColumns.value = false
    })
  }
}

function onCheckboxChange(dataIndex: string, checked: boolean) {
  // 安全检查：确保 dataIndex 存在，且 showColumnKeys 和 allColumns 都已初始化
  if (!dataIndex || !showColumnKeys.value || !Array.isArray(showColumnKeys.value)) {
    return
  }
  if (!allColumns.value || !Array.isArray(allColumns.value)) {
    return
  }

  // 确保 dataIndex 在 allColumns 中存在
  const columnExists = allColumns.value.some((col) => col?.dataIndex === dataIndex)
  if (!columnExists) {
    console.warn(`列 ${dataIndex} 不存在于 allColumns 中`)
    return
  }

  // 使用 nextTick 确保响应式更新顺序正确
  nextTick(() => {
    if (checked) {
      if (!showColumnKeys.value.includes(dataIndex)) {
        showColumnKeys.value.push(dataIndex)
      }
    } else {
      showColumnKeys.value = showColumnKeys.value.filter((key) => key !== dataIndex)
    }
  })
}

function resetColumnOrder() {
  try {
    // 设置同步标志，防止触发 watch 监听器
    isSyncingColumns.value = true
    try {
      allColumns.value = [...INITIAL_COLUMNS]
      draggableColumns.value = [...INITIAL_COLUMNS]
      showColumnKeys.value = [...DEFAULT_VISIBLE_COLUMNS.filter((key): key is string => typeof key === 'string')]
    } finally {
      // 使用 nextTick 确保所有更新完成后再重置标志
      nextTick(() => {
        isSyncingColumns.value = false
      })
    }
  } catch (error) {
    console.error('重置列顺序失败:', error)
    message.error('重置列顺序失败')
    isSyncingColumns.value = false
  }
}

function onDragEnd(event: any) {
  // 拖拽结束后，同步 draggableColumns 到 allColumns
  // 设置同步标志，防止触发 watch 监听器
  isSyncingColumns.value = true
  try {
    // 直接更新 allColumns，这会触发 watch(allColumns)，但由于 isSyncingColumns 标志，不会形成循环
    const newAllColumns = draggableColumns.value.filter((col) => col && typeof col.dataIndex === 'string')

    // 使用深度比较，避免不必要的更新
    const currentKeys = allColumns.value.map((col) => col?.dataIndex).join(',')
    const newKeys = newAllColumns.map((col) => col?.dataIndex).join(',')

    if (currentKeys !== newKeys) {
      allColumns.value = newAllColumns
      // showColumnKeys 只保留 allColumns 里存在的 dataIndex
      showColumnKeys.value = showColumnKeys.value.filter((key) =>
        allColumns.value.some((col) => col.dataIndex === key),
      )
    }
  } finally {
    // 使用 nextTick 确保所有更新完成后再重置标志
    nextTick(() => {
      isSyncingColumns.value = false
    })
  }
}

// 防止循环更新的标志
const isSyncingColumns = ref(false)

// 监听 allColumns 变化，同步到 draggableColumns（仅在非拖拽场景下）
// 注意：拖拽时 VueDraggable 会直接更新 draggableColumns，不需要通过 watch 同步
watch(
  allColumns,
  (val) => {
    if (isSyncingColumns.value) return
    isSyncingColumns.value = true
    try {
      // 只在 allColumns 变化时同步到 draggableColumns，避免拖拽时的循环更新
      const newDraggableColumns = val.filter((col) => col && typeof col.dataIndex === 'string')
      // 使用深度比较，避免不必要的更新
      const currentKeys = draggableColumns.value.map((col) => col?.dataIndex).join(',')
      const newKeys = newDraggableColumns.map((col) => col?.dataIndex).join(',')
      if (currentKeys !== newKeys) {
        draggableColumns.value = newDraggableColumns
      }
    } finally {
      // 使用 nextTick 确保更新完成后再重置标志
      nextTick(() => {
        isSyncingColumns.value = false
      })
    }
  },
  { deep: true },
)

// 移除 draggableColumns 的 watch 监听器
// 原因：VueDraggable 的 v-model 会直接更新 draggableColumns
// 拖拽时通过 onDragEnd 手动同步到 allColumns，避免循环更新

const tableContentRef = ref<HTMLElement | null>(null)
const paginationRef = ref<HTMLElement | null>(null)
const tableBodyHeight = ref(400)

function updateTableBodyHeight() {
  nextTick(() => {
    // 添加更严格的 null 检查，防止组件卸载后访问
    if (!tableContentRef.value || !paginationRef.value) {
      return
    }
    try {
      const tableHeader = tableContentRef.value.querySelector('.ant-table-header') as HTMLElement
      const containerHeight = tableContentRef.value.clientHeight
      const paginationHeight = paginationRef.value.clientHeight
      const tableHeaderHeight = tableHeader ? tableHeader.clientHeight : 55
      const bodyHeight = containerHeight - paginationHeight - tableHeaderHeight
      tableBodyHeight.value = Math.max(bodyHeight, 200)
    } catch (error) {
      // 静默处理错误，避免在组件卸载时抛出异常
      console.warn('updateTableBodyHeight error:', error)
    }
  })
}


// 存储滚动检测的定时器和观察器
let scrollCheckTimer: number | null = null
let resizeHandler: (() => void) | null = null
let mutationObserver: MutationObserver | null = null

/**
 * 检测并更新复制按钮的显示状态
 * 核心逻辑：检测单元格是否被固定列遮盖，如果被遮盖则隐藏复制按钮
 */
function updateCopyIconVisibility() {
  if (!cellCopyEnabled.value || !tableContentRef.value) {
    return
  }

  nextTick(() => {
    // 为所有普通列的复制按钮添加/移除隐藏类
    const cells = tableContentRef.value?.querySelectorAll('.ant-table-tbody > tr > td:not(.ant-table-cell-fix-right)') || []
    const fixedRightColumn = tableContentRef.value?.querySelector('.ant-table-fixed-right') as HTMLElement

    if (!fixedRightColumn) {
      // 如果没有固定列，显示所有复制按钮
      cells.forEach((cell) => {
        const copyIcon = cell.querySelector('.cell-copy-icon') as HTMLElement
        if (copyIcon) {
          copyIcon.classList.remove('cell-copy-icon-hidden')
        }
      })
      return
    }

    const fixedColumnRect = fixedRightColumn.getBoundingClientRect()
    const fixedColumnLeft = fixedColumnRect.left

    // 优化：只检测可见的单元格
    const visibleCells = Array.from(cells).filter(cell => {
      const rect = cell.getBoundingClientRect()
      return rect.width > 0 && rect.height > 0
    })

    visibleCells.forEach((cell) => {
      const cellRect = cell.getBoundingClientRect()
      const cellRight = cellRect.right
      const copyIcon = cell.querySelector('.cell-copy-icon') as HTMLElement

      if (copyIcon) {
        // 关键修复：如果单元格的右边缘接近或超过固定列左边缘，隐藏复制按钮
        // 容差值：复制按钮宽度 16px + padding 4px + 安全边距 10px = 30px
        // 使用 >= 判断，确保复制按钮完全不会显示在固定列区域内
        if (cellRight >= fixedColumnLeft - 30) {
          copyIcon.classList.add('cell-copy-icon-hidden')
        } else {
          copyIcon.classList.remove('cell-copy-icon-hidden')
        }
      }
    })
  })
}

onMounted(() => {
  loadData()
  updateTableBodyHeight()
  window.addEventListener('resize', updateTableBodyHeight)

  // 监听表格滚动，动态更新复制按钮显示状态
  if (tableContentRef.value) {
    const tableBody = tableContentRef.value.querySelector('.ant-table-body') as HTMLElement
    if (tableBody) {
      const handleScroll = () => {
        // 使用防抖，避免频繁计算
        if (scrollCheckTimer) {
          clearTimeout(scrollCheckTimer)
        }
        scrollCheckTimer = window.setTimeout(() => {
          updateCopyIconVisibility()
        }, 50)
      }

      tableBody.addEventListener('scroll', handleScroll, { passive: true })

      // 初始检测 - 延迟执行，确保表格完全渲染
      setTimeout(() => {
        updateCopyIconVisibility()
      }, 200)

      // 监听窗口大小变化
      resizeHandler = () => {
        setTimeout(() => {
          updateCopyIconVisibility()
        }, 100)
      }
      window.addEventListener('resize', resizeHandler)

      // 使用 MutationObserver 监听 DOM 变化，确保在表格更新后重新检测
      mutationObserver = new MutationObserver(() => {
        if (cellCopyEnabled.value) {
          setTimeout(() => {
            updateCopyIconVisibility()
          }, 100)
        }
      })

      if (tableBody) {
        mutationObserver.observe(tableBody, {
          childList: true,
          subtree: true,
        })
      }
    }
  }
})

onBeforeUnmount(() => {
  // 清理事件监听器，防止内存泄漏
  window.removeEventListener('resize', updateTableBodyHeight)
  if (resizeHandler) {
    window.removeEventListener('resize', resizeHandler)
    resizeHandler = null
  }
  if (scrollCheckTimer) {
    clearTimeout(scrollCheckTimer)
    scrollCheckTimer = null
  }
  if (mutationObserver) {
    mutationObserver.disconnect()
    mutationObserver = null
  }
})

watch(
  () => pagination.value.pageSize,
  () => {
    updateTableBodyHeight()
  },
)

// 监听复制功能开关，更新复制按钮显示状态
watch(
  () => cellCopyEnabled.value,
  () => {
    if (cellCopyEnabled.value) {
      setTimeout(() => {
        updateCopyIconVisibility()
      }, 100)
    }
  },
)

// 监听列变化，更新复制按钮显示状态
watch(
  () => columns.value,
  () => {
    if (cellCopyEnabled.value) {
      setTimeout(() => {
        updateCopyIconVisibility()
      }, 100)
    }
  },
  { deep: true }
)


// 获取行类名，用于斑马纹和悬停效果
function getRowClassName(_record: any, index: number) {
  if (!zebraStripeEnabled.value) {
    return ''
  }
  return index % 2 === 0 ? 'table-row-even' : 'table-row-odd'
}

// 处理表格密度菜单点击
function handleDensityMenuClick({ key }: { key: string }) {
  if (key === 'default' || key === 'small' || key === 'middle' || key === 'large') {
    tableSize.value = key as 'default' | 'small' | 'middle' | 'large'
    // 密度变化时，重新计算表格高度
    updateTableBodyHeight()
  }
}

// 处理单元格复制
function handleCellCopy(value: any, columnTitle: string) {
  if (!cellCopyEnabled.value) {
    return
  }

  try {
    // 将值转换为字符串
    const textToCopy = value !== null && value !== undefined ? String(value) : ''

    if (!textToCopy.trim()) {
      message.warning('单元格内容为空，无法复制')
      return
    }

    // 使用 Clipboard API 复制文本
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(textToCopy)
        .then(() => {
          const title = columnTitle || '单元格'
          message.success(`已复制 ${title}：${textToCopy}`)
        })
        .catch((error) => {
          console.error('复制失败:', error)
          // 降级方案：使用传统方法
          fallbackCopyTextToClipboard(textToCopy, columnTitle)
        })
    } else {
      // 降级方案：使用传统方法
      fallbackCopyTextToClipboard(textToCopy, columnTitle)
    }
  } catch (error) {
    console.error('复制处理错误:', error)
    message.error('复制失败：' + (error instanceof Error ? error.message : '未知错误'))
  }
}

// 降级复制方案（兼容旧浏览器）
function fallbackCopyTextToClipboard(text: string, columnTitle: string) {
  try {
    const textArea = document.createElement('textarea')
    textArea.value = text
    textArea.style.position = 'fixed'
    textArea.style.left = '-999999px'
    textArea.style.top = '-999999px'
    document.body.appendChild(textArea)
    textArea.focus()
    textArea.select()

    const successful = document.execCommand('copy')
    document.body.removeChild(textArea)

    if (successful) {
      const title = columnTitle || '单元格'
      message.success(`已复制 ${title}：${text}`)
    } else {
      message.error('复制失败，请手动复制')
    }
  } catch (err) {
    console.error('降级复制方案失败:', err)
    message.error('复制失败，请手动复制')
  }
}

// 构建导出请求的列定义（排除操作列，只导出当前显示的列）
const getExportColumns = () => {
  return allColumns.value
    .filter(col => col.dataIndex !== 'action' && showColumnKeys.value.includes(col.dataIndex))
    .map(col => ({
      title: col.title,
      field: col.dataIndex as string,
    }))
}

// 构建导出请求的过滤条件
const getExportFilters = () => {
  const filters: Record<string, any> = {}
  if (query.value.tenantCode?.trim()) {
    filters.tenantCode = query.value.tenantCode.trim()
  }
  if (query.value.productCode?.trim()) {
    filters.productCode = query.value.productCode.trim()
  }
  if (query.value.status) {
    filters.status = query.value.status
  }
  return filters
}

// 同步导出当前页数据
async function handleExportSync() {
  exporting.value = true
  try {
    const baseFilters = getExportFilters()
    const pageFilters: Record<string, any> = {
      ...baseFilters,
      __mode: 'page',
      __page: pagination.value.current,
      __pageSize: pagination.value.pageSize,
    }

    const exportRequest = {
      fileName: 'demo_export_usage',
      pageSize: pagination.value.pageSize,
      async: false,
      sheets: [
        {
          sheetName: '导出测试数据',
          exportType: 'demo_export_usage',
          // 导出当前页：在基础查询条件上增加分页信息
          filters: pageFilters,
          columns: getExportColumns(),
        },
      ],
    }

    const blob = await request.post<Blob>('/export/sync', exportRequest, {
      responseType: 'blob' as any,
    })

    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `demo_export_usage_${dayjs().format('YYYYMMDD_HHmmss')}.xlsx`
    a.click()
    window.URL.revokeObjectURL(url)
    message.success('导出成功，文件已开始下载')
  } catch (error: any) {
    message.error('导出失败: ' + (error?.message || '未知错误'))
  } finally {
    exporting.value = false
  }
}

// 异步导出全部数据
async function handleExportAsync() {
  exportingAsync.value = true
  try {
    const exportRequest = {
      fileName: 'demo_export_usage',
      pageSize: 5000, // 异步导出使用较大的分页大小
      async: true,
      sheets: [
        {
          sheetName: '导出测试数据',
          exportType: 'demo_export_usage',
          filters: getExportFilters(),
          columns: getExportColumns(),
        },
      ],
    }

    const res = await request.post<{ taskId: string }>('/export/async', exportRequest)
    const taskId = (res as any)?.taskId
    if (taskId) {
      Modal.success({
        title: '异步导出任务已创建',
        content: `任务ID: ${taskId}，请在"导出任务"页面查看进度并下载文件`,
        okText: '前往导出任务',
        onOk: () => {
          // 使用 Vue Router 导航到导出任务页面，并带上 taskId 作为查询参数进行过滤
          router.push({
            path: '/export/task',
            query: { taskId }
          })
        },
      })
    } else {
      message.success('异步导出任务已提交，请在"导出任务"页面查看进度')
    }
  } catch (error: any) {
    message.error('异步导出失败: ' + (error?.message || '未知错误'))
  } finally {
    exportingAsync.value = false
  }
}
</script>

<style scoped>
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

.form-container {
  padding: 24px;
  border-bottom: 1px solid #f0f0f0;
  background: transparent;
}

/* 表格标题工具栏容器 - 固定在顶部，不随表格滚动 */
.toolbar-container {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #f0f0f0;
  padding: 8px 24px;
  flex-shrink: 0;
  /* 防止工具栏被压缩 */
  background-color: #fff;
  /* 确保背景色，避免滚动时内容透过 */
}

.table-container {
  display: flex;
  /* 启用flex布局 */
  flex-direction: column;
  /* 垂直排列子元素 */
  flex: 1;
  /* 占满父容器剩余空间 */
  min-height: 0;
  /* 关键，防止撑开 */
  width: 100%;
  overflow: hidden;
}

.table-scroll-container {
  min-height: 0;
  overflow: auto;
  /* 隐藏自身滚动条（含横向），但保留滚动能力，统一与 ExportTask.vue 体验 */
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.table-scroll-container::-webkit-scrollbar {
  display: none;
}

/* 数据为空时，AntD 会通过 ant-table-content/placeholder 渲染，占用的仍是内容区域滚动条，继续隐藏 */
:deep(.ant-table-content) {
  scrollbar-width: none;
  -ms-overflow-style: none;
}

:deep(.ant-table-content::-webkit-scrollbar) {
  display: none;
}

.pagination-container {
  display: flex;
  /* 启用flex布局 */
  align-items: center;
  /* 垂直居中 */
  justify-content: space-between;
  /* 左右分布 */
  background: #fff;
  /* 可选，分页条背景 */
  padding: 12px 24px;
  /* 上下留白，确保有足够空间垂直居中 */
  min-height: 56px;
  /* 最小高度，确保有足够的垂直空间 */
}

.pagination-left {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 100%;
}

.export-group {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  background: transparent;
}

.export-group .export-btn {
  height: 32px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.export-group .export-btn:hover {
  z-index: 1;
  position: relative;
}

.export-group .export-btn[type="primary"] {
  background: #1890ff;
  color: #fff;
  border-color: #1890ff;
}

.export-group .export-btn[type="primary"]:hover {
  background: #40a9ff;
  border-color: #40a9ff;
}

:deep(.ant-pagination) {
  display: flex !important;
  flex-direction: row !important;
  /* 强制横向排列 */
  align-items: center !important;
}

/* 禁止表格单元格换行，超出内容使用省略号，避免"跨行"撑高行高 */
:deep(.ant-table-cell) {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 质量评分列特殊处理，允许 Rate 组件正常显示 */
:deep(.ant-table-cell .quality-score-cell) {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  width: 100%;
  overflow: visible;
  white-space: normal;
}

:deep(.ant-table-cell .quality-score-cell .ant-rate) {
  font-size: 14px;
  line-height: 1;
  display: inline-flex;
  flex-shrink: 0;
}

:deep(.ant-table-cell .quality-score-cell .ant-rate-star) {
  margin-right: 4px;
  font-size: 14px;
}

/* 隐藏表格内容区的滚动条，但保留滚动功能 */
:deep(.ant-table-body) {
  scrollbar-width: none;
  -ms-overflow-style: none;
}

:deep(.ant-table-body::-webkit-scrollbar) {
  display: none;
}

/* 现代化表格样式：斑马纹和行悬停效果 */
:deep(.ant-table-tbody > tr) {
  transition: background-color 0.2s ease, box-shadow 0.2s ease;
  cursor: default;
}

/* 偶数行背景色（仅在斑马纹开启时生效） */
:deep(.ant-table-tbody > tr.table-row-even) {
  background-color: #fafbfc;
}

/* 奇数行背景色（仅在斑马纹开启时生效） */
:deep(.ant-table-tbody > tr.table-row-odd) {
  background-color: #fff;
}

/* 当斑马纹关闭时，所有行使用统一背景 */
:deep(.ant-table-tbody > tr:not(.table-row-even):not(.table-row-odd)) {
  background-color: #fff;
}

/* 行悬停效果 */
:deep(.ant-table-tbody > tr:hover) {
  background-color: #f0f7ff !important;
  box-shadow: 0 1px 4px rgba(24, 144, 255, 0.1);
  transform: translateY(-1px);
}

/* 表头样式优化 */
:deep(.ant-table-thead > tr > th) {
  background-color: #fafafa;
  font-weight: 600;
  border-bottom: 2px solid #e8e8e8;
}

/* 表格边框优化 */
:deep(.ant-table) {
  border-radius: 8px;
  overflow: hidden;
}

/* 单元格内边距由 size 属性控制，移除固定 padding 以支持密度配置 */
:deep(.ant-table-tbody > tr > td) {
  border-bottom: 1px solid #f0f0f0;
}

/* 空状态优化 */
:deep(.ant-empty) {
  padding: 48px 0;
}

:deep(.ant-empty-description) {
  color: #8c8c8c;
  font-size: 14px;
}

/* 加载状态优化 */
:deep(.ant-spin-nested-loading) {
  min-height: 200px;
}

:deep(.ant-spin-container) {
  transition: opacity 0.3s ease;
}

.ml-2 {
  margin-left: 8px;
}

.table-title {
  font-size: 16px;
  font-weight: bold;
  color: #222;
}

.table-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.toolbar-btn {
  border-radius: 4px;
  height: 32px;
  padding: 0 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.action-icon {
  font-size: 18px;
  cursor: pointer;
  color: #595959;
  border-radius: 4px;
  padding: 8px;
  transition: color 0.2s, background 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 32px;
  min-height: 32px;
}

.action-icon:hover {
  color: #1890ff;
  background: #f5f5f5;
}

.action-icon.active {
  color: #1890ff;
  background: #e6f7ff;
}

.zebra-stripe-switch {
  display: flex;
  align-items: center;
  padding: 4px;
  cursor: pointer;
}

.table-density-menu {
  padding: 8px 0;
}

.density-title {
  font-size: 14px;
  font-weight: 500;
  color: #262626;
  margin-bottom: 12px;
}

.action-buttons {
  display: flex;
  align-items: center;
  gap: 4px;
  justify-content: center;
}

.action-btn {
  padding: 2px 4px;
  height: auto;
  line-height: 1.2;
  font-size: 12px;
}

/* 单元格复制功能样式 */
.cell-text {
  display: block;
  width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: text;
  user-select: text;
  padding: 0;
  margin: 0;
  border-radius: 2px;
  box-sizing: border-box;
}

.cell-copy-icon {
  position: absolute;
  top: 4px;
  right: 4px;
  opacity: 0.4;
  font-size: 12px;
  color: #8c8c8c;
  transition: opacity 0.2s ease, color 0.2s ease, transform 0.2s ease;
  z-index: 1;
  /* 降低 z-index 到 1，确保固定列（z-index: 10）始终在最上层 */
  background-color: rgba(255, 255, 255, 0.9);
  padding: 2px;
  border-radius: 2px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
  pointer-events: auto;
  line-height: 1;
  cursor: pointer;
  width: 16px;
  height: 16px;
  margin: 0;
}

/* 当鼠标悬停在单元格上时，图标更明显 */
:deep(.ant-table-tbody > tr > td:hover .cell-copy-icon) {
  opacity: 1;
  color: #1890ff;
  transform: scale(1.1);
  box-shadow: 0 2px 4px rgba(24, 144, 255, 0.2);
}

/* 当鼠标悬停在图标上时，图标更突出 */
.cell-copy-icon:hover {
  opacity: 1 !important;
  color: #1890ff !important;
  transform: scale(1.15);
  box-shadow: 0 2px 6px rgba(24, 144, 255, 0.3);
}

/* 确保复制图标在单元格内正确显示，图标浮动不影响内容布局 */
/* 关键修复：使用 overflow: hidden 防止复制按钮显示在单元格外部 */
/* 当单元格被固定列遮盖时，复制按钮也会被裁剪，不会显示在固定列区域内 */
:deep(.ant-table-tbody > tr > td:not(.ant-table-cell-fix-right):not(.ant-table-cell-fix-left)) {
  position: relative;
  /* 使用 overflow: hidden 防止复制按钮显示在单元格外部 */
  overflow: hidden;
}

/* 固定列不需要 overflow: hidden，保持正常显示 */
:deep(.ant-table-fixed-right .ant-table-tbody > tr > td),
:deep(.ant-table-fixed-left .ant-table-tbody > tr > td) {
  overflow: visible;
}

/* 确保文本内容可以正常显示省略号 */
:deep(.ant-table-tbody > tr > td .cell-text) {
  /* 文本内容需要 overflow: hidden 来显示省略号 */
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 关键修复：确保复制按钮不会显示在固定列区域内 */
/* 通过 overflow: hidden 在单元格级别裁剪，复制按钮如果超出单元格边界会被隐藏 */
:deep(.ant-table-tbody > tr > td:not(.ant-table-cell-fix-right):not(.ant-table-cell-fix-left) .cell-copy-icon) {
  /* 按钮位置在单元格内，如果单元格被固定列遮盖，按钮也会被裁剪 */
  right: 4px;
  /* 确保按钮在单元格内，不会超出边界 */
  /* 使用 clip-path 作为额外保护，确保按钮不会显示在固定列区域 */
  clip-path: inset(0);
}

/* 隐藏被固定列遮盖的复制按钮 */
.cell-copy-icon-hidden {
  display: none !important;
}

/* 确保固定列始终在最上层，遮盖普通列的复制按钮 */
:deep(.ant-table-fixed-right),
:deep(.ant-table-fixed-left) {
  z-index: 100 !important;
  /* 提高 z-index 到 100，确保固定列始终在最上层，完全遮盖普通列的内容 */
  position: relative;
}

/* 固定列内的单元格也应该有较高的 z-index */
:deep(.ant-table-fixed-right .ant-table-tbody > tr > td),
:deep(.ant-table-fixed-left .ant-table-tbody > tr > td) {
  position: relative;
  z-index: 100;
}

/* 确保固定列的表头也在最上层 */
:deep(.ant-table-fixed-right .ant-table-thead > tr > th),
:deep(.ant-table-fixed-left .ant-table-thead > tr > th) {
  z-index: 100;
  position: relative;
}

/* 确保开启复制功能时，单元格和表格整体布局不受影响 */
:deep(.ant-table-tbody > tr > td .cell-text) {
  width: 100%;
  box-sizing: border-box;
  min-width: 0;
  max-width: 100%;
}

/* 确保表格整体宽度不受复制功能影响，使用固定布局避免表头和数据错位 */
:deep(.ant-table) {
  table-layout: fixed;
  width: 100%;
  max-width: 100%;
}

:deep(.ant-table-thead > tr > th),
:deep(.ant-table-tbody > tr > td) {
  max-width: 100%;
  box-sizing: border-box;
}

/* 确保表格容器不会因为复制功能而被撑开 */
:deep(.ant-table-container) {
  width: 100%;
  max-width: 100%;
  overflow: hidden;
}

:deep(.ant-table-body) {
  width: 100%;
  max-width: 100%;
}

.draggable-columns {
  max-height: 300px;
  overflow-y: auto;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.draggable-columns::-webkit-scrollbar {
  display: none;
}

.draggable-column-item {
  display: flex;
  align-items: center;
  padding: 4px 2px;
  margin-bottom: 4px;
  background: transparent;
  border-radius: 4px;
  transition: background-color 0.2s ease;
  cursor: default;
}

.draggable-column-item:hover {
  background-color: #f5f5f5;
}

.draggable-column-item.sortable-ghost {
  opacity: 0.5;
  background: #e6f7ff;
}

.draggable-column-item.sortable-chosen {
  background: #e6f7ff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.drag-handle {
  margin-right: 8px;
  color: #bfbfbf;
  font-size: 16px;
  cursor: move;
  transition: color 0.2s;
}

.drag-handle:hover {
  color: #1890ff;
}

:deep(.ant-pagination),
:deep(.ant-pagination-item),
:deep(.ant-pagination-item-link) {
  height: 32px !important;
  /* 保证高度一致 */
  line-height: 32px !important;
  /* 保证内容垂直居中 */
  min-width: 32px;
  /* 保证宽度一致 */
  box-sizing: border-box;
  vertical-align: middle;
}

:deep(.ant-pagination-item-container) {
  /* 增加一点右边距，防止和"下一页"按钮重叠 */
  margin-right: 8px;
}

/* 修正省略号垂直居中 */
:deep(.ant-pagination-item-ellipsis) {
  line-height: 32px !important;
  /* AntD 默认高度 */
  vertical-align: middle !important;
  display: inline-block !important;
  font-size: 16px !important;
}

/* 保证分页条整体高度和内容一致 */
:deep(.ant-pagination) {
  min-height: 32px !important;
  height: 32px !important;
  line-height: 32px !important;
}

/* 保证每个分页项高度一致 */
:deep(.ant-pagination-item),
:deep(.ant-pagination-item-link),
:deep(.ant-pagination-prev),
:deep(.ant-pagination-next),
:deep(.ant-pagination-jump-next),
:deep(.ant-pagination-jump-prev) {
  height: 32px !important;
  min-width: 32px !important;
  line-height: 32px !important;
  box-sizing: border-box;
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
  padding: 0 !important;
}
</style>
