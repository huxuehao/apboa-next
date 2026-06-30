<script setup lang="ts">
import { reactive, watch } from 'vue'
import { message } from 'ant-design-vue'

const open = defineModel<boolean>('open', { default: false })

const props = withDefaults(
  defineProps<{
    mode: 'create' | 'edit'
    initial?: {
      name?: string
      remark?: string
    }
    loading?: boolean
  }>(),
  {
    loading: false,
  },
)

const emit = defineEmits<{
  submit: [payload: { name: string; remark?: string }]
}>()

const form = reactive({
  name: '',
  remark: '',
})

watch(
  () => [open.value, props.initial],
  () => {
    if (!open.value) return
    form.name = props.initial?.name || ''
    form.remark = props.initial?.remark || ''
  },
  { immediate: true },
)

function handleOk() {
  const name = form.name.trim()
  if (!name) {
    message.warning('请输入工作流名称')
    return
  }
  emit('submit', {
    name,
    remark: form.remark.trim() || undefined,
  })
}
</script>

<template>
  <AModal
    v-model:open="open"
    :title="mode === 'create' ? '新建工作流' : '编辑工作流'"
    :confirm-loading="loading"
    :ok-text="mode === 'create' ? '创建并设计' : '保存'"
    cancel-text="取消"
    destroy-on-close
    @ok="handleOk"
  >
    <AForm layout="vertical" class="workflow-info-form">
      <AFormItem label="工作流名称" required>
        <AInput
          v-model:value="form.name"
          allow-clear
          :maxlength="80"
          placeholder="请输入一个清晰、可识别的工作流名称"
          @press-enter="handleOk"
        />
      </AFormItem>
      <AFormItem label="描述信息">
        <ATextarea
          v-model:value="form.remark"
          allow-clear
          :maxlength="300"
          :auto-size="{ minRows: 4, maxRows: 7 }"
          placeholder="补充业务用途、触发场景或维护说明，便于后续识别"
        />
      </AFormItem>
    </AForm>
  </AModal>
</template>

<style scoped lang="scss">
.workflow-info-form {
  padding: 4px 2px;
}
</style>
