<script setup lang="ts">
/**
 * 数据集管理：列表 CRUD + SQL 编辑（仅查询）+ 运行预览。
 *
 * @author huxuehao
 */
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { ArrowLeftOutlined, PlayCircleOutlined, PlusOutlined } from '@ant-design/icons-vue'
import ConfigCodeEditor from '@/components/editor/ConfigCodeEditor.vue'
import {
  datasetPage,
  datasetSave,
  datasetUpdate,
  datasetRemove,
  datasetEnable,
  datasetExecute,
} from '@/api/dashboard'
import { RouteNames } from '@/router/constants'
import type { DashboardDatasetEntity, DatasetExecuteResult } from '@/types/dashboard'

const router = useRouter()

const list = ref<DashboardDatasetEntity[]>([])
const loading = ref(false)

const columns = [
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '描述', dataIndex: 'remark', key: 'remark' },
  { title: '缓存(秒)', dataIndex: 'cacheTtl', key: 'cacheTtl', width: 100 },
  { title: '状态', key: 'enabled', width: 90 },
  { title: '操作', key: 'action', width: 180 },
]

async function loadList() {
  loading.value = true
  try {
    const resp = await datasetPage({ page: 1, size: 100 })
    list.value = resp.data.data.records || []
  } finally {
    loading.value = false
  }
}

// ── 编辑弹窗 ──
const modalOpen = ref(false)
const editing = ref(false)
interface DatasetForm {
  id?: string
  name: string
  remark: string
  sqlText: string
  cacheTtl: number
}
const form = reactive<DatasetForm>({
  name: '',
  remark: '',
  sqlText: '',
  cacheTtl: 0,
})
const previewResult = ref<DatasetExecuteResult | null>(null)
const previewLoading = ref(false)
const previewError = ref<string | null>(null)

function openCreate() {
  editing.value = false
  Object.assign(form, { id: undefined, name: '', remark: '', sqlText: '', cacheTtl: 0 })
  previewResult.value = null
  previewError.value = null
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
  previewResult.value = null
  previewError.value = null
  modalOpen.value = true
}

const previewColumns = ref<{ title: string; dataIndex: string; key: string; ellipsis: boolean }[]>(
  [],
)
const previewRows = ref<Record<string, unknown>[]>([])

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
  loadList()
}

async function remove(record: DashboardDatasetEntity) {
  if (!record.id) return
  await datasetRemove([record.id])
  message.success('已删除')
  loadList()
}

async function toggleEnable(record: DashboardDatasetEntity) {
  if (!record.id) return
  await datasetEnable(record.id, record.enabled ? 0 : 1)
  loadList()
}

function goBack() {
  router.push({ name: RouteNames.DASHBOARD_DESIGN })
}

onMounted(loadList)
</script>

<template>
  <div class="dataset-manage">
    <div class="dm-header">
      <div class="dm-title-wrap">
        <a-button type="text" @click="goBack">
          <template #icon><ArrowLeftOutlined /></template>
        </a-button>
        <span class="dm-title">数据集管理</span>
      </div>
      <a-button type="primary" @click="openCreate">
        <template #icon><PlusOutlined /></template>
        新建数据集
      </a-button>
    </div>

    <div class="dm-body">
      <a-table
        :columns="columns"
        :data-source="list"
        :loading="loading"
        row-key="id"
        size="middle"
        :pagination="false"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'enabled'">
            <a-tag :color="record.enabled ? 'green' : 'default'">
              {{ record.enabled ? '启用' : '停用' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a @click="openEdit(record)">编辑</a>
              <a @click="toggleEnable(record)">{{ record.enabled ? '停用' : '启用' }}</a>
              <a-popconfirm title="确认删除该数据集？" @confirm="remove(record)">
                <a class="danger">删除</a>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </div>

    <a-modal
      v-model:open="modalOpen"
      :title="editing ? '编辑数据集' : '新建数据集'"
      width="760px"
      :ok-text="'保存'"
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
.dataset-manage {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #fff;
}

.dm-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  border-bottom: 1px solid #f0f0f0;
}

.dm-title-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
}

.dm-title {
  font-size: 16px;
  font-weight: 600;
  color: #1a1a1a;
}

.dm-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 16px 24px;
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
