/**
 * 本地知识库配置表单组件
 * 维护本地RAG知识库的连接与检索配置（文档管理入口由外层页面提供）
 *
 * @author huxuehao
 */
<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { message } from 'ant-design-vue'
import { type KnowledgeBaseConfig, type KnowledgeBaseConfigVO, KbType, RAGMode } from '@/types'
import * as knowledgeApi from '@/api/knowledge'

/**
 * Props定义
 */
const props = defineProps<{
  data?: KnowledgeBaseConfigVO
}>()

/**
 * Emits定义
 */
const emit = defineEmits<{
  success: []
  cancel: []
}>()

/**
 * 是否编辑模式
 */
const isEdit = computed(() => !!props.data?.id)

/**
 * 表单引用
 */
const formRef = ref()

/**
 * 表单数据
 */
const formData = reactive<Partial<KnowledgeBaseConfigVO>>({
  used: [],
  name: '',
  kbType: KbType.LOCAL,
  ragMode: RAGMode.AGENTIC,
  description: ''
})

/**
 * 当前选中的配置区域
 */
const activeConfigSection = ref<string>('connection')

/**
 * 配置区域选项
 */
const configSectionOptions = [
  { label: '* 连接配置', value: 'connection' },
  { label: '检索配置', value: 'retrieval' }
]

/**
 * 本地RAG连接配置
 */
const localConnection = reactive({
  providerType: 'ollama' as 'ollama' | 'bailian',
  baseUrl: 'http://localhost:11434/api/embed',
  apiKey: '',
  embeddingModel: 'qwen3-embedding:4b',
  dimension: 1024,
  bufferSizeMb: 50,
  batchSize: 10
})

/**
 * 本地RAG检索配置
 */
const localRetrieval = reactive({
  chunkSize: 512,
  chunkOverlap: 64,
  chunkDelimiters: '',
  topK: 5,
  scoreThreshold: 0.5
})

/**
 * 默认服务地址提示
 */
const defaultBaseUrlHint = computed(() => {
  if (localConnection.providerType === 'bailian') {
    return 'https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings'
  }
  return 'http://localhost:11434/api/embed'
})

/**
 * 默认嵌入模型提示
 */
const defaultModelHint = computed(() => {
  if (localConnection.providerType === 'bailian') {
    return 'text-embedding-v4'
  }
  return 'qwen3-embedding:4b'
})

/**
 * 重置服务地址为当前提供商的默认值
 */
function resetBaseUrl() {
  localConnection.baseUrl = defaultBaseUrlHint.value
}

/**
 * 重置嵌入模型为当前提供商的默认值
 */
function resetEmbeddingModel() {
  localConnection.embeddingModel = defaultModelHint.value
}

/**
 * 切换提供商时自动重置服务地址和模型为默认值
 */
const handleProviderTypeChange = () => {
  localConnection.baseUrl = defaultBaseUrlHint.value
  localConnection.embeddingModel = defaultModelHint.value
}

/**
 * 监听数据变化,初始化表单
 */
watch(() => props.data, () => {
  if (props.data) {
    initForm()
  }
}, { immediate: true })

/**
 * 初始化表单
 */
function initForm() {
  Object.assign(formData, props.data)
  loadConfigData()
  activeConfigSection.value = 'connection'
}

/**
 * 加载配置数据到各个配置对象
 */
function loadConfigData() {
  const { connectionConfig, retrievalConfig } = props.data!

  if (connectionConfig) {
    Object.assign(localConnection, {
      providerType: connectionConfig.providerType || 'ollama',
      baseUrl: connectionConfig.baseUrl || (connectionConfig.providerType === 'bailian' ? 'https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings' : 'http://localhost:11434/api/embed'),
      apiKey: connectionConfig.apiKey || '',
      embeddingModel: connectionConfig.embeddingModel || (connectionConfig.providerType === 'bailian' ? 'text-embedding-v4' : 'qwen3-embedding:4b'),
      dimension: connectionConfig.dimension || 1024,
      bufferSizeMb: connectionConfig.bufferSizeMb || 50,
      batchSize: connectionConfig.batchSize || 10
    })
  }
  if (retrievalConfig) {
    Object.assign(localRetrieval, retrievalConfig)
  }
}

/**
 * 构建提交数据
 */
