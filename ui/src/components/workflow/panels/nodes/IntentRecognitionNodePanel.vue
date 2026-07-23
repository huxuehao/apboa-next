<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { QuestionCircleOutlined } from '@ant-design/icons-vue'
import PanelSection from '../shared/PanelSection.vue'
import NodeNameInput from '../shared/NodeNameInput.vue'
import AutoInputBinding from '@/components/workflow/bindings/AutoInputBinding.vue'
import OutputDisplay from '../shared/OutputDisplay.vue'
import ModelConfigSelector from '@/components/workflow/bindings/ModelConfigSelector.vue'
import ExtendConfigEditor, { type ExtendConfigData } from '@/components/model/ExtendConfigEditor.vue'
import IconFont from '@/components/common/IconFont.vue'
import NextNodeSelector from '@/components/workflow/bindings/NextNodeSelector.vue'
import { getNodeIconName } from '@/config/workflow/common'
import * as modelApi from '@/api/model'
import type {
  ModelConfigVO,
  ModelProviderVO,
} from '@/types'
import type { WorkflowFlowEdge, WorkflowFlowNode, WorkflowResourceMaps } from '@/types/workflow'
import { LoadingOutlined } from '@ant-design/icons-vue'

interface IntentItem {
  name: string
  description: string
  nextNodeId: string
}

interface IntentRecognitionConfig {
  modelConfigId?: string
  modelParamsOverrideEnabled?: boolean
  modelParamsOverride?: Record<string, unknown>
  systemPromptExtension?: string
  intents?: IntentItem[]
  defaultNextNodeId?: string
}

const props = defineProps<{
  node: WorkflowFlowNode
  nodes: WorkflowFlowNode[]
  edges: WorkflowFlowEdge[]
  resources: WorkflowResourceMaps
}>()
const emit = defineEmits<{ update: [node: WorkflowFlowNode] }>()

const loading = ref(false)
const modelProviders = ref<ModelProviderVO[]>([])
const allModels = ref<ModelConfigVO[]>([])
const modelParamsForm = ref<Record<string, unknown>>({})
const modelParamsFormGuard = ref(false)

const config = computed(() => (props.node.data.config || {}) as IntentRecognitionConfig)
const intents = computed<IntentItem[]>(() => (Array.isArray(config.value.intents) ? config.value.intents : []))

const showFixedSystemMessage = computed(() => {
  const model = allModels.value.find((item) => String(item.id) === String(config.value.modelConfigId || ''))
  if (!model) return false
  const provider = modelProviders.value.find((item) => String(item.id) === String(model.providerId))
  return String(provider?.type || '') === 'OPEN_AI'
})

const extendConfigForm = computed({
  get: () => (modelParamsForm.value.extendConfig as ExtendConfigData) || null,
  set: (value: ExtendConfigData | null) => {
    modelParamsForm.value = { ...modelParamsForm.value, extendConfig: value }
  },
})

function updateNode(patch: Partial<WorkflowFlowNode['data']>) {
  emit('update', { ...props.node, data: { ...props.node.data, ...patch } })
}
function updateConfig(key: string, value: unknown) {
  updateNode({ config: { ...(props.node.data.config || {}), [key]: value } })
}

// 下游节点列表（用于展示匹配行）
const downstreamNodes = computed(() => {
  const edges = props.edges || []
  const targetIds = new Set<string>()
  for (const edge of edges) {
    if (edge.source === props.node.id) {
      targetIds.add(edge.target)
    }
  }
  return props.nodes.filter((n) => targetIds.has(n.id))
})

const displayIntents = computed(() => {
  const seen = new Set<string>()
  const items: Array<IntentItem & { nodeLabel?: string; nodeColor?: string }> = []
  for (const intent of intents.value) {
    if (!intent.nextNodeId || seen.has(intent.nextNodeId)) continue
    const node = downstreamNodes.value.find((n) => n.id === intent.nextNodeId)
    seen.add(intent.nextNodeId)
    items.push({
      ...intent,
      nodeLabel: node?.data.label || node?.id,
      nodeColor: node?.data.schema?.color,
    })
  }
  for (const node of downstreamNodes.value) {
    if (!seen.has(node.id)) {
      items.push({ name: '', description: '', nextNodeId: node.id, nodeLabel: node.data.label || node.id, nodeColor: node.data.schema?.color })
    }
  }
  return items
})

// 自动清理：下游节点断开时移除对应的残留意向
let intentSyncGuard = false
watch(downstreamNodes, () => {
  if (intentSyncGuard) return
  const currentIds = new Set(downstreamNodes.value.map((n) => n.id))
  const cleaned = intents.value.filter((i) => i.nextNodeId && currentIds.has(i.nextNodeId))
  if (cleaned.length !== intents.value.length) {
    intentSyncGuard = true
    updateConfig('intents', cleaned)
    nextTick(() => { intentSyncGuard = false })
  }
}, { immediate: true })

