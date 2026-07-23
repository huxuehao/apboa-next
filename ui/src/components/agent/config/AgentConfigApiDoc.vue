/**
 * 智能体配置-访问 API 文档子组件
 *
 * ⚠️ 维护提示：本组件的接口清单（endpoints / workspaceEndpoints / aguiEndpoints / voiceEndpoints
 * 四个数组以及"访问入口""鉴权方式"区块）为**前端手写硬编码**，后端不提供接口文档数据源。
 * 当以下后端 Controller 增删接口或改动 URL/参数/响应时，务必同步本文件：
 *   - AgentChatKeyController（runner-console）  → endpoints 中的 get-chat-key / get-agent-id-by-chat-key
 *   - ChatSessionController（runner-console）   → endpoints（会话管理接口）
 *   - AttachController（runner-console）        → endpoints 中的 upload/download/delete-file（多模态附件）
 *   - WorkspaceController（runner-runtime）     → workspaceEndpoints（工作空间接口）
 *   - AguiRestController（runner-runtime）      → "访问入口"区块 + aguiEndpoints（对话运行控制）
 *   - SubAgentConfirmEndPoint（runner-runtime） → aguiEndpoints 中的 agui-subagent-*（子智能体 HITL）
 *   - AsrController / TtsController（runtime）  → voiceEndpoints（语音接口）
 * 收录范围：带 @SkAccess / @ChatKeyAccess（支持对外 API Key / chatKey 鉴权）**且**属于外置对话
 * 协议的接口——"带注解"是必要非充分条件。故意不收录：ToolEndPoint#doTool（虽带注解但可绕过对话
 * 直接执行工具，存在过度暴露风险，勿加入）、AgentDefinition/Account 的只读元数据接口（非对话协议）。
 * 后端 @RequestMapping 不含 /api 前缀，/api 由网关统一添加，故文档路径均带 /api。
 *
 * @component
 */
<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { CopyOutlined, CheckOutlined, DownOutlined, RightOutlined, KeyOutlined, LinkOutlined, ThunderboltOutlined, SyncOutlined, GlobalOutlined, CodeOutlined, FolderOpenOutlined, SoundOutlined, FilePdfOutlined, PrinterOutlined, CloseOutlined, DownloadOutlined } from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import { getChatKey } from '@/api/agentChatKey'
import { renderApiDocPdf, buildStandaloneApiDocHtml, buildExportFileBaseName, formatDateTime, type ExportSection, type ExportEndpoint, type ExportFileFormat, type ExportOptions } from '@/utils/apiDocExport'

const props = defineProps<{
  agentId?: string | number
  agentCode: string
}>()

/**
 * 外置对话链接的chatKey
 */
const chatKey = ref<string>('')
const chatKeyLoading = ref(false)

/**
 * 外置对话链接
 */
const externalChatUrl = computed(() => {
  if (!chatKey.value) return ''
  const loc = window.location
  return `${loc.protocol}//${loc.host}/#/communication/${chatKey.value}`
})

/**
 * 加载chatKey
 */
async function loadChatKey(refresh: boolean = false) {
  if (!props.agentId) return

  chatKeyLoading.value = true
  try {
    const res = await getChatKey(props.agentId, refresh)
    if (res.data.data) {
      chatKey.value = res.data.data
    }
  } catch (error) {
    console.error('获取chatKey失败:', error)
    message.error('获取对话链接失败')
  } finally {
    chatKeyLoading.value = false
  }
}

/**
 * 刷新chatKey（带确认提示）
 */
function handleRefreshChatKey() {
  Modal.confirm({
    title: '刷新确认',
    content: '刷新后，之前的对话链接将失效，已分享的链接将无法继续访问。确定要刷新吗？',
    okText: '确定刷新',
    cancelText: '取消',
    okButtonProps: { danger: true },
    onOk: () => {
      loadChatKey(true)
    }
  })
}

/**
 * 监听agentId变化，自动加载chatKey
 */
watch(
  () => props.agentId,
  (newVal) => {
    if (newVal) {
      loadChatKey()
    }
  },
  { immediate: true }
)

/**
 * 访问路径
 */
const accessUrl = computed(() => {
  const loc = window.location
  return `${loc.protocol}//${loc.host}/api/runtime/agui/run/${props.agentCode}`
})

/**
 * 网站嵌入 · 方式一 iframe 直接嵌入代码
 */
const embedIframeCode = computed(() => {
  if (!externalChatUrl.value) return ''
  return `<iframe\n  src="${externalChatUrl.value}?embed=1"\n  style="width:400px;height:800px;border:none;border-radius:12px"\n></iframe>`
})

/**
 * 网站嵌入 · 方式二 悬浮气泡引入代码
 */
