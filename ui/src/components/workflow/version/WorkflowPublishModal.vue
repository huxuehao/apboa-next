<script setup lang="ts">
import { ref, watch } from 'vue'
import { CloudUploadOutlined } from '@ant-design/icons-vue'

const open = defineModel<boolean>('open', { default: false })

defineProps<{
  workflowName?: string
  loading?: boolean
}>()

const emit = defineEmits<{
  publish: [remark?: string]
}>()

const remark = ref('')

watch(open, (value) => {
  if (value) {
    remark.value = ''
  }
})

function submit() {
  emit('publish', remark.value.trim() || undefined)
}
</script>

<template>
  <AModal
    v-model:open="open"
    title="发布工作流"
    :width="520"
    :confirm-loading="loading"
    ok-text="发布"
    cancel-text="取消"
    @ok="submit"
  >
    <div class="publish-modal">
      <div class="publish-summary">
        <span class="publish-icon">
          <CloudUploadOutlined />
        </span>
        <div class="publish-copy">
          <div class="publish-title">{{ workflowName || '未命名工作流' }}</div>
          <div class="publish-desc">发布后会生成不可变版本，正式运行将使用最新发布版本。</div>
        </div>
      </div>

      <AForm layout="vertical">
        <AFormItem label="发布备注">
          <ATextarea
            v-model:value="remark"
            :rows="4"
            :maxlength="200"
            show-count
            placeholder="例如：补充订单风控节点，调整异常分支处理"
          />
        </AFormItem>
      </AForm>
    </div>
  </AModal>
</template>

<style scoped lang="scss">
.publish-modal {
  display: grid;
  gap: 16px;
}

.publish-summary {
  display: flex;
  gap: 12px;
  padding: 12px;
  border: 1px solid #e6f4ff;
  border-radius: 8px;
  background: #f0f8ff;
}

.publish-icon {
  width: 34px;
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: #1677ff;
  color: #fff;
  flex-shrink: 0;
}

.publish-copy {
  min-width: 0;
}

.publish-title {
  color: #262626;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.publish-desc {
  margin-top: 2px;
  color: #595959;
  font-size: 12px;
  line-height: 1.6;
}
</style>
