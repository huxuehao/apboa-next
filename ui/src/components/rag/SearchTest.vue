/**
 * RAG检索测试组件（支持所有类型知识库）
 *
 * 通过统一检索通路（KnowledgeFactory -> Knowledge.retrieve）测试检索效果，
 * 高级参数按知识库类型动态展示，仅对本次测试生效（合并覆盖到检索配置，不落库）。
 *
 * @author huxuehao
 */
<script setup lang="ts">
import { ref, computed, reactive, watch, nextTick } from 'vue'
import { message } from 'ant-design-vue'
import {
  SearchOutlined,
  SettingOutlined,
  ClearOutlined,
  ClockCircleOutlined,
  LoadingOutlined,
  FileTextOutlined,
  NumberOutlined,
  ExperimentOutlined
} from '@ant-design/icons-vue'
import * as ragApi from '@/api/rag'
import { KbType } from '@/types'

const props = defineProps<{
  knowledgeBaseConfigId: string
  kbType: KbType
  name?: string | null
  description?: string | null
  /** 知识库已保存的检索配置（用于预填高级参数） */
  defaultRetrievalConfig?: Record<string, unknown> | null
}>()

const searchQuery = ref('')
const searching = ref(false)
const searchResults = ref<Record<string, unknown>[]>([])
const hasSearched = ref(false)

/** 是否展示高级参数面板 */
const showAdvanced = ref(false)

/** 检索历史 */
const searchHistory = ref<string[]>(
  loadSearchHistory()
)

/**
 * 是否有检索结果
 */
const resultCount = computed(() => searchResults.value.length)

/**
 * 阈值显示文案（RagFlow 使用“相似度阈值”，其余使用“分数阈值”）
 */
const thresholdLabel = computed(() =>
  props.kbType === KbType.RAGFLOW ? '相似度阈值' : '分数阈值'
)

/**
 * 当前生效的阈值（用于结果统计展示）
 */
const currentThreshold = computed(() =>
  props.kbType === KbType.RAGFLOW ? advanced.similarityThreshold : advanced.scoreThreshold
)

/**
 * 高级参数（类型感知，仅本次测试生效）
 */
const advanced = reactive({
  // 通用
  topK: 5,
  scoreThreshold: 0.5,
  // Dify
  retrievalMode: 'HYBRID_SEARCH',
  weights: 0.6,
  // RagFlow
  similarityThreshold: 0.2,
  vectorSimilarityWeight: 0.3,
  useKg: false,
  tocEnhance: false,
  rerankId: undefined as number | undefined,
  keyword: false,
  highlight: false,
  // 百炼
  denseSimilarityTopK: undefined as number | undefined,
  sparseSimilarityTopK: undefined as number | undefined
})

/**
 * 高级参数项定义
 */
interface AdvancedParam {
  key: string
  label: string
  kind: 'slider' | 'number' | 'switch' | 'select'
  min?: number
  max?: number
  step?: number
  options?: { label: string; value: string }[]
}

/**
 * 各类型高级参数配置
 */
const advancedParamsByType: Record<KbType, AdvancedParam[]> = {
  [KbType.LOCAL]: [
    { key: 'topK', label: 'Top K', kind: 'slider', min: 1, max: 100, step: 1 },
    { key: 'scoreThreshold', label: '分数阈值', kind: 'slider', min: 0, max: 1, step: 0.05 }
  ],
  [KbType.DIFY]: [
    { key: 'topK', label: 'Top K', kind: 'slider', min: 1, max: 100, step: 1 },
    { key: 'scoreThreshold', label: '分数阈值', kind: 'slider', min: 0, max: 1, step: 0.05 },
    {
      key: 'retrievalMode', label: '检索模式', kind: 'select',
      options: [
        { label: '混合检索', value: 'HYBRID_SEARCH' },
        { label: '向量检索', value: 'SEMANTIC_SEARCH' },
        { label: '关键词检索', value: 'KEYWORD_SEARCH' },
        { label: '全文检索', value: 'FULL_TEXT_SEARCH' }
      ]
    },
    { key: 'weights', label: '权重', kind: 'slider', min: 0, max: 1, step: 0.05 }
  ],
  [KbType.RAGFLOW]: [
    { key: 'topK', label: 'Top K', kind: 'slider', min: 1, max: 2048, step: 1 },
    { key: 'similarityThreshold', label: '相似度阈值', kind: 'slider', min: 0, max: 1, step: 0.05 },
    { key: 'vectorSimilarityWeight', label: '向量相似度权重', kind: 'slider', min: 0, max: 1, step: 0.05 },
    { key: 'useKg', label: '使用知识图谱', kind: 'switch' },
    { key: 'tocEnhance', label: '目录增强', kind: 'switch' },
    { key: 'rerankId', label: '重排序ID', kind: 'number', min: 0 },
    { key: 'keyword', label: '关键词搜索', kind: 'switch' },
    { key: 'highlight', label: '高亮显示', kind: 'switch' }
  ],
  [KbType.BAILIAN]: [
    { key: 'topK', label: 'Top K', kind: 'slider', min: 1, max: 1000, step: 1 },
    { key: 'scoreThreshold', label: '分数阈值', kind: 'slider', min: 0, max: 1, step: 0.05 },
    { key: 'denseSimilarityTopK', label: '稠密相似度Top K', kind: 'number', min: 1, max: 1000 },
    { key: 'sparseSimilarityTopK', label: '稀疏相似度Top K', kind: 'number', min: 1, max: 1000 }
  ]
}

