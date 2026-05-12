import { describe, expect, it } from 'vitest'
import { buildProcessPreviewHtml, escapeProcessPreviewHtml } from './processPreviewHtml'

describe('process preview HTML', () => {
  it('escapes unsafe process fields before writing preview HTML', () => {
    const html = buildProcessPreviewHtml({
      name: '<img src=x onerror=alert(1)>',
      key: 'demo"><script>alert(2)</script>',
      version: "1' onclick='alert(3)",
    })

    expect(html).toContain('&lt;img src=x onerror=alert(1)&gt;')
    expect(html).toContain('demo&quot;&gt;&lt;script&gt;alert(2)&lt;/script&gt;')
    expect(html).toContain('1&#39; onclick=&#39;alert(3)')
    expect(html).not.toContain('<img src=x onerror=alert(1)>')
    expect(html).not.toContain('<script>alert(2)</script>')
    expect(html).not.toContain("1' onclick='alert(3)")
  })

  it('escapes all HTML-sensitive characters consistently', () => {
    expect(escapeProcessPreviewHtml(`&<>"'`)).toBe('&amp;&lt;&gt;&quot;&#39;')
  })
})
