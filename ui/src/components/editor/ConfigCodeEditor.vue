<template>
  <div
    v-memo="[isMaximized, languageLabel]"
    class="config-code-editor"
    :class="{ maximized: isMaximized }"
    :style="isMaximized ? maximizedStyle : normalStyle"
  >
    <!-- 头部工具栏 -->
    <div class="editor-header">
      <span class="lang-badge">{{ languageLabel }}</span>
      <div class="header-actions">
        <button class="action-btn" title="格式化" @click="formatCode">
          <FormatPainterOutlined />
        </button>
        <button class="action-btn" title="复制" @click="copyCode">
          <CopyOutlined />
        </button>
        <button class="action-btn" title="最大化" @click="toggleMaximize">
          <FullscreenExitOutlined v-if="isMaximized" />
          <FullscreenOutlined v-else />
        </button>
      </div>
    </div>

    <!-- 编辑器主体：v-once 冻结，Vue 永不触碰此 DOM -->
    <div v-once ref="editorContainer" class="editor-body"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, computed, shallowRef, markRaw, nextTick } from 'vue'
import {
  FormatPainterOutlined,
  CopyOutlined,
  FullscreenExitOutlined,
  FullscreenOutlined,
} from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'

type SupportedLanguage = 'java' | 'python' | 'javascript' | 'sql'

// ── CodeMirror 内部类型 ──
// eslint-disable-next-line @typescript-eslint/no-explicit-any
type CMExtension = any
// eslint-disable-next-line @typescript-eslint/no-explicit-any
type CMEditorView = any
// eslint-disable-next-line @typescript-eslint/no-explicit-any
type CMModules = any

