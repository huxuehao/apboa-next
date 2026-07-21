<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import ModelConfigSelector from '@/components/workflow/bindings/ModelConfigSelector.vue'
import SkillSelect from '@/components/workflow/bindings/SkillSelect.vue'
import ToolSelect from '@/components/workflow/bindings/ToolSelect.vue'
import McpSelect, { type McpBinding } from '@/components/workflow/bindings/McpSelect.vue'
import PanelSection from '../shared/PanelSection.vue'
import FormatterGuideModal from '../shared/FormatterGuideModal.vue'
import NodeNameInput from '../shared/NodeNameInput.vue'
import InputBindingSection from '../shared/InputBindingSection.vue'
import OutputDisplay from '../shared/OutputDisplay.vue'
import ConfigCodeEditor from '@/components/editor/ConfigCodeEditor.vue'
import ExtendConfigEditor, { type ExtendConfigData } from '@/components/model/ExtendConfigEditor.vue'
import * as modelApi from '@/api/model'
import * as skillApi from '@/api/skill'
import * as toolApi from '@/api/tool'
import * as mcpApi from '@/api/mcp'
import { RoutePaths } from '@/router/constants.ts'
import type {
  McpServerVO,
  ModelConfigVO,
  ModelProviderVO,
  SkillPackageVO,
  ToolVO,
} from '@/types'
import { McpToolExposureMode } from '@/types'
import type { WorkflowFlowEdge, WorkflowFlowNode, WorkflowResourceMaps } from '@/types/workflow'
import { LoadingOutlined } from '@ant-design/icons-vue'

interface AgentNodeMcpConfig {
  mcpServerId: string
  exposureMode: McpToolExposureMode
  mcpToolIds: string[]
}

interface AgentModelParamsOverride {
  temperature?: number
  topP?: number
  topK?: number
  maxTokens?: number
  repeatPenalty?: number
  seed?: string | number
  extendConfig?: ExtendConfigData | null
}

interface AgentNodeConfig {
  modelConfigId?: string
  modelParamsOverrideEnabled?: boolean
  modelParamsOverride?: AgentModelParamsOverride
  formatterType?: 'STRING' | 'VELOCITY'
  systemPrompt?: string
  userPrompt?: string
  skillPackageIds?: string[]
  toolIds?: string[]
  mcps?: AgentNodeMcpConfig[]
  maxIterations?: number
  structuredOutputEnabled?: boolean
  structuredOutput?: Record<string, unknown>
}

const props = defineProps<{
  node: WorkflowFlowNode
  nodes: WorkflowFlowNode[]
  edges: WorkflowFlowEdge[]
  resources: WorkflowResourceMaps
}>()

const emit = defineEmits<{ update: [node: WorkflowFlowNode] }>()

const panelRoot = ref<HTMLElement>()
const isEditorMaximized = ref(false)
const loading = ref(false)
const modelProviders = ref<ModelProviderVO[]>([])
const allModels = ref<ModelConfigVO[]>([])
const toolCategories = ref<string[]>([])
const allTools = ref<ToolVO[]>([])
const skillCategories = ref<string[]>([])
const allSkills = ref<SkillPackageVO[]>([])
const allMcpServers = ref<McpServerVO[]>([])
const modelParamsForm = ref<AgentModelParamsOverride>({})
const modelParamsFormGuard = ref(false)
const structuredOutputText = ref('{}')
const structuredOutputError = ref('')

const config = computed(() => (props.node.data.config || {}) as AgentNodeConfig)

const formatterOptions = [
  { label: '字符串', value: 'STRING' },
  { label: 'Velocity', value: 'VELOCITY' },
]

const mcpBindings = computed(() =>
  (Array.isArray(config.value.mcps) ? config.value.mcps : []).map((item) => ({ mcpServerId: String(item.mcpServerId) })),
)

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

function updateConfig(key: keyof AgentNodeConfig, value: unknown) {
  updateNode({ config: { ...(props.node.data.config || {}), [key]: value } })
}