/**
 * 当前类型的高级参数项
 */
const advancedParams = computed(() => advancedParamsByType[props.kbType] || [])

/**
 * 读取高级参数值
 */
function getParamValue(key: string): unknown {
  return (advanced as unknown as Record<string, unknown>)[key]
}

/**
 * 写入高级参数值
 */
function setParamValue(key: string, value: unknown) {
  ;(advanced as unknown as Record<string, unknown>)[key] = value
}

/**
 * 读取数值型高级参数
 */
function getNumberValue(key: string): number | undefined {
  const value = getParamValue(key)
  return typeof value === 'number' ? value : undefined
}

/**
 * 读取布尔型高级参数
 */
function getBooleanValue(key: string): boolean {
  return Boolean(getParamValue(key))
}

/**
 * 读取字符串型高级参数
 */
function getStringValue(key: string): string {
  const value = getParamValue(key)
  return typeof value === 'string' ? value : ''
}

/**
 * 用知识库已保存的检索配置预填高级参数
 */
function prefillAdvanced(cfg?: Record<string, unknown> | null) {
  if (!cfg) return
  if (typeof cfg.topK === 'number') advanced.topK = cfg.topK
  if (typeof cfg.scoreThreshold === 'number') advanced.scoreThreshold = cfg.scoreThreshold
  if (typeof cfg.retrievalMode === 'string') advanced.retrievalMode = cfg.retrievalMode
  if (typeof cfg.weights === 'number') advanced.weights = cfg.weights
  if (typeof cfg.similarityThreshold === 'number') advanced.similarityThreshold = cfg.similarityThreshold
  if (typeof cfg.vectorSimilarityWeight === 'number') advanced.vectorSimilarityWeight = cfg.vectorSimilarityWeight
  if (typeof cfg.useKg === 'boolean') advanced.useKg = cfg.useKg
  if (typeof cfg.tocEnhance === 'boolean') advanced.tocEnhance = cfg.tocEnhance
  if (typeof cfg.rerankId === 'number') advanced.rerankId = cfg.rerankId
  if (typeof cfg.keyword === 'boolean') advanced.keyword = cfg.keyword
  if (typeof cfg.highlight === 'boolean') advanced.highlight = cfg.highlight
  if (typeof cfg.denseSimilarityTopK === 'number') advanced.denseSimilarityTopK = cfg.denseSimilarityTopK
  if (typeof cfg.sparseSimilarityTopK === 'number') advanced.sparseSimilarityTopK = cfg.sparseSimilarityTopK
}

watch(() => props.defaultRetrievalConfig, (cfg) => prefillAdvanced(cfg), { immediate: true, deep: true })

/**
 * 构建本次测试的检索配置覆盖项
 */
function buildRetrievalConfig(): Record<string, unknown> {
  const cfg: Record<string, unknown> = { topK: advanced.topK }
  switch (props.kbType) {
    case KbType.LOCAL:
      cfg.scoreThreshold = advanced.scoreThreshold
      break
    case KbType.DIFY:
      cfg.scoreThreshold = advanced.scoreThreshold
      cfg.retrievalMode = advanced.retrievalMode
      cfg.weights = advanced.weights
      break
    case KbType.RAGFLOW:
      cfg.similarityThreshold = advanced.similarityThreshold
      cfg.vectorSimilarityWeight = advanced.vectorSimilarityWeight
      cfg.useKg = advanced.useKg
      cfg.tocEnhance = advanced.tocEnhance
      cfg.rerankId = advanced.rerankId
      cfg.keyword = advanced.keyword
      cfg.highlight = advanced.highlight
      break
    case KbType.BAILIAN:
      cfg.scoreThreshold = advanced.scoreThreshold
      cfg.denseSimilarityTopK = advanced.denseSimilarityTopK
      cfg.sparseSimilarityTopK = advanced.sparseSimilarityTopK
      break
  }
  return cfg
}

