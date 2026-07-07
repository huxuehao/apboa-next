<template>
  <div class="ctrl-add-guide">
    <h2 class="guide-title">Ctrl 快速添加节点</h2>
    <p class="guide-intro">
      在画布上<b>按住 Ctrl 键</b>悬浮节点，即可看到 <b>+ 按钮</b>，点击后直接从当前节点弹出节点库，自动完成连线。
    </p>

    <!-- 使用场景 -->
    <h3 class="section-title">使用场景</h3>
    <p class="section-text">
      当你需要从某个已存在的节点<b>快速串联下一个节点</b>时，无需从画布左侧的节点库中拖拽后再手动连线。按住 Ctrl 点击 + 按钮，一步完成"选节点 + 连线"。
    </p>

    <!-- 操作方式 -->
    <h3 class="section-title">操作方式</h3>
    <ol class="step-list">
      <li>将鼠标<b>悬浮</b>到画布上的任意节点（END 节点除外），节点上方出现 "按住 Ctrl 添加节点" 提示</li>
      <li><b>按住 Ctrl 键</b>，节点右侧出现蓝色 <b>+</b> 按钮</li>
      <li><b>点击 + 按钮</b>，弹出节点库面板</li>
      <li>选择目标节点类型，新节点<b>自动添加到右侧并完成连线</b></li>
    </ol>

    <!-- 示意图 -->
    <h3 class="section-title">操作示意</h3>
    <div class="diagram">
      <!-- 步骤 1 -->
      <div class="diagram-step">
        <div class="step-badge">1</div>
        <div class="step-label">悬浮节点</div>
        <div class="diagram-scene">
          <div class="mock-node">
            <div class="mock-node-hint">按住 Ctrl 添加节点</div>
            <div class="mock-node-body">
              <span class="mock-node-icon" />
              <span class="mock-node-name">代码处理</span>
              <span class="mock-node-dot" />
            </div>
          </div>
        </div>
        <div class="step-desc">鼠标悬浮时，节点上方出现操作提示</div>
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
        <div class="step-label">按住 Ctrl</div>
        <div class="diagram-scene diagram-scene-wide">
          <div class="keyboard-hint">
            <kbd class="key-cap key-cap-lg">Ctrl</kbd>
            <span class="key-hold-label">按住</span>
          </div>
          <div class="mock-nodes-row">
            <div class="mock-node mock-node-active">
              <div class="mock-node-body">
                <span class="mock-node-icon" />
                <span class="mock-node-name">代码处理</span>
                <span class="mock-node-dot" />
              </div>
              <div class="mock-plus-btn" />
            </div>
            <div class="mock-link-line" />
            <div class="mock-node">
              <div class="mock-node-body mock-node-body-dim">
                <span class="mock-node-icon" />
                <span class="mock-node-name">新节点</span>
                <span class="mock-node-dot" />
              </div>
            </div>
          </div>
        </div>
        <div class="step-desc">按住 Ctrl 后，节点右侧出现蓝色 + 按钮</div>
      </div>

      <!-- 箭头 -->
      <div class="diagram-arrow">
        <svg width="24" height="40" viewBox="0 0 24 40">
          <line x1="12" y1="0" x2="12" y2="32" stroke="#bfbfbf" stroke-width="2" />
          <polyline points="4,26 12,36 20,26" fill="none" stroke="#bfbfbf" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </div>

      <!-- 步骤 3 -->
      <div class="diagram-step">
        <div class="step-badge">3</div>
        <div class="step-label">点击 + 并选择</div>
        <div class="diagram-scene">
          <div class="mock-library">
            <div class="mock-library-header">节点库</div>
            <div class="mock-library-item mock-library-item-active">HTTP 请求</div>
            <div class="mock-library-item">数据库查询</div>
            <div class="mock-library-item">条件分支</div>
            <div class="mock-library-item">循环遍历</div>
          </div>
        </div>
        <div class="step-desc">点击 + 弹出节点库，选择后自动连线</div>
      </div>
    </div>

    <!-- 分支节点说明 -->
    <h3 class="section-title">分支节点的特殊行为</h3>
    <p class="section-text">
      对于<b>条件分支（IF-ELSE）</b>等具有多个输出分支的节点，按住 Ctrl 后<b>每个分支右侧</b>都会出现独立的 + 按钮，点击不同分支的 + 会将新节点添加到对应分支上。
    </p>

    <div class="mock-branch-demo">
      <div class="mock-node mock-node-active" style="align-self: flex-start;">
        <div class="mock-node-body mock-node-body-branch">
          <span class="mock-node-icon" />
          <span class="mock-node-name">条件分支</span>
          <span class="mock-node-dot" />
        </div>
        <div class="mock-branch-handles">
          <div class="mock-branch-row">
            <span class="mock-branch-label">TRUE</span>
            <div class="mock-plus-btn mock-plus-btn-sm" />
          </div>
          <div class="mock-branch-row">
            <span class="mock-branch-label">FALSE</span>
            <div class="mock-plus-btn mock-plus-btn-sm" />
          </div>
        </div>
      </div>
    </div>

    <!-- 补充说明 -->
    <div class="tip-box">
      <span class="tip-icon">!</span>
      <span><b>提示：</b>该快捷操作在<b>工作流未锁定</b>时生效。若画布已锁定，+ 按钮不会出现。</span>
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
}

