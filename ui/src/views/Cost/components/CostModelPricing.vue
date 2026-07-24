<script setup lang="ts">
/**
 * 成本中心-模型配价：LLM 模型价格 + 近30天用量合并视图，行内编辑批量改价。
 * 「按官网价填充全部」对未配价模型跑前缀匹配（Ollama 填 0）；
 * 改价保存后可选择按新价重算该模型历史流水。
 *
 * @author huxuehao
 */
import { computed, ref, onMounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { SearchOutlined, ThunderboltOutlined } from '@ant-design/icons-vue'
import * as costApi from '@/api/cost'
import type { CostModelPricingRow } from '@/types'
import { matchOfficialPrice } from '@/components/model/officialModelPrices'
import { formatCny, formatTokens } from '../costFormat'

const loading = ref(false)
const rows = ref<CostModelPricingRow[]>([])
const searchQuery = ref('')
const pricingFilter = ref<'all' | 'unpriced' | 'priced'>('all')

/** 行内编辑态：modelConfigId -> 草稿价 */
const editing = ref<Record<string, { input: number | null; output: number | null }>>({})
const savingId = ref<string | null>(null)

const pricingOptions = [
  { label: '全部', value: 'all' },
  { label: '待配价', value: 'unpriced' },
  { label: '已配价', value: 'priced' }
]

const pricedCount = computed(() => rows.value.filter(isPriced).length)
const unpricedCount = computed(() => rows.value.length - pricedCount.value)
const pricingCoverage = computed(() => rows.value.length > 0 ? pricedCount.value / rows.value.length * 100 : 0)
const totalTokens30d = computed(() => rows.value.reduce((sum, row) => sum + Number(row.tokens30d || 0), 0))
const totalCost30d = computed(() => rows.value.reduce((sum, row) => sum + Number(row.cost30d || 0), 0))
const totalRuns30d = computed(() => rows.value.reduce((sum, row) => sum + Number(row.runCount30d || 0), 0))
const totalUnpricedTokens30d = computed(() => rows.value.reduce((sum, row) => sum + Number(row.unpricedTokens30d || 0), 0))

const filteredRows = computed(() => {
  const keyword = searchQuery.value.trim().toLowerCase()
  return rows.value.filter(row => {
    if (pricingFilter.value === 'priced' && !isPriced(row)) return false
    if (pricingFilter.value === 'unpriced' && isPriced(row)) return false
    if (!keyword) return true
    return [row.name, row.modelId, row.providerName, row.providerType]
      .some(value => String(value || '').toLowerCase().includes(keyword))
  })
})

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
  { title: '模型', key: 'name', width: 260 },
  { title: '供应商', key: 'provider', width: 150 },
  { title: '模型单价（¥ / 百万 Token）', key: 'pricing', width: 330 },
  { title: '配价状态', key: 'status', width: 105 },
  { title: '近 30 天消耗', key: 'usage', width: 205 },
  { title: '操作', key: 'action', width: 125 }
]
</script>

