/**
 * 智能体模型与提示词表单组件
 *
 * @author huxuehao
 */
<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { RoutePaths } from '@/router/constants.ts'
import SmartCodeEditor from '@/components/editor/SmartCodeEditor.vue'
import TtsVoiceSelect from '@/components/model/TtsVoiceSelect.vue'
import ModelParamsOverrideForm from '@/components/model/ModelParamsOverrideForm.vue'
import * as modelApi from '@/api/model'
import * as promptApi from '@/api/prompt'
import type { ModelProviderVO, ModelConfigVO, SystemPromptTemplateVO } from '@/types'
import { ModelCategory } from '@/types'
/**
 * Props定义
 */
const props = defineProps<{
  modelValue: {
    modelConfigId: string
    /** 额外候选对话模型（不含默认；对话页用户可切换） */
    models: string[]
    /** 各候选模型的参数覆盖（key=候选 id；空对象/缺省=跟随模型默认。默认模型的覆盖走 modelParamsOverride） */
    modelsParamsOverride: Record<string, Record<string, unknown> | null>
    asrModelConfigId: string | null
    ttsModelConfigId: string | null
    modelParamsOverride: Record<string, unknown> | null
    ttsParamsOverride: Record<string, unknown> | null
    systemPromptTemplateId: string
    followTemplate: boolean
    systemPrompt: string
  }
}>()

/**
 * Emits定义
 */
const emit = defineEmits<{
  'update:modelValue': [value: typeof props.modelValue]
}>()

const formRef = ref()
const loading = ref(false)

const modelProviders = ref<ModelProviderVO[]>([])
const allModels = ref<ModelConfigVO[]>([])
const promptCategories = ref<string[]>([])
const allPrompts = ref<SystemPromptTemplateVO[]>([])

const selectedProviderId = ref<string>('')
const selectedPromptCategory = ref<string>('')

/**
 * 表单数据
 */
const formData = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

/** 对话模型（历史写法是排除 ASR，新增 TTS 用途后改为白名单，避免语音模型混入对话下拉） */
const llmModels = computed(() => allModels.value.filter(m => m.category === ModelCategory.LLM))

/** 语音识别模型 */
const asrModels = computed(() => allModels.value.filter(m => m.category === ModelCategory.ASR))

/** 语音合成模型 */
const ttsModels = computed(() => allModels.value.filter(m => m.category === ModelCategory.TTS))

/**
 * 模型提供商选项（仅显示有对话模型的供应商；模型列表未返回前先显示全部避免闪空态）
 */
const providerOptions = computed(() => {
  const providers = allModels.value.length === 0
    ? modelProviders.value
    : modelProviders.value.filter(p => llmModels.value.some(m => String(m.providerId) === String(p.id)))
  return providers.map(p => ({
    label: p.name,
    value: p.id
  }))
})

/**
 * 当前提供商的对话模型列表
 */
const currentModels = computed(() => {
  if (!selectedProviderId.value) {
    const model = llmModels.value.filter(p => p.id === formData.value.modelConfigId)[0] || null
    if (model) {
      selectedProviderId.value = model.providerId
    } else {
      selectedProviderId.value = llmModels.value[0]?.providerId || ''
    }
    return []
  }
  return llmModels.value.filter(m => m.providerId === selectedProviderId.value)
})

/**
 * 语音识别供应商选项：「不启用」+ 有 ASR 模型的供应商（交互与上方对话模型一致）
 */
const asrProviderOptions = computed(() => {
  const opts = modelProviders.value
    .filter(p => asrModels.value.some(m => String(m.providerId) === String(p.id)))
    .map(p => ({ label: p.name, value: String(p.id) }))
  return [{ label: '不启用', value: '' }, ...opts]
})

/** 语音识别选中的供应商（''=不启用，null=待初始化） */
const asrSelectedProviderKey = ref<string | null>(null)

/**
 * 回显定位：模型列表与绑定值任一到达都重算——已绑定则选中绑定模型所在供应商分组
 * （列表与父组件回填的先后不定，找到绑定即覆盖定位；未绑定仅做一次「不启用」初始化，
 * 不能放在 currentAsrModels 里懒初始化：模板中它被 v-if 挡住，未初始化时永远不会被求值）
 */
watch(
  [asrModels, () => formData.value.asrModelConfigId],
  ([models, boundId]) => {
    const bound = models.find(m => String(m.id) === String(boundId ?? ''))
    if (bound) {
      asrSelectedProviderKey.value = String(bound.providerId)
    } else if (asrSelectedProviderKey.value === null && allModels.value.length > 0) {
      asrSelectedProviderKey.value = ''
    }
  },
  { immediate: true }
)

