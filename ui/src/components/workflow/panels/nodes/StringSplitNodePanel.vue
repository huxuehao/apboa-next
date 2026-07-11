<script setup lang="ts">
import { computed } from 'vue'
import { QuestionCircleOutlined } from '@ant-design/icons-vue'
import PanelSection from '../shared/PanelSection.vue'
import NodeNameInput from '../shared/NodeNameInput.vue'
import BlurInput from '../shared/BlurInput.vue'
import InputBindingSection from '../shared/InputBindingSection.vue'
import OutputDisplay from '../shared/OutputDisplay.vue'
import WorkflowArrayEditors from '@/components/workflow/fields/WorkflowArrayEditors.vue'
import type { WorkflowFlowEdge, WorkflowFlowNode, WorkflowResourceMaps } from '@/types/workflow'

const props = defineProps<{
  node: WorkflowFlowNode
  nodes: WorkflowFlowNode[]
  edges: WorkflowFlowEdge[]
  resources: WorkflowResourceMaps
}>()
const emit = defineEmits<{ update: [node: WorkflowFlowNode] }>()

function updateNode(patch: Partial<WorkflowFlowNode['data']>) {
  emit('update', { ...props.node, data: { ...props.node.data, ...patch } })
}
function updateConfig(key: string, value: unknown) {
  updateNode({ config: { ...(props.node.data.config || {}), [key]: value } })
}

const mode = computed(() => props.node.data.config?.mode as string)
const isKeyValue = computed(() => mode.value === 'KEY_VALUE')
const isMultiDelimiter = computed(() => mode.value === 'MULTIPLE_DELIMITERS')
const showDelimiter = computed(
  () => mode.value === 'SIMPLE' || mode.value === 'REGEX' || mode.value === 'FIXED_LENGTH',
)
const delimiterLabel = computed(() => {
  switch (mode.value) {
    case 'SIMPLE':
      return '分隔符'
    case 'REGEX':
      return '正则表达式'
    case 'FIXED_LENGTH':
      return '分割长度'
    default:
      return '分隔符'
  }
})
const processingResult = computed(
  () => Boolean(props.node.data.config?.processingResult ?? true),
)
</script>

