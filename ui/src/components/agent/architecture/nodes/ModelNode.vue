/**
 * 模型节点组件
 * 展示模型配置的详细信息
 *
 * @author huxuehao
 */
<script setup lang="ts">
import { computed } from 'vue'
import { Handle, Position } from '@vue-flow/core'
import {
  AudioOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  SoundOutlined,
  ThunderboltOutlined
} from '@ant-design/icons-vue'
import type { ModelNodeData } from '../types'

/**
 * Props定义
 */
const props = defineProps<{
  data: ModelNodeData
}>()

const roleMeta = computed(() => ({
  LLM: {
    title: '对话生成模型',
    capability: '文本 / 多模态 → 文本',
    emptyText: '未配置对话生成模型',
    color: '#fa8c16',
    background: '#fff7e6',
    border: '#ffe7ba',
    shadow: 'rgba(250, 140, 22, 0.3)'
  },
  ASR: {
    title: '语音识别模型',
    capability: '音频 → 文字',
    emptyText: '未配置语音识别模型',
    color: '#1677ff',
    background: '#e6f4ff',
    border: '#bae0ff',
    shadow: 'rgba(22, 119, 255, 0.3)'
  },
  TTS: {
    title: '语音合成模型',
    capability: '文字 → 音频',
    emptyText: '未配置语音合成模型',
    color: '#13a8a8',
    background: '#e6fffb',
    border: '#b5f5ec',
    shadow: 'rgba(19, 168, 168, 0.3)'
  }
}[props.data.role]))

/**
 * 获取模型参数（考虑覆盖）
 */
const modelParams = computed(() => {
  if (props.data.role !== 'LLM') return null

  const config = props.data.modelConfig
  const override = props.data.paramsOverride

  if (!config) return null

  // 如果有覆盖参数，使用覆盖的值，否则使用模型默认值
  return {
    temperature: override?.temperature ?? config.temperature,
    topP: override?.topP ?? config.topP,
    topK: override?.topK ?? config.topK,
    maxTokens: override?.maxTokens ?? config.maxTokens,
    streaming: override?.streaming ?? config.streaming,
    thinking: override?.thinking ?? config.thinking
  }
})

/**
 * 是否有参数覆盖
 */
const hasOverride = computed(() => {
  if (props.data.role === 'TTS') {
    return typeof props.data.paramsOverride?.voice === 'string'
      && props.data.paramsOverride.voice.trim().length > 0
  }
  return props.data.role === 'LLM'
    && !!props.data.paramsOverride
    && Object.keys(props.data.paramsOverride).length > 0
})

/**
 * TTS最终音色：Agent覆盖 > 模型默认 > 服务默认
 */
const ttsVoice = computed(() => {
  const overrideVoice = props.data.paramsOverride?.voice
  if (typeof overrideVoice === 'string' && overrideVoice.trim()) {
    return { value: overrideVoice.trim(), source: 'Agent覆盖' }
  }

  const bodyParams = props.data.modelConfig?.extendConfig?.bodyParams
  const defaultVoice = bodyParams && typeof bodyParams === 'object'
    ? (bodyParams as Record<string, unknown>).voice
    : undefined
  if (typeof defaultVoice === 'string' && defaultVoice.trim()) {
    return { value: defaultVoice.trim(), source: '模型默认' }
  }

  return { value: '服务默认', source: '未指定' }
})
</script>

<template>
  <div
    class="model-node"
    :class="{ disabled: data.modelConfig?.enabled === false || data.loadFailed }"
    :style="{
      '--node-color': roleMeta.color,
      '--node-bg-color': roleMeta.background,
      '--node-border-color': roleMeta.border,
      '--node-shadow-color': roleMeta.shadow
    }"
  >
    <Handle type="target" :position="Position.Top" id="top" />
    <Handle type="target" :position="Position.Right" id="right" />
    <Handle type="target" :position="Position.Bottom" id="bottom" />
    <Handle type="target" :position="Position.Left" id="left" />

    <div class="node-header">
      <div class="node-avatar">
        <ThunderboltOutlined v-if="data.role === 'LLM'" />
        <AudioOutlined v-else-if="data.role === 'ASR'" />
        <SoundOutlined v-else />
      </div>
      <div class="node-title">
        <div class="node-name">{{ roleMeta.title }}</div>
        <div class="node-provider" v-if="data.provider">
          {{ data.provider.name }}
        </div>
      </div>
      <ATag v-if="data.modelConfig?.enabled === false" :bordered="false" color="default" size="small">已禁用</ATag>
      <ATag v-if="hasOverride" :bordered="false" color="orange" size="small">已覆盖</ATag>
    </div>

    <template v-if="data.modelConfig">
      <div class="node-model-info">
        <div class="info-item">
          <span class="info-label">模型ID:</span>
          <span class="info-value">{{ data.modelConfig.modelId }}</span>
        </div>
        <div class="info-item">
          <span class="info-label">名称:</span>
          <span class="info-value">{{ data.modelConfig.name }}</span>
        </div>
      </div>

      <div class="node-capability">{{ roleMeta.capability }}</div>

      <div class="node-params" v-if="modelParams">
        <div class="params-row">
          <div class="param-item">
            <span class="param-label">Temperature</span>
            <span class="param-value">{{ modelParams.temperature }}</span>
          </div>
          <div class="param-item">
            <span class="param-label">Top P</span>
            <span class="param-value">{{ modelParams.topP }}</span>
          </div>
        </div>
        <div class="params-row">
          <div class="param-item">
            <span class="param-label">Top K</span>
            <span class="param-value">{{ modelParams.topK }}</span>
          </div>
          <div class="param-item">
            <span class="param-label">Max Tokens</span>
            <span class="param-value">{{ modelParams.maxTokens }}</span>
          </div>
        </div>
      </div>

      <div class="node-flags" v-if="modelParams">
        <div class="flag-item">
          <CheckCircleOutlined v-if="modelParams.streaming" class="flag-icon enabled" />
          <CloseCircleOutlined v-else class="flag-icon disabled" />
          <span>流式输出</span>
        </div>
        <div class="flag-item">
          <CheckCircleOutlined v-if="modelParams.thinking" class="flag-icon enabled" />
          <CloseCircleOutlined v-else class="flag-icon disabled" />
          <span>思维链</span>
        </div>
      </div>

      <div class="node-voice" v-if="data.role === 'TTS'">
        <div class="voice-label">生效音色</div>
        <div class="voice-value" :title="ttsVoice.value">{{ ttsVoice.value }}</div>
        <ATag :bordered="false" :color="hasOverride ? 'orange' : 'cyan'" size="small">
          {{ ttsVoice.source }}
        </ATag>
      </div>
    </template>

    <div class="node-empty" v-else>
      {{ data.loadFailed ? '配置详情读取失败' : roleMeta.emptyText }}
    </div>
  </div>