/**
 * 当前供应商的语音识别模型列表
 */
const currentAsrModels = computed(() => {
  if (!asrSelectedProviderKey.value) return []
  return asrModels.value.filter(m => String(m.providerId) === asrSelectedProviderKey.value)
})

/**
 * 语音识别供应商切换：选「不启用」时清空绑定
 */
function handleAsrProviderChange(value: string | number) {
  if (value === '') {
    formData.value.asrModelConfigId = null
  }
}

/**
 * 语音合成供应商选项：「不启用」+ 有 TTS 模型的供应商（交互与语音识别一致）
 */
const ttsProviderOptions = computed(() => {
  const opts = modelProviders.value
    .filter(p => ttsModels.value.some(m => String(m.providerId) === String(p.id)))
    .map(p => ({ label: p.name, value: String(p.id) }))
  return [{ label: '不启用', value: '' }, ...opts]
})

/** 语音合成选中的供应商（''=不启用，null=待初始化） */
const ttsSelectedProviderKey = ref<string | null>(null)

/**
 * 回显定位（同 ASR：列表与绑定值任一到达都重算，未绑定仅做一次「不启用」初始化）
 */
watch(
  [ttsModels, () => formData.value.ttsModelConfigId],
  ([models, boundId]) => {
    const bound = models.find(m => String(m.id) === String(boundId ?? ''))
    if (bound) {
      ttsSelectedProviderKey.value = String(bound.providerId)
    } else if (ttsSelectedProviderKey.value === null && allModels.value.length > 0) {
      ttsSelectedProviderKey.value = ''
    }
  },
  { immediate: true }
)

/**
 * 当前供应商的语音合成模型列表
 */
const currentTtsModels = computed(() => {
  if (!ttsSelectedProviderKey.value) return []
  return ttsModels.value.filter(m => String(m.providerId) === ttsSelectedProviderKey.value)
})

/**
 * 语音合成供应商切换：选「不启用」时清空绑定
 */
function handleTtsProviderChange(value: string | number) {
  if (value === '') {
    formData.value.ttsModelConfigId = null
  }
}

/** 选中 TTS 模型的提供商（据此显隐音色覆盖下拉，并区分固定列表 vs 动态拉取） */
const selectedTtsProvider = computed(() => {
  const id = formData.value.ttsModelConfigId
  if (!id) return null
  const model = ttsModels.value.find(m => String(m.id) === String(id))
  if (!model) return null
  return modelProviders.value.find(p => String(p.id) === String(model.providerId)) || null
})
const selectedTtsIsDashScope = computed(() => selectedTtsProvider.value?.type === 'DASH_SCOPE')
const selectedTtsIsOpenAi = computed(() => selectedTtsProvider.value?.type === 'OPEN_AI')
const selectedTtsBaseUrl = computed(() => selectedTtsProvider.value?.baseUrl || '')

/**
 * agent 级音色覆盖（写入 ttsParamsOverride.voice，扁平结构）。
 * 空串=不覆盖（跟随模型默认音色），存 {}；否则存 { voice }。
 */
const ttsVoiceOverride = computed<string>({
  get: () => {
    const ov = formData.value.ttsParamsOverride
    return ov && typeof ov.voice === 'string' ? ov.voice : ''
  },
  set: (v: string) => {
    const voice = (v || '').trim()
    formData.value = { ...formData.value, ttsParamsOverride: voice ? { voice } : {} }
  }
})

/**
 * 提示词分类选项
 */
const promptCategoryOptions = computed(() => {
  return promptCategories.value.map(c => ({
    label: c,
    value: c
  }))
})

/**
 * 当前分类的提示词列表
 */
const currentPrompts = computed(() => {
  if (!selectedPromptCategory.value) {
    const prompts = allPrompts.value.filter(p => p.id === formData.value.systemPromptTemplateId)[0] || null
    if (prompts) {
      selectedPromptCategory.value = prompts.category
    } else {
      selectedPromptCategory.value = allPrompts.value[0]?.category || ''
    }
    return []
  }

  return allPrompts.value.filter(p => p.category === selectedPromptCategory.value)
})

/**
 * 表单验证规则
 */
const rules = {
  modelConfigId: [
    { required: true, message: '请选择对话生成模型', trigger: 'blur' }
  ],
  systemPromptTemplateId: [
    { required: true, message: '请选择系统提示词模板', trigger: 'blur' }
  ]
}

