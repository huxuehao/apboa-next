<script setup lang="ts">
/**
 * 服务监控组件 — 展示执行节点和消息服务的运行状态
 *
 * @author huxuehao
 */
import { ref, onMounted, onUnmounted } from 'vue'
import * as heartbeatApi from '@/api/heartbeat'
import type { NodeStatusVO, ServiceStatusInfo, WebSocketNodeVO } from '@/types'

const nodes = ref<NodeStatusVO[]>([])
const loading = ref(false)
let overviewTimer: ReturnType<typeof setInterval> | null = null

/** 当前激活的Tab */
const activeTab = ref('node')

/** 消息服务节点列表 */
const wsNodes = ref<WebSocketNodeVO[]>([])
const wsLoading = ref(false)

/** 服务类型中文映射 */
const serviceLabelMap: Record<string, string> = {
  RUNTIME: '运行时',
  FILE: '文件同步',
  PROXY: 'Shell代理',
}

/** 服务显示顺序（固定排列） */
const serviceOrder = ['RUNTIME', 'FILE', 'PROXY']

/** 按固定顺序排列服务列表 */
function sortedServices(services: ServiceStatusInfo[]): ServiceStatusInfo[] {
  return serviceOrder
    .map((type) => services.find((s) => s.serviceType === type))
    .filter((s): s is ServiceStatusInfo => !!s)
}

/** 节点监控总览（执行节点 + WebSocket 节点合一，替代两个独立轮询） */
async function fetchOverview() {
  loading.value = true
  wsLoading.value = true
  try {
    const res = await heartbeatApi.overview()
    nodes.value = res.data.data?.nodes || []
    wsNodes.value = res.data.data?.websocketNodes || []
  } catch {
    // 统一由 request.ts 响应拦截器处理错误提示
  } finally {
    loading.value = false
    wsLoading.value = false
  }
}

/** 计算连续运行时长 */
function formatUptime(startedAt: string | null): string {
  if (!startedAt) return '--'
  const start = new Date(startedAt).getTime()
  const diff = Date.now() - start
  if (diff < 0) return '--'
  const days = Math.floor(diff / 86400000)
  const hours = Math.floor((diff % 86400000) / 3600000)
  const minutes = Math.floor((diff % 3600000) / 60000)
  if (days > 0) return `${days}天${hours}小时`
  if (hours > 0) return `${hours}小时${minutes}分钟`
  return `${minutes}分钟`
}

/** 获取头像文本 */
function getAvatarText(text: string) {
  return text.charAt(0).toUpperCase()
}


onMounted(() => {
  fetchOverview()
  overviewTimer = setInterval(fetchOverview, 30000)
})

onUnmounted(() => {
  if (overviewTimer) {
    clearInterval(overviewTimer)
    overviewTimer = null
  }
})
</script>

