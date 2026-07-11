<template>
  <div class="quick-input-guide">
    <h2 class="guide-title">快捷输入绑定名</h2>
    <p class="guide-intro">
      在配置面板的输入框或代码编辑器中，通过<b>双击 Ctrl 键</b>快速插入 <code>${输入绑定名}</code> 占位符，无需手动输入。
    </p>

    <!-- 使用场景 -->
    <h3 class="section-title">使用场景</h3>
    <p class="section-text">
      当你在节点配置面板中编辑提示词、SQL 语句、HTTP Body 等<b>支持模板语法的文本内容</b>时，需要引用上游节点的输入绑定。此时双击 Ctrl 即可快速选取并插入，避免拼写错误。
    </p>

    <!-- 操作方式 -->
    <h3 class="section-title">操作方式</h3>
    <ol class="step-list">
      <li>在配置面板的任意输入框或代码编辑器中点击<b>获取焦点</b>（出现光标闪烁）</li>
      <li><b>快速双击 Ctrl 键</b>（两次按下间隔不超过 0.5 秒）</li>
      <li>
        若当前节点<b>仅有 1 个</b>输入绑定：<code>${绑定名}</code> 直接插入光标位置
      </li>
      <li>
        若当前节点<b>有多个</b>输入绑定：在光标附近弹出选择面板，点击或键盘（↑↓ Enter）选取后插入
      </li>
    </ol>

    <!-- 示意图 -->
    <h3 class="section-title">操作示意</h3>
    <div class="diagram">
      <!-- 步骤 1 -->
      <div class="diagram-step">
        <div class="step-badge">1</div>
        <div class="step-label">获取焦点</div>
        <div class="diagram-scene">
          <div class="mock-panel">
            <div class="mock-panel-header">节点配置</div>
            <div class="mock-field">
              <span class="mock-label">系统提示词</span>
              <div class="mock-editor mock-editor-focused">
                <span class="mock-cursor">|</span>
              </div>
            </div>
          </div>
        </div>
        <div class="step-desc">在输入框或编辑器中点击，出现闪烁光标</div>
      </div>

      <!-- 箭头 -->
      <div class="diagram-arrow">
        <svg width="24" height="40" viewBox="0 0 24 40">
          <line x1="12" y1="0" x2="12" y2="32" stroke="#bfbfbf" stroke-width="2" />
          <polyline points="4,26 12,36 20,26" fill="none" stroke="#bfbfbf" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </div>

      <!-- 步骤 2 -->
      <div class="diagram-step">
        <div class="step-badge">2</div>
        <div class="step-label">双击 Ctrl</div>
        <div class="diagram-scene">
          <div class="keyboard-hint">
            <kbd class="key-cap key-cap-lg">Ctrl</kbd>
            <span class="key-times">&times; 2</span>
          </div>
        </div>
        <div class="step-desc">快速双击 Ctrl 键（间隔 &lt; 0.5s）</div>
      </div>

      <!-- 箭头 -->
      <div class="diagram-arrow">
        <svg width="24" height="40" viewBox="0 0 24 40">
          <line x1="12" y1="0" x2="12" y2="32" stroke="#bfbfbf" stroke-width="2" />
          <polyline points="4,26 12,36 20,26" fill="none" stroke="#bfbfbf" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </div>

      <!-- 步骤 3（多绑定时） -->
      <div class="diagram-step">
        <div class="step-badge">3</div>
        <div class="step-label">选择插入</div>
        <div class="diagram-scene diagram-scene-wide">
          <div class="mock-panel">
            <div class="mock-panel-header">节点配置</div>
            <div class="mock-field">
              <span class="mock-label">系统提示词</span>
              <div class="mock-editor">
                请处理以下内容：<span class="mock-inserted">${input}</span><span class="mock-cursor">|</span>
              </div>
            </div>
          </div>
          <div class="mock-popup">
            <div class="mock-popup-header">输入绑定</div>
            <div class="mock-popup-item mock-popup-item-active">input</div>
            <div class="mock-popup-item">userName</div>
            <div class="mock-popup-item">context</div>
          </div>
        </div>
        <div class="step-desc">选择目标绑定名，<code>${输入名}</code> 自动插入光标处</div>
      </div>
    </div>

    <!-- 补充说明 -->
    <div class="tip-box">
      <span class="tip-icon">!</span>
      <span><b>提示：</b>仅单个输入绑定时，双击 Ctrl 将<b>直接插入</b>不弹窗，进一步提升效率。</span>
    </div>
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
  margin: 0 0 24px;
  font-size: 14px;
  color: #595959;
  line-height: 1.8;

  b { font-weight: 600; color: #262626; }

  code {
    padding: 1px 6px;
    background: #f5f5f5;
    border-radius: 4px;
    font-size: 13px;
    font-family: 'JetBrains Mono', 'Consolas', monospace;
    color: #d4380d;
  }
}

