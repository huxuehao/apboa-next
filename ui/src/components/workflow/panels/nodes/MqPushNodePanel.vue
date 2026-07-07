<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { QuestionCircleOutlined } from '@ant-design/icons-vue'
import PanelSection from '../shared/PanelSection.vue'
import FormatterGuideModal from '../shared/FormatterGuideModal.vue'
import NodeNameInput from '../shared/NodeNameInput.vue'
import BlurInput from '../shared/BlurInput.vue'
import InputBindingSection from '../shared/InputBindingSection.vue'
import OutputDisplay from '../shared/OutputDisplay.vue'
import WorkflowResourceSelect from '@/components/workflow/fields/WorkflowResourceSelect.vue'
import ConfigCodeEditor from '@/components/editor/ConfigCodeEditor.vue'
import type { WorkflowFlowNode, WorkflowResourceMaps } from '@/types/workflow'

const panelRoot = ref<HTMLElement>()
const isEditorMaximized = ref(false)

function onEditorMaximizeChange(val: boolean) {
  isEditorMaximized.value = val
}

const props = defineProps<{
  node: WorkflowFlowNode
  nodes: WorkflowFlowNode[]
  resources: WorkflowResourceMaps
}>()
const emit = defineEmits<{ update: [node: WorkflowFlowNode] }>()

function updateNode(patch: Partial<WorkflowFlowNode['data']>) {
  emit('update', { ...props.node, data: { ...props.node.data, ...patch } })
}
function updateConfig(key: string, value: unknown) {
  updateNode({ config: { ...(props.node.data.config || {}), [key]: value } })
}

const formatterOptions = [
  { label: '纯文本替换', value: 'STRING' },
  { label: 'JSON 保类型', value: 'JACKSON' },
  { label: 'Velocity 模板', value: 'VELOCITY' },
]

// 消息模式：静态内容(message) vs 模板渲染(messageTemplate)
const messageMode = ref<'static' | 'template'>(
  props.node.data.config?.messageTemplate ? 'template' : 'static',
)

// 外部 config 变更时同步 radio 状态
watch(
  () => props.node.data.config?.messageTemplate,
  (val) => {
    messageMode.value = val ? 'template' : 'static'
  },
)

let switchingMode = false

function onMessageModeChange(val: 'static' | 'template') {
  if (switchingMode) return
  switchingMode = true
  const currentContent = String(
    props.node.data.config?.messageTemplate || props.node.data.config?.message || '',
  )
  if (val === 'static') {
    updateConfig('message', currentContent)
    updateConfig('messageTemplate', null)
  } else {
    updateConfig('messageTemplate', currentContent)
    updateConfig('message', null)
  }
  // 下一个 tick 重置标记，避免 watch 循环
  setTimeout(() => { switchingMode = false }, 0)
}

// 消息内容：根据模式读写对应字段
const messageContent = computed({
  get: () =>
    String(props.node.data.config?.messageTemplate || props.node.data.config?.message || ''),
  set: (val: string) => {
    if (messageMode.value === 'template') {
      updateConfig('messageTemplate', val)
    } else {
      updateConfig('message', val)
    }
  },
})

const messagePlaceholder = computed(() =>
  messageMode.value === 'template'
    ? '使用 ${输入绑定名} 引用输入绑定，如 {\"id\":\"${input.id}\"}'
    : '直接输入消息内容，如 {\"key\":\"value\"}',
)
</script>

