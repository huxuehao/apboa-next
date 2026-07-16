/**
 * 简约开关控件
 *
 * @author huxuehao
 */
<script setup lang="ts">
const props = withDefaults(defineProps<{
  checked?: boolean
  disabled?: boolean
  loading?: boolean
}>(), {
  checked: false,
  disabled: false,
  loading: false,
})

const emit = defineEmits<{
  'update:checked': [value: boolean]
  change: [checked: boolean]
}>()

function toggle() {
  if (props.disabled || props.loading) return
  const next = !props.checked
  emit('update:checked', next)
  emit('change', next)
}
</script>

<template>
  <button
    type="button"
    class="simple-switch"
    :class="{
      'is-checked': checked,
      'is-disabled': disabled,
      'is-loading': loading,
    }"
    :disabled="disabled || loading"
    @click="toggle"
  >
    <span class="switch-knob">
      <span v-if="loading" class="switch-spinner" />
    </span>
  </button>
</template>

<style scoped lang="scss">
.simple-switch {
  position: relative;
  display: inline-flex;
  align-items: center;
  width: 28px;
  height: 16px;
  padding: 0;
  border: none;
  border-radius: 10px;
  background-color: #d9d9d9;
  cursor: pointer;
  transition: background-color 0.2s ease;
  flex-shrink: 0;
  outline: none;

  &:hover:not(.is-disabled) {
    background-color: #c0c0c0;
  }

  &.is-checked {
    background-color: #4096ff;

    &:hover:not(.is-disabled) {
      background-color: #69b1ff;
    }

    .switch-knob {
      transform: translateX(12px);
    }
  }

  &.is-disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }

  .switch-knob {
    position: absolute;
    top: 2px;
    left: 2px;
    width: 12px;
    height: 12px;
    border-radius: 50%;
    background-color: #fff;
    box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
    transition: transform 0.2s ease;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .switch-spinner {
    width: 8px;
    height: 8px;
    border: 1.5px solid rgba(0, 0, 0, 0.15);
    border-top-color: rgba(0, 0, 0, 0.45);
    border-radius: 50%;
    animation: switch-spin 0.6s linear infinite;
  }
}

@keyframes switch-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