function buildSubmitData(): KnowledgeBaseConfig {
  const data: Record<string, unknown> = {
    name: formData.name,
    kbType: formData.kbType,
    description: formData.description,
    ragMode: formData.ragMode
  }

  if (isEdit.value) {
    data.id = formData.id
  }

  data.connectionConfig = {
    providerType: localConnection.providerType,
    baseUrl: localConnection.baseUrl,
    apiKey: localConnection.providerType === 'bailian' ? (localConnection.apiKey || undefined) : undefined,
    embeddingModel: localConnection.embeddingModel,
    dimension: localConnection.dimension,
    bufferSizeMb: localConnection.bufferSizeMb,
    batchSize: localConnection.batchSize
  }
  data.retrievalConfig = { ...localRetrieval }
  data.endpointConfig = null
  data.rerankingConfig = null
  data.queryRewriteConfig = null
  data.metadataFilters = null
  data.httpConfig = null

  return data as unknown as KnowledgeBaseConfig
}

/**
 * 处理提交
 */
async function handleSubmit() {
  try {
    await formRef.value?.validate()

    const submitData = buildSubmitData()

    if (isEdit.value) {
      await knowledgeApi.update(submitData)
      message.success('编辑成功')
    } else {
      await knowledgeApi.save(submitData)
      message.success('新增成功')
    }

    emit('success')
  } catch (error) {
    console.error('表单验证失败:', error)
  }
}

/**
 * 处理取消
 */
function handleCancel() {
  emit('cancel')
}
</script>

