/**
 * 智能体配置-历史对话子组件
 * 分页会话列表 + 对话详情展示
 *
 * @component
 */
<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { LoadingOutlined, PushpinOutlined, DeleteOutlined, CopyOutlined, CheckOutlined } from '@ant-design/icons-vue'
import { Modal, message } from 'ant-design-vue'
import * as chatSessionApi from '@/api/chatSession'
import MessageList from '@/components/chatHistory/MessageList.vue'
import type {ChatSessionVO, ChatMessageVO, DisplayMessage} from '@/types'
import dayjs from 'dayjs'

const props = defineProps<{
  agentId: string
}>()

const sessions = ref<ChatSessionVO[]>([])
const totalSessions = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const listLoading = ref(false)

const selectedSessionId = ref<string | null>(null)
const messages = ref<ChatMessageVO[]>([])
const detailLoading = ref(false)
const showDetail = ref(false)

/** 会话 ID 复制反馈（2 秒内显示对勾） */
const sessionIdCopied = ref(false)

/**
 * 复制选中会话 ID（供排查定位使用）
 */
async function copySessionId() {
  if (!selectedSessionId.value || sessionIdCopied.value) return
  try {
    await navigator.clipboard.writeText(selectedSessionId.value)
    sessionIdCopied.value = true
    setTimeout(() => { sessionIdCopied.value = false }, 2000)
  } catch {
    // 剪贴板不可用时静默
  }
}

/** 会话 ID 过滤关键字（精确匹配） */
const sessionIdFilter = ref('')

/**
 * 按会话 ID 过滤：仅接受纯数字（后端为 bigint 精确匹配），回车触发
 */
function handleFilterById() {
  const kw = sessionIdFilter.value.trim()
  if (kw && !/^\d+$/.test(kw)) {
    message.warning('会话 ID 应为纯数字')
    return
  }
  currentPage.value = 1
  loadSessions()
}

/**
 * 清除过滤（点输入框清除按钮）时恢复全量列表
 */
function handleFilterChange() {
  if (!sessionIdFilter.value) {
    currentPage.value = 1
    loadSessions()
  }
}

/** 列表项 ID 复制反馈（记录已复制的会话 ID，2 秒内显示对勾） */
const copiedListId = ref<string | null>(null)

/**
 * 复制列表项会话 ID
 */
async function copyListSessionId(id: string) {
  try {
    await navigator.clipboard.writeText(id)
    copiedListId.value = id
    setTimeout(() => { if (copiedListId.value === id) copiedListId.value = null }, 2000)
  } catch {
    // 剪贴板不可用时静默
  }
}

/**
 * 加载会话列表
 */
async function loadSessions() {
  listLoading.value = true
  try {
    const res = await chatSessionApi.pageSessions({
      id: sessionIdFilter.value.trim() || undefined,
      agentId: props.agentId,
      page: currentPage.value,
      size: pageSize.value
    })
    const page = res.data.data
    sessions.value = page.records || []
    totalSessions.value = page.total || 0
  } catch (e) {
    console.error('加载会话列表失败:', e)
  } finally {
    listLoading.value = false
  }
}

/**
 * 选中会话，加载对话详情
 */
async function handleSelectSession(session: ChatSessionVO) {
  selectedSessionId.value = String(session.id)
  detailLoading.value = true
  showDetail.value = true
  try {
    const res = await chatSessionApi.getCurrentMessages(String(session.id))
    messages.value = res.data.data || []
  } catch (e) {
    console.error('加载对话内容失败:', e)
    messages.value = []
  } finally {
    detailLoading.value = false
  }
}

function handlePageChange(page: number) {
  currentPage.value = page
  loadSessions()
}

function formatTime(time: string) {
  if (!time) return ''
  return dayjs(time).format('MM-DD HH:mm')
}

/**
 * 删除会话（二次确认）
 */
function handleDeleteSession(session: ChatSessionVO, e: Event) {
  e.stopPropagation()
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除会话「${session.title || '未命名会话'}」吗？删除后不可恢复。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      try {
        await chatSessionApi.deleteSession(String(session.id))
        // 如果删除的是当前选中的会话，清空详情
        if (String(session.id) === selectedSessionId.value) {
          selectedSessionId.value = null
          messages.value = []
          showDetail.value = false
        }
        await loadSessions()
      } catch (err) {
        console.error('删除会话失败:', err)
      }
    }
  })
}

/**
 * 尝试从消息内容中解析推理和正文
 * 如果内容为 JSON 格式 {reasoning, content}，则提取两部分；否则原样返回
 */
function parseMessageContent(raw: string): { content: string; reasoningContent?: string } {
  try {
    const parsed = JSON.parse(raw)
    if (parsed && typeof parsed === 'object' && 'reasoning' in parsed && 'content' in parsed) {
      return { content: parsed.content as string, reasoningContent: parsed.reasoning as string }
    }
  } catch {
    // not JSON, return as-is
  }
  return { content: raw }
}


/**
 * 过滤掉 system 根消息
 */
