import type {
  WorkflowFieldOption,
  WorkflowInputConfig,
  WorkflowNodeSchema,
  WorkflowOutputConfig,
  WorkflowResource,
  WorkflowResourceMaps,
} from '@/types/workflow'

export const WORKFLOW_GROUPS = [
  { key: 'basic', title: '基础' },
  { key: 'logic', title: '逻辑' },
  { key: 'data', title: '数据' },
  { key: 'cache', title: '缓存' },
  { key: 'message', title: '消息' },
  { key: 'integration', title: '集成' },
  { key: 'transform', title: '转换' },
  { key: 'list', title: '列表' },
  { key: 'variable', title: '变量' },
] as const

const formatterOptions: WorkflowFieldOption[] = [
  { label: '普通字符串', value: 'STRING' },
  { label: 'Jackson JSON', value: 'JACKSON' },
  { label: 'Velocity 模板', value: 'VELOCITY' },
]

const httpMethodOptions = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'HEAD', 'OPTIONS'].map((value) => ({
  label: value,
  value,
}))

const variableTypeOptions = ['String', 'Long', 'Integer', 'Float', 'Double', 'Boolean', 'Array', 'Object'].map((value) => ({
  label: value,
  value,
}))

const dbParamTypeOptions = ['STRING', 'INTEGER', 'INT', 'LONG', 'DOUBLE', 'FLOAT', 'BOOLEAN', 'BOOL'].map((value) => ({
  label: value,
  value,
}))

const symbolOptions: WorkflowFieldOption[] = [
  { label: '等于', value: 'EQ' },
  { label: '不等于', value: 'NE' },
  { label: '大于', value: 'GT' },
  { label: '小于', value: 'LT' },
  { label: '大于等于', value: 'GE' },
  { label: '小于等于', value: 'LE' },
  { label: '包含', value: 'CONTAINS' },
  { label: '不包含', value: 'NOT_CONTAINS' },
  { label: '全部是', value: 'IS_ALL' },
  { label: '开头匹配', value: 'STARTS_WITH' },
  { label: '结尾匹配', value: 'ENDS_WITH' },
  { label: '严格等于', value: 'EQUALS' },
  { label: '严格不等于', value: 'NOT_EQUALS' },
  { label: '为 true', value: 'IS_TRUE' },
  { label: '为 false', value: 'IS_FALSE' },
  { label: '表达式', value: 'EXPRESSION' },
]

const simpleSymbolOptions: WorkflowFieldOption[] = symbolOptions.filter((item) => item.value !== 'IS_ALL' && item.value !== 'EXPRESSION')

const input = (): WorkflowInputConfig[] => [{ name: 'input', sourceType: 'NODE_OUTPUT' }]
const output = (type = 'Object'): WorkflowOutputConfig[] => [{ name: 'output', type, description: '节点默认输出' }]

function groupTitle(group: string) {
  return WORKFLOW_GROUPS.find((item) => item.key === group)?.title || group
}

function resourceName(list: WorkflowResource[], id: unknown) {
  return list.find((item) => item.id === id)?.name || String(id || '未选择')
}

function line(label: string, value: unknown) {
  if (value === undefined || value === null || value === '') return {}
  return {
    label,
    value,
  }
}

function schema(item: Omit<WorkflowNodeSchema, 'groupTitle'>): WorkflowNodeSchema {
  return { ...item, groupTitle: groupTitle(item.group) }
}

const cacheFields = [
  {
    name: 'cacheId',
    label: '缓存实例',
    control: 'resource',
    resourceType: 'cache',
    required: true,
    description: '仅可选择已启用的缓存配置。',
  },
  {
    name: 'key',
    label: '缓存键',
    control: 'input',
    required: true,
    placeholder: '例如 user:${userId}',
    description: '支持 Velocity 变量语法。',
  },
  {
    name: 'formatterType',
    label: '模板格式',
    control: 'select',
    defaultValue: 'VELOCITY',
    options: formatterOptions,
  },
] satisfies WorkflowNodeSchema['fields']

