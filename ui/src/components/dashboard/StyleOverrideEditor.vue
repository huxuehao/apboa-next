<script setup lang="ts">
/**
 * 样式覆盖编辑器：按分组（卡片/标题栏/文字）动态渲染可配置项，遵循简约灰阶规范（禁渐变、阴影仅预设）。
 * 分组由面板描述符 styleGroups 决定，实现"哪些组件展示哪些样式"的可扩展标准。
 *
 * @author huxuehao
 */
import { computed } from 'vue'
import ColorAlphaInput from './ColorAlphaInput.vue'
import type { PanelStyleGroup } from '@/types/dashboard'

const props = withDefaults(
  defineProps<{
    styleValue: Record<string, string>
    /** 启用的样式分组，缺省 card + header */
    groups?: PanelStyleGroup[]
  }>(),
  { groups: () => ['card', 'header'] },
)
const emit = defineEmits<{ (e: 'update:styleValue', value: Record<string, string>): void }>()

const has = (g: PanelStyleGroup) => props.groups.includes(g)

const fontWeightOptions = [
  { label: '常规', value: 'normal' },
  { label: '中等', value: '500' },
  { label: '加粗', value: '600' },
]

const textAlignOptions = [
  { label: '左对齐', value: 'left' },
  { label: '居中', value: 'center' },
  { label: '右对齐', value: 'right' },
]

// 阴影预设：仅提供无/浅/中，避免任意值破坏简约规范
const SHADOW_PRESETS: Record<string, string> = {
  none: 'none',
  sm: '0 1px 4px rgba(0, 0, 0, 0.06)',
  md: '0 4px 12px rgba(0, 0, 0, 0.1)',
}
const shadowOptions = [
  { label: '无', value: 'none' },
  { label: '浅', value: 'sm' },
  { label: '中', value: 'md' },
]
const shadowLevel = computed(() => {
  const v = props.styleValue.boxShadow
  if (!v) return undefined
  return Object.keys(SHADOW_PRESETS).find((k) => SHADOW_PRESETS[k] === v)
})

function set(key: string, value: string | number | undefined) {
  const next = { ...props.styleValue }
  if (value === undefined || value === '' || value === null) {
    delete next[key]
  } else {
    next[key] = String(value)
  }
  emit('update:styleValue', next)
}

function setShadow(level: string | undefined) {
  set('boxShadow', level ? SHADOW_PRESETS[level] : undefined)
}

// 边框开关：关闭时写入 none，开启时清除（回落默认边框）
const borderOn = computed(() => props.styleValue.border !== 'none')
function toggleBorder(on: boolean) {
  set('border', on ? undefined : 'none')
}

// 标题栏分割线开关
const headerDividerOn = computed(() => props.styleValue.headerDivider !== 'none')
function toggleHeaderDivider(on: boolean) {
  set('headerDivider', on ? undefined : 'none')
}

// 内边距开关：关闭时 padding 为 0（写入 paddingOff 标记），开启时四边可调
const paddingOn = computed(() => props.styleValue.paddingOff !== 'true')
function togglePadding(on: boolean) {
  set('paddingOff', on ? undefined : 'true')
}
</script>

