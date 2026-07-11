<template>
  <div class="formatter-guide">
    <h2 class="guide-title">参数模板格式指南</h2>
    <p class="guide-intro">
      模板格式决定参数值会被如何被渲染。三种格式对比如下，请根据实际需求选择。
    </p>

    <!-- 1. 纯文本替换 -->
    <h3 class="section-heading">1. 纯文本替换（STRING）</h3>
    <p class="section-desc">
      通过 <code>${输入绑定名}</code> 占位符直接将变量替换为其 <code>toString()</code> 结果。
    </p>
    <div class="example-row">
      <div class="example-col">
        <div class="example-label">模板</div>
        <pre class="code-block">欢迎你，${userName}，今年${userAge}岁</pre>
      </div>
      <div class="example-col">
        <div class="example-label">结果</div>
        <pre class="code-block dim">欢迎你，张三，今年25岁</pre>
      </div>
    </div>
    <ul class="pros-cons">
      <li class="pro">语法极简，无学习成本</li>
      <li class="pro">支持多变量同时占位</li>
      <li class="con">所有值转为字符串，不保留数字/布尔等原始类型</li>
    </ul>

    <hr class="section-divider" />

    <!-- 2. JSON 保类型 -->
    <h3 class="section-heading">2. JSON 保类型（JACKSON）</h3>
    <p class="section-desc">
      模板<b>必须为合法 JSON</b>。<code>${输入绑定名}</code> 仅允许出现在字符串值中。
      替换后<b>保留变量的原始类型</b>——数字仍是数字，布尔仍是布尔。
    </p>
    <div class="example-row">
      <div class="example-col">
        <div class="example-label">模板</div>
        <pre class="code-block">{
  "name": "${userName}",
  "age": ${userAge},
  "active": ${isActive}
}</pre>
      </div>
      <div class="example-col">
        <div class="example-label">结果</div>
        <pre class="code-block dim">{
  "name": "张三",
  "age": 25,
  "active": true
}</pre>
      </div>
    </div>
    <ul class="pros-cons">
      <li class="pro">保留数字、布尔、对象等原始数据类型</li>
      <li class="pro">支持深层嵌套 JSON 和数组元素递归替换</li>
      <li class="con">不支持 key 名占位，如 <code>{"${key}": value}</code></li>
      <li class="con">不支持值内字符串拼接，如 <code>"前缀${var}后缀"</code></li>
    </ul>

    <hr class="section-divider" />

    <!-- 3. Velocity 模板 -->
    <h3 class="section-heading">3. Velocity 模板（VELOCITY）</h3>
    <p class="section-desc">
      基于 Apache Velocity 引擎，支持完整 VTL 语法，渲染结果自动反序列化为 Java 对象。
    </p>

    <h4 class="sub-heading">对象属性访问</h4>
    <p class="sub-desc">
      通过 <code>$变量.属性名</code> 直接访问对象深层字段。当上游节点输出复杂对象时，无需额外拆分即可取值。
    </p>
    <div class="example-label">假设上游变量 <code>order</code> 的结构：</div>
    <pre class="code-block">{
  "orderId": "ORD-20240001",
  "customer": { "name": "张三", "phone": "138xxxx" },
  "amount": 299.00,
  "items": [
    { "name": "商品A", "qty": 2 },
    { "name": "商品B", "qty": 1 }
  ]
}</pre>
    <div class="example-row" style="margin-top: 12px">
      <div class="example-col">
        <div class="example-label">模板</div>
        <pre class="code-block">{
  "id": "${order.orderId}",
  "buyer": "${order.customer.name}",
  "total": ${order.amount}
}</pre>
      </div>
      <div class="example-col">
        <div class="example-label">结果</div>
        <pre class="code-block dim">{
  "id": "ORD-20240001",
  "buyer": "张三",
  "total": 299.0
}</pre>
      </div>
    </div>

    <h4 class="sub-heading">循环遍历</h4>
    <p class="sub-desc">使用 <code>#foreach</code> 遍历集合，配合 <code>$foreach.hasNext</code> 控制分隔符。</p>
    <div class="example-row">
      <div class="example-col">
        <div class="example-label">模板</div>
        <pre class="code-block">{
  "names": [
    #foreach($item in $order.items)
      "${item.name}"#if($foreach.hasNext),#end
    #end
  ]
}</pre>
      </div>
      <div class="example-col">
        <div class="example-label">结果</div>
        <pre class="code-block dim">{
  "names": ["商品A", "商品B"]
}</pre>
      </div>
    </div>

    <h4 class="sub-heading">条件判断</h4>
    <p class="sub-desc">使用 <code>#if / #elseif / #else</code> 实现分支逻辑。</p>
    <pre class="code-block">#if($order.amount > 100)
  大额订单
#else
  普通订单
