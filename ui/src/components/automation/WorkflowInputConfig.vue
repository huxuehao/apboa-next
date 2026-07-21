/**
 * Workflow 输入配置组件
 * 用于配置工作流的开始节点参数和自定义变量
 *
 * @component
 */
<script setup lang="ts">
/* eslint-disable @typescript-eslint/no-explicit-any */
import { computed } from 'vue'

interface ParamItem {
  name: string
  type: string
  value: unknown
  required: boolean
}

const params = defineModel<ParamItem[]>('params', { default: [] })
const variables = defineModel<Record<string, unknown>>('variables', { default: {} })

const hasParams = computed(() => params.value.length > 0)
const hasVariables = computed(() => Object.keys(variables.value).length > 0)
const hasAnyConfig = computed(() => hasParams.value || hasVariables.value)

/**
 * 根据类型获取输入组件类型
 */
function getInputType(type: string): string {
  switch (type) {
    case 'Boolean':
      return 'switch'
    case 'Long':
    case 'Integer':
    case 'Float':
    case 'Double':
      return 'number'
    case 'Array':
    case 'Object':
      return 'textarea'
    default:
      return 'input'
  }
}

/**
 * 更新指定索引的参数值
 * 通过替换数组元素触发 defineModel 的响应式更新
 */
function setParamValue(idx: number, val: unknown) {
  params.value = params.value.map((p, i) =>
    i === idx ? { ...p, value: val } : p
  )
}

// /**
//  * 格式化变量名用于显示
//  */
// function formatVariableName(name: string): string {
//   return name.replace(/_/g, ' ').replace(/\b\w/g, l => l.toUpperCase())
// }
</script>

<template>
  <div class="workflow-input-config">
    <!-- 无配置提示 -->
    <div v-if="!hasAnyConfig" class="config-empty">
      <AEmpty description="该工作流暂无可配置的参数或变量" />
    </div>

    <!-- 开始节点参数 -->
    <div v-if="hasParams" class="config-section">
      <div class="param-list">
        <div v-for="(param, idx) in params" :key="param.name" class="param-item">
          <div class="param-header">
            <span class="param-name">{{ param.name }}</span>
            <span class="param-type">（{{ param.type }}）</span>
            <span v-if="param.required" class="param-required">必填</span>
          </div>

          <!-- String 类型 -->
          <AInput
            v-if="getInputType(param.type) === 'input'"
            :value="String(param.value ?? '')"
            @update:value="(v: any) => setParamValue(idx, v)"
            :placeholder="`请输入 ${param.name}`"
          />

          <!-- Number 类型 -->
          <AInputNumber
            v-else-if="getInputType(param.type) === 'number'"
            :value="Number(param.value ?? 0)"
            @update:value="(v: any) => setParamValue(idx, v)"
            :placeholder="`请输入 ${param.name}`"
            style="width: 100%"
          />

          <!-- Boolean 类型 -->
          <ASwitch
            v-else-if="getInputType(param.type) === 'switch'"
            :checked="Boolean(param.value ?? false)"
            @update:checked="(v: any) => setParamValue(idx, v)"
          />

          <!-- Array/Object 类型 -->
          <ATextarea
            v-else
            :value="String(param.value ?? '')"
            @update:value="(v: any) => setParamValue(idx, v)"
            :placeholder="`请输入 ${param.name} (JSON格式)`"
            :rows="3"
          />
        </div>
      </div>
    </div>

    <!-- 自定义变量 -->
<!--    <div v-if="hasVariables" class="config-section">-->
<!--      <div class="section-header">-->
<!--        <span class="section-title">自定义变量</span>-->
<!--        <span class="section-desc">工作流中定义的全局变量</span>-->
<!--      </div>-->

<!--      <div class="variable-list">-->
<!--        <div v-for="(_, name) in variables" :key="name" class="variable-item">-->
<!--          <div class="variable-header">-->
<!--            <span class="variable-name">{{ formatVariableName(String(name)) }}</span>-->
<!--            <code class="variable-key">{{ name }}</code>-->
<!--          </div>-->
<!--          <AInput-->
<!--            v-model:value="variables[name]"-->
<!--            :placeholder="`设置 ${String(name)} 的值`"-->
<!--          />-->
<!--        </div>-->
<!--      </div>-->
<!--    </div>-->
  </div>
</template>

<style scoped lang="scss">
.workflow-input-config {
  .config-empty {
    padding: 24px 0;
  }

  .config-section {
    margin-bottom: 24px;

    &:last-child {
      margin-bottom: 0;
    }
  }

  .section-header {
    margin-bottom: 16px;
    padding-bottom: 12px;
  }

  .section-title {
    font-size: 16px;
    font-weight: 500;
    color: #1a1a1a;
    margin-right: 8px;
  }

  .section-desc {
    font-size: 12px;
    color: #999;
  }

  .param-list,
  .variable-list {
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  .param-item,
  .variable-item {
    border-radius: 6px;
  }

  .param-header,
  .variable-header {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 8px;
  }

  .param-name,
  .variable-name,
  .param-type {
    font-size: var(--font-size-sm, 12px);
    color: var(--color-text-secondary, #999);
  }

  .param-required {
    font-size: 12px;
    color: #ff4d4f;
    margin-left: auto;
  }

  .variable-key {
    font-size: 12px;
    color: #666;
    background-color: #f0f0f0;
    padding: 2px 6px;
    border-radius: 3px;
    font-family: monospace;
  }
}
</style>
