/**
 * 属性面板翻译
 * 对应 bpmn-js-i18n-zh/lib/properties-panel/index.js
 */

export interface TranslationMap {
  [key: string]: string
}

// 属性面板翻译
const propertiesPanelTranslations: TranslationMap = {
  // 基础属性
  General: '通用',
  // 2025-08-06 新增 版本 "bpmn-js-properties-panel": "^5.39.0" 翻译
  'This maps to the process definition key.': '这映射到流程定义键。',
  Completion: '完成',
  ID: 'ID',
  Documentation: '文档',
  'Element documentation': '元素文档',
  'Process documentation': '流程文档',
  Name: '名称',
  'Version tag': '版本标签',
  Executable: '可执行',
  Description: '描述',
  Type: '类型',
  Value: '值',
  '<empty>': '空',
  '<none>': '无',
  '<no type>': '无类型',
  'Create new ...': '创建...',
  Create: '新增',
  'Create new list item': '新增列表项',
  'List contains {numOfItems} item': '列表包含 {numOfItems} 项',
  'List contains {numOfItems} items': '列表包含 {numOfItems} 项',
  'Toggle section': '展开/折叠分组',
  'Toggle list item': '展开/折叠列表项',
  'Delete item': '删除项',

  // 用户分配
  Assignee: '受理人',
  'Candidate users': '候选用户',
  'Candidate groups': '候选组',
  'Due date': '到期日期',
  'Follow up date': '跟进日期',
  Priority: '权重',

  // 表单相关
  'Form key': '表单键',
  'Form fields': '表单字段',
  Forms: '表单',
  'Camunda Forms': 'Camunda 表单',
  'Embedded or External Task Forms': '嵌入式或外部任务表单',
  'Generated Task Forms': '生成的任务表单',

  // 参数相关
  'Input parameters': '输入参数',
  'Output parameters': '输出参数',
  Inputs: '输入',
  Outputs: '输出',

  // 监听器相关
  'Task listener': '任务监听器',
  'Execution listener': '执行监听器',
  'Task listeners': '任务监听器',
  'Execution listeners': '执行监听器',
  'Listener type': '监听器类型',
  'Listener ID': '监听器ID',
  'Java class': 'Java 类',
  Expression: '表达式',
  'Delegate expression': '委托表达式',
  'Field injection': '字段注入',

  // 事件相关
  'Event type': '事件类型',
  Timer: '时间',
  Message: '消息',
  Signal: '信号',
  Error: '错误',
  Escalation: '升级',
  Compensation: '补偿',
  Link: '链接',
  Conditional: '条件',

  // 流程相关
  Process: '流程',
  PROCESS: '流程',
  process: '流程',
  'Process ID': '流程 ID',
  'Process name': '流程名称',
  'Process variables': '流程变量',
  'Start initiator': '启动发起人',
  Startable: '可启动',
  'Candidate starter': '候选启动者',
  'Candidate starter groups': '候选启动者组',
  'Candidate starter users': '候选启动者用户',

  // 异步相关
  'Asynchronous continuations': '异步延续',

  // 外部任务
  'External task': '外部任务',
  External: '外部',
  Topic: '主题',
  Connector: '连接器',
  'Connector ID': '连接器ID',
  'Job execution': '作业执行',

  // 网关相关
  Exclusive: '排他',
  'Retry time cycle': '重试时间周期',

  // 映射相关
  'In mapping propagation': '输入映射传播',
  'In mappings': '输入映射',
  'Out mapping propagation': '输出映射传播',
  'Out mappings': '输出映射',
  'Connector inputs': '连接器输入',
  'Connector outputs': '连接器输出',

  // 多实例相关
  'Multi-instance': '多实例',
  'Sequential multi-instance': '顺序多实例',
  'Parallel multi-instance': '并行多实例',

  // 脚本相关
  Script: '脚本',
  'Script format': '脚本格式',
  'Script type': '脚本类型',

  // 条件相关
  Condition: '条件',
  'Condition expression': '条件表达式',

  // 调用相关
  'Called element': '被调用元素',

  // 扩展属性
  'Extension properties': '扩展属性',

  // 字段注入
  'Field injections': '字段注入',

  // 业务键
  'Business key': '业务键',

  // 时间相关
  'Time to live': '生存时间',

  // 历史清理
  'History cleanup': '历史清理',

  // 任务列表
  Tasklist: '任务列表',

  // 实现相关
  Implementation: '实现',

  // 错误相关
  Errors: '错误',

  // 用户分配
  'User assignment': '用户分配',

  // 候选启动者说明
  'Specify more than one group as a comma separated list.': '指定多个组，用逗号分隔。',
  'Specify more than one user as a comma separated list.': '指定多个用户，用逗号分隔。',

  // 业务相关
  'Change element': '更改元素',
  'Participant Name': '协助名称',
  'Participant ID': '协助 ID',

  // 变量相关
  'Variable name': '变量名称',
  'Local variable name': '局部变量名称',
  'Process variable name': '流程变量名称',

  // 分配类型
  'Assignment type': '分配类型',
  List: '列表',
  Map: '映射',

  // 表达式相关
  'String or expression': '字符串或表达式',
  'Condition Expression': '条件表达式',
  'Throw expression': '异常抛出表达式',
  'Source expression': '源表达式',
  'Start typing "${}" to create an expression.': '开始输入 "${}" 来创建表达式。',

  // 日期相关
  'The due date as an EL expression (e.g. ${someDate}) or an ISO date (e.g. 2015-06-26T09:54:00).':
    '到期日期，可以是 EL 表达式（如 ${someDate}）或 ISO 日期格式（如 2015-06-26T09:54:00）。',
  'The follow up date as an EL expression (e.g. ${someDate}) or an ISO date (e.g. 2015-06-26T09:54:00).':
    '跟进日期，可以是 EL 表达式（如 ${someDate}）或 ISO 日期格式（如 2015-06-26T09:54:00）。',

  // 其他
  'Created in': '创建于',
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

  // 事件类型
  Initiator: '发起人',
  Before: '之前',
  After: '之后',
  Assignment: '分配',
  Complete: '完成',
  Delete: '删除',
  Update: '更新',
  Timeout: '超时',
  Take: '获取',
  Start: '开始',
  End: '结束',
  create: '创建',
  assignment: '分配',
  complete: '完成',
  delete: '删除',
  update: '更新',
  timeout: '超时',
  take: '获取',
  start: '开始',
  end: '结束',

  // 新增的翻译项（来自 bpmn-js-i18n-zh）
  'Completion condition': '完成条件',
  'Loop cardinality': '循环基数',
  'Wait for completion': '结束等待',
  'Activity reference': '活动引用',
  'Global error reference': '全局错误引用',
  'Global escalation reference': '全局升级引用',
  'Global message reference': '全局消息引用',
  'Global signal reference': '全局信号引用',
  Code: '编码',
  Date: '日期时间',
  Duration: '持续时间',
  Cycle: '循环周期',
  'Documentation: Timer events': '文档：定时事件',
  'UTC time': 'UTC 时间格式',
  'UTC plus 2 hours zone offset': 'UTC 加 2 小时时区偏移',
  'A specific point in time defined as ISO 8601 combined date and time representation.':
    '定义为 ISO 8601 组合日期和时间表示的特定时间点。',
  'A cycle defined as ISO 8601 repeating intervals format.':
    '定义为 ISO 8601 重复间隔格式的循环周期。',
  'A time duration defined as ISO 8601 durations format.': '定义为ISO 8601重复间隔格式的持续时间。',
  'every 10 seconds, up to 5 times': '每 10 秒，最多执行 5 次',
  'every day, infinitely': '每天，不停止',
  '15 seconds': '15 秒',
  '1 hour and 30 minutes': '1 小时 30 分钟',
  '14 days': '14 天',
}

export default propertiesPanelTranslations
