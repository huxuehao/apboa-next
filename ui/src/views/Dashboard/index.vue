<script setup lang="ts">
/**
 * 工作台页面
 *
 * @author huxuehao
 */
import { ref, onMounted } from 'vue'
import { useAccountStore } from '@/stores'
import {
  RobotOutlined,
  ApartmentOutlined,
  DatabaseOutlined,
  ApiOutlined,
  ToolOutlined,
  CloudServerOutlined,
  AppstoreOutlined,
  FileTextOutlined,
  SafetyCertificateOutlined
} from '@ant-design/icons-vue'

const accountStore = useAccountStore()
const loading = ref(false)

/**
 * 统计数据
 */
const stats = ref([
  { title: '智能体', value: 0, icon: RobotOutlined, color: '#1677ff' },
  { title: '工作流', value: 0, icon: ApartmentOutlined, color: '#52c41a' },
  { title: '知识库', value: 0, icon: DatabaseOutlined, color: '#722ed1' },
  { title: '模型', value: 0, icon: ApiOutlined, color: '#13c2c2' },
  { title: '工具', value: 0, icon: ToolOutlined, color: '#fa8c16' },
  { title: 'MCP', value: 0, icon: CloudServerOutlined, color: '#eb2f96' },
  { title: '技能', value: 0, icon: AppstoreOutlined, color: '#fadb14' },
  { title: '提示词', value: 0, icon: FileTextOutlined, color: '#597ef7' },
  { title: '敏感词', value: 0, icon: SafetyCertificateOutlined, color: '#ff4d4f' }
])

/**
 * 快捷操作
 */
const quickActions = [
  { title: '创建智能体', description: '快速创建新的智能体', path: '/agent' },
  { title: '创建工作流', description: '设计新的工作流', path: '/workflow' },
  { title: '上传知识库', description: '添加知识库文档', path: '/knowledge' },
  { title: '配置模型', description: '管理模型供应商', path: '/model' }
]

/**
 * 获取统计数据
 */
async function fetchStats() {
  loading.value = true
  try {
    // TODO: 调用API获取统计数据
    // 这里暂时使用模拟数据
    stats.value.forEach(stat => {
      stat.value = Math.floor(Math.random() * 100)
    })
  } catch (error) {
    console.error('获取统计数据失败:', error)
  } finally {
    loading.value = false
  }
}

/**
 * 跳转到指定页面
 */
function navigateTo(path: string) {
  // 使用router跳转
  window.location.hash = path
}

onMounted(() => {
  fetchStats()
})
</script>

<template>
  <div class="dashboard-container">
    <!-- 欢迎信息 -->
    <div class="welcome-section">
      <h1 class="welcome-title">
        欢迎回来，{{ accountStore.userInfo?.username || '用户' }}
      </h1>
      <p class="welcome-desc">
        这是您的工作台，可以快速访问常用功能和查看系统状态。
      </p>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-section">
      <h2 class="section-title">系统概览</h2>
      <div class="stats-grid">
        <div
          v-for="stat in stats"
          :key="stat.title"
          class="stat-card"
          v-loading="loading"
        >
          <div class="stat-icon" :style="{ backgroundColor: stat.color + '15', color: stat.color }">
            <component :is="stat.icon" />
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stat.value }}</div>
            <div class="stat-title">{{ stat.title }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 快捷操作 -->
    <div class="quick-section">
      <h2 class="section-title">快捷操作</h2>
      <div class="quick-grid">
        <div
          v-for="action in quickActions"
          :key="action.title"
          class="quick-card"
          @click="navigateTo(action.path)"
        >
          <div class="quick-title">{{ action.title }}</div>
          <div class="quick-desc">{{ action.description }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.dashboard-container {
  padding: 24px;
  background-color: #FFFFFF;
  min-height: 100%;
}

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

.section-title {
  font-size: 18px;
  font-weight: 500;
  color: #1a1a1a;
  margin: 0 0 16px 0;
}

.stats-section {
  margin-bottom: 32px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
}

.stat-card {
  background: white;
  border-radius: 8px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
  transition: transform 0.2s ease;

  &:hover {
    transform: translateY(-2px);
  }
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 24px;
  font-weight: 600;
  color: #1a1a1a;
  line-height: 1;
}

.stat-title {
  font-size: 14px;
  color: #666;
  margin-top: 4px;
}

.quick-section {
  margin-bottom: 32px;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 16px;
}

.quick-card {
  background: white;
  border-radius: 8px;
  padding: 20px;
  cursor: pointer;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
  transition: all 0.2s ease;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  }
}

.quick-title {
  font-size: 16px;
  font-weight: 500;
  color: #1a1a1a;
  margin-bottom: 8px;
}

.quick-desc {
  font-size: 14px;
  color: #666;
}
</style>
