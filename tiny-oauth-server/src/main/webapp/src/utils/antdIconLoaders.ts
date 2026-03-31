/**
 * Ant Design Icons 按文件拆分的懒加载映射（Vite import.meta.glob）。
 * 禁止 `import * from '@ant-design/icons-vue'`，否则会把整包打进 vendor-antd-icons。
 */
/** 相对路径以满足 Vite import.meta.glob 必须以 `./` 或 `/` 开头的要求 */
export const antdIconLoaders = import.meta.glob(
  '../../node_modules/@ant-design/icons-vue/es/icons/*.js',
) as Record<string, () => Promise<{ default: import('vue').Component }>>

export function resolveAntdIconLoader(
  iconName: string | undefined | null,
): (() => Promise<{ default: import('vue').Component }>) | null {
  const n = iconName?.trim()
  if (!n) return null
  const suffix = `/${n}.js`
  const key = Object.keys(antdIconLoaders).find((k) => k.endsWith(suffix))
  if (!key) return null
  return antdIconLoaders[key] ?? null
}

export function getAntdIconLoaderOrFallback(
  iconName: string | undefined | null,
): () => Promise<{ default: import('vue').Component }> {
  return resolveAntdIconLoader(iconName) ?? resolveAntdIconLoader('MenuOutlined')!
}
