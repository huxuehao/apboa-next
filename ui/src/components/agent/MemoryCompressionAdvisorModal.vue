<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { CheckCircleFilled, ThunderboltOutlined } from '@ant-design/icons-vue'

export interface MemoryCompressionConfig {
  maxToken: number
  msgThreshold: number
  lastKeep: number
  tokenRatio: number
  minCompressionTokenThreshold: number
  currentRoundCompressionRatio: number
  minConsecutiveToolMessages: number
  offloadSinglePreview: number
  largePayloadThreshold: number
  strategyType: string
}

export interface CompressionScenario {
  key: string
  title: string
  description: string
  suitable: string
  color: string
  profile: CompressionScenarioProfile
}

interface CompressionScenarioProfile {
  systemReserveRatio: number
  memoryUtilizationRatio: number
  expectedTokensPerMessage: number
  minCandidateTokens: [number, number, number, number]
  largePayloadChars: [number, number, number, number]
  lastKeepMin: number
  lastKeepMax: number
  lastKeepRatio: number
  minConsecutiveToolMessages: number
  currentRoundCompressionRatio: number
  previewRatio: number
}

const props = defineProps<{
  open: boolean
  contextWindow: number
  maxOutputTokens: number
  initialStrategyType?: string
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  apply: [config: MemoryCompressionConfig]
}>()

/** 当前选中的应用场景方案。 */
const selected = ref('balanced')
const formatTokens = (value: number) => `${Math.round(value).toLocaleString()} tokens`
const clamp = (value: number, min: number, max: number) => Math.min(max, Math.max(min, Math.round(value)))

/** 六类常见智能体场景及其参数生成规则。 */
const scenarios: CompressionScenario[] = [
  {
    key: 'conservative', title: '稳健保真', color: '#1677ff',
    description: '较晚触发，保留更多最近消息，适合需要完整上下文的问答和分析 Agent。',
    suitable: '研究分析、长文档问答',
    profile: {
      systemReserveRatio: 0.08, memoryUtilizationRatio: 0.96, expectedTokensPerMessage: 450,
      minCandidateTokens: [256, 384, 512, 768], largePayloadChars: [4096, 8192, 16384, 32768],
      lastKeepMin: 6, lastKeepMax: 16, lastKeepRatio: 0.20, minConsecutiveToolMessages: 4,
      currentRoundCompressionRatio: 0.55, previewRatio: 0.12
    }
  },
  {
    key: 'balanced', title: '通用平衡', color: '#13c2c2',
    description: '在上下文成本和回答完整性之间平衡，适合作为大多数 Agent 的默认方案。',
    suitable: '通用助手、业务问答',
    profile: {
      systemReserveRatio: 0.12, memoryUtilizationRatio: 0.90, expectedTokensPerMessage: 350,
      minCandidateTokens: [128, 192, 256, 384], largePayloadChars: [2048, 4096, 8192, 16384],
      lastKeepMin: 5, lastKeepMax: 12, lastKeepRatio: 0.16, minConsecutiveToolMessages: 3,
      currentRoundCompressionRatio: 0.40, previewRatio: 0.08
    }
  },
  {
    key: 'tool_heavy', title: '工具密集', color: '#722ed1',
    description: '更积极处理工具调用历史，降低连续工具链占用，保留工具结果摘要。',
    suitable: '代码执行、MCP、数据查询',
    profile: {
      systemReserveRatio: 0.18, memoryUtilizationRatio: 0.85, expectedTokensPerMessage: 1200,
      minCandidateTokens: [192, 256, 384, 512], largePayloadChars: [2048, 4096, 8192, 16384],
      lastKeepMin: 5, lastKeepMax: 10, lastKeepRatio: 0.14, minConsecutiveToolMessages: 2,
      currentRoundCompressionRatio: 0.35, previewRatio: 0.08
    }
  },
  {
    key: 'long_dialogue', title: '长对话', color: '#fa8c16',
    description: '优先压缩较早轮次，保留最近对话，适合持续数十轮的陪伴式交互。',
    suitable: '客服、项目协作、长期会话',
    profile: {
      systemReserveRatio: 0.12, memoryUtilizationRatio: 0.90, expectedTokensPerMessage: 180,
      minCandidateTokens: [96, 128, 192, 256], largePayloadChars: [4096, 8192, 12288, 16384],
      lastKeepMin: 6, lastKeepMax: 18, lastKeepRatio: 0.22, minConsecutiveToolMessages: 3,
      currentRoundCompressionRatio: 0.45, previewRatio: 0.08
    }
  },
  {
    key: 'document_heavy', title: '大内容卸载', color: '#52c41a',
    description: '更早卸载大段代码、文件和网页内容，通过预览与恢复标记节省上下文。',
    suitable: '文件处理、网页阅读、代码审查',
    profile: {
      systemReserveRatio: 0.13, memoryUtilizationRatio: 0.86, expectedTokensPerMessage: 2000,
      minCandidateTokens: [192, 256, 384, 512], largePayloadChars: [1024, 2048, 4096, 8192],
      lastKeepMin: 4, lastKeepMax: 8, lastKeepRatio: 0.12, minConsecutiveToolMessages: 4,
      currentRoundCompressionRatio: 0.35, previewRatio: 0.06
    }
  },
  {
    key: 'low_latency', title: '成本优先', color: '#eb2f96',
    description: '控制主模型输入长度和上下文成本；压缩调用本身会增加额外延迟。',
    suitable: '批量任务、实时客服',
    profile: {
      systemReserveRatio: 0.10, memoryUtilizationRatio: 0.82, expectedTokensPerMessage: 700,
      minCandidateTokens: [384, 512, 768, 1024], largePayloadChars: [2048, 4096, 8192, 16384],
      lastKeepMin: 4, lastKeepMax: 8, lastKeepRatio: 0.10, minConsecutiveToolMessages: 4,
      currentRoundCompressionRatio: 0.25, previewRatio: 0.06
    }
  }
]

