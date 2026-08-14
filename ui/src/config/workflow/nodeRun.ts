/**
 * 节点「独立运行」能力配置
 *
 * 用于控制右键菜单中「运行此节点」选项是否对某个节点类型可用。
 * 后续新增节点需要支持独立运行时，只需将该节点的 NodeType 加入 RUNNABLE_NODE_TYPES 集合即可。
 *
 * 说明：仅允许具备确定执行语义、无需完整工作流上下文即可运行的节点。
 */
export const RUNNABLE_NODE_TYPES: ReadonlySet<string> = new Set([
  // 迭代处理
  'ITERATE',
  // 字符串分割
  'STRING_SPLIT',
  // 字符串模板
  'STRING_TEMPLATE',
  // 序列化 / 反序列化
  'SERIALIZE',
  'UNSERIALIZE',
  // 列表过滤 / 列表排序
  'LIST_FILTER',
  'LIST_SORT',
  // 聚合操作
  'VARIABLE_AGG',
  // 数据库查询 / 插入 / 更新 / 删除
  'DB_SELECT',
  'DB_INSERT',
  'DB_UPDATE',
  'DB_DELETE',
  // 读取 / 写入 / 删除 / 刷新缓存
  'CACHE_FETCH',
  'CACHE_SET',
  'CACHE_REMOVE',
  'CACHE_REFRESH',
  // 发送消息
  'MQ_PUSH',
  // 智能体
  'AGENT',
  // 工具执行
  'TOOL_EXECUTE',
  // 知识库检索
  'KNOWLEDGE_RETRIEVE',
  // MCP 调用
  'MCP_CALL',
  // HTTP 请求
  'HTTP_EXTERNAL',
  // 代码执行
  'CODE',
  // 发送邮件
  'EMAIL_SEND',
  // 企微消息
  'WECOM_SEND',
  // 钉钉消息
  'DINGTALK_SEND',
  // 飞书消息
  'FEISHU_SEND',
])

/**
 * 判断节点类型是否支持独立运行。
 */
export function isNodeRunnable(type?: string | null): boolean {
  return Boolean(type && RUNNABLE_NODE_TYPES.has(type))
}
