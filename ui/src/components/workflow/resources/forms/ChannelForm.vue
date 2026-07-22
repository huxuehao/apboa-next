<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import type { ChannelResource } from '@/types/workflowResources'

const props = defineProps<{
  initialValue?: Partial<ChannelResource>
}>()

const formRef = ref()
const form = reactive<ChannelResource>({
  name: '',
  type: 'EMAIL',
  config: '',
  remark: '',
  enabled: true
})

// 按类型分离的配置字段
const emailConfig = reactive({
  serverHost: '',
  serverPort: '465',
  sender: '',
  enableSmtpAuth: 'true',
  user: '',
  passwd: '',
  starttlsEnable: 'true',
  sslEnable: 'true',
  smtpSslTrust: '*',
})

const webhookConfig = reactive({
  webhook: '',
  keyword: '',
  secret: '',
})

const typeOptions = [
  { label: '邮箱（SMTP）', value: 'EMAIL' },
  { label: '企业微信机器人', value: 'WECOM' },
  { label: '钉钉机器人', value: 'DINGTALK' },
  { label: '飞书机器人', value: 'FEISHU' },
]

function buildConfigJson() {
  if (form.type === 'EMAIL') {
    return JSON.stringify({
      serverHost: emailConfig.serverHost,
      serverPort: emailConfig.serverPort,
      sender: emailConfig.sender,
      enableSmtpAuth: emailConfig.enableSmtpAuth,
      user: emailConfig.user,
      passwd: emailConfig.passwd,
      starttlsEnable: emailConfig.starttlsEnable,
      sslEnable: emailConfig.sslEnable,
      smtpSslTrust: emailConfig.smtpSslTrust,
    })
  }
  const cfg: Record<string, string> = { webhook: webhookConfig.webhook }
  if (form.type === 'DINGTALK' && webhookConfig.keyword) cfg.keyword = webhookConfig.keyword
  if (form.type === 'FEISHU' && webhookConfig.secret) cfg.secret = webhookConfig.secret
  return JSON.stringify(cfg)
}

function parseConfigJson(configStr?: string) {
  if (!configStr) return
  try {
    const cfg = JSON.parse(configStr)
    if (form.type === 'EMAIL') {
      emailConfig.serverHost = cfg.serverHost || ''
      emailConfig.serverPort = cfg.serverPort || '465'
      emailConfig.sender = cfg.sender || ''
      emailConfig.enableSmtpAuth = cfg.enableSmtpAuth || 'true'
      emailConfig.user = cfg.user || ''
      emailConfig.passwd = cfg.passwd || ''
      emailConfig.starttlsEnable = cfg.starttlsEnable || 'true'
      emailConfig.sslEnable = cfg.sslEnable || 'true'
      emailConfig.smtpSslTrust = cfg.smtpSslTrust || '*'
    } else {
      webhookConfig.webhook = cfg.webhook || ''
      webhookConfig.keyword = cfg.keyword || ''
      webhookConfig.secret = cfg.secret || ''
    }
  } catch { /* ignore */ }
}

function reset(value?: Partial<ChannelResource>) {
  Object.assign(form, {
    id: value?.id,
    name: value?.name || '',
    type: value?.type || 'EMAIL',
    remark: value?.remark || '',
    enabled: value?.enabled ?? true,
    healthStatus: value?.healthStatus,
    lastHealthCheck: value?.lastHealthCheck,
    lastCheckMessage: value?.lastCheckMessage,
  })
  parseConfigJson(value?.config || '')
}

function handleTypeChange() {
  parseConfigJson('{}')
}

const rules = {
  name: [{ required: true, message: '请输入渠道名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择渠道类型', trigger: 'change' }],
}

async function validate() {
  await formRef.value?.validate()
  // 验证关键配置字段
  if (form.type === 'EMAIL') {
    if (!emailConfig.serverHost) throw new Error('SMTP 服务器地址不能为空')
  } else {
    if (!webhookConfig.webhook) throw new Error('Webhook 地址不能为空')
  }
}

function getPayload(): ChannelResource {
  return {
    ...form,
    config: buildConfigJson(),
  }
}

watch(() => props.initialValue, (value) => reset(value), { immediate: true, deep: true })

defineExpose({ validate, getPayload })
</script>

<template>
  <AForm ref="formRef" :model="form" :rules="rules" layout="vertical">
    <AFormItem label="渠道名称" name="name">
      <AInput v-model:value="form.name" placeholder="例如：公司邮箱、运维钉钉群" :maxlength="100" show-count />
    </AFormItem>
    <AFormItem label="渠道类型" name="type">
      <ASelect v-model:value="form.type" :options="typeOptions" @change="handleTypeChange" />
    </AFormItem>

    <!-- 邮箱配置 -->
    <template v-if="form.type === 'EMAIL'">
      <div class="resource-form-grid">
        <AFormItem label="SMTP 服务器" required>
          <AInput v-model:value="emailConfig.serverHost" placeholder="smtp.example.com" />
        </AFormItem>
        <AFormItem label="端口" required>
          <AInput v-model:value="emailConfig.serverPort" placeholder="465" />
        </AFormItem>
      </div>
      <AFormItem label="发件人地址" required>
        <AInput v-model:value="emailConfig.sender" placeholder="noreply@example.com" />
      </AFormItem>
      <div class="resource-form-grid">
        <AFormItem label="用户名">
          <AInput v-model:value="emailConfig.user" placeholder="邮箱账号" />
        </AFormItem>
        <AFormItem label="密码/授权码">
          <AInputPassword v-model:value="emailConfig.passwd" placeholder="SMTP 授权码" autocomplete="new-password" />
        </AFormItem>
      </div>
      <div class="resource-form-grid">
        <AFormItem label="SMTP 认证">
          <ASelect v-model:value="emailConfig.enableSmtpAuth" :options="[{ label: '开启', value: 'true' }, { label: '关闭', value: 'false' }]" />
        </AFormItem>
        <AFormItem label="SSL 信任">
          <AInput v-model:value="emailConfig.smtpSslTrust" placeholder="*" />
        </AFormItem>
      </div>
      <div class="resource-form-grid">
        <AFormItem label="STARTTLS">
          <ASelect v-model:value="emailConfig.starttlsEnable" :options="[{ label: '开启', value: 'true' }, { label: '关闭', value: 'false' }]" />
        </AFormItem>
        <AFormItem label="SSL">
          <ASelect v-model:value="emailConfig.sslEnable" :options="[{ label: '开启', value: 'true' }, { label: '关闭', value: 'false' }]" />
        </AFormItem>
      </div>
    </template>

    <!-- Webhook 配置（企微/钉钉/飞书） -->
    <template v-else>
      <AFormItem label="Webhook 地址" required>
        <AInput v-model:value="webhookConfig.webhook" placeholder="https://..." />
      </AFormItem>
      <AFormItem v-if="form.type === 'DINGTALK'" label="安全关键词">
        <AInput v-model:value="webhookConfig.keyword" placeholder="钉钉机器人安全设置中的关键词" />
      </AFormItem>
      <AFormItem v-if="form.type === 'FEISHU'" label="签名密钥">
        <AInput v-model:value="webhookConfig.secret" placeholder="飞书机器人安全设置中的签名密钥" />
      </AFormItem>
    </template>

    <AFormItem label="启用状态" name="enabled">
      <ASwitch v-model:checked="form.enabled" checked-children="启用" un-checked-children="禁用" />
    </AFormItem>
    <AFormItem label="备注" name="remark">
      <ATextarea v-model:value="form.remark" :rows="3" placeholder="记录渠道用途或负责人" :maxlength="500" show-count />
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
