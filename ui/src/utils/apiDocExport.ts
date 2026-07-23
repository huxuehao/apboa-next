/**
 * 接口文档导出 PDF —— 基于 Paged.js 的分页排版
 *
 * 设计：把「所选范围 + 是否脱敏」的接口数据生成一份自包含的 HTML + 独立 print CSS，
 * 交给 Paged.js 预排成一页页 DOM（含封面 / 页眉页脚 / 页码 / 水印），再由调用方
 * window.print() 另存为矢量 PDF（文字可复制、可搜索）。
 *
 * 现有页面渲染完全不受影响——本模块只读取归一化后的 section 数据。
 *
 * @author psh
 */
import { Previewer } from 'pagedjs'

/* ============================ 数据类型 ============================ */

export interface ExportEndpointParam {
  name: string
  type: string
  required: boolean
  desc: string
}

export interface ExportEndpoint {
  id: string
  method: string
  path: string
  desc: string
  note?: string | null
  params: ExportEndpointParam[]
  bodyExample?: string | null
  responseExample?: string | null
}

/** 区块类型：external=外置链接 / access=对话入口 / auth=鉴权 / identity=身份断言（工具方验签）/ endpoints=接口数组 */
export type ExportSectionKind = 'external' | 'embed' | 'access' | 'auth' | 'identity' | 'endpoints'

export interface ExportSection {
  key: string
  title: string
  kind: ExportSectionKind
  /** kind=endpoints 时的接口列表 */
  items?: ExportEndpoint[]
}

export interface ExportOptions {
  /** 封面大标题 + 页眉左侧 */
  docTitle: string
  /** 封面副标题（空则不显示） */
  coverSubtitle: string
  /** 封面「智能体」行 + 页眉右侧显示值（用户可自定义，空则不显示） */
  agentLabel: string
  externalChatUrl: string
  accessUrl: string
  aguiBodyExample: string
  /** 是否脱敏 chatKey / agentCode（作用于正文里的 URL 凭证） */
  desensitize: boolean
  watermarkText: string
  /** 已格式化的生成时间，如 2026-07-05 14:30 */
  generatedAt: string
  /** 是否已启用嵌入用户身份（embedSecret 已配置）——影响嵌入区块的身份传递说明 */
  embedIdentityEnabled?: boolean
  /** 平台 JWKS 公钥端点（身份断言验签用），如 https://host/.well-known/jwks.json */
  jwksUrl?: string
}

export type ExportFileFormat = 'pdf' | 'html'

/* ============================ 工具函数 ============================ */