<template>
  <h3 class="intro-title">{{ formData.name || '暂无名称' }}</h3>
  <div class="knowledge-form">
    <AForm
      ref="formRef"
      :model="formData"
      layout="vertical"
    >
      <AFormItem label="关联智能体" v-if="isEdit">
        <div class="code-wrapper">
          {{ formData?.used?.join('、') || '无' }}
        </div>
      </AFormItem>

      <AFormItem label="RAG模式" name="ragMode" :rules="[{ required: true, message: '请选择RAG模式' }]">
        <ASelect v-model:value="formData.ragMode" placeholder="请选择RAG模式">
          <ASelectOption value="GENERIC">Generic（在每个推理步骤之前自动检索和注入知识）</ASelectOption>
          <ASelectOption value="AGENTIC">Agentic（Agent 使用工具决定何时检索）</ASelectOption>
        </ASelect>
      </AFormItem>

      <AFormItem label="名称" name="name" :rules="[{ required: true, message: '请输入名称' }]">
        <AInput v-model:value="formData.name" placeholder="请输入知识库名称" />
      </AFormItem>

      <AFormItem label="描述" name="description" :rules="[
                 { required: true, message: '请输入描述', trigger: 'blur' },
                 { max: 200, message: '描述长度不能超过200个字符', trigger: 'blur' }]">
        <ATextarea
          v-model:value="formData.description"
          placeholder="请输入描述"
          :rows="3"
        />
      </AFormItem>

      <div class="config-section">
        <ASegmented
          v-model:value="activeConfigSection"
          :options="configSectionOptions"
          block
          style="margin-bottom: 16px; background-color: #F2F4F7"
        />

        <!-- 连接配置 -->
        <div v-show="activeConfigSection === 'connection'" class="config-content">
          <h4 style="margin-bottom: 12px">连接配置</h4>
          <AFormItem label="模型提供商" :rules="[{ required: true, message: '请选择模型提供商' }]">
            <ASelect v-model:value="localConnection.providerType" @change="handleProviderTypeChange" style="width: 100%">
              <ASelectOption value="ollama">Ollama</ASelectOption>
              <ASelectOption value="bailian">Bailian</ASelectOption>
            </ASelect>
            <div style="color: var(--color-text-secondary); font-size: 12px; margin-top: 4px;">
              仅支持 Bailian、Ollama
            </div>
          </AFormItem>

          <template v-if="localConnection.providerType === 'bailian'">
            <AFormItem label="API密钥（API Key）" :rules="[{ required: true, message: '请输入API Key' }]">
              <AInputPassword v-model:value="localConnection.apiKey" placeholder="请输入API Key，支持 ${ENV_VAR} 引用环境变量" />
            </AFormItem>
          </template>

          <AFormItem label="服务地址" :rules="[{ required: true, message: '请输入服务地址' }]">
            <div class="flex gap-sm" style="align-items: center">
              <AInput v-model:value="localConnection.baseUrl" style="flex: 1" />
              <AButton type="text" @click="resetBaseUrl">重置默认</AButton>
            </div>
            <div style="color: var(--color-text-secondary); font-size: 12px; margin-top: 4px;">
              当前提供商默认地址：{{ defaultBaseUrlHint }}
            </div>
          </AFormItem>

          <AFormItem label="嵌入模型" :rules="[{ required: true, message: '请输入嵌入模型名称' }]">
            <div class="flex gap-sm" style="align-items: center">
              <AInput v-model:value="localConnection.embeddingModel" style="flex: 1" />
              <AButton type="text" @click="resetEmbeddingModel">重置默认</AButton>
            </div>
            <div style="color: var(--color-text-secondary); font-size: 12px; margin-top: 4px;">
              当前提供商默认模型：{{ defaultModelHint }}
            </div>
          </AFormItem>

          <AFormItem label="向量化维度（仅可选择一次）" :rules="[{ required: true, message: '请选择向量化维度' }]">
            <ASelect v-model:value="localConnection.dimension" :disabled="isEdit" style="width: 100%">
              <ASelectOption :value="64">64</ASelectOption>
              <ASelectOption :value="128">128</ASelectOption>
              <ASelectOption :value="256">256</ASelectOption>
              <ASelectOption :value="512">512</ASelectOption>
              <ASelectOption :value="768">768</ASelectOption>
              <ASelectOption :value="1024">1024</ASelectOption>
              <ASelectOption :value="2048">2048</ASelectOption>
              <ASelectOption :value="2560">2560</ASelectOption>
            </ASelect>
            <div style="color: var(--color-text-secondary); font-size: 12px; margin-top: 4px;">
              默认1024，新增后不可修改。1024 维度是性能与成本的最佳平衡点，适用于绝大多数语义检索任务
            </div>
          </AFormItem>

          <AFormItem label="响应缓冲大小（单位MB）">
            <AInputNumber v-model:value="localConnection.bufferSizeMb" :min="1" :max="512" style="width: 100%" />
            <div style="color: var(--color-text-secondary); font-size: 12px; margin-top: 4px;">
              默认50MB，大数据量时可适当增大
            </div>
          </AFormItem>

          <AFormItem label="单批次最大文本数">
            <AInputNumber v-model:value="localConnection.batchSize" :min="1" :max="100" style="width: 100%" />
            <div style="color: var(--color-text-secondary); font-size: 12px; margin-top: 4px;">
              默认10，超过将自动拆分为多批次请求
            </div>
          </AFormItem>
        </div>

        <!-- 检索配置 -->
        <div v-show="activeConfigSection === 'retrieval'" class="config-content">
          <h4 style="margin-bottom: 12px">检索配置(可选)</h4>
          <AFormItem label="分块分隔符">
            <AInput v-model:value="localRetrieval.chunkDelimiters" placeholder="多个分隔符用逗号分隔，如：\n\n,^|,\n" />
            <div style="color: var(--color-text-secondary); font-size: 12px; margin-top: 4px;">
              支持转义字符：\n（换行）、\t（制表符）、\r（回车），多个分隔符用英文逗号分隔。不填则按字符数分块
            </div>
          </AFormItem>
          <AFormItem label="最大块长度(字符数)">
            <AInputNumber v-model:value="localRetrieval.chunkSize" :min="128" :max="8192" style="width: 100%" />
          </AFormItem>
          <AFormItem label="分块重叠(字符数)">
            <AInputNumber v-model:value="localRetrieval.chunkOverlap" :min="0" :max="1024" style="width: 100%" />
          </AFormItem>
          <AFormItem label="返回Top K（Top K）">
            <AInputNumber v-model:value="localRetrieval.topK" :min="1" :max="100" style="width: 100%" />
          </AFormItem>
          <AFormItem label="相似度阈值">
            <AInputNumber v-model:value="localRetrieval.scoreThreshold" :min="0" :max="1" :step="0.1" style="width: 100%" />
          </AFormItem>
        </div>
      </div>
    </AForm>
  </div>
  <div class="form-footer">
    <AButton v-if="!isEdit" @click="handleCancel">取消</AButton>
    <AButton type="primary" @click="handleSubmit">保存</AButton>
  </div>
</template>

<style scoped lang="scss">
.intro-title {
  font-size: var(--font-size-2xl);
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: var(--spacing-sm);
}

.knowledge-form {
  max-height: calc(100vh - 140px);
  overflow: auto;
  .config-section {
    .config-content {
      padding: 12px;
      background-color: #fcfcfc;
      border: 1px solid #eaeaea;
      border-radius: var(--border-radius-base);
      margin-bottom: 2px;

      h4 {
        font-weight: 600;
        color: var(--color-text-primary);
      }
    }
  }
}
.form-footer {
  display: flex;
  justify-content: center;
  gap: var(--spacing-sm);
  margin-top: var(--spacing-base);
}
</style>
