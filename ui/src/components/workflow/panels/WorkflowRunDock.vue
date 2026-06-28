<script setup lang="ts">
import { computed, ref } from 'vue'
import { CloseOutlined, PlayCircleOutlined,BugOutlined } from '@ant-design/icons-vue'
import SmartCodeEditor from '@/components/editor/SmartCodeEditor.vue'
import type { WorkflowRunResult } from '@/types/workflow'

defineProps<{
  open: boolean
  result: WorkflowRunResult | null
  loading?: boolean
}>()

const inputText = defineModel<string>('inputText', { default: '{\n  "body": {},\n  "variables": {}\n}' })

const emit = defineEmits<{
  run: []
  close: []
  focusNode: [nodeId: string]
}>()

const activeKey = ref('input')
const width = ref(530)
const maxWidth = computed(() => Math.floor(window.innerWidth * 0.5))
const dragging = ref(false)
const outputText = computed(() => '')


function beginResize(event: MouseEvent) {
  dragging.value = true
  const startX = event.clientX
  const startWidth = width.value
  const onMove = (moveEvent: MouseEvent) => {
    const next = startWidth + (startX - moveEvent.clientX)
    width.value = Math.max(350, Math.min(maxWidth.value, next))
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
  <section v-if="open" class="run-dock" :class="{ dragging }" :style="{ width: `${width}px` }">
    <div class="resize-handle" @mousedown.prevent="beginResize" />

    <header class="dock-header">
      <div>
        <div class="dock-title"><BugOutlined /> 调试面板</div>
        <div class="dock-subtitle">使用当前草稿配置运行，不影响已发布版本</div>
      </div>
      <div class="dock-actions">
        <AButton type="primary" :loading="loading" @click="emit('run')">
          <template #icon><PlayCircleOutlined /></template>
          运行草稿
        </AButton>
        <AButton type="text" @click="emit('close')">
          <template #icon><CloseOutlined /></template>
        </AButton>
      </div>
    </header>

    <ATabs v-model:active-key="activeKey" size="small" class="dock-tabs">
      <ATabPane key="input" tab="调试输入">
        <SmartCodeEditor
          v-model="inputText"
          language="json"
          theme="light"
          height="210px"
          :show-change-language="false"
          :show-theme-toggle="false"
          :show-fullscreen="false"
        />
      </ATabPane>
      <ATabPane key="result" tab="运行结果">
        <pre class="result-pre">{{ result ? JSON.stringify(result.output ?? result.run?.outputs ?? result.run, null, 2) : outputText }}</pre>
      </ATabPane>
      <ATabPane key="nodes" tab="节点日志">
        <div class="execution-list">
          <button
            v-for="item in result?.nodeExecutions || []"
            :key="item.id"
            type="button"
            class="execution-item"
            @click="emit('focusNode', item.nodeId)"
          >
            <span class="execution-copy">
              <span class="execution-title">{{ item.nodeTitle || item.nodeId }}</span>
              <span class="execution-type">{{ item.nodeType }}</span>
            </span>
            <ATag :color="item.status === 'SUCCESS' ? 'green' : item.status === 'FAIL' ? 'red' : 'blue'">
              {{ item.status }}
            </ATag>
          </button>
        </div>
      </ATabPane>
    </ATabs>
  </section>
</template>

<style scoped lang="scss">
.run-dock {
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

.dock-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-bottom: 1px solid #f0f0f0;
}

.dock-title {
  color: #262626;
  font-size: 15px;
  font-weight: 700;
}

.dock-subtitle {
  color: #b3b3b3;
  font-size: 12px;
}

.dock-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.dock-tabs {
  min-height: 0;
  padding: 0 14px 14px;
  overflow: auto;
}

.result-pre {
  min-height: 210px;
  margin: 0;
  padding: 12px;
  overflow: auto;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fafafa;
  font-size: 12px;
}

.execution-list {
  display: grid;
  gap: 8px;
}

.execution-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  padding: 9px 10px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  text-align: left;
}

.execution-copy {
  display: grid;
  gap: 2px;
}

.execution-title {
  color: #262626;
  font-weight: 700;
}

.execution-type {
  color: #8c8c8c;
  font-size: 12px;
}

@media (max-width: 900px) {
  .run-dock {
    left: 12px;
    right: 12px;
  }
}
</style>
