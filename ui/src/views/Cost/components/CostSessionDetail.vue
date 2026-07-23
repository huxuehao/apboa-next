<script setup lang="ts">
/**
 * 成本中心-会话明细钻取：按「实际发生」口径逐轮列出（含废弃分支灰色标记），
 * 汇总卡 + 按模型 chips + 逐轮表格 + 口径说明。
 *
 * @author huxuehao
 */
import { ref, watch } from 'vue'
import { ArrowLeftOutlined, ExportOutlined, InfoCircleOutlined } from '@ant-design/icons-vue'
import * as costApi from '@/api/cost'
import type { CostSessionDetailVO, CostRunItemVO } from '@/types'
import { formatCny, formatTokens, formatDuration } from '../costFormat'

const props = defineProps<{
  sessionId: string
}>()

defineEmits<{
  back: []
}>()

const loading = ref(false)
const data = ref<CostSessionDetailVO | null>(null)

async function loadData() {
  loading.value = true
  try {
    const res = await costApi.sessionDetail(props.sessionId)
    data.value = res.data.data
  } catch (e) {
    console.error('加载会话明细失败:', e)
  } finally {
    loading.value = false
  }
}

watch(() => props.sessionId, loadData, { immediate: true })

const columns = [
  { title: '#', key: 'idx', width: 46 },
  { title: '时间', key: 'createdAt', width: 150 },
  { title: '用户问题', key: 'userQuestion', ellipsis: true },
  { title: '回复摘要', key: 'assistantSummary', ellipsis: true },
  { title: '模型', key: 'modelLabel', width: 190 },
  { title: 'LLM调用', key: 'iterationCount', align: 'right' as const, width: 80 },
  { title: '输入 tok', key: 'inputTokens', align: 'right' as const, width: 90 },
  { title: '输出 tok', key: 'outputTokens', align: 'right' as const, width: 90 },
  { title: '耗时', key: 'durationMs', align: 'right' as const, width: 76 },
  { title: '成本', key: 'cost', align: 'right' as const, width: 96 },
  { title: '状态', key: 'status', width: 130 }
]

/** 废弃分支行灰化 */
function rowClassName(record: CostRunItemVO) {
  return record.onCurrentPath ? '' : 'dead-row'
}
</script>

<template>
  <div class="cost-detail">
    <a class="back-link" @click="$emit('back')"><ArrowLeftOutlined /> 返回会话账单</a>

    <ASpin :spinning="loading">
      <template v-if="data">
        <div class="detail-head flex items-center">
          <span class="detail-title">{{ data.title || '（无标题）' }}</span>
          <span class="detail-meta">
            会话 {{ data.sessionId }} · {{ data.agentName }} · {{ data.userName }}
          </span>
          <div class="flex-1"></div>
          <RouterLink :to="`/chat/${data.agentId}/${data.sessionId}`" target="_blank" class="jump-chat">
            <ExportOutlined /> 打开对话
          </RouterLink>
        </div>

        <!-- 汇总卡 -->
        <div class="summary-grid">
          <div class="stat-card">
            <div class="stat-label">会话总成本</div>
            <div class="stat-value money">{{ formatCny(data.totalCost) }}</div>
            <div v-if="data.unpricedRunCount > 0" class="stat-sub">另有 {{ data.unpricedRunCount }} 轮未配价未计入</div>
          </div>
          <div class="stat-card">
            <div class="stat-label">轮次</div>
            <div class="stat-value">{{ data.runCount }}</div>
            <div v-if="data.discardedRunCount > 0" class="stat-sub">其中 {{ data.discardedRunCount }} 轮为废弃分支</div>
          </div>
          <div class="stat-card">
            <div class="stat-label">输入 token</div>
            <div class="stat-value">{{ formatTokens(data.inputTokens) }}</div>
          </div>
          <div class="stat-card">
            <div class="stat-label">输出 token</div>
            <div class="stat-value">{{ formatTokens(data.outputTokens) }}</div>
          </div>
          <div class="stat-card">
            <div class="stat-label">废弃分支成本</div>
            <div class="stat-value">{{ formatCny(data.discardedCost) }}</div>
            <div v-if="Number(data.totalCost) > 0" class="stat-sub">
              占 {{ Math.round(Number(data.discardedCost) / Number(data.totalCost) * 100) }}% —— 反复重新生成的代价
            </div>
          </div>
        </div>

        <!-- 按模型花费小计：会话中途可切换模型，此处按模型分组显示各自跑了几轮、花了多少钱 -->
        <div class="model-chips">
          <span class="chips-label">
            按模型花费小计
            <ATooltip title="一个会话中途可以切换模型，这里按模型分组：每个模型各自回答了几轮、花了多少钱，加起来等于会话总成本">
              <InfoCircleOutlined class="chips-help" />
            </ATooltip>
            ：
          </span>
          <ATag v-for="m in data.byModel" :key="m.modelLabel" color="blue">
            {{ m.modelLabel }}｜{{ m.runCount }} 轮｜{{ formatCny(m.cost) }}
          </ATag>
          <span v-if="data.byModel.length > 1" class="chips-note">共用过 {{ data.byModel.length }} 个模型</span>
        </div>

        <!-- 逐轮明细 -->
        <div class="runs-panel">
          <div class="panel-head">
            <span class="panel-title">逐轮明细</span>
            <span class="panel-hint">按发生时间排列，每行 = 一次完整回复（内部可能含多次 LLM 调用）</span>
          </div>
          <ATable
            :columns="columns"
            :data-source="data.runs"
            :pagination="false"
            size="middle"
            row-key="recordId"
            :row-class-name="rowClassName"
            :scroll="{ x: 1240 }"
          >
            <template #bodyCell="{ column, record, index }">
              <template v-if="column.key === 'idx'">
                <span class="num">{{ index + 1 }}</span>
              </template>
              <template v-else-if="column.key === 'createdAt'">
                <span class="num muted">{{ record.createdAt }}</span>
              </template>
              <template v-else-if="column.key === 'userQuestion'">
                <span class="q-text" :title="record.userQuestion || ''">{{ record.userQuestion || '—' }}</span>
              </template>
              <template v-else-if="column.key === 'assistantSummary'">
                <span class="q-text muted" :title="record.assistantSummary || ''">{{ record.assistantSummary || '—' }}</span>
              </template>
              <template v-else-if="column.key === 'modelLabel'">
                <ATag color="blue" :bordered="false">{{ record.modelLabel }}</ATag>
              </template>
              <template v-else-if="column.key === 'iterationCount'">
                <ATooltip v-if="record.iterationCount > 1" title="本轮回复经历多次推理（如调用工具后再回答）">
                  <span class="num">{{ record.iterationCount }} ⚒</span>
                </ATooltip>
                <span v-else class="num">1</span>
              </template>
              <template v-else-if="column.key === 'inputTokens'">
                <span class="num">{{ formatTokens(record.inputTokens) }}</span>
              </template>
              <template v-else-if="column.key === 'outputTokens'">
                <span class="num">{{ formatTokens(record.outputTokens) }}</span>
              </template>
              <template v-else-if="column.key === 'durationMs'">
                <span class="num muted">{{ formatDuration(record.durationMs) }}</span>
              </template>
              <template v-else-if="column.key === 'cost'">
                <ATag v-if="record.cost == null" color="orange" :bordered="false">未配价</ATag>
                <span v-else class="money num">{{ formatCny(record.cost) }}</span>
              </template>
              <template v-else-if="column.key === 'status'">
                <ATag v-if="record.onCurrentPath" color="green" :bordered="false">当前链</ATag>
                <ATooltip v-else title="被「重新生成」顶替：聊天界面已看不到这一轮，但当时真实调用了模型、真实产生了费用">
                  <ATag :bordered="false">已废弃</ATag>
                </ATooltip>
              </template>
            </template>
          </ATable>
          <div class="note">
            <b>口径说明：</b>灰色行是被重新生成顶替的废弃分支——聊天界面已看不到它们，
            但当时真实调用了模型、真实产生了费用，故计入会话总成本。
          </div>
        </div>
      </template>
    </ASpin>
  </div>
