import request from '@/utils/request'

export type PlatformRoleOption = {
  roleId: number
  code: string
  name: string
  description?: string
  enabled?: boolean
  builtin?: boolean
  /** 与后端 role.risk_level 对齐 */
  riskLevel?: string
  /** NONE | ONE_STEP — ONE_STEP 须走审批申请，不可直写绑定 */
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
