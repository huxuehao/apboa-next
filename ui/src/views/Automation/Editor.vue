/**
 * 自动化任务编辑页面
 * 支持新增和编辑模式，根据目标类型（Agent/Workflow）动态显示不同的配置表单
 *
 * @author huxuehao
 */
<script setup lang="ts">
/* eslint-disable @typescript-eslint/no-explicit-any */
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import type { JobInfo } from '@/types'
import * as automationApi from '@/api/automation'
import * as workflowApi from '@/api/workflow'
import CronBuilder from '@/components/automation/CronBuilder.vue'
import TargetSelector from '@/components/automation/TargetSelector.vue'
import WorkflowInputConfig from '@/components/automation/WorkflowInputConfig.vue'

interface TargetItem {
  id: string
  name: string
  description?: string
}

const router = useRouter()
const route = useRoute()

const jobId = computed(() => route.params.id as string | undefined)
const isEdit = computed(() => !!jobId.value)
const pageTitle = computed(() => isEdit.value ? '编辑-自动化任务' : '新增-自动化任务')

const loading = ref(false)
const saving = ref(false)
const loadingJobData = ref(false)
const targetType = ref<'AGENT' | 'WORKFLOW'>('AGENT')
const selectedTarget = ref<TargetItem | null>(null)
const cron = ref('0 0 * * * ?')
const jobName = ref('')

// Agent 配置
const agentUserPrompt = ref('')

// 编辑模式下从DB加载的原始enabled值
const editEnabled = ref(true)

// Workflow 配置
const workflowParams = ref<Array<{ name: string; type: string; value: unknown; required: boolean }>>([])
const workflowVariables = ref<Record<string, unknown>>({})
const workflowConfigLoading = ref(false)

/**
 * 加载工作流配置（开始节点参数和自定义变量）
 */
async function loadWorkflowConfig(workflowId: string) {
  workflowConfigLoading.value = true
  try {
    const res = await workflowApi.workflowDetail(workflowId)
    const detail = res.data.data
    if (!detail?.workflow?.config) return

    const config = typeof detail.workflow.config === 'string' ? JSON.parse(detail.workflow.config) : detail.workflow.config
    const nodes = config.nodes || []

    // 获取开始节点的参数定义
    const startNode = nodes.find((n: Record<string, any>) => n?.type === 'START')
    if (startNode?.config?.params) {
      workflowParams.value = startNode.config.params.map((p: Record<string, any>) => ({
        name: p.name || '',
        type: p.type || 'String',
        value: p.value ?? getDefaultValue(p.type || 'String'),
        required: p.required ?? false
      }))
    } else {
      workflowParams.value = []
    }

    // 获取自定义变量定义
    if (config.variables && Array.isArray(config.variables)) {
      const vars: Record<string, unknown> = {}
      config.variables.forEach((v: Record<string, any>) => {
        vars[v.name] = v.defaultValue ?? getDefaultValue(v.type || 'String')
      })
      workflowVariables.value = vars
    } else {
      workflowVariables.value = {}
    }
  } catch (e) {
    console.error('加载工作流配置失败:', e)
  } finally {
    workflowConfigLoading.value = false
  }
}

/**
 * 根据类型获取默认值
 */
function getDefaultValue(type: string): unknown {
  switch (type) {
    case 'Boolean': return false
    case 'Long':
    case 'Integer':
    case 'Float':
    case 'Double': return 0
    case 'Array': return '[]'
    case 'Object': return '{}'
    default: return ''
  }
}

/**
 * 加载任务数据（编辑模式）
 */
async function loadJobData() {
  if (!jobId.value) return

  loadingJobData.value = true
  loading.value = true
  try {
    // 通过ID获取最新数据（包括enabled等字段）
    const res = await automationApi.getJobById(jobId.value)
    const job = res.data.data
    if (!job) {
      message.error('任务不存在')
      router.back()
      return
    }

    targetType.value = (job.type as 'AGENT' | 'WORKFLOW') || 'AGENT'
    cron.value = job.cron || '0 0 * * * ?'
    editEnabled.value = job.enabled ?? true

    try {
      const dataMap = JSON.parse(job.dataMap || '{}')
      jobName.value = dataMap.jobName || ''
      selectedTarget.value = {
        id: job.bizId,
        name: dataMap.bizName || ''
      }

      if (job.type === 'AGENT') {
        // 优先读新格式 userPrompt，兼容旧格式 inputs.userPrompt
        agentUserPrompt.value = dataMap.userPrompt || dataMap.inputs?.userPrompt || ''
      } else if (job.type === 'WORKFLOW') {
        // 加载工作流配置
        await loadWorkflowConfig(job.bizId)
        // 恢复之前保存的参数值（优先读新格式 params，兼容旧格式 inputs）
        const savedParams = dataMap.params || dataMap.inputs
        if (savedParams) {
          workflowParams.value = workflowParams.value.map(p => ({
            ...p,
            value: savedParams[p.name] ?? p.value
          }))
        }
        // 恢复之前保存的变量值
        if (dataMap.variables) {
          workflowVariables.value = { ...workflowVariables.value, ...dataMap.variables }
        }
      }
    } catch {
      // ignore
    }
  } catch (e) {
    console.error('加载任务数据失败:', e)
  } finally {
    loading.value = false
    loadingJobData.value = false
  }
}

