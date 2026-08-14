<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { SearchOutlined, CloseCircleFilled } from '@ant-design/icons-vue'
import knowledgeAvatar from '@/assets/avatar/knowledgebase.png'
import bailianLogo from '@/assets/brand/bailian-color.png'
import difyLogo from '@/assets/brand/dify-color.png'
import ragflowLogo from '@/assets/brand/ragflow.png'
import type { KnowledgeBaseConfigVO } from '@/types'

const props = defineProps<{
  modelValue?: string
  knowledgeBases: KnowledgeBaseConfigVO[]
}>()

const emit = defineEmits<{
  'update:modelValue': [id: string]
  clear: []
}>()

const popoverOpen = ref(false)
const searchText = ref('')

/** 知识库类型中文名 */
const KB_TYPE_LABELS: Record<string, string> = {
  BAILIAN: '百炼',
  DIFY: 'Dify',
  RAGFLOW: 'Ragflow',
  LOCAL: '本地',
}

/** 知识库类型图标 */
const KB_TYPE_ICONS: Record<string, string> = {
  BAILIAN: bailianLogo,
  DIFY: difyLogo,
  RAGFLOW: ragflowLogo,
  LOCAL: knowledgeAvatar,
}

/** 分类显示顺序 */
const KB_TYPE_ORDER = ['BAILIAN', 'DIFY', 'RAGFLOW', 'LOCAL']

const selectedItem = computed(() =>
  props.knowledgeBases.find((kb) => String(kb.id) === String(props.modelValue || '')) || null,
)

const selectedLabel = computed(() => selectedItem.value?.name || '')

const selectedIcon = computed(() => {
  if (!selectedItem.value) return knowledgeAvatar
  return KB_TYPE_ICONS[selectedItem.value.kbType] || knowledgeAvatar
})

function typeLabel(kbType: string): string {
  return KB_TYPE_LABELS[kbType] || kbType || '其他'
}

const groupedKnowledgeBases = computed(() => {
  const groups: { kbType: string; kbTypeLabel: string; items: KnowledgeBaseConfigVO[] }[] = []
  const typeGroups = new Map<string, KnowledgeBaseConfigVO[]>()
  props.knowledgeBases.forEach((kb) => {
    const type = String(kb.kbType || '')
    if (!typeGroups.has(type)) typeGroups.set(type, [])
    typeGroups.get(type)!.push(kb)
  })
  // 按既定顺序输出已知分类，未知分类追加到末尾
  const orderedTypes = [
    ...KB_TYPE_ORDER.filter((type) => typeGroups.has(type)),
    ...[...typeGroups.keys()].filter((type) => !KB_TYPE_ORDER.includes(type)),
  ]
  orderedTypes.forEach((type) => {
    groups.push({ kbType: type, kbTypeLabel: typeLabel(type), items: typeGroups.get(type)! })
  })
  return groups
})

const filteredGroups = computed(() => {
  const query = searchText.value.trim().toLowerCase()
  if (!query) return groupedKnowledgeBases.value
  return groupedKnowledgeBases.value
    .map((group) => ({
      ...group,
      items: group.items.filter(
        (kb) =>
          (kb.name || '').toLowerCase().includes(query) ||
          (kb.description || '').toLowerCase().includes(query),
      ),
    }))
    .filter((group) => group.items.length > 0)
})

function selectKnowledgeBase(id: string) {
  emit('update:modelValue', id)
  popoverOpen.value = false
  searchText.value = ''
}

function clearSelection() {
  emit('clear')
}

watch(popoverOpen, (open) => {
  if (!open) searchText.value = ''
})
</script>

