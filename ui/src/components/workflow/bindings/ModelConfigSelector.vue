<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { SearchOutlined, CloseCircleFilled, SlackOutlined } from '@ant-design/icons-vue'
import type { ModelConfigVO, ModelProviderVO } from '@/types'

const props = defineProps<{
  modelValue?: string
  providers: ModelProviderVO[]
  models: ModelConfigVO[]
}>()

const emit = defineEmits<{
  'update:modelValue': [modelId: string]
  clear: []
}>()

const popoverOpen = ref(false)
const searchText = ref('')

const selectedLabel = computed(() => {
  const model = props.models.find((m) => String(m.id) === String(props.modelValue || ''))
  return model?.name || ''
})

const groupedModels = computed(() => {
  const groups: { providerId: string; providerName: string; models: ModelConfigVO[] }[] = []
  const modelGroups = new Map<string, ModelConfigVO[]>()
  props.models.forEach((model) => {
    const pid = String(model.providerId)
    if (!modelGroups.has(pid)) modelGroups.set(pid, [])
    modelGroups.get(pid)!.push(model)
  })
  props.providers.forEach((provider) => {
    const pid = String(provider.id)
    const grouped = modelGroups.get(pid)
    if (grouped && grouped.length > 0) {
      groups.push({ providerId: pid, providerName: provider.name, models: grouped })
    }
  })
  modelGroups.forEach((grouped, pid) => {
    if (!groups.some((g) => g.providerId === pid)) {
      groups.push({ providerId: pid, providerName: '未知供应商', models: grouped })
    }
  })
  return groups
})

const filteredGroups = computed(() => {
  const query = searchText.value.trim().toLowerCase()
  if (!query) return groupedModels.value
  return groupedModels.value
    .map((group) => ({
      ...group,
      models: group.models.filter(
        (m) =>
          (m.name || '').toLowerCase().includes(query) ||
          (m.modelId || '').toLowerCase().includes(query) ||
          (m.description || '').toLowerCase().includes(query),
      ),
    }))
    .filter((group) => group.models.length > 0)
})

function selectModel(modelId: string) {
  emit('update:modelValue', modelId)
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
    <div class="model-selector-trigger" :class="{ placeholder: !selectedLabel }">
      <span v-if="selectedLabel" class="trigger-selected-label">
        <SlackOutlined style="color: #52C41A;"/>
        {{ selectedLabel }}
      </span>
      <span v-else class="trigger-placeholder-text">选择模型...</span>
      <CloseCircleFilled
        v-if="selectedLabel"
        class="trigger-clear"
        @click.stop="clearSelection"
      />
    </div>
    <template #content>
      <div class="model-selector-dropdown">
        <div class="dropdown-search">
          <span class="search-icon"><SearchOutlined /></span>
          <input
            v-model="searchText"
            type="text"
            class="search-input"
            placeholder="搜索模型名称..."
            @click.stop
          />
        </div>
        <div class="dropdown-list" :class="{ empty: !filteredGroups.length }">
          <template v-if="filteredGroups.length">
            <div
              v-for="group in filteredGroups"
              :key="group.providerId"
              class="model-group"
            >
              <div class="model-group-header">{{ group.providerName }}</div>
              <div
                v-for="model in group.models"
                :key="model.id"
                class="model-row"
                :class="{ selected: String(model.id) === String(modelValue || '') }"
                @click="selectModel(String(model.id))"
              >
                <span class="model-row-name">{{ model.name }}</span>
                <span class="model-row-id">{{ model.modelId }}</span>
              </div>
            </div>
          </template>
          <div v-else class="dropdown-empty">无匹配的模型</div>
        </div>
      </div>
    </template>
  </APopover>
</template>

<style scoped lang="scss">
.model-selector-trigger {
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

.model-selector-dropdown {
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

.model-group {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.model-group-header {
  padding: 6px 12px 4px;
  font-size: 12px;
  font-weight: 600;
  color: #8c8c8c;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.model-row {
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

.model-row-name {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  color: #262626;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.model-row-id {
  flex-shrink: 0;
  font-size: 12px;
  color: #a8a8a8;
  max-width: 120px;
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
