/**
 * 扩展配置编辑器
 * 用于编辑 headers、queryParams、bodyParams、思考参数(thinkingParams，注入请求体)
 *
 * @author huxuehao
 */
<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import { MinusCircleOutlined, PlusOutlined } from '@ant-design/icons-vue'

export interface ExtendConfigData {
  headers: Record<string, string>
  queryParams: Record<string, string>
  bodyParams: Record<string, unknown>
  /** 固定系统消息 */
  fixedSystemMessage?: boolean
  /** 思考参数：开/关分别注入的请求体参数（数据驱动，参数名由模型自己声明） */
  thinkingParams?: { on: Record<string, unknown>; off: Record<string, unknown> }
}

interface KeyValueItem {
  key: string
  value: string
}

interface BodyParamItem {
  key: string
  value: string
  valueType: 'string' | 'number' | 'boolean' | 'json'
}

const props = withDefaults(
  defineProps<{
    modelValue: ExtendConfigData | null
    /** 是否紧凑模式（用于 AgentFormModel 等嵌套场景） */
    compact?: boolean
    /** 是否显示固定系统消息配置项（仅 OpenAI 供应商显示） */
    showFixedSystemMessage?: boolean
    /** 是否显示思考参数配置（模型 thinking=true 且走 bodyParams 思考的供应商，如 OPEN_AI） */
    showThinkingParams?: boolean
  }>(),
  { compact: false, showFixedSystemMessage: false, showThinkingParams: false }
)

const emit = defineEmits<{
  'update:modelValue': [value: ExtendConfigData | null]
}>()

/** 内部编辑结构 - 使用 ref 以便可编辑 */
const headersList = ref<KeyValueItem[]>([])
const queryParamsList = ref<KeyValueItem[]>([])
const bodyParamsList = ref<BodyParamItem[]>([])
const thinkingOnList = ref<BodyParamItem[]>([])
const thinkingOffList = ref<BodyParamItem[]>([])
const fixedSystemMessage = ref(false)

// 标记内部编辑触发的 emit：避免 emit→父级更新 modelValue→watch 回灌列表，
// 把用户刚选的值类型（如"对象"未填合法 JSON 时会被按值反推回"字符串"）冲掉
let isInternalUpdate = false

watch(
  () => props.modelValue,
  (val) => {
    if (isInternalUpdate) {
      isInternalUpdate = false
      return
    }
    headersList.value = recordToKeyValueList(val?.headers)
    queryParamsList.value = recordToKeyValueList(val?.queryParams)
    bodyParamsList.value = recordToBodyParamList(val?.bodyParams)
    thinkingOnList.value = recordToBodyParamList(val?.thinkingParams?.on)
    thinkingOffList.value = recordToBodyParamList(val?.thinkingParams?.off)
    fixedSystemMessage.value = val?.fixedSystemMessage ?? false
  },
  { immediate: true }
)

function syncHeaders() {
  emitUpdate('headers', keyValueListToRecord(headersList.value))
}

function syncQueryParams() {
  emitUpdate('queryParams', keyValueListToRecord(queryParamsList.value))
}

function syncBodyParams() {
  emitUpdate('bodyParams', bodyParamListToRecord(bodyParamsList.value))
}

function recordToKeyValueList(record: Record<string, string> | undefined): KeyValueItem[] {
  if (!record || Object.keys(record).length === 0) return []
  return Object.entries(record).map(([key, value]) => ({ key, value }))
}

function keyValueListToRecord(list: KeyValueItem[]): Record<string, string> {
  const record: Record<string, string> = {}
  list.forEach(({ key, value }) => {
    record[key.trim()] = value ?? ''
  })
  return Object.keys(record).length > 0 ? record : {}
}

function recordToBodyParamList(record: Record<string, unknown> | undefined): BodyParamItem[] {
  if (!record || Object.keys(record).length === 0) return []
  return Object.entries(record).map(([key, value]) => {
    let valueType: BodyParamItem['valueType']
    let text: string
    if (typeof value === 'number') {
      valueType = 'number'; text = String(value)
    } else if (typeof value === 'boolean') {
      valueType = 'boolean'; text = String(value)
    } else if (value !== null && typeof value === 'object') {
      // 对象/数组回显为 JSON 字符串（否则 String(value) 会变成 [object Object]）
      valueType = 'json'; text = JSON.stringify(value)
    } else {
      valueType = 'string'; text = String(value)
    }
    return { key, value: text, valueType }
  })
}

