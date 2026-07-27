/**
 * 网关应用管理页面
 * 应用是网关的服务端口，每个应用监听一个独立端口
 *
 * @author huxuehao
 */
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { PlusOutlined, EditOutlined, DeleteOutlined, LeftOutlined } from '@ant-design/icons-vue'
import { Modal, message } from 'ant-design-vue'
import type { GatewayApp, GatewayAppWhitelistItem } from '@/types/apiService'
import * as apiServiceApi from '@/api/apiService'
import SimpleSwitch from '@/components/common/SimpleSwitch.vue'
import ApboaModal from '@/components/common/ApboaModal.vue'

const router = useRouter()

const list = ref<GatewayApp[]>([])
const loading = ref(false)
/** 行级开关的加载状态：appId -> loading */
const toggleLoadingMap = ref<Record<string, boolean>>({})

// 表单弹窗
const formVisible = ref(false)
const formLoading = ref(false)
const isEditForm = ref(false)
const formData = ref<GatewayApp>(emptyForm())

function emptyForm(): GatewayApp {
  return {
    name: '',
    remark: '',
    port: undefined,
    config: {
      corsOpen: false,
      allowedOrigin: '*',
      contentLength: 2 * 1024 * 1024,
      whitelist: []
    }
  }
}

const IPV4_RE = /^((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)\.){3}(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)$/
const IPV6_RE = /^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$|^(([0-9a-fA-F]{1,4}:)*)?::(([0-9a-fA-F]{1,4}:)*[0-9a-fA-F]{1,4})?$/

function isValidIp(ip: string): boolean {
  return ip.includes(':') ? IPV6_RE.test(ip) : IPV4_RE.test(ip)
}

function addWhitelistRow() {
  formData.value.config!.whitelist!.push({ ip: '', remark: '' })
}

function removeWhitelistRow(index: number) {
  formData.value.config!.whitelist!.splice(index, 1)
}

/**
 * 白名单校验与清洗：过滤全空行，IP必填、格式合法且不重复；不合法时返回 null
 */
function cleanWhitelist(): GatewayAppWhitelistItem[] | null {
  const rows = (formData.value.config!.whitelist ?? [])
    .filter(item => item.ip?.trim() || item.remark?.trim())
  const seen = new Set<string>()
  for (const item of rows) {
    const ip = item.ip?.trim() ?? ''
    if (!ip) {
      message.warning('白名单IP不能为空')
      return null
    }
    if (!isValidIp(ip)) {
      message.warning(`白名单IP格式不合法：${ip}`)
      return null
    }
    if (seen.has(ip)) {
      message.warning(`白名单IP重复：${ip}`)
      return null
    }
    seen.add(ip)
  }
  return rows.map(item => ({ ip: item.ip!.trim(), remark: item.remark?.trim() || undefined }))
}

/**
 * 加载应用列表
 */
async function fetchList() {
  loading.value = true
  try {
    const res = await apiServiceApi.listApps()
    list.value = res.data.data || []
  } catch (e) {
    console.error('加载应用列表失败:', e)
  } finally {
    loading.value = false
  }
}

function handleCreate() {
  isEditForm.value = false
  formData.value = emptyForm()
  formVisible.value = true
}

function handleEdit(app: GatewayApp) {
  isEditForm.value = true
  formData.value = {
    ...app,
    config: {
      corsOpen: app.config?.corsOpen ?? false,
      allowedOrigin: app.config?.allowedOrigin ?? '*',
      contentLength: app.config?.contentLength ?? 2 * 1024 * 1024,
      // 拷贝白名单条目，避免编辑时直接改动列表数据
      whitelist: (app.config?.whitelist ?? []).map(item => ({ ...item }))
    }
  }
  formVisible.value = true
}

/**
 * 提交表单
 */
