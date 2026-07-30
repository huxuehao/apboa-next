<script setup lang="ts">
/**
 * HTTP 数据集 使用说明
 *
 * @author huxuehao
 */
</script>

<template>
  <div class="guide-content">
    <h2 class="guide-title">HTTP 数据集</h2>
    <p class="guide-intro">
      除 SQL 外，数据集也可以调用<b>外部 HTTP 接口</b>取数。仅支持 <b>GET</b> 请求，由<b>后端代理执行</b>（无跨域问题），
      响应 JSON 经映射后与 SQL 数据集一样被表格、图表、卡片等面板消费。
    </p>

    <h3 class="section-title">适用与限制</h3>
    <ul class="req-list">
      <li>仅 <b>GET</b>；不支持请求体、动态请求头</li>
      <li>请求头<b>只支持固定值</b></li>
      <li>请求参数 query 支持<b>默认值</b>、<b>绑定私有筛选</b>、<b>系统参数</b></li>
      <li>目标 <code>IP:端口</code> 与当前浏览器一致时，<b>自动携带平台登录 token</b>（跨域绝不携带）</li>
    </ul>

    <h3 class="section-title">如何配置</h3>
    <ol class="req-list">
      <li>在数据集<b>新建/编辑</b>弹窗，类型选择 <b>HTTP 接口</b></li>
      <li>填写<b>请求地址</b>（http/https，仅 GET）</li>
      <li>按需添加<b>请求参数 query</b>：参数名 + 值 + 默认值</li>
      <li>按需添加<b>请求头</b>（固定值）</li>
      <li>填写<b>数据路径 dataPath</b>：定位响应中的数组，如 <code>data.list</code>；为空则取整个响应体</li>
      <li>点击<b>运行预览</b>校验，保存后即可被面板绑定</li>
    </ol>

    <h3 class="section-title">query 值的三种来源</h3>
    <div class="field-map">
      <div class="fm-row">
        <span class="fm-key">固定值</span>
        <span class="fm-val">直接填写，如 <code>type</code> = <code>order</code></span>
      </div>
      <div class="fm-row">
        <span class="fm-key">绑定筛选</span>
        <span class="fm-val">填 <code>:参数名</code>，取本面板私有筛选值，如 <code>status</code> = <code>:status</code></span>
      </div>
      <div class="fm-row">
        <span class="fm-key">系统参数</span>
        <span class="fm-val">填 <code>:currentTenantId</code> 或 <code>:currentUserId</code>，由后端注入、不可伪造</span>
      </div>
    </div>
    <p class="section-text">
      取值规则：值为 <code>:名称</code> 时取对应参数；<b>未命中或筛选未选</b>则回退<b>默认值</b>；仍为空则<b>省略该参数</b>（不拼接到 URL）。
    </p>

    <h3 class="section-title">同源自动带 token</h3>
    <p class="section-text">
      当请求地址的 <code>scheme://host:port</code> 与当前浏览器一致（即调用平台自身接口）时，后端会把你当前登录的
      <code>Authorization</code> 自动转发给该接口；<b>跨域目标绝不携带</b>，避免 token 外泄。
    </p>

    <h3 class="section-title">响应如何变成表格</h3>
    <div class="schematic">
      <div class="mock-json">
        <pre class="code-block mini">{
  "code": 0,
  "data": {
    "list": [
      { "dept": "销售", "cnt": 12 },
      { "dept": "研发", "cnt": 8 }
    ]
  }
}</pre>
      </div>
      <div class="sc-arrow">→</div>
      <div class="mock-table">
        <div class="mt-head"><span>dept</span><span>cnt</span></div>
        <div class="mt-row"><span>销售</span><span>12</span></div>
        <div class="mt-row"><span>研发</span><span>8</span></div>
      </div>
      <div class="sc-caption">dataPath = <code>data.list</code> 定位数组，按<b>首行的键</b>推断列；若定位到对象则转为单行</div>
    </div>

    <h3 class="section-title">完整示例</h3>
    <p class="section-text">
      地址 <code>https://内网平台/api/report/dept</code>，query 配置：
      <code>tenantId</code> = <code>:currentTenantId</code>、
      <code>status</code> = <code>:status</code>（默认 <code>done</code>）、
      <code>kw</code> = <code>:keyword</code>。dataPath = <code>data.list</code>。
    </p>
    <p class="section-text">
      当私有筛选选择状态“进行中”、关键字为空时，实际请求为
      <code>?tenantId=1001&amp;status=doing</code>（keyword 未选、无默认值，故省略），并自动带上平台 token。
    </p>

    <div class="tip-box">
      <span class="tip-icon">!</span>
      <span>
        安全与限制：仅 http/https；跨域目标禁止访问内网/环回/云元数据地址（可配主机白名单）；
        有<b>连接/读取超时</b>与<b>响应体大小上限</b>；请求头为<b>固定值明文</b>存储，请勿在此填写敏感密钥。
      </span>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/dashboard/guide' as *;

.code-block.mini {
  margin: 0;
  font-size: 12px;
}

.mock-table {
  display: flex;
  flex-direction: column;
  border: 1px solid #e8e8e8;
  border-radius: 6px;
  overflow: hidden;
  background: #fff;
}

.mt-head,
.mt-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
}

.mt-head span,
.mt-row span {
  padding: 5px 12px;
  font-size: 12px;
  color: #434343;
}

.mt-head {
  background: #fafafa;
  font-weight: 600;
}

.mt-row {
  border-top: 1px solid #f0f0f0;
}
</style>