<template>
  <div class="cost-pricing">
    <div class="pricing-summary">
      <div class="summary-item primary">
        <div class="summary-label">模型配置</div>
        <div class="summary-value">{{ rows.length }}<span class="summary-unit">个</span></div>
        <div class="summary-note">其中 {{ rows.filter(row => row.enabled).length }} 个已启用</div>
      </div>
      <div class="summary-item" :class="{ warning: unpricedCount > 0 }">
        <div class="summary-label">配价覆盖率</div>
        <div class="summary-value">{{ pricingCoverage.toFixed(0) }}%</div>
        <div class="summary-note">已配 {{ pricedCount }} · 待配 {{ unpricedCount }}</div>
      </div>
      <div class="summary-item">
        <div class="summary-label">近 30 天 Token</div>
        <div class="summary-value">{{ formatTokens(totalTokens30d) }}</div>
        <div class="summary-note">共 {{ Number(totalRuns30d).toLocaleString() }} 次模型回复</div>
      </div>
      <div class="summary-item" :class="{ warning: totalUnpricedTokens30d > 0 }">
        <div class="summary-label">近 30 天已计成本</div>
        <div class="summary-value money">{{ formatCny(totalCost30d) }}</div>
        <div class="summary-note">
          <span v-if="totalUnpricedTokens30d > 0">另有 {{ formatTokens(totalUnpricedTokens30d) }} Token 未计价</span>
          <span v-else>全部用量均已纳入成本</span>
        </div>
      </div>
    </div>

    <section class="pricing-panel">
      <div class="pricing-head">
        <div>
          <div class="section-title">模型价格管理</div>
          <div class="section-hint">输入、输出价格均按人民币元 / 百万 Token 维护。</div>
        </div>
        <AButton :loading="fillingAll" @click="fillAllUnpriced">
          <ThunderboltOutlined />
          补全未配价模型
        </AButton>
      </div>

      <div class="pricing-guide">
        <div class="guide-step">
          <span class="guide-index">1</span>
          <span><b>云端模型</b>参考供应商官网原价填写</span>
        </div>
        <div class="guide-step">
          <span class="guide-index">2</span>
          <span><b>本地模型</b>无外部调用费时填写 0</span>
        </div>
        <div class="guide-step">
          <span class="guide-index">3</span>
          <span><b>保存新价</b>后可选择重算近 30 天历史</span>
        </div>
      </div>

      <div class="pricing-toolbar">
        <AInput v-model:value="searchQuery" allow-clear placeholder="搜索模型、Model ID 或供应商" class="model-search">
          <template #prefix><SearchOutlined /></template>
        </AInput>
        <ASegmented v-model:value="pricingFilter" :options="pricingOptions" />
        <span class="filter-result">显示 {{ filteredRows.length }} / {{ rows.length }} 个模型</span>
      </div>

      <ATable
        :columns="columns"
        :data-source="filteredRows"
        :loading="loading"
        row-key="modelConfigId"
        size="small"
        :pagination="false"
        :scroll="{ x: 1175 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'name'">
            <div class="model-cell">
              <div class="model-title-row">
                <span class="model-name" :title="record.name">{{ record.name }}</span>
                <ATag v-if="!record.enabled" color="default" :bordered="false">已禁用</ATag>
              </div>
              <span class="model-id" :title="record.modelId">{{ record.modelId }}</span>
            </div>
          </template>
          <template v-else-if="column.key === 'provider'">
            <div class="provider-cell">
              <span>{{ record.providerName || '—' }}</span>
              <span class="provider-type">{{ record.providerType || '未知类型' }}</span>
            </div>
          </template>
          <template v-else-if="column.key === 'pricing'">
            <div v-if="editing[record.modelConfigId]" class="price-editor">
              <label>
                <span>输入</span>
                <AInputNumber
                  v-model:value="editing[record.modelConfigId]!.input"
                  :min="0"
                  :precision="4"
                  size="small"
                  placeholder="未配价"
                />
              </label>
              <label>
                <span>输出</span>
                <AInputNumber
                  v-model:value="editing[record.modelConfigId]!.output"
                  :min="0"
                  :precision="4"
                  size="small"
                  placeholder="未配价"
                />
              </label>
            </div>
            <div v-else class="price-view">
              <div class="price-pair">
                <span class="price-label">输入</span>
                <b class="num">{{ record.inputPrice != null ? `¥${record.inputPrice.toFixed(4)}` : '—' }}</b>
              </div>
              <span class="price-divider"></span>
              <div class="price-pair">
                <span class="price-label">输出</span>
                <b class="num">{{ record.outputPrice != null ? `¥${record.outputPrice.toFixed(4)}` : '—' }}</b>
              </div>
            </div>
          </template>
          <template v-else-if="column.key === 'status'">
            <ATag v-if="isPriced(record)" color="green" :bordered="false">已配价</ATag>
            <ATooltip v-else :title="`近30天有 ${formatTokens(record.unpricedTokens30d)} Token 未计入成本`">
              <ATag color="orange" :bordered="false">待配价</ATag>
            </ATooltip>
          </template>
          <template v-else-if="column.key === 'usage'">
            <div class="usage-cell">
              <span class="usage-main num">{{ formatTokens(record.tokens30d) }} Token</span>
              <span class="usage-sub num">{{ Number(record.runCount30d).toLocaleString() }} 次回复</span>
              <span v-if="isPriced(record)" class="usage-cost money num">{{ formatCny(record.cost30d) }}</span>
              <span v-else class="usage-unpriced">尚未计入成本</span>
            </div>
          </template>
          <template v-else-if="column.key === 'action'">
            <div v-if="editing[record.modelConfigId]" class="action-group">
              <AButton type="primary" size="small" :loading="savingId === record.modelConfigId" @click="saveEdit(record)">保存</AButton>
              <AButton type="text" size="small" @click="cancelEdit(record)">取消</AButton>
            </div>
            <AButton v-else type="link" size="small" @click="startEdit(record)">编辑单价</AButton>
          </template>
        </template>
        <template #emptyText>
          <AEmpty description="没有符合条件的模型" />
        </template>
      </ATable>

      <div class="pricing-note">
        官网价格快照按原价维护，不包含限时折扣；自动填充后仍建议与供应商官网核对。自定义模型名无法匹配时，请手动填写。
      </div>
    </section>
  </div>
