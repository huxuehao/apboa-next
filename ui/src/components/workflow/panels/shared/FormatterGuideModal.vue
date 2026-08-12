<script setup lang="ts">
import { ref } from 'vue'
import { QuestionCircleOutlined } from '@ant-design/icons-vue'

/** 左侧导航条目（与右侧内容 section 一一对应） */
const navItems = [
  { id: 'STRING', label: '纯文本替换', code: 'STRING' },
  { id: 'JACKSON', label: 'JSON 保类型', code: 'JACKSON' },
  { id: 'VELOCITY', label: 'Velocity 模板', code: 'VELOCITY' },
  { id: 'REF', label: '选型速查', code: '' },
]

const open = ref(false)
const activeId = ref('STRING')
const contentRef = ref<HTMLElement>()
const sectionEls: Record<string, HTMLElement> = {}
// 点击导航平滑滚动期间暂停滚动联动，避免高亮抖动
let suppressSpy = false
let suppressTimer: number | undefined

function setSectionEl(id: string, el: unknown) {
  if (el instanceof HTMLElement) sectionEls[id] = el
}

function scrollToSection(id: string) {
  activeId.value = id
  const container = contentRef.value
  const target = sectionEls[id]
  if (!container || !target) return
  suppressSpy = true
  window.clearTimeout(suppressTimer)
  suppressTimer = window.setTimeout(() => {
    suppressSpy = false
  }, 600)
  container.scrollTo({ top: Math.max(target.offsetTop - 4, 0), behavior: 'smooth' })
}

// 内容区滚动时反向高亮左侧导航
function onContentScroll() {
  if (suppressSpy) return
  const container = contentRef.value
  if (!container) return
  const anchor = container.scrollTop + 48
  let current = activeId.value
  for (const item of navItems) {
    const el = sectionEls[item.id]
    if (el && el.offsetTop <= anchor) current = item.id
  }
  activeId.value = current
}
</script>

<template>
  <span class="guide-trigger" @click="open = true">
    <QuestionCircleOutlined style="color: #2F54EB" />
  </span>

  <AModal
    v-model:open="open"
    title="参数模板格式指南"
    width="860px"
    :footer="null"
    :destroy-on-close="true"
  >
    <div class="formatter-guide-layout">
      <!-- 左侧格式导航 -->
      <nav class="guide-sidebar">
        <button
          v-for="item in navItems"
          :key="item.id"
          :class="['sidebar-item', { active: activeId === item.id }]"
          type="button"
          @click="scrollToSection(item.id)"
        >
          <span class="item-label">{{ item.label }}</span>
          <span v-if="item.code" class="item-code">{{ item.code }}</span>
        </button>
      </nav>

      <!-- 右侧说明内容 -->
      <div ref="contentRef" class="guide-content" @scroll="onContentScroll">
        <p class="guide-intro">
          模板格式决定参数值会被如何被渲染。三种格式对比如下，请根据实际需求选择。
        </p>

        <!-- 1. 纯文本替换 -->
        <section :ref="(el) => setSectionEl('STRING', el)" class="format-section">
          <h3 class="section-heading">纯文本替换 <code>STRING</code></h3>
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
        </section>

        <!-- 2. JSON 保类型 -->
        <section :ref="(el) => setSectionEl('JACKSON', el)" class="format-section">
          <h3 class="section-heading">JSON 保类型 <code>JACKSON</code></h3>
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
        </section>

        <!-- 3. Velocity 模板 -->
        <section :ref="(el) => setSectionEl('VELOCITY', el)" class="format-section">
          <h3 class="section-heading">Velocity 模板 <code>VELOCITY</code></h3>
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
        </section>

        <!-- 选型速查 -->
        <section :ref="(el) => setSectionEl('REF', el)" class="format-section">
          <h3 class="section-heading">选型速查</h3>
          <table class="quick-ref">
            <thead>
              <tr>
                <th>你想得到的渲染结果</th>
                <th>推荐</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>一段纯文本，如消息内容、提示词，不在乎变量类型</td>
                <td class="rec">纯文本替换</td>
              </tr>
              <tr>
                <td>一个 JSON 对象，且数字/布尔保持原类型不被转成字符串</td>
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
        </section>
      </div>
    </div>
  </AModal>
</template>

<style scoped lang="scss">
.guide-trigger {
  display: inline-flex;
  align-items: center;
  color: #8c8c8c;
  font-size: 13px;
  cursor: pointer;
  user-select: none;

  &:hover {
    color: #1677ff;
  }
}

.formatter-guide-layout {
  display: grid;
  grid-template-columns: 172px minmax(0, 1fr);
  height: clamp(420px, 60vh, 640px);
}

// ── 左侧导航 ──
.guide-sidebar {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding-right: 12px;
  border-right: 1px solid #f0f0f0;
  overflow-y: auto;
}

.sidebar-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 10px;
  border: none;
  border-radius: 6px;
  background: transparent;
  cursor: pointer;
  text-align: left;
  transition: all 0.15s;

  .item-label {
    font-size: 13px;
    color: #595959;
  }

  .item-code {
    font-size: 11px;
    font-family: 'JetBrains Mono', 'Consolas', monospace;
    color: #bfbfbf;
  }

  &:hover {
    background: #f5f5f5;

    .item-label {
      color: #262626;
    }
  }

  &.active {
    background: #f0f5ff;

    .item-label {
      color: #1677ff;
      font-weight: 600;
    }

    .item-code {
      color: #1677ff;
    }
  }
}

// ── 右侧内容 ──
.guide-content {
  position: relative;
  min-width: 0;
  padding: 0 4px 0 20px;
  overflow-y: auto;
}

.guide-intro {
  margin: 0 0 20px;
  font-size: 13px;
  color: #8c8c8c;
  line-height: 1.75;
}

.format-section {
  margin-bottom: 28px;

  &:last-child {
    margin-bottom: 0;
  }
}

.section-heading {
  margin: 0 0 8px;
  padding-bottom: 6px;
  border-bottom: 1px solid #f0f0f0;
  font-size: 15px;
  font-weight: 700;
  color: #262626;

  code {
    margin-left: 4px;
    padding: 1px 5px;
    background: #f5f5f5;
    border-radius: 3px;
    font-size: 12px;
    font-weight: normal;
    font-family: 'JetBrains Mono', 'Consolas', monospace;
    color: #d4380d;
  }
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
