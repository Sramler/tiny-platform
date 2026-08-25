let pageUnloading = false

if (typeof window !== 'undefined') {
  window.addEventListener(
    'pagehide',
    () => {
      pageUnloading = true
    },
    { once: true },
  )
}

/** 请求因浏览器离开或关闭当前 document 被取消时，不应记录成认证服务故障。 */
export function isPageUnloading(): boolean {
  return pageUnloading
}
