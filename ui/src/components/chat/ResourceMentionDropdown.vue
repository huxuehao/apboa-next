<script setup lang="ts">
/**
 * 资源 @mention 下拉组件
 * 支持搜索优先的双页面交互：
 *  - 主页零关键词：最近使用 + 工作空间文件 + 资源分类入口
 *  - 主页有关键词：跨工具/技能/MCP/文件的相关性搜索
 *  - 详情页：分类内搜索；MCP 零关键词按服务折叠浏览
 * 双页面间通过方向感知的滑动动画切换
 *
 * @component
 */
import { computed, nextTick, onBeforeUpdate, ref, watch } from 'vue'
import {
  ArrowLeftOutlined,
  ClockCircleOutlined,
  DownOutlined,
  RightOutlined
} from '@ant-design/icons-vue'
import type { FlatFileItem } from '@/composables/chat/useWorkspaceFiles'
import type {
  AgentMcpToolItem,
  AgentSkillItem,
  AgentToolItem,
  MentionResourceItem,
  ResourceKind
} from '@/types/chat-mention'
import {
  RESOURCE_CATEGORY_REGISTRY,
  toResourceItem,
  type MainCategorySection,
  useResourceCategories
} from '@/composables/chat/useResourceCategories'
import {
  mentionItemKey,
  pushMentionRecent,
  resolveMentionRecents,
  searchMentionItems
} from '@/utils/chat/mentionPicker'

const props = withDefaults(
  defineProps<{
    /** 是否显示 */
    visible: boolean
    /** 工作空间文件列表 */
    workspaceFiles?: FlatFileItem[]
    /** Agent 工具列表 */
    agentTools?: AgentToolItem[]
    /** Agent 技能列表 */
    agentSkills?: AgentSkillItem[]
    /** Agent MCP 工具列表（按 server 分组拍平，带 server 标注） */
    agentMcpTools?: AgentMcpToolItem[]
    /** 过滤关键词（来自 @ 后输入） */
    keyword?: string
    /** 最近使用隔离键（账号 + agent），为空时不持久化 */
    recentScope?: string
  }>(),
  {
    workspaceFiles: () => [],
    agentTools: () => [],
    agentSkills: () => [],
    agentMcpTools: () => [],
    keyword: '',
    recentScope: ''
  }
)

const emit = defineEmits<{
  (e: 'select', item: MentionResourceItem): void
  (e: 'close'): void
}>()

const RECENT_LIMIT = 6
const RECENT_STORAGE_PREFIX = 'chat-mention-recents:'

/**
 * 类目数据：平铺项（工作空间文件）+ 底部文件夹区段
 */
const { flatItems, folderSections } = useResourceCategories({
  workspaceFiles: computed(() => props.workspaceFiles),
  agentTools: computed(() => props.agentTools),
  agentSkills: computed(() => props.agentSkills),
  agentMcpTools: computed(() => props.agentMcpTools)
})

/** 所有资源的统一扁平目录：全局搜索与最近使用共用。 */
const allItems = computed<MentionResourceItem[]>(() => [
  ...flatItems.value,
  ...folderSections.value.flatMap((section) => section.items)
])

const queryActive = computed(() => !!(props.keyword || '').trim())
const recentKeys = ref<string[]>([])

const recentStorageKey = computed(() =>
  props.recentScope ? `${RECENT_STORAGE_PREFIX}${props.recentScope}` : '')

function loadRecentKeys() {
  const key = recentStorageKey.value
  if (!key) {
    recentKeys.value = []
    return
  }
  try {
    const parsed = JSON.parse(localStorage.getItem(key) || '[]')
    recentKeys.value = Array.isArray(parsed)
      ? parsed.filter((item): item is string => typeof item === 'string').slice(0, RECENT_LIMIT)
      : []
  } catch {
    recentKeys.value = []
  }
}

function persistRecentKeys() {
  if (!recentStorageKey.value) return
  try {
    localStorage.setItem(recentStorageKey.value, JSON.stringify(recentKeys.value))
  } catch {
    // 隐私模式或存储配额异常不影响资源选择主流程。
  }
}

function selectItem(item: MentionResourceItem) {
  if (item.kind !== 'workspace-file') {
    recentKeys.value = pushMentionRecent(recentKeys.value, item, RECENT_LIMIT)
    persistRecentKeys()
  }
  emit('select', item)
}

watch(recentStorageKey, loadRecentKeys, { immediate: true })

const recentItems = computed(() =>
  resolveMentionRecents(recentKeys.value, allItems.value).slice(0, RECENT_LIMIT))
