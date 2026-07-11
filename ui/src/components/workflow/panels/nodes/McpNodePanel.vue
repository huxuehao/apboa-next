<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { QuestionCircleOutlined } from '@ant-design/icons-vue'
import PanelSection from '../shared/PanelSection.vue'
import NodeNameInput from '../shared/NodeNameInput.vue'
import InputBindingSection from '../shared/InputBindingSection.vue'
import OutputDisplay from '../shared/OutputDisplay.vue'
import McpSelect from '@/components/workflow/bindings/McpSelect.vue'
import * as mcpApi from '@/api/mcp'
import type { McpServerVO, McpToolVO } from '@/types'
import type { WorkflowFlowEdge, WorkflowFlowNode, WorkflowInputConfig, WorkflowResourceMaps } from '@/types/workflow'

interface McpNodeConfig {
  mcpServerId?: string | null
  mcpToolId?: string | null
  mcpServerName?: string | null
  mcpToolName?: string | null
}

const props = defineProps<{
  node: WorkflowFlowNode
  nodes: WorkflowFlowNode[]
  edges: WorkflowFlowEdge[]
  resources: WorkflowResourceMaps
}>()

const emit = defineEmits<{ update: [node: WorkflowFlowNode] }>()

const allServers = ref<McpServerVO[]>([])
const mcpTools = ref<McpToolVO[]>([])

const config = computed(() => (props.node.data.config || {}) as McpNodeConfig)

const selectedServerId = computed(() => config.value.mcpServerId || null)

const selectedServer = computed(() => {
  if (!selectedServerId.value) return null
  return allServers.value.find((s) => String(s.id) === String(selectedServerId.value)) || null
})

const selectedToolId = computed(() => config.value.mcpToolId || null)

const selectedTool = computed(() => {
  if (!selectedToolId.value) return null
  return mcpTools.value.find((t) => String(t.id) === String(selectedToolId.value)) || null
})

const toolOptions = computed(() =>
  mcpTools.value
    .filter((t) => t.enabled && !t.missing)
    .map((t) => ({ label: t.toolName, value: String(t.id), description: t.description })),
)

function updateNode(patch: Partial<WorkflowFlowNode['data']>) {
  emit('update', { ...props.node, data: { ...props.node.data, ...patch } })
}

function onServerChange(serverId: string | null) {
  updateNode({
    config: {
      ...props.node.data.config,
      mcpServerId: serverId || undefined,
      mcpToolId: undefined,
      mcpToolName: undefined,
      mcpServerName: serverId ? allServers.value.find((s) => String(s.id) === serverId)?.name : undefined,
    },
    inputConfigs: [],
  })
  mcpTools.value = []
  if (serverId) {
    loadMcpTools(serverId)
  }
}

/** MCP工具选择变更 */
function onToolChange(toolId: string | null) {
  const newConfig = {
    ...props.node.data.config,
    mcpToolId: toolId || undefined,
    mcpToolName: toolId ? mcpTools.value.find((t) => String(t.id) === toolId)?.toolName : undefined,
  }
  let inputs: WorkflowInputConfig[] = []
  if (toolId) {
    const tool = mcpTools.value.find((t) => String(t.id) === toolId)
    if (tool && tool.inputSchema) {
      const props_ = (tool.inputSchema as Record<string, unknown>).properties as Record<string, unknown> | undefined
      if (props_) {
        inputs = Object.keys(props_).map((name) => ({
          name,
          sourceType: 'NODE_OUTPUT' as const,
        }))
      }
    }
  }
  updateNode({ config: newConfig, inputConfigs: inputs })
}

async function loadAllServers() {
  const response = await mcpApi.page({ page: 1, size: 1000, enabled: true })
  allServers.value = response.data.data.records || []
}

async function loadMcpTools(serverId: string) {
  const response = await mcpApi.listTools(serverId)
  mcpTools.value = response.data.data || []
}

onMounted(() => {
  loadAllServers()
  if (selectedServerId.value) {
    loadMcpTools(selectedServerId.value)
  }
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
      <template v-if="!selectedTool">
        <div class="input-hint">请先选择 MCP 服务和工具，输入绑定将根据工具的参数自动生成</div>
      </template>
      <template v-else>
        <InputBindingSection
          :model-value="node.data.inputConfigs"
          :nodes="nodes"
          :edges="edges"
          :current-node-id="node.id"
          :readonly-name="true"
          @update:model-value="(v: any) => updateNode({ inputConfigs: v })"
        />
      </template>
    </PanelSection>

    <!-- 3. MCP配置 -->
    <PanelSection title="MCP配置">
      <div class="config-block">
        <div class="config-block-label">
          MCP服务
          <ATooltip title="选择要调用的 MCP 服务，仅显示已激活且包含可用工具的服务">
            <QuestionCircleOutlined class="help-icon" />
          </ATooltip>
        </div>
        <McpSelect
          :model-value="selectedServerId"
          :servers="allServers"
          :single="true"
          @update:model-value="(v: any) => onServerChange(v as string | null)"
        />
      </div>
      <div v-if="selectedServer" class="config-block" style="margin-top: 16px;">
        <div class="config-block-label">
          MCP工具
          <ATooltip title="选择该 MCP 服务中要调用的具体工具">
            <QuestionCircleOutlined class="help-icon" />
          </ATooltip>
        </div>
        <ASelect
          :value="selectedToolId"
          :options="toolOptions"
          placeholder="选择工具..."
          allow-clear
          show-search
          :filter-option="(input: string, option: any) => (option.label || '').toLowerCase().includes(input.toLowerCase()) || (option.description || '').toLowerCase().includes(input.toLowerCase())"
          style="width: 100%;"
          @update:value="(v: any) => onToolChange(v || null)"
        >
          <template #option="{ label, description }">
            <div class="tool-option">
              <span class="tool-option-name">{{ label }}</span>
              <span class="tool-option-desc">{{ description }}</span>
            </div>
          </template>
        </ASelect>
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

.tool-option {
  display: flex;
  flex-direction: column;
}

.tool-option-name {
  font-size: 13px;
  color: #262626;
}

.tool-option-desc {
  font-size: 11px;
  color: #a8a8a8;
}
</style>
