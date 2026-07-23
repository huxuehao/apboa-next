/**
 * 智能体表单组件
 *
 * @author huxuehao
 */
<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { message } from 'ant-design-vue'
import { RobotOutlined } from '@ant-design/icons-vue'
import AgentFormBasic from './AgentFormBasic.vue'
import AgentFormModel from './AgentFormModel.vue'
import AgentFormTools from './AgentFormTools.vue'
import AgentFormKnowledge from './AgentFormKnowledge.vue'
import AgentFormAdvanced from './AgentFormAdvanced.vue'
import type { AgentDefinitionVO, CommonQuestion } from '@/types'
import { ToolChoiceStrategy } from '@/types'
import * as agentApi from '@/api/agent'
import { useAccountStore } from '@/stores'

const { currentTenant } = useAccountStore()

/**
 * Props定义
 */
const props = defineProps<{
  visible: boolean
  data?: AgentDefinitionVO
  tags: string[]
}>()

/**
 * Emits定义
 */
const emit = defineEmits<{
  'update:visible': [value: boolean]
  success: []
}>()

const currentStep = ref(0)
const loading = ref(false)

const basicFormRef = ref()
const modelFormRef = ref()
const toolsFormRef = ref()
const knowledgeFormRef = ref()
const advancedFormRef = ref()

/**
 * 表单数据
 */
const formData = ref({
  basic: {
    name: '',
    agentCode: '',
    description: '',
    tag: '',
    avatar: null as string | null,
    commonQuestions: [] as CommonQuestion[],
    commonQuestionsPinned: true
  },
  model: {
    modelConfigId: '',
    asrModelConfigId: null as string | null,
    ttsModelConfigId: null as string | null,
    modelParamsOverride: null as Record<string, unknown> | null,
    ttsParamsOverride: null as Record<string, unknown> | null,
    systemPromptTemplateId: '',
    followTemplate: true,
    systemPrompt: ''
  },
  tools: {
    hook: [] as string[],
    toolChoiceStrategy: ToolChoiceStrategy.AUTO,
    tool: [] as string[],
    specificToolName: '',
    workflow: [] as string[],
    skill: [] as string[],
    sensitiveWordConfigId: '',
    sensitiveFilterEnabled: false
  },
  knowledge: {
    knowledgeBase: [] as string[],
    mcp: [] as string[],
    mcpBindings: [] as AgentDefinitionVO['mcpBindings'],
    subAgent: [] as string[]
  },
  advanced: {
    enablePlanning: false,
    maxIterations: 50,
    maxSubtasks: 10,
    requirePlanConfirmation: false,
    enableMemory: true,
    showToolProcess: false,
    enableMemoryCompression: false,
    memoryCompressionConfig: null as Record<string, unknown> | null,
    structuredOutputEnabled: false,
    structuredOutputReminder: 'TOOL_CHOICE' as "TOOL_CHOICE" | "PROMPT",
    structuredOutputSchema: '',
    studioConfigId: null as string | null,
    codeExecutionConfigId: null as string | null,
    longTermMemoryConfigId: null as string | null
  }
})

// ========== 头像（独立接口，随保存串行提交） ==========
// 注意：声明在所有可能调用 loadAvatar 的 watch 之前，避免 TDZ ReferenceError

/** 头像基线：与当前值一致时保存不发头像请求 */
const avatarBaseline = ref<string | null>(null)

/**
 * 编辑模式：异步加载头像回填表单与基线
 */
function loadAvatar(id: string) {
  avatarBaseline.value = null
  agentApi.getAvatar(id).then(res => {
    const v = res.data?.data || null
    formData.value.basic.avatar = v
    avatarBaseline.value = v
  }).catch(() => {})
}

/**
 * 头像相对基线有变更时提交（失败不影响主流程，单独提示）
 */
async function syncAvatarIfChanged(id: string) {
  const current = formData.value.basic.avatar ?? null
  if (current === avatarBaseline.value) return
  try {
    await agentApi.updateAvatar(id, current)
    avatarBaseline.value = current
  } catch {
    message.warning('头像保存失败，可稍后在配置页重新上传')
  }
}