async function handleSubmit() {
  if (!formData.value.name?.trim()) {
    message.warning('请填写应用名称')
    return
  }
  if (!formData.value.port || formData.value.port < 1024 || formData.value.port > 65535) {
    message.warning('端口必须在 1024-65535 之间')
    return
  }
  const whitelist = cleanWhitelist()
  if (whitelist === null) {
    return
  }
  formData.value.config!.whitelist = whitelist
  formLoading.value = true
  try {
    if (isEditForm.value) {
      await apiServiceApi.updateApp(formData.value)
      message.success('更新成功')
    } else {
      await apiServiceApi.addApp(formData.value)
      message.success('创建成功')
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
 * 切换应用上下线（局部加载，不刷新整页）
 */
async function handleToggle(app: GatewayApp) {
  const online = app.online === 1
  toggleLoadingMap.value[app.id!] = true
  try {
    await apiServiceApi.toggleAppOnline(app.id!, online ? 0 : 1)
    app.online = online ? 0 : 1
    message.success(online ? '应用已下线，其下API已同步下线' : '应用已上线')
  } catch (e) {
    console.error('切换状态失败:', e)
  } finally {
    toggleLoadingMap.value[app.id!] = false
  }
}

/**
 * 删除应用
 */
function handleDelete(app: GatewayApp) {
  if (app.online === 1) {
    message.warning('请先下线应用')
    return
  }
  Modal.confirm({
    title: '确认删除',
    content: '应用下存在API定义时无法删除，删除后无法恢复，是否继续？',
    okText: '删除',
    cancelText: '取消',
    async onOk() {
      try {
        await apiServiceApi.deleteApps([app.id!])
        message.success('删除成功')
        fetchList()
      } catch (e) {
        console.error('删除失败:', e)
      }
    }
  })
}

function handleBack() {
  router.push('/api-service')
}

onMounted(() => {
  fetchList()
})
</script>

<template>
  <div class="api-app-page">
    <!-- 页面标题区 -->
    <section class="intro-section">
      <span class="back-link" @click="handleBack">
        <LeftOutlined />
        返回API服务
      </span>
      <div class="intro-header">
        <h3 class="intro-title">网关应用</h3>
        <AButton
          type="primary"
          v-permission="['TENANT_EDITOR','TENANT_ADMIN','TENANT_OWNER']"
          @click="handleCreate"
        >
          <template #icon><PlusOutlined /></template>
          新增应用
        </AButton>
      </div>
      <p class="intro-desc text-secondary">
        应用是API服务的承载单元，每个应用对应一个独立的监听端口，上线后网关节点会在该端口对外提供服务。
      </p>
    </section>

    <!-- 应用列表 -->
    <section class="list-section">
      <ApboaSpin :spinning="loading">
        <div v-if="list.length === 0 && !loading" class="list-empty">
          <AEmpty description="暂无网关应用" />
        </div>
        <div v-else class="list-container">
          <div v-for="app in list" :key="app.id" class="app-item">
            <div class="item-main">
              <div class="item-header">
                <span class="item-name">{{ app.name }}（:{{ app.port }}）</span>
              </div>
              <div class="item-meta">
                <span>{{ app.config?.corsOpen ? '已开启跨域' : '未开启跨域' }}</span>
                <span>·</span>
                <span>{{ app.config?.whitelist?.length ? `白名单 ${app.config.whitelist.length} 条` : '白名单未启用' }}</span>
                <span>·</span>
                <span>{{ app.remark || '暂无描述' }}</span>
              </div>
            </div>
            <div class="item-actions">
              <SimpleSwitch
                :checked="app.online === 1"
                :loading="toggleLoadingMap[app.id!]"
                size="small"
                @change="handleToggle(app)"
              />
              <span class="actions-divider" />
              <ATooltip title="编辑" v-permission="['TENANT_EDITOR','TENANT_ADMIN','TENANT_OWNER']">
                <AButton type="text" @click="handleEdit(app)">
                  <template #icon><EditOutlined /></template>
                </AButton>
              </ATooltip>
              <ATooltip title="删除" v-permission="['TENANT_EDITOR','TENANT_ADMIN','TENANT_OWNER']">
                <AButton type="text" danger @click="handleDelete(app)">
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
      :title="isEditForm ? '编辑应用' : '新增应用'"
      :confirm-loading="formLoading"
      destroyOnClose
      defaultWidth="720px"
      @ok="handleSubmit"
      @cancel="formVisible = false"
      @update:open="(v: boolean) => (formVisible = v)"
    >
      <AForm layout="vertical">
        <ARow :gutter="16">
          <ACol :span="12">
            <AFormItem label="应用名称" required>
              <AInput v-model:value="formData.name" placeholder="填写应用名称" :maxlength="100" />
            </AFormItem>
          </ACol>
          <ACol :span="12">
            <AFormItem label="监听端口" required extra="范围 1024-65535，全局唯一，在线时不可修改">
              <AInputNumber
                v-model:value="formData.port"
                :min="1024"
                :max="65535"
                style="width: 100%"
                placeholder="如 18080"
                :disabled="isEditForm && formData.online === 1"
              />
            </AFormItem>
          </ACol>
        </ARow>
        <ARow :gutter="16">
          <ACol :span="12">
            <AFormItem label="跨域访问">
              <ASwitch v-model:checked="formData.config!.corsOpen" />
            </AFormItem>
          </ACol>
          <ACol :span="12">
            <AFormItem v-if="formData.config!.corsOpen" label="允许的来源（* 表示全部）">
              <AInput v-model:value="formData.config!.allowedOrigin" placeholder="*" />
            </AFormItem>
          </ACol>
        </ARow>
        <ARow :gutter="16">
          <ACol :span="12">
            <AFormItem label="请求体大小上限（字节，0表示不限制）">
              <AInputNumber v-model:value="formData.config!.contentLength" :min="0" style="width: 100%" />
            </AFormItem>
          </ACol>
          <ACol :span="12">
            <AFormItem label="描述">
              <AInput v-model:value="formData.remark" placeholder="填写应用描述" :maxlength="500" />
            </AFormItem>
          </ACol>
        </ARow>

        <!-- 访问白名单（全宽内联编辑） -->
        <div class="whitelist-section">
          <div class="whitelist-header">
            <span class="whitelist-title">访问白名单</span>
            <span class="whitelist-tip text-secondary">留空表示不限制来源IP，配置后仅允许命中IP访问</span>
          </div>
          <div v-if="formData.config!.whitelist!.length" class="whitelist-rows">
            <div v-for="(item, idx) in formData.config!.whitelist" :key="idx" class="whitelist-row">
              <AInput
                v-model:value="item.ip"
                class="whitelist-ip"
                placeholder="IP，如 192.168.1.100"
              />
              <AInput
                v-model:value="item.remark"
                class="whitelist-remark"
                placeholder="描述（选填）"
                :maxlength="200"
              />
              <AButton type="text" danger @click="removeWhitelistRow(idx)">
                <template #icon><DeleteOutlined /></template>
              </AButton>
            </div>
          </div>
          <AButton type="dashed" block @click="addWhitelistRow">
            <template #icon><PlusOutlined /></template>
            添加白名单
          </AButton>
        </div>
      </AForm>
    </ApboaModal>
  </div>
</template>

<script lang="ts">
export default {
  name: 'ApiServiceApps'
}
</script>

<style scoped lang="scss">
@use '@/styles/api-service/manage.scss' as *;

.app-item {
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
  font-size: 22px;
  color: var(--color-text-secondary);
}

.item-port {
  font-family: monospace;
}

.whitelist-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-top: 4px;
}

.whitelist-header {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.whitelist-title {
  font-weight: 500;
  color: var(--color-text-primary);
}

.whitelist-tip {
  font-size: 12px;
}

.whitelist-rows {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.whitelist-row {
  display: flex;
  align-items: center;
  gap: 8px;

  .whitelist-ip {
    flex: 0 0 220px;
  }

  .whitelist-remark {
    flex: 1;
  }
}
</style>
