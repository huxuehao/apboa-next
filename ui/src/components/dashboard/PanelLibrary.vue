<script setup lang="ts">
/**
 * 面板库：按 category 列出所有已注册面板，点击添加到画布。
 *
 * @author huxuehao
 */
import { computed } from 'vue'
import { listPanelsByCategory } from './panels'

const emit = defineEmits<{ (e: 'add', type: string): void }>()

const groups = computed(() => listPanelsByCategory())
</script>

<template>
  <div class="panel-library">
    <div class="lib-title">面板库</div>
    <div v-for="(defs, category) in groups" :key="category" class="lib-group">
      <div class="group-title">{{ category }}</div>
      <div class="group-items">
        <button
          v-for="def in defs"
          :key="def.type"
          class="lib-item"
          @click="emit('add', def.type)"
        >
          <component :is="def.icon" v-if="def.icon" class="lib-icon" />
          <span class="lib-name">{{ def.name }}</span>
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.panel-library {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 16px;
}

.lib-title {
  font-size: 14px;
  font-weight: 600;
  color: #1a1a1a;
}

.lib-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.group-title {
  font-size: 13px;
  color: #999;
}

.group-items {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
}

.lib-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 12px 8px;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  background: #fff;
  color: #595959;
  cursor: pointer;
}

.lib-item:hover {
  border-color: #1677ff;
  color: #1677ff;
}

.lib-icon {
  font-size: 18px;
}

.lib-name {
  font-size: 13px;
}
</style>
