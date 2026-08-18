/**
 * 百炼知识库配置表单组件
 * 维护百炼知识库的连接、端点、检索、重排序与查询重写配置
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
  kbType: KbType.BAILIAN,
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
  { label: '查询重写配置', value: 'queryRewrite' }
]

/**
 * 百炼连接配置
 */
const bailianConnection = reactive({
  accessKeyId: '',
  accessKeySecret: '',
  workspaceId: '',
  indexId: '',
  saveRetrieverHistory: false
})

/**
 * 百炼端点配置
 */
const bailianEndpoint = reactive({ endpoint: '' })

/**
 * 百炼检索配置
 */
const bailianRetrieval = reactive({
  denseSimilarityTopK: undefined as number | undefined,
  sparseSimilarityTopK: undefined as number | undefined
})

/**
 * 百炼重排序配置
 */
const bailianReranking = reactive({
  enableReranking: false,
  modelName: '',
  rerankMinScore: undefined as number | undefined,
  rerankTopN: undefined as number | undefined
})

/**
 * 百炼查询重写配置
 */
const queryRewrite = reactive({
  enableRewrite: false,
  modelName: ''
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
  const { connectionConfig, endpointConfig, retrievalConfig, rerankingConfig, queryRewriteConfig } = props.data!

  if (connectionConfig) {
    Object.assign(bailianConnection, connectionConfig)
  }
  if (endpointConfig) {
    Object.assign(bailianEndpoint, endpointConfig)
  }
  if (retrievalConfig) {
    Object.assign(bailianRetrieval, retrievalConfig)
  }
  if (rerankingConfig) {
    bailianReranking.enableReranking = !!rerankingConfig.enableReranking
    if (rerankingConfig.rerankConfig) {
      Object.assign(bailianReranking, rerankingConfig.rerankConfig)
    }
  }
  if (queryRewriteConfig) {
    queryRewrite.enableRewrite = !!queryRewriteConfig.enableRewrite
    if (queryRewriteConfig.rewriteConfig) {
      Object.assign(queryRewrite, queryRewriteConfig.rewriteConfig)
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

  data.connectionConfig = { ...bailianConnection }
  data.endpointConfig = bailianEndpoint.endpoint ? { endpoint: bailianEndpoint.endpoint } : null
  data.retrievalConfig = (bailianRetrieval.denseSimilarityTopK || bailianRetrieval.sparseSimilarityTopK) ? { ...bailianRetrieval } : null

  if (bailianReranking.enableReranking) {
    data.rerankingConfig = {
      enableReranking: true,
      rerankConfig: {
        modelName: bailianReranking.modelName,
        rerankMinScore: bailianReranking.rerankMinScore,
        rerankTopN: bailianReranking.rerankTopN
      }
    }
  } else {
    data.rerankingConfig = null
  }

  if (queryRewrite.enableRewrite) {
    data.queryRewriteConfig = {
      enableRewrite: true,
      rewriteConfig: {
        modelName: queryRewrite.modelName
      }
    }
  } else {
    data.queryRewriteConfig = null
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
          <AFormItem label="访问密钥ID（Access Key ID）" :rules="[{ required: true, message: '请输入Access Key ID' }]">
            <AInput v-model:value="bailianConnection.accessKeyId" placeholder="请输入Access Key ID" />
          </AFormItem>
          <AFormItem label="访问密钥Secret（Access Key Secret）" :rules="[{ required: true, message: '请输入Access Key Secret' }]">
            <AInputPassword v-model:value="bailianConnection.accessKeySecret" placeholder="请输入Access Key Secret" />
          </AFormItem>
          <AFormItem label="工作空间ID（Workspace ID）" :rules="[{ required: true, message: '请输入Workspace ID' }]">
            <AInput v-model:value="bailianConnection.workspaceId" placeholder="请输入Workspace ID" />
          </AFormItem>
          <AFormItem label="索引ID（Index ID）" :rules="[{ required: true, message: '请输入Index ID' }]">
            <AInput v-model:value="bailianConnection.indexId" placeholder="请输入Index ID" />
          </AFormItem>
          <AFormItem label="保存检索历史">
            <ASwitch v-model:checked="bailianConnection.saveRetrieverHistory" />
          </AFormItem>
        </div>

        <!-- 端点配置 -->
        <div v-show="activeConfigSection === 'endpoint'" class="config-content">
          <h4 style="margin-bottom: 12px">端点配置(可选)</h4>
          <AFormItem label="端点地址（Endpoint）">
            <AInput v-model:value="bailianEndpoint.endpoint" placeholder="例如: bailian.cn-beijing.aliyuncs.com" />
          </AFormItem>
        </div>

        <!-- 检索配置 -->
        <div v-show="activeConfigSection === 'retrieval'" class="config-content">
          <h4 style="margin-bottom: 12px">检索配置(可选)</h4>
          <AFormItem label="稠密相似度Top K（Dense Similarity Top K）">
            <AInputNumber v-model:value="bailianRetrieval.denseSimilarityTopK" :min="1" :max="1000" style="width: 100%" />
          </AFormItem>
          <AFormItem label="稀疏相似度Top K（Sparse Similarity Top K）">
            <AInputNumber v-model:value="bailianRetrieval.sparseSimilarityTopK" :min="1" :max="1000" style="width: 100%" />
          </AFormItem>
        </div>

        <!-- 重排序配置 -->
        <div v-show="activeConfigSection === 'reranking'" class="config-content">
          <h4 style="margin-bottom: 12px">重排序配置(可选)</h4>
          <AFormItem label="启用重排序">
            <ASwitch v-model:checked="bailianReranking.enableReranking" />
          </AFormItem>
          <template v-if="bailianReranking.enableReranking">
            <AFormItem label="模型名称（Model Name）" :rules="[{ required: true, message: '请输入Model Name' }]">
              <AInput v-model:value="bailianReranking.modelName" placeholder="例如: gte-rerank-hybrid" />
            </AFormItem>
            <AFormItem label="重排序最小分数（Rerank Min Score）">
              <AInputNumber v-model:value="bailianReranking.rerankMinScore" :min="0" :max="1" :step="0.1" style="width: 100%" />
            </AFormItem>
            <AFormItem label="重排序返回Top N（Rerank Top N）">
              <AInputNumber v-model:value="bailianReranking.rerankTopN" :min="1" :max="100" style="width: 100%" />
            </AFormItem>
          </template>
        </div>

        <!-- 查询重写配置 -->
        <div v-show="activeConfigSection === 'queryRewrite'" class="config-content">
          <h4 style="margin-bottom: 12px">查询重写配置(可选)</h4>
          <AFormItem label="启用查询重写">
            <ASwitch v-model:checked="queryRewrite.enableRewrite" />
          </AFormItem>
          <template v-if="queryRewrite.enableRewrite">
            <AFormItem label="模型名称（Model Name）" :rules="[{ required: true, message: '请输入Model Name' }]">
              <AInput v-model:value="queryRewrite.modelName" placeholder="例如: conv-rewrite-qwen-1.8b" />
            </AFormItem>
          </template>
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