const recentMainItems = computed(() =>
  recentItems.value.filter((item) => item.kind !== 'workspace-file'))
const recentMcpItems = computed(() =>
  recentItems.value.filter((item) => item.kind === 'agent-mcp'))
const globalSearchItems = computed(() =>
  searchMentionItems(allItems.value, props.keyword || '', recentKeys.value))

/** 当前视图：主页 / 详情页 */
const view = ref<'main' | 'detail'>('main')
/** 当前进入的详情类目 */
const detailKind = ref<ResourceKind | null>(null)
/** 切换方向，控制滑动动画 */
const direction = ref<'forward' | 'back'>('forward')

/** 主页：高亮项索引（覆盖文件项 + 文件夹项） */
const mainActiveIndex = ref(0)
/** 详情页：高亮项索引 */
const detailActiveIndex = ref(0)

const mainListRef = ref<HTMLDivElement | null>(null)
const detailListRef = ref<HTMLDivElement | null>(null)
const mainItemRefs = ref<HTMLDivElement[]>([])
const detailItemRefs = ref<HTMLDivElement[]>([])

onBeforeUpdate(() => {
  mainItemRefs.value = []
  detailItemRefs.value = []
})

/**
 * 主页：关键词过滤后的工作空间文件项
 */
const filteredFlatItems = computed<MentionResourceItem[]>(() => {
  const kw = (props.keyword || '').trim().toLowerCase()
  if (!kw) return flatItems.value
  return flatItems.value.filter((item) => {
    const inName = item.name.toLowerCase().includes(kw)
    const inDesc = (item.description || '').toLowerCase().includes(kw)
    return inName || inDesc
  })
})

/**
 * 主页底部文件夹区段（按注册表 order 升序）
 */
const visibleFolderSections = computed<MainCategorySection[]>(() => folderSections.value)

/**
 * 主页"行项"统一结构（用于键盘导航）
 *  - flat 类型：可直接选中的资源项
 *  - folder 类型：进入详情页的文件夹入口
 */
type MainEntry =
  | { kind: 'flat'; item: MentionResourceItem }
  | { kind: 'folder'; section: MainCategorySection }

const mainEntries = computed<MainEntry[]>(() => {
  if (queryActive.value) {
    return globalSearchItems.value.map((item) => ({ kind: 'flat' as const, item }))
  }
  const list: MainEntry[] = []
  for (const item of recentMainItems.value) {
    list.push({ kind: 'flat', item })
  }
  for (const item of filteredFlatItems.value) {
    list.push({ kind: 'flat', item })
  }
  for (const section of visibleFolderSections.value) {
    list.push({ kind: 'folder', section })
  }
  return list
})

/**
 * 详情页当前类目元数据
 */
const detailMeta = computed(() => {
  if (!detailKind.value) return null
  return RESOURCE_CATEGORY_REGISTRY[detailKind.value]
})

const detailCountLabel = computed(() => {
  const count = filteredDetailItems.value.length
  return queryActive.value && count >= 30 ? `前 ${count}` : String(count)
})

/**
 * 详情页：关键词过滤后的资源项
 */
const filteredDetailItems = computed<MentionResourceItem[]>(() => {
  if (!detailKind.value) return []
  const list = pickRawListByKind(detailKind.value)
  const items = list.map((raw) => toResourceItem(detailKind.value!, raw))
  if (!queryActive.value) return items
  return searchMentionItems(items, props.keyword || '', recentKeys.value)
})

/**
 * MCP 详情页：按 serverId 分组，每项携带在 filteredDetailItems 中的全局 index，
 * 使分组渲染与既有的扁平键盘导航/滚动索引对齐（详情页导航仍以 filteredDetailItems 为准）。
 */
const detailGroups = computed(() => {
  if (detailKind.value !== 'agent-mcp') return []
  const groups = new Map<string, {
    serverId: string
    serverName: string
    items: Array<{ item: MentionResourceItem; index: number }>
  }>()
  filteredDetailItems.value.forEach((item, index) => {
    const raw = item.raw as AgentMcpToolItem
    const sid = raw.serverId
    if (!groups.has(sid)) {
      groups.set(sid, { serverId: sid, serverName: raw.serverName, items: [] })
    }
    groups.get(sid)!.items.push({ item, index })
  })
  return Array.from(groups.values())
})

const expandedMcpServerId = ref<string | null>(null)

type McpBrowseEntry =
  | { type: 'item'; item: MentionResourceItem }
  | { type: 'server'; serverId: string }

