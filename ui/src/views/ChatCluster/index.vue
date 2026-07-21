<script setup lang="ts">
/**
 * 对话广场页面（骨架屏模式）
 * 用于聚合展示所有可访问的智能体，方便用户集中浏览和使用。
 *
 * @author huxuehao
 */
import { ref, onMounted, onUnmounted } from 'vue'

// 彩蛋：停留超过5秒的幽默提示
const easterEggVisible = ref(false)
const easterEggStep = ref(0)
let eggTimer: ReturnType<typeof setTimeout> | null = null
let stepTimer: ReturnType<typeof setTimeout> | null = null

// 幽默文案逐句揭晓
const messages = [
  '你盯着这页看了5秒了...还在等什么？',
  '别看了别看了，这个页面根本没做完！',
  '不过...好消息是总会有见面的一天的。',
  '去忙正事吧，别在这骨架屏前发呆了~',
]

function showEasterEgg() {
  easterEggVisible.value = true
  easterEggStep.value = 0
  stepTimer = setTimeout(() => {
    easterEggStep.value = 1
    stepTimer = setTimeout(() => {
      easterEggStep.value = 2
      stepTimer = setTimeout(() => {
        easterEggStep.value = 3
      }, 3000)
    }, 3000)
  }, 1500)
}

function dismissEgg() {
  easterEggVisible.value = false
  easterEggStep.value = 0
  if (stepTimer) clearTimeout(stepTimer)
}

onMounted(() => {
  eggTimer = setTimeout(showEasterEgg, 5000)
})

onUnmounted(() => {
  if (eggTimer) clearTimeout(eggTimer)
  if (stepTimer) clearTimeout(stepTimer)
})
</script>

<script lang="ts">
export default {
  name: 'ChatClusterView'
}
</script>

<template>
  <div class="chat-cluster-container">
    <!-- 对话广场头部 -->
    <div class="plaza-header">
      <h1 class="plaza-title">
        对话广场
      </h1>
      <p class="plaza-desc">
        汇聚所有可访问的智能体，一处浏览、即时对话，高效完成各类任务。
      </p>
    </div>

    <!-- 搜索栏骨架屏 -->
    <div class="section">
      <div class="search-bar-skeleton">
        <div class="skeleton-search-icon"></div>
        <div class="skeleton-text skeleton-search-text"></div>
      </div>
    </div>

    <!-- 分类标签骨架屏 -->
    <div class="section">
      <div class="category-bar-skeleton">
        <div v-for="i in 6" :key="i" class="skeleton-category-tab"></div>
      </div>
    </div>

    <!-- 智能体卡片网格骨架屏 -->
    <div class="agent-grid-section">
      <div v-for="i in 12" :key="i" class="agent-card-skeleton">
        <div class="agent-card-top-skeleton">
          <div class="agent-card-avatar-skeleton"></div>
          <div class="skeleton-text skeleton-agent-name"></div>
        </div>
        <div class="agent-card-body-skeleton">
          <div class="skeleton-text skeleton-agent-desc"></div>
          <div class="skeleton-text skeleton-agent-desc-short"></div>
        </div>
        <div class="agent-card-footer-skeleton">
          <div class="skeleton-text skeleton-agent-tag"></div>
          <div class="skeleton-text skeleton-agent-tag"></div>
          <div class="skeleton-text skeleton-agent-stat"></div>
        </div>
      </div>
    </div>
  </div>

  <!-- 彩蛋：5秒后弹出的幽默提示 -->
  <Teleport to="body">
    <Transition name="egg-slide">
      <div v-if="easterEggVisible" class="easter-egg-container" @click="dismissEgg">
        <div class="easter-egg-card" @click.stop>
          <div class="easter-egg-emoji">&#x1F47B;</div>
          <div class="easter-egg-content">
            <p v-for="(msg, idx) in messages" :key="idx" class="easter-egg-line" :class="{ active: idx <= easterEggStep }">
              {{ msg }}
            </p>
          </div>
          <div class="easter-egg-footer">
            <span class="easter-egg-hint">点击关闭 - 但我猜你已经看完了</span>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped lang="scss">
/* 骨架屏动画 */
@keyframes shimmer {
  0% {
    background-position: -200px 0;
  }
  100% {
    background-position: calc(200px + 100%) 0;
  }
}

.chat-cluster-container {
  padding: 24px;
  background-color: #ffffff;
  min-height: 100%;
}

/* 对话广场头部 */
.plaza-header {
  margin-bottom: 28px;
}

