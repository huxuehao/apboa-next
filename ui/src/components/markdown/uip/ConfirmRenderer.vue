<template>
  <div class="uip-confirm-renderer">
    <div class="uip-confirm-message">
      <span
        class="uip-confirm-icon"
        :class="{
          'is-confirmed': disabled && interaction.submittedData?.confirmed,
          'is-cancelled': disabled && interaction.submittedData && !interaction.submittedData.confirmed
        }"
      >
        <template v-if="disabled && interaction.submittedData?.confirmed">&#10003;</template>
        <template v-else-if="disabled && interaction.submittedData && !interaction.submittedData.confirmed">&#10007;</template>
        <template v-else>?</template>
      </span>
      {{ interaction.message }}
    </div>
    <div v-if="interaction.payload && !disabled" class="uip-confirm-payload" @click="payloadOpen = !payloadOpen">
      <span class="uip-confirm-payload-toggle">
        {{ payloadOpen ? '收起' : '查看' }}详情
        <span :class="['uip-confirm-arrow', { 'is-open': payloadOpen }]">▾</span>
      </span>
      <pre v-if="payloadOpen" class="uip-confirm-payload-content">{{ JSON.stringify(interaction.payload, null, 2) }}</pre>
    </div>

    <div v-if="!disabled" class="uip-confirm-actions">
      <a-button type="primary" @click="handleSubmit" :loading="submitting" :disabled="canceling">
        {{ interaction.confirmLabel || '确认' }}
      </a-button>
      <a-button style="margin-left: 8px" @click="handleCancel" :loading="canceling"  :disabled="submitting">
        {{ interaction.cancelLabel || '取消' }}
      </a-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { ConfirmInteraction } from './types'

defineProps<{
  interaction: ConfirmInteraction
  disabled?: boolean
}>()

const emit = defineEmits<{
  submit: [data: { confirmed: boolean; payload?: Record<string, unknown> }]
}>()

const submitting = ref(false)
const canceling = ref(false)
const payloadOpen = ref(false)

async function handleSubmit() {
  if (submitting.value) return
  submitting.value = true
  emit('submit', { confirmed: true })
  // 不需将 submitting.value 只为 false，disabled 时会隐藏按钮
}

function handleCancel() {
  if (canceling.value) return
  canceling.value = true
  emit('submit', { confirmed: false })
  // 不需将 submitting.value 只为 false，disabled 时会隐藏按钮
}
</script>

<style scoped>
.uip-confirm-renderer {
  margin: 12px 0;
  border: 1px solid #e8ecf1;
  border-radius: 12px;
  padding: 18px 20px;
  background: #fafbfc;
}

.uip-confirm-message {
  font-size: 15px;
  font-weight: 600;
  color: #1d2129;
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.uip-confirm-icon {
  flex-shrink: 0;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #f59e0b;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.25s;
}

.uip-confirm-icon.is-confirmed {
  background: #52c41a;
}

.uip-confirm-icon.is-cancelled {
  background: #c9cdd4;
}

.uip-confirm-payload {
  margin: 10px 0;
  cursor: pointer;
  user-select: none;
}

.uip-confirm-payload-toggle {
  font-size: 12px;
  color: #4e5969;
}

.uip-confirm-arrow {
  display: inline-block;
  transition: transform 0.2s;
  font-size: 10px;
  margin-left: 2px;
}

.uip-confirm-arrow.is-open {
  transform: rotate(180deg);
}

.uip-confirm-payload-content {
  margin-top: 8px;
  padding: 10px;
  background: #f2f4f7;
  border-radius: 8px;
  font-size: 12px;
  color: #4e5969;
  max-height: 160px;
  overflow-y: auto;
}

.uip-confirm-actions {
  padding-top: 12px;
  border-top: 1px solid #f0f1f3;
}
</style>