const dbFields = [
  {
    name: 'datasourceId',
    label: '数据源',
    control: 'resource',
    resourceType: 'datasource',
    required: true,
  },
  {
    name: 'sql',
    label: 'SQL 语句',
    control: 'code',
    language: 'txt',
    required: true,
    placeholder: '使用 ? 作为参数占位符',
    description: 'SQL 本体不会整体模板替换，参数值支持模板替换。',
  },
  {
    name: 'params',
    label: 'SQL 参数',
    control: 'dbParams',
    defaultValue: [],
    options: dbParamTypeOptions,
  },
  {
    name: 'formatterType',
    label: '参数模板格式',
    control: 'select',
    defaultValue: 'VELOCITY',
    options: formatterOptions,
  },
] satisfies WorkflowNodeSchema['fields']

export const workflowNodeSchemas: WorkflowNodeSchema[] = [
  schema({
    type: 'START',
    title: '开始',
    group: 'basic',
    description: '工作流入口，定义对外触发协议、请求参数、访问控制和鉴权信息。',
    icon: 'play',
    color: '#1677ff',
    defaultConfig: {
      params: []
    },
    fields: [
      { name: 'params', label: '输入参数', control: 'startParams', defaultValue: [], options: variableTypeOptions },
    ],
    inputConfigs: [],
    outputConfigs: output(),
  }),
  schema({
    type: 'END',
    title: '结束',
    group: 'basic',
    description: '工作流结束节点，按模板生成最终响应。',
    icon: 'stop',
    color: '#8c8c8c',
    defaultConfig: { responseTemplate: '${input}', formatterType: 'JACKSON' },
    fields: [
      { name: 'responseTemplate', label: '响应模板', control: 'code', language: 'json', required: true },
      { name: 'formatterType', label: '响应模板格式', control: 'select', options: formatterOptions, defaultValue: 'JACKSON' },
    ],
    inputConfigs: input(),
    outputConfigs: output(),
  }),
  schema({
    type: 'IF_ELSE',
    title: '条件分支',
    group: 'logic',
    description: '根据输入值或表达式结果选择 true/false 分支。',
    icon: 'branches',
    color: '#fa8c16',
    defaultConfig: { evaluatorType: 'GROOVY', scope: 'SELF', inputIsNullUse: false, symbol: 'EQ', compareTo: { type: 'CONSTANT', value: '' } },
    fields: [
      { name: 'evaluatorType', label: '表达式引擎', control: 'select', options: [{ label: 'Groovy', value: 'GROOVY' }] },
      { name: 'conditionExpression', label: '条件表达式', control: 'code', language: 'txt', description: '当运算符为 EXPRESSION 时使用。' },
      { name: 'allowInputType', label: '允许输入类型', control: 'stringList', options: variableTypeOptions },
      { name: 'scope', label: '计算范围', control: 'segmented', options: [{ label: '值本身', value: 'SELF' }, { label: '长度', value: 'LENGTH' }] },
      { name: 'inputIsNullUse', label: '输入为空时通过', control: 'switch' },
      { name: 'symbol', label: '运算符', control: 'select', options: symbolOptions, required: true },
      { name: 'compareTo', label: '比较值', control: 'compareTo' },
      { name: 'trueNextNodeId', label: 'True 分支节点ID', control: 'input' },
      { name: 'falseNextNodeId', label: 'False 分支节点ID', control: 'input' },
    ],
    inputConfigs: input(),
    outputConfigs: output('Boolean'),
    branchHandles: [{ id: 'true', label: 'true' }, { id: 'false', label: 'false' }],
    summary: (config) => [line('运算符', config.symbol), line('范围', config.scope)],
  }),
  schema({
    type: 'CACHE_FETCH',
    title: '读取缓存',
    group: 'cache',
    description: '从指定缓存实例读取键值。',
    icon: 'database',
    color: '#13a8a8',
    defaultConfig: { formatterType: 'VELOCITY' },
    fields: cacheFields,
    inputConfigs: input(),
    outputConfigs: output(),
    summary: (config, resources) => [line('缓存', resourceName(resources?.caches || [], config.cacheId)), line('键', config.key)],
  }),
  schema({
    type: 'CACHE_SET',
    title: '写入缓存',
    group: 'cache',
    description: '将值写入指定缓存，expire 为空或 0 表示不过期。',
    icon: 'save',
    color: '#13a8a8',
    defaultConfig: { formatterType: 'VELOCITY', expire: 0 },
    fields: [
      ...cacheFields,
      { name: 'value', label: '缓存值', control: 'json', language: 'json', required: true, description: '支持字符串、数字、对象或数组。' },
      { name: 'expire', label: '过期时间(秒)', control: 'number', min: 0, defaultValue: 0 },
    ],
    inputConfigs: input(),
    outputConfigs: output('Boolean'),
    summary: (config, resources) => [line('缓存', resourceName(resources?.caches || [], config.cacheId)), line('过期', config.expire || '不过期')],
  }),
  schema({
    type: 'CACHE_REMOVE',
    title: '删除缓存',
    group: 'cache',
    description: '删除指定缓存键。',
    icon: 'delete',
    color: '#13a8a8',
    defaultConfig: { formatterType: 'VELOCITY' },
    fields: cacheFields,
    inputConfigs: input(),
    outputConfigs: output('Boolean'),
    summary: (config, resources) => [line('缓存', resourceName(resources?.caches || [], config.cacheId)), line('键', config.key)],
  }),
  schema({
    type: 'CACHE_REFRESH',
    title: '刷新缓存',
    group: 'cache',
    description: '刷新缓存键的过期时间。',
    icon: 'reload',
    color: '#13a8a8',
    defaultConfig: { formatterType: 'VELOCITY', expire: 0 },
    fields: [...cacheFields, { name: 'expire', label: '新的过期时间(秒)', control: 'number', min: 0, defaultValue: 0 }],
    inputConfigs: input(),
    outputConfigs: output('Boolean'),
    summary: (config, resources) => [line('缓存', resourceName(resources?.caches || [], config.cacheId)), line('过期', config.expire || '不过期')],
  }),
  ...(['DB_SELECT', 'DB_INSERT', 'DB_UPDATE', 'DB_DELETE'] as const).map((type) => {
    const titleMap = { DB_SELECT: '数据库查询', DB_INSERT: '数据库插入', DB_UPDATE: '数据库更新', DB_DELETE: '数据库删除' }
    return schema({
      type,
      title: titleMap[type],
      group: 'data',
      description: '执行使用 ? 占位符的 SQL，并按顺序绑定参数。',
      icon: 'table',
      color: '#2f54eb',
      defaultConfig: { params: [], formatterType: 'VELOCITY' },
      fields: dbFields,
      inputConfigs: input(),
      outputConfigs: output(type === 'DB_SELECT' ? 'Array' : 'Integer'),
      summary: (config, resources) => [line('数据源', resourceName(resources?.datasources || [], config.datasourceId)), line('参数', Array.isArray(config.params) ? `${config.params.length} 个` : 0)],
    })
  }),
  schema({
    type: 'MQ_PUSH',
    title: '发送消息',
    group: 'message',
    description: '向 Kafka、RabbitMQ 或 RocketMQ 推送消息。',
    icon: 'message',
    color: '#722ed1',
    defaultConfig: { templateType: 'STRING' },
    fields: [
      { name: 'mqId', label: 'MQ 实例', control: 'resource', resourceType: 'mq', required: true },
      { name: 'topicOrQueue', label: 'Topic / Queue', control: 'input', required: true, description: 'Kafka/RocketMQ 为 topic，RabbitMQ 为 queue/exchange 语义。' },
      { name: 'key', label: '消息 Key', control: 'input', description: 'Kafka 分区键、RabbitMQ routing key、RocketMQ tag。' },
      { name: 'message', label: '消息内容', control: 'code', language: 'json' },
      { name: 'messageTemplate', label: '消息模板', control: 'code', language: 'txt', description: '优先级高于 message。' },
      { name: 'templateType', label: '模板格式', control: 'select', options: formatterOptions, defaultValue: 'STRING' },
    ],
    inputConfigs: input(),
    outputConfigs: output('Boolean'),
    summary: (config, resources) => [line('MQ', resourceName(resources?.mqs || [], config.mqId)), line('目标', config.topicOrQueue)],
  }),
  schema({
    type: 'HTTP_EXTERNAL',
    title: 'HTTP 请求',
    group: 'integration',
    description: '调用 HTTP 请求接口，支持参数、Header、Body、超时、重试和响应解析。',
    icon: 'api',
    color: '#1677ff',
    defaultConfig: {
      formatterType: 'STRING',
      connectTimeout: 10,
      readTimeout: 30,
      writeTimeout: 30,
      maxRetries: 3,
      retryStatusCodes: [],
      followRedirects: true,
      syncExecute: true,
      bodyToObject: true,
      request: { method: 'GET', contentType: 'JSON', pathParams: [], queryParams: [], headers: [], body: '' },
    },
    fields: [
      { name: 'request', label: '请求配置', control: 'httpRequest', required: true },
      { name: 'formatterType', label: '请求模板格式', control: 'select', options: formatterOptions, defaultValue: 'STRING' },
      { name: 'connectTimeout', label: '连接超时(秒)', control: 'number', min: 1, defaultValue: 10 },
      { name: 'readTimeout', label: '读取超时(秒)', control: 'number', min: 1, defaultValue: 30 },
      { name: 'writeTimeout', label: '写入超时(秒)', control: 'number', min: 1, defaultValue: 30 },
      { name: 'maxRetries', label: '最大重试次数', control: 'number', min: 0, defaultValue: 3 },
      { name: 'retryStatusCodes', label: '重试状态码', control: 'stringList', placeholder: '例如 502' },
      { name: 'followRedirects', label: '跟随重定向', control: 'switch', defaultValue: true },
      { name: 'syncExecute', label: '同步执行', control: 'switch', defaultValue: true },
      { name: 'bodyToObject', label: '响应体转 JSON', control: 'switch', defaultValue: true },
    ],
    inputConfigs: input(),
    outputConfigs: output(),
    summary: (config) => {
      const request = (config.request || {}) as Record<string, unknown>
      return [line('方法', request.method || 'GET'), line('URL', request.url)]
    },
  }),
  schema({
    type: 'CODE',
    title: '代码执行',
    group: 'integration',
    description: '执行 Java 或 JavaScript 代码处理输入。',
    icon: 'code',
    color: '#eb2f96',
    defaultConfig: { language: 'JAVA', codeSource: '' },
    fields: [
      { name: 'language', label: '语言', control: 'segmented', options: [{ label: 'Java', value: 'JAVA' }, { label: 'JavaScript', value: 'JAVASCRIPT' }] },
      { name: 'codeSource', label: '代码', control: 'code', language: 'java', required: true },
    ],
    inputConfigs: input(),
    outputConfigs: output(),
    summary: (config) => [line('语言', config.language || 'JAVA')],
  }),
  schema({
    type: 'ITERATE',
    title: '迭代处理',
    group: 'logic',
    description: '对集合输入逐项执行迭代处理代码。',
    icon: 'iterate',
    color: '#fa8c16',
    defaultConfig: { language: 'JAVA', iterateCode: '' },
    fields: [
      { name: 'language', label: '语言', control: 'segmented', options: [{ label: 'Java', value: 'JAVA' }, { label: 'JavaScript', value: 'JAVASCRIPT' }] },
      { name: 'iterateCode', label: '迭代代码', control: 'code', language: 'java', required: true },
    ],
    inputConfigs: input(),
    outputConfigs: output('Array'),
    summary: (config) => [line('语言', config.language || 'JAVA')],
  }),
  schema({
    type: 'LOOP',
    title: '循环',
    group: 'logic',
    description: '按次数或数据源执行子工作流。',
    icon: 'loop',
    color: '#fa8c16',
    defaultConfig: { loopVariable: 'loopIndex', maxIterations: 1000, itemVariable: 'item', subNodes: [], subEdges: [] },
    fields: [
      { name: 'loopVariable', label: '循环变量名', control: 'input', defaultValue: 'loopIndex' },
      { name: 'maxIterations', label: '最大循环次数', control: 'number', min: 1, defaultValue: 1000 },
      { name: 'terminationExpression', label: '终止表达式', control: 'code', language: 'txt' },
      { name: 'iterateDataSource', label: '迭代数据源变量', control: 'input' },
      { name: 'itemVariable', label: '元素变量名', control: 'input', defaultValue: 'item' },
      { name: 'entryNodeId', label: '子流程入口节点ID', control: 'input' },
      { name: 'subNodes', label: '子流程节点', control: 'readonlyJson' },
      { name: 'subEdges', label: '子流程连线', control: 'readonlyJson' },
      { name: 'workflow', label: '运行时子工作流实例', control: 'readonlyJson' },
    ],
    inputConfigs: input(),
    outputConfigs: output(),
    summary: (config) => [line('最大次数', config.maxIterations), line('元素变量', config.itemVariable)],
  }),
  schema({
    type: 'LIST_FILTER',
    title: '列表过滤',
    group: 'list',
    description: '按简单条件或 Groovy 表达式过滤列表元素。',
    icon: 'filter',
    color: '#52c41a',
    defaultConfig: { mode: 'SIMPLE', evaluatorType: 'GROOVY', itemIsNullUse: false },
    fields: [
      { name: 'mode', label: '过滤模式', control: 'segmented', options: [{ label: '简单', value: 'SIMPLE' }, { label: '表达式', value: 'EXPRESSION' }] },
      { name: 'supportType', label: '简单类型', control: 'select', options: [{ label: '字符串', value: 'STRING' }, { label: '数字', value: 'NUMBER' }, { label: '布尔', value: 'BOOLEAN' }] },
      { name: 'itemIsNullUse', label: '元素为空时保留', control: 'switch' },
      { name: 'simpleSymbol', label: '简单运算符', control: 'select', options: simpleSymbolOptions },
      { name: 'evaluatorType', label: '表达式引擎', control: 'select', options: [{ label: 'Groovy', value: 'GROOVY' }] },
      { name: 'condition', label: '过滤条件', control: 'code', language: 'txt', required: true },
      { name: 'compareTo', label: '比较值', control: 'compareTo' },
    ],
    inputConfigs: input(),
    outputConfigs: output('Array'),
    summary: (config) => [line('模式', config.mode), line('条件', config.condition)],
  }),
  schema({
    type: 'LIST_SORT',
    title: '列表排序',
    group: 'list',
    description: '按表达式计算的字段对列表排序。',
    icon: 'sort',
    color: '#52c41a',
    defaultConfig: { evaluatorType: 'GROOVY', direction: 'ASC', nullFirst: false, strictMode: false },
    fields: [
      { name: 'evaluatorType', label: '表达式引擎', control: 'select', options: [{ label: 'Groovy', value: 'GROOVY' }] },
      { name: 'condition', label: '排序表达式', control: 'code', language: 'txt', required: true },
      { name: 'direction', label: '排序方向', control: 'segmented', options: [{ label: '升序', value: 'ASC' }, { label: '降序', value: 'DESC' }] },
      { name: 'nullFirst', label: '空值靠前', control: 'switch' },
      { name: 'strictMode', label: '严格模式', control: 'switch' },
    ],
    inputConfigs: input(),
    outputConfigs: output('Array'),
    summary: (config) => [line('方向', config.direction), line('严格模式', config.strictMode ? '是' : '否')],
  }),
  schema({
    type: 'STRING_SPLIT',
    title: '字符串分割',
    group: 'transform',
    description: '按简单分隔符、正则、固定长度、换行、键值或多分隔符拆分字符串。',
    icon: 'split',
    color: '#08979c',
    defaultConfig: { trimParts: true, removeEmpty: true, limit: -1, maxResults: -1, processingResult: true, keyValueDelimiter: '=', keyValueOutputFormat: 'COLON_SEPARATED' },
    fields: [
      {
        name: 'mode',
        label: '分割模式',
        control: 'select',
        options: [
          { label: '简单分隔符', value: 'SIMPLE' },
          { label: '正则', value: 'REGEX' },
          { label: '固定长度', value: 'FIXED_LENGTH' },
          { label: '换行', value: 'LINE_BREAK' },
          { label: '键值对', value: 'KEY_VALUE' },
          { label: '多个分隔符', value: 'MULTIPLE_DELIMITERS' },
        ],
      },
      { name: 'delimiter', label: '分隔符/正则/长度', control: 'input' },
      { name: 'delimiters', label: '多个分隔符', control: 'stringList' },
      { name: 'trimParts', label: '去除首尾空白', control: 'switch' },
      { name: 'removeEmpty', label: '移除空字符串', control: 'switch' },
      { name: 'limit', label: 'Split limit', control: 'number', defaultValue: -1 },
      { name: 'maxResults', label: '最大结果数', control: 'number', defaultValue: -1 },
      { name: 'processingResult', label: '处理分割结果', control: 'switch' },
      { name: 'prefix', label: '结果前缀', control: 'input' },
      { name: 'suffix', label: '结果后缀', control: 'input' },
      { name: 'keyValueDelimiter', label: '键值分隔符', control: 'input', defaultValue: '=' },
      {
        name: 'keyValueOutputFormat',
        label: '键值输出格式',
        control: 'select',
        options: [
          { label: 'key: value', value: 'COLON_SEPARATED' },
          { label: 'key=value', value: 'EQUALS_SEPARATED' },
          { label: 'JSON 对象', value: 'JSON_OBJECT' },
          { label: 'key -> value', value: 'MAP_ENTRY' },
          { label: '自定义', value: 'CUSTOM' },
        ],
      },
      { name: 'keyValueCustomFormat', label: '自定义格式', control: 'input', placeholder: '%s===>%s' },
    ],
    inputConfigs: input(),
    outputConfigs: output('Array'),
    summary: (config) => [line('模式', config.mode), line('分隔符', config.delimiter)],
  }),
  schema({
    type: 'STRING_TEMPLATE',
    title: '字符串模板',
    group: 'transform',
    description: '使用字符串或 Velocity 模板生成文本。',
    icon: 'template',
    color: '#08979c',
    defaultConfig: { templateType: 'STRING', template: '' },
    fields: [
      { name: 'templateType', label: '模板格式', control: 'segmented', options: [{ label: '字符串', value: 'STRING' }, { label: 'Velocity', value: 'VELOCITY' }] },
      { name: 'template', label: '模板内容', control: 'code', language: 'txt', required: true },
    ],
    inputConfigs: input(),
    outputConfigs: output('String'),
    summary: (config) => [line('格式', config.templateType)],
  }),
  schema({
    type: 'SERIALIZE',
    title: '序列化',
    group: 'transform',
    description: '将对象序列化为 JSON、XML、YAML、Base64 或 URL 编码。',
    icon: 'serialize',
    color: '#08979c',
    defaultConfig: { mode: 'COMPACT', format: 'JSON', excludeNulls: false, excludeEmptyStrings: false, encoding: 'UTF-8' },
    fields: [
      { name: 'mode', label: '序列化模式', control: 'segmented', options: [{ label: '紧凑', value: 'COMPACT' }, { label: '美化', value: 'PRETTY' }] },
      { name: 'format', label: '格式', control: 'select', options: ['JSON', 'XML', 'YAML', 'BASE64', 'URL_ENCODED'].map((value) => ({ label: value, value })) },
      { name: 'excludeNulls', label: '排除 null', control: 'switch' },
      { name: 'excludeEmptyStrings', label: '排除空字符串', control: 'switch' },
      { name: 'encoding', label: '编码', control: 'input', defaultValue: 'UTF-8' },
    ],
    inputConfigs: input(),
    outputConfigs: output('String'),
    summary: (config) => [line('格式', config.format), line('模式', config.mode)],
  }),
  schema({
    type: 'UNSERIALIZE',
    title: '反序列化',
    group: 'transform',
    description: '将 JSON、XML、YAML、Base64 或 URL 编码内容反序列化。',
    icon: 'unserialize',
    color: '#08979c',
    defaultConfig: { format: 'JSON', excludeNulls: false, encoding: 'UTF-8' },
    fields: [
      { name: 'format', label: '格式', control: 'select', options: ['JSON', 'XML', 'YAML', 'BASE64', 'URL_ENCODED'].map((value) => ({ label: value, value })) },
      { name: 'excludeNulls', label: '排除 null', control: 'switch' },
      { name: 'encoding', label: '编码', control: 'input', defaultValue: 'UTF-8' },
    ],
    inputConfigs: input(),
    outputConfigs: output(),
    summary: (config) => [line('格式', config.format)],
  }),
  schema({
    type: 'VARIABLE_AGG',
    title: '变量聚合',
    group: 'variable',
    description: '将多个输入聚合为数组、Map 或字符串。',
    icon: 'aggregate',
    color: '#faad14',
    defaultConfig: { strategy: 'MAP', excludeNull: false, splicingSymbol: '' },
    fields: [
      { name: 'strategy', label: '聚合策略', control: 'segmented', options: [{ label: '数组', value: 'ARRAY' }, { label: 'Map', value: 'MAP' }, { label: '字符串', value: 'STRING' }] },
      { name: 'excludeNull', label: '排除空值', control: 'switch' },
      { name: 'splicingSymbol', label: '字符串拼接符', control: 'input' },
    ],
    inputConfigs: input(),
    outputConfigs: output(),
    summary: (config) => [line('策略', config.strategy)],
  }),
  schema({
    type: 'NON_EMPTY_SELECT',
    title: '非空选择',
    group: 'logic',
    description: '从多个候选输入中选择第一个或最后一个非空值。',
    icon: 'select',
    color: '#fa8c16',
    defaultConfig: { strategy: 'FIRST' },
    fields: [
      { name: 'strategy', label: '选择策略', control: 'segmented', options: [{ label: '第一个', value: 'FIRST' }, { label: '最后一个', value: 'LAST' }] },
      { name: 'defaultNextNodeId', label: '默认节点ID', control: 'input' },
    ],
    inputConfigs: input(),
    outputConfigs: output(),
    summary: (config) => [line('策略', config.strategy)],
  }),
  schema({
    type: 'MATCH_RESULT',
    title: '结果匹配',
    group: 'logic',
    description: '按匹配值选择不同后续节点，类似 switch-case。',
    icon: 'match',
    color: '#fa8c16',
    defaultConfig: { matches: [], matchType: 'EQUALS', caseSensitive: true },
    fields: [
      { name: 'matches', label: '匹配项', control: 'matchList', defaultValue: [] },
      { name: 'matchType', label: '匹配方式', control: 'segmented', options: [{ label: '等于', value: 'EQUALS' }, { label: '包含', value: 'CONTAINS' }] },
      { name: 'caseSensitive', label: '区分大小写', control: 'switch' },
      { name: 'defaultNextNodeId', label: '默认节点ID', control: 'input' },
    ],
    inputConfigs: input(),
    outputConfigs: output(),
    branchHandles: [{ id: 'match', label: 'match' }, { id: 'default', label: 'default' }],
    summary: (config) => [line('匹配方式', config.matchType), line('匹配项', Array.isArray(config.matches) ? `${config.matches.length} 个` : 0)],
  }),
]

export const workflowNodeSchemaMap = workflowNodeSchemas.reduce<Record<string, WorkflowNodeSchema>>((acc, item) => {
  acc[item.type] = item
  return acc
}, {})

export function getWorkflowNodeSchema(type: string) {
  return workflowNodeSchemaMap[type]
}

export function cloneDefaultConfig(schema: WorkflowNodeSchema) {
  return JSON.parse(JSON.stringify(schema.defaultConfig || {})) as Record<string, unknown>
}

export function cloneDefaultInputs(schema: WorkflowNodeSchema) {
  return JSON.parse(JSON.stringify(schema.inputConfigs || [])) as WorkflowInputConfig[]
}

export function cloneDefaultOutputs(schema: WorkflowNodeSchema, nodeId: string) {
  const outputs = JSON.parse(JSON.stringify(schema.outputConfigs || output())) as WorkflowOutputConfig[]
  return outputs.map((item) => ({ ...item, fromNodeId: nodeId }))
}

export function buildNodeSummary(schema: WorkflowNodeSchema | undefined, config: Record<string, unknown>, resources?: WorkflowResourceMaps) {
  if (!schema?.summary) return []
  return schema.summary(config || {}, resources).filter(Boolean).slice(0, 3)
}
