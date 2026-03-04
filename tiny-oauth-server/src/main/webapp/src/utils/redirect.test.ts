import { describe, expect, it } from 'vitest'

import { sanitizeInternalRedirect } from '@/utils/redirect'

describe('sanitizeInternalRedirect', () => {
  it('should keep safe internal relative paths', () => {
    expect(sanitizeInternalRedirect('/dashboard')).toBe('/dashboard')
    expect(sanitizeInternalRedirect('/oauth2/authorize?client_id=vue-client')).toBe(
      '/oauth2/authorize?client_id=vue-client',
    )
  })

  it('should reject external and unsafe redirects', () => {
    expect(sanitizeInternalRedirect('https://evil.com/callback')).toBe('/')
    expect(sanitizeInternalRedirect('//evil.com/callback')).toBe('/')
    expect(sanitizeInternalRedirect('javascript:alert(1)')).toBe('/')
  })

  it('should normalize same-origin absolute urls into relative path', () => {
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: new URL('http://localhost:5173/login'),
    })

    expect(sanitizeInternalRedirect('http://localhost:5173/profile?tab=security')).toBe(
      '/profile?tab=security',
    )
  })

  it('should fallback when value is blank or contains unsafe chars', () => {
    expect(sanitizeInternalRedirect('')).toBe('/')
    expect(sanitizeInternalRedirect('/safe\u0000path')).toBe('/')
    expect(sanitizeInternalRedirect(undefined, '/fallback')).toBe('/fallback')
  })
})
