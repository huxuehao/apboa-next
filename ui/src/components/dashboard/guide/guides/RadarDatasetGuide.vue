<script setup lang="ts">
/**
 * 雷达图 数据集使用说明
 *
 * @author huxuehao
 */
</script>

<template>
  <div class="guide-content">
    <h2 class="guide-title">雷达图</h2>
    <p class="guide-intro">
      雷达图用于<b>多维度对比</b>：数据集返回<b>一列维度 + 一列或多列数值</b>，每行是一个维度轴，每个数值列是一条雷达系列。
    </p>

    <h3 class="section-title">数据集要求</h3>
    <ul class="req-list">
      <li>返回<b>一列维度字段</b>（各条轴的名称），如能力项、指标项</li>
      <li>返回<b>一列或多列数值字段</b>（雷达系列），多列 = 多个对象叠加对比</li>
      <li>各维度数值<b>量纲相近</b>更易读（雷达按统一最大值缩放）</li>
    </ul>

    <h3 class="section-title">字段映射</h3>
    <div class="field-map">
      <div class="fm-row">
        <span class="fm-key">维度列</span>
        <span class="fm-val">各轴名称，如 <code>ability</code></span>
      </div>
      <div class="fm-row">
        <span class="fm-key">数值列(系列)</span>
        <span class="fm-val">可多选，如 <code>张三, 李四</code>（每列一条雷达）</span>
      </div>
    </div>

    <h3 class="section-title">SQL 示例</h3>
    <pre class="code-block">select ability as name, zhangsan as 张三, lisi as 李四
from radar_view
where tenant_id = :currentTenantId</pre>

    <h3 class="section-title">结果如何变成图形</h3>
    <div class="schematic">
      <div class="mock-table">
        <div class="mt-row mt-head">
          <div class="mt-cell">name</div>
          <div class="mt-cell">张三</div>
          <div class="mt-cell">李四</div>
        </div>
        <div class="mt-row"><div class="mt-cell mt-x">沟通</div><div class="mt-cell mt-y">90</div><div class="mt-cell">70</div></div>
        <div class="mt-row"><div class="mt-cell mt-x">技术</div><div class="mt-cell mt-y">60</div><div class="mt-cell">85</div></div>
        <div class="mt-row"><div class="mt-cell mt-x">执行</div><div class="mt-cell mt-y">80</div><div class="mt-cell">65</div></div>
      </div>
      <div class="sc-arrow">→</div>
      <svg width="80" height="80" viewBox="0 0 80 80">
        <polygon
          points="40,10 68.5,30.7 57.6,64.3 22.4,64.3 11.5,30.7"
          fill="none"
          stroke="#e8e8e8"
        />
        <polygon
          points="40,13 57.1,34.4 54.1,59.4 31.2,52.1 20,33.5"
          fill="#1677ff"
          fill-opacity="0.18"
          stroke="#1677ff"
          stroke-width="1.5"
        />
      </svg>
      <div class="sc-caption">维度列作雷达各轴，每个数值列画成一条雷达</div>
    </div>

    <div class="tip-box">
      <span class="tip-icon">!</span>
      <span>
        <b>只查询：</b>仅支持 SELECT。维度数量建议 3~8 个；多系列（多个数值列）用于对比不同对象在同一组维度上的差异。
      </span>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/dashboard/guide' as *;
</style>
