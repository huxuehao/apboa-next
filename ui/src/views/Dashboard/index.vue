<script setup lang="ts">
/**
 * 工作台页面（骨架屏模式）
 *
 * @author huxuehao
 */
import { ref, onMounted, onUnmounted } from 'vue'
import { useAccountStore } from '@/stores'

const accountStore = useAccountStore()

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
  name: 'DashboardView'
}
</script>

<template>
  <div class="dashboard-container">
    <!-- 欢迎信息 -->
    <div class="welcome-section">
      <h1 class="welcome-title">
        欢迎回来，{{ accountStore.userInfo?.nickname || '用户' }}
      </h1>
      <p class="welcome-desc">
        这是您的工作台，可以快速访问常用功能和查看系统状态。
      </p>
    </div>

    <!-- 统计概览骨架屏 -->
    <div class="section">
      <div class="section-header">
        <div class="skeleton-text skeleton-title"></div>
        <div class="skeleton-text skeleton-link"></div>
      </div>
      <div class="stats-grid">
        <div v-for="i in 9" :key="i" class="stat-card-skeleton">
          <div class="skeleton-icon"></div>
          <div class="stat-content-skeleton">
            <div class="skeleton-text skeleton-value"></div>
            <div class="skeleton-text skeleton-label"></div>
          </div>
        </div>
      </div>
    </div>

    <!-- 快捷操作骨架屏 -->
    <div class="section">
      <div class="section-header">
        <div class="skeleton-text skeleton-title"></div>
      </div>
      <div class="quick-grid">
        <div v-for="i in 4" :key="i" class="quick-card-skeleton">
          <div class="quick-card-icon-skeleton"></div>
          <div class="quick-card-content-skeleton">
            <div class="skeleton-text skeleton-card-title"></div>
            <div class="skeleton-text skeleton-card-desc"></div>
          </div>
        </div>
      </div>
    </div>

    <!-- 双栏布局：最近活动 + 数据概览 -->
    <div class="dual-column">
      <!-- 最近活动骨架屏 -->
      <div class="section">
        <div class="section-header">
          <div class="skeleton-text skeleton-title"></div>
          <div class="skeleton-text skeleton-link"></div>
        </div>
        <div class="activity-list">
          <div v-for="i in 5" :key="i" class="activity-item-skeleton">
            <div class="skeleton-avatar"></div>
            <div class="activity-content-skeleton">
              <div class="skeleton-text skeleton-activity-title"></div>
              <div class="skeleton-text skeleton-activity-desc"></div>
              <div class="skeleton-text skeleton-activity-time"></div>
            </div>
          </div>
        </div>
      </div>

      <!-- 数据概览骨架屏 -->
      <div class="section">
        <div class="section-header">
          <div class="skeleton-text skeleton-title"></div>
          <div class="skeleton-text skeleton-link"></div>
        </div>
        <div class="chart-skeleton">
          <div class="chart-header-skeleton">
            <div class="skeleton-text skeleton-chart-label"></div>
            <div class="skeleton-text skeleton-chart-value"></div>
          </div>
          <div class="chart-bars-skeleton">
            <div v-for="i in 7" :key="i" class="skeleton-bar" :style="{ height: (20 + Math.random() * 60) + '%' }"></div>
          </div>
          <div class="chart-labels-skeleton">
            <div v-for="i in 7" :key="i" class="skeleton-text skeleton-chart-xlabel"></div>
          </div>
        </div>
      </div>
    </div>

    <!-- 系统公告骨架屏 -->
    <div class="section">
      <div class="section-header">
        <div class="skeleton-text skeleton-title"></div>
        <div class="skeleton-text skeleton-link"></div>
      </div>
      <div class="announcement-list">
        <div v-for="i in 3" :key="i" class="announcement-item-skeleton">
          <div class="announcement-badge-skeleton"></div>
          <div class="announcement-content-skeleton">
            <div class="skeleton-text skeleton-announcement-title"></div>
            <div class="skeleton-text skeleton-announcement-summary"></div>
            <div class="skeleton-text skeleton-announcement-time"></div>
          </div>
        </div>
      </div>
    </div>

    <!-- 常用资源骨架屏 -->
    <div class="section">
      <div class="section-header">
        <div class="skeleton-text skeleton-title"></div>
      </div>
      <div class="resources-grid">
        <div v-for="i in 6" :key="i" class="resource-card-skeleton">
          <div class="resource-icon-skeleton"></div>
          <div class="resource-info-skeleton">
            <div class="skeleton-text skeleton-resource-name"></div>
            <div class="skeleton-text skeleton-resource-type"></div>
          </div>
          <div class="resource-action-skeleton"></div>
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

.dashboard-container {
  padding: 24px;
  background-color: #ffffff;
  min-height: 100%;
}

