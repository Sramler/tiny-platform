import { describe, expect, it } from 'vitest'

import { PLATFORM_DRAFT_TEMPLATES } from './platformDraftTemplates'

describe('platformDraftTemplates', () => {
  it('provides the first batch of platform workflow draft templates', () => {
    expect(PLATFORM_DRAFT_TEMPLATES).toHaveLength(8)
    expect(PLATFORM_DRAFT_TEMPLATES.map((template) => template.key)).toEqual([
      'platform_tenant_onboarding',
      'platform_tenant_plan_change',
      'platform_tenant_suspend_restore',
      'platform_permission_publish',
      'platform_role_baseline_change',
      'platform_connector_publish',
      'platform_template_publish',
      'platform_config_change',
    ])
  })

  it('builds executable BPMN XML with stable platform keys', () => {
    for (const template of PLATFORM_DRAFT_TEMPLATES) {
      expect(template.bpmnXml).toContain('<bpmn:definitions')
      expect(template.bpmnXml).toContain(`id="${template.key}"`)
      expect(template.bpmnXml).toContain(`name="${template.name}"`)
      expect(template.bpmnXml).toContain('isExecutable="true"')
      expect(template.bpmnXml).toContain('camunda:candidateGroups=')
      expect(template.bpmnXml).toContain('camunda:delegateExpression=')
      expect(template.bpmnXml).toContain(`value="${template.businessModule}"`)
      expect(template.bpmnXml).toContain('name="tp:startPermission"')
      expect(template.bpmnXml).toContain('name="tp:approvePermission"')
      expect(template.bpmnXml).toContain('name="tp:managePermission"')
      expect(template.roleCodes.every((roleCode) => roleCode.startsWith('ROLE_PLATFORM_'))).toBe(true)
      expect(template.permissions.map((permission) => permission.usage)).toEqual(['START', 'APPROVE', 'MANAGE'])
    }
  })

  it('uses Camunda group ids aligned with stripped platform role codes', () => {
    const tenantOnboarding = PLATFORM_DRAFT_TEMPLATES.find(
      (template) => template.key === 'platform_tenant_onboarding',
    )

    expect(tenantOnboarding?.roleCodes).toEqual([
      'ROLE_PLATFORM_PRODUCT',
      'ROLE_PLATFORM_ADMIN',
      'ROLE_PLATFORM_OPS',
    ])
    expect(tenantOnboarding?.bpmnXml).toContain('camunda:candidateGroups="PLATFORM_PRODUCT"')
    expect(tenantOnboarding?.bpmnXml).toContain('camunda:candidateGroups="PLATFORM_ADMIN"')
    expect(tenantOnboarding?.bpmnXml).toContain('camunda:candidateGroups="PLATFORM_OPS"')
  })
})
