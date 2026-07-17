export type PlatformDraftTemplateTaskType = 'userTask' | 'serviceTask'

export interface PlatformDraftTemplateTask {
  id: string
  name: string
  type?: PlatformDraftTemplateTaskType
  roleCode?: string
  candidateGroups?: string
  formKey?: string
  delegateExpression?: string
  documentation?: string
}

export interface PlatformDraftTemplatePermission {
  code: string
  name: string
  usage: 'START' | 'APPROVE' | 'MANAGE'
}

export interface PlatformDraftTemplate {
  key: string
  name: string
  description: string
  category: string
  businessModule: string
  roleCodes: string[]
  permissions: PlatformDraftTemplatePermission[]
  tasks: PlatformDraftTemplateTask[]
  bpmnXml: string
}

interface PlatformDraftTemplateDefinition {
  key: string
  name: string
  description: string
  category: string
  businessModule: string
  tasks: PlatformDraftTemplateTask[]
}

const ROLE_PLATFORM_ADMIN = 'ROLE_PLATFORM_ADMIN'
const ROLE_PLATFORM_SECURITY = 'ROLE_PLATFORM_SECURITY'
const ROLE_PLATFORM_OPS = 'ROLE_PLATFORM_OPS'
const ROLE_PLATFORM_PRODUCT = 'ROLE_PLATFORM_PRODUCT'

