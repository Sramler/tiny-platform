/**
 * TRACE_ID 工具函数
 * 用于生成和管理分布式追踪 ID
 */

/**
 * 生成一个随机十六进制字符串（32 字符，用于 TRACE_ID）
 * 格式与后端 UUID 去掉连字符后的格式一致
 */
export function generateTraceId(): string {
  // 生成 UUID 格式的字符串，然后去掉连字符
  // 例如: 550e8400-e29b-41d4-a716-446655440000 -> 550e8400e29b41d4a716446655440000
  return crypto.randomUUID().replace(/-/g, '')
}

/**
 * 生成一个短的请求 ID（16 字符）
 */
export function generateRequestId(): string {
  return generateTraceId().substring(0, 16)
}

const TRACE_STORAGE_KEY = 'app_trace_id'
let inMemoryTraceId: string | null = null

const safeSessionStorage = {
  get(): string | null {
    if (typeof sessionStorage === 'undefined') {
      return inMemoryTraceId
    }
    try {
      const value = sessionStorage.getItem(TRACE_STORAGE_KEY)
      return value ?? inMemoryTraceId
    } catch (error) {
      console.warn('[TRACE_ID] 访问 sessionStorage 失败，使用内存 traceId 兜底', error)
      return inMemoryTraceId
    }
  },
  set(value: string): void {
    inMemoryTraceId = value
    if (typeof sessionStorage === 'undefined') {
      return
    }
    try {
      sessionStorage.setItem(TRACE_STORAGE_KEY, value)
    } catch (error) {
      console.warn('[TRACE_ID] 写入 sessionStorage 失败，仅保存到内存', error)
    }
  },
  remove(): void {
    inMemoryTraceId = null
    if (typeof sessionStorage === 'undefined') {
      return
    }
    try {
      sessionStorage.removeItem(TRACE_STORAGE_KEY)
    } catch (error) {
      console.warn('[TRACE_ID] 移除 sessionStorage 失败，已清理内存 traceId', error)
    }
  },
}

/**
 * 获取当前会话的 TRACE_ID
 * 如果不存在则生成一个新的
 */
export function getOrCreateTraceId(): string {
  let traceId = safeSessionStorage.get()

  if (!traceId) {
    traceId = generateTraceId()
    safeSessionStorage.set(traceId)
  }

  return traceId
}

/**
 * 创建新的 TRACE_ID 并替换会话中的旧值
 */
export function createNewTraceId(): string {
  const traceId = generateTraceId()
  safeSessionStorage.set(traceId)
  return traceId
}

/**
 * 清除当前会话的 TRACE_ID
 */
export function clearTraceId(): void {
  safeSessionStorage.remove()
}

/**
 * 获取当前的 TRACE_ID（不创建新的）
 */
export function getCurrentTraceId(): string | null {
  return safeSessionStorage.get()
}

/**
 * 为 fetch 请求添加 TRACE_ID headers
 * @param options 原始的 fetch RequestInit 选项
 * @returns 添加了 TRACE_ID headers 的选项
 */
export function addTraceIdToFetchOptions(options: RequestInit = {}): RequestInit {
  const traceId = getOrCreateTraceId()
  const requestId = generateRequestId()

  // 处理 headers，支持 Headers 对象或普通对象
  const headers = new Headers(options.headers)
  headers.set('X-Trace-Id', traceId)
  headers.set('X-Request-Id', requestId)

  return {
    ...options,
    headers,
  }
}

// 防止重复跳转的标记
let isRedirectingTo401 = false

/**
 * 处理 401 未授权错误，统一跳转到 401 页面
 * 避免在多个地方重复处理
 */
