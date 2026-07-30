<script setup lang="ts">
/**
 * 柱状图/折线图/面积图/散点图 数据集使用说明
 *
 * @author huxuehao
 */
</script>

<template>
  <div class="guide-content">
    <h2 class="guide-title">柱状图 / 折线图 / 面积图 / 散点图</h2>
    <p class="guide-intro">
      这几类图共用一套<b>直角坐标系</b>映射：数据集返回<b>一列分类 + 一列或多列数值</b>即可。
    </p>

    <h3 class="section-title">数据集要求</h3>
    <ul class="req-list">
      <li>返回<b>一列分类字段</b>（做横轴 X），如日期、部门、状态</li>
      <li>返回<b>一列或多列数值字段</b>（做纵轴 Y），需为数字；多列 = 多组柱子 / 多条线</li>
      <li>建议用 <code>ORDER BY</code> 控制横轴顺序（后端不会自动排序）</li>
    </ul>

    <h3 class="section-title">字段映射</h3>
    <div class="field-map">
      <div class="fm-row">
        <span class="fm-key">分类轴(X)</span>
        <span class="fm-val">选择分类列，如 <code>dept</code></span>
      </div>
      <div class="fm-row">
        <span class="fm-key">数值列(Y)</span>
        <span class="fm-val">可多选，如 <code>cnt</code>（多选即多组）</span>
      </div>
    </div>

    <h3 class="section-title">SQL 示例</h3>
    <pre class="code-block">select dept_name as dept, count(*) as cnt
from user_view
group by dept_name
order by cnt desc</pre>

    <h3 class="section-title">结果如何变成图形</h3>
    <div class="schematic">
      <div class="mock-table">
        <div class="mt-row mt-head">
          <div class="mt-cell">dept</div>
          <div class="mt-cell">cnt</div>
        </div>
        <div class="mt-row">
          <div class="mt-cell mt-x">研发</div>
          <div class="mt-cell mt-y">12</div>
        </div>
        <div class="mt-row">
          <div class="mt-cell mt-x">销售</div>
          <div class="mt-cell mt-y">8</div>
        </div>
        <div class="mt-row">
          <div class="mt-cell mt-x">运营</div>
          <div class="mt-cell mt-y">5</div>
        </div>
      </div>
      <div class="sc-arrow">→</div>
      <div class="chart-gallery">
        <div class="cg-item">
          <div class="mock-bars cg-bars">
            <div class="mc-bar" style="height: 90%" />
            <div class="mc-bar" style="height: 60%" />
            <div class="mc-bar" style="height: 38%" />
          </div>
          <div class="cg-label">柱状图</div>
        </div>
        <div class="cg-item">
          <svg width="120" height="72" viewBox="0 0 120 72">
            <line x1="12" y1="8" x2="12" y2="62" stroke="#e8e8e8" stroke-width="1" />
            <line x1="12" y1="62" x2="112" y2="62" stroke="#e8e8e8" stroke-width="1" />
            <polyline points="22,44 48,28 74,36 100,16" fill="none" stroke="#1677ff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
          <div class="cg-label">折线图</div>
        </div>
        <div class="cg-item">
          <svg width="120" height="72" viewBox="0 0 120 72">
            <line x1="12" y1="8" x2="12" y2="62" stroke="#e8e8e8" stroke-width="1" />
            <line x1="12" y1="62" x2="112" y2="62" stroke="#e8e8e8" stroke-width="1" />
            <polygon points="22,44 48,28 74,36 100,16 100,62 22,62" fill="#1677ff" fill-opacity="0.15" stroke="none" />
            <polyline points="22,44 48,28 74,36 100,16" fill="none" stroke="#1677ff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
          <div class="cg-label">面积图</div>
        </div>
        <div class="cg-item">
          <svg width="120" height="72" viewBox="0 0 120 72">
            <line x1="12" y1="8" x2="12" y2="62" stroke="#e8e8e8" stroke-width="1" />
            <line x1="12" y1="62" x2="112" y2="62" stroke="#e8e8e8" stroke-width="1" />
            <circle cx="26" cy="46" r="3" fill="#1677ff" fill-opacity="0.85" />
            <circle cx="44" cy="30" r="3" fill="#1677ff" fill-opacity="0.85" />
            <circle cx="60" cy="50" r="3" fill="#1677ff" fill-opacity="0.85" />
            <circle cx="78" cy="22" r="3" fill="#1677ff" fill-opacity="0.85" />
            <circle cx="96" cy="38" r="3" fill="#1677ff" fill-opacity="0.85" />
          </svg>
          <div class="cg-label">散点图</div>
        </div>
      </div>
      <div class="sc-caption">同一份“分类 + 数值”结果，切换图表类型即渲染为不同形态（分类列作横轴/维度，数值列决定高度或位置）</div>
    </div>

    <h3 class="section-title">多组对比（多 series）</h3>
    <p class="section-text">数值列(Y)选多列即可并列对比多组：</p>
    <pre class="code-block">select month, sum(income) as income, sum(cost) as cost
from finance_view
group by month
order by month</pre>
    <p class="section-text">
      映射：分类轴(X)=<code>month</code>，数值列(Y)=<code>income, cost</code> → 每月并列两根柱子，series 名即列名。
    </p>

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

.chart-gallery {
  display: grid;
  grid-template-columns: repeat(2, auto);
  gap: 12px;
}

.cg-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fff;
}

.cg-bars {
  height: 60px;
}

.cg-label {
  font-size: 12px;
  color: #595959;
}
</style>
