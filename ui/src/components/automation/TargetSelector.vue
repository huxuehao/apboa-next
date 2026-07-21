/**
 * 目标选择器组件
 * 支持 Agent 和 Workflow 的选择，带搜索和无限滚动
 *
 * @component
 */
<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { watchDebounced } from '@vueuse/core'
import { SearchOutlined, CloseCircleFilled } from '@ant-design/icons-vue'
import agentAvatar from '@/assets/avatar/agent.png'
import workflowAvatar from '@/assets/avatar/workflow.png'
import * as agentApi from '@/api/agent'
import * as workflowApi from '@/api/workflow'
import ApboaInfiniteLoading from '@/components/common/ApboaInfiniteLoading.vue'

interface TargetItem {
  id: string
  name: string
  description?: string
}

const props = defineProps<{
  targetType: 'AGENT' | 'WORKFLOW'
}>()

const modelValue = defineModel<TargetItem | null>({ default: null })

const visible = ref(false)
const keyword = ref('')
const list = ref<TargetItem[]>([])
const loading = ref(false)
const hasMore = ref(true)
const currentPage = ref(1)
const isFirstLoad = ref(true)
const infiniteLoadingKey = ref(0)
const dropdownRef = ref<HTMLElement | null>(null)

const avatarSrc = computed(() => {
  return props.targetType === 'AGENT' ? agentAvatar : workflowAvatar
})

const placeholderText = computed(() => {
  return props.targetType === 'AGENT' ? '选择智能体' : '选择工作流'
})

/**
 * 加载列表数据
 */
async function fetchPage(page: number) {
  loading.value = true
  try {
    if (props.targetType === 'AGENT') {
      const res = await agentApi.page({
        page,
        size: 20,
        name: keyword.value || undefined,
        enabled: true
      })
      const data = res.data.data
      const items = (data.records || []).map((item: { id: string | number; name: string; description?: string }) => ({
        id: String(item.id),
        name: item.name,
        description: item.description
      }))
      if (page === 1) {
        list.value = items
      } else {
        list.value = [...list.value, ...items]
      }
      hasMore.value = list.value.length < (data.total || 0)
    } else {
      const res = await workflowApi.workflowPage({
        page,
        size: 20,
        name: keyword.value || undefined,
        status: 'PUBLISHED'
      })
      const data = res.data.data
      const items = (data.records || []).map((item: { id?: string; name?: string; remark?: string }) => ({
        id: String(item.id || ''),
        name: item.name || '',
        description: item.remark
      }))
      if (page === 1) {
        list.value = items
      } else {
        list.value = [...list.value, ...items]
      }
      hasMore.value = list.value.length < (data.total || 0)
    }
    currentPage.value = page
  } catch (e) {
    console.error('加载列表失败:', e)
  } finally {
    loading.value = false
  }
}

/**
 * 无限滚动加载
 */
async function handleInfiniteLoading($state: {
  loaded: () => void
  complete: () => void
  error: () => void
}) {
  if (isFirstLoad.value) {
    isFirstLoad.value = false
    if (list.value.length > 0) {
      $state.loaded()
      return
    }
    try {
      await fetchPage(1)
      if (hasMore.value) {
        $state.loaded()
      } else {
        $state.complete()
      }
    } catch {
      isFirstLoad.value = true
      $state.error()
    }
    return
  }

  if (!hasMore.value || loading.value) {
    $state.complete()
    return
  }

  try {
    await fetchPage(currentPage.value + 1)
    if (hasMore.value) {
      $state.loaded()
    } else {
      $state.complete()
    }
  } catch {
    $state.error()
  }
}

/**
 * 选择目标
 */
function handleSelect(item: TargetItem) {
  modelValue.value = item
  visible.value = false
}

/**
 * 清除选择
 */
function handleClear(e: Event) {
  e.stopPropagation()
  modelValue.value = null
}

/**
 * 打开下拉面板
 */
function handleOpen() {
  visible.value = true
}

/**
 * 搜索
 */
function handleSearch() {
  list.value = []
  isFirstLoad.value = true
  infiniteLoadingKey.value++
}

/**
 * 点击外部关闭
 */
function handleClickOutside(e: MouseEvent) {
  if (dropdownRef.value && !dropdownRef.value.contains(e.target as Node)) {
    visible.value = false
  }
}

