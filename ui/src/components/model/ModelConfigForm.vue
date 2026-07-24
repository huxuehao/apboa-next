/**
 * 模型配置表单组件
 *
 * @author huxuehao
 */
<script setup lang="ts">
import { ref, watch, computed, h } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { EyeOutlined, InfoCircleOutlined, ThunderboltOutlined } from '@ant-design/icons-vue'
import { matchOfficialPrice } from './officialModelPrices'
import type { ModelConfigVO, ModelConfig, ModelProviderVO } from '@/types'
import { ModelCategory, ModelType, ModelProviderType } from '@/types'
import * as modelApi from '@/api/model'
import ExtendConfigEditor, { type ExtendConfigData } from './ExtendConfigEditor.vue'
import TtsVoiceSelect from './TtsVoiceSelect.vue'
import ParamSlider from './ParamSlider.vue'
import ParamLabel from './ParamLabel.vue'
import { DEFAULT_TTS_VOICE } from '@/constants/ttsVoices'
import { MODEL_ICON_MAP, MODEL_ICON_GROUPS, DEFAULT_MODEL_ICON, DEFAULT_MODEL_ICON_COLOR, MODEL_ICON_COLORS, resolveModelIcon } from '@/constants/modelIcons'

/**
 * Props定义
 */
const props = defineProps<{
  visible: boolean
  providerId: string
  providerType?: string
  data?: ModelConfigVO
}>()

/**
 * Emits定义
 */
const emit = defineEmits<{
  'update:visible': [value: boolean]
  success: []
}>()

const formRef = ref()
const loading = ref<boolean>(false)

/** 图标选择器（策展分组见 modelIcons.ts MODEL_ICON_GROUPS，存组件名字符串 + hex 颜色） */
const logoPickerOpen = ref(false)
const logoSearchKeyword = ref('')
/** 搜索命中（限策展集，名称包含匹配大小写不敏感；空关键字走分组视图不用本值） */
const filteredLogoIcons = computed(() => {
  const kw = logoSearchKeyword.value.trim().toLowerCase()
  if (!kw) return []
  return MODEL_ICON_GROUPS
    .flatMap(g => g.icons)
    .filter(name => name.toLowerCase().includes(kw))
})
function handleLogoPick(iconName: string) {
  formData.value.logo = iconName
}
function handleLogoColorPick(color: string) {
  formData.value.logoColor = color
}

const formData = ref<{
  used?: string[]
  category: ModelCategory
  name: string
  modelId: string
  modelType: ModelType[]
  description: string
  logo: string
  logoColor: string
  streaming: boolean
  thinking: boolean
  contextWindow: number
  maxTokens: number
  temperature: number
  topP: number
  topK: number
  repeatPenalty: number
  seed: string
  extendConfig: ExtendConfigData | null
  ttsVoice: string
  inputPrice: number | null
  outputPrice: number | null
}>({
  category: ModelCategory.LLM,
  name: '',
  modelId: '',
  modelType: [ModelType.CHAT],
  description: '',
  logo: '',
  logoColor: '',
  streaming: true,
  thinking: false,
  contextWindow: 4096,
  maxTokens: 2048,
  temperature: 0.7,
  topP: 0.9,
  topK: 50,
  repeatPenalty: 1.0,
  seed: '',
  extendConfig: null,
  ttsVoice: DEFAULT_TTS_VOICE,
  inputPrice: null,
  outputPrice: null
})

const isEdit = computed(() => !!props.data?.id)

/** 是否语音识别用途（隐藏 LLM 专属区块与校验） */
const isAsr = computed(() => formData.value.category === ModelCategory.ASR)

/** 是否语音合成用途（同样无 LLM 参数，但保留扩展配置区块：音色 instruct/voice 靠 bodyParams 下发） */
const isTts = computed(() => formData.value.category === ModelCategory.TTS)

