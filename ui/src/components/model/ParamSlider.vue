/**
 * 参数滑块（统一：线性刻度 + 档位对数吸附两种模式）
 *
 * - 线性模式（默认）：值直接映射滑块位置，ticks 数组自动生成刻度。
 *   用于温度/TopP/TopK/重复惩罚等小范围参数。
 * - 档位模式（preset="token" 或传 steps）：跨度大的值（如 4K~1M）按等比档位分布，
 *   滑块内部值域为档位索引（线性），拖动吸附档位真实值；右侧输入框可填任意精确值，
 *   非档位值仅影响滑块显示位置（吸附最近档展示），不会被改写。
 *
 * @author vaulka
 */
<script setup lang="ts">
import { computed } from 'vue'

/** token 预设档位：4K~1M 二进制幂九档 */
const TOKEN_STEPS = [4096, 8192, 16384, 32768, 65536, 131072, 262144, 524288, 1048576]

const props = withDefaults(defineProps<{
  /** 档位模式：token 预设（4K~1M + K/M 标签） */
  preset?: 'token'
  /** 档位模式：自定义档位真实值（升序）；与 preset 二选一 */
  steps?: number[]
  /** 线性模式：范围与步长 */
  min?: number
  max?: number
  step?: number
  /** 线性模式：刻度点（值数组），自动转成 marks */
  ticks?: number[]
  /** 标签/提示格式化（默认原样；token 预设自带 K/M） */
  format?: (v: number) => string
  /** 输入框最小值（档位模式默认 1） */
  inputMin?: number
}>(), {
  min: 0,
  max: 100,
  step: 1
})

const modelValue = defineModel<number>({ default: 0 })

/** 实际档位数组（preset=token 用内置档位，否则用 steps） */
const stepValues = computed<number[]>(() =>
  props.preset === 'token' ? TOKEN_STEPS : (props.steps ?? [])
)

/** 是否档位模式 */
const stepMode = computed(() => stepValues.value.length > 0)

/** token 的 K/M 格式化 */
function formatToken(v: number): string {
  if (v >= 1024 * 1024) return `${v / (1024 * 1024)}M`
  if (v >= 1024) return `${v / 1024}K`
  return String(v)
}

/** 标签/提示格式化：优先 props.format，token 预设默认 K/M，其余原样 */
const fmt = computed<(v: number) => string>(() => {
  if (props.format) return props.format
  if (props.preset === 'token') return formatToken
  return (v: number) => String(v)
})

/** 线性刻度：ticks → { 值: 标签 } */
const linearMarks = computed<Record<number, string>>(() => {
  const m: Record<number, string> = {}
  ;(props.ticks ?? []).forEach((v) => { m[v] = fmt.value(v) })
  return m
})

/** 档位刻度：索引 → 标签 */
const stepMarks = computed<Record<number, string>>(() => {
  const m: Record<number, string> = {}
  stepValues.value.forEach((v, i) => { m[i] = fmt.value(v) })
  return m
})

/** 当前值 → 档位索引（对数最近档；手填非档位值只影响显示位置，不改写真实值） */
const sliderIndex = computed<number>({
  get: () => {
    const steps = stepValues.value
    if (steps.length === 0) return 0
    const val = modelValue.value && modelValue.value > 0 ? modelValue.value : steps[0] as number
    let best = 0
    let bestDist = Infinity
    steps.forEach((s, i) => {
      const dist = Math.abs(Math.log2(val) - Math.log2(s))
      if (dist < bestDist) { bestDist = dist; best = i }
    })
    return best
  },
  set: (i: number) => {
    const steps = stepValues.value
    modelValue.value = (steps[i] ?? steps[0]) as number
  }
})

/** 档位模式输入框上限取最高档 */
const stepInputMax = computed(() => stepValues.value[stepValues.value.length - 1])
</script>

<template>
  <ARow :gutter="16">
    <ACol :span="16">
      <!-- 档位模式：滑块走索引，对数吸附 -->
      <ASlider
        v-if="stepMode"
        v-model:value="sliderIndex"
        :min="0"
        :max="stepValues.length - 1"
        :step="1"
        :marks="stepMarks"
        :tip-formatter="(i: number) => fmt(stepValues[i] ?? 0)"
        class="param-slider-marks"
      />
      <!-- 线性模式：滑块直接绑值 -->
      <ASlider
        v-else
        v-model:value="modelValue"
        :min="min"
        :max="max"
        :step="step"
        :marks="linearMarks"
        :tip-formatter="(v: number) => fmt(v)"
        class="param-slider-marks"
      />
    </ACol>
    <ACol :span="8">
      <AInputNumber
        v-model:value="modelValue"
        :min="stepMode ? (inputMin ?? 1) : min"
        :max="stepMode ? stepInputMax : max"
        :step="stepMode ? undefined : step"
        style="width: 100%"
      />
    </ACol>
  </ARow>
</template>

<style scoped lang="scss">
.param-slider-marks {
  /* 刻度标签偏挤时压小字号避免相邻叠字（档位模式九档尤其需要） */
  :deep(.ant-slider-mark-text) {
    font-size: 11px;
    white-space: nowrap;
  }
}
</style>
