/**
 * 智能体架构图组件
 * 使用 Vue Flow 展示智能体的完整配置架构
 *
 * @author huxuehao
 */
<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch, markRaw } from 'vue'
import { VueFlow, useVueFlow, type Node, type Edge } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { MiniMap } from '@vue-flow/minimap'
import { ControlButton, Controls } from '@vue-flow/controls'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/minimap/dist/style.css'
import '@vue-flow/controls/dist/style.css'
import { LockOutlined, NodeIndexOutlined, UnlockOutlined } from '@ant-design/icons-vue'
import { Spin } from 'ant-design-vue'

import { useArchitectureData } from './composables/useArchitectureData'
import { CATEGORY_CONFIGS, type CategoryType } from './types'
import { getArchitectureEdgeHandles, layoutArchitectureNodes } from './architectureLayout'

import CenterAgentNode from './nodes/CenterAgentNode.vue'
import CategoryNode from './nodes/CategoryNode.vue'
import ToolItemNode from './nodes/ToolItemNode.vue'
import HookItemNode from './nodes/HookItemNode.vue'
import SkillItemNode from './nodes/SkillItemNode.vue'
import McpItemNode from './nodes/McpItemNode.vue'
import KnowledgeItemNode from './nodes/KnowledgeItemNode.vue'
import AgentItemNode from './nodes/AgentItemNode.vue'
import WorkflowItemNode from './nodes/WorkflowItemNode.vue'
import ModelNode from './nodes/ModelNode.vue'
import PromptNode from './nodes/PromptNode.vue'
import AdvancedConfigNode from './nodes/AdvancedConfigNode.vue'
import SensitiveItemNode from './nodes/SensitiveItemNode.vue'

/**
 * Props定义
 */
const props = defineProps<{
  agentId: string
}>()

/**
 * 数据获取
 */
const { loading, data, loadArchitectureData, resetData } = useArchitectureData()

/**
 * Vue Flow
 */
const { fitView, setNodes } = useVueFlow()

/**
 * 只锁定节点位置，不影响画布平移、节点选择和既有连线
 */
const layoutLocked = ref(false)

/**
 * 自定义节点类型
 */
const nodeTypes = {
  'center-agent': markRaw(CenterAgentNode),
  'category': markRaw(CategoryNode),
  'tool-item': markRaw(ToolItemNode),
  'hook-item': markRaw(HookItemNode),
  'skill-item': markRaw(SkillItemNode),
  'mcp-item': markRaw(McpItemNode),
  'knowledge-item': markRaw(KnowledgeItemNode),
  'agent-item': markRaw(AgentItemNode),
  'workflow-item': markRaw(WorkflowItemNode),
  'model': markRaw(ModelNode),
  'prompt': markRaw(PromptNode),
  'advanced-config': markRaw(AdvancedConfigNode),
  'sensitive-item': markRaw(SensitiveItemNode)
} as any

/**
 * 需要显示的分类列表（仅包含多对一关系的分类）
 */
const activeCategories = computed<CategoryType[]>(() => {
  const categories: CategoryType[] = []

  if (data.tools.length > 0) categories.push('tool')
  if (data.workflows.length > 0) categories.push('workflow')
  if (data.hooks.length > 0) categories.push('hook')
  if (data.skills.length > 0) categories.push('skill')
  if (data.mcps.length > 0) categories.push('mcp')
  if (data.knowledgeBases.length > 0) categories.push('knowledge')
  if (data.subAgents.length > 0) categories.push('sub-agent')

  return categories
})

/**
 * 生成节点
 */
