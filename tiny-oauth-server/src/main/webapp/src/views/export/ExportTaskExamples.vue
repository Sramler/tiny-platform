<template>
    <div class="examples-container">
        <a-typography-title :level="5" class="title">导出任务使用说明</a-typography-title>
        <a-typography-paragraph class="desc">
            导出任务应在各业务页面发起，例如用户列表导出、订单明细导出、用量账单导出等。
            本页作为“导出任务中心”，主要用于统一查看各类导出任务的进度、状态和下载结果，
            下方示例以 <code>demo_export_usage</code>（用量/账单型数据）作为演示数据源。
        </a-typography-paragraph>

        <a-typography-title :level="5" class="section">典型流程</a-typography-title>
        <ol class="steps">
            <li>在业务页面选择过滤条件并点击“导出”。</li>
            <li>前端调用后端导出接口：
                <ul>
                    <li><code>POST /export/sync</code>（小数据量，同步返回文件）。</li>
                    <li><code>POST /export/async</code>（大数据量，后台任务，返回 taskId）。</li>
                </ul>
            </li>
            <li>异步任务提交后，返回的 <code>taskId</code> 可在本页面查询、等待完成并下载。</li>
        </ol>

        <a-typography-title :level="5" class="section">接口示例（基于 demo_export_usage）</a-typography-title>
        <div class="api-layout">
            <div class="api-card">
                <div class="api-header">
                    <span class="api-title">同步导出（小数据量）</span>
                    <a-tag color="blue">POST /export/sync</a-tag>
                </div>
                <a-typography-paragraph class="api-desc">
                    适合记录量较少、可以在一次请求中直接生成并返回文件的场景，例如几十条到几百条账单明细。
                </a-typography-paragraph>
                <a-descriptions size="small" :column="1" bordered>
                    <a-descriptions-item label="fileName">demo_export_usage</a-descriptions-item>
                    <a-descriptions-item label="columns">
                        tenant_code, usage_date, product_code, product_name, amount, currency, status ...
                    </a-descriptions-item>
                    <a-descriptions-item label="filters">
                        {"{"} "is_billable": true {"}"}（仅导出可计费记录）
                    </a-descriptions-item>
                </a-descriptions>
                <div class="card-actions">
                    <a-space>
                        <a-button type="primary" size="small" @click="triggerSyncExport">
                            发起同步导出（demo_export_usage）
                        </a-button>
                        <a-button type="link" size="small" @click="showSyncDemo">
                            查看请求示例
                        </a-button>
                    </a-space>
                </div>
            </div>

            <div class="api-card">
                <div class="api-header">
                    <span class="api-title">异步导出（大数据量）</span>
                    <a-tag color="green">POST /export/async</a-tag>
                </div>
                <a-typography-paragraph class="api-desc">
                    适合上万行甚至百万行以上的数据导出，后端会创建 ExportTask 任务并在后台生成文件，完成后在本页查看状态并下载。
                </a-typography-paragraph>
                <a-descriptions size="small" :column="1" bordered>
                    <a-descriptions-item label="fileName">demo_export_usage</a-descriptions-item>
                    <a-descriptions-item label="columns">
                        与同步导出相同，保持列定义一致，便于统一报表。
                    </a-descriptions-item>
                    <a-descriptions-item label="filters">
                        {"{"} "is_billable": true {"}"} 或按日期区间、租户ID等条件构造查询。
                    </a-descriptions-item>
                </a-descriptions>
                <div class="card-actions">
                    <a-space>
                        <a-button type="primary" size="small" @click="triggerAsyncExport">
                            发起异步导出任务（demo_export_usage）
                        </a-button>
                        <a-button type="link" size="small" @click="showAsyncDemo">
                            查看请求示例
                        </a-button>
                    </a-space>
                </div>
            </div>
        </div>

        <a-alert class="hint" message="提示" type="info" show-icon
            description="提交后请在本页通过 taskId 查看进度，状态为 SUCCESS 时可点击下载。" />
    </div>
</template>

<script setup lang="ts">
import { h } from 'vue'
import { Modal, message } from 'ant-design-vue'
import request from '@/utils/request'
import { getTenantId } from '@/utils/tenant'

// 符合后端 ExportRequest/SheetConfig 结构的示例请求体
function resolveTenantId(): string | undefined {
    const raw = getTenantId()
    return raw ? String(raw) : undefined
}

