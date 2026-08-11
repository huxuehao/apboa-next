<script setup lang="ts">
/** 记忆压缩说明文档弹窗。 */
defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
}>()
</script>

<template>
  <AModal
    :open="open"
    title="记忆压缩配置说明"
    :footer="null"
    width="860px"
    style="top: 0"
    @cancel="emit('update:open', false)"
  >
    <div class="compression-doc">
      <AAlert
        type="info"
        show-icon
        message="压缩只作用于当前【工作上下文】"
        description="系统保留完整原始历史，同时在每次推理前检查【工作上下文】。只有【工作上下文】会被送入模型，原始历史不会参与本页的压力计算。"
      />

      <h3>一、什么时候触发压缩</h3>
      <p>
        每次推理前，系统都会计算当前消息数和 token 数。满足下面任意一个条件就进入压缩流程：
      </p>
      <div class="formula">消息数 ≥ [消息阈值]，或 token 数 ≥ [最大Token数 × Token比率]</div>
      <p>
        <b>触发并不等于一定能压缩。</b>触发后还要按<b>六级</b>策略寻找可处理的历史内容；所有策略都没有候选内容时，本轮会<b>跳过压缩</b>。
      </p>

      <h3>二、六级压缩策略</h3>
      <ol>
        <li><b>1. 历史工具调用摘要：</b>寻找较早且连续的工具调用与工具结果，交给压缩模型生成事实摘要。</li>
        <li><b>2. 带保护的大消息卸载：</b>把较大的历史消息替换为预览和可恢复标记，并保护最近的 [保留最近消息数] 条消息。</li>
        <li><b>3. 不带保护的大消息卸载：</b>上下文仍超限时，允许处理更近的大消息。</li>
        <li><b>4. 历史轮次摘要：</b>将已完成的 USER → ASSISTANT 轮次摘要成一条消息；单个候选轮次小于 [最小压缩Token阈值] 会跳过。</li>
        <li><b>5. 当前轮大消息摘要：</b>保守处理当前轮中超过大消息阈值的内容。</li>
        <li><b>6. 当前轮整体摘要：</b>按照 [当前轮压缩比] 压缩当前轮工具交互，保留关键结果。</li>
      </ol>

      <h3>三、配置项如何影响算法</h3>
      <div class="config-table">
        <div class="config-row config-head"><span>配置项</span><span>作用与建议</span></div>
        <div class="config-row"><code>最大Token数</code><span>模型上下文窗口的预算上限，应与实际模型一致。</span></div>
        <div class="config-row"><code>Token比率</code><span>Token 触发比例。0.70-0.80 通常较平衡，越低越早压缩。</span></div>
        <div class="config-row"><code>消息阈值</code><span>消息数触发阈值。短消息很多的 Agent 应降低；工具密集型 Agent 可适当提高。</span></div>
        <div class="config-row"><code>保留最近消息数</code><span>优先保护最近消息，建议为消息阈值的 10%-30%，常见范围为 4-18；设置过大将缩小策略 1、2 可处理的历史范围。</span></div>
        <div class="config-row"><code>最小压缩Token阈值</code><span>单个候选摘要的最低 token 数。它应匹配典型历史轮次或工具组大小，而不是随上下文窗口线性增长；建议按窗口分档设置在 96-1024 之间。</span></div>
        <div class="config-row"><code>最小连续工具消息数</code><span>连续工具消息门槛。上游判断使用“超过”该值，配置 2 实际表示至少 3 条连续工具消息。</span></div>
        <div class="config-row"><code>大负载阈值</code><span>单条消息的字符阈值，适合大文件、网页和代码输出；通常为 1K-8K 字符。</span></div>
        <div class="config-row"><code>卸载预览长度</code><span>卸载后保留的预览字符数，越大越容易理解，越小越节省上下文。</span></div>
        <div class="config-row"><code>当前轮压缩比</code><span>当前轮摘要目标比例。0.30-0.50 较常用；越低节省 token 越多，但细节损失越大。</span></div>
      </div>

      <h3>四、配置检查要点</h3>
      <ul>
        <li>1. 不要把 [最小压缩Token阈值] 设置得高于大多数历史轮次的 token 数。</li>
        <li>2. 工具密集型 Agent 不要把 [最小连续工具消息数] 固定为 6，除非确实存在很长的工具链。</li>
        <li>3. 上下文窗口发生变化后，应重新使用“智能配置”，否则 [最大Token数] 与模型实际能力可能不匹配。</li>
        <li>4. 圆环显示的是 “工作上下文” 的综合压缩压力，不是“已经压缩了多少内容”。</li>
      </ul>
    </div>
  </AModal>
</template>

<style scoped lang="scss">
.compression-doc { color: var(--color-text); line-height: 1.7; }
.compression-doc h3 { margin: 20px 0 8px; font-size: 16px; }
.compression-doc p { margin: 6px 0; }
.compression-doc ol, .compression-doc ul { padding-left: 22px; margin: 8px 0; }
.formula { padding: 10px 14px; margin: 10px 0; background: #f5f7fa; border-left: 3px solid #1677ff; }
.config-table { border: 1px solid #e5e7eb; border-radius: 6px; overflow: hidden; }
.config-row { display: grid; grid-template-columns: 230px 1fr; gap: 16px; padding: 9px 12px; border-top: 1px solid #eef0f2; }
.config-row:first-child { border-top: 0; }
.config-head { font-weight: 600; background: #f7f8fa; }
.config-row code { color: #0958d9; }
@media (max-width: 700px) { .config-row { grid-template-columns: 1fr; gap: 2px; } }
</style>
