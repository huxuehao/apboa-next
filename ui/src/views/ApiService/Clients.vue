/**
 * 访问客户端管理页面
 * 客户端是Token体系的授权主体：凭 clientCode + clientSecret 获取Token，
 * 携带Token访问已授权的API
 *
 * @author huxuehao
 */
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  LeftOutlined,
  KeyOutlined,
  CopyOutlined,
  ReloadOutlined,
  HistoryOutlined
} from '@ant-design/icons-vue'
import { Modal, message } from 'ant-design-vue'
import dayjs, { Dayjs } from 'dayjs'
import type { GatewayApi, GatewayClient, GatewayTokenLog } from '@/types/apiService'
import * as apiServiceApi from '@/api/apiService'
import SimpleSwitch from '@/components/common/SimpleSwitch.vue'
import ApboaModal from '@/components/common/ApboaModal.vue'

const router = useRouter()

const list = ref<GatewayClient[]>([])
const loading = ref(false)
const toggleLoadingMap = ref<Record<string, boolean>>({})

// 表单弹窗
const formVisible = ref(false)
const formLoading = ref(false)
const isEditForm = ref(false)
const formData = ref<GatewayClient>(emptyForm())
const expireAtValue = ref<Dayjs | null>(null)
const briefApis = ref<GatewayApi[]>([])

// 密钥查看弹窗
const secretVisible = ref(false)
const secretClient = ref<GatewayClient | null>(null)
const secretLoading = ref(false)

// Token日志抽屉
const tokenLogVisible = ref(false)
const tokenLogs = ref<GatewayTokenLog[]>([])
const tokenLogLoading = ref(false)
const tokenLogClientCode = ref('')

function emptyForm(): GatewayClient {
  return {
    code: '',
    name: '',
    consumer: '',
    tokenTtl: 7200000,
    apiIds: []
  }
}

/**
 * 加载客户端列表
 */
async function fetchList() {
  loading.value = true
  try {
    const res = await apiServiceApi.pageClients({ page: 1, size: 200 })
    list.value = res.data.data.records || []
  } catch (e) {
    console.error('加载客户端列表失败:', e)
  } finally {
    loading.value = false
  }
}

/**
 * 加载可授权的API列表
 */
async function loadBriefApis() {
  try {
    const res = await apiServiceApi.getBriefApis()
    briefApis.value = res.data.data || []
  } catch (e) {
    console.error('加载API列表失败:', e)
  }
}

function handleCreate() {
  isEditForm.value = false
  formData.value = emptyForm()
  expireAtValue.value = null
  formVisible.value = true
  loadBriefApis()
}

async function handleEdit(client: GatewayClient) {
  isEditForm.value = true
  formVisible.value = true
  loadBriefApis()
  try {
    const res = await apiServiceApi.getClient(client.id!)
    const detail = res.data.data
    formData.value = { ...detail, apiIds: detail.apiIds || [] }
    expireAtValue.value = detail.expireAt ? dayjs(detail.expireAt) : null
  } catch (e) {
    console.error('加载客户端详情失败:', e)
  }
}

/**
 * 提交表单
 */
async function handleSubmit() {
  if (!formData.value.code?.trim()) {
    message.warning('请填写客户端编号')
    return
  }
  if (!/^[A-Za-z0-9_-]{4,64}$/.test(formData.value.code)) {
    message.warning('客户端编号必须为4-64位字母、数字、下划线或中划线')
    return
  }
  if (!formData.value.name?.trim()) {
    message.warning('请填写客户端名称')
    return
  }
  formLoading.value = true
  try {
    const payload: GatewayClient = {
      ...formData.value,
      expireAt: expireAtValue.value ? expireAtValue.value.format('YYYY-MM-DD HH:mm:ss') : null
    }
    if (isEditForm.value) {
      await apiServiceApi.updateClient(payload)
      message.success('更新成功')
    } else {
      await apiServiceApi.addClient(payload)
      message.success('创建成功，请在密钥管理中查看客户端密钥')
    }
    formVisible.value = false
    fetchList()
  } catch (e) {
    console.error('保存失败:', e)
  } finally {
    formLoading.value = false
  }
}