const PLATFORM_DRAFT_TEMPLATE_DEFINITIONS: PlatformDraftTemplateDefinition[] = [
  {
    key: 'platform_tenant_onboarding',
    name: '租户开通审批',
    description: '新租户入驻、套餐确认、资源初始化与平台运营复核。',
    category: '租户生命周期',
    businessModule: 'platform.tenant.lifecycle',
    tasks: [
      task('TenantRequestReview', '入驻资料审核', ROLE_PLATFORM_PRODUCT, 'forms/platform/tenant-onboarding-review'),
      task('TenantPlanApprove', '套餐与额度审批', ROLE_PLATFORM_ADMIN, 'forms/platform/tenant-plan-approval'),
      serviceTask('TenantProvisioning', '初始化租户资源', 'platformTenantProvisioningConnector'),
      task('TenantActivationReview', '开通结果复核', ROLE_PLATFORM_OPS, 'forms/platform/tenant-activation-review'),
    ],
  },
  {
    key: 'platform_tenant_plan_change',
    name: '租户套餐变更审批',
    description: '租户升级、降级套餐或调整资源额度前的商务、成本与风险审批。',
    category: '租户生命周期',
    businessModule: 'platform.tenant.plan',
    tasks: [
      task('PlanChangeIntake', '变更申请核对', ROLE_PLATFORM_PRODUCT, 'forms/platform/tenant-plan-change'),
      task('QuotaRiskReview', '配额与成本复核', ROLE_PLATFORM_OPS, 'forms/platform/quota-risk-review'),
      serviceTask('ApplyPlanChange', '应用套餐变更', 'platformTenantPlanChangeConnector'),
      task('PlanChangeConfirm', '变更结果确认', ROLE_PLATFORM_ADMIN, 'forms/platform/tenant-plan-change-confirm'),
    ],
  },
  {
    key: 'platform_tenant_suspend_restore',
    name: '租户停用 / 恢复审批',
    description: '欠费、违规或风控触发租户停用，以及问题解除后的恢复审批。',
    category: '租户生命周期',
    businessModule: 'platform.tenant.lifecycle',
    tasks: [
      task('SuspendRiskReview', '停用风险复核', ROLE_PLATFORM_SECURITY, 'forms/platform/tenant-suspend-risk'),
      task('BusinessApproval', '业务负责人审批', ROLE_PLATFORM_ADMIN, 'forms/platform/high-risk-approval'),
      serviceTask('ApplySuspendRestore', '执行停用或恢复', 'platformTenantLifecycleConnector'),
      task('NotifyAndArchive', '通知与归档确认', ROLE_PLATFORM_OPS, 'forms/platform/operation-archive'),
    ],
  },
  {
    key: 'platform_permission_publish',
    name: '平台权限码发布审批',
    description: '新增、废弃或调整平台权限标识前的影响评估与发布审批。',
    category: '权限治理',
    businessModule: 'platform.authorization.permission',
    tasks: [
      task('PermissionSpecReview', '权限标识规范审核', ROLE_PLATFORM_SECURITY, 'forms/platform/permission-spec-review'),
      task('PermissionImpactReview', '影响范围评估', ROLE_PLATFORM_PRODUCT, 'forms/platform/permission-impact-review'),
      serviceTask('PublishPermissionCode', '发布权限码', 'platformPermissionPublishConnector'),
      task('PermissionReleaseConfirm', '发布结果确认', ROLE_PLATFORM_ADMIN, 'forms/platform/permission-release-confirm'),
    ],
  },
  {
    key: 'platform_role_baseline_change',
    name: '平台角色基线变更审批',
    description: '平台管理员、审计员、运营等角色基线权限调整的双人复核流程。',
    category: '权限治理',
    businessModule: 'platform.authorization.role',
    tasks: [
      task('RoleDiffReview', '角色差异审查', ROLE_PLATFORM_SECURITY, 'forms/platform/role-diff-review'),
      task('RbacApproval', 'RBAC3 负责人审批', ROLE_PLATFORM_ADMIN, 'forms/platform/rbac3-approval'),
      serviceTask('ApplyRoleBaseline', '应用角色基线', 'platformRoleBaselineConnector'),
      task('RoleBaselineConfirm', '基线结果确认', ROLE_PLATFORM_SECURITY, 'forms/platform/role-baseline-confirm'),
    ],
  },
  {
    key: 'platform_connector_publish',
    name: '连接器发布审批',
    description: '服务任务或 connector 上架给平台流程使用前的白名单、凭据和权限审批。',
    category: '连接器治理',
    businessModule: 'platform.workflow.connector',
    tasks: [
      task('ConnectorSpecReview', '连接器规范审核', ROLE_PLATFORM_PRODUCT, 'forms/platform/connector-spec-review'),
      task('SecretAndPermissionReview', '凭据与权限复核', ROLE_PLATFORM_SECURITY, 'forms/platform/connector-security-review'),
      serviceTask('PublishConnector', '发布连接器', 'platformConnectorPublishConnector'),
      task('ConnectorReleaseConfirm', '发布结果确认', ROLE_PLATFORM_OPS, 'forms/platform/connector-release-confirm'),
    ],
  },
  {
    key: 'platform_template_publish',
    name: '流程模板发布审批',
    description: '平台流程模板上架给租户复制前的模型校验、版本发布和归档保护。',
    category: '模板治理',
    businessModule: 'platform.workflow.template',
    tasks: [
      task('TemplateDesignReview', '模板设计评审', ROLE_PLATFORM_PRODUCT, 'forms/platform/template-design-review'),
      task('TemplateValidationReview', '模板校验复核', ROLE_PLATFORM_OPS, 'forms/platform/template-validation-review'),
      serviceTask('PublishTemplateVersion', '发布模板版本', 'platformTemplatePublishConnector'),
      task('TemplateReleaseConfirm', '上架结果确认', ROLE_PLATFORM_ADMIN, 'forms/platform/template-release-confirm'),
    ],
  },
  {
    key: 'platform_config_change',
    name: '生产配置变更审批',
    description: '平台级配置、功能开关、菜单策略等生产变更的审批与回滚确认。',
    category: '变更治理',
    businessModule: 'platform.change.config',
    tasks: [
      task('ConfigChangeReview', '变更内容审查', ROLE_PLATFORM_OPS, 'forms/platform/config-change-review'),
      task('RollbackPlanReview', '回滚方案复核', ROLE_PLATFORM_SECURITY, 'forms/platform/rollback-plan-review'),
      serviceTask('ApplyConfigChange', '执行生产配置变更', 'platformConfigChangeConnector'),
      task('ConfigChangeVerify', '变更结果验证', ROLE_PLATFORM_ADMIN, 'forms/platform/config-change-verify'),
    ],
  },
]

export const PLATFORM_DRAFT_TEMPLATES: PlatformDraftTemplate[] = PLATFORM_DRAFT_TEMPLATE_DEFINITIONS.map(
  (template) => ({
    ...template,
    roleCodes: roleCodesOf(template.tasks),
    permissions: businessPermissions(template),
    bpmnXml: buildPlatformDraftTemplateXml(template),
  }),
)

