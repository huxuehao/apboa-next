<template>
  <div>
    <!-- 页面内容 -->
    <div class="page-content">
      <h1>Apboa Next 加载动画示例</h1>

      <!-- 示例1: 基础加载动画 -->
      <div class="example-section">
        <h2>基础加载动画</h2>
        <div class="example-box">
          <ApboaLoading />
        </div>
      </div>

      <!-- 示例2: 带提示文本 -->
      <div class="example-section">
        <h2>带提示文本</h2>
        <div class="example-box">
          <ApboaLoading tip="加载数据中..." />
        </div>
      </div>

      <!-- 示例3: 不同尺寸 -->
      <div class="example-section">
        <h2>不同尺寸</h2>
        <div class="example-box">
          <div class="size-demo">
            <div>
              <h3>Small</h3>
              <ApboaLoading size="small" />
            </div>
            <div>
              <h3>Default</h3>
              <ApboaLoading />
            </div>
            <div>
              <h3>Large</h3>
              <ApboaLoading size="large" />
            </div>
          </div>
        </div>
      </div>

      <!-- 示例4: 包裹式加载 -->
      <div class="example-section">
        <h2>包裹式加载 (ApboaSpin)</h2>
        <div class="example-box">
          <ApboaSpin :spinning="spinning" tip="加载中...">
            <div class="content-box">
              <p>这是被包裹的内容</p>
              <p>点击按钮切换加载状态</p>
              <button @click="spinning = !spinning">
                {{ spinning ? '停止加载' : '开始加载' }}
              </button>
            </div>
          </ApboaSpin>
        </div>
      </div>

      <!-- 示例5: 全屏加载 -->
      <div class="example-section">
        <h2>全屏加载</h2>
        <div class="example-box">
          <button @click="showFullscreen = true">显示全屏加载</button>
          <button @click="showFullscreen = false">隐藏全屏加载</button>
        </div>
      </div>

      <!-- 全屏加载组件 -->
      <ApboaLoading
        v-if="showFullscreen"
        fullscreen
        tip="系统加载中..."
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import ApboaLoading from '@/components/common/ApboaLoading.vue'
import ApboaSpin from '@/components/common/ApboaSpin.vue'

const spinning = ref(false)
const showFullscreen = ref(false)
</script>

<style scoped>
.page-content {
  max-width: 800px;
  margin: 0 auto;
  padding: 24px;
}

.example-section {
  margin-bottom: 32px;
}

.example-section h2 {
  margin-bottom: 16px;
  color: #333;
}

.example-box {
  background: #f5f5f5;
  border-radius: 8px;
  padding: 24px;
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 200px;
}

.size-demo {
  display: flex;
  gap: 48px;
  align-items: center;
}

.size-demo h3 {
  text-align: center;
  margin-bottom: 12px;
  color: #666;
}

.content-box {
  text-align: center;
  padding: 24px;
}

.content-box p {
  margin-bottom: 12px;
  color: #666;
}

button {
  padding: 8px 16px;
  margin: 0 8px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  background: #fff;
  cursor: pointer;
  transition: all 0.3s;
}

button:hover {
  border-color: #1890ff;
  color: #1890ff;
}
</style>