/** DASH_SCOPE 的 TTS：音色用结构化下拉直选（取代自由 KV），写入 extendConfig.bodyParams.voice */
const isDashScopeTts = computed(() => isTts.value && props.providerType === ModelProviderType.DASH_SCOPE)

/** OPEN_AI 的 TTS（本地 mlx 克隆）：音色从 TTS 服务 /voices 动态拉，存音色名到 bodyParams.voice */
const isOpenAiTts = computed(() => isTts.value && props.providerType === ModelProviderType.OPEN_AI)

/** 两类 TTS 都用音色下拉（LLM 不用） */
const isTtsVoiceDropdown = computed(() => isDashScopeTts.value || isOpenAiTts.value)

/** OPEN_AI TTS 拉 /voices 需要 TTS 服务地址（异步查提供商 baseUrl 传给音色下拉） */
const providerBaseUrl = ref('')
watch(
  [isOpenAiTts, () => props.providerId],
  async ([openai, pid]) => {
    if (!openai || !pid) {
      providerBaseUrl.value = ''
      return
    }
    try {
      const res = await modelApi.providerDetail(pid as string)
      providerBaseUrl.value = res.data.data.baseUrl || ''
    } catch {
      providerBaseUrl.value = ''
    }
  },
  { immediate: true }
)

/** 是否对话生成用途（LLM 专属参数区块的显隐依据） */
const isLlm = computed(() => formData.value.category === ModelCategory.LLM)

/**
 * 模型用途选项（单选，编辑时不可改：已被 agent 绑定的用途变更会造成引用错乱）
 */
const categoryOptions = [
  { label: '对话生成', value: ModelCategory.LLM },
  { label: '语音识别', value: ModelCategory.ASR },
  { label: '语音合成', value: ModelCategory.TTS }
]

/**
 * 用途切换时清掉另一形态的校验残留
 */
function handleCategoryChange() {
  formRef.value?.clearValidate()
}

/**
 * 模型类型选项（LLM 输入模态能力）
 */
const modelTypeOptions = [
  { label: '文本模型', value: ModelType.CHAT },
  { label: '图像模型', value: ModelType.IMAGE },
  { label: '音频模型', value: ModelType.AUDIO },
  { label: '视频模型', value: ModelType.VIDEO }
]

watch(
  () => props.visible,
  (newVal) => {
    if (newVal) {
      if (props.data) {
        const ec = props.data.extendConfig as ExtendConfigData | null
        formData.value = {
          used: props.data.used,
          category: props.data.category || ModelCategory.LLM,
          name: props.data.name,
          modelId: props.data.modelId,
          // ASR 模型的 modelType 为 null，回填成空数组避免 [null]
          modelType: Array.isArray(props.data.modelType) ? props.data.modelType : (props.data.modelType ? [props.data.modelType] : []),
          description: props.data.description,
          logo: props.data.logo || '',
          logoColor: props.data.logoColor || '',
          streaming: props.data.streaming,
          thinking: props.data.thinking,
          contextWindow: props.data.contextWindow,
          maxTokens: props.data.maxTokens,
          temperature: props.data.temperature,
          topP: props.data.topP,
          topK: props.data.topK,
          repeatPenalty: props.data.repeatPenalty,
          seed: props.data.seed || '',
          extendConfig: ec && typeof ec === 'object' ? { headers: ec.headers || {}, queryParams: ec.queryParams || {}, bodyParams: ec.bodyParams || {}, fixedSystemMessage: ec.fixedSystemMessage ?? false, thinkingParams: ec.thinkingParams } : null,
          // DASH_SCOPE TTS 的音色从 bodyParams.voice 回填到结构化下拉
          ttsVoice: (ec && typeof ec === 'object' && ec.bodyParams && typeof (ec.bodyParams as Record<string, unknown>).voice === 'string') ? String((ec.bodyParams as Record<string, unknown>).voice) : DEFAULT_TTS_VOICE,
          inputPrice: props.data.inputPrice ?? null,
          outputPrice: props.data.outputPrice ?? null
        }
      } else {
        resetForm()
      }
    }
  }
)

