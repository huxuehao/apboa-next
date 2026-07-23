<template>
  <!-- 只读回显模式（历史消息）：仅定制渲染器有业务快照价值，通用表单/JSON 由历史卡片
       现有的参数/结果区承担，无定制渲染器时本面板不渲染任何内容 -->
  <div v-if="readonly" class="tool-confirm-panel tool-confirm-panel--readonly">
    <component
      v-if="customRenderer"
      :is="customRenderer"
      :name="name"
      :input="parsedInput"
      :fields="fields"
      readonly
      :decided="decided"
    />
  </div>
  <div v-else class="tool-confirm-panel" @click.stop>
    <!-- ① 定制确认渲染器：按工具名注册的业务语义 UI（解引用编号、业务级交互） -->
    <component
      v-if="customRenderer"
      :is="customRenderer"
      :name="name"
      :input="parsedInput"
      :fields="fields"
      @decide="emit('decide', $event)"
    />
    <!-- ② schema 通用表单：字段元数据驱动的可编辑表单（开发人员核对层） -->
    <GenericConfirmForm
      v-else-if="fields?.length"
      :name="name"
      :input="parsedInput"
      :fields="fields"
      @decide="emit('decide', $event)"
    />
    <!-- ③ JSON 兜底：无任何元数据（框架内置工具等），维持原只读展示 + 允许/禁止 -->
    <div v-else class="tool-confirm-fallback">
      <pre v-if="prettyArgs && prettyArgs !== '{}'" class="chat-tool-item-code">{{ prettyArgs }}</pre>
      <div class="tool-confirm-actions">
        <AButton type="primary" size="small" @click="emit('decide', { approved: true })">允许</AButton>
        <AButton size="small" @click="emit('decide', { approved: false })">禁止</AButton>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * HITL 确认 UI 分发面板（主工具卡与子智能体步共用）：按逐级回退策略选择确认形态——
 * 定制渲染器（confirmRenderers 注册表）→ schema 通用表单 → JSON 只读兜底。
 * 三层 UI 的产出统一为 decide({ approved, input? })，经同一 resume 管道回传执行；
 * 系统级拦截闸门在后端（IConfirmationHook），本面板只是闸门上的门面。
 */
import { computed } from 'vue'
import type { ConfirmFieldMeta } from '@/types'
import { resolveConfirmRenderer } from './confirmRenderers'
import GenericConfirmForm from './GenericConfirmForm.vue'

const props = defineProps<{
  /** 工具原始名（toolId / MCP 原生名，注册表匹配 key） */
  name: string
  /** 参数 JSON 串：确认时=模型生成值；只读回显时=落库最终值（含用户改参） */
  args?: string
  fields?: ConfirmFieldMeta[]
  /** 只读回显模式（历史消息重现确认时的业务快照，不可交互） */
  readonly?: boolean
  /** 只读模式下的决策结果（落库 confirmState） */
  decided?: 'approved' | 'rejected'
}>()

const emit = defineEmits<{
  /** summary：定制渲染器可选提供的业务摘要，供工具卡在决策后渲染一行只读回显 */
  decide: [value: { approved: boolean; input?: Record<string, unknown>; summary?: string }]
}>()

const customRenderer = computed(() => resolveConfirmRenderer(props.name))

const parsedInput = computed<Record<string, unknown>>(() => {
  try {
    const v = JSON.parse(props.args || '{}')
    return v && typeof v === 'object' && !Array.isArray(v) ? v : {}
  } catch {
    return {}
  }
})

const prettyArgs = computed(() => {
  if (!props.args) return ''
  try {
    return JSON.stringify(JSON.parse(props.args), null, 2)
  } catch {
    return props.args
  }
})
</script>

<style scoped lang="scss">
@use '@/styles/chat/index.scss' as *;

.tool-confirm-panel {
  margin: 6px 0 4px;
}

.tool-confirm-fallback {
  .tool-confirm-actions {
    display: flex;
    gap: 8px;
    margin-top: 6px;

    :deep(.ant-btn) {
      border-radius: 999px;
      padding-inline: 16px;
    }
  }
}
</style>

<style lang="scss">
/* 确认卡内 Select 的下拉浮层统一样式：浮层挂载于 body，scoped 样式不可达，
   须全局定义（各 Select 以 popup-class-name="confirm-select-popup" 挂载本类）。
   项目 antd 主题基准字号 16 且选中项默认 600 加粗，与卡片内 13~15px/400 割裂，
   此处对齐卡片字号并去除选中加粗 */
.confirm-select-popup {
  .ant-select-item {
    font-size: 13px;
    min-height: 28px;
  }

  .ant-select-item-option-selected:not(.ant-select-item-option-disabled) {
    font-weight: 400;
  }
}
</style>