/**
 * 获取相似度分数颜色
 */
function getScoreColor(score: number): string {
  if (score >= 0.7) return '#52c41a'
  if (score >= 0.5) return '#1677ff'
  if (score >= 0.3) return '#faad14'
  return '#ff4d4f'
}

/**
 * 获取相似度分数百分比宽度
 */
function getScoreWidth(score: number): string {
  return Math.round((score || 0) * 100) + '%'
}

/**
 * 内容超出行数标记（key 为结果 id）
 */
const contentOverflow = ref<Set<string>>(new Set())

/**
 * 内容折叠状态（key 为结果 id）
 */
const contentCollapsed = ref<Set<string>>(new Set())

/**
 * 各卡片内容元素引用（key 为结果 id，非响应式，仅测量用）
 */
const contentEls = new Map<string, HTMLElement>()

/**
 * 结果卡片的稳定 key（优先结果 id，缺失时回退为下标）
 */
function resultKey(result: Record<string, unknown>, index: number): string {
  return result.id != null ? String(result.id) : String(index)
}

/**
 * 内容是否超行（超行时展示展开/收起入口）
 */
function isContentOverflow(key: string): boolean {
  return contentOverflow.value.has(key)
}

/**
 * 内容是否处于折叠状态
 */
function isContentCollapsed(key: string): boolean {
  return contentCollapsed.value.has(key)
}

/**
 * 切换内容展开/折叠
 */
function toggleContent(key: string) {
  const next = new Set(contentCollapsed.value)
  if (next.has(key)) {
    next.delete(key)
  } else {
    next.add(key)
  }
  contentCollapsed.value = next
}

/**
 * 收集卡片内容元素（ref 回调，仅存取元素，不在渲染期改动响应式状态）
 */
function collectContentEl(el: unknown, key: string) {
  if (el) {
    contentEls.set(key, el as HTMLElement)
  } else {
    contentEls.delete(key)
  }
}

/**
 * 检测各卡片内容是否超过默认行数（3 行）：
 * 用“未折叠时的完整高度”对比“套用折叠样式后的 3 行高度”，超行则标记为可展开并默认折叠。
 * 在渲染完成后（flush: post + nextTick）执行，避免在渲染期改动响应式状态。
 */
async function measureContentOverflow() {
  await nextTick()
  contentEls.forEach((el, key) => {
    if (contentOverflow.value.has(key)) return
    const naturalHeight = el.scrollHeight
    el.classList.add('search-result-content-collapsed')
    const clampedHeight = el.clientHeight
    el.classList.remove('search-result-content-collapsed')
    if (naturalHeight > clampedHeight) {
      contentOverflow.value = new Set(contentOverflow.value).add(key)
      contentCollapsed.value = new Set(contentCollapsed.value).add(key)
    }
  })
}

/**
 * 检索结果变化后重新检测内容超行
 */
watch(searchResults, () => {
  measureContentOverflow()
}, { flush: 'post' })

/**
 * 重置内容折叠状态（新检索或清空结果时调用）
 */
function resetContentStates() {
  contentOverflow.value = new Set()
  contentCollapsed.value = new Set()
}

/**
 * 执行检索
 */
async function handleSearch() {
  if (!searchQuery.value.trim()) {
    message.warning('请输入检索内容')
    return
  }
  if (!props.knowledgeBaseConfigId) {
    message.warning('知识库配置ID不存在')
    return
  }
  if (!props.kbType) {
    message.warning('知识库类型不存在')
    return
  }

  searching.value = true
  hasSearched.value = true

  try {
    const response = await ragApi.search({
      knowledgeBaseConfigId: props.knowledgeBaseConfigId,
      kbType: props.kbType,
      query: searchQuery.value,
      retrievalConfig: buildRetrievalConfig()
    })
    searchResults.value = response.data.data || []
    resetContentStates()
    addToHistory(searchQuery.value)
  } finally {
    searching.value = false
  }
}