const rawNodes = computed<Node[]>(() => {
  if (!data.agent) return []

  const result: Node[] = []
  // 中心节点
  result.push({
    id: 'center',
    type: 'center-agent',
    position: { x: 0, y: 0 },
    data: { agent: data.agent }
  })

  // 遭历活跃的分类
  activeCategories.value.forEach((category) => {
    const config = CATEGORY_CONFIGS[category]

    // 分类节点
    let count = 0
    switch (category) {
      case 'tool': count = data.tools.length; break
      case 'workflow': count = data.workflows.length; break
      case 'hook': count = data.hooks.length; break
      case 'skill': count = data.skills.length; break
      case 'mcp': count = data.mcps.length; break
      case 'knowledge': count = data.knowledgeBases.length; break
      case 'sub-agent': count = data.subAgents.length; break
      default: count = 0
    }

    result.push({
      id: `category-${category}`,
      type: 'category',
      position: { x: 0, y: 0 },
      data: {
        category,
        label: config.label,
        count,
        icon: config.icon,
        color: config.color,
        bgColor: config.bgColor,
        borderColor: config.borderColor
      }
    })

    // 配置项节点
    switch (category) {
      case 'tool':
        data.tools.forEach((tool) => {
          result.push({
            id: `tool-${tool.id}`,
            type: 'tool-item',
            position: { x: 0, y: 0 },
            data: { tool }
          })
        })
        break

      case 'workflow':
        data.workflows.forEach((workflow) => {
          result.push({
            id: `workflow-${workflow.id}`,
            type: 'workflow-item',
            position: { x: 0, y: 0 },
            data: { workflow }
          })
        })
        break

      case 'hook':
        data.hooks.forEach((hook) => {
          result.push({
            id: `hook-${hook.id}`,
            type: 'hook-item',
            position: { x: 0, y: 0 },
            data: { hook }
          })
        })
        break

      case 'skill':
        data.skills.forEach((skill) => {
          result.push({
            id: `skill-${skill.id}`,
            type: 'skill-item',
            position: { x: 0, y: 0 },
            data: { skill }
          })
        })
        break

      case 'mcp':
        data.mcps.forEach((mcp) => {
          result.push({
            id: `mcp-${mcp.id}`,
            type: 'mcp-item',
            position: { x: 0, y: 0 },
            data: { mcp }
          })
        })
        break

      case 'knowledge':
        data.knowledgeBases.forEach((knowledge) => {
          result.push({
            id: `knowledge-${knowledge.id}`,
            type: 'knowledge-item',
            position: { x: 0, y: 0 },
            data: { knowledge }
          })
        })
        break

      case 'sub-agent':
        data.subAgents.forEach((agent) => {
          result.push({
            id: `agent-${agent.id}`,
            type: 'agent-item',
            position: { x: 0, y: 0 },
            data: { agent }
          })
        })
        break
    }
  })

  // 直连节点（模型、提示词、高级配置、敏感词）放置在中心节点下方
  const directNodes: {
    id: string
    type: string
    data: Record<string, unknown>
  }[] = []

  // 对话生成模型节点始终显示
  directNodes.push({
    id: 'model-config',
    type: 'model',
    data: {
      role: 'LLM',
      modelConfig: data.modelConfig,
      provider: data.modelProvider,
      paramsOverride: data.agent?.modelParamsOverride || null,
      loadFailed: data.modelLoadFailed
    }
  })

  // 语音识别模型仅在Agent已绑定时显示
  if (data.agent.asrModelConfigId) {
    directNodes.push({
      id: 'asr-model-config',
      type: 'model',
      data: {
        role: 'ASR',
        modelConfig: data.asrModelConfig,
        provider: data.asrModelProvider,
        paramsOverride: null,
        loadFailed: data.asrModelLoadFailed
      }
    })
  }

  // 语音合成模型仅在Agent已绑定时显示
  if (data.agent.ttsModelConfigId) {
    directNodes.push({
      id: 'tts-model-config',
      type: 'model',
      data: {
        role: 'TTS',
        modelConfig: data.ttsModelConfig,
        provider: data.ttsModelProvider,
        paramsOverride: data.agent.ttsParamsOverride || null,
        loadFailed: data.ttsModelLoadFailed
      }
    })
  }

  // 提示词节点
  directNodes.push({
    id: 'prompt-config',
    type: 'prompt',
    data: {
      promptTemplate: data.promptTemplate,
      followTemplate: data.agent?.followTemplate || false,
      systemPrompt: data.agent?.systemPrompt || ''
    }
  })

  // 高级配置节点
  directNodes.push({
    id: 'advanced-config',
    type: 'advanced-config',
    data: {
      enablePlanning: data.agent?.enablePlanning || false,
      enableMemory: data.agent?.enableMemory || false,
      enableMemoryCompression: data.agent?.enableMemoryCompression || false,
      structuredOutputEnabled: data.agent?.structuredOutputEnabled || false,
      codeExecutionConfigId: data.agent?.codeExecutionConfigId || false,
      maxIterations: data.agent?.maxIterations || 10,
      maxSubtasks: data.agent?.maxSubtasks || 5
    }
  })

  // 敏感词节点（仅在启用时显示）
  if (data.agent?.sensitiveFilterEnabled && data.sensitiveConfig) {
    directNodes.push({
      id: `sensitive-${data.sensitiveConfig.id}`,
      type: 'sensitive-item',
      data: { sensitive: data.sensitiveConfig }
    })
  }

  result.push(...directNodes.map(node => ({
    ...node,
    position: { x: 0, y: 0 }
  })))

  return result
})

