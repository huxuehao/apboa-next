<script setup lang="ts">
/**
 * 历史版本抽屉：列出当前用户的版本快照，支持回滚与删除。
 * 回滚前弹确认询问是否将当前配置存为历史版本。
 *
 * @author huxuehao
 */
import { ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { DeleteOutlined, HistoryOutlined } from '@ant-design/icons-vue'
import dayjs from 'dayjs'
import {
  dashboardHistoryList,
  dashboardRollback,
  dashboardDeleteHistory,
} from '@/api/dashboard'
import type { DashboardHistoryEntity, DashboardDsl } from '@/types/dashboard'

const props = defineProps<{ open: boolean; dashboardId: string | null }>()
const emit = defineEmits<{
  (e: 'update:open', v: boolean): void
  (e: 'rolledback', config: DashboardDsl): void
}>()

const list = ref<DashboardHistoryEntity[]>([])
const loading = ref(false)

// 回滚确认弹窗（三选一：保存当前再回滚 / 直接回滚 / 取消）
const confirmOpen = ref(false)
const rolling = ref(false)
const pending = ref<DashboardHistoryEntity | null>(null)

async function load() {
  if (!props.dashboardId) return
  loading.value = true
  try {
    const resp = await dashboardHistoryList(props.dashboardId)
    list.value = resp.data.data || []
  } finally {
    loading.value = false
  }
}

watch(
  () => props.open,
  (v) => {
    if (v) load()
  },
)

function formatTime(t?: string): string {
  return t ? dayjs(t).format('YYYY-MM-DD HH:mm') : ''
}

function onRollback(item: DashboardHistoryEntity) {
  pending.value = item
  confirmOpen.value = true
}

async function doRollback(snapshotCurrent: boolean) {
  if (!props.dashboardId || !pending.value) return
  rolling.value = true
  try {
    const resp = await dashboardRollback(props.dashboardId, pending.value.id, { snapshotCurrent })
    const config = resp.data.data as DashboardDsl
    message.success('已回滚到该版本')
    confirmOpen.value = false
    pending.value = null
    emit('rolledback', config)
    load()
  } finally {
    rolling.value = false
  }
}

async function onDelete(item: DashboardHistoryEntity) {
  if (!props.dashboardId) return
  await dashboardDeleteHistory(props.dashboardId, item.id)
  message.success('已删除该版本')
  load()
}
</script>

<template>
  <a-drawer
    :open="open"
    title="历史版本"
    width="420"
    @update:open="emit('update:open', $event)"
  >
    <a-spin :spinning="loading">
      <div v-if="list.length" class="hv-list">
        <div v-for="item in list" :key="item.id" class="hv-item">
          <div class="hv-main">
            <div class="hv-note">{{ item.note || '未命名版本' }}</div>
            <div class="hv-time">{{ formatTime(item.createdAt) }}</div>
          </div>
          <div class="hv-ops">
            <a-button type="link" size="small" @click="onRollback(item)">回滚</a-button>
            <a-popconfirm title="删除该历史版本？" @confirm="onDelete(item)">
              <a-button type="text" size="small" danger>
                <template #icon><DeleteOutlined /></template>
              </a-button>
            </a-popconfirm>
          </div>
        </div>
      </div>
      <a-empty v-else description="暂无历史版本">
        <template #image><HistoryOutlined class="hv-empty-icon" /></template>
      </a-empty>
    </a-spin>

    <a-modal
      v-model:open="confirmOpen"
      title="回滚到该版本"
      :footer="null"
      width="420px"
    >
      <p class="hv-confirm-text">
        回滚前是否将当前配置保存为一个历史版本？避免误回滚丢失当前布局。
      </p>
      <div class="hv-confirm-actions">
        <a-button @click="confirmOpen = false">取消</a-button>
        <a-button :loading="rolling" @click="doRollback(false)">直接回滚</a-button>
        <a-button type="primary" :loading="rolling" @click="doRollback(true)">
          保存当前再回滚
        </a-button>
      </div>
    </a-modal>
  </a-drawer>
</template>

<style scoped lang="scss">
.hv-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.hv-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
}

.hv-main {
  min-width: 0;
}

.hv-note {
  font-size: 14px;
  color: #1a1a1a;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hv-time {
  margin-top: 2px;
  font-size: 12px;
  color: #999;
}

.hv-ops {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 2px;
}

.hv-empty-icon {
  font-size: 40px;
  color: #d9d9d9;
}

.hv-confirm-text {
  margin: 0 0 20px;
  font-size: 13px;
  color: #595959;
  line-height: 1.7;
}

.hv-confirm-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
