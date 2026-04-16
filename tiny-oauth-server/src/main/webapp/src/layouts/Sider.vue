<template>
  <!-- 侧边栏容器 -->
  <div :class="['sider', { collapsed }]" v-bind="$attrs">
    <!-- Logo 区域 -->
    <div class="logo">
      <img src="@/assets/logo.svg" alt="logo" class="logo-img" />
      <span v-if="!collapsed" class="logo-text">Tiny app</span>
    </div>
    <!-- 菜单列表，支持滚动 -->
    <div class="menu-scroll">
      <ul class="menu">
        <template v-for="(item, idx) in menuList" :key="item.url || item.id || idx">
          <!-- 一级菜单项 -->
          <li :class="['menu-item', { active: isActive(item), open: openMenu === idx }]" @click="toggleMenu(idx, item)">
            <!-- 菜单图标 -->
            <Icon v-if="item.showIcon && item.icon" :icon="item.icon" className="icon" />
            <!-- 菜单标题（折叠时隐藏） -->
            <span v-if="!collapsed" class="text">{{ item.title }}</span>
            <!-- 展开/折叠箭头（仅在有子菜单且未折叠时显示） -->
            <Icon v-if="hasChildren(item) && !collapsed" :icon="openMenu === idx ? 'DownOutlined' : 'RightOutlined'"
              class="arrow" />
          </li>
          <!-- 二级菜单（仅在一级菜单展开且未折叠时显示） -->
          <ul v-if="hasChildren(item) && openMenu === idx && !collapsed" class="submenu">
            <template v-for="(sub, subIdx) in item.children" :key="sub.url || sub.id || subIdx">
              <!-- 二级菜单项 -->
              <li :class="['submenu-item', { active: isActive(sub), open: openSubMenu === subIdx }]"
                @click.stop="toggleSubMenu(subIdx, sub)">
                <span class="text">{{ sub.title }}</span>
                <!-- 展开/折叠箭头（仅在有三级菜单时显示） -->
                <Icon v-if="hasChildren(sub)" :icon="openSubMenu === subIdx ? 'DownOutlined' : 'RightOutlined'"
                  class="arrow" />
              </li>
              <!-- 三级菜单（仅在二级菜单展开时显示） -->
              <ul v-if="hasChildren(sub) && openSubMenu === subIdx" class="submenu third">
                <li v-for="third in sub.children" :key="third.url || third.id || third.name"
                  :class="['submenu-item', { active: isActive(third) }]" @click.stop="handleThirdMenuClick(third)">
                  <span class="text">{{ third.title }}</span>
                </li>
              </ul>
            </template>
          </ul>
        </template>
      </ul>
    </div>

    <!-- 折叠按钮固定在底部 -->
    <div class="collapse-btn" @click="toggleCollapse">
      <Icon :icon="collapsed ? 'MenuUnfoldOutlined' : 'MenuFoldOutlined'" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { menuTree, type MenuItem } from '@/api/menu'
import { message } from 'ant-design-vue'
import Icon from '@/components/Icon.vue'

/**
 * 常量定义
 */
const DEFAULT_OPEN_MENU = 0 // 默认打开的一级菜单索引（工作台）
const INVALID_INDEX = -1 // 无效索引，表示未选中任何菜单
const COLLAPSED_STORAGE_KEY = 'sider-collapsed' // localStorage 中存储折叠状态的键名

/**
 * 组件配置
 */
defineOptions({
  name: 'SiderLayout',
  inheritAttrs: false // 禁用自动属性继承，手动控制属性绑定
})

/**
 * 路由实例
 */
const router = useRouter()
const route = useRoute()

/**
 * 响应式状态
 */
// 侧边栏折叠状态（从 localStorage 恢复）
const getInitialCollapsedState = (): boolean => {
  const stored = localStorage.getItem(COLLAPSED_STORAGE_KEY)
  return stored === 'true'
}
const collapsed = ref<boolean>(getInitialCollapsedState())

// 菜单项列表，初始为空，后续通过接口加载
const menuList = ref<MenuItem[]>([])

// 当前打开的一级菜单索引，默认工作台（0）
const openMenu = ref<number>(DEFAULT_OPEN_MENU)

// 当前打开的二级菜单索引
const openSubMenu = ref<number>(INVALID_INDEX)

/**
 * 工具函数
 */

/**
 * 检查菜单项是否有子菜单
 * @param item 菜单项
 * @returns 是否有子菜单
 */
function hasChildren(item: MenuItem): boolean {
  return !!(item.children && item.children.length > 0)
}

function normalizeMenuPath(path?: string): string | null {
  if (typeof path !== 'string') {
    return null
  }
  const normalized = path.trim()
  return normalized ? normalized : null
}