<template>
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
      :edges="edges"
      :current-node-id="node.id"
      :max-bindings="1"
      :readonly-name="true"
      @update:model-value="(v: any) => updateNode({ inputConfigs: v })"
    />
    <PanelSection title="节点配置">
      <div class="config-row">
        <span class="config-row-label">
          分割模式
          <ATooltip title="选择字符串的拆分策略：简单分隔符按字符分割、正则按表达式匹配、固定长度按字符数截断、换行按换行符分割、键值对拆分为 key-value、多个分隔符同时匹配多种符号">
            <QuestionCircleOutlined class="help-icon" />
          </ATooltip>
        </span>
        <ASelect
          :value="node.data.config?.mode"
          :options="[
            { label: '简单分隔符', value: 'SIMPLE' },
            { label: '正则', value: 'REGEX' },
            { label: '固定长度', value: 'FIXED_LENGTH' },
            { label: '换行', value: 'LINE_BREAK' },
            { label: '键值对', value: 'KEY_VALUE' },
            { label: '多个分隔符', value: 'MULTIPLE_DELIMITERS' },
          ]"
          style="width: 160px"
          @update:value="(v: any) => updateConfig('mode', v)"
        />
      </div>
      <div v-if="showDelimiter" class="config-row">
        <span class="config-row-label">
          {{ delimiterLabel }}
          <ATooltip v-if="mode === 'SIMPLE'" title="按指定字符作为分隔符拆分字符串">
            <QuestionCircleOutlined class="help-icon" />
          </ATooltip>
          <ATooltip v-else-if="mode === 'REGEX'" title="按正则表达式匹配拆分字符串">
            <QuestionCircleOutlined class="help-icon" />
          </ATooltip>
          <ATooltip v-else-if="mode === 'FIXED_LENGTH'" title="按指定字符数截断字符串，每段固定长度">
            <QuestionCircleOutlined class="help-icon" />
          </ATooltip>
        </span>
        <BlurInput
          :model-value="String(node.data.config?.delimiter || '')"
          style="width: 160px"
          @update:model-value="(v: any) => updateConfig('delimiter', v)"
        />
      </div>
      <AFormItem v-if="isMultiDelimiter">
        <template #label>
          多个分隔符
          <ATooltip title="使用多个符号同时作为分隔符，匹配任意一个即分割">
            <QuestionCircleOutlined class="help-icon" />
          </ATooltip>
        </template>
        <WorkflowArrayEditors
          :model-value="node.data.config?.delimiters"
          type="stringList"
          @update:model-value="(v: any) => updateConfig('delimiters', v)"
        />
      </AFormItem>
      <div class="config-row">
        <span class="config-row-label">
          去除首尾空白
          <ATooltip title="是否对分割后的每个结果去除两端的空白字符">
            <QuestionCircleOutlined class="help-icon" />
          </ATooltip>
        </span>
        <ASwitch
          :checked="Boolean(node.data.config?.trimParts ?? true)"
          @update:checked="(v: any) => updateConfig('trimParts', v)"
        />
      </div>
      <div class="config-row">
        <span class="config-row-label">
          移除空字符串
          <ATooltip title="是否移除分割结果中的空字符串">
            <QuestionCircleOutlined class="help-icon" />
          </ATooltip>
        </span>
        <ASwitch
          :checked="Boolean(node.data.config?.removeEmpty ?? true)"
          @update:checked="(v: any) => updateConfig('removeEmpty', v)"
        />
      </div>
      <div class="config-row">
        <span class="config-row-label">
          Split limit
          <ATooltip title="控制底层 String.split 的 limit 参数：>0 最多拆分 limit-1 次，=0 移除末尾空串，<0 不作限制且保留所有空串">
            <QuestionCircleOutlined class="help-icon" />
          </ATooltip>
        </span>
        <AInputNumber
          :value="Number(node.data.config?.limit ?? -1)"
          style="width: 160px"
          @update:value="(v: any) => updateConfig('limit', v)"
        />
      </div>
      <div class="config-row">
        <span class="config-row-label">
          最大结果数
          <ATooltip title="限制最终输出的结果数量上限，超出部分将被丢弃，设为 -1 表示不限制">
            <QuestionCircleOutlined class="help-icon" />
          </ATooltip>
        </span>
        <AInputNumber
          :value="Number(node.data.config?.maxResults ?? -1)"
          style="width: 160px"
          @update:value="(v: any) => updateConfig('maxResults', v)"
        />
      </div>
      <div class="config-row">
        <span class="config-row-label">
          处理分割结果
          <ATooltip title="是否对分割后的结果进行额外处理，开启后可添加前后缀">
            <QuestionCircleOutlined class="help-icon" />
          </ATooltip>
        </span>
        <ASwitch
          :checked="processingResult"
          @update:checked="(v: any) => updateConfig('processingResult', v)"
        />
      </div>
      <div v-if="processingResult" class="config-row">
        <span class="config-row-label">
          结果前缀
          <ATooltip title="在每个分割结果前面添加的字符串">
            <QuestionCircleOutlined class="help-icon" />
          </ATooltip>
        </span>
        <BlurInput
          :model-value="String(node.data.config?.prefix || '')"
          style="width: 160px"
          @update:model-value="(v: any) => updateConfig('prefix', v)"
        />
      </div>
      <div v-if="processingResult" class="config-row">
        <span class="config-row-label">
          结果后缀
          <ATooltip title="在每个分割结果后面添加的字符串">
            <QuestionCircleOutlined class="help-icon" />
          </ATooltip>
        </span>
        <BlurInput
          :model-value="String(node.data.config?.suffix || '')"
          style="width: 160px"
          @update:model-value="(v: any) => updateConfig('suffix', v)"
        />
      </div>
      <template v-if="isKeyValue">
        <div class="config-row">
          <span class="config-row-label">
            键值分隔符
            <ATooltip title="将键值对拆分为 key 和 value 的分隔符号，默认为 =">
              <QuestionCircleOutlined class="help-icon" />
            </ATooltip>
          </span>
          <BlurInput
            :model-value="String(node.data.config?.keyValueDelimiter ?? '=')"
            style="width: 160px"
            @update:model-value="(v: any) => updateConfig('keyValueDelimiter', v)"
          />
        </div>
        <div class="config-row">
          <span class="config-row-label">
            键值输出格式
            <ATooltip title="键值对输出的格式化方式：冒号分隔、等号分隔、JSON 对象、箭头映射或自定义格式">
              <QuestionCircleOutlined class="help-icon" />
            </ATooltip>
          </span>
          <ASelect
            :value="node.data.config?.keyValueOutputFormat"
            :options="[
              { label: 'key: value', value: 'COLON_SEPARATED' },
              { label: 'key=value', value: 'EQUALS_SEPARATED' },
              { label: 'JSON 对象', value: 'JSON_OBJECT' },
              { label: 'key -> value', value: 'MAP_ENTRY' },
              { label: '自定义', value: 'CUSTOM' },
            ]"
            style="width: 140px"
            @update:value="(v: any) => updateConfig('keyValueOutputFormat', v)"
          />
        </div>
        <div
          v-if="(node.data.config?.keyValueOutputFormat as string) === 'CUSTOM'"
          class="config-row"
        >
          <span class="config-row-label">
            自定义格式
            <ATooltip title="使用 %s 作为占位符，第一个 %s 代表 key，第二个代表 value，如 %s===>%s">
              <QuestionCircleOutlined class="help-icon" />
            </ATooltip>
          </span>
          <BlurInput
            :model-value="String(node.data.config?.keyValueCustomFormat || '')"
            placeholder="%s===>%s"
            style="width: 160px"
            @update:model-value="(v: any) => updateConfig('keyValueCustomFormat', v)"
          />
        </div>
      </template>
    </PanelSection>
    <PanelSection title="输出说明">
      <OutputDisplay :outputs="node.data.outputConfigs || []" />
    </PanelSection>
  </AForm>
</template>

<style scoped lang="scss">
.config-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 32px;
  margin-bottom: 16px;

  &:last-child {
    margin-bottom: 0;
  }
}

.config-row-label {
  flex-shrink: 0;
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
</style>
