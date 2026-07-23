/**
 * 模型配置管理页面
 *
 * @author huxuehao
 */
<script setup lang="ts">
/* eslint-disable vue/multi-word-component-names */
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeftOutlined } from '@ant-design/icons-vue'
import { Modal, message } from 'ant-design-vue'
import type { ModelConfigVO, ModelConfigDTO, ModelConfig } from '@/types'
import * as modelApi from '@/api/model'
import ModelConfigForm from '@/components/model/ModelConfigForm.vue'
import ModelConfigCard from '@/components/model/ModelConfigCard.vue'
import { RoutePaths } from '@/router/constants.ts'

const route = useRoute()
const router = useRouter()

const providerId = computed(() => route.params.providerId as string)
const providerName = ref('')
const providerDescription = ref('')
const providerBaseUrl = ref('')
const providerType = ref<string>()

const modelList = ref<ModelConfigVO[]>([])
const loading = ref<boolean>(false)
const searchKeyword = ref<string>('')

const formVisible = ref<boolean>(false)
const currentData = ref<ModelConfigVO | undefined>(undefined)

/**
 * 加载模型列表
 */
async function fetchModelList() {
  loading.value = true
  try {
    const query: ModelConfigDTO = {
      page: 1,
      size: 500,
      providerId: providerId.value,
      name: searchKeyword.value || undefined
    }
    const response = await modelApi.configPage(query)
    modelList.value = response.data.data.records || []
  } finally {
    loading.value = false
  }
}

/**
 * 加载供应商信息
 */
async function fetchProviderInfo() {
  const response = await modelApi.providerDetail(providerId.value)
  const data = response.data.data
  providerName.value = data.name
  providerDescription.value = data.description || ''
  providerBaseUrl.value = data.baseUrl || ''
  providerType.value = data.type
}

/**
 * 返回列表
 */
function handleBack() {
  router.push(`/${RoutePaths.MODEL}`)
}

/**
 * 处理搜索
 */
function handleSearch() {
  fetchModelList()
}

/**
 * 处理新增
 */
function handleCreate() {
  currentData.value = undefined
  formVisible.value = true
}

/**
 * 处理编辑
 */
async function handleEdit(id: string) {
  const response = await modelApi.configDetail(id)
  currentData.value = response.data.data
  formVisible.value = true
}

/**
 * 处理删除
 */
function handleDelete(id: string) {
  Modal.confirm({
    title: '确认删除',
    content: '删除后无法恢复，是否继续？',
    onOk: async () => {
      await modelApi.configRemove([id])
      message.success('删除成功')
      fetchModelList()
    }
  })
}

/**
 * 处理启用/禁用
 */
async function handleEnable(id: string) {
  const response = await modelApi.configDetail(id)
  const data = response.data.data
  await modelApi.configUpdate({ ...data, enabled: !data.enabled } as ModelConfig)
  message.success(data.enabled ? '已停用' : '已启用')
  fetchModelList()
}

/**
 * 处理测试连接
 */
async function handleTest(id: string) {
  try {
    const response = await modelApi.checkModel(id)
    const result = response.data.data
    if (result.success) {
      message.success('连接测试成功')
    } else {
      message.error(`连接测试失败: ${result.message || '未知错误'}`)
    }
    fetchModelList()
  } catch {
    message.error('连接测试请求失败')
  }
}

/**
 * 表单提交成功
 */
function handleFormSuccess() {
  fetchModelList()
}

onMounted(() => {
  fetchProviderInfo()
  fetchModelList()
})
</script>

<template>
  <div class="provider-config-page">
    <section class="intro-section">
      <div class="intro-nav">
        <AButton type="link" @click="handleBack" class="back-btn">
          <ArrowLeftOutlined />
          <span>返回供应商列表</span>
        </AButton>
      </div>
      <h3 class="intro-title">{{ providerName }} - 模型配置</h3>
      <p class="intro-desc text-secondary">
        {{ providerDescription || '管理当前供应商下的模型配置，包括模型参数、连接测试和启用状态控制。' }}
      </p>
    </section>

    <section class="filter-section flex justify-between items-center">
      <AInput
        v-model:value="searchKeyword"
        placeholder="搜索模型名称"
        style="width: 300px"
        @pressEnter="handleSearch"
      >
        <template #suffix>
          <AButton type="text" size="small" @click="handleSearch">搜索</AButton>
        </template>
      </AInput>
      <AButton type="primary" @click="handleCreate"
               v-permission="['TENANT_EDITOR','TENANT_ADMIN','TENANT_OWNER']">
        新增模型
      </AButton>
    </section>

    <section class="card-section">
      <ASpin :spinning="loading">
        <div v-if="modelList.length > 0" class="card-grid">
          <ModelConfigCard
            v-for="item in modelList"
            :key="item.id"
            :data="item"
            :provider-base-url="providerBaseUrl"
            @edit="handleEdit"
            @delete="handleDelete"
            @enable="handleEnable"
            @test="handleTest"
          />
        </div>
        <div v-else class="empty-state">
          <AEmpty :description="loading ? '正在加载模型列表...' : '暂未配置模型，点击「新增模型」添加'" />
        </div>
      </ASpin>
    </section>

    <ModelConfigForm
      v-model:visible="formVisible"
      :data="currentData"
      :provider-id="providerId"
      :provider-type="providerType"
      @success="handleFormSuccess"
    />
  </div>
</template>

<style scoped lang="scss">
.provider-config-page {
  padding: var(--spacing-lg);
  min-height: 100%;

  .intro-section {
    margin-bottom: var(--spacing-lg);

    .intro-nav {
      margin-bottom: var(--spacing-sm);
    }

    .back-btn {
      padding: 0;
      color: rgba(0, 0, 0, 0.45);

      &:hover {
        color: rgba(0, 0, 0, 0.88);
      }
    }

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
  }

  .filter-section {
    margin-bottom: var(--spacing-lg);
    gap: var(--spacing-md);
    position: sticky;
    top: 0;
    background-color: var(--color-bg);
    padding: var(--spacing-base) 0;
    z-index: 10;
  }

  .card-section {
    position: relative;

    .card-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
      gap: var(--spacing-md);

      @media (max-width: 768px) {
        grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
      }

      @media (min-width: 769px) and (max-width: 1024px) {
        grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
      }

      @media (min-width: 1025px) {
        grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
      }
    }
  }

  .empty-state {
    padding: 60px 0;
  }
}
</style>
