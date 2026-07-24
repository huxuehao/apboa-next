/**
 * 模型展示图标集。
 *
 * 两层结构：
 * - MODEL_ICON_MAP：@ant-design/icons-vue 全量 Outlined（421 个，运行时过滤）——
 *   仅作渲染解析兜底，任何历史配置名永远可解析，不随策展调整回落默认；
 * - MODEL_ICON_GROUPS：人工策展的分组精选（面板展示源）——只收适合"模型"
 *   语义的图标并分类，过滤掉品牌/杂类等明显不适用的，新增图标在对应组补名即可
 *   （写错/不存在的名会被存在性过滤自动剔除，不会渲染空位）。
 * 搜索时跨全量匹配（平铺展示），空关键字回分组视图。
 */
import type { Component } from 'vue'
import * as AllIcons from '@ant-design/icons-vue'
import { DeploymentUnitOutlined } from '@ant-design/icons-vue'

/** 默认图标名（未配置 logo 时的回落） */
export const DEFAULT_MODEL_ICON = 'DeploymentUnitOutlined'

/** 默认图标颜色（未配置 logoColor 时的回落，同 $chat-primary） */
export const DEFAULT_MODEL_ICON_COLOR = '#0F74FF'

/** 图标颜色预置色板（后台点选；与图标网格同一选择面板） */
export const MODEL_ICON_COLORS = [
  '#0F74FF', // 主题蓝
  '#52c41a', // 绿
  '#722ed1', // 紫
  '#fa8c16', // 橙
  '#f5222d', // 红
  '#13c2c2', // 青
  '#eb2f96', // 粉
  '#faad14', // 金
  '#2f54eb', // 深蓝
  '#8c8c8c'  // 灰
]

/** 图标名 → 组件（全量 Outlined；渲染解析兜底，历史配置名永远可解析） */
export const MODEL_ICON_MAP: Record<string, Component> = Object.fromEntries(
  Object.entries(AllIcons).filter(([name]) => name.endsWith('Outlined'))
) as unknown as Record<string, Component>

/** 构建分组并做存在性过滤（写错的名静默剔除） */
function group(label: string, names: string[]): { label: string; icons: string[] } {
  return { label, icons: names.filter((n) => n in MODEL_ICON_MAP) }
}

/** 面板展示的策展分组（适合模型语义的精选） */
export const MODEL_ICON_GROUPS: Array<{ label: string; icons: string[] }> = [
  group('AI 与科技', [
    'DeploymentUnitOutlined', 'ApiOutlined', 'RobotOutlined', 'RocketOutlined',
    'ThunderboltOutlined', 'ExperimentOutlined', 'CodeSandboxOutlined', 'BulbOutlined',
    'BranchesOutlined', 'ClusterOutlined', 'NodeIndexOutlined', 'PartitionOutlined',
    'GatewayOutlined', 'FunctionOutlined', 'CodeOutlined', 'BugOutlined',
    'ToolOutlined', 'ControlOutlined', 'SlidersOutlined', 'BuildOutlined',
    'AppstoreOutlined', 'BlockOutlined', 'GoldOutlined', 'CompressOutlined'
  ]),
  group('云与数据', [
    'CloudOutlined', 'CloudServerOutlined', 'CloudSyncOutlined', 'CloudDownloadOutlined',
    'CloudUploadOutlined', 'DatabaseOutlined', 'HddOutlined', 'SaveOutlined',
    'GlobalOutlined', 'WifiOutlined', 'LinkOutlined', 'ShareAltOutlined',
    'InboxOutlined', 'FolderOpenOutlined', 'FileZipOutlined', 'DeliveredProcedureOutlined'
  ]),
  group('视觉与多媒体', [
    'EyeOutlined', 'CameraOutlined', 'PictureOutlined', 'VideoCameraOutlined',
    'PlayCircleOutlined', 'AudioOutlined', 'SoundOutlined', 'CustomerServiceOutlined',
    'FileImageOutlined', 'ScanOutlined', 'QrcodeOutlined', 'ExpandOutlined',
    'BgColorsOutlined', 'HighlightOutlined', 'FormatPainterOutlined'
  ]),
  group('对话与文本', [
    'MessageOutlined', 'CommentOutlined', 'TranslationOutlined', 'FontSizeOutlined',
    'EditOutlined', 'FormOutlined', 'FileTextOutlined', 'ReadOutlined',
    'BookOutlined', 'ProfileOutlined', 'SolutionOutlined', 'AlignLeftOutlined',
    'OrderedListOutlined', 'FileSearchOutlined', 'SnippetsOutlined'
  ]),
  group('分析与数学', [
    'CalculatorOutlined', 'LineChartOutlined', 'BarChartOutlined', 'PieChartOutlined',
    'AreaChartOutlined', 'DotChartOutlined', 'FundOutlined', 'RadarChartOutlined',
    'StockOutlined', 'RiseOutlined', 'FallOutlined', 'NumberOutlined',
    'PercentageOutlined', 'FieldBinaryOutlined', 'MonitorOutlined', 'DashboardOutlined'
  ]),
  group('符号与其他', [
    'StarOutlined', 'HeartOutlined', 'FireOutlined', 'CrownOutlined',
    'TrophyOutlined', 'GiftOutlined', 'SmileOutlined', 'CoffeeOutlined',
    'AimOutlined', 'CompassOutlined', 'FlagOutlined', 'TagOutlined',
    'KeyOutlined', 'SafetyOutlined', 'SecurityScanOutlined', 'BellOutlined',
    'SendOutlined', 'SearchOutlined', 'FilterOutlined', 'SyncOutlined',
    'HistoryOutlined', 'ClockCircleOutlined', 'HourglassOutlined', 'FieldTimeOutlined'
  ])
]

/** 按名取图标组件（未知名/空回落默认） */
export function resolveModelIcon(name?: string | null): Component {
  return (name && MODEL_ICON_MAP[name]) || DeploymentUnitOutlined
}
