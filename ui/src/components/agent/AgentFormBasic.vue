/**
 * 智能体基本信息表单组件
 *
 * @author huxuehao
 */
<script setup lang="ts">
import { ref, computed, defineComponent, watch, nextTick, onMounted, onUnmounted } from 'vue'
import {
  InfoCircleOutlined,
  PlusOutlined,
  DeleteOutlined,
  HolderOutlined,
  SmileOutlined,
  EyeOutlined,
  CameraOutlined
} from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import Sortable from 'sortablejs'
import AvatarCropModal from './AvatarCropModal.vue'
import type { CommonQuestion } from '@/types'
import {
  COMMON_QUESTION_ICONS,
  COMMON_QUESTION_ICON_NAMES,
  COMMON_QUESTION_COLORS,
  MAX_COMMON_QUESTIONS,
  resolveCommonQuestionIcon
} from './commonQuestionIcons'

/**
 * Props定义
 */
const props = defineProps<{
  modelValue: {
    name: string
    agentCode: string
    description: string
    tag: string
    /** 智能体头像（base64 data URL）；父组件未传该字段时不显示头像位（如 A2A 表单） */
    avatar?: string | null
    /** 常用问题列表；父组件未传该字段时不显示编辑器（如 A2A 表单） */
    commonQuestions?: CommonQuestion[]
    /** 常用问题是否在对话中常驻显示 */
    commonQuestionsPinned?: boolean
  }
  tags: string[]
  /** 租户编号，用于作为agentCode前缀 */
  tenantCode?: string
}>()

/**
 * Emits定义
 */
const emit = defineEmits<{
  'update:modelValue': [value: {
    name: string
    agentCode: string
    description: string
    tag: string
    avatar?: string | null
    commonQuestions?: CommonQuestion[]
    commonQuestionsPinned?: boolean
  }]
}>()

const formRef = ref()
const inputRef = ref()
const newTagName = ref('')

/**
 * 表单数据
 */
const formData = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

/**
 * agentCode 后缀计算属性
 * 自动剥离/拼接租户编号前缀，用户只需输入后缀部分
 */
const agentCodeSuffix = computed({
  get: () => {
    const code = formData.value.agentCode
    if (props.tenantCode && code?.startsWith(props.tenantCode + '-')) {
      return code.substring(props.tenantCode.length + 1)
    }
    return code
  },
  set: (val: string) => {
    formData.value = {
      ...formData.value,
      agentCode: props.tenantCode ? props.tenantCode + '-' + val : val
    }
  }
})

/**
 * 过滤后的标签列表
 */
const filteredTags = computed(() => {
  return props.tags || []
})

/**
 * 表单验证规则
 */
const rules = {
  tag: [
    { max: 6, message: '标签长度不能超过6个字符', trigger: 'blur' }
  ],
  name: [
    { required: true, message: '请输入名称', trigger: 'blur' },
    { max: 100, message: '名称长度不能超过100个字符', trigger: 'blur' }
  ],
  agentCode: [
    { required: true, message: '请输入智能体编号', trigger: 'blur' },
    { max: 50, message: '智能体编号长度不能超过50个字符', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_-]+$/, message: '智能体编号只能包含字母、数字、下划线和连字符', trigger: 'blur' }
  ],
  description: [
    { required: true, message: '请输入描述', trigger: 'blur' },
    { max: 500, message: '描述长度不能超过500个字符', trigger: 'blur' }
  ]
}

/**
 * 添加新标签
 */
const addTag = (e: Event) => {
  e.preventDefault()
  if (newTagName.value && !filteredTags.value.includes(newTagName.value)) {
    formData.value.tag = newTagName.value
  }
  newTagName.value = ''
  setTimeout(() => {
    inputRef.value?.focus()
  }, 0)
}

/**
 * 是否启用头像编辑（父组件传入 avatar 字段才启用）
 */
const showAvatar = computed(() => props.modelValue.avatar !== undefined)

const avatarInputRef = ref<HTMLInputElement | null>(null)

// 裁切弹窗状态
const cropVisible = ref(false)
const cropImageUrl = ref('')
const cropIsPng = ref(false)

/** 头像大图预览（antd Image 受控预览） */
const avatarPreviewVisible = ref(false)

/**
 * 选择头像图片：校验后打开裁切弹窗，由用户手动构图
 */
