<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Modal } from 'ant-design-vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import PanelSection from '../shared/PanelSection.vue'
import NodeNameInput from '../shared/NodeNameInput.vue'
import InputBindingSection from '../shared/InputBindingSection.vue'
import OutputDisplay from '../shared/OutputDisplay.vue'
import KnowledgeSelector from '@/components/workflow/bindings/KnowledgeSelector.vue'
import * as knowledgeApi from '@/api/knowledge'
import { RoutePaths } from '@/router/constants.ts'
import type { KnowledgeBaseConfigVO } from '@/types'
import type { WorkflowFlowEdge, WorkflowFlowNode, WorkflowResourceMaps } from '@/types/workflow'

interface KnowledgeNodeConfig {
  knowledgeBaseConfigId?: string | null
  knowledgeBaseName?: string | null
  retrievalConfig?: Record<string, unknown> | null
}

/** 核心检索配置表单模型（key 与知识库 retrievalConfig 保持一致） */
interface KnowledgeRetrievalForm {
  topK?: number
  scoreThreshold?: number
  retrievalMode?: string
  weights?: number
  similarityThreshold?: number
  vectorSimilarityWeight?: number
  denseSimilarityTopK?: number
  sparseSimilarityTopK?: number
}

const props = defineProps<{
  node: WorkflowFlowNode
  nodes: WorkflowFlowNode[]
  edges: WorkflowFlowEdge[]
  resources: WorkflowResourceMaps
}>()

const emit = defineEmits<{ update: [node: WorkflowFlowNode] }>()

const allKnowledgeBases = ref<KnowledgeBaseConfigVO[]>([])
const retrievalForm = reactive<KnowledgeRetrievalForm>({})

const config = computed(() => (props.node.data.config || {}) as KnowledgeNodeConfig)

const selectedKnowledgeBaseId = computed(() => config.value.knowledgeBaseConfigId || null)

const selectedKb = computed(() => {
  const id = selectedKnowledgeBaseId.value
  if (!id) return null
  return allKnowledgeBases.value.find((item) => String(item.id) === String(id)) || null
})

const DIFY_MODE_OPTIONS = [
  { label: '混合检索', value: 'HYBRID_SEARCH' },
  { label: '向量检索', value: 'SEMANTIC_SEARCH' },
  { label: '关键词检索', value: 'KEYWORD_SEARCH' },
  { label: '全文检索', value: 'FULL_TEXT_SEARCH' },
]

function updateNode(patch: Partial<WorkflowFlowNode['data']>) {
  emit('update', { ...props.node, data: { ...props.node.data, ...patch } })
}

function updateConfig(key: string, value: unknown) {
  updateNode({ config: { ...(props.node.data.config || {}), [key]: value } })
}

function updateRetrievalField(key: keyof KnowledgeRetrievalForm, value: unknown) {
  ;(retrievalForm as Record<string, unknown>)[key] = value
  updateConfig('retrievalConfig', { ...retrievalForm })
}

function clearRetrievalFormLocal() {
  Object.keys(retrievalForm).forEach((key) => delete (retrievalForm as Record<string, unknown>)[key])
}

function setRetrievalFormLocal(values: Partial<KnowledgeRetrievalForm>) {
  clearRetrievalFormLocal()
  Object.assign(retrievalForm, values)
}

/** 覆盖当前表单并持久化到节点配置（仅修改 retrievalConfig，适用于重置等场景） */
function applyRetrievalForm(values: Partial<KnowledgeRetrievalForm>) {
  setRetrievalFormLocal(values)
  updateConfig('retrievalConfig', { ...values })
}

/** 从知识库配置中抽取核心检索项（缺失项使用各类型默认值） */
function buildDefaultRetrieval(kb: KnowledgeBaseConfigVO): Partial<KnowledgeRetrievalForm> {
  const src = (kb.retrievalConfig as Record<string, unknown>) || {}
  switch (kb.kbType) {
    case 'LOCAL':
      return { topK: num(src.topK, 5), scoreThreshold: num(src.scoreThreshold, 0.5) }
    case 'DIFY':
      return {
        retrievalMode: normalizeDifyMode(src.retrievalMode),
        topK: num(src.topK, 10),
        scoreThreshold: num(src.scoreThreshold, 0),
        weights: num(src.weights, 0.6),
      }
    case 'RAGFLOW':
      return {
        topK: num(src.topK, 1024),
        similarityThreshold: num(src.similarityThreshold, 0.2),
        vectorSimilarityWeight: num(src.vectorSimilarityWeight, 0.3),
      }
    case 'BAILIAN':
      return {
        denseSimilarityTopK: num(src.denseSimilarityTopK, 100),
        sparseSimilarityTopK: num(src.sparseSimilarityTopK, 100),
      }
    default:
      return {}
  }
}

