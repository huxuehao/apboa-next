/**
 * SkillHub 技能市场页面
 *
 * @author huxuehao
 */
<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Modal, message } from 'ant-design-vue'
import {
  SearchOutlined,
  AppstoreOutlined,
  CloudDownloadOutlined,
  ReloadOutlined,
  ArrowLeftOutlined,
  KeyOutlined,
} from '@ant-design/icons-vue'
import * as skillHubApi from '@/api/skillHub'
import type { SkillsHubVO } from '@/types'
import { RouteNames } from '@/router/constants'
import ApboaInfiniteLoading from '@/components/common/ApboaInfiniteLoading.vue'
import cache from '@/utils/cache'

const router = useRouter()

/** 搜索表单 localStorage 缓存键 */
const SEARCH_FORM_CACHE_KEY = 'skill-hub-search-form'

/**
 * 搜索表单
 */
const searchForm = reactive({
  keyword: '通用',
  category: undefined as string | undefined,
  source: undefined as string | undefined,
  labels: '',
  sortBy: 'updated_at' as string,
  order: 'desc' as string,
})

/**
 * 分页加载状态
 */
const currentPage = ref(1)
const loading = ref(false)
const skillList = ref<SkillsHubVO[]>([])
const hasMore = ref(true)
const searched = ref(false)
const pageSize = 30
const isFirstLoad = ref(true)
const infiniteLoadingKey = ref(0)

/**
 * 来源选项
 */
const sourceOptions = [
  { label: '全部', value: undefined },
  { label: '官方', value: 'official' },
  { label: '社区', value: 'community' },
  { label: '企业', value: 'enterprise' },
  { label: 'ClawHub', value: 'clawhub' },
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
  { label: '生活服务', value: 'life-service' },
]

/**
 * 排序选项
 */
const sortByOptions = [
  { label: '更新时间', value: 'updated_at' },
  { label: '下载量', value: 'downloads' },
  { label: '收藏数', value: 'stars' },
  { label: '安装量', value: 'installs' },
  { label: '评分', value: 'score' },
]

/**
 * 返回技能列表
 */
function handleBack() {
  router.push({ name: RouteNames.SKILL })
}

/**
 * 执行搜索（重置状态并触发无限加载）
 */
function handleSearch() {
  // 持久化当前搜索表单，下次进入页面自动恢复
  cache.local.setJSON(SEARCH_FORM_CACHE_KEY, { ...searchForm })
  currentPage.value = 1
  skillList.value = []
  hasMore.value = true
  searched.value = true
  isFirstLoad.value = true
  infiniteLoadingKey.value++
}

/**
 * 无限加载处理
 *
 * @param $state 加载状态对象
 */
async function handleInfiniteLoading($state: {
  loaded: () => void
  complete: () => void
  error: () => void
}) {
  // 首次加载
  if (isFirstLoad.value) {
    isFirstLoad.value = false
    if (skillList.value.length > 0) {
      $state.loaded()
      return
    }
    loading.value = true
    try {
      const response = await skillHubApi.search({
        keyword: searchForm.keyword || undefined,
        category: searchForm.category || undefined,
        source: searchForm.source,
        labels: searchForm.labels || undefined,
        sortBy: searchForm.sortBy,
        order: searchForm.order,
        page: 1,
      })
      const result = response.data.data
      skillList.value = result || []
      if (!result || result.length === 0) {
        hasMore.value = false
        $state.complete()
      } else if (result.length < pageSize) {
        hasMore.value = false
        $state.complete()
      } else {
        $state.loaded()
      }
    } catch {
      isFirstLoad.value = true
      $state.error()
    } finally {
      loading.value = false
    }
    return
  }

  // 非首次加载
  if (!hasMore.value || loading.value) {
    $state.complete()
    return
  }

  currentPage.value++
  try {
    const response = await skillHubApi.search({
      keyword: searchForm.keyword || undefined,
      category: searchForm.category || undefined,
      source: searchForm.source,
      labels: searchForm.labels || undefined,
      sortBy: searchForm.sortBy,
      order: searchForm.order,
      page: currentPage.value,
    })
    const result = response.data.data
    if (!result || result.length === 0) {
      hasMore.value = false
      $state.complete()
    } else {
      skillList.value = [...skillList.value, ...result]
      if (result.length < pageSize) {
        hasMore.value = false
        $state.complete()
      } else {
        $state.loaded()
      }
    }
  } catch {
    currentPage.value--
    $state.error()
  }
}

