<script setup lang="ts">
import { computed, ref } from 'vue'
import { CloseOutlined } from '@ant-design/icons-vue'
import WorkflowFieldRenderer from '@/components/workflow/fields/WorkflowFieldRenderer.vue'
import InputBindingEditor from '@/components/workflow/bindings/InputBindingEditor.vue'
import IconFont from '@/components/common/IconFont.vue'
import type { IconName } from '@/components/common/icons'
import type { WorkflowFlowNode, WorkflowResourceMaps } from '@/types/workflow'

const props = defineProps<{
  node: WorkflowFlowNode | null
  nodes: WorkflowFlowNode[]
  resources: WorkflowResourceMaps
}>()

const emit = defineEmits<{
  update: [node: WorkflowFlowNode]
  close: []
}>()

const width = ref(530)
const dragging = ref(false)

const maxWidth = computed(() => Math.floor(window.innerWidth * 0.5))

/**
 * 节点类型 → iconfont 图标名称映射
 */
const nodeIconMap: Record<string, IconName> = {
  START: 'nodestart',
  END: 'nodeend',
  IF_ELSE: 'nodeif_else',
  CACHE_FETCH: 'nodecache',
  CACHE_SET: 'nodecache',
  CACHE_REMOVE: 'nodecache',
  CACHE_REFRESH: 'nodecache',
  DB_SELECT: 'nodedb_select',
  DB_INSERT: 'nodedb_insert',
  DB_UPDATE: 'nodedb_update',
  DB_DELETE: 'nodedb_delete',
  MQ_PUSH: 'nodemq_push',
  HTTP_EXTERNAL: 'nodehttp_external',
  CODE: 'nodecode',
  ITERATE: 'nodeiterate',
  LOOP: 'nodeloop',
  LIST_FILTER: 'nodelist_filter',
  LIST_SORT: 'nodelist_sort',
  STRING_SPLIT: 'nodestring_split',
  STRING_TEMPLATE: 'nodestring_template',
  SERIALIZE: 'nodeserialize',
  UNSERIALIZE: 'nodeunserialize',
  VARIABLE_AGG: 'nodevariable_agg',
  NON_EMPTY_SELECT: 'nodenon_empty_select',
  MATCH_RESULT: 'nodematch_result',
}

function getNodeIconName(type: string): IconName {
  return nodeIconMap[type] || 'nodecode'
}

const nodeColor = computed(() => props.node?.data.schema?.color || '#1677ff')

function updateNode(patch: Partial<WorkflowFlowNode['data']>) {
  if (!props.node) return
  emit('update', { ...props.node, data: { ...props.node.data, ...patch } })
}

function updateConfig(key: string, value: unknown) {
  if (!props.node) return
  updateNode({ config: { ...(props.node.data.config || {}), [key]: value } })
}

function beginResize(event: MouseEvent) {
  dragging.value = true
  const startX = event.clientX
  const startWidth = width.value
  const onMove = (moveEvent: MouseEvent) => {
    const next = startWidth + (startX - moveEvent.clientX)
    width.value = Math.max(530, Math.min(maxWidth.value, next))
  }
  const onUp = () => {
    dragging.value = false
    window.removeEventListener('mousemove', onMove)
    window.removeEventListener('mouseup', onUp)
  }
  window.addEventListener('mousemove', onMove)
  window.addEventListener('mouseup', onUp)
}
</script>