.section-title {
  margin: 28px 0 10px;
  padding-bottom: 6px;
  border-bottom: 1px solid #f5f5f5;
  font-size: 15px;
  font-weight: 700;
  color: #262626;

  &:first-of-type { margin-top: 0; }
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
}

.diagram-scene {
  display: flex;
  justify-content: center;
  width: 100%;

  &.diagram-scene-wide {
    gap: 20px;
    align-items: center;
  }
}

.diagram-arrow {
  display: flex;
  justify-content: center;
  margin: 4px 0;
}

// ── 键盘提示 ──
.keyboard-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
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

.key-hold-label {
  font-size: 13px;
  font-weight: 600;
  color: #1677ff;
}

// ── 模拟节点 ──
.mock-nodes-row {
  display: flex;
  align-items: center;
  gap: 0;
}

.mock-link-line {
  width: 32px;
  height: 2px;
  background: #d9d9d9;
  flex-shrink: 0;
}

.mock-node {
  position: relative;
  width: 180px;
  border-radius: 10px;
  background: #fff;
  border: 1px solid #e8e8e8;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  flex-shrink: 0;

  &.mock-node-active {
    border-color: #1677ff;
  }
}

.mock-node-hint {
  position: absolute;
  left: 6px;
  right: 6px;
  top: -15px;
  height: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #f2f2f2;
  border-bottom: 0;
  border-radius: 10px 10px 0 0;
  background: #f7f8fa;
  color: #aaa;
  font-size: 8px;
  font-weight: 500;
  white-space: nowrap;
}

.mock-node-body {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 12px;
}

.mock-node-body-branch {
  padding-bottom: 4px;
}

.mock-node-body-dim {
  opacity: 0.4;
}

.mock-node-icon {
  width: 24px;
  height: 24px;
  border-radius: 6px;
  background: #1677ff;
  opacity: 0.8;
  flex-shrink: 0;
}

.mock-node-name {
  font-size: 12px;
  font-weight: 600;
  color: #262626;
  flex: 1;
}

.mock-node-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  border: 1px solid #d9d9d9;
  background: #f5f5f5;
  flex-shrink: 0;
}

.mock-plus-btn {
  position: absolute;
  right: -8px;
  top: 50%;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background:
    url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 18 18'%3E%3Crect x='3' y='8' width='12' height='2' rx='1' fill='white'/%3E%3Crect x='8' y='3' width='2' height='12' rx='1' fill='white'/%3E%3C/svg%3E")
      no-repeat center / 16px 16px,
    #1677ff;
  transform: translateY(-50%);

  &.mock-plus-btn-sm {
    width: 13px;
    height: 13px;
    background-size: 13px 13px;
  }
}

// ── 分支节点演示 ──
.mock-branch-demo {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}

.mock-branch-handles {
  display: flex;
  flex-direction: column;
  gap: 0;
  padding: 0 12px 10px;
}

.mock-branch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 0;
  position: relative;
}

.mock-branch-label {
  font-size: 11px;
  font-weight: 600;
  color: #8c8c8c;
}

.mock-plus-btn-sm {
  right: -6px;
  top: 50%;
}

// ── 模拟节点库 ──
.mock-library {
  width: 200px;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.12);
  overflow: hidden;
}

.mock-library-header {
  padding: 8px 14px;
  background: #fafafa;
  border-bottom: 1px solid #f0f0f0;
  font-size: 12px;
  font-weight: 600;
  color: #8c8c8c;
}

.mock-library-item {
  padding: 8px 14px;
  font-size: 13px;
  color: #434343;
  cursor: default;

  &.mock-library-item-active {
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
