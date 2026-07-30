<script setup lang="ts">
/**
 * 数字翻牌 数据集使用说明
 *
 * @author huxuehao
 */
</script>

<template>
  <div class="guide-content">
    <h2 class="guide-title">数字翻牌</h2>
    <p class="guide-intro">
      数字翻牌展示<b>单个数值</b>并带 count-up 动画：取数据集<b>第一行</b>的取值列作为数字，可加前后缀、千分位。
    </p>

    <h3 class="section-title">数据集要求</h3>
    <ul class="req-list">
      <li>返回<b>一行</b>即可（多行只取第一行）</li>
      <li>包含<b>一个数值列</b>（要翻牌展示的数字）</li>
      <li>不绑定数据集时可在配置里直接填「静态值」</li>
    </ul>

    <h3 class="section-title">字段映射与格式</h3>
    <div class="field-map">
      <div class="fm-row"><span class="fm-key">取值列</span><span class="fm-val">数字来源列，如 <code>val</code></span></div>
      <div class="fm-row"><span class="fm-key">前缀/后缀</span><span class="fm-val">如 <code>¥</code>、<code>元</code></span></div>
      <div class="fm-row"><span class="fm-key">小数位/千分位</span><span class="fm-val">控制展示格式</span></div>
    </div>

    <h3 class="section-title">SQL 示例</h3>
    <pre class="code-block">select sum(amount) as val
from sales_view
where tenant_id = :currentTenantId</pre>

    <h3 class="section-title">结果如何变成图形</h3>
    <div class="schematic">
      <div class="mock-table">
        <div class="mt-row mt-head"><div class="mt-cell">val</div></div>
        <div class="mt-row"><div class="mt-cell mt-y">128360</div></div>
      </div>
      <div class="sc-arrow">→</div>
      <div class="mock-flip">
        <span class="mf-digit">1</span>
        <span class="mf-digit">2</span>
        <span class="mf-digit">8</span>
        <span class="mf-sep">,</span>
        <span class="mf-digit">3</span>
        <span class="mf-digit">6</span>
        <span class="mf-digit">0</span>
      </div>
      <div class="sc-caption">取值列作数字，count-up 动画翻牌展示（可千分位）</div>
    </div>

    <div class="tip-box">
      <span class="tip-icon">!</span>
      <span><b>只查询：</b>仅支持 SELECT。取值列需为数字；建议查带租户列的视图并用 <code>:currentTenantId</code> 过滤。</span>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/dashboard/guide' as *;

.mock-flip {
  display: flex;
  align-items: center;
  gap: 3px;
}

.mf-digit {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  padding: 4px 3px;
  border-radius: 3px;
  background: #262626;
  color: #fff;
  font-family: 'JetBrains Mono', Menlo, Consolas, monospace;
  font-size: 16px;
  font-weight: 700;
}

.mf-sep {
  color: #262626;
  font-weight: 700;
}
</style>