/**
 * 查看密钥
 */
async function handleViewSecret(client: GatewayClient) {
  secretVisible.value = true
  secretLoading.value = true
  try {
    const res = await apiServiceApi.getClient(client.id!)
    secretClient.value = res.data.data
  } catch (e) {
    console.error('加载密钥失败:', e)
  } finally {
    secretLoading.value = false
  }
}

/**
 * 重新生成密钥
 */
function handleRegenerateSecret() {
  Modal.confirm({
    title: '确认重置密钥',
    content: '重置后旧密钥立即失效，已颁发的Token将无法通过验签，是否继续？',
    okText: '确认重置',
    cancelText: '取消',
    async onOk() {
      try {
        const res = await apiServiceApi.regenerateSecret(secretClient.value!.id!)
        secretClient.value = { ...secretClient.value!, tokenSecret: res.data.data }
        message.success('密钥已重置')
      } catch (e) {
        console.error('重置密钥失败:', e)
      }
    }
  })
}

/**
 * 复制文本到剪贴板
 */
async function copyText(text?: string) {
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    message.success('已复制')
  } catch {
    message.warning('复制失败，请手动复制')
  }
}

/**
 * 切换客户端上下线（局部加载）
 */
async function handleToggle(client: GatewayClient) {
  const online = client.online === 1
  toggleLoadingMap.value[client.id!] = true
  try {
    await apiServiceApi.toggleClientOnline(client.id!, online ? 0 : 1)
    client.online = online ? 0 : 1
    message.success(online ? '已禁用' : '已启用')
  } catch (e) {
    console.error('切换状态失败:', e)
  } finally {
    toggleLoadingMap.value[client.id!] = false
  }
}

/**
 * 删除客户端
 */
function handleDelete(client: GatewayClient) {
  Modal.confirm({
    title: '确认删除',
    content: '删除后该客户端已颁发的Token将立即失效，是否继续？',
    okText: '删除',
    cancelText: '取消',
    async onOk() {
      try {
        await apiServiceApi.deleteClients([client.id!])
        message.success('删除成功')
        fetchList()
      } catch (e) {
        console.error('删除失败:', e)
      }
    }
  })
}

/**
 * 查看Token颁发日志
 */
async function handleTokenLogs(client: GatewayClient) {
  tokenLogVisible.value = true
  tokenLogClientCode.value = client.code
  tokenLogLoading.value = true
  try {
    const res = await apiServiceApi.pageTokenLogs({ page: 1, size: 100, clientCode: client.code })
    tokenLogs.value = res.data.data.records || []
  } catch (e) {
    console.error('加载Token日志失败:', e)
  } finally {
    tokenLogLoading.value = false
  }
}

function handleBack() {
  router.push('/api-service')
}

onMounted(() => {
  fetchList()
})
</script>