/**
 * 步骤配置
 */
const steps = [
  { title: '基本信息', description: '配置智能体的基本信息' },
  { title: '模型与提示词', description: '选择模型和提示词模板' },
  { title: '工具与能力', description: '配置钩子、工具、技能和敏感词' },
  { title: '知识库与MCP', description: '配置知识库、MCP服务器和子智能体' },
  { title: '高级设置', description: '配置计划、记忆、执行环境和Studio等' }
]

/**
 * 是否为编辑模式
 */
const isEdit = computed(() => !!props.data?.id)

/**
 * 当前步骤引用
 */
const currentFormRef = computed(() => {
  switch (currentStep.value) {
    case 0:
      return basicFormRef.value
    case 1:
      return modelFormRef.value
    case 2:
      return toolsFormRef.value
    case 3:
      return knowledgeFormRef.value
    case 4:
      return advancedFormRef.value
    default:
      return null
  }
})

/**
 * 初始化表单数据
 */
watch(
  () => props.visible,
  (newVal) => {
    if (newVal) {
      if (props.data) {
        formData.value.basic = {
          name: props.data.name,
          agentCode: props.data.agentCode,
          description: props.data.description,
          tag: props.data.tag || '',
          avatar: null,
          commonQuestions: props.data.commonQuestions || [],
          commonQuestionsPinned: props.data.commonQuestionsPinned !== false
        }
        loadAvatar(String(props.data.id))
        formData.value.model = {
          modelConfigId: props.data.modelConfigId,
          asrModelConfigId: props.data.asrModelConfigId || null,
          ttsModelConfigId: props.data.ttsModelConfigId || null,
          modelParamsOverride: props.data.modelParamsOverride || null,
          ttsParamsOverride: props.data.ttsParamsOverride || null,
          systemPromptTemplateId: props.data.systemPromptTemplateId,
          followTemplate: props.data.followTemplate,
          systemPrompt: props.data.systemPrompt
        }
        formData.value.tools = {
          hook: props.data.hook || [],
          toolChoiceStrategy: props.data.toolChoiceStrategy,
          tool: props.data.tool || [],
          specificToolName: props.data.specificToolName || '',
          workflow: props.data.workflow || [],
          skill: props.data.skill || [],
          sensitiveWordConfigId: props.data.sensitiveWordConfigId || '',
          sensitiveFilterEnabled: props.data.sensitiveFilterEnabled
        }
        formData.value.knowledge = {
          knowledgeBase: props.data.knowledgeBase || [],
          mcp: props.data.mcp || [],
          mcpBindings: props.data.mcpBindings || [],
          subAgent: props.data.subAgent || []
        }
        formData.value.advanced = {
          enablePlanning: props.data.enablePlanning,
          maxIterations: props.data.maxIterations || 50,
          maxSubtasks: props.data.maxSubtasks || 10,
          requirePlanConfirmation: props.data.requirePlanConfirmation,
          enableMemory: props.data.enableMemory,
          showToolProcess: props.data.showToolProcess,
          enableMemoryCompression: props.data.enableMemoryCompression,
          memoryCompressionConfig: props.data.memoryCompressionConfig || null,
          structuredOutputEnabled: props.data.structuredOutputEnabled,
          structuredOutputReminder: props.data.structuredOutputReminder || 'TOOL_CHOICE',
          structuredOutputSchema: props.data.structuredOutputSchema ? JSON.stringify(props.data.structuredOutputSchema, null, 2) : '',
          studioConfigId: props.data.studioConfigId || null,
          codeExecutionConfigId: props.data.codeExecutionConfigId || null,
          longTermMemoryConfigId: props.data.longTermMemoryConfigId || null
        }
      } else {
        resetForm()
      }
    }
  }
)

/**
 * 重置表单
 */
