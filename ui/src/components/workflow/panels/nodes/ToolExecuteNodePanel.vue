<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { QuestionCircleOutlined } from '@ant-design/icons-vue'
import PanelSection from '../shared/PanelSection.vue'
import NodeNameInput from '../shared/NodeNameInput.vue'
import InputBindingSection from '../shared/InputBindingSection.vue'
import OutputDisplay from '../shared/OutputDisplay.vue'
import ToolSelect from '@/components/workflow/bindings/ToolSelect.vue'
import * as toolApi from '@/api/tool'
import type { ToolVO } from '@/types'
import type { WorkflowFlowEdge, WorkflowFlowNode, WorkflowInputConfig, WorkflowResourceMaps } from '@/types/workflow'

interface ToolExecuteConfig {
  toolId?: string | null
  toolName?: string | null
}

const props = defineProps<{
  node: WorkflowFlowNode
  nodes: WorkflowFlowNode[]
  edges: WorkflowFlowEdge[]
  resources: WorkflowResourceMaps
}>()

const emit = defineEmits<{ update: [node: WorkflowFlowNode] }>()

const toolCategories = ref<string[]>([])
const allTools = ref<ToolVO[]>([])

const config = computed(() => (props.node.data.config || {}) as ToolExecuteConfig)

const selectedToolId = computed(() => config.value.toolId || null)

const selectedTool = computed(() => {
  if (!selectedToolId.value) return null
  return allTools.value.find((t) => String(t.id) === String(selectedToolId.value)) || null
})

function updateNode(patch: Partial<WorkflowFlowNode['data']>) {
  emit('update', { ...props.node, data: { ...props.node.data, ...patch } })
}

function updateConfig(key: string, value: unknown) {
  updateNode({ config: { ...(props.node.data.config || {}), [key]: value } })
}

/** 工具选择变更时，同步更新 inputConfigs 和 toolName */
function onToolChange(toolId: string | null) {
  updateConfig('toolId', toolId)
  if (toolId) {
    const tool = allTools.value.find((t) => String(t.id) === String(toolId))
    if (tool && tool.inputSchema && Array.isArray(tool.inputSchema)) {
      const inputs: WorkflowInputConfig[] = tool.inputSchema.map((param: Record<string, unknown>) => ({
        name: String(param.name || ''),
        sourceType: 'NODE_OUTPUT' as const,
      }))
      updateNode({ inputConfigs: inputs, config: { ...props.node.data.config, toolId, toolName: tool.name } })
    } else {
      updateConfig('toolName', tool?.name || null)
    }
  } else {
    updateNode({ inputConfigs: [], config: { ...props.node.data.config, toolId: null, toolName: null } })
  }
}

async function loadToolCategories() {
  const response = await toolApi.listCategories()
  toolCategories.value = response.data.data || []
}

async function loadAllTools() {
  const response = await toolApi.page({ page: 1, size: 1000, enabled: true })
  allTools.value = response.data.data.records || []
}

onMounted(() => {
  loadToolCategories()
  loadAllTools()
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
    <PanelSection title="输入绑定" v-if="!selectedTool">
      <div class="input-hint">请先选择一个工具，输入绑定将根据工具的参数自动生成</div>
    </PanelSection>
    <InputBindingSection
      v-else
      :model-value="node.data.inputConfigs"
      :nodes="nodes"
      :edges="edges"
      :current-node-id="node.id"
      :readonly-name="true"
      @update:model-value="(v: any) => updateNode({ inputConfigs: v })"
    />

    <!-- 3. 工具选择 -->
    <PanelSection title="工具选择">
      <div class="config-block">
        <ToolSelect
          :model-value="selectedToolId"
          :tools="allTools"
          :categories="toolCategories"
          :multiple="false"
          @update:model-value="(v: any) => onToolChange(v as string | null)"
        />
      </div>
    </PanelSection>

    <!-- 4. 输出说明 -->
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

.config-block-label {
  font-size: 14px;
  color: rgba(0, 0, 0, 0.88);
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.help-icon {
  color: rgba(0, 0, 0, 0.25);
  font-size: 13px;
  cursor: help;

  &:hover {
    color: rgba(0, 0, 0, 0.45);
  }
}

.input-hint {
  padding: 16px;
  color: #8c8c8c;
  font-size: 13px;
  text-align: center;
  line-height: 1.6;
}
</style>
