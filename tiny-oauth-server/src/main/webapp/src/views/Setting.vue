<template>
  <div class="setting-page">
    <a-card title="个人设置" :bordered="false">
      <a-tabs v-model:activeKey="activeKey">
        <!-- 基本信息 -->
        <a-tab-pane key="basic" tab="基本信息">
          <a-form
            :model="basicForm"
            :rules="basicRules"
            :label-col="{ span: 4 }"
            :wrapper-col="{ span: 16 }"
            @finish="handleBasicSubmit"
          >
            <a-form-item label="头像" name="avatar">
              <div class="avatar-upload-section">
                <a-upload
                  :before-upload="beforeAvatarUpload"
                  :custom-request="handleAvatarUpload"
                  :show-upload-list="false"
                  accept="image/png,image/jpeg,image/jpg,image/webp"
                >
                  <a-avatar 
                    :size="80" 
                    class="user-avatar" 
                    :src="avatarUrl" 
                    :style="avatarStyle"
                    @error="handleAvatarError"
                  >
                    <template #icon>
                      <UserOutlined />
                    </template>
                  </a-avatar>
                </a-upload>
                <div class="avatar-upload-tips">
                  <div class="upload-tip">点击头像上传</div>
                  <div class="upload-hint">支持 PNG、JPEG、WebP 格式，文件大小不超过 1MB</div>
                </div>
              </div>
            </a-form-item>
            <a-form-item label="用户名" name="username">
              <a-input v-model:value="basicForm.username" disabled />
              <div class="form-help-text">用户名不可修改</div>
            </a-form-item>
            <a-form-item label="昵称" name="nickname">
              <a-input v-model:value="basicForm.nickname" placeholder="请输入昵称" />
            </a-form-item>
            <a-form-item label="邮箱" name="email">
              <a-input v-model:value="basicForm.email" placeholder="请输入邮箱" />
            </a-form-item>
            <a-form-item label="手机号" name="phone">
              <a-input v-model:value="basicForm.phone" placeholder="请输入手机号" />
            </a-form-item>
            <a-form-item :wrapper-col="{ offset: 4, span: 16 }">
              <a-button type="primary" html-type="submit" :loading="basicLoading">
                保存
              </a-button>
              <a-button style="margin-left: 8px" @click="resetBasicForm">
                重置
              </a-button>
            </a-form-item>
          </a-form>
        </a-tab-pane>

        <!-- 修改密码 -->
        <a-tab-pane key="password" tab="修改密码">
          <a-form
            :model="passwordForm"
            :rules="passwordRules"
            :label-col="{ span: 4 }"
            :wrapper-col="{ span: 16 }"
            @finish="handlePasswordSubmit"
          >
            <a-form-item label="原密码" name="oldPassword">
              <a-input-password v-model:value="passwordForm.oldPassword" placeholder="请输入原密码" />
            </a-form-item>
            <a-form-item label="新密码" name="newPassword">
              <a-input-password v-model:value="passwordForm.newPassword" placeholder="请输入新密码（6-20个字符）" />
            </a-form-item>
            <a-form-item label="确认密码" name="confirmPassword">
              <a-input-password v-model:value="passwordForm.confirmPassword" placeholder="请再次输入新密码" />
            </a-form-item>
            <a-form-item :wrapper-col="{ offset: 4, span: 16 }">
              <a-button type="primary" html-type="submit" :loading="passwordLoading">
                修改密码
              </a-button>
              <a-button style="margin-left: 8px" @click="resetPasswordForm">
                重置
              </a-button>
            </a-form-item>
          </a-form>
        </a-tab-pane>

        <!-- 安全设置 -->
        <a-tab-pane key="security" tab="安全设置">
          <a-space direction="vertical" size="large" style="width: 100%">
            <a-card title="两步验证" size="small">
              <a-space direction="vertical" style="width: 100%">
                <div v-if="securityLoading">
                  <a-spin tip="加载中..." />
                </div>
                <div v-else-if="totpBound">
                  <a-alert
                    message="两步验证已绑定"
                    description="您的账户已启用两步验证，可以提供额外的安全保障。"
                    type="success"
                    show-icon
                    style="margin-bottom: 16px"
                  />
                  <a-space>
                    <a-button type="primary" danger @click="showUnbindModal">
                      解绑两步验证
                    </a-button>
                  </a-space>
                </div>
                <div v-else>
                  <p>两步验证可以为您的账户提供额外的安全保护。</p>
                  <a-button type="primary" @click="handleBindTotp" style="margin-top: 16px">
                    绑定两步验证
                  </a-button>
                </div>
              </a-space>
            </a-card>
          </a-space>
        </a-tab-pane>
      </a-tabs>
    </a-card>

    <!-- 解绑两步验证模态框 -->
    <a-modal
      v-model:open="unbindModalVisible"
      title="解绑两步验证"
      :confirm-loading="unbindLoading"
      @ok="handleUnbindSubmit"
      @cancel="resetUnbindForm"
    >
      <a-alert
        message="警告"
        description="解绑两步验证会降低账户安全性，请确认此操作。"
        type="warning"
        show-icon
        style="margin-bottom: 16px"
      />
      <a-form
        :model="unbindForm"
        :rules="unbindRules"
        :label-col="{ span: 6 }"
        :wrapper-col="{ span: 18 }"
        ref="unbindFormRef"
      >
        <a-form-item label="TOTP验证码" name="totpCode">
          <a-input
            v-model:value="unbindForm.totpCode"
            placeholder="请输入6位动态验证码"
            maxlength="6"
          />
          <div class="form-help-text">请从您的验证器 App 中输入 6 位动态验证码</div>
        </a-form-item>
        <a-form-item label="密码（可选）" name="password">
          <a-input-password
            v-model:value="unbindForm.password"
            placeholder="请输入登录密码（推荐）"
          />
          <div class="form-help-text">强烈建议输入密码以提高安全性</div>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { UserOutlined } from '@ant-design/icons-vue'