function resetForm() {
  currentStep.value = 0
  avatarBaseline.value = null
  formData.value = {
    basic: {
      name: '',
      agentCode: '',
      description: '',
      tag: '',
      avatar: null as string | null,
      commonQuestions: [] as CommonQuestion[],
      commonQuestionsPinned: true
    },
    model: {
      modelConfigId: '',
      asrModelConfigId: null,
      ttsModelConfigId: null,
      modelParamsOverride: null,
      ttsParamsOverride: null,
      systemPromptTemplateId: '',
      followTemplate: true,
      systemPrompt: ''
    },
    tools: {
      hook: [],
      toolChoiceStrategy: ToolChoiceStrategy.AUTO,
      tool: [],
      specificToolName: '',
      workflow: [],
      skill: [],
      sensitiveWordConfigId: '',
      sensitiveFilterEnabled: false
    },
    knowledge: {
      knowledgeBase: [],
      mcp: [],
      mcpBindings: [],
      subAgent: []
    },
    advanced: {
      enablePlanning: false,
      maxIterations: 50,
      maxSubtasks: 10,
      requirePlanConfirmation: false,
      enableMemory: true,
      showToolProcess: false,
      enableMemoryCompression: false,
      memoryCompressionConfig: null,
      structuredOutputEnabled: false,
      structuredOutputReminder: 'TOOL_CHOICE',
      structuredOutputSchema: '',
      studioConfigId: null,
      codeExecutionConfigId: null,
      longTermMemoryConfigId: null
    }
  }
}

/**
 * 上一步
 */
function handlePrevious() {
  if (currentStep.value > 0) {
    currentStep.value--
  }
}

/**
 * 下一步
 */
async function handleNext() {
  const valid = await currentFormRef.value?.validate()
  if (valid && currentStep.value < steps.length - 1) {
    currentStep.value++
  }
}

/**
 * 改变步数
 */
async function handleStepChange(step: number) {
  const valid = await currentFormRef.value?.validate()
  if (valid) {
    currentStep.value = step
  }
}



/**
 * 提交表单
 */
async function handleSubmit() {
  try {
    const valid = await currentFormRef.value?.validate()
    if (!valid) return

    loading.value = true

    const vo: Partial<AgentDefinitionVO> = {
      name: formData.value.basic.name,
      agentType: 'CUSTOM',
      agentCode: formData.value.basic.agentCode,
      description: formData.value.basic.description,
      tag: formData.value.basic.tag || '',
      // 始终传数组（清空时传 []），null 会被 updateById 的非空策略跳过导致清不掉旧配置
      commonQuestions: formData.value.basic.commonQuestions || [],
      commonQuestionsPinned: formData.value.basic.commonQuestionsPinned !== false,
      modelConfigId: formData.value.model.modelConfigId,
      asrModelConfigId: formData.value.model.asrModelConfigId || null,
      ttsModelConfigId: formData.value.model.ttsModelConfigId || null,
      modelParamsOverride: formData.value.model.modelParamsOverride,
      ttsParamsOverride: formData.value.model.ttsParamsOverride,
      systemPromptTemplateId: formData.value.model.systemPromptTemplateId,
      followTemplate: formData.value.model.followTemplate,
      systemPrompt: formData.value.model.systemPrompt,
      toolChoiceStrategy: formData.value.tools.toolChoiceStrategy,
      tool: formData.value.tools.tool,
      specificToolName: formData.value.tools.specificToolName,
      workflow: formData.value.tools.workflow,
      skill: formData.value.tools.skill,
      sensitiveWordConfigId: formData.value.tools.sensitiveWordConfigId,
      sensitiveFilterEnabled: formData.value.tools.sensitiveFilterEnabled,
      knowledgeBase: formData.value.knowledge.knowledgeBase,
      mcp: formData.value.knowledge.mcp,
      mcpBindings: formData.value.knowledge.mcpBindings,
      subAgent: formData.value.knowledge.subAgent,
      hook: formData.value.tools.hook,
      enablePlanning: formData.value.advanced.enablePlanning,
      maxIterations: formData.value.advanced.maxIterations,
      maxSubtasks: formData.value.advanced.maxSubtasks,
      requirePlanConfirmation: formData.value.advanced.requirePlanConfirmation && formData.value.advanced.enableMemory,
      showToolProcess: formData.value.advanced.showToolProcess,
      enableMemory: formData.value.advanced.enableMemory,
      enableMemoryCompression: formData.value.advanced.enableMemoryCompression,
      memoryCompressionConfig: formData.value.advanced.memoryCompressionConfig,
      structuredOutputEnabled: formData.value.advanced.structuredOutputEnabled,
      structuredOutputReminder: formData.value.advanced.structuredOutputReminder,
      structuredOutputSchema: formData.value.advanced.structuredOutputEnabled && formData.value.advanced.structuredOutputSchema
        ? JSON.parse(formData.value.advanced.structuredOutputSchema)
        : null,
      studioConfigId: formData.value.advanced.studioConfigId,
      codeExecutionConfigId: formData.value.advanced.codeExecutionConfigId,
      longTermMemoryConfigId: formData.value.advanced.longTermMemoryConfigId,
      version: '1.0.0'
    }

    if (isEdit.value && props.data) {
      vo.id = props.data.id
      vo.enabled = props.data.enabled
      await agentApi.update(vo as AgentDefinitionVO)
      await syncAvatarIfChanged(String(props.data.id))
      message.success('更新成功')
    } else {
      const res = await agentApi.save(vo as AgentDefinitionVO)
      // 新建后串行保存头像：save 返回回填了 id 的 VO
      const newId = res.data?.data?.id
      if (newId) await syncAvatarIfChanged(String(newId))
      message.success('创建成功')
    }

    emit('success')
    handleCancel()
  } catch (error) {
    console.error('提交失败:', error)
  } finally {
    loading.value = false
  }
}

