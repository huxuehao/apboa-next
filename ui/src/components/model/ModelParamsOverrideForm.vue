/**
 * 模型参数覆盖表单（开关 + 滑块组 + 扩展配置，自治组件）
 *
 * v-model 语义：空对象/空 = 未启用覆盖（跟随模型自身默认）；非空对象 = 覆盖参数。
 * 关闭开关写回 {} 而非 null——agent 保存走 updateById 忽略 null 字段，
 * null 清不掉旧覆盖（沿用改造前 AgentFormModel 的既有约定）。
 * 开启开关时拉取目标模型默认值灌底；默认模型与各候选模型共用（per-model 参数覆盖）。
 */
<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import ExtendConfigEditor, { type ExtendConfigData } from '@/components/model/ExtendConfigEditor.vue'
import ParamSlider from '@/components/model/ParamSlider.vue'
import ParamLabel from '@/components/model/ParamLabel.vue'
import * as modelApi from '@/api/model'

/** 覆盖参数表单结构（滑块控件需要明确的 number 类型） */
interface ModelParamsForm {
  streaming?: boolean
  temperature?: number
  topP?: number
  topK?: number
  maxTokens?: number
  repeatPenalty?: number
  seed?: number | string | null
  extendConfig?: ExtendConfigData | null
  [key: string]: unknown
}

const props = defineProps<{
  modelValue?: Record<string, unknown> | null
  /** 覆盖目标模型 id（开启时拉它的默认值灌底） */
  modelId: string | null
  /** 目标模型的供应商类型（OPEN_AI 才显示固定系统消息项） */
  providerType?: string | null
  /** 开关行标题 */
  label?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, unknown>]
}>()

const enabled = ref(false)
const paramsForm = ref<ModelParamsForm>({})

/** 最近一次自身写出的快照：区分自身回声与外部重置，防回灌循环 */
let lastEmitted: string | null = null

function emitValue(value: Record<string, unknown>) {
  lastEmitted = JSON.stringify(value)
  emit('update:modelValue', value)
}

/** 规整 extendConfig 结构后灌入表单 */
function fillFromValue(value: Record<string, unknown>) {
  const ec = value.extendConfig as ExtendConfigData | null | undefined
  paramsForm.value = {
    ...value,
    extendConfig: ec && typeof ec === 'object'
      ? { headers: ec.headers || {}, queryParams: ec.queryParams || {}, bodyParams: ec.bodyParams || {}, fixedSystemMessage: ec.fixedSystemMessage ?? false }
      : null
  }
}

onMounted(() => {
  if (props.modelValue && Object.keys(props.modelValue).length > 0) {
    enabled.value = true
    fillFromValue(props.modelValue)
  }
})

// 外部重置（父组件回显/清空）：只处理 空↔非空 转换；值内容变化多为自身 emit 回声，按快照跳过
watch(() => props.modelValue, (val) => {
  const snapshot = val == null ? '{}' : JSON.stringify(val)
  if (snapshot === lastEmitted) return
  const hasVal = !!val && Object.keys(val).length > 0
  if (hasVal && !enabled.value) {
    enabled.value = true
    fillFromValue(val!)
  } else if (!hasVal && enabled.value) {
    enabled.value = false
    paramsForm.value = {}
  }
})

/** 拉取目标模型默认值灌底 */
async function loadModelDefaults(modelId: string) {
  const response = await modelApi.configDetail(modelId)
  const model = response.data.data
  const ec = model.extendConfig as ExtendConfigData | null | undefined
  paramsForm.value = {
    streaming: model.streaming,
    temperature: model.temperature,
    topP: model.topP,
    topK: model.topK,
    maxTokens: model.maxTokens,
    repeatPenalty: model.repeatPenalty,
    seed: model.seed,
    extendConfig: ec && typeof ec === 'object'
      ? { headers: ec.headers || {}, queryParams: ec.queryParams || {}, bodyParams: ec.bodyParams || {}, fixedSystemMessage: ec.fixedSystemMessage ?? false }
      : null
  }
  emitValue({ ...paramsForm.value })
}