/**
 * 加载模型提供商
 */
async function loadModelProviders() {
  try {
    loading.value = true
    const response = await modelApi.providerPage({ page: 1, size: 100, enabled: true })
    modelProviders.value = response.data.data.records || []
  } finally {
    loading.value = false
  }
}

/**
 * 加载所有模型
 */
async function loadAllModels() {
  const response = await modelApi.configPage({ page: 1, size: 1000, enabled: true })
  allModels.value = response.data.data.records || []
}

/**
 * 加载提示词分类
 */
async function loadPromptCategories() {
  const response = await promptApi.listCategories()
  promptCategories.value = response.data.data || []
}

/**
 * 加载所有提示词
 */
async function loadAllPrompts() {
  const response = await promptApi.page({ page: 1, size: 1000, enabled: true })
  allPrompts.value = response.data.data.records || []
}

/**
 * 处理模型选择
 */
function handleModelChange(modelId: string) {
  formData.value.modelConfigId = modelId

  // 新默认若在额外候选里则移除（默认模型天然是候选，避免关联表重复存）；
  // 参数覆盖的重灌由 ModelParamsOverrideForm 监听 modelId 变化自理
  if (formData.value.models?.some(id => String(id) === String(modelId))) {
    formData.value.models = formData.value.models.filter(id => String(id) !== String(modelId))
  }
}

/** 目标模型的供应商类型（组件内 OPEN_AI 才显示固定系统消息项） */
function providerTypeOf(modelId: string | null | undefined): string | null {
  if (!modelId) return null
  const model = allModels.value.find(m => String(m.id) === String(modelId))
  if (!model) return null
  const provider = modelProviders.value.find(p => String(p.id) === String(model.providerId))
  return provider?.type ?? null
}

/** 候选模型显示名（参数覆盖块标题用） */
function modelNameOf(modelId: string): string {
  return allModels.value.find(m => String(m.id) === String(modelId))?.name || modelId
}

// 候选取消勾选时清掉它的参数覆盖（孤儿键不落库）
watch(() => formData.value.models, (ids) => {
  const map = formData.value.modelsParamsOverride
  if (!map) return
  const keep = new Set((ids || []).map(String))
  Object.keys(map).forEach(k => {
    if (!keep.has(k)) {
      delete map[k]
    }
  })
})

/** 额外候选模型的分组选项（按供应商分组；默认模型置灰防重复勾选） */
const candidateModelGroups = computed(() =>
  modelProviders.value
    .map(p => ({
      label: p.name,
      models: llmModels.value.filter(m => String(m.providerId) === String(p.id))
    }))
    .filter(g => g.models.length > 0)
)

/**
 * 处理提示词模板选择
 */
async function handlePromptChange(promptId: string) {
  formData.value.systemPromptTemplateId = promptId

  if (!formData.value.followTemplate) {
    const response = await promptApi.detail(promptId)
    formData.value.systemPrompt = response.data.data.content
  }
}

/**
 * 处理随模板变化开关
 */
function handleFollowTemplateToggle(checked: boolean) {
  formData.value.followTemplate = checked
  if (!checked && formData.value.systemPromptTemplateId) {
    handlePromptChange(formData.value.systemPromptTemplateId)
  }
}

/**
 * 验证表单
 */
async function validate(): Promise<boolean> {
  try {
    await formRef.value?.validate()
    return true
  } catch {
    return false
  }
}

onMounted(() => {
  loadModelProviders()
  loadAllModels()
  loadPromptCategories()
  loadAllPrompts()

  if (formData.value.modelConfigId) {
    const model = allModels.value.find(m => m.id === formData.value.modelConfigId)
    if (model) {
      selectedProviderId.value = model.providerId
    }
  }

  if (formData.value.systemPromptTemplateId) {
    const prompt = allPrompts.value.find(p => p.id === formData.value.systemPromptTemplateId)
    if (prompt) {
      selectedPromptCategory.value = prompt.category
    }
  }

})

defineExpose({
  validate
})
</script>