/**
 * 表单验证规则：基础字段两种用途通用，LLM 专属参数仅在对话生成用途下校验
 */
const baseRules = {
  name: [
    { required: true, message: '请输入名称', trigger: 'blur' },
    { max: 100, message: '名称长度不能超过100个字符', trigger: 'blur' }
  ],
  modelId: [
    { required: true, message: '请输入模型ID', trigger: 'blur' },
    { max: 100, message: '模型ID长度不能超过100个字符', trigger: 'blur' }
  ],
  description: [
    { required: true, message: '请输入模型描述', trigger: 'blur' },
    { max: 300, message: '描述长度不能超过300个字符', trigger: 'blur' }
  ]
}

const llmRules = {
  modelType: [
    { required: true, type: 'array', min: 1, message: '请至少选择一个模型类型', trigger: 'change' }
  ],
  contextWindow: [
    { required: true, message: '请输入上下文窗口大小', trigger: 'blur' }
  ],
  maxTokens: [
    { required: true, message: '请输入最大Token数', trigger: 'blur' }
  ],
  temperature: [
    { required: true, message: '请设置温度参数', trigger: 'blur' }
  ],
  topP: [
    { required: true, message: '请设置Top P参数', trigger: 'blur' }
  ],
  topK: [
    { required: true, message: '请设置Top K参数', trigger: 'blur' }
  ],
  repeatPenalty: [
    { required: true, message: '请设置重复惩罚参数', trigger: 'blur' }
  ]
}

const rules = computed(() => (isLlm.value ? { ...baseRules, ...llmRules } : baseRules))

/**
 * 重置表单
 */
function resetForm() {
  formData.value = {
    category: ModelCategory.LLM,
    name: '',
    modelId: '',
    modelType: [ModelType.CHAT],
    description: '',
    logo: '',
    logoColor: '',
    streaming: true,
    thinking: false,
    contextWindow: 4096,
    maxTokens: 2048,
    temperature: 0.7,
    topP: 0.9,
    topK: 50,
    repeatPenalty: 1.0,
    seed: '',
    extendConfig: null,
    ttsVoice: DEFAULT_TTS_VOICE,
    inputPrice: null,
    outputPrice: null
  }
  formRef.value?.resetFields()
}

/**
 * 按官网价填充成本计价：Ollama 本地模型直接填 0；其余按 modelId 前缀匹配
 * 内置官网价快照（快照有时效，填充后建议与官网核对）；未收录提示手动填写
 */
function fillOfficialPrice() {
  if (props.providerType === ModelProviderType.OLLAMA) {
    formData.value.inputPrice = 0
    formData.value.outputPrice = 0
    message.success('本地模型（Ollama）已填 0 元')
    return
  }
  const hit = matchOfficialPrice(formData.value.modelId)
  if (!hit) {
    message.info(`未收录「${formData.value.modelId || '（未填模型ID）'}」的官网价，请到供应商官网查询后手动填写`)
    return
  }
  formData.value.inputPrice = hit.input
  formData.value.outputPrice = hit.output
  message.success(`已按官网价快照填充：输入 ¥${hit.input} / 输出 ¥${hit.output}（每百万 token）——价格可能调整，建议与官网核对`)
}

/**
 * TTS 提交时的扩展配置：DASH_SCOPE 用音色下拉的值组装 bodyParams.voice；
 * 其余（OPEN_AI 本地等）沿用自由 KV 编辑器的 extendConfig。
 */
function buildTtsExtendConfig(): ExtendConfigData | undefined {
  if (isTtsVoiceDropdown.value) {
    const voice = formData.value.ttsVoice.trim()
    return voice ? { headers: {}, queryParams: {}, bodyParams: { voice } } : undefined
  }
  return formData.value.extendConfig || undefined
}

/**
 * 处理提交
 */