const props = withDefaults(
  defineProps<{
    modelValue: string
    language?: SupportedLanguage
    placeholder?: string
    readonly?: boolean
    height?: string
    maximizeTarget?: HTMLElement | null
  }>(),
  {
    language: 'java',
    placeholder: '',
    readonly: false,
    height: '280px',
    maximizeTarget: null,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
  maximizeChange: [value: boolean]
}>()

const editorContainer = ref<HTMLElement>()
const editorView = shallowRef<CMEditorView>(null)
const isMaximized = ref(false)

// SmartCodeEditor 同款：内部更新标记，防止 watch 误触发 dispatch
const isInternalUpdate = ref(false)

const modules = shallowRef<CMModules>({} as CMModules)
const langExtCache = shallowRef<Map<string, CMExtension>>(new Map())

const languageLabel = computed(() => {
  const map: Record<string, string> = {
    java: 'Java',
    python: 'Python',
    javascript: 'JavaScript',
    sql: 'SQL',
  }
  return map[props.language] || props.language
})

// ── 最大化：position:absolute + inset:0 填满父容器，天然自适应宽高 ──
const maximizedStyle = computed(() => {
  if (!isMaximized.value) return undefined
  return {
    position: 'absolute' as const,
    top: '0',
    left: '0',
    right: '0',
    bottom: '0',
    zIndex: '50',
    borderRadius: '3px',
  }
})

// 非最大化高度：用 shallowRef 保持引用稳定，避免每次渲染都触发 DOM style 写入
const normalStyle = shallowRef<Record<string, string>>({ height: props.height })
watch(() => props.height, (h) => { normalStyle.value = { height: h } })

// ── 加载 CodeMirror 模块 ──
async function initModules() {
  if (Object.keys(modules.value).length > 0) return
  const [state, view, commands, language, autocomplete] = await Promise.all([
    import('@codemirror/state'),
    import('@codemirror/view'),
    import('@codemirror/commands'),
    import('@codemirror/language'),
    import('@codemirror/autocomplete'),
  ])
  modules.value = markRaw({
    EditorState: state.EditorState,
    EditorView: view.EditorView,
    keymap: view.keymap,
    lineNumbers: view.lineNumbers,
    highlightActiveLine: view.highlightActiveLine,
    highlightActiveLineGutter: view.highlightActiveLineGutter,
    drawSelection: view.drawSelection,
    dropCursor: view.dropCursor,
    rectangularSelection: view.rectangularSelection,
    crosshairCursor: view.crosshairCursor,
    defaultKeymap: commands.defaultKeymap,
    history: commands.history,
    historyKeymap: commands.historyKeymap,
    indentWithTab: commands.indentWithTab,
    indentMore: commands.indentMore,
    indentSelection: commands.indentSelection,
    bracketMatching: language.bracketMatching,
    indentOnInput: language.indentOnInput,
    syntaxHighlighting: language.syntaxHighlighting,
    defaultHighlightStyle: language.defaultHighlightStyle,
    closeBrackets: autocomplete.closeBrackets,
    closeBracketsKeymap: autocomplete.closeBracketsKeymap,
  })
}

async function getLanguageExtension(lang: string): Promise<CMExtension> {
  if (langExtCache.value.has(lang)) return langExtCache.value.get(lang)!
  let ext: CMExtension = null
  switch (lang) {
    case 'java': {
      const { java } = await import('@codemirror/lang-java')
      ext = java()
      break
    }
    case 'python': {
      const { python } = await import('@codemirror/lang-python')
      ext = python()
      break
    }
    case 'javascript': {
      const { javascript } = await import('@codemirror/lang-javascript')
      ext = javascript()
      break
    }
    case 'sql': {
      const { sql, PostgreSQL } = await import('@codemirror/lang-sql')
      ext = sql({ dialect: PostgreSQL })
      break
    }
    default:
      ext = []
  }
  langExtCache.value.set(lang, markRaw(ext))
  return ext
}

// ── placeholder：用绝对定位 DOM 覆盖，空文档 + 失焦时显示 ──
async function buildPlaceholderExtension(text: string): Promise<CMExtension> {
  const { ViewPlugin } = await import('@codemirror/view')

  return ViewPlugin.fromClass(
    class {
      dom: HTMLElement | null = null
      constructor(readonly view: CMEditorView) {
        this.dom = document.createElement('div')
        this.dom.textContent = text
        this.dom.style.cssText =
          'position:absolute;top:4px;left:38px;color:#bfbfbf;font-style:italic;pointer-events:none;user-select:none;font-size:13px;line-height:1.65;font-family:inherit;white-space:nowrap;'
        this.view.dom.appendChild(this.dom)
        this.sync()
      }
      sync() {
        if (!this.dom) return
        const empty = this.view.state.doc.length === 0
        const focused = this.view.hasFocus
        this.dom.style.display = empty && !focused ? '' : 'none'
      }
      update(update: CMEditorView) {
        if (update.docChanged || update.focusChanged) this.sync()
      }
      destroy() {
        this.dom?.remove()
        this.dom = null
      }
    },
  )
}

// ── 内容双向同步（和 SmartCodeEditor 完全一致的模式） ──
// updateListener 里：先置 isInternalUpdate=true → emit → nextTick 复位
// watch 里：检测 isInternalUpdate 跳过内部触发的更新

// ── 创建编辑器 ──
async function createEditor() {
  await initModules()
  const m = modules.value
  const { EditorState, EditorView } = m

  const langExt = await getLanguageExtension(props.language)
  let phExt: CMExtension = []
  if (props.placeholder) {
    phExt = await buildPlaceholderExtension(props.placeholder)
  }

  const extensions = [
    m.history(),
    m.drawSelection(),
    m.dropCursor(),
    m.lineNumbers(),
    m.highlightActiveLine(),
    m.highlightActiveLineGutter(),
    m.syntaxHighlighting(m.defaultHighlightStyle, { fallback: true }),
    m.indentOnInput(),
    m.closeBrackets(),
    m.bracketMatching(),
    m.rectangularSelection(),
    m.crosshairCursor(),
    EditorView.lineWrapping,
    EditorState.tabSize.of(2),
    EditorState.readOnly.of(props.readonly),
    EditorView.updateListener.of((update: CMEditorView) => {
      if (update.docChanged) {
        const content = update.state.doc.toString()
        isInternalUpdate.value = true
        emit('update:modelValue', content)
        nextTick(() => {
          isInternalUpdate.value = false
        })
      }
    }),
  ]

  if (langExt) extensions.push(langExt)
  if (phExt) extensions.push(phExt)

  extensions.push(
    m.keymap.of([
      ...m.defaultKeymap,
      ...m.historyKeymap,
      ...m.closeBracketsKeymap,
      m.indentWithTab,
    ]),
  )

  const state = EditorState.create({
    doc: props.modelValue,
    extensions: extensions.filter(Boolean),
  })

  const view = new EditorView({
    state,
    parent: editorContainer.value!,
  })

  editorView.value = markRaw(view)
}

function destroyEditor() {
  if (editorView.value) {
    editorView.value.destroy()
    editorView.value = null
  }
}

// ── 监听外部 modelValue 变化（SmartCodeEditor 同款：用 isInternalUpdate 跳过自身触发的更新） ──
watch(
  () => props.modelValue,
  (newValue) => {
    const view = editorView.value
    if (!view) return
    if (isInternalUpdate.value) return
    const current = view.state.doc.toString()
    if (newValue !== current) {
      view.dispatch({
        changes: { from: 0, to: current.length, insert: newValue },
      })
    }
  },
)

// ── 语言切换 ──
watch(
  () => props.language,
  async () => {
    const view = editorView.value
    if (!view) return
    const langExt = await getLanguageExtension(props.language)
    view.dispatch({
      effects: modules.value.EditorState.reconfigure.of(langExt ? [langExt] : []),
    })
  },
)

// ── 操作 ──
function formatCode() {
  const view = editorView.value
  if (!view) return
  const m = modules.value
  const { state } = view
  view.dispatch({
    selection: { anchor: 0, head: state.doc.length },
  })
  m.indentSelection({
    state: view.state,
    dispatch: (tr: CMEditorView) => view.dispatch(tr),
  })
  view.focus()
}

async function copyCode() {
  const view = editorView.value
  if (!view) return
  const code = view.state.doc.toString()
  try {
    await navigator.clipboard.writeText(code)
    message.success('已复制')
  } catch {
    message.error('复制失败')
  }
}

function toggleMaximize() {
  isMaximized.value = !isMaximized.value
  emit('maximizeChange', isMaximized.value)
  nextTick(() => {
    editorView.value?.requestMeasure()
    editorView.value?.focus()
  })
}

// ── ResizeObserver：最大化时监听容器尺寸变化，更新位置+触发自适应 ──
let resizeObserver: ResizeObserver | null = null

function setupResizeObserver() {
  if (!props.maximizeTarget) return
  resizeObserver = new ResizeObserver(() => {
    if (isMaximized.value) {
      editorView.value?.requestMeasure()
    }
  })
  resizeObserver.observe(props.maximizeTarget)
}

function teardownResizeObserver() {
  resizeObserver?.disconnect()
  resizeObserver = null
}

// ── ESC 退出最大化 ──
function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && isMaximized.value) {
    isMaximized.value = false
    emit('maximizeChange', false)
    nextTick(() => editorView.value?.requestMeasure())
  }
}

