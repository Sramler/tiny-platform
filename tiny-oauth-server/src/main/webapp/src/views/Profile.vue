<template>
  <div class="profile-page">
    <a-row :gutter="24">
      <!-- 左侧：用户信息卡片 -->
      <a-col :xs="24" :md="6">
        <a-card class="user-info-card" :bordered="false">
          <div class="user-avatar-section">
            <a-avatar 
              :size="104" 
              class="user-avatar" 
              :src="avatarUrl" 
              :style="avatarStyle"
              @error="handleAvatarError"
            >
              <template #icon>
                <UserOutlined />
              </template>
            </a-avatar>
            <div class="user-name">
              <h3 class="user-nickname">{{ userInfo.nickname || userInfo.username || '未设置昵称' }}</h3>
              <p class="user-username">@{{ userInfo.username }}</p>
            </div>
          </div>
          <a-divider style="margin: 24px 0" />
          <div class="user-stats">
            <div class="stat-item">
              <div class="stat-icon">
                <UserOutlined />
              </div>
              <div class="stat-content">
                <div class="stat-label">用户ID</div>
                <div class="stat-value">{{ userInfo.id || '-' }}</div>
              </div>
            </div>
            <div class="stat-item">
              <div class="stat-icon">
                <CheckCircleOutlined :style="{ color: userInfo.enabled ? '#52c41a' : '#ff4d4f' }" />
              </div>
              <div class="stat-content">
                <div class="stat-label">账户状态</div>
                <div class="stat-value">
                  <a-tag :color="userInfo.enabled ? 'green' : 'red'">
                    {{ userInfo.enabled ? '已启用' : '已禁用' }}
                  </a-tag>
                </div>
              </div>
            </div>
            <div class="stat-item" v-if="userInfo.lastLoginAt">
              <div class="stat-icon">
                <ClockCircleOutlined />
              </div>
              <div class="stat-content">
                <div class="stat-label">最后登录</div>
                <div class="stat-value">{{ formatDateTime(userInfo.lastLoginAt) }}</div>
              </div>
            </div>
          </div>
        </a-card>
      </a-col>

      <!-- 右侧：详细信息 -->
      <a-col :xs="24" :md="18">
        <a-card :bordered="false">
          <a-tabs v-model:activeKey="activeKey" size="large" @change="handleTabChange">
            <!-- 基本信息 -->
            <a-tab-pane key="base" tab="基本信息">
              <a-descriptions :column="2" bordered>
                <a-descriptions-item label="用户名" :span="1">
                  {{ userInfo.username }}
                </a-descriptions-item>
                <a-descriptions-item label="昵称" :span="1">
                  {{ userInfo.nickname || '-' }}
                </a-descriptions-item>
                <a-descriptions-item label="邮箱" :span="1">
                  {{ userInfo.email || '-' }}
                </a-descriptions-item>
                <a-descriptions-item label="手机号" :span="1">
                  {{ userInfo.phone || '-' }}
                </a-descriptions-item>
                <a-descriptions-item label="用户ID" :span="1">
                  {{ userInfo.id || '-' }}
                </a-descriptions-item>
                <a-descriptions-item label="账户状态" :span="1">
                  <a-tag :color="userInfo.enabled ? 'green' : 'red'">
                    {{ userInfo.enabled ? '已启用' : '已禁用' }}
                  </a-tag>
                </a-descriptions-item>
                <a-descriptions-item label="账号未过期" :span="1">
                  <a-tag :color="userInfo.accountNonExpired ? 'green' : 'red'">
                    {{ userInfo.accountNonExpired ? '是' : '否' }}
                  </a-tag>
                </a-descriptions-item>
                <a-descriptions-item label="账号未锁定" :span="1">
                  <a-tag :color="userInfo.accountNonLocked ? 'green' : 'red'">
                    {{ userInfo.accountNonLocked ? '是' : '否' }}
                  </a-tag>
                </a-descriptions-item>
                <a-descriptions-item label="密码未过期" :span="1">
                  <a-tag :color="userInfo.credentialsNonExpired ? 'green' : 'red'">
                    {{ userInfo.credentialsNonExpired ? '是' : '否' }}
                  </a-tag>
                </a-descriptions-item>
                <a-descriptions-item label="最后登录时间" :span="1">
                  {{ userInfo.lastLoginAt ? formatDateTime(userInfo.lastLoginAt) : '-' }}
                </a-descriptions-item>
                <a-descriptions-item label="最后登录IP" :span="1">
                  {{ userInfo.lastLoginIp || '-' }}
                </a-descriptions-item>
                <a-descriptions-item label="最后登录设备" :span="1">
                  {{ userInfo.lastLoginDevice || '-' }}
                </a-descriptions-item>
                <a-descriptions-item label="失败登录次数" :span="1">
                  {{ userInfo.failedLoginCount || 0 }}
                </a-descriptions-item>
                <a-descriptions-item label="最后失败登录时间" :span="1">
                  {{ userInfo.lastFailedLoginAt ? formatDateTime(userInfo.lastFailedLoginAt) : '-' }}
                </a-descriptions-item>
              </a-descriptions>

              <div class="action-buttons">
                <a-button type="primary" @click="goToSettings">
                  <template #icon>
                    <SettingOutlined />
                  </template>
                  编辑个人设置
                </a-button>
              </div>
            </a-tab-pane>

            <!-- 账户安全 -->
            <a-tab-pane key="security" tab="账户安全">
              <a-list item-layout="horizontal">
                <a-list-item>
                  <a-list-item-meta>
                    <template #title>
                      <span>账户密码</span>
                    </template>
                    <template #description>
                      <span>当前密码强度：<a-tag color="blue">强</a-tag></span>
                    </template>
                  </a-list-item-meta>
                  <template #actions>
                    <a @click="goToSettings">修改</a>
                  </template>
                </a-list-item>
                <a-list-item>
                  <a-list-item-meta>
                    <template #title>
                      <span>两步验证</span>
                    </template>
                    <template #description>
                      <span v-if="totpBound">已开启两步验证，账户安全性更高</span>
                      <span v-else>未开启两步验证，建议开启以提升账户安全性</span>
                    </template>
                  </a-list-item-meta>
                  <template #actions>
                    <a v-if="totpBound" @click="goToSettings">查看</a>
                    <a v-else @click="handleBindTotp">开启</a>
                  </template>
                </a-list-item>
                <a-list-item>
                  <a-list-item-meta>
                    <template #title>
                      <span>绑定手机</span>
                    </template>
                    <template #description>
                      <span>已绑定手机：{{ userInfo.phone || '未绑定' }}</span>
                    </template>
                  </a-list-item-meta>
                  <template #actions>
                    <a>修改</a>
                  </template>
                </a-list-item>
                <a-list-item>
                  <a-list-item-meta>
                    <template #title>
                      <span>绑定邮箱</span>
                    </template>
                    <template #description>
                      <span>已绑定邮箱：{{ userInfo.email || '未绑定' }}</span>
                    </template>
                  </a-list-item-meta>
                  <template #actions>
                    <a>修改</a>
                  </template>
                </a-list-item>
                <a-list-item>
                  <a-list-item-meta>
                    <template #title>
                      <span>登录设备</span>
                    </template>
                    <template #description>
                      <span>最后登录设备：{{ userInfo.lastLoginDevice || '未知' }}</span>
                    </template>
                  </a-list-item-meta>
                </a-list-item>
                <a-list-item>
                  <a-list-item-meta>
                    <template #title>
                      <span>登录安全</span>
                    </template>
                    <template #description>
                      <span>失败登录次数：{{ userInfo.failedLoginCount || 0 }}
                        <span v-if="userInfo.lastFailedLoginAt">
                          （最后失败：{{ formatDateTime(userInfo.lastFailedLoginAt) }}）
                        </span>
                      </span>
                    </template>
                  </a-list-item-meta>
                </a-list-item>
              </a-list>
            </a-tab-pane>

            <!-- 登录历史 -->
            <a-tab-pane key="login" tab="登录历史">
              <a-spin :spinning="loginHistoryLoading">
                <a-empty v-if="!loginHistoryLoading && loginHistory.length === 0" description="暂无登录记录" />
                <div v-else>
                  <a-table
                    :columns="loginHistoryColumns"
                    :data-source="loginHistory"
                    :pagination="loginHistoryPagination"
                    :loading="loginHistoryLoading"
                    @change="handleLoginHistoryTableChange"
                    row-key="id"
                    size="middle"
                  >
                    <template #bodyCell="{ column, record }">
                      <template v-if="column.key === 'success'">
                        <a-tag :color="record.success ? 'green' : 'red'">
                          {{ record.success ? '成功' : '失败' }}
                        </a-tag>
                      </template>
                      <template v-else-if="column.key === 'authenticationFactor'">
                        <a-tag v-if="record.authenticationFactor" color="blue">
                          {{ record.authenticationFactor }}
                        </a-tag>
                        <span v-else>-</span>
                      </template>
                      <template v-else-if="column.key === 'createdAt'">
                        {{ formatDateTime(record.createdAt) }}
                      </template>
                      <template v-else-if="column.key === 'userAgent'">
                        <a-tooltip :title="record.userAgent">
                          <span class="text-ellipsis">{{ record.userAgent || '-' }}</span>
                        </a-tooltip>
                      </template>
                    </template>
                  </a-table>
                </div>
              </a-spin>
            </a-tab-pane>
          </a-tabs>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { UserOutlined, SettingOutlined, CheckCircleOutlined, ClockCircleOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { getSecurityStatus } from '@/api/security'
import { getCurrentUser } from '@/api/user'
import type { TableColumnsType } from 'ant-design-vue'
import { generateAvatarStyleObject } from '@/utils/avatar'
import { getActiveTenantId, resolveActiveTenantQueryValue, withActiveTenantQuery } from '@/utils/tenant'
import { fetchWithTraceId } from '@/utils/traceId'
import type { Key } from 'ant-design-vue/es/_util/type'

// 路由
const route = useRoute()
const router = useRouter()

const buildNavigationQuery = () =>
  withActiveTenantQuery({}, resolveActiveTenantQueryValue(route.query) ?? getActiveTenantId())

// 当前激活的标签页
const activeKey = ref('base')

// TOTP 绑定状态
const totpBound = ref(false)

// 头像相关
const avatarUrl = ref<string>('')

// 计算头像URL（如果用户有头像）
const getAvatarUrl = () => {
  if (!userInfo.value.id) return ''
  const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:9000'
  const baseUrl = apiBaseUrl.endsWith('/') ? apiBaseUrl.slice(0, -1) : apiBaseUrl
  return `${baseUrl}/sys/users/${userInfo.value.id}/avatar?t=${Date.now()}`
}

// 更新头像URL
const updateAvatarUrl = () => {
  avatarUrl.value = getAvatarUrl()
}

// 计算头像样式（当没有头像时使用随机颜色）
const avatarStyle = computed(() => {
  if (avatarUrl.value) {
    // 有头像时不使用样式，让图片显示
    return {}
  }
  // 没有头像时使用基于用户ID的随机颜色
  return generateAvatarStyleObject(userInfo.value.id, userInfo.value.username)
})

// 登录历史
const loginHistory = ref<any[]>([])
const loginHistoryLoading = ref(false)
const loginHistoryPagination = ref({
  current: 1,
  pageSize: 20,
  total: 0,
  showSizeChanger: true,
  showTotal: (total: number) => `共 ${total} 条记录`,
  pageSizeOptions: ['10', '20', '50', '100']
})

// 登录历史表格列定义
const loginHistoryColumns: TableColumnsType = [
  {
    title: '登录时间',
    key: 'createdAt',
    dataIndex: 'createdAt',
    width: 180
  },
  {
    title: '状态',
    key: 'success',
    dataIndex: 'success',
    width: 80
  },
  {
    title: '认证方式',
    key: 'authenticationFactor',
    dataIndex: 'authenticationFactor',
    width: 120
  },
  {
    title: 'IP地址',
    key: 'ipAddress',
    dataIndex: 'ipAddress',
    width: 150
  },
  {
    title: '设备信息',
    key: 'userAgent',
    dataIndex: 'userAgent',
    ellipsis: true
  }
]

// 用户信息
const userInfo = ref<any>({
  id: '',
  username: '',
  nickname: '',
  enabled: true,
  accountNonExpired: true,
  accountNonLocked: true,
  credentialsNonExpired: true,
  lastLoginAt: null,
  createdAt: null,
  updatedAt: null,
  phone: null,
  email: null,
  lastLoginIp: null,
  lastLoginDevice: null,
  failedLoginCount: 0,
  lastFailedLoginAt: null
})

// 加载用户信息
const loadUserInfo = async () => {
  try {
    const data = await getCurrentUser()
    userInfo.value = data
    // 更新头像URL
    updateAvatarUrl()
  } catch (error) {
    console.error('加载用户信息失败:', error)
    message.error('加载用户信息失败')
  }
}

// 处理头像加载错误
const handleAvatarError = () => {
  // 头像加载失败时，使用默认图标
  avatarUrl.value = ''
}

// 监听头像上传成功事件
const handleAvatarUploaded = (event: Event) => {
  const customEvent = event as CustomEvent
  // 如果事件中包含 userId，且与当前用户ID匹配，则更新头像
  if (!customEvent.detail?.userId || customEvent.detail.userId === userInfo.value.id) {
    // 重新加载头像（添加时间戳避免缓存）
    updateAvatarUrl()
  }
}

// 加载安全状态
const loadSecurityStatus = async () => {
  try {
    const data = await getSecurityStatus()
    totpBound.value = Boolean(data.totpBound)
  } catch (error) {
    console.error('加载安全状态失败:', error)
  }
}

// 加载登录历史
const loadLoginHistory = async () => {
  loginHistoryLoading.value = true
  try {
    const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:9000'
    const baseUrl = apiBaseUrl.endsWith('/') ? apiBaseUrl.slice(0, -1) : apiBaseUrl
    // 构建分页参数：Spring Data JPA 使用 page (从0开始) 和 size
    const page = loginHistoryPagination.value.current - 1 // Ant Design Vue 从1开始，后端从0开始
    const size = loginHistoryPagination.value.pageSize
    const response = await fetchWithTraceId(`${baseUrl}/sys/users/current/login-history?page=${page}&size=${size}&sort=createdAt,desc`, {
      method: 'GET',
      credentials: 'include',
      headers: { Accept: 'application/json' }
    })
    if (!response.ok) {
      throw new Error('无法获取登录历史')
    }
    const data = await response.json()
    if (data.success) {
      loginHistory.value = data.content || []
      loginHistoryPagination.value.total = data.totalElements || 0
    } else {
      throw new Error(data.error || '获取登录历史失败')
    }
  } catch (error) {
    console.error('加载登录历史失败:', error)
    message.error('加载登录历史失败')
  } finally {
    loginHistoryLoading.value = false
  }
}

// 处理登录历史表格变化（分页、排序等）
const handleLoginHistoryTableChange = (pag: any) => {
  if (pag && typeof pag.current === 'number') {
    loginHistoryPagination.value.current = pag.current
  }
  if (pag && typeof pag.pageSize === 'number') {
    loginHistoryPagination.value.pageSize = pag.pageSize
    loginHistoryPagination.value.current = 1 // 切换每页数量时重置到第一页
  }
  loadLoginHistory()
}

// 格式化日期时间
const formatDateTime = (dateTime: string | null | undefined): string => {
  if (!dateTime) return '-'
  try {
    const date = new Date(dateTime)
    return date.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    })
  } catch (error) {
    return dateTime
  }
}