</template>

<style scoped lang="scss">
.cost-detail {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.back-link {
  color: var(--color-primary);
  cursor: pointer;
  width: fit-content;
}

.detail-head {
  gap: var(--spacing-md);
  flex-wrap: wrap;

  .detail-title {
    font-size: var(--font-size-lg);
    font-weight: 700;
  }

  .detail-meta {
    font-size: var(--font-size-xs);
    color: var(--color-text-placeholder);
    font-variant-numeric: tabular-nums;
  }

  .jump-chat {
    color: var(--color-primary);
    font-size: var(--font-size-sm);
  }
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: var(--spacing-md);

  @media (max-width: 1000px) {
    grid-template-columns: repeat(2, 1fr);
  }
}

.stat-card {
  background: var(--color-bg-white);
  border: 1px solid var(--color-border-light);
  border-radius: var(--border-radius-lg);
  padding: var(--spacing-md) var(--spacing-lg);

  .stat-label {
    font-size: var(--font-size-sm);
    color: var(--color-text-secondary);
  }

  .stat-value {
    font-size: 20px;
    font-weight: 700;
    margin-top: 2px;
    font-variant-numeric: tabular-nums;
  }

  .stat-sub {
    font-size: var(--font-size-xs);
    color: var(--color-text-placeholder);
    margin-top: 2px;
  }
}

.model-chips {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--spacing-xs);

  .chips-label {
    font-size: var(--font-size-sm);
    color: var(--color-text-secondary);
    font-weight: 600;
  }

  .chips-help {
    color: var(--color-text-placeholder);
    cursor: pointer;
    font-size: 12px;
  }

  .chips-note {
    font-size: var(--font-size-xs);
    color: var(--color-text-placeholder);
  }

  /* 全局 .ant-tag 有 max-width:120px 截断，模型名与金额必须完整可读 */
  :deep(.ant-tag) {
    max-width: none;
  }
}

.runs-panel {
  background: var(--color-bg-white);
  border: 1px solid var(--color-border-light);
  border-radius: var(--border-radius-lg);
  padding: var(--spacing-md) var(--spacing-lg);

  .panel-head {
    display: flex;
    align-items: baseline;
    gap: var(--spacing-sm);
    margin-bottom: var(--spacing-sm);

    .panel-title {
      font-weight: 600;
    }

    .panel-hint {
      font-size: var(--font-size-xs);
      color: var(--color-text-placeholder);
    }
  }

  .note {
    font-size: var(--font-size-xs);
    color: var(--color-text-placeholder);
    padding-top: var(--spacing-sm);
    border-top: 1px dashed var(--color-border-light);
    margin-top: var(--spacing-sm);

    b {
      color: var(--color-text-secondary);
    }
  }

  :deep(.dead-row) td {
    background: var(--color-bg-light) !important;
    color: var(--color-text-placeholder);
  }

  /* 逐轮表格里的模型/状态 tag 同样放开全局 120px 截断 */
  :deep(.ant-table-cell .ant-tag) {
    max-width: none;
  }
}

.q-text {
  font-size: var(--font-size-sm);
}

.muted {
  color: var(--color-text-secondary);
}

.num {
  font-variant-numeric: tabular-nums;
}

.money {
  color: #d4380d;
}
</style>