watchDebounced(keyword, () => {
  handleSearch()
}, { debounce: 300 })

// 监听 targetType 变化，重置列表
watch(() => props.targetType, () => {
  list.value = []
  isFirstLoad.value = true
  infiniteLoadingKey.value++
  keyword.value = ''
})

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})
</script>

<template>
  <div ref="dropdownRef" class="target-selector">
    <!-- 已选目标显示 -->
    <div class="target-display" @click="handleOpen">
      <template v-if="modelValue">
        <div class="target-selected">
          <img :src="avatarSrc" class="target-avatar" />
          <span class="target-name">{{ modelValue.name }}</span>
          <CloseCircleFilled class="target-clear" @click="handleClear" />
        </div>
      </template>
      <template v-else>
        <div class="target-placeholder">
          <img :src="avatarSrc" class="target-avatar" />
          <span>{{ placeholderText }}</span>
        </div>
      </template>
    </div>

    <!-- 下拉面板 -->
    <Transition name="dropdown">
      <div v-if="visible" class="target-dropdown">
        <div class="dropdown-header">
          <AInput
            v-model:value="keyword"
            :placeholder="`搜索${targetType === 'AGENT' ? '智能体' : '工作流'}名称`"
          >
            <template #prefix>
              <SearchOutlined />
            </template>
          </AInput>
        </div>

        <div class="dropdown-body">
          <div v-if="list.length === 0 && !loading && !isFirstLoad" class="dropdown-empty">
            <AEmpty description="暂无数据" />
          </div>

          <div v-else class="dropdown-list">
            <div
              v-for="item in list"
              :key="item.id"
              class="dropdown-item"
              :class="{ active: modelValue?.id === item.id }"
              @click="handleSelect(item)"
            >
              <img :src="avatarSrc" class="item-avatar" />
              <div class="item-info">
                <div class="item-name">{{ item.name }}</div>
                <div v-if="item.description" class="item-desc">{{ item.description }}</div>
              </div>
            </div>

            <ApboaInfiniteLoading
              :loading-key="infiniteLoadingKey"
              @infinite="handleInfiniteLoading"
            />
          </div>
        </div>
      </div>
    </Transition>
  </div>
</template>

<style scoped lang="scss">
.target-selector {
  position: relative;
  width: 100%;
}

.target-display {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--border-radius-base);
  cursor: pointer;
  transition: border-color 0.2s ease;
  min-height: 40px;
  background-color: #F2F4F7;

  &:hover {
    border-color: var(--color-primary);
  }
}

.target-selected {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.target-avatar {
  width: 24px;
  height: 24px;
  flex-shrink: 0;
}

.target-name {
  flex: 1;
  font-size: 14px;
  color: var(--color-text);
}

.target-clear {
  color: var(--color-text-secondary);
  cursor: pointer;
  flex-shrink: 0;

  &:hover {
    color: var(--color-text);
  }
}

.target-placeholder {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--color-text-secondary);
  font-size: 14px;
}

.target-dropdown {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  margin-top: 4px;
  background: #fff;
  border: 1px solid var(--color-border);
  border-radius: var(--border-radius-base);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  z-index: 1000;
  max-height: 400px;
  display: flex;
  flex-direction: column;
}

.dropdown-header {
  padding: 12px;
  border-bottom: 1px solid var(--color-border);
}

.dropdown-body {
  flex: 1;
  overflow-y: auto;
  max-height: 340px;
}

.dropdown-empty {
  padding: 24px;
  text-align: center;
}

.dropdown-list {
  padding: 4px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.dropdown-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  border-radius: 6px;
  cursor: pointer;
  transition: background-color 0.2s ease;

  &:hover {
    background-color: rgba(0, 0, 0, 0.02);
  }

  &.active {
    background-color: #FAFAFA;
  }
}

.item-avatar {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
}

.item-info {
  flex: 1;
  min-width: 0;
}

.item-name {
  font-size: 14px;
  color: var(--color-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.item-desc {
  font-size: 12px;
  color: var(--color-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-top: 2px;
}

// 下拉动画
.dropdown-enter-active,
.dropdown-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.dropdown-enter-from,
.dropdown-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
