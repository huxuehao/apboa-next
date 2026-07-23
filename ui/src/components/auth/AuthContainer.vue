<script setup lang="ts">
/**
 * 认证页面容器组件 — 左右双栏布局
 *
 * @author huxuehao
 */
import {
  ApiOutlined,
  LinkOutlined,
  SafetyCertificateOutlined,
  LineChartOutlined,
} from '@ant-design/icons-vue'
import BackButton from './BackButton.vue'

interface Props {
  showBack?: boolean
  backTo?: string
}

withDefaults(defineProps<Props>(), {
  showBack: false,
  backTo: '/login',
})

/** 左侧品牌特性列表 */
const features = [
  { icon: ApiOutlined, title: '低代码敏捷搭建智能体', desc: '依托可视化编排能力，无需复杂开发，通过简单配置拖拽即可快速创建专属 AI 智能体。' },
  { icon: LineChartOutlined, title: '工作流全生命周期管理', desc: '可将复杂业务逻辑沉淀为可调用、可追踪、可复用的标准化流程，高效实现资源协同。支持 30+ 内置节点。' },
  { icon: LinkOutlined, title: '全方位交互与能力升级', desc: '支持多会话并行、刷新页面可持续流式输出；新增交互式表单、可视化图表卡片，打破纯文本对话限制。' },
  { icon: SafetyCertificateOutlined, title: '企业级分布式高性能架构', desc: '控制台 + 3 个可无限横向扩展运行时 + 消息服务，支持弹性扩容、多租户权限隔离，满足大型企业部署与组织安全管控需求。' },
]

const currentYear = new Date().getFullYear()
</script>

<template>
  <div class="auth-page">
    <!-- 左侧品牌区域 -->
    <div class="auth-brand">
      <div class="auth-brand-top">
        <div class="auth-brand-header">
          <img src="@/assets/logo/logo_3.png" alt="APBOA·NEXT" class="auth-brand-logo" />
          <span class="auth-brand-tag">智能体平台</span>
        </div>
        <h1 class="auth-brand-title">
          构建<span class="highlight">智能体</span>，释放无限可能
        </h1>
        <p class="auth-brand-desc">
          新一代智能体开发与协同平台，融合先进的 AI 能力与丰富的生态工具，助力企业与开发者快速构建、运营和进化智能体
        </p>
        <div class="auth-brand-features">
          <div v-for="feature in features" :key="feature.title" class="auth-feature-item">
            <div class="auth-feature-icon">
              <component :is="feature.icon" />
            </div>
            <div class="auth-feature-text">
              <h4>{{ feature.title }}</h4>
              <p>{{ feature.desc }}</p>
            </div>
          </div>
        </div>
      </div>
      <div class="auth-brand-bottom">
        <div class="auth-brand-decoration">
          <div class="deco-shape"></div>
        </div>
        <div class="auth-brand-footer">
          &copy; {{ currentYear }} Apboa Next. All rights reserved. Powered by Huxuehao
        </div>
      </div>
    </div>

    <!-- 右侧表单卡片区 -->
    <div class="auth-card-area">
      <BackButton v-if="showBack" :to="backTo" />
      <div class="auth-card">
        <slot />
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/modules/auth' as *;
</style>