.plaza-title {
  font-size: 28px;
  font-weight: 600;
  color: #1a1a1a;
  margin: 0 0 8px 0;
}

.plaza-desc {
  font-size: 14px;
  color: #666;
  margin: 0;
}

/* 区块通用样式 */
.section {
  background: #fff;
  border-radius: 12px;
  padding: 20px 24px;
  margin-bottom: 20px;
  box-shadow: 0 0 10px rgba(0, 0, 0, 0.01);
  border: 1px solid rgba(0, 0, 0, 0.05);
}

/* 骨架屏基础样式 */
.skeleton-text {
  background: linear-gradient(90deg, #f0f0f0 25%, #e8e8e8 50%, #f0f0f0 75%);
  background-size: 200px 100%;
  animation: shimmer 1.5s ease-in-out infinite;
  border-radius: 4px;
}

/* 搜索栏骨架屏 */
.search-bar-skeleton {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 40px;
}

.skeleton-search-icon {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200px 100%;
  animation: shimmer 1.5s ease-in-out infinite;
  flex-shrink: 0;
}

.skeleton-search-text {
  width: 200px;
  height: 16px;
}

/* 分类标签骨架屏 */
.category-bar-skeleton {
  display: flex;
  gap: 12px;
}

.skeleton-category-tab {
  width: 80px;
  height: 32px;
  border-radius: 16px;
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200px 100%;
  animation: shimmer 1.5s ease-in-out infinite;
}

.skeleton-category-tab:first-child {
  width: 60px;
}

.skeleton-category-tab:nth-child(3) {
  width: 96px;
}

.skeleton-category-tab:nth-child(5) {
  width: 72px;
}

/* 智能体卡片网格骨架屏 */
.agent-grid-section {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.agent-card-skeleton {
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 12px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  box-shadow: 0 0 10px rgba(0, 0, 0, 0.01);
}

.agent-card-top-skeleton {
  display: flex;
  align-items: center;
  gap: 12px;
}

.agent-card-avatar-skeleton {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200px 100%;
  animation: shimmer 1.5s ease-in-out infinite;
  flex-shrink: 0;
}

.skeleton-agent-name {
  width: 100px;
  height: 18px;
}

.agent-card-body-skeleton {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.skeleton-agent-desc {
  width: 100%;
  height: 12px;
}

.skeleton-agent-desc-short {
  width: 60%;
  height: 12px;
}

.agent-card-footer-skeleton {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
}

.skeleton-agent-tag {
  width: 52px;
  height: 22px;
  border-radius: 4px;
}

.skeleton-agent-stat {
  width: 36px;
  height: 14px;
  margin-left: auto;
}

/* ========== 彩蛋样式 ========== */
.easter-egg-container {
  position: fixed;
  inset: 0;
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.35);
  backdrop-filter: blur(2px);
}

.easter-egg-card {
  background: #fff;
  border-radius: 16px;
  padding: 32px 36px 24px;
  max-width: 420px;
  width: 90%;
  text-align: center;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
}

.easter-egg-emoji {
  font-size: 48px;
  margin-bottom: 16px;
  line-height: 1;
}

.easter-egg-content {
  display: flex;
  flex-direction: column;
  gap: 10px;
  text-align: left;
}

.easter-egg-line {
  font-size: 15px;
  color: #999;
  margin: 0;
  padding: 8px 12px;
  border-radius: 8px;
  background: #f8f8f8;
  opacity: 0.4;
  transform: translateY(6px);
  transition: all 0.5s ease;
  line-height: 1.5;
}

.easter-egg-line.active {
  color: #333;
  opacity: 1;
  transform: translateY(0);
  background: #f0f7ff;
}

.easter-egg-footer {
  margin-top: 20px;
}

.easter-egg-hint {
  font-size: 12px;
  color: #bbb;
}

/* 进入/离开动画 */
.egg-slide-enter-active,
.egg-slide-leave-active {
  transition: opacity 0.3s ease;
}

.egg-slide-enter-active .easter-egg-card,
.egg-slide-leave-active .easter-egg-card {
  transition: transform 0.3s ease;
}

.egg-slide-enter-from,
.egg-slide-leave-to {
  opacity: 0;
}

.egg-slide-enter-from .easter-egg-card {
  transform: scale(0.85) translateY(20px);
}

.egg-slide-leave-to .easter-egg-card {
  transform: scale(0.85) translateY(20px);
}
</style>