</template>

<style scoped lang="scss">
.model-node {
  width: 280px;
  padding: 14px;
  background: white;
  border: 1px solid var(--node-border-color);
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  transition: all 0.2s ease;

  &:hover {
    box-shadow: 0 4px 12px var(--node-shadow-color);
  }

  &.disabled {
    opacity: 0.68;
  }

  .node-header {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 12px;
    padding-bottom: 10px;
    border-bottom: 1px solid #f0f0f0;

    .node-avatar {
      width: 36px;
      height: 36px;
      display: flex;
      align-items: center;
      justify-content: center;
      background: var(--node-bg-color);
      color: var(--node-color);
      border-radius: 10px;
      font-size: 18px;
    }

    .node-title {
      flex: 1;
      min-width: 0;

      .node-name {
        font-size: 14px;
        font-weight: 600;
        color: #262626;
      }

      .node-provider {
        font-size: 11px;
        color: #8c8c8c;
        margin-top: 2px;
      }
    }

    :deep(.ant-tag) {
      margin: 0;
      font-size: 10px;
    }
  }

  .node-model-info {
    background: #fafafa;
    border-radius: 8px;
    padding: 10px;
    margin-bottom: 10px;

    .info-item {
      display: flex;
      align-items: center;
      font-size: 12px;
      margin-bottom: 4px;

      &:last-child {
        margin-bottom: 0;
      }

      .info-label {
        color: #8c8c8c;
        width: 50px;
        flex-shrink: 0;
      }

      .info-value {
        color: #262626;
        font-family: 'Monaco', 'Menlo', monospace;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
    }
  }

  .node-capability {
    margin-bottom: 10px;
    color: var(--node-color);
    font-size: 11px;
    font-weight: 500;
    text-align: center;
  }

  .node-params {
    margin-bottom: 10px;

    .params-row {
      display: flex;
      gap: 10px;
      margin-bottom: 6px;

      &:last-child {
        margin-bottom: 0;
      }
    }

    .param-item {
      flex: 1;
      display: flex;
      flex-direction: column;
      background: #f5f5f5;
      border-radius: 6px;
      padding: 6px 8px;

      .param-label {
        font-size: 10px;
        color: #8c8c8c;
      }

      .param-value {
        font-size: 12px;
        font-weight: 500;
        color: #262626;
        font-family: 'Monaco', 'Menlo', monospace;
      }
    }
  }

  .node-flags {
    display: flex;
    gap: 16px;

    .flag-item {
      display: flex;
      align-items: center;
      gap: 4px;
      font-size: 11px;
      color: #595959;

      .flag-icon {
        font-size: 14px;

        &.enabled {
          color: #52c41a;
        }

        &.disabled {
          color: #d9d9d9;
        }
      }
    }
  }

  .node-voice {
    display: grid;
    grid-template-columns: auto minmax(0, 1fr) auto;
    gap: 8px;
    align-items: center;
    padding: 9px 10px;
    border-radius: 8px;
    background: #fafafa;
    font-size: 11px;

    .voice-label {
      color: #8c8c8c;
    }

    .voice-value {
      overflow: hidden;
      color: #262626;
      font-family: 'Monaco', 'Menlo', monospace;
      font-weight: 500;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    :deep(.ant-tag) {
      margin: 0;
      font-size: 10px;
    }
  }

  .node-empty {
    text-align: center;
    color: #bfbfbf;
    font-size: 12px;
    padding: 20px 0;
  }

  :deep(.vue-flow__handle) {
    width: 8px;
    height: 8px;
    background: transparent;
    border: none;
    opacity: 0;
  }
}
</style>