<template>
  <div class="style-editor">
    <!-- 卡片 -->
    <template v-if="has('card')">
      <div class="group-title">卡片</div>
      <div class="style-row">
        <span class="style-label">背景色</span>
        <ColorAlphaInput
          :value="styleValue.backgroundColor"
          default="#ffffff"
          @update:value="set('backgroundColor', $event)"
        />
      </div>
      <div class="style-row">
        <span class="style-label">边框</span>
        <a-switch :checked="borderOn" @update:checked="toggleBorder" />
      </div>
      <div v-if="borderOn" class="style-row">
        <span class="style-label">边框色</span>
        <ColorAlphaInput
          :value="styleValue.borderColor"
          default="#f0f0f0"
          @update:value="set('borderColor', $event)"
        />
      </div>
      <div class="style-row">
        <span class="style-label">圆角</span>
        <a-input-number
          :value="styleValue.borderRadius"
          :min="0"
          placeholder="8"
          style="width: 100%"
          @update:value="set('borderRadius', $event)"
        />
      </div>
      <div class="style-row">
        <span class="style-label">阴影</span>
        <a-select
          :value="shadowLevel"
          :options="shadowOptions"
          placeholder="无"
          allow-clear
          style="width: 100%"
          @update:value="setShadow"
        />
      </div>
      <div class="style-row">
        <span class="style-label">内边距</span>
        <a-switch :checked="paddingOn" @update:checked="togglePadding" />
      </div>
      <template v-if="paddingOn">
        <div class="style-row">
          <span class="style-label">上 / 下</span>
          <a-input-number
            :value="styleValue.padTop"
            :min="0"
            placeholder="12"
            style="width: 100%"
            @update:value="set('padTop', $event)"
          />
          <a-input-number
            :value="styleValue.padBottom"
            :min="0"
            placeholder="12"
            style="width: 100%"
            @update:value="set('padBottom', $event)"
          />
        </div>
        <div class="style-row">
          <span class="style-label">左 / 右</span>
          <a-input-number
            :value="styleValue.padLeft"
            :min="0"
            placeholder="14"
            style="width: 100%"
            @update:value="set('padLeft', $event)"
          />
          <a-input-number
            :value="styleValue.padRight"
            :min="0"
            placeholder="14"
            style="width: 100%"
            @update:value="set('padRight', $event)"
          />
        </div>
      </template>
    </template>

    <!-- 标题栏 -->
    <template v-if="has('header')">
      <div class="group-title">标题栏</div>
      <div class="style-row">
        <span class="style-label">背景色</span>
        <ColorAlphaInput
          :value="styleValue.headerBg"
          default="#ffffff"
          @update:value="set('headerBg', $event)"
        />
      </div>
      <div class="style-row">
        <span class="style-label">标题色</span>
        <ColorAlphaInput
          :value="styleValue.headerColor"
          default="#1a1a1a"
          @update:value="set('headerColor', $event)"
        />
      </div>
      <div class="style-row">
        <span class="style-label">分割线</span>
        <a-switch :checked="headerDividerOn" @update:checked="toggleHeaderDivider" />
      </div>
    </template>

    <!-- 文字（仅文字类面板启用） -->
    <template v-if="has('text')">
      <div class="group-title">文字</div>
      <div class="style-row">
        <span class="style-label">文字色</span>
        <ColorAlphaInput
          :value="styleValue.color"
          default="#1a1a1a"
          @update:value="set('color', $event)"
        />
      </div>
      <div class="style-row">
        <span class="style-label">字号</span>
        <a-input-number
          :value="styleValue.fontSize"
          :min="0"
          placeholder="14"
          style="width: 100%"
          @update:value="set('fontSize', $event)"
        />
      </div>
      <div class="style-row">
        <span class="style-label">字重</span>
        <a-select
          :value="styleValue.fontWeight"
          :options="fontWeightOptions"
          placeholder="默认"
          allow-clear
          style="width: 100%"
          @update:value="set('fontWeight', $event)"
        />
      </div>
      <div class="style-row">
        <span class="style-label">对齐</span>
        <a-select
          :value="styleValue.textAlign"
          :options="textAlignOptions"
          placeholder="默认"
          allow-clear
          style="width: 100%"
          @update:value="set('textAlign', $event)"
        />
      </div>
    </template>
  </div>
</template>

<style scoped lang="scss">
.style-editor {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.group-title {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
  font-size: 12px;
  font-weight: 600;
  color: #8c8c8c;
}

.group-title::before {
  content: '';
  width: 3px;
  height: 12px;
  border-radius: 1px;
  background: #bfbfbf;
}

.group-title:first-child {
  margin-top: 0;
}

.style-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.style-label {
  flex-shrink: 0;
  width: 66px;
  font-size: 13px;
  color: #595959;
}

.color-input {
  width: 48px;
  height: 28px;
  padding: 0;
  border: 1px solid #f0f0f0;
  border-radius: 4px;
  background: #fff;
  cursor: pointer;
}
</style>