/**
 * 目标类型变化时，清空已选择的目标（编辑模式下不触发，由 loadJobData 负责恢复）
 */
watch(targetType, () => {
  if (isEdit.value) return
  selectedTarget.value = null
  // 重置 Workflow 配置
  workflowParams.value = []
  workflowVariables.value = {}
})

/**
 * 目标选择变化
 */
watch(selectedTarget, (newTarget) => {
  if (loadingJobData.value) return
  if (newTarget && targetType.value === 'WORKFLOW') {
    loadWorkflowConfig(newTarget.id)
  } else if (!newTarget) {
    // 清空时重置配置
    workflowParams.value = []
    workflowVariables.value = {}
  }
})

/**
 * 保存任务
 */
async function handleSave() {
  // 校验
  if (!selectedTarget.value) {
    message.warning('请选择目标')
    return
  }

  saving.value = true
  try {
    // 构建 dataMap
    const dataMap: Record<string, unknown> = {
      jobName: jobName.value,
      bizName: selectedTarget.value.name
    }

    if (targetType.value === 'AGENT') {
      dataMap.userPrompt = agentUserPrompt.value
    } else {
      // Workflow: 保存参数值和变量值
      const params: Record<string, unknown> = {}
      workflowParams.value.forEach(p => {
        params[p.name] = p.value
      })
      dataMap.params = params
      dataMap.variables = workflowVariables.value
    }
    dataMap.bizId = selectedTarget.value.id
    dataMap.type =  targetType.value

    const jobClass = targetType.value === 'AGENT'
      ? 'com.hxh.apboa.scheduler.scheduler.AgentScheduler'
      : 'com.hxh.apboa.scheduler.scheduler.WorkflowScheduler'

    const jobInfo: JobInfo = {
      id: jobId.value || undefined,
      type: targetType.value,
      bizId: selectedTarget.value.id,
      cron: cron.value,
      jobClass,
      dataMap: JSON.stringify(dataMap),
      enabled: isEdit.value ? editEnabled.value : true
    }

    if (isEdit.value) {
      await automationApi.updateJob(jobInfo)
      message.success('更新成功')
    } else {
      await automationApi.addJob(jobInfo)
      message.success('创建成功')
    }

    await router.push('/automation')
  } catch (e) {
    console.error('保存失败:', e)
  } finally {
    saving.value = false
  }
}

/**
 * 返回列表
 */
function handleBack() {
  router.push('/automation')
}

onMounted(() => {
  if (isEdit.value) {
    loadJobData()
  }
})
</script>

<template>
  <div class="automation-editor">
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
            <!-- 任务名称 -->
            <div class="form-row">
              <div class="form-label">任务名称</div>
              <div class="form-control">
                <AInput
                  v-model:value="jobName"
                  placeholder="填写任务名称"
                />
              </div>
            </div>
            <!-- 目标类型 -->
            <div class="form-row">
              <div class="form-label">目标类型</div>
              <div class="form-control">
                <ASegmented
                  v-model:value="targetType"
                  :options="[
                    { label: '智能体', value: 'AGENT' },
                    { label: '工作流', value: 'WORKFLOW' }
                  ]"
                  :disabled="isEdit"
                />
              </div>
            </div>

            <!-- 目标选择 -->
            <div class="form-row">
              <div class="form-label">选择目标</div>
              <div class="form-control">
                <TargetSelector
                  :target-type="targetType"
                  v-model="selectedTarget"
                />
              </div>
            </div>

          </div>
        </div>

        <!-- Agent 输入配置 -->
        <div v-if="targetType === 'AGENT'" class="config-card">
          <div class="card-header">
            <span class="card-title">输入配置</span>
          </div>
          <div class="card-body">
            <div class="form-row">
              <div class="form-control">
                <ATextarea
                  v-model:value="agentUserPrompt"
                  placeholder="请输入发送给智能体的消息内容"
                  :rows="6"
                  show-count
                  :maxlength="2000"
                />
              </div>
            </div>
          </div>
        </div>

        <!-- Workflow 输入配置 -->
        <div v-if="targetType === 'WORKFLOW'" class="config-card">
          <div class="card-header">
            <span class="card-title">输入配置</span>
          </div>
          <div class="card-body">
            <ApboaSpin :spinning="workflowConfigLoading">
              <WorkflowInputConfig
                v-model:params="workflowParams"
                v-model:variables="workflowVariables"
              />
            </ApboaSpin>
          </div>
        </div>

        <!-- 执行策略卡片 -->
        <div class="config-card">
          <div class="card-header">
            <span class="card-title">执行策略</span>
          </div>
          <div class="card-body">
            <CronBuilder v-model="cron" />
          </div>
        </div>
      </div>
    </ApboaSpin>
  </div>
</template>

<script lang="ts">
export default {
  name: 'AutomationEditor'
}
</script>

<style scoped lang="scss">
@use '@/styles/automation/editor.scss' as *;
</style>