/**
 * 重置搜索
 */
function handleReset() {
  // 用户显式重置后清除缓存，下次进入页面保持初始状态
  cache.local.remove(SEARCH_FORM_CACHE_KEY)
  searchForm.keyword = ''
  searchForm.category = undefined
  searchForm.source = undefined
  searchForm.labels = ''
  searchForm.sortBy = 'updated_at'
  searchForm.order = 'desc'
  currentPage.value = 1
  skillList.value = []
  hasMore.value = true
  searched.value = false
  isFirstLoad.value = true
  infiniteLoadingKey.value++
}

/**
 * 获取分类中文标签
 */
function getCategoryLabel(value: string): string {
  const option = categoryOptions.find(o => o.value === value)
  return option?.label || value
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
    day: '2-digit',
  })
}

/**
 * 打开技能主页
 */
function handleOpenHomepage(item: SkillsHubVO) {
  if (item.homepage) {
    window.open(item.homepage, '_blank')
  }
}

/**
 * 导入技能（弹窗确认，确认按钮带加载动画）
 */
function handleImport(item: SkillsHubVO) {
  Modal.confirm({
    title: '确认导入',
    content: `确定要导入技能包「${item.name}」吗？导入后分类默认为「SkillHub安装」。`,
    okText: '确认导入',
    cancelText: '取消',
    onOk: async () => {
      try {
        const response = await skillHubApi.download(item.slug, 'SkillHub安装', item.name)
        const resData = response.data
        if (resData.data.importedCount > 0) {
          message.success(`导入成功，共 ${resData.data?.importedCount} 个技能`)
          router.push({ name: RouteNames.SKILL, query: { refresh: '1' } })
        } else if (resData.data.skippedCount > 0) {
          message.warning('导入失败，技能包已存在')
          throw new Error('SKIPPED')
        } else {
          message.warning(`导入失败，${resData.data.hintMessage}`)
          throw new Error('FAILED')
        }
      } catch (e: unknown) {
        const err = e as Error
        if (err?.message !== 'SKIPPED' && err?.message !== 'FAILED') {
          message.error('导入失败，请稍后重试')
        }
        throw e
      }
    },
  })
}

/**
 * 页面挂载时恢复缓存的搜索表单并自动执行搜索
 */
onMounted(() => {
  let saved: Record<string, unknown> | null = null
  try {
    saved = cache.local.getJSON(SEARCH_FORM_CACHE_KEY) as Record<string, unknown> | null
  } catch {
    // 缓存数据损坏时静默忽略
  }
  if (!saved || typeof saved !== 'object' || Object.keys(saved).length === 0) {
    handleSearch()
    return
  }
  Object.assign(searchForm, {
    keyword: saved.keyword ?? '通用',
    category: saved.category ?? undefined,
    source: saved.source ?? undefined,
    labels: saved.labels ?? '',
    sortBy: saved.sortBy ?? 'updated_at',
    order: saved.order ?? 'desc',
  })
  handleSearch()
})
</script>

