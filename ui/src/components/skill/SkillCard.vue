/**
 * 技能包卡片组件
 *
 * @author huxuehao
 */
<script setup lang="ts">
import { ref, computed, defineComponent } from 'vue'
import { EllipsisOutlined, PlusOutlined, ToolOutlined } from '@ant-design/icons-vue'
import skillAvatar from '@/assets/avatar/skill.png'
import { message } from 'ant-design-vue'
import type { SkillPackageVO } from '@/types'
import * as skillApi from '@/api/skill'
import {
  createViewItem,
  createViewContentItem,
  createEditItem,
  createEnableItem,
  createDeleteItem,
  createSetCategoryItem,
  createToolLinkItem,
  createAliasItem,
  createDivider,
} from '@/composables/useCardMenuItems'

/**
 * Props定义
 */
const props = defineProps<{
  data: SkillPackageVO
  categories: string[]
}>()

/**
 * Emits定义
 */
const emit = defineEmits<{
  view: [id: string]
  viewContent: [id: string]
  edit: [id: string]
  delete: [id: string]
  enable: [id: string]
  setCategory: [id: string, category: string]
  toolLink: [id: string]
  aliasUpdated: [id: string]
}>()

/**
 * 格式化更新时间
 */
const formattedTime = computed(() => {
  if (!props.data.updatedAt) return ''
  const date = new Date(props.data.updatedAt)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
})

/**
 * 操作菜单项
 */
const menuItems = computed(() => {
  const items = [createViewItem()]
  if (props.data.skillType === 'BUILTIN') {
    // 内置技能包只读：查看元信息 + 查看正文内容 + 别名 + 启停
    items.push(createViewContentItem())
    items.push(createAliasItem())
    items.push(createEnableItem(props.data.enabled))
  } else {
    items.push(createEnableItem(props.data.enabled))
    items.push(createEditItem())
    items.push(createSetCategoryItem())
    items.push(createAliasItem())
    items.push(createToolLinkItem())
    items.push(createDivider())
    items.push(createDeleteItem())
  }
  return items
})

/**
 * 技能类型显示文本
 */
const skillTypeText = computed(() => {
  return props.data.skillType === 'BUILTIN' ? '内置' : '自定义'
})

// 分类设置弹窗
const categoryModalVisible = ref(false)
const categoryValue = ref('')
const categorySearchText = ref('')
const categoryNewName = ref('')
// 本地分类列表副本，支持运行时新增
const localCategories = ref<string[]>([...props.categories])

const filteredCategories = computed(() => {
  if (!categorySearchText.value) {
    return localCategories.value
  }
  const searchLower = categorySearchText.value.toLowerCase()
  const filtered = localCategories.value.filter(cat =>
    cat.toLowerCase().includes(searchLower)
  )
  if (!filtered.includes(categorySearchText.value)) {
    filtered.unshift(categorySearchText.value)
  }
  return filtered
})

const VNodes = defineComponent({
  props: {
    vnodes: { type: Object, required: true },
  },
  render() {
    return this.vnodes
  },
})

function addCategory(e: Event) {
  e.preventDefault()
  if (!categoryNewName.value) return
  if (!localCategories.value.includes(categoryNewName.value)) {
    localCategories.value.push(categoryNewName.value)
  }
  categoryValue.value = categoryNewName.value
  categoryNewName.value = ''
}

function openCategoryModal() {
  categoryValue.value = props.data.category || ''
  categorySearchText.value = ''
  categoryNewName.value = ''
  categoryModalVisible.value = true
}

async function handleCategoryConfirm() {
  if (!categoryValue.value.trim()) {
    message.warning('请选择或输入分类')
    return
  }
  try {
    const detailRes = await skillApi.detail(String(props.data.id))
    const vo = detailRes.data.data as SkillPackageVO
    if (vo) {
      await skillApi.update({
        id: String(vo.id),
        name: vo.name,
        description: vo.description,
        category: categoryValue.value,
        skillContent: '',
        references: null,
        examples: null,
        scripts: null,
        tools: vo.tools || [],
      })
      message.success('分类设置成功')
      categoryModalVisible.value = false
      emit('setCategory', String(props.data.id), categoryValue.value)
    }
  } catch {
    message.error('设置分类失败')
  }
}

// 别名设置弹窗
const aliasModalVisible = ref(false)
const aliasValue = ref('')

function openAliasModal() {
  aliasValue.value = props.data.alias || ''
  aliasModalVisible.value = true
}

async function handleAliasConfirm() {
  try {
    await skillApi.updateAlias(String(props.data.id), aliasValue.value.trim())
    message.success('别名设置成功')
    aliasModalVisible.value = false
    emit('aliasUpdated', String(props.data.id))
  } catch {
    message.error('设置别名失败')
  }
}

/**
 * 处理菜单点击
 */
function handleMenuClick({ key }: { key: string }) {
  switch (key) {
    case 'view':
      emit('view', props.data.id as string)
      break
    case 'viewContent':
      emit('viewContent', props.data.id as string)
      break
    case 'edit':
      emit('edit', props.data.id as string)
      break
    case 'setCategory':
      openCategoryModal()
      break
    case 'alias':
      openAliasModal()
      break
    case 'toolLink':
      emit('toolLink', props.data.id as string)
      break
    case 'enable':
      emit('enable', props.data.id as string)
      break
    case 'delete':
      emit('delete', props.data.id as string)
      break
  }
}
</script>