import type { Rule } from 'ant-design-vue/es/form'
import type { FormInstance } from 'ant-design-vue'
import type { UploadRequestOption } from 'rc-upload/es/interface'
import { message } from 'ant-design-vue'
import { getSecurityStatus } from '@/api/security'
import { getCurrentUser, updateUser } from '@/api/user'
import { generateAvatarStyleObject } from '@/utils/avatar'
import { getActiveTenantId, resolveActiveTenantQueryValue, withActiveTenantQuery } from '@/utils/tenant'

// 路由
const route = useRoute()
const router = useRouter()

const buildNavigationQuery = () =>
  withActiveTenantQuery({}, resolveActiveTenantQueryValue(route.query) ?? getActiveTenantId())

// 当前激活的标签页
const activeKey = ref('basic')

// 基本信息表单
const basicForm = reactive({
  id: '',
  username: '',
  nickname: '',
  email: '',
  phone: '',
  enabled: true,
  accountNonExpired: true,
  accountNonLocked: true,
  credentialsNonExpired: true
})

// 基本信息表单验证规则
const basicRules: Record<string, Rule[]> = {
  nickname: [
    { required: true, message: '请输入昵称' },
    { max: 50, message: '昵称长度不能超过50个字符' }
  ],
  email: [
    {
      validator: (_rule: unknown, value: string) => {
        if (!value || value.trim() === '') {
          return Promise.resolve() // 允许为空
        }
        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
        if (!emailRegex.test(value)) {
          return Promise.reject('请输入正确的邮箱地址')
        }
        if (value.length > 100) {
          return Promise.reject('邮箱长度不能超过100个字符')
        }
        return Promise.resolve()
      }
    }
  ],
  phone: [
    {
      validator: (_rule: unknown, value: string) => {
        if (!value || value.trim() === '') {
          return Promise.resolve() // 允许为空
        }
        const phoneRegex = /^1[3-9]\d{9}$/
        if (!phoneRegex.test(value)) {
          return Promise.reject('请输入正确的手机号（11位数字，以1开头）')
        }
        if (value.length > 20) {
          return Promise.reject('手机号长度不能超过20个字符')
        }
        return Promise.resolve()
      }
    }
  ]
}

// 基本信息加载状态
const basicLoading = ref(false)

// 头像相关
const avatarUrl = ref<string>('')
const avatarLoading = ref(false)

// 密码表单
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