function arrayConfig(key: 'toolIds' | 'skillPackageIds') {
  const value = config.value[key]
  return Array.isArray(value) ? value.map((item) => String(item)) : []
}

function onEditorMaximizeChange(value: boolean) {
  isEditorMaximized.value = value
}

async function loadModelProviders() {
  const response = await modelApi.providerPage({ page: 1, size: 100, enabled: true })
  modelProviders.value = response.data.data.records || []
}

async function loadAllModels() {
  const response = await modelApi.configPage({ page: 1, size: 1000, enabled: true })
  allModels.value = response.data.data.records || []
}

async function loadToolCategories() {
  const response = await toolApi.listCategories()
  toolCategories.value = response.data.data || []
}

async function loadAllTools() {
  const response = await toolApi.page({ page: 1, size: 1000, enabled: true })
  allTools.value = response.data.data.records || []
}

async function loadSkillCategories() {
  const response = await skillApi.listCategories()
  skillCategories.value = response.data.data || []
}

async function loadAllSkills() {
  const response = await skillApi.page({ page: 1, size: 1000, enabled: true })
  allSkills.value = response.data.data.records || []
}

async function loadAllMcpServers() {
  const response = await mcpApi.page({ page: 1, size: 1000 })
  allMcpServers.value = response.data.data.records || []
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
  const extendConfig = normalizeExtendConfig(model.extendConfig as ExtendConfigData | null | undefined)
  modelParamsForm.value = {
    temperature: model.temperature,
    topP: model.topP,
    topK: model.topK,
    maxTokens: model.maxTokens,
    repeatPenalty: model.repeatPenalty,
    seed: model.seed,
    extendConfig,
  }
}