// 跳转到个人设置
const goToSettings = () => {
  router.push({
    path: '/profile/setting',
    query: buildNavigationQuery(),
  })
}

// 绑定两步验证
const handleBindTotp = () => {
  router.push({
    path: '/self/security/totp-bind',
    query: buildNavigationQuery(),
  })
}

// 处理标签页切换
const handleTabChange = (key: Key) => {
  if (key === 'login' && loginHistory.value.length === 0 && !loginHistoryLoading.value) {
    // 切换到登录历史标签页时加载数据
    loadLoginHistory()
  }
}

// 组件挂载时加载数据和监听事件
onMounted(() => {
  loadUserInfo()
  loadSecurityStatus()
  // 如果默认显示登录历史标签页，则加载登录历史
  if (activeKey.value === 'login') {
    loadLoginHistory()
  }
  // 监听头像上传成功事件
  window.addEventListener('avatar-uploaded', handleAvatarUploaded)
})

// 组件卸载时移除事件监听
onUnmounted(() => {
  window.removeEventListener('avatar-uploaded', handleAvatarUploaded)
})
</script>

<style scoped>
.profile-page {
  padding: 24px;
  background: #f0f2f5;
  min-height: calc(100vh - 64px);
}

.user-info-card {
  text-align: center;
}

.user-avatar-section {
  padding: 20px 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}

