<template>
  <div class="tenant-create-wizard" data-test="tenant-create-wizard">
    <a-steps :current="currentStep" class="wizard-steps">
      <a-step title="基础信息" />
      <a-step title="初始化策略" />
      <a-step title="初始管理员" />
      <a-step title="确认" />
    </a-steps>

    <div class="wizard-content">
      <div v-if="submitState === 'submit-success'" class="wizard-result" data-test="wizard-submit-success">
        <a-alert
          type="success"
          show-icon
          message="租户创建成功"
          description="初始化已完成，请按需进入租户详情页查看详情、权限摘要与模板差异。"
        />
        <div class="confirm-section">
          <div class="confirm-title">创建结果</div>
          <div class="confirm-grid">
            <div class="confirm-item">
              <span class="label">新租户 ID</span>
              <span>{{ submitResult?.tenantId ?? '-' }}</span>
            </div>
            <div class="confirm-item">
              <span class="label">新租户编码</span>
              <span>{{ displayValue(submitResult?.tenantCode) }}</span>
            </div>
            <div class="confirm-item">
              <span class="label">新租户名称</span>
              <span>{{ displayValue(submitResult?.tenantName) }}</span>
            </div>
            <div class="confirm-item">
              <span class="label">初始管理员账号</span>
              <span>{{ displayValue(submitResult?.initialAdminUsername) }}</span>
            </div>
          </div>
        </div>
        <div v-if="submitResult?.initializationSummary" class="confirm-section">
          <div class="confirm-title">初始化摘要</div>
          <div class="confirm-grid">
            <div class="confirm-item">
              <span class="label">租户编码</span>
              <span>{{ displayValue(submitResult.initializationSummary.tenantCode) }}</span>
            </div>
            <div class="confirm-item">
              <span class="label">租户名称</span>
              <span>{{ displayValue(submitResult.initializationSummary.tenantName) }}</span>
            </div>
            <div class="confirm-item">
              <span class="label">初始管理员用户名</span>
              <span>{{ displayValue(submitResult.initializationSummary.initialAdminUsername) }}</span>
            </div>
            <div class="confirm-item">
              <span class="label">平台模板就绪</span>
              <span>{{ submitResult.initializationSummary.platformTemplateReady ? '是' : '否' }}</span>
            </div>
            <div class="confirm-item">
              <span class="label">默认角色数</span>
              <span>{{ submitResult.initializationSummary.defaultRoleCount }}</span>
            </div>
            <div class="confirm-item">
              <span class="label">默认菜单数</span>
              <span>{{ submitResult.initializationSummary.defaultMenuCount }}</span>
            </div>
            <div class="confirm-item">
              <span class="label">默认 UI Action 数</span>
              <span>{{ submitResult.initializationSummary.defaultUiActionCount }}</span>
            </div>
            <div class="confirm-item">
              <span class="label">默认 API Endpoint 数</span>
              <span>{{ submitResult.initializationSummary.defaultApiEndpointCount }}</span>
            </div>
            <div class="confirm-item">
              <span class="label">默认权限数</span>
              <span>{{ submitResult.initializationSummary.defaultPermissionCount }}</span>
            </div>
          </div>
        </div>
        <div class="result-actions" data-test="wizard-result-actions">
          <a-button data-test="wizard-go-overview" @click="goToTenantDetail('overview')">查看租户详情</a-button>
          <a-button data-test="wizard-go-permission-summary" class="ml-2" @click="goToTenantDetail('permission-summary')">查看权限摘要</a-button>
          <a-button data-test="wizard-go-template-diff" class="ml-2" @click="goToTenantDetail('template-diff')">查看模板差异</a-button>
        </div>
      </div>

      <div v-else-if="submitState === 'submit-failure'" class="wizard-result">
        <a-alert
          type="error"
          show-icon
          message="租户创建失败"
          :description="submitError || '创建请求失败，请稍后重试'"
        />
        <div v-if="submitBlockingIssues.length" class="confirm-section">
          <div class="confirm-title">阻断项</div>
          <ul class="issue-list issue-list-error">
            <li v-for="issue in submitBlockingIssues" :key="`submit-failure-${issue.code}-${issue.field}`">
              [{{ issue.code }}] {{ issue.message }}<span v-if="issue.field">（字段：{{ issue.field }}）</span>
            </li>
          </ul>
        </div>
      </div>

      <div v-else-if="currentStep === 0">
        <h4>基础信息</h4>
        <a-form layout="vertical">
          <a-form-item label="租户编码" required>
            <a-input
              v-model:value="baseInfo.code"
              placeholder="请输入租户编码"
              data-test="wizard-tenant-code-input"
            />
          </a-form-item>
          <a-form-item label="租户名称" required>
            <a-input
              v-model:value="baseInfo.name"
              placeholder="请输入租户名称"
              data-test="wizard-tenant-name-input"
            />
          </a-form-item>
          <a-form-item label="域名">
            <a-input
              v-model:value="baseInfo.domain"
              placeholder="如: tenant.example.com"
              data-test="wizard-tenant-domain-input"
            />
          </a-form-item>
        </a-form>
      </div>

      <div v-else-if="currentStep === 1">
        <h4>初始化策略</h4>
        <a-alert
          type="info"
          show-icon
          message="初始化策略会在最终提交时一次性生效，向导过程不会提前写入租户业务数据。"
        />
        <a-form layout="vertical" class="step-form">
          <a-form-item label="启用">
            <a-switch v-model:checked="settings.enabled" />
          </a-form-item>
          <a-form-item label="套餐">
            <a-input v-model:value="settings.planCode" placeholder="如: pro/enterprise" />
          </a-form-item>
          <a-form-item label="到期时间">
            <a-input v-model:value="settings.expiresAt" placeholder="YYYY-MM-DDTHH:mm:ss" />
          </a-form-item>
          <a-form-item label="最大用户数">
            <a-input-number v-model:value="settings.maxUsers" :min="1" style="width: 100%" />
          </a-form-item>
          <a-form-item label="存储配额(GB)">
            <a-input-number v-model:value="settings.maxStorageGb" :min="0" style="width: 100%" />
          </a-form-item>
          <a-form-item label="联系人">
            <a-input v-model:value="settings.contactName" />
          </a-form-item>
          <a-form-item label="联系邮箱">
            <a-input v-model:value="settings.contactEmail" />
          </a-form-item>
          <a-form-item label="联系电话">
            <a-input v-model:value="settings.contactPhone" />
          </a-form-item>
          <a-form-item label="备注">
            <a-textarea v-model:value="settings.remark" :rows="3" />
          </a-form-item>
        </a-form>
      </div>

      <div v-else-if="currentStep === 2">
        <h4>初始管理员</h4>
        <a-form layout="vertical">
          <a-form-item label="管理员用户名" required>
            <a-input
              v-model:value="initialAdmin.username"
              placeholder="请输入初始管理员用户名"
              data-test="wizard-admin-username-input"
            />
          </a-form-item>
          <a-form-item label="管理员昵称">
            <a-input v-model:value="initialAdmin.nickname" placeholder="默认：租户管理员" />
          </a-form-item>
          <a-form-item label="管理员邮箱">
            <a-input v-model:value="initialAdmin.email" placeholder="请输入管理员邮箱" />
          </a-form-item>
          <a-form-item label="管理员手机">
            <a-input v-model:value="initialAdmin.phone" placeholder="请输入管理员手机号" />
          </a-form-item>
          <a-form-item label="初始密码" required>
            <a-input-password
              v-model:value="initialAdmin.password"
              placeholder="请输入初始密码"
              data-test="wizard-admin-password-input"
            />
          </a-form-item>
          <a-form-item label="确认密码" required>
            <a-input-password
              v-model:value="initialAdmin.confirmPassword"
              placeholder="请再次输入密码"
              data-test="wizard-admin-confirm-password-input"
            />
          </a-form-item>
        </a-form>
      </div>

      <div v-else class="wizard-confirmation" data-test="wizard-confirmation-step">
        <div class="confirm-header">
          <h4>确认</h4>
          <a-button
            :loading="precheckStatus === 'loading'"
            :disabled="isSubmitting"
            @click="runPrecheck"
          >
            重新预检查
          </a-button>
        </div>

        <a-alert
          v-if="precheckStatus === 'loading'"
          data-test="wizard-precheck-loading"
          type="info"
          show-icon
          message="预检查进行中，请稍候..."
        />
        <a-alert
          v-else-if="precheckStatus === 'failure'"
          data-test="wizard-precheck-failure"
          type="error"
          show-icon
          message="预检查失败"
          :description="precheckError || '请重试预检查'"
        />
        <a-alert
          v-else-if="precheckStale"
          type="warning"
          show-icon
          message="步骤数据已变更，历史预检查结果已失效，请重新预检查"
        />
        <a-alert
          v-else-if="precheckStatus === 'idle'"
          type="info"
          show-icon
          message="尚未完成预检查，请先执行预检查"
        />

        <a-alert
          v-if="precheckStatus === 'success' && hasBlockingIssues"
          data-test="wizard-precheck-blocking-banner"
          type="error"
          show-icon
          message="存在阻断项，当前不能创建租户"
        />

        <div
          v-if="precheckStatus === 'success' && precheckResponse?.blockingIssues.length"
          data-test="wizard-precheck-blocking-issues"
          class="confirm-section"
        >
          <div class="confirm-title">阻断项</div>
          <ul class="issue-list issue-list-error">
            <li v-for="issue in precheckResponse.blockingIssues" :key="`blocking-${issue.code}-${issue.field}`">
              [{{ issue.code }}] {{ issue.message }}<span v-if="issue.field">（字段：{{ issue.field }}）</span>
            </li>
          </ul>
        </div>

        <div
          v-if="precheckStatus === 'success' && precheckResponse?.warnings.length"
          class="confirm-section"
        >
          <div class="confirm-title">警告项</div>
          <ul class="issue-list issue-list-warning">
            <li v-for="issue in precheckResponse.warnings" :key="`warning-${issue.code}-${issue.field}`">
              [{{ issue.code }}] {{ issue.message }}<span v-if="issue.field">（字段：{{ issue.field }}）</span>
            </li>
          </ul>
        </div>

        <div class="confirm-section">
          <div class="confirm-title">基础信息</div>
          <div class="confirm-grid">
            <div class="confirm-item">
              <span class="label">租户编码</span>
              <span>{{ displayValue(baseInfo.code) }}</span>
            </div>
            <div class="confirm-item">
              <span class="label">租户名称</span>
              <span>{{ displayValue(baseInfo.name) }}</span>
            </div>
            <div class="confirm-item full">
              <span class="label">域名</span>
              <span>{{ displayValue(baseInfo.domain) }}</span>
            </div>
          </div>
        </div>

        <div class="confirm-section">
          <div class="confirm-title">初始化参数</div>
          <div class="confirm-grid">
            <div class="confirm-item">
              <span class="label">启用状态</span>
              <span>{{ settings.enabled ? '启用' : '禁用' }}</span>
            </div>
            <div class="confirm-item">
              <span class="label">套餐</span>
              <span>{{ displayValue(settings.planCode) }}</span>
            </div>
            <div class="confirm-item">
              <span class="label">到期时间</span>
              <span>{{ displayValue(settings.expiresAt) }}</span>
            </div>
            <div class="confirm-item">
              <span class="label">最大用户数</span>
              <span>{{ displayNumber(settings.maxUsers) }}</span>
            </div>
            <div class="confirm-item">
              <span class="label">存储配额(GB)</span>
              <span>{{ displayNumber(settings.maxStorageGb) }}</span>
            </div>
            <div class="confirm-item">
              <span class="label">联系人</span>
              <span>{{ displayValue(settings.contactName) }}</span>
            </div>
            <div class="confirm-item">
              <span class="label">联系邮箱</span>
              <span>{{ displayValue(settings.contactEmail) }}</span>
            </div>
            <div class="confirm-item">
              <span class="label">联系电话</span>
              <span>{{ displayValue(settings.contactPhone) }}</span>
            </div>
            <div class="confirm-item full">
              <span class="label">备注</span>
              <span>{{ displayValue(settings.remark) }}</span>
            </div>
          </div>
        </div>

        <div class="confirm-section">
          <div class="confirm-title">初始管理员</div>
          <div class="confirm-grid">
            <div class="confirm-item">
              <span class="label">管理员用户名</span>
              <span>{{ displayValue(initialAdmin.username) }}</span>
            </div>
            <div class="confirm-item">
              <span class="label">管理员昵称</span>
              <span>{{ displayValue(initialAdmin.nickname) }}</span>
            </div>
            <div class="confirm-item">
              <span class="label">管理员邮箱</span>
              <span>{{ displayValue(initialAdmin.email) }}</span>
            </div>
            <div class="confirm-item">
              <span class="label">管理员手机</span>
              <span>{{ displayValue(initialAdmin.phone) }}</span>
            </div>
            <div class="confirm-item full">
              <span class="label">初始密码</span>
              <span>已设置，确认后将直接创建</span>
            </div>
          </div>
        </div>

        <div v-if="precheckStatus === 'success' && summary" class="confirm-section">
          <div class="confirm-title">初始化摘要</div>
          <div class="confirm-grid">
            <div class="confirm-item">
              <span class="label">租户编码</span>
              <span>{{ displayValue(summary.tenantCode) }}</span>
            </div>
            <div class="confirm-item">
              <span class="label">租户名称</span>
              <span>{{ displayValue(summary.tenantName) }}</span>
            </div>
            <div class="confirm-item">
              <span class="label">初始管理员用户名</span>
              <span>{{ displayValue(summary.initialAdminUsername) }}</span>
            </div>
            <div class="confirm-item">
              <span class="label">平台模板就绪</span>
              <span>{{ summary.platformTemplateReady ? '是' : '否' }}</span>
            </div>
            <div class="confirm-item">
              <span class="label">默认角色数</span>
              <span>{{ summary.defaultRoleCount }}</span>
            </div>
            <div class="confirm-item">
              <span class="label">默认菜单数</span>
              <span>{{ summary.defaultMenuCount }}</span>
            </div>
            <div class="confirm-item">
              <span class="label">默认 UI Action 数</span>
              <span>{{ summary.defaultUiActionCount }}</span>
            </div>
            <div class="confirm-item">
              <span class="label">默认 API Endpoint 数</span>
              <span>{{ summary.defaultApiEndpointCount }}</span>
            </div>
            <div class="confirm-item">
              <span class="label">默认权限数</span>
              <span>{{ summary.defaultPermissionCount }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="wizard-actions">
      <a-button
        v-if="submitState === 'submit-success'"
        type="primary"
        data-test="wizard-complete-close"
        @click="handleResultClose"
      >
        完成并关闭
      </a-button>
      <template v-else-if="submitState === 'submit-failure'">
        <a-button @click="handleBackToEditing">返回编辑</a-button>
        <a-button
          type="primary"
          class="ml-2"
          :loading="isSubmitting"
          :disabled="!canCreateRequest"
          @click="handleRetrySubmit"
        >
          重试创建
        </a-button>
      </template>
      <template v-else>
      <a-button :disabled="isSubmitting" @click="handleCancel">取消</a-button>
      <a-button v-if="currentStep > 0" class="ml-2" :disabled="isSubmitting" @click="handlePrevious">
        上一步
      </a-button>
      <a-button
        v-if="currentStep < 3"
        type="primary"
        class="ml-2"
        :disabled="isSubmitting"
        data-test="wizard-next-step"
        @click="handleNext"
      >
        下一步
      </a-button>
      <a-button
        v-else
        type="primary"
        class="ml-2"
        :loading="isSubmitting"
        :disabled="!canCreateRequest || submitState !== 'editing'"
        data-test="wizard-submit-create"
        @click="handleComplete"
      >
        创建租户
      </a-button>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  createTenant,
  precheckTenantCreate,
  type TenantInitializationSummary,
  type TenantPrecheckIssue,
  type TenantPrecheckResponse,
} from '@/api/tenant'