/**
 * MCP 零关键词浏览模型：最近使用在前，服务默认折叠且同一时刻只展开一个。
 * 每个可操作行携带连续 index，保证鼠标渲染和键盘导航使用同一顺序。
 */
const mcpBrowseModel = computed(() => {
  const entries: McpBrowseEntry[] = []
  const recent = recentMcpItems.value.map((item) => {
    const index = entries.length
    entries.push({ type: 'item', item })
    return { item, index }
  })
  const groups = detailGroups.value.map((group) => {
    const serverIndex = entries.length
    entries.push({ type: 'server', serverId: group.serverId })
    const tools = expandedMcpServerId.value === group.serverId
      ? group.items.map(({ item }) => {
          const index = entries.length
          entries.push({ type: 'item', item })
          return { item, index }
        })
      : []
    return { ...group, serverIndex, tools }
  })
  return { entries, recent, groups }
})

function itemServerName(item: MentionResourceItem): string {
  if (item.kind !== 'agent-mcp') return ''
  return (item.raw as AgentMcpToolItem | undefined)?.serverName || ''
}

function itemSecondary(item: MentionResourceItem): string {
  return [itemServerName(item), item.description].filter(Boolean).join(' · ')
}

function itemTypeLabel(item: MentionResourceItem): string {
  return RESOURCE_CATEGORY_REGISTRY[item.kind].label
}

function itemDomKey(item: MentionResourceItem): string {
  return mentionItemKey(item)
}

/**
 * 按 kind 取出原始数据列表
 */
function pickRawListByKind(
  kind: ResourceKind
): Array<FlatFileItem | AgentToolItem | AgentSkillItem | AgentMcpToolItem> {
  if (kind === 'workspace-file') return props.workspaceFiles
  if (kind === 'agent-tool') return props.agentTools
  if (kind === 'agent-skill') return props.agentSkills
  if (kind === 'agent-mcp') return props.agentMcpTools
  return []
}

/**
 * 进入文件夹详情页
 *
 * @param section 类目区段
 */
const enterFolder = (section: MainCategorySection) => {
  direction.value = 'forward'
  detailKind.value = section.kind
  detailActiveIndex.value = 0
  expandedMcpServerId.value = null
  // 在下一帧再切换 view，确保 direction 已经被订阅到 transition name
  nextTick(() => {
    view.value = 'detail'
    scrollToActive('detail')
  })
}

/**
 * 返回主页
 */
const backToMain = () => {
  direction.value = 'back'
  nextTick(() => {
    view.value = 'main'
    scrollToActive('main')
  })
}

/**
 * 主页确认选中
 */
const confirmMainSelection = () => {
  const entries = mainEntries.value
  if (entries.length === 0) {
    emit('close')
    return
  }
  const idx = clampIndex(mainActiveIndex.value, entries.length)
  const entry = entries[idx]
  if (!entry) return
  if (entry.kind === 'flat') {
    selectItem(entry.item)
  } else {
    enterFolder(entry.section)
  }
}

/**
 * 详情页确认选中
 */
const confirmDetailSelection = () => {
  if (detailKind.value === 'agent-mcp' && !queryActive.value) {
    const entries = mcpBrowseModel.value.entries
    if (entries.length === 0) return
    const idx = clampIndex(detailActiveIndex.value, entries.length)
    const entry = entries[idx]
    if (entry) handleMcpBrowseEntry(entry, idx)
    return
  }
  const items = filteredDetailItems.value
  if (items.length === 0) return
  const idx = clampIndex(detailActiveIndex.value, items.length)
  const target = items[idx]
  if (target) selectItem(target)
}

/**
 * 主页点击行项
 */
const handleMainEntryClick = (entry: MainEntry, index: number) => {
  mainActiveIndex.value = index
  if (entry.kind === 'flat') {
    selectItem(entry.item)
  } else {
    enterFolder(entry.section)
  }
}

/**
 * 详情页点击行项
 */
const handleDetailItemClick = (item: MentionResourceItem, index: number) => {
  detailActiveIndex.value = index
  selectItem(item)
}

function toggleMcpServer(serverId: string) {
  expandedMcpServerId.value = expandedMcpServerId.value === serverId ? null : serverId
  nextTick(() => {
    const group = mcpBrowseModel.value.groups.find((candidate) => candidate.serverId === serverId)
    if (group) {
      detailActiveIndex.value = group.serverIndex
      scrollToActive('detail')
    }
  })
}

