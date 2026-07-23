<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { useToolCallDisplayName } from '@/composables/chat/useToolCallDisplayName'

const props = defineProps<{
  id: string,
  name: string
  args?: string
  result?: string
  elapsed?: number
  loading?: boolean
  needConfirm?: boolean
  startTime?: number
}>()

const { resolveToolCallName } = useToolCallDisplayName()

/** 仅显示层翻译（工具/子智能体 → 显示名）；HITL 允许/禁止回传仍用原始 name */
const displayName = computed(() => resolveToolCallName(props.name))

// 执行中实时耗时（100ms 刷新，0.1s 步进滚动）；needConfirm 暂停态不计时（工具没在跑，是在等人）
const nowTick = ref(Date.now())
let elapsedTimer: number | null = null

const startTick = () => {
  if (elapsedTimer != null) return
  nowTick.value = Date.now()
  elapsedTimer = window.setInterval(() => { nowTick.value = Date.now() }, 100)
}
const stopTick = () => {
  if (elapsedTimer != null) {
    clearInterval(elapsedTimer)
    elapsedTimer = null
  }
}

watch(
  () => props.loading && !props.needConfirm,
  (running) => { running ? startTick() : stopTick() },
  { immediate: true }
)
onUnmounted(stopTick)

const runningElapsed = computed(() => {
  if (!props.startTime) return ''
  const s = Math.max(0, nowTick.value - props.startTime) / 1000
  return `${s.toFixed(1)}s`
})

const emit = defineEmits<{
  (e: 'toolContent', value: any): void
}>()

const foldArgs = ref<boolean>(true)

/** 允许：仅记录决策（§6.5），工具实际由后端 resume 续跑执行（天然带租户/MCP 上下文） */
const handleConfirm = (id: string, name: string) => {
  emit('toolContent', { toolUseId: id, name, approved: true })
}

/** 禁止：仅记录决策（§6.5），后端 resume 时喂入「拒绝授权」错误结果，不再前端塞文本 */
const handleCancel = (id: string, name: string) => {
  emit('toolContent', { toolUseId: id, name, approved: false })
}

/*查看参数*/
const handleShowArgs = () => {
  foldArgs.value = !foldArgs.value
}
</script>

<template>
  <div>
    <div class="chat-tool-call" :class="{ 'chat-tool-call--loading': loading }">
      <span class="chat-tool-call-dot"></span>
      <span class="chat-tool-call-label">
      <template v-if="loading">
        正在执行 {{ displayName }}<span v-if="!needConfirm && runningElapsed" class="chat-tool-call-elapsed"> · {{ runningElapsed }}</span>
      </template>
      <template v-if="needConfirm" >
        <div class="chat-tool-call-actions">
          <AButton v-if="args && args !== '{}'"
                   type="link"
                   size="small"
                   @click="handleShowArgs">
            {{ `${foldArgs ? '展开参数' : '折叠参数'}` }}
          </AButton>
          <AButton type="primary" size="small" @click="handleConfirm(id, name)">允许</AButton>
          <AButton size="small" @click="handleCancel(id, name)">禁止</AButton>
        </div>
      </template>
    </span>
    </div>
    <div class="chat-tool-call" v-if="args && args !== '{}' && !foldArgs">
      {{ args && args !== '{}' ? args : '无参数' }}
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/chat/index.scss' as *;

/* 等宽数字：计时滚动时不左右抖动 */
.chat-tool-call-elapsed {
  font-variant-numeric: tabular-nums;
}
</style>
