import { describe, it, expect } from 'vitest'
import router from './index'

/**
 * 路由级 code-split：关键顶层路由须保持异步组件，避免与入口 chunk 混绑。
 * 若改为静态 import，此测试应失败以提醒回归。
 */
describe('router lazy routes (code-split)', () => {
  it('mainLayout and Login use function/async components', () => {
    const main = router.getRoutes().find((r) => r.name === 'mainLayout')
    const login = router.getRoutes().find((r) => r.name === 'Login')
    expect(main?.components?.default).toBeTypeOf('function')
    expect(login?.components?.default).toBeTypeOf('function')
  })
})