function isDescendantMenuPath(item: MenuItem, targetPath: string): boolean {
  if (!hasChildren(item)) {
    return normalizeMenuPath(item.url) === targetPath
  }
  return item.children!.some((child) => isDescendantMenuPath(child, targetPath))
}

function resolveFirstNavigablePath(items?: MenuItem[]): string | null {
  if (!Array.isArray(items)) {
    return null
  }
  for (const item of items) {
    const target = resolveMenuNavigationTarget(item)
    if (target) {
      return target
    }
  }
  return null
}

function resolveMenuNavigationTarget(item: MenuItem): string | null {
  if (!hasChildren(item)) {
    return normalizeMenuPath(item.url)
  }

  const redirect = normalizeMenuPath(item.redirect)
  if (redirect && isDescendantMenuPath(item, redirect)) {
    return redirect
  }

  return resolveFirstNavigablePath(item.children)
}

/**
 * 菜单位置接口定义
 */
interface MenuPosition {
  firstLevelIdx: number // 一级菜单索引
  secondLevelIdx: number | null // 二级菜单索引（如果是一级菜单则为 null）
}

/**
 * 递归查找菜单项在菜单树中的位置
 * 支持一级、二级、三级菜单的查找
 * @param menuList 菜单列表
 * @param targetPath 目标路径
 * @returns 菜单位置信息，如果未找到则返回 null
 */
function findMenuPosition(
  menuList: MenuItem[],
  targetPath: string
): MenuPosition | null {
  for (let i = 0; i < menuList.length; i++) {
    const item = menuList[i]
    if (!item) continue

    // 检查一级菜单
    if (item.url === targetPath) {
      return {
        firstLevelIdx: i,
        secondLevelIdx: null
      }
    }

    // 检查二级菜单
    if (hasChildren(item)) {
      for (let j = 0; j < item.children!.length; j++) {
        const sub = item.children![j]
        if (!sub) continue

        if (sub.url === targetPath) {
          return {
            firstLevelIdx: i,
            secondLevelIdx: j
          }
        }

        // 检查三级菜单
        if (hasChildren(sub)) {
          for (const third of sub.children!) {
            if (!third) continue
            if (third.url === targetPath) {
              return {
                firstLevelIdx: i,
                secondLevelIdx: j
              }
            }
          }
        }
      }
    }
  }

  return null
}

/**
 * 业务逻辑函数
 */

/**
 * 加载菜单数据
 * 从后端 API 获取菜单树结构
 */
async function loadMenu() {
  try {
    const data = await menuTree()

    if (data && Array.isArray(data) && data.length > 0) {
      menuList.value = data
    } else {
      menuList.value = []
      message.warning('未加载到可用菜单')
    }
  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : '未知错误'
    message.error(`加载菜单失败：${errorMessage}`)
    menuList.value = []
  }
}

/**
 * 切换折叠状态
 * 同时将状态保存到 localStorage 以便下次访问时恢复
 */
function toggleCollapse() {
  collapsed.value = !collapsed.value
  localStorage.setItem(COLLAPSED_STORAGE_KEY, String(collapsed.value))
}

/**
 * 一级菜单点击处理
 * 实现手风琴效果：点击已展开的菜单会折叠，点击未展开的菜单会展开
 * @param idx 菜单索引
 * @param item 菜单项
 */
function toggleMenu(idx: number, item: MenuItem) {
  if (hasChildren(item)) {
    // 切换展开/折叠
    openMenu.value = openMenu.value === idx ? INVALID_INDEX : idx
    openSubMenu.value = INVALID_INDEX

    const targetPath = resolveMenuNavigationTarget(item)
    if (targetPath) {
      goMenu(targetPath)
    }
  } else {
    // 叶子节点直接跳转到 path
    if (item.url) {
      goMenu(item.url)
      openMenu.value = idx
      openSubMenu.value = INVALID_INDEX
    }
  }
}

/**
 * 二级菜单点击处理
 * 实现手风琴效果
 * @param subIdx 二级菜单索引
 * @param sub 二级菜单项
 */
function toggleSubMenu(subIdx: number, sub: MenuItem) {
  if (hasChildren(sub)) {
    // 切换展开/折叠
    openSubMenu.value = openSubMenu.value === subIdx ? INVALID_INDEX : subIdx
    const targetPath = resolveMenuNavigationTarget(sub)
    if (targetPath) {
      goMenu(targetPath)
    }
  } else {
    // 叶子节点直接跳转
    if (sub.url) {
      goMenu(sub.url)
      openSubMenu.value = subIdx
    }
  }
}

/**
 * 三级菜单点击处理
 * @param third 三级菜单项
 */
function handleThirdMenuClick(third: MenuItem) {
  if (third.url) {
    goMenu(third.url)
  }
}

/**
 * 跳转到指定菜单路径
 * @param path 菜单路径
 */
