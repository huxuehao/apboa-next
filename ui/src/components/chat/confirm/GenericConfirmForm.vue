<template>
  <div class="gc-card" @click.stop>
    <div class="gc-grid">
      <template v-for="f in fields" :key="f.name">
        <label class="gc-label" :title="f.name">
          {{ f.name }}<span v-if="f.required" class="gc-required">*</span>
        </label>
        <div class="gc-control">
          <ASelect
            v-if="f.options?.length"
            v-model:value="formData[f.name] as string"
            size="small"
            style="width: 100%"
            :placeholder="f.description"
            popup-class-name="confirm-select-popup"
            allow-clear
          >
            <ASelectOption v-for="o in f.options" :key="o" :value="o">{{ o }}</ASelectOption>
          </ASelect>
          <AInputNumber
            v-else-if="f.type === 'integer' || f.type === 'number'"
            v-model:value="formData[f.name] as number"
            size="small"
            style="width: 100%"
            :placeholder="f.description"
          />
          <ASwitch
            v-else-if="f.type === 'boolean'"
            v-model:checked="formData[f.name] as boolean"
            size="small"
          />
          <ATextarea
            v-else-if="f.type === 'array' || f.type === 'object'"
            v-model:value="formData[f.name] as string"
            size="small"
            :auto-size="{ minRows: 2, maxRows: 6 }"
            :placeholder="f.description || 'JSON'"
            class="gc-json"
          />
          <AInput
            v-else
            v-model:value="formData[f.name] as string"
            size="small"
            :placeholder="f.description"
          />
          <div v-if="f.description" class="gc-help">{{ f.description }}</div>
        </div>
      </template>
    </div>
    <div class="gc-actions">
      <AButton type="primary" size="small" @click="handleApprove">允许</AButton>
      <AButton size="small" @click="emit('decide', { approved: false })">禁止</AButton>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * HITL 通用确认表单（回退层②，「对话内联」紧凑风格）：按后端下发的字段元数据
 * 渲染 key-value 网格表单，模型生成的参数值预填，用户核对/修改后允许执行。
 * 定位是开发人员核对层——字段名显示原始参数名、描述做占位与帮助；
 * 业务语义展示请注册定制渲染器（回退层①，见 confirmRenderers 注册表说明）。
 */
import { reactive } from 'vue'
import { message } from 'ant-design-vue'
import type { ConfirmFieldMeta } from '@/types'

const props = defineProps<{
  name: string
  /** 模型生成的原始参数（预填初始值） */
  input: Record<string, unknown>
  fields: ConfirmFieldMeta[]
}>()

const emit = defineEmits<{
  /** input 仅在用户修改过参数时携带；原样确认省略（后端走原参数路径，避免无谓改写） */
  decide: [value: { approved: boolean; input?: Record<string, unknown>; summary?: string }]
}>()

/** 初始值：模型生成参数预填；object/array 序列化为可编辑 JSON 文本 */
function initValue(f: ConfirmFieldMeta): unknown {
  const v = props.input?.[f.name]
  if (f.type === 'array' || f.type === 'object') {
    return v === undefined ? '' : JSON.stringify(v, null, 2)
  }
  if (f.type === 'boolean') return Boolean(v)
  if (v === undefined) return f.type === 'integer' || f.type === 'number' ? undefined : ''
  return v
}

const formData = reactive<Record<string, unknown>>({})
props.fields.forEach(f => {
  formData[f.name] = initValue(f)
})

/** 表单值 → 工具参数（按声明类型转换；schema 未声明的原参数原样保留；空的非必填项剔除） */
function collect(): Record<string, unknown> | null {
  const out: Record<string, unknown> = { ...(props.input ?? {}) }
  for (const f of props.fields) {
    const v = formData[f.name]
    const empty = v === undefined || v === null || v === ''
    if (empty) {
      if (f.required) {
        message.warning(`请填写必填参数：${f.name}`)
        return null
      }
      delete out[f.name]
      continue
    }
    if (f.type === 'array' || f.type === 'object') {
      try {
        out[f.name] = JSON.parse(String(v))
      } catch {
        message.warning(`参数 ${f.name} 不是合法 JSON`)
        return null
      }
    } else if (f.type === 'integer' || f.type === 'number') {
      const n = Number(v)
      if (Number.isNaN(n)) {
        message.warning(`参数 ${f.name} 必须是数字`)
        return null
      }
      out[f.name] = n
    } else if (f.type === 'boolean') {
      out[f.name] = Boolean(v)
    } else {
      out[f.name] = v
    }
  }
  return out
}

const handleApprove = () => {
  const out = collect()
  if (!out) return
  const changed = JSON.stringify(out) !== JSON.stringify(props.input ?? {})
  emit('decide', changed ? { approved: true, input: out } : { approved: true })
}
</script>

<style scoped lang="scss">
.gc-card {
  max-width: 460px;
  background: #fff;
  border: 1px solid #d9e8fb;
  border-radius: 10px;
  padding: 12px 14px;
}

.gc-grid {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 10px 14px;
  align-items: start;
}

.gc-label {
  font-size: 12px;
  font-family: monospace;
  color: rgba(0, 0, 0, 0.55);
  padding-top: 5px;
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.gc-required {
  color: #cf1322;
  margin-left: 2px;
}

.gc-control {
  min-width: 0;
}

.gc-help {
  font-size: 11px;
  color: rgba(0, 0, 0, 0.3);
  margin-top: 3px;
  line-height: 1.5;
}

.gc-json {
  font-family: monospace;
  font-size: 12px;
}

.gc-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid #f0f1f3;

  :deep(.ant-btn) {
    border-radius: 999px;
    padding-inline: 16px;
  }
}
</style>