async function handleSubmit() {
  try {
    await formRef.value?.validate()
    loading.value = true

    // 语音识别/语音合成用途只保留基础信息（合成额外带扩展配置：音色 instruct/voice 走 bodyParams），
    // LLM 生成参数与模态类型一概不提交
    const entity: ModelConfig = (!isLlm.value
      ? {
          providerId: props.providerId,
          category: formData.value.category,
          name: formData.value.name,
          modelId: formData.value.modelId,
          modelType: null,
          description: formData.value.description,
          logo: formData.value.logo || null,
          logoColor: formData.value.logoColor || null,
          ...(isTts.value ? { extendConfig: buildTtsExtendConfig() } : {})
        }
      : {
          providerId: props.providerId,
          category: formData.value.category,
          name: formData.value.name,
          modelId: formData.value.modelId,
          modelType: formData.value.modelType,
          description: formData.value.description,
          logo: formData.value.logo || null,
          logoColor: formData.value.logoColor || null,
          streaming: formData.value.streaming,
          thinking: formData.value.thinking,
          contextWindow: formData.value.contextWindow,
          maxTokens: formData.value.maxTokens,
          temperature: formData.value.temperature,
          topP: formData.value.topP,
          topK: formData.value.topK,
          repeatPenalty: formData.value.repeatPenalty,
          seed: formData.value.seed,
          extendConfig: formData.value.extendConfig || undefined,
          inputPrice: formData.value.inputPrice,
          outputPrice: formData.value.outputPrice
        }) as ModelConfig

    if (isEdit.value && props.data) {
      entity.id = props.data.id as string
      await modelApi.configUpdate(entity)
      message.success('更新成功')
    } else {
      await modelApi.configSave(entity)
      message.success('创建成功')
    }

    emit('success')
    handleCancel()
  } catch (error) {
    console.error('提交失败:', error)
  } finally {
    loading.value = false
  }
}

/**
 * 处理取消
 */
function handleCancel() {
  emit('update:visible', false)
  resetForm()
}

/**
 * 获取供应商类型标签
 */
function getProviderTypeLabel(type: string): string {
  const map: Record<string, string> = {
    [ModelProviderType.DASH_SCOPE]: 'DashScope',
    [ModelProviderType.OPEN_AI]: 'OpenAI',
    [ModelProviderType.ANTHROPIC]: 'Anthropic',
    [ModelProviderType.GEMINI]: 'Gemini',
    [ModelProviderType.OLLAMA]: 'Ollama'
  }
  return map[type] || type
}

/**
 * 查看供应商详情
 */
async function handleViewProvider() {
  if (!props.providerId) {
    message.warning('供应商ID不存在')
    return
  }

  const response = await modelApi.providerDetail(props.providerId)
  const data: ModelProviderVO = response.data.data

  Modal.info({
    title: '供应商详情',
    closable: true,
    icon: null,
    footer: null,
    width: 600,
    content: h('div', { style: { maxHeight: '600px', overflowY: 'auto' } }, [
      h('p', {}, [h('strong', '供应商类型: '), getProviderTypeLabel(data.type)]),
      h('p', {}, [h('strong', '名称: '), data.name]),
      h('p', {}, [h('strong', '描述: '), data.description]),
      h('p', {}, [h('strong', 'Base URL: '), data.baseUrl]),
      h('p', {}, [h('strong', '认证类型: '), data.authType === 'CONFIG' ? '直接配置' : '环境变量']),
      ...(data.authType === 'CONFIG' ? [
        h('p', {}, [h('strong', 'API密钥: '), '********'])
      ] : [
        h('p', {}, [h('strong', '环境变量名: '), data.envVarName])
      ]),
      h('p', {}, [h('strong', '是否启用: '), data.enabled ? '是' : '否']),
      h('p', {}, [h('strong', '创建时间: '), data.createdAt]),
      h('p', {}, [h('strong', '更新时间: '), data.updatedAt])
    ])
  })
}