async function handleToggle(checked: boolean) {
  enabled.value = checked
  if (checked && props.modelId) {
    await loadModelDefaults(props.modelId)
  } else {
    paramsForm.value = {}
    emitValue({})
  }
}

// 覆盖开启中换了目标模型（如切换默认模型）：重新按新模型默认灌底（沿用改造前行为）
watch(() => props.modelId, (id) => {
  if (enabled.value && id) {
    loadModelDefaults(id)
  }
})

// 表单编辑同步写出
watch(paramsForm, (newVal) => {
  if (enabled.value) {
    emitValue({ ...newVal })
  }
}, { deep: true })

/** 扩展配置（用于 v-model 绑定） */
const extendConfigForm = computed({
  get: () => (paramsForm.value.extendConfig as ExtendConfigData) || null,
  set: (v: ExtendConfigData | null) => {
    paramsForm.value = { ...paramsForm.value, extendConfig: v }
  }
})

/** 是否显示固定系统消息配置（仅 OpenAI 供应商） */
const showFixedSystemMessage = computed(() => props.providerType === 'OPEN_AI')
</script>

<template>
  <div class="model-params-override">
    <AFormItem :label="label || '覆盖模型参数'">
      <ASwitch :checked="enabled" @change="handleToggle" />
    </AFormItem>

    <div v-if="enabled" class="params-override-section">
      <ARow :gutter="16">
        <ACol :span="8">
          <AFormItem>
            <template #label><ParamLabel param="temperature" /></template>
            <ParamSlider v-model="paramsForm.temperature" :min="0" :max="2" :step="0.1" :ticks="[0, 0.5, 1, 1.5, 2]" />
          </AFormItem>
        </ACol>
        <ACol :span="8">
          <AFormItem>
            <template #label><ParamLabel param="topP" /></template>
            <ParamSlider v-model="paramsForm.topP" :min="0" :max="1" :step="0.05" :ticks="[0, 0.5, 0.9, 1]" />
          </AFormItem>
        </ACol>
        <ACol :span="8">
          <AFormItem>
            <template #label><ParamLabel param="repeatPenalty" /></template>
            <ParamSlider v-model="paramsForm.repeatPenalty" :min="1" :max="2" :step="0.05" :ticks="[1, 1.2, 1.5, 2]" />
          </AFormItem>
        </ACol>
        <ACol :span="8">
          <AFormItem>
            <template #label><ParamLabel param="maxTokens" /></template>
            <ParamSlider v-model="paramsForm.maxTokens" preset="token" />
          </AFormItem>
        </ACol>
        <ACol :span="8">
          <AFormItem>
            <template #label><ParamLabel param="topK" /></template>
            <ParamSlider v-model="paramsForm.topK" :min="0" :max="100" :step="1" :ticks="[0, 20, 40, 60, 80, 100]" />
          </AFormItem>
        </ACol>
        <ACol :span="8">
          <AFormItem name="seed">
            <template #label><ParamLabel param="seed" /></template>
            <AInputNumber
              v-model:value="paramsForm.seed"
              style="width: 100%"
              placeholder="请输入随机种子，留空表示随机" />
          </AFormItem>
        </ACol>
        <ACol :span="8">
          <AFormItem>
            <template #label><ParamLabel param="streaming" /></template>
            <ASwitch v-model:checked="paramsForm.streaming" />
          </AFormItem>
        </ACol>
      </ARow>
      <div class="extend-config-wrapper">
        <AFormItem label="扩展配置">
          <ExtendConfigEditor
            v-model="extendConfigForm"
            compact
            :show-fixed-system-message="showFixedSystemMessage"
          />
        </AFormItem>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.params-override-section {
  padding: var(--spacing-md);
  background-color: var(--color-bg-light);
  border-radius: var(--border-radius-md);
  margin-bottom: var(--spacing-md);

  .extend-config-wrapper {
    margin-top: var(--spacing-md);
    padding-top: var(--spacing-md);
    border-top: 1px solid var(--color-border-light);
  }
}
</style>
