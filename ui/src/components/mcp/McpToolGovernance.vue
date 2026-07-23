/**
 * MCP 工具治理弹窗组件
 *
 * @author huxuehao
 */
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { BugOutlined, SearchOutlined, ToolOutlined } from '@ant-design/icons-vue'
import type { McpServerVO, McpToolVO } from '@/types'

const props = defineProps<{
  open: boolean
  server: McpServerVO | undefined
  tools: McpToolVO[]
  loading: boolean
  readonly: boolean
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  toolEnabledChange: [tool: McpToolVO, enabled: boolean]
  toolNeedConfirmChange: [tool: McpToolVO, needConfirm: boolean]
  debugTool: [tool: McpToolVO]
}>()

/** 搜索关键词 */
const searchKeyword = ref('')

/** 正在切换启停状态的工具：key=工具ID, value=目标启用状态 */
const togglingMap = ref(new Map<string | number, boolean>())

/** 过滤后的工具列表 */
const filteredTools = computed(() => {
  const kw = searchKeyword.value.trim().toLowerCase()
  if (!kw) return props.tools
  return props.tools.filter(tool =>
    tool.toolName.toLowerCase().includes(kw)
    || (tool.description || '').toLowerCase().includes(kw)
  )
})

/** 监听工具列表变化，清除已完成的切换 loading 状态 */
watch(
  () => props.tools,
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

/** 处理工具启停切换，先显示 loading 再通知父组件 */
function handleToggleEnabled(tool: McpToolVO, checked: boolean) {
  togglingMap.value.set(tool.id, checked)
  emit('toolEnabledChange', tool, checked)
}

/** 切换工具「需要人工确认」（HITL §6.6）：调用前由 IConfirmationHook 暂停等用户允许/拒绝 */
function handleToggleNeedConfirm(tool: McpToolVO, checked: boolean) {
  emit('toolNeedConfirmChange', tool, checked)
}

function handleClose() {
  emit('update:open', false)
}
</script>

<template>
  <ApboaModal
    :open="open"
    title="工具治理"
    :footer="null"
    :background-color="'#F5F6F8'"
    defaultWidth="100%"
    @cancel="handleClose"
  >
    <div class="tool-governance">
      <div class="tool-governance-header">
        <div class="tool-governance-title-row">
          <div class="tool-governance-title">
            {{ server?.name || 'MCP 工具' }}
          </div>
          <AInput
            v-model:value="searchKeyword"
            placeholder="搜索工具名称或描述"
            allowClear
            style="width: 250px; border: rgba(14,14,14,0.1) solid 1px !important;"
          >
            <template #prefix><SearchOutlined class="text-placeholder" /></template>
          </AInput>
        </div>
        <div class="text-placeholder text-xs">
          运行时只会注册"全局可用"且未消失的工具。
        </div>
        <AAlert
          v-if="readonly"
          type="warning"
          message="当前展示为上次缓存。该 MCP 因运行时自动降级而进入只读态，重新连接成功前只可查看工具目录，不能修改工具治理。"
          banner
          closable
        />
      </div>

      <ASpin :spinning="loading">
        <AEmpty v-if="!tools.length" description="暂无工具目录" />
        <template v-else>
          <AEmpty v-if="!filteredTools.length" description="未找到匹配的工具" />
          <div v-else class="tool-card-grid">
            <div
              v-for="tool in filteredTools"
              :key="tool.id"
              class="tool-card"
            >
              <div class="card-header">
                <div class="card-icon">
                  <ToolOutlined />
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
                    @click="emit('debugTool', tool)"
                  >
                    <BugOutlined />
                  </AButton>
                </ATooltip>
              </div>
              <div class="card-content line-clamp-3" :title="tool.description">
                {{ tool.description || '暂无描述' }}
              </div>
              <div class="card-footer">
                <div class="footer-row">
                  <ATag v-if="tool.missing" color="warning" :bordered="false">已消失</ATag>
                  <ATag v-else :color="tool.enabled ? 'success' : 'error'" :bordered="false">全局可用</ATag>
                  <ASwitch
                    :checked="tool.enabled"
                    :loading="togglingMap.has(tool.id)"
                    :disabled="loading || readonly || togglingMap.has(tool.id)"
                    checked-children="开"
                    un-checked-children="关"
                    @change="(checked: boolean) => handleToggleEnabled(tool, checked)"
                  />
                </div>
                <div class="footer-row">
                  <ATag :color="tool.needConfirm ? 'warning' : 'success'" :bordered="false">需确认</ATag>
                  <ASwitch
                    :checked="tool.needConfirm"
                    :disabled="loading || readonly || !tool.enabled"
                    checked-children="开"
                    un-checked-children="关"
                    @change="(checked: boolean) => handleToggleNeedConfirm(tool, checked)"
                  />
                </div>
              </div>
            </div>
          </div>
        </template>
      </ASpin>
    </div>
  </ApboaModal>
</template>

<style scoped lang="scss">
.tool-governance {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.tool-governance-header {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.tool-governance-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.tool-governance-title {
  font-size: 16px;
  font-weight: 600;
}

.tool-card-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  max-height: calc(100vh - 200px);
  overflow-y: auto;

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

.tool-card {
  min-width: 0;
  min-height: 160px;
  overflow: hidden;
  padding: var(--spacing-md);
  background-color: var(--color-bg-white);
  border-radius: var(--border-radius-lg);
  box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.1);
  transition: all var(--transition-base);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);

  &:hover {
    box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
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
      background-color: #e3f2fd;
      color: #42a5f5;
      border-radius: var(--border-radius-xl);
      font-size: 18px;
      flex-shrink: 0;
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
    flex-direction: column;
    gap: 10px;
    padding-top: var(--spacing-xs);

    .footer-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
    }
  }
}
</style>