function escapeHtml(s: string): string {
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

export function formatDateTime(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

export function buildExportFileBaseName(opts: Pick<ExportOptions, 'docTitle' | 'generatedAt'>): string {
  const title = (opts.docTitle || 'API 接口文档').trim()
  const generatedAt = (opts.generatedAt || '').trim().replace(/[: ]/g, '-')
  const base = generatedAt ? `${title}-${generatedAt}` : title
  return base.replace(/[\\/:*?"<>|]+/g, '-').replace(/\s+/g, '-').replace(/^-+|-+$/g, '') || 'api-doc'
}

/** 脱敏外置对话链接里的 chatKey：.../#/communication/xxx → .../#/communication/{chatKey} */
function desensitizeChatUrl(url: string, on: boolean): string {
  if (!on || !url) return url
  return url.replace(/(#\/communication\/)[^/?#]+/, '$1{chatKey}')
}

/** 脱敏访问入口里的 agentCode：.../agui/run/xxx → .../agui/run/{agentCode} */
function desensitizeAccessUrl(url: string, on: boolean): string {
  if (!on || !url) return url
  return url.replace(/(\/agui\/run\/)[^/?#]+/, '$1{agentCode}')
}

/** CSS content 字符串转义（用于 @page margin box） */
function cssStr(s: string): string {
  return s.replace(/\\/g, '\\\\').replace(/"/g, '\\"')
}

function jsonScriptStr(s: string): string {
  return s.replace(/</g, '\\u003c').replace(/\u2028/g, '\\u2028').replace(/\u2029/g, '\\u2029')
}

function anchorPart(s: string): string {
  return String(s)
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9_-]+/g, '-')
    .replace(/^-+|-+$/g, '') || 'item'
}

function sectionAnchorId(section: ExportSection): string {
  return `section-${anchorPart(section.key)}`
}

function endpointAnchorId(section: ExportSection, ep: ExportEndpoint): string {
  return `endpoint-${anchorPart(`${section.key}-${ep.id}`)}`
}

/** 生成斜向平铺水印背景 data-uri */
function watermarkDataUri(text: string): string {
  const t = escapeHtml(text || '')
  const svg =
    `<svg xmlns="http://www.w3.org/2000/svg" width="340" height="220">` +
    `<text x="170" y="120" transform="rotate(-28 170 120)" text-anchor="middle" ` +
    `font-family="sans-serif" font-size="20" fill="rgba(20,30,60,0.05)">${t}</text></svg>`
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
}

/* ============================ HTML 片段 ============================ */

function renderParamsTable(params: ExportEndpointParam[]): string {
  if (!params || params.length === 0) return ''
  const rows = params
    .map(
      (p) =>
        `<tr>` +
        `<td class="param-name">${escapeHtml(p.name)}</td>` +
        `<td class="param-type">${escapeHtml(p.type)}</td>` +
        `<td>${p.required ? '<span class="param-required">Required</span>' : '<span class="param-optional">Optional</span>'}</td>` +
        `<td>${escapeHtml(p.desc)}</td>` +
        `</tr>`,
    )
    .join('')
  return (
    `<div class="detail-title">Parameters</div>` +
    `<table class="param-table"><thead><tr>` +
    `<th>参数名</th><th>类型</th><th>必填</th><th>说明</th>` +
    `</tr></thead><tbody>${rows}</tbody></table>`
  )
}

function renderCodeBlock(title: string, code: string): string {
  return `<div class="detail-title">${escapeHtml(title)}</div><pre class="code-block">${escapeHtml(code)}</pre>`
}

function renderEndpointCard(section: ExportSection, ep: ExportEndpoint): string {
  const method = escapeHtml(ep.method)
  const cls = ep.method.toLowerCase()
  const anchorId = escapeHtml(endpointAnchorId(section, ep))
  const parts: string[] = []
  if (ep.note) parts.push(`<div class="ep-note">${escapeHtml(ep.note)}</div>`)
  parts.push(renderParamsTable(ep.params))
  if (ep.bodyExample) parts.push(renderCodeBlock('Request Body', ep.bodyExample))
  if (ep.responseExample) parts.push(renderCodeBlock('Response', ep.responseExample))
  return (
    `<div class="ep-card">` +
    `<div class="ep-head">` +
    `<h2 id="${anchorId}" class="ep-heading"><span class="method-badge ${cls}">${method}</span><span class="ep-path">${escapeHtml(ep.path)}</span></h2>` +
    `<span class="ep-desc">${escapeHtml(ep.desc)}</span>` +
    `</div>` +
    `<div class="ep-body">${parts.join('')}</div>` +
    `</div>`
  )
}

/** 访问入口（agui run）的 Request Body 说明表（与前端页面一致） */
const AGUI_BODY_ROWS: Array<{ name: string; type: string; required: boolean; desc: string; indent: number }> = [
  { name: 'threadId', type: 'string', required: true, desc: '会话线程ID，用于标识一次完整的对话', indent: 0 },
  { name: 'runId', type: 'string', required: true, desc: '本次运行ID，系统自动生成的唯一标识', indent: 0 },
  { name: 'messages', type: 'array', required: true, desc: '消息列表，包含 id、role（user）、content 字段', indent: 0 },
  { name: 'id', type: 'string', required: true, desc: '消息ID', indent: 1 },
  { name: 'role', type: 'string', required: true, desc: '消息角色，可选值为 user', indent: 1 },
  { name: 'content', type: 'string', required: true, desc: '消息内容', indent: 1 },
  { name: 'forwardedProps', type: 'object', required: false, desc: '转发属性对象 memoryActive、planActive、fileIds、params 字段', indent: 0 },
  { name: 'memoryActive', type: 'boolean', required: false, desc: '是否启用记忆功能', indent: 1 },
  { name: 'planActive', type: 'boolean', required: false, desc: '是否启用计划功能', indent: 1 },
  { name: 'fileIds', type: 'string[]', required: false, desc: '文件ID列表，上传多模态文件后返回的ID', indent: 1 },
  { name: 'params', type: 'object', required: false, desc: '扩展参数键值对，在工具中可直接获取该对象', indent: 1 },
]

function renderAccessSection(section: ExportSection, opts: ExportOptions): string {
  const url = escapeHtml(desensitizeAccessUrl(opts.accessUrl, opts.desensitize))
  const rows = AGUI_BODY_ROWS.map(
    (r) =>
      `<tr>` +
      `<td class="param-name" style="padding-left:${12 + r.indent * 18}px">${escapeHtml(r.name)}</td>` +
      `<td class="param-type">${escapeHtml(r.type)}</td>` +
      `<td>${r.required ? '<span class="param-required">Required</span>' : '<span class="param-optional">Optional</span>'}</td>` +
      `<td>${escapeHtml(r.desc)}</td>` +
      `</tr>`,
  ).join('')
  return (
    `<section class="doc-section">` +
    `<h1 id="${escapeHtml(sectionAnchorId(section))}" class="doc-h2">${escapeHtml(section.title)}</h1>` +
    `<div class="info-box info">` +
    `<div class="box-title">智能体对话接口</div>` +
    `<div class="access-url"><span class="method-badge post">POST</span> <code>${url}</code></div>` +
    `<div class="box-hint">该接口为智能体的主要对话入口，支持流式和非流式响应。</div>` +
    `<div class="box-hint">若不在路径中指定智能体，也可调用 <code>POST /api/runtime/agui/run</code>（不带 agentCode），此时智能体由请求头 <code>X-Agent-Id</code> 或 <code>forwardedProps.agentId</code> 解析。</div>` +
    `</div>` +
    `<div class="detail-title">Request Body</div>` +
    `<table class="param-table"><thead><tr><th>参数名</th><th>类型</th><th>必填</th><th>说明</th></tr></thead><tbody>${rows}</tbody></table>` +
    renderCodeBlock('Request Example', opts.aguiBodyExample) +
    `</section>`
  )
}

function renderExternalSection(section: ExportSection, opts: ExportOptions): string {
  const url = escapeHtml(desensitizeChatUrl(opts.externalChatUrl, opts.desensitize)) || '（未生成）'
  return (
    `<section class="doc-section">` +
    `<h1 id="${escapeHtml(sectionAnchorId(section))}" class="doc-h2">${escapeHtml(section.title)}</h1>` +
    `<div class="info-box success">` +
    `<div class="box-title">外置对话入口</div>` +
    `<div class="access-url"><code>${url}</code></div>` +
    `<div class="box-hint">该链接可直接在外部浏览器中打开进行对话，无需登录即可使用。</div>` +
    `</div>` +
    `</section>`
  )
}

function renderEmbedSection(section: ExportSection, opts: ExportOptions): string {
  const raw = opts.externalChatUrl
  if (!raw) {
    return (
      `<section class="doc-section">` +
      `<h1 id="${escapeHtml(sectionAnchorId(section))}" class="doc-h2">${escapeHtml(section.title)}</h1>` +
      `<div class="info-box info"><div class="box-hint">该智能体尚未生成外置对话链接，无法展示嵌入代码。</div></div>` +
      `</section>`
    )
  }
  // embedUrl / scriptSrc / chatKey 不预先转义：最终由 renderCodeBlock 内部 escapeHtml 统一处理
  const embedUrl = desensitizeChatUrl(raw, opts.desensitize) + '?embed=1'
  const scriptSrc = raw.replace(/#\/.*$/, 'embed.js')
  const keyMatch = raw.match(/communication\/([^/?#]+)/)
  const chatKey = opts.desensitize ? '{chatKey}' : keyMatch ? keyMatch[1] : '{chatKey}'
  const iframeCode =
    `<iframe\n` +
    `  src="${embedUrl}"\n` +
    `  style="width:400px;height:800px;border:none;border-radius:12px"\n` +
    `></iframe>`
  const bubbleCode =
    `<script\n` +
    `  src="${scriptSrc}"\n` +
    `  data-chat-key="${chatKey}"\n` +
    `  defer\n` +
    `><\/script>`
  // 已启用嵌入身份时：气泡代码带 data-user-jwt 占位，并追加身份传递接入说明
  const identityEnabled = !!opts.embedIdentityEnabled
  // 方式三·整页链接：启用身份时带 ?userJwt= 占位（业务方后端现签，打开后即抹除）
  const fullPageUrl = desensitizeChatUrl(raw, opts.desensitize) +
    (identityEnabled ? '?userJwt=<你后端用 embedSecret 签发的 userJwt>' : '')
  const bubbleCodeFinal = identityEnabled
    ? bubbleCode.replace(
        `  data-chat-key="${chatKey}"\n`,
        `  data-chat-key="${chatKey}"\n  data-user-jwt="<你后端用 embedSecret 签发的 userJwt>"\n`)
    : bubbleCode
  const userJwtExample =
    `// Node（jsonwebtoken）：用户每次打开嵌入页时现签，有效期 5 分钟\n` +
    `const jwt = require('jsonwebtoken')\n` +
    `const userJwt = jwt.sign(\n` +
    `  { sub: String(user.id), name: user.displayName },\n` +
    `  EMBED_SECRET,  // ← 由平台方经安全渠道单独交付，只放服务端\n` +
    `  { algorithm: 'HS256', expiresIn: '5m' }\n` +
    `)`
  const identityBox = identityEnabled
    ? (
      `<div class="info-box warning">` +
      `<div class="box-title">嵌入用户身份传递（本智能体已启用）</div>` +
      `<div class="box-text">三步接入：① 向平台方索取 embedSecret（本文档不包含密钥，请经安全渠道单独获取）；` +
      `② 你的后端用它为当前登录用户签发短命 userJwt（示例如下）；` +
      `③ 把 userJwt 传给平台——方式 ①/② 嵌入代码带 data-user-jwt，方式 ③ 整页链接带 URL ?userJwt=（读后即抹除）。此后该用户的工具调用断言会携带 external_sub（你系统的用户 ID），` +
      `供你的 MCP/工具侧做用户级权限判定。</div>` +
      renderCodeBlock('JavaScript', userJwtExample) +
      `<div class="box-hint"><b>必须遵守：</b>用户登出或切换账号时调用 window.apboaEmbed.reset()，` +
      `否则下一个用户会沿用上一个用户的身份；userJwt 验签失败会直接拒绝会话（不会静默降级为匿名）；` +
      `不带 data-user-jwt 则为匿名访客（工具断言无 external_sub）。</div>` +
      `</div>` +
      `<div class="info-box info">` +
      `<div class="box-title">流程与信任架构</div>` +
      renderEmbedIdentityFlowHtml() +
      `</div>`
    )
    : ''
  return (
    `<section class="doc-section">` +
    `<h1 id="${escapeHtml(sectionAnchorId(section))}" class="doc-h2">${escapeHtml(section.title)}</h1>` +
    `<p style="font-size:13px;line-height:1.8;color:#546e7a;margin:6px 0 16px;">三种方式按需取舍：<b>① iframe / ② 悬浮气泡</b>嵌在你的网页里，身份随 <code>data-user-jwt</code>（postMessage）传，但受浏览器跨源限制<b>没有语音</b>；<b>③ 整页链接</b>作为独立页面打开，身份随 URL 传，<b>保留语音输入 / 播报</b>。要语音选 ③，要嵌进页面选 ①/②。</p>` +
    `<div class="info-box info">` +
    `<div class="box-title">方式一 · iframe 直接嵌入</div>` +
    `<div class="box-text">把智能体对话作为固定区域嵌入网页，始终可见；宽高可按容器调整。</div>` +
    renderCodeBlock('HTML', iframeCode) +
    `</div>` +
    `<div class="info-box success">` +
    `<div class="box-title">方式二 · 悬浮气泡</div>` +
    `<div class="box-text">在网站右下角生成悬浮按钮，点击弹出对话浮窗（内嵌上方 embed 页）。把下面一行放到页面 &lt;/body&gt; 之前即可。</div>` +
    renderCodeBlock('HTML', bubbleCodeFinal) +
    `<div class="box-hint">可选属性：data-base-url（默认自动推导）、data-title、data-color、data-position（left | right）、data-width、data-height（默认 400×800）。</div>` +
    `</div>` +
    `<div class="info-box info">` +
    `<div class="box-title">方式三 · 整页链接（唯一保留语音）</div>` +
    `<div class="box-text">作为独立页面（新标签 / 跳转）打开，而非塞进 iframe——唯一能同时用语音输入·播报和嵌入用户身份的方式（iframe / 悬浮受浏览器跨源限制，麦克风与语音播报被禁用）。启用身份时业务方后端把 userJwt 拼进链接，打开后凭证即从地址栏抹除（5 分钟过期 + 建议 HTTPS）。</div>` +
    renderCodeBlock('URL', fullPageUrl) +
    `</div>` +
    identityBox +
    `</section>`
  )
}

/**
 * 嵌入用户身份 · 流程图（页面内子块与导出文档共用，docs/identity-propagation-design.md §4bis）。
 * 纯内联样式 HTML：导出文档是自包含的（禁外部依赖，不能带 mermaid 运行时），
 * 页面内经 v-html 挂载同一份，双端渲染一致。
 * 受众：业务方接入者 + 本系统维护者——看懂流程流转与信任架构。
 */
export function renderEmbedIdentityFlowHtml(): string {
  // 四个角色的徽章配色（含深浅色安全的中性底）
  const ROLES = {
    biz: { label: '业务方后端', color: '#fa8c16' },
    browser: { label: '用户浏览器', color: '#1677ff' },
    apboa: { label: 'Apboa Next 平台', color: '#52c41a' },
    tool: { label: '业务方 MCP/工具', color: '#722ed1' },
  } as const
  const steps: Array<{ role: keyof typeof ROLES; text: string }> = [
    { role: 'biz', text: '用户在业务方系统登录。后端用 embedSecret（印章，只存服务端）给该用户现签一张 userJwt（通行证：sub=用户ID，5 分钟过期），渲染进页面 data-user-jwt' },
    { role: 'browser', text: '页面加载 embed.js，右下角出现气泡。用户点开 → iframe 加载对话页' },
    { role: 'browser', text: 'iframe 广播 ready，embed.js 经 postMessage 把 userJwt 一次性传入（targetOrigin=平台源，不进 URL / 浏览器历史）' },
    { role: 'apboa', text: '换取会话：用该 chatKey 的 embedSecret 验 userJwt 签名与有效期。通过 → 签发会话 token 并烙入 external_sub/external_iss；失败 → 拒绝（绝不静默降级为匿名）' },
    { role: 'browser', text: '用户对话。LLM 推理后决定调用某个工具' },
    { role: 'apboa', text: '每次工具调用现签一张身份断言（平台私钥 RS256，5 分钟）：sub / tenant_id / external_sub / aud / tool_name…，注入 MCP 请求 _meta（不经过 LLM，提示词注入改不到）' },
    { role: 'tool', text: '用平台 JWKS 公钥验签断言 → 校验 aud 是自己 + external_iss 是自己的 chatKey → 取 external_sub 查自己的权限表 → 放行或拒绝。权限判定全程在业务方，平台零参与' },
    { role: 'browser', text: '登出或切换账号：业务方页面调用 window.apboaEmbed.reset() 清平台会话（否则串号）；同浏览器换人时嵌入页也会按 userJwt.sub 变化自动重新换取身份' },
  ]
  const stepRows = steps.map((s, i) => {
    const role = ROLES[s.role]
    return (
      `<div style="display:flex;align-items:flex-start;gap:10px;padding:7px 0;border-bottom:1px dashed rgba(128,128,128,.25);">` +
      `<span style="flex:0 0 22px;height:22px;border-radius:50%;background:${role.color};color:#fff;font-size:12px;font-weight:600;display:flex;align-items:center;justify-content:center;">${i + 1}</span>` +
      `<span style="flex:0 0 108px;font-size:12px;font-weight:600;color:${role.color};padding-top:2px;">${escapeHtml(role.label)}</span>` +
      `<span style="flex:1;font-size:12.5px;line-height:1.7;">${escapeHtml(s.text)}</span>` +
      `</div>`
    )
  }).join('')

  // ===== 泳道时序图（SVG，微信支付文档风格：角色生命线 + 横向消息箭头）=====
  // 固定白底容器包裹，深浅色主题下都可读；文字全走 escapeHtml
  // 首泳道 x 需 ≥ note 半宽(118) + 编号圆半径(9)，否则步骤 1 的说明框编号圆会被画布左缘裁掉
  const LANES: Array<{ key: keyof typeof ROLES; x: number }> = [
    { key: 'biz', x: 150 },
    { key: 'browser', x: 395 },
    { key: 'apboa', x: 640 },
    { key: 'tool', x: 885 },
  ]
  const laneX = (k: keyof typeof ROLES) => LANES.find(l => l.key === k)!.x
  // 每行都带 step（1~8），与下方"流程流转（8 步）"列表严格同号；
  // 同一步骤对应多条线时共用同号，编号圆填色 = 该步骤在列表里的角色色
  type SeqRow =
    | { kind: 'msg'; step: number; from: keyof typeof ROLES; to: keyof typeof ROLES; text: string; dashed?: boolean }
    | { kind: 'note'; step: number; at: keyof typeof ROLES; lines: string[] }
  const seq: SeqRow[] = [
    { kind: 'msg', step: 1, from: 'browser', to: 'biz', text: '用户登录业务方系统，请求页面' },
    { kind: 'note', step: 1, at: 'biz', lines: ['用 embedSecret 给该用户', '现签 userJwt（5 分钟）'] },
    { kind: 'msg', step: 1, from: 'biz', to: 'browser', text: '返回页面：embed.js + data-user-jwt', dashed: true },
    { kind: 'note', step: 2, at: 'browser', lines: ['页面加载 embed.js 出现气泡', '用户点开，iframe 加载对话页'] },
    { kind: 'note', step: 3, at: 'browser', lines: ['iframe 广播 ready，embed.js 经', 'postMessage 传 userJwt（不进 URL）'] },
    { kind: 'msg', step: 4, from: 'browser', to: 'apboa', text: '换会话：chatKey + userJwt' },
    { kind: 'note', step: 4, at: 'apboa', lines: ['embedSecret 验签', '通过→烙 external_sub；失败→401'] },
    { kind: 'msg', step: 4, from: 'apboa', to: 'browser', text: '会话 token', dashed: true },
    { kind: 'msg', step: 5, from: 'browser', to: 'apboa', text: '用户发起对话' },
    { kind: 'note', step: 5, at: 'apboa', lines: ['LLM 推理', '决定调用某个工具'] },
    { kind: 'note', step: 6, at: 'apboa', lines: ['平台私钥现签身份断言（5 分钟）', '含 external_sub / aud / tool_name'] },
    { kind: 'msg', step: 6, from: 'apboa', to: 'tool', text: 'tools/call + _meta 身份断言' },
    { kind: 'note', step: 7, at: 'tool', lines: ['JWKS 验签 + 验 aud/external_iss', '按 external_sub 查自己权限表'] },
    { kind: 'msg', step: 7, from: 'tool', to: 'apboa', text: '业务数据 / 拒绝', dashed: true },
    { kind: 'msg', step: 7, from: 'apboa', to: 'browser', text: '流式回复用户', dashed: true },
    { kind: 'note', step: 8, at: 'browser', lines: ['登出/换账号：调 apboaEmbed.reset()', '同浏览器换人也会自动重换身份'] },
  ]
  const stepColor = (step: number) => ROLES[steps[step - 1]!.role].color
  const stepBadge = (cx: number, cy: number, step: number) =>
    `<circle cx="${cx}" cy="${cy}" r="9" fill="${stepColor(step)}"/>` +
    `<text x="${cx}" y="${cy + 4}" text-anchor="middle" font-size="11" font-weight="600" fill="#fff">${step}</text>`
  const ROW_H = 46
  const TOP = 78
  const svgH = TOP + seq.length * ROW_H + 24
  let seqBody = ''
  seq.forEach((row, i) => {
    const y = TOP + i * ROW_H
    if (row.kind === 'msg') {
      const x1 = laneX(row.from)
      const x2 = laneX(row.to)
      const dir = x2 > x1 ? 1 : -1
      const dash = row.dashed ? ` stroke-dasharray="6 4"` : ''
      seqBody +=
        `<line x1="${x1}" y1="${y}" x2="${x2 - dir * 8}" y2="${y}" stroke="#555" stroke-width="1.4"${dash} marker-end="url(#apb-arrow)"/>` +
        `<text x="${(x1 + x2) / 2}" y="${y - 7}" text-anchor="middle" font-size="12" fill="#333">${escapeHtml(row.text)}</text>` +
        stepBadge(x1 + dir * 16, y, row.step)
    } else {
      const cx = laneX(row.at)
      const w = 236
      seqBody +=
        `<rect x="${cx - w / 2}" y="${y - 15}" width="${w}" height="38" rx="6" fill="#fffbe6" stroke="#faad14" stroke-width="1"/>` +
        row.lines.map((ln, j) =>
          `<text x="${cx}" y="${y + j * 15}" text-anchor="middle" font-size="11" fill="#7a5c00">${escapeHtml(ln)}</text>`
        ).join('') +
        stepBadge(cx - w / 2, y - 15, row.step)
    }
  })
  const laneHeads = LANES.map(l => {
    const role = ROLES[l.key]
    return (
      `<line x1="${l.x}" y1="46" x2="${l.x}" y2="${svgH - 12}" stroke="#bbb" stroke-width="1" stroke-dasharray="4 4"/>` +
      `<rect x="${l.x - 76}" y="10" width="152" height="30" rx="6" fill="${role.color}"/>` +
      `<text x="${l.x}" y="30" text-anchor="middle" font-size="13" font-weight="600" fill="#fff">${escapeHtml(role.label)}</text>`
    )
  }).join('')
  const seqSvg =
    `<div style="background:#fff;border:1px solid rgba(128,128,128,.25);border-radius:8px;padding:8px;overflow-x:auto;">` +
    `<svg viewBox="0 0 1015 ${svgH}" xmlns="http://www.w3.org/2000/svg" style="width:100%;min-width:760px;display:block;">` +
    `<defs><marker id="apb-arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">` +
    `<path d="M 0 0 L 10 5 L 0 10 z" fill="#555"/></marker></defs>` +
    laneHeads + seqBody +
    `</svg></div>`
  const trustRows: Array<[string, string, string, string]> = [
    ['embedSecret（印章）', 'HMAC 对称密钥', '业务方后端 + 平台（chatKey 配置）', '业务方签 userJwt；平台验。泄漏只影响该业务方自己，轮换双活不瞬断'],
    ['平台签名密钥 + JWKS', 'RSA 非对称', '私钥仅平台；公钥公开（/.well-known/jwks.json）', '平台签工具调用断言；业务方 MCP/工具验。密钥轮换双 kid 并存，验签方无感'],
  ]
  const trustTable =
    `<table style="width:100%;border-collapse:collapse;font-size:12px;margin-top:8px;">` +
    `<thead><tr>` +
    ['密钥', '类型', '谁持有', '干什么用'].map(h => `<th style="text-align:left;padding:5px 8px;border:1px solid rgba(128,128,128,.3);">${h}</th>`).join('') +
    `</tr></thead><tbody>` +
    trustRows.map(cols =>
      `<tr>` + cols.map(c => `<td style="padding:5px 8px;border:1px solid rgba(128,128,128,.3);line-height:1.6;">${escapeHtml(c)}</td>`).join('') + `</tr>`
    ).join('') +
    `</tbody></table>`
  return (
    `<div>` +
    `<div style="font-weight:600;font-size:13px;margin-bottom:6px;">时序总览</div>` +
    seqSvg +
    `<div style="font-weight:600;font-size:13px;margin:14px 0 4px;">流程流转（8 步）</div>` +
    stepRows +
    `<div style="font-weight:600;font-size:13px;margin:12px 0 0;">两把钥匙 · 信任架构</div>` +
    trustTable +
    `<div style="font-size:12px;color:#8c8c8c;margin-top:8px;line-height:1.7;">` +
    `设计立场：平台是"可信邮差"——只证明"这话确实是业务方说的 / 这次调用确实是该用户发起的"，` +
    `不维护业务方的用户映射、不做用户级权限判定；数据面权限始终在业务方自己手里。</div>` +
    `</div>`
  )
}

/**
 * 身份断言（工具方验签）内容：受众是业务方的 MCP / 工具后端开发者，
 * docs/identity-integration-guide.md §1 的精简版。
 * 与嵌入流程图同法：纯内联样式，API 页面（v-html）与导出文档共用同一份。
 */
export function renderIdentityVerifyHtml(jwksUrl: string): string {
  const url = jwksUrl || '{平台地址}/.well-known/jwks.json'
  const box = (accent: string, inner: string) =>
    `<div style="border:1px solid rgba(128,128,128,.25);border-left:3px solid ${accent};border-radius:8px;padding:12px 14px;margin-bottom:12px;">${inner}</div>`
  const title = (t: string) => `<div style="font-weight:600;font-size:13px;margin-bottom:6px;">${escapeHtml(t)}</div>`
  const text = (t: string) => `<div style="font-size:12.5px;line-height:1.8;">${t}</div>`
  const hint = (t: string) => `<div style="font-size:12px;color:#8c8c8c;margin-top:6px;line-height:1.7;">${t}</div>`
  const claimsRows: Array<[string, string]> = [
    ['iss', '固定 apboa-platform（签发方标识）'],
    ['sub', '平台侧用户 ID；嵌入会话为会话级随机 ID，权限判定请勿依赖'],
    ['tenant_id / tenant_role', '租户与租户内角色'],
    ['agent_id / thread_id / tool_name', '审计溯源：哪个智能体、哪次会话、调的哪个工具'],
    ['aud', '目标系统标识（平台 MCP 配置的 audience）——必须校验是自己'],
    ['external_sub / external_iss / external_name', '嵌入用户身份：业务方声称的用户 ID、出处 chatKey、显示名。external_sub 仅在 external_iss 命名空间内有意义'],
    ['iat / exp / jti', '签发/过期时间（有效期 5 分钟）与唯一编号'],
  ]
  const claimsTable =
    `<table style="width:100%;border-collapse:collapse;font-size:12px;">` +
    `<thead><tr>` +
    ['字段', '说明'].map(h => `<th style="text-align:left;padding:5px 8px;border:1px solid rgba(128,128,128,.3);">${h}</th>`).join('') +
    `</tr></thead><tbody>` +
    claimsRows.map(([k, v]) =>
      `<tr><td style="padding:5px 8px;border:1px solid rgba(128,128,128,.3);white-space:nowrap;"><code>${escapeHtml(k)}</code></td>` +
      `<td style="padding:5px 8px;border:1px solid rgba(128,128,128,.3);line-height:1.6;">${escapeHtml(v)}</td></tr>`
    ).join('') +
    `</tbody></table>`
  const verifyExample =
    `import { createRemoteJWKSet, jwtVerify } from 'jose'\n\n` +
    `const JWKS = createRemoteJWKSet(new URL('${url}'))\n\n` +
    `async function verifyAssertion(jwt) {\n` +
    `  const { payload } = await jwtVerify(jwt, JWKS, {\n` +
    `    issuer: 'apboa-platform',\n` +
    `    audience: '<你在平台登记的 audience>',\n` +
    `    clockTolerance: 60,\n` +
    `  })\n` +
    `  if (payload.external_sub && payload.external_iss !== '<你的 chatKey>') {\n` +
    `    throw new Error('external identity from unknown issuer')\n` +
    `  }\n` +
    `  return payload\n` +
    `}`
  const codeBlock =
    `<pre style="background:rgba(128,128,128,.08);border-radius:6px;padding:10px 12px;font-size:12px;line-height:1.6;overflow-x:auto;margin:8px 0 0;">${escapeHtml(verifyExample)}</pre>`
  return (
    `<div>` +
    box('#1677ff',
      title('这是什么') +
      text(`平台替用户调用你的 MCP / 工具时，会随请求附一张平台私钥签名的短命 JWT（身份断言）：` +
        `MCP 在 tools/call 请求 _meta 的 <code>apboa.identityAssertion</code> 键；脚本工具按约定放请求头。` +
        `你用平台公钥验签后取出用户身份，自行做权限判定——权限表在你手里，平台零参与。`) +
      hint(`JWKS 公钥端点：<code>${escapeHtml(url)}</code>（启动时拉取缓存，建议每日刷新；平台轮换密钥时双 kid 并存，无需人工干预）。`)) +
    box('#52c41a', title('断言字段') + claimsTable) +
    box('#faad14',
      title('验签五步（缺一不可）') +
      text(`① 按断言 header 的 kid 从 JWKS 匹配公钥；② 验签名与过期（RS256，时钟容差 30~60 秒）；` +
        `③ 验 iss = apboa-platform；④ <b>必须验 aud 是你自己</b>（防止发给别家系统的断言被重放到你这里）；` +
        `⑤ 用到 external_sub 时<b>必须同时验 external_iss 是你的 chatKey</b>（别家的"用户42"不是你的"用户42"）。`) +
      codeBlock +
      hint(`未配置 audience 的 MCP 不会收到断言；没带断言的请求如何处置（拒绝 / 仅开放公开能力）由你自行决定。`)) +
    `</div>`
  )
}

/**
 * 身份断言导出区块：section 壳 + 共用内容（renderIdentityVerifyHtml）
 */
function renderIdentitySection(section: ExportSection, opts: ExportOptions): string {
  return (
    `<section class="doc-section">` +
    `<h1 id="${escapeHtml(sectionAnchorId(section))}" class="doc-h2">${escapeHtml(section.title)}</h1>` +
    renderIdentityVerifyHtml(opts.jwksUrl || '') +
    `</section>`
  )
}

function renderAuthSection(section: ExportSection): string {
  return (
    `<section class="doc-section">` +
    `<h1 id="${escapeHtml(sectionAnchorId(section))}" class="doc-h2">${escapeHtml(section.title)}</h1>` +
    `<div class="info-box warning">` +
    `<div class="box-title">API Key 鉴权</div>` +
    `<div class="box-text">所有接口请求需要在请求头中携带 API Key 进行身份验证：</div>` +
    `<pre class="code-block">Authorization: {API_KEY}</pre>` +
    `<div class="box-hint">API Key 可在系统设置 &gt; API Keys 中创建和管理。请妥善保管您的 API Key，不要在客户端代码中暴露。</div>` +
    `</div>` +
    `<div class="info-box success">` +
    `<div class="box-title">chatKey 免登鉴权</div>` +
    `<div class="box-text">顶部「外置对话链接」背后的免登方式：凭该智能体专属的 chatKey，外部用户无需登录即可直接对话。分享链接（/#/communication/{chatKey}）打开后由前端自动完成鉴权换取访问凭证。</div>` +
    `<div class="box-hint">chatKey 可刷新（刷新后旧链接失效）。免登凭证仅能访问带 @ChatKeyAccess 的接口（对话运行、会话管理、工作空间、附件、语音等对话协议相关接口）；未开放 chatKey 的接口须使用 API Key。</div>` +
    `</div>` +
    `</section>`
  )
}

function renderEndpointsSection(section: ExportSection): string {
  const cards = (section.items || []).map((ep) => renderEndpointCard(section, ep)).join('')
  return (
    `<section class="doc-section">` +
    `<h1 id="${escapeHtml(sectionAnchorId(section))}" class="doc-h2">${escapeHtml(section.title)}</h1>` +
    cards +
    `</section>`
  )
}

function renderToc(sections: ExportSection[]): string {
  const rows = sections.map((section) => {
    const sectionHref = `#${sectionAnchorId(section)}`
    const children = (section.items || []).map((ep) => {
      const endpointHref = `#${endpointAnchorId(section, ep)}`
      return (
        `<a class="toc-line toc-level-2" href="${escapeHtml(endpointHref)}">` +
        `<span class="toc-text"><span class="toc-method">${escapeHtml(ep.method)}</span>${escapeHtml(ep.path)}</span>` +
        `</a>`
      )
    }).join('')

    return (
      `<div class="toc-group">` +
      `<a class="toc-line toc-level-1" href="${escapeHtml(sectionHref)}">` +
      `<span class="toc-text">${escapeHtml(section.title)}</span>` +
      `</a>` +
      children +
      `</div>`
    )
  }).join('')

  return (
    `<nav class="toc" role="doc-toc">` +
    `<h1 class="toc-title">目录</h1>` +
    `<div class="toc-list">${rows}</div>` +
    `</nav>`
  )
}

function renderHtmlSidebar(sections: ExportSection[]): string {
  const rows = sections.map((section) => {
    const sectionHref = `#${sectionAnchorId(section)}`
    const children = (section.items || []).map((ep) => {
      const endpointHref = `#${endpointAnchorId(section, ep)}`
      return (
        `<a class="html-nav-link html-nav-link--endpoint" href="${escapeHtml(endpointHref)}">` +
        `<span class="html-nav-title">${escapeHtml(ep.desc)}</span>` +
        `<span class="html-nav-meta">` +
        `<span class="html-nav-method">${escapeHtml(ep.method)}</span>` +
        `<span class="html-nav-path">${escapeHtml(ep.path)}</span>` +
        `</span>` +
        `</a>`
      )
    }).join('')

    return (
      `<div class="html-nav-group">` +
      `<a class="html-nav-link html-nav-link--section" href="${escapeHtml(sectionHref)}">${escapeHtml(section.title)}</a>` +
      children +
      `</div>`
    )
  }).join('')

  return `<nav class="html-sidebar" aria-label="接口目录">${rows}</nav>`
}

function renderSection(section: ExportSection, opts: ExportOptions): string {
  switch (section.kind) {
    case 'external':
      return renderExternalSection(section, opts)
    case 'embed':
      return renderEmbedSection(section, opts)
    case 'access':
      return renderAccessSection(section, opts)
    case 'auth':
      return renderAuthSection(section)
    case 'identity':
      return renderIdentitySection(section, opts)
    case 'endpoints':
      return renderEndpointsSection(section)
    default:
      return ''
  }
}

function renderCover(opts: ExportOptions): string {
  return (
    `<div class="cover">` +
    `<div class="cover-inner">` +
    `<div class="cover-title">${escapeHtml(opts.docTitle)}</div>` +
    (opts.coverSubtitle ? `<div class="cover-sub">${escapeHtml(opts.coverSubtitle)}</div>` : '') +
    `<div class="cover-meta">` +
    (opts.agentLabel ? `<div><span>智能体</span>${escapeHtml(opts.agentLabel)}</div>` : '') +
    `<div><span>生成时间</span>${escapeHtml(opts.generatedAt)}</div>` +
    `</div>` +
    `</div>` +
    `</div>`
  )
}

export function buildExportHtml(sections: ExportSection[], opts: ExportOptions): string {
  const body = sections.map((s) => renderSection(s, opts)).join('')
  return `<div class="apidoc-export">${renderCover(opts)}${renderToc(sections)}${body}</div>`
}

function buildStandaloneHtmlCss(opts: ExportOptions): string {
  const watermark = watermarkDataUri(opts.watermarkText)
  return `
* { box-sizing: border-box; }
html { scroll-behavior: smooth; }
body {
  margin: 0;
  font-family: -apple-system, "PingFang SC", "Microsoft YaHei", "Segoe UI", sans-serif;
  color: #1f2328;
  background: #f6f8fa;
  font-size: 14px;
  line-height: 1.65;
}
a { color: inherit; }
code, pre { font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace; }
.html-shell {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
}
.html-sidebar {
  position: sticky;
  top: 0;
  height: 100vh;
  overflow: auto;
  padding: 20px 14px;
  background: #ffffff;
  border-right: 1px solid #d8dee4;
}
.html-nav-group { margin-bottom: 12px; }
.html-nav-link {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  width: 100%;
  min-width: 0;
  text-decoration: none;
  border-radius: 6px;
}
.html-nav-link:hover { background: #f6f8fa; }
.html-nav-link--section {
  padding: 8px 10px;
  color: #1f2328;
  font-weight: 700;
}
.html-nav-link--endpoint {
  display: block;
  padding: 7px 10px 7px 18px;
  color: #57606a;
  font-size: 12px;
}
.html-nav-title {
  display: block;
  margin-bottom: 2px;
  color: #1f2328;
  font-weight: 600;
  overflow-wrap: anywhere;
}
.html-nav-meta {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  min-width: 0;
}
.html-nav-method {
  flex: 0 0 42px;
  color: #4449d0;
  font-weight: 700;
}
.html-nav-path {
  min-width: 0;
  overflow-wrap: anywhere;
}
.html-main {
  min-width: 0;
  padding: 32px 40px 64px;
}
.html-header {
  max-width: 1180px;
  margin: 0 auto 24px;
  padding-bottom: 18px;
  border-bottom: 1px solid #d8dee4;
}
.html-title {
  margin: 0 0 8px;
  color: #111;
  font-size: 28px;
  line-height: 1.25;
}
.html-subtitle {
  color: #57606a;
  font-size: 15px;
}
.html-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 18px;
  margin-top: 14px;
  color: #6e7781;
  font-size: 12px;
}
.html-content {
  position: relative;
  max-width: 1180px;
  margin: 0 auto;
  padding: 8px 0;
}
.html-content::before {
  content: "";
  position: fixed;
  inset: 0;
  background-image: url("${watermark}");
  background-repeat: repeat;
  pointer-events: none;
  opacity: ${opts.watermarkText ? '1' : '0'};
}
.doc-section {
  position: relative;
  scroll-margin-top: 24px;
  margin-bottom: 32px;
}
.doc-h2 {
  margin: 0 0 16px;
  padding-bottom: 10px;
  border-bottom: 2px solid #d0d7de;
  color: #111;
  font-size: 22px;
  line-height: 1.35;
}
.ep-card {
  margin-bottom: 14px;
  overflow: hidden;
  border: 1px solid #d8dee4;
  border-radius: 8px;
  background: #fff;
}
.ep-head {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 16px;
  background: #f6f8fa;
  border-bottom: 1px solid #d8dee4;
}
.ep-heading {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
  margin: 0;
  scroll-margin-top: 18px;
  font-size: 14px;
  font-weight: 500;
}
.ep-path {
  min-width: 0;
  color: #1f2328;
  overflow-wrap: anywhere;
}
.ep-desc {
  flex-shrink: 0;
  max-width: 260px;
  color: #6e7781;
  font-size: 12px;
  line-height: 1.5;
}
.ep-body { padding: 6px 16px 16px; }
.ep-note { margin: 10px 0 4px; color: #57606a; }
.method-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 52px;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
  flex-shrink: 0;
}
.method-badge.get { background: #e8f5e9; color: #2e7d32; }
.method-badge.post { background: #e3f2fd; color: #1565c0; }
.method-badge.put { background: #fff3e0; color: #e65100; }
.method-badge.delete { background: #fce4ec; color: #c62828; }
.detail-title {
  margin: 14px 0 7px;
  color: #656d76;
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
}
.param-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.param-table th {
  padding: 8px 10px;
  text-align: left;
  color: #57606a;
  background: #f6f8fa;
  border: 1px solid #d8dee4;
}
.param-table td {
  padding: 8px 10px;
  vertical-align: top;
  border: 1px solid #d8dee4;
}
.param-name { color: #4449d0; font-size: 12px; }
.param-type { color: #57606a; font-size: 12px; }
.param-required { color: #d1242f; font-weight: 700; }
.param-optional { color: #8c959f; }
.code-block {
  overflow: auto;
  margin: 6px 0;
  padding: 12px 14px;
  color: #1f2328;
  background: #f6f8fa;
  border: 1px solid #d8dee4;
  border-radius: 6px;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-word;
}
.info-box {
  margin-bottom: 12px;
  padding: 14px 16px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid #d8dee4;
}
.info-box.info { background: #eef2ff; color: #303469; border-color: #c7d2fe; }
.info-box.success { background: #ecfdf3; color: #1b5e20; border-color: #a5d6a7; }
.info-box.warning { background: #fff8e1; color: #795548; border-color: #ffe082; }
.box-title { margin-bottom: 6px; font-weight: 700; }
.box-text { margin-bottom: 6px; }
.box-hint { margin-top: 6px; color: #546e7a; font-size: 12px; }
.access-url {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin: 4px 0;
}
.access-url code { overflow-wrap: anywhere; }
@media (max-width: 900px) {
  .html-shell { display: block; }
  .html-sidebar {
    position: static;
    height: auto;
    max-height: 42vh;
    border-right: 0;
    border-bottom: 1px solid #d8dee4;
  }
  .html-main { padding: 24px 18px 48px; }
  .ep-head { display: block; }
  .ep-desc { display: block; max-width: none; margin-top: 8px; }
}
@media print {
  body { background: #fff; }
  .html-shell { display: block; }
  .html-sidebar { display: none; }
  .html-main { padding: 0; }
  .html-content::before { display: none; }
}
`
}

export function buildStandaloneApiDocHtml(sections: ExportSection[], opts: ExportOptions): string {
  const body = sections.map((s) => renderSection(s, opts)).join('')
  const subtitle = opts.coverSubtitle ? `<div class="html-subtitle">${escapeHtml(opts.coverSubtitle)}</div>` : ''
  const agent = opts.agentLabel ? `<span>智能体：${escapeHtml(opts.agentLabel)}</span>` : ''
  const watermark = opts.watermarkText ? `<span>水印：${escapeHtml(opts.watermarkText)}</span>` : ''
  const css = buildStandaloneHtmlCss(opts)
  return (
    `<!doctype html>` +
    `<html lang="zh-CN">` +
    `<head>` +
    `<meta charset="utf-8">` +
    `<meta name="viewport" content="width=device-width, initial-scale=1">` +
    `<title>${escapeHtml(opts.docTitle)}</title>` +
    `<style>${css}</style>` +
    `</head>` +
    `<body>` +
    `<div class="html-shell">` +
    renderHtmlSidebar(sections) +
    `<main class="html-main">` +
    `<header class="html-header">` +
    `<h1 class="html-title">${escapeHtml(opts.docTitle)}</h1>` +
    subtitle +
    `<div class="html-meta">${agent}<span>生成时间：${escapeHtml(opts.generatedAt)}</span>${watermark}</div>` +
    `</header>` +
    `<div class="html-content">${body}</div>` +
    `</main>` +
    `</div>` +
    `<script type="application/json" id="apidoc-export-meta">${jsonScriptStr(JSON.stringify({ generatedAt: opts.generatedAt, sections: sections.length }))}</script>` +
    `</body>` +
    `</html>`
  )
}

/* ============================ Print CSS ============================ */

export function buildExportCss(opts: ExportOptions): string {
  const agent = opts.agentLabel || ''
  const watermark = watermarkDataUri(opts.watermarkText)
  return `
/* ---------- 页面与页眉页脚（Paged.js 解析 @page） ---------- */
@page {
  size: A4;
  margin: 18mm 15mm 16mm 15mm;
  @top-left { content: "${cssStr(opts.docTitle)}"; font-size: 8pt; color: #9aa0a6; }
  @top-right { content: "${cssStr(agent)}"; font-size: 8pt; color: #9aa0a6; }
  @bottom-left { content: "生成于 ${cssStr(opts.generatedAt)}"; font-size: 8pt; color: #9aa0a6; }
  @bottom-center { content: "第 " counter(page) " / " counter(pages) " 页"; font-size: 8pt; color: #9aa0a6; }
}
.cover { break-after: page; }
.toc { break-after: page; padding-top: 8mm; }
.toc-title {
  font-size: 22px; font-weight: 800; color: #111;
  margin: 0 0 18px; padding-bottom: 10px; border-bottom: 2px solid #d0d7de;
}
.toc-list { display: flex; flex-direction: column; gap: 8px; }
.toc-group { break-inside: avoid; page-break-inside: avoid; }
.toc-line {
  display: grid; grid-template-columns: minmax(0, 1fr) 14mm; gap: 10px;
  align-items: baseline; color: #1f2328; text-decoration: none;
  break-inside: avoid; page-break-inside: avoid;
}
.toc-line::after {
  content: target-counter(attr(href), page);
  color: #656d76; text-align: right; font-variant-numeric: tabular-nums;
}
.toc-text {
  min-width: 0; overflow-wrap: anywhere; word-break: break-word;
}
.toc-level-1 {
  font-size: 13px; font-weight: 700; padding: 4px 0 2px;
  border-bottom: 1px solid #e5e7eb;
}
.toc-level-2 {
  font-size: 11px; color: #57606a; padding: 2px 0 2px 10mm;
}
.toc-method {
  display: inline-block; min-width: 30px; margin-right: 6px;
  color: #4449d0; font-family: "SFMono-Regular", Consolas, monospace; font-weight: 700;
}

/* ---------- 每页水印 ---------- */
.pagedjs_page .pagedjs_area::before {
  content: "";
  position: absolute;
  inset: 0;
  background-image: url("${watermark}");
  background-repeat: repeat;
  z-index: 0;
  pointer-events: none;
}
.pagedjs_page .pagedjs_area > * { position: relative; z-index: 1; }

/* ---------- 正文排版 ---------- */
.apidoc-export {
  font-family: -apple-system, "PingFang SC", "Microsoft YaHei", "Segoe UI", sans-serif;
  color: #1f2328;
  font-size: 12px;
  line-height: 1.6;
}
.doc-section { break-before: page; }
.doc-section:first-of-type { break-before: auto; }
.doc-h2 {
  font-size: 16px; font-weight: 700; color: #111;
  margin: 0 0 14px; padding-bottom: 8px; border-bottom: 2px solid #e5e7eb;
}

/* 接口卡片：不被跨页截断 */
.ep-card {
  border: 1px solid #e5e7eb; border-radius: 8px;
  margin-bottom: 12px; overflow: hidden; break-inside: avoid;
}
.ep-head {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 14px; background: #fafbfc;
}
.ep-heading { display: flex; align-items: center; gap: 10px; margin: 0; flex: 1; min-width: 0; font-weight: 400; }
.ep-path { font-family: "SFMono-Regular", Consolas, Menlo, monospace; font-size: 12px; color: #1f2328; flex: 1; word-break: break-all; }
.ep-desc { font-size: 11px; color: #656d76; flex-shrink: 0; }
.ep-body { padding: 4px 14px 14px; }
.ep-note { font-size: 12px; color: #57606a; margin: 10px 0 4px; }

.method-badge {
  display: inline-flex; align-items: center; justify-content: center;
  min-width: 50px; padding: 2px 8px; border-radius: 4px;
  font-size: 10px; font-weight: 700; letter-spacing: 0.5px; text-transform: uppercase; flex-shrink: 0;
}
.method-badge.get { background: #e8f5e9; color: #2e7d32; }
.method-badge.post { background: #e3f2fd; color: #1565c0; }
.method-badge.put { background: #fff3e0; color: #e65100; }
.method-badge.delete { background: #fce4ec; color: #c62828; }

.detail-title {
  font-size: 11px; font-weight: 700; color: #656d76;
  text-transform: uppercase; letter-spacing: 0.5px; margin: 12px 0 6px;
}

.param-table { width: 100%; border-collapse: collapse; font-size: 12px; break-inside: avoid; }
.param-table th {
  text-align: left; padding: 6px 10px; background: #f6f8fa;
  color: #656d76; font-weight: 600; font-size: 11px; border: 1px solid #e5e7eb;
}
.param-table td { padding: 6px 10px; border: 1px solid #eaecef; color: #1f2328; vertical-align: top; }
.param-name { font-family: "SFMono-Regular", Consolas, monospace; font-size: 11px; color: #4449d0; }
.param-type { font-size: 11px; color: #656d76; }
.param-required { font-size: 10px; color: #d1242f; font-weight: 600; }
.param-optional { font-size: 10px; color: #999; }

/* 代码块：打印转浅色 + 自动换行（避免横向截断） */
.code-block {
  background: #f6f8fa; color: #1f2328;
  border: 1px solid #e1e4e8; border-radius: 6px;
  padding: 12px 14px; margin: 6px 0;
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
  font-size: 11px; line-height: 1.55;
  white-space: pre-wrap; word-break: break-word; break-inside: avoid;
}

.info-box { padding: 12px 16px; border-radius: 8px; margin-bottom: 12px; font-size: 12px; line-height: 1.6; break-inside: avoid; }
.info-box.info { background: #e8eaf6; color: #303469; border: 1px solid #c5cae9; }
.info-box.success { background: #e8f5e9; color: #1b5e20; border: 1px solid #a5d6a7; }
.info-box.warning { background: #fff8e1; color: #795548; border: 1px solid #ffe082; }
.box-title { font-weight: 700; margin-bottom: 6px; }
.box-text { margin-bottom: 6px; }
.box-hint { font-size: 11px; color: #546e7a; margin-top: 6px; }
.access-url { margin: 4px 0; display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.access-url code { font-size: 12px; word-break: break-all; }
code { font-family: "SFMono-Regular", Consolas, monospace; }

/* ---------- 封面 ---------- */
.cover {
  height: 297mm; box-sizing: border-box;
  display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #1f2a53 0%, #3949ab 100%);
  color: #fff;
}
.cover-inner { text-align: center; padding: 40px; }
.cover-title { font-size: 34px; font-weight: 800; margin-bottom: 12px; }
.cover-sub { font-size: 16px; opacity: 0.85; margin-bottom: 40px; }
.cover-meta { font-size: 14px; line-height: 2.1; opacity: 0.92; }
.cover-meta span { display: inline-block; width: 90px; opacity: 0.7; }
`
}

/* ============================ 渲染入口 ============================ */

/**
 * 将接口文档渲染成分页 PDF 预览（Paged.js），写入指定容器。
 * 调用方负责在容器可见后触发 window.print()。
 */
export async function renderApiDocPdf(
  container: HTMLElement,
  sections: ExportSection[],
  opts: ExportOptions,
): Promise<number> {
  container.innerHTML = ''
  const html = buildExportHtml(sections, opts)
  const css = buildExportCss(opts)
  const previewer = new Previewer()
  const flow = await previewer.preview(html, [{ _: css }], container)
  return flow?.total ?? 0
}
