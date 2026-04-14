import request from '@/utils/request'

export interface PermissionOptionItem {
  id: number
  permissionCode: string
  permissionName: string
}

export function getPermissionOptions(keyword?: string, limit = 50): Promise<PermissionOptionItem[]> {
  return request.get('/sys/permissions/options', {
    params: { keyword, limit },
  })
}