type TenantCreateWizardPayload = {
  code: string
  name: string
  domain?: string
  enabled: boolean
  planCode?: string
  expiresAt?: string
  maxUsers?: number
  maxStorageGb?: number
  contactName?: string
  contactEmail?: string
  contactPhone?: string
  remark?: string
  initialAdminUsername: string
  initialAdminNickname?: string
  initialAdminEmail?: string
  initialAdminPhone?: string
  initialAdminPassword: string
  initialAdminConfirmPassword: string
}

const emit = defineEmits<{
  (e: 'cancel'): void
  (e: 'completed'): void
}>()
const route = useRoute()
const router = useRouter()

const USERNAME_PATTERN = /^[a-zA-Z0-9_]+$/
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const PHONE_PATTERN = /^1[3-9]\d{9}$/

const currentStep = ref(0)
const precheckResponse = ref<TenantPrecheckResponse | null>(null)
const precheckStatus = ref<'idle' | 'loading' | 'success' | 'failure'>('idle')
const precheckError = ref('')
const precheckStale = ref(false)
const activePrecheckRunId = ref(0)
const validatedPayloadFingerprint = ref<string | null>(null)
const submitState = ref<'editing' | 'submitting' | 'submit-success' | 'submit-failure'>('editing')
const submitError = ref('')
const submitBlockingIssues = ref<TenantPrecheckIssue[]>([])
const submitResult = ref<{
  tenantId: number | null
  tenantCode?: string
  tenantName?: string
  initialAdminUsername: string
  initializationSummary?: TenantInitializationSummary
} | null>(null)