function goMenu(path: string) {
  if (path) {
    router.push(path)
  }
}

/**
 * 判断当前路由是否激活（用于高亮显示）
 * @param item 菜单项
 * @returns 是否激活
 */
function isActive(item: MenuItem): boolean {
  return route.path === item.url
}

/**
 * 根据路由路径自动展开对应菜单
 * 用于路由变化时自动展开对应的菜单项
 * @param path 路由路径
 */
function expandMenuByPath(path: string) {
  if (!menuList.value.length) return

  const position = findMenuPosition(menuList.value, path)

  if (position) {
    openMenu.value = position.firstLevelIdx

    if (position.secondLevelIdx !== null) {
      openSubMenu.value = position.secondLevelIdx
    } else {
      openSubMenu.value = INVALID_INDEX
    }
  } else {
    // 如果找不到匹配的菜单，重置为默认状态
    openMenu.value = DEFAULT_OPEN_MENU
    openSubMenu.value = INVALID_INDEX
  }
}

/**
 * 监听器
 */

// 监听路由变化，自动展开对应菜单
watch(
  () => route.path,
  (newPath) => {
    expandMenuByPath(newPath)
  },
  { immediate: true } // 立即执行一次，确保初始路由也能正确展开
)

// 监听菜单列表变化，重新展开当前路由对应的菜单
// 用于菜单加载完成后自动展开当前路由对应的菜单项
watch(
  menuList,
  () => {
    if (menuList.value.length > 0) {
      expandMenuByPath(route.path)
    }
  },
  { deep: true } // 深度监听，确保子菜单变化也能触发
)

/**
 * 生命周期
 */

// 组件挂载时加载菜单
/**
 * 监听全局菜单重载事件，只刷新侧边栏
 */
const handleMenuReload = () => {
  // 避免事件在服务端渲染环境触发
  if (typeof window !== 'undefined') {
    loadMenu()
  }
}

onMounted(() => {
  loadMenu()
  if (typeof window !== 'undefined') {
    window.addEventListener('reload-menu-tree', handleMenuReload)
  }
})

onUnmounted(() => {
  if (typeof window !== 'undefined') {
    window.removeEventListener('reload-menu-tree', handleMenuReload)
  }
})
</script>

<style scoped>
/* 侧边栏主容器 */
.sider {
  /* 使用 CSS 变量定义宽度，便于主题定制 */
  width: var(--sider-width-expanded);
  color: #fff;
  height: 100vh;
  display: flex;
  flex-direction: column;
  transition: width 0.2s;
  /* 折叠/展开动画 */
  background: #fafbfc;
}

/* 折叠状态下的侧边栏 */
.sider.collapsed {
  width: var(--sider-width-collapsed);
}

/* Logo 区域 */
.logo {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  height: 56px;
  padding: 0 12px;
  min-width: 0;
  overflow: hidden;
}

.logo-img {
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  display: block;
}

.logo-text {
  margin-left: 10px;
  font-size: 20px;
  font-weight: bold;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 120px;
  line-height: 1;
  display: flex;
  align-items: center;
  color: #333;
}

/* 折叠状态下的 Logo 样式 */
.sider.collapsed .logo {
  justify-content: center;
  padding: 0;
}

.sider.collapsed .logo-img {
  margin: 0 auto;
}

/* 折叠状态下隐藏文字和箭头 */
.sider.collapsed .logo-text,
.sider.collapsed .text,
.sider.collapsed .arrow {
  display: none;
}

/* 菜单滚动区域 */
.menu-scroll {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
  /* 确保 flex 子元素可以正确收缩 */
}

/* 菜单列表 */
.menu {
  list-style: none;
  padding: 0;
  margin: 0;
}

/* 菜单项基础样式 */
.menu-item,
.submenu-item {
  color: rgba(0, 0, 0, 0.85);
  /* Ant Design Vue Pro 亮色主题默认文字颜色 */
}

/* 一级菜单项 */
.menu-item {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  cursor: pointer;
  transition: background 0.2s, color 0.2s;
  user-select: none;
  background: transparent;
  /* 默认背景透明 */
}

/* 一级菜单项悬停效果 */
.menu-item:hover:not(.active) {
  background: #f0f5ff;
  /* Ant Design Vue Pro 亮色主题悬停背景色 */
  color: #1890ff;
  /* Ant Design Vue Pro 亮色主题悬停文字颜色 */
}

/* 激活状态的一级菜单项 */
.menu-item.active {
  background: #1890ff;
  /* Ant Design Vue Pro 亮色主题选中背景色 */
  color: #fff;
  /* Ant Design Vue Pro 亮色主题选中文字颜色 */
}

/* 激活状态下的悬停效果（保持选中样式） */
.menu-item.active:hover {
  background: #1890ff;
  /* 保持选中背景色 */
  color: #fff;
  /* 保持选中文字颜色 */
}

