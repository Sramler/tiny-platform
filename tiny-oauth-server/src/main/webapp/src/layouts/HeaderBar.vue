<template>
  <!-- 使用单个根节点包装所有内容，并继承所有属性 -->
  <div v-bind="$attrs" class="header-bar-container">
    <!-- 顶部栏容器 -->
    <div class="header-bar">
      <!-- 左侧占位（可放面包屑等） -->
      <div class="left"></div>
      <!-- 右侧用户信息区域 -->
      <div class="right">
        <a-dropdown trigger="hover" placement="bottomRight">
          <div class="dropdown" @click.stop>
            <div class="user-info">
              <!-- 用户头像 -->
              <img v-if="avatarUrl" class="avatar" :src="avatarUrl" alt="avatar" @error="handleAvatarError" />
              <!-- 默认头像图标（当没有头像时显示） -->
              <div v-else class="avatar-icon" :style="avatarStyle">
                <UserOutlined />
              </div>
              <!-- 用户名和下拉箭头 -->
              <span class="username">{{ username }}</span>
              <DownOutlined class="dropdown-icon" />
            </div>
          </div>
          <template #overlay>
            <a-menu class="dropdown-menu" @click="handleMenuSelect">
              <a-menu-item :key="MENU_KEYS.PROFILE">
                <UserOutlined class="menu-icon" />
                个人中心
              </a-menu-item>
              <a-menu-item :key="MENU_KEYS.SETTINGS">
                <SettingOutlined class="menu-icon" />
                个人设置
              </a-menu-item>
              <a-menu-item v-if="canSwitchScopeEntry" :key="MENU_KEYS.SCOPE">
                <SettingOutlined class="menu-icon" />
                切换作用域
              </a-menu-item>
              <a-menu-item :key="MENU_KEYS.LOGOUT">
                <LogoutOutlined class="menu-icon" />
                退出登录
              </a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
        <a-tag v-if="activeScopeLabel" class="scope-tag" color="blue">{{ activeScopeLabel }}</a-tag>
      </div>
    </div>
    <!-- 标签页导航插槽 -->
    <slot name="tags"></slot>

    <a-modal
      v-model:open="scopeModalOpen"
      title="切换作用域"
      ok-text="切换"
      cancel-text="取消"
      :confirm-loading="scopeSwitching"
      @ok="confirmSwitchScope"
    >
      <a-space direction="vertical" style="width: 100%">
        <a-radio-group v-model:value="nextScopeType" @change="onScopeTypeRadioChange">
          <a-radio-button value="TENANT">TENANT</a-radio-button>
          <a-radio-button value="ORG">ORG</a-radio-button>
          <a-radio-button value="DEPT">DEPT</a-radio-button>
        </a-radio-group>
        <a-select
          v-if="nextScopeType !== 'TENANT'"
          v-model:value="nextScopeId"
          placeholder="选择 org/dept"
          style="width: 100%"
          :options="scopeUnitOptions"
          show-search
          :filter-option="filterScopeOption"
        />
      </a-space>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { UserOutlined, SettingOutlined, LogoutOutlined, DownOutlined } from '@ant-design/icons-vue'
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuth, refreshTokenAfterActiveScopeSwitch } from '@/auth/auth'
import { getCurrentUser, switchActiveScope, type ActiveScopeType } from '@/api/user'
import { notifyActiveScopeChanged } from '@/utils/activeScopeEvents'
import { getOrgList, type OrgUnit } from '@/api/org'
import { generateAvatarStyleObject } from '@/utils/avatar'
import { getActiveTenantId, setActiveTenantId } from '@/utils/tenant'
import { message } from 'ant-design-vue'
import type { MenuProps } from 'ant-design-vue'

/**
 * 常量定义
 */

/**
 * 菜单键值常量
 */
const MENU_KEYS = {
  PROFILE: 'profile', // 个人中心
  SETTINGS: 'settings', // 个人设置
  SCOPE: 'scope', // 切换作用域
  LOGOUT: 'logout' // 退出登录
} as const

/**
 * 路由路径常量
 */
const ROUTES = {
  PROFILE_CENTER: '/profile/center', // 个人中心路由
  PROFILE_SETTING: '/profile/setting' // 个人设置路由
} as const

/**
 * 默认用户名常量
 */
