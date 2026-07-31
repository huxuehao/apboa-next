<script setup lang="ts">
/**
 * 自定义组件开发规范 使用说明
 *
 * @author huxuehao
 */
</script>

<template>
  <div v-pre class="guide-content">
    <h2 class="guide-title">自定义组件开发规范</h2>
    <p class="guide-intro">
      「自定义组件」面板可渲染 <code>ui/src/components/portal/</code> 目录下的业务组件。
      目录在编译期通过 glob 锁定（路径隔离），运行期无法注入其他路径的代码。
    </p>

    <h3 class="section-title">目录与扫描规则</h3>
    <ul class="req-list">
      <li>组件放在 <code>ui/src/components/portal/</code>，支持子目录，仅识别 <code>.vue</code> 文件</li>
      <li>组件标识为相对路径去扩展名，如 <code>DemoWelcome</code>、<code>sub/Foo</code></li>
      <li>新增文件后开发服务器自动感知；生产环境每个组件独立分包、按需加载</li>
      <li>配置面板下拉自动列出全部组件，支持按名称/文件名/描述搜索</li>
    </ul>

    <h3 class="section-title">元信息约定（下拉三要素）</h3>
    <p class="section-text">在组件中声明 <code>portalMeta</code>，下拉选项将自动展示组件名称与描述：</p>
    <pre class="code-block">defineOptions({
  portalMeta: {
    name: '示例欢迎卡',
    description: '一句话说明组件用途',
  },
})</pre>
    <p class="section-text">未声明时名称回退为文件名、描述为空。</p>

    <h3 class="section-title">props 与上下文注入</h3>
    <ul class="req-list">
      <li>配置面板「编辑 props」打开弹窗，逐条添加 <code>key + 类型 + 值</code></li>
      <li>类型支持：字符串 / 数字 / 布尔 / 数组 / 日期 / 对象，值控件按类型定制（数字输入、开关、日期选择器、JSON 编辑）</li>
      <li>传递时按声明类型强制转换：数字/布尔为原生类型，日期注入 <code>Date</code> 对象，数组/对象为解析后的 JSON 结构</li>
      <li>面板渲染器额外注入 <code>panelContext</code> prop：<code>{ panelId, title, interactive }</code>（interactive 为 true 表示门户运行态）</li>
      <li>组件按需声明 props 即可，多余的透传属性会被忽略</li>
    </ul>

    <h3 class="section-title">标题栏操作按钮（自动识别）</h3>
    <p class="section-text">
      通过 <code>defineExpose</code> 暴露 <code>panelActions</code>，面板会自动识别并渲染在标题栏左区；
      用户可在配置面板「操作按钮」中逐个控制显隐：
    </p>
    <pre class="code-block">defineExpose({
  panelActions: [
    { key: 'refresh', label: '刷新', icon: 'ReloadOutlined', run: refresh },
    { key: 'more', label: '更多', icon: 'EllipsisOutlined', run: openMore },
  ],
})</pre>
    <ul class="req-list">
      <li><code>key</code>：唯一标识（英文），显隐配置以此记录</li>
      <li><code>icon</code>：图标注册表中的 Outlined 图标名，缺省时按钮显示 label 文本</li>
      <li><code>run</code>：点击回调，在组件内部实现具体行为</li>
    </ul>

    <h3 class="section-title">定时刷新接入</h3>
    <p class="section-text">
      组件暴露 <code>refresh()</code> 方法后，面板的「定时刷新」配置将周期性调用它（自定义组件不绑定数据集，刷新行为完全由组件自身定义）：
    </p>
    <pre class="code-block">defineExpose({ refresh: () => reload() })</pre>

    <h3 class="section-title">错误兜底</h3>
    <ul class="req-list">
      <li>组件加载失败 / 标识不存在：面板内显示错误提示，不影响画布</li>
      <li>组件运行时抛错：被面板捕获并就地展示错误信息</li>
      <li>props 条目非法（如 JSON 解析失败）：保存时前置校验拦截，运行时非法条目自动跳过</li>
    </ul>

    <h3 class="section-title">完整示例</h3>
    <p class="section-text">
      参考内置示例 <code>ui/src/components/portal/DemoWelcome.vue</code>，
      其演示了 portalMeta、panelContext、自定义 props、panelActions 与 refresh 全部约定。
    </p>

    <div class="tip-box">
      <span class="tip-icon">!</span>
      <span>
        自定义组件不支持绑定数据集；如需取数请在组件内部自行调用 API。
        组件样式遵循平台简约规范：纯色、无渐变、无大面积阴影、图标仅用 Outlined 风格。
      </span>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/dashboard/guide' as *;
</style>
