<script setup lang="ts">
/**
 * 数据卡片 数据集使用说明
 *
 * @author huxuehao
 */
</script>

<template>
  <div class="guide-content">
    <h2 class="guide-title">数据卡片</h2>
    <p class="guide-intro">
      数据卡片展示<b>单个关键指标</b>（一个大数字 + 描述）。可绑定数据集取<b>第一行</b>的值，也可只填静态值。
    </p>

    <h3 class="section-title">数据集要求</h3>
    <ul class="req-list">
      <li>返回<b>一行</b>即可（多行只取第一行）</li>
      <li>包含<b>取值列</b>（要展示的数字/文本）</li>
      <li>可选<b>描述列</b>（卡片下方说明文字）</li>
    </ul>

    <h3 class="section-title">字段映射</h3>
    <div class="field-map">
      <div class="fm-row">
        <span class="fm-key">取值列</span>
        <span class="fm-val">大数字取值，如 <code>num</code></span>
      </div>
      <div class="fm-row">
        <span class="fm-key">描述列</span>
        <span class="fm-val">说明文字，如 <code>name</code>（可留空，用静态描述）</span>
      </div>
    </div>

    <h3 class="section-title">SQL 示例</h3>
    <pre class="code-block">select count(*) as num, '用户总数' as name
from user_view
where tenant_id = :currentTenantId</pre>

    <h3 class="section-title">结果如何变成图形</h3>
    <div class="schematic">
      <div class="mock-table">
        <div class="mt-row mt-head">
          <div class="mt-cell">num</div>
          <div class="mt-cell">name</div>
        </div>
        <div class="mt-row">
          <div class="mt-cell mt-y">1,286</div>
          <div class="mt-cell mt-x">用户总数</div>
        </div>
      </div>
      <div class="sc-arrow">→</div>
      <div class="mock-metric">
        <div class="mm-value">1,286</div>
        <div class="mm-label">用户总数</div>
      </div>
      <div class="sc-caption">取值列作大数字，描述列作下方说明</div>
    </div>

    <div class="tip-box">
      <span class="tip-icon">!</span>
      <span>
        <b>不绑定数据集</b>时可在配置里直接填"静态值"与"描述"；<b>只查询：</b>仅支持 SELECT，建议查带租户列的视图。
      </span>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/dashboard/guide' as *;

.mock-metric {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 120px;
  padding: 14px 18px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fff;
}

.mm-value {
  font-size: 26px;
  font-weight: 700;
  color: #1a1a1a;
  line-height: 1.1;
}

.mm-label {
  font-size: 12px;
  color: #999;
}
</style>
