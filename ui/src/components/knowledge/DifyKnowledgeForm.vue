/**
 * Dify知识库配置表单组件
 * 维护Dify知识库的连接、端点、检索、重排序、元数据过滤与HTTP配置
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
  kbType: KbType.DIFY,
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
  { label: '端点配置', value: 'endpoint' },
  { label: '检索配置', value: 'retrieval' },
  { label: '重排序配置', value: 'reranking' },
  { label: '元数据过滤', value: 'metadata' },
  { label: 'HTTP配置', value: 'http' }
]

/**
 * Dify连接配置
 */
const difyConnection = reactive({
  apiKey: '',
  datasetId: '',
  saveRetrieverHistory: false
})

/**
 * Dify端点配置
 */
const difyEndpoint = reactive({ apiBaseUrl: '' })

/**
 * Dify检索配置
 */
const difyRetrieval = reactive({
  retrievalMode: 'HYBRID_SEARCH',
  topK: undefined as number | undefined,
  scoreThreshold: undefined as number | undefined,
  weights: undefined as number | undefined
})

/**
 * Dify重排序配置
 */
const difyReranking = reactive({
  enableRerank: false,
  providerName: '',
  modelName: '',
  topN: undefined as number | undefined
})

/**
 * Dify元数据过滤配置
 */
const metadataFilters = reactive({
  logicalOperator: 'AND',
  conditions: [] as Array<{ name: string; comparisonOperator: string; value: string }>
})

/**
 * Dify HTTP配置
 */
