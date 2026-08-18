/**
 * RagFlow知识库配置表单组件
 * 维护RagFlow知识库的连接、检索与HTTP配置
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
  kbType: KbType.RAGFLOW,
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
  { label: '检索配置', value: 'retrieval' },
  { label: 'HTTP配置', value: 'http' }
]

/**
 * RAGFlow连接配置
 */
const ragflowConnection = reactive({
  apiKey: '',
  baseUrl: '',
  datasetIds: [] as string[],
  documentIds: [] as string[]
})

/**
 * RAGFlow检索配置
 */
const ragflowRetrieval = reactive({
  topK: 1024,
  scoreThreshold: 0.5,
  similarityThreshold: 0.2,
  vectorSimilarityWeight: 0.3,
  page: 1,
  pageSize: 30,
  useKg: false,
  tocEnhance: false,
  rerankId: undefined as number | undefined,
  keyword: false,
  highlight: false,
  crossLanguages: [] as string[]
})

/**
 * RAGFlow HTTP配置
 */
const ragflowHttp = reactive({
  timeout: '',
  maxRetries: undefined as number | undefined,
  customHeaders: '{}'
})

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
  const { connectionConfig, retrievalConfig, httpConfig } = props.data!

  if (connectionConfig) {
    Object.assign(ragflowConnection, connectionConfig)
  }
  if (retrievalConfig) {
    Object.assign(ragflowRetrieval, retrievalConfig)
  }
  if (httpConfig) {
    Object.assign(ragflowHttp, httpConfig)
    if (httpConfig.customHeaders) {
      ragflowHttp.customHeaders = JSON.stringify(httpConfig.customHeaders, null, 2)
    }
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

  data.connectionConfig = { ...ragflowConnection }
  data.retrievalConfig = { ...ragflowRetrieval }

  if (ragflowHttp.timeout || ragflowHttp.maxRetries) {
    data.httpConfig = {
      timeout: ragflowHttp.timeout || undefined,
      maxRetries: ragflowHttp.maxRetries || undefined,
      customHeaders: ragflowHttp.customHeaders ? JSON.parse(ragflowHttp.customHeaders) : undefined
    }
  } else {
    data.httpConfig = null
  }

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
          <AFormItem label="API密钥（API Key）" :rules="[{ required: true, message: '请输入API Key' }]">
            <AInputPassword v-model:value="ragflowConnection.apiKey" placeholder="请输入API Key" />
          </AFormItem>
          <AFormItem label="基础地址（Base URL）" :rules="[{ required: true, message: '请输入Base URL' }]">
            <AInput v-model:value="ragflowConnection.baseUrl" placeholder="例如: https://cloud.ragflow.io" />
          </AFormItem>
          <AFormItem label="数据集ID列表（Dataset IDs）">
            <ASelect
              v-model:value="ragflowConnection.datasetIds"
              mode="tags"
              placeholder="输入后按回车添加"
              :token-separators="[',']"
            />
          </AFormItem>
          <AFormItem label="文档ID列表（Document IDs）">
            <ASelect
              v-model:value="ragflowConnection.documentIds"
              mode="tags"
              placeholder="输入后按回车添加"
              :token-separators="[',']"
            />
          </AFormItem>
        </div>

        <!-- 检索配置 -->
        <div v-show="activeConfigSection === 'retrieval'" class="config-content">
          <h4 style="margin-bottom: 12px">检索配置(可选)</h4>
          <AFormItem label="返回Top K（Top K）">
            <AInputNumber v-model:value="ragflowRetrieval.topK" :min="1" :max="2048" style="width: 100%" />
          </AFormItem>
          <AFormItem label="分数阈值（Score Threshold）">
            <AInputNumber v-model:value="ragflowRetrieval.scoreThreshold" :min="0" :max="1" :step="0.1" style="width: 100%" />
          </AFormItem>
          <AFormItem label="相似度阈值（Similarity Threshold）">
            <AInputNumber v-model:value="ragflowRetrieval.similarityThreshold" :min="0" :max="1" :step="0.1" style="width: 100%" />
          </AFormItem>
          <AFormItem label="向量相似度权重（Vector Similarity Weight）">
            <AInputNumber v-model:value="ragflowRetrieval.vectorSimilarityWeight" :min="0" :max="1" :step="0.1" style="width: 100%" />
          </AFormItem>
          <AFormItem label="页码（Page）">
            <AInputNumber v-model:value="ragflowRetrieval.page" :min="1" style="width: 100%" />
          </AFormItem>
          <AFormItem label="每页大小（Page Size）">
            <AInputNumber v-model:value="ragflowRetrieval.pageSize" :min="1" :max="100" style="width: 100%" />
          </AFormItem>
          <AFormItem label="使用知识图谱（Use KG）">
            <ASwitch v-model:checked="ragflowRetrieval.useKg" />
          </AFormItem>
          <AFormItem label="目录增强（TOC Enhance）">
            <ASwitch v-model:checked="ragflowRetrieval.tocEnhance" />
          </AFormItem>
          <AFormItem label="重排序ID（Rerank ID）">
            <AInputNumber v-model:value="ragflowRetrieval.rerankId" :min="0" style="width: 100%" />
          </AFormItem>
          <AFormItem label="关键词搜索（Keyword）">
            <ASwitch v-model:checked="ragflowRetrieval.keyword" />
          </AFormItem>
          <AFormItem label="高亮显示（Highlight）">
            <ASwitch v-model:checked="ragflowRetrieval.highlight" />
          </AFormItem>
          <AFormItem label="跨语言搜索（Cross Languages）">
            <ASelect
              v-model:value="ragflowRetrieval.crossLanguages"
              mode="tags"
              placeholder="输入语言代码后按回车添加, 如: en, zh"
              :token-separators="[',']"
            />
          </AFormItem>
        </div>

        <!-- HTTP配置 -->
        <div v-show="activeConfigSection === 'http'" class="config-content">
          <h4 style="margin-bottom: 12px">HTTP配置(可选)</h4>
          <AFormItem label="超时时间（Timeout）">
            <AInput v-model:value="ragflowHttp.timeout" placeholder="例如: PT30S" />
          </AFormItem>
          <AFormItem label="最大重试次数（Max Retries）">
            <AInputNumber v-model:value="ragflowHttp.maxRetries" :min="0" :max="10" style="width: 100%" />
          </AFormItem>
          <AFormItem label="自定义请求头JSON（Custom Headers (JSON)）">
            <ATextarea v-model:value="ragflowHttp.customHeaders" :rows="4" placeholder='{"X-Custom-Header": "value"}' />
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