<template>
  <div class="skill-hub-page">
    <!-- 顶部导航 -->
    <section class="intro-section">
      <div class="intro-nav">
        <AButton type="link" @click="handleBack" class="back-btn">
          <ArrowLeftOutlined />
          <span>返回技能列表</span>
        </AButton>
      </div>
      <h3 class="intro-title">SkillHub 技能市场</h3>
      <p class="intro-desc text-secondary">
        浏览和发现来自社区与官方的技能包，一键导入即可为智能体装配专业领域知识与处理逻辑。
      </p>
    </section>

    <!-- 筛选区域 -->
    <section class="filter-section">
      <div class="filter-row">
        <AInput
          v-model:value="searchForm.keyword"
          placeholder="输入关键词搜索技能（标题/描述）"
          allow-clear
          style="width: 280px"
          @pressEnter="handleSearch"
        >
          <template #prefix>
            <SearchOutlined class="text-secondary" />
          </template>
        </AInput>
        <ASelect
          v-model:value="searchForm.source"
          :options="sourceOptions"
          placeholder="来源"
          allow-clear
          style="width: 140px"
        />
        <ASelect
          v-model:value="searchForm.category"
          :options="categoryOptions"
          placeholder="一级标签"
          allow-clear
          style="width: 180px"
        />
        <ASelect
          v-model:value="searchForm.sortBy"
          :options="sortByOptions"
          placeholder="排序字段"
          style="width: 140px"
        />
        <ASelect
          v-model:value="searchForm.order"
          :options="[{ label: '降序', value: 'desc' }, { label: '升序', value: 'asc' }]"
          style="width: 100px"
        />
        <AButton type="primary" :loading="loading" @click="handleSearch">
          <SearchOutlined />
          搜索
        </AButton>
        <AButton @click="handleReset">
          <ReloadOutlined />
          重置
        </AButton>
      </div>
    </section>

    <!-- 卡片列表区域 -->
    <section class="card-section">
      <!-- 搜索结果 -->
      <template v-if="searched">
        <div v-if="!loading && skillList.length === 0 && !hasMore" class="hub-empty flex-col flex-center">
          <p class="hub-empty__text">暂无搜索结果</p>
          <p class="hub-empty__desc">尝试调整搜索条件或关键词</p>
        </div>
        <template v-else>
          <div v-if="skillList.length > 0" class="card-grid">
            <div
              v-for="item in skillList"
              :key="item.name"
              class="hub-card"
            >
              <!-- 头部 -->
              <div class="hub-card__header flex items-start gap-sm">
                <div class="hub-card__avatar flex-center">
                  <img v-if="item.iconUrl" :src="item.iconUrl" alt="icon" />
                  <AppstoreOutlined v-else class="hub-card__avatar-icon" />
                </div>
                <div class="hub-card__info flex-1">
                  <!-- 标题行：名称 + 导入按钮 -->
                  <div class="hub-card__title-row flex items-center gap-sm">
                    <ATooltip :title="item.homepage ? '点击访问主页' : item.name">
                      <div class="hub-card__name-wrapper">
                        <span
                          class="hub-card__name truncate"
                          :class="{ 'hub-card__name--link': item.homepage }"
                          @click="handleOpenHomepage(item)"
                        >{{ item.name }}</span>
                      </div>
                    </ATooltip>
                    <ATooltip title="导入技能包">
                      <AButton
                        type="text"
                        size="small"
                        class="hub-card__import-btn"
                        @click.stop="handleImport(item)"
                      >
                        <CloudDownloadOutlined />
                      </AButton>
                    </ATooltip>
                  </div>
                  <!-- 元信息行：版本 + 下载量 -->
                  <div class="hub-card__meta-row flex items-center gap-sm">
                    <span class="hub-card__version text-xs text-secondary">版本: {{ item.version || '-' }}</span>
                    <span class="hub-card__stat text-xs text-secondary">
                      下载量: {{ item.downloads || 0 }}
                    </span>
                  </div>
                </div>
              </div>

              <!-- 描述 -->
              <div class="hub-card__desc line-clamp-3" :title="item.description">
                {{ item.description || '暂无描述' }}
              </div>

              <!-- 底部：标签 + 时间 -->
              <div class="hub-card__footer flex items-center justify-between">
                <div class="hub-card__tags flex items-center gap-xs">
                  <ATag v-if="item.category" color="default" class="tag">{{ getCategoryLabel(item.category) }}</ATag>
                  <ATooltip v-if="item.requiresApiKey === 'true'" title="需要 API Key">
                    <KeyOutlined class="hub-card__apikey-icon" />
                  </ATooltip>
                </div>
                <span class="hub-card__time text-xs text-placeholder">{{ formatTime(item.updatedAt) }}</span>
              </div>
            </div>
          </div>

          <!-- 无限滚动加载 -->
          <ApboaInfiniteLoading
            :loading-key="infiniteLoadingKey"
            @infinite="handleInfiniteLoading"
          />
        </template>
      </template>

      <!-- 初始状态 -->
      <div v-else class="hub-init flex-col flex-center">
        <p class="hub-init__title">探索技能市场</p>
        <p class="hub-init__desc">输入关键词并点击搜索，浏览和发现来自社区与官方的技能包</p>
      </div>
    </section>
  </div>