function handleMcpBrowseEntry(entry: McpBrowseEntry, index: number) {
  detailActiveIndex.value = index
  if (entry.type === 'server') {
    toggleMcpServer(entry.serverId)
  } else {
    selectItem(entry.item)
  }
}

/**
 * 安全索引
 */
function clampIndex(idx: number, len: number): number {
  if (len === 0) return 0
  return Math.max(0, Math.min(idx, len - 1))
}

/**
 * 滚动到当前高亮项
 */
const scrollToActive = (which: 'main' | 'detail') => {
  nextTick(() => {
    if (which === 'main') {
      const el = mainItemRefs.value[mainActiveIndex.value]
      if (el) el.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
    } else {
      const el = detailItemRefs.value[detailActiveIndex.value]
      if (el) el.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
    }
  })
}

/**
 * 处理键盘导航
 *
 * @param e 键盘事件
 */
const handleKeydown = (e: KeyboardEvent) => {
  if (!props.visible) return

  if (view.value === 'main') {
    handleMainKeydown(e)
  } else {
    handleDetailKeydown(e)
  }
}

/**
 * 主页键盘
 */
const handleMainKeydown = (e: KeyboardEvent) => {
  const entries = mainEntries.value
  if (e.key === 'Escape') {
    e.preventDefault()
    emit('close')
    return
  }
  if (entries.length === 0 && e.key === 'Enter') {
    e.preventDefault()
    return
  }
  if (entries.length === 0) return

  if (e.key === 'ArrowDown') {
    e.preventDefault()
    mainActiveIndex.value = (mainActiveIndex.value + 1) % entries.length
    scrollToActive('main')
  } else if (e.key === 'ArrowUp') {
    e.preventDefault()
    mainActiveIndex.value =
      (mainActiveIndex.value - 1 + entries.length) % entries.length
    scrollToActive('main')
  } else if (e.key === 'Enter') {
    e.preventDefault()
    confirmMainSelection()
  } else if (e.key === 'ArrowRight') {
    // 右箭头：若高亮文件夹则进入
    const idx = clampIndex(mainActiveIndex.value, entries.length)
    const entry = entries[idx]
    if (entry && entry.kind === 'folder') {
      e.preventDefault()
      enterFolder(entry.section)
    }
  }
}

/**
 * 详情页键盘
 */
const handleDetailKeydown = (e: KeyboardEvent) => {
  const mcpBrowse = detailKind.value === 'agent-mcp' && !queryActive.value
  const length = mcpBrowse
    ? mcpBrowseModel.value.entries.length
    : filteredDetailItems.value.length
  if (e.key === 'Escape') {
    e.preventDefault()
    backToMain()
    return
  }
  if (e.key === 'ArrowLeft') {
    e.preventDefault()
    if (mcpBrowse && expandedMcpServerId.value) {
      const expandedGroup = mcpBrowseModel.value.groups
        .find((group) => group.serverId === expandedMcpServerId.value)
      if (expandedGroup) detailActiveIndex.value = expandedGroup.serverIndex
      expandedMcpServerId.value = null
      nextTick(() => scrollToActive('detail'))
    } else {
      backToMain()
    }
    return
  }
  if (length === 0 && e.key === 'Enter') {
    e.preventDefault()
    return
  }
  if (length === 0) return

  if (e.key === 'ArrowDown') {
    e.preventDefault()
    detailActiveIndex.value = (detailActiveIndex.value + 1) % length
    scrollToActive('detail')
  } else if (e.key === 'ArrowUp') {
    e.preventDefault()
    detailActiveIndex.value =
      (detailActiveIndex.value - 1 + length) % length
    scrollToActive('detail')
  } else if (e.key === 'Enter') {
    e.preventDefault()
    confirmDetailSelection()
  } else if (e.key === 'ArrowRight' && mcpBrowse) {
    const entry = mcpBrowseModel.value.entries[clampIndex(detailActiveIndex.value, length)]
    if (entry?.type === 'server' && expandedMcpServerId.value !== entry.serverId) {
      e.preventDefault()
      toggleMcpServer(entry.serverId)
    }
  }
}

/**
 * 关键词变化重置高亮
 */
watch(
  () => props.keyword,
  () => {
    mainActiveIndex.value = 0
    detailActiveIndex.value = 0
  }
)

/**
 * 显隐变化时重置全部状态
 */
watch(
  () => props.visible,
  (val) => {
    if (val) {
      mainActiveIndex.value = 0
      detailActiveIndex.value = 0
      view.value = 'main'
      detailKind.value = null
      expandedMcpServerId.value = null
      loadRecentKeys()
    }
  }
)

