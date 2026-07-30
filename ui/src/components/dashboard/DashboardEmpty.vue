<script setup lang="ts">
/**
 * 工作台空状态引导：无面板时展示友好插画与引导，指引用户进入设计器。
 * 遵循简约扁平规范：单色灰阶 + 主题蓝点缀，无渐变/霓虹/emoji/炫技动画。
 *
 * @author huxuehao
 */
import { EditOutlined } from '@ant-design/icons-vue'

const emit = defineEmits<{ (e: 'create'): void }>()
</script>

<template>
  <div class="dash-empty">
    <svg class="de-art" viewBox="0 0 240 160" fill="none" xmlns="http://www.w3.org/2000/svg">
      <rect x="20" y="24" width="200" height="118" rx="10" fill="#f7f8fa" stroke="#e8e8e8" />
      <rect x="34" y="40" width="80" height="46" rx="6" fill="#fff" stroke="#e2e5ea" />
      <rect x="122" y="40" width="84" height="26" rx="6" fill="#fff" stroke="#e2e5ea" />
      <rect x="122" y="72" width="84" height="14" rx="4" fill="#fff" stroke="#e2e5ea" />
      <rect x="34" y="96" width="172" height="30" rx="6" fill="#fff" stroke="#e2e5ea" />
      <!-- 迷你柱状 -->
      <rect x="44" y="66" width="10" height="12" rx="2" fill="#c8d3e6" />
      <rect x="60" y="58" width="10" height="20" rx="2" fill="#9fb6de" />
      <rect x="76" y="52" width="10" height="26" rx="2" fill="#1677ff" />
      <rect x="92" y="62" width="10" height="16" rx="2" fill="#c8d3e6" />
      <!-- 占位文本条 -->
      <rect x="132" y="48" width="52" height="4" rx="2" fill="#e2e5ea" />
      <rect x="132" y="56" width="34" height="4" rx="2" fill="#eef0f3" />
      <!-- 加号提示圈 -->
      <circle cx="196" cy="120" r="16" fill="#fff" stroke="#1677ff" stroke-dasharray="3 3" />
      <path d="M196 113v14M189 120h14" stroke="#1677ff" stroke-width="2" stroke-linecap="round" />
    </svg>
    <h3 class="de-title">还没有你的工作台</h3>
    <p class="de-desc">进入设计器，拖拽面板、绑定数据集，几步搭出属于你的专属数据看板。</p>
    <a-button type="text" @click="emit('create')">
      <template #icon><EditOutlined /></template>
      进入设计器开始搭建
    </a-button>
  </div>
</template>

<style scoped lang="scss">
.dash-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  height: 100%;
  padding: 40px 24px;
}

.de-art {
  width: 240px;
  max-width: 60%;
  height: auto;
  margin-bottom: 6px;
}

.de-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #1a1a1a;
}

.de-desc {
  margin: 0 0 6px;
  max-width: 420px;
  font-size: 13px;
  color: #999;
  line-height: 1.7;
  text-align: center;
}
</style>
