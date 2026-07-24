<script setup lang="ts">
/**
 * DayDivider —— 消息列表中的日期分隔线。
 *
 * 相对时间（"周三 14:20"）本身没有日期锚点，长会话靠它定位是哪一天。
 */
defineProps<{
  day: string
}>()
</script>

<template>
  <div class="chat-day-divider">
    <span class="chat-day-divider-text">{{ day }}</span>
  </div>
</template>

<style scoped lang="scss">
.chat-day-divider {
  position: relative;
  align-self: stretch;
  margin: 12px 0;
  text-align: center;

  &::before {
    content: '';
    position: absolute;
    left: 0;
    right: 0;
    top: 50%;
    border-top: 1px dashed var(--color-border-light, rgba(0, 0, 0, 0.1));
  }
}

.chat-day-divider-text {
  position: relative;
  padding: 0 12px;
  /* 与聊天主区背景一致（$chat-bg-main），遮住穿过的虚线 */
  background: #ffffff;
  font-size: var(--font-size-xs);
  color: var(--color-text-placeholder);
  user-select: none;
}
</style>