function bodyParamListToRecord(list: BodyParamItem[]): Record<string, unknown> {
  const record: Record<string, unknown> = {}
  list.forEach(({ key, value, valueType }) => {
    let v: unknown
    if (valueType === 'number') {
      v = Number(value)
    } else if (valueType === 'boolean') {
      v = value === 'true'
    } else if (valueType === 'json') {
      // 解析失败保留原字符串，避免丢数据
      try { v = JSON.parse(value) } catch { v = value }
    } else {
      v = value
    }
    record[key.trim()] = v
  })
  return Object.keys(record).length > 0 ? record : {}
}

function hasThinkingParams(tp: ExtendConfigData['thinkingParams']): boolean {
  return !!tp && (Object.keys(tp.on || {}).length > 0 || Object.keys(tp.off || {}).length > 0)
}

function computeHasData(next: ExtendConfigData): boolean {
  return Object.keys(next.headers).length > 0
    || Object.keys(next.queryParams).length > 0
    || Object.keys(next.bodyParams).length > 0
    || next.fixedSystemMessage === true
    || hasThinkingParams(next.thinkingParams)
}

/** 统一出口：标记为内部更新后再 emit，供 watch 跳过回灌；nextTick 兜底重置防标记卡住 */
function doEmit(val: ExtendConfigData | null) {
  isInternalUpdate = true
  emit('update:modelValue', val)
  nextTick(() => { isInternalUpdate = false })
}

function emitUpdate(field: keyof ExtendConfigData, value: Record<string, unknown> | Record<string, string>) {
  const current = props.modelValue || { headers: {}, queryParams: {}, bodyParams: {}, fixedSystemMessage: false }
  const next = { ...current, [field]: value, fixedSystemMessage: fixedSystemMessage.value }
  doEmit(computeHasData(next) ? next : null)
}

function syncFixedSystemMessage() {
  const current = props.modelValue || { headers: {}, queryParams: {}, bodyParams: {} }
  const next = { ...current, fixedSystemMessage: fixedSystemMessage.value }
  doEmit(computeHasData(next) ? next : null)
}

/** 思考参数（开/关两组）同步：两组都空则整体清为 undefined */
function syncThinkingParams() {
  const current = props.modelValue || { headers: {}, queryParams: {}, bodyParams: {}, fixedSystemMessage: false }
  const on = bodyParamListToRecord(thinkingOnList.value)
  const off = bodyParamListToRecord(thinkingOffList.value)
  const tp = (Object.keys(on).length > 0 || Object.keys(off).length > 0) ? { on, off } : undefined
  const next: ExtendConfigData = { ...current, fixedSystemMessage: fixedSystemMessage.value, thinkingParams: tp }
  doEmit(computeHasData(next) ? next : null)
}

function addRow(field: 'headers' | 'queryParams' | 'bodyParams' | 'thinkingOn' | 'thinkingOff') {
  if (field === 'bodyParams') {
    bodyParamsList.value.push({ key: '', value: '', valueType: 'string' })
    syncBodyParams()
  } else if (field === 'thinkingOn') {
    thinkingOnList.value.push({ key: '', value: '', valueType: 'string' })
    syncThinkingParams()
  } else if (field === 'thinkingOff') {
    thinkingOffList.value.push({ key: '', value: '', valueType: 'string' })
    syncThinkingParams()
  } else if (field === 'headers') {
    headersList.value.push({ key: '', value: '' })
    syncHeaders()
  } else {
    queryParamsList.value.push({ key: '', value: '' })
    syncQueryParams()
  }
  // 不在此处 sync：新增空行会被 keyValueListToRecord 过滤，emit 后 watch 会覆盖列表
  // 用户填写后 blur 时会自动 sync
}