<template>
  <div>
    <h2 class="settings-page-title">服务监控</h2>

    <ATabs v-model:activeKey="activeTab">
      <!-- 执行节点 -->
      <ATabPane key="node" tab="执行节点">
        <div class="nodes-desc mb-lg">
          监控所有执行节点的运行状态，每个节点包含运行时、文件同步、Shell代理三个核心服务
        </div>

        <ApboaSpin :spinning="loading">
          <div v-if="nodes.length === 0" class="empty-state">
            <AEmpty description="暂无执行节点，请确保 file/proxy/runtime 服务已启动并配置心跳上报" />
          </div>

          <div v-else class="node-list">
            <div
              v-for="node in nodes"
              :key="node.nodeId"
              class="node-card"
              :class="`node-status-${node.nodeStatus.toLowerCase()}`"
            >
              <!-- 节点头部 -->
              <div class="node-header">
                <div class="node-title-row">
                  <div class="node-avatar">
                    <span class="avatar-text">{{ (node.nodeId || '?').charAt(0).toUpperCase() }}</span>
                  </div>
                  <div class="node-info">
                    <span class="node-id" :title="node.nodeId">{{ node.nodeId }}</span>
                    <span class="node-host" :title="`${node.hostname} / ${node.ip}`">
                      {{ node.hostname }}
                      <span class="node-ip">{{ node.ip }}</span>
                    </span>
                  </div>
                  <div class="node-status-badge" :class="`badge-${node.nodeStatus.toLowerCase()}`">
                    <span class="status-dot"></span>
                    {{ node.nodeStatus === 'HEALTHY' ? '正常' : node.nodeStatus === 'DEGRADED' ? '部分异常' : '离线' }}
                  </div>
                </div>
              </div>

              <!-- 服务状态列表 -->
              <div class="services-row">
                <div
                  v-for="svc in sortedServices(node.services)"
                  :key="svc.serviceType"
                  class="service-item"
                  :class="{ 'service-down': svc.status === 'DOWN' }"
                >
                  <div class="service-indicator">
                    <span class="indicator-dot" :class="svc.status === 'UP' ? 'dot-up' : 'dot-down'"></span>
                  </div>
                  <div class="service-detail">
                    <span class="service-name">{{ serviceLabelMap[svc.serviceType] || svc.serviceType }}</span>
                    <span class="service-meta">
                      <template v-if="svc.port">{{ svc.port }}</template>
                      <template v-if="svc.status === 'UP'">
                        <span>
                          <span> · </span><span :title="`运行 ${svc.startedAt}`">运行 {{ formatUptime(svc.startedAt) }}</span>
                        </span>
                      </template>
                      <template v-else>
                        <span>
                          <span> · </span><span :title="`最后心跳 ${svc.lastHeartbeat}`">最后心跳 {{ svc.lastHeartbeat || '--' }}</span>
                        </span>
                      </template>
                    </span>
                  </div>
                </div>

                <!-- 缺少的服务占位 -->
                <div
                  v-for="missing in serviceOrder.filter(t => !node.services.some(s => s.serviceType === t))"
                  :key="missing"
                  class="service-item service-missing"
                >
                  <div class="service-indicator">
                    <span class="indicator-dot dot-missing"></span>
                  </div>
                  <div class="service-detail">
                    <span class="service-name">{{ serviceLabelMap[missing] || missing }}</span>
                    <span class="service-meta">未上报</span>
                  </div>
                </div>
              </div>

              <!-- 节点底部信息 -->
              <div class="node-footer">
                <span class="footer-item">首次发现：{{ node.firstSeenAt }}</span>
                <span class="footer-item">最近更新：{{ node.lastUpdatedAt }}</span>
              </div>
            </div>
          </div>
        </ApboaSpin>
      </ATabPane>

      <!-- 消息服务 -->
      <ATabPane key="websocket" tab="消息服务">
        <div class="tab-content">
          <div class="nodes-desc mb-lg">
            监控所有消息服务运行状态，消息服务是实现 Websocket 通知的核心服务
          </div>

          <ApboaSpin :spinning="wsLoading">
            <div v-if="wsNodes.length === 0" class="empty-state">
              <AEmpty description="暂无消息服务节点" />
            </div>

            <div v-else class="node-list">
              <div
                v-for="node in wsNodes"
                :key="node.nodeId"
                class="node-card"
              >
                <!-- 节点头部 -->
                <div class="node-header">
                  <div class="node-title-row">
                    <div class="node-avatar">
                      <span class="avatar-text">{{ getAvatarText(node.nodeId) }}</span>
                    </div>
                    <div class="node-info">
                      <span class="node-id" :title="node.nodeId">{{ node.nodeId }}</span>
                      <span class="node-host" :title="`${node.hostname} / ${node.ip}`">
                        {{ node.hostname }}
                        <span class="node-ip">{{ node.ip }}{{ node.port ? ' :' + node.port : '' }}</span>
                      </span>
                    </div>
                    <div class="node-status-badge" :class="node.status === 'UP' ? 'badge-healthy' : 'badge-down'">
                      <span class="status-dot"></span>
                      {{ node.status === 'UP' ? '运行中' : '已离线' }}
                    </div>
                  </div>
                </div>

                <!-- 运行信息 -->
                <div class="ws-run-info">
                  <span v-if="node.status === 'UP'" :title="`运行 ${node.startedAt}`">运行 {{ formatUptime(node.startedAt) }}</span>
                  <span v-else :title="`最后心跳 ${node.lastHeartbeat}`">最后心跳 {{ node.lastHeartbeat || '--' }}</span>
                </div>

                <!-- 节点底部信息 -->
                <div class="node-footer">
                  <span class="footer-item">首次发现：{{ node.firstSeenAt }}</span>
                  <span class="footer-item">最近更新：{{ node.lastUpdatedAt }}</span>
                </div>
              </div>
            </div>
          </ApboaSpin>
        </div>
      </ATabPane>
    </ATabs>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/modules/_settings.scss' as *;

// ========== 页面描述 ==========
.nodes-desc {
  font-size: 14px;
  color: var(--text-secondary, #666);
}

.node-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-height: calc(100vh - 260px);
  overflow-y: auto;
}

