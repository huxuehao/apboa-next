/**
 * API服务编辑页面
 * 支持新增和编辑模式：基础信息（含自维护分类）、绑定已发布工作流、
 * 请求参数到工作流输入的映射、鉴权与限流配置
 *
 * @author huxuehao
 */
<script setup lang="ts">
import { ref, computed, onMounted, watch, defineComponent } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import type { GatewayApi, GatewayApiConfig, GatewayApiParam, GatewayApp } from '@/types/apiService'
import * as apiServiceApi from '@/api/apiService'
import * as workflowApi from '@/api/workflow'
import TargetSelector from '@/components/automation/TargetSelector.vue'

interface TargetItem {
  id: string
  name: string
  description?: string
}

/** 工作流开始节点参数定义 */
interface WorkflowParamDef {
  name: string
  type: string
  required: boolean
}

const router = useRouter()
const route = useRoute()

const apiId = computed(() => route.params.id as string | undefined)
const isEdit = computed(() => !!apiId.value)
const pageTitle = computed(() => isEdit.value ? '编辑-API服务' : '新增-API服务')

const loading = ref(false)
const saving = ref(false)
const loadingApiData = ref(false)

// 基础信息
const apiName = ref('')
const category = ref<string>('')
const remark = ref('')
const appId = ref<string | undefined>(undefined)
const method = ref<string>('GET')
const path = ref('')

// 工作流绑定
const selectedWorkflow = ref<TargetItem | null>(null)
const workflowParams = ref<WorkflowParamDef[]>([])
const workflowConfigLoading = ref(false)

// 参数映射
const params = ref<GatewayApiParam[]>([])
const wholeBodyParam = ref<string>('')

// 鉴权与限流
const authType = ref<'TOKEN' | 'NONE'>('TOKEN')
const limitType = ref<'NONE' | 'MINUTE' | 'HOUR' | 'DAY'>('NONE')
const routeTimes = ref<number | null>(null)
const ipTimes = ref<number | null>(null)

// 数据源
const apps = ref<GatewayApp[]>([])
const categories = ref<string[]>([])
const newCategoryName = ref('')

const methodOptions = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'ALL'].map(m => ({ label: m, value: m }))
const positionOptions = [
  { label: 'PATH', value: 'PATH' },
  { label: 'QUERY', value: 'QUERY' },
  { label: 'HEADER', value: 'HEADER' },
  { label: 'BODY', value: 'BODY' }
]
const typeOptions = ['STRING', 'INTEGER', 'LONG', 'DOUBLE', 'BOOLEAN', 'OBJECT', 'ARRAY'].map(t => ({ label: t, value: t }))
const limitOptions = [
  { label: '不限制', value: 'NONE' },
  { label: '每分钟', value: 'MINUTE' },
  { label: '每小时', value: 'HOUR' },
  { label: '每天', value: 'DAY' }
]

/**
 * 分类下拉过滤（支持将输入词置顶为新分类候选）
 */
const filteredCategories = computed(() => categories.value)

/**
 * 透传下拉菜单虚拟节点（自维护分类控件）
 */
const VNodes = defineComponent({
  props: {
    vnodes: {
      type: Object,
      required: true,
    },
  },
  render() {
    return this.vnodes
  },
})

/**
 * 直接在下拉中新增分类并选中
 */
function addCategory(e: Event) {
  e.preventDefault()
  if (!newCategoryName.value) return
  if (!categories.value.includes(newCategoryName.value)) {
    categories.value.push(newCategoryName.value)
  }
  category.value = newCategoryName.value
  newCategoryName.value = ''
}

/**
 * 加载数据源（应用与分类）
 */
async function loadOptions() {
  try {
    const [appRes, categoryRes] = await Promise.all([
      apiServiceApi.listApps(),
      apiServiceApi.getCategories()
    ])
    apps.value = appRes.data.data || []
    categories.value = categoryRes.data.data || []
  } catch (e) {
    console.error('加载数据源失败:', e)
  }
}

/**
 * 加载工作流开始节点参数定义（用于映射下拉提示）
 */
async function loadWorkflowConfig(workflowId: string) {
  workflowConfigLoading.value = true
  try {
    const res = await workflowApi.workflowDetail(workflowId)
    const detail = res.data.data
    if (!detail?.workflow?.config) {
      workflowParams.value = []
      return
    }
    const config = typeof detail.workflow.config === 'string'
      ? JSON.parse(detail.workflow.config)
      : detail.workflow.config
    const nodes = config.nodes || []
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const startNode = nodes.find((n: Record<string, any>) => n?.type === 'START')
    if (startNode?.config?.params) {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      workflowParams.value = startNode.config.params.map((p: Record<string, any>) => ({
        name: p.name || '',
        type: p.type || 'String',
        required: p.required ?? false
      }))
    } else {
      workflowParams.value = []
    }
  } catch (e) {
    console.error('加载工作流配置失败:', e)
  } finally {
    workflowConfigLoading.value = false
  }
}

