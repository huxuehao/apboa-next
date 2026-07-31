<script setup lang="ts">
/**
 * 支持透明度的取色器：原生色板 + 透明度滑块。
 * 输出：透明度 100% 时为 #rrggbb，否则为 rgba(r,g,b,a)。可解析 hex / rgba 回填。
 *
 * @author huxuehao
 */
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{ value?: string; default?: string }>(),
  { default: '#ffffff' },
)
const emit = defineEmits<{ (e: 'update:value', v: string): void }>()

/** 解析当前值为 { hex:#rrggbb, alpha:0-100 } */
const parsed = computed(() => parseColor(props.value || props.default))
const hex = computed(() => parsed.value.hex)
const alpha = computed(() => parsed.value.alpha)

function parseColor(input: string): { hex: string; alpha: number } {
  const v = (input || '').trim()
  const rgba = v.match(/^rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*(?:,\s*([\d.]+)\s*)?\)$/i)
  if (rgba) {
    const r = Number(rgba[1])
    const g = Number(rgba[2])
    const b = Number(rgba[3])
    const a = rgba[4] === undefined ? 1 : Number(rgba[4])
    return { hex: toHex(r, g, b), alpha: Math.round(a * 100) }
  }
  const h = v.match(/^#([0-9a-f]{6})([0-9a-f]{2})?$/i)
  if (h) {
    const alphaHex = h[2]
    return { hex: '#' + (h[1] as string), alpha: alphaHex ? Math.round((parseInt(alphaHex, 16) / 255) * 100) : 100 }
  }
  return { hex: props.default, alpha: 100 }
}

function toHex(r: number, g: number, b: number): string {
  const c = (n: number) => n.toString(16).padStart(2, '0')
  return `#${c(r)}${c(g)}${c(b)}`
}

function emitColor(nextHex: string, nextAlpha: number) {
  if (nextAlpha >= 100) {
    emit('update:value', nextHex)
    return
  }
  const r = parseInt(nextHex.slice(1, 3), 16)
  const g = parseInt(nextHex.slice(3, 5), 16)
  const b = parseInt(nextHex.slice(5, 7), 16)
  emit('update:value', `rgba(${r}, ${g}, ${b}, ${(nextAlpha / 100).toFixed(2)})`)
}

function onHex(e: Event) {
  emitColor((e.target as HTMLInputElement).value, alpha.value)
}
function onAlpha(v: number) {
  emitColor(hex.value, v)
}
</script>

<template>
  <div class="color-alpha">
    <input type="color" class="ca-swatch" :value="hex" @input="onHex" />
    <a-slider
      class="ca-slider"
      :value="alpha"
      :min="0"
      :max="100"
      :tip-formatter="(v: number) => v + '%'"
      @update:value="onAlpha"
    />
    <span class="ca-value">{{ alpha }}%</span>
  </div>
</template>

<style scoped lang="scss">
.color-alpha {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
}

.ca-swatch {
  flex-shrink: 0;
  width: 40px;
  height: 28px;
  padding: 0;
  border: 1px solid #f0f0f0;
  border-radius: 4px;
  background: #fff;
  cursor: pointer;
}

.ca-slider {
  flex: 1;
  margin: 0;
}

.ca-value {
  flex-shrink: 0;
  width: 34px;
  font-size: 12px;
  color: #8c8c8c;
  text-align: right;
}
</style>
