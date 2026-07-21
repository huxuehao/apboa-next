/**
 * MCP 工具治理页面
 *
 * @author huxuehao
 */
<script setup lang="ts">
/* eslint-disable vue/multi-word-component-names */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeftOutlined, BugOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import type { McpServerVO, McpToolVO } from '@/types'
import { McpActivationStatus, McpFailureSource } from '@/types'
import * as mcpApi from '@/api/mcp'
import ToolDebugDrawer from '@/components/mcp/ToolDebugDrawer.vue'
import mcpToolAvatar from '@/assets/avatar/mcp-tool.png'
import SimpleSwitch from '@/components/common/SimpleSwitch.vue'

const route = useRoute()
const router = useRouter()

const serverId = computed(() => route.params.serverId as string)

const server = ref<McpServerVO | undefined>(undefined)
const tools = ref<McpToolVO[]>([])
const loading = ref(false)
const searchKeyword = ref('')

// 调试抽屉状态
const debugOpen = ref(false)
const debugTool = ref<McpToolVO | null>(null)

// 正在切换启停状态的工具
const togglingMap = ref(new Map<string | number, boolean>())

const readonly = computed(() => {
  return server.value?.activationStatus === McpActivationStatus.FAILED
    && server.value?.failureSource === McpFailureSource.RUNTIME_AUTO_DEGRADE
})

/** 过滤后的工具列表 */
const filteredTools = computed(() => {
  const kw = searchKeyword.value.trim().toLowerCase()
  if (!kw) return tools.value
  return tools.value.filter(tool =>
    tool.toolName.toLowerCase().includes(kw)
    || (tool.description || '').toLowerCase().includes(kw)
  )
})

/** 监听工具列表变化，清除已完成的切换 loading 状态 */
watch(
  () => tools.value,
  (newTools) => {
    for (const [id, targetEnabled] of togglingMap.value.entries()) {
      const tool = newTools.find(t => t.id === id)
      if (tool && tool.enabled === targetEnabled) {
        togglingMap.value.delete(id)
      }
    }
  },
  { deep: true }
)

/** 加载服务器和工具数据 */
async function loadData() {
  loading.value = true
  try {
    const [detailRes, toolsRes] = await Promise.all([
      mcpApi.detail(serverId.value),
      mcpApi.listTools(serverId.value)
    ])
    server.value = detailRes.data.data
    tools.value = toolsRes.data.data || []
  } finally {
    loading.value = false
  }
}

/** 返回列表 */
function handleBack() {
  router.push('/mcp')
}

/** 处理工具启停切换 */
async function handleToggleEnabled(tool: McpToolVO, checked: boolean) {
  if (!server.value) return
  togglingMap.value.set(tool.id, checked)
  try {
    const response = await mcpApi.updateToolsGlobalEnabled(
      server.value.id as string,
      [tool.id as string],
      checked
    )
    // 更新本地状态
    const target = tools.value.find(t => t.id === tool.id)
    if (target) {
      target.enabled = checked
    }
    // 同步服务器信息
    if (response.data.data) {
      server.value = response.data.data as McpServerVO
    }
    message.success('工具全局可用状态已更新')
  } finally {
    togglingMap.value.delete(tool.id)
  }
}