// 密码表单验证规则
const passwordRules: Record<string, Rule[]> = {
  oldPassword: [
    { required: true, message: '请输入原密码' }
  ],
  newPassword: [
    { required: true, message: '请输入新密码' },
    { min: 6, max: 20, message: '密码长度为6-20个字符' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码' },
    {
      validator: (_rule: unknown, value: string) => {
        if (!value) {
          return Promise.reject('请确认新密码')
        }
        if (value !== passwordForm.newPassword) {
          return Promise.reject('两次输入的密码不一致')
        }
        return Promise.resolve()
      }
    }
  ]
}

// 密码修改加载状态
const passwordLoading = ref(false)

// TOTP 安全状态
const totpBound = ref(false)
const totpActivated = ref(false)
const securityLoading = ref(false)

// 解绑模态框状态
const unbindModalVisible = ref(false)
const unbindLoading = ref(false)
const unbindFormRef = ref<FormInstance>()

// 解绑表单
const unbindForm = reactive({
  totpCode: '',
  password: ''
})

// 解绑表单验证规则
const unbindRules: Record<string, Rule[]> = {
  totpCode: [
    { required: true, message: '请输入TOTP验证码' },
    { len: 6, message: '验证码必须为6位数字' },
    { pattern: /^\d+$/, message: '验证码只能包含数字' }
  ],
  password: [
    // 密码可选，不需要必填验证
  ]
}

// 加载安全状态
const loadSecurityStatus = async () => {
  securityLoading.value = true
  try {
    const data = await getSecurityStatus()
    totpBound.value = Boolean(data.totpBound)
    totpActivated.value = Boolean(data.totpActivated)
  } catch (error: any) {
    console.error('加载安全状态失败:', error)
    // fetchWithTraceId 已经统一处理了 401 和网络错误，会自动跳转到登录页
    // 这里只需要显示错误提示（如果还没有跳转）
    if (!error.message?.includes('未授权') && !error.message?.includes('超时') && !error.message?.includes('网络错误')) {
    message.error('加载安全状态失败')
    }
  } finally {
    securityLoading.value = false
  }
}

// 显示解绑模态框
const showUnbindModal = () => {
  unbindModalVisible.value = true
  resetUnbindForm()
}

// 重置解绑表单
const resetUnbindForm = () => {
  unbindForm.totpCode = ''
  unbindForm.password = ''
  unbindFormRef.value?.resetFields()
}

// 提交解绑
const handleUnbindSubmit = async () => {
  try {
    // 验证表单
    await unbindFormRef.value?.validate()
    
    unbindLoading.value = true
    const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:9000'
    const baseUrl = apiBaseUrl.endsWith('/') ? apiBaseUrl.slice(0, -1) : apiBaseUrl
    const { fetchWithTraceId } = await import('@/utils/traceId')
    
    const response = await fetchWithTraceId(`${baseUrl}/self/security/totp/unbind`, {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json'
      },
      body: JSON.stringify({
        totpCode: unbindForm.totpCode,
        password: unbindForm.password || null
      })
    })
    
    if (!response.ok) {
      const errorData = await response.json()
      throw new Error(errorData.error || '解绑失败')
    }
    
    const data = await response.json()
    if (data.success) {
      message.success('解绑成功')
      unbindModalVisible.value = false
      resetUnbindForm()
      // 重新加载安全状态
      await loadSecurityStatus()
    } else {
      throw new Error(data.error || '解绑失败')
    }
  } catch (error: any) {
    console.error('解绑失败:', error)
    if (error.errorFields) {
      // 表单验证错误
      return
    }
    message.error(error.message || '解绑失败，请检查验证码是否正确')
  } finally {
    unbindLoading.value = false
  }
}

// 计算头像URL（如果用户有头像）
const getAvatarUrl = () => {
  if (!basicForm.id) return ''
  const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:9000'
  const baseUrl = apiBaseUrl.endsWith('/') ? apiBaseUrl.slice(0, -1) : apiBaseUrl
  return `${baseUrl}/sys/users/${basicForm.id}/avatar?t=${Date.now()}`
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
  // 确保 id 和 username 存在才生成样式
  if (basicForm.id && basicForm.username) {
    return generateAvatarStyleObject(basicForm.id, basicForm.username)
  }
  return {}
})

// 处理头像加载错误
const handleAvatarError = () => {
  // 头像加载失败时，使用默认图标
  avatarUrl.value = ''
}

// 头像上传前的验证
const beforeAvatarUpload = (file: File) => {
  const isImage = file.type.startsWith('image/')
  const allowedTypes = ['image/png', 'image/jpeg', 'image/jpg', 'image/webp']
  const isValidType = allowedTypes.includes(file.type.toLowerCase())
  const isLt1M = file.size / 1024 / 1024 < 1

  if (!isImage || !isValidType) {
    message.error('只能上传 PNG、JPEG、WebP 格式的图片!')
    return false
  }
  if (!isLt1M) {
    message.error('图片大小不能超过 1MB!')
    return false
  }
  return true
}

// 处理头像上传
const handleAvatarUpload = async (options: UploadRequestOption) => {
  const { file } = options
  avatarLoading.value = true
  try {
    const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:9000'
    const baseUrl = apiBaseUrl.endsWith('/') ? apiBaseUrl.slice(0, -1) : apiBaseUrl
    
    const formData = new FormData()
    if (file instanceof File) {
      formData.append('file', file)
    } else {
      formData.append('file', file as any)
    }

    const { fetchWithTraceId } = await import('@/utils/traceId')
    const response = await fetchWithTraceId(`${baseUrl}/sys/users/current/avatar`, {
      method: 'POST',
      credentials: 'include',
      body: formData
    })

    if (!response.ok) {
      const errorData = await response.json()
      throw new Error(errorData.error || '头像上传失败')
    }

    const data = await response.json()
    if (data.success) {
      message.success('头像上传成功')
      // 更新头像URL（添加时间戳避免缓存）
      updateAvatarUrl()
      // 触发自定义事件，通知 HeaderBar 更新头像
      window.dispatchEvent(new CustomEvent('avatar-uploaded', {
        detail: { userId: basicForm.id }
      }))
    } else {
      throw new Error(data.error || '头像上传失败')
    }
  } catch (error: any) {
    console.error('头像上传失败:', error)
    message.error(error.message || '头像上传失败')
  } finally {
    avatarLoading.value = false
  }
}