/**
 * 暴露给父组件
 */
defineExpose({
  handleKeydown
})

/**
 * Transition name 计算（方向感知）
 */
const transitionName = computed(() =>
  direction.value === 'forward' ? 'slide-forward' : 'slide-back'
)
</script>

<template>
  <Transition name="mention-dropdown">
    <div
      v-if="visible"
      class="resource-mention-dropdown"
      @mousedown.prevent
    >
      <div class="dropdown-stack">
        <Transition :name="transitionName">
          <!-- 主页 -->
          <div v-if="view === 'main'" key="main" class="dropdown-page main-page">
            <!-- 有查询词：跨工作空间文件、工具、技能、MCP 工具统一搜索并按相关性扁平展示。 -->
            <div v-if="queryActive" ref="mainListRef" class="main-files-area search-results-area">
              <div v-if="globalSearchItems.length === 0" class="dropdown-empty">
                <span>未找到匹配资源</span>
              </div>
              <div
                v-for="(item, index) in globalSearchItems"
                :key="`search-${itemDomKey(item)}`"
                :ref="(el) => { if (el) mainItemRefs[index] = el as HTMLDivElement }"
                class="dropdown-item detail-item"
                :class="{ active: index === mainActiveIndex }"
                @click="handleMainEntryClick({ kind: 'flat', item }, index)"
                @mouseenter="mainActiveIndex = index"
              >
                <span class="dropdown-item-icon">
                  <component :is="RESOURCE_CATEGORY_REGISTRY[item.kind].renderItemIcon(item)" />
                </span>
                <div class="dropdown-item-content detail-item-content">
                  <span class="detail-item-name" :title="item.alias || item.name">
                    {{ item.alias || item.name }}
                  </span>
                  <span v-if="itemSecondary(item)" class="detail-item-desc" :title="itemSecondary(item)">
                    {{ itemSecondary(item) }}
                  </span>
                </div>
                <span class="resource-kind-badge">{{ itemTypeLabel(item) }}</span>
              </div>
            </div>

            <!-- 零关键词：最近使用优先，现有工作空间文件保持原入口，分类固定在底部。 -->
            <template v-else>
              <div
                v-if="recentMainItems.length > 0 || filteredFlatItems.length > 0"
                ref="mainListRef"
                class="main-files-area"
              >
                <div v-if="recentMainItems.length > 0" class="dropdown-section-title">
                  <ClockCircleOutlined />
                  <span>最近使用</span>
                </div>
                <div
                  v-for="(item, index) in recentMainItems"
                  :key="`recent-${itemDomKey(item)}`"
                  :ref="(el) => { if (el) mainItemRefs[index] = el as HTMLDivElement }"
                  class="dropdown-item compact-resource-item"
                  :class="{ active: index === mainActiveIndex }"
                  @click="handleMainEntryClick({ kind: 'flat', item }, index)"
                  @mouseenter="mainActiveIndex = index"
                >
                  <span class="dropdown-item-icon">
                    <component :is="RESOURCE_CATEGORY_REGISTRY[item.kind].renderItemIcon(item)" />
                  </span>
                  <div class="dropdown-item-content">
                    <span class="dropdown-item-name" :title="item.alias || item.name">
                      {{ item.alias || item.name }}
                    </span>
                    <span class="dropdown-item-folder" :title="itemServerName(item) || itemTypeLabel(item)">
                      {{ itemServerName(item) || itemTypeLabel(item) }}
                    </span>
                  </div>
                </div>

                <div v-if="filteredFlatItems.length > 0" class="dropdown-section-title">
                  <span>工作空间文件</span>
                </div>
                <div
                  v-for="(item, index) in filteredFlatItems"
                  :key="`flat-${itemDomKey(item)}`"
                  :ref="(el) => {
                    const idx = recentMainItems.length + index
                    if (el) mainItemRefs[idx] = el as HTMLDivElement
                  }"
                  class="dropdown-item"
                  :class="{ active: recentMainItems.length + index === mainActiveIndex }"
                  @click="handleMainEntryClick({ kind: 'flat', item }, recentMainItems.length + index)"
                  @mouseenter="mainActiveIndex = recentMainItems.length + index"
                >
                  <span class="dropdown-item-icon">
                    <component :is="RESOURCE_CATEGORY_REGISTRY[item.kind].renderItemIcon(item)" />
                  </span>
                  <div class="dropdown-item-content">
                    <span class="dropdown-item-name" :title="item.name">{{ item.name }}</span>
                    <span v-if="item.description" class="dropdown-item-folder" :title="item.description">
                      {{ item.description }}
                    </span>
                  </div>
                </div>
              </div>

              <div v-if="visibleFolderSections.length > 0" class="main-folders-area">
                <div class="dropdown-section-title"><span>浏览分类</span></div>
                <div
                  v-for="(section, sIdx) in visibleFolderSections"
                  :key="`folder-${section.kind}`"
                  :ref="(el) => {
                    const idx = recentMainItems.length + filteredFlatItems.length + sIdx
                    if (el) mainItemRefs[idx] = el as HTMLDivElement
                  }"
                  class="dropdown-folder-item"
                  :class="{
                    active: recentMainItems.length + filteredFlatItems.length + sIdx === mainActiveIndex
                  }"
                  @click="handleMainEntryClick(
                    { kind: 'folder', section },
                    recentMainItems.length + filteredFlatItems.length + sIdx
                  )"
                  @mouseenter="mainActiveIndex = recentMainItems.length + filteredFlatItems.length + sIdx"
                >
                  <span class="folder-icon"><component :is="section.meta.folderIcon" /></span>
                  <span class="folder-label">{{ section.meta.label }}</span>
                  <span class="folder-count">{{ section.items.length }} 个</span>
                  <RightOutlined class="folder-chevron" />
                </div>
              </div>
            </template>
          </div>

          <!-- 详情页 -->
          <div v-else key="detail" class="dropdown-page detail-page">
            <div class="detail-header">
              <span class="detail-back-btn" @click="backToMain">
                <ArrowLeftOutlined />
              </span>
              <span class="detail-title">
                {{ detailMeta?.label }}
              </span>
              <span class="detail-count">{{ detailCountLabel }}</span>
            </div>
            <div ref="detailListRef" class="detail-list">
              <div
                v-if="detailKind === 'agent-mcp' && !queryActive
                  ? mcpBrowseModel.entries.length === 0
                  : filteredDetailItems.length === 0"
                class="dropdown-empty"
              >
                <span>{{ queryActive ? '未找到匹配资源' : '暂无可用项' }}</span>
              </div>

              <!-- MCP 零关键词：最近使用 + 默认折叠的服务目录，同一时间只展开一个服务。 -->
              <template v-else-if="detailKind === 'agent-mcp' && !queryActive">
                <template v-if="mcpBrowseModel.recent.length > 0">
                  <div class="dropdown-section-title detail-section-title">
                    <ClockCircleOutlined />
                    <span>最近使用</span>
                  </div>
                  <div
                    v-for="{ item, index } in mcpBrowseModel.recent"
                    :key="`mcp-recent-${itemDomKey(item)}`"
                    :ref="(el) => { if (el) detailItemRefs[index] = el as HTMLDivElement }"
                    class="dropdown-item detail-item"
                    :class="{ active: index === detailActiveIndex }"
                    @click="handleMcpBrowseEntry({ type: 'item', item }, index)"
                    @mouseenter="detailActiveIndex = index"
                  >
                    <div class="dropdown-item-content detail-item-content">
                      <span class="detail-item-name" :title="item.name">{{ item.name }}</span>
                      <span v-if="itemSecondary(item)" class="detail-item-desc" :title="itemSecondary(item)">
                        {{ itemSecondary(item) }}
                      </span>
                    </div>
                  </div>
                </template>

                <div v-if="mcpBrowseModel.groups.length > 0" class="dropdown-section-title detail-section-title">
                  <span>按服务浏览</span>
                </div>
                <div
                  v-for="group in mcpBrowseModel.groups"
                  :key="`mcp-grp-${group.serverId}`"
                  class="detail-group"
                >
                  <div
                    :ref="(el) => { if (el) detailItemRefs[group.serverIndex] = el as HTMLDivElement }"
                    class="dropdown-folder-item mcp-server-row"
                    :class="{ active: group.serverIndex === detailActiveIndex }"
                    @click="handleMcpBrowseEntry({ type: 'server', serverId: group.serverId }, group.serverIndex)"
                    @mouseenter="detailActiveIndex = group.serverIndex"
                  >
                    <span class="folder-icon">
                      <DownOutlined v-if="expandedMcpServerId === group.serverId" />
                      <RightOutlined v-else />
                    </span>
                    <span class="folder-label">{{ group.serverName }}</span>
                    <span class="folder-count">{{ group.items.length }} 个</span>
                  </div>
                  <div
                    v-for="{ item, index } in group.tools"
                    :key="`mcp-tool-${itemDomKey(item)}`"
                    :ref="(el) => { if (el) detailItemRefs[index] = el as HTMLDivElement }"
                    class="dropdown-item detail-item mcp-tool-row"
                    :class="{ active: index === detailActiveIndex }"
                    @click="handleMcpBrowseEntry({ type: 'item', item }, index)"
                    @mouseenter="detailActiveIndex = index"
                  >
                    <div class="dropdown-item-content detail-item-content">
                      <span class="detail-item-name" :title="item.name">{{ item.name }}</span>
                      <span v-if="item.description" class="detail-item-desc" :title="item.description">
                        {{ item.description }}
                      </span>
                    </div>
                  </div>
                </div>
              </template>

              <!-- 搜索态（含 MCP）与非 MCP 详情：相关性排序后的扁平结果。 -->
              <template v-else>
                <div
                  v-for="(item, index) in filteredDetailItems"
                  :key="`detail-${itemDomKey(item)}`"
                  :ref="(el) => { if (el) detailItemRefs[index] = el as HTMLDivElement }"
                  class="dropdown-item detail-item"
                  :class="{ active: index === detailActiveIndex }"
                  @click="handleDetailItemClick(item, index)"
                  @mouseenter="detailActiveIndex = index"
                >
                  <div class="dropdown-item-content detail-item-content">
                    <span class="detail-item-name" :title="item.alias || item.name">
                      {{ item.alias || item.name }}
                    </span>
                    <span
                      v-if="itemSecondary(item)"
                      class="detail-item-desc"
                      :title="itemSecondary(item)"
                    >
                      {{ itemSecondary(item) }}
                    </span>
                  </div>
                </div>
              </template>
            </div>
          </div>
        </Transition>
      </div>
    </div>
  </Transition>