<template>
  <div class="api-client-page">
    <!-- 页面标题区 -->
    <section class="intro-section">
      <span class="back-link" @click="handleBack">
        <LeftOutlined />
        返回API服务
      </span>
      <div class="intro-header">
        <h3 class="intro-title">访问客户端</h3>
        <AButton
          type="primary"
          v-permission="['TENANT_EDITOR','TENANT_ADMIN','TENANT_OWNER']"
          @click="handleCreate"
        >
          <template #icon><PlusOutlined /></template>
          新增客户端
        </AButton>
      </div>
      <p class="intro-desc text-secondary">
        客户端凭编号与密钥调用 Token 服务（GET /oauth/accessToken?clientCode=xxx&amp;clientSecret=xxx）换取访问Token，
        携带Token即可访问已授权的API。免鉴权API无需Token。
      </p>
    </section>

    <!-- 客户端列表 -->
    <section class="list-section">
      <ApboaSpin :spinning="loading">
        <div v-if="list.length === 0 && !loading" class="list-empty">
          <AEmpty description="暂无访问客户端" />
        </div>
        <div v-else class="list-container">
          <div v-for="client in list" :key="client.id" class="client-item">
            <div class="item-icon">
              <KeyOutlined />
            </div>
            <div class="item-main">
              <div class="item-header">
                <span class="item-name">{{ client.name }}</span>
                <ATag class="item-code">{{ client.code }}</ATag>
              </div>
              <div class="item-meta">
                <span>已授权 {{ client.apiCount || 0 }} 个API</span>
                <span>·</span>
                <span>Token有效期 {{ Math.round((client.tokenTtl || 0) / 60000) }} 分钟</span>
                <span>·</span>
                <span>{{ client.expireAt ? `${client.expireAt} 过期` : '永不过期' }}</span>
                <span v-if="client.consumer">·</span>
                <span v-if="client.consumer">消费方 {{ client.consumer }}</span>
              </div>
            </div>
            <div class="item-actions">
              <SimpleSwitch
                :checked="client.online === 1"
                :loading="toggleLoadingMap[client.id!]"
                size="small"
                @change="handleToggle(client)"
              />
              <span class="actions-divider" />
              <ATooltip title="密钥管理" v-permission="['TENANT_EDITOR','TENANT_ADMIN','TENANT_OWNER']">
                <AButton type="text" @click="handleViewSecret(client)">
                  <template #icon><KeyOutlined /></template>
                </AButton>
              </ATooltip>
              <ATooltip title="Token颁发记录">
                <AButton type="text" @click="handleTokenLogs(client)">
                  <template #icon><HistoryOutlined /></template>
                </AButton>
              </ATooltip>
              <ATooltip title="编辑" v-permission="['TENANT_EDITOR','TENANT_ADMIN','TENANT_OWNER']">
                <AButton type="text" @click="handleEdit(client)">
                  <template #icon><EditOutlined /></template>
                </AButton>
              </ATooltip>
              <ATooltip title="删除" v-permission="['TENANT_EDITOR','TENANT_ADMIN','TENANT_OWNER']">
                <AButton type="text" danger @click="handleDelete(client)">
                  <template #icon><DeleteOutlined /></template>
                </AButton>
              </ATooltip>
            </div>
          </div>
        </div>
      </ApboaSpin>
    </section>

    <!-- 新增/编辑弹窗 -->
    <ApboaModal
      :open="formVisible"
      :title="isEditForm ? '编辑客户端' : '新增客户端'"
      :confirm-loading="formLoading"
      destroyOnClose
      defaultWidth="680px"
      @ok="handleSubmit"
      @cancel="formVisible = false"
      @update:open="(v: boolean) => (formVisible = v)"
    >
      <AForm layout="vertical">
        <AFormItem label="客户端编号" required extra="4-64位字母、数字、下划线或中划线，创建后不可修改">
          <AInput
            v-model:value="formData.code"
            placeholder="如 order-system"
            :maxlength="64"
            :disabled="isEditForm"
          />
        </AFormItem>
        <AFormItem label="客户端名称" required>
          <AInput v-model:value="formData.name" placeholder="填写客户端名称" :maxlength="100" />
        </AFormItem>
        <AFormItem label="消费方">
          <AInput v-model:value="formData.consumer" placeholder="客户端归属方，如某业务系统或团队" :maxlength="100" />
        </AFormItem>
        <AFormItem label="客户端过期时间（留空表示永不过期）">
          <ADatePicker
            v-model:value="expireAtValue"
            show-time
            style="width: 100%"
            placeholder="选择过期时间"
          />
        </AFormItem>
        <AFormItem label="Token有效期（毫秒）" extra="默认 7200000（2小时）">
          <AInputNumber v-model:value="formData.tokenTtl" :min="60000" style="width: 100%" />
        </AFormItem>
        <AFormItem label="API授权" extra="仅Token鉴权的API需要授权，免鉴权API任何请求方均可访问">
          <ASelect
            v-model:value="formData.apiIds"
            mode="multiple"
            placeholder="选择该客户端可访问的API"
            style="width: 100%"
            option-filter-prop="label"
          >
            <ASelectOption
              v-for="api in briefApis"
              :key="api.id"
              :value="api.id"
              :label="api.name"
            >
              {{ api.name }}（{{ api.method }} {{ api.path }}）
            </ASelectOption>
          </ASelect>
        </AFormItem>
      </AForm>
    </ApboaModal>

    <!-- 密钥管理弹窗 -->
    <ApboaModal
      :open="secretVisible"
      title="密钥管理"
      destroyOnClose
      defaultWidth="600px"
      :footer="null"
      @cancel="secretVisible = false"
      @update:open="(v: boolean) => (secretVisible = v)"
    >
      <ApboaSpin :spinning="secretLoading">
        <div class="secret-panel" v-if="secretClient">
          <div class="secret-row">
            <span class="secret-label">客户端编号</span>
            <div class="secret-value">
              <span>{{ secretClient.code }}</span>
              <AButton type="text" size="small" @click="copyText(secretClient.code)">
                <template #icon><CopyOutlined /></template>
              </AButton>
            </div>
          </div>
          <div class="secret-row">
            <span class="secret-label">客户端密钥</span>
            <div class="secret-value">
              <span class="secret-text">{{ secretClient.tokenSecret }}</span>
              <AButton type="text" size="small" @click="copyText(secretClient.tokenSecret)">
                <template #icon><CopyOutlined /></template>
              </AButton>
            </div>
          </div>
          <AAlert
            type="warning"
            show-icon
            message="密钥用于获取Token与签名验证，请妥善保管。重置密钥后旧密钥与已颁发Token立即失效。"
            style="margin: 12px 0"
          />
          <AButton danger v-permission="['TENANT_EDITOR','TENANT_ADMIN','TENANT_OWNER']" @click="handleRegenerateSecret">
            <template #icon><ReloadOutlined /></template>
            重置密钥
          </AButton>
        </div>
      </ApboaSpin>
    </ApboaModal>

    <!-- Token颁发记录抽屉 -->
    <ADrawer
      v-model:open="tokenLogVisible"
      :title="`Token颁发记录 - ${tokenLogClientCode}`"
      width="560"
      destroyOnClose
    >
      <ApboaSpin :spinning="tokenLogLoading">
        <AEmpty v-if="tokenLogs.length === 0 && !tokenLogLoading" description="暂无颁发记录" />
        <div v-else class="token-log-list">
          <div v-for="log in tokenLogs" :key="log.id" class="token-log-item">
            <div class="log-line">
              <ATag :color="log.status === 1 ? 'success' : 'error'">
                {{ log.status === 1 ? '成功' : '失败' }}
              </ATag>
              <span class="log-ip">{{ log.accessIp || '-' }}</span>
              <span class="log-time">{{ log.createdAt }}</span>
            </div>
            <div v-if="log.error" class="log-error">{{ log.error }}</div>
          </div>
        </div>
      </ApboaSpin>
    </ADrawer>
  </div>
