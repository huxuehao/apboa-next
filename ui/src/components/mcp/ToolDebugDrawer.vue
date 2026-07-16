/**
 * 工具调试抽屉
 *
 * 参数输入 → 执行 → 结果展示
 *
 * @author huxuehao
 */
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { CloseCircleOutlined, PlayCircleOutlined } from '@ant-design/icons-vue'
import type { McpToolDebugResultVO, McpToolVO } from '@/types'
import * as mcpApi from '@/api/mcp'
import McpDebugForm from '@/components/mcp/McpDebugForm.vue'

const props = defineProps<{
  open: boolean
  tool: McpToolVO | null
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
}>()

const formParams = ref<Record<string, unknown>>({})
const executing = ref(false)
const result = ref<McpToolDebugResultVO | null>(null)

const hasSchema = computed(() => {
  const schema = props.tool?.inputSchema
  if (!schema) return false
  const props_ = (schema as Record<string, unknown>).properties
  return props_ && typeof props_ === 'object' && Object.keys(props_).length > 0
})

async function handleExecute() {
  if (!props.tool || executing.value) return

  executing.value = true
  result.value = null

  try {
    const res = await mcpApi.debugTool(props.tool.id, formParams.value)
    result.value = res.data.data
  } catch (e: unknown) {
    const errMsg = e instanceof Error ? e.message : '请求失败'
    result.value = {
      success: false,
      toolName: props.tool.toolName,
      content: null,
      errorMessage: errMsg,
      durationMs: 0,
      executedAt: new Date().toISOString()
    }
  } finally {
    executing.value = false
  }
}

function formatJson(obj: unknown): string {
  if (obj == null) return 'null'
  try {
    return JSON.stringify(obj, null, 2)
  } catch {
    return String(obj)
  }
}

watch(() => props.open, (val) => {
  if (val) {
    formParams.value = {}
    result.value = null
  }
})
</script>

<template>
  <ADrawer
    class="debug-drawer"
    :open="open"
    :closable="false"
    placement="right"
    :width="600"
    :body-style="{ padding: 0, display: 'flex', flexDirection: 'column', height: '100%' }"
  >
    <!-- 可滚动内容 -->
    <div class="drawer-scroll">
      <!-- 工具信息（无背景无边框，纯排版） -->
      <div class="drawer-intro">
        <h3 class="drawer-intro-title">{{ tool?.toolName }}</h3>
        <p v-if="tool?.description" class="drawer-intro-desc">{{ tool.description }}</p>
      </div>

      <div v-if="!hasSchema" class="empty-params text-placeholder">此工具无需输入参数，点击执行即可</div>

      <div v-else class="drawer-section">
        <div class="section-title">参数</div>
        <McpDebugForm v-model="formParams" :schema="tool?.inputSchema ?? null" />
      </div>

      <Transition name="result-fade">
        <div v-if="result" class="drawer-section result-area">
          <div class="section-title">
            执行结果
            <ATag :color="result.success ? 'default' : 'error'" :bordered="false" size="small">
              {{ result.success ? '成功' : '失败' }}
            </ATag>
            <span v-if="result.durationMs" class="text-placeholder text-xs">{{ result.durationMs }}ms</span>
          </div>
          <div class="result-scroll">
            <pre
              class="result-code"
              :class="{ 'result-error': !result.success }"
            >{{ result.success ? formatJson(result.content) : result.errorMessage }}</pre>
          </div>
        </div>
      </Transition>
    </div>

    <!-- 固定底部 -->
    <div class="drawer-foot">
      <AButton
        type="primary"
        :loading="executing"
        :disabled="!tool"
        @click="handleExecute"
      >
        <PlayCircleOutlined v-if="!executing" />
        {{ executing ? '执行中...' : '执行调试' }}
      </AButton>
      <AButton
        @click="emit('update:open', false)"
        type="default"
      >
        <CloseCircleOutlined />
        关闭调试抽屉
      </AButton>
    </div>
  </ADrawer>
</template>

<style scoped lang="scss">
/* 去掉 ADrawer header 底部白线 */
:deep(.ant-drawer-header) {
  border-bottom: none !important;
}

/* 可滚动区域 */
.drawer-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 0 24px 20px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* 工具信息 */
.drawer-intro {
  margin-top: 8px;
}

.drawer-intro-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin: 0;
  line-height: 1.4;
}

.drawer-intro-desc {
  font-size: var(--font-size-sm);
  line-height: 1.6;
  color: var(--color-text-placeholder);
  margin: 6px 0 0;
}

.empty-params {
  padding: 24px;
  text-align: center;
  font-size: var(--font-size-sm);
}

.drawer-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--color-text-primary);
}

.result-area {
  flex: 1;
  min-height: 0;
}

.result-scroll {
  max-height: 360px;
  overflow-y: auto;
}

.result-code {
  background: #fafafa;
  border: 1px solid #ebebeb;
  border-radius: var(--border-radius-lg);
  padding: 12px 16px;
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: var(--font-size-sm);
  line-height: 1.6;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;

  &.result-error {
    background: #fff2f0;
    border-color: #ffccc7;
    color: #820014;
  }
}

/* 固定底部按钮区 */
.drawer-foot {
  padding: 12px 24px;
  display: flex;
  gap: 12px;
  flex-shrink: 0;

  .ant-btn {
    flex: 1;
  }
}

/* 结果淡入 */
.result-fade-enter-active {
  transition: opacity 0.3s ease, transform 0.3s ease;
}
.result-fade-enter-from {
  opacity: 0;
  transform: translateY(8px);
}
</style>