<template>
  <div class="skill-card">
    <div class="card-header flex items-center gap-sm">
      <ATooltip :title="data.tools && data.tools.length > 0 ? `已关联 ${data.tools.length} 个工具` : ''">
        <div class="card-avatar-wrapper">
          <div class="card-avatar flex-center" :class="{ disabled: !data.enabled }"><img :src="skillAvatar" alt="skill" /></div>
          <span
            v-if="data.tools && data.tools.length > 0"
            class="avatar-corner-badge"
          >
            <ToolOutlined />
          </span>
        </div>
      </ATooltip>
      <div class="card-name flex-1 truncate" :title="data.alias || data.name" @click="emit('view', data.id as string)">{{ data.alias || data.name }}</div>
      <ADropdown :trigger="['hover']">
        <AButton type="text" size="small" v-permission="['TENANT_EDITOR','TENANT_ADMIN','TENANT_OWNER']">
          <EllipsisOutlined />
        </AButton>
        <template #overlay>
          <AMenu @click="handleMenuClick" :items="menuItems"></AMenu>
        </template>
      </ADropdown>
    </div>

    <div class="card-content line-clamp-3" :title="data.description">
      {{ data.description }}
    </div>

    <div class="card-footer flex items-center justify-between">
      <div class="card-tags flex items-center gap-xs">
        <ATag color="default" class="tag">{{ skillTypeText }}</ATag>
        <ATag color="default" class="tag">{{ data.category || '未设置标签' }}</ATag>
      </div>
      <div class="card-time text-placeholder text-xs">更新于 {{ formattedTime }}</div>
    </div>
  </div>

  <!-- 分类设置弹窗 -->
  <a-modal
    v-model:open="categoryModalVisible"
    title="设置标签"
    :ok-text="'确定'"
    :cancel-text="'取消'"
    @ok="handleCategoryConfirm"
    destroyOnClose
  >
    <a-form layout="vertical">
      <a-form-item label="选择标签">
        <a-select
          v-model:value="categoryValue"
          placeholder="选择或输入标签"
          show-search
          @search="categorySearchText = $event"
        >
          <a-select-option v-for="cat in filteredCategories" :key="cat" :value="cat">
            {{ cat }}
          </a-select-option>
          <template #dropdownRender="{ menuNode: menu }">
            <VNodes :vnodes="menu" />
            <a-divider style="margin: 4px 0" />
            <a-space style="padding: 4px 8px">
              <a-input v-model:value="categoryNewName" style="width: 260px" placeholder="请输入新标签" />
              <a-button type="text" @click="addCategory">
                <template #icon>
                  <PlusOutlined />
                </template>
                添加
              </a-button>
            </a-space>
          </template>
        </a-select>
      </a-form-item>
    </a-form>
  </a-modal>

  <!-- 别名设置弹窗 -->
  <a-modal
    v-model:open="aliasModalVisible"
    title="设置别名"
    :ok-text="'确定'"
    :cancel-text="'取消'"
    @ok="handleAliasConfirm"
    destroyOnClose
  >
    <a-form layout="vertical">
      <a-form-item label="展示别名（仅用于界面展示，不影响技能实际名称与发送给智能体的值）">
        <a-input v-model:value="aliasValue" placeholder="给这个技能起个友好的中文名，如「用户交互协议」" allow-clear />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<style scoped lang="scss">
.skill-card {
  min-height: 180px;
  padding: var(--spacing-md);
  background-color: #FFFFFF;
  border-radius: var(--border-radius-lg);
  border: 1px solid #ebebeb;
  transition: all var(--transition-base);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);

  &:hover {
    box-shadow: 0 4px 6px -5px rgba(0, 0, 0, 0.3);
    transform: translateY(-2px);
  }

  .card-header {
    .card-avatar-wrapper {
      position: relative;
      flex-shrink: 0;
    }

    .card-avatar {
      width: 40px;
      height: 40px;
      background-color: #e8eaf6;
      border-radius: var(--border-radius-xl);
      flex-shrink: 0;

      img {
        width: 28px;
        height: 28px;
        object-fit: contain;
      }
    }

    .avatar-corner-badge {
      position: absolute;
      bottom: -4px;
      right: -4px;
      width: 18px;
      height: 18px;
      display: flex;
      align-items: center;
      justify-content: center;
      border-radius: 50%;
      box-shadow: 0 0 0 2px var(--color-bg-white);
      background: #fff;
      color: #ab47bc;
      font-size: 11px;
    }

    .card-name {
      font-size: var(--font-size-base);
      font-weight: 600;
      color: var(--color-text-primary);
      cursor: pointer;
      transition: color var(--transition-base);

      //&:hover {
      //  color: #ab47bc;
      //}
    }
  }

  .card-content {
    font-size: var(--font-size-sm);
    color: var(--color-text-regular);
    line-height: 1.6;
    display: -webkit-box;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 3;
    overflow: hidden;
    text-overflow: ellipsis;
    word-break: break-all;
    min-height: 65px;
    max-height: 65px;
  }

  .card-footer {
    padding-top: var(--spacing-xs);

    .card-tags {
      flex-wrap: wrap;
    }

    .card-time {
      white-space: nowrap;
    }
  }

  .disabled {
    color: #757575 !important;
    background-color: #e7e7e7 !important;
    img {
      filter: grayscale(100%);
      opacity: 0.5;
    }
  }
}
</style>
