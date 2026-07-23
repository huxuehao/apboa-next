<script setup lang="ts">
import { ref } from 'vue'

defineProps<{
  id: string,
  name: string
  args?: string
  result?: string
  elapsed?: number
  loading?: boolean
  needConfirm?: boolean
}>()

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
      <template v-if="loading">正在执行 {{ name }}</template>
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
</style>