function task(id: string, name: string, roleCode: string, formKey: string): PlatformDraftTemplateTask {
  return {
    id,
    name,
    type: 'userTask',
    roleCode,
    candidateGroups: camundaGroupFromRoleCode(roleCode),
    formKey,
    documentation: `${name}，请按平台治理要求补充审批意见。`,
  }
}

function serviceTask(id: string, name: string, connectorKey: string): PlatformDraftTemplateTask {
  return {
    id,
    name,
    type: 'serviceTask',
    delegateExpression: '${' + connectorKey + '}',
    documentation: `${name}。正式部署前必须确认连接器已在当前 scope 白名单内启用。`,
  }
}

function buildPlatformDraftTemplateXml(template: PlatformDraftTemplateDefinition) {
  const flowCount = template.tasks.length + 1
  const permissions = businessPermissions(template)
  const roleCodes = roleCodesOf(template.tasks)
  const taskXml = template.tasks
    .map((taskItem, index) => buildTaskXml(taskItem, index + 1))
    .join('\n')
  const flows = Array.from({ length: flowCount }, (_, index) => buildSequenceFlowXml(template.tasks, index + 1))
    .join('\n')
  const shapes = buildDiagramShapes(template)
  const edges = Array.from({ length: flowCount }, (_, index) => buildSequenceFlowEdgeXml(template.tasks.length, index + 1))
    .join('\n')

  return `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
                  xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
                  xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
                  id="Definitions_${escapeXmlAttribute(template.key)}"
                  targetNamespace="http://tiny-platform/workflow/templates/platform"
                  exporter="Tiny Platform"
                  exporterVersion="1.6">
  <bpmn:process id="${escapeXmlAttribute(template.key)}" name="${escapeXmlAttribute(template.name)}" isExecutable="true" camunda:historyTimeToLive="180">
    <bpmn:documentation>${escapeXmlText(template.description)}</bpmn:documentation>
    <bpmn:extensionElements>
      <camunda:properties>
        <camunda:property name="tp:businessModule" value="${escapeXmlAttribute(template.businessModule)}"/>
        <camunda:property name="tp:startPermission" value="${escapeXmlAttribute(permissionByUsage(permissions, 'START'))}"/>
        <camunda:property name="tp:approvePermission" value="${escapeXmlAttribute(permissionByUsage(permissions, 'APPROVE'))}"/>
        <camunda:property name="tp:managePermission" value="${escapeXmlAttribute(permissionByUsage(permissions, 'MANAGE'))}"/>
        <camunda:property name="tp:roleCodes" value="${escapeXmlAttribute(roleCodes.join(','))}"/>
      </camunda:properties>
    </bpmn:extensionElements>
    <bpmn:startEvent id="StartEvent_1" name="提交申请">
      <bpmn:outgoing>Flow_1</bpmn:outgoing>
    </bpmn:startEvent>
${taskXml}
    <bpmn:endEvent id="EndEvent_1" name="流程完成">
      <bpmn:incoming>Flow_${flowCount}</bpmn:incoming>
    </bpmn:endEvent>
${flows}
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="${escapeXmlAttribute(template.key)}">
${shapes}
${edges}
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`
}

function buildTaskXml(taskItem: PlatformDraftTemplateTask, sequence: number) {
  const id = taskElementId(taskItem, sequence)
  const incoming = `Flow_${sequence}`
  const outgoing = `Flow_${sequence + 1}`
  const commonAttributes = `id="${escapeXmlAttribute(id)}" name="${escapeXmlAttribute(taskItem.name)}"`
  const documentation = taskItem.documentation
    ? `\n      <bpmn:documentation>${escapeXmlText(taskItem.documentation)}</bpmn:documentation>`
    : ''

  if (taskItem.type === 'serviceTask') {
    const delegateExpression = taskItem.delegateExpression
      ? ` camunda:delegateExpression="${escapeXmlAttribute(taskItem.delegateExpression)}"`
      : ''
    return `    <bpmn:serviceTask ${commonAttributes}${delegateExpression}>${documentation}
      <bpmn:incoming>${incoming}</bpmn:incoming>
      <bpmn:outgoing>${outgoing}</bpmn:outgoing>
    </bpmn:serviceTask>`
  }

  const candidateGroups = taskItem.candidateGroups
    ? ` camunda:candidateGroups="${escapeXmlAttribute(taskItem.candidateGroups)}"`
    : ''
  const formKey = taskItem.formKey
    ? ` camunda:formKey="${escapeXmlAttribute(taskItem.formKey)}"`
    : ''
  return `    <bpmn:userTask ${commonAttributes}${candidateGroups}${formKey}>${documentation}
      <bpmn:incoming>${incoming}</bpmn:incoming>
      <bpmn:outgoing>${outgoing}</bpmn:outgoing>
    </bpmn:userTask>`
}