/**
 * 添加参数映射行
 */
function addParam() {
  params.value.push({
    position: 'QUERY',
    key: '',
    type: 'STRING',
    required: false,
    defaultVal: undefined,
    workflowParam: undefined,
    remark: undefined
  })
}

function removeParam(index: number) {
  params.value.splice(index, 1)
}

/**
 * 一键按工作流参数生成映射行（未映射的工作流参数）
 */
function generateFromWorkflow() {
  if (workflowParams.value.length === 0) {
    message.warning('当前工作流没有定义输入参数')
    return
  }
  const mapped = new Set(params.value.map(p => p.workflowParam || p.key))
  workflowParams.value
    .filter(wp => !mapped.has(wp.name))
    .forEach(wp => {
      params.value.push({
        position: 'QUERY',
        key: wp.name,
        type: mapWorkflowType(wp.type),
        required: wp.required,
        defaultVal: undefined,
        workflowParam: wp.name,
        remark: undefined
      })
    })
}

/**
 * 工作流参数类型到网关参数类型的映射
 */
function mapWorkflowType(type: string): GatewayApiParam['type'] {
  switch (type) {
    case 'Integer': return 'INTEGER'
    case 'Long': return 'LONG'
    case 'Float':
    case 'Double': return 'DOUBLE'
    case 'Boolean': return 'BOOLEAN'
    case 'Object': return 'OBJECT'
    case 'Array': return 'ARRAY'
    default: return 'STRING'
  }
}

/**
 * 加载API数据（编辑模式）
 */
async function loadApiData() {
  if (!apiId.value) return
  loadingApiData.value = true
  loading.value = true
  try {
    const res = await apiServiceApi.getApi(apiId.value)
    const api = res.data.data
    if (!api) {
      message.error('API不存在')
      router.back()
      return
    }
    apiName.value = api.name || ''
    category.value = api.category || ''
    remark.value = api.remark || ''
    appId.value = api.appId
    method.value = api.method || 'GET'
    path.value = api.path || ''

    if (api.workflowId) {
      selectedWorkflow.value = { id: api.workflowId, name: api.workflowName || '' }
      await loadWorkflowConfig(api.workflowId)
    }

    const config = api.config || ({} as GatewayApiConfig)
    authType.value = config.authType || 'TOKEN'
    limitType.value = config.limitType || 'NONE'
    routeTimes.value = config.routeTimes ?? null
    ipTimes.value = config.ipTimes ?? null
    params.value = config.params || []
    wholeBodyParam.value = config.wholeBodyParam || ''
  } catch (e) {
    console.error('加载API数据失败:', e)
  } finally {
    loading.value = false
    loadingApiData.value = false
  }
}

/**
 * 工作流选择变化后刷新参数定义
 */
watch(selectedWorkflow, (target) => {
  if (loadingApiData.value) return
  if (target) {
    loadWorkflowConfig(target.id)
  } else {
    workflowParams.value = []
  }
})

/**
 * 保存API
 */
async function handleSave() {
  if (!apiName.value.trim()) {
    message.warning('请填写API名称')
    return
  }
  if (!appId.value) {
    message.warning('请选择所属应用')
    return
  }
  if (!path.value.trim() || !path.value.startsWith('/')) {
    message.warning('请填写以 / 开头的路由路径')
    return
  }
  if (!selectedWorkflow.value) {
    message.warning('请绑定已发布的工作流')
    return
  }
  for (const p of params.value) {
    if (!p.key || !p.key.trim()) {
      message.warning('存在未命名的参数定义')
      return
    }
    if (p.position === 'PATH' && !path.value.includes(`:${p.key}`)) {
      message.warning(`PATH参数 ${p.key} 未出现在路由路径中`)
      return
    }
  }
  if (limitType.value !== 'NONE' && !(routeTimes.value || ipTimes.value)) {
    message.warning('已开启访问限制，请至少填写一项限制次数')
    return
  }

  saving.value = true
  try {
    const config: GatewayApiConfig = {
      authType: authType.value,
      limitType: limitType.value,
      routeTimes: routeTimes.value ?? undefined,
      ipTimes: ipTimes.value ?? undefined,
      params: params.value,
      wholeBodyParam: wholeBodyParam.value.trim() || undefined
    }
    const api: GatewayApi = {
      id: apiId.value || undefined,
      appId: appId.value,
      category: category.value || undefined,
      name: apiName.value.trim(),
      remark: remark.value || undefined,
      method: method.value as GatewayApi['method'],
      path: path.value.trim(),
      config,
      workflowId: selectedWorkflow.value.id
    }

    if (isEdit.value) {
      await apiServiceApi.updateApi(api)
      message.success('更新成功')
    } else {
      await apiServiceApi.addApi(api)
      message.success('创建成功')
    }
    await router.push('/api-service')
  } catch (e) {
    console.error('保存失败:', e)
  } finally {
    saving.value = false
  }
}

