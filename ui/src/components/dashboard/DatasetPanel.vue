<script setup lang="ts">
/**
 * 数据集面板：在设计器右侧就地维护数据集（卡片列表 + 新建/编辑弹窗 + 运行预览）。
 *
 * @author huxuehao
 */
import { onMounted, reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { debounce } from 'lodash-es'
import { CloseOutlined, PlayCircleOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons-vue'
import ConfigCodeEditor from '@/components/editor/ConfigCodeEditor.vue'
import {
  datasetPage,
  datasetSave,
  datasetUpdate,
  datasetRemove,
  datasetEnable,
  datasetExecute,
} from '@/api/dashboard'
import type { DashboardDatasetEntity, DatasetExecuteResult } from '@/types/dashboard'

const emit = defineEmits<{ (e: 'close'): void; (e: 'changed'): void }>()

const list = ref<DashboardDatasetEntity[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const keyword = ref('')
const bodyRef = ref<HTMLElement | null>(null)

const PAGE_SIZE = 20
const page = ref(1)
const total = ref(0)
const hasMore = ref(true)

/**
 * 拉取分页数据；reset 为 true 时从第一页重新加载
 */
async function fetchPage(reset: boolean) {
  if (reset) {
    page.value = 1
    hasMore.value = true
  }
  if (!reset && (!hasMore.value || loadingMore.value)) return
  if (reset) loading.value = true
  else loadingMore.value = true
  try {
    const resp = await datasetPage({
      page: page.value,
      size: PAGE_SIZE,
      name: keyword.value.trim() || undefined,
    })
    const result = resp.data.data
    const records = result.records || []
    list.value = reset ? records : [...list.value, ...records]
    total.value = result.total || 0
    hasMore.value = list.value.length < total.value
    page.value += 1
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

/** 重新加载首页（供增删改后刷新与初始化使用） */
function loadList() {
  return fetchPage(true)
}

// 搜索防抖：服务端按名称模糊查询，输入停顿 300ms 后从第一页重拉
const debouncedSearch = debounce(() => fetchPage(true), 300)
watch(keyword, () => debouncedSearch())

/** 滚动到底部附近时加载下一页 */
function onScroll() {
  const el = bodyRef.value
  if (!el || loading.value || loadingMore.value || !hasMore.value) return
  if (el.scrollTop + el.clientHeight >= el.scrollHeight - 48) {
    fetchPage(false)
  }
}

// ── 新建/编辑弹窗 ──
const modalOpen = ref(false)
const editing = ref(false)
interface DatasetForm {
  id?: string
  name: string
  remark: string
  sqlText: string
  cacheTtl: number
}
const form = reactive<DatasetForm>({ name: '', remark: '', sqlText: '', cacheTtl: 0 })

const previewLoading = ref(false)
const previewError = ref<string | null>(null)
const previewResult = ref<DatasetExecuteResult | null>(null)
const previewColumns = ref<{ title: string; dataIndex: string; key: string; ellipsis: boolean }[]>([])
const previewRows = ref<Record<string, unknown>[]>([])

function resetPreview() {
  previewResult.value = null
  previewError.value = null
  previewColumns.value = []
  previewRows.value = []
}

function openCreate() {
  editing.value = false
  Object.assign(form, { id: undefined, name: '', remark: '', sqlText: '', cacheTtl: 0 })
  resetPreview()
  modalOpen.value = true
}

function openEdit(record: DashboardDatasetEntity) {
  editing.value = true
  Object.assign(form, {
    id: record.id,
    name: record.name,
    remark: record.remark,
    sqlText: record.sqlText,
    cacheTtl: record.cacheTtl ?? 0,
  })
  resetPreview()
  modalOpen.value = true
}

async function runPreview() {
  if (!form.sqlText) {
    message.warning('请先输入查询语句')
    return
  }
  previewLoading.value = true
  previewError.value = null
  try {
    const resp = await datasetExecute({ sql: form.sqlText, limit: 50 })
    previewResult.value = resp.data.data
    previewColumns.value = (resp.data.data.columns || []).map((c) => ({
      title: c.name,
      dataIndex: c.name,
      key: c.name,
      ellipsis: true,
    }))
    previewRows.value = (resp.data.data.rows || []).map((r, i) => ({ ...r, _rowKey: i }))
  } catch (e: unknown) {
    previewError.value = typeof e === 'string' ? e : (e as Error)?.message || '执行失败'
  } finally {
    previewLoading.value = false
  }
}

async function submit() {
  if (!form.name || !form.sqlText) {
    message.warning('名称与查询语句必填')
    return
  }
  if (editing.value) {
    await datasetUpdate({ ...form })
  } else {
    await datasetSave({ ...form })
  }
  message.success('已保存')
  modalOpen.value = false
  await loadList()
  emit('changed')
}

async function remove(record: DashboardDatasetEntity) {
  if (!record.id) return
  await datasetRemove([record.id])
  message.success('已删除')
  await loadList()
  emit('changed')
}

async function toggleEnable(record: DashboardDatasetEntity) {
  if (!record.id) return
  await datasetEnable(record.id, record.enabled ? 0 : 1)
  await loadList()
  emit('changed')
}

onMounted(loadList)
</script>

<template>
  <div class="dataset-panel">
    <div class="dp-header">
      <span class="dp-title">数据集</span>
      <a-button type="text" size="small" title="关闭" @click="emit('close')">
        <template #icon><CloseOutlined /></template>
      </a-button>
    </div>

    <div ref="bodyRef" class="dp-body" @scroll="onScroll">
      <div class="dp-toolbar">
        <a-input v-model:value="keyword" placeholder="搜索数据集" allow-clear class="dp-search">
          <template #prefix><SearchOutlined /></template>
        </a-input>
        <a-button type="primary" @click="openCreate">
          <template #icon><PlusOutlined /></template>
          新建
        </a-button>
      </div>

      <div class="dp-content">
        <a-spin v-if="loading" />
        <a-empty v-else-if="!list.length" description="暂无数据集" />
        <template v-else>
          <div class="dp-cards">
            <div v-for="d in list" :key="d.id" class="ds-card">
              <div class="ds-card-head">
                <span class="ds-name">{{ d.name }}</span>
                <a-tag :color="d.enabled ? 'green' : 'default'" :bordered="false">{{ d.enabled ? '启用' : '停用' }}</a-tag>
              </div>
              <div class="ds-remark">{{ d.remark || '无描述' }}</div>
              <div class="ds-card-actions">
                <a @click="openEdit(d)">编辑</a>
                <a @click="toggleEnable(d)">{{ d.enabled ? '停用' : '启用' }}</a>
                <a-popconfirm title="确认删除该数据集？" @confirm="remove(d)">
                  <a class="danger">删除</a>
                </a-popconfirm>
              </div>
            </div>
          </div>
          <div class="dp-footer">
            <span v-if="loadingMore">正在加载中...</span>
            <span v-else-if="!hasMore">没有更多数据了</span>
          </div>
        </template>
      </div>
    </div>

    <a-modal
      v-model:open="modalOpen"
      :title="editing ? '编辑数据集' : '新建数据集'"
      width="760px"
      ok-text="保存"
      @ok="submit"
    >
      <div class="form-grid">
        <div class="form-item">
          <span class="form-label">名称</span>
          <a-input v-model:value="form.name" placeholder="数据集名称" />
        </div>
        <div class="form-item">
          <span class="form-label">缓存(秒)</span>
          <a-input-number v-model:value="form.cacheTtl" :min="0" style="width: 100%" />
        </div>
        <div class="form-item full">
          <span class="form-label">描述</span>
          <a-input v-model:value="form.remark" placeholder="描述" />
        </div>
        <div class="form-item full">
          <div class="sql-header">
            <span class="form-label">查询语句（仅 SELECT）</span>
            <a-button size="small" :loading="previewLoading" @click="runPreview">
              <template #icon><PlayCircleOutlined /></template>
              运行预览
            </a-button>
          </div>
          <ConfigCodeEditor v-model="form.sqlText" language="sql" height="200px" :maximize="false" />
        </div>
      </div>

      <div v-if="previewError" class="preview-error">{{ previewError }}</div>
      <div v-else-if="previewResult" class="preview-block">
        <div class="preview-meta">
          返回 {{ previewResult.rowCount }} 行，耗时 {{ previewResult.elapsedMs }}ms
          <span v-if="previewResult.truncated" class="truncated">（已截断）</span>
        </div>
        <a-table
          :columns="previewColumns"
          :data-source="previewRows"
          row-key="_rowKey"
          size="small"
          :scroll="{ y: 200 }"
          :pagination="false"
        />
      </div>
    </a-modal>
  </div>
</template>

<style scoped lang="scss">
.dataset-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.dp-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #f0f0f0;
}

.dp-title {
  font-size: 14px;
  font-weight: 600;
  color: #1a1a1a;
}

.dp-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.dp-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 0;
}

/* 搜索工具栏：滚动时吸顶，白底避免卡片漏光 */
.dp-toolbar {
  position: sticky;
  top: 0;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: #fff;
}

.dp-search {
  flex: 1;
  min-width: 0;
}

.dp-content {
  padding: 0 16px 12px;
}

.dp-cards {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.dp-footer {
  padding: 14px 0 4px;
  text-align: center;
  font-size: 12px;
  color: #bbb;
}

.ds-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fff;
}

.ds-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.ds-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
  font-weight: 600;
  color: #1a1a1a;
}

.ds-remark {
  font-size: 13px;
  color: #999;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ds-card-actions {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 13px;
}

.ds-card-actions a {
  cursor: pointer;
}

.danger {
  color: #cf1322;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-item.full {
  grid-column: 1 / -1;
}

.form-label {
  font-size: 13px;
  color: #595959;
}

.sql-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.preview-block {
  margin-top: 16px;
}

.preview-meta {
  margin-bottom: 8px;
  font-size: 13px;
  color: #999;
}

.truncated {
  color: #d48806;
}

.preview-error {
  margin-top: 16px;
  font-size: 13px;
  color: #cf1322;
}
</style>
