import type {
  WorkflowInputConfig,
  WorkflowNodeSchema,
  WorkflowOutputConfig,
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
  { key: 'business', title: '业务' },
] as const

const input = (): WorkflowInputConfig[] => [{ name: 'input', sourceType: 'NODE_OUTPUT', type: 'String' }]
const output = (type = 'Object'): WorkflowOutputConfig[] => [{ name: 'output', type, description: '节点默认输出' }]

function groupTitle(group: string) {
  return WORKFLOW_GROUPS.find((item) => item.key === group)?.title || group
}

function schema(item: Omit<WorkflowNodeSchema, 'groupTitle'>): WorkflowNodeSchema {
  return { ...item, groupTitle: groupTitle(item.group) }
}

export const workflowNodeSchemas: WorkflowNodeSchema[] = [
  schema({
    type: 'START', title: '开始', group: 'basic',
    description: '工作流入口，定义对外触发协议、请求参数、访问控制和鉴权信息。',
    icon: 'play', color: '#1677ff', panelComponent: 'StartNodePanel',
    defaultConfig: { params: [] },
    inputConfigs: [], outputConfigs: [],
    summaryComponent: 'StartNodeSummary',
    showSummary: false,
  }),
  schema({
    type: 'END', title: '结束', group: 'basic',
    description: '工作流结束节点，按模板生成最终响应。',
    icon: 'stop', color: '#8c8c8c', panelComponent: 'EndNodePanel',
    defaultConfig: { responseTemplate: '${input}', formatterType: 'STRING' },
    inputConfigs: input(), outputConfigs: output(),
    summaryComponent: 'EndNodeSummary',
    showSummary: false,
  }),
  schema({
    type: 'IF_ELSE', title: '条件分支', group: 'logic',
    description: '根据输入值或表达式结果选择 true/false 分支。',
    icon: 'branches', color: '#fa8c16', panelComponent: 'IfElseNodePanel',
    defaultConfig: { evaluatorType: 'GROOVY', scope: 'SELF', inputIsNullUse: false, symbol: 'EQ', compareTo: { type: 'CONSTANT', value: '' } },
    inputConfigs: input(), outputConfigs: output('Boolean'),
    branchHandles: [{ id: 'true', label: 'true' }, { id: 'false', label: 'false' }],
    summaryComponent: 'IfElseNodeSummary',
    showSummary: true,
  }),
  schema({
    type: 'ITERATE', title: '迭代处理', group: 'logic',
    description: '对集合输入逐项执行迭代处理代码。',
    icon: 'iterate', color: '#fa8c16', panelComponent: 'IterateNodePanel',
    defaultConfig: { language: 'JAVA', iterateCode: `package com.hxh.apboa.node.iterate.load;

import java.util.*;
import com.hxh.apboa.node.iterate.IteratorExecutor;

/**
 * 数据加载处理实现类。
 * 作为迭代器执行器，对可迭代对象中的每个元素进行处理。
 */
public class DataProcess implements IteratorExecutor {

    /**
     * 处理迭代过程中的单个元素，该方法会在遍历可迭代对象时被逐一调用。
     *
     * @param item  当前迭代到的子元素
     * @param index 当前子元素在可迭代对象中的位置索引
     * @return 处理后的子元素对象
     */
    @Override
    public Object doIterate(Object item, Integer index) {
        // 对item进行处理

        return item;
    }
}` },
    inputConfigs: input(), outputConfigs: output('Array'),
    summaryComponent: 'IterateNodeSummary',
    showSummary: true,
  }),
  schema({
    type: 'LOOP', title: '循环', group: 'logic',
    description: '按次数或数据源执行子工作流。',
    icon: 'loop', color: '#fa8c16', panelComponent: 'LoopNodePanel',
    defaultConfig: { loopVariable: 'loopIndex', maxIterations: 1000, terminationExpression: '', iterateDataSource: '', itemVariable: 'item', entryNodeId: '', subNodes: [], subEdges: [] },
    inputConfigs: input(), outputConfigs: output(),
    summaryComponent: 'LoopNodeSummary',
    showSummary: true,
  }),
  schema({
    type: 'NON_EMPTY_SELECT', title: '非空选择', group: 'logic',
    description: '从多个候选输入中选择第一个或最后一个非空值。',
    icon: 'select', color: '#fa8c16', panelComponent: 'NonEmptySelectNodePanel',
    defaultConfig: { strategy: 'FIRST' },
    inputConfigs: input(), outputConfigs: output(),
    summaryComponent: 'NonEmptySelectNodeSummary',
    showSummary: true,
  }),
  schema({
    type: 'MATCH_RESULT', title: '结果匹配', group: 'logic',
    description: '按匹配值选择不同后续节点，类似 switch-case。',
    icon: 'match', color: '#fa8c16', panelComponent: 'MatchResultNodePanel',
    defaultConfig: { matches: [], matchType: 'EQUALS', caseSensitive: true },
    inputConfigs: input(), outputConfigs: output(),
    summaryComponent: 'MatchResultNodeSummary',
    showSummary: true,
  }),
  schema({
    type: 'CACHE_FETCH', title: '读取缓存', group: 'cache',
    description: '从指定缓存实例读取键值。',
    icon: 'database', color: '#13a8a8', panelComponent: 'CacheFetchNodePanel',
    defaultConfig: { formatterType: 'VELOCITY' },
    inputConfigs: input(), outputConfigs: output(),
    summaryComponent: 'CacheFetchNodeSummary',
    showSummary: true,
  }),
  schema({
    type: 'CACHE_SET', title: '写入缓存', group: 'cache',
    description: '将值写入指定缓存，expire 为空或 0 表示不过期。',
    icon: 'save', color: '#13a8a8', panelComponent: 'CacheSetNodePanel',
    defaultConfig: { formatterType: 'VELOCITY', expire: 0 },
    inputConfigs: input(), outputConfigs: output('Boolean'),
    summaryComponent: 'CacheSetNodeSummary',
    showSummary: true,
  }),
  schema({
    type: 'CACHE_REMOVE', title: '删除缓存', group: 'cache',
    description: '删除指定缓存键。',
    icon: 'delete', color: '#13a8a8', panelComponent: 'CacheRemoveNodePanel',
    defaultConfig: { formatterType: 'VELOCITY' },
    inputConfigs: input(), outputConfigs: output(),
    summaryComponent: 'CacheRemoveNodeSummary',
    showSummary: true,
  }),
  schema({
    type: 'CACHE_REFRESH', title: '刷新缓存', group: 'cache',
    description: '刷新缓存键的过期时间。',
    icon: 'reload', color: '#13a8a8', panelComponent: 'CacheRefreshNodePanel',
    defaultConfig: { formatterType: 'VELOCITY', expire: 0 },
    inputConfigs: input(), outputConfigs: output('Boolean'),
    summaryComponent: 'CacheRefreshNodeSummary',
    showSummary: true,
  }),
  ...(['DB_SELECT', 'DB_INSERT', 'DB_UPDATE', 'DB_DELETE'] as const).map((type) => {
    const titleMap = { DB_SELECT: '数据库查询', DB_INSERT: '数据库插入', DB_UPDATE: '数据库更新', DB_DELETE: '数据库删除' }
    const panelMap: Record<string, string> = { DB_SELECT: 'DbSelectNodePanel', DB_INSERT: 'DbInsertNodePanel', DB_UPDATE: 'DbUpdateNodePanel', DB_DELETE: 'DbDeleteNodePanel' }
    const summaryMap: Record<string, string> = { DB_SELECT: 'DbSelectNodeSummary', DB_INSERT: 'DbInsertNodeSummary', DB_UPDATE: 'DbUpdateNodeSummary', DB_DELETE: 'DbDeleteNodeSummary' }
    return schema({
      type, title: titleMap[type], group: 'data',
      description: '执行使用 ? 占位符的 SQL，并按顺序绑定参数。',
      icon: 'table', color: '#2f54eb', panelComponent: panelMap[type] || 'DbSelectNodePanel',
      defaultConfig: { params: [], formatterType: 'VELOCITY' },
      inputConfigs: input(), outputConfigs: output(),
      summaryComponent: summaryMap[type] || 'DbSelectNodeSummary',
      showSummary: true,
    })
  }),
  schema({
    type: 'MQ_PUSH', title: '发送消息', group: 'message',
    description: '向 Kafka、RabbitMQ 或 RocketMQ 推送消息。',
    icon: 'message', color: '#722ed1', panelComponent: 'MqPushNodePanel',
    defaultConfig: { templateType: 'STRING' },
    inputConfigs: input(), outputConfigs: output('Boolean'),
    summaryComponent: 'MqPushNodeSummary',
    showSummary: true,
  }),
  schema({
    type: 'AGENT', title: '智能体', group: 'integration',
    description: '使用模型、提示词、技能、工具和 MCP 发起阻塞式 ReAct Agent 调用。',
    icon: 'llm', color: '#eb2f96', panelComponent: 'AgentNodePanel',
    defaultConfig: {
      modelConfigId: undefined,
      modelParamsOverrideEnabled: false,
      modelParamsOverride: {},
      formatterType: 'STRING',
      systemPrompt: '',
      userPrompt: '',
      skillPackageIds: [],
      toolIds: [],
      mcps: [],
      maxIterations: 5,
      structuredOutputEnabled: false,
      structuredOutput: {},
    },
    inputConfigs: input(),
    outputConfigs: [
      { name: 'output', type: 'Object', description: 'Agent 默认输出' },
      { name: 'text', type: 'String', description: 'Agent 文本输出' },
      { name: 'structured', type: 'Object', description: 'Agent 结构化输出' },
    ],
    summaryComponent: 'AgentNodeSummary',
    showSummary: true,
  }),
  schema({
    type: 'TOOL_EXECUTE', title: '工具执行', group: 'integration',
    description: '调用平台已注册的内置或自定义工具，将输入参数传递给工具并返回执行结果。',
    icon: 'nodetool', color: '#eb2f96', panelComponent: 'ToolExecuteNodePanel',
    defaultConfig: { toolId: undefined, toolName: undefined },
    inputConfigs: input(), outputConfigs: output(),
    summaryComponent: 'ToolExecuteNodeSummary',
    showSummary: true,
  }),
  schema({
    type: 'MCP_CALL', title: 'MCP 调用', group: 'integration',
    description: '调用指定 MCP 服务上的工具，将输入参数传递给工具并返回执行结果。',
    icon: 'nodemcp', color: '#eb2f96', panelComponent: 'McpNodePanel',
    defaultConfig: { mcpServerId: undefined, mcpToolId: undefined, mcpServerName: undefined, mcpToolName: undefined },
    inputConfigs: input(), outputConfigs: output(),
    summaryComponent: 'McpNodeSummary',
    showSummary: true,
  }),
  schema({
    type: 'HTTP_EXTERNAL', title: 'HTTP 请求', group: 'integration',
    description: '调用 HTTP 请求接口，支持参数、Header、Body、超时、重试和响应解析。',
    icon: 'api', color: '#eb2f96', panelComponent: 'HttpExternalNodePanel',
    defaultConfig: { formatterType: 'STRING', connectTimeout: 10, readTimeout: 30, writeTimeout: 30, maxRetries: 3, retryStatusCodes: [], followRedirects: true, syncExecute: true, bodyToObject: true, request: { method: 'GET', contentType: 'JSON', pathParams: [], queryParams: [], headers: [], body: '' } },
    inputConfigs: input(), outputConfigs: output(),
    summaryComponent: 'HttpExternalNodeSummary',
    showSummary: true,
  }),
  schema({
    type: 'CODE', title: '代码执行', group: 'integration',
    description: '执行 Java 或 JavaScript 代码处理输入。',
    icon: 'code', color: '#eb2f96', panelComponent: 'CodeNodePanel',
    defaultConfig: { language: 'JAVA', codeSource: `package com.hxh.apboa.node.code.load;

import com.hxh.apboa.node.code.CodeExecutor;
import java.util.Map;

public class CodeExecute implements CodeExecutor {

    /**
     * 执行代码
     *
     * @param inputs 输入参数，与输入绑定一一对应
     * @return 输出参数
     */
    @Override
    public Object execute(Map<String, Object> inputs) throws Exception {

        return null;
    }
}` },
    inputConfigs: input(), outputConfigs: output(),
    summaryComponent: 'CodeNodeSummary',
    showSummary: true,
  }),
  schema({
    type: 'STRING_SPLIT', title: '字符串分割', group: 'transform',
    description: '按简单分隔符、正则、固定长度、换行、键值或多分隔符拆分字符串。',
    icon: 'split', color: '#08979c', panelComponent: 'StringSplitNodePanel',
    defaultConfig: { trimParts: true, removeEmpty: true, limit: -1, maxResults: -1, processingResult: true, keyValueDelimiter: '=', keyValueOutputFormat: 'COLON_SEPARATED' },
    inputConfigs: input(), outputConfigs: output('Array'),
    summaryComponent: 'StringSplitNodeSummary',
    showSummary: true,
  }),
  schema({
    type: 'STRING_TEMPLATE', title: '字符串模板', group: 'transform',
    description: '使用字符串或 Velocity 模板生成文本。',
    icon: 'template', color: '#08979c', panelComponent: 'StringTemplateNodePanel',
    defaultConfig: { templateType: 'STRING', template: '' },
    inputConfigs: input(), outputConfigs: output('String'),
    summaryComponent: 'StringTemplateNodeSummary',
    showSummary: true,
  }),
  schema({
    type: 'SERIALIZE', title: '序列化', group: 'transform',
    description: '将对象序列化为 JSON、XML、YAML、Base64 或 URL 编码。',
    icon: 'serialize', color: '#08979c', panelComponent: 'SerializeNodePanel',
    defaultConfig: { mode: 'COMPACT', format: 'JSON', excludeNulls: false, excludeEmptyStrings: false, encoding: 'UTF-8' },
    inputConfigs: input(), outputConfigs: output('String'),
    summaryComponent: 'SerializeNodeSummary',
    showSummary: true,
  }),
  schema({
    type: 'UNSERIALIZE', title: '反序列化', group: 'transform',
    description: '将 JSON、XML、YAML、Base64 或 URL 编码内容反序列化。',
    icon: 'unserialize', color: '#08979c', panelComponent: 'UnserializeNodePanel',
    defaultConfig: { format: 'JSON', excludeNulls: false, encoding: 'UTF-8' },
    inputConfigs: input(), outputConfigs: output(),
    summaryComponent: 'UnserializeNodeSummary',
    showSummary: true,
  }),
  schema({
    type: 'LIST_FILTER', title: '列表过滤', group: 'list',
    description: '按简单条件或 Groovy 表达式过滤列表元素。',
    icon: 'filter', color: '#52c41a', panelComponent: 'ListFilterNodePanel',
    defaultConfig: { mode: 'SIMPLE', evaluatorType: 'GROOVY', itemIsNullUse: false },
    inputConfigs: input(), outputConfigs: output('Array'),
    summaryComponent: 'ListFilterNodeSummary',
    showSummary: true,
  }),
  schema({
    type: 'LIST_SORT', title: '列表排序', group: 'list',
    description: '按表达式计算的字段对列表排序。',
    icon: 'sort', color: '#52c41a', panelComponent: 'ListSortNodePanel',
    defaultConfig: { evaluatorType: 'GROOVY', direction: 'ASC', nullFirst: false, strictMode: false },
    inputConfigs: input(), outputConfigs: output('Array'),
    summaryComponent: 'ListSortNodeSummary',
    showSummary: true,
  }),
  schema({
    type: 'VARIABLE_AGG', title: '变量聚合', group: 'variable',
    description: '将多个输入聚合为数组、Map 或字符串。',
    icon: 'aggregate', color: '#faad14', panelComponent: 'VariableAggNodePanel',
    defaultConfig: { strategy: 'MAP', excludeNull: false, splicingSymbol: '' },
    inputConfigs: input(), outputConfigs: output(),
    summaryComponent: 'VariableAggNodeSummary',
    showSummary: true,
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