function handleBack() {
  router.push('/api-service')
}

onMounted(async () => {
  await loadOptions()
  if (isEdit.value) {
    loadApiData()
  }
})
</script>

<template>
  <div class="api-service-editor">
    <!-- 顶部导航栏 -->
    <div class="editor-header">
      <div class="header-left">
        <h2 class="header-title">{{ pageTitle }}</h2>
      </div>
      <div class="header-right">
        <AButton @click="handleBack">取消</AButton>
        <AButton type="primary" :loading="saving" @click="handleSave">
          保存
        </AButton>
      </div>
    </div>

    <!-- 内容区 -->
    <ApboaSpin :spinning="loading">
      <div class="editor-content">
        <!-- 基础信息卡片 -->
        <div class="config-card">
          <div class="card-header">
            <span class="card-title">基础信息</span>
          </div>
          <div class="card-body">
            <div class="form-row-inline">
              <div class="form-row">
                <div class="form-label">API名称</div>
                <div class="form-control">
                  <AInput v-model:value="apiName" placeholder="填写API名称" :maxlength="100" />
                </div>
              </div>
              <div class="form-row">
                <div class="form-label">分类</div>
                <div class="form-control">
                  <ASelect
                    v-model:value="category"
                    placeholder="选择或输入分类"
                    allow-clear
                    style="width: 100%"
                  >
                    <ASelectOption v-for="cat in filteredCategories" :key="cat" :value="cat">
                      {{ cat }}
                    </ASelectOption>
                    <template #dropdownRender="{ menuNode: menu }">
                      <VNodes :vnodes="menu" />
                      <ADivider style="margin: 4px 0" />
                      <ASpace style="padding: 4px 8px">
                        <AInput v-model:value="newCategoryName" style="width: 240px" placeholder="请输入新分类" />
                        <AButton type="text" @click="addCategory">
                          <template #icon>
                            <PlusOutlined />
                          </template>
                          添加
                        </AButton>
                      </ASpace>
                    </template>
                  </ASelect>
                </div>
              </div>
            </div>

            <div class="form-row">
              <div class="form-label">描述</div>
              <div class="form-control">
                <ATextarea v-model:value="remark" placeholder="填写API描述" :rows="2" :maxlength="500" />
              </div>
            </div>

            <div class="form-row">
              <div class="form-label">所属应用（服务端口）</div>
              <div class="form-control">
                <ASelect v-model:value="appId" placeholder="选择网关应用" style="width: 100%">
                  <ASelectOption v-for="app in apps" :key="app.id" :value="app.id">
                    {{ app.name }}（:{{ app.port }}）
                  </ASelectOption>
                </ASelect>
              </div>
            </div>

            <div class="form-row-inline">
              <div class="form-row">
                <div class="form-label">请求方法</div>
                <div class="form-control">
                  <ASegmented v-model:value="method" :options="methodOptions" />
                </div>
              </div>
              <div class="form-row">
                <div class="form-label">路由路径（支持 :param 路径参数，如 /order/:orderId）</div>
                <div class="form-control">
                  <AInput v-model:value="path" placeholder="/example/:id" :maxlength="255" />
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 工作流绑定卡片 -->
        <div class="config-card">
          <div class="card-header">
            <span class="card-title">绑定工作流</span>
            <span class="card-desc">API请求将触发所绑定工作流的最新已发布版本</span>
          </div>
          <div class="card-body">
            <div class="form-row">
              <div class="form-label">选择已发布的工作流</div>
              <div class="form-control">
                <TargetSelector target-type="WORKFLOW" v-model="selectedWorkflow" />
              </div>
            </div>
            <div v-if="selectedWorkflow" class="form-row">
              <div class="form-label">工作流输入参数</div>
              <div class="form-control">
                <ApboaSpin :spinning="workflowConfigLoading">
                  <template v-if="workflowParams.length > 0">
                    <ATag v-for="wp in workflowParams" :key="wp.name" style="margin-bottom: 4px">
                      {{ wp.name }}（{{ wp.type }}{{ wp.required ? '，必填' : '' }}）
                    </ATag>
                  </template>
                  <span v-else class="param-empty">该工作流未定义输入参数</span>
                </ApboaSpin>
              </div>
            </div>
          </div>
        </div>

        <!-- 参数映射卡片 -->
        <div class="config-card">
          <div class="card-header">
            <span class="card-title">请求参数映射</span>
            <span class="card-desc">将请求中的 PATH、QUERY、HEADER、BODY 参数转换为工作流输入参数</span>
          </div>
          <div class="card-body">
            <div class="param-table">
              <div v-if="params.length > 0" class="param-header-row">
                <span>位置</span>
                <span>参数名</span>
                <span>类型</span>
                <span>必填</span>
                <span>默认值</span>
                <span>映射工作流参数</span>
                <span></span>
              </div>
              <div v-for="(param, index) in params" :key="index" class="param-row">
                <ASelect v-model:value="param.position" :options="positionOptions" />
                <AInput v-model:value="param.key" placeholder="参数名" />
                <ASelect v-model:value="param.type" :options="typeOptions" />
                <ACheckbox v-model:checked="param.required" />
                <AInput v-model:value="param.defaultVal" placeholder="默认值（可选）" />
                <ASelect
                  v-model:value="param.workflowParam"
                  placeholder="默认同参数名"
                  allow-clear
                  show-search
                >
                  <ASelectOption v-for="wp in workflowParams" :key="wp.name" :value="wp.name">
                    {{ wp.name }}
                  </ASelectOption>
                </ASelect>
                <AButton type="text" danger @click="removeParam(index)">
                  <template #icon><DeleteOutlined /></template>
                </AButton>
              </div>
              <div v-if="params.length === 0" class="param-empty">
                暂无参数定义，可手动添加或根据工作流参数一键生成
              </div>
            </div>

            <ASpace class="param-add">
              <AButton @click="addParam">
                <template #icon><PlusOutlined /></template>
                添加参数
              </AButton>
              <AButton :disabled="!selectedWorkflow" @click="generateFromWorkflow">
                根据工作流参数生成
              </AButton>
            </ASpace>

            <div class="form-row" style="margin-top: 24px">
              <div class="form-label">整体请求体映射（可选，将整个JSON请求体作为一个工作流参数传入）</div>
              <div class="form-control">
                <ASelect
                  v-model:value="wholeBodyParam"
                  placeholder="选择接收整体请求体的工作流参数"
                  allow-clear
                  show-search
                  style="width: 100%"
                >
                  <ASelectOption v-for="wp in workflowParams" :key="wp.name" :value="wp.name">
                    {{ wp.name }}
                  </ASelectOption>
                </ASelect>
              </div>
            </div>
          </div>
        </div>

        <!-- 鉴权与限流卡片 -->
        <div class="config-card">
          <div class="card-header">
            <span class="card-title">鉴权与限流</span>
          </div>
          <div class="card-body">
            <div class="form-row-inline">
              <div class="form-row">
                <div class="form-label">鉴权方式</div>
                <div class="form-control">
                  <ASegmented
                    v-model:value="authType"
                    :options="[
                      { label: '平台鉴权', value: 'TOKEN' },
                      { label: '免鉴权', value: 'NONE' }
                    ]"
                  />
                </div>
              </div>
            </div>

            <AAlert
              v-if="authType === 'TOKEN'"
              type="info"
              show-icon
              style="margin-bottom: 24px"
              message="平台鉴权复用统一凭证体系：调用时在 Authorization 请求头携带平台登录Token（Bearer xxx）或已注册的SK（sk-xxx），且凭证需与本API属于同一租户。SK可在 设置 - API Keys 中创建。"
            />

            <div class="form-row">
              <div class="form-label">访问限制（固定时间窗）</div>
              <div class="form-control">
                <ASegmented v-model:value="limitType" :options="limitOptions" />
              </div>
            </div>

            <div class="form-row-inline" v-if="limitType !== 'NONE'">
              <div class="form-row">
                <div class="form-label">API总访问次数上限（0或留空不限制）</div>
                <div class="form-control">
                  <AInputNumber v-model:value="routeTimes" :min="0" style="width: 100%" placeholder="如 10000" />
                </div>
              </div>
              <div class="form-row">
                <div class="form-label">单IP访问次数上限（0或留空不限制）</div>
                <div class="form-control">
                  <AInputNumber v-model:value="ipTimes" :min="0" style="width: 100%" placeholder="如 100" />
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </ApboaSpin>
  </div>
</template>

<script lang="ts">
export default {
  name: 'ApiServiceEditor'
}
</script>

<style scoped lang="scss">
@use '@/styles/api-service/editor.scss' as *;
</style>