/* 菜单图标 */
.menu-item .icon {
  font-size: 18px;
  width: 24px;
  text-align: center;
}

/* 菜单文字 */
.menu-item .text {
  margin-left: 8px;
}

/* 展开/折叠箭头 */
.menu-item .arrow {
  margin-left: auto;
  font-size: 12px;
  transition: transform 0.2s;
  width: 12px;
  text-align: center;
  margin-right: 16px;
}

/* 二级菜单容器 */
.submenu {
  padding-left: 24px;
  margin-top: 2px;
}

/* 二级菜单项 */
.submenu-item {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  cursor: pointer;
  font-size: 14px;
  transition: background 0.2s, color 0.2s;
  color: rgba(0, 0, 0, 0.85);
  /* Ant Design Vue Pro 亮色主题默认文字颜色 */
  border-radius: 4px;
  margin: 1px 0;
  background: transparent;
  /* 默认背景透明 */
}

/* 二级菜单项悬停效果 */
.submenu-item:hover:not(.active) {
  background: #f0f5ff;
  /* Ant Design Vue Pro 亮色主题悬停背景色 */
  color: #1890ff;
  /* Ant Design Vue Pro 亮色主题悬停文字颜色 */
}

/* 激活状态的二级菜单项 */
.submenu-item.active {
  background: #1890ff;
  /* Ant Design Vue Pro 亮色主题选中背景色 */
  color: #fff;
  /* Ant Design Vue Pro 亮色主题选中文字颜色 */
}

/* 激活状态下的悬停效果（保持选中样式） */
.submenu-item.active:hover {
  background: #1890ff;
  /* 保持选中背景色 */
  color: #fff;
  /* 保持选中文字颜色 */
}

/* 二级菜单项的箭头 */
.submenu-item .arrow {
  margin-left: auto;
  font-size: 12px;
  transition: transform 0.2s;
  width: 12px;
  text-align: center;
  margin-right: 16px;
}

/* 三级菜单容器 */
.submenu.third {
  background: transparent;
  padding-left: 24px;
  margin-top: 4px;
  border-left: 1px solid #f0f0f0;
  margin-left: 0;
}

/* 三级菜单项 */
.submenu.third .submenu-item {
  padding: 8px 12px;
  font-size: 14px;
  color: rgba(0, 0, 0, 0.85);
  /* Ant Design Vue Pro 亮色主题默认文字颜色 */
  position: relative;
  margin-left: -8px;
  margin-right: -16px;
  border-radius: 4px;
  margin-top: 1px;
  margin-bottom: 1px;
  background: transparent;
  /* 默认背景透明 */
}

/* 三级菜单项悬停效果 */
.submenu.third .submenu-item:hover:not(.active) {
  background: #f0f5ff !important;
  /* Ant Design Vue Pro 亮色主题悬停背景色 */
  color: #1890ff !important;
  /* Ant Design Vue Pro 亮色主题悬停文字颜色 */
}

/* 激活状态的三级菜单项 */
.submenu.third .submenu-item.active {
  background: #1890ff !important;
  /* Ant Design Vue Pro 亮色主题选中背景色 */
  color: #fff !important;
  /* Ant Design Vue Pro 亮色主题选中文字颜色 */
}

/* 确保三级菜单样式优先级最高 */
.submenu.third li.submenu-item:hover:not(.active) {
  background: #f0f5ff !important;
  /* Ant Design Vue Pro 亮色主题悬停背景色 */
  color: #1890ff !important;
  /* Ant Design Vue Pro 亮色主题悬停文字颜色 */
}

.submenu.third li.submenu-item.active {
  background: #1890ff !important;
  /* Ant Design Vue Pro 亮色主题选中背景色 */
  color: #fff !important;
  /* Ant Design Vue Pro 亮色主题选中文字颜色 */
}

/* 激活状态下的悬停效果（保持选中样式） */
.submenu.third li.submenu-item.active:hover {
  background: #1890ff !important;
  /* 保持选中背景色 */
  color: #fff !important;
  /* 保持选中文字颜色 */
}

/* 折叠按钮 */
.collapse-btn {
  cursor: pointer;
  padding: 12px 0;
  font-size: 22px;
  text-align: center;
  color: #333;
  background: #fafbfc;
  border-top: 1px solid #e5e6eb;
  transition: background 0.2s, color 0.2s;
  width: 100%;
}

/* 折叠按钮悬浮效果 */
.collapse-btn:hover {
  background: #f0f5ff;
  color: #1890ff;
}

/* 折叠按钮图标样式 */
.collapse-btn :deep(.anticon) {
  font-size: 18px;
  width: 24px;
  vertical-align: middle;
  display: inline-block;
}
</style>