function num(value: unknown, fallback: number): number {
  const n = Number(value)
  return Number.isFinite(n) ? n : fallback
}

function normalizeDifyMode(value: unknown): string {
  const v = String(value ?? '')
  const legacyMap: Record<string, string> = { VECTOR: 'SEMANTIC_SEARCH', FULL_TEXT: 'FULL_TEXT_SEARCH' }
  if (legacyMap[v]) return legacyMap[v]
  if (DIFY_MODE_OPTIONS.some((item) => item.value === v)) return v
  return 'HYBRID_SEARCH'
}

function onKnowledgeChange(knowledgeBaseId: string) {
  const kb = allKnowledgeBases.value.find((item) => String(item.id) === String(knowledgeBaseId))
  const values = kb ? buildDefaultRetrieval(kb) : {}
  // 一次提交完整配置，避免 props 未更新导致的覆盖丢失；切换后检索配置动态切换为新知识库默认值
  updateNode({
    config: {
      ...(props.node.data.config || {}),
      knowledgeBaseConfigId: knowledgeBaseId,
      knowledgeBaseName: kb?.name || null,
      retrievalConfig: kb ? { ...values } : null,
    },
  })
  setRetrievalFormLocal(values)
}

function onKnowledgeClear() {
  updateNode({
    config: {
      ...(props.node.data.config || {}),
      knowledgeBaseConfigId: null,
      knowledgeBaseName: null,
      retrievalConfig: null,
    },
  })
  setRetrievalFormLocal({})
}

function handleReset() {
  const kb = selectedKb.value
  if (!kb) return
  Modal.confirm({
    title: '重置检索配置',
    content: `将检索配置重置为知识库「${kb.name}」的默认配置，当前修改将丢失。是否继续？`,
    okText: '重置',
    okType: 'danger',
    cancelText: '取消',
    onOk: () => {
      applyRetrievalForm(buildDefaultRetrieval(kb))
    },
  })
}

async function loadAllKnowledgeBases() {
  const response = await knowledgeApi.page({ page: 1, size: 1000, enabled: true })
  allKnowledgeBases.value = response.data.data.records || []
}

/** 已保存过覆盖配置则回填，否则用知识库默认值初始化 */
function initRetrievalForm() {
  const kb = selectedKb.value
  if (!kb) return
  const existing = config.value.retrievalConfig
  if (existing && Object.keys(existing).length > 0) {
    Object.assign(retrievalForm, existing)
  } else {
    applyRetrievalForm(buildDefaultRetrieval(kb))
  }
}

onMounted(async () => {
  await loadAllKnowledgeBases()
  initRetrievalForm()
})
</script>

