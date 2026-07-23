/**
 * IconFont 图标定义
 * 所有可用图标的统一注册表，与 public/iconfont/iconfont.css 保持同步
 *
 * @author huxuehao
 **/

/**
 * 图标条目定义
 */
export interface IconDef {
  /** 图标名称（class 中 icon- 前缀后的部分） */
  name: string
  /** 中文描述 */
  label: string
  /** unicode 编码 */
  unicode: string
}

/**
 * 所有可用图标定义
 */
export const ICON_DEFS: IconDef[] = [
  { name: 'nodestart', label: '开始节点', unicode: '\\e8e8' },
  { name: 'nodeend', label: '结束节点', unicode: '\\e6de' },
  { name: 'nodecode', label: '代码节点', unicode: '\\e6f7' },
  { name: 'nodellm', label: 'LLM 节点', unicode: '\\e8f1' },
  { name: 'nodeif_else', label: '条件分支', unicode: '\\e616' },
  { name: 'nodeiterate', label: '迭代节点', unicode: '\\e62a' },
  { name: 'nodeloop', label: '循环节点', unicode: '\\e62b' },
  { name: 'nodehttp_inline', label: 'HTTP 内联', unicode: '\\e619' },
  { name: 'nodehttp_external', label: 'HTTP 外部', unicode: '\\e9d7' },
  { name: 'nodedb_select', label: '数据库查询', unicode: '\\e93f' },
  { name: 'nodedb_insert', label: '数据库插入', unicode: '\\e80a' },
  { name: 'nodedb_update', label: '数据库更新', unicode: '\\e93d' },
  { name: 'nodedb_delete', label: '数据库删除', unicode: '\\e80b' },
  { name: 'nodeplugin', label: '插件节点', unicode: '\\e7b6' },
  { name: 'nodemq_push', label: '消息推送', unicode: '\\e729' },
  { name: 'nodeserialize', label: '序列化', unicode: '\\e665' },
  { name: 'nodeunserialize', label: '反序列化', unicode: '\\e601' },
  { name: 'nodestring_template', label: '字符串模板', unicode: '\\e69f' },
  { name: 'nodestring_split', label: '字符串分割', unicode: '\\ed26' },
  { name: 'nodevariable_agg', label: '变量聚合', unicode: '\\e680' },
  { name: 'nodelist_filter', label: '列表过滤', unicode: '\\e718' },
  { name: 'nodelist_sort', label: '列表排序', unicode: '\\e609' },
  { name: 'nodematch_result', label: '匹配结果', unicode: '\\e600' },
  { name: 'nodecache', label: '缓存节点', unicode: '\\e684' },
  { name: 'nodenon_empty_select', label: '非空选择', unicode: '\\e634' },
  { name: 'nodetool', label: '工具执行', unicode: '\\e679' },
  { name: 'nodemcp', label: 'MCP调用', unicode: '\\e9d8' },
  { name: 'nodevariable', label: '变量', unicode: '\\e61f' },
  { name: 'nodeemail', label: '发送邮件', unicode: '\\e60a' },
  { name: 'nodeqiyeweixin', label: '企业微信', unicode: '\\e986' },
  { name: 'nodedingding', label: '钉钉', unicode: '\\e648' },
  { name: 'nodefeishu', label: '飞书', unicode: '\\e802' },
  { name: 'nodeprocessOutput', label: '过程输出', unicode: '\\e633' },
  { name: 'nodeintentRecognition', label: '意图识别', unicode: '\\e604' },
  { name: 'nodenoOperation', label: '空操作', unicode: '\\e69c' },
]

/**
 * 所有可用图标名称的联合类型，用于组件 props 类型约束
 */
export type IconName = (typeof ICON_DEFS)[number]['name']

/**
 * 图标名称 → 图标定义的快速查找映射
 */
export const ICON_MAP: Record<IconName, IconDef> = ICON_DEFS.reduce(
  (map, def) => {
    map[def.name] = def
    return map
  },
  {} as Record<IconName, IconDef>,
)

/**
 * 判断是否为有效的图标名称
 */
export function isValidIconName(name: string): name is IconName {
  return name in ICON_MAP
}
