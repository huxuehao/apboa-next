<script setup lang="ts">
/**
 * 饼图 数据集使用说明
 *
 * @author huxuehao
 */
</script>

<template>
  <div class="guide-content">
    <h2 class="guide-title">饼图</h2>
    <p class="guide-intro">
      饼图用于展示<b>占比构成</b>：数据集返回<b>一列名称 + 一列数值</b>，每行是一个扇区。
    </p>

    <h3 class="section-title">数据集要求</h3>
    <ul class="req-list">
      <li>返回<b>一列名称字段</b>（扇区名称），如状态、类别</li>
      <li>返回<b>一列数值字段</b>（扇区占比大小），需为数字</li>
      <li>行数不宜过多，通常聚合到 <code>group by</code> 的少数几类</li>
    </ul>

    <h3 class="section-title">字段映射</h3>
    <div class="field-map">
      <div class="fm-row">
        <span class="fm-key">名称列</span>
        <span class="fm-val">扇区名称，如 <code>name</code></span>
      </div>
      <div class="fm-row">
        <span class="fm-key">数值列</span>
        <span class="fm-val">扇区数值，如 <code>value</code></span>
      </div>
    </div>

    <h3 class="section-title">SQL 示例</h3>
    <pre class="code-block">select status as name, count(*) as value
from order_view
group by status</pre>

    <h3 class="section-title">结果如何变成图形</h3>
    <div class="schematic">
      <div class="mock-table">
        <div class="mt-row mt-head">
          <div class="mt-cell">name</div>
          <div class="mt-cell">value</div>
        </div>
        <div class="mt-row">
          <div class="mt-cell mt-x">已完成</div>
          <div class="mt-cell mt-y">50</div>
        </div>
        <div class="mt-row">
          <div class="mt-cell mt-x">进行中</div>
          <div class="mt-cell mt-y">30</div>
        </div>
        <div class="mt-row">
          <div class="mt-cell mt-x">待处理</div>
          <div class="mt-cell mt-y">20</div>
        </div>
      </div>
      <div class="sc-arrow">→</div>
      <svg width="72" height="72" viewBox="0 0 72 72">
        <g transform="rotate(-90 36 36)">
          <circle cx="36" cy="36" r="18" fill="none" stroke="#1677ff" stroke-width="36" stroke-dasharray="56.5 113" stroke-dashoffset="0" />
          <circle cx="36" cy="36" r="18" fill="none" stroke="#69b1ff" stroke-width="36" stroke-dasharray="33.9 113" stroke-dashoffset="-56.5" />
          <circle cx="36" cy="36" r="18" fill="none" stroke="#b7d3ff" stroke-width="36" stroke-dasharray="22.6 113" stroke-dashoffset="-90.4" />
        </g>
      </svg>
      <div class="sc-caption">名称列决定扇区，数值列决定扇区占比</div>
    </div>

    <div class="tip-box">
      <span class="tip-icon">!</span>
      <span>
        <b>只查询：</b>仅支持 SELECT。建议查带租户列的视图，并用自动注入的
        <code>:currentTenantId</code> 过滤，如 <code>where tenant_id = :currentTenantId</code>，防止跨租户越权。
      </span>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/dashboard/guide' as *;
</style>
