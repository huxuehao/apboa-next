<script setup lang="ts">
/**
 * portal 自定义组件示例：演示元信息、上下文注入、自定义 props、
 * 标题栏操作按钮（panelActions）与定时刷新（refresh）全部约定。
 *
 * @author huxuehao
 */
import { computed, ref } from 'vue'
import { message } from 'ant-design-vue'

defineOptions({
  portalMeta: {
    name: '示例欢迎卡',
    description: '演示自定义组件全部约定：元信息、上下文、props、操作按钮、定时刷新',
  },
})

const props = withDefaults(
  defineProps<{
    /** 设计器 props JSON 可覆盖，如 { "greeting": "你好" } */
    greeting?: string
    /** 面板渲染器注入的标准上下文 */
    panelContext?: { panelId: string; title: string; interactive: boolean }
  }>(),
  { greeting: '欢迎使用自定义组件', panelContext: undefined },
)

const counter = ref(0)
const lastRefreshed = ref<string>('-')

function refresh() {
  counter.value += 1
  lastRefreshed.value = new Date().toLocaleTimeString()
}

const contextText = computed(() =>
  props.panelContext
    ? `面板 ${props.panelContext.title || props.panelContext.panelId}（${props.panelContext.interactive ? '门户态' : '编辑态'}）`
    : '未注入上下文',
)

// 标题栏操作按钮 + 定时刷新处理器（自动被面板识别）
defineExpose({
  panelActions: [
    { key: 'refresh', label: '刷新', icon: 'ReloadOutlined', run: refresh },
    {
      key: 'more',
      label: '更多',
      icon: 'EllipsisOutlined',
      run: () => message.info('这是示例组件的「更多」动作'),
    },
  ],
  refresh,
})
</script>

<template>
  <div class="demo-welcome">
    <div class="dw-greeting">{{ greeting }}</div>
    <div class="dw-context">{{ contextText }}</div>
    <div class="dw-stats">
      <span>刷新次数：{{ counter }}</span>
      <span>最近刷新：{{ lastRefreshed }}</span>
    </div>
  </div>
</template>

<style scoped lang="scss">
.demo-welcome {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
  height: 100%;
}

.dw-greeting {
  font-size: 16px;
  font-weight: 600;
  color: #1a1a1a;
}

.dw-context {
  font-size: 13px;
  color: #8c8c8c;
}

.dw-stats {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #999;
}
</style>
