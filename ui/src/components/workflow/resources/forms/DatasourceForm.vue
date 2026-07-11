<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import ResourceJsonConfigEditor from '../ResourceJsonConfigEditor.vue'
import type { DatasourceResource } from '@/types/workflowResources'

const props = defineProps<{
  initialValue?: Partial<DatasourceResource>
}>()

const formRef = ref()
const form = reactive<DatasourceResource>({
  name: '',
  type: 'MYSQL',
  ip: '',
  port: '3306',
  db: '',
  username: '',
  password: '',
  config: '',
  remark: '',
  enabled: true
})

const typeOptions = [
  { label: 'MySQL', value: 'MYSQL' },
  { label: 'Oracle', value: 'ORACLE' },
  { label: 'PostgreSQL', value: 'POSTGRESQL' }
]

const defaultPorts: Record<string, string> = {
  MYSQL: '3306',
  ORACLE: '1521',
  POSTGRESQL: '5432'
}

function reset(value?: Partial<DatasourceResource>) {
  Object.assign(form, {
    id: value?.id,
    name: value?.name || '',
    type: value?.type || 'MYSQL',
    ip: value?.ip || '',
    port: value?.port || defaultPorts[value?.type || 'MYSQL'],
    db: value?.db || '',
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
  name: [{ required: true, message: '请输入数据源名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择数据源类型', trigger: 'change' }],
  ip: [{ required: true, message: '请输入主机地址', trigger: 'blur' }],
  port: [{ required: true, message: '请输入端口', trigger: 'blur' }],
  db: [{ required: true, message: '请输入数据库/服务名', trigger: 'blur' }],
  config: [{ validator: validateJson, trigger: 'blur' }]
}

function handleTypeChange(value: string) {
  form.port = defaultPorts[value] || form.port
}

async function validate() {
  await formRef.value?.validate()
}

function getPayload(): DatasourceResource {
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
    <AFormItem label="数据源名称" name="name">
      <AInput v-model:value="form.name" placeholder="例如：生产订单库" :maxlength="100" show-count />
    </AFormItem>
    <AFormItem label="类型" name="type">
      <ASelect v-model:value="form.type" :options="typeOptions" @change="handleTypeChange" />
    </AFormItem>
    <AFormItem label="主机地址" name="ip">
      <AInput v-model:value="form.ip" placeholder="127.0.0.1 或数据库域名" />
    </AFormItem>
    <div class="resource-form-grid">
      <AFormItem label="端口" name="port">
        <AInput v-model:value="form.port" placeholder="3306" />
      </AFormItem>
      <AFormItem label="数据库/服务名" name="db">
        <AInput v-model:value="form.db" placeholder="database 或 service name" />
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
      <ATextarea v-model:value="form.remark" :rows="3" placeholder="记录用途、负责人或访问边界" :maxlength="500" show-count />
    </AFormItem>
    <AFormItem label="高级配置" name="config">
      <ResourceJsonConfigEditor v-model:value="form.config" />
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
