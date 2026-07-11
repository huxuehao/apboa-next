<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { DeleteOutlined, DownloadOutlined, HistoryOutlined, ReloadOutlined, FlagOutlined } from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import * as workflowApi from '@/api/workflow'
import type { Workflow, WorkflowStatus, WorkflowVersion } from '@/types/workflow'

const open = defineModel<boolean>('open', { default: false })

const props = defineProps<{
  workflowId?: string
  workflowName?: string
  currentVersion?: string
  status?: WorkflowStatus
}>()

const emit = defineEmits<{
  loaded: [workflow: Workflow]
}>()

const loading = ref(false)
const versions = ref<WorkflowVersion[]>([])

const hasVersions = computed(() => versions.value.length > 0)

watch(
  () => [open.value, props.workflowId],
  () => {
    if (open.value && props.workflowId) {
      loadVersions()
    }
  },
)

async function loadVersions() {
  if (!props.workflowId) return
  loading.value = true
  try {
    const response = await workflowApi.workflowVersions(props.workflowId)
    versions.value = response.data.data || []
  } finally {
    loading.value = false
  }
}

function isCurrent(item: WorkflowVersion) {
  return props.status === 'PUBLISHED' && item.version === props.currentVersion
}

function isLoaded(item: WorkflowVersion) {
  return item.version === props.currentVersion
}

function currentTagText(item: WorkflowVersion) {
  if (isCurrent(item)) return '当前发布版本'
  if (isLoaded(item)) return '当前载入版本'
  return '历史版本'
}

function formatTime(value?: string) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 19)
}

function nodeCount(item: WorkflowVersion) {
  return Array.isArray(item.config?.nodes) ? item.config.nodes.length : 0
}

function handleLoad(item: WorkflowVersion) {
  if (!props.workflowId) return
  Modal.confirm({
    title: `载入 v${item.version}`,
    content: '载入后会用该历史版本覆盖当前草稿。当前未发布修改将被替换，请确认已经保存需要保留的内容。',
    okText: '载入版本',
    cancelText: '取消',
    icon: null,
    onOk: async () => {
      const response = await workflowApi.workflowRollback(props.workflowId!, item.version)
      message.success(`已载入 v${item.version}`)
      emit('loaded', response.data.data)
      open.value = false
    },
  })
}

function handleDelete(item: WorkflowVersion) {
  if (!props.workflowId || isLoaded(item)) return
  Modal.confirm({
    title: `删除 v${item.version}`,
    content: '删除后该历史版本不可恢复，已发布的当前版本不会被允许删除。',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    icon: null,
    onOk: async () => {
      await workflowApi.workflowDeleteVersion(props.workflowId!, item.version)
      message.success(`已删除 v${item.version}`)
      await loadVersions()
    },
  })
}
</script>

<template>
  <AModal
    v-model:open="open"
    title="版本记录"
    :width="760"
    :destroy-on-close="true"
    :footer="null"
  >
    <div class="workflow-version-modal">
      <div class="version-head">
        <div class="version-head-main">
          <div class="version-title">
            <HistoryOutlined />
            <span>{{ workflowName || '未命名工作流' }}</span>
          </div>
          <div class="version-subtitle">
            当前版本 v{{ currentVersion || '0' }} · {{ status === 'PUBLISHED' ? '已发布' : '草稿' }}
          </div>
        </div>
        <AButton :loading="loading" @click="loadVersions">
          <template #icon><ReloadOutlined /></template>
          刷新
        </AButton>
      </div>

      <ASpin :spinning="loading">
        <div v-if="hasVersions" class="version-list">
          <div v-for="item in versions" :key="item.id" class="version-item">
            <div class="version-info">
              <div class="version-line">
                <span class="version-name">v{{ item.version.toUpperCase() }} 版本 · {{ formatTime(item.createdAt) }}</span>
                <ATooltip title="当前版本">
                  <span v-if="isLoaded(item)"> · <FlagOutlined style="color: #2782FF" /></span>
                </ATooltip>
              </div>
              <div class="version-remark" :title="item.remark || '暂无备注'">
                {{ item.remark || '暂无备注' }}
              </div>
            </div>
            <div class="version-actions">
              <ATooltip title="载入该版本">
                <AButton type="text" size="small" @click="handleLoad(item)">
                  <DownloadOutlined />
                </AButton>
              </ATooltip>
              <ATooltip :title="isLoaded(item) ? '当前版本不能删除' : '删除该版本'">
                <AButton
                  type="text"
                  size="small"
                  danger
                  :disabled="isLoaded(item)"
                  @click="handleDelete(item)"
                >
                  <DeleteOutlined />
                </AButton>
              </ATooltip>
            </div>
          </div>
        </div>
        <div v-else class="version-empty">
          <AEmpty description="暂无版本记录" />
        </div>
      </ASpin>
    </div>
  </AModal>
</template>

<style scoped lang="scss">
.workflow-version-modal {
  min-height: 360px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.version-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.version-head-main {
  min-width: 0;
}

.version-title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #262626;
  font-size: 16px;
  font-weight: 700;
}

.version-subtitle {
  margin-top: 4px;
  color: #8c8c8c;
  font-size: 12px;
}

.version-list {
  max-height: min(540px, calc(100vh - 260px));
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-right: 4px;
}

.version-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fff;
  transition: background 0.12s ease;

  &:hover {
    background: #fafafa;
  }
}

.version-marker {
  width: 8px;
  height: 42px;
  border-radius: 4px;
  background: #1677ff;
  flex-shrink: 0;
}

.version-info {
  min-width: 0;
  flex: 1;
}

.version-line,
.version-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.version-name {
  color: #262626;
  font-size: 14px;
  font-weight: 700;
}

.version-meta {
  margin-top: 4px;
  color: #8c8c8c;
  font-size: 12px;
}

.version-remark {
  margin-top: 4px;
  color: #595959;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.version-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}

.version-empty {
  min-height: 260px;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
