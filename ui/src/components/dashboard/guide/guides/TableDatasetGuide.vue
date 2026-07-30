<script setup lang="ts">
/**
 * 数据表格 数据集使用说明
 *
 * @author huxuehao
 */
</script>

<template>
  <div class="guide-content">
    <h2 class="guide-title">数据表格</h2>
    <p class="guide-intro">
      数据表格<b>原样展示查询结果</b>：数据集返回的<b>每一列即一个表头</b>，每一行即一条记录，无需字段映射。
    </p>

    <h3 class="section-title">数据集要求</h3>
    <ul class="req-list">
      <li>返回<b>你想展示的所有列</b>，列名（或别名）即表头</li>
      <li>建议用 <code>ORDER BY</code> 指定排序</li>
      <li>数据量较大时用 <code>LIMIT</code> 控制，避免一次拉取过多</li>
    </ul>

    <h3 class="section-title">配置项</h3>
    <div class="field-map">
      <div class="fm-row">
        <span class="fm-key">每页行数</span>
        <span class="fm-val">超过该行数时分页显示</span>
      </div>
    </div>

    <h3 class="section-title">SQL 示例</h3>
    <pre class="code-block">select name as 名称, status as 状态, created_at as 创建时间
from order_view
where tenant_id = :currentTenantId
order by created_at desc
limit 200</pre>

    <h3 class="section-title">结果如何变成表格</h3>
    <div class="schematic">
      <div class="mock-table">
        <div class="mt-row mt-head">
          <div class="mt-cell">名称</div>
          <div class="mt-cell">状态</div>
          <div class="mt-cell">创建时间</div>
        </div>
        <div class="mt-row">
          <div class="mt-cell">订单 A</div>
          <div class="mt-cell">已完成</div>
          <div class="mt-cell">08-01</div>
        </div>
        <div class="mt-row">
          <div class="mt-cell">订单 B</div>
          <div class="mt-cell">进行中</div>
          <div class="mt-cell">08-02</div>
        </div>
      </div>
      <div class="sc-arrow">→</div>
      <div class="sc-caption">列 = 表头，行 = 记录，所见即所得</div>
    </div>

    <div class="tip-box">
      <span class="tip-icon">!</span>
      <span>
        <b>只查询：</b>仅支持 SELECT。中文别名会直接作为表头显示；建议查带租户列的视图并用
        <code>:currentTenantId</code> 过滤。
      </span>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/dashboard/guide' as *;
</style>