function handleAvatarPick(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (!/^image\/(png|jpe?g|webp)$/.test(file.type)) {
    message.warning('仅支持 PNG/JPG/WebP 图片')
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    message.warning('图片不能超过 5MB')
    return
  }
  if (cropImageUrl.value) URL.revokeObjectURL(cropImageUrl.value)
  cropImageUrl.value = URL.createObjectURL(file)
  cropIsPng.value = file.type === 'image/png'
  cropVisible.value = true
}

/**
 * 裁切确认：base64 存入表单，随「保存」提交
 */
function handleCropConfirm(dataUrl: string) {
  formData.value = { ...props.modelValue, avatar: dataUrl }
}

/**
 * 移除头像（保存后清除）
 */
function removeAvatar() {
  formData.value = { ...props.modelValue, avatar: null }
}

/**
 * 是否启用常用问题编辑（父组件传入 commonQuestions 字段才启用）
 */
const showCommonQuestions = computed(() => props.modelValue.commonQuestions !== undefined)

/**
 * 常用问题对话中常驻开关（默认开）
 */
const questionsPinned = computed({
  get: () => props.modelValue.commonQuestionsPinned !== false,
  set: (v: boolean) => {
    formData.value = { ...props.modelValue, commonQuestionsPinned: v }
  }
})

/**
 * 常用问题列表
 */
const commonQuestions = computed(() => props.modelValue.commonQuestions || [])

// 为每个问题项发放稳定 key：拖拽重排依赖 keyed diff 保证 DOM 与数据一致，
// 字段编辑原地修改不换对象引用，避免输入过程中 DOM 重建导致焦点丢失
let questionKeySeq = 0
const questionKeyMap = new WeakMap<CommonQuestion, number>()
function questionKey(q: CommonQuestion): number {
  let key = questionKeyMap.get(q)
  if (key === undefined) {
    key = ++questionKeySeq
    questionKeyMap.set(q, key)
  }
  return key
}

/**
 * 整体替换常用问题列表（增删/排序场景）
 */
function emitQuestions(list: CommonQuestion[]) {
  formData.value = { ...props.modelValue, commonQuestions: list }
}

/**
 * 添加常用问题
 */
function addQuestion() {
  if (commonQuestions.value.length >= MAX_COMMON_QUESTIONS) return
  emitQuestions([
    ...commonQuestions.value,
    { icon: '', color: COMMON_QUESTION_COLORS[0], title: '', question: '' }
  ])
}

/**
 * 删除常用问题
 */
function removeQuestion(index: number) {
  const list = [...commonQuestions.value]
  list.splice(index, 1)
  emitQuestions(list)
}

/**
 * 原地更新问题项字段（保持对象引用稳定）
 */
function updateQuestion(index: number, patch: Partial<CommonQuestion>) {
  const target = commonQuestions.value[index]
  if (target) {
    Object.assign(target, patch)
  }
}

const questionsListRef = ref<HTMLElement | null>(null)
let questionSortable: ReturnType<typeof Sortable.create> | null = null

/**
 * 初始化常用问题列表拖拽排序
 */
function initQuestionSortable() {
  if (!questionsListRef.value || commonQuestions.value.length === 0) return
  if (questionSortable) return

  questionSortable = Sortable.create(questionsListRef.value, {
    animation: 150,
    handle: '.question-drag-handle',
    ghostClass: 'question-sortable-ghost',
    chosenClass: 'question-sortable-chosen',
    dragClass: 'question-sortable-drag',
    forceFallback: false,
    fallbackClass: 'question-sortable-fallback',
    preventOnFilter: false,
    onStart: () => {
      document.body.classList.add('dragging')
    },
    onEnd: async (evt: { oldIndex?: number; newIndex?: number }) => {
      document.body.classList.remove('dragging')
      const { oldIndex, newIndex } = evt
      if (oldIndex == null || newIndex == null || oldIndex === newIndex) return
      const list = [...commonQuestions.value]
      const [item] = list.splice(oldIndex, 1)
      if (item === undefined) return
      list.splice(newIndex, 0, item)
      emitQuestions(list)
      // 拖拽后销毁并重新初始化 Sortable，确保 DOM 与 Vue 数据同步
      destroyQuestionSortable()
      await nextTick()
      initQuestionSortable()
    }
  })
}

/**
 * 销毁拖拽实例
 */
function destroyQuestionSortable() {
  if (questionSortable) {
    questionSortable.destroy()
    questionSortable = null
  }
}

