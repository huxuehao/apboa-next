<template>
  <div class="loop-guide">
    <h2 class="guide-title">循环节点使用指南</h2>
    <p class="guide-intro">
      循环节点用于对数据集合逐项处理，或按固定次数<b>重复执行同一段子流程</b>。
      子流程可访问当前迭代索引、当前元素，以及主流程中所有上游节点的输出。
    </p>

    <!-- 1. 整体架构 -->
    <h3 class="section-heading">1. 整体架构</h3>
    <p class="section-desc">循环节点在主流程中作为一个普通节点，内部包含一个独立的子流程。执行时，主流程将数据传入，子流程逐项处理后将结果汇总返回。</p>

    <div class="flow-diagram">
      <div class="flow-row">
        <span class="flow-node">START</span>
        <span class="flow-arrow">→</span>
        <span class="flow-node">HTTP请求</span>
        <span class="flow-arrow">→</span>
        <span class="flow-node loop-node">Loop
          <div class="flow-sub-box">
            <div class="flow-sub-title">子流程</div>
            <div class="flow-sub-row">
              <span class="flow-arrow-in">→</span>
              <span class="flow-node-sm">节点A</span>
              <span class="flow-arrow-in">→</span>
              <span class="flow-node-sm">节点B</span>
              <span class="flow-arrow-in">→</span>
            </div>
          </div>
        </span>
        <span class="flow-arrow">→</span>
        <span class="flow-node">END</span>
      </div>
      <div class="flow-legend">
        <span>主流程节点 &nbsp;|&nbsp; 虚线框内为 Loop 的子流程</span>
      </div>
    </div>

    <hr class="section-divider" />

    <!-- 2. 循环模式 -->
    <h3 class="section-heading">2. 循环模式</h3>
    <p class="section-desc">两种工作模式，通过面板顶部的分段控件切换：</p>
    <table class="quick-ref">
      <thead><tr><th>模式</th><th>适用场景</th><th>数据来源</th></tr></thead>
      <tbody>
        <tr><td class="rec">数据迭代</td><td>遍历上游节点输出的数组/集合</td><td>从<b>输入绑定</b>中选择一个可迭代变量</td></tr>
        <tr><td class="rec">计数循环</td><td>按指定次数重复执行</td><td>不需要数据源，仅依赖最大次数</td></tr>
      </tbody>
    </table>

    <div class="mode-selector-mock">
      <div class="mock-segmented">
        <span class="mock-seg-option active">数据迭代</span>
        <span class="mock-seg-option">计数循环</span>
      </div>
      <span class="mock-caption">面板控件</span>
    </div>

    <hr class="section-divider" />

    <!-- 3. 数据迭代完整示例 -->
    <h3 class="section-heading">3. 数据迭代 — 完整示例</h3>

    <div class="step-block">
      <div class="step-num">1</div>
      <div class="step-content">
        <b>上游节点输出</b> — 假设 HTTP 请求节点返回了一个包含数组的 JSON：
        <pre class="code-block">{
  "total": 100,
  "orders": [
    { "id": 1, "amount": 99.0 },
    { "id": 2, "amount": 199.0 }
  ]
}</pre>
      </div>
    </div>

    <div class="step-block">
      <div class="step-num">2</div>
      <div class="step-content">
        <b>配置输入绑定</b> — 在 Loop 面板的「输入绑定」中添加一条，引用 HTTP 请求节点的 <code>output</code>，命名为 <code>data</code>
      </div>
    </div>

    <div class="step-block">
      <div class="step-num">3</div>
      <div class="step-content">
        <b>选择迭代数据源</b> — 在「迭代数据源」下拉中选择 <code>data</code>，然后在下级字段 <code>data.orders</code> 中引用数组。
        选择后，元素变量名 <code>item</code> 代表每次迭代的当前行。
      </div>
    </div>

    <div class="step-block">
      <div class="step-num">4</div>
      <div class="step-content">
        <b>子流程中引用</b> — 进入子流程编辑后，在子流程节点中：
        <ul class="pros-cons">
          <li class="pro">输入绑定类型选「变量」，填入 <code>item</code>，即可获取当前订单对象</li>
          <li class="pro">输入绑定类型选「变量」，填入 <code>loopIndex</code>，即可获取当前索引（从 0 开始）</li>
          <li class="pro">输入绑定类型选「变量」，填入 <code>data</code>，即可获取整个上游对象（含 total 和 orders）</li>
        </ul>
      </div>
    </div>

    <hr class="section-divider" />

    <!-- 4. 循环变量速查 -->
    <h3 class="section-heading">4. 循环变量速查</h3>
    <table class="quick-ref">
      <thead><tr><th>变量名</th><th>默认值</th><th>说明</th></tr></thead>
      <tbody>
        <tr><td class="rec">loopIndex</td><td><code>loopVariable</code></td><td>当前迭代索引，从 0 递增。计数循环时此变量最重要</td></tr>
        <tr><td class="rec">item</td><td><code>itemVariable</code></td><td>当前迭代元素。计数循环时为 <code>null</code></td></tr>
      </tbody>
    </table>

    <hr class="section-divider" />

    <!-- 5. 子流程编辑 -->
    <h3 class="section-heading">5. 进入 / 退出子流程</h3>
    <p class="section-desc">点击面板中「进入子流程可视化编辑」，画布将切换为子流程视图。</p>

    <div class="feature-grid">
      <div class="feature-item pro">子流程中可添加任意节点（开始/结束/循环节点隐藏）</div>
      <div class="feature-item pro">画布顶部显示蓝色横幅「正在编辑 xxx 的子流程」</div>
      <div class="feature-item pro">左上角「← 完成编辑，返回主流程」按钮退出</div>
      <div class="feature-item pro">退出时子流程数据自动回写，无需手动保存</div>
    </div>

    <hr class="section-divider" />

    <!-- 6. 父子流程数据共享 -->
    <h3 class="section-heading">6. 父子流程数据共享</h3>
    <p class="section-desc">
      循环节点执行迭代前，会将<b>主流程所有上游节点的输出</b>复制到子流程上下文。
      子流程节点可以直接引用主流程中任意上游节点的输出。
    </p>

    <div class="data-flow-diagram">
      <div class="data-flow-parent">
        <div class="data-flow-label">主流程上游节点输出</div>
        <div class="data-flow-nodes">
          <span class="df-node">START</span>
          <span class="df-arrow">→</span>
          <span class="df-node highlight">HTTP请求</span>
        </div>
      </div>
      <div class="data-flow-arrow-down">全部复制</div>
      <div class="data-flow-child">
        <div class="data-flow-label">子流程上下文（每次迭代）</div>
        <div class="data-flow-nodes">
          <span class="df-node">节点A</span>
          <span class="df-arrow">→</span>
          <span class="df-node">节点B</span>
        </div>
      </div>
    </div>

    <p class="section-desc" style="margin-top:14px;">
      在子流程节点的「输入绑定 → 节点输出」下拉列表中：
    </p>
    <div class="node-selector-mock">
      <div class="ns-group-header">
        <span class="ns-dot local" />
        <span>代码执行</span>
      </div>
      <div class="ns-output-row">output · Object · 节点默认输出</div>

      <div class="ns-divider">
        <span class="ns-divider-text">以下输出来自主流程</span>
      </div>

      <div class="ns-group-header parent">
        <span class="ns-dot parent" />
        <span>HTTP请求</span>
      </div>
      <div class="ns-output-row">output · Object · 节点默认输出</div>
    </div>
    <p class="guide-footnote">
      分隔线以上为当前子流程节点，分隔线以下为来自主流程的上游节点，带有轻微透明区分。
    </p>

    <hr class="section-divider" />

    <!-- 7. 入口节点 -->
    <h3 class="section-heading">7. 入口节点</h3>
    <p class="section-desc">
      入口节点是子流程每条工作流分支的<b>起点</b>——即子流程中<b>没有入边</b>的节点。
      系统自动从子流程定义中计算候选值。
    </p>
    <p class="guide-footnote">
      每条分支需要一个入口节点。用户只需在「入口节点」下拉中点击选择，无需手动输入 ID。
    </p>

    <hr class="section-divider" />

    <!-- 8. 终止条件 -->
    <h3 class="section-heading">8. 终止条件</h3>
    <p class="section-desc">
      可选，通过 <b>Groovy 表达式</b>提前终止循环。表达式可使用 <code>loopIndex</code>、<code>item</code> 及主流程全局变量。
    </p>
    <table class="quick-ref">
      <thead><tr><th>表达式</th><th>效果</th></tr></thead>
      <tbody>
        <tr><td class="rec"><code>item == null</code></td><td>当前元素为空时停止</td></tr>
        <tr><td class="rec"><code>loopIndex &gt;= 100</code></td><td>迭代达到 100 次时停止</td></tr>
        <tr><td class="rec"><code>item.amount &gt; 500</code></td><td>当前元素 amount 字段超过 500 时停止</td></tr>
      </tbody>
    </table>

    <hr class="section-divider" />

    <!-- 9. 输出 -->
    <h3 class="section-heading">9. 循环节点输出</h3>
    <table class="quick-ref">
      <thead><tr><th>输出字段</th><th>类型</th><th>含义</th></tr></thead>
      <tbody>
        <tr><td class="rec">output</td><td>Array</td><td>所有迭代结果的数组（每次迭代中子流程最后节点的输出）</td></tr>
        <tr><td class="rec">totalIterations</td><td>Number</td><td>实际执行的总迭代次数</td></tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped lang="scss">
