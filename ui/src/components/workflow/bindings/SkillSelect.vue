<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {SearchOutlined, CloseCircleFilled, AppstoreOutlined} from '@ant-design/icons-vue'
import type { SkillPackageVO } from '@/types'

const props = defineProps<{
  modelValue: string[]
  skills: SkillPackageVO[]
  categories: string[]
}>()

const emit = defineEmits<{
  'update:modelValue': [ids: string[]]
}>()

const popoverOpen = ref(false)
const searchText = ref('')

const skillsByCategory = computed(() => {
  const categoryList = props.categories.length > 0
    ? props.categories
    : [...new Set(props.skills.map((s) => s.category || '默认'))]
  const groups: { category: string; items: SkillPackageVO[] }[] = []
  categoryList.forEach((category) => {
    const items = props.skills.filter((s) => (s.category || '默认') === category)
    if (items.length > 0) groups.push({ category, items })
  })
  return groups
})

const selectedItems = computed(() =>
  props.skills.filter((s) => props.modelValue.includes(String(s.id))),
)

const selectedLabel = computed(() => {
  if (selectedItems.value.length === 0) return ''
  const first = selectedItems.value[0]?.name || ''
  return first
})

const selectedCount = computed(() => selectedItems.value.length)

const filteredGroups = computed(() => {
  const query = searchText.value.trim().toLowerCase()
  if (!query) return skillsByCategory.value
  return skillsByCategory.value
    .map((group) => ({
      ...group,
      items: group.items.filter(
        (item) =>
          (item.name || '').toLowerCase().includes(query) ||
          (item.description || '').toLowerCase().includes(query),
      ),
    }))
    .filter((group) => group.items.length > 0)
})

function toggleItem(id: string) {
  const next = new Set(props.modelValue)
  if (next.has(id)) {
    next.delete(id)
  } else {
    next.add(id)
  }
  emit('update:modelValue', [...next])
}

function clearAll() {
  emit('update:modelValue', [])
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
    <div class="multi-select-trigger" :class="{ placeholder: selectedCount === 0 }">
      <span v-if="selectedCount === 0" class="trigger-placeholder">选择技能包...</span>
      <span v-else class="trigger-content">
        <span class="trigger-name">
          <AppstoreOutlined style="color: #AB47BC;" />
          {{ selectedLabel }}
        </span>
        <span v-if="selectedCount > 1" class="trigger-count">+{{ selectedCount - 1 }}</span>
      </span>
      <CloseCircleFilled
        v-if="selectedCount > 0"
        class="trigger-clear"
        @click.stop="clearAll"
      />
    </div>
    <template #content>
      <div class="multi-select-dropdown">
        <div class="dropdown-search">
          <span class="search-icon"><SearchOutlined /></span>
          <input
            v-model="searchText"
            type="text"
            class="search-input"
            placeholder="搜索技能包..."
            @click.stop
          />
        </div>
        <div class="dropdown-list" :class="{ empty: !filteredGroups.length }">
          <template v-if="filteredGroups.length">
            <div
              v-for="group in filteredGroups"
              :key="group.category"
              class="select-group"
            >
              <div class="group-header">{{ group.category }}</div>
              <div
                v-for="item in group.items"
                :key="item.id"
                class="item-row"
                :class="{ selected: modelValue.includes(String(item.id)) }"
                @click="toggleItem(String(item.id))"
              >
                <ACheckbox
                  :checked="modelValue.includes(String(item.id))"
                  @click.stop
                  @change="toggleItem(String(item.id))"
                />
                <div class="item-text">
                  <span class="item-name">{{ item.name }}</span>
                  <span class="item-desc">{{ item.description || '暂无描述' }}</span>
                </div>
              </div>
            </div>
          </template>
          <div v-else class="dropdown-empty">无匹配的技能包</div>
        </div>
      </div>
    </template>
  </APopover>
</template>

<style scoped lang="scss">
.multi-select-trigger {
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

  &.placeholder {
    color: #bfbfbf;
  }
}

.trigger-placeholder {
  flex: 1;
  color: #bfbfbf;
}

.trigger-content {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: 6px;
  background-color: #ffffff;
  overflow: hidden;
}

.trigger-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #262626;
}

.trigger-count {
  flex-shrink: 0;
  padding: 0 5px;
  font-size: 11px;
  font-weight: 600;
  color: #1677ff;
  background: rgba(22, 119, 255, 0.08);
  border-radius: 4px;
  line-height: 18px;
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

.multi-select-dropdown {
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

.select-group {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.group-header {
  padding: 6px 12px 4px;
  font-size: 12px;
  font-weight: 600;
  color: #8c8c8c;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.item-row {
  display: flex;
  align-items: flex-start;
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

.item-text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.item-name {
  font-size: 13px;
  color: #262626;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-desc {
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