const baseInfo = ref({
  code: '',
  name: '',
  domain: '',
})

const settings = ref({
  enabled: true,
  planCode: '',
  expiresAt: '',
  maxUsers: undefined as number | undefined,
  maxStorageGb: undefined as number | undefined,
  contactName: '',
  contactEmail: '',
  contactPhone: '',
  remark: '',
})

const initialAdmin = ref({
  username: '',
  nickname: '租户管理员',
  email: '',
  phone: '',
  password: '',
  confirmPassword: '',
})

function normalizeOptionalString(value: string) {
  const trimmed = value.trim()
  return trimmed ? trimmed : undefined
}

function displayValue(value: string | undefined) {
  return value && value.trim() ? value.trim() : '-'
}

function displayNumber(value: number | undefined) {
  return value == null ? '-' : String(value)
}

function validateBaseInfo() {
  const code = baseInfo.value.code.trim()
  const name = baseInfo.value.name.trim()

  if (!code) {
    message.warning('租户编码不能为空')
    return false
  }
  if (code.length < 2 || code.length > 64) {
    message.warning('租户编码长度需为 2-64 个字符')
    return false
  }
  if (!name) {
    message.warning('租户名称不能为空')
    return false
  }
  if (name.length < 2 || name.length > 128) {
    message.warning('租户名称长度需为 2-128 个字符')
    return false
  }
  return true
}

