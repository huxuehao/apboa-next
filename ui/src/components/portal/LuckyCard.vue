<script setup lang="ts">
/**
 * 每日一签：点击翻牌抽取程序员版签文（宜/忌），按日期与面板稳定随机。
 * 两段式翻牌（签面翻出、签文翻入）+ 条目错落入场，均为用户触发的一次性动画。
 *
 * @author huxuehao
 */
import { computed, ref } from 'vue'
import dayjs from 'dayjs'

defineOptions({
  portalMeta: {
    name: '每日一签',
    description: '点击翻牌抽今日签文，程序员版宜忌，给一天一点仪式感',
  },
})

const props = withDefaults(
  defineProps<{
    panelContext?: { panelId: string; title: string; interactive: boolean }
  }>(),
  { panelContext: undefined },
)

const GOODS = [
  '写单测', '重构小函数', '给变量起个好名字', '删除死代码', '补文档',
  '早点下班', '给同事的 PR 点个赞', '备份重要数据', '清理浏览器标签页',
  '整理桌面', '喝够八杯水', '起身拉伸五分钟', '认真吃午饭', '提前规划明天',
  '给代码加注释',
]

const BADS = [
  '周五发版', '改生产配置', '手滑 force push', '不看文档就上手', '硬扛不求助',
  '会议开到饭点', '边吃饭边改 bug', '凌晨部署', '相信"就改一行"',
  '跳过代码评审', '在主分支直接提交', '忽略报警信息', '带着情绪写代码',
]

/** 翻牌阶段：cover 签面 -> out 翻出中 -> result 签文 */
const phase = ref<'cover' | 'out' | 'result'>('cover')
const drawOffset = ref(0)
/** 重抽时递增以重放签文入场动画 */
const revealKey = ref(0)

/** 简单字符串哈希（稳定随机种子） */
function hash(text: string): number {
  let h = 0
  for (let i = 0; i < text.length; i++) {
    h = (h * 31 + text.charCodeAt(i)) >>> 0
  }
  return h
}

const seed = computed(() => {
  const day = new Date().toISOString().slice(0, 10)
  return hash(`${day}:${props.panelContext?.panelId || 'lucky'}:${drawOffset.value}`)
})

/** 以种子从数组中取 n 个互不重复的条目 */
function pick(list: string[], n: number, base: number): string[] {
  const result: string[] = []
  const used = new Set<number>()
  let cursor = base
  while (result.length < n && used.size < list.length) {
    cursor = (cursor * 1103515245 + 12345) >>> 0
    const idx = cursor % list.length
    if (!used.has(idx)) {
      used.add(idx)
      const item = list[idx]
      if (item !== undefined) result.push(item)
    }
  }
  return result
}

const goods = computed(() => pick(GOODS, 2, seed.value))
const bads = computed(() => pick(BADS, 2, seed.value + 7))

const todayText = dayjs().format('YYYY年MM月DD日')

function flip() {
  if (phase.value === 'cover') phase.value = 'out'
}

/** 签面翻出动画结束后切入签文 */
function onCoverGone() {
  phase.value = 'result'
  revealKey.value += 1
}

function redraw() {
  drawOffset.value += 1
  phase.value = 'result'
  revealKey.value += 1
}

defineExpose({
  panelActions: [{ key: 'redraw', label: '再抽一签', icon: 'RedoOutlined', run: redraw }],
})
</script>

<template>
  <div class="lucky">
    <button
      v-if="phase !== 'result'"
      class="lk-cover"
      :class="{ out: phase === 'out' }"
      @click="flip"
      @animationend="onCoverGone"
    >
      <span class="lk-frame">
        <span class="lk-cover-title">今日一签</span>
        <span class="lk-cover-sub">点击翻牌</span>
      </span>
    </button>
    <div v-else :key="revealKey" class="lk-result">
      <div class="lk-cols">
        <div class="lk-col">
          <span class="lk-tag good lk-in" style="animation-delay: 0.05s">宜</span>
          <div class="lk-items">
            <div
              v-for="(g, i) in goods"
              :key="g"
              class="lk-item lk-in"
              :style="{ animationDelay: 0.15 + i * 0.12 + 's' }"
            >
              <span class="lk-bullet good" />{{ g }}
            </div>
          </div>
        </div>
        <div class="lk-divider" />
        <div class="lk-col">
          <span class="lk-tag bad lk-in" style="animation-delay: 0.05s">忌</span>
          <div class="lk-items">
            <div
              v-for="(b, i) in bads"
              :key="b"
              class="lk-item lk-in"
              :style="{ animationDelay: 0.15 + i * 0.12 + 's' }"
            >
              <span class="lk-bullet bad" />{{ b }}
            </div>
          </div>
        </div>
      </div>
      <div class="lk-date lk-in" style="animation-delay: 0.55s">{{ todayText }} · 今日运势已锁定</div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.lucky {
  display: flex;
  align-items: stretch;
  justify-content: center;
  height: 100%;
  perspective: 700px;
}

.lk-cover {
  flex: 1;
  display: flex;
  padding: 10px;
  border: none;
  border-radius: 10px;
  background: #262a33;
  cursor: pointer;
}

/* 签面内框：细线描边营造签牌质感（纯色） */
.lk-frame {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  border: 1px solid #454a55;
  border-radius: 6px;
}

.lk-cover-title {
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 8px;
  text-indent: 8px;
  color: #f5f6f7;
}

.lk-cover-sub {
  font-size: 12px;
  letter-spacing: 2px;
  color: #8c8c8c;
}

/* 签面翻出：用户点击触发的一次性动画 */
.lk-cover.out {
  animation: lk-out 0.25s ease-in forwards;
  pointer-events: none;
}

@keyframes lk-out {
  from {
    transform: rotateY(0deg);
    opacity: 1;
  }
  to {
    transform: rotateY(88deg);
    opacity: 0.4;
  }
}

/* 签文翻入 */
.lk-result {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
  padding: 4px;
  animation: lk-reveal 0.35s ease-out;
}

@keyframes lk-reveal {
  from {
    transform: rotateY(-88deg);
    opacity: 0;
  }
  to {
    transform: rotateY(0deg);
    opacity: 1;
  }
}

.lk-cols {
  display: flex;
  align-items: stretch;
  gap: 14px;
}

.lk-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

/* 条目错落入场：一次性上移淡入，fill both 保证延迟前不可见 */
.lk-in {
  animation: lk-item-in 0.35s ease-out both;
}

@keyframes lk-item-in {
  from {
    transform: translateY(10px);
    opacity: 0;
  }
  to {
    transform: translateY(0);
    opacity: 1;
  }
}

.lk-tag {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  font-size: 16px;
  font-weight: 700;
  color: #fff;
}

.lk-tag.good {
  background: #389e0d;
}

.lk-tag.bad {
  background: #cf1322;
}

.lk-items {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.lk-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #434343;
}

.lk-bullet {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  flex-shrink: 0;
}

.lk-bullet.good {
  background: #389e0d;
}

.lk-bullet.bad {
  background: #cf1322;
}

.lk-divider {
  width: 1px;
  background: #f0f0f0;
}

.lk-date {
  text-align: center;
  font-size: 11px;
  color: #bfbfbf;
}
</style>