const embedBubbleCode = computed(() => {
  if (!externalChatUrl.value) return ''
  const scriptSrc = externalChatUrl.value.replace(/#\/.*$/, 'embed.js')
  return `<script\n  src="${scriptSrc}"\n  data-chat-key="${chatKey.value || '{chatKey}'}"\n  defer\n><\/script>`
})

/**
 * 复制到剪贴板
 */
const copiedKey = ref('')
async function copyToClipboard(text: string, key: string) {
  try {
    await navigator.clipboard.writeText(text)
    copiedKey.value = key
    message.success('已复制')
    setTimeout(() => { copiedKey.value = '' }, 2000)
  } catch {
    message.error('复制失败')
  }
}

/**
 * 展开/收起的接口
 */
const expandedEndpoints = ref<Set<string>>(new Set())
function toggleEndpoint(id: string) {
  if (expandedEndpoints.value.has(id)) {
    expandedEndpoints.value.delete(id)
  } else {
    expandedEndpoints.value.add(id)
  }
}

/**
 * 智能体对话接口 Request Body 折叠状态
 */
const aguiBodyExpanded = ref(false)

/**
 * 智能体对话接口 Request Example 折叠状态
 */
const aguiExampleExpanded = ref(false)

/**
 * Request Body 中 messages 子属性折叠状态
 */
const messagesExpanded = ref(false)

/**
 * Request Body 中 forwardedProps 子属性折叠状态
 */
const forwardedPropsExpanded = ref(false)

/**
 * 智能体对话接口请求体示例
 */
const aguiBodyExample = `{
  "threadId": "2038965802636013570",
  "runId": "run_1775396544170_0c84jdg37",
  "messages": [
    {
      "id": "undefined",
      "role": "user",
      "content": "你好"
    }
  ],
  "forwardedProps": {
    "memoryActive": false,
    "planActive": false,
    "fileIds": [],
    "params": {
      "reqToken": "xxxxx"
    }
  }
}`

/**
 * 接口定义
 */
const endpoints = [
  {
    id: 'get-chat-key',
    method: 'GET',
    path: '/api/agent/chat-key/{agentId}?refresh=false',
    desc: '获取外置对话 chatKey',
    note: '获取（或刷新）智能体的外置对话 chatKey——即"外置对话链接"背后的免登凭证。refresh=true 会生成新 key 并使旧链接失效。',
    params: [
      { name: 'agentId', type: 'Long (路径参数)', required: true, desc: '智能体ID' },
      { name: 'refresh', type: 'Boolean (查询参数)', required: true, desc: '是否刷新：true 生成新 key（旧链接失效），false 返回现有 key' }
    ],
    bodyExample: null,
    responseExample: '{\n  "code": 200,\n  "success": true,\n  "data": "eyJhZ2VudCI6..."\n}'
  },
  {
    id: 'get-agent-id-by-chat-key',
    method: 'GET',
    path: '/api/agent/chat-key/{chatKey}/get-agent-id',
    desc: '由 chatKey 反解智能体ID',
    note: '外置对话页（/#/communication/{chatKey}）用 chatKey 反查对应的智能体ID。',
    params: [
      { name: 'chatKey', type: 'string (路径参数)', required: true, desc: '外置对话 chatKey' }
    ],
    bodyExample: null,
    responseExample: '{\n  "code": 200,\n  "success": true,\n  "data": "123456"\n}'
  },
  {
    id: 'create-session',
    method: 'POST',
    path: '/api/agent/chat/session',
    desc: '创建新会话',
    note: '创建一个新的对话会话，系统会自动插入根消息并设置 current_message_id。',
    params: [
      { name: 'agentId', type: 'Long', required: true, desc: '智能体ID（大数值建议以字符串传递，避免精度丢失）' },
      { name: 'title', type: 'string', required: false, desc: '会话标题，默认"新对话"' },
      { name: 'initWorkspace', type: 'Boolean', required: false, desc: '是否初始化工作区' }
    ],
    bodyExample: '{\n  "agentId": "123456",\n  "title": "测试对话",\n  "initWorkspace": false\n}',
    responseExample: '{\n  "code": 200,\n  "success": true,\n  "data": {\n    "id": "789",\n    "userId": "1",\n    "agentId": "123456",\n    "title": "测试对话",\n    "isPinned": false\n  }\n}'
  },
  {
    id: 'append-message',
    method: 'POST',
    path: '/api/agent/chat/session/{sessionId}/message',
    desc: '追加消息',
    note: '在当前对话的 current_message_id 后追加新消息，并更新游标。',
    params: [
      { name: 'sessionId', type: 'Long (路径参数)', required: true, desc: '会话ID' },
      { name: 'role', type: 'string', required: true, desc: '消息角色：user / assistant / system / tool' },
      { name: 'content', type: 'string', required: true, desc: '消息内容' }
    ],
    bodyExample: '{\n  "role": "user",\n  "content": "你好，请帮我分析一下数据"\n}',
    responseExample: '{\n  "code": 200,\n  "success": true,\n  "data": {\n    "id": "10",\n    "sessionId": "789",\n    "role": "user",\n    "content": "你好，请帮我分析一下数据",\n    "depth": 1\n  }\n}'
  },
  {
    id: 'regenerate',
    method: 'POST',
    path: '/api/agent/chat/session/{sessionId}/regenerate',
    desc: '重新生成（新分支）',
    note: '以当前消息为父节点创建新分支消息，适用于重新生成回复的场景。',
    params: [
      { name: 'sessionId', type: 'Long (路径参数)', required: true, desc: '会话ID' },
      { name: 'role', type: 'string', required: true, desc: '消息角色' },
      { name: 'content', type: 'string', required: true, desc: '重新生成的内容' }
    ],
    bodyExample: '{\n  "role": "assistant",\n  "content": "这是重新生成的回复"\n}',
    responseExample: null
  },
  {
    id: 'switch-branch',
    method: 'PUT',
    path: '/api/agent/chat/session/{sessionId}/current?messageId=xxx',
    desc: '切换历史分支',
    note: '仅更新 current_message_id 指针，切换到历史对话分支。不会创建新消息。',
    params: [
      { name: 'sessionId', type: 'Long (路径参数)', required: true, desc: '会话ID' },
      { name: 'messageId', type: 'Integer (查询参数)', required: true, desc: '目标消息ID' }
    ],
    bodyExample: null,
    responseExample: null
  },
  {
    id: 'current-messages',
    method: 'GET',
    path: '/api/agent/chat/session/{sessionId}/messages/current',
    desc: '获取当前完整对话',
    note: '根据 current_message_id 回溯路径，返回完整的消息链，按深度升序排列。',
    params: [
      { name: 'sessionId', type: 'Long (路径参数)', required: true, desc: '会话ID' }
    ],
    bodyExample: null,
    responseExample: '{\n  "code": 200,\n  "success": true,\n  "data": [\n    { "id": "1", "role": "system", "content": "", "depth": 0 },\n    { "id": "2", "role": "user", "content": "你好", "depth": 1 },\n    { "id": "3", "role": "assistant", "content": "你好！", "depth": 2 }\n  ]\n}'
  },
  {
    id: 'messages-paged',
    method: 'GET',
    path: '/api/agent/chat/session/{sessionId}/messages/paged',
    desc: '分页加载历史消息',
    note: '滚动加载当前对话的历史消息。首次不传 beforeDepth 返回最新 50 条；加载更多时传入上次返回的 nextBeforeDepth，获取更早的一批。',
    params: [
      { name: 'sessionId', type: 'Long (路径参数)', required: true, desc: '会话ID' },
      { name: 'beforeDepth', type: 'Integer (查询参数)', required: false, desc: '游标：加载此 depth 之前的更早消息，首次不传' },
      { name: 'size', type: 'Integer (查询参数)', required: false, desc: '每页条数，默认 50' }
    ],
    bodyExample: null,
    responseExample: '{\n  "code": 200,\n  "success": true,\n  "data": {\n    "messages": [\n      { "id": 2, "role": "user", "content": "你好", "depth": 1 }\n    ],\n    "hasMore": true,\n    "nextBeforeDepth": 1\n  }\n}'
  },
  {
    id: 'update-current-message-content',
    method: 'PUT',
    path: '/api/agent/chat/session/{sessionId}/current-message/content',
    desc: '编辑当前消息内容',
    note: '修改当前消息（current_message_id 指向的消息）的内容。后端通过会话游标自动定位，无需传 messageId。',
    params: [
      { name: 'sessionId', type: 'Long (路径参数)', required: true, desc: '会话ID' }
    ],
    bodyExample: '{\n  "content": "修改后的消息内容"\n}',
    responseExample: null
  },
  {
    id: 'list-sessions',
    method: 'GET',
    path: '/api/agent/chat/session/list',
    desc: '会话列表',
    note: '获取当前用户的会话列表，可按 agentId 筛选，按置顶和更新时间倒序排列。',
    params: [
      { name: 'agentId', type: 'Long (查询参数)', required: false, desc: '按智能体ID筛选' }
    ],
    bodyExample: null,
    responseExample: null
  },
  {
    id: 'page-sessions',
    method: 'GET',
    path: '/api/agent/chat/session/page',
    desc: '分页查询会话',
    note: '支持分页查询，可按 isPinned 筛选置顶会话。',
    params: [
      { name: 'agentId', type: 'Long (查询参数)', required: false, desc: '按智能体ID筛选' },
      { name: 'isPinned', type: 'Boolean (查询参数)', required: false, desc: '按置顶状态筛选' },
      { name: 'current', type: 'Integer (查询参数)', required: false, desc: '页码' },
      { name: 'size', type: 'Integer (查询参数)', required: false, desc: '每页条数' }
    ],
    bodyExample: null,
    responseExample: null
  },
  {
    id: 'session-detail',
    method: 'GET',
    path: '/api/agent/chat/session/{id}',
    desc: '会话详情',
    note: '获取指定会话的详细信息。',
    params: [
      { name: 'id', type: 'Long (路径参数)', required: true, desc: '会话ID' }
    ],
    bodyExample: null,
    responseExample: null
  },
  {
    id: 'pin-session',
    method: 'PUT',
    path: '/api/agent/chat/session/{id}/pin',
    desc: '置顶会话',
    note: '将指定会话设为置顶状态。',
    params: [
      { name: 'id', type: 'Long (路径参数)', required: true, desc: '会话ID' }
    ],
    bodyExample: null,
    responseExample: null
  },
  {
    id: 'unpin-session',
    method: 'PUT',
    path: '/api/agent/chat/session/{id}/unpin',
    desc: '取消置顶会话',
    note: '取消指定会话的置顶状态。',
    params: [
      { name: 'id', type: 'Long (路径参数)', required: true, desc: '会话ID' }
    ],
    bodyExample: null,
    responseExample: null
  },
  {
    id: 'update-title',
    method: 'PUT',
    path: '/api/agent/chat/session/{id}/title?title=xxx',
    desc: '更新会话标题',
    note: '修改指定会话的标题。',
    params: [
      { name: 'id', type: 'Long (路径参数)', required: true, desc: '会话ID' },
      { name: 'title', type: 'String (查询参数)', required: true, desc: '新标题' }
    ],
    bodyExample: null,
    responseExample: null
  },
  {
    id: 'get-confirm-mode',
    method: 'GET',
    path: '/api/agent/chat/session/{sessionId}/confirm-mode',
    desc: '查询工具授权模式',
    note: 'HITL 人工确认模式。无记录默认 MANUAL（逐步确认）。返回值为模式名称字符串。',
    params: [
      { name: 'sessionId', type: 'Long (路径参数)', required: true, desc: '会话ID' }
    ],
    bodyExample: null,
    responseExample: '{\n  "code": 200,\n  "success": true,\n  "data": "MANUAL"\n}'
  },
  {
    id: 'set-confirm-mode',
    method: 'PUT',
    path: '/api/agent/chat/session/{sessionId}/confirm-mode?mode=xxx',
    desc: '设置工具授权模式',
    note: 'AUTO_APPROVE 一键授权 / MANUAL 逐步确认 / AUTO_REJECT 拒绝授权。设置后对后续工具调用即时生效。',
    params: [
      { name: 'sessionId', type: 'Long (路径参数)', required: true, desc: '会话ID' },
      { name: 'mode', type: 'String (查询参数)', required: true, desc: '授权模式：AUTO_APPROVE / MANUAL / AUTO_REJECT' }
    ],
    bodyExample: null,
    responseExample: null
  },
  {
    id: 'get-thinking-mode',
    method: 'GET',
    path: '/api/agent/chat/session/{sessionId}/thinking-mode',
    desc: '查询思考模式',
    note: '查询会话思考模式是否开启（会话覆盖值优先，无则默认开）。仅对支持思考开关的模型有实际意义。',
    params: [
      { name: 'sessionId', type: 'Long (路径参数)', required: true, desc: '会话ID' }
    ],
    bodyExample: null,
    responseExample: '{\n  "code": 200,\n  "success": true,\n  "data": true\n}'
  },
  {
    id: 'set-thinking-mode',
    method: 'PUT',
    path: '/api/agent/chat/session/{sessionId}/thinking-mode?enabled=true',
    desc: '设置思考模式',
    note: '开启/关闭会话思考模式，写入会话覆盖值，下一条消息生效。',
    params: [
      { name: 'sessionId', type: 'Long (路径参数)', required: true, desc: '会话ID' },
      { name: 'enabled', type: 'Boolean (查询参数)', required: true, desc: '是否开启思考' }
    ],
    bodyExample: null,
    responseExample: null
  },
  {
    id: 'delete-session',
    method: 'DELETE',
    path: '/api/agent/chat/session/{id}',
    desc: '删除会话',
    note: '物理删除会话及其所有消息，操作不可逆。',
    params: [
      { name: 'id', type: 'Long (路径参数)', required: true, desc: '会话ID' }
    ],
    bodyExample: null,
    responseExample: null
  },
  {
    id: 'upload-file',
    method: 'POST',
    path: '/api/attach/upload',
    desc: '上传多模态文件',
    note: '文件类型仅支持图片、音频、视频，大小受系统参数限制（默认 5MB）',
    params: [
      { name: 'file', type: 'File (表单字段)', required: true, desc: '上传的文件' }
    ],
    bodyExample: null,
    responseExample: null
  },
  {
    id: 'download-file',
    method: 'GET',
    path: '/api/attach/download/{id}',
    desc: '下载多模态文件',
    note: '按附件ID下载文件，返回文件二进制流（响应头 Content-Disposition 附带原始文件名）。',
    params: [
      { name: 'id', type: 'Long (路径参数)', required: true, desc: '附件ID' }
    ],
    bodyExample: null,
    responseExample: null
  },
  {
    id: 'delete-file',
    method: 'POST',
    path: '/api/attach/delete',
    desc: '删除多模态文件',
    note: '批量删除附件，请求体为附件ID数组。注意为 POST 方法。',
    params: [
      { name: 'body', type: 'Long[] (请求体)', required: true, desc: '要删除的附件ID列表' }
    ],
    bodyExample: '[\n  "123456",\n  "123457"\n]',
    responseExample: '{\n  "code": 200,\n  "success": true,\n  "data": true\n}'
  }
]

/**
 * 工作空间接口定义
 */
const workspaceEndpoints = [
  {
    id: 'ws-upload',
    method: 'POST',
    path: '/api/runtime/workspace/upload',
    desc: '上传单个文件',
    note: '上传单个文件到工作空间，使用 multipart/form-data 格式提交。',
    params: [
      { name: 'sessionId', type: 'string (表单字段)', required: true, desc: '会话ID' },
      { name: 'file', type: 'File (表单字段)', required: true, desc: '上传的文件' }
    ],
    bodyExample: null,
    responseExample: null
  },
  {
    id: 'ws-upload-batch',
    method: 'POST',
    path: '/api/runtime/workspace/upload/batch',
    desc: '批量上传文件',
    note: '上传多个文件到工作空间，使用 multipart/form-data 格式提交。',
    params: [
      { name: 'sessionId', type: 'string (表单字段)', required: true, desc: '会话ID' },
      { name: 'files', type: 'File[] (表单字段)', required: true, desc: '上传的文件列表' }
    ],
    bodyExample: null,
    responseExample: null
  },
  {
    id: 'ws-upload-archive',
    method: 'POST',
    path: '/api/runtime/workspace/upload/archive',
    desc: '上传压缩包并解压',
    note: '上传压缩包文件到工作空间，系统会自动解压到工作空间目录中。',
    params: [
      { name: 'sessionId', type: 'string (表单字段)', required: true, desc: '会话ID' },
      { name: 'file', type: 'File (表单字段)', required: true, desc: '上传的压缩包文件' }
    ],
    bodyExample: null,
    responseExample: null
  },
  {
    id: 'ws-list-files',
    method: 'GET',
    path: '/api/runtime/workspace/files',
    desc: '获取文件树',
    note: '获取工作空间的文件树结构，返回树形的文件节点列表。',
    params: [
      { name: 'sessionId', type: 'string (查询参数)', required: true, desc: '会话ID' }
    ],
    bodyExample: null,
    responseExample: null
  },
  {
    id: 'ws-download',
    method: 'GET',
    path: '/api/runtime/workspace/download',
    desc: '下载单个文件',
    note: '下载工作空间中的指定文件，返回文件流。',
    params: [
      { name: 'sessionId', type: 'string (查询参数)', required: true, desc: '会话ID' },
      { name: 'fileName', type: 'string (查询参数)', required: true, desc: '文件名' }
    ],
    bodyExample: null,
    responseExample: null
  },
  {
    id: 'ws-download-batch',
    method: 'POST',
    path: '/api/runtime/workspace/download/batch',
    desc: '批量下载文件',
    note: '将指定的多个文件打包成 ZIP 后下载，请求体为文件路径数组。',
    params: [
      { name: 'sessionId', type: 'string (查询参数)', required: true, desc: '会话ID' },
      { name: 'body', type: 'string[] (请求体)', required: true, desc: '要下载的文件路径列表' }
    ],
    bodyExample: '[\n  "src/main.java",\n  "config/application.yml"\n]',
    responseExample: null
  },
  {
    id: 'ws-download-all',
    method: 'GET',
    path: '/api/runtime/workspace/download/all',
    desc: '下载整个工作空间',
    note: '将工作空间中的所有文件打包成 ZIP 后下载。',
    params: [
      { name: 'sessionId', type: 'string (查询参数)', required: true, desc: '会话ID' }
    ],
    bodyExample: null,
    responseExample: null
  },
  {
    id: 'ws-delete-file',
    method: 'DELETE',
    path: '/api/runtime/workspace/file',
    desc: '删除单个文件',
    note: '删除工作空间中指定的文件，操作不可逆。',
    params: [
      { name: 'sessionId', type: 'string (查询参数)', required: true, desc: '会话ID' },
      { name: 'filePath', type: 'string (查询参数)', required: true, desc: '文件路径' }
    ],
    bodyExample: null,
    responseExample: null
  },
  {
    id: 'ws-clear',
    method: 'DELETE',
    path: '/api/runtime/workspace/clear',
    desc: '清空工作空间',
    note: '清空工作空间下的所有文件，操作不可逆。',
    params: [
      { name: 'sessionId', type: 'string (查询参数)', required: true, desc: '会话ID' }
    ],
    bodyExample: null,
    responseExample: null
  },
  {
    id: 'ws-capacity',
    method: 'GET',
    path: '/api/runtime/workspace/capacity',
    desc: '获取工作空间容量',
    note: '返回工作空间已用/上限容量（字节与可读格式）及使用百分比。',
    params: [
      { name: 'sessionId', type: 'string (查询参数)', required: true, desc: '会话ID' }
    ],
    bodyExample: null,
    responseExample: '{\n  "code": 200,\n  "success": true,\n  "data": {\n    "usedBytes": 5452595,\n    "maxBytes": 31457280,\n    "usedReadable": "5.2 MB",\n    "maxReadable": "30.0 MB",\n    "percent": 17.33\n  }\n}'
  }
]

/**
 * 对话运行控制接口定义（AG-UI 协议原生端点）
 *
 * 注意：这些端点响应不走统一 R<> 包装（结构见各接口 responseExample）；
 * reconnect / resume 返回 text/event-stream（SSE 事件流）。
 */
const aguiEndpoints = [
  {
    id: 'agui-reconnect',
    method: 'GET',
    path: '/api/runtime/agui/reconnect/{threadId}',
    desc: 'SSE 断线重连',
    note: '断线后重新接入事件流：回放缓冲区已产生的事件后，继续接续实时流。响应为 text/event-stream。',
    params: [
      { name: 'threadId', type: 'string (路径参数)', required: true, desc: '会话线程ID（等同 sessionId）' }
    ],
    bodyExample: null,
    responseExample: null
  },
  {
    id: 'agui-resume',
    method: 'POST',
    path: '/api/runtime/agui/resume/{threadId}',
    desc: 'HITL 确认恢复',
    note: '提交逐工具确认决策，从暂停点续跑并接续 SSE 事件流。decisions 为空表示全部允许；approved=false 的工具将被拒绝。响应为 text/event-stream。',
    params: [
      { name: 'threadId', type: 'string (路径参数)', required: true, desc: '暂停态会话线程ID' }
    ],
    bodyExample: '{\n  "decisions": [\n    { "toolUseId": "call_abc", "name": "shell", "approved": true }\n  ],\n  "memoryActive": false\n}',
    responseExample: null
  },
  {
    id: 'agui-pending',
    method: 'GET',
    path: '/api/runtime/agui/pending/{threadId}',
    desc: '待确认工具列表',
    note: '从暂停态会话重建待确认工具列表，供前端刷新/重进会话时重建确认 UI。无暂停态则 pending 为空数组。',
    params: [
      { name: 'threadId', type: 'string (路径参数)', required: true, desc: '会话线程ID' }
    ],
    bodyExample: null,
    responseExample: '{\n  "pending": [\n    { "toolUseId": "call_abc", "name": "shell", "input": { "command": "ls" } }\n  ]\n}'
  },
  {
    id: 'agui-subagent-resume',
    method: 'POST',
    path: '/api/runtime/agui/subagent/resume',
    desc: '子智能体 HITL 确认',
    note: '提交「子智能体内需确认工具」的逐工具决策，唤醒挂起的 SubAgentTool 续跑（续跑事件沿原主 SSE 流下发）。与主 /resume 不同：本端点即时返回 JSON、不产生新事件流。',
    params: [],
    bodyExample: '{\n  "subSessionId": "sub_xxxxx",\n  "decisions": [\n    { "toolUseId": "call_abc", "name": "shell", "approved": true }\n  ]\n}',
    responseExample: '{\n  "resumed": true\n}'
  },
  {
    id: 'agui-subagent-pending',
    method: 'GET',
    path: '/api/runtime/agui/subagent/pending?threadId=xxx',
    desc: '子确认待处理列表',
    note: '查询主会话下所有挂起中的子智能体确认请求，供前端刷新/重进会话时重建子确认 UI。注意 threadId 为查询参数。',
    params: [
      { name: 'threadId', type: 'string (查询参数)', required: true, desc: '主会话线程ID' }
    ],
    bodyExample: null,
    responseExample: '{\n  "pending": [\n    { "subSessionId": "sub_xxxxx", "toolUseId": "call_abc", "name": "shell", "input": { "command": "ls" } }\n  ]\n}'
  },
  {
    id: 'agui-status',
    method: 'GET',
    path: '/api/runtime/agui/status/{threadId}',
    desc: '查询运行状态',
    note: '查询会话当前是否正在运行（生成中）。',
    params: [
      { name: 'threadId', type: 'string (路径参数)', required: true, desc: '会话线程ID' }
    ],
    bodyExample: null,
    responseExample: '{\n  "running": true\n}'
  },
  {
    id: 'agui-stop',
    method: 'POST',
    path: '/api/runtime/agui/stop/{threadId}',
    desc: '强制停止',
    note: '强制停止指定会话正在进行的生成。',
    params: [
      { name: 'threadId', type: 'string (路径参数)', required: true, desc: '会话线程ID' }
    ],
    bodyExample: null,
    responseExample: '{\n  "stopped": true\n}'
  },
  {
    id: 'agui-active-runs',
    method: 'GET',
    path: '/api/runtime/agui/active-runs',
    desc: '活跃运行列表',
    note: '返回当前所有正在运行的会话线程ID列表（直接返回字符串数组）。',
    params: [],
    bodyExample: null,
    responseExample: '[\n  "2038965802636013570",\n  "2038965802636013571"\n]'
  }
]

/**
 * 语音接口定义（ASR 识别 / TTS 合成）
 *
 * 注意：asr/recognize 为 multipart/form-data；tts/broadcast 音频经 WebSocket
 * 通道流回、HTTP 仅返回受理结果。
 */
const voiceEndpoints = [
  {
    id: 'voice-asr-recognize',
    method: 'POST',
    path: '/api/runtime/asr/recognize',
    desc: '语音转文字',
    note: '聊天输入框的语音识别：接收整段音频文件（WAV），返回转写文字。使用 multipart/form-data 提交，音频即抛不落盘。',
    params: [
      { name: 'agentId', type: 'Long (表单字段)', required: true, desc: '智能体ID（定位其绑定的 ASR 模型）' },
      { name: 'file', type: 'File (表单字段)', required: true, desc: '音频文件（WAV）' }
    ],
    bodyExample: null,
    responseExample: '{\n  "code": 200,\n  "success": true,\n  "data": "识别出的文字内容"\n}'
  },
  {
    id: 'voice-tts-broadcast',
    method: 'POST',
    path: '/api/runtime/tts/broadcast',
    desc: '手动朗读消息',
    note: '朗读消息正文：走流式会话通道，音频经 WebSocket 订阅流回前端（前端须已订阅该 threadId 通道）。会打断该 thread 上进行中的播报。HTTP 仅返回受理结果，音频不在本响应体内。',
    params: [],
    bodyExample: '{\n  "threadId": "2038965802636013570",\n  "agentId": "123456",\n  "text": "这是要朗读的消息正文"\n}',
    responseExample: '{\n  "code": 200,\n  "success": true,\n  "data": null\n}'
  }
]

/**
 * ===================== 导出 PDF =====================
 * 归一化 4 个数组 + 3 个手写区块为统一的可导出结构（现有渲染不受影响）
 */
const exportSections = computed<ExportSection[]>(() => [
  { key: 'external-link', title: '外置对话链接', kind: 'external' },
  { key: 'embed', title: '网站嵌入', kind: 'embed' },
  { key: 'access', title: '访问入口', kind: 'access' },
  { key: 'agui', title: '对话运行控制接口', kind: 'endpoints', items: aguiEndpoints as ExportEndpoint[] },
  { key: 'auth', title: '鉴权方式', kind: 'auth' },
  { key: 'session', title: '会话管理接口', kind: 'endpoints', items: endpoints as ExportEndpoint[] },
  { key: 'workspace', title: '工作空间接口', kind: 'endpoints', items: workspaceEndpoints as ExportEndpoint[] },
  { key: 'voice', title: '语音接口', kind: 'endpoints', items: voiceEndpoints as ExportEndpoint[] },
])

const exportModalOpen = ref(false)
const exportFormat = ref<ExportFileFormat>('html')
const desensitize = ref(true)
const checkedKeys = ref<string[]>([])
const docTitleInput = ref('API 接口文档')
const coverSubtitleInput = ref('智能体接口对接文档')
const agentLabelInput = ref('')
const watermarkInput = ref('仅供对接使用')

/** 选择树：区块为父节点，接口为叶节点（叶 key = section::id 避免冲突） */
const treeData = computed(() =>
  exportSections.value.map((s) => ({
    key: s.key,
    title: s.title,
    children: (s.items || []).map((it) => ({
      key: `${s.key}::${it.id}`,
      title: `${it.method}  ${it.path}`,
    })),
  })),
)

function allExportKeys(): string[] {
  const ks: string[] = []
  exportSections.value.forEach((s) => {
    ks.push(s.key)
    ;(s.items || []).forEach((it) => ks.push(`${s.key}::${it.id}`))
  })
  return ks
}

function openExportModal() {
  checkedKeys.value = allExportKeys()
  exportFormat.value = 'html'
  desensitize.value = true
  docTitleInput.value = 'API 接口文档'
  coverSubtitleInput.value = '智能体接口对接文档'
  agentLabelInput.value = props.agentCode || ''
  watermarkInput.value = '仅供对接使用'
  exportModalOpen.value = true
}

const exportOkText = computed(() => exportFormat.value === 'html' ? '下载 HTML' : '生成预览')

/** 按勾选过滤：数组区块看子 key，手写区块看自身 key */
function buildSelectedSections(): ExportSection[] {
  const checked = new Set(checkedKeys.value)
  const result: ExportSection[] = []
  for (const s of exportSections.value) {
    if (s.kind === 'endpoints') {
      const items = (s.items || []).filter((it) => checked.has(`${s.key}::${it.id}`))
      if (items.length) result.push({ ...s, items })
    } else if (checked.has(s.key)) {
      result.push(s)
    }
  }
  return result
}

const previewVisible = ref(false)
const previewLoading = ref(false)
const pageCount = ref(0)
const printContainer = ref<HTMLElement>()
const pdfFileBaseName = ref('')

function buildExportOptions(): ExportOptions {
  return {
    docTitle: docTitleInput.value || 'API 接口文档',
    coverSubtitle: coverSubtitleInput.value,
    agentLabel: agentLabelInput.value,
    externalChatUrl: externalChatUrl.value,
    accessUrl: accessUrl.value,
    aguiBodyExample,
    desensitize: desensitize.value,
    watermarkText: watermarkInput.value,
    generatedAt: formatDateTime(new Date()),
  }
}

function downloadTextFile(content: string, fileName: string, mimeType: string) {
  const blob = new Blob([content], { type: mimeType })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

function downloadHtmlExport(selected: ExportSection[]) {
  const opts = buildExportOptions()
  const html = buildStandaloneApiDocHtml(selected, opts)
  const fileName = `${buildExportFileBaseName(opts)}.html`
  downloadTextFile(html, fileName, 'text/html;charset=utf-8')
  message.success('HTML 文档已生成')
}

async function confirmExport() {
  const selected = buildSelectedSections()
  if (!selected.length) {
    message.warning('请至少选择一项接口')
    return
  }
  if (exportFormat.value === 'html') {
    downloadHtmlExport(selected)
    exportModalOpen.value = false
    return
  }
  exportModalOpen.value = false
  previewVisible.value = true
  previewLoading.value = true
  const opts = buildExportOptions()
  pdfFileBaseName.value = buildExportFileBaseName(opts)
  await nextTick()
  try {
    pageCount.value = await renderApiDocPdf(printContainer.value!, selected, opts)
  } catch (error) {
    console.error('导出渲染失败:', error)
    message.error('导出渲染失败')
    previewVisible.value = false
  } finally {
    previewLoading.value = false
  }
}

function doPrint() {
  const originalTitle = document.title
  let restored = false
  let restoreTimer: number | undefined
  const restore = () => {
    if (restored) return
    restored = true
    window.removeEventListener('afterprint', restore)
    if (restoreTimer !== undefined) window.clearTimeout(restoreTimer)
    document.title = originalTitle
  }
  window.addEventListener('afterprint', restore)
  if (pdfFileBaseName.value) {
    document.title = pdfFileBaseName.value
  }
  window.print()
  restoreTimer = window.setTimeout(restore, 60000)
}

function closePreview() {
  previewVisible.value = false
  if (printContainer.value) printContainer.value.innerHTML = ''
}
</script>

<template>
  <div class="api-doc">
    <div class="api-doc-toolbar">
      <AButton type="primary" @click="openExportModal">
        <template #icon><DownloadOutlined /></template>
        导出文档
      </AButton>
    </div>

    <!-- 外置对话链接 -->
    <div class="api-doc-section">
      <div class="api-doc-title">
        <GlobalOutlined style="margin-right: 8px;" />外置对话链接
      </div>

      <div class="api-info-box success">
        <div style="margin-bottom: 8px; font-weight: 600;">外置对话入口</div>
        <div v-if="chatKeyLoading" style="padding: 12px 0;">
          <ApboaSpin size="small" />
          <span style="margin-left: 8px; color: var(--color-text-secondary);">加载中...</span>
        </div>
        <template v-else-if="externalChatUrl">
          <div style="display: flex; align-items: center; gap: 4px;">
            <code style="font-size: 13px; word-break: break-all; flex: 1;">{{ externalChatUrl }}</code>
            <AButton
              type="text"
              size="small"
              @click="copyToClipboard(externalChatUrl, 'external-url')"
            >
              <template #icon>
                <CheckOutlined v-if="copiedKey === 'external-url'" style="color: #52c41a;" />
                <CopyOutlined v-else />
              </template>
            </AButton>
            <ATooltip title="刷新链接（原链接将失效）">
              <AButton
                type="text"
                size="small"
                :loading="chatKeyLoading"
                @click="handleRefreshChatKey"
              >
                <template #icon><SyncOutlined /></template>
              </AButton>
            </ATooltip>
          </div>
        </template>
        <div v-else style="padding: 12px 0; color: var(--color-text-secondary);">
          暂无对话链接
        </div>
        <div style="font-size: 12px; color: #546e7a; margin-top: 8px;">
          该链接可直接在外部浏览器中打开进行对话，无需登录即可使用。
        </div>
      </div>
    </div>

    <!-- 网站嵌入 -->
    <div class="api-doc-section">
      <div class="api-doc-title">
        <CodeOutlined style="margin-right: 8px;" />网站嵌入
      </div>

      <div class="api-info-box info">
        <div style="margin-bottom: 4px; font-weight: 600;">方式一 · iframe 直接嵌入</div>
        <div style="font-size: 12px; color: #546e7a; margin-bottom: 8px;">
          把对话作为固定区域嵌入网页，始终可见，宽高可按容器调整。
        </div>
        <div style="display: flex; align-items: flex-start; gap: 4px;">
          <code style="font-size: 13px; word-break: break-all; white-space: pre-wrap; flex: 1;">{{ embedIframeCode || '（暂无对话链接）' }}</code>
          <AButton
            type="text"
            size="small"
            :disabled="!embedIframeCode"
            @click="copyToClipboard(embedIframeCode, 'embed-iframe')"
          >
            <template #icon>
              <CheckOutlined v-if="copiedKey === 'embed-iframe'" style="color: #52c41a;" />
              <CopyOutlined v-else />
            </template>
          </AButton>
        </div>
      </div>

      <div class="api-info-box success">
        <div style="margin-bottom: 4px; font-weight: 600;">方式二 · 悬浮气泡</div>
        <div style="font-size: 12px; color: #546e7a; margin-bottom: 8px;">
          在网站右下角生成悬浮按钮，点击弹出对话浮窗；把下面一行放到页面 &lt;/body&gt; 之前即可。
        </div>
        <div style="display: flex; align-items: flex-start; gap: 4px;">
          <code style="font-size: 13px; word-break: break-all; white-space: pre-wrap; flex: 1;">{{ embedBubbleCode || '（暂无对话链接）' }}</code>
          <AButton
            type="text"
            size="small"
            :disabled="!embedBubbleCode"
            @click="copyToClipboard(embedBubbleCode, 'embed-bubble')"
          >
            <template #icon>
              <CheckOutlined v-if="copiedKey === 'embed-bubble'" style="color: #52c41a;" />
              <CopyOutlined v-else />
            </template>
          </AButton>
        </div>
        <div style="font-size: 12px; color: #546e7a; margin-top: 8px;">
          可选属性：data-base-url、data-title、data-color、data-position（left|right）、data-width、data-height（默认 400×800）。
        </div>
      </div>
    </div>

    <!-- 访问入口 -->
    <div class="api-doc-section">
      <div class="api-doc-title">
        <LinkOutlined style="margin-right: 8px;" />访问入口
      </div>

      <div class="api-info-box info">
        <div style="margin-bottom: 8px; font-weight: 600;">智能体对话接口</div>
        <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 4px;">
          <span class="method-badge post">POST</span>
          <code style="font-size: 13px; word-break: break-all;">{{ accessUrl }}</code>
          <AButton
            type="text"
            size="small"
            @click="copyToClipboard(accessUrl, 'url')"
          >
            <template #icon>
              <CheckOutlined v-if="copiedKey === 'url'" style="color: #52c41a;" />
              <CopyOutlined v-else />
            </template>
          </AButton>
        </div>
        <div style="font-size: 12px; color: #546e7a; margin-top: 4px;">
          该接口为智能体的主要对话入口，支持流式和非流式响应。
        </div>
        <div style="font-size: 12px; color: #546e7a; margin-top: 4px;">
          若不在路径中指定智能体，也可调用 <code>POST /api/runtime/agui/run</code>（不带 agentCode），此时智能体由请求头 <code>X-Agent-Id</code> 或 <code>forwardedProps.agentId</code> 解析。
        </div>

        <div
          class="agui-collapse-header"
          style="margin-top: 12px;"
          @click="aguiBodyExpanded = !aguiBodyExpanded"
        >
          <component
            :is="aguiBodyExpanded ? DownOutlined : RightOutlined"
            class="agui-collapse-arrow"
          />
          <span class="agui-collapse-title">{{aguiBodyExpanded ? '折叠' : '展开'}} Request Body</span>
        </div>

        <template v-if="aguiBodyExpanded">
        <div class="endpoint-detail-title">Request Body</div>
        <table class="param-table">
          <thead>
            <tr>
              <th style="padding-left: 32px;">参数名</th>
              <th>类型</th>
              <th>必填</th>
              <th>说明</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td class="param-name" style="padding-left: 32px;">threadId</td>
              <td class="param-type">string</td>
              <td><span class="param-required">Required</span></td>
              <td>会话线程ID，用于标识一次完整的对话</td>
            </tr>
            <tr>
              <td class="param-name" style="padding-left: 32px;">runId</td>
              <td class="param-type">string</td>
              <td><span class="param-required">Required</span></td>
              <td>本次运行ID，系统自动生成的唯一标识</td>
            </tr>
            <tr class="param-collapse-row" @click="messagesExpanded = !messagesExpanded">
              <td class="param-name">
                <component
                  :is="messagesExpanded ? DownOutlined : RightOutlined"
                  class="param-collapse-arrow"
                />
                messages
              </td>
              <td class="param-type">array</td>
              <td><span class="param-required">Required</span></td>
              <td>消息列表，包含 id、role（user）、content 字段</td>
            </tr>
            <template v-if="messagesExpanded">
            <tr>
              <td class="param-name" style="padding-left: 48px;">id</td>
              <td class="param-type">string</td>
              <td><span class="param-required">Required</span></td>
              <td>消息ID</td>
            </tr>
            <tr>
              <td class="param-name" style="padding-left: 48px;">role</td>
              <td class="param-type">string</td>
              <td><span class="param-required">Required</span></td>
              <td>消息角色，可选值为 user</td>
            </tr>
            <tr>
              <td class="param-name" style="padding-left: 48px;">content</td>
              <td class="param-type">string</td>
              <td><span class="param-required">Required</span></td>
              <td>消息内容</td>
            </tr>
            </template>
            <tr class="param-collapse-row" @click="forwardedPropsExpanded = !forwardedPropsExpanded">
              <td class="param-name">
                <component
                  :is="forwardedPropsExpanded ? DownOutlined : RightOutlined"
                  class="param-collapse-arrow"
                />
                forwardedProps
              </td>
              <td class="param-type">object</td>
              <td><span style="color: #bfbfbf;">Optional</span></td>
              <td>转发属性对象 memoryActive、planActive、fileIds、params 字段</td>
            </tr>
            <template v-if="forwardedPropsExpanded">
            <tr>
              <td class="param-name" style="padding-left: 48px;">memoryActive</td>
              <td class="param-type">boolean</td>
              <td><span style="color: #bfbfbf;">Optional</span></td>
              <td>是否启用记忆功能</td>
            </tr>
            <tr>
              <td class="param-name" style="padding-left: 48px;">planActive</td>
              <td class="param-type">boolean</td>
              <td><span style="color: #bfbfbf;">Optional</span></td>
              <td>是否启用计划功能</td>
            </tr>
            <tr>
              <td class="param-name" style="padding-left: 48px;">fileIds</td>
              <td class="param-type">string[]</td>
              <td><span style="color: #bfbfbf;">Optional</span></td>
              <td>文件ID列表，上传多模态文件后返回的ID</td>
            </tr>
            <tr>
              <td class="param-name" style="padding-left: 48px;">params</td>
              <td class="param-type">object</td>
              <td><span style="color: #bfbfbf;">Optional</span></td>
              <td>扩展参数键值对，在工具中可直接获取该对象</td>
            </tr>
            </template>
          </tbody>
        </table>

        </template>

        <div
          class="agui-collapse-header"
          style="margin-top: 12px;"
          @click="aguiExampleExpanded = !aguiExampleExpanded"
        >
          <component
            :is="aguiExampleExpanded ? DownOutlined : RightOutlined"
            class="agui-collapse-arrow"
          />
          <span class="agui-collapse-title">{{aguiExampleExpanded ? '折叠' : '展开'}} Request Example</span>
        </div>

        <template v-if="aguiExampleExpanded">
        <div class="endpoint-detail-title">Request Example</div>
        <div class="code-block" style="margin: 0;">{{ aguiBodyExample }}<span
          class="code-copy-btn"
          style="position: absolute; top: 8px; right: 8px; cursor: pointer;"
          @click="copyToClipboard(aguiBodyExample, 'agui-body')"
        >
          <CheckOutlined v-if="copiedKey === 'agui-body'" style="color: #a6e3a1;" />
          <CopyOutlined v-else style="color: #a6adc8;" />
        </span></div>
        </template>
      </div>
    </div>

    <!-- 对话运行控制接口 -->
    <div class="api-doc-section">
      <div class="api-doc-title">
        <ThunderboltOutlined style="margin-right: 8px;" />对话运行控制接口
      </div>

      <div class="api-info-box info" style="margin-bottom: 12px;">
        <div style="font-size: 12px; color: #546e7a;">
          AG-UI 协议原生端点：<code>reconnect</code> / <code>resume</code> 返回 SSE 事件流（text/event-stream），其余返回 JSON。响应不走统一 R&lt;&gt; 包装，结构见各接口 Response。
        </div>
      </div>

      <div
        v-for="ep in aguiEndpoints"
        :key="ep.id"
        class="api-endpoint-card"
      >
        <div class="endpoint-header" @click="toggleEndpoint(ep.id)">
          <component
            :is="expandedEndpoints.has(ep.id) ? DownOutlined : RightOutlined"
            style="font-size: 10px; color: #bfbfbf;"
          />
          <span class="method-badge" :class="ep.method.toLowerCase()">{{ ep.method }}</span>
          <span class="endpoint-path">{{ ep.path }}</span>
          <span class="endpoint-desc">{{ ep.desc }}</span>
        </div>

        <div v-if="expandedEndpoints.has(ep.id)" class="endpoint-body">
          <div v-if="ep.note" style="font-size: 13px; color: var(--color-text-secondary); margin-top: 12px; margin-bottom: 8px;">
            {{ ep.note }}
          </div>

          <!-- 参数表 -->
          <div v-if="ep.params.length > 0">
            <div class="endpoint-detail-title">Parameters</div>
            <table class="param-table">
              <thead>
                <tr>
                  <th>参数名</th>
                  <th>类型</th>
                  <th>必填</th>
                  <th>说明</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="p in ep.params" :key="p.name">
                  <td class="param-name">{{ p.name }}</td>
                  <td class="param-type">{{ p.type }}</td>
                  <td><span v-if="p.required" class="param-required">Required</span><span v-else style="color: #bfbfbf;">Optional</span></td>
                  <td>{{ p.desc }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <!-- 请求体示例 -->
          <template v-if="ep.bodyExample">
            <div class="endpoint-detail-title">Request Body</div>
            <div class="code-block">{{ ep.bodyExample }}<span
              class="code-copy-btn"
              style="position: absolute; top: 8px; right: 8px; cursor: pointer;"
              @click="copyToClipboard(ep.bodyExample!, `body-${ep.id}`)"
            >
              <CheckOutlined v-if="copiedKey === `body-${ep.id}`" style="color: #a6e3a1;" />
              <CopyOutlined v-else style="color: #a6adc8;" />
            </span></div>
          </template>

          <!-- 响应示例 -->
          <template v-if="ep.responseExample">
            <div class="endpoint-detail-title">Response</div>
            <div class="code-block">{{ ep.responseExample }}<span
              class="code-copy-btn"
              style="position: absolute; top: 8px; right: 8px; cursor: pointer;"
              @click="copyToClipboard(ep.responseExample!, `resp-${ep.id}`)"
            >
              <CheckOutlined v-if="copiedKey === `resp-${ep.id}`" style="color: #a6e3a1;" />
              <CopyOutlined v-else style="color: #a6adc8;" />
            </span></div>
          </template>
        </div>
      </div>
    </div>

    <!-- 鉴权说明 -->
    <div class="api-doc-section">
      <div class="api-doc-title">
        <KeyOutlined style="margin-right: 8px;" />鉴权方式
      </div>

      <div class="api-info-box warning">
        <div style="margin-bottom: 8px; font-weight: 600;">API Key 鉴权</div>
        <div style="margin-bottom: 8px;">所有接口请求需要在请求头中携带 API Key 进行身份验证：</div>
        <div class="code-block" style="margin: 0;">Authorization: {API_KEY}<span
          class="code-copy-btn"
          style="position: absolute; top: 8px; right: 8px; cursor: pointer;"
          @click="copyToClipboard('Authorization: {API_KEY}', 'auth')"
        >
          <CheckOutlined v-if="copiedKey === 'auth'" style="color: #a6e3a1;" />
          <CopyOutlined v-else style="color: #a6adc8;" />
        </span></div>
        <div style="font-size: 12px; color: #795548; margin-top: 8px;">
          API Key 可在系统设置 > API Keys 中创建和管理。请妥善保管您的 API Key，不要在客户端代码中暴露。
        </div>
      </div>

      <div class="api-info-box success" style="margin-top: 12px;">
        <div style="margin-bottom: 8px; font-weight: 600;">chatKey 免登鉴权</div>
        <div style="margin-bottom: 8px;">
          顶部「外置对话链接」背后的免登方式：凭该智能体专属的 chatKey，外部用户无需登录即可直接对话。分享链接（/#/communication/{chatKey}）打开后由前端自动完成鉴权换取访问凭证。
        </div>
        <div style="font-size: 12px; color: #546e7a; margin-top: 8px;">
          chatKey 可刷新（刷新后旧链接失效，见顶部「外置对话链接」）。免登凭证仅能访问带 @ChatKeyAccess 的接口（对话运行、会话管理、工作空间、附件、语音等对话协议相关接口）；未开放 chatKey 的接口须使用 API Key。
        </div>
      </div>
    </div>

    <!-- 会话管理接口 -->
    <div class="api-doc-section">
      <div class="api-doc-title">
        <ThunderboltOutlined style="margin-right: 8px;" />会话管理接口
      </div>

      <div
        v-for="ep in endpoints"
        :key="ep.id"
        class="api-endpoint-card"
      >
        <div class="endpoint-header" @click="toggleEndpoint(ep.id)">
          <component
            :is="expandedEndpoints.has(ep.id) ? DownOutlined : RightOutlined"
            style="font-size: 10px; color: #bfbfbf;"
          />
          <span class="method-badge" :class="ep.method.toLowerCase()">{{ ep.method }}</span>
          <span class="endpoint-path">{{ ep.path }}</span>
          <span class="endpoint-desc">{{ ep.desc }}</span>
        </div>

        <div v-if="expandedEndpoints.has(ep.id)" class="endpoint-body">
          <div v-if="ep.note" style="font-size: 13px; color: var(--color-text-secondary); margin-top: 12px; margin-bottom: 8px;">
            {{ ep.note }}
          </div>

          <!-- 参数表 -->
          <div v-if="ep.params.length > 0">
            <div class="endpoint-detail-title">Parameters</div>
            <table class="param-table">
              <thead>
                <tr>
                  <th>参数名</th>
                  <th>类型</th>
                  <th>必填</th>
                  <th>说明</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="p in ep.params" :key="p.name">
                  <td class="param-name">{{ p.name }}</td>
                  <td class="param-type">{{ p.type }}</td>
                  <td><span v-if="p.required" class="param-required">Required</span><span v-else style="color: #bfbfbf;">Optional</span></td>
                  <td>{{ p.desc }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <!-- 请求体示例 -->
          <template v-if="ep.bodyExample">
            <div class="endpoint-detail-title">Request Body</div>
            <div class="code-block">{{ ep.bodyExample }}<span
              class="code-copy-btn"
              style="position: absolute; top: 8px; right: 8px; cursor: pointer;"
              @click="copyToClipboard(ep.bodyExample!, `body-${ep.id}`)"
            >
              <CheckOutlined v-if="copiedKey === `body-${ep.id}`" style="color: #a6e3a1;" />
              <CopyOutlined v-else style="color: #a6adc8;" />
            </span></div>
          </template>

          <!-- 响应示例 -->
          <template v-if="ep.responseExample">
            <div class="endpoint-detail-title">Response</div>
            <div class="code-block">{{ ep.responseExample }}<span
              class="code-copy-btn"
              style="position: absolute; top: 8px; right: 8px; cursor: pointer;"
              @click="copyToClipboard(ep.responseExample!, `resp-${ep.id}`)"
            >
              <CheckOutlined v-if="copiedKey === `resp-${ep.id}`" style="color: #a6e3a1;" />
              <CopyOutlined v-else style="color: #a6adc8;" />
            </span></div>
          </template>
        </div>
      </div>
    </div>

    <!-- 工作空间接口 -->
    <div class="api-doc-section">
      <div class="api-doc-title">
        <FolderOpenOutlined style="margin-right: 8px;" />工作空间接口
      </div>

      <div
        v-for="ep in workspaceEndpoints"
        :key="ep.id"
        class="api-endpoint-card"
      >
        <div class="endpoint-header" @click="toggleEndpoint(ep.id)">
          <component
            :is="expandedEndpoints.has(ep.id) ? DownOutlined : RightOutlined"
            style="font-size: 10px; color: #bfbfbf;"
          />
          <span class="method-badge" :class="ep.method.toLowerCase()">{{ ep.method }}</span>
          <span class="endpoint-path">{{ ep.path }}</span>
          <span class="endpoint-desc">{{ ep.desc }}</span>
        </div>

        <div v-if="expandedEndpoints.has(ep.id)" class="endpoint-body">
          <div v-if="ep.note" style="font-size: 13px; color: var(--color-text-secondary); margin-top: 12px; margin-bottom: 8px;">
            {{ ep.note }}
          </div>

          <!-- 参数表 -->
          <div v-if="ep.params.length > 0">
            <div class="endpoint-detail-title">Parameters</div>
            <table class="param-table">
              <thead>
                <tr>
                  <th>参数名</th>
                  <th>类型</th>
                  <th>必填</th>
                  <th>说明</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="p in ep.params" :key="p.name">
                  <td class="param-name">{{ p.name }}</td>
                  <td class="param-type">{{ p.type }}</td>
                  <td><span v-if="p.required" class="param-required">Required</span><span v-else style="color: #bfbfbf;">Optional</span></td>
                  <td>{{ p.desc }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <!-- 请求体示例 -->
          <template v-if="ep.bodyExample">
            <div class="endpoint-detail-title">Request Body</div>
            <div class="code-block">{{ ep.bodyExample }}<span
              class="code-copy-btn"
              style="position: absolute; top: 8px; right: 8px; cursor: pointer;"
              @click="copyToClipboard(ep.bodyExample!, `body-${ep.id}`)"
            >
              <CheckOutlined v-if="copiedKey === `body-${ep.id}`" style="color: #a6e3a1;" />
              <CopyOutlined v-else style="color: #a6adc8;" />
            </span></div>
          </template>

          <!-- 响应示例 -->
          <template v-if="ep.responseExample">
            <div class="endpoint-detail-title">Response</div>
            <div class="code-block">{{ ep.responseExample }}<span
              class="code-copy-btn"
              style="position: absolute; top: 8px; right: 8px; cursor: pointer;"
              @click="copyToClipboard(ep.responseExample!, `resp-${ep.id}`)"
            >
              <CheckOutlined v-if="copiedKey === `resp-${ep.id}`" style="color: #a6e3a1;" />
              <CopyOutlined v-else style="color: #a6adc8;" />
            </span></div>
          </template>
        </div>
      </div>
    </div>

    <!-- 语音接口 -->
    <div class="api-doc-section">
      <div class="api-doc-title">
        <SoundOutlined style="margin-right: 8px;" />语音接口
      </div>

      <div class="api-info-box info" style="margin-bottom: 12px;">
        <div style="font-size: 12px; color: #546e7a;">
          语音输入输出：<code>asr/recognize</code> 为 multipart/form-data 语音转文字；<code>tts/broadcast</code> 音频经 WebSocket 通道流回、HTTP 仅返回受理结果。
        </div>
      </div>

      <div
        v-for="ep in voiceEndpoints"
        :key="ep.id"
        class="api-endpoint-card"
      >
        <div class="endpoint-header" @click="toggleEndpoint(ep.id)">
          <component
            :is="expandedEndpoints.has(ep.id) ? DownOutlined : RightOutlined"
            style="font-size: 10px; color: #bfbfbf;"
          />
          <span class="method-badge" :class="ep.method.toLowerCase()">{{ ep.method }}</span>
          <span class="endpoint-path">{{ ep.path }}</span>
          <span class="endpoint-desc">{{ ep.desc }}</span>
        </div>

        <div v-if="expandedEndpoints.has(ep.id)" class="endpoint-body">
          <div v-if="ep.note" style="font-size: 13px; color: var(--color-text-secondary); margin-top: 12px; margin-bottom: 8px;">
            {{ ep.note }}
          </div>

          <!-- 参数表 -->
          <div v-if="ep.params.length > 0">
            <div class="endpoint-detail-title">Parameters</div>
            <table class="param-table">
              <thead>
                <tr>
                  <th>参数名</th>
                  <th>类型</th>
                  <th>必填</th>
                  <th>说明</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="p in ep.params" :key="p.name">
                  <td class="param-name">{{ p.name }}</td>
                  <td class="param-type">{{ p.type }}</td>
                  <td><span v-if="p.required" class="param-required">Required</span><span v-else style="color: #bfbfbf;">Optional</span></td>
                  <td>{{ p.desc }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <!-- 请求体示例 -->
          <template v-if="ep.bodyExample">
            <div class="endpoint-detail-title">Request Body</div>
            <div class="code-block">{{ ep.bodyExample }}<span
              class="code-copy-btn"
              style="position: absolute; top: 8px; right: 8px; cursor: pointer;"
              @click="copyToClipboard(ep.bodyExample!, `body-${ep.id}`)"
            >
              <CheckOutlined v-if="copiedKey === `body-${ep.id}`" style="color: #a6e3a1;" />
              <CopyOutlined v-else style="color: #a6adc8;" />
            </span></div>
          </template>

          <!-- 响应示例 -->
          <template v-if="ep.responseExample">
            <div class="endpoint-detail-title">Response</div>
            <div class="code-block">{{ ep.responseExample }}<span
              class="code-copy-btn"
              style="position: absolute; top: 8px; right: 8px; cursor: pointer;"
              @click="copyToClipboard(ep.responseExample!, `resp-${ep.id}`)"
            >
              <CheckOutlined v-if="copiedKey === `resp-${ep.id}`" style="color: #a6e3a1;" />
              <CopyOutlined v-else style="color: #a6adc8;" />
            </span></div>
          </template>
        </div>
      </div>
    </div>

    <!-- 导出 PDF：选择范围 -->
    <AModal
      v-model:open="exportModalOpen"
      title="导出接口文档"
      :ok-text="exportOkText"
      cancel-text="取消"
      :width="520"
      @ok="confirmExport"
    >
      <div class="export-fields">
        <label class="export-field">
          <span>导出格式</span>
          <ARadioGroup v-model:value="exportFormat" button-style="solid">
            <ARadioButton value="html">HTML</ARadioButton>
            <ARadioButton value="pdf">PDF</ARadioButton>
          </ARadioGroup>
        </label>
        <label class="export-field">
          <span>文档标题</span>
          <AInput v-model:value="docTitleInput" placeholder="API 接口文档" allow-clear />
        </label>
        <label class="export-field">
          <span>封面副标题</span>
          <AInput v-model:value="coverSubtitleInput" placeholder="智能体接口对接文档" allow-clear />
        </label>
        <label class="export-field">
          <span>智能体名称</span>
          <AInput v-model:value="agentLabelInput" placeholder="显示在封面与页眉，留空则不显示" allow-clear />
        </label>
        <label class="export-field">
          <span>水印文字</span>
          <AInput v-model:value="watermarkInput" placeholder="每页平铺的水印，留空则无水印" allow-clear />
        </label>
      </div>

      <div style="margin-bottom: 10px; font-size: 13px; color: var(--color-text-secondary);">
        选择要导出的接口（默认全选）：
      </div>
      <ATree
        v-model:checkedKeys="checkedKeys"
        :tree-data="treeData"
        checkable
        :selectable="false"
        :default-expand-all="true"
        style="max-height: 360px; overflow: auto;"
      />
      <div style="margin-top: 16px; display: flex; align-items: center; gap: 8px;">
        <ASwitch v-model:checked="desensitize" size="small" />
        <span style="font-size: 13px;">脱敏敏感凭证（chatKey / agentCode 用占位符）</span>
      </div>
    </AModal>

    <!-- 导出 PDF：打印预览遮罩 -->
    <Teleport to="body">
      <div v-if="previewVisible" class="apidoc-print-overlay">
        <div class="apidoc-print-toolbar">
          <span class="apidoc-print-info">
            <template v-if="previewLoading">正在生成预览…</template>
            <template v-else>共 {{ pageCount }} 页 · 在打印对话框中选择「另存为 PDF」</template>
          </span>
          <div class="apidoc-print-actions">
            <AButton type="primary" :disabled="previewLoading" @click="doPrint">
              <template #icon><PrinterOutlined /></template>
              打印 / 另存 PDF
            </AButton>
            <AButton ghost @click="closePreview">
              <template #icon><CloseOutlined /></template>
              关闭
            </AButton>
          </div>
        </div>
        <div class="apidoc-print-scroll">
          <div ref="printContainer" class="apidoc-print-pages"></div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/agent/config-panel.scss' as *;

.agui-collapse-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  cursor: pointer;
  border-radius: 6px;
  transition: background-color 0.2s;

  &:hover {
    background-color: rgba(0, 0, 0, 0.04);
  }
}

.agui-collapse-arrow {
  font-size: 10px;
}

.agui-collapse-title {
  font-size: 13px;
  font-weight: 600;
}

.param-collapse-row {
  cursor: pointer;

  &:hover {
    background-color: rgba(0, 0, 0, 0.02);
  }
}

.param-collapse-arrow {
  font-size: 10px;
  margin-right: 4px;
  color: #bfbfbf;
}
</style>

<!-- 导出 PDF 相关样式：遮罩 Teleport 到 body，须非 scoped 才能命中 -->
<style lang="scss">
.api-doc-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 16px;
}

.export-fields {
  margin-bottom: 16px;
}
.export-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 12px;
}
.export-field > span {
  font-size: 13px;
  color: var(--color-text-secondary);
}

.apidoc-print-overlay {
  position: fixed;
  inset: 0;
  z-index: 2000;
  background: #525659;
  display: flex;
  flex-direction: column;
}

.apidoc-print-toolbar {
  flex-shrink: 0;
  height: 56px;
  padding: 0 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #323639;
  color: #fff;
}

.apidoc-print-info {
  font-size: 13px;
  color: #cfd3d6;
}

.apidoc-print-actions {
  display: flex;
  gap: 8px;
}

.apidoc-print-scroll {
  flex: 1;
  overflow: auto;
  padding: 24px 0;
}

.apidoc-print-pages {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.apidoc-print-pages .pagedjs_page {
  background: #fff;
  box-shadow: 0 2px 14px rgba(0, 0, 0, 0.35);
}

@media print {
  body > *:not(.apidoc-print-overlay) {
    display: none !important;
  }
  .apidoc-print-overlay {
    position: static;
    background: #fff;
  }
  .apidoc-print-toolbar {
    display: none !important;
  }
  .apidoc-print-scroll {
    overflow: visible;
    padding: 0;
  }
  .apidoc-print-pages {
    gap: 0;
  }
  .apidoc-print-pages .pagedjs_page {
    box-shadow: none;
    margin: 0;
  }
  @page {
    size: A4;
    margin: 0;
  }
}
</style>
