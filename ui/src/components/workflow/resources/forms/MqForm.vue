<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import ResourceJsonConfigEditor from '../ResourceJsonConfigEditor.vue'
import type { MqResource } from '@/types/workflowResources'

const props = defineProps<{
  initialValue?: Partial<MqResource>
}>()

const formRef = ref()
const form = reactive<MqResource>({
  name: '',
  type: 'KAFKA',
  address: '',
  port: 9092,
  username: '',
  password: '',
  config: '',
  remark: '',
  enabled: true
})

const typeOptions = [
  { label: 'Kafka', value: 'KAFKA' },
  { label: 'RabbitMQ', value: 'RABBITMQ' },
  { label: 'RocketMQ', value: 'ROCKETMQ' }
]

const defaultPorts: Record<string, number> = {
  KAFKA: 9092,
  RABBITMQ: 5672,
  ROCKETMQ: 9876
}

function reset(value?: Partial<MqResource>) {
  Object.assign(form, {
    id: value?.id,
    name: value?.name || '',
    type: value?.type || 'KAFKA',
    address: value?.address || '',
    port: value?.port ?? defaultPorts[value?.type || 'KAFKA'],
    username: value?.username || '',
    password: '',
    config: value?.config || '',
    remark: value?.remark || '',
    enabled: value?.enabled ?? true,
    healthStatus: value?.healthStatus,
    lastHealthCheck: value?.lastHealthCheck,
    lastCheckMessage: value?.lastCheckMessage
  })
}

function validateJson(_rule: unknown, value?: string) {
  if (!value || !value.trim()) return Promise.resolve()
  try {
    JSON.parse(value)
    return Promise.resolve()
  } catch {
    return Promise.reject('扩展配置必须是合法 JSON')
  }
}

const rules = {
  name: [{ required: true, message: '请输入消息资源名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择消息类型', trigger: 'change' }],
  address: [{ required: true, message: '请输入连接地址', trigger: 'blur' }],
  port: [{ required: true, type: 'number', min: 1, max: 65535, message: '端口范围为 1-65535', trigger: 'change' }],
  config: [{ validator: validateJson, trigger: 'blur' }]
}

function handleTypeChange(value: string) {
  form.port = defaultPorts[value] || form.port
}

async function validate() {
  await formRef.value?.validate()
}

function getPayload(): MqResource {
  return {
    ...form,
    password: form.password?.trim() ? form.password : undefined,
    config: form.config?.trim() || undefined
  }
}

watch(() => props.initialValue, (value) => reset(value), { immediate: true, deep: true })

defineExpose({ validate, getPayload })
</script>

<template>
  <AForm ref="formRef" :model="form" :rules="rules" layout="vertical">
    <AFormItem label="消息资源名称" name="name">
      <AInput v-model:value="form.name" placeholder="例如：订单事件 Kafka" :maxlength="100" show-count />
    </AFormItem>
    <AFormItem label="类型" name="type">
      <ASelect v-model:value="form.type" :options="typeOptions" @change="handleTypeChange" />
    </AFormItem>
    <div class="resource-form-grid">
      <AFormItem label="连接地址" name="address">
        <AInput v-model:value="form.address" placeholder="Broker 地址或主机名" />
      </AFormItem>
      <AFormItem label="端口" name="port">
        <AInputNumber v-model:value="form.port" :min="1" :max="65535" style="width: 100%" />
      </AFormItem>
    </div>
    <div class="resource-form-grid">
      <AFormItem label="用户名" name="username">
        <AInput v-model:value="form.username" placeholder="可选" />
      </AFormItem>
      <AFormItem label="密码" name="password">
        <AInputPassword v-model:value="form.password" placeholder="留空表示不修改" autocomplete="new-password" />
      </AFormItem>
    </div>
    <AFormItem label="启用状态" name="enabled">
      <ASwitch v-model:checked="form.enabled" checked-children="启用" un-checked-children="禁用" />
    </AFormItem>
    <AFormItem label="备注" name="remark">
      <ATextarea v-model:value="form.remark" :rows="3" placeholder="记录 topic/queue 约定或负责人" :maxlength="500" show-count />
    </AFormItem>
    <AFormItem label="高级配置" name="config">
      <ResourceJsonConfigEditor v-model:value="form.config" placeholder="例如：RabbitMQ vhost、Kafka SASL、RocketMQ namespace 等 JSON 配置" />
    </AFormItem>
  </AForm>
</template>

<style scoped lang="scss">
.resource-form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
</style>