async function handleUnauthorized(): Promise<void> {
  const currentPath = window.location.pathname
  // 避免在登录页、回调页和 401 页面重复跳转
  if (currentPath !== '/login' && currentPath !== '/callback' && currentPath !== '/exception/401') {
    // 如果已经在跳转中，避免重复跳转
    if (isRedirectingTo401) {
      console.log('[401] 已在跳转中，跳过重复跳转')
      return
    }

    isRedirectingTo401 = true

    // 记录持久化日志（避免302跳转清空控制台）
    const { persistentLogger } = await import('@/utils/logger')
    persistentLogger.warn('[401] 检测到未授权，准备跳转到 401 页面', {
      currentPath,
      timestamp: new Date().toISOString(),
      userAgent: navigator.userAgent,
    })

    console.log('[401] 检测到未授权，准备跳转到 401 页面，当前路径:', currentPath)

    // ⚠️ 重要：先跳转到 401 页面，再处理 logout
    // 因为 logout() 会触发 window.location.href，会覆盖我们的跳转
    // 所以我们先跳转，然后在 401 页面中再处理 logout

    // 延迟跳转，给时间查看日志（开发环境）
    if (import.meta.env.DEV) {
      await new Promise((resolve) => setTimeout(resolve, 100))
    }

    // 使用 window.location 跳转，确保页面完全刷新并显示 401 页面
    // 这样可以避免路由守卫的干扰，确保 401 页面能正确显示
    const targetUrl = '/exception/401'
    console.log(
      '[401] 准备跳转到 401 页面，当前 URL:',
      window.location.href,
      '目标 URL:',
      targetUrl,
    )

    // 记录跳转前的状态
    persistentLogger.debug('[401] 跳转前状态', {
      currentPath: window.location.pathname,
      currentHref: window.location.href,
      targetUrl,
      timestamp: new Date().toISOString(),
    })

    try {
      // 方法1: 使用 window.location.href（推荐，会触发完整页面刷新）
      window.location.href = targetUrl
      console.log('[401] ✅ 已执行 window.location.href =', targetUrl)

      // 设置一个检查点，如果 500ms 后还在当前页面，尝试其他方法
      const checkInterval = setInterval(() => {
        const currentPath = window.location.pathname
        if (currentPath === targetUrl || currentPath === '/exception/401') {
          console.log('[401] ✅ 跳转成功，当前路径:', currentPath)
          clearInterval(checkInterval)
          isRedirectingTo401 = false // 重置标志
        } else if (currentPath !== '/login' && currentPath !== '/callback') {
          // 如果还在原页面或其他页面，尝试强制跳转
          console.warn('[401] ⚠️ 跳转可能未生效，当前路径:', currentPath, '尝试使用 replace')
          clearInterval(checkInterval)
          try {
            window.location.replace(targetUrl)
            console.log('[401] ✅ 已执行 window.location.replace =', targetUrl)
          } catch (replaceError) {
            console.error('[401] ❌ replace 失败:', replaceError)
            // 最后尝试使用 router（虽然可能被路由守卫拦截）
            import('@/router').then(({ default: router }) => {
              router.replace(targetUrl).catch((routerError) => {
                console.error('[401] ❌ router.replace 也失败:', routerError)
              })
            })
          }
        }
      }, 100)

      // 5秒后清除检查（避免内存泄漏）
      setTimeout(() => clearInterval(checkInterval), 5000)
    } catch (error) {
      console.error('[401] ❌ window.location.href 失败:', error)
      persistentLogger.error('[401] 跳转失败', error)

      // 备选方案1: 使用 replace
      try {
        window.location.replace(targetUrl)
        console.log('[401] ✅ 已执行 window.location.replace =', targetUrl)
      } catch (replaceError) {
        console.error('[401] ❌ replace 也失败:', replaceError)

        // 备选方案2: 使用 router（可能被路由守卫拦截，但至少尝试）
        import('@/router').then(({ default: router }) => {
          router.replace(targetUrl).catch((routerError) => {
            console.error('[401] ❌ router.replace 也失败:', routerError)
            // 如果所有方法都失败，至少显示一个错误提示
            alert('认证失败，请手动刷新页面或重新登录')
          })
        })
      }
    }
  } else {
    console.log('[401] 已在目标页面，跳过跳转:', currentPath)
    isRedirectingTo401 = false
  }
}

/**
 * 带 TRACE_ID 的 fetch 包装函数
 * 自动为请求添加 TRACE_ID headers，支持超时控制，并统一处理 401 和网络错误
 * @param url 请求 URL
 * @param options fetch 选项，可包含 timeout 属性（毫秒）和 skipAuthError 属性（跳过 401 处理）
 * @returns fetch Promise
 */
