<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { QuestionCircleOutlined } from '@ant-design/icons-vue'
import PanelSection from '../shared/PanelSection.vue'
import FormatterGuideModal from '../shared/FormatterGuideModal.vue'
import NodeNameInput from '../shared/NodeNameInput.vue'
import BlurInput from '../shared/BlurInput.vue'
import InputBindingSection from '../shared/InputBindingSection.vue'
import OutputDisplay from '../shared/OutputDisplay.vue'
import WorkflowArrayEditors from '@/components/workflow/fields/WorkflowArrayEditors.vue'
import ConfigCodeEditor from '@/components/editor/ConfigCodeEditor.vue'
import type { WorkflowFlowEdge, WorkflowFlowNode, WorkflowResourceMaps } from '@/types/workflow'

const panelRoot = ref<HTMLElement>()
const isEditorMaximized = ref(false)

function onEditorMaximizeChange(val: boolean) {
  isEditorMaximized.value = val
}

const props = defineProps<{
  node: WorkflowFlowNode
  nodes: WorkflowFlowNode[]
  edges: WorkflowFlowEdge[]
  resources: WorkflowResourceMaps
}>()
const emit = defineEmits<{ update: [node: WorkflowFlowNode] }>()

function updateNode(patch: Partial<WorkflowFlowNode['data']>) {
  emit('update', { ...props.node, data: { ...props.node.data, ...patch } })
}
function updateConfig(key: string, value: unknown) {
  updateNode({ config: { ...(props.node.data.config || {}), [key]: value } })
}

function getRequest() {
  return (
    (props.node.data.config?.request as Record<string, unknown>) || {
      method: 'GET',
      contentType: 'JSON',
      queryParams: [],
      headers: [],
      body: '',
    }
  )
}
function updateRequest(key: string, value: unknown) {
  updateConfig('request', { ...getRequest(), [key]: value })
}

// Body 始终存为字符串（后端 JSON 类型要求 String）
function stringifyBody(v: unknown) {
  if (v === undefined || v === null) return ''
  return typeof v === 'string' ? v : JSON.stringify(v, null, 2)
}
function updateBody(v: string) {
  // 始终以字符串存储，不做 JSON.parse
  updateRequest('body', v)
}

const activeTab = ref<'headers' | 'params' | 'body'>('headers')

const formatterOptions = [
  { label: '纯文本替换', value: 'STRING' },
  { label: 'JSON 保类型', value: 'JACKSON' },
  { label: 'Velocity 模板', value: 'VELOCITY' },
]

const isFormBody = computed(() => {
  const ct = getRequest().contentType
  return ct === 'FORM_URLENCODED' || ct === 'FORM_DATA'
})

const bodyPlaceholder = computed(() =>
  isFormBody.value
    ? 'key1=value1&key2=value2'
    : '支持 ${输入绑定名} 模板语法，如 {\"id\":\"${input.id}\"}',
)

const showBody = computed(() => {
  const m = getRequest().method
  return m === 'POST' || m === 'PUT' || m === 'DELETE' || m === 'PATCH'
})

// 方法切换时如果 Body 不可用则切回 Headers
watch(showBody, (val) => {
  if (!val && activeTab.value === 'body') activeTab.value = 'headers'
})

const headerCount = computed(() => {
  const h = getRequest().headers
  return Array.isArray(h) ? h.length : 0
})
const queryCount = computed(() => {
  const q = getRequest().queryParams
  return Array.isArray(q) ? q.length : 0
})
</script>