<template>
  <AForm layout="vertical">
    <!-- 1. 节点名称 -->
    <PanelSection title="节点名称">
      <NodeNameInput
        :model-value="node.data.label"
        @update:model-value="(v: any) => updateNode({ label: v })"
      />
    </PanelSection>

    <!-- 2. 输入绑定 -->
    <PanelSection title="输入绑定">
      <div class="input-hint">将检索关键词绑定到 query 输入</div>
      <InputBindingSection
        :model-value="node.data.inputConfigs"
        :nodes="nodes"
        :edges="edges"
        :current-node-id="node.id"
        @update:model-value="(v: any) => updateNode({ inputConfigs: v })"
      />
    </PanelSection>

    <!-- 3. 知识库选择 -->
    <PanelSection title="知识库选择">
      <div class="config-block">
        <template v-if="allKnowledgeBases.length > 0">
          <KnowledgeSelector
            :model-value="String(selectedKnowledgeBaseId || '')"
            :knowledge-bases="allKnowledgeBases"
            @update:model-value="(v: string) => onKnowledgeChange(v)"
            @clear="onKnowledgeClear"
          />
        </template>
        <div v-else class="empty-action">
          <AButton type="text">未配置知识库</AButton>
          <AButton type="link" :href="`/#/${RoutePaths.KNOWLEDGE}`" target="_blank">去配置</AButton>
          <AButton type="link" @click="loadAllKnowledgeBases()">刷新</AButton>
        </div>
      </div>
    </PanelSection>

    <!-- 4. 检索配置 -->
    <PanelSection v-if="selectedKb" title="检索配置">
      <div class="retrieval-header">
        <span class="retrieval-hint">仅覆盖核心检索项，其余使用知识库默认配置</span>
        <AButton type="link" size="small" @click="handleReset">
          <template #icon><ReloadOutlined /></template>
          重置为知识库默认
        </AButton>
      </div>

      <div class="retrieval-fields">
        <!-- 本地 -->
        <template v-if="selectedKb.kbType === 'LOCAL'">
          <AFormItem label="返回 Top K">
            <AInputNumber
              :value="retrievalForm.topK"
              :min="1"
              :max="100"
              style="width: 100%"
              @update:value="(v: any) => updateRetrievalField('topK', v)"
            />
          </AFormItem>
          <AFormItem label="相似度阈值">
            <AInputNumber
              :value="retrievalForm.scoreThreshold"
              :min="0"
              :max="1"
              :step="0.1"
              style="width: 100%"
              @update:value="(v: any) => updateRetrievalField('scoreThreshold', v)"
            />
          </AFormItem>
        </template>

        <!-- Dify -->
        <template v-else-if="selectedKb.kbType === 'DIFY'">
          <AFormItem label="检索模式">
            <ASelect
              :value="retrievalForm.retrievalMode"
              :options="DIFY_MODE_OPTIONS"
              @update:value="(v: any) => updateRetrievalField('retrievalMode', v)"
            />
          </AFormItem>
          <AFormItem label="返回 Top K">
            <AInputNumber
              :value="retrievalForm.topK"
              :min="1"
              :max="100"
              style="width: 100%"
              @update:value="(v: any) => updateRetrievalField('topK', v)"
            />
          </AFormItem>
          <AFormItem label="分数阈值">
            <AInputNumber
              :value="retrievalForm.scoreThreshold"
              :min="0"
              :max="1"
              :step="0.1"
              style="width: 100%"
              @update:value="(v: any) => updateRetrievalField('scoreThreshold', v)"
            />
          </AFormItem>
          <AFormItem label="混合检索权重">
            <AInputNumber
              :value="retrievalForm.weights"
              :min="0"
              :max="1"
              :step="0.1"
              style="width: 100%"
              @update:value="(v: any) => updateRetrievalField('weights', v)"
            />
          </AFormItem>
        </template>

        <!-- RagFlow -->
        <template v-else-if="selectedKb.kbType === 'RAGFLOW'">
          <AFormItem label="返回 Top K">
            <AInputNumber
              :value="retrievalForm.topK"
              :min="1"
              :max="2048"
              style="width: 100%"
              @update:value="(v: any) => updateRetrievalField('topK', v)"
            />
          </AFormItem>
          <AFormItem label="相似度阈值">
            <AInputNumber
              :value="retrievalForm.similarityThreshold"
              :min="0"
              :max="1"
              :step="0.1"
              style="width: 100%"
              @update:value="(v: any) => updateRetrievalField('similarityThreshold', v)"
            />
          </AFormItem>
          <AFormItem label="向量相似度权重">
            <AInputNumber
              :value="retrievalForm.vectorSimilarityWeight"
              :min="0"
              :max="1"
              :step="0.1"
              style="width: 100%"
              @update:value="(v: any) => updateRetrievalField('vectorSimilarityWeight', v)"
            />
          </AFormItem>
        </template>

        <!-- 百炼 -->
        <template v-else-if="selectedKb.kbType === 'BAILIAN'">
          <AFormItem label="稠密相似度 Top K">
            <AInputNumber
              :value="retrievalForm.denseSimilarityTopK"
              :min="1"
              :max="1000"
              style="width: 100%"
              @update:value="(v: any) => updateRetrievalField('denseSimilarityTopK', v)"
            />
          </AFormItem>
          <AFormItem label="稀疏相似度 Top K">
            <AInputNumber
              :value="retrievalForm.sparseSimilarityTopK"
              :min="1"
              :max="1000"
              style="width: 100%"
              @update:value="(v: any) => updateRetrievalField('sparseSimilarityTopK', v)"
            />
          </AFormItem>
        </template>
      </div>
    </PanelSection>

    <!-- 5. 输出说明 -->
    <PanelSection title="输出说明">
      <OutputDisplay :outputs="node.data.outputConfigs || []" />
    </PanelSection>
  </AForm>
</template>

<style scoped lang="scss">
.config-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.input-hint {
  margin-bottom: 8px;
  color: #8c8c8c;
  font-size: 13px;
  line-height: 1.6;
}

.empty-action {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  color: #8c8c8c;
}

.retrieval-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 4px;
}

.retrieval-hint {
  color: #8c8c8c;
  font-size: 12px;
  line-height: 1.5;
}

.retrieval-fields {
  padding: 12px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fafafa;
}
</style>