</template>

<style scoped lang="scss">
.cost-pricing {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.pricing-summary {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--spacing-md);

  @media (max-width: 900px) {
    grid-template-columns: repeat(2, 1fr);
  }
}

.summary-item {
  position: relative;
  overflow: hidden;
  min-width: 0;
  padding: var(--spacing-md) var(--spacing-lg);
  border: 1px solid var(--color-border-light);
  border-radius: var(--border-radius-lg);
  background: var(--color-bg-white);

  &::before {
    position: absolute;
    inset: 0 auto 0 0;
    width: 3px;
    content: '';
    background: var(--color-border-base);
  }

  &.primary::before { background: var(--color-primary); }
  &.warning::before { background: #fa8c16; }
}

.summary-label {
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.summary-value {
  margin-top: 2px;
  font-size: 24px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.summary-unit {
  margin-left: 4px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  font-weight: 400;
}

.summary-note {
  min-height: 18px;
  margin-top: 2px;
  color: var(--color-text-placeholder);
  font-size: var(--font-size-xs);
  font-variant-numeric: tabular-nums;
}

.pricing-panel {
  min-width: 0;
  padding: var(--spacing-md) var(--spacing-lg) 0;
  border: 1px solid var(--color-border-light);
  border-radius: var(--border-radius-lg);
  background: var(--color-bg-white);

  :deep(.ant-table-cell .ant-tag) {
    height: auto;
    max-width: none;
    margin: 0;
    white-space: normal;
  }
}

.pricing-head,
.pricing-toolbar {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
}

.pricing-head {
  justify-content: space-between;
}

.section-title {
  font-weight: 600;
}

.section-hint,
.filter-result {
  margin-top: 2px;
  color: var(--color-text-placeholder);
  font-size: var(--font-size-xs);
}

.pricing-guide {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1px;
  overflow: hidden;
  margin: var(--spacing-md) 0;
  border: 1px solid #d6e4ff;
  border-radius: var(--border-radius-md);
  background: #d6e4ff;

  @media (max-width: 900px) {
    grid-template-columns: 1fr;
  }
}

.guide-step {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 10px 12px;
  color: var(--color-text-secondary);
  background: #f5f9ff;
  font-size: var(--font-size-xs);

  b { color: var(--color-text); }
}

.guide-index {
  flex: 0 0 auto;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  color: #fff;
  background: var(--color-primary);
  text-align: center;
  font-size: 11px;
  font-weight: 600;
  line-height: 20px;
}

.pricing-toolbar {
  margin-bottom: var(--spacing-md);
  flex-wrap: wrap;
}

.model-search {
  width: 300px;
}

.filter-result {
  margin-left: auto;
}

.model-cell,
.provider-cell,
.usage-cell {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.model-title-row {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 6px;
}

.model-name {
  overflow: hidden;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.model-id,
.provider-type,
.usage-sub {
  margin-top: 3px;
  overflow: hidden;
  color: var(--color-text-placeholder);
  font-size: var(--font-size-xs);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.price-editor,
.price-view {
  display: grid;
  grid-template-columns: 1fr 1fr;
  align-items: center;
  gap: 12px;
}

.price-editor label {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr);
  align-items: center;
  gap: 5px;
  color: var(--color-text-placeholder);
  font-size: var(--font-size-xs);

  :deep(.ant-input-number) { width: 100%; }
}

.price-view {
  position: relative;
}

.price-pair {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.price-label {
  color: var(--color-text-placeholder);
  font-size: var(--font-size-xs);
}

.price-divider {
  position: absolute;
  top: 3px;
  bottom: 3px;
  left: 50%;
  width: 1px;
  background: var(--color-border-light);
}

.usage-main {
  font-weight: 600;
}

.usage-cost,
.usage-unpriced {
  margin-top: 4px;
  font-size: var(--font-size-xs);
}

.usage-unpriced {
  color: #d46b08;
}

.action-group {
  display: flex;
  gap: 2px;
}

.pricing-note {
  padding: 11px 0;
  border-top: 1px dashed var(--color-border-light);
  color: var(--color-text-placeholder);
  font-size: var(--font-size-xs);
}

.money {
  color: #d4380d;
}

.num {
  font-variant-numeric: tabular-nums;
}
</style>