const DEFAULT_USERNAME = '管理员' // 默认显示的用户名
const FALLBACK_USERNAME = '用户' // 加载失败时的备用用户名

/**
 * 类型定义
 */

/**
 * 头像上传事件详情
 */
interface AvatarUploadedEventDetail {
  userId?: string | number // 用户ID（可选）
}

/**
 * 头像上传事件类型
 */
type AvatarUploadedEvent = CustomEvent<AvatarUploadedEventDetail>

/**
 * 组件配置
 */
defineOptions({
  name: 'HeaderBar',
  inheritAttrs: false // 禁用自动属性继承，手动控制属性绑定
})

/**
 * 路由和认证
 */
const router = useRouter()
const { logout } = useAuth()

/**
 * 响应式状态
 */
// 用户名
const username = ref<string>(DEFAULT_USERNAME)
// 头像 URL
const avatarUrl = ref<string>('')
// 用户 ID
const userId = ref<string>('')
const activeScopeType = ref<string>('TENANT')
const activeScopeId = ref<number | null>(null)

const scopeModalOpen = ref(false)
const scopeSwitching = ref(false)
const nextScopeType = ref<ActiveScopeType>('TENANT')
const nextScopeId = ref<number | null>(null)
const orgUnits = ref<OrgUnit[]>([])

/** 平台态（activeScopeType=PLATFORM）且本地没有 activeTenantId 时，不允许打开作用域切换入口。 */
const canSwitchScopeEntry = computed(() => {
  if (activeScopeType.value !== 'PLATFORM') return true
  return Boolean(getActiveTenantId())
})

/** 仅在用户切换到 ORG/DEPT 时拉取组织列表（不在弹窗打开时请求，避免当前活动 scope 为 ORG 时误触发 GET /sys/org/list） */
async function onScopeTypeRadioChange() {
  const type = nextScopeType.value
  if (type === 'TENANT' || orgUnits.value.length > 0) return
  try {
    orgUnits.value = await getOrgList()
  } catch {
    message.error('加载组织列表失败')
  }
}

const activeScopeLabel = computed(() => {
  if (!activeScopeType.value) return ''
  if (activeScopeType.value === 'TENANT') return 'TENANT'
  if (!activeScopeId.value) return activeScopeType.value
  return `${activeScopeType.value}:${activeScopeId.value}`
})

const scopeUnitOptions = computed(() => {
  const type = nextScopeType.value
  const filtered = orgUnits.value.filter((u) => (type === 'ORG' ? u.unitType === 'ORG' : u.unitType === 'DEPT'))
  return filtered.map((u) => ({ value: u.id, label: `${u.name} (#${u.id})` }))
})

function filterScopeOption(input: string, option?: { label: string }) {
  if (!option?.label) return false
  return option.label.toLowerCase().includes((input || '').toLowerCase())
}

/**
 * 工具函数
 */

/**
 * 获取 API 基础 URL
 * 从环境变量读取，如果没有则使用默认值
 * @returns API 基础 URL（已去除尾部斜杠）
 */
function getApiBaseUrl(): string {
  const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:9000'
  return apiBaseUrl.endsWith('/') ? apiBaseUrl.slice(0, -1) : apiBaseUrl
}

/**
 * 构建头像 URL
 * 添加时间戳参数避免浏览器缓存
 * @param userId 用户ID
 * @returns 头像 URL
 */
function buildAvatarUrl(userId: string | number): string {
  if (!userId) return ''
  const baseUrl = getApiBaseUrl()
  return `${baseUrl}/sys/users/${userId}/avatar?t=${Date.now()}`
}

/**
 * 更新头像 URL
 * 根据当前用户ID更新头像URL
 */
function updateAvatarUrl() {
  if (userId.value) {
    avatarUrl.value = buildAvatarUrl(userId.value)
  } else {
    avatarUrl.value = ''
  }
}

/**
 * 业务逻辑函数
 */

/**
 * 处理菜单项点击
 * 根据不同的菜单项执行相应的操作
 * @param action 菜单操作类型
 */