const difyHttp = reactive({
  connectTimeout: '',
  readTimeout: '',
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
  const { connectionConfig, endpointConfig, retrievalConfig, rerankingConfig, metadataFilters: mf, httpConfig } = props.data!

  if (connectionConfig) {
    Object.assign(difyConnection, connectionConfig)
  }
  if (endpointConfig) {
    Object.assign(difyEndpoint, endpointConfig)
  }
  if (retrievalConfig) {
    Object.assign(difyRetrieval, retrievalConfig)
    normalizeLegacyDifyMode()
  }
  if (rerankingConfig) {
    difyReranking.enableRerank = !!rerankingConfig.enableRerank
    if (rerankingConfig.rerankConfig) {
      Object.assign(difyReranking, rerankingConfig.rerankConfig)
    }
  }
  if (mf) {
    Object.assign(metadataFilters, mf)
  }
  if (httpConfig) {
    Object.assign(difyHttp, httpConfig)
    if (httpConfig.customHeaders) {
      difyHttp.customHeaders = JSON.stringify(httpConfig.customHeaders, null, 2)
    }
  }
}

/**
 * 兼容旧版本存储的 Dify 检索模式别名
 */
function normalizeLegacyDifyMode() {
  if (difyRetrieval.retrievalMode === 'VECTOR') {
    difyRetrieval.retrievalMode = 'SEMANTIC_SEARCH'
  } else if (difyRetrieval.retrievalMode === 'FULL_TEXT') {
    difyRetrieval.retrievalMode = 'FULL_TEXT_SEARCH'
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

  data.connectionConfig = { ...difyConnection }
  data.endpointConfig = difyEndpoint.apiBaseUrl ? { apiBaseUrl: difyEndpoint.apiBaseUrl } : null
  data.retrievalConfig = { ...difyRetrieval }

  if (difyReranking.enableRerank) {
    data.rerankingConfig = {
      enableRerank: true,
      rerankConfig: {
        providerName: difyReranking.providerName,
        modelName: difyReranking.modelName,
        topN: difyReranking.topN
      }
    }
  } else {
    data.rerankingConfig = null
  }

  data.metadataFilters = metadataFilters.conditions.length > 0 ? { ...metadataFilters } : null

  if (difyHttp.connectTimeout || difyHttp.readTimeout || difyHttp.maxRetries) {
    data.httpConfig = {
      connectTimeout: difyHttp.connectTimeout || undefined,
      readTimeout: difyHttp.readTimeout || undefined,
      maxRetries: difyHttp.maxRetries || undefined,
      customHeaders: difyHttp.customHeaders ? JSON.parse(difyHttp.customHeaders) : undefined
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

/**
 * 添加元数据条件
 */
function addMetadataCondition() {
  metadataFilters.conditions.push({
    name: '',
    comparisonOperator: '=',
    value: ''
  })
}

/**
 * 删除元数据条件
 */
function removeMetadataCondition(index: number) {
  metadataFilters.conditions.splice(index, 1)
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
            <AInputPassword v-model:value="difyConnection.apiKey" placeholder="请输入API Key" />
          </AFormItem>
          <AFormItem label="数据集ID（Dataset ID）" :rules="[{ required: true, message: '请输入Dataset ID' }]">
            <AInput v-model:value="difyConnection.datasetId" placeholder="请输入Dataset ID" />
          </AFormItem>
          <AFormItem label="保存检索历史">
            <ASwitch v-model:checked="difyConnection.saveRetrieverHistory" />
          </AFormItem>
        </div>

        <!-- 端点配置 -->
        <div v-show="activeConfigSection === 'endpoint'" class="config-content">
          <h4 style="margin-bottom: 12px">端点配置(可选)</h4>
          <AFormItem label="API基础地址（API Base URL）">
            <AInput v-model:value="difyEndpoint.apiBaseUrl" placeholder="例如: https://api.dify.ai/v1" />
          </AFormItem>
        </div>

        <!-- 检索配置 -->
        <div v-show="activeConfigSection === 'retrieval'" class="config-content">
          <h4 style="margin-bottom: 12px">检索配置(可选)</h4>
          <AFormItem label="检索模式（Retrieval Mode）">
            <ASelect v-model:value="difyRetrieval.retrievalMode">
              <ASelectOption value="HYBRID_SEARCH">混合检索</ASelectOption>
              <ASelectOption value="SEMANTIC_SEARCH">向量检索</ASelectOption>
              <ASelectOption value="KEYWORD_SEARCH">关键词检索</ASelectOption>
              <ASelectOption value="FULL_TEXT_SEARCH">全文检索</ASelectOption>
            </ASelect>
          </AFormItem>
          <AFormItem label="返回Top K（Top K）">
            <AInputNumber v-model:value="difyRetrieval.topK" :min="1" :max="100" style="width: 100%" />
          </AFormItem>
          <AFormItem label="分数阈值（Score Threshold）">
            <AInputNumber v-model:value="difyRetrieval.scoreThreshold" :min="0" :max="1" :step="0.1" style="width: 100%" />
          </AFormItem>
          <AFormItem label="权重（Weights）">
            <AInputNumber v-model:value="difyRetrieval.weights" :min="0" :max="1" :step="0.1" style="width: 100%" />
          </AFormItem>
        </div>

        <!-- 重排序配置 -->
        <div v-show="activeConfigSection === 'reranking'" class="config-content">
          <h4 style="margin-bottom: 12px">重排序配置(可选)</h4>
          <AFormItem label="启用重排序">
            <ASwitch v-model:checked="difyReranking.enableRerank" />
          </AFormItem>
          <template v-if="difyReranking.enableRerank">
            <AFormItem label="提供商名称（Provider Name）" :rules="[{ required: true, message: '请输入Provider Name' }]">
              <AInput v-model:value="difyReranking.providerName" placeholder="例如: cohere" />
            </AFormItem>
            <AFormItem label="模型名称（Model Name）" :rules="[{ required: true, message: '请输入Model Name' }]">
              <AInput v-model:value="difyReranking.modelName" placeholder="例如: rerank-english-v2.0" />
            </AFormItem>
            <AFormItem label="返回Top N（Top N）">
              <AInputNumber v-model:value="difyReranking.topN" :min="1" :max="100" style="width: 100%" />
            </AFormItem>
          </template>
        </div>

        <!-- 元数据过滤 -->
        <div v-show="activeConfigSection === 'metadata'" class="config-content">
          <h4 style="margin-bottom: 12px">元数据过滤(可选)</h4>
          <AFormItem label="逻辑运算符（Logical Operator）">
            <ASelect v-model:value="metadataFilters.logicalOperator">
              <ASelectOption value="AND">AND</ASelectOption>
              <ASelectOption value="OR">OR</ASelectOption>
            </ASelect>
          </AFormItem>

          <AFormItem label="过滤条件（Conditions）">
            <div v-for="(condition, index) in metadataFilters.conditions" :key="index" class="flex gap-sm mb-sm">
              <AInput v-model:value="condition.name" placeholder="Name" style="flex: 1" />
              <ASelect v-model:value="condition.comparisonOperator" style="width: 100px">
                <ASelectOption value="=">=</ASelectOption>
                <ASelectOption value=">">&gt;</ASelectOption>
                <ASelectOption value="<">&lt;</ASelectOption>
                <ASelectOption value=">=">&gt;=</ASelectOption>
                <ASelectOption value="<=">&lt;=</ASelectOption>
                <ASelectOption value="!=">!=</ASelectOption>
              </ASelect>
              <AInput v-model:value="condition.value" placeholder="Value" style="flex: 1" />
              <AButton type="text" danger @click="removeMetadataCondition(index)">删除</AButton>
            </div>
            <AButton type="dashed" block @click="addMetadataCondition">添加条件</AButton>
          </AFormItem>
        </div>

        <!-- HTTP配置 -->
        <div v-show="activeConfigSection === 'http'" class="config-content">
          <h4 style="margin-bottom: 12px">HTTP配置(可选)</h4>
          <AFormItem label="连接超时（Connect Timeout）">
            <AInput v-model:value="difyHttp.connectTimeout" placeholder="例如: PT30S" />
          </AFormItem>
          <AFormItem label="读取超时（Read Timeout）">
            <AInput v-model:value="difyHttp.readTimeout" placeholder="例如: PT60S" />
          </AFormItem>
          <AFormItem label="最大重试次数（Max Retries）">
            <AInputNumber v-model:value="difyHttp.maxRetries" :min="0" :max="10" style="width: 100%" />
          </AFormItem>
          <AFormItem label="自定义请求头JSON（Custom Headers (JSON)）">
            <ATextarea v-model:value="difyHttp.customHeaders" :rows="4" placeholder='{"X-Custom-Header": "value"}' />
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

.intro-desc {
  font-size: var(--font-size-base);
  line-height: 1.6;
  max-width: 800px;
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