#end</pre>

    <ul class="pros-cons" style="margin-top: 14px">
      <li class="pro">点号路径访问深层属性：<code>$obj.field.nested</code></li>
      <li class="pro">循环遍历：<code>#foreach($item in $list) ... #end</code></li>
      <li class="pro">条件分支：<code>#if / #elseif / #else / #end</code></li>
      <li class="pro">变量赋值：<code>#set($var = value)</code></li>
      <li class="con">相比前两种语法更复杂，有一定学习曲线</li>
    </ul>

    <hr class="section-divider" />

    <!-- 选型速查 -->
    <h3 class="section-heading">选型速查</h3>
    <table class="quick-ref">
      <thead>
        <tr>
          <th>你的需求</th>
          <th>推荐</th>
        </tr>
      </thead>
      <tbody>
        <tr>
          <td>参数值是简单文本，不需要保留类型</td>
          <td class="rec">纯文本替换</td>
        </tr>
        <tr>
          <td>参数是 JSON，需要数字/布尔保持原类型</td>
          <td class="rec">JSON 保类型</td>
        </tr>
        <tr>
          <td>需要访问对象深层属性，如 <code>$user.profile.name</code></td>
          <td class="rec">Velocity 模板</td>
        </tr>
        <tr>
          <td>需要循环遍历列表生成批量数据</td>
          <td class="rec">Velocity 模板</td>
        </tr>
        <tr>
          <td>需要根据条件动态生成不同内容</td>
          <td class="rec">Velocity 模板</td>
        </tr>
      </tbody>
    </table>
    <p class="guide-footnote">
      不确定选哪个？从<b>纯文本替换</b>开始，它满足大多数场景。需要对象属性访问或循环时再切换到 Velocity。
    </p>
  </div>
</template>

<style scoped lang="scss">
.guide-title {
  margin: 0 0 8px;
  font-size: 18px;
  font-weight: 700;
  color: #262626;
}

.guide-intro {
  margin: 0 0 28px;
  font-size: 14px;
  color: #595959;
  line-height: 1.75;

  b { font-weight: 600; color: #262626; }
}

.section-heading {
  margin: 0 0 8px;
  font-size: 16px;
  font-weight: 700;
  color: #262626;
}

.section-divider {
  margin: 24px 0;
  border: none;
  border-top: 1px solid #f0f0f0;
}

.section-desc {
  margin: 0 0 14px;
  font-size: 14px;
  color: #434343;
  line-height: 1.75;

  b { font-weight: 600; }

  code {
    padding: 1px 5px;
    background: #f5f5f5;
    border-radius: 3px;
    font-size: 13px;
    font-family: 'JetBrains Mono', 'Consolas', monospace;
    color: #d4380d;
  }
}

.sub-heading {
  margin: 18px 0 4px;
  font-size: 14px;
  font-weight: 700;
  color: #262626;

  &:first-of-type { margin-top: 4px; }
}

.sub-desc {
  margin: 0 0 12px;
  font-size: 13px;
  color: #595959;
  line-height: 1.7;

  code {
    padding: 1px 5px;
    background: #f5f5f5;
    border-radius: 3px;
    font-size: 12px;
    font-family: 'JetBrains Mono', 'Consolas', monospace;
    color: #d4380d;
  }
}

.example-label {
  margin-bottom: 6px;
  font-size: 12px;
  color: #8c8c8c;
  font-weight: 500;

  code {
    padding: 1px 4px;
    background: #f5f5f5;
    border-radius: 3px;
    font-size: 12px;
    font-family: 'JetBrains Mono', 'Consolas', monospace;
    color: #d4380d;
  }
}

.example-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 12px;
}

.example-col { min-width: 0; }

.code-block {
  margin: 0;
  padding: 10px 14px;
  background: #f8f9fa;
  border: 1px solid #eee;
  border-radius: 6px;
  font-size: 13px;
  font-family: 'JetBrains Mono', 'Fira Code', 'Consolas', monospace;
  color: #262626;
  line-height: 1.7;
  white-space: pre;
  overflow-x: auto;

  &.dim { color: #595959; }
}

.pros-cons {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 4px;

  li {
    font-size: 13px;
    line-height: 1.65;
    padding-left: 16px;
    position: relative;

    &::before {
      position: absolute;
      left: 0;
      font-weight: 600;
    }

    code {
      padding: 1px 5px;
      background: #f5f5f5;
      border-radius: 3px;
      font-size: 12px;
      font-family: 'JetBrains Mono', 'Consolas', monospace;
    }
  }

  .pro {
    color: #434343;
    &::before { content: '+'; color: #8c8c8c; }
  }

  .con {
    color: #8c8c8c;
    &::before { content: '\2013'; color: #bfbfbf; }
  }
}

.quick-ref {
  width: 100%;
  border-collapse: collapse;
  margin-bottom: 14px;
  font-size: 13px;

  th, td {
    padding: 9px 14px;
    text-align: left;
    border-bottom: 1px solid #f0f0f0;
  }

  th {
    font-weight: 600;
    color: #8c8c8c;
    font-size: 12px;
    letter-spacing: 0.3px;
  }

  td {
    color: #434343;
    line-height: 1.6;

    code {
      padding: 1px 4px;
      background: #f5f5f5;
      border-radius: 3px;
      font-size: 12px;
      font-family: 'JetBrains Mono', 'Consolas', monospace;
      color: #d4380d;
    }
  }

  .rec {
    font-weight: 600;
    color: #262626;
  }
}

.guide-footnote {
  margin: 0;
  font-size: 13px;
  color: #8c8c8c;
  line-height: 1.6;

  b { font-weight: 600; color: #595959; }
}
</style>
