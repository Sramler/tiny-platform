import type { Component } from 'vue'

type AntdIconModule = { default: Component }
type AntdIconLoader = () => Promise<AntdIconModule>

/**
 * 侧栏菜单图标白名单。
 *
 * 这里不能使用 `import.meta.glob('@ant-design/icons-vue/es/icons/*.js')`：
 * Vite 会为完整图标目录生成巨大映射，弱网下会把数百/数千个 icon 模块拖进启动瀑布。
 * 新菜单图标进入数据库 seed 时，同步把图标名加入此静态 map；未知图标统一兜底 MenuOutlined。
 */
export const antdIconLoaders: Record<string, AntdIconLoader> = {
  AlertOutlined: () => import('@ant-design/icons-vue/es/icons/AlertOutlined.js'),
  DeploymentUnitOutlined: () => import('@ant-design/icons-vue/es/icons/DeploymentUnitOutlined.js'),
  DownOutlined: () => import('@ant-design/icons-vue/es/icons/DownOutlined.js'),
  HomeOutlined: () => import('@ant-design/icons-vue/es/icons/HomeOutlined.js'),
  MenuFoldOutlined: () => import('@ant-design/icons-vue/es/icons/MenuFoldOutlined.js'),
  MenuOutlined: () => import('@ant-design/icons-vue/es/icons/MenuOutlined.js'),
  MenuUnfoldOutlined: () => import('@ant-design/icons-vue/es/icons/MenuUnfoldOutlined.js'),
  RadarChartOutlined: () => import('@ant-design/icons-vue/es/icons/RadarChartOutlined.js'),
  ReadOutlined: () => import('@ant-design/icons-vue/es/icons/ReadOutlined.js'),
  RightOutlined: () => import('@ant-design/icons-vue/es/icons/RightOutlined.js'),
  ScheduleOutlined: () => import('@ant-design/icons-vue/es/icons/ScheduleOutlined.js'),
  SettingOutlined: () => import('@ant-design/icons-vue/es/icons/SettingOutlined.js'),
  SolutionOutlined: () => import('@ant-design/icons-vue/es/icons/SolutionOutlined.js'),
  TeamOutlined: () => import('@ant-design/icons-vue/es/icons/TeamOutlined.js'),
  UserOutlined: () => import('@ant-design/icons-vue/es/icons/UserOutlined.js'),
}

export const supportedAntdIconNames = Object.freeze(Object.keys(antdIconLoaders))

export function resolveAntdIconLoader(
  iconName: string | undefined | null,
): AntdIconLoader | null {
  const n = iconName?.trim()
  if (!n) return null
  return antdIconLoaders[n] ?? null
}

export function getAntdIconLoaderOrFallback(
  iconName: string | undefined | null,
): AntdIconLoader {
  return resolveAntdIconLoader(iconName) ?? antdIconLoaders.MenuOutlined
}
