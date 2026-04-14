<template>
  <div class="token-debug-page">
    <a-card v-if="!isPlatformScope" title="平台作用域限制">
      当前会话不是 PLATFORM 作用域，已阻止加载 token debug 控制面。请切换到平台作用域后重试。
    </a-card>
    <template v-else>
    <div class="token-debug-header">
      <h2>平台 Token Decode 工具</h2>
      <p>只读解析 access token claims，用于权限与作用域排障，不做签发或刷新。</p>
    </div>
    <div class="token-debug-card">
      <a-form layout="vertical">
        <a-form-item label="Token">
          <a-textarea
            v-model:value="token"
            :rows="6"
            placeholder="粘贴 access token（仅 decode 展示）"
          />
        </a-form-item>
        <a-space>
          <a-button type="primary" :loading="loading" @click="handleDecode">Decode</a-button>
          <a-button @click="handleReset">清空</a-button>
        </a-space>
      </a-form>
    </div>

    <div v-if="decoded" class="token-debug-card">
      <h3>关键字段</h3>
      <a-descriptions :column="2" bordered size="small">
        <a-descriptions-item label="permissionsVersion">{{ decoded.permissionsVersion || '-' }}</a-descriptions-item>
        <a-descriptions-item label="activeScopeType">{{ decoded.activeScopeType || '-' }}</a-descriptions-item>
        <a-descriptions-item label="activeTenantId">{{ decoded.activeTenantId ?? '-' }}</a-descriptions-item>
      </a-descriptions>

      <div class="claim-section">
        <h4>authorities</h4>
        <a-tag v-for="item in decoded.authorities" :key="`a-${item}`">{{ item }}</a-tag>
        <span v-if="decoded.authorities.length === 0">-</span>
      </div>
      <div class="claim-section">
        <h4>permissions</h4>
        <a-tag color="green" v-for="item in decoded.permissions" :key="`p-${item}`">{{ item }}</a-tag>
        <span v-if="decoded.permissions.length === 0">-</span>
      </div>
      <div class="claim-section">
        <h4>roleCodes</h4>
        <a-tag color="blue" v-for="item in decoded.roleCodes" :key="`r-${item}`">{{ item }}</a-tag>
        <span v-if="decoded.roleCodes.length === 0">-</span>
      </div>

      <h4>raw claims</h4>
      <pre class="claims-json">{{ JSON.stringify(decoded.claims, null, 2) }}</pre>
    </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { decodePlatformToken } from '@/api/audit'
import type { TokenDebugDecodeResponse } from '@/api/audit'
import { usePlatformScope } from '@/composables/usePlatformScope'

const token = ref('')
const loading = ref(false)
const decoded = ref<TokenDebugDecodeResponse | null>(null)
const { isPlatformScope } = usePlatformScope()

async function handleDecode() {
  if (!isPlatformScope.value) {
    return
  }
  if (!token.value.trim()) {
    message.warning('请输入 token')
    return
  }
  loading.value = true
  try {
    decoded.value = await decodePlatformToken(token.value.trim())
  } catch (error: any) {
    decoded.value = null
    message.error(error?.message || 'token decode 失败')
  } finally {
    loading.value = false
  }
}

function handleReset() {
  token.value = ''
  decoded.value = null
}
</script>

<style scoped>
.token-debug-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.token-debug-header,
.token-debug-card {
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fff;
  padding: 16px 20px;
}

.token-debug-header h2 {
  margin: 0;
}

.token-debug-header p {
  margin: 8px 0 0;
  color: #595959;
}

.claim-section {
  margin-top: 16px;
}

.claim-section h4 {
  margin: 0 0 8px;
}

.claims-json {
  margin-top: 8px;
  background: #0f172a;
  color: #e2e8f0;
  padding: 12px;
  border-radius: 6px;
  max-height: 320px;
  overflow: auto;
}
</style>

