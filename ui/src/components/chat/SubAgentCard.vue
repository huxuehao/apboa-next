<script setup lang="ts">
import { computed, ref, watch, nextTick, onMounted } from 'vue'
import {
  CheckCircleFilled,
  DownOutlined,
  LoadingOutlined,
  StopFilled,
  WarningFilled,
} from '@ant-design/icons-vue'
import { Tooltip as ATooltip } from 'ant-design-vue'
import SubAgentEventList from './SubAgentEventList.vue'
import type { SubAgentRunVO } from '@/types'
import type { InteractionSubmitPayload } from '@/components/markdown/uip/types'
import agentAvatar from '@/assets/avatar/agent.png'

const props = defineProps<{ run: SubAgentRunVO }>()

defineEmits<{
  (e: 'inputTagPreview', value: unknown): void
  (e: 'interactionSubmit', payload: InteractionSubmitPayload): void
  (e: 'uipRetry', uipCode: string): void
  (e: 'vepRetry', vepCode: string): void
}>()

const isTerminal = (status?: string) =>
  ['SUCCESS', 'BLOCKED', 'FAILED', 'CANCELLED'].includes(status ?? '')
const collapsed = ref(isTerminal(props.run.status))
const running = computed(() => !isTerminal(props.run.status))
const title = computed(() => props.run.agentTitle || props.run.agentCode || '子智能体')
const summary = computed(() =>
  (props.run.summary || props.run.task || '已完成子智能体执行')
    .replace(/(^|\r?\n)session_id:\s*[^\r\n]+/gi, '')
    .trim(),
)
const statusText = computed(
  () =>
    ({
      SUCCESS: '执行完成',
      BLOCKED: '需主智能体接管',
      FAILED: '执行失败',
      CANCELLED: '已取消',
    })[props.run.status] || '运行中',
)

// 任务区域折叠相关逻辑
const taskRef = ref<HTMLElement | null>(null)
const taskCollapsed = ref(false)
const taskNeedCollapse = ref(false)
const isTaskHovered = ref(false)
const MAX_AUTO_COLLAPSE_HEIGHT = 80

// 检查任务内容高度，决定是否需要折叠
const checkTaskHeight = () => {
  if (!taskRef.value) return
  taskNeedCollapse.value = taskRef.value.scrollHeight > MAX_AUTO_COLLAPSE_HEIGHT
  if (taskNeedCollapse.value) {
    taskCollapsed.value = true
  }
}

// 切换任务区域的折叠状态
const toggleTaskCollapse = () => {
  if (!taskNeedCollapse.value) return
  taskCollapsed.value = !taskCollapsed.value
}

// 监听 run.task 变化，重新检查高度
watch(
  () => props.run.task,
  async () => {
    await nextTick()
    checkTaskHeight()
  }
)

onMounted(() => {
  checkTaskHeight()
})

// 监听卡片主体折叠状态，当卡片展开时重新检查任务高度
watch(
  () => collapsed.value,
  async (newVal) => {
    if (!newVal) {
      await nextTick()
      checkTaskHeight()
    }
  }
)

watch(
  () => props.run.status,
  (next, previous) => {
    if (isTerminal(next) && !isTerminal(previous)) collapsed.value = true
  },
)
</script>

<template>
  <section
    class="subagent-card"
    :class="{
      'is-running': running,
      'is-failed': run.status === 'FAILED',
      'is-blocked': run.status === 'BLOCKED',
    }"
  >
    <button
      type="button"
      class="subagent-card-header"
      :aria-expanded="!collapsed"
      @click="collapsed = !collapsed"
    >
      <ATooltip :title="statusText" placement="top">
        <span class="card-avatar flex-center">
          <LoadingOutlined v-if="running" class="card-avatar-loading" spin />
          <img v-else :src="agentAvatar" alt="agent" />
          <span v-if="!running" class="card-avatar-status" aria-hidden="true">
            <WarningFilled v-if="run.status === 'FAILED' || run.status === 'BLOCKED'" />
            <StopFilled v-else-if="run.status === 'CANCELLED'" />
            <CheckCircleFilled v-else />
          </span>
        </span>
      </ATooltip>
      <span class="subagent-card-heading">
        <span class="subagent-card-title"
        >{{ running ? '正在执行' : '' }}智能体（{{ title }}）</span
        >
        <span class="subagent-card-subtitle">{{
            running ? run.task || '正在执行任务' : summary
          }}</span>
      </span>
      <DownOutlined class="subagent-card-arrow" :class="{ 'is-collapsed': collapsed }" />
    </button>
    <Transition name="collapse">
      <div v-show="!collapsed" class="subagent-card-details">
        <div
          v-if="run.task"
          ref="taskRef"
          class="subagent-card-task"
          :class="{
            'is-collapsed': taskCollapsed,
            'is-expandable': taskNeedCollapse,
          }"
          @click="toggleTaskCollapse"
          @mouseenter="isTaskHovered = true"
          @mouseleave="isTaskHovered = false"
        >
          <span class="task-label">任务: </span>
          <span class="task-content">{{ run.task }}</span>

          <!-- 悬停提示遮罩 -->
          <div
            v-if="taskNeedCollapse"
            class="task-hint-overlay"
          >
          <Transition name="hint-fade">
            <span class="task-hint-text" v-if="isTaskHovered">
              {{ taskCollapsed ? '点击展开任务' : '点击折叠任务' }}
            </span>
          </Transition>
          </div>
        </div>
        <SubAgentEventList
          :events="run.events"
          :active="running"
          @inputTagPreview="$emit('inputTagPreview', $event)"
          @interaction-submit="$emit('interactionSubmit', $event)"
          @uip-retry="$emit('uipRetry', $event)"
          @vep-retry="$emit('vepRetry', $event)"
        />
      </div>
    </Transition>
  </section>