function validateSettings() {
  if (settings.value.maxUsers != null && settings.value.maxUsers < 1) {
    message.warning('最大用户数至少为 1')
    return false
  }
  if (settings.value.maxStorageGb != null && settings.value.maxStorageGb < 0) {
    message.warning('存储配额不能为负数')
    return false
  }
  const contactEmail = normalizeOptionalString(settings.value.contactEmail)
  if (contactEmail && !EMAIL_PATTERN.test(contactEmail)) {
    message.warning('联系邮箱格式不正确')
    return false
  }
  return true
}

function validateInitialAdmin() {
  const username = initialAdmin.value.username.trim()
  if (!username) {
    message.warning('管理员用户名不能为空')
    return false
  }
  if (username.length < 3 || username.length > 20 || !USERNAME_PATTERN.test(username)) {
    message.warning('管理员用户名需为 3-20 位字母、数字或下划线')
    return false
  }
  if (!initialAdmin.value.password) {
    message.warning('初始密码不能为空')
    return false
  }
  if (initialAdmin.value.password.length < 6 || initialAdmin.value.password.length > 20) {
    message.warning('初始密码长度需为 6-20 位')
    return false
  }
  if (!initialAdmin.value.confirmPassword) {
    message.warning('确认密码不能为空')
    return false
  }
  if (initialAdmin.value.password !== initialAdmin.value.confirmPassword) {
    message.warning('两次输入的密码不一致')
    return false
  }

  const email = normalizeOptionalString(initialAdmin.value.email)
  if (email && !EMAIL_PATTERN.test(email)) {
    message.warning('管理员邮箱格式不正确')
    return false
  }

  const phone = normalizeOptionalString(initialAdmin.value.phone)
  if (phone && !PHONE_PATTERN.test(phone)) {
    message.warning('管理员手机号格式不正确')
    return false
  }

  return true
}

