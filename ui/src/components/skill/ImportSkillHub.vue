/**
 * SkillHub 技能市场导入组件 - 卡片界面
 *
 * @author huxuehao
 */
<script setup lang="ts">
import { ref, reactive } from 'vue'
import { message } from 'ant-design-vue'
import {
  SearchOutlined,
  AppstoreOutlined,
  CloudDownloadOutlined,
  ReloadOutlined,
  HomeOutlined,
  ImportOutlined
} from '@ant-design/icons-vue'
import * as skillHubApi from '@/api/skillHub'
import type { SkillsHubVO } from '@/types'

/**
 * Props定义
 */
defineProps<{
  visible: boolean
}>()

/**
 * Emits定义
 */
const emit = defineEmits<{
  'update:visible': [value: boolean]
  success: [category?: string]
}>()

/**
 * 搜索表单
 */
const searchForm = reactive({
  keyword: '',
  category: undefined as string | undefined,
  source: undefined as string | undefined,
  labels: '',
  sortBy: 'updated_at' as string,
  order: 'desc' as string
})

/**
 * 分页加载状态
 */
const currentPage = ref(1)
const loading = ref(false)
const loadMoreLoading = ref(false)
const importingSlug = ref('')
const skillList = ref<SkillsHubVO[]>([])
const hasMore = ref(true)
const searched = ref(false)
const pageSize = 30

/**
 * 来源选项
 */
const sourceOptions = [
  { label: '全部', value: undefined },
  { label: '官方', value: 'official' },
  { label: '社区', value: 'community' },
  { label: '企业', value: 'enterprise' },
  { label: 'ClawHub', value: 'clawhub' }
]

/**
 * 一级标签选项
 */
const categoryOptions = [
  { label: '全部', value: undefined },
  { label: '办公效率', value: 'office-efficiency' },
  { label: '内容创作', value: 'content-creation' },
  { label: '开发编程', value: 'dev-programming' },
  { label: '数据分析', value: 'data-analysis' },
  { label: '设计多媒体', value: 'design-media' },
  { label: 'AI Agent', value: 'ai-agent' },
  { label: '知识管理', value: 'knowledge-management' },
  { label: '商业运营', value: 'business-ops' },
  { label: '教育学习', value: 'education' },
  { label: '行业专业', value: 'professional' },
  { label: 'IT 运维与安全', value: 'it-ops-security' },
  { label: '生活服务', value: 'life-service' }
]

/**
 * 排序选项
 */
const sortByOptions = [
  { label: '更新时间', value: 'updated_at' },
  { label: '下载量', value: 'downloads' },
  { label: '收藏数', value: 'stars' },
  { label: '安装量', value: 'installs' },
  { label: '评分', value: 'score' }
]

/**
 * 执行搜索（重置）
 */
async function handleSearch() {
  currentPage.value = 1
  skillList.value = []
  hasMore.value = true
  await fetchData()
}

/**
 * 请求数据（首页用 loading，翻页用 loadMoreLoading）
 */