watch(() => props.initialStrategyType, value => {
  if (value && scenarios.some(item => item.key === value)) selected.value = value
}, { immediate: true })

/** 根据模型窗口、输出预算和场景特征生成可执行的压缩配置。 */
function makeConfig(
  contextWindow: number,
  maxOutputTokens: number,
  strategyType: string,
  profile: CompressionScenarioProfile
): MemoryCompressionConfig {
  const windowTier = contextWindow <= 16_384 ? 0 : contextWindow <= 131_072 ? 1 : contextWindow <= 524_288 ? 2 : 3
  const systemReserve = clamp(contextWindow * profile.systemReserveRatio, 256, Math.max(256, contextWindow * 0.20))
  const requestedOutput = maxOutputTokens > 0 ? maxOutputTokens : Math.round(contextWindow * 0.15)
  const outputReserve = Math.min(requestedOutput, Math.round(contextWindow * 0.50))
  const usableInputBudget = Math.max(Math.round(contextWindow * 0.30), contextWindow - systemReserve - outputReserve)
  const tokenTriggerBudget = Math.round(usableInputBudget * profile.memoryUtilizationRatio)
  const tokenRatio = Math.min(0.85, Math.max(0.15, tokenTriggerBudget / contextWindow))
  const minimumMessageThreshold = Math.max(16, profile.lastKeepMin * 3)
  const msgThreshold = clamp(tokenTriggerBudget / profile.expectedTokensPerMessage, minimumMessageThreshold, 800)
  const lastKeep = clamp(
    Math.min(Math.round(msgThreshold * profile.lastKeepRatio), Math.floor(msgThreshold * 0.30)),
    profile.lastKeepMin,
    profile.lastKeepMax
  )
  const largePayloadThreshold = profile.largePayloadChars[windowTier]
  return {
    maxToken: contextWindow,
    msgThreshold,
    lastKeep: Math.min(lastKeep, msgThreshold - 2),
    tokenRatio,
    minCompressionTokenThreshold: profile.minCandidateTokens[windowTier],
    currentRoundCompressionRatio: profile.currentRoundCompressionRatio,
    minConsecutiveToolMessages: profile.minConsecutiveToolMessages,
    offloadSinglePreview: clamp(largePayloadThreshold * profile.previewRatio, 128, 512),
    largePayloadThreshold,
    strategyType
  }
}

const currentScenario = computed<CompressionScenario>(() => scenarios.find(item => item.key === selected.value) || scenarios[1]!)
const previewConfig = computed(() => makeConfig(
  props.contextWindow,
  props.maxOutputTokens,
  currentScenario.value.key,
  currentScenario.value.profile
))
const budgetWarning = computed(() => props.maxOutputTokens >= props.contextWindow
  ? '当前最大输出 Token 数不小于 Context Window，模型配置本身可能无法满足完整请求预算。'
  : '')