<template>
  <ApboaSpin :spinning="loading">
    <AForm ref="formRef" :model="formData" :rules="rules" layout="vertical">
      <AFormItem label="对话生成模型" name="modelConfigId" required>
        <template v-if="providerOptions?.length > 0">
          <div class="mb-md">
            <ASegmented
              v-model:value="selectedProviderId"
              :options="providerOptions"
              style="margin-bottom: 12px; background-color: var(--color-bg)"
            />
          </div>
          <ARadioGroup v-model:value="formData.modelConfigId" style="width: 100%">
            <div class="model-grid" v-if="currentModels?.length > 0">
              <ARadio
                v-for="model in currentModels"
                :key="model.id"
                :value="model.id"
                class="model-radio"
                @change="handleModelChange(model.id as string)"
              >
                <div class="model-info">
                  <div class="model-name">{{ model.name }}</div>
                  <div class="model-desc text-placeholder text-xs">{{ model.description }}</div>
                </div>
              </ARadio>
            </div>
            <div v-else class="text-placeholder mt-xs">
              <AButton type="text">未配置模型？</AButton>
              <AButton type="link" :href="`/#/${RoutePaths.MODEL}`" target="_blank">去配置</AButton>
              <AButton type="link" @click="loadModelProviders();loadAllModels()">刷新</AButton>
            </div>
          </ARadioGroup>
        </template>
        <template v-else>
          <div class="text-placeholder mt-xs">
            <AButton type="text">未添加模型提供商？</AButton>
            <AButton type="link" :href="`/#/${RoutePaths.MODEL}`" target="_blank">去添加</AButton>
            <AButton type="link" @click="loadModelProviders();loadAllModels()">刷新</AButton>
          </div>
        </template>
      </AFormItem>

      <AFormItem name="models" v-if="llmModels.length > 1">
        <template #label>
          额外候选模型
          <span class="text-placeholder text-xs" style="margin-left: 8px;">对话页可切换；默认模型始终是候选，无需重复勾选</span>
        </template>
        <ASelect
          v-model:value="formData.models"
          mode="multiple"
          placeholder="不选 = 仅默认模型，对话页不出现切换入口"
          allow-clear
          style="width: 100%"
        >
          <ASelectOptGroup v-for="g in candidateModelGroups" :key="g.label" :label="g.label">
            <ASelectOption
              v-for="m in g.models"
              :key="m.id"
              :value="m.id"
              :disabled="String(m.id) === String(formData.modelConfigId)"
            >
              {{ m.name }}
            </ASelectOption>
          </ASelectOptGroup>
        </ASelect>
      </AFormItem>

      <ModelParamsOverrideForm
        v-if="formData.modelConfigId"
        v-model="formData.modelParamsOverride"
        :model-id="formData.modelConfigId"
        :provider-type="providerTypeOf(formData.modelConfigId)"
        label="覆盖模型参数"
      />

      <template v-if="formData.models?.length">
        <ModelParamsOverrideForm
          v-for="mid in formData.models"
          :key="mid"
          v-model="formData.modelsParamsOverride[mid]"
          :model-id="mid"
          :provider-type="providerTypeOf(mid)"
          :label="`覆盖参数 · ${modelNameOf(mid)}`"
        />
      </template>

      <AFormItem label="语音识别模型">
        <div class="mb-md">
          <ASegmented
            v-model:value="asrSelectedProviderKey"
            :options="asrProviderOptions"
            style="margin-bottom: 12px; background-color: var(--color-bg)"
            @change="handleAsrProviderChange"
          />
        </div>
        <ARadioGroup v-if="asrSelectedProviderKey" v-model:value="formData.asrModelConfigId" style="width: 100%">
          <div class="model-grid" v-if="currentAsrModels?.length > 0">
            <ARadio
              v-for="model in currentAsrModels"
              :key="model.id"
              :value="model.id"
              class="model-radio"
            >
              <div class="model-info">
                <div class="model-name">{{ model.name }}</div>
                <div class="model-desc text-placeholder text-xs">{{ model.description }}</div>
              </div>
            </ARadio>
          </div>
          <div v-else class="text-placeholder mt-xs">该供应商下暂无语音识别模型</div>
        </ARadioGroup>
        <div v-else class="text-placeholder text-xs mt-xs">
          不启用语音输入；在模型配置中创建「语音识别」用途的模型后可在此绑定
        </div>
      </AFormItem>

      <AFormItem label="语音合成模型">
        <div class="mb-md">
          <ASegmented
            v-model:value="ttsSelectedProviderKey"
            :options="ttsProviderOptions"
            style="margin-bottom: 12px; background-color: var(--color-bg)"
            @change="handleTtsProviderChange"
          />
        </div>
        <ARadioGroup v-if="ttsSelectedProviderKey" v-model:value="formData.ttsModelConfigId" style="width: 100%">
          <div class="model-grid" v-if="currentTtsModels?.length > 0">
            <ARadio
              v-for="model in currentTtsModels"
              :key="model.id"
              :value="model.id"
              class="model-radio"
            >
              <div class="model-info">
                <div class="model-name">{{ model.name }}</div>
                <div class="model-desc text-placeholder text-xs">{{ model.description }}</div>
              </div>
            </ARadio>
          </div>
          <div v-else class="text-placeholder mt-xs">该供应商下暂无语音合成模型</div>
        </ARadioGroup>
        <div v-else class="text-placeholder text-xs mt-xs">
          不启用语音播报；在模型配置中创建「语音合成」用途的模型后可在此绑定
        </div>
      </AFormItem>

      <AFormItem v-if="selectedTtsIsDashScope || selectedTtsIsOpenAi" label="音色">
        <ARow :gutter="16">
          <ACol :span="8">
            <TtsVoiceSelect
              v-model="ttsVoiceOverride"
              empty-option-label="跟随模型默认音色"
              :remote-base-url="selectedTtsIsOpenAi ? selectedTtsBaseUrl : undefined"
            />
          </ACol>
        </ARow>
        <div class="text-placeholder text-xs mt-xs">
          仅覆盖该智能体的播报音色；选「跟随模型默认音色」则用所选语音合成模型的默认音色。
        </div>
      </AFormItem>

      <AFormItem label="系统提示词模板" name="systemPromptTemplateId" required>
        <template v-if="promptCategoryOptions?.length > 0">
          <div class="mb-md">
            <ASegmented
              v-model:value="selectedPromptCategory"
              :options="promptCategoryOptions"
              style="margin-bottom: 12px; background-color: var(--color-bg)"
            />
          </div>
          <ARadioGroup v-model:value="formData.systemPromptTemplateId" style="width: 100%">
            <div class="prompt-grid" v-if="currentPrompts?.length > 0">
              <ARadio
                v-for="prompt in currentPrompts"
                :key="prompt.id"
                :value="prompt.id"
                class="prompt-radio"
                @change="handlePromptChange(prompt.id as string)"
              >
                <div class="prompt-info">
                  <div class="prompt-name">{{ prompt.name }}</div>
                  <div class="prompt-desc text-placeholder text-xs">{{ prompt.description }}</div>
                </div>
              </ARadio>
            </div>
            <div v-else class="text-placeholder mt-xs">
              <AButton type="text">没有有效的提示模板？</AButton>
              <AButton type="link" :href="`/#/${RoutePaths.PROMPT}`" target="_blank">去设置</AButton>
              <AButton type="link" @click="loadPromptCategories();loadAllPrompts()">刷新</AButton>
            </div>
          </ARadioGroup>
        </template>
        <template v-else>
          <div class="text-placeholder mt-xs">
            <AButton type="text">未添系统提示词模板？</AButton>
            <AButton type="link" :href="`/#/${RoutePaths.PROMPT}`" target="_blank">去添加</AButton>
            <AButton type="link" @click="loadPromptCategories();loadAllPrompts()">刷新</AButton>
          </div>
        </template>
      </AFormItem>

      <AFormItem label="随模板变化" v-if="formData.systemPromptTemplateId">
        <ASwitch
          v-model:checked="formData.followTemplate"
          @change="handleFollowTemplateToggle"
        />
        <div class="text-placeholder text-xs mt-xs">
          开启后,提示词内容将随模板更新而自动更新
        </div>
      </AFormItem>

      <AFormItem v-if="formData.systemPromptTemplateId && !formData.followTemplate" label="系统提示词">
        <SmartCodeEditor
          v-model="formData.systemPrompt"
          v-if="!formData.followTemplate"
          language="markdown"
          height="350px"
        />
      </AFormItem>
    </AForm>
  </ApboaSpin>
</template>

<style scoped lang="scss">
.model-grid,
.prompt-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--spacing-sm);
}

.model-radio,
.prompt-radio {
  padding: var(--spacing-sm);
  border: 1px solid var(--color-border-base);
  border-radius: var(--border-radius-md);
  margin: 0 !important;
  width: 100%;
  transition: all var(--transition-base);

  &:hover {
    border-color: var(--color-primary);
    background-color: var(--color-bg-light);
  }
}

.model-info,
.prompt-info {
  .model-name,
  .prompt-name {
    font-weight: 500;
    margin-bottom: 4px;
  }

  .model-desc,
  .prompt-desc {
    line-height: 1.4;
  }
}

</style>
