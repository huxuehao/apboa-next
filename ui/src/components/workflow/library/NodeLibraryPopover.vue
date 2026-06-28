<script setup lang="ts">
import { computed, ref } from 'vue'
import { SearchOutlined } from '@ant-design/icons-vue'
import { WORKFLOW_GROUPS, workflowNodeSchemas } from '@/config/workflow/nodeSchemas'
import type { WorkflowNodeSchema } from '@/types/workflow'
import IconFont from '@/components/common/IconFont.vue'
import type { IconName } from '@/components/common/icons'

const props = defineProps<{
  open: boolean
  anchorX?: number
  anchorY?: number
}>()

const emit = defineEmits<{
  close: []
  add: [schema: WorkflowNodeSchema]
}>()

const keyword = ref('')
const hovered = ref<WorkflowNodeSchema | null>(null)
const detailY = ref(0)
const libraryRef = ref<HTMLElement | null>(null)
const detailRef = ref<HTMLElement | null>(null)

/**
 * 跟踪鼠标垂直位置，用于定位详情面板，底部不超出库面板
 */
function handleMouseMove(e: MouseEvent) {
  if (!libraryRef.value) return
  const rect = libraryRef.value.getBoundingClientRect()
  let y = e.clientY - rect.top
  if (detailRef.value) {
    const maxY = rect.height - detailRef.value.offsetHeight
    y = Math.min(y, maxY)
    y = Math.max(y, 0)
  }
  detailY.value = y
}

const groups = computed(() => {
  const normalized = keyword.value.trim().toLowerCase()
  return WORKFLOW_GROUPS.map((group) => ({
    ...group,
    nodes: workflowNodeSchemas.filter((schema) => {
      if (schema.group !== group.key) return false
      if (!normalized) return true
      return `${schema.title} ${schema.type} ${schema.description}`.toLowerCase().includes(normalized)
    }),
  })).filter((group) => group.nodes.length > 0)
})

/**
 * 节点类型 → iconfont 图标名称映射
 */
const nodeIconMap: Record<string, IconName> = {
  START: 'nodestart',
  END: 'nodeend',
  IF_ELSE: 'nodeif_else',
  CACHE_FETCH: 'nodecache',
  CACHE_SET: 'nodecache',
  CACHE_REMOVE: 'nodecache',
  CACHE_REFRESH: 'nodecache',
  DB_SELECT: 'nodedb_select',
  DB_INSERT: 'nodedb_insert',
  DB_UPDATE: 'nodedb_update',
  DB_DELETE: 'nodedb_delete',
  MQ_PUSH: 'nodemq_push',
  HTTP_EXTERNAL: 'nodehttp_external',
  CODE: 'nodecode',
  ITERATE: 'nodeiterate',
  LOOP: 'nodeloop',
  LIST_FILTER: 'nodelist_filter',
  LIST_SORT: 'nodelist_sort',
  STRING_SPLIT: 'nodestring_split',
  STRING_TEMPLATE: 'nodestring_template',
  SERIALIZE: 'nodeserialize',
  UNSERIALIZE: 'nodeunserialize',
  VARIABLE_AGG: 'nodevariable_agg',
  NON_EMPTY_SELECT: 'nodenon_empty_select',
  MATCH_RESULT: 'nodematch_result',
}

/**
 * 根据节点类型获取对应的 iconfont 图标名称
 */
function getNodeIconName(type: string): IconName {
  return nodeIconMap[type] || 'nodecode'
}

function handleAdd(schema: WorkflowNodeSchema) {
  emit('add', schema)
  emit('close')
}
</script>

<template>
  <Transition name="popover">
    <div v-if="props.open" ref="libraryRef" class="node-library"
      :class="{ anchored: anchorX !== undefined }"
      :style="anchorX !== undefined ? { position: 'fixed', left: `${anchorX + 12}px`, top: `${anchorY}px`, transform: 'translateY(-50%)' } : {}"
      @mousemove="handleMouseMove">
    <div class="library-search">
      <AInput v-model:value="keyword" allow-clear placeholder="搜索节点">
        <template #prefix><SearchOutlined /></template>
      </AInput>
    </div>

    <div class="library-body">
      <template v-if="groups.length > 0">
        <div v-for="group in groups" :key="group.key">
          <div class="group-title">{{ group.title }}</div>
          <button
            v-for="node in group.nodes"
            :key="node.type"
            type="button"
            class="library-item"
            @mouseenter="hovered = node"
            @mouseleave="hovered = null"
            @click="handleAdd(node)"
          >
            <span class="item-avatar" :style="{ backgroundColor: `${node.color}CC` }">
              <IconFont :name="getNodeIconName(node.type)" :size="16" color="#ffffff" />
            </span>
            <span class="item-copy">
              <span class="item-title">{{ node.title }}</span>
            </span>
          </button>
        </div>
      </template>
      <template v-else>
        <div class="library-empty">
          未找到匹配的节点
        </div>
      </template>
    </div>

    <div v-if="hovered" ref="detailRef" class="node-detail" :style="{ top: detailY + 'px' }">
      <div class="detail-header">
        <span class="detail-avatar" :style="{ backgroundColor: `${hovered.color}CC` }">
          <IconFont :name="getNodeIconName(hovered.type)" :size="16" color="#ffffff" />
        </span>
        <span class="detail-title">{{ hovered.title }}</span>
      </div>
      <div class="detail-type">{{ hovered.type }}</div>
      <div class="detail-desc">{{ hovered.description }}</div>
    </div>
    </div>
  </Transition>
</template>

<style scoped lang="scss">
.node-library {
  position: absolute;
  left: 72px;
  top: 50%;
  z-index: 20;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  width: 220px;
  height: min(680px, calc(100vh - 120px));
  box-shadow: 0px 3px 10px rgba(0, 0, 0, 0.08);
  border-radius: 8px;
  background: #fff;
  transform: translateY(-50%);
}

.library-search {
  padding: 12px;
  border-bottom: 1px solid #f0f0f0;
}

.library-body {
  display: flex;
  flex-direction: column;
  overflow: auto;
  padding: 12px;
  min-height: 0;
}

.library-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #8c8c8c;
  font-size: 14px;
}

.group-title {
  color: #8c8c8c;
  font-size: 12px;
  font-weight: 700;
  margin: 10px 0;
}

.library-item {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr);
  gap: 6px;
  align-items: center;
  width: 100%;
  padding: 5px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  text-align: left;
  height: 42px;
}

.library-item:hover {
  background: #F5F6F8;
}

.item-avatar,
.detail-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 6px;
}

.item-copy {
  display: flex;
  align-items: center;
  min-width: 0;
}

.item-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-title {
  color: #262626;
  font-size: 12px;
  font-weight: 700;
}

.item-description {
  color: #bfbfbf;
  font-size: 10px;
}

.node-detail {
  position: absolute;
  left: calc(100% + 5px);
  z-index: 30;
  display: grid;
  gap: 5px;
  width: 220px;
  padding: 10px;
  box-shadow: 0px 3px 10px rgba(0, 0, 0, 0.08);
  border-radius: 12px;
  background: #fff;
}

.detail-header {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.detail-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #262626;
  font-size: 12px;
  font-weight: 700;
}

.detail-type,
.detail-desc {
  color: #8c8c8c;
  font-size: 10px;
  line-height: 1.4;
}
</style>

<style>
.popover-enter-active,
.popover-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.popover-enter-from,
.popover-leave-to {
  opacity: 0;
  transform: scale(0.95);
}
</style>
