<script setup lang="ts">
/**
 * 面板私有筛选器 数据集使用说明
 *
 * @author huxuehao
 */
</script>

<template>
  <div class="guide-content">
    <h2 class="guide-title">面板私有筛选器</h2>
    <p class="guide-intro">
      私有筛选器渲染在面板卡片上，只影响<b>本面板</b>的取数：筛选值以<b>命名参数</b>注入该面板绑定的数据集
      SQL，配合 <code>where</code> 条件实现按需过滤。
    </p>

    <h3 class="section-title">参数作用域</h3>
    <p class="section-text">
      参数分两层：<b>系统</b>参数由后端注入、不可被前端覆盖；<b>面板私有</b>参数按参数名直接注入。
      私有筛选值只随本面板的取数请求发送，不同面板即使参数名相同也互不干扰。
    </p>
    <div class="field-map">
      <div class="fm-row">
        <span class="fm-key">系统</span>
        <span class="fm-val">后端注入、不可覆盖：<code>:currentTenantId</code>、<code>:currentUserId</code></span>
      </div>
      <div class="fm-row">
        <span class="fm-key">面板私有</span>
        <span class="fm-val">按参数名注入：status → <code>:status</code>；日期范围 dt → <code>:dtStart</code>/<code>:dtEnd</code>；日期/月份/年份 → <code>:参数名</code>（YYYY-MM-DD / YYYY-MM / YYYY）</span>
      </div>
    </div>

    <h3 class="section-title">如何配置</h3>
    <ol class="req-list">
      <li>选中支持的面板（表格/折线/柱状/面积/散点/KPI），在配置面板<b>「私有筛选」</b>中启用</li>
      <li>点击<b>「编辑筛选项」</b>添加筛选器，类型：<b>日期范围 / 日期 / 月份 / 年份 / 下拉选择 / 文本</b></li>
      <li>填写<b>显示名称</b>与<b>参数名</b>（英文标识符，SQL 中引用；不得占用系统保留名）</li>
      <li>下拉类型在选项列表中逐条添加（显示名 + 值，可删除）</li>
      <li>还可配置：<b>位置</b>（标题栏右侧/内容区左上、右上、左下、右下）、<b>尺寸</b>（大/中/小）、<b>是否显示名称</b>，也可随时关闭</li>
    </ol>

    <h3 class="section-title">SQL 示例</h3>
    <p class="section-text">
      假设本面板私有配置了日期范围（参数名 <code>dt</code>）与状态下拉（参数名 <code>status</code>）：
    </p>
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
      <div class="sc-caption">筛选值按参数名注入本面板绑定的数据集，改变即刷新本面板</div>
    </div>

    <h3 class="section-title">可选过滤（未选择时不过滤）</h3>
    <p class="section-text">
      筛选器<b>未选择</b>时，参数会以 <code>NULL</code> 注入。若直接写
      <code>status = :status</code>，NULL 比较永远不成立，会查不到任何数据。
      正确写法是用 <code>(:status is null or status = :status)</code>：
    </p>
    <ul class="req-list">
      <li><b>未选择</b>状态：<code>:status</code> = NULL → <code>:status is null</code> 成立，整个括号恒为真，<b>等于不过滤</b>，统计全部数据</li>
      <li><b>选择了“已完成”</b>：<code>:status</code> = done → 左半边不成立，只能靠 <code>status = 'done'</code>，<b>只统计已完成</b></li>
    </ul>
    <p class="section-text">完整案例（日期范围与状态都是可选的）：</p>
    <pre class="code-block">select dept as name, count(*) as value
from order_view
where tenant_id = :currentTenantId
  and (:dtStart is null or created_at &gt;= :dtStart)
  and (:dtEnd is null or created_at &lt;= :dtEnd)
  and (:status is null or status = :status)
group by dept</pre>
    <p class="section-text">
      这样面板首次打开（什么都没选）就能展示全量数据；用户选了哪个筛选器，哪个条件才生效。
      反之，若某筛选器是<b>必选</b>语义（如必须选时间段），直接写 <code>created_at between :dtStart and :dtEnd</code> 即可——未选时查不出数据，反而能提醒用户去选。
    </p>

    <div class="tip-box">
      <span class="tip-icon">!</span>
      <span>
        参数名需为英文标识符（字母开头，仅字母/数字/下划线），不得占用系统保留名；
        <b>未选择</b>的筛选项会以 <code>NULL</code> 注入参数，需要“不选则不过滤”时请使用上方的
        <code>(:x is null or ...)</code> 写法。
      </span>
    </div>

    <div class="tip-box">
      <span class="tip-icon">!</span>
      <span>
        租户隔离已由平台<b>强制自动注入</b>：无论 SQL 是否手写
        <code>tenant_id = :currentTenantId</code>，执行时都会自动追加当前租户过滤，
        无法跨租户查询；示例中保留手写写法仅为清晰起见。
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