/** 进入工具调试 */
function handleDebugTool(tool: McpToolVO) {
  debugTool.value = tool
  debugOpen.value = true
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="tool-governance-page">
    <section class="intro-section">
      <div class="intro-nav">
        <AButton type="link" @click="handleBack" class="back-btn">
          <ArrowLeftOutlined />
          <span>返回 MCP 列表</span>
        </AButton>
      </div>
      <h3 class="intro-title">{{ server?.name || 'MCP 工具' }} - 工具治理</h3>
      <p class="intro-desc text-secondary">
        管理当前 MCP 服务下的工具目录，控制工具的全局可用状态。运行时只会注册"全局可用"且未消失的工具。
      </p>
    </section>

    <AAlert
      v-if="readonly"
      type="warning"
      message="当前展示为上次缓存。该 MCP 因运行时自动降级而进入只读态，重新连接成功前只可查看工具目录，不能修改工具治理。"
      banner
      closable
      style="margin-bottom: 16px;"
    />

    <section class="filter-section flex justify-between items-center">
      <AInput
        v-model:value="searchKeyword"
        placeholder="搜索工具名称或描述"
        allowClear
        style="width: 300px;"
      >
        <template #prefix><SearchOutlined class="text-placeholder" /></template>
      </AInput>
    </section>

    <section class="card-section">
      <ASpin :spinning="loading">
        <AEmpty v-if="!tools.length && !loading" description="暂无工具目录" />
        <template v-else>
          <AEmpty v-if="!filteredTools.length" description="未找到匹配的工具" />
          <div v-else class="tool-card-grid">
            <div
              v-for="tool in filteredTools"
              :key="tool.id"
              class="tool-card"
            >
              <div class="card-header">
                <div class="card-icon" :class="{ 'avatar-disabled': tool.missing || !tool.enabled }">
                  <img :src="mcpToolAvatar" alt="mcp-tool" />
                </div>
                <div class="card-title truncate" :title="tool.toolName">
                  {{ tool.toolName }}
                </div>
                <ATooltip title="调试工具">
                  <AButton
                    type="text"
                    size="small"
                    class="card-debug-btn"
                    :disabled="tool.missing || !tool.enabled || readonly"
                    @click="handleDebugTool(tool)"
                  >
                    <BugOutlined />
                  </AButton>
                </ATooltip>
              </div>
              <div class="card-content line-clamp-3" :title="tool.description">
                {{ tool.description || '暂无描述' }}
              </div>
              <div class="card-footer">
                <div class="card-tags">
                  <ATag v-if="tool.missing" color="warning" :bordered="false">已消失</ATag>
                  <ATag v-else-if="tool.enabled" color="default" :bordered="false">全局可用</ATag>
                  <ATag v-else color="default" :bordered="false">全局禁用</ATag>
                </div>
                <SimpleSwitch
                  :checked="tool.enabled"
                  :loading="togglingMap.has(tool.id)"
                  :disabled="loading || readonly || togglingMap.has(tool.id)"
                  @change="(checked: boolean) => handleToggleEnabled(tool, checked)"
                />
              </div>
            </div>
          </div>
        </template>
      </ASpin>
    </section>

    <!-- 调试抽屉 -->
    <ToolDebugDrawer
      v-model:open="debugOpen"
      :tool="debugTool"
    />
  </div>
</template>

<style scoped lang="scss">
.tool-governance-page {
  padding: var(--spacing-lg);
  min-height: 100%;

  .intro-section {
    margin-bottom: var(--spacing-lg);

    .intro-nav {
      margin-bottom: var(--spacing-sm);
    }

    .back-btn {
      padding: 0;
      color: rgba(0, 0, 0, 0.45);

      &:hover {
        color: rgba(0, 0, 0, 0.88);
      }
    }

    .intro-title {
      font-size: var(--font-size-2xl);
      font-weight: 600;
      color: var(--color-text-primary);
      margin-bottom: var(--spacing-sm);
    }

    .intro-desc {
      font-size: var(--font-size-base);
      line-height: 1.6;
      max-width: 800px;
    }
  }

  .filter-section {
    margin-bottom: var(--spacing-lg);
    gap: var(--spacing-md);
    position: sticky;
    top: 0;
    background-color: var(--color-bg);
    padding: var(--spacing-base) 0;
    z-index: 10;
  }

  .card-section {
    position: relative;

    .tool-card-grid {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: var(--spacing-md);

      @media (max-width: 1400px) {
        grid-template-columns: repeat(3, 1fr);
      }

      @media (max-width: 1000px) {
        grid-template-columns: repeat(2, 1fr);
      }

      @media (max-width: 640px) {
        grid-template-columns: 1fr;
      }
    }
  }

  .tool-card {
    min-width: 0;
    min-height: 160px;
    overflow: hidden;
    padding: var(--spacing-md);
    background-color: #FFFFFF;
    border-radius: var(--border-radius-lg);
    border: 1px solid #ebebeb;
    transition: all var(--transition-base);
    display: flex;
    flex-direction: column;
    gap: var(--spacing-sm);

    &:hover {
      box-shadow: 0 4px 6px -5px rgba(0, 0, 0, 0.3);
      transform: translateY(-2px);
    }

    .card-header {
      display: flex;
      align-items: center;
      gap: 8px;

      .card-icon {
        width: 36px;
        height: 36px;
        display: flex;
        align-items: center;
        justify-content: center;
        background-color: #e7e7e7;
        border-radius: var(--border-radius-xl);
        flex-shrink: 0;

        img {
          width: 22px;
          height: 22px;
          object-fit: contain;
          filter: grayscale(100%);
          opacity: 0.5;
        }

        /* 可用态：正常颜色 */
        &:not(.avatar-disabled) {
          background-color: #e8eaf6;

          img {
            filter: none;
            opacity: 1;
          }
        }
      }

      .card-title {
        flex: 1;
        min-width: 0;
        font-size: var(--font-size-base);
        font-weight: 600;
        color: var(--color-text-primary);
      }

      .card-debug-btn {
        flex-shrink: 0;
      }
    }

    .card-content {
      flex: 1;
      font-size: var(--font-size-sm);
      color: var(--color-text-regular);
      line-height: 1.6;
      display: -webkit-box;
      -webkit-box-orient: vertical;
      -webkit-line-clamp: 3;
      line-clamp: 3;
      overflow: hidden;
      text-overflow: ellipsis;
      word-break: break-all;
      min-height: 0;
    }

    .card-footer {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding-top: var(--spacing-xs);

      .card-tags {
        flex: 1;
        min-width: 0;
        display: flex;
        flex-wrap: wrap;
        gap: 4px;
      }
    }
  }
}
</style>