.guide-title { margin: 0 0 8px; font-size: 18px; font-weight: 700; color: #262626; }
.guide-intro { margin: 0 0 28px; font-size: 14px; color: #595959; line-height: 1.75; b { font-weight: 600; color: #262626; } }
.section-heading { margin: 0 0 8px; font-size: 16px; font-weight: 700; color: #262626; }
.section-divider { margin: 24px 0; border: none; border-top: 1px solid #f0f0f0; }
.section-desc {
  margin: 0 0 14px; font-size: 14px; color: #434343; line-height: 1.75;
  b { font-weight: 600; }
  code { padding: 1px 5px; background: #f5f5f5; border-radius: 3px; font-size: 13px; font-family: 'JetBrains Mono', 'Consolas', monospace; color: #d4380d; }
}

.code-block {
  margin: 8px 0; padding: 10px 14px; background: #f8f9fa; border: 1px solid #eee; border-radius: 6px;
  font-size: 12px; font-family: 'JetBrains Mono', 'Fira Code', 'Consolas', monospace; color: #262626; line-height: 1.7; white-space: pre-wrap;
}

/* ── 流程示意图 ── */
.flow-diagram {
  margin-bottom: 18px; padding: 14px 16px; background: #fafafa; border: 1px solid #eee; border-radius: 8px;
}
.flow-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.flow-node {
  padding: 3px 10px; font-size: 12px; font-weight: 600; color: #262626; background: #fff; border: 1px solid #d9d9d9; border-radius: 4px;
}
.flow-arrow { font-size: 14px; color: #bfbfbf; }
.flow-node.loop-node {
  position: relative; padding-bottom: 0; background: #fafafa; border: 1px dashed #1677ff;
  .flow-sub-box { margin-top: 6px; padding: 6px 8px 8px; background: #f0f5ff; border-radius: 0 0 3px 3px; }
}
.flow-sub-title { font-size: 10px; color: #8c8c8c; margin-bottom: 4px; text-align: center; }
.flow-sub-row { display: flex; align-items: center; gap: 4px; justify-content: center; }
.flow-arrow-in { font-size: 12px; color: #1677ff; }
.flow-node-sm { padding: 2px 6px; font-size: 11px; color: #1677ff; background: #fff; border: 1px solid #91caff; border-radius: 3px; }
.flow-legend { margin-top: 8px; font-size: 11px; color: #8c8c8c; text-align: center; }

/* ── 操作步骤 ── */
.step-block { display: flex; gap: 12px; margin-bottom: 16px; }
.step-num {
  flex-shrink: 0; width: 22px; height: 22px; display: flex; align-items: center; justify-content: center;
  font-size: 11px; font-weight: 700; color: #fff; background: #1677ff; border-radius: 50%;
}
.step-content { flex: 1; font-size: 13px; color: #434343; line-height: 1.7; b { font-weight: 600; } }

/* ── 特性列表 ── */
.feature-grid { display: flex; flex-direction: column; gap: 6px; margin-bottom: 14px; }
.feature-item {
  font-size: 13px; color: #434343; line-height: 1.65; padding-left: 16px; position: relative;
  &.pro::before { content: '+'; position: absolute; left: 0; color: #8c8c8c; font-weight: 600; }
}

/* ── 数据流示意图 ── */
.data-flow-diagram { margin-bottom: 14px; }
.data-flow-parent, .data-flow-child {
  padding: 10px 14px; background: #fafafa; border: 1px solid #eee; border-radius: 6px; margin-bottom: 6px;
}
.data-flow-label { font-size: 11px; color: #8c8c8c; margin-bottom: 6px; }
.data-flow-nodes { display: flex; align-items: center; gap: 8px; }
.df-node { padding: 3px 8px; font-size: 12px; color: #595959; background: #fff; border: 1px solid #d9d9d9; border-radius: 3px; }
.df-node.highlight { border-color: #1677ff; color: #1677ff; }
.df-arrow { font-size: 13px; color: #bfbfbf; }
.data-flow-arrow-down { text-align: center; font-size: 12px; color: #8c8c8c; padding: 4px 0; }

/* ── 节点输出下拉示意 ── */
.node-selector-mock {
  padding: 8px 12px; background: #fafafa; border: 1px solid #eee; border-radius: 6px; margin-bottom: 14px;
  font-size: 12px;
}
.ns-group-header {
  display: flex; align-items: center; gap: 6px; padding: 4px 0; color: #262626; font-weight: 500;
  &.parent { opacity: 0.75; }
}
.ns-dot { width: 6px; height: 6px; border-radius: 50%; flex-shrink: 0; background: #1677ff; }
.ns-dot.local { background: #1677ff; }
.ns-dot.parent { background: #8c8c8c; }
.ns-output-row { padding: 2px 0 2px 12px; color: #8c8c8c; font-size: 11px; }
.ns-divider {
  display: flex; align-items: center; padding: 6px 0; margin: 4px 0; border-top: 1px solid #f0f0f0;
}
.ns-divider-text { font-size: 10px; color: #bfbfbf; }

/* ── 表格 ── */
.quick-ref {
  width: 100%; border-collapse: collapse; margin-bottom: 14px; font-size: 13px;
  th, td { padding: 9px 14px; text-align: left; border-bottom: 1px solid #f0f0f0; }
  th { font-weight: 600; color: #8c8c8c; font-size: 12px; }
  td { color: #434343; line-height: 1.6; code { padding: 1px 4px; background: #f5f5f5; border-radius: 3px; font-size: 12px; font-family: 'JetBrains Mono', 'Consolas', monospace; color: #d4380d; } }
  .rec { font-weight: 600; color: #262626; }
}

.pros-cons {
  margin: 4px 0; padding: 0; list-style: none; display: flex; flex-direction: column; gap: 3px;
  li { font-size: 13px; line-height: 1.6; padding-left: 16px; position: relative; color: #434343; }
  .pro::before { content: '+'; position: absolute; left: 0; color: #8c8c8c; font-weight: 600; }
}

.guide-footnote { margin: 0 0 12px; font-size: 13px; color: #8c8c8c; line-height: 1.6; b { font-weight: 600; color: #595959; } }

.mode-selector-mock { margin-bottom: 14px; display: flex; align-items: center; gap: 12px; }
.mock-segmented { display: inline-flex; border-radius: 6px; background: #f2f4f7; padding: 2px; }
.mock-seg-option { padding: 4px 12px; border-radius: 4px; font-size: 13px; color: rgba(0,0,0,0.65); &.active { background: #fff; color: rgba(0,0,0,0.88); box-shadow: 0 1px 2px rgba(0,0,0,0.06); } }
.mock-caption { font-size: 12px; color: #8c8c8c; }
</style>
