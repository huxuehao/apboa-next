<script setup lang="ts">
/**
 * 成本中心-模型配价：LLM 模型价格 + 近30天用量合并视图，行内编辑批量改价。
 * 「按官网价填充全部」对未配价模型跑前缀匹配（Ollama 填 0）；
 * 改价保存后可选择按新价重算该模型历史流水。
 *
 * @author huxuehao
 */
import { ref, onMounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { ThunderboltOutlined } from '@ant-design/icons-vue'
import * as costApi from '@/api/cost'
import type { CostModelPricingRow } from '@/types'
import { matchOfficialPrice } from '@/components/model/officialModelPrices'
import { formatCny, formatTokens } from '../costFormat'

const loading = ref(false)
const rows = ref<CostModelPricingRow[]>([])

/** 行内编辑态：modelConfigId -> 草稿价 */
const editing = ref<Record<string, { input: number | null; output: number | null }>>({})
const savingId = ref<string | null>(null)

async function loadData() {
  loading.value = true
  try {
    const res = await costApi.modelPricingList()
    rows.value = res.data.data || []
  } catch (e) {
    console.error('加载模型配价失败:', e)
  } finally {
    loading.value = false
  }
}

onMounted(loadData)

function isPriced(row: CostModelPricingRow) {
  return row.inputPrice != null && row.outputPrice != null
}

function startEdit(row: CostModelPricingRow) {
  editing.value[row.modelConfigId] = { input: row.inputPrice, output: row.outputPrice }
}

function cancelEdit(row: CostModelPricingRow) {
  delete editing.value[row.modelConfigId]
}

/**
 * 保存单行价格；成功后询问是否按新价重算该模型历史流水
 */
async function saveEdit(row: CostModelPricingRow) {
  const draft = editing.value[row.modelConfigId]
  if (!draft) return
  if ((draft.input == null) !== (draft.output == null)) {
    message.warning('输入与输出单价要么都填、要么都留空（留空=未配价）')
    return
  }
  savingId.value = row.modelConfigId
  try {
    await costApi.updateModelPricing(row.modelConfigId, draft.input, draft.output)
    row.inputPrice = draft.input
    row.outputPrice = draft.output
    delete editing.value[row.modelConfigId]
    message.success(`「${row.name}」价格已保存`)
    if (draft.input != null) {
      confirmRecalc(row)
    }
  } catch (e) {
    console.error('保存价格失败:', e)
  } finally {
    savingId.value = null
  }
}

/** 改价后重算联动：只重算该模型的历史流水 */
function confirmRecalc(row: CostModelPricingRow) {
  Modal.confirm({
    title: '按新价重算历史？',
    content: `将把「${row.name}」近 30 天的流水按新单价重新计算成本（改价本身只影响之后的新账单）。`,
    okText: '重算',
    cancelText: '暂不',
    onOk: async () => {
      try {
        const res = await costApi.recalculate({ modelConfigId: row.modelConfigId })
        message.success(`已重算 ${res.data.data} 条流水`)
        loadData()
      } catch (e) {
        console.error('重算失败:', e)
      }
    }
  })
}

/**
 * 按官网价填充全部未配价模型：Ollama 填 0，其余前缀匹配价目表快照；
 * 未收录的汇总提示手动填写
 */
const fillingAll = ref(false)
async function fillAllUnpriced() {
  const targets = rows.value.filter(r => !isPriced(r))
  if (!targets.length) {
    message.info('没有未配价的模型')
    return
  }
  fillingAll.value = true
  let filled = 0
  const missed: string[] = []
  try {
    for (const row of targets) {
      const price = row.providerType === 'OLLAMA'
        ? { input: 0, output: 0 }
        : matchOfficialPrice(row.modelId)
      if (!price) {
        missed.push(row.name)
        continue
      }
      await costApi.updateModelPricing(row.modelConfigId, price.input, price.output)
      row.inputPrice = price.input
      row.outputPrice = price.output
      filled++
    }
    const missedText = missed.length ? `；未收录 ${missed.length} 个（${missed.join('、')}），请手动填写` : ''
    message.success(`已按官网价快照填充 ${filled} 个模型${missedText}`, 6)
  } finally {
    fillingAll.value = false
  }
}

const columns = [
  { title: '模型', key: 'name', width: 220, ellipsis: true },
  { title: '供应商', key: 'provider', width: 130, ellipsis: true },
  { title: '输入单价（¥/M tok）', key: 'inputPrice', align: 'right' as const, width: 170 },
  { title: '输出单价（¥/M tok）', key: 'outputPrice', align: 'right' as const, width: 170 },
  { title: '状态', key: 'status', width: 110 },
  { title: '近 30 天用量', key: 'usage', align: 'right' as const, width: 180 },
  { title: '操作', key: 'action', width: 150 }
]
</script>

<template>
  <div class="cost-pricing">
    <div class="pricing-toolbar flex items-center">
      <span class="pricing-hint">
        单价按「元 / 百万 token」填写（供应商官网报价可直接抄）；本地模型（Ollama）填 0；
        留空=未配价（只记 token 不计成本）。改价只影响之后的新账单，历史修正走保存后的重算确认。
      </span>
      <div class="flex-1"></div>
      <AButton :loading="fillingAll" @click="fillAllUnpriced">
        <ThunderboltOutlined /> 按官网价填充全部未配价
      </AButton>
    </div>

    <ATable
      :columns="columns"
      :data-source="rows"
      :loading="loading"
      row-key="modelConfigId"
      size="middle"
      :pagination="false"
      :scroll="{ x: 1130 }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'name'">
          <div class="model-cell">
            <span class="model-name">{{ record.name }}</span>
            <span class="model-id" :title="record.modelId">{{ record.modelId }}</span>
          </div>
          <ATag v-if="!record.enabled" color="default" :bordered="false">已禁用</ATag>
        </template>
        <template v-else-if="column.key === 'provider'">
          <span>{{ record.providerName || '—' }}</span>
        </template>
        <template v-else-if="column.key === 'inputPrice'">
          <AInputNumber
            v-if="editing[record.modelConfigId]"
            v-model:value="editing[record.modelConfigId]!.input"
            :min="0"
            :precision="4"
            size="small"
            style="width: 120px"
            placeholder="留空=未配价"
          />
          <span v-else class="num">{{ record.inputPrice != null ? record.inputPrice.toFixed(2) : '—' }}</span>
        </template>
        <template v-else-if="column.key === 'outputPrice'">
          <AInputNumber
            v-if="editing[record.modelConfigId]"
            v-model:value="editing[record.modelConfigId]!.output"
            :min="0"
            :precision="4"
            size="small"
            style="width: 120px"
            placeholder="留空=未配价"
          />
          <span v-else class="num">{{ record.outputPrice != null ? record.outputPrice.toFixed(2) : '—' }}</span>
        </template>
        <template v-else-if="column.key === 'status'">
          <ATag v-if="isPriced(record)" color="green" :bordered="false">已配价</ATag>
          <ATooltip v-else :title="`近30天有 ${formatTokens(record.unpricedTokens30d)} token 未计入成本`">
            <ATag color="orange" :bordered="false">未配价</ATag>
          </ATooltip>
        </template>
        <template v-else-if="column.key === 'usage'">
          <span class="num">{{ formatTokens(record.tokens30d) }} tok · {{ isPriced(record) ? formatCny(record.cost30d) : '？' }}</span>
        </template>
        <template v-else-if="column.key === 'action'">
          <template v-if="editing[record.modelConfigId]">
            <AButton type="link" size="small" :loading="savingId === record.modelConfigId" @click="saveEdit(record)">保存</AButton>
            <AButton type="text" size="small" @click="cancelEdit(record)">取消</AButton>
          </template>
          <AButton v-else type="link" size="small" @click="startEdit(record)">编辑</AButton>
        </template>
      </template>
    </ATable>
    <div class="note">
      价格表快照收录常见模型的官网价（<b>按原价、不含限时折扣</b>），填充后建议与官网核对；
      未收录的模型（如自定义命名）请查官网手动填写。
    </div>
  </div>
</template>

<style scoped lang="scss">
.cost-pricing {
  background: var(--color-bg-white);
  border: 1px solid var(--color-border-light);
  border-radius: var(--border-radius-lg);
  padding: var(--spacing-md) var(--spacing-lg);

  /* 放开全局 .ant-tag 120px 截断 */
  :deep(.ant-table-cell .ant-tag) {
    max-width: none;
  }
}

.pricing-toolbar {
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-md);

  .pricing-hint {
    font-size: var(--font-size-xs);
    color: var(--color-text-placeholder);
    max-width: 640px;
  }
}

.model-cell {
  display: inline-flex;
  flex-direction: column;
  min-width: 0;
  vertical-align: middle;
  margin-right: 6px;

  .model-name {
    font-weight: 600;
  }

  .model-id {
    font-size: var(--font-size-xs);
    color: var(--color-text-placeholder);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.muted {
  color: var(--color-text-secondary);
}

.num {
  font-variant-numeric: tabular-nums;
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
</style>
