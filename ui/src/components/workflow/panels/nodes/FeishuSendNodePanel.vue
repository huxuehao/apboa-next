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

const messageMode = ref<'static' | 'template'>(
  props.node.data.config?.contentTemplate ? 'template' : 'static',
)

watch(
  () => props.node.data.config?.contentTemplate,
  (val) => { messageMode.value = val ? 'template' : 'static' },
)

let switchingMode = false

function onMessageModeChange(val: 'static' | 'template') {
  if (switchingMode) return
  switchingMode = true
  const currentContent = String(
    props.node.data.config?.contentTemplate || props.node.data.config?.content || '',
  )
  if (val === 'static') {
    updateConfig('content', currentContent)
    updateConfig('contentTemplate', null)
  } else {
    updateConfig('contentTemplate', currentContent)
    updateConfig('content', null)
  }
  setTimeout(() => { switchingMode = false }, 0)
}

const messageContent = computed({
  get: () =>
    String(props.node.data.config?.contentTemplate || props.node.data.config?.content || ''),
  set: (val: string) => {
    if (messageMode.value === 'template') {
      updateConfig('contentTemplate', val)
    } else {
      updateConfig('content', val)
    }
  },
})
</script>

<template>
  <div ref="panelRoot" class="channel-panel" :class="{ 'editor-maximized': isEditorMaximized }">
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
            飞书渠道
            <ATooltip title="选择已配置并启用的飞书机器人渠道">
              <QuestionCircleOutlined class="help-icon" />
            </ATooltip>
          </template>
          <WorkflowResourceSelect
            :model-value="String(node.data.config?.channelId || '')"
            resource-type="channel"
            channel-type="FEISHU"
            :resources="resources"
            @update:model-value="(v: any) => updateConfig('channelId', v)"
          />
        </AFormItem>

        <AFormItem>
          <template #label>
            消息标题
            <ATooltip title="消息卡片标题。支持 ${输入绑定名} 模板语法">
              <QuestionCircleOutlined class="help-icon" />
            </ATooltip>
          </template>
          <BlurInput
            :model-value="String(node.data.config?.subject || '')"
            placeholder="消息标题"
            @update:model-value="(v: any) => updateConfig('subject', v)"
          />
        </AFormItem>

        <div class="message-field">
          <div class="message-header">
            <span class="form-label required-field">消息内容</span>
            <div class="formatter-selector">
              <FormatterGuideModal />
              <ASelect
                :value="node.data.config?.formatterType || 'STRING'"
                :options="formatterOptions"
                size="small"
                style="width: 130px"
                @update:value="(v: any) => updateConfig('formatterType', v)"
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
            :placeholder="messageMode === 'template' ? '使用 ${输入绑定名} 引用输入绑定' : '支持 Markdown 格式'"
            :maximize-target="panelRoot"
            @maximize-change="onEditorMaximizeChange"
          />

          <span class="field-help">选择「模板渲染」后，内容经所选模板格式渲染再发送；标题同样使用该格式渲染。</span>

          <div class="switch-row">
            <span class="switch-label">
              同步执行
              <ATooltip title="开启后同步等待发送结果；关闭后异步发送，直接返回固定字符串">
                <QuestionCircleOutlined class="help-icon" />
              </ATooltip>
            </span>
            <ASwitch
              :checked="Boolean(node.data.config?.syncExecute ?? true)"
              @update:checked="(v: any) => updateConfig('syncExecute', v)"
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
.channel-panel {
  position: relative;
  &.editor-maximized { height: 100%; overflow: hidden; }
  :deep(.ant-form-item-label) label { display: inline-flex; align-items: center; gap: 4px; }
}
.help-icon { color: rgba(0, 0, 0, 0.25); font-size: 13px; cursor: help; &:hover { color: rgba(0, 0, 0, 0.45); } }
.message-field { margin-bottom: 24px; }
.message-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.form-label { font-size: 14px; color: rgba(0, 0, 0, 0.88); line-height: 1.5; }
.formatter-selector { display: flex; align-items: center; gap: 6px; }
.message-mode-toggle { margin-bottom: 8px; :deep(.ant-radio-wrapper) { font-size: 13px; } }
.field-help { display: block; margin-top: 4px; color: #8c8c8c; font-size: 12px; line-height: 1.6; }

.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 32px;
  margin-bottom: 12px;
}

.switch-label {
  flex-shrink: 0;
  font-size: 14px;
  color: rgba(0, 0, 0, 0.88);
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
</style>