/**
 * 生成连线
 */
const rawEdges = computed<Edge[]>(() => {
  if (!data.agent) return []

  const result: Edge[] = []

  // 统一边样式
  const edgeStyle = { stroke: '#d9d9d9', strokeWidth: 1.5 }

  // 中心到分类的连线
  activeCategories.value.forEach((category) => {
    result.push({
      id: `e-center-${category}`,
      source: 'center',
      target: `category-${category}`,
      type: 'default',
      style: edgeStyle
    })

    // 分类到配置项的连线
    switch (category) {
      case 'tool':
        data.tools.forEach(tool => {
          result.push({
            id: `e-category-tool-${tool.id}`,
            source: `category-${category}`,
            target: `tool-${tool.id}`,
            type: 'default',
            style: edgeStyle
          })
        })
        break

      case 'workflow':
        data.workflows.forEach(workflow => {
          result.push({
            id: `e-category-workflow-${workflow.id}`,
            source: `category-${category}`,
            target: `workflow-${workflow.id}`,
            type: 'default',
            style: edgeStyle
          })
        })
        break

      case 'hook':
        data.hooks.forEach(hook => {
          result.push({
            id: `e-category-hook-${hook.id}`,
            source: `category-${category}`,
            target: `hook-${hook.id}`,
            type: 'default',
            style: edgeStyle
          })
        })
        break

      case 'skill':
        data.skills.forEach(skill => {
          result.push({
            id: `e-category-skill-${skill.id}`,
            source: `category-${category}`,
            target: `skill-${skill.id}`,
            type: 'default',
            style: edgeStyle
          })
        })
        break

      case 'mcp':
        data.mcps.forEach(mcp => {
          result.push({
            id: `e-category-mcp-${mcp.id}`,
            source: `category-${category}`,
            target: `mcp-${mcp.id}`,
            type: 'default',
            style: edgeStyle
          })
        })
        break

      case 'knowledge':
        data.knowledgeBases.forEach(kb => {
          result.push({
            id: `e-category-knowledge-${kb.id}`,
            source: `category-${category}`,
            target: `knowledge-${kb.id}`,
            type: 'default',
            style: edgeStyle
          })
        })
        break

      case 'sub-agent':
        data.subAgents.forEach(agent => {
          result.push({
            id: `e-category-agent-${agent.id}`,
            source: `category-${category}`,
            target: `agent-${agent.id}`,
            type: 'default',
            style: edgeStyle
          })
        })
        break
    }
  })

  // 中心到直连配置节点的连线
  result.push({
    id: 'e-center-model',
    source: 'center',
    target: 'model-config',
    type: 'default',
    style: edgeStyle
  })

  if (data.agent.asrModelConfigId) {
    result.push({
      id: 'e-center-asr-model',
      source: 'center',
      target: 'asr-model-config',
      type: 'default',
      style: edgeStyle
    })
  }

  if (data.agent.ttsModelConfigId) {
    result.push({
      id: 'e-center-tts-model',
      source: 'center',
      target: 'tts-model-config',
      type: 'default',
      style: edgeStyle
    })
  }

  result.push({
    id: 'e-center-prompt',
    source: 'center',
    target: 'prompt-config',
    type: 'default',
    style: edgeStyle
  })

  result.push({
    id: 'e-center-advanced',
    source: 'center',
    target: 'advanced-config',
    type: 'default',
    style: edgeStyle
  })

  // 敏感词节点连线（仅在启用时）
  if (data.agent?.sensitiveFilterEnabled && data.sensitiveConfig) {
    result.push({
      id: 'e-center-sensitive',
      source: 'center',
      target: `sensitive-${data.sensitiveConfig.id}`,
      type: 'default',
      style: edgeStyle
    })
  }

  return result
})

