<script setup lang="ts">
/**
 * KPI 趋势卡片 数据集使用说明
 *
 * @author huxuehao
 */
</script>

<template>
  <div class="guide-content">
    <h2 class="guide-title">KPI 趋势</h2>
    <p class="guide-intro">
      KPI 趋势卡片展示<b>关键指标 + 涨跌 + 迷你走势</b>：数据集返回<b>按时间排序的多行</b>，取最新值为主数字，整列为 sparkline。
    </p>

    <h3 class="section-title">数据集要求</h3>
    <ul class="req-list">
      <li>返回<b>多行</b>并按时间<b>升序</b>（用 <code>ORDER BY</code>），最后一行为最新</li>
      <li>包含<b>取值/趋势列</b>（数字）：主数字取最后一行，sparkline 取整列</li>
      <li>可选<b>环比列</b>；未提供时由趋势<b>首尾自动</b>算涨跌百分比</li>
    </ul>

    <h3 class="section-title">字段映射</h3>
    <div class="field-map">
      <div class="fm-row">
        <span class="fm-key">取值列</span>
        <span class="fm-val">主数字（最新行），如 <code>val</code></span>
      </div>
      <div class="fm-row">
        <span class="fm-key">趋势列</span>
        <span class="fm-val">sparkline 走势，通常与取值列相同</span>
      </div>
      <div class="fm-row">
        <span class="fm-key">环比列</span>
        <span class="fm-val">可选；填了则直接展示该值(%)</span>
      </div>
    </div>

    <h3 class="section-title">SQL 示例</h3>
    <pre class="code-block">select month, sum(amount) as val
from sales_view
where tenant_id = :currentTenantId
group by month
order by month</pre>

    <h3 class="section-title">结果如何变成卡片</h3>
    <div class="schematic">
      <div class="mock-table">
        <div class="mt-row mt-head">
          <div class="mt-cell">month</div>
          <div class="mt-cell">val</div>
        </div>
        <div class="mt-row"><div class="mt-cell mt-x">01</div><div class="mt-cell mt-y">90</div></div>
        <div class="mt-row"><div class="mt-cell mt-x">02</div><div class="mt-cell mt-y">110</div></div>
        <div class="mt-row"><div class="mt-cell mt-x">03</div><div class="mt-cell mt-y">128</div></div>
      </div>
      <div class="sc-arrow">→</div>
      <div class="mock-kpi">
        <div class="mk-label">销售额</div>
        <div class="mk-value">128</div>
        <div class="mk-delta">▲ +42.2%</div>
        <svg class="mk-spark" viewBox="0 0 100 24" preserveAspectRatio="none">
          <polyline points="0,18 50,10 100,3" fill="none" stroke="#52c41a" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </div>
      <div class="sc-caption">最后一行作主数字，整列作走势，首尾算环比</div>
    </div>

    <div class="tip-box">
      <span class="tip-icon">!</span>
      <span>
        <b>只查询：</b>仅支持 SELECT。务必 <code>ORDER BY</code> 时间升序，否则"最新值"与走势方向会错。
      </span>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/dashboard/guide' as *;

.mock-kpi {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 120px;
  padding: 12px 16px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fff;
}

.mk-label {
  font-size: 12px;
  color: #999;
}

.mk-value {
  font-size: 24px;
  font-weight: 700;
  color: #1a1a1a;
  line-height: 1.1;
}

.mk-delta {
  font-size: 12px;
  color: #52c41a;
}

.mk-spark {
  width: 100%;
  height: 24px;
  margin-top: 4px;
}
</style>
