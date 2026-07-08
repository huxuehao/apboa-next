<script setup lang="ts">
import { computed, inject, ref, watch } from 'vue'
import { QuestionCircleOutlined, EditOutlined, CaretRightOutlined } from '@ant-design/icons-vue'
import PanelSection from '../shared/PanelSection.vue'
import NodeNameInput from '../shared/NodeNameInput.vue'
import BlurInput from '../shared/BlurInput.vue'
import InputBindingSection from '../shared/InputBindingSection.vue'
import OutputDisplay from '../shared/OutputDisplay.vue'
import ConfigCodeEditor from '@/components/editor/ConfigCodeEditor.vue'
import EntryNodeSelect from '@/components/workflow/bindings/EntryNodeSelect.vue'
import type { WorkflowFlowEdge, WorkflowFlowNode, WorkflowResourceMaps } from '@/types/workflow'

const props = defineProps<{
  node: WorkflowFlowNode
  nodes: WorkflowFlowNode[]
  edges: WorkflowFlowEdge[]
  resources: WorkflowResourceMaps
}>()
const emit = defineEmits<{ update: [node: WorkflowFlowNode] }>()

const subWorkflow = inject<{
  active: ReturnType<typeof import('vue')['ref']>
  enter: (nodeId: string) => void
  exit: () => void
} | null>('subWorkflow', null)

function updateNode(patch: Partial<WorkflowFlowNode['data']>) {
  emit('update', { ...props.node, data: { ...props.node.data, ...patch } })
}
function updateConfig(key: string, value: unknown) {
  updateNode({ config: { ...(props.node.data.config || {}), [key]: value } })
}

/** 从当前节点的 inputConfigs 中提取名称列表，作为迭代数据源的候选值 */
const inputBindingNames = computed(() =>
  (props.node.data.inputConfigs || []).map((ic) => ic.name).filter(Boolean),
)

const dataSourceOptions = computed(() =>
  inputBindingNames.value.map((name) => ({ label: name, value: name })),
)

function subNodesJson(): string {
  const sn = (props.node.data.config as any)?.subNodes
  return sn ? JSON.stringify(sn, null, 2) : '[]'
}
function subEdgesJson(): string {
  const se = (props.node.data.config as any)?.subEdges
  return se ? JSON.stringify(se, null, 2) : '[]'
}
function onSubNodesChange(text: string) {
  try { updateConfig('subNodes', JSON.parse(text || '[]')) } catch { /* 忽略格式错误 */ }
}
function onSubEdgesChange(text: string) {
  try { updateConfig('subEdges', JSON.parse(text || '[]')) } catch { /* 忽略格式错误 */ }
}

const terminationCollapsed = ref(true)
const panelRoot = ref<HTMLElement>()
const isEditorMaximized = ref(false)

function onEditorMaximizeChange(val: boolean) {
  isEditorMaximized.value = val
}

/** 传递给 EntryNodeSelect 的原始子节点/边数组 */
const rawSubNodes = computed(() => {
  const sn = (props.node.data.config as any)?.subNodes
  return Array.isArray(sn) ? sn : []
})

const loopMode = ref<'data' | 'count'>(
  props.node.data.config?.iterateDataSource ? 'data' : 'count',
)

watch(() => props.node.data.config?.iterateDataSource, (val) => {
  if (!val) loopMode.value = 'count'
})

const rawSubEdges = computed(() => {
  const se = (props.node.data.config as any)?.subEdges
  return Array.isArray(se) ? se : []
})
</script>

