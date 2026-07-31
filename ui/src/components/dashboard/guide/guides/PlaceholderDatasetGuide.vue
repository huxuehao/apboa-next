<script setup lang="ts">
/**
 * 文本 / Markdown 动态占位 使用说明
 *
 * @author huxuehao
 */
</script>

<template>
  <!-- 纯静态说明，含字面量 {{ }} 占位示例，用 v-pre 跳过模板编译 -->
  <div v-pre class="guide-content">
    <h2 class="guide-title">文本 / Markdown 动态占位</h2>
    <p class="guide-intro">
      文本与 Markdown 面板可<b>绑定数据集</b>，在内容里用占位符引用列的值，实现动态文案。
      不绑定数据集时按普通静态内容展示。
    </p>

    <h3 class="section-title">两种占位语法</h3>
    <div class="field-map">
      <div class="fm-row">
        <span class="fm-key">标量</span>
        <span class="fm-val"><code>{{ 列名 }}</code>，取数据集<b>首行</b>该列的值</span>
      </div>
      <div class="fm-row">
        <span class="fm-key">行循环</span>
        <span class="fm-val">
          <code>{{#each}}</code> ... <code>{{ 列名 }}</code> ... <code>{{/each}}</code>，对<b>每一行</b>重复内部模板，块内 <code>{{ 列名 }}</code> 指向当前行
        </span>
      </div>
    </div>

    <h3 class="section-title">如何使用</h3>
    <ol class="req-list">
      <li>拖入<b>文本</b>或 <b>Markdown</b> 面板，在配置面板<b>绑定数据集</b></li>
      <li>在内容中用 <code>{{ 列名 }}</code> 引用列（列名即数据集返回的字段名）</li>
      <li>需要逐行展开时，用 <code>{{#each}}...{{/each}}</code> 包裹一段模板</li>
    </ol>

    <h3 class="section-title">标量示例</h3>
    <p class="section-text">数据集返回首行 <code>total = 1280</code>、<code>growth = 12.5</code>：</p>
    <pre class="code-block">本月订单 {{ total }} 单，环比增长 {{ growth }}%</pre>
    <p class="section-text">渲染为：本月订单 1280 单，环比增长 12.5%</p>

    <h3 class="section-title">行循环示例（Markdown）</h3>
    <p class="section-text">数据集返回多行 <code>name</code>、<code>value</code>：</p>
    <pre class="code-block">### 部门排行
{{#each}}
- {{ name }}：{{ value }}
{{/each}}</pre>
    <p class="section-text">每一行生成一个列表项。</p>

    <div class="tip-box">
      <span class="tip-icon">!</span>
      <span>
        字段缺失或未取到数据时，占位符替换为<b>空串</b>；未绑定数据集则按静态内容展示。
        列名区分大小写，需与数据集返回字段一致（建议在 SQL 中用别名规范列名）。
      </span>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/dashboard/guide' as *;
</style>