function applySelected() {
  emit('apply', previewConfig.value)
  emit('update:open', false)
}
</script>

<template>
  <AModal
    :open="open"
    title="智能生成记忆压缩方案"
    width="980px"
    style="top: 5%"
    ok-text="应用此方案"
    cancel-text="取消"
    @ok="applySelected"
    @cancel="emit('update:open', false)"
  >
    <AAlert type="info" show-icon class="advisor-alert">
      <template #message>已读取模型上下文窗口：{{ formatTokens(contextWindow) }}</template>
      <template #description>系统会按所选场景计算六级压缩策略的阈值并回填表单。模型上下文窗口发生变化后，建议重新生成配置。</template>
    </AAlert>
    <AAlert v-if="budgetWarning" type="warning" show-icon class="advisor-alert" :message="budgetWarning" />
    <div class="budget-summary">
      最大输出 Token 预留：{{ maxOutputTokens > 0 ? formatTokens(maxOutputTokens) : '未配置，按窗口的 15% 估算' }}
      <span>本方案会据此为系统提示词、工具定义和模型输出保留预算。</span>
    </div>
    <div class="scenario-grid">
      <button
        v-for="scenario in scenarios"
        :key="scenario.key"
        type="button"
        class="scenario-card"
        :class="{ selected: selected === scenario.key }"
        :style="{ '--scenario-color': scenario.color }"
        @click="selected = scenario.key"
      >
        <span class="scenario-check"><CheckCircleFilled v-if="selected === scenario.key" /></span>
        <span class="scenario-title"><ThunderboltOutlined /> {{ scenario.title }}</span>
        <span class="scenario-description">{{ scenario.description }}</span>
        <span class="scenario-suitable">适用：{{ scenario.suitable }}</span>
      </button>
    </div>
    <div class="config-preview">
      <div class="preview-title">{{ currentScenario.title }} · 将回填的配置</div>
      <div class="preview-grid">
        <span>最大Token数 <b>{{ previewConfig.maxToken.toLocaleString() }}</b></span>
        <span>Token比率 <b>{{ previewConfig.tokenRatio }}</b></span>
        <span>消息阈值 <b>{{ previewConfig.msgThreshold }}</b></span>
        <span>保留最近消息数 <b>{{ previewConfig.lastKeep }}</b></span>
        <span>最小压缩Token阈值 <b>{{ previewConfig.minCompressionTokenThreshold }}</b></span>
        <span>最小连续工具消息数 <b>{{ previewConfig.minConsecutiveToolMessages }}</b></span>
        <span>大负载阈值 <b>{{ previewConfig.largePayloadThreshold }}</b></span>
        <span>当前轮次压缩比率 <b>{{ previewConfig.currentRoundCompressionRatio }}</b></span>
      </div>
    </div>
  </AModal>
</template>

<style scoped lang="scss">
.advisor-alert { margin-bottom: 18px; }
.budget-summary { margin: -8px 0 18px; color: #4b5563; font-size: 13px; }
.budget-summary span { margin-left: 8px; color: #6b7280; }
.scenario-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
.scenario-card { position: relative; min-height: 170px; padding: 16px; text-align: left; border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; color: #1f2937; cursor: pointer; transition: border-color .2s, box-shadow .2s, transform .2s; }
.scenario-card:hover, .scenario-card.selected { border-color: var(--scenario-color); box-shadow: 0 0 0 2px color-mix(in srgb, var(--scenario-color) 15%, transparent); transform: translateY(-1px); }
.scenario-check { position: absolute; top: 10px; right: 12px; color: var(--scenario-color); }
.scenario-title { display: block; color: var(--scenario-color); font-size: 15px; font-weight: 600; margin-bottom: 9px; }
.scenario-description { display: block; min-height: 58px; color: #4b5563; font-size: 13px; line-height: 1.55; }
.scenario-suitable { display: block; margin-top: 8px; color: #6b7280; font-size: 12px; }
.config-preview { margin-top: 18px; padding: 14px; border: 1px solid #edf0f2; border-radius: 6px; background: #fafafa; }
.preview-title { margin-bottom: 10px; font-weight: 600; }
.preview-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px 18px; color: #6b7280; font-family: monospace; font-size: 12px; }
.preview-grid b { color: #111827; font-weight: 600; }
@media (max-width: 760px) { .scenario-grid, .preview-grid { grid-template-columns: 1fr; } }
</style>
