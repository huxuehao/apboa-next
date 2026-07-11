<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import ResourceJsonConfigEditor from '../ResourceJsonConfigEditor.vue'
import type { CacheResource } from '@/types/workflowResources'

const props = defineProps<{
  initialValue?: Partial<CacheResource>
}>()

const formRef = ref()
const form = reactive<CacheResource>({
  name: '',
  type: 'REDIS',
  ip: '',
  port: 6379,
  db: 0,
  username: '',
  password: '',
  config: '',
  remark: '',
  enabled: true
})

function reset(value?: Partial<CacheResource>) {
  Object.assign(form, {
    id: value?.id,
    name: value?.name || '',
    type: value?.type || 'REDIS',
    ip: value?.ip || '',
    port: value?.port ?? 6379,
    db: value?.db ?? 0,
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
  name: [{ required: true, message: '请输入缓存名称', trigger: 'blur' }],
  ip: [{ required: true, message: '请输入主机地址', trigger: 'blur' }],
  port: [{ required: true, type: 'number', min: 1, max: 65535, message: '端口范围为 1-65535', trigger: 'change' }],
  db: [{ required: true, type: 'number', min: 0, message: 'DB 不能小于 0', trigger: 'change' }],
  config: [{ validator: validateJson, trigger: 'blur' }]
}

async function validate() {
  await formRef.value?.validate()
}

function getPayload(): CacheResource {
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
    <AFormItem label="缓存名称" name="name">
      <AInput v-model:value="form.name" placeholder="例如：默认 Redis" :maxlength="100" show-count />
    </AFormItem>
    <AFormItem label="类型" name="type">
      <ASelect v-model:value="form.type" :options="[{ label: 'Redis', value: 'REDIS' }]" disabled />
    </AFormItem>
    <AFormItem label="主机地址" name="ip">
      <AInput v-model:value="form.ip" placeholder="127.0.0.1 或 Redis 域名" />
    </AFormItem>
    <div class="resource-form-grid">
      <AFormItem label="端口" name="port">
        <AInputNumber v-model:value="form.port" :min="1" :max="65535" style="width: 100%" />
      </AFormItem>
      <AFormItem label="DB" name="db">
        <AInputNumber v-model:value="form.db" :min="0" style="width: 100%" />
      </AFormItem>
    </div>
    <div class="resource-form-grid">
      <AFormItem label="用户名" name="username">
        <AInput v-model:value="form.username" placeholder="Redis ACL 用户名，可选" />
      </AFormItem>
      <AFormItem label="密码" name="password">
        <AInputPassword v-model:value="form.password" placeholder="留空表示不修改" autocomplete="new-password" />
      </AFormItem>
    </div>
    <AFormItem label="启用状态" name="enabled">
      <ASwitch v-model:checked="form.enabled" checked-children="启用" un-checked-children="禁用" />
    </AFormItem>
    <AFormItem label="备注" name="remark">
      <ATextarea v-model:value="form.remark" :rows="3" placeholder="记录缓存用途或访问范围" :maxlength="500" show-count />
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