watch(
  () => commonQuestions.value.length,
  async (len) => {
    destroyQuestionSortable()
    if (len > 0) {
      await nextTick()
      initQuestionSortable()
    }
  }
)

onMounted(async () => {
  if (commonQuestions.value.length > 0) {
    await nextTick()
    initQuestionSortable()
  }
})

onUnmounted(() => {
  destroyQuestionSortable()
  if (cropImageUrl.value) URL.revokeObjectURL(cropImageUrl.value)
})

/**
 * 验证表单
 */
async function validate(): Promise<boolean> {
  try {
    await formRef.value?.validate()
  } catch {
    return false
  }
  const list = props.modelValue.commonQuestions
  if (list && list.some(q => !q.title?.trim() || !q.question?.trim())) {
    message.warning('常用问题的标题与问题内容不能为空')
    return false
  }
  return true
}

/**
 * VNodes组件
 */
const VNodes = defineComponent({
  props: {
    vnodes: {
      type: Object,
      required: true
    }
  },
  render() {
    return this.vnodes
  }
})

defineExpose({
  validate
})
</script>

<template>
  <AForm
    ref="formRef"
    :model="formData"
    :rules="rules"
    layout="vertical"
    style="padding: 0 1px;">
    <AFormItem v-if="showAvatar" label="头像">
      <div class="agent-avatar-editor">
        <input
          ref="avatarInputRef"
          type="file"
          accept="image/png,image/jpeg,image/webp"
          class="agent-avatar-file"
          style="display: none"
          @change="handleAvatarPick"
        />
        <div
          class="agent-avatar-preview"
          :title="formData.avatar ? '' : '点击上传'"
          @click="!formData.avatar && avatarInputRef?.click()"
        >
          <img v-if="formData.avatar" :src="formData.avatar" alt="头像" />
          <PlusOutlined v-else class="agent-avatar-placeholder" />
          <!-- 有头像：hover 出预览/更换双操作 -->
          <div v-if="formData.avatar" class="agent-avatar-actions">
            <EyeOutlined title="预览大图" @click.stop="avatarPreviewVisible = true" />
            <CameraOutlined title="更换头像" @click.stop="avatarInputRef?.click()" />
          </div>
          <!-- 无头像：保持整卡点击上传 -->
          <div v-else class="agent-avatar-mask">上传</div>
        </div>
        <!-- 隐藏 AImage：仅用其受控全屏预览（缩放/旋转工具栏） -->
        <AImage
          v-if="formData.avatar"
          style="display: none"
          :src="formData.avatar"
          :preview="{ visible: avatarPreviewVisible, onVisibleChange: (v: boolean) => (avatarPreviewVisible = v) }"
        />
        <div class="agent-avatar-side">
          <AButton v-if="formData.avatar" type="text" danger size="small" @click="removeAvatar">移除</AButton>
          <span class="text-placeholder text-xs">PNG/JPG/WebP，自动裁剪为方形，点击「保存」后生效</span>
        </div>
      </div>
    </AFormItem>

    <AFormItem label="标签" name="tag">
      <ASelect
        v-model:value="formData.tag"
        placeholder="选择或输入标签"
        allow-clear
      >
        <ASelectOption v-for="tag in filteredTags" :key="tag" :value="tag">
          {{ tag }}
        </ASelectOption>
        <template #dropdownRender="{ menuNode: menu }">
          <VNodes :vnodes="menu" />
          <ADivider style="margin: 4px 0" />
          <ASpace style="padding: 4px 8px">
            <AInput
              ref="inputRef"
              v-model:value="newTagName"
              style="width: 300px"
              placeholder="输入新标签"
            />
            <AButton type="text" @click="addTag">
              <template #icon>
                <PlusOutlined />
              </template>
              添加
            </AButton>
          </ASpace>
        </template>
      </ASelect>
    </AFormItem>

    <AFormItem label="名称" name="name">
      <AInput v-model:value="formData.name" placeholder="请输入智能体名称" />
    </AFormItem>

    <AFormItem name="agentCode">
      <template #label>
        <ATooltip title="“智能体编号”将会在agui接口和外置对话界面中使用，请谨慎填写。您修改“智能体编号”后需要重新生成“外置对话链接”。">
          <span>智能体编号</span><InfoCircleOutlined class="text-secondary cursor-pointer" />
        </ATooltip>
      </template>
      <AInput :prefix="tenantCode ? tenantCode + '-' : ''" v-model:value="agentCodeSuffix" placeholder="请输入智能体编号，如: my-agent-001" />
    </AFormItem>

    <AFormItem label="描述" name="description">
      <ATextarea
        v-model:value="formData.description"
        placeholder="请输入智能体描述"
        :rows="2"
      />
    </AFormItem>

    <AFormItem v-if="showCommonQuestions">
      <template #label>
        <ATooltip title="配置后将在新会话欢迎页展示为快捷提问卡片，点击卡片直接发送对应问题；可拖拽排序，最多 8 个。">
          <span>常用问题</span><InfoCircleOutlined class="text-secondary cursor-pointer" />
        </ATooltip>
      </template>
      <!-- 编辑器含多个输入控件，用 AFormItemRest 阻断 FormItem 的字段收集（校验在 validate() 手动做） -->
      <AFormItemRest>
      <div class="common-questions-editor">
        <div class="common-questions-pinned-row">
          <ASwitch v-model:checked="questionsPinned" size="small" />
          <span class="common-questions-pinned-label">对话中常驻显示</span>
          <ATooltip title="开启后，进行中的对话里输入框上方也会常驻常用问题快捷入口；关闭则仅在新会话欢迎页展示。未配置任何问题时不展示。">
            <InfoCircleOutlined class="text-secondary cursor-pointer" />
          </ATooltip>
        </div>
        <div
          v-if="commonQuestions.length > 0"
          ref="questionsListRef"
          class="common-questions-list"
        >
          <div
            v-for="(q, index) in commonQuestions"
            :key="questionKey(q)"
            class="common-question-item"
          >
            <span class="question-drag-handle" title="拖拽排序">
              <HolderOutlined class="question-drag-handle-icon" />
            </span>
            <APopover trigger="click" placement="bottomLeft">
              <template #content>
                <div class="cq-icon-picker">
                  <div class="cq-icon-picker-grid">
                    <span
                      v-for="name in COMMON_QUESTION_ICON_NAMES"
                      :key="name"
                      class="cq-icon-picker-cell"
                      :class="{ active: q.icon === name }"
                      @click="updateQuestion(index, { icon: name })"
                    >
                      <component
                        :is="COMMON_QUESTION_ICONS[name]"
                        :style="{ color: q.icon === name ? (q.color || undefined) : undefined }"
                      />
                    </span>
                  </div>
                  <div class="cq-icon-picker-colors">
                    <span
                      v-for="color in COMMON_QUESTION_COLORS"
                      :key="color"
                      class="cq-icon-picker-color"
                      :class="{ active: q.color === color }"
                      :style="{ backgroundColor: color }"
                      @click="updateQuestion(index, { color })"
                    />
                  </div>
                </div>
              </template>
              <AButton class="question-icon-btn" title="选择图标与颜色">
                <component
                  v-if="resolveCommonQuestionIcon(q.icon)"
                  :is="resolveCommonQuestionIcon(q.icon)"
                  :style="{ color: q.color || undefined }"
                />
                <SmileOutlined v-else class="text-placeholder" />
              </AButton>
            </APopover>
            <div class="question-fields">
              <AInput
                :value="q.title"
                placeholder="标题，如：采购预测"
                :maxlength="20"
                @update:value="updateQuestion(index, { title: $event })"
              />
              <ATextarea
                :value="q.question"
                placeholder="点击卡片后发送的问题内容"
                :rows="2"
                :maxlength="500"
                @update:value="updateQuestion(index, { question: $event })"
              />
            </div>
            <AButton
              type="text"
              danger
              title="删除"
              class="question-remove-btn"
              @click="removeQuestion(index)"
            >
              <template #icon><DeleteOutlined /></template>
            </AButton>
          </div>
        </div>
        <AButton
          type="dashed"
          block
          :disabled="commonQuestions.length >= MAX_COMMON_QUESTIONS"
          @click="addQuestion"
        >
          <template #icon><PlusOutlined /></template>
          添加问题（{{ commonQuestions.length }}/{{ MAX_COMMON_QUESTIONS }}）
        </AButton>
      </div>
      </AFormItemRest>
    </AFormItem>
  </AForm>

  <!-- 头像裁切弹窗（置于 AForm 外，避免弹窗内控件被 FormItem 收集） -->
  <AvatarCropModal
    v-model:visible="cropVisible"
    :image-url="cropImageUrl"
    :is-png="cropIsPng"
    @confirm="handleCropConfirm"
  />