<template>
  <aside v-if="node" class="config-panel" :class="{ dragging }" :style="{ width: `${width}px` }">
    <div class="resize-handle" @mousedown.prevent="beginResize" />

    <header class="panel-header">
      <div class="panel-title-wrap">
        <span class="panel-avatar" :style="{ backgroundColor: `${nodeColor}CC` }">
          <IconFont :name="getNodeIconName(node.data.type)" :size="16" color="#ffffff" />
        </span>
        <div class="panel-title">{{ node.data.schema?.title || node.data.type }}</div>
      </div>
      <AButton type="text" @click="$emit('close')">
        <template #icon><CloseOutlined /></template>
      </AButton>
    </header>

    <div class="panel-body">
      <AForm layout="vertical">
        <AFormItem label="节点名称" required>
          <AInput :value="node.data.label" @update:value="(value: string) => updateNode({ label: value })" />
        </AFormItem>

        <div class="config-group">
          <div class="group-header">基础配置</div>
          <div class="group-body">
            <WorkflowFieldRenderer
              v-for="field in node.data.schema?.fields || []"
              :key="field.name"
              :field="field"
              :value="node.data.config?.[field.name]"
              :resources="resources"
              :nodes="nodes"
              :current-node-id="node.id"
              @change="(value) => updateConfig(field.name, value)"
            />
          </div>
        </div>

        <div class="config-group">
          <div class="group-header">输入绑定</div>
          <div class="group-body">
            <InputBindingEditor
              :model-value="node.data.inputConfigs"
              :nodes="nodes"
              :current-node-id="node.id"
              @update:model-value="(value) => updateNode({ inputConfigs: value })"
            />
          </div>
        </div>

        <div class="config-group">
          <div class="group-header">输出说明</div>
          <div class="group-body">
            <div class="output-list">
              <div v-for="output in node.data.outputConfigs || []" :key="output.name" class="output-item">
                <span class="output-name">{{ output.name }}</span>
                <span class="output-type">{{ output.type || 'Object' }}</span>
                <span class="output-desc">{{ output.description || '节点运行输出' }}</span>
              </div>
            </div>
          </div>
        </div>

        <div class="config-group">
          <div class="group-header">原始配置</div>
          <div class="group-body">
            <pre class="raw-json">{{ JSON.stringify(node.data.config || {}, null, 2) }}</pre>
          </div>
        </div>
      </AForm>
    </div>
  </aside>
</template>

<style scoped lang="scss">
.config-panel {
  position: absolute;
  right: 16px;
  top: 60px;
  bottom: 18px;
  z-index: 16;
  min-width: 530px;
  max-width: 50vw;
  box-shadow: 0px 3px 10px rgba(0, 0, 0, 0.08);
  border-radius: 8px;
  background: #fff;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
}

.resize-handle {
  position: absolute;
  left: -5px;
  top: 0;
  bottom: 0;
  width: 3px;
  cursor: col-resize;
  background: transparent;
  transition: background 0.2s ease;
}

.resize-handle::after {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 40px;
  border-radius: 1px;
  background: #c9c9c9;
  transition: background 0.2s ease;
}

.resize-handle:hover::after,
.dragging .resize-handle::after {
  display: none;
}

.resize-handle:hover,
.dragging .resize-handle {
  background: #1677FF;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 6px 16px;
  border-bottom: 1px solid #f0f0f0;
}

.panel-title-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.panel-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 6px;
  flex-shrink: 0;
}

.panel-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #262626;
  font-size: 15px;
  font-weight: 700;
}

.panel-body {
  min-height: 0;
  overflow: auto;
  padding: 12px 16px 18px;
}

.config-group {
  margin-bottom: 24px;

  &:last-child {
    margin-bottom: 0;
  }
}

.group-header {
  position: relative;
  margin-bottom: 10px;
  padding: 0 0 6px 10px;
  color: #2D2626;
  font-size: 14px;
  font-weight: 700;

  &::before {
    content: '';
    position: absolute;
    left: 0;
    top: 2px;
    bottom: 10px;
    width: 3px;
    border-radius: 2px;
    background: #2D2626;
  }
}

.group-body {
  display: grid;
  // gap: 12px;
}

.output-list {
  display: grid;
  gap: 8px;
}

.output-item {
  display: grid;
  grid-template-columns: minmax(80px, 1fr) 80px minmax(0, 1.4fr);
  gap: 8px;
  padding: 8px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  font-size: 12px;
}

.output-name {
  color: #262626;
  font-weight: 700;
}

.output-type,
.output-desc {
  color: #8c8c8c;
}

.raw-json {
  max-height: 260px;
  margin: 0;
  padding: 10px;
  overflow: auto;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fafafa;
  font-size: 12px;
}

@media (max-width: 900px) {
  .config-panel {
    left: 12px;
    right: 12px;
    top: auto;
    width: auto !important;
    min-width: 0;
    max-width: none;
    height: 70vh;
  }

  .resize-handle {
    display: none;
  }
}
</style>