// ========== 节点卡片 ==========
.node-card {
  padding: 20px 24px;
  background: var(--color-bg-primary, #fff);
  border: 1px solid var(--color-border-light, #eee);
  border-radius: 12px;
  transition: box-shadow 0.25s ease, border-color 0.25s ease;

  &:hover {
    box-shadow: 0 2px 16px rgba(0, 0, 0, 0.06);
  }
}

// ========== 节点头部 ==========
.node-header {
  margin-bottom: 16px;
}

.node-title-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.node-avatar {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  background: linear-gradient(135deg, #ede9fe 0%, #e0e7ff 100%);
}

.avatar-text {
  font-size: 18px;
  font-weight: 700;
  color: #6d6afe;
}

.node-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.node-id {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary, #1a1a1a);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-host {
  font-size: 12px;
  color: var(--text-tertiary, #aaa);
  font-family: "SF Mono", "Cascadia Code", "Fira Code", monospace;
}

.node-ip {
  margin-left: 4px;
  color: var(--text-tertiary, #bbb);
}

// ========== 节点状态徽标 ==========
.node-status-badge {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 500;

  &.badge-healthy {
    background: rgba(82, 196, 26, 0.08);
    color: #389e0d;
  }

  &.badge-degraded {
    background: rgba(250, 173, 20, 0.08);
    color: #d48806;
  }

  &.badge-down {
    background: rgba(255, 77, 79, 0.08);
    color: #cf1322;
  }

  .status-dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    display: inline-block;
  }

  &.badge-healthy .status-dot {
    background: #52c41a;
    animation: pulse-green 2s ease-in-out infinite;
  }

  &.badge-degraded .status-dot {
    background: #faad14;
    animation: pulse-yellow 2s ease-in-out infinite;
  }

  &.badge-down .status-dot {
    background: #ff4d4f;
    animation: pulse-red 1.5s ease-in-out infinite;
  }
}

// ========== 服务状态行 ==========
.services-row {
  display: flex;
  gap: 12px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}

.service-item {
  flex: 1;
  min-width: 150px;
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 12px;
  background: var(--color-bg-base, #f7f7f7);
  border-radius: 8px;
  transition: background 0.2s ease;

  &.service-down {
    background: rgba(255, 77, 79, 0.04);
  }

  &.service-missing {
    opacity: 0.5;
  }
}

.service-indicator {
  flex-shrink: 0;
  padding-top: 2px;
}

.indicator-dot {
  display: block;
  width: 8px;
  height: 8px;
  border-radius: 50%;

  &.dot-up {
    background: #52c41a;
    box-shadow: 0 0 0 0 rgba(82, 196, 26, 0.4);
    animation: dot-pulse 2s ease-in-out infinite;
  }

  &.dot-down {
    background: #ff4d4f;
    animation: dot-shake 0.6s ease-in-out infinite;
  }

  &.dot-missing {
    background: #d9d9d9;
  }
}

.service-detail {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.service-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary, #1a1a1a);
}

.service-meta {
  font-size: 11px;
  color: var(--text-tertiary, #999);
  font-family: "SF Mono", "Cascadia Code", "Fira Code", monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

// ========== 节点底部 ==========
.node-footer {
  display: flex;
  justify-content: space-between;
}

.footer-item {
  font-size: 11px;
  color: var(--text-tertiary, #bbb);
  font-family: "SF Mono", "Cascadia Code", "Fira Code", monospace;
}

// ========== 空状态 ==========
.empty-state {
  padding-top: 60px;
}

// ========== 动画 ==========
@keyframes pulse-green {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

@keyframes pulse-yellow {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

@keyframes pulse-red {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.7; transform: scale(1.3); }
}

@keyframes dot-pulse {
  0% { box-shadow: 0 0 0 0 rgba(82, 196, 26, 0.4); }
  70% { box-shadow: 0 0 0 5px rgba(82, 196, 26, 0); }
  100% { box-shadow: 0 0 0 0 rgba(82, 196, 26, 0); }
}

@keyframes dot-shake {
  0% { box-shadow: 0 0 0 0 rgba(196, 26, 26, 0.4); }
  70% { box-shadow: 0 0 0 5px rgba(196, 26, 26, 0); }
  100% { box-shadow: 0 0 0 0 rgba(196, 26, 26, 0); }
}

// ========== 消息服务运行信息 ==========
.ws-run-info {
  margin-bottom: 14px;
  font-size: 13px;
  color: var(--text-secondary, #666);
  text-align: right;
}

// ========== 消息服务 ==========
.tab-content {
  max-width: 720px;
}

.text-tertiary {
  color: var(--text-tertiary, #999);
  font-size: 13px;
}
</style>