</template>

<style scoped lang="scss">
:deep(.ant-input-prefix) {
  margin-inline-end: 0;
}

.agent-avatar-editor {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
}

.agent-avatar-file {
  display: none;
}

.agent-avatar-preview {
  position: relative;
  width: 96px;
  height: 96px;
  border-radius: var(--border-radius-lg);
  border: 1px dashed var(--color-border-base);
  background-color: var(--color-bg-light);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  overflow: hidden;
  flex-shrink: 0;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  .agent-avatar-placeholder {
    font-size: 24px;
    color: var(--color-text-secondary);
  }

  .agent-avatar-mask {
    position: absolute;
    inset: auto 0 0 0;
    padding: 3px 0;
    font-size: 12px;
    text-align: center;
    color: #fff;
    background-color: rgba(0, 0, 0, 0.45);
    opacity: 0;
    transition: opacity var(--transition-base);
  }

  /* 有头像时 hover 出现的预览/更换双操作遮罩 */
  .agent-avatar-actions {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 16px;
    background-color: rgba(0, 0, 0, 0.45);
    color: #fff;
    font-size: 20px;
    opacity: 0;
    transition: opacity var(--transition-base);

    .anticon {
      cursor: pointer;
      transition: transform var(--transition-base);

      &:hover {
        transform: scale(1.2);
      }
    }
  }

  &:hover {
    border-color: var(--color-primary);

    .agent-avatar-mask,
    .agent-avatar-actions {
      opacity: 1;
    }
  }
}