/* 欢迎信息 */
.welcome-section {
  margin-bottom: 32px;
}

.welcome-title {
  font-size: 28px;
  font-weight: 600;
  color: #1a1a1a;
  margin: 0 0 8px 0;
}

.welcome-desc {
  font-size: 14px;
  color: #666;
  margin: 0;
}

/* 区块通用样式 */
.section {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 24px;
  box-shadow: 0 0 10px rgba(0, 0, 0, 0.01);
  border: 1px solid rgba(0, 0, 0, 0.05);
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

/* 骨架屏基础样式 */
.skeleton-text {
  background: linear-gradient(90deg, #f0f0f0 25%, #f0f0f0 50%, #f0f0f0 75%);
  background-size: 200px 100%;
  animation: shimmer 1.5s ease-in-out infinite;
  border-radius: 4px;
}

.skeleton-title {
  width: 120px;
  height: 20px;
}

.skeleton-link {
  width: 60px;
  height: 16px;
}

/* 统计卡片骨架屏 */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
}

.stat-card-skeleton {
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 12px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
}

.skeleton-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200px 100%;
  animation: shimmer 1.5s ease-in-out infinite;
}

.stat-content-skeleton {
  flex: 1;
}

.skeleton-value {
  width: 60px;
  height: 28px;
  margin-bottom: 8px;
}

.skeleton-label {
  width: 40px;
  height: 14px;
}

/* 快捷操作骨架屏 */
.quick-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 16px;
}

.quick-card-skeleton {
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 12px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
}

.quick-card-icon-skeleton {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200px 100%;
  animation: shimmer 1.5s ease-in-out infinite;
  flex-shrink: 0;
}

.quick-card-content-skeleton {
  flex: 1;
}

.skeleton-card-title {
  width: 80px;
  height: 16px;
  margin-bottom: 8px;
}

.skeleton-card-desc {
  width: 140px;
  height: 12px;
}

/* 双栏布局 */
.dual-column {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
  margin-bottom: 24px;

  > .section {
    margin-bottom: 0;
  }
}

/* 活动列表骨架屏 */
.activity-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.activity-item-skeleton {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid #f5f5f5;

  &:last-child {
    border-bottom: none;
  }
}

.skeleton-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200px 100%;
  animation: shimmer 1.5s ease-in-out infinite;
  flex-shrink: 0;
}

.activity-content-skeleton {
  flex: 1;
}

.skeleton-activity-title {
  width: 200px;
  height: 14px;
  margin-bottom: 6px;
}

.skeleton-activity-desc {
  width: 160px;
  height: 12px;
  margin-bottom: 6px;
}

.skeleton-activity-time {
  width: 80px;
  height: 10px;
}

/* 图表骨架屏 */
.chart-skeleton {
  height: 200px;
}

.chart-header-skeleton {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 20px;
}

.skeleton-chart-label {
  width: 80px;
  height: 14px;
}

.skeleton-chart-value {
  width: 50px;
  height: 20px;
}

.chart-bars-skeleton {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  height: 120px;
  padding: 0 8px;
}

.skeleton-bar {
  flex: 1;
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200px 100%;
  animation: shimmer 1.5s ease-in-out infinite;
  border-radius: 4px 4px 0 0;
}

.chart-labels-skeleton {
  display: flex;
  gap: 12px;
  padding: 8px 8px 0;
}

.skeleton-chart-xlabel {
  flex: 1;
  height: 10px;
}

/* 公告列表骨架屏 */
.announcement-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.announcement-item-skeleton {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 16px;
  background: #fafafa;
  border-radius: 8px;
}

.announcement-badge-skeleton {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200px 100%;
  animation: shimmer 1.5s ease-in-out infinite;
  margin-top: 6px;
  flex-shrink: 0;
}

.announcement-content-skeleton {
  flex: 1;
}

.skeleton-announcement-title {
  width: 240px;
  height: 16px;
  margin-bottom: 8px;
}

.skeleton-announcement-summary {
  width: 100%;
  height: 12px;
  margin-bottom: 8px;
}

.skeleton-announcement-time {
  width: 100px;
  height: 10px;
}

/* 资源网格骨架屏 */
.resources-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 12px;
}

.resource-card-skeleton {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: #fafafa;
  border-radius: 8px;
}

.resource-icon-skeleton {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200px 100%;
  animation: shimmer 1.5s ease-in-out infinite;
  flex-shrink: 0;
}

.resource-info-skeleton {
  flex: 1;
}

.skeleton-resource-name {
  width: 120px;
  height: 14px;
  margin-bottom: 6px;
}

.skeleton-resource-type {
  width: 60px;
  height: 10px;
}

.resource-action-skeleton {
  width: 24px;
  height: 24px;
  border-radius: 4px;
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200px 100%;
  animation: shimmer 1.5s ease-in-out infinite;
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