function updateIntent(displayIndex: number, field: keyof IntentItem, value: string) {
  const displayItem = displayIntents.value[displayIndex]
  if (!displayItem) return

  const existingIndex = intents.value.findIndex((i) => i.nextNodeId === displayItem.nextNodeId)
  const list = [...intents.value]

  if (existingIndex >= 0) {
    const existing = list[existingIndex]!
    list[existingIndex] = { name: existing.name || '', description: existing.description || '', nextNodeId: existing.nextNodeId || '', [field]: value as string }
  } else {
    const newIntent: IntentItem = { name: '', description: '', nextNodeId: displayItem.nextNodeId }
    newIntent[field] = value
    list.push(newIntent)
  }

  updateConfig('intents', list)
}

// 模型加载
async function loadModelProviders() {
  const response = await modelApi.providerPage({ page: 1, size: 100, enabled: true })
  modelProviders.value = response.data.data.records || []
}
async function loadAllModels() {
  const response = await modelApi.configPage({ page: 1, size: 1000, enabled: true })
  allModels.value = response.data.data.records || []
}
async function handleModelChange(modelId: string) {
  updateConfig('modelConfigId', modelId)
  if (config.value.modelParamsOverrideEnabled) {
    await loadModelParams(modelId)
  }
}
function handleModelClear() {
  updateConfig('modelConfigId', undefined)
}
async function loadModelParams(modelId: string) {
  const response = await modelApi.configDetail(modelId)
  const model = response.data.data
  modelParamsForm.value = {
    temperature: model.temperature,
    topP: model.topP,
    topK: model.topK,
    maxTokens: model.maxTokens,
    repeatPenalty: model.repeatPenalty,
    seed: model.seed,
    extendConfig: model.extendConfig ? { ...model.extendConfig } : null,
  }
}
async function handleOverrideToggle(checked: boolean) {
  if (checked && config.value.modelConfigId) {
    await loadModelParams(String(config.value.modelConfigId))
    updateConfig('modelParamsOverrideEnabled', checked)
  } else {
    modelParamsFormGuard.value = true
    modelParamsForm.value = {}
    updateNode({
      config: {
        ...(props.node.data.config || {}),
        modelParamsOverrideEnabled: false,
        modelParamsOverride: {},
      },
    })
    modelParamsFormGuard.value = false
  }
}

watch(modelParamsForm, (value) => {
  if (modelParamsFormGuard.value) return
  if (config.value.modelParamsOverrideEnabled) {
    updateConfig('modelParamsOverride', { ...value })
  }
}, { deep: true })

