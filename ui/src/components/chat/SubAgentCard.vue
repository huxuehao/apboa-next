<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { CheckCircleFilled, DownOutlined, LoadingOutlined, StopFilled, WarningFilled } from '@ant-design/icons-vue'
import { Tooltip as ATooltip } from 'ant-design-vue'
import SubAgentEventList from './SubAgentEventList.vue'
import type { SubAgentRunVO } from '@/types'
import type { InteractionSubmitPayload } from '@/components/markdown/uip/types'
import agentAvatar from "@/assets/avatar/agent.png";

const props = defineProps<{ run: SubAgentRunVO }>()

defineEmits<{
  (e: 'inputTagPreview', value: unknown): void
  (e: 'interactionSubmit', payload: InteractionSubmitPayload): void
  (e: 'uipRetry', uipCode: string): void
  (e: 'vepRetry', vepCode: string): void
}>()

const isTerminal = (status?: string) => ['SUCCESS', 'BLOCKED', 'FAILED', 'CANCELLED'].includes(status ?? '')
const collapsed = ref(isTerminal(props.run.status))
const running = computed(() => !isTerminal(props.run.status))
const title = computed(() => props.run.agentTitle || props.run.agentCode || '子智能体')
const summary = computed(() => (props.run.summary || props.run.task || '已完成子智能体执行')
  .replace(/(^|\r?\n)session_id:\s*[^\r\n]+/ig, '').trim())
const statusText = computed(() => ({
  SUCCESS: '执行完成',
  BLOCKED: '需主智能体接管',
  FAILED: '执行失败',
  CANCELLED: '已取消',
}[props.run.status] || '运行中'))

watch(() => props.run.status, (next, previous) => {
  if (isTerminal(next) && !isTerminal(previous)) collapsed.value = true
})
</script>

<template>
  <section class="subagent-card" :class="{ 'is-running': running, 'is-failed': run.status === 'FAILED', 'is-blocked': run.status === 'BLOCKED' }">
    <button type="button" class="subagent-card-header" :aria-expanded="!collapsed" @click="collapsed = !collapsed">
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
        <span class="subagent-card-title">{{ running ? '正在执行' : '' }}智能体（{{ title }}）</span>
        <span class="subagent-card-subtitle">{{ running ? (run.task || '正在执行任务') : summary }}</span>
      </span>
      <DownOutlined class="subagent-card-arrow" :class="{ 'is-collapsed': collapsed }" />
    </button>
    <div v-show="!collapsed" class="subagent-card-details">
      <div v-if="run.task" class="subagent-card-task"><span>任务</span>{{ run.task }}</div>
      <SubAgentEventList
        :events="run.events"
        :active="running"
        @inputTagPreview="$emit('inputTagPreview', $event)"
        @interaction-submit="$emit('interactionSubmit', $event)"
        @uip-retry="$emit('uipRetry', $event)"
        @vep-retry="$emit('vepRetry', $event)"
      />
    </div>
  </section>
</template>

<style scoped lang="scss">
.subagent-card { margin: 12px 14px; overflow: hidden; border: 1px solid #e4e7ec; border-radius: 10px; background: #fff; }
.subagent-card.is-running { border-color: #91caff; }
.subagent-card.is-failed { border-color: #ffccc7; }
.subagent-card.is-blocked { border-color: #ffe58f; }
.subagent-card-header { display: flex; width: 100%; align-items: center; gap: 10px; padding: 11px 12px; border: 0; background: #fff; color: inherit; text-align: left; cursor: pointer; }
.subagent-card-header:hover { background: #fafafa; }
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
.card-avatar-loading { color: #1677ff; font-size: 21px; }
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
.is-failed .card-avatar-status { color: #cf1322; }
.is-blocked .card-avatar-status { color: #d48806; }
.subagent-card:not(.is-running) .card-avatar-status { cursor: help; }
.subagent-card-heading { display: flex; min-width: 0; flex: 1; flex-direction: column; gap: 2px; }
.subagent-card-title { overflow: hidden; color: #1d2939; font-size: 14px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.subagent-card-subtitle { overflow: hidden; color: #667085; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.subagent-card-arrow { flex: 0 0 auto; color: #98a2b3; transition: transform .2s ease; }
.subagent-card-arrow.is-collapsed { transform: rotate(-90deg); }
.subagent-card-details { border-top: 1px solid #f0f0f0; padding: 8px 10px 10px; background: #fcfcfd; }
.subagent-card-task { margin: 0 2px 8px; color: #475467; font-size: 13px; line-height: 1.55; white-space: pre-wrap; overflow-wrap: anywhere; }
.subagent-card-task span { margin-right: 8px; color: #98a2b3; font-size: 12px; }
</style>
