/**
 * 系统介绍组件
 *
 * @author huxuehao
 */
<script setup lang="ts">
// 纯静态内容展示
</script>

<template>
  <div class="system-intro-container">
    <div class="system-intro-logo">

    </div>
    <h1 class="system-intro-title">
      您的专属智能体，从此高效构建
    </h1>

    <p class="system-intro-paragraph">
      <strong>Apboa Next</strong> 是基于领先的 <strong>React</strong> 理念打造的新一代智能体开发与管理平台。我们致力于将复杂的AI智能体开发过程变得简单、高效，让您能轻松拥有功能强大的专属数字助手。
    </p>

    <p class="system-intro-paragraph">
      平台为您提供了一站式的全链路解决方案：从保障对话安全的<strong>敏感词过滤</strong>，到塑造智能体个性的<strong>提示词工坊</strong>；从灵活配置的<strong>多模型供应商接入</strong>，到拓展能力的<strong>工具与技能包集成</strong>；再到沉淀专有知识的<strong>知识库系统</strong>，以及最终直观的<strong>智能体编排与管理</strong>。Apboa 将这些核心模块无缝整合，形成清晰的<strong>架构图</strong>。
    </p>

    <p class="system-intro-paragraph">
      不仅如此，Apboa Next 进一步突破了智能体的能力边界。本次版本在架构、交互与能力上进行了系统性升级：后台拆分为 <strong>5 个服务</strong>，1 个控制台与 3 个运行时（支持无限横向扩展）加 1 个消息服务协同工作，辅以优化后的 SKILL 同步机制与内置服务监控，实现企业级<strong>分布式部署</strong>与弹性扩容；同时提供更友好的前端<strong>多租户</strong>交互体验，满足企业级组织架构下的权限隔离需求。它支持<strong>多 Session 并行运行</strong>，无需等待消息结束即可开启新对话，消息记录迁移至后端存储，运行中的 Session <strong>状态自动保存</strong>，强制刷新页面后流式输出仍可继续；新增的<strong>文档识别能力</strong>与模型解耦，理论上可扩展支持任意类型的文档内容识别；在交互层面，<strong>交互式表单</strong>让对话不再局限于纯文本，<strong>视觉增强卡片</strong>与<strong>图表</strong>使信息呈现更加直观。
    </p>

    <p class="system-intro-paragraph">
      无论是构建客服助手、创意伙伴还是行业专家，您都无需从零开始。在 Apboa Next 的赋能下，只需通过简单的配置与拖拽，即可将前沿的AI能力快速转化为解决实际业务问题的智能体，大幅降低技术门槛与开发周期，真正实现智能应用的随需而创、高效落地。
    </p>

    <p class="system-intro-slogan">在 Apboa Next，轻松开启您的智能体之旅。</p>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/modules/_settings.scss' as *;
</style>
