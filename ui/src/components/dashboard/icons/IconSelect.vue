<script setup lang="ts">
/**
 * 图标选择器：从 Ant Design 图标中选择一个（用于快捷方式头像等）。
 *
 * @author huxuehao
 */
import { computed, ref } from 'vue'
import { CloseOutlined } from '@ant-design/icons-vue'
import { listIcons, resolveIcon } from './iconRegistry'

const props = defineProps<{ value?: string }>()
const emit = defineEmits<{ (e: 'update:value', v: string | undefined): void }>()

const open = ref(false)
const keyword = ref('')
const icons = listIcons()
const current = computed(() => resolveIcon(props.value))

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return icons
  return icons.filter((i) => i.name.toLowerCase().includes(kw))
})

function pick(name: string) {
  emit('update:value', name)
  open.value = false
}

function clear() {
  emit('update:value', undefined)
}
</script>

<template>
  <div class="icon-select">
    <a-popover v-model:open="open" trigger="click" placement="bottomLeft">
      <template #content>
        <div class="icon-pop">
          <a-input v-model:value="keyword" placeholder="搜索图标" allow-clear size="small" />
          <div class="icon-grid">
            <button
              v-for="i in filtered"
              :key="i.name"
              class="icon-cell"
              :class="{ active: i.name === value }"
              :title="i.name"
              @click="pick(i.name)"
            >
              <component :is="i.component" />
            </button>
          </div>
        </div>
      </template>
      <button class="icon-trigger">
        <component :is="current" v-if="current" />
        <span v-else class="icon-placeholder">选择图标</span>
      </button>
    </a-popover>
    <a-button v-if="value" type="text" size="small" title="清除" @click="clear">
      <template #icon><CloseOutlined /></template>
    </a-button>
  </div>
</template>

<style scoped lang="scss">
.icon-select {
  display: flex;
  align-items: center;
  gap: 4px;
}

.icon-trigger {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 36px;
  height: 32px;
  padding: 0 10px;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  background: #fff;
  color: #595959;
  font-size: 16px;
  cursor: pointer;
}

.icon-trigger:hover {
  border-color: #1677ff;
  color: #1677ff;
}

.icon-placeholder {
  font-size: 13px;
  color: #bbb;
}

.icon-pop {
  width: 260px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.icon-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 4px;
  max-height: 220px;
  overflow: auto;
}

.icon-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 30px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: #595959;
  font-size: 16px;
  cursor: pointer;
}

.icon-cell:hover {
  background: #f5f5f5;
  color: #1677ff;
}

.icon-cell.active {
  border-color: #1677ff;
  color: #1677ff;
}
</style>