onMounted(async () => {
  await createEditor()
  setupResizeObserver()
  document.addEventListener('keydown', onKeydown)
})

onUnmounted(() => {
  destroyEditor()
  teardownResizeObserver()
  document.removeEventListener('keydown', onKeydown)
})
</script>

<style scoped lang="scss">
.config-code-editor {
  display: flex;
  flex-direction: column;
  border-radius: 8px;
  background: #f2f4f7;
  overflow: hidden;
  min-height: 200px;
  transition: box-shadow 0.25s;
}

.editor-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
  padding: 6px 12px 6px 16px;
  user-select: none;
}

.lang-badge {
  font-size: 12px;
  font-weight: 600;
  color: #8c8c8c;
  letter-spacing: 0.3px;
  text-transform: uppercase;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}

.action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: #8c8c8c;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.15s;

  &:hover {
    background: #e3e6eb;
    color: #595959;
  }

  &:active {
    background: #d7dae0;
    color: #434343;
  }
}

.editor-body {
  flex: 1;
  overflow: hidden;

  // CodeMirror 样式覆盖 —— 融入 #F2F4F7 背景
  :deep(.cm-editor) {
    height: 100%;
    background: #f2f4f7;
    font-size: 13px;
    font-family: 'JetBrains Mono', 'Fira Code', 'Cascadia Code', 'Consolas', 'Monaco',
      monospace;
    line-height: 1.65;

    .cm-scroller {
      overflow: auto;
      font-family: inherit;
    }

    .cm-content {
      padding: 4px 0;
      caret-color: #1677ff;
    }

    .cm-gutters {
      background: #f2f4f7;
      border-right: none;
      color: #bfbfbf;
      font-size: 11px;
      padding-right: 8px;
      min-width: 36px;
      user-select: none;
    }

    .cm-activeLineGutter {
      background: rgba(0, 0, 0, 0.03);
    }

    .cm-activeLine {
      background: rgba(0, 0, 0, 0.03);
    }

    .cm-cursor {
      border-left-color: #1677ff;
    }

    .cm-selectionBackground,
    .cm-selectionMatch {
      background: rgba(22, 119, 255, 0.15) !important;
    }

    .cm-matchingBracket {
      background: rgba(22, 119, 255, 0.12);
      outline: 1px solid rgba(22, 119, 255, 0.3);
    }

    .cm-nonmatchingBracket {
      color: #ff4d4f;
    }

    .cm-foldPlaceholder {
      background: #e3e6eb;
      border-color: #d7dae0;
      color: #8c8c8c;
    }

    .cm-tooltip {
      background: #fff;
      border: 1px solid #e3e6eb;
      border-radius: 8px;
      box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
      padding: 4px 8px;
      font-size: 12px;
    }
  }
}
</style>