/**
 * 加载检索历史
 */
function loadSearchHistory(): string[] {
  try {
    const stored = localStorage.getItem('rag_search_history')
    return stored ? JSON.parse(stored) : []
  } catch {
    return []
  }
}

/**
 * 保存检索历史
 */
function saveSearchHistory(history: string[]) {
  localStorage.setItem('rag_search_history', JSON.stringify(history))
}

/**
 * 添加到检索历史
 */
function addToHistory(query: string) {
  const trimmed = query.trim()
  if (!trimmed || searchHistory.value[0] === trimmed) return

  const newHistory = [trimmed, ...searchHistory.value.filter(h => h !== trimmed)].slice(0, 5)
  searchHistory.value = newHistory
  saveSearchHistory(newHistory)
}

/**
 * 使用历史记录检索
 */
function searchFromHistory(query: string) {
  searchQuery.value = query
  handleSearch()
}

/**
 * 清除检索历史
 */
function clearHistory() {
  searchHistory.value = []
  saveSearchHistory([])
}

/**
 * 清除检索结果
 */
function clearResults() {
  searchResults.value = []
  hasSearched.value = false
  searchQuery.value = ''
  resetContentStates()
}
</script>

<template>
  <div class="search-test-container">
    <h3 class="intro-title">{{ name }}</h3>
    <p class="intro-desc text-secondary">
      {{ description }}
    </p>
    <!-- 检索输入区 -->
    <div class="search-test-input-area">
      <div class="search-test-input-row">
        <AInput
          v-model:value="searchQuery"
          placeholder="输入检索内容测试RAG效果，例如：什么是向量数据库？"
          size="middle"
          @pressEnter="handleSearch"
          allow-clear
        >
          <template #prefix>
            <SearchOutlined class="text-placeholder" />
          </template>
        </AInput>
        <AButton type="primary" :loading="searching" @click="handleSearch">
          <SearchOutlined /> 检索
        </AButton>
        <AButton @click="showAdvanced = !showAdvanced">
          <SettingOutlined />
        </AButton>
      </div>
    </div>

    <!-- 检索结果区 -->
    <div class="search-test-results">
      <!-- 高级参数（按知识库类型动态展示，仅本次测试生效） -->
      <div v-if="showAdvanced" class="search-test-params">
        <div class="search-test-params-row">
          <div
            v-for="param in advancedParams"
            :key="param.key"
            class="search-test-param-item"
          >
            <span class="search-test-param-label">{{ param.label }}</span>
            <template v-if="param.kind === 'slider'">
              <ASlider
                :value="getNumberValue(param.key)"
                :min="param.min"
                :max="param.max"
                :step="param.step"
                style="flex: 1"
                @update:value="(value: unknown) => setParamValue(param.key, value)"
              />
              <span class="text-xs" style="min-width: 30px; text-align: right;">
                {{ getNumberValue(param.key) }}
              </span>
            </template>
            <template v-else-if="param.kind === 'number'">
              <AInputNumber
                :value="getNumberValue(param.key)"
                :min="param.min"
                :max="param.max"
                style="flex: 1"
                @update:value="(value: unknown) => setParamValue(param.key, value)"
              />
            </template>
            <template v-else-if="param.kind === 'switch'">
              <ASwitch
                :checked="getBooleanValue(param.key)"
                @update:checked="(value: unknown) => setParamValue(param.key, value)"
              />
            </template>
            <template v-else-if="param.kind === 'select'">
              <ASelect
                :value="getStringValue(param.key)"
                :options="param.options"
                style="flex: 1"
                @update:value="(value: unknown) => setParamValue(param.key, value)"
              />
            </template>
          </div>
        </div>
        <div class="text-placeholder" style="font-size: 12px; margin-top: 8px;">
          高级参数仅对本次检索测试生效，不会修改知识库已保存的配置
        </div>
      </div>

      <!-- 检索历史 -->
      <div v-if="searchHistory.length > 0" class="search-test-history">
        <span class="search-test-history-label">
          <ClockCircleOutlined /> 最近搜索
        </span>
        <ATag
          v-for="(item, index) in searchHistory"
          :key="index"
          :bordered="false"
          color="default"
          style="cursor: pointer"
          @click="searchFromHistory(item)"
        >
          {{ item }}
        </ATag>
        <AButton type="text" size="small" @click="clearHistory">
          <ClearOutlined />
        </AButton>
      </div>

      <!-- 加载中 -->
      <div v-if="searching" class="search-test-empty">
        <div class="search-test-empty-text" style="margin-top: 16px;"><LoadingOutlined /> 正在检索中...</div>
      </div>

      <!-- 有结果 -->
      <template v-else-if="searchResults.length > 0">
        <div class="search-test-result-stats">
          <ExperimentOutlined />
          共检索到 <span class="search-test-result-highlight">{{ resultCount }}</span> 条结果
          <span class="text-placeholder">
            （Top K: {{ getParamValue('topK') }}，{{ thresholdLabel }}: {{ currentThreshold }}）
          </span>
          <AButton type="text" size="small" @click="clearResults" style="margin-left: auto;">
            <ClearOutlined /> 清除
          </AButton>
        </div>

        <div class="search-result-cards">
          <div
            v-for="(result, index) in searchResults"
            :key="resultKey(result, index)"
            class="search-result-card"
          >
            <div class="search-result-card-header">
              <div class="search-result-card-header-left">
                <ATag v-if="result.chunkIndex != null" color="blue" :bordered="false"># {{ result.chunkIndex }}</ATag>
                <div v-if="result.score != null" class="search-result-score">
                  <span>相关度</span>
                  <div class="search-result-score-bar">
                    <div
                      class="search-result-score-fill"
                      :style="{
                        width: getScoreWidth(result.score as number),
                        backgroundColor: getScoreColor(result.score as number)
                      }"
                    />
                  </div>
                  <span :style="{ color: getScoreColor(result.score as number) }">
                    {{ ((result.score as number) * 100).toFixed(0) }}%
                  </span>
                </div>
              </div>
              <span class="text-placeholder text-xs">
                {{ result.fileName || result.docId }}
              </span>
            </div>

            <div
              :ref="(el) => collectContentEl(el, resultKey(result, index))"
              class="search-result-content"
              :class="{ 'search-result-content-collapsed': isContentCollapsed(resultKey(result, index)) }"
              @click="isContentOverflow(resultKey(result, index)) && toggleContent(resultKey(result, index))"
            >{{ result.content }}</div>

            <div
              v-if="isContentOverflow(resultKey(result, index))"
              class="search-result-expand"
              @click="toggleContent(resultKey(result, index))"
            >
              {{ isContentCollapsed(resultKey(result, index)) ? '展开' : '收起' }}
            </div>

            <div class="search-result-footer">
              <span v-if="result.tokenCount != null">
                <NumberOutlined /> ~{{ result.tokenCount }} tokens
              </span>
              <span v-if="result.chunkIndex != null">
                <FileTextOutlined /> 分块索引 {{ result.chunkIndex }}
              </span>
              <span v-else-if="result.chunkId">
                <FileTextOutlined /> 分块 {{ result.chunkId }}
              </span>
            </div>
          </div>
        </div>
      </template>

      <!-- 搜索后无结果 -->
      <div v-else-if="hasSearched && !searching" class="search-test-empty">
        <AEmpty  description="未找到匹配的检索结果"/>
        <div class="search-test-empty-hint">尝试调整检索内容或降低相似度阈值</div>
      </div>

      <!-- 初始状态 -->
      <div v-else class="search-test-empty">
        <SearchOutlined class="search-test-empty-icon" />
        <div class="search-test-empty-text">输入检索内容进行RAG效果测试</div>
        <div class="search-test-empty-hint">支持对已配置的知识库进行检索验证</div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/rag/_doc-manager.scss' as *;

.intro-title {
  font-size: var(--font-size-2xl);
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: var(--spacing-sm);
}


.intro-desc {
  font-size: var(--font-size-base);
  line-height: 1.6;
  max-width: 800px;

  /* 新增以下三行 */
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

// ===== 结果区高度动态适配 =====
.search-test-input-area {
  flex-shrink: 0;
}

.search-test-results {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  max-height: calc(100vh - 180px);
}

.search-test-result-stats {
  flex-shrink: 0;
}

.search-result-cards {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

// ===== 结果内容超行折叠/展开 =====
.search-result-content {
  &.search-result-content-collapsed {
    display: -webkit-box;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 3;
    overflow: hidden;
    cursor: pointer;
  }
}

.search-result-expand {
  display: inline-block;
  margin-top: var(--spacing-xs);
  font-size: var(--font-size-xs);
  color: var(--color-primary);
  cursor: pointer;
  user-select: none;

  &:hover {
    opacity: 0.8;
  }
}
</style>
