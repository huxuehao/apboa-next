<script setup lang="ts">
/**
 * 进度环/完成率 数据集使用说明
 *
 * @author huxuehao
 */
</script>

<template>
  <div class="guide-content">
    <h2 class="guide-title">进度环 / 完成率</h2>
    <p class="guide-intro">
      进度环展示<b>当前值相对目标的完成百分比</b>：数据集返回<b>第一行</b>的当前值（和可选目标值），自动算出百分比。
    </p>

    <h3 class="section-title">数据集要求</h3>
    <ul class="req-list">
      <li>返回<b>一行</b>即可（多行只取第一行）</li>
      <li>包含<b>当前值列</b>（数字）</li>
      <li>可选<b>目标值列</b>；未提供时用配置中的「目标值(默认)」</li>
    </ul>

    <h3 class="section-title">字段映射</h3>
    <div class="field-map">
      <div class="fm-row"><span class="fm-key">当前值列</span><span class="fm-val">已完成量，如 <code>done</code></span></div>
      <div class="fm-row"><span class="fm-key">目标值列</span><span class="fm-val">目标量，如 <code>total</code>（可留空用默认）</span></div>
    </div>

    <h3 class="section-title">SQL 示例</h3>
    <pre class="code-block">select count(*) filter (where status = 'done') as done,
       count(*) as total
from task_view
where tenant_id = :currentTenantId</pre>
    <p class="section-text">映射：当前值列=<code>done</code>，目标值列=<code>total</code> → 百分比 = done / total。</p>

    <h3 class="section-title">结果如何变成图形</h3>
    <div class="schematic">
      <div class="mock-table">
        <div class="mt-row mt-head"><div class="mt-cell">done</div><div class="mt-cell">total</div></div>
        <div class="mt-row"><div class="mt-cell mt-y">75</div><div class="mt-cell">100</div></div>
      </div>
      <div class="sc-arrow">→</div>
      <svg width="72" height="72" viewBox="0 0 100 100">
        <circle cx="50" cy="50" r="42" fill="none" stroke="#f0f0f0" stroke-width="10" />
        <circle cx="50" cy="50" r="42" fill="none" stroke="#1677ff" stroke-width="10" stroke-linecap="round" stroke-dasharray="263.9" stroke-dashoffset="66" transform="rotate(-90 50 50)" />
      </svg>
      <div class="sc-caption">当前值/目标值 = 完成百分比（环形或条形展示）</div>
    </div>

    <div class="tip-box">
      <span class="tip-icon">!</span>
      <span>
        <b>只查询：</b>仅支持 SELECT。若数据库不支持 <code>filter</code>，可用
        <code>sum(case when ... then 1 else 0 end)</code> 替代。
      </span>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/dashboard/guide' as *;
</style>