function removeRow(field: 'headers' | 'queryParams' | 'bodyParams' | 'thinkingOn' | 'thinkingOff', index: number) {
  if (field === 'bodyParams') {
    bodyParamsList.value.splice(index, 1)
    syncBodyParams()
  } else if (field === 'thinkingOn') {
    thinkingOnList.value.splice(index, 1)
    syncThinkingParams()
  } else if (field === 'thinkingOff') {
    thinkingOffList.value.splice(index, 1)
    syncThinkingParams()
  } else if (field === 'headers') {
    headersList.value.splice(index, 1)
    syncHeaders()
  } else {
    queryParamsList.value.splice(index, 1)
    syncQueryParams()
  }
}


const bodyValueTypeOptions = [
  { label: '字符串', value: 'string' },
  { label: '数字', value: 'number' },
  { label: '布尔', value: 'boolean' },
  { label: '对象', value: 'json' }
]

/** 值输入框占位提示 */
function valuePlaceholder(t: BodyParamItem['valueType']): string {
  if (t === 'boolean') return 'true / false'
  if (t === 'number') return '数字'
  if (t === 'json') return '如 {"type":"disabled"}'
  return '字符串值'
}
</script>

<template>
  <div class="extend-config-editor" :class="{ compact }">
    <div v-if="showFixedSystemMessage" class="fixed-system-message-row">
      <ASwitch v-model:checked="fixedSystemMessage" @change="syncFixedSystemMessage" />
      <div class="label-wrap">
        <span class="label">固定系统消息</span>
        <span class="text-placeholder text-xs">确保 system 消息始终在消息列表的最前面，以兼容 SGLang 等严格部署环境</span>
      </div>
    </div>
    <ACollapse :bordered="false" :default-active-key="[]">
      <ACollapsePanel key="headers" :header="`请求头 (${headersList.length})`">
        <div class="param-list">
          <div
            v-for="(item, index) in headersList"
            :key="`h-${index}`"
            class="param-row"
          >
            <AInput v-model:value="item.key" placeholder="Header 名称" class="param-key" @blur="syncHeaders" />
            <AInput v-model:value="item.value" placeholder="Header 值" class="param-value" @blur="syncHeaders" />
            <AButton type="text" danger size="small" html-type="button" class="param-remove" @click="removeRow('headers', index)">
              <MinusCircleOutlined />
            </AButton>
          </div>
          <AButton type="dashed" block size="small" html-type="button" @click="addRow('headers')">
            <PlusOutlined /> 添加请求头
          </AButton>
        </div>
      </ACollapsePanel>

      <ACollapsePanel key="queryParams" :header="`查询参数 (${queryParamsList.length})`">
        <div class="param-list">
          <div
            v-for="(item, index) in queryParamsList"
            :key="`q-${index}`"
            class="param-row"
          >
            <AInput v-model:value="item.key" placeholder="参数名" class="param-key" @blur="syncQueryParams" />
            <AInput v-model:value="item.value" placeholder="参数值" class="param-value" @blur="syncQueryParams" />
            <AButton type="text" danger size="small" html-type="button" class="param-remove" @click="removeRow('queryParams', index)">
              <MinusCircleOutlined />
            </AButton>
          </div>
          <AButton type="dashed" block size="small" html-type="button" @click="addRow('queryParams')">
            <PlusOutlined /> 添加查询参数
          </AButton>
        </div>
      </ACollapsePanel>

      <ACollapsePanel key="bodyParams" :header="`请求体参数 (${bodyParamsList.length})`">
        <div class="param-list">
          <div
            v-for="(item, index) in bodyParamsList"
            :key="`b-${index}`"
            class="param-row param-row-body"
          >
            <AInput v-model:value="item.key" placeholder="参数名" class="param-key" @blur="syncBodyParams" />
            <ASelect v-model:value="item.valueType" :options="bodyValueTypeOptions" class="param-type" @change="syncBodyParams" />
            <AInput
              v-model:value="item.value"
              :placeholder="valuePlaceholder(item.valueType)"
              class="param-value"
              @blur="syncBodyParams"
            />
            <AButton type="text" danger size="small" html-type="button" class="param-remove" @click="removeRow('bodyParams', index)">
              <MinusCircleOutlined />
            </AButton>
          </div>
          <AButton type="dashed" block size="small" html-type="button" @click="addRow('bodyParams')">
            <PlusOutlined /> 添加 Body 参数
          </AButton>
        </div>
      </ACollapsePanel>

      <ACollapsePanel v-if="showThinkingParams" key="thinkingOn" :header="`思考「开」时注入的请求体参数 (${thinkingOnList.length})`">
        <div class="param-list">
          <div class="thinking-hint text-placeholder text-xs">思考开启时并入请求体(body)。一般留空即可；Qwen 填 enable_thinking=true（布尔），DeepSeek 填 thinking=对象</div>
          <div
            v-for="(item, index) in thinkingOnList"
            :key="`ton-${index}`"
            class="param-row param-row-body"
          >
            <AInput v-model:value="item.key" placeholder="留空=开思考不额外传参" class="param-key" @blur="syncThinkingParams" />
            <ASelect v-model:value="item.valueType" :options="bodyValueTypeOptions" class="param-type" @change="syncThinkingParams" />
            <AInput
              v-model:value="item.value"
              :placeholder="valuePlaceholder(item.valueType)"
              class="param-value"
              @blur="syncThinkingParams"
            />
            <AButton type="text" danger size="small" html-type="button" class="param-remove" @click="removeRow('thinkingOn', index)">
              <MinusCircleOutlined />
            </AButton>
          </div>
          <AButton type="dashed" block size="small" html-type="button" @click="addRow('thinkingOn')">
            <PlusOutlined /> 添加参数
          </AButton>
        </div>
      </ACollapsePanel>

      <ACollapsePanel v-if="showThinkingParams" key="thinkingOff" :header="`思考「关」时注入的请求体参数 (${thinkingOffList.length})`">
        <div class="param-list">
          <div class="thinking-hint text-placeholder text-xs">思考关闭时并入请求体(body)。Ollama 填 reasoning_effort=none（字符串）；Qwen 填 enable_thinking=false（布尔）；DeepSeek 填 thinking=对象</div>
          <div
            v-for="(item, index) in thinkingOffList"
            :key="`tof-${index}`"
            class="param-row param-row-body"
          >
            <AInput v-model:value="item.key" placeholder="如 reasoning_effort" class="param-key" @blur="syncThinkingParams" />
            <ASelect v-model:value="item.valueType" :options="bodyValueTypeOptions" class="param-type" @change="syncThinkingParams" />
            <AInput
              v-model:value="item.value"
              :placeholder="valuePlaceholder(item.valueType)"
              class="param-value"
              @blur="syncThinkingParams"
            />
            <AButton type="text" danger size="small" html-type="button" class="param-remove" @click="removeRow('thinkingOff', index)">
              <MinusCircleOutlined />
            </AButton>
          </div>
          <AButton type="dashed" block size="small" html-type="button" @click="addRow('thinkingOff')">
            <PlusOutlined /> 添加参数
          </AButton>
        </div>
      </ACollapsePanel>
    </ACollapse>
  </div>