</template>

<script lang="ts">
export default {
  name: 'ApiServiceClients'
}
</script>

<style scoped lang="scss">
@use '@/styles/api-service/manage.scss' as *;

.client-item {
  display: flex;
  align-items: center;
  padding: 16px 20px;
  border: 1px solid #EBEBEB;
  border-radius: 8px;
  gap: 16px;
  margin-bottom: 10px;
  transition: background-color 0.2s ease;

  &:hover {
    background-color: rgba(0, 0, 0, 0.02);
  }
}

.item-icon {
  flex-shrink: 0;
  font-size: 20px;
  color: var(--color-text-secondary);
}

.item-code {
  font-family: monospace;
}

.secret-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;

  .secret-row {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  .secret-label {
    font-size: 12px;
    color: var(--color-text-secondary);
  }

  .secret-value {
    display: flex;
    align-items: center;
    gap: 8px;
    background-color: #F2F4F7;
    border-radius: 6px;
    padding: 8px 12px;

    .secret-text {
      font-family: monospace;
      font-size: 12px;
      word-break: break-all;
    }
  }
}

.token-log-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.token-log-item {
  padding: 10px 12px;
  border: 1px solid #EBEBEB;
  border-radius: 6px;

  .log-line {
    display: flex;
    align-items: center;
    gap: 10px;
    font-size: 13px;
  }

  .log-ip {
    font-family: monospace;
  }

  .log-time {
    margin-left: auto;
    font-size: 12px;
    color: var(--color-text-secondary);
  }

  .log-error {
    margin-top: 6px;
    font-size: 12px;
    color: #e03131;
  }
}
</style>
