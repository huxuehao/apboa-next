<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { CheckCircleFilled, DownOutlined, LoadingOutlined, RobotOutlined, StopFilled, WarningFilled } from '@ant-design/icons-vue'
import SubAgentEventList from './SubAgentEventList.vue'
import type { SubAgentRunVO } from '@/types'

const props = defineProps<{ run: SubAgentRunVO }>()

const isTerminal = (status?: string) => ['SUCCESS', 'FAILED', 'CANCELLED'].includes(status ?? '')
const collapsed = ref(isTerminal(props.run.status))
const running = computed(() => !isTerminal(props.run.status))
const title = computed(() => props.run.agentTitle || props.run.agentCode || '子智能体')
const summary = computed(() => props.run.summary || props.run.task || '已完成子智能体执行')
const statusText = computed(() => ({ SUCCESS: '执行完成', FAILED: '执行失败', CANCELLED: '已取消' }[props.run.status] || '运行中'))

watch(() => props.run.status, (next, previous) => {
  if (isTerminal(next) && !isTerminal(previous)) collapsed.value = true
})
</script>

<template>
  <section class="subagent-card" :class="{ 'is-running': running, 'is-failed': run.status === 'FAILED' }">
    <button type="button" class="subagent-card-header" :aria-expanded="!collapsed" @click="collapsed = !collapsed">
      <span class="subagent-card-icon"><RobotOutlined /></span>
      <span class="subagent-card-heading">
        <span class="subagent-card-title">{{ title }}</span>
        <span class="subagent-card-subtitle">{{ running ? (run.task || '正在执行任务') : summary }}</span>
      </span>
      <span class="subagent-card-state">
        <LoadingOutlined v-if="running" spin />
        <WarningFilled v-else-if="run.status === 'FAILED'" />
        <StopFilled v-else-if="run.status === 'CANCELLED'" />
        <CheckCircleFilled v-else />
        <span>{{ statusText }}</span>
      </span>
      <DownOutlined class="subagent-card-arrow" :class="{ 'is-collapsed': collapsed }" />
    </button>
    <div v-show="!collapsed" class="subagent-card-details">
      <div v-if="run.task" class="subagent-card-task"><span>任务</span>{{ run.task }}</div>
      <SubAgentEventList :events="run.events" :active="running" />
    </div>
  </section>
</template>

<style scoped lang="scss">
.subagent-card { margin: 12px 0; overflow: hidden; border: 1px solid #d9e6f7; border-radius: 12px; background: #fff; box-shadow: 0 3px 12px rgba(15, 74, 145, .06); }
.subagent-card.is-running { border-color: #91caff; box-shadow: 0 3px 14px rgba(22, 119, 255, .12); }
.subagent-card.is-failed { border-color: #ffccc7; }
.subagent-card-header { display: flex; width: 100%; align-items: center; gap: 10px; padding: 12px 14px; border: 0; background: linear-gradient(90deg, #f8fbff, #fff); color: inherit; text-align: left; cursor: pointer; }
.subagent-card-header:hover { background: #f3f8ff; }
.subagent-card-icon { display: inline-flex; align-items: center; justify-content: center; width: 30px; height: 30px; border-radius: 8px; background: #e6f4ff; color: #1677ff; font-size: 16px; }
.subagent-card-heading { display: flex; min-width: 0; flex: 1; flex-direction: column; gap: 2px; }
.subagent-card-title { overflow: hidden; color: #1d2939; font-size: 14px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.subagent-card-subtitle { overflow: hidden; color: #667085; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.subagent-card-state { display: inline-flex; flex: 0 0 auto; align-items: center; gap: 5px; color: #1677ff; font-size: 12px; }
.is-failed .subagent-card-state { color: #cf1322; }
.subagent-card-arrow { flex: 0 0 auto; color: #98a2b3; transition: transform .2s ease; }
.subagent-card-arrow.is-collapsed { transform: rotate(-90deg); }
.subagent-card-details { border-top: 1px solid #edf2f7; padding: 8px 10px 10px; }
.subagent-card-task { margin: 0 2px 8px; color: #475467; font-size: 13px; line-height: 1.55; white-space: pre-wrap; overflow-wrap: anywhere; }
.subagent-card-task span { margin-right: 8px; color: #98a2b3; font-size: 12px; }
</style>