/**
 * 本地克隆 TTS 音色协议文档（i 按钮）：告诉接入方 /voices 接口契约 + 音色文件夹规范，
 * 免得私有协议只有开发者自己知道。
 */
function showVoiceProtocol() {
  const preStyle = {
    background: 'var(--color-bg-light)', padding: '8px 12px', borderRadius: '4px',
    whiteSpace: 'pre-wrap', margin: '4px 0 12px'
  }
  Modal.info({
    title: '本地克隆 TTS 音色协议（私有）',
    width: 680,
    okText: '知道了',
    content: h('div', { style: { maxHeight: '60vh', overflowY: 'auto', fontSize: '13px', lineHeight: '1.7' } }, [
      h('p', {}, [h('strong', 'TTS 服务实现以下两条约定，apboa-next 即可下拉选音色：')]),
      h('p', {}, [h('strong', '① 音色列表接口')]),
      h('pre', { style: preStyle }, 'GET  {baseUrl}/voices\n\n响应 JSON:\n[\n  {\n    "name": "音色名",\n    "refAudio": "参考音频绝对路径",\n    "refText": "参考音频的准确转写"\n  }\n]'),
      h('p', {}, [h('strong', '② 音色文件夹规范（TTS 服务扫它自己的目录）')]),
      h('pre', { style: preStyle }, '{音色名}/\n  audio.wav     参考音频\n  ref.txt       音频对应的准确转写（克隆用）\n  design.txt    可选，音色设计提示词（仅存档，不参与合成）'),
      h('p', {}, [h('strong', '说明')]),
      h('ul', { style: { paddingLeft: '20px', margin: '0' } }, [
        h('li', {}, 'refAudio 是「跑合成的 TTS 服务」本地能读到的绝对路径'),
        h('li', {}, 'apboa-next 库里只存音色名，合成时后端调 /voices 解析成 ref_audio + ref_text'),
        h('li', {}, 'lang_code 固定 Chinese；此协议只属于 TTS，与 ASR 无关')
      ])
    ])
  })
}
</script>

