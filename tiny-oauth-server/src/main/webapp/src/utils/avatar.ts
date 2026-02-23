/**
 * 头像相关工具函数
 */

/**
 * 基于字符串生成确定性颜色
 * 使用简单哈希算法，确保相同输入总是得到相同颜色
 */
function stringToHash(str: string): number {
  let hash = 0
  if (str.length === 0) return hash
  for (let i = 0; i < str.length; i++) {
    const char = str.charCodeAt(i)
    hash = ((hash << 5) - hash) + char
    hash = hash & hash // Convert to 32bit integer
  }
  return Math.abs(hash)
}

/**
 * 预设的渐变颜色组合（美观且区分度高）
 * 每个颜色组合包含起始色和结束色
 */
const COLOR_PAIRS: Array<[string, string]> = [
  ['#667eea', '#764ba2'], // 紫蓝色
  ['#f093fb', '#f5576c'], // 粉红色
  ['#4facfe', '#00f2fe'], // 青色
  ['#43e97b', '#38f9d7'], // 绿色
  ['#fa709a', '#fee140'], // 粉黄色
  ['#30cfd0', '#330867'], // 深青色
  ['#a8edea', '#fed6e3'], // 浅粉绿
  ['#ff9a9e', '#fecfef'], // 浅粉色
  ['#ffecd2', '#fcb69f'], // 浅橙色
  ['#ff8a80', '#ff80ab'], // 珊瑚色
  ['#84fab0', '#8fd3f4'], // 薄荷蓝
  ['#a1c4fd', '#c2e9fb'], // 天蓝色
  ['#ffd89b', '#19547b'], // 金黄色
  ['#89f7fe', '#66a6ff'], // 蓝色
  ['#fbc2eb', '#a6c1ee'], // 紫色
  ['#fad961', '#f76b1c'], // 橙色
]

/**
 * 根据用户ID或用户名生成颜色
 * @param userId 用户ID（字符串或数字）
 * @param username 用户名（可选，用于备用）
 * @returns 渐变颜色的数组 [起始色, 结束色]
 */
export function generateAvatarColor(userId: string | number | null | undefined, username?: string | null): [string, string] {
  // 优先使用userId，如果没有则使用username
  const seed = userId?.toString() || username || 'default'
  
  // 使用哈希值选择颜色对
  const hash = stringToHash(seed)
  const colorIndex = hash % COLOR_PAIRS.length
  
  return COLOR_PAIRS[colorIndex] ?? ['#667eea', '#764ba2']
}

/**
 * 生成头像样式（背景渐变）
 * @param userId 用户ID
 * @param username 用户名
 * @returns CSS渐变样式字符串
 */
export function generateAvatarStyle(userId: string | number | null | undefined, username?: string | null): string {
  const [startColor, endColor] = generateAvatarColor(userId, username)
  return `background: linear-gradient(135deg, ${startColor} 0%, ${endColor} 100%);`
}

/**
 * 生成头像内联样式对象
 * @param userId 用户ID
 * @param username 用户名
 * @returns 样式对象
 */
export function generateAvatarStyleObject(userId: string | number | null | undefined, username?: string | null): { background: string } {
  const [startColor, endColor] = generateAvatarColor(userId, username)
  return {
    background: `linear-gradient(135deg, ${startColor} 0%, ${endColor} 100%)`
  }
}