const visibleMessages = computed(() =>{
  const list: DisplayMessage[] = []
  const messagesList = messages.value.filter(m => !(m.role === 'system' && m.depth === 0))

  for (let i = 0; i < messagesList.length; i++) {
    const m = messagesList[i]
    if (m == null) {
      continue
    }
    // 解析推理内容 - 不修改原数据
    const parsed = m.role === 'assistant'
      ? parseMessageContent(m.content || '')
      : { content: m.content || '', reasoningContent: undefined }

    list.push({
      id: String(m.id),
      role: m.role as DisplayMessage['role'],
      content: parsed.content,
      createdAt: m.createdAt,
      meta: m.meta,
      isStreaming: false
    })
  }

  return list
})

onMounted(() => loadSessions())
</script>

<template>
  <div class="history-container">
    <!-- 会话列表 -->
    <div class="history-list-panel">
      <div class="history-list-inner">
        <div class="history-list-header">
          <div class="history-list-title">
            会话列表
            <ATag color="processing" style="margin: 0;">{{ totalSessions }}</ATag>
          </div>
          <AInput
            v-model:value="sessionIdFilter"
            placeholder="按会话 ID 精确过滤，回车查询"
            allow-clear
            size="small"
            @press-enter="handleFilterById"
            @change="handleFilterChange"
          />
        </div>

        <ApboaSpin :spinning="listLoading">
          <div v-if="sessions.length === 0 && !listLoading" style="padding: 40px 0; text-align: center;">
            <AEmpty description="暂无会话记录" />
          </div>

          <div v-else class="history-sessions">
            <div
              v-for="session in sessions"
              :key="session.id"
              class="history-session-item"
              :class="{ active: String(session.id) === selectedSessionId }"
              @click="handleSelectSession(session)"
            >
              <div class="session-title-row">
                <span class="session-title-text">
                  <PushpinOutlined v-if="session.isPinned" style="margin-right: 6px; color: #4449d0;" />
                  {{ session.title || '未命名会话' }}
                </span>
                <DeleteOutlined
                  class="session-delete-btn"
                  @click="handleDeleteSession(session, $event)"
                />
              </div>
              <div class="session-meta">
                <span>创建于 {{ formatTime(session.createdAt) }}</span>
                <span style="margin-left: 12px;">更新于 {{ formatTime(session.updatedAt) }}</span>
              </div>
              <div
                class="session-id-row"
                :title="copiedListId === String(session.id) ? '已复制' : '点击复制会话 ID'"
                @click.stop="copyListSessionId(String(session.id))"
              >
                ID: {{ session.id }}
                <CheckOutlined v-if="copiedListId === String(session.id)" class="session-id-done" />
                <CopyOutlined v-else class="session-id-copy" />
              </div>
            </div>
          </div>
        </ApboaSpin>

        <div v-if="totalSessions > pageSize" style="padding: 16px 0; display: flex; justify-content: center;">
          <APagination
            :current="currentPage"
            :total="totalSessions"
            :page-size="pageSize"
            size="small"
            @change="handlePageChange"
          />
        </div>
      </div>
    </div>

    <!-- 对话详情 -->
    <div class="history-detail-panel">
      <!-- 会话 ID 信息条：滚动悬停，点击复制便于排查定位 -->
      <div v-if="selectedSessionId" class="history-detail-header">
        <span
          class="history-session-id"
          :title="sessionIdCopied ? '已复制' : '点击复制会话 ID'"
          @click="copySessionId"
        >
          会话 ID：{{ selectedSessionId }}
          <CheckOutlined v-if="sessionIdCopied" class="history-session-id-done" />
          <CopyOutlined v-else />
        </span>
      </div>
      <div v-if="detailLoading" class="loading-messages">
        <LoadingOutlined style="margin-right: 6px" />加载中
      </div>
      <div v-else-if="visibleMessages.length === 0" style="text-align: center;margin-top: 250px">
        <AEmpty description="暂无对话内容" />
      </div>
      <MessageList v-else :messages="visibleMessages as DisplayMessage[]"/>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/agent/config-panel.scss' as *;

/* 列表头部：标题与总数徽标 flex 垂直居中齐平，下方为 ID 过滤框 */
.history-list-header {
  padding: 12px 12px 8px;
}

.history-list-title {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: 10px;
}

/* 列表项会话 ID 行：小字灰色，点击复制 */
.session-id-row {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 2px;
  font-size: var(--font-size-xs);
  color: var(--color-text-placeholder);
  font-variant-numeric: tabular-nums;
  cursor: pointer;
  user-select: none;

  &:hover {
    color: var(--color-primary);
  }

  .session-id-copy {
    font-size: 11px;
  }

  .session-id-done {
    font-size: 11px;
    color: #52c41a;
  }
}
.loading-messages {
  width: 100%;
  height: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
}

/* 会话 ID 信息条：贴详情面板顶部，随内容滚动保持可见 */
.history-detail-header {
  position: sticky;
  top: 0;
  z-index: 1;
  display: flex;
  justify-content: flex-end;
  padding: 6px 12px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(4px);
  border-bottom: 1px solid var(--color-border-extra-light);
}

.history-session-id {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
  font-variant-numeric: tabular-nums;
  cursor: pointer;
  user-select: none;
  transition: color 0.15s ease;

  &:hover {
    color: var(--color-primary);
  }

  .history-session-id-done {
    color: #52c41a;
  }
}
</style>