function validateCurrentStep() {
  if (currentStep.value === 0) {
    return validateBaseInfo()
  }
  if (currentStep.value === 1) {
    return validateSettings()
  }
  if (currentStep.value === 2) {
    return validateInitialAdmin()
  }
  return validateBaseInfo() && validateSettings() && validateInitialAdmin()
}

const payloadFingerprint = computed(() => JSON.stringify(buildPayload()))

const hasBlockingIssues = computed(() => (precheckResponse.value?.blockingIssues?.length ?? 0) > 0)
const summary = computed<TenantInitializationSummary | undefined>(
  () => precheckResponse.value?.initializationSummary,
)
const isSubmitting = computed(() => submitState.value === 'submitting')

function invalidatePrecheckResult() {
  precheckResponse.value = null
  precheckError.value = ''
  precheckStatus.value = 'idle'
  validatedPayloadFingerprint.value = null
  precheckStale.value = true
}

const canCreateRequest = computed(
  () =>
    precheckStatus.value === 'success'
    && !precheckStale.value
    && validatedPayloadFingerprint.value === payloadFingerprint.value
    && !!precheckResponse.value?.ok
    && !hasBlockingIssues.value,
)

watch(payloadFingerprint, () => {
  if (precheckStatus.value === 'idle' && !precheckResponse.value) {
    return
  }
  invalidatePrecheckResult()
})

