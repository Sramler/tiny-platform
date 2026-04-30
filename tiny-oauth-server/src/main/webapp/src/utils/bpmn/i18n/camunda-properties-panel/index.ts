/**
 * Camunda 属性面板翻译
 * 对应 bpmn-js-i18n-zh/lib/camunda-properties-panel/index.js
 */

export interface TranslationMap {
  [key: string]: string
}

// Camunda 属性面板翻译
const camundaPropertiesPanelTranslations: TranslationMap = {
  // Camunda 特有功能
  'Camunda Platform': 'Camunda 平台',
  'Camunda Forms': 'Camunda 表单',
  'Embedded or External Task Forms': '嵌入或者外部任务表单',
  'Generated Task Forms': '生成任务表单',
  'Custom type': '自定义类型',
  '<custom type>': '自定义类型',
  boolean: '布尔',
  date: '日期',
  enum: '枚举',
  long: '长整数',
  string: '字符串',

  // 外部任务
  'External task': '外部任务',
  External: '外部',
  Topic: '外部',
  Connector: '连接器',
  'Connector ID': '连接器 ID',
  'Job execution': '执行作业',

  // 监听器
  'Task listeners': '任务监听器',
  'Execution listeners': '执行监听器',
  'Listener type': '监听器类型',
  'Listener ID': '监听器 ID',
  'Java class': 'Java 类',
  Expression: '表达式',
  'Delegate expression': '代理表达式',
  'Field injection': '字段注入',

  // 事件类型
  Initiator: '启动器',
  Before: '异步前',
  After: '异步后',
  create: '创建',
  assignment: '指派',
  complete: '完成',
  delete: '删除',
  update: '更新',
  timeout: '超时',
  take: '采用',
  start: '开始',
  end: '结束',

  // 映射相关
  'In mapping propagation': '输入映射传播',
  'In mappings': '输入映射',
  'Connector inputs': '连接器输入',
  'Out mapping propagation': '输出映射传播',
  'Out mappings': '输出映射',
  'Connector outputs': '连接器输出',
  'Field injections': '字段注入',

  // 业务相关
  'Business key': '业务关键字',
  'Change element': '更改元素',

  // 变量相关
  'Variable name': '变量名称',
  'Local variable name': '本地变量名称',
  'Process variable name': '流程变量名称',

  // 分配类型
  'Assignment type': '分配类型',
  List: '列表',
  Map: '映射',

  // 表达式相关
  'String or expression': '字符串或表达式',
  'Condition expression': '条件表达式',
  'Condition Expression': '条件表达式',
  'Throw expression': '异常抛出表达式',
  'Source expression': '源表达式',
  'Start typing "${}" to create an expression.': '开始键入 "${}" 以创建表达式。',

  // 日期相关
  'The due date as an EL expression (e.g. ${someDate}) or an ISO date (e.g. 2015-06-26T09:54:00).':
    '截止日期为 EL 表达式 (例如 ${someDate}) 或 ISO 格式日期 (例如 2015-06-26T09:54:00)。',
  'The follow up date as an EL expression (e.g. ${someDate}) or an ISO date (e.g. 2015-06-26T09:54:00).':
    '跟进日期为 EL 表达式 (例如 ${someDate}) 或 ISO 格式日期 (例如 2015-06-26T09:54:00)。',

  // 其他
  'Created in': '创建自',
  String: '字符串',

  // 验证消息
  'ID must not be empty.': 'ID 不能为空。',
  'ID must not contain spaces.': 'ID 不能包含空格。',
  'ID must not contain prefix.': 'ID 不能包含前缀。',
  'ID must be unique.': 'ID 必须唯一。',
  'ID must be a valid QName.': 'ID 必须是有效的 QName。',
  'Select an element to edit its properties.': '选择要编辑其属性的元素。',
  'Multiple elements are selected. Select a single element to edit its properties.':
    '多个元素被选择。选择单个元素以编辑其属性。',
  'Specify more than one group as a comma separated list.': '以逗号分隔列表的形式指定多个组。',
  'Specify more than one user as a comma separated list.': '以逗号分隔列表的形式指定多个用户。',

  // 新增的翻译项（来自 bpmn-js-i18n-zh）
  ID: 'ID',
  Key: 'key',
  Implementation: '实现方式',
  Errors: '错误',
  'User assignment': '用户分配',
  Script: '脚本',
  'Called element': '调用元素',
  Condition: '条件',
  'Start initiator': '启动器',
  'Asynchronous continuations': '异步延续',
  Exclusive: '独占',
  'Candidate starter': '候选启动器',
  'History cleanup': '历史清除',
  Tasklist: '任务列表',
  'Process variables': '流程变量',
  'Form fields': '表单字段',
  Forms: '表单',
  Inputs: '输入',
  Outputs: '输出',
  'Extension properties': '扩展属性',
  'Decision reference': '决策引用',
  Binding: '绑定',
  Version: '版本号',
  'Version tag': '版本标签',
  'Tenant ID': '团队 ID',
  'Result variable': '结果变量',
  'collectEntries (List<Object>)': '入口集合（List<Object>）',
  'resultList (List<Map<String, Object>>)': '结果列表（List<Map<String, Object>>）',
  'singleEntry (TypedValue)': '信号入口（TypedValue）',
  'singleResult (Map<String, Object>)': '信号结果（Map<String, Object>）',
  'Map decision result': '映射决策结果',
  Message: '消息',
  'Code variable': '编码变量',
  'Message variable': '消息变量',
  Priority: '权重',
  Config: '配置',
  'Form key': '表单 key',
  'Form reference': '表单引用',
  'Time to live': '存活时间',
  'List values': '枚举列表',
  '<empty>': '空',
  'Map entries': '映射入口',
  Value: '值',
  Startable: '可启动',
  Constraints: '约束条件',
  Properties: '属性列表',
  'Retry time cycle': '重试时间周期',
  'Candidate starter groups': '候选启动组',
  'Candidate starter users': '候选启动用户',
  Label: '标签名',
  'Default value': '默认值',
  'Script type': '脚本类型',
  deployment: '开发版',
  latest: '最新正式版',
  version: '版本号',
  versionTag: '版本标签',
  'External resource': '外部资源',
  'Inline script': '内联脚本',
  Resource: '资源地址',
  Format: '格式',
  Local: '本地',
  Source: '源',
  Target: '目标',
  'Case ref': '参考案例',
  'Refers to the process variable name': '引用流程变量名称',
  'Define the name of the variable that will contain the error code.':
    '定义将包含错误代码的变量的名称。',
}

export default camundaPropertiesPanelTranslations
