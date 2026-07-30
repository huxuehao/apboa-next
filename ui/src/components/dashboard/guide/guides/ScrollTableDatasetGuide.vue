<script setup lang="ts">
/**
 * 滚动轮播表 数据集使用说明
 *
 * @author huxuehao
 */
</script>

<template>
  <div class="guide-content">
    <h2 class="guide-title">滚动轮播表</h2>
    <p class="guide-intro">
      滚动轮播表<b>原样展示查询结果</b>并在行数超过可视行数时<b>自动纵向轮播</b>：每列即表头，每行即一条记录。
    </p>

    <h3 class="section-title">数据集要求</h3>
    <ul class="req-list">
      <li>返回<b>你想展示的所有列</b>，列名（或别名）即表头</li>
      <li>行数<b>多于「可视行数」</b>时才会自动滚动，通常用于榜单/动态列表</li>
      <li>建议 <code>ORDER BY</code> 指定顺序，<code>LIMIT</code> 控制总量</li>
    </ul>

    <h3 class="section-title">配置项</h3>
    <div class="field-map">
      <div class="fm-row"><span class="fm-key">显示表头</span><span class="fm-val">是否显示表头行</span></div>
      <div class="fm-row"><span class="fm-key">可视行数</span><span class="fm-val">同时可见的行数</span></div>
      <div class="fm-row"><span class="fm-key">滚动间隔</span><span class="fm-val">每次滚动一行的毫秒数</span></div>
    </div>

    <h3 class="section-title">SQL 示例</h3>
    <pre class="code-block">select rank as 排名, name as 名称, score as 分数
from leaderboard_view
where tenant_id = :currentTenantId
order by score desc
limit 50</pre>

    <h3 class="section-title">结果如何变成图形</h3>
    <div class="schematic">
      <div class="mock-table">
        <div class="mt-row mt-head">
          <div class="mt-cell">排名</div>
          <div class="mt-cell">名称</div>
          <div class="mt-cell">分数</div>
        </div>
        <div class="mt-row"><div class="mt-cell">1</div><div class="mt-cell">张三</div><div class="mt-cell mt-y">98</div></div>
        <div class="mt-row"><div class="mt-cell">2</div><div class="mt-cell">李四</div><div class="mt-cell mt-y">95</div></div>
        <div class="mt-row"><div class="mt-cell">3</div><div class="mt-cell">王五</div><div class="mt-cell mt-y">90</div></div>
      </div>
      <div class="sc-arrow">→</div>
      <div class="sc-caption">列 = 表头，行超出可视区域即自动向上轮播、无缝循环</div>
    </div>

    <div class="tip-box">
      <span class="tip-icon">!</span>
      <span>
        <b>只查询：</b>仅支持 SELECT。中文别名直接作为表头；建议查带租户列的视图并用 <code>:currentTenantId</code> 过滤。
      </span>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/dashboard/guide' as *;
</style>