<template>
  <div ref="panelRoot" class="http-external-panel" :class="{ 'editor-maximized': isEditorMaximized }">
  <AForm layout="vertical">
    <PanelSection title="节点名称">
      <NodeNameInput
        :model-value="node.data.label"
        @update:model-value="(v: any) => updateNode({ label: v })"
      />
    </PanelSection>

    <InputBindingSection
      :model-value="node.data.inputConfigs"
      :nodes="nodes"
      :edges="edges"
      :current-node-id="node.id"
      @update:model-value="(v: any) => updateNode({ inputConfigs: v })"
    />

    <PanelSection title="节点配置">
       <!-- 模板提示 -->
      <div class="template-hint">
        💡 URL、Params、Headers、Body 均支持 ${输入绑定名} 引用输入绑定，由「高级设置」→「模板格式」统一渲染。
      </div>

      <!-- Hero: URL 是绝对主角 -->
      <div class="url-hero">
        <ASelect
          :value="getRequest().method || 'GET'"
          :options="
            ['GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'HEAD', 'OPTIONS'].map((v: any) => ({
              label: v,
              value: v,
            }))
          "
          @update:value="(v: any) => updateRequest('method', v)"
        />
        <BlurInput
          :model-value="String(getRequest().url || '')"
          placeholder="https://api.example.com/users/${id}"
          @update:model-value="(v: any) => updateRequest('url', v)"
        />
      </div>

      <!-- 请求配置 -->
      <div class="segmented-tabs">
        <button
          class="seg-btn"
          :class="{ active: activeTab === 'headers' }"
          @click="activeTab = 'headers'"
        >
          Headers<sup class="seg-badge">{{ headerCount }}</sup>
        </button>
        <button
          class="seg-btn"
          :class="{ active: activeTab === 'params' }"
          @click="activeTab = 'params'"
        >
          Params<sup class="seg-badge">{{ queryCount }}</sup>
        </button>
        <button
          v-if="showBody"
          class="seg-btn"
          :class="{ active: activeTab === 'body' }"
          @click="activeTab = 'body'"
        >
          Body
        </button>
      </div>

      <div class="tab-content">
        <div v-show="activeTab === 'headers'">
          <WorkflowArrayEditors
            :model-value="getRequest().headers"
            type="keyValue"
            @update:model-value="(v: any) => updateRequest('headers', v)"
          />
        </div>
        <div v-show="activeTab === 'params'">
          <WorkflowArrayEditors
            :model-value="getRequest().queryParams"
            type="keyValue"
            @update:model-value="(v: any) => updateRequest('queryParams', v)"
          />
        </div>
        <div v-if="activeTab === 'body' && showBody">
          <ConfigCodeEditor
            :model-value="stringifyBody(getRequest().body)"
            language="txt"
            :placeholder="bodyPlaceholder"
            :maximize-target="panelRoot"
            height="200px"
            @update:model-value="updateBody"
            @maximize-change="onEditorMaximizeChange"
          />
        </div>
      </div>

      <!-- 高级设置 -->
      <ACollapse ghost class="advanced-card">
        <ACollapsePanel key="advanced" header="高级设置">
          <div class="adv-row">
            <span class="adv-label">模板格式</span>
            <div class="formatter-selector">
              <FormatterGuideModal />
              <ASelect
                :value="node.data.config?.formatterType || 'STRING'"
                :options="formatterOptions"
                style="width: 140px"
                @update:value="(v: any) => updateConfig('formatterType', v)"
              />
            </div>
          </div>
          <div class="adv-row">
            <span class="adv-label">
              Content-Type
              <ATooltip title="设置 HTTP 请求体的内容类型，决定数据的编码格式">
                <QuestionCircleOutlined class="help-icon" />
              </ATooltip>
            </span>
            <ASelect
              class="adv-control"
              :value="getRequest().contentType || 'JSON'"
              :options="[
                { label: 'JSON', value: 'JSON' },
                { label: 'Form', value: 'FORM_URLENCODED' },
                { label: 'Form Data', value: 'FORM_DATA' },
                { label: 'XML', value: 'XML' },
                { label: 'Text', value: 'TEXT_PLAIN' },
                { label: 'Binary', value: 'OCTET_STREAM' },
              ]"
              style="width: 140px"
              @update:value="(v: any) => updateRequest('contentType', v)"
            />
          </div>
          <div class="adv-row">
            <span class="adv-label">
              连接超时(秒)
              <ATooltip title="建立 TCP 连接的最大等待时间，超时则本次请求失败">
                <QuestionCircleOutlined class="help-icon" />
              </ATooltip>
            </span>
            <AInputNumber
              class="adv-control"
              :value="Number(node.data.config?.connectTimeout ?? 10)"
              :min="0"
              @update:value="(v: any) => updateConfig('connectTimeout', v)"
            />
          </div>
          <div class="adv-row">
            <span class="adv-label">
              读取超时(秒)
              <ATooltip title="等待服务器响应数据的最大时间，超时则本次请求失败">
                <QuestionCircleOutlined class="help-icon" />
              </ATooltip>
            </span>
            <AInputNumber
              class="adv-control"
              :value="Number(node.data.config?.readTimeout ?? 30)"
              :min="0"
              @update:value="(v: any) => updateConfig('readTimeout', v)"
            />
          </div>
          <div class="adv-row">
            <span class="adv-label">
              写入超时(秒)
              <ATooltip title="向服务器发送请求数据的最大时间，超时则本次请求失败">
                <QuestionCircleOutlined class="help-icon" />
              </ATooltip>
            </span>
            <AInputNumber
              class="adv-control"
              :value="Number(node.data.config?.writeTimeout ?? 30)"
              :min="0"
              @update:value="(v: any) => updateConfig('writeTimeout', v)"
            />
          </div>
          <div class="adv-row">
            <span class="adv-label">
              最大重试次数
              <ATooltip title="请求失败后的最大自动重试次数，设为 0 则不重试">
                <QuestionCircleOutlined class="help-icon" />
              </ATooltip>
            </span>
            <AInputNumber
              class="adv-control"
              :value="Number(node.data.config?.maxRetries ?? 3)"
              :min="0"
              @update:value="(v: any) => updateConfig('maxRetries', v)"
            />
          </div>
          <div class="adv-row">
            <span class="adv-label">
              跟随重定向
              <ATooltip title="是否自动跟随 3xx 重定向响应，开启后客户端会请求新地址">
                <QuestionCircleOutlined class="help-icon" />
              </ATooltip>
            </span>
            <ASwitch
              :checked="Boolean(node.data.config?.followRedirects ?? true)"
              @update:checked="(v: any) => updateConfig('followRedirects', v)"
            />
          </div>
          <div class="adv-row">
            <span class="adv-label">
              同步执行
              <ATooltip title="开启后同步等待请求结果并返回响应；关闭后异步执行，直接返回固定字符串">
                <QuestionCircleOutlined class="help-icon" />
              </ATooltip>
            </span>
            <ASwitch
              :checked="Boolean(node.data.config?.syncExecute ?? true)"
              @update:checked="(v: any) => updateConfig('syncExecute', v)"
            />
          </div>
          <div class="adv-row">
            <span class="adv-label">
              响应体转 JSON
              <ATooltip title="是否将响应体自动解析为 JSON 对象，开启后可提取结构化数据">
                <QuestionCircleOutlined class="help-icon" />
              </ATooltip>
            </span>
            <ASwitch
              :checked="Boolean(node.data.config?.bodyToObject ?? true)"
              @update:checked="(v: any) => updateConfig('bodyToObject', v)"
            />
          </div>
          <div class="adv-row-block">
            <span class="adv-label">
              重试状态码
              <ATooltip title="触发自动重试的 HTTP 状态码列表，留空则默认对 5xx、408、429 重试">
                <QuestionCircleOutlined class="help-icon" />
              </ATooltip>
            </span>
            <WorkflowArrayEditors
              :model-value="node.data.config?.retryStatusCodes"
              type="stringList"
              @update:model-value="(v: any) => updateConfig('retryStatusCodes', v)"
            />
          </div>
        </ACollapsePanel>
      </ACollapse>
    </PanelSection>

    <PanelSection title="输出说明">
      <OutputDisplay :outputs="node.data.outputConfigs || []" />
    </PanelSection>
  </AForm>
  </div>