</template>

<style scoped lang="scss">
.subagent-card {
  margin: 12px 14px;
  overflow: hidden;
  border: 1px solid #f0f0f0;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 0 10px rgba(0, 0, 0, 0.03);
}

.subagent-card.is-running {
  border-color: #91caff;
}

.subagent-card.is-failed {
  border-color: #ffccc7;
}

.subagent-card.is-blocked {
  border-color: #ffe58f;
}

.subagent-card-header {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 10px;
  padding: 11px 12px;
  border: 0;
  background: #fff;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.card-avatar {
  position: relative;
  width: 40px;
  height: 40px;
  background-color: #e8eaf6;
  border-radius: var(--border-radius-xl);
  flex-shrink: 0;

  img {
    width: 28px;
    height: 28px;
    object-fit: contain;
  }
}

.card-avatar-loading {
  color: #1677ff;
  font-size: 21px;
}

.card-avatar-status {
  position: absolute;
  right: -2px;
  bottom: -2px;
  display: inline-flex;
  width: 17px;
  height: 17px;
  align-items: center;
  justify-content: center;
  border: 1px solid #fff;
  border-radius: 50%;
  background: #fff;
  color: #12b76a;
  font-size: 11px;
}

.is-failed .card-avatar-status {
  color: #cf1322;
}

.is-blocked .card-avatar-status {
  color: #d48806;
}

.subagent-card:not(.is-running) .card-avatar-status {
  cursor: help;
}

.subagent-card-heading {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 2px;
}

.subagent-card-title {
  overflow: hidden;
  color: #1d2939;
  font-size: 14px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.subagent-card-subtitle {
  overflow: hidden;
  color: #667085;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.subagent-card-arrow {
  flex: 0 0 auto;
  color: #98a2b3;
  transition: transform 0.2s ease;
}

.subagent-card-arrow.is-collapsed {
  transform: rotate(-90deg);
}

.subagent-card-details {
  border-top: 1px solid #f0f0f0;
  background: #fcfcfd;
  overflow: hidden;
}

.subagent-card-task {
  position: relative;
  color: #475467;
  font-size: 13px;
  line-height: 1.55;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  background-color: #ffffff;
  padding: 8px 10px;
  border: 1px solid #f0f0f0;
  box-shadow: 0 0 10px rgba(0, 0, 0, 0.03);
  margin: 5px;
  border-radius: 8px;
  transition: max-height 0.35s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
  cursor: default;

  &.is-expandable {
    cursor: pointer;

    &.is-collapsed {
      max-height: 80px;
    }

    &:not(.is-collapsed) {
      max-height: 400px;
    }
  }
}

.task-label {
  margin-right: 8px;
  color: #828282;
  font-weight: bolder;
  font-size: 13px;
}

.task-content {
  position: relative;
  z-index: 1;
}

/* 悬停提示遮罩 */
.task-hint-overlay {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 40px;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding-bottom: 6px;
  background: linear-gradient(to top, rgba(255, 255, 255, 0.75) 0%, rgba(255, 255, 255, 0) 100%);
  border-radius: 0 0 6px 6px;
  pointer-events: none;
  z-index: 2;
}

.task-hint-text {
  font-size: 12px;
  color: #1677ff;
  font-weight: 500;
  padding: 2px 10px;
  background-color: white;
  border-radius: 4px;
  white-space: nowrap;
}

/* 提示文字淡入淡出动画 */
.hint-fade-enter-active {
  transition: opacity 0.25s ease;
}
.hint-fade-leave-active {
  transition: opacity 0.2s ease;
}
.hint-fade-enter-from,
.hint-fade-leave-to {
  opacity: 0;
}

/* 折叠过渡动画 */
.collapse-enter-active,
.collapse-leave-active {
  transition:
    max-height var(--transition-base),
    opacity var(--transition-fast),
    padding var(--transition-fast);
  overflow: hidden;
}

.collapse-enter-from,
.collapse-leave-to {
  max-height: 0;
  opacity: 0;
  padding-top: 0;
  padding-bottom: 0;
}

.collapse-enter-to,
.collapse-leave-from {
  max-height: 500px;
  opacity: 1;
}
</style>