export async function fetchWithTraceId(
  url: string,
  options: RequestInit & { timeout?: number; skipAuthError?: boolean } = {},
): Promise<Response> {
  const { timeout = 5000, skipAuthError = false, ...fetchOptions } = options // 默认 5 秒超时
  const optionsWithTraceId = addTraceIdToFetchOptions(fetchOptions)
  const { getTenantId } = await import('@/utils/tenant')
  const headers = new Headers(optionsWithTraceId.headers)
  const tenantId = getTenantId()
  if (tenantId) {
    headers.set('X-Tenant-Id', tenantId)
  }
  optionsWithTraceId.headers = headers

  // 创建 AbortController 用于超时控制
  const controller = new AbortController()
  const timeoutId = timeout > 0 ? setTimeout(() => controller.abort(), timeout) : null

  try {
    const response = await fetch(url, {
      ...optionsWithTraceId,
      signal: controller.signal,
    })

    // 统一处理 401 未授权错误（后端 Session 丢失）
    // 注意：Spring Security 可能返回 302 重定向到登录页，这表示未认证
    // fetch 默认不会自动跟随重定向，所以 response.status 会是 302
    // 我们需要检查 response.status === 302 或 response.redirected === true
    const isUnauthorized =
      response.status === 401 ||
      (response.status === 302 && response.redirected) ||
      (response.status === 302 && response.url.includes('/login'))

    if (isUnauthorized && !skipAuthError) {
      const statusCode = response.status
      const redirectUrl = response.url
      console.warn(
        `检测到 ${statusCode} ${statusCode === 302 ? '重定向' : '未授权'}，后端 Session 可能已丢失，跳转到 401 页面`,
      )

      // 记录持久化日志（避免302跳转清空控制台）
      const { persistentLogger } = await import('@/utils/logger')
      persistentLogger.warn(
        `[${statusCode}] fetchWithTraceId 检测到未授权/重定向`,
        {
          url,
          redirectUrl,
          method: options.method || 'GET',
          headers: Object.fromEntries(new Headers(options.headers).entries()),
          status: statusCode,
          redirected: response.redirected,
          timestamp: new Date().toISOString(),
        },
        url,
        statusCode,
      )

      // 先执行跳转，再抛出错误
      // 注意：handleUnauthorized 会执行 window.location.href，这会触发页面跳转
      // 但为了确保跳转执行，我们需要等待一小段时间
      await handleUnauthorized()

      // 给跳转一些时间执行（开发环境延迟更长以便调试）
      const delay = import.meta.env.DEV ? 200 : 50
      await new Promise((resolve) => setTimeout(resolve, delay))

      // 抛出错误，让调用方知道请求失败
      throw new Error('未授权，请重新登录')
    }

    return response
  } catch (error) {
    // 如果是超时错误，抛出更明确的错误信息
    if (error instanceof DOMException && error.name === 'AbortError') {
      const timeoutError = new Error('请求超时') as Error & { code?: string }
      timeoutError.code = 'ECONNABORTED'

      // 网络错误时也跳转到登录页（后端可能重启）
      if (!skipAuthError) {
        console.warn('请求超时，后端可能不可用，跳转到登录页')
        await handleUnauthorized()
      }

      throw timeoutError
    }

    // 如果是网络错误，添加错误码
    if (error instanceof TypeError && error.message.includes('fetch')) {
      const networkError = new Error('网络错误') as Error & { code?: string }
      networkError.code = 'ERR_NETWORK'

      // 网络错误时也跳转到登录页（后端可能重启）
      if (!skipAuthError) {
        console.warn('网络错误，后端可能不可用，跳转到登录页')
        await handleUnauthorized()
      }

      throw networkError
    }

    // 如果已经是我们抛出的错误（如 401 处理后的错误），直接抛出
    if (error instanceof Error && error.message === '未授权，请重新登录') {
      throw error
    }

    throw error
  } finally {
    if (timeoutId) {
      clearTimeout(timeoutId)
    }
  }
}