function buildDemoExportRequest(asyncFlag = false) {
    const tenantId = resolveTenantId()
    return {
        fileName: 'demo_export_usage',
        pageSize: 5000,
        async: asyncFlag,
        sheets: [
            {
                sheetName: '用量明细',
                exportType: 'demo_export_usage', // 需要在后端注册对应 DataProvider
                filters: {
                    is_billable: true,
                    ...(tenantId ? { tenantId } : {}),
                },
                // 简化的列定义：字段名需与 DemoExportUsageRow 属性一致
                columns: [
                    { title: '租户ID', field: 'tenantId' },
                    { title: '用量日期', field: 'usageDate' },
                    { title: '产品编码', field: 'productCode' },
                    { title: '产品名称', field: 'productName' },
                    { title: '套餐档位', field: 'planTier' },
                    { title: '区域', field: 'region' },
                    { title: '用量', field: 'usageQty' },
                    { title: '单位', field: 'unit' },
                    { title: '单价', field: 'unitPrice' },
                    { title: '金额', field: 'amount' },
                    { title: '币种', field: 'currency' },
                    { title: '税率', field: 'taxRate' },
                    { title: '是否计费', field: 'billable' },
                    { title: '状态', field: 'status' },
                ],
                // aggregateKey/options 可选，示例中暂不使用
            },
        ],
    }
}

function showSyncDemo() {
    const demoExportRequest = buildDemoExportRequest(false)
    Modal.info({
        title: '同步导出示例（demo_export_usage）',
        width: 520,
        content: () =>
            h('pre', { class: 'modal-code' }, [
                `POST /export/sync\n`,
                `Content-Type: application/json\n\n`,
                JSON.stringify(demoExportRequest, null, 2)
            ].join('')),
        onOk: () => message.success('示例仅供参考，请在业务页触发实际导出')
    })
}

function showAsyncDemo() {
    const demoExportRequest = buildDemoExportRequest(true)
    Modal.info({
        title: '异步导出示例（demo_export_usage）',
        width: 520,
        content: () =>
            h('pre', { class: 'modal-code' }, [
                `POST /export/async\n`,
                `Content-Type: application/json\n\n`,
                JSON.stringify(
                    {
                        ...demoExportRequest,
                        async: true,
                    },
                    null,
                    2,
                )
            ].join('')),
        onOk: () => message.success('示例仅供参考，请在业务页触发实际导出')
    })
}

async function triggerSyncExport() {
    try {
        const demoExportRequest = buildDemoExportRequest(false)
        const blob = await request.post<Blob>('/export/sync', demoExportRequest, {
            // axios responseType，TS 这里简单断言
            responseType: 'blob' as any
        })
        const url = window.URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = 'demo_export_usage.xlsx'
        a.click()
        window.URL.revokeObjectURL(url)
        message.success('已发起同步导出（demo_export_usage），请查看下载的文件')
    } catch (error: any) {
        message.error('同步导出示例调用失败：' + (error?.message || '未知错误'))
    }
}

async function triggerAsyncExport() {
    try {
        const demoExportRequest = buildDemoExportRequest(true)
        const res = await request.post<{ taskId: string }>('/export/async', {
            ...demoExportRequest,
            async: true,
        })
        const taskId = (res as any)?.taskId
        if (taskId) {
            message.success(`异步导出任务已创建，taskId=${taskId}，可在列表中刷新查看`)
        } else {
            message.success('异步导出任务已提交，请在列表中刷新查看')
        }
    } catch (error: any) {
        message.error('异步导出示例调用失败：' + (error?.message || '未知错误'))
    }
}
</script>

<style scoped>
.examples-container {
    padding: 24px;
}

.title {
    margin-bottom: 12px;
}

.desc {
    color: #595959;
    margin-bottom: 16px;
}

.section {
    margin-top: 16px;
    margin-bottom: 8px;
}

.steps {
    margin: 0 0 12px 16px;
    padding: 0;
}

.steps li {
    margin-bottom: 6px;
    color: #595959;
}

.steps code {
    background: #f5f5f5;
    padding: 0 4px;
    border-radius: 2px;
}

.api-layout {
    display: flex;
    flex-direction: column;
    gap: 12px;
}

.api-card {
    border: 1px solid #f0f0f0;
    border-radius: 4px;
    padding: 12px 16px;
    background: #fafafa;
}

.api-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 8px;
}

.api-title {
    font-weight: 500;
    color: #262626;
}

.api-desc {
    color: #595959;
    margin-bottom: 8px;
}

.card-actions {
    margin-top: 8px;
    text-align: right;
}

.modal-code {
    background: #f5f5f5;
    padding: 12px;
    border-radius: 4px;
    font-family: Menlo, Monaco, Consolas, "Courier New", monospace;
    font-size: 12px;
    line-height: 1.6;
    white-space: pre-wrap;
    margin: 0;
}

.hint {
    margin-top: 16px;
}
</style>
