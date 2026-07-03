<script setup lang="ts">
import { computed, inject, watch, nextTick } from 'vue'
import type { ComputedRef } from 'vue'
import IconFont from '@/components/common/IconFont.vue'
import type { IconName } from '@/components/common/icons'
import type { WorkflowFlowEdge, WorkflowFlowNode } from '@/types/workflow'
import PanelSection from '@/components/workflow/panels/shared/PanelSection.vue'

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

function getIconName(type: string): IconName {
  return nodeIconMap[type] || 'nodecode'
}

interface MatchItem {
  matchValue: string
  nextNodeId: string
}

const props = defineProps<{
  modelValue?: MatchItem[]
  nodes: WorkflowFlowNode[]
  edges: WorkflowFlowEdge[]
  currentNodeId: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: MatchItem[]]
}>()

const injectedEdges = inject<ComputedRef<WorkflowFlowEdge[]>>('workflowEdges', computed(() => []))

// 收集当前节点连接的所有下游节点 ID
const matchTargetNodeIds = computed(() => {
  const edges = injectedEdges.value.length ? injectedEdges.value : props.edges
  if (!edges || !props.currentNodeId) return []

  const targetIds = new Set<string>()
  for (const edge of edges) {
    if (edge.source === props.currentNodeId) {
      targetIds.add(edge.target)
    }
  }
  return [...targetIds]
})

// 收集下游节点信息
const matchTargetNodes = computed(() => {
  return matchTargetNodeIds.value
    .map((id) => props.nodes.find((n) => n.id === id))
    .filter((n): n is WorkflowFlowNode => !!n)
})

interface DisplayItem {
  matchValue: string
  nextNodeId: string
  nodeLabel: string
  nodeType: string
  nodeColor: string
}

const displayItems = computed<DisplayItem[]>(() => {
  const items: DisplayItem[] = []
  const seen = new Set<string>()

  const existingMatches = (props.modelValue || []) as MatchItem[]

  // 先用现有匹配项的顺序
  for (const match of existingMatches) {
    if (!match.nextNodeId || seen.has(match.nextNodeId)) continue
    if (!matchTargetNodeIds.value.includes(match.nextNodeId)) continue
    const node = matchTargetNodes.value.find((n) => n.id === match.nextNodeId)
    if (!node) continue

    seen.add(match.nextNodeId)
    items.push({
      matchValue: match.matchValue || '',
      nextNodeId: match.nextNodeId,
      nodeLabel: node.data.label || node.id,
      nodeType: node.data.type,
      nodeColor: node.data.schema?.color || '#1677ff',
    })
  }

  // 新连接的节点追加到末尾
  for (const node of matchTargetNodes.value) {
    if (!seen.has(node.id)) {
      items.push({
        matchValue: '',
        nextNodeId: node.id,
        nodeLabel: node.data.label || node.id,
        nodeType: node.data.type,
        nodeColor: node.data.schema?.color || '#1677ff',
      })
    }
  }

  return items
})

// 自动同步
let syncing = false
watch(
  [matchTargetNodeIds, () => props.modelValue],
  () => {
    if (syncing) return
    const edges = injectedEdges.value.length ? injectedEdges.value : props.edges
    if (!edges || !props.currentNodeId) return

    const currentIds = new Set(matchTargetNodeIds.value)
    const existingMatches = (props.modelValue || []) as MatchItem[]

    // 移除已断开连接的匹配项
    const filtered = existingMatches.filter((m) => m.nextNodeId && currentIds.has(m.nextNodeId))

    // 添加新连接但尚未在匹配项中的节点
    const existingIds = new Set(filtered.map((m) => m.nextNodeId))
    for (const nodeId of currentIds) {
      if (!existingIds.has(nodeId)) {
        filtered.push({ matchValue: '', nextNodeId: nodeId })
      }
    }

    const needUpdate =
      filtered.length !== existingMatches.length ||
      filtered.some((m, i) => m.nextNodeId !== existingMatches[i]?.nextNodeId)

    if (needUpdate) {
      syncing = true
      emit('update:modelValue', filtered)
      nextTick(() => { syncing = false })
    }
  },
  { immediate: true, deep: false },
)

// 更新单个匹配项的 matchValue
function updateMatchValue(nextNodeId: string, value: string) {
  const matches = ((props.modelValue || []) as MatchItem[]).map((m) =>
    m.nextNodeId === nextNodeId ? { ...m, matchValue: value } : { ...m },
  )
  syncing = true
  emit('update:modelValue', matches)
  nextTick(() => { syncing = false })
}
</script>

<template>
  <PanelSection title="匹配项">
    <div class="match-binding-list" :class="{ empty: !displayItems.length }">
      <div
        v-for="item in displayItems"
        :key="item.nextNodeId"
        class="match-binding-row"
      >
        <div class="match-value-cell">
          <AInput
            :value="item.matchValue"
            placeholder="匹配值"
            @update:value="(v: string) => updateMatchValue(item.nextNodeId, v)"
          />
        </div>
        <div class="match-next-cell">
          <div class="match-next-display selector-trigger">
            <span class="trigger-text">
              <span class="trigger-icon">
                <IconFont
                  :name="getIconName(item.nodeType)"
                  :size="14"
                  :color="item.nodeColor"
                />
                <span style="margin-left: 4px;">{{ item.nodeLabel }}</span>
              </span>
            </span>
          </div>
        </div>
      </div>
      <div v-if="!displayItems.length" class="match-binding-empty">
        暂无匹配分支，请先连线到下游节点
      </div>
    </div>
  </PanelSection>
</template>

<style scoped lang="scss">
.match-binding-list {
  display: grid;
  gap: 8px;

  &.empty {
    gap: 0;
  }
}

.match-binding-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.match-value-cell {
  flex: 1;
  min-width: 0;
}

.match-next-cell {
  flex: 2;
  min-width: 0;
}

.match-next-display {
  width: 100%;
}

// 复用 NodeOutputSelector 的 trigger 样式
.selector-trigger {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 2px 8px;
  background-color: #F2F4F7;
  border-radius: 6px;
  font-size: 14px;
  min-height: 32px;
}

.trigger-text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #262626;
}

.trigger-icon {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 6px;
  background-color: #ffffff;
}

.match-binding-empty {
  padding: 20px 0;
  text-align: center;
  color: #bfbfbf;
  font-size: 13px;
}
</style>