.section-title {
  margin: 28px 0 10px;
  padding-bottom: 6px;
  border-bottom: 1px solid #f5f5f5;
  font-size: 15px;
  font-weight: 700;
  color: #262626;

  &:first-of-type {
    margin-top: 0;
  }
}

.section-text {
  margin: 0 0 8px;
  font-size: 14px;
  color: #434343;
  line-height: 1.8;

  b { font-weight: 600; }
}

// ── 操作步骤列表 ──
.step-list {
  margin: 0;
  padding: 0 0 0 20px;

  li {
    margin-bottom: 8px;
    font-size: 14px;
    color: #434343;
    line-height: 1.75;

    b { font-weight: 600; color: #262626; }

    code {
      padding: 1px 6px;
      background: #f5f5f5;
      border-radius: 4px;
      font-size: 13px;
      font-family: 'JetBrains Mono', 'Consolas', monospace;
      color: #d4380d;
    }
  }
}

// ── 示意图区域 ──
.diagram {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0;
  margin-top: 16px;
}

.diagram-step {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  width: 100%;
}

.step-badge {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: #1677ff;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
}

.step-label {
  font-size: 14px;
  font-weight: 600;
  color: #262626;
}

.step-desc {
  margin-top: 4px;
  font-size: 12px;
  color: #8c8c8c;
  text-align: center;
  line-height: 1.6;

  code {
    padding: 1px 5px;
    background: #f5f5f5;
    border-radius: 3px;
    font-size: 11px;
    font-family: 'JetBrains Mono', 'Consolas', monospace;
    color: #d4380d;
  }
}

.diagram-scene {
  display: flex;
  justify-content: center;
  width: 100%;

  &.diagram-scene-wide {
    gap: 16px;
    align-items: flex-start;
  }
}

.diagram-arrow {
  display: flex;
  justify-content: center;
  margin: 4px 0;
}

// ── 模拟配置面板 ──
.mock-panel {
  width: 220px;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  background: #fff;
  overflow: hidden;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.mock-panel-header {
  padding: 6px 10px;
  background: #fafafa;
  border-bottom: 1px solid #f0f0f0;
  font-size: 12px;
  font-weight: 600;
  color: #8c8c8c;
}

.mock-field {
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.mock-label {
  font-size: 12px;
  color: #595959;
}

.mock-editor {
  min-height: 52px;
  padding: 8px 10px;
  border: 1px solid #e8e8e8;
  border-radius: 6px;
  background: #f8f9fa;
  font-size: 12px;
  font-family: 'JetBrains Mono', 'Consolas', monospace;
  color: #8c8c8c;
  line-height: 1.6;
  position: relative;

  &.mock-editor-focused {
    border-color: #1677ff;
    box-shadow: 0 0 0 2px rgba(22, 119, 255, 0.12);
  }
}

.mock-cursor {
  color: #1677ff;
  font-weight: 700;
  animation: blink 1s step-end infinite;
}

@keyframes blink {
  50% { opacity: 0; }
}

.mock-inserted {
  padding: 1px 4px;
  background: #e6f4ff;
  border-radius: 3px;
  color: #1677ff;
  font-weight: 500;
}

// ── 键盘提示 ──
.keyboard-hint {
  display: flex;
  align-items: center;
  gap: 8px;
}

.key-cap {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 28px;
  height: 28px;
  padding: 0 8px;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  border-bottom-width: 3px;
  background: #fff;
  font-size: 12px;
  font-family: inherit;
  color: #434343;
  line-height: 1;

  &.key-cap-lg {
    min-width: 48px;
    height: 34px;
    font-size: 14px;
    font-weight: 600;
  }
}

.key-times {
  font-size: 16px;
  font-weight: 700;
  color: #1677ff;
}

// ── 模拟弹出面板 ──
.mock-popup {
  width: 170px;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.12);
  overflow: hidden;
  flex-shrink: 0;
}

.mock-popup-header {
  padding: 6px 10px;
  border-bottom: 1px solid #f0f0f0;
  font-size: 11px;
  color: #8c8c8c;
}

.mock-popup-item {
  padding: 5px 10px;
  font-size: 12px;
  color: #434343;
  cursor: default;

  &.mock-popup-item-active {
    background: #f0f5ff;
    color: #1677ff;
    font-weight: 600;
  }
}

// ── 提示框 ──
.tip-box {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-top: 24px;
  padding: 12px 14px;
  border-radius: 8px;
  background: #fffbe6;
  border: 1px solid #ffe58f;

  span {
    font-size: 13px;
    color: #595959;
    line-height: 1.7;

    b { font-weight: 600; color: #262626; }
  }
}

.tip-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #faad14;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
  margin-top: 1px;
}
</style>