async function handleMenuClick(action: string) {
  switch (action) {
    case MENU_KEYS.PROFILE:
      // 跳转到个人中心页面
      router.push(ROUTES.PROFILE_CENTER)
      break
    case MENU_KEYS.SETTINGS:
      // 跳转到个人设置页面
      router.push(ROUTES.PROFILE_SETTING)
      break
    case MENU_KEYS.SCOPE:
      if (!canSwitchScopeEntry.value) {
        message.warning('当前平台态不支持在此处切换作用域')
        return
      }
      scopeModalOpen.value = true
      nextScopeType.value = (activeScopeType.value as ActiveScopeType) || 'TENANT'
      nextScopeId.value = activeScopeId.value
      break
    case MENU_KEYS.LOGOUT:
      // 执行退出登录逻辑
      try {
        await logout()
      } catch (error) {
        const errorMessage = error instanceof Error ? error.message : '未知错误'
        message.error(`退出登录失败：${errorMessage}`)
      }
      break
    default:
      // 忽略未知操作
      break
  }
}

async function confirmSwitchScope() {
  if (!canSwitchScopeEntry.value) {
    message.warning('当前平台态不支持在此处切换作用域')
    scopeModalOpen.value = false
    return
  }
  try {
    scopeSwitching.value = true
    const switchResult = await switchActiveScope({
      scopeType: nextScopeType.value,
      scopeId: nextScopeType.value === 'TENANT' ? undefined : (nextScopeId.value ?? undefined),
    })

    if (switchResult.tokenRefreshRequired === true) {
      const renew = await refreshTokenAfterActiveScopeSwitch()
      if (!renew.ok) {
        message.warning('作用域已在服务端更新，但未能刷新访问令牌。请重新登录后再继续使用。')
        scopeModalOpen.value = false
        return
      }
    }

    const profileOk = await loadUserInfo({ suppressErrorToast: true })
    if (!profileOk) {
      message.warning('作用域已在服务端更新，但未能加载当前用户信息。请刷新页面或重新登录后再试。')
      scopeModalOpen.value = false
      return
    }
    notifyActiveScopeChanged()
    message.success('作用域已切换')
    scopeModalOpen.value = false
  } catch {
    message.error('切换作用域失败')
  } finally {
    scopeSwitching.value = false
  }
}

/**
 * 菜单选择处理函数
 * 适配 Ant Design Vue Menu 组件的点击事件
 */
const handleMenuSelect: MenuProps['onClick'] = (info) => {
  handleMenuClick(info.key as string)
}

/**
 * 加载用户信息
 * 从后端 API 获取当前用户信息并更新显示
 * @returns 是否成功拉取并更新展示字段（失败时回退展示占位；默认 toast，可抑制以避免与作用域切换提示重复）
 */
async function loadUserInfo(options?: { suppressErrorToast?: boolean }): Promise<boolean> {
  try {
    const data = await getCurrentUser()
    const tid = (data as { activeTenantId?: unknown }).activeTenantId
    if (tid != null && tid !== '') {
      setActiveTenantId(tid as string | number)
    }
    // 优先使用昵称，其次用户名，最后使用备用用户名
    username.value = data.nickname || data.username || FALLBACK_USERNAME
    userId.value = String(data.id || '')
    activeScopeType.value = (data as any).activeScopeType || 'TENANT'
    activeScopeId.value = typeof (data as any).activeScopeId === 'number' ? (data as any).activeScopeId : null
    updateAvatarUrl()
    return true
  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : '未知错误'
    if (!options?.suppressErrorToast) {
      message.error(`加载用户信息失败：${errorMessage}`)
    }
    // 加载失败时使用备用值
    username.value = FALLBACK_USERNAME
    userId.value = ''
    avatarUrl.value = ''
    return false
  }
}

/**
 * 处理头像加载错误
 * 当头像图片加载失败时，使用默认图标和随机颜色
 */
function handleAvatarError() {
  avatarUrl.value = ''
}

/**
 * 计算头像样式（当没有头像时使用随机颜色）
 * 使用计算属性，根据用户ID和用户名生成随机颜色
 */
const avatarStyle = computed(() => {
  if (avatarUrl.value) {
    // 有头像时不使用样式
    return {}
  }
  // 没有头像时使用基于用户ID的随机颜色
  return generateAvatarStyleObject(userId.value, username.value)
})

/**
 * 监听头像上传成功事件
 * 当其他组件上传头像成功后，通过自定义事件通知更新头像
 * @param event 自定义事件
 */
