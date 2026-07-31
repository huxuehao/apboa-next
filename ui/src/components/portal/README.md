# 自定义组件开发规范

「自定义组件」面板可渲染本目录（`ui/src/components/portal/`）下的业务组件。
目录在编译期通过 `import.meta.glob` 锁定（路径隔离），运行期无法注入其他路径的代码。

## 目录与扫描规则

- 组件放在本目录下，支持子目录，仅识别 `.vue` 文件（本 README 与 `_shared/` 工具不会被扫描为组件）
- 组件标识为相对路径去扩展名，如 `DemoWelcome`、`sub/Foo`
- 新增文件后开发服务器自动感知；生产环境每个组件独立分包、按需加载
- 设计器配置面板下拉自动列出全部组件，支持按名称 / 文件名 / 描述搜索

## 元信息约定（下拉三要素）

在组件中声明 `portalMeta`，下拉选项将自动展示组件名称与描述：

```ts
defineOptions({
  portalMeta: {
    name: '示例欢迎卡',
    description: '一句话说明组件用途',
  },
})
```

未声明时名称回退为文件名、描述为空。

## props 声明与自动识别

- 组件正常使用 `defineProps` + `withDefaults` 声明 props 即可，**无需额外配置**
- 设计器「编辑 props」弹窗会**自动识别**组件声明的 props（key / 类型 / 默认值）并预填，
  已配置条目保留用户值；也可手动添加、修改、删除
- 支持的类型与传递保证（按声明类型强制转换，保证数据类型正确）：

| 声明类型 | 弹窗控件 | 注入组件的类型 |
| --- | --- | --- |
| `String` | 文本输入 | `string` |
| `Number` | 数字输入 | `number` |
| `Boolean` | 开关 | `boolean` |
| `Date` | 日期选择器 | `Date` 对象 |
| `Array` | JSON 文本域 | 解析后的数组 |
| `Object` | JSON 文本域 | 解析后的对象 |

- 切换组件时会自动清空旧组件的 props 配置与操作按钮显隐，避免脏数据残留

## 上下文注入 panelContext

面板渲染器会额外注入 `panelContext` prop（自动识别时已排除，无需用户配置）：

```ts
const props = defineProps<{
  panelContext?: { panelId: string; title: string; interactive: boolean }
}>()
```

- `panelId`：面板实例 ID（可用作 localStorage 键前缀，避免多实例串扰）
- `title`：面板标题
- `interactive`：true 表示门户运行态，false 表示设计器编辑态

## 标题栏操作按钮（自动识别）

通过 `defineExpose` 暴露 `panelActions`，面板自动识别并渲染在标题栏右侧；
用户可在配置面板「操作按钮」中逐个控制显隐：

```ts
defineExpose({
  panelActions: [
    { key: 'refresh', label: '刷新', icon: 'ReloadOutlined', run: refresh },
    { key: 'more', label: '更多', icon: 'EllipsisOutlined', run: openMore },
  ],
})
```

- `key`：唯一标识（英文），显隐配置以此记录
- `icon`：图标注册表中的 Outlined 图标名，缺省时按钮显示 label 文本
- `run`：点击回调，在组件内部实现具体行为

## 定时刷新接入

组件暴露 `refresh()` 方法后，面板的「定时刷新」配置将周期性调用它
（自定义组件不绑定数据集，刷新行为完全由组件自身定义）：

```ts
defineExpose({ refresh: () => reload() })
```

## 错误兜底

- 组件加载失败 / 标识不存在：面板内显示错误提示，不影响画布
- 组件运行时抛错：被面板捕获并就地展示错误信息
- props 条目非法（如 JSON 解析失败）：保存时前置校验拦截，运行时非法条目自动跳过

## 视觉与动画规范（必须遵守）

- 纯色扁平：禁止渐变背景、大面积阴影、玻璃态、霓虹高光
- 禁止 emoji 字符作图标；表情/图形用自绘 SVG 线条图标或 Outlined 图标
- 禁止无限循环动画（bounce / float / pulse / infinite）；
  允许**用户触发或数据变化驱动的一次性动画**（翻牌、数字滚动、错落入场、水位上涨等）
- 布局优先 Flexbox / CSS Grid
- 数值缓动可复用共享工具 `_shared/animate.ts` 的 `animateNumber(from, to, duration, onUpdate)`（rAF 驱动、返回取消函数）

## 状态持久化建议

- 轻量个人状态用 `localStorage`，键格式建议 `apboa-<组件名>:<panelId>[:<日期>]`
- 读写包 try/catch，存储不可用时静默降级为会话内状态
- 按日数据在键中带日期实现跨天自动清零；长期数据注意裁剪（如仅留最近 30 天）

## 完整示例

- `DemoWelcome.vue`：portalMeta、panelContext、自定义 props、panelActions、refresh 全约定演示
- `WorkdayProgress.vue`：数字滚动 + 里程碑 + 文案过渡
- `LuckyCard.vue`：两段式翻牌 + 错落入场
- `WaterTracker.vue`：clipPath 水位上涨 + localStorage 按日持久化
- `MoodBoard.vue`：自绘 SVG 表情 + 迷你柱状图 + 历史裁剪
- `QuoteCard.vue`：打字机逐字展开 + 操作按钮