// 加载用户信息
const loadUserInfo = async () => {
  try {
    const data = await getCurrentUser()
    basicForm.id = data.id
    basicForm.username = data.username
    basicForm.nickname = data.nickname || ''
    basicForm.email = data.email || ''
    basicForm.phone = data.phone || ''
    // 加载用户状态字段（虽然不可修改，但需要传递以通过后端验证）
    basicForm.enabled = data.enabled !== undefined ? data.enabled : true
    basicForm.accountNonExpired = data.accountNonExpired !== undefined ? data.accountNonExpired : true
    basicForm.accountNonLocked = data.accountNonLocked !== undefined ? data.accountNonLocked : true
    basicForm.credentialsNonExpired = data.credentialsNonExpired !== undefined ? data.credentialsNonExpired : true
    // 更新头像URL
    updateAvatarUrl()
  } catch (error) {
    console.error('加载用户信息失败:', error)
    message.error('加载用户信息失败')
  }
}

// 提交基本信息
const handleBasicSubmit = async () => {
  basicLoading.value = true
  try {
    // 处理空值：空字符串转换为 undefined
    const emailValue = basicForm.email && basicForm.email.trim() ? basicForm.email.trim() : undefined
    const phoneValue = basicForm.phone && basicForm.phone.trim() ? basicForm.phone.trim() : undefined
    
    // 提交基本信息（包含 username，虽然不可修改但需要传递以通过后端验证）
    await updateUser(basicForm.id, {
      username: basicForm.username, // 必需字段，虽然不可修改但需要传递
      nickname: basicForm.nickname,
      email: emailValue,
      phone: phoneValue,
      enabled: basicForm.enabled, // 从当前用户信息中获取
      accountNonExpired: basicForm.accountNonExpired, // 从当前用户信息中获取
      accountNonLocked: basicForm.accountNonLocked, // 从当前用户信息中获取
      credentialsNonExpired: basicForm.credentialsNonExpired // 从当前用户信息中获取
    })
    message.success('基本信息更新成功')
    await loadUserInfo()
  } catch (error) {
    console.error('更新基本信息失败:', error)
    message.error('更新基本信息失败')
  } finally {
    basicLoading.value = false
  }
}

// 重置基本信息表单
const resetBasicForm = async () => {
  await loadUserInfo()
}

// 提交密码修改
const handlePasswordSubmit = async () => {
  passwordLoading.value = true
  try {
    // TODO: 这里需要调用修改密码的API，可能需要先验证原密码
    // 目前先使用更新用户接口，后端需要支持密码修改
    await updateUser(basicForm.id, {
      password: passwordForm.newPassword,
      oldPassword: passwordForm.oldPassword
    })
    message.success('密码修改成功，请重新登录')
    // 清空密码表单
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
    // 延迟跳转到登录页
    setTimeout(() => {
      router.push('/login')
    }, 1500)
  } catch (error) {
    console.error('修改密码失败:', error)
    message.error('修改密码失败，请检查原密码是否正确')
  } finally {
    passwordLoading.value = false
  }
}

// 重置密码表单
const resetPasswordForm = () => {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
}

// 绑定两步验证
const handleBindTotp = () => {
  router.push({
    path: '/self/security/totp-bind',
    query: buildNavigationQuery(),
  })
}

// 组件挂载时加载数据
onMounted(() => {
  loadUserInfo()
  loadSecurityStatus()
})
</script>

<style scoped>
.setting-page {
  padding: 24px;
  background: #f0f2f5;
  min-height: calc(100vh - 64px);
}

.form-help-text {
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
  margin-top: 4px;
}

.avatar-upload-section {
  display: flex;
  align-items: center;
  gap: 16px;
}

.user-avatar {
  cursor: pointer;
  transition: opacity 0.3s;
}

.user-avatar:hover {
  opacity: 0.8;
}

.avatar-upload-tips {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.upload-tip {
  font-size: 14px;
  color: rgba(0, 0, 0, 0.85);
}

.upload-hint {
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
}
</style>