function buildSequenceFlowXml(tasks: PlatformDraftTemplateTask[], sequence: number) {
  const sourceRef = sequence === 1 ? 'StartEvent_1' : taskElementId(tasks[sequence - 2], sequence - 1)
  const targetRef = sequence > tasks.length ? 'EndEvent_1' : taskElementId(tasks[sequence - 1], sequence)
  return `    <bpmn:sequenceFlow id="Flow_${sequence}" sourceRef="${sourceRef}" targetRef="${targetRef}"/>`
}

function buildDiagramShapes(template: PlatformDraftTemplateDefinition) {
  const taskShapes = template.tasks
    .map((taskItem, index) => {
      const x = taskX(index)
      return `      <bpmndi:BPMNShape id="${taskElementId(taskItem, index + 1)}_di" bpmnElement="${taskElementId(taskItem, index + 1)}">
        <dc:Bounds x="${x}" y="80" width="118" height="80"/>
        <bpmndi:BPMNLabel/>
      </bpmndi:BPMNShape>`
    })
    .join('\n')
  const endX = taskX(template.tasks.length) + 40
  return `      <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1">
        <dc:Bounds x="152" y="102" width="36" height="36"/>
        <bpmndi:BPMNLabel/>
      </bpmndi:BPMNShape>
${taskShapes}
      <bpmndi:BPMNShape id="EndEvent_1_di" bpmnElement="EndEvent_1">
        <dc:Bounds x="${endX}" y="102" width="36" height="36"/>
        <bpmndi:BPMNLabel/>
      </bpmndi:BPMNShape>`
}

function buildSequenceFlowEdgeXml(taskCount: number, sequence: number) {
  const sourceX = sequence === 1 ? 188 : taskX(sequence - 2) + 118
  const targetX = sequence > taskCount ? taskX(taskCount) + 40 : taskX(sequence - 1)
  return `      <bpmndi:BPMNEdge id="Flow_${sequence}_di" bpmnElement="Flow_${sequence}">
        <di:waypoint x="${sourceX}" y="120"/>
        <di:waypoint x="${targetX}" y="120"/>
      </bpmndi:BPMNEdge>`
}

function taskElementId(taskItem: PlatformDraftTemplateTask, sequence: number) {
  const prefix = taskItem.type === 'serviceTask' ? 'ServiceTask' : 'UserTask'
  return `${prefix}_${sequence}_${taskItem.id}`
}

function businessPermissions(template: PlatformDraftTemplateDefinition): PlatformDraftTemplatePermission[] {
  const segment = template.key.replace(/^platform_/, '').replace(/_/g, '-')
  return [
    {
      code: `workflow:platform:${segment}:start`,
      name: `${template.name}启动`,
      usage: 'START',
    },
    {
      code: `workflow:platform:${segment}:approve`,
      name: `${template.name}处理`,
      usage: 'APPROVE',
    },
    {
      code: `workflow:platform:${segment}:manage`,
      name: `${template.name}管理`,
      usage: 'MANAGE',
    },
  ]
}

function permissionByUsage(permissions: PlatformDraftTemplatePermission[], usage: PlatformDraftTemplatePermission['usage']) {
  return permissions.find((permission) => permission.usage === usage)?.code ?? ''
}

function roleCodesOf(tasks: PlatformDraftTemplateTask[]) {
  return Array.from(new Set(tasks.map((taskItem) => taskItem.roleCode).filter(Boolean))) as string[]
}

function camundaGroupFromRoleCode(roleCode: string) {
  return roleCode.replace(/^ROLE_/, '')
}

function taskX(index: number) {
  return 240 + index * 170
}

function escapeXmlText(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

function escapeXmlAttribute(value: string) {
  return escapeXmlText(value)
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;')
}
