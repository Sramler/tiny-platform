export interface ProcessPreviewInfo {
  name?: unknown
  key?: unknown
  version?: unknown
}

export const escapeProcessPreviewHtml = (value: unknown): string =>
  String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')

export function buildProcessPreviewHtml(process: ProcessPreviewInfo): string {
  const processName = escapeProcessPreviewHtml(process.name)
  const processKey = escapeProcessPreviewHtml(process.key)
  const processVersion = escapeProcessPreviewHtml(process.version)

  return (
    '<!DOCTYPE html>' +
    '<html>' +
    '<head>' +
    '<title>流程预览</title>' +
    '<style>' +
    'body { margin: 20px; font-family: Arial, sans-serif; }' +
    '.header { margin-bottom: 20px; padding: 20px; background: #f5f5f5; border-radius: 8px; }' +
    '.header h1 { margin: 0 0 10px 0; color: #333; }' +
    '.header p { margin: 5px 0; color: #666; }' +
    '.info { background: #e6f7ff; padding: 15px; border-radius: 6px; margin: 20px 0; }' +
    '</style>' +
    '</head>' +
    '<body>' +
    '<div class="header">' +
    '<h1>' +
    processName +
    '</h1>' +
    '<p><strong>流程Key:</strong> ' +
    processKey +
    '</p>' +
    '<p><strong>版本:</strong> ' +
    processVersion +
    '</p>' +
    '<p><strong>状态:</strong> 活跃</p>' +
    '</div>' +
    '<div class="info">' +
    '<h3>流程预览功能</h3>' +
    '<p>BPMN流程图预览功能正在开发中，当前显示流程基本信息。</p>' +
    '<p>您可以在建模页面查看和编辑完整的BPMN流程图。</p>' +
    '</div>' +
    '</body>' +
    '</html>'
  )
}
