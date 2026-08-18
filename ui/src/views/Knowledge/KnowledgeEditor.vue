/**
 * 知识库新增/编辑页面
 *
 * 支持新增（/knowledge/new?kbType=xxx）与编辑（/knowledge/:id/edit）两种模式。
 * 页面左侧为“伪二级菜单”（由本组件自行维护，非子路由）：
 *  - 本地知识库：配置信息 / 文档管理 / 检索测试
 *  - 外部集成知识库（百炼、Dify、RagFlow）：配置信息 / 检索测试
 * 默认选中“配置信息”，用户可手动切换到“文档管理/检索测试”（新增模式下未保存前不可用）。
 *
 * @author huxuehao
 */
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { ArrowLeftOutlined, SettingOutlined, FileTextOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { KbType, type KnowledgeBaseConfigVO } from '@/types'
import * as knowledgeApi from '@/api/knowledge'
import { useKnowledgeStore } from '@/stores'
import LocalKnowledgeForm from '@/components/knowledge/LocalKnowledgeForm.vue'
import BailianKnowledgeForm from '@/components/knowledge/BailianKnowledgeForm.vue'
import DifyKnowledgeForm from '@/components/knowledge/DifyKnowledgeForm.vue'
import RagflowKnowledgeForm from '@/components/knowledge/RagflowKnowledgeForm.vue'
import DocumentList from '@/components/rag/DocumentList.vue'
import SearchTest from '@/components/rag/SearchTest.vue'

const route = useRoute()
const router = useRouter()
const store = useKnowledgeStore()

/**
 * 知识库配置ID（编辑模式存在）
 */
const kbId = computed(() => (route.params.id as string) || '')
const isEdit = computed(() => !!kbId.value)

/**
 * 新增模式下通过 query 传入的知识库类型
 */
const queryKbType = computed(() => route.query.kbType as KbType | undefined)

/**
 * 编辑模式下加载的详情数据
 */
const detailData = ref<KnowledgeBaseConfigVO | undefined>(undefined)
const loadingDetail = ref(false)

/**
 * 当前知识库类型
 */
const kbType = computed<KbType | undefined>(() => {
  if (isEdit.value) return detailData.value?.kbType
  return queryKbType.value
})

/**
 * 检索测试组件要求的知识库类型（进入编辑模式加载完成后必有值）
 */
const editorKbType = computed<KbType>(() => kbType.value as KbType)

/**
 * 左侧伪二级菜单项（按类型动态生成）
 */
const menuItems = computed(() => {
  const items = [
    { key: 'config', label: '配置信息', icon: SettingOutlined }
  ]
  if (kbType.value === 'LOCAL') {
    items.push({ key: 'documents', label: '文档管理', icon: FileTextOutlined })
  }
  items.push({ key: 'search', label: '检索测试', icon: SearchOutlined })
  return items
})

/**
 * 当前激活的菜单项
 */
const activeMenu = ref<string>('config')

/**
 * 非配置菜单在新增模式下不可用（尚未保存出配置ID）
 */
function isMenuDisabled(key: string): boolean {
  if (key === 'config') return false
  return !isEdit.value
}

/**
 * 处理菜单点击
 */
function handleMenuClick(key: string) {
  if (isMenuDisabled(key)) {
    message.info('请先保存知识库配置')
    return
  }
  activeMenu.value = key
}

/**
 * 加载详情数据（编辑模式）
 */
async function loadDetail() {
  if (!isEdit.value) return
  loadingDetail.value = true
  try {
    const response = await knowledgeApi.detail(kbId.value)
    detailData.value = response.data.data
  } catch (error) {
    console.error('加载知识库配置失败:', error)
    message.error('加载知识库配置失败')
    router.push('/knowledge')
  } finally {
    loadingDetail.value = false
  }
}


/**
 * 表单保存成功，标记列表刷新并返回列表
 */
function handleFormSuccess() {
  store.markNeedsRefresh()
  router.push('/knowledge')
}

/**
 * 返回列表
 */
function handleBack() {
  router.push('/knowledge')
}

onMounted(() => {
  // 新增模式缺少或携带非法类型参数时无法渲染表单，直接返回列表
  if (!isEdit.value) {
    const type = queryKbType.value
    const validTypes: KbType[] = [KbType.LOCAL, KbType.BAILIAN, KbType.DIFY, KbType.RAGFLOW]
    if (!type || !validTypes.includes(type)) {
      message.error('缺少或非法的知识库类型参数')
      router.push('/knowledge')
      return
    }
  }
  loadDetail()
})
</script>

<template>
  <div class="knowledge-editor-page">
    <section class="editor-layout">
      <!-- 左侧伪二级菜单 -->
      <div class="editor-sidebar">
        <AButton type="link" class="back-btn" @click="handleBack" >
          <ArrowLeftOutlined />
          <span>返回知识库列表</span>
        </AButton>
        <div class="editor-menu-list">
          <div
            v-for="item in menuItems"
            :key="item.key"
            class="editor-menu-item"
            :class="{ active: activeMenu === item.key, disabled: isMenuDisabled(item.key) }"
            @click="handleMenuClick(item.key)"
          >
            <component :is="item.icon" class="editor-menu-icon" />
            <span class="editor-menu-label">{{ item.label }}</span>
          </div>
        </div>
      </div>

      <!-- 分割线 -->
      <div class="editor-divider"></div>

      <!-- 右侧内容区 -->
      <div class="editor-content">
        <ApboaSpin :spinning="loadingDetail">
          <template v-if="activeMenu === 'config'">
            <LocalKnowledgeForm
              v-if="kbType === 'LOCAL'"
              :data="detailData"
              @success="handleFormSuccess"
              @cancel="handleBack"
            />
            <BailianKnowledgeForm
              v-else-if="kbType === 'BAILIAN'"
              :data="detailData"
              @success="handleFormSuccess"
              @cancel="handleBack"
            />
            <DifyKnowledgeForm
              v-else-if="kbType === 'DIFY'"
              :data="detailData"
              @success="handleFormSuccess"
              @cancel="handleBack"
            />
            <RagflowKnowledgeForm
              v-else-if="kbType === 'RAGFLOW'"
              :data="detailData"
              @success="handleFormSuccess"
              @cancel="handleBack"
            />
          </template>

          <DocumentList
            v-else-if="activeMenu === 'documents'"
            :name="detailData?.name"
            :description="detailData?.description"
            :knowledge-base-config-id="kbId"
          />
          <SearchTest
            v-else-if="activeMenu === 'search'"
            :knowledge-base-config-id="kbId"
            :kb-type="editorKbType"
            :name="detailData?.name"
            :description="detailData?.description"
            :default-retrieval-config="detailData?.retrievalConfig"
          />
        </ApboaSpin>
      </div>
    </section>
  </div>
</template>

<style scoped lang="scss">
.knowledge-editor-page {
  padding: var(--spacing-lg);
  min-height: 100%;

  // 伪二级菜单 + 内容区布局（继承文档管理页的布局模式）
  .editor-layout {
    display: flex;
    height: calc(100vh - 60px);
    min-height: 480px;
  }

  .editor-sidebar {
    width: 180px;
    flex-shrink: 0;
    padding: 0 0 24px 0;

    .back-btn {
      padding: 0 0 0 5px;
      color: rgba(0, 0, 0, 0.45);

      &:hover {
        color: rgba(0, 0, 0, 0.88);
      }
    }

    .editor-menu-title {
      font-size: var(--font-size-lg);
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      padding: 0 12px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .editor-menu-list {
      display: flex;
      flex-direction: column;
      gap: 2px;
      margin-top: 10px;
    }

    .editor-menu-item {
      display: flex;
      align-items: center;
      gap: var(--spacing-sm);
      padding: 8px 12px;
      border-radius: var(--border-radius-md);
      cursor: pointer;
      color: var(--color-text-regular);
      font-size: var(--font-size-sm);
      transition: all var(--transition-fast);

      &:hover {
        color: var(--color-text-primary);
        background-color: var(--color-bg-base);
      }

      &.active {
        color: #000000;
        background-color: #F2F4F7;
        font-weight: 500;
      }

      &.disabled {
        color: var(--color-text-placeholder);
        cursor: not-allowed;

        &:hover {
          color: var(--color-text-placeholder);
          background-color: transparent;
        }
      }
    }

    .editor-menu-icon {
      font-size: 16px;
      flex-shrink: 0;
    }

    .editor-menu-label {
      white-space: nowrap;
    }
  }

  .editor-divider {
    width: 1px;
    align-self: stretch;
    background-color: var(--color-border-light);
    margin: 0 20px;
  }

  .editor-content {
    flex: 1;
    min-width: 0;
    overflow-y: auto;
  }
}
</style>