watch(currentStep, (step) => {
  if (step === 3 && precheckStatus.value === 'idle' && !precheckResponse.value && !precheckStale.value) {
    void runPrecheck()
  }
})

async function runPrecheck() {
  if (!validateBaseInfo() || !validateSettings() || !validateInitialAdmin()) {
    return
  }
  const requestFingerprint = payloadFingerprint.value
  const runId = activePrecheckRunId.value + 1
  activePrecheckRunId.value = runId
  precheckStatus.value = 'loading'
  precheckError.value = ''
  precheckResponse.value = null
  try {
    const response = await precheckTenantCreate(buildPayload())
    if (activePrecheckRunId.value !== runId) {
      return
    }
    activePrecheckRunId.value = 0
    if (payloadFingerprint.value !== requestFingerprint) {
      invalidatePrecheckResult()
      return
    }
    precheckResponse.value = response
    precheckStatus.value = 'success'
    precheckStale.value = false
    validatedPayloadFingerprint.value = requestFingerprint
  } catch (error) {
    if (activePrecheckRunId.value !== runId) {
      return
    }
    activePrecheckRunId.value = 0
    if (payloadFingerprint.value !== requestFingerprint) {
      invalidatePrecheckResult()
      return
    }
    precheckStatus.value = 'failure'
    precheckResponse.value = null
    validatedPayloadFingerprint.value = null
    precheckStale.value = false
    precheckError.value = error instanceof Error ? error.message : '预检查失败，请稍后重试'
  }
}

function handleCancel() {
  if (isSubmitting.value) {
    return
  }
  emit('cancel')
}

function handlePrevious() {
  if (currentStep.value > 0) {
    currentStep.value -= 1
  }
}

function handleNext() {
  if (!validateCurrentStep()) {
    return
  }
  currentStep.value += 1
  if (currentStep.value === 3 && precheckStatus.value === 'idle' && !precheckResponse.value && !precheckStale.value) {
    void runPrecheck()
  }
}

function buildPayload(): TenantCreateWizardPayload {
  return {
    code: baseInfo.value.code.trim(),
    name: baseInfo.value.name.trim(),
    domain: normalizeOptionalString(baseInfo.value.domain),
    enabled: settings.value.enabled,
    planCode: normalizeOptionalString(settings.value.planCode),
    expiresAt: normalizeOptionalString(settings.value.expiresAt),
    maxUsers: settings.value.maxUsers,
    maxStorageGb: settings.value.maxStorageGb,
    contactName: normalizeOptionalString(settings.value.contactName),
    contactEmail: normalizeOptionalString(settings.value.contactEmail),
    contactPhone: normalizeOptionalString(settings.value.contactPhone),
    remark: normalizeOptionalString(settings.value.remark),
    initialAdminUsername: initialAdmin.value.username.trim(),
    initialAdminNickname: normalizeOptionalString(initialAdmin.value.nickname),
    initialAdminEmail: normalizeOptionalString(initialAdmin.value.email),
    initialAdminPhone: normalizeOptionalString(initialAdmin.value.phone),
    initialAdminPassword: initialAdmin.value.password,
    initialAdminConfirmPassword: initialAdmin.value.confirmPassword,
  }
}