async function fetchData() {
  loading.value = true
  searched.value = true
  try {
    const response = await skillHubApi.search({
      keyword: searchForm.keyword || undefined,
      category: searchForm.category || undefined,
      source: searchForm.source,
      labels: searchForm.labels || undefined,
      sortBy: searchForm.sortBy,
      order: searchForm.order,
      page: currentPage.value
    })
    const resData: Record<string, any> = response.data as any
    const list: SkillsHubVO[] = (resData.data || []) as SkillsHubVO[]
    skillList.value = list
    if (list.length === 0) {
      message.info('未搜索到相关技能')
      hasMore.value = false
    } else if (list.length < pageSize) {
      hasMore.value = false
    }
  } catch {
    message.error('搜索失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

/**
 * 加载下一页
 */
async function loadNextPage() {
  if (loadMoreLoading.value || !hasMore.value) return
  loadMoreLoading.value = true
  currentPage.value++
  try {
    const response = await skillHubApi.search({
      keyword: searchForm.keyword || undefined,
      category: searchForm.category || undefined,
      source: searchForm.source,
      labels: searchForm.labels || undefined,
      sortBy: searchForm.sortBy,
      order: searchForm.order,
      page: currentPage.value
    })
    const resData: Record<string, any> = response.data as any
    const list: SkillsHubVO[] = (resData.data || []) as SkillsHubVO[]
    if (list.length === 0) {
      hasMore.value = false
      message.info('无更多数据')
    } else {
      skillList.value = [...skillList.value, ...list]
      if (list.length < pageSize) {
        hasMore.value = false
      }
    }
  } catch {
    message.error('加载失败，请稍后重试')
    currentPage.value--
  } finally {
    loadMoreLoading.value = false
  }
}


/**
 * 重置搜索
 */
function handleReset() {
  searchForm.keyword = ''
  searchForm.category = undefined
  searchForm.source = undefined
  searchForm.labels = ''
  searchForm.sortBy = 'downloads'
  searchForm.order = 'desc'
  currentPage.value = 1
  skillList.value = []
  hasMore.value = true
  searched.value = false
}

/**
 * 格式化时间
 */
function formatTime(timeStr: string) {
  if (!timeStr) return ''
  const date = new Date(timeStr)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  })
}

/**
 * 关闭弹窗
 */
function handleCancel() {
  emit('update:visible', false)
}

/**
 * 导入技能
 */
async function handleImport(item: SkillsHubVO) {
  importingSlug.value = item.slug
  try {
    const response = await skillHubApi.download(item.slug, item.category)
    const resData = response.data
    if(resData.data.importedCount>0){
      message.success(`导入成功，共 ${resData.data?.importedCount} 个技能`)
      emit('success', item.category)
    }else if(resData.data.skippedCount>0){
      message.warning(`导入失败，技能包已存在`)
    }else {
      message.warning(`导入失败，${resData.data.hintMessage}`)
    }
  } catch {
    message.error('导入失败，请稍后重试')
  } finally {
    importingSlug.value = ''
  }
}
</script>

<template>
  <ApboaModal
    :open="visible"
    :title-icon="AppstoreOutlined"
    title="SkillHub 技能市场"
    :footer="null"
    defaultWidth="1080px"
    destroyOnClose
    @cancel="handleCancel"
  >
    <!-- 搜索区域 -->
    <div class="hub-search">
      <div class="hub-search__row">

        <ASelect
          v-model:value="searchForm.source"
          :options="sourceOptions"
          placeholder="来源"
          allow-clear
          style="width: 200px;"
        />
        <ASelect
          v-model:value="searchForm.category"
          :options="categoryOptions"
          placeholder="一级标签"
          allow-clear
          style="width: 240px"
        />
        <ASelect
          v-model:value="searchForm.sortBy"
          :options="sortByOptions"
          placeholder="排序字段"
          style="width: 200px"
        />
        <ASelect
          v-model:value="searchForm.order"
          :options="[{ label: '降序', value: 'desc' }, { label: '升序', value: 'asc' }]"
          style="width: 150px"
        />

      </div>
      <div class="hub-search__row">
        <AInput
          v-model:value="searchForm.keyword"
          placeholder="输入关键词搜索技能（标题/描述）"
          allow-clear
          style="width: 820px"
          @pressEnter="handleSearch"
        >
          <template #prefix>
            <SearchOutlined class="text-secondary" />
          </template>
        </AInput>

        <div class="flex-1" />
        <AButton type="primary" :loading="loading" @click="handleSearch">
          <SearchOutlined />
          搜索
        </AButton>
        <AButton @click="handleReset">
          <ReloadOutlined />
          重置
        </AButton>
      </div>
    </div>

    <!-- 卡片列表区域 -->
    <div class="hub-content">
      <!-- 搜索结果 -->
      <template v-if="searched">
        <div v-if="loading" class="hub-loading flex-center">
          <ASpin size="large" tip="搜索中..." />
        </div>
        <div v-else-if="skillList.length === 0" class="hub-empty flex-col flex-center">
          <div class="hub-empty__icon">
            <AppstoreOutlined />
          </div>
          <p class="hub-empty__text">暂无搜索结果</p>
          <p class="hub-empty__desc">尝试调整搜索条件或关键词</p>
        </div>
        <template v-else>
          <div class="hub-grid-wrapper">
            <div ref="scrollContainer" class="hub-grid">
              <div
                v-for="item in skillList"
                :key="item.name"
                class="hub-card"
              >
                <div class="hub-card__header flex items-center gap-sm">
                  <div class="hub-card__avatar flex-center">
                    <img v-if="item.iconUrl" :src="item.iconUrl" alt="icon" />
                    <AppstoreOutlined v-else class="hub-card__avatar-icon" />
                  </div>
                  <div class="hub-card__info flex-1">
                    <div class="hub-card__name truncate" :title="item.name">{{ item.name }}</div>
                    <div class="hub-card__version text-xs text-secondary">v{{ item.version || '-' }}</div>
                  </div>
                </div>

                <div class="hub-card__desc line-clamp-3" :title="item.description">
                  {{ item.description || '暂无描述' }}
                </div>

                <div class="hub-card__footer flex items-center justify-between">
                  <div class="hub-card__tags flex items-center gap-xs">
                    <ATag v-if="item.category" color="blue" class="hub-card__tag">{{ item.category }}</ATag>
                    <ATag v-if="item.requiresApiKey === 'true'" color="orange" class="hub-card__tag">API Key</ATag>

                  </div>
                  <div class="hub-card__meta flex items-center gap-sm">
                    <span class="hub-card__stat text-xs text-secondary">
                      <CloudDownloadOutlined />
                      {{ item.downloads || 0 }}
                    </span>
                    <span class="hub-card__time text-xs text-placeholder">{{ formatTime(item.updatedAt) }}</span>
                  </div>
                </div>

                <div class="hub-card__actions flex items-center gap-sm">
                  <a v-if="item.homepage" :href="item.homepage" target="_blank" class="hub-card__link">
                    <HomeOutlined /> 主页
                  </a>
                  <div class="flex-1" />
                  <AButton
                    type="primary"
                    size="small"
                    :loading="importingSlug === item.slug"
                    @click="handleImport(item)"
                  >
                    <ImportOutlined />
                    导入
                  </AButton>
                </div>
              </div>
            </div>

            <!-- 分页区域 -->
            <div class="hub-pagination flex-center">
              <AButton
                v-if="hasMore"
                :loading="loadMoreLoading"
                @click="loadNextPage"
              >
                下一页
              </AButton>
              <span v-else class="hub-pagination__end">— 无更多数据 —</span>
            </div>
          </div>
        </template>
      </template>

      <!-- 初始状态 -->
      <div v-else class="hub-init flex-col flex-center">
        <div class="hub-init__icon">
          <AppstoreOutlined />
        </div>
        <p class="hub-init__title">SkillHub 技能市场</p>
        <p class="hub-init__desc">输入关键词并点击搜索，浏览和发现来自社区与官方的技能包</p>
      </div>
    </div>
  </ApboaModal>
</template>

<style scoped lang="scss">

.hub-search {
  margin-bottom: var(--spacing-md);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  flex-shrink: 0;

  &__row {
    display: flex;
    align-items: center;
    gap: var(--spacing-sm);
  }
}

.hub-content {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.hub-loading {
  padding: var(--spacing-3xl) 0;
}

.hub-empty {
  padding: var(--spacing-3xl) 0;

  &__icon {
    font-size: 48px;
    color: var(--color-text-placeholder, #bfbfbf);
    margin-bottom: var(--spacing-md);
  }

  &__text {
    font-size: var(--font-size-base);
    color: var(--color-text-secondary, #666);
    margin: 0 0 var(--spacing-xs) 0;
  }

  &__desc {
    font-size: var(--font-size-sm);
    color: var(--color-text-placeholder, #999);
    margin: 0;
  }
}

.hub-init {
  flex: 1;
  padding: var(--spacing-3xl) 0;

  &__icon {
    font-size: 64px;
    color: var(--color-primary);
    opacity: 0.3;
    margin-bottom: var(--spacing-lg);
  }

  &__title {
    font-size: var(--font-size-lg);
    font-weight: 600;
    color: var(--color-text-primary, #333);
    margin: 0 0 var(--spacing-sm) 0;
  }

  &__desc {
    font-size: var(--font-size-sm);
    color: var(--color-text-secondary, #666);
    margin: 0;
  }
}

.hub-grid-wrapper {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.hub-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--spacing-md);
  padding: var(--spacing-xs);
  flex: 1;
  align-content: start;
  overflow-y: auto;
  min-height: 0;

  @media (max-width: 768px) {
    grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  }
}

.hub-pagination {
  padding: var(--spacing-md) 0;
  flex-shrink: 0;

  &__end {
    font-size: var(--font-size-sm);
    color: var(--color-text-placeholder, #999);
  }
}

.hub-card {
  padding: var(--spacing-sm) var(--spacing-md);
  background-color: #fff;
  border-radius: var(--border-radius-lg);
  border: 1px solid #ebebeb;
  transition: all var(--transition-base);
  display: flex;
  flex-direction: column;
  gap: 4px;

  &:hover {
    box-shadow: 0 4px 6px -5px rgba(0, 0, 0, 0.3);
    transform: translateY(-2px);
  }

  &__header {
    .hub-card__avatar {
      width: 32px;
      height: 32px;
      background-color: #e8eaf6;
      border-radius: var(--border-radius-xl);
      flex-shrink: 0;
      overflow: hidden;

      img {
        width: 22px;
        height: 22px;
        object-fit: contain;
      }

      &-icon {
        font-size: 16px;
        color: var(--color-text-secondary, #666);
      }
    }

    .hub-card__info {
      min-width: 0;
    }

    .hub-card__name {
      font-size: var(--font-size-sm);
      font-weight: 600;
      color: var(--color-text-primary, #333);
    }

    .hub-card__version {
      margin-top: 1px;
      font-size: 11px;
    }
  }

  &__desc {
    font-size: 12px;
    color: var(--color-text-regular, #555);
    line-height: 1.5;
    display: -webkit-box;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
    overflow: hidden;
    text-overflow: ellipsis;
    word-break: break-all;
    min-height: 36px;
    max-height: 36px;
  }

  &__footer {
    padding-top: 2px;
    border-top: 1px solid #f0f0f0;

    .hub-card__tag {
      font-size: 10px;
      line-height: 16px;
    }

    .hub-card__meta {
      flex-shrink: 0;
    }

    .hub-card__stat {
      display: inline-flex;
      align-items: center;
      gap: 2px;
      white-space: nowrap;
      font-size: 11px;
    }

    .hub-card__time {
      white-space: nowrap;
      font-size: 11px;
    }
  }

  &__actions {
    margin-top: 2px;

    .hub-card__link {
      font-size: var(--font-size-xs);
      color: var(--color-primary);
      display: inline-flex;
      align-items: center;
      gap: 3px;
      text-decoration: none;

      &:hover {
        text-decoration: underline;
      }
    }
  }
}
</style>