/**
 * 取消
 */
function handleCancel() {
  emit('update:visible', false)
  resetForm()
}
</script>

<template>
  <ApboaModal
    :open="visible"
    :title-icon="RobotOutlined"
    :title="isEdit ? '编辑智能体' : '新增智能体'"
    :footer="null"
    defaultWidth="100%"
    destroyOnClose
    @cancel="handleCancel"
  >
    <ASteps
      :current="currentStep"
      :items="steps"
      @change="handleStepChange"
      class="mb-lg"/>

    <div class="form-content" v-if="visible">
      <AgentFormBasic
        v-if="currentStep === 0"
        ref="basicFormRef"
        v-model="formData.basic"
        :tags="tags"
        :tenant-code="currentTenant?.tenantCode"
      />

      <AgentFormModel
        v-if="currentStep === 1"
        ref="modelFormRef"
        v-model="formData.model"
      />

      <AgentFormTools
        v-if="currentStep === 2"
        ref="toolsFormRef"
        v-model="formData.tools"
      />

      <AgentFormKnowledge
        v-if="currentStep === 3"
        ref="knowledgeFormRef"
        v-model="formData.knowledge"
        :current-agent-id="data?.id"
      />

      <AgentFormAdvanced
        v-if="currentStep === 4"
        ref="advancedFormRef"
        style="width: calc(100% - 10px);"
        v-model="formData.advanced"
      />
    </div>

    <div class="form-actions flex justify-between mt-lg">
      <ASpace>
        <AButton :disabled="currentStep <= 0" @click="handlePrevious">
          上一步
        </AButton>
        <AButton :disabled="currentStep >= steps.length - 1" type="primary" @click="handleNext">
          下一步
        </AButton>
      </ASpace>

      <ASpace>
        <AButton @click="handleCancel">取消</AButton>
        <AButton :disabled="!isEdit && currentStep < steps.length - 1" type="primary" :loading="loading" @click="handleSubmit">
          {{ isEdit ? '更新' : '创建' }}
        </AButton>
      </ASpace>
    </div>
  </ApboaModal>
</template>

<style scoped lang="scss">
.form-content {
  height: calc(100vh - 260px);
  overflow-y: auto;
  padding: var(--spacing-md) 0;
}

.form-actions {
  padding-top: var(--spacing-md);
}
</style>