<template>
  <ApboaModal
    :open="visible"
    :title="isEdit ? '编辑模型配置' : '新增模型配置'"
    :confirm-loading="loading"
    destroyOnClose
    @ok="handleSubmit"
    @cancel="handleCancel"
  >
    <AForm ref="formRef" :model="formData" :rules="rules" layout="vertical">
      <div class="form-section">
        <AFormItem label="关联智能体" v-if="isEdit">
          <div class="code-wrapper ">
            {{ formData?.used?.join('、') || '无' }}
          </div>
        </AFormItem>
        <div class="section-title">基础信息</div>
        <AFormItem label="模型用途" name="category">
          <ASegmented
            v-model:value="formData.category"
            :options="categoryOptions"
            :disabled="isEdit"
            style="background-color: var(--color-bg)"
            @change="handleCategoryChange"
          />
          <div v-if="isEdit" class="text-placeholder text-xs mt-xs">
            用途创建后不可修改
          </div>
        </AFormItem>
        <AFormItem label="名称" name="name">
          <AInput v-model:value="formData.name" placeholder="请输入模型名称">
            <template #suffix>
              <EyeOutlined
                class="view-icon"
                title="查看供应商详情"
                @click="handleViewProvider"
              />
            </template>
          </AInput>
        </AFormItem>

        <AFormItem label="模型ID" name="modelId">
          <AInput v-model:value="formData.modelId" placeholder="请输入模型ID，如: gpt-4" />
        </AFormItem>

        <AFormItem v-if="isLlm" label="模型类型" name="modelType">
          <ASelect
            v-model:value="formData.modelType"
            mode="multiple"
            placeholder="请选择模型类型"
          >
            <ASelectOption v-for="opt in modelTypeOptions" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </ASelectOption>
          </ASelect>
        </AFormItem>

        <AFormItem label="描述" name="description">
          <ATextarea
            v-model:value="formData.description"
            placeholder="请输入模型描述"
            :rows="2"
          />
        </AFormItem>

        <AFormItem label="图标" name="logo">
          <APopover v-model:open="logoPickerOpen" trigger="click" placement="bottomLeft">
            <template #content>
              <AInput
                v-model:value="logoSearchKeyword"
                placeholder="搜索图标名（如 rocket、cloud）"
                allow-clear
                size="small"
                class="model-logo-search"
              />
              <div class="model-logo-picker-scroll">
                <!-- 搜索态：跨分组平铺命中项 -->
                <div v-if="logoSearchKeyword.trim()" class="model-logo-picker-grid">
                  <button
                    v-for="iconName in filteredLogoIcons"
                    :key="iconName"
                    type="button"
                    class="model-logo-picker-item"
                    :class="{ 'is-selected': (formData.logo || DEFAULT_MODEL_ICON) === iconName }"
                    :title="iconName"
                    :style="{ color: formData.logoColor || DEFAULT_MODEL_ICON_COLOR }"
                    @click="handleLogoPick(iconName)"
                  >
                    <component :is="MODEL_ICON_MAP[iconName]" />
                  </button>
                  <div v-if="!filteredLogoIcons.length" class="model-logo-picker-empty">无匹配图标</div>
                </div>
                <!-- 浏览态：策展分组 -->
                <template v-else>
                  <div v-for="g in MODEL_ICON_GROUPS" :key="g.label" class="model-logo-picker-group">
                    <div class="model-logo-picker-group-title">{{ g.label }}</div>
                    <div class="model-logo-picker-grid">
                      <button
                        v-for="iconName in g.icons"
                        :key="iconName"
                        type="button"
                        class="model-logo-picker-item"
                        :class="{ 'is-selected': (formData.logo || DEFAULT_MODEL_ICON) === iconName }"
                        :title="iconName"
                        :style="{ color: formData.logoColor || DEFAULT_MODEL_ICON_COLOR }"
                        @click="handleLogoPick(iconName)"
                      >
                        <component :is="MODEL_ICON_MAP[iconName]" />
                      </button>
                    </div>
                  </div>
                </template>
              </div>
              <div class="model-logo-color-row">
                <button
                  v-for="c in MODEL_ICON_COLORS"
                  :key="c"
                  type="button"
                  class="model-logo-color-item"
                  :class="{ 'is-selected': (formData.logoColor || DEFAULT_MODEL_ICON_COLOR) === c }"
                  :style="{ backgroundColor: c }"
                  :title="c"
                  @click="handleLogoColorPick(c)"
                />
              </div>
            </template>
            <AButton class="model-logo-trigger">
              <component :is="resolveModelIcon(formData.logo)" :style="{ color: formData.logoColor || DEFAULT_MODEL_ICON_COLOR }" />
              <span class="model-logo-trigger-text">点击更换图标与颜色（对话页模型下拉展示）</span>
            </AButton>
          </APopover>
        </AFormItem>
      </div>

      <div v-if="isLlm" class="form-section">
        <div class="section-title">功能开关</div>

        <ARow :gutter="24">
          <ACol :span="12">
            <AFormItem name="streaming">
              <template #label><ParamLabel param="streaming" /></template>
              <ASwitch v-model:checked="formData.streaming" />
            </AFormItem>
          </ACol>
          <ACol :span="12">
            <AFormItem name="thinking">
              <template #label><ParamLabel param="thinking" /></template>
              <ASwitch v-model:checked="formData.thinking" :disabled="providerType != 'DASH_SCOPE' && providerType != 'OPEN_AI'" />
              <span v-if="providerType != 'DASH_SCOPE' && providerType != 'OPEN_AI'" class="text-placeholder text-xs mt-xs">&nbsp;&nbsp;当前供应商暂不支持思考切换</span>
              <span v-else-if="providerType === 'OPEN_AI' && formData.thinking" class="text-placeholder text-xs mt-xs">&nbsp;&nbsp;在下方“扩展配置”的思考参数里设置开/关分别注入的请求体参数</span>
            </AFormItem>
          </ACol>
        </ARow>
      </div>

      <div v-if="isLlm" class="form-section">
        <div class="section-title">参数配置</div>

        <AFormItem name="contextWindow">
          <template #label><ParamLabel param="contextWindow" /></template>
          <ParamSlider v-model="formData.contextWindow" preset="token" />
        </AFormItem>

        <AFormItem name="maxTokens">
          <template #label><ParamLabel param="maxTokens" /></template>
          <ParamSlider v-model="formData.maxTokens" preset="token" />
        </AFormItem>

        <AFormItem name="temperature">
          <template #label><ParamLabel param="temperature" /></template>
          <ParamSlider v-model="formData.temperature" :min="0" :max="2" :step="0.1" :ticks="[0, 0.5, 1, 1.5, 2]" />
        </AFormItem>

        <AFormItem name="topP">
          <template #label><ParamLabel param="topP" /></template>
          <ParamSlider v-model="formData.topP" :min="0" :max="1" :step="0.05" :ticks="[0, 0.5, 0.9, 1]" />
        </AFormItem>

        <AFormItem name="topK">
          <template #label><ParamLabel param="topK" /></template>
          <ParamSlider v-model="formData.topK" :min="0" :max="100" :step="1" :ticks="[0, 20, 40, 60, 80, 100]" />
        </AFormItem>

        <AFormItem name="repeatPenalty">
          <template #label><ParamLabel param="repeatPenalty" /></template>
          <ParamSlider v-model="formData.repeatPenalty" :min="1" :max="2" :step="0.05" :ticks="[1, 1.2, 1.5, 2]" />
        </AFormItem>

        <AFormItem name="seed">
          <template #label><ParamLabel param="seed" /></template>
          <AInputNumber
            v-model:value="formData.seed"
            style="width: 100%"
            placeholder="请输入随机种子，留空表示随机" />
        </AFormItem>
      </div>

      <div v-if="isLlm" class="form-section">
        <div class="section-title" style="display: flex; align-items: center; justify-content: space-between;">
          <span>成本计价</span>
          <AButton type="text" size="small" @click="fillOfficialPrice">
            <ThunderboltOutlined /> 按官网价填充
          </AButton>
        </div>
        <ARow :gutter="24">
          <ACol :span="12">
            <AFormItem label="输入单价（元/百万 token）" name="inputPrice">
              <AInputNumber
                v-model:value="formData.inputPrice"
                style="width: 100%"
                :min="0"
                :precision="4"
                placeholder="留空=未配价"
              />
            </AFormItem>
          </ACol>
          <ACol :span="12">
            <AFormItem label="输出单价（元/百万 token）" name="outputPrice">
              <AInputNumber
                v-model:value="formData.outputPrice"
                style="width: 100%"
                :min="0"
                :precision="4"
                placeholder="留空=未配价"
              />
            </AFormItem>
          </ACol>
        </ARow>
        <div class="text-placeholder text-xs">
          供成本中心按人民币计算对话成本，可直接抄供应商官网报价；本地/免费模型填 0。
          留空=未配价：用量照记 token 但不计成本。改价只影响之后的新账单。
        </div>
      </div>

      <!-- DASH_SCOPE 语音合成：内置固定音色下拉 -->
      <div v-if="isDashScopeTts" class="form-section">
        <div class="section-title">音色</div>
        <AFormItem label="">
          <TtsVoiceSelect v-model="formData.ttsVoice" placeholder="请选择音色，默认 Cherry（芊悦·女）" />
          <div class="text-placeholder text-xs mt-xs">
            默认女声 Cherry；此为模型默认音色，可在智能体处按需覆盖。
          </div>
        </AFormItem>
      </div>

      <!-- OPEN_AI 本地克隆 TTS：从 TTS 服务 /voices 动态拉音色 + 协议文档按钮 -->
      <div v-if="isOpenAiTts" class="form-section">
        <div class="section-title" style="display: flex; align-items: center; justify-content: space-between;">
          <span>音色</span>
          <AButton type="text" size="small" @click="showVoiceProtocol">
            <InfoCircleOutlined /> 接入协议
          </AButton>
        </div>
        <AFormItem label="">
          <TtsVoiceSelect v-model="formData.ttsVoice" :remote-base-url="providerBaseUrl" placeholder="从 TTS 服务的音色库选择" />
          <div class="text-placeholder text-xs mt-xs">
            音色来自 TTS 服务的 /voices（本地克隆音色）；库里只存音色名，合成时后端解析成参考音频。点「接入协议」看规范。
          </div>
        </AFormItem>
      </div>

      <!-- LLM：通用扩展配置（自由 KV） -->
      <div v-if="isLlm" class="form-section">
        <div class="section-title">扩展配置</div>
        <AFormItem label="">
          <ExtendConfigEditor
            v-model="formData.extendConfig"
            :show-fixed-system-message="providerType === 'OPEN_AI' && isLlm"
            :show-thinking-params="providerType === 'OPEN_AI' && isLlm && formData.thinking"
          />
        </AFormItem>
      </div>
    </AForm>
  </ApboaModal>
