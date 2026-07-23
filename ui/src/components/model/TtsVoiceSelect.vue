/**
 * TTS 音色选择器（DASH_SCOPE 固定列表 / OPEN_AI 本地克隆动态拉取 共用）
 *
 * 纯下拉选择（不可手输）。
 * - 不传 remoteBaseUrl：用内置 TTS_VOICES（DASH_SCOPE 云端固定音色），带「试听」链接跳阿里音色列表页。
 * - 传 remoteBaseUrl：从该 TTS 服务 {baseUrl}/voices 动态拉本地克隆音色（私有协议），失败下拉置空。
 * agent 覆盖场景传 emptyOptionLabel，首位加「跟随模型默认」项（内部 FOLLOW 哨兵绕 antd 空串）。
 *
 * @author vaulka
 */
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { SoundOutlined } from '@ant-design/icons-vue'
import { TTS_VOICES } from '@/constants/ttsVoices'
import * as ttsApi from '@/api/tts'

/** 阿里官方音色列表页（DASH_SCOPE 固定音色可试听） */
const VOICE_LIST_URL = 'https://help.aliyun.com/zh/model-studio/qwen-tts-voice-list'

/** 「跟随模型默认音色」哨兵；对外仍映射为空串 */
const FOLLOW = '__follow__'

const props = withDefaults(
  defineProps<{
    modelValue?: string | null
    placeholder?: string
    emptyOptionLabel?: string
    /** OPEN_AI 本地克隆：从 {baseUrl}/voices 动态拉；不传=DASH_SCOPE 内置固定列表 */
    remoteBaseUrl?: string
  }>(),
  { modelValue: '', placeholder: '请选择音色' }
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const isRemote = computed(() => !!props.remoteBaseUrl)

const remoteVoices = ref<{ value: string; label: string }[]>([])
const remoteLoading = ref(false)

/** remoteBaseUrl 变化就重新拉音色（每次打开即拉；失败置空，等价现状路径丢了报错） */
watch(
  () => props.remoteBaseUrl,
  async (url) => {
    remoteVoices.value = []
    if (!url) return
    remoteLoading.value = true
    try {
      const res = await ttsApi.voices(url)
      remoteVoices.value = (res.data.data || []).map((v) => ({
        value: v.name,
        label: v.refText ? `${v.name}（${v.refText.slice(0, 12)}）` : v.name
      }))
    } catch {
      remoteVoices.value = []
    } finally {
      remoteLoading.value = false
    }
  },
  { immediate: true }
)

const options = computed(() => {
  const base = isRemote.value
    ? remoteVoices.value
    : TTS_VOICES.map((v) => ({ value: v.value, label: `${v.name} · ${v.value} · ${v.gender}` }))
  return props.emptyOptionLabel ? [{ value: FOLLOW, label: props.emptyOptionLabel }, ...base] : base
})

/** 空串（不覆盖）映射到 FOLLOW 哨兵，避免 antd 把空串当未选中而不显示「跟随」项 */
const selectValue = computed(() => {
  const v = props.modelValue || ''
  return v === '' && props.emptyOptionLabel ? FOLLOW : v
})

function onChange(val: string) {
  emit('update:modelValue', val === FOLLOW ? '' : (val ?? ''))
}
</script>

<template>
  <div class="tts-voice-select">
    <ASelect
      :value="selectValue"
      :options="options"
      :placeholder="placeholder"
      :loading="remoteLoading"
      class="voice-select"
      @change="onChange"
    />
    <a
      v-if="!isRemote"
      :href="VOICE_LIST_URL"
      target="_blank"
      rel="noopener noreferrer"
      class="voice-preview-link"
      title="打开阿里官方音色列表，可试听各音色"
    >
      <SoundOutlined />
      <span>试听</span>
    </a>
  </div>
</template>

<style scoped lang="scss">
.tts-voice-select {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);

  .voice-select {
    flex: 1;
    min-width: 0;
  }

  .voice-preview-link {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    white-space: nowrap;
    color: var(--color-primary);

    &:hover {
      color: var(--color-primary-hover);
    }
  }
}
</style>
