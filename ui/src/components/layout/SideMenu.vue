<script setup lang="ts">
/**
 * 侧边栏菜单组件
 *
 * @author huxuehao
 */
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import homeAvatar from '@/assets/avatar/home.png'
import chatBotAvatar from '@/assets/avatar/chat-bot.png'
import agentAvatar from '@/assets/avatar/agent.png'
import workflowAvatar from '@/assets/avatar/workflow.png'
import automationAvatar from '@/assets/avatar/automation.png'
import knowledgebaseAvatar from '@/assets/avatar/knowledgebase.png'
import modelProviderAvatar from '@/assets/avatar/model-provider.png'
import skillAvatar from '@/assets/avatar/skill.png'
import mcpAvatar from '@/assets/avatar/mcp.png'
import toolAvatar from '@/assets/avatar/tool.png'
import hookAvatar from '@/assets/avatar/hook.png'
import promptAvatar from '@/assets/avatar/prompt.png'
import sensitiveAvatar from '@/assets/avatar/sensitive.png'

/**
 * Props定义
 */
const props = defineProps<{
  collapsed?: boolean
}>()

const route = useRoute()
const router = useRouter()

/**
 * 菜单项类型定义
 */
interface MenuItem {
  key: string
  label: string
  avatar?: string
  path: string
  type: 'menu' | 'category'
}

/**
 * 菜单配置
 */
const menuConfig: MenuItem[] = [
// 空间分类
  {
    key: 'space-category',
    label: '空间',
    path: '',
    type: 'category'
  },
  {
    key: 'dashboard',
    label: '总览',
    avatar: homeAvatar,
    path: '/dashboard',
    type: 'menu'
  },
  // {
  //   key: 'chat-cluster',
  //   label: '对话',
  //   avatar: chatBotAvatar,
  //   path: '/chat-cluster',
  //   type: 'menu'
  // },
  // 开发分类
  {
    key: 'dev-category',
    label: '开发',
    path: '',
    type: 'category'
  },
  {
    key: 'agent',
    label: '智能体',
    avatar: agentAvatar,
    path: '/agent',
    type: 'menu'
  },
  {
    key: 'workflow',
    label: '工作流',
    avatar: workflowAvatar,
    path: '/workflow',
    type: 'menu'
  },
  {
    key: 'automation',
    label: '自动化',
    avatar: automationAvatar,
    path: '/automation',
    type: 'menu'
  },
  {
    key: 'knowledge',
    label: '知识库',
    avatar: knowledgebaseAvatar,
    path: '/knowledge',
    type: 'menu'
  },
  // 资源分类
  {
    key: 'resource-category',
    label: '资源',
    path: '',
    type: 'category'
  },
  {
    key: 'model',
    label: '模型',
    avatar: modelProviderAvatar,
    path: '/model',
    type: 'menu'
  },
  {
    key: 'skill',
    label: '技能',
    avatar: skillAvatar,
    path: '/skill',
    type: 'menu'
  },
  {
    key: 'mcp',
    label: 'MCP',
    avatar: mcpAvatar,
    path: '/mcp',
    type: 'menu'
  },
  {
    key: 'tool',
    label: '工具',
    avatar: toolAvatar,
    path: '/tool',
    type: 'menu'
  },
  {
    key: 'hook',
    label: '扩展',
    avatar: hookAvatar,
    path: '/hook',
    type: 'menu'
  },
  {
    key: 'prompt',
    label: '提示词',
    avatar: promptAvatar,
    path: '/prompt',
    type: 'menu'
  },
  {
    key: 'sensitive',
    label: '敏感词',
    avatar: sensitiveAvatar,
    path: '/sensitive',
    type: 'menu'
  }
]

/**
 * 当前激活的菜单项
 */
const activeMenu = computed(() => {
  const path = route.path
  // 找到匹配的菜单项
  const menuItem = menuConfig.find(item => {
    if (item.type !== 'menu') return false
    // 精确匹配或前缀匹配
    return path === item.path || path.startsWith(item.path + '/')
  })
  return menuItem?.key || ''
})

/**
 * 处理菜单点击
 */
const handleMenuClick = (item: MenuItem) => {
  if (item.type === 'menu' && item.path) {
    router.push(item.path)
  }
}
</script>

<template>
  <div class="side-menu-wrapper" :class="{ collapsed: props.collapsed }">
    <div class="side-menu">
      <template v-for="item in menuConfig" :key="item.key">
        <!-- 分类 -->
        <div v-if="item.type === 'category'" class="menu-category">
          {{ item.label }}
        </div>
        <!-- 菜单项 -->
        <ATooltip
          v-else
          :title="props.collapsed ? item.label : ''"
          placement="right"
        >
          <div
            class="menu-item"
            :class="{ active: activeMenu === item.key }"
            @click="handleMenuClick(item)"
          >
            <img :src="item.avatar" width="20px" alt="icon"/>
            <span class="menu-label">{{ item.label }}</span>
          </div>
        </ATooltip>
      </template>
    </div>
  </div>
</template>

<style scoped lang="scss">
.side-menu {
  padding: 4px 12px;
}

.menu-category {
  font-size: 11px;
  color: #999;
  padding: 16px 6px 4px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  font-weight: 500;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 16px;
  border-radius: 6px;
  cursor: pointer;
  color: #666;
  font-size: 14px;
  transition: all 0.2s ease;
  margin: 2px 0;

  &:hover {
    background-color: #E9EAEA;
    color: #666;
  }

  &.active {
    color: #000000;
    background-color: #E9EAEA;

    .menu-icon {
      color: #000000;
    }
  }
}

.menu-label {
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  transition: opacity 0.3s ease;
}

/* 收缩态 */
.side-menu-wrapper.collapsed {
  .menu-category {
    height: 0;
    padding: 0;
    overflow: hidden;
    opacity: 0;
  }

  .menu-item {
    justify-content: center;
    padding: 8px;
  }

  .menu-label {
    display: none;
  }
}
</style>