<template>
  <div ref="panelRoot" class="loop-panel" :class="{ 'editor-maximized': isEditorMaximized }">
  <AForm layout="vertical">
    <!-- 1. 节点名称 -->
    <PanelSection title="节点名称">
      <NodeNameInput :model-value="node.data.label" @update:model-value="(v: any) => updateNode({ label: v })" />
    </PanelSection>

    <!-- 2. 输入绑定 -->
    <InputBindingSection
      :model-value="node.data.inputConfigs"
      :nodes="nodes" :edges="edges" :current-node-id="node.id"
      @update:model-value="(v: any) => updateNode({ inputConfigs: v })"
    />

    <!-- 3. 迭代设置 -->
    <PanelSection title="迭代设置">
      <!-- 模式选择 -->
      <div class="config-row">
        <span class="config-row-label">
          循环模式
          <ATooltip title="数据迭代：遍历输入绑定提供的数据（数组或集合），逐项执行子流程。计数循环：按固定次数执行子流程，不依赖外部数据。">
            <QuestionCircleOutlined class="help-icon" />
          </ATooltip>
        </span>
        <ASegmented
          v-model:value="loopMode"
          :options="[
            { label: '数据迭代', value: 'data' },
            { label: '计数循环', value: 'count' },
          ]"
          @update:value="(v: any) => { if (v === 'count') updateConfig('iterateDataSource', undefined) }"
        />
      </div>

      <!-- 数据迭代模式 -->
      <div v-if="loopMode === 'data'" class="logic-block data-block">
        <div class="block-header">
          <span class="block-label">数据迭代</span>
          <span class="block-tag data-tag">DATA</span>
        </div>
        <div class="block-body">
          <AFormItem>
            <template #label>
              迭代数据源&nbsp;
              <ATooltip title="从输入绑定中选择提供可迭代数据的变量，支持数组和集合">
                <QuestionCircleOutlined class="help-icon" />
              </ATooltip>
            </template>
            <ASelect
              :value="String(node.data.config?.iterateDataSource || '')"
              :options="dataSourceOptions"
              placeholder="选择输入绑定..."
              allow-clear
              style="width: 100%;"
              @update:value="(v: any) => updateConfig('iterateDataSource', v || undefined)"
            />
            <div v-if="!inputBindingNames.length" class="field-hint">
              请先在「输入绑定」中添加数据来源
            </div>
            <div v-else class="field-hint">
              注意: 值必须是数组或集合，否则运行时将报错
            </div>
          </AFormItem>
          <AFormItem required>
            <template #label>
              元素变量名&nbsp;
              <ATooltip title="子流程中通过此变量访问每次迭代的当前元素">
                <QuestionCircleOutlined class="help-icon" />
              </ATooltip>
            </template>
            <BlurInput :model-value="String(node.data.config?.itemVariable ?? 'item')" placeholder="item" @update:model-value="(v: any) => updateConfig('itemVariable', v)" />
          </AFormItem>
          <AFormItem required>
            <template #label>
              最大循环次数&nbsp;
              <ATooltip title="防止死循环的安全上限">
                <QuestionCircleOutlined class="help-icon" />
              </ATooltip>
            </template>
            <AInputNumber class="full-input" :value="Number(node.data.config?.maxIterations ?? 1000)" :min="1" @update:value="(v: any) => updateConfig('maxIterations', v)" />
          </AFormItem>
        </div>
      </div>

      <!-- 计数循环模式 -->
      <div v-else class="logic-block count-block">
        <div class="block-header">
          <span class="block-label">计数循环</span>
          <span class="block-tag count-tag">COUNT</span>
        </div>
        <div class="block-body">
          <div class="section-desc">按固定次数执行子流程，不依赖外部数据源。</div>
          <AFormItem required>
            <template #label>
              最大循环次数&nbsp;
              <ATooltip title="防止死循环的安全上限">
                <QuestionCircleOutlined class="help-icon" />
              </ATooltip>
            </template>
            <AInputNumber class="full-input" :value="Number(node.data.config?.maxIterations ?? 1000)" :min="1" @update:value="(v: any) => updateConfig('maxIterations', v)" />
          </AFormItem>
        </div>
      </div>

      <!-- 共享：索引变量 -->
      <AFormItem required>
        <template #label>
          循环索引变量名&nbsp;
          <ATooltip title="子流程中通过此变量访问当前迭代索引（从 0 开始）">
            <QuestionCircleOutlined class="help-icon" />
          </ATooltip>
        </template>
        <BlurInput :model-value="String(node.data.config?.loopVariable ?? 'loopIndex')" placeholder="loopIndex" @update:model-value="(v: any) => updateConfig('loopVariable', v)" />
      </AFormItem>

      <!-- 高级选项 -->
      <div class="advanced-toggle" @click="terminationCollapsed = !terminationCollapsed">
        <CaretRightOutlined :class="{ rotated: !terminationCollapsed }" class="toggle-icon" />
        <span>终止表达式</span>
        <ATooltip title="Groovy 表达式，求值为 true 时终止循环。可使用循环变量名、元素变量名和主流程全局变量">
          <QuestionCircleOutlined class="help-icon" />
        </ATooltip>
      </div>
      <div v-show="!terminationCollapsed" class="advanced-options">
        <ConfigCodeEditor
            :model-value="String(node.data.config?.terminationExpression || '')"
            language="txt" :height="'60px'"
            placeholder='如 "item == null" 或 "loopIndex >= 10"'
            :maximize-target="panelRoot"
            @update:model-value="(v: any) => updateConfig('terminationExpression', v)"
            @maximize-change="onEditorMaximizeChange"
          />
      </div>
    </PanelSection>

    <!-- 4. 子流程配置 -->
    <PanelSection title="子流程配置">
      <div class="section-desc">定义每次迭代要执行的处理逻辑。点击按钮进入可视化编辑，也可手动编辑下方 JSON。</div>

      <AFormItem>
        <AButton block type="primary" @click="subWorkflow?.enter(props.node.id)">
          <template #icon><EditOutlined /></template>
          进入子流程可视化编辑
        </AButton>
        <div class="field-hint" style="margin-top:6px;">
          可视化编辑后的结果会自动同步到下方 JSON，也可手动修改 JSON 后刷新页面生效
        </div>
      </AFormItem>

      <!-- 入口节点选择器 -->
      <div class="editor-field">
        <label class="form-label required-field">
          入口节点
          <ATooltip title="选择子流程中没有入边的节点作为入口；每条工作流分支需要一个入口节点">
            <QuestionCircleOutlined class="help-icon" />
          </ATooltip>
        </label>
        <EntryNodeSelect
          :model-value="String(node.data.config?.entryNodeId || '')"
          :sub-nodes="rawSubNodes"
          :sub-edges="rawSubEdges"
          @update:model-value="(v: any) => updateConfig('entryNodeId', v || undefined)"
        />
      </div>

      <div class="editor-field">
        <label class="form-label">
          节点定义
          <ATooltip title="JSON 数组，每个元素含 id/type/name/position/config/inputConfigs/outputConfigs">
            <QuestionCircleOutlined class="help-icon" />
          </ATooltip>
        </label>
        <ConfigCodeEditor
          :model-value="subNodesJson()"
          language="json" :height="'160px'"
          placeholder="[]"
          :maximize-target="panelRoot"
          @update:model-value="(v: any) => onSubNodesChange(v)"
          @maximize-change="onEditorMaximizeChange"
        />
      </div>

      <div class="editor-field">
        <label class="form-label">
          边定义
          <ATooltip title="JSON 数组，每个元素含 id/source/target/sourceHandle/targetHandle">
            <QuestionCircleOutlined class="help-icon" />
          </ATooltip>
        </label>
        <ConfigCodeEditor
          :model-value="subEdgesJson()"
          language="json" :height="'100px'"
          placeholder="[]"
          :maximize-target="panelRoot"
          @update:model-value="(v: any) => onSubEdgesChange(v)"
          @maximize-change="onEditorMaximizeChange"
        />
      </div>
    </PanelSection>

    <!-- 6. 输出说明 -->
    <PanelSection title="输出说明">
      <OutputDisplay :outputs="node.data.outputConfigs || []" />
    </PanelSection>
  </AForm>
  </div>