</template>

<style scoped lang="scss">
.extend-config-editor {
  .fixed-system-message-row {
    display: flex;
    align-items: flex-start;
    gap: var(--spacing-sm);
    margin-bottom: var(--spacing-md);

    .label-wrap {
      display: flex;
      flex-direction: column;
      gap: 2px;

      .label {
        font-size: 14px;
        color: var(--color-text-primary);
      }
    }
  }

  :deep(.ant-input, .ant-input-select) {
    background-color: white !important;
  }
  :deep(.ant-select-selector) {
    background-color: white !important;
  }
  .thinking-hint {
    margin-bottom: var(--spacing-sm);
    line-height: 1.6;
  }
  .param-list {
    .param-row {
      display: flex;
      gap: var(--spacing-sm);
      align-items: center;
      margin-bottom: var(--spacing-sm);

      .param-key {
        flex: 1;
        min-width: 120px;
      }

      .param-value {
        flex: 1;
        min-width: 140px;
      }

      .param-type {
        width: 90px;
        flex-shrink: 0;
      }

      .param-remove {
        flex-shrink: 0;
      }
    }

    .param-row-body {
      .param-key {
        flex: 0.8;
      }

      .param-value {
        flex: 1.2;
      }
    }
  }

  &.compact {
    :deep(.ant-collapse-header) {
      padding: 8px !important;
    }

    .param-row {
      margin-bottom: 6px;
    }
  }
}
</style>