</template>

<style scoped lang="scss">
.resource-mention-dropdown {
  position: absolute;
  z-index: 100;
  bottom: calc(100% + 12px);
  left: 0;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 -5px 24px rgba(0, 0, 0, 0.12);
  border: 1px solid var(--color-border-light);
  width: 100%;
  max-height: min(420px, 62vh);
  max-height: min(420px, 62dvh);
  overflow: hidden;
  text-align: left;
  display: flex;
  flex-direction: column;
}

// 使用 grid 单元格叠加，使“当前可见 page”自然擑起容器高度
.dropdown-stack {
  position: relative;
  display: grid;
  grid-template-columns: 1fr;
  grid-template-rows: minmax(0, max-content);
  max-height: min(370px, calc(62vh - 50px));
  max-height: min(370px, calc(62dvh - 50px));
  overflow: hidden;
}

.dropdown-page {
  grid-row: 1;
  grid-column: 1;
  display: flex;
  flex-direction: column;
  background: #fff;
  min-height: 0;
  // 限制 page 高度不超过 stack，保证内部 flex:1 区域可滑动
  max-height: min(370px, calc(62vh - 50px));
  max-height: min(370px, calc(62dvh - 50px));
}

// 动画期间：离场页面脱离文档流，不参与 grid cell 高度计算
// 这样进场页决定容器高度，避免切换时高度被两页 max 擑高
.slide-forward-leave-active,
.slide-back-leave-active {
  position: absolute;
  inset: 0;
}

