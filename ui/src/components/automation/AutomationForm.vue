/**
 * 自动化任务表单弹窗
 * 用于新增和编辑自动化任务
 *
 * @component
 */
<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { message } from 'ant-design-vue'
import type { JobInfo } from '@/types'
import CronBuilder from './CronBuilder.vue'
import TargetSelector from './TargetSelector.vue'
import * as automationApi from '@/api/automation'

interface TargetItem {
  id: string
  name: string
  description?: string
}

const props = defineProps<{
  data?: JobInfo | null
}>()

const emit = defineEmits<{
  success: []
}>()

const visible = defineModel<boolean>('visible', { default: false })

const loading = ref(false)
const formRef = ref()
const targetType = ref<'AGENT' | 'WORKFLOW'>('AGENT')
const selectedTarget = ref<TargetItem | null>(null)
const cron = ref('0 0 * * * ?')
const inputText = ref('')
const remark = ref('')

const isEdit = computed(() => !!props.data?.id)
const modalTitle = computed(() => isEdit.value ? '编辑自动化任务' : '新增自动化任务')

/**
 * 初始化表单数据
 */
function initForm() {
  if (props.data) {
    targetType.value = (props.data.type as 'AGENT' | 'WORKFLOW') || 'AGENT'
    cron.value = props.data.cron || '0 0 * * * ?'
    try {
      const dataMap = JSON.parse(props.data.dataMap || '{}')
      inputText.value = dataMap.userPrompt || dataMap.input || ''
      selectedTarget.value = {
        id: props.data.bizId,
        name: dataMap.targetName || ''
      }
    } catch {
      inputText.value = ''
      selectedTarget.value = null
    }
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    remark.value = (props.data as Record<string, any>).remark || ''
  } else {
    targetType.value = 'AGENT'
    cron.value = '0 0 * * * ?'
    inputText.value = ''
    selectedTarget.value = null
    remark.value = ''
  }
}

/**
 * 提交表单
 */
async function handleSubmit() {
  // 校验
  if (!selectedTarget.value) {
    message.warning('请选择目标')
    return
  }

  loading.value = true
  try {
    const dataMap = JSON.stringify({
      targetName: selectedTarget.value.name,
      userPrompt: targetType.value === 'AGENT' ? inputText.value : undefined,
      input: targetType.value === 'AGENT' ? inputText.value : undefined
    })

    const jobClass = targetType.value === 'AGENT'
      ? 'com.hxh.apboa.scheduler.scheduler.AgentScheduler'
      : 'com.hxh.apboa.scheduler.scheduler.WorkflowScheduler'

    const jobInfo: JobInfo = {
      id: props.data?.id || undefined,
      type: targetType.value,
      bizId: selectedTarget.value.id,
      cron: cron.value,
      jobClass,
      dataMap,
      enabled: props.data?.enabled ?? true
    }

    if (isEdit.value) {
      await automationApi.updateJob(jobInfo)
      message.success('更新成功')
    } else {
      await automationApi.addJob(jobInfo)
      message.success('创建成功')
    }

    visible.value = false
    emit('success')
  } catch (e) {
    console.error('保存失败:', e)
  } finally {
    loading.value = false
  }
}

watch(visible, (val) => {
  if (val) {
    initForm()
  }
})
</script>

<template>
  <AModal
    v-model:open="visible"
    :title="modalTitle"
    :width="640"
    :confirm-loading="loading"
    @ok="handleSubmit"
    @cancel="visible = false"
  >
    <AForm ref="formRef" layout="vertical" class="automation-form">
      <!-- 目标类型 -->
      <AFormItem label="目标类型" required>
        <ASegmented
          v-model:value="targetType"
          :options="[
            { label: '智能体', value: 'AGENT' },
            { label: '工作流', value: 'WORKFLOW' }
          ]"
          :disabled="isEdit"
        />
      </AFormItem>

      <!-- 目标选择 -->
      <AFormItem label="选择目标" required>
        <TargetSelector
          :target-type="targetType"
          v-model="selectedTarget"
        />
      </AFormItem>

      <!-- 执行策略 -->
      <AFormItem label="执行策略" required>
        <CronBuilder v-model="cron" />
      </AFormItem>

      <!-- 输入内容（仅 Agent） -->
      <AFormItem v-if="targetType === 'AGENT'" label="输入内容">
        <ATextarea
          v-model:value="inputText"
          placeholder="请输入定时任务触发时发送给智能体的消息内容"
          :rows="4"
          show-count
          :maxlength="500"
        />
      </AFormItem>

      <!-- 备注 -->
      <AFormItem label="备注">
        <ATextarea
          v-model:value="remark"
          placeholder="可选，添加备注信息"
          :rows="2"
          :maxlength="200"
        />
      </AFormItem>
    </AForm>
  </AModal>
</template>

<style scoped lang="scss">
.automation-form {
  max-height: 60vh;
  overflow-y: auto;
  padding-right: 8px;
}
</style>