onMounted(async () => {
  if (config.value.modelParamsOverride && Object.keys(config.value.modelParamsOverride).length > 0) {
    modelParamsForm.value = { ...config.value.modelParamsOverride }
  }
  loading.value = true
  try {
    await Promise.all([loadModelProviders(), loadAllModels()])
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="intent-recognition-panel">
    <div v-if="loading" class="loading-overlay">
      <LoadingOutlined style="margin-right: 6px" />加载中
    </div>
    <AForm layout="vertical">
      <PanelSection title="节点名称">
        <NodeNameInput
          :model-value="node.data.label"
          @update:model-value="(v: any) => updateNode({ label: v })"
        />
      </PanelSection>

      <AutoInputBinding
        :model-value="node.data.inputConfigs"
        :nodes="nodes"
        :edges="edges"
        :current-node-id="node.id"
        :draggable="false"
        title="输入节点（第一个有效）"
        @update:model-value="(v: any) => updateNode({ inputConfigs: v })"
      />

      <PanelSection title="模型">
        <AFormItem label="模型配置" required>
          <ModelConfigSelector
            :model-value="String(config.modelConfigId || '')"
            :providers="modelProviders"
            :models="allModels"
            @update:model-value="(value: string) => handleModelChange(value)"
            @clear="handleModelClear"
          />
        </AFormItem>

        <div class="switch-row">
          <span class="switch-label">启用参数覆盖</span>
          <ASwitch
            :checked="Boolean(config.modelParamsOverrideEnabled)"
            :disabled="!config.modelConfigId"
            @update:checked="(value: any) => handleOverrideToggle(Boolean(value))"
          />
        </div>

        <div v-if="config.modelParamsOverrideEnabled" class="params-grid">
          <AFormItem label="Temperature">
            <AInputNumber v-model:value="modelParamsForm.temperature" :min="0" :max="2" :step="0.1" />
          </AFormItem>
          <AFormItem label="Top P">
            <AInputNumber v-model:value="modelParamsForm.topP" :min="0" :max="1" :step="0.1" />
          </AFormItem>
          <AFormItem label="Top K">
            <AInputNumber v-model:value="modelParamsForm.topK" :min="0" :max="100" />
          </AFormItem>
          <AFormItem label="Max Tokens">
            <AInputNumber v-model:value="modelParamsForm.maxTokens" :min="1" :max="1000000" />
          </AFormItem>
          <AFormItem label="Repeat Penalty">
            <AInputNumber v-model:value="modelParamsForm.repeatPenalty" :min="0" :max="2" :step="0.1" />
          </AFormItem>
          <AFormItem label="Seed">
            <AInput v-model:value="modelParamsForm.seed" placeholder="留空表示随机" />
          </AFormItem>
        </div>

        <div v-if="config.modelParamsOverrideEnabled" class="extend-config">
          <AFormItem label="扩展配置">
            <ExtendConfigEditor
              v-model="extendConfigForm"
              compact
              :show-fixed-system-message="showFixedSystemMessage"
            />
          </AFormItem>
        </div>
      </PanelSection>

      <PanelSection title="系统提示词扩展">
        <div class="builtin-tip">
          系统已内置意图识别的标准提示词，附加说明将追加到提示词末尾。
        </div>
        <ATextarea
          :value="String(config.systemPromptExtension || '')"
          :rows="3"
          placeholder="追加额外的系统提示词，提升意图匹配成功率。例如：当用户提到价格时，优先匹配 '价格咨询' 意图。"
          @update:value="(v: any) => updateConfig('systemPromptExtension', v)"
        />
      </PanelSection>

      <PanelSection title="意图匹配">
        <div class="intent-list">
          <div v-for="(item, index) in displayIntents" :key="item.nextNodeId" class="intent-card">
            <div class="intent-card-header">
              <IconFont
                :name="getNodeIconName(downstreamNodes.find(n => n.id === item.nextNodeId)?.data.type || '')"
                :size="16"
                :color="item.nodeColor || '#1677ff'"
              />
              <span class="intent-card-title">{{ item.nodeLabel || item.nextNodeId }}</span>
            </div>
            <div class="intent-card-body">
              <div class="intent-field">
                <span class="intent-field-label">意图名称</span>
                <AInput
                  :value="item.name"
                  placeholder="如：退款咨询"
                  @update:value="(v: any) => updateIntent(index, 'name', v)"
                />
              </div>
              <div class="intent-field">
                <span class="intent-field-label">意图描述</span>
                <AInput
                  :value="item.description"
                  placeholder="描述该意图的特征，帮助模型更准确识别"
                  @update:value="(v: any) => updateIntent(index, 'description', v)"
                />
              </div>
            </div>
          </div>
          <div v-if="displayIntents.length === 0" class="intent-empty">
            请先连接下游节点，然后为各节点填写意图名称和描述。
          </div>
        </div>
      </PanelSection>

      <PanelSection title="默认路由">
        <div class="config-row">
          <span class="config-row-label">
            默认输出
            <ATooltip title="当所有意图都不匹配时，路由到此处选择的默认节点">
              <QuestionCircleOutlined class="help-icon" />
            </ATooltip>
          </span>
          <div class="next-node-selector">
            <NextNodeSelector
              :nodes="nodes"
              :edges="edges"
              :current-node-id="node.id"
              :selected-node-id="(config.defaultNextNodeId as string) || undefined"
              @select="(id: string) => updateConfig('defaultNextNodeId', id)"
              @clear="updateConfig('defaultNextNodeId', undefined)"
            />
          </div>
        </div>
      </PanelSection>

      <PanelSection title="输出说明">
        <OutputDisplay :outputs="node.data.outputConfigs || []" />
      </PanelSection>
    </AForm>
  </div>
</template>

<style scoped lang="scss">
.intent-recognition-panel {
  position: relative;
}
.loading-overlay {
  position: sticky;
  top: 0;
  z-index: 10;
  height: 100vh;
  margin-bottom: -100vh;
  background: rgba(255, 255, 255, 0.65);
  display: flex;
  align-items: center;
  justify-content: center;
}
.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 32px;
  margin-bottom: 12px;
}
.switch-label {
  flex-shrink: 0;
  color: rgba(0, 0, 0, 0.88);
  font-size: 14px;
}
.params-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 12px;
  padding: 12px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fafafa;
  :deep(.ant-input-number),
  :deep(.ant-input) { width: 100%; }
  :deep(.ant-form-item) { margin-bottom: 0; }
}
.extend-config {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed rgba(15, 23, 42, 0.08);
}
.builtin-tip {
  margin-bottom: 8px;
  padding: 0 8px;
  border-radius: 6px;
  color: #8c8c8c;
  font-size: 12px;
  line-height: 1.6;
}
.intent-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.intent-card {
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  overflow: hidden;
}
.intent-card-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border-bottom: 1px solid #f0f0f0;
  background: #F2F4F7;
}
.intent-card-title {
  font-size: 13px;
  font-weight: 500;
  color: rgba(0, 0, 0, 0.85);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.intent-card-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 12px 12px;
}
.intent-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.intent-field-label {
  font-size: 12px;
  color: #8c8c8c;
  line-height: 1;
}
.intent-empty {
  padding: 16px;
  text-align: center;
  color: #8c8c8c;
  font-size: 13px;
}
.config-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 32px;
}
.config-row-label {
  flex-shrink: 0;
  font-size: 14px;
  color: rgba(0, 0, 0, 0.88);
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.next-node-selector {
  flex: 1;
  margin-left: 12px;
  min-width: 0;
}
.help-icon {
  color: rgba(0, 0, 0, 0.25);
  font-size: 13px;
  cursor: help;
  &:hover { color: rgba(0, 0, 0, 0.45); }
}
</style>
