<script setup lang="ts">
/**
 * 保存模态框：单弹窗提供「直接保存」与「保存为历史版本(可填说明)」两个动作。
 *
 * @author huxuehao
 */
import { ref, watch } from 'vue'

const props = defineProps<{ open: boolean; saving?: boolean }>()
const emit = defineEmits<{
  (e: 'update:open', v: boolean): void
  (e: 'save'): void
  (e: 'saveVersion', note: string): void
}>()

const note = ref('')

watch(
  () => props.open,
  (v) => {
    if (v) note.value = ''
  },
)

function close() {
  emit('update:open', false)
}
</script>

<template>
  <a-modal
    :open="open"
    title="保存工作台"
    :footer="null"
    width="440px"
    @update:open="emit('update:open', $event)"
  >
    <p class="sv-hint">
      直接保存会覆盖当前配置；保存为历史版本会额外留存一个快照，便于日后回滚。
    </p>
    <div class="sv-field">
      <span class="sv-label">版本说明</span>
      <a-input
        v-model:value="note"
        placeholder="选填，仅用于历史版本"
        :maxlength="200"
        allow-clear
      />
    </div>
    <div class="sv-actions">
      <a-button @click="close">取消</a-button>
      <a-button :loading="saving" @click="emit('save')">直接保存</a-button>
      <a-button type="primary" :loading="saving" @click="emit('saveVersion', note)">
        保存为历史版本
      </a-button>
    </div>
  </a-modal>
</template>

<style scoped lang="scss">
.sv-hint {
  margin: 0 0 14px;
  font-size: 12px;
  color: #999;
  line-height: 1.7;
}

.sv-field {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}

.sv-label {
  flex-shrink: 0;
  width: 60px;
  font-size: 13px;
  color: #595959;
}

.sv-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