function handleAvatarUploaded(event: Event) {
  const customEvent = event as AvatarUploadedEvent
  // 如果事件中包含 userId，且与当前用户ID匹配，则更新头像
  // 如果没有 userId，则默认更新（可能是当前用户上传的）
  if (!customEvent.detail?.userId || String(customEvent.detail.userId) === userId.value) {
    updateAvatarUrl()
  }
}

/**
 * 生命周期
 */

// 组件挂载时加载用户信息和监听事件
onMounted(() => {
  void loadUserInfo()
  // 监听全局头像上传成功事件
  window.addEventListener('avatar-uploaded', handleAvatarUploaded)
})

// 组件卸载时移除事件监听
onUnmounted(() => {
  window.removeEventListener('avatar-uploaded', handleAvatarUploaded)
})

/** 供单元测试直接编排 `confirmSwitchScope`，勿在生产业务代码中依赖。 */
defineExpose({
  confirmSwitchScope,
})
</script>

<style scoped>
/* 根容器样式 */
.header-bar-container {
  width: 100%;
  display: flex;
  flex-direction: column;
}

/* 顶部栏主容器 */
.header-bar {
  /* 使用 CSS 变量定义高度，便于主题定制 */
  height: var(--header-height);
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #eee;
  padding: 0;
  position: relative;
  z-index: 100;
  /* 使用与系统UI一致的字体 */
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial,
    'Noto Sans', sans-serif, 'Apple Color Emoji', 'Segoe UI Emoji', 'Segoe UI Symbol',
    'Noto Color Emoji';
  font-size: 14px;
  line-height: 1.5715;
  /* 确保布局正确 */
  width: 100%;
  box-sizing: border-box;
}

.scope-tag {
  margin-left: 12px;
}

/* 左侧占位区域 */
.left {
  flex: 1;
  min-width: 0;
  /* 确保左侧区域不会挤压右侧 */
  overflow: hidden;
}

/* 右侧用户信息区域 */
.right {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-shrink: 0;
  margin-left: auto;
  /* 确保右侧区域始终显示在最右侧 */
  position: relative;
  z-index: 1;
  /* 确保不被其他元素影响 */
  min-width: fit-content;
}

/* 下拉菜单容器 */
.dropdown {
  position: relative;
  cursor: pointer;
  /* 确保下拉菜单有正确的定位上下文 */
  z-index: 1000;
}

/* 用户信息区域样式 */
.user-info {
  display: flex;
  align-items: center;
  padding: 0 8px;
  border-radius: 6px;
  transition: background-color 0.3s;
  height: 32px;
}

/* 用户信息悬浮效果 */
.user-info:hover {
  background-color: rgba(0, 0, 0, 0.025);
}

/* 用户头像图片 */
.avatar {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  margin-right: 8px;
  flex-shrink: 0;
  object-fit: cover;
}

/* 默认头像图标容器 */
.avatar-icon {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  margin-right: 8px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 14px;
  /* 背景色由 avatarStyle 动态设置 */
}

/* 用户名文字 */
.username {
  margin-right: 4px;
  font-weight: 400;
  color: rgba(0, 0, 0, 0.85);
  font-size: 14px;
  line-height: 1.5715;
  white-space: nowrap;
}

/* 下拉箭头图标 */
.dropdown-icon {
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
  transition: transform 0.3s;
  flex-shrink: 0;
}

/* 下拉菜单显示时箭头旋转（未使用，保留以备将来扩展） */
.dropdown-icon.rotated {
  transform: rotate(180deg);
}

/* 下拉菜单容器 */
.dropdown-menu {
  padding: 4px 0;
}

/* 菜单项样式 */
.dropdown-menu :deep(.ant-dropdown-menu-item) {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: rgba(0, 0, 0, 0.85);
  transition: background-color 0.3s, color 0.3s;
}

/* 菜单项悬浮和激活状态 */
.dropdown-menu :deep(.ant-dropdown-menu-item:hover),
.dropdown-menu :deep(.ant-dropdown-menu-item-active) {
  color: #1890ff;
  background: #f0f5ff;
}

/* 菜单项图标 */
.menu-icon {
  font-size: 14px;
  color: rgba(0, 0, 0, 0.45);
  flex-shrink: 0;
}

/* 菜单项悬浮时图标颜色 */
.dropdown-menu :deep(.ant-dropdown-menu-item:hover .menu-icon) {
  color: #1890ff;
}
</style>