/**
 * 初始布局与整理操作共用同一组确定性坐标
 */
const nodes = computed<Node[]>(() => layoutArchitectureNodes(rawNodes.value, rawEdges.value))

/**
 * 根据径向坐标选择离目标最近的连接点
 */
const edges = computed<Edge[]>(() => {
  const nodeMap = new Map(nodes.value.map(node => [node.id, node]))
  return rawEdges.value.map((edge) => {
    const sourceNode = nodeMap.get(edge.source)
    const targetNode = nodeMap.get(edge.target)
    if (!sourceNode || !targetNode) return edge

    return {
      ...edge,
      ...getArchitectureEdgeHandles(sourceNode, targetNode)
    }
  })
})

/**
 * 恢复初始布局并适配全部节点
 */
async function organizeLayout() {
  const initialPositions = new Map(nodes.value.map(node => [node.id, node.position]))
  setNodes(currentNodes => currentNodes.map((node) => {
    const position = initialPositions.get(node.id)
    return position
      ? { ...node, position: { ...position } }
      : node
  }))
  await nextTick()
  await fitView({ padding: 0.08, duration: 500 })
}

/**
 * 加载数据
 */
async function loadData() {
  await loadArchitectureData(props.agentId)
  setTimeout(() => {
    fitView({ padding: 0.08, duration: 800 })
  }, 100)
}

/**
 * 监听agentId变化
 */
watch(() => props.agentId, () => {
  resetData()
  loadData()
})

/**
 * 初始化
 */
onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="agent-architecture-diagram">
    <Spin :spinning="loading" tip="正在加载架构数据..." style="background-color: transparent">
      <div class="diagram-container">
        <VueFlow
          :nodes="nodes"
          :edges="edges"
          :node-types="nodeTypes"
          :fit-view-on-init="false"
          :zoom-on-scroll="true"
          :pan-on-drag="true"
          :nodes-draggable="!layoutLocked"
          :nodes-connectable="false"
          :elements-selectable="true"
          :default-viewport="{ x: 0, y: 0, zoom: 0.8 }"
        >
          <Background pattern-color="#e8e8e8" :gap="24" variant="dots" />
          <MiniMap position="bottom-right" :pannable="true" :zoomable="true" />
          <Controls position="bottom-left">
            <template #control-fit-view>
              <ControlButton
                class="vue-flow__controls-fitview"
                title="整理布局"
                aria-label="整理布局"
                @click="organizeLayout"
              >
                <NodeIndexOutlined />
              </ControlButton>
            </template>
            <template #control-interactive>
              <ControlButton
                class="vue-flow__controls-interactive"
                :title="layoutLocked ? '解锁布局' : '锁定布局'"
                :aria-label="layoutLocked ? '解锁布局' : '锁定布局'"
                @click="layoutLocked = !layoutLocked"
              >
                <LockOutlined v-if="layoutLocked" />
                <UnlockOutlined v-else />
              </ControlButton>
            </template>
          </Controls>
        </VueFlow>
      </div>
    </Spin>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/agent/architecture-diagram.scss' as *;
</style>
