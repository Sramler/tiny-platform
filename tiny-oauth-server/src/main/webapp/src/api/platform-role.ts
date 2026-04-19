import request from '@/utils/request'

export type PlatformRoleOption = {
  roleId: number
  code: string
  name: string
  description?: string
  enabled?: boolean
  builtin?: boolean
  riskLevel?: string
  approvalMode?: string
}

export function listPlatformRoleOptions(params?: { keyword?: string; limit?: number }) {
  return request.get<PlatformRoleOption[]>('/platform/roles/options', {
    params: {
      keyword: params?.keyword,
      limit: params?.limit ?? 200,
    },
  })
}
