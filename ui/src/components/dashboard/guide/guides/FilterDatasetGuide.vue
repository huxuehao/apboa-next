<script setup lang="ts">
/**
 * 全局筛选器 数据集使用说明
 *
 * @author huxuehao
 */
</script>

<template>
  <div class="guide-content">
    <h2 class="guide-title">全局筛选器（联动）</h2>
    <p class="guide-intro">
      全局筛选器改变时会<b>联动刷新所有面板</b>：筛选值以<b>命名参数</b>注入各数据集的 SQL，配合
      <code>where</code> 条件实现按需过滤。
    </p>

    <h3 class="section-title">如何配置</h3>
    <ol class="req-list">
      <li>在设计器顶部点击<b>「筛选器」</b>，添加一个筛选器</li>
      <li>选择类型：<b>日期范围 / 下拉选择 / 文本</b></li>
      <li>填写<b>显示名称</b>与<b>参数名</b>（英文，SQL 中引用）</li>
      <li>下拉类型再补充候选项（每行 <code>显示名=值</code>）</li>
    </ol>

    <h3 class="section-title">参数注入规则</h3>
    <div class="field-map">
      <div class="fm-row">
        <span class="fm-key">文本 / 下拉</span>
        <span class="fm-val">注入 <code>:参数名</code></span>
      </div>
      <div class="fm-row">
        <span class="fm-key">日期范围</span>
        <span class="fm-val">注入 <code>:参数名Start</code> 与 <code>:参数名End</code>（YYYY-MM-DD）</span>
      </div>
      <div class="fm-row">
        <span class="fm-key">内置</span>
        <span class="fm-val">始终可用 <code>:currentTenantId</code>、<code>:currentUserId</code></span>
      </div>
    </div>

    <h3 class="section-title">SQL 示例</h3>
    <p class="section-text">假设配置了日期范围（参数名 <code>dt</code>）与下拉状态（参数名 <code>status</code>）：</p>
    <pre class="code-block">select dept as name, count(*) as value
from order_view
where tenant_id = :currentTenantId
  and created_at between :dtStart and :dtEnd
  and status = :status
group by dept</pre>

    <h3 class="section-title">筛选值如何进入 SQL</h3>
    <div class="schematic">
      <div class="mock-filter">
        <div class="mf-item">日期范围：01-01 ~ 01-31</div>
        <div class="mf-item">状态：已完成</div>
      </div>
      <div class="sc-arrow">→</div>
      <div class="mock-params">
        <div class="mp-line"><code>:dtStart</code> = 2026-01-01</div>
        <div class="mp-line"><code>:dtEnd</code> = 2026-01-31</div>
        <div class="mp-line"><code>:status</code> = done</div>
      </div>
      <div class="sc-caption">筛选栏的值按参数名注入每个数据集，改变即联动刷新</div>
    </div>

    <div class="tip-box">
      <span class="tip-icon">!</span>
      <span>
        参数名建议用英文；<b>未选择</b>的筛选项不会注入对应参数，SQL 里可用
        <code>(:status is null or status = :status)</code> 做可选过滤。
      </span>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/dashboard/guide' as *;

.mock-filter {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.mf-item {
  padding: 6px 12px;
  border: 1px solid #e8e8e8;
  border-radius: 6px;
  background: #fff;
  font-size: 12px;
  color: #434343;
  white-space: nowrap;
}

.mock-params {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.mp-line {
  font-size: 12px;
  color: #595959;

  code {
    padding: 1px 5px;
    border-radius: 4px;
    background: #f2f3f5;
    font-family: 'JetBrains Mono', Menlo, Consolas, monospace;
    font-size: 12px;
    color: #c41d7f;
  }
}
</style>