/* 主页布局：文件区可滚动，文件夹区固定底部 */
.main-page {
  /* 容器即 flex column */
}

.main-files-area {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 6px;
  border-bottom: 1px solid #ecf0f2;
  scrollbar-width: thin;
  scrollbar-color: var(--color-border-light) transparent;

  &::-webkit-scrollbar {
    width: 6px;
  }
  &::-webkit-scrollbar-track {
    background: transparent;
  }
  &::-webkit-scrollbar-thumb {
    background: var(--color-border-light);
    border-radius: 3px;
  }
  &::-webkit-scrollbar-thumb:hover {
    background: var(--color-text-placeholder);
  }
}

.search-results-area {
  border-bottom: 0;
}

.dropdown-section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 10px 4px;
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-placeholder);
  user-select: none;
}

.detail-section-title {
  padding-top: 8px;
}

.main-folders-area {
  flex-shrink: 0;
  padding: 6px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.folder-chevron {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--color-text-placeholder);
}

/* 详情页布局：头部固定不滚，内容区滚动 */
.detail-page {
  /* flex column */
}

.detail-header {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  user-select: none;
  border-bottom: 1px solid #ecf0f2;
}

.detail-back-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 6px;
  cursor: pointer;
  color: var(--color-text-secondary);
  transition: background-color 0.15s ease, color 0.15s ease;

  &:hover {
    background: rgba(116, 116, 116, 0.08);
    color: var(--color-text-primary);
  }
}