function normalizeExtendConfig(value: ExtendConfigData | null | undefined) {
  if (!value || typeof value !== 'object') return null
  return {
    headers: value.headers || {},
    queryParams: value.queryParams || {},
    bodyParams: value.bodyParams || {},
    fixedSystemMessage: value.fixedSystemMessage ?? false,
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

function handleMcpBindingsChange(bindings: string | McpBinding[] | null) {
  const list = Array.isArray(bindings) ? bindings : []
  updateConfig('mcps', list.map((b) => ({
    mcpServerId: b.mcpServerId,
    exposureMode: McpToolExposureMode.ALL_GLOBAL,
    mcpToolIds: [],
  })))
}

function handleStructuredOutputToggle(checked: boolean) {
  updateConfig('structuredOutputEnabled', checked)
  if (checked) {
    syncStructuredOutputText(config.value.structuredOutput || {})
  }
}

function syncStructuredOutputText(value: unknown) {
  structuredOutputText.value = JSON.stringify(value && typeof value === 'object' ? value : {}, null, 2)
  structuredOutputError.value = ''
}

function handleStructuredOutputChange(value: string) {
  structuredOutputText.value = value
  try {
    const parsed = JSON.parse(value || '{}')
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      structuredOutputError.value = '结构化输出必须是 JSON 对象'
      return
    }
    structuredOutputError.value = ''
    updateConfig('structuredOutput', parsed)
  } catch {
    structuredOutputError.value = 'JSON 格式不正确'
  }
}

watch(modelParamsForm, (value) => {
  if (modelParamsFormGuard.value) return
  if (config.value.modelParamsOverrideEnabled) {
    updateConfig('modelParamsOverride', { ...value })
  }
}, { deep: true })

watch(() => config.value.structuredOutput, (value) => {
  if (!structuredOutputError.value) {
    syncStructuredOutputText(value || {})
  }
}, { deep: true })

onMounted(async () => {
  syncStructuredOutputText(config.value.structuredOutput || {})
  if (config.value.modelParamsOverride && Object.keys(config.value.modelParamsOverride).length > 0) {
    modelParamsForm.value = {
      ...config.value.modelParamsOverride,
      extendConfig: normalizeExtendConfig(config.value.modelParamsOverride.extendConfig),
    }
  }

  try {
    loading.value = true
    await Promise.all([
      loadModelProviders(),
      loadAllModels(),
      loadToolCategories(),
      loadAllTools(),
      loadSkillCategories(),
      loadAllSkills(),
      loadAllMcpServers(),
    ])
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div ref="panelRoot" class="agent-node-panel" :class="{ 'editor-maximized': isEditorMaximized }">
    <div v-if="loading" class="loading-overlay">
      <LoadingOutlined style="margin-right: 6px" />加载中
    </div>
    <AForm layout="vertical">
      <PanelSection title="节点名称">
        <NodeNameInput
          :model-value="node.data.label"
          @update:model-value="(value: any) => updateNode({ label: value })"
        />
      </PanelSection>

      <InputBindingSection
        :model-value="node.data.inputConfigs"
        :nodes="nodes"
        :edges="edges"
        :current-node-id="node.id"
        @update:model-value="(value: any) => updateNode({ inputConfigs: value })"
      />

      <PanelSection title="模型">
        <AFormItem label="模型配置" required>
          <template v-if="allModels.length > 0">
            <ModelConfigSelector
              :model-value="String(config.modelConfigId || '')"
              :providers="modelProviders"
              :models="allModels"
              @update:model-value="(value: string) => handleModelChange(value)"
              @clear="handleModelClear"
            />
          </template>
          <div v-else class="empty-action">
            <AButton type="text">未配置模型</AButton>
            <AButton type="link" :href="`/#/${RoutePaths.MODEL}`" target="_blank">去配置</AButton>
            <AButton type="link" @click="loadModelProviders(); loadAllModels()">刷新</AButton>
          </div>
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

      <PanelSection title="提示词">
        <div class="config-row">
          <span class="config-row-label">提示词模板</span>
          <div class="formatter-selector">
            <FormatterGuideModal />
            <ASelect
              :value="config.formatterType || 'STRING'"
              :options="formatterOptions"
              style="width: 130px"
              @update:value="(value: any) => updateConfig('formatterType', value)"
            />
          </div>
        </div>

        <div class="editor-field">
          <div class="editor-label required-field">
            系统提示词
          </div>
          <ConfigCodeEditor
            :model-value="String(config.systemPrompt || '')"
            language="txt"
            placeholder="输入系统提示词，可使用 ${input} 引用输入绑定"
            :maximize-target="panelRoot"
            @update:model-value="(value: string) => updateConfig('systemPrompt', value)"
            @maximize-change="onEditorMaximizeChange"
          />
        </div>

        <div class="editor-field">
          <div class="editor-label required-field">
            用户提示词
          </div>
          <ConfigCodeEditor
            :model-value="String(config.userPrompt || '')"
            language="txt"
            placeholder="输入用户提示词，可使用 ${input} 引用输入绑定"
            :maximize-target="panelRoot"
            @update:model-value="(value: string) => updateConfig('userPrompt', value)"
            @maximize-change="onEditorMaximizeChange"
          />
        </div>
      </PanelSection>

      <PanelSection title="能力选择">
        <AFormItem label="技能包">
          <template v-if="allSkills.length > 0">
            <SkillSelect
              :model-value="arrayConfig('skillPackageIds')"
              :skills="allSkills"
              :categories="skillCategories"
              @update:model-value="(ids: string[]) => updateConfig('skillPackageIds', ids)"
            />
          </template>
          <div v-else class="empty-action">
            <AButton type="text">未配置技能包</AButton>
            <AButton type="link" :href="`/#/${RoutePaths.SKILL}`" target="_blank">去配置</AButton>
            <AButton type="link" @click="loadSkillCategories(); loadAllSkills()">刷新</AButton>
          </div>
        </AFormItem>

        <AFormItem label="工具">
          <template v-if="allTools.length > 0">
            <ToolSelect
              :model-value="arrayConfig('toolIds')"
              :tools="allTools"
              :categories="toolCategories"
              @update:model-value="(ids: string | string[] | null) => updateConfig('toolIds', ids)"
            />
          </template>
          <div v-else class="empty-action">
            <AButton type="text">未配置工具</AButton>
            <AButton type="link" :href="`/#/${RoutePaths.TOOL}`" target="_blank">去配置</AButton>
            <AButton type="link" @click="loadToolCategories(); loadAllTools()">刷新</AButton>
          </div>
        </AFormItem>

        <AFormItem label="MCP">
          <template v-if="allMcpServers.length > 0">
            <McpSelect
              :model-value="mcpBindings"
              :servers="allMcpServers"
              @update:model-value="handleMcpBindingsChange"
            />
          </template>
          <div v-else class="empty-action">
            <AButton type="text">未配置 MCP 服务</AButton>
            <AButton type="link" :href="`/#/${RoutePaths.MCP}`" target="_blank">去配置</AButton>
            <AButton type="link" @click="loadAllMcpServers()">刷新</AButton>
          </div>
        </AFormItem>
      </PanelSection>

      <PanelSection title="执行与输出">
        <div class="config-row">
          <span class="config-row-label">最大迭代次数</span>
          <AInputNumber
            :value="Number(config.maxIterations ?? 5)"
            :min="1"
            style="width: 130px"
            @update:value="(value: any) => updateConfig('maxIterations', value)"
          />
        </div>
        <div class="switch-row">
          <span class="switch-label">结构化输出</span>
          <ASwitch
            :checked="Boolean(config.structuredOutputEnabled)"
            @update:checked="(value: any) => handleStructuredOutputToggle(Boolean(value))"
          />
        </div>
        <div v-if="config.structuredOutputEnabled" class="structured-editor">
          <ConfigCodeEditor
            :model-value="structuredOutputText"
            language="json"
            placeholder="{ &quot;type&quot;: &quot;object&quot;, &quot;properties&quot;: {} }"
            height="220px"
            :maximize-target="panelRoot"
            @update:model-value="handleStructuredOutputChange"
            @maximize-change="onEditorMaximizeChange"
          />
          <AAlert
            v-if="structuredOutputError"
            class="json-error"
            type="warning"
            show-icon
            :message="structuredOutputError"
          />
        </div>
      </PanelSection>

      <PanelSection title="输出说明">
        <OutputDisplay :outputs="node.data.outputConfigs || []" />
      </PanelSection>
    </AForm>
  </div>
</template>

<style scoped lang="scss">
.agent-node-panel {
  position: relative;

  &.editor-maximized {
    height: 100%;
    overflow: hidden;
  }
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

.checkbox-grid,
.tool-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 10px;
}

.checkbox-item,
.tool-item {
  width: 100%;
  min-width: 0;
  margin: 0 !important;
  padding: 10px;
  border: 1px solid var(--color-border-base);
  border-radius: 8px;
  transition: all var(--transition-base);

  &:hover {
    border-color: var(--color-primary);
    background-color: var(--color-bg-light);
  }
}

.item-info {
  min-width: 0;
}

.item-name {
  min-width: 0;
  overflow: hidden;
  color: #262626;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-desc {
  min-width: 0;
  overflow: hidden;
  margin-top: 4px;
  color: #8c8c8c;
  font-size: 12px;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.config-row,
.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 32px;
  margin-bottom: 12px;
}

.editor-field {
  margin-bottom: 16px;
}

.editor-label {
  margin-bottom: 4px;
  font-size: 14px;
  color: rgba(0, 0, 0, 0.88);
}

.config-row-label,
.switch-label {
  flex-shrink: 0;
  color: rgba(0, 0, 0, 0.88);
  font-size: 14px;
}

.formatter-selector {
  display: flex;
  align-items: center;
  gap: 6px;
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
  :deep(.ant-input) {
    width: 100%;
  }

  :deep(.ant-form-item) {
    margin-bottom: 0;
  }
}

.extend-config {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed rgba(15, 23, 42, 0.08);
}

.structured-editor {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.json-error {
  border-radius: 6px;
}

.empty-action {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  color: #8c8c8c;
}

@media (max-width: 720px) {
  .params-grid,
  .checkbox-grid,
  .tool-grid {
    grid-template-columns: 1fr;
  }

  .mcp-item-header {
    flex-direction: column;
  }

  .mcp-tag-group {
    justify-content: flex-start;
  }
}
</style>