<template>
  <div ref="panelRoot" class="mq-push-panel" :class="{ 'editor-maximized': isEditorMaximized }">
  <AForm layout="vertical">
    <PanelSection title="节点名称">
      <NodeNameInput
        :model-value="node.data.label"
        @update:model-value="(v: any) => updateNode({ label: v })"
      />
    </PanelSection>

    <InputBindingSection
      :model-value="node.data.inputConfigs"
      :nodes="nodes"
      :current-node-id="node.id"
      @update:model-value="(v: any) => updateNode({ inputConfigs: v })"
    />

    <PanelSection title="节点配置">
      <AFormItem required>
        <template #label>
          MQ 实例
          <ATooltip title="选择已配置并启用的消息中间件实例，支持 Kafka、RabbitMQ、RocketMQ 三种类型">
            <QuestionCircleOutlined class="help-icon" />
          </ATooltip>
        </template>
        <WorkflowResourceSelect
          :model-value="String(node.data.config?.mqId || '')"
          resource-type="mq"
          :resources="resources"
          @update:model-value="(v: any) => updateConfig('mqId', v)"
        />
      </AFormItem>

      <div class="topic-field">
        <AFormItem required>
          <template #label>
            Topic / Queue
            <ATooltip title="Kafka / RocketMQ 为 Topic 名称，RabbitMQ 为 Queue 名称。支持 ${输入绑定名} 模板语法">
              <QuestionCircleOutlined class="help-icon" />
            </ATooltip>
          </template>
          <BlurInput
            :model-value="String(node.data.config?.topicOrQueue || '')"
            placeholder="Kafka/RocketMQ 为 topic，RabbitMQ 为 queue"
            @update:model-value="(v: any) => updateConfig('topicOrQueue', v)"
          />
        </AFormItem>
        <span class="field-help">支持 ${输入绑定名} 引用输入绑定，渲染格式由下方「模板格式」控制。</span>
      </div>

      <div class="key-field">
        <AFormItem>
          <template #label>
            消息 Key
            <ATooltip title="Kafka 的分区键、RabbitMQ 的 Routing Key、RocketMQ 的 Tag。可选，支持 ${输入绑定名} 模板语法">
              <QuestionCircleOutlined class="help-icon" />
            </ATooltip>
          </template>
          <BlurInput
            :model-value="String(node.data.config?.key || '')"
            placeholder="Kafka 分区键、RabbitMQ routing key、RocketMQ tag"
            @update:model-value="(v: any) => updateConfig('key', v)"
          />
        </AFormItem>
        <span class="field-help">可选。支持 ${输入绑定名} 引用输入绑定，渲染格式由下方「模板格式」控制。</span>
      </div>

      <div class="message-field">
        <div class="message-header">
          <span class="form-label required-field">消息内容</span>
          <div class="formatter-selector">
            <FormatterGuideModal />
            <ASelect
              :value="node.data.config?.templateType || 'STRING'"
              :options="formatterOptions"
              size="small"
              style="width: 130px"
              @update:value="(v: any) => updateConfig('templateType', v)"
            />
          </div>
        </div>

        <ARadioGroup v-model:value="messageMode" @update:value="onMessageModeChange" class="message-mode-toggle">
          <ARadio value="static">静态内容</ARadio>
          <ARadio value="template">模板渲染</ARadio>
        </ARadioGroup>

        <ConfigCodeEditor
          v-model="messageContent"
          language="txt"
          :placeholder="messagePlaceholder"
          :maximize-target="panelRoot"
          @maximize-change="onEditorMaximizeChange"
        />

        <span class="field-help">
          选择「模板渲染」后，内容经所选模板格式渲染再发送；Topic/Queue 和 Key 同样使用该格式渲染。
        </span>
      </div>
    </PanelSection>

    <PanelSection title="输出说明">
      <OutputDisplay :outputs="node.data.outputConfigs || []" />
    </PanelSection>
  </AForm>
  </div>
</template>

<style scoped lang="scss">
.mq-push-panel {
  position: relative;

  &.editor-maximized {
    height: 100%;
    overflow: hidden;
  }

  :deep(.ant-form-item-label) {
    label {
      display: inline-flex;
      align-items: center;
      gap: 4px;
    }
  }
}

.help-icon {
  color: rgba(0, 0, 0, 0.25);
  font-size: 13px;
  cursor: help;

  &:hover {
    color: rgba(0, 0, 0, 0.45);
  }
}

.topic-field,
.key-field {
  margin-bottom: 24px;

  :deep(.ant-form-item) {
    margin-bottom: 0;
  }
}

.field-help {
  display: block;
  margin-top: 4px;
  color: #8c8c8c;
  font-size: 12px;
  line-height: 1.6;

  code {
    padding: 1px 4px;
    background: #f5f5f5;
    border-radius: 3px;
    font-size: 11px;
    font-family: 'JetBrains Mono', 'Consolas', monospace;
    color: #d4380d;
  }
}

.message-field {
  margin-bottom: 24px;
}

.message-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.form-label {
  font-size: 14px;
  color: rgba(0, 0, 0, 0.88);
  line-height: 1.5;
}

.formatter-selector {
  display: flex;
  align-items: center;
  gap: 6px;
}

.message-mode-toggle {
  margin-bottom: 8px;

  :deep(.ant-radio-wrapper) {
    font-size: 13px;
  }
}
</style>