function handleComplete() {
  if (!validateCurrentStep()) {
    return
  }
  if (!canCreateRequest.value) {
    message.warning('请先完成最新预检查并修复阻断项后再创建租户')
    return
  }
  void submitCreate()
}

function normalizeCreateError(error: unknown) {
  if (typeof error === 'object' && error && 'response' in error) {
    const maybeResponse = (error as { response?: { data?: { message?: string } } }).response
    if (maybeResponse?.data?.message) {
      return maybeResponse.data.message
    }
  }
  return error instanceof Error ? error.message : '创建失败，请稍后重试'
}

function extractBlockingIssues(error: unknown): TenantPrecheckIssue[] {
  if (typeof error === 'object' && error && 'response' in error) {
    const maybeBlockingIssues = (
      error as { response?: { data?: { blockingIssues?: TenantPrecheckIssue[] } } }
    ).response?.data?.blockingIssues
    if (Array.isArray(maybeBlockingIssues)) {
      return maybeBlockingIssues
    }
  }
  return []
}

async function submitCreate() {
  if (!canCreateRequest.value || isSubmitting.value) {
    return
  }
  const payload = buildPayload()
  submitState.value = 'submitting'
  submitError.value = ''
  submitBlockingIssues.value = []
  try {
    const created = await createTenant(payload) as { id?: number; code?: string; name?: string }
    submitResult.value = {
      tenantId: typeof created?.id === 'number' ? created.id : null,
      tenantCode: created?.code ?? payload.code,
      tenantName: created?.name ?? payload.name,
      initialAdminUsername: payload.initialAdminUsername,
      initializationSummary: summary.value,
    }
    submitState.value = 'submit-success'
  } catch (error) {
    submitError.value = normalizeCreateError(error)
    submitBlockingIssues.value = extractBlockingIssues(error)
    submitState.value = 'submit-failure'
  }
}

function handleRetrySubmit() {
  if (!canCreateRequest.value) {
    message.warning('当前预检查结果无效，请重新预检查后重试')
    return
  }
  void submitCreate()
}

function handleBackToEditing() {
  submitState.value = 'editing'
}

function goToTenantDetail(section: 'overview' | 'permission-summary' | 'template-diff') {
  if (!submitResult.value?.tenantId) {
    return
  }
  void router.push({
    path: `/platform/tenants/${submitResult.value.tenantId}`,
    query: {
      from: route.fullPath,
      section,
    },
  })
}

function handleResultClose() {
  emit('completed')
}
</script>

<style scoped>
.tenant-create-wizard {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.wizard-steps {
  margin-bottom: 8px;
}

.wizard-content {
  min-height: 360px;
}

.step-form {
  margin-top: 16px;
}

.wizard-confirmation {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.wizard-result {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.confirm-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.confirm-section {
  border: 1px solid var(--ant-color-border, #f0f0f0);
  border-radius: 8px;
  padding: 16px;
  background: var(--ant-color-bg-container, #fff);
}

.confirm-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 12px;
}

.confirm-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 16px;
}

.confirm-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.confirm-item.full {
  grid-column: 1 / -1;
}

.confirm-item .label {
  color: #8c8c8c;
  font-size: 12px;
}

.issue-list {
  margin: 0;
  padding-left: 20px;
}

.issue-list li + li {
  margin-top: 8px;
}

.issue-list-error {
  color: #cf1322;
}

.issue-list-warning {
  color: #ad6800;
}

.result-actions {
  display: flex;
  justify-content: flex-end;
}

.wizard-actions {
  display: flex;
  justify-content: flex-end;
}
</style>