</template>

<style scoped lang="scss">
.http-external-panel {
  position: relative;

  &.editor-maximized {
    height: 100%;
    overflow: hidden;
  }
}

// ① URL Hero
.url-hero {
  display: grid;
  grid-template-columns: 100px minmax(0, 1fr);
  gap: 5px;
  border-radius: 8px;
  overflow: hidden;
  margin-bottom: 12px;
}

// ② Content-Type
.ct-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.ct-label {
  font-size: 14px;
  color: rgba(0, 0, 0, 0.88);
  flex-shrink: 0;
}

// ③ Segmented Slider Tabs
.segmented-tabs {
  display: flex;
  width: 100%;
  gap: 0;
  padding: 3px;
  border-radius: 8px;
  background: #F2F4F7;
  margin-bottom: 8px;
}

.seg-btn {
  flex: 1;
  position: relative;
  z-index: 1;
  padding: 3px 0;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: #8c8c8c;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
  text-align: center;
  outline: none;

  &.active {
    background: #fff;
    color: #262626;
    font-weight: 600;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1), 0 1px 1px rgba(0, 0, 0, 0.06);
  }

  &:hover:not(.active) {
    color: #595959;
  }
}

.seg-badge {
  margin-left: 3px;
  color: inherit;
  font-size: 10px;
  font-weight: 400;
  opacity: 0.7;
}

.tab-content {
  margin-bottom: 8px;
}

// ④ Format Row
.format-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.format-label {
  font-size: 14px;
  color: rgba(0, 0, 0, 0.88);
  line-height: 1.5;
}

// ⑤ Template Hint
.template-hint {
  padding: 8px 10px;
  border-radius: 6px;
  background: #f6f8fa;
  color: #8c8c8c;
  font-size: 12px;
  line-height: 1.6;
  margin-bottom: 12px;

  code {
    padding: 1px 4px;
    background: #ededed;
    border-radius: 3px;
    font-size: 11px;
    font-family: 'JetBrains Mono', 'Consolas', monospace;
    color: #d4380d;
  }
}

// ⑥ Advanced Card
.advanced-card {
  margin-top: 14px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fafafa;

  :deep(.ant-collapse-header) {
    padding: 10px 14px !important;
    font-size: 13px;
    font-weight: 600;
    color: #595959;
  }

  :deep(.ant-collapse-content-box) {
    padding: 0 14px 12px !important;
  }

  :deep(.ant-collapse-item) {
    border: none !important;
  }
}

.adv-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  min-height: 32px;
}

.adv-label {
  font-size: 13px;
  color: rgba(0, 0, 0, 0.65);
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.help-icon {
  color: rgba(0, 0, 0, 0.25);
  font-size: 13px;
  cursor: help;

  &:hover {
    color: rgba(0, 0, 0, 0.45);
  }
}

.adv-control {
  width: 140px;
}

.adv-row-block {
  margin-bottom: 8px;

  .adv-label {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    margin-bottom: 6px;
    font-size: 13px;
    color: rgba(0, 0, 0, 0.65);
  }
}

// Shared
.formatter-selector {
  display: flex;
  align-items: center;
  gap: 6px;
}

@media (max-width: 720px) {
  .url-hero {
    grid-template-columns: 1fr;
  }
}
</style>
