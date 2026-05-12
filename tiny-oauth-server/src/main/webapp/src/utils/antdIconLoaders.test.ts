import { describe, expect, it } from 'vitest'
import {
  antdIconLoaders,
  getAntdIconLoaderOrFallback,
  resolveAntdIconLoader,
  supportedAntdIconNames,
} from './antdIconLoaders'

describe('antdIconLoaders', () => {
  it('uses an explicit startup-safe whitelist instead of a full icon glob', () => {
    expect(supportedAntdIconNames).toEqual(Object.keys(antdIconLoaders))
    expect(supportedAntdIconNames).toContain('MenuOutlined')
    expect(supportedAntdIconNames).toContain('DeploymentUnitOutlined')
    expect(supportedAntdIconNames).toContain('ScheduleOutlined')
    expect(supportedAntdIconNames.length).toBeLessThan(40)
  })

  it('resolves configured menu icons and falls back for unknown values', () => {
    expect(resolveAntdIconLoader('UserOutlined')).toBe(antdIconLoaders.UserOutlined)
    expect(resolveAntdIconLoader(' UserOutlined ')).toBe(antdIconLoaders.UserOutlined)
    expect(resolveAntdIconLoader('MissingOutlined')).toBeNull()
    expect(getAntdIconLoaderOrFallback('MissingOutlined')).toBe(antdIconLoaders.MenuOutlined)
  })
})
