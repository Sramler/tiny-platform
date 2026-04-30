import { userManager } from './oidc'
import { logger } from '@/utils/logger'

/**
 * OIDC silent renew 的 iframe 回调入口。
 *
 * 这个文件由项目根目录的 `silent-renew.html` 引入，并通过 Vite 正常打包；
 * 不要在这里依赖 CDN。平台内网、本地开发或离线环境中，CDN 不可达会导致
 * `signinSilent()` 一直等不到 iframe 回调，进而让主应用在认证初始化阶段白屏。
 */
userManager
  .signinSilentCallback()
  .then(() => {
    logger.debug('[OIDC] Silent renew completed')
  })
  .catch((error) => {
    logger.error('[OIDC] Silent renew error', error)
  })