</template>

<style scoped lang="scss">
.skill-hub-page {
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
    position: sticky;
    top: 0;
    z-index: 10;
    background-color: var(--color-bg);
    padding: var(--spacing-base) 0;
    margin-bottom: var(--spacing-lg);
    flex-shrink: 0;

    .filter-row {
      display: flex;
      align-items: center;
      gap: var(--spacing-sm);
    }
  }

  .card-section {
    position: relative;
    min-height: 200px;

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

  .hub-empty {
    padding: var(--spacing-3xl) 0;
    margin-top: 180px;

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
    padding: var(--spacing-3xl) 0;
    margin-top: 180px;

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

  .hub-card {
    min-height: 180px;
    padding: var(--spacing-md);
    background-color: #fff;
    border-radius: var(--border-radius-lg);
    border: 1px solid #ebebeb;
    transition: all var(--transition-base);
    display: flex;
    flex-direction: column;
    gap: var(--spacing-sm);

    &:hover {
      box-shadow: 0 4px 6px -5px rgba(0, 0, 0, 0.3);
      transform: translateY(-2px);
    }

    &__header {
      .hub-card__avatar {
        width: 40px;
        height: 40px;
        border-radius: var(--border-radius-xl);
        flex-shrink: 0;
        overflow: hidden;
        margin-top: 2px;

        img {
          width: 40px;
          height: 40px;
          object-fit: contain;
        }

        &-icon {
          font-size: 18px;
          color: var(--color-text-secondary, #666);
        }
      }

      .hub-card__info {
        min-width: 0;
      }

      .hub-card__title-row {
        min-width: 0;

        :deep(.ant-tooltip-trigger) {
          min-width: 0;
          overflow: hidden;
        }
      }

      .hub-card__name-wrapper {
        flex: 1;
        min-width: 0;
        overflow: hidden;
      }

      .hub-card__name {
        font-size: var(--font-size-base);
        font-weight: 600;
        color: var(--color-text-primary, #333);
        cursor: default;
        display: block;

        &--link {
          cursor: pointer;
          color: var(--color-text-primary, #333);

          &:hover {
            color: var(--color-primary);
          }
        }
      }

      .hub-card__meta-row {
        margin-top: 4px;
        min-width: 0;
      }

      .hub-card__version {
        font-size: var(--font-size-xs);
        white-space: nowrap;
        flex-shrink: 0;
      }

      .hub-card__stat {
        display: inline-flex;
        align-items: center;
        gap: 2px;
        white-space: nowrap;
        flex-shrink: 0;
      }

      .hub-card__import-btn {
        flex-shrink: 0;
      }
    }

    &__desc {
      font-size: var(--font-size-sm);
      color: var(--color-text-regular, #555);
      line-height: 1.6;
      display: -webkit-box;
      -webkit-box-orient: vertical;
      -webkit-line-clamp: 3;
      overflow: hidden;
      text-overflow: ellipsis;
      word-break: break-all;
      min-height: 65px;
      max-height: 65px;
    }

    &__footer {
      padding-top: var(--spacing-xs);

      .hub-card__time {
        white-space: nowrap;
        font-size: 11px;
        flex-shrink: 0;
      }

      .hub-card__apikey-icon {
        font-size: 14px;
        color: #faad14;
        flex-shrink: 0;
      }
    }
  }
}
</style>