</template>

<style scoped lang="scss">
.loop-panel { position: relative; }

.help-icon { color: rgba(0, 0, 0, 0.25); font-size: 13px; cursor: help; &:hover { color: rgba(0, 0, 0, 0.45); } }
.full-input { width: 100%; }
.section-desc { margin-bottom: 12px; color: #8c8c8c; font-size: 12px; line-height: 1.6; }
.field-hint { margin-top: 4px; font-size: 12px; color: #8c8c8c; line-height: 1.5; display: flex; align-items: flex-start; }

.editor-field { margin-bottom: 24px; }

.form-label {
  display: block;
  margin-bottom: 8px;
  color: rgba(0, 0, 0, 0.88);
  font-size: 14px;
  font-weight: 400;
  line-height: 1.5;
}

.editor-maximized {
  height: 100%;
  overflow: hidden;
}

/* ── 卡片块（参考 IfElseNodePanel）── */
.logic-block {
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  overflow: hidden;
  margin: 12px 0;
}
.block-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: #fafafa;
  border-bottom: 1px solid #f0f0f0;
}
.block-tag {
  padding: 0 6px;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.8px;
  border-radius: 3px;
  line-height: 18px;
  color: rgba(0, 0, 0, 0.45);
  background: rgba(0, 0, 0, 0.04);
}
.block-label { font-size: 13px; color: #262626; font-weight: 500; }
.block-body { padding: 8px 12px 4px; }

/* ── 高级选项折叠 ── */
.advanced-toggle {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 0;
  cursor: pointer;
  font-size: 12px;
  color: #8c8c8c;
  user-select: none;
  &:hover { color: #1677ff; }
}
.toggle-icon {
  font-size: 10px;
  transition: transform 0.2s;
  &.rotated { transform: rotate(90deg); }
}
.advanced-options {
  padding: 0 0 8px;
}

/* ── config-row ── */
.config-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 32px;
  margin-bottom: 16px;
}
.config-row-label {
  flex-shrink: 0;
  font-size: 14px;
  color: rgba(0, 0, 0, 0.88);
}
</style>