:deep(.ant-upload) {
  margin-bottom: 8px;
}

.user-avatar {
  margin-bottom: 8px;
  border: 4px solid #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  cursor: pointer;
  transition: opacity 0.3s;
  /* 背景色由 avatarStyle 动态设置 */
}

.user-avatar:hover {
  opacity: 0.8;
}


.user-name {
  margin-top: 16px;
}

.user-nickname {
  margin: 0 0 8px 0;
  font-size: 20px;
  font-weight: 500;
  color: rgba(0, 0, 0, 0.85);
}

.user-username {
  margin: 0;
  font-size: 14px;
  color: rgba(0, 0, 0, 0.45);
}

.user-stats {
  padding: 0;
}

.stat-item {
  display: flex;
  align-items: center;
  padding: 16px 0;
  text-align: left;
}

.stat-item:not(:last-child) {
  border-bottom: 1px solid #f0f0f0;
}

.stat-icon {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #f0f5ff;
  margin-right: 16px;
  font-size: 18px;
  color: #1890ff;
}

.stat-content {
  flex: 1;
}

.stat-label {
  font-size: 14px;
  color: rgba(0, 0, 0, 0.45);
  margin-bottom: 4px;
}

.stat-value {
  font-size: 14px;
  color: rgba(0, 0, 0, 0.85);
  font-weight: 500;
}

.action-buttons {
  margin-top: 24px;
  text-align: right;
}

:deep(.ant-tabs-content-holder) {
  padding-top: 24px;
}

:deep(.ant-descriptions-item-label) {
  width: 150px;
  font-weight: 500;
  background: #fafafa;
}

:deep(.ant-list-item) {
  padding: 16px 0;
  border-bottom: 1px solid #f0f0f0;
}

:deep(.ant-list-item:last-child) {
  border-bottom: none;
}

:deep(.ant-list-item-meta-title) {
  font-size: 14px;
  font-weight: 500;
  color: rgba(0, 0, 0, 0.85);
  margin-bottom: 4px;
}

:deep(.ant-list-item-meta-description) {
  font-size: 14px;
  color: rgba(0, 0, 0, 0.45);
}

:deep(.ant-list-item-action > li) {
  padding: 0 8px;
}

:deep(.ant-list-item-action > li > a) {
  color: #1890ff;
}

.text-ellipsis {
  display: inline-block;
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style> 