.agent-avatar-side {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
}

.common-questions-pinned-row {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
}

.common-questions-pinned-label {
  font-size: var(--font-size-sm);
  color: var(--color-text-regular);
}

.common-questions-editor {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.common-questions-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.common-question-item {
  display: flex;
  align-items: flex-start;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm) var(--spacing-md);
  background-color: var(--color-bg-light);
  border: 1px solid var(--color-border-base);
  border-radius: var(--border-radius-md);
  transition: all var(--transition-base);

  &:hover {
    border-color: var(--color-primary);
  }
}

.question-drag-handle {
  display: flex;
  align-items: center;
  cursor: grab;
  padding: 6px 2px;

  &:active {
    cursor: grabbing;
  }

  .question-drag-handle-icon {
    color: #999;
    font-size: 16px;

    &:hover {
      color: #666;
    }
  }
}

.question-icon-btn {
  flex-shrink: 0;
  width: 32px;
  padding: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
}

.question-fields {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
}

.question-remove-btn {
  flex-shrink: 0;
}

:deep(.question-sortable-ghost) {
  opacity: 0.5;
  background-color: #f0f0f0;
  border: 1px dashed #1890ff;
}

:deep(.question-sortable-chosen) {
  background-color: #fafafa;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

:deep(.question-sortable-drag) {
  opacity: 0.9;
  transform: rotate(2deg);
  box-shadow: 0 5px 15px rgba(0, 0, 0, 0.3);
}

:deep(.question-sortable-fallback) {
  opacity: 1 !important;
}

/* 防止拖拽时页面滚动 */
:global(body.dragging) {
  user-select: none;
  -webkit-user-select: none;
}
</style>

<style lang="scss">
/* 图标选择弹层渲染在 body 下，需使用非 scoped 样式 */
.cq-icon-picker {
  .cq-icon-picker-grid {
    display: grid;
    grid-template-columns: repeat(8, 28px);
    gap: 4px;
  }

  .cq-icon-picker-cell {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 28px;
    height: 28px;
    border-radius: var(--border-radius-sm);
    border: 1px solid transparent;
    cursor: pointer;
    font-size: 16px;
    color: var(--color-text-regular);

    &:hover {
      background-color: var(--color-bg-light);
    }

    &.active {
      border-color: var(--color-primary);
      background-color: var(--color-bg-light);
    }
  }

  .cq-icon-picker-colors {
    display: flex;
    justify-content: center;
    gap: 8px;
    margin-top: 10px;
    padding-top: 10px;
    border-top: 1px solid var(--color-border-base);
  }

  .cq-icon-picker-color {
    width: 20px;
    height: 20px;
    border-radius: 50%;
    cursor: pointer;
    border: 2px solid transparent;
    box-sizing: border-box;
    transition: transform var(--transition-base);

    &:hover {
      transform: scale(1.15);
    }

    &.active {
      border-color: var(--color-text-primary);
      box-shadow: inset 0 0 0 2px #fff;
    }
  }
}
</style>