<template>
  <APopover
    v-model:open="popoverOpen"
    trigger="click"
    placement="bottomLeft"
    :overlay-inner-style="{ padding: 0 }"
  >
    <div class="knowledge-selector-trigger" :class="{ placeholder: !selectedLabel }">
      <span v-if="selectedLabel" class="trigger-selected-label">
        <img :src="selectedIcon" class="select-icon" />
        {{ selectedLabel }}
      </span>
      <span v-else class="trigger-placeholder-text">选择知识库...</span>
      <CloseCircleFilled
        v-if="selectedLabel"
        class="trigger-clear"
        @click.stop="clearSelection"
      />
    </div>
    <template #content>
      <div class="knowledge-selector-dropdown">
        <div class="dropdown-search">
          <span class="search-icon"><SearchOutlined /></span>
          <input
            v-model="searchText"
            type="text"
            class="search-input"
            placeholder="搜索知识库..."
            @click.stop
          />
        </div>
        <div class="dropdown-list" :class="{ empty: !filteredGroups.length }">
          <template v-if="filteredGroups.length">
            <div
              v-for="group in filteredGroups"
              :key="group.kbType"
              class="knowledge-group"
            >
              <div class="knowledge-group-header">{{ group.kbTypeLabel }}</div>
              <div
                v-for="kb in group.items"
                :key="kb.id"
                class="knowledge-row"
                :class="{ selected: String(kb.id) === String(modelValue || '') }"
                @click="selectKnowledgeBase(String(kb.id))"
              >
                <img :src="KB_TYPE_ICONS[kb.kbType] || knowledgeAvatar" class="row-icon" />
                <div class="row-text">
                  <span class="row-name">{{ kb.name }}</span>
                  <span class="row-desc">{{ kb.description || typeLabel(kb.kbType) }}</span>
                </div>
              </div>
            </div>
          </template>
          <div v-else class="dropdown-empty">无匹配的知识库</div>
        </div>
      </div>
    </template>
  </APopover>
</template>

<style scoped lang="scss">
.knowledge-selector-trigger {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  min-width: 0;
  box-sizing: border-box;
  padding: 2px 8px;
  background-color: #f2f4f7;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  transition: border-color 0.2s;
  min-height: 32px;
  position: relative;

  &.placeholder {
    color: #bfbfbf;
  }
}

.trigger-selected-label {
  flex: 1;
  min-width: 0;
  padding: 2px 8px;
  border-radius: 6px;
  background-color: #ffffff;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #262626;
  display: flex;
  align-items: center;
  gap: 4px;

  .select-icon {
    width: 16px;
    height: 16px;
    flex-shrink: 0;
    object-fit: contain;
  }
}

.trigger-placeholder-text {
  flex: 1;
  color: #bfbfbf;
}

.trigger-clear {
  flex-shrink: 0;
  color: #bfbfbf;
  font-size: 12px;
  cursor: pointer;
  transition: color 0.2s;

  &:hover {
    color: #595959;
  }
}

.knowledge-selector-dropdown {
  width: 360px;
  padding: 8px;
}

.dropdown-search {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border-bottom: 1px solid #f0f0f0;
  position: sticky;
  top: 0;
  background: #fff;
  z-index: 1;
  border-radius: 8px 8px 0 0;
}

.search-icon {
  color: #bfbfbf;
  font-size: 14px;
  flex-shrink: 0;
}

.search-input {
  flex: 1;
  border: none;
  outline: none;
  font-size: 13px;
  color: #262626;
  background: transparent;

  &::placeholder {
    color: #bfbfbf;
  }
}

.dropdown-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 5px;
  max-height: 320px;
  overflow-y: auto;

  &.empty {
    max-height: auto;
  }
}

.knowledge-group {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.knowledge-group-header {
  padding: 6px 12px 4px;
  font-size: 12px;
  font-weight: 600;
  color: #8c8c8c;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.knowledge-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.15s;

  &:hover {
    background: #f5f5f5;
  }

  &.selected {
    background: #f5f5f5;
  }
}

.row-icon {
  width: 18px;
  height: 18px;
  flex-shrink: 0;
  object-fit: contain;
}

.row-text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.row-name {
  font-size: 13px;
  color: #262626;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row-desc {
  font-size: 11px;
  color: #a8a8a8;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dropdown-empty {
  padding: 24px 16px;
  text-align: center;
  color: #bfbfbf;
  font-size: 13px;
}
</style>
