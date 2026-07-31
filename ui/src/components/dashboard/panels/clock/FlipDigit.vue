<script setup lang="ts">
/**
 * 翻牌数字（机械 split-flap）：数字变化时，上半片(旧)绕中缝下翻、下半片(新)绕中缝上翻，
 * 中间有铰接缝线。为一次性短动画，非循环。
 *
 * @author huxuehao
 */
import { computed, ref, watch } from 'vue'

const props = defineProps<{ value: string }>()

const shown = ref(props.value)
const prev = ref(props.value)
const flipping = ref(false)

// 上半静态始终显示新值(被翻落的上片揭开后露出)；下半静态在翻牌中显示旧值，翻完显示新值
const lowerNum = computed(() => (flipping.value ? prev.value : shown.value))

watch(
  () => props.value,
  (nv) => {
    prev.value = shown.value
    shown.value = nv
    flipping.value = false
    requestAnimationFrame(() => {
      flipping.value = true
    })
  },
)
</script>

<template>
  <span class="flap">
    <!-- 静态上半：新值上半 -->
    <span class="half upper"><span class="num">{{ shown }}</span></span>
    <!-- 静态下半：翻牌中为旧值，翻完为新值 -->
    <span class="half lower"><span class="num">{{ lowerNum }}</span></span>
    <template v-if="flipping">
      <!-- 翻落的上片：旧值上半，绕中缝向下 -->
      <span class="half flip-top"><span class="num">{{ prev }}</span></span>
      <!-- 翻起的下片：新值下半，绕中缝向上 -->
      <span class="half flip-bottom" @animationend="flipping = false">
        <span class="num">{{ shown }}</span>
      </span>
    </template>
  </span>
</template>

<style scoped lang="scss">
$h: 50px;
$half: 25px;
// 双色折面：上片略亮、下片略暗，形成立体感（纯色，无渐变）
$seam: #16171a;
$upper-bg: #33363c;
$lower-bg: #282a2f;

.flap {
  position: relative;
  display: inline-block;
  width: 36px;
  height: $h;
  background: $seam;
  border-radius: 8px;
  font-family: 'JetBrains Mono', Menlo, Consolas, monospace;
  font-size: 34px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: #f5f6f7;
}

/* 中缝铰接线：始终位于最上层，分隔上下两片 */
.flap::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  top: calc(50% - 1px);
  height: 2px;
  background: $seam;
  z-index: 3;
}

.half {
  position: absolute;
  left: 0;
  right: 0;
  height: $half;
  overflow: hidden;
  backface-visibility: hidden;
}

.upper,
.flip-top {
  top: 0;
  border-radius: 8px 8px 0 0;
  background: $upper-bg;
  transform-origin: center bottom;
}

.lower,
.flip-bottom {
  bottom: 0;
  border-radius: 0 0 8px 8px;
  background: $lower-bg;
  transform-origin: center top;
}

.num {
  position: absolute;
  left: 0;
  right: 0;
  height: $h;
  line-height: $h;
  text-align: center;
}

/* 上半显示字形上半；下半将字形整体上移半格，显示下半 */
.upper .num,
.flip-top .num {
  top: 0;
}

.lower .num,
.flip-bottom .num {
  top: -$half;
}

.flip-top {
  z-index: 2;
  animation: flap-top 0.16s ease-in forwards;
}

.flip-bottom {
  z-index: 2;
  animation: flap-bottom 0.16s ease-out 0.16s both;
}

@keyframes flap-top {
  from {
    transform: rotateX(0deg);
  }
  to {
    transform: rotateX(-90deg);
  }
}

@keyframes flap-bottom {
  from {
    transform: rotateX(90deg);
  }
  to {
    transform: rotateX(0deg);
  }
}
</style>