</template>

<style scoped lang="scss">
.form-section {
  margin-bottom: var(--spacing-lg);

  .section-title {
    font-size: var(--font-size-base);
    font-weight: 600;
    color: var(--color-text-primary);
    margin-bottom: var(--spacing-md);
    padding-bottom: var(--spacing-xs);
    border-bottom: 1px solid var(--color-border-light);
  }
}

.view-icon {
  cursor: pointer;
  color: var(--color-primary);
  font-size: 16px;

  &:hover {
    color: var(--color-primary-hover);
  }
}

/* 模型图标选择器 */
.model-logo-trigger {
  display: inline-flex;
  align-items: center;
  gap: 8px;

  .model-logo-trigger-text {
    font-size: var(--font-size-sm);
    color: var(--color-text-secondary);
  }
}
</style>

<style lang="scss">
/* 图标网格挂 Popover（body 层），须全局样式 */
.model-logo-search {
  width: 100%;
  margin-bottom: 8px;
}

/* 滚动容器包住分组/搜索两种视图，网格自身不再滚动 */
.model-logo-picker-scroll {
  max-height: 320px;
  overflow-y: auto;
}

.model-logo-picker-group + .model-logo-picker-group {
  margin-top: 10px;
}

.model-logo-picker-group-title {
  font-size: 12px;
  color: var(--color-text-secondary);
  margin-bottom: 6px;
  position: sticky;
  top: 0;
  background: var(--color-bg-container, #fff);
  z-index: 1;
  padding: 2px 0;
}

.model-logo-picker-grid {
  display: grid;
  grid-template-columns: repeat(8, 36px);
  gap: 6px;

  .model-logo-picker-empty {
    grid-column: 1 / -1;
    padding: 16px 0;
    text-align: center;
    font-size: 12px;
    color: var(--color-text-placeholder);
  }

  .model-logo-picker-item {
    width: 36px;
    height: 36px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    font-size: 18px;
    border: 1px solid transparent;
    border-radius: var(--border-radius-md);
    background: transparent;
    color: var(--color-text-secondary);
    cursor: pointer;

    &:hover {
      color: var(--color-primary);
      background-color: var(--color-bg-light);
    }

    &.is-selected {
      border-color: var(--color-primary);
      background-color: var(--color-bg-light);
    }
  }
}

/* 图标颜色色板（图标网格下方一排） */
.model-logo-color-row {
  display: flex;
  gap: 8px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--color-border-light);

  .model-logo-color-item {
    width: 20px;
    height: 20px;
    border-radius: 50%;
    border: 2px solid transparent;
    cursor: pointer;
    padding: 0;

    &.is-selected {
      border-color: var(--color-text-primary);
      box-shadow: 0 0 0 2px #fff inset;
    }
  }
}
</style>