.detail-title {
  flex: 1;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail-title-icon {
  font-size: 14px;
  color: var(--color-text-secondary);
}

.detail-count {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--color-text-placeholder);
  background: rgba(0, 0, 0, 0.04);
  padding: 1px 8px;
  border-radius: 10px;
}

.detail-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 6px;
  scrollbar-width: thin;
  scrollbar-color: var(--color-border-light) transparent;

  &::-webkit-scrollbar {
    width: 6px;
  }
  &::-webkit-scrollbar-track {
    background: transparent;
  }
  &::-webkit-scrollbar-thumb {
    background: var(--color-border-light);
    border-radius: 3px;
  }
  &::-webkit-scrollbar-thumb:hover {
    background: var(--color-text-placeholder);
  }
}

/* 共用：行项 */
.dropdown-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 20px 16px;
  color: var(--color-text-placeholder);
  font-size: 14px;
}

.dropdown-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background-color 0.15s ease;
  min-width: 0;

  &:hover,
  &.active {
    background-color: rgba(116, 116, 116, 0.08);
  }
}

.dropdown-item-icon {
  flex-shrink: 0;
  font-size: 18px;
  color: var(--color-text-secondary);
  display: inline-flex;
  align-items: center;
}

.dropdown-item-content {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.dropdown-item-name {
  font-size: 14px;
  color: var(--color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex-shrink: 1;
  min-width: 0;
}

.dropdown-item-folder {
  font-size: 12px;
  color: var(--color-text-placeholder);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex-shrink: 0;
  max-width: 50%;
}

.compact-resource-item {
  padding-top: 7px;
  padding-bottom: 7px;
}

.resource-kind-badge {
  flex-shrink: 0;
  padding: 1px 7px;
  border-radius: 9px;
  background: rgba(116, 116, 116, 0.08);
  color: var(--color-text-placeholder);
  font-size: 11px;
  line-height: 18px;
}

/* 文件夹入口行 */
.dropdown-folder-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background-color 0.15s ease;
  min-width: 0;
  user-select: none;

  &:hover,
  &.active {
    background-color: rgba(116, 116, 116, 0.08);
  }
}

.folder-icon {
  flex-shrink: 0;
  font-size: 16px;
  color: var(--color-text-secondary);
  display: inline-flex;
  align-items: center;
}

.folder-label {
  flex: 1;
  font-size: 14px;
  color: var(--color-text-primary);
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.folder-count {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--color-text-placeholder);
  padding: 1px 8px;
  border-radius: 10px;
}

/* 详情页行项：上下结构（名称 + 描述） */
.detail-item {
  align-items: flex-start;
}

.detail-item-content {
  flex-direction: column;
  align-items: flex-start;
  justify-content: flex-start;
  gap: 2px;
}

.detail-group-title {
  padding: 6px 12px 2px;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-placeholder);
}

.mcp-server-row {
  margin-top: 1px;
}

.mcp-tool-row {
  margin-left: 22px;
  width: calc(100% - 22px);
}

.detail-item-name {
  font-size: 14px;
  color: var(--color-text-primary);
  font-weight: 500;
  width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail-item-desc {
  font-size: 12px;
  color: var(--color-text-placeholder);
  line-height: 1.4;
  width: 100%;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
  word-break: break-word;
}

/* 双页面方向感知滑动 */
.slide-forward-enter-active,
.slide-forward-leave-active,
.slide-back-enter-active,
.slide-back-leave-active {
  transition: transform 0.28s cubic-bezier(0.4, 0, 0.2, 1);
  will-change: transform;
}

/* forward：主页 -> 详情页 */
.slide-forward-enter-from {
  transform: translateX(100%);
}
.slide-forward-leave-to {
  transform: translateX(-100%);
}

/* back：详情页 -> 主页 */
.slide-back-enter-from {
  transform: translateX(-100%);
}
.slide-back-leave-to {
  transform: translateX(100%);
}

/* 整体下拉显隐 */
.mention-dropdown-enter-active,
.mention-dropdown-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}

.mention-dropdown-enter-from,
.mention-dropdown-leave-to {
  opacity: 0;
  transform: translateY(4px);
}

</style>
