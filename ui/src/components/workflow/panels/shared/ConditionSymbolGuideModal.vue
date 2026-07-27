<script setup lang="ts">
import { ref } from 'vue'
import { QuestionCircleOutlined } from '@ant-design/icons-vue'

/** 单个运算符的说明文档 */
interface SymbolDoc {
  value: string
  label: string
  // 适用的输入类型与计算对象组合（与后端 VariableTypeSupportSymbol 白名单一致）
  support: string
  // 含义
  meaning: string
  // 运算规则
  behavior: string
  // 应用场景
  scenario: string
}

/** 运算符分组（与下拉选项顺序保持一致） */
interface SymbolGroup {
  title: string
  items: SymbolDoc[]
}

const symbolGroups: SymbolGroup[] = [
  {
    title: '数值比较',
    items: [
      {
        value: 'EQ',
        label: '等于',
        support: '数值（值本身）；字符串、数组（长度）',
        meaning: '判断输入值与比较值在数值语义上相等。',
        behavior: '值本身模式按数值比较，1 与 1.0 视为相等；长度模式比较字符串长度或数组元素个数是否等于比较值。',
        scenario: '判断接口返回状态码等于 200；判断列表长度等于 0 时走空数据分支。',
      },
      {
        value: 'NE',
        label: '不等于',
        support: '数值（值本身）；字符串、数组（长度）',
        meaning: '判断输入值与比较值在数值语义上不相等。',
        behavior: '「等于」的取反，同样支持长度模式下比较字符串长度或数组元素个数。',
        scenario: '过滤指定状态的数据，如执行结果不等于 0 时进入异常处理分支。',
      },
      {
        value: 'GT',
        label: '大于',
        support: '数值（值本身）；字符串、数组（长度）',
        meaning: '判断输入值大于比较值。',
        behavior: '值本身模式按数值大小比较；长度模式比较字符串长度或数组元素个数是否大于比较值。',
        scenario: '订单金额大于阈值时走人工审批分支；列表长度大于 0 时继续处理。',
      },
      {
        value: 'LT',
        label: '小于',
        support: '数值（值本身）；字符串、数组（长度）',
        meaning: '判断输入值小于比较值。',
        behavior: '值本身模式按数值大小比较；长度模式比较字符串长度或数组元素个数是否小于比较值。',
        scenario: '重试次数小于上限时继续重试；库存小于安全值时触发补货流程。',
      },
      {
        value: 'GE',
        label: '大于等于',
        support: '数值（值本身）；字符串、数组（长度）',
        meaning: '判断输入值大于或等于比较值。',
        behavior: '值本身模式按数值大小比较；长度模式比较字符串长度或数组元素个数是否不小于比较值。',
        scenario: '评分大于等于及格线时进入通过分支。',
      },
      {
        value: 'LE',
        label: '小于等于',
        support: '数值（值本身）；字符串、数组（长度）',
        meaning: '判断输入值小于或等于比较值。',
        behavior: '值本身模式按数值大小比较；长度模式比较字符串长度或数组元素个数是否不大于比较值。',
        scenario: '文本长度不超过限制（长度模式）时才允许发送消息。',
      },
    ],
  },
  {
    title: '包含与匹配',
    items: [
      {
        value: 'CONTAINS',
        label: '包含',
        support: '字符串、数组（仅值本身）',
        meaning: '字符串包含指定子串，或数组包含指定元素。',
        behavior: '输入为字符串时做子串查找；输入为数组时判断元素是否存在于数组中。',
        scenario: '日志内容包含 error 关键字时触发告警；标签列表包含某标签时走定向推送。',
      },
      {
        value: 'NOT_CONTAINS',
        label: '不包含',
        support: '字符串、数组（仅值本身）',
        meaning: '字符串不包含指定子串，或数组不包含指定元素。',
        behavior: '「包含」的取反。',
        scenario: '回复内容不包含敏感词时才继续发送。',
      },
      {
        value: 'IS_ALL',
        label: '全部是',
        support: '数组（仅值本身）',
        meaning: '数组中所有元素都等于比较值时才为真。',
        behavior: '逐个遍历数组元素与比较值做相等判断，任一不等即为假。',
        scenario: '批量任务结果全部为 success 时才进入汇总分支。',
      },
      {
        value: 'STARTS_WITH',
        label: '开头匹配',
        support: '字符串（仅值本身）',
        meaning: '判断输入字符串以比较值开头。',
        behavior: '前缀匹配，区分大小写。',
        scenario: '单号以 ORD- 开头时走订单处理流程。',
      },
      {
        value: 'ENDS_WITH',
        label: '结尾匹配',
        support: '字符串（仅值本身）',
        meaning: '判断输入字符串以比较值结尾。',
        behavior: '后缀匹配，区分大小写。',
        scenario: '文件名以 .pdf 结尾时走文档解析分支。',
      },
    ],
  },
  {
    title: '严格比较',
    items: [
      {
        value: 'EQUALS',
        label: '严格等于',
        support: '字符串（仅值本身）',
        meaning: '判断两个字符串完全一致，区分大小写。',
        behavior: '区别于「等于」的数值语义，这里是字符级精确比较，"1" 与 "1.0" 不相等。',
        scenario: '精确匹配指令文本，如输入严格等于 confirm 时执行操作。',
      },
      {
        value: 'NOT_EQUALS',
        label: '严格不等于',
        support: '字符串（仅值本身）',
        meaning: '判断两个字符串不完全一致。',
        behavior: '「严格等于」的取反。',
        scenario: '输入不是保留关键字时才允许作为自定义名称。',
      },
    ],
  },
  {
    title: '布尔判断',
    items: [
      {
        value: 'IS_TRUE',
        label: '为 true',
        support: '布尔（仅值本身）',
        meaning: '判断输入布尔值为 true。',
        behavior: '直接判断输入本身，比较值不参与运算（仍需填写任意值以通过配置校验）。',
        scenario: '上游校验节点输出通过标记为 true 时进入下一步。',
      },
      {
        value: 'IS_FALSE',
        label: '为 false',
        support: '布尔（仅值本身）',
        meaning: '判断输入布尔值为 false。',
        behavior: '直接判断输入本身，比较值不参与运算（仍需填写任意值以通过配置校验）。',
        scenario: '开关状态为 false 时走降级分支。',
      },
    ],
  },
  {
    title: '表达式',
    items: [
      {
        value: 'EXPRESSION',
        label: '表达式',
        support: '任意类型；对象类型输入只能使用此运算符',
        meaning: '编写 Groovy 表达式对输入自定义求值，可表达任意复合条件。',
        behavior: '表达式中通过输入绑定名引用输入值；结果为布尔时直接使用，数字非 0 为真，字符串非空为真。选中后不再需要配置比较值与计算对象。',
        scenario: '输入为复杂对象或多条件组合，如 input.age > 18 && input.vip。',
      },
    ],
  },
]

const open = ref(false)
const activeSymbol = ref('EQ')
const contentRef = ref<HTMLElement>()
const sectionEls: Record<string, HTMLElement> = {}
// 点击导航平滑滚动期间暂停滚动联动，避免高亮抖动
let suppressSpy = false
let suppressTimer: number | undefined

function setSectionEl(value: string, el: unknown) {
  if (el instanceof HTMLElement) sectionEls[value] = el
}

function scrollToSymbol(value: string) {
  activeSymbol.value = value
  const container = contentRef.value
  const target = sectionEls[value]
  if (!container || !target) return
  suppressSpy = true
  window.clearTimeout(suppressTimer)
  suppressTimer = window.setTimeout(() => {
    suppressSpy = false
  }, 600)
  container.scrollTo({ top: Math.max(target.offsetTop - 4, 0), behavior: 'smooth' })
}

// 内容区滚动时反向高亮左侧导航
function onContentScroll() {
  if (suppressSpy) return
  const container = contentRef.value
  if (!container) return
  const anchor = container.scrollTop + 48
  let current = activeSymbol.value
  for (const group of symbolGroups) {
    for (const item of group.items) {
      const el = sectionEls[item.value]
      if (el && el.offsetTop <= anchor) current = item.value
    }
  }
  activeSymbol.value = current
}
</script>

<template>
  <span class="guide-trigger" @click="open = true">
    <QuestionCircleOutlined />
  </span>

  <AModal
    v-model:open="open"
    title="运算符说明"
    width="860px"
    :footer="null"
    :destroy-on-close="true"
  >
    <div class="symbol-guide-layout">
      <!-- 左侧运算符导航 -->
      <nav class="guide-sidebar">
        <template v-for="group in symbolGroups" :key="group.title">
          <div class="sidebar-group">{{ group.title }}</div>
          <button
            v-for="item in group.items"
            :key="item.value"
            :class="['sidebar-item', { active: activeSymbol === item.value }]"
            type="button"
            @click="scrollToSymbol(item.value)"
          >
            <span class="item-label">{{ item.label }}</span>
            <span class="item-code">{{ item.value }}</span>
          </button>
        </template>
      </nav>

      <!-- 右侧说明内容 -->
      <div ref="contentRef" class="guide-content" @scroll="onContentScroll">
        <p class="guide-intro">
          运算符决定分支条件的判定方式。注意两点：输入为空时不执行运算，直接取「输入为空时视为True」开关的值；
          运算符需与输入类型、计算对象匹配，不匹配将在运行时报错，其中「长度」模式仅支持数值比较类运算符。
        </p>
        <template v-for="group in symbolGroups" :key="group.title">
          <div class="content-group-title">{{ group.title }}</div>
          <section
            v-for="item in group.items"
            :key="item.value"
            :ref="(el) => setSectionEl(item.value, el)"
            class="symbol-section"
          >
            <h3 class="symbol-heading">
              {{ item.label }}
              <code>{{ item.value }}</code>
            </h3>
            <div class="symbol-support">适用：{{ item.support }}</div>
            <div class="symbol-row">
              <span class="row-label">含义</span>
              <span class="row-text">{{ item.meaning }}</span>
            </div>
            <div class="symbol-row">
              <span class="row-label">规则</span>
              <span class="row-text">{{ item.behavior }}</span>
            </div>
            <div class="symbol-row">
              <span class="row-label">场景</span>
              <span class="row-text">{{ item.scenario }}</span>
            </div>
          </section>
        </template>
      </div>
    </div>
  </AModal>
</template>

<style scoped lang="scss">
.guide-trigger {
  display: inline-flex;
  align-items: center;
  color: #8c8c8c;
  font-size: 13px;
  cursor: pointer;
  user-select: none;

  &:hover {
    color: #1677ff;
  }
}

.symbol-guide-layout {
  display: grid;
  grid-template-columns: 172px minmax(0, 1fr);
  height: clamp(420px, 60vh, 640px);
}

// ── 左侧导航 ──
.guide-sidebar {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding-right: 12px;
  border-right: 1px solid #f0f0f0;
  overflow-y: auto;
}

.sidebar-group {
  margin: 10px 0 2px;
  padding: 0 10px;
  font-size: 12px;
  color: #bfbfbf;

  &:first-child {
    margin-top: 0;
  }
}

.sidebar-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 5px 10px;
  border: none;
  border-radius: 6px;
  background: transparent;
  cursor: pointer;
  text-align: left;
  transition: all 0.15s;

  .item-label {
    font-size: 13px;
    color: #595959;
  }

  .item-code {
    font-size: 11px;
    font-family: 'JetBrains Mono', 'Consolas', monospace;
    color: #bfbfbf;
  }

  &:hover {
    background: #f5f5f5;

    .item-label {
      color: #262626;
    }
  }

  &.active {
    background: #f0f5ff;

    .item-label {
      color: #1677ff;
      font-weight: 600;
    }

    .item-code {
      color: #1677ff;
    }
  }
}

// ── 右侧内容 ──
.guide-content {
  position: relative;
  min-width: 0;
  padding: 0 4px 0 20px;
  overflow-y: auto;
}

.guide-intro {
  margin: 0 0 20px;
  font-size: 13px;
  color: #8c8c8c;
  line-height: 1.75;
}

.content-group-title {
  margin: 0 0 12px;
  padding-bottom: 6px;
  border-bottom: 1px solid #f0f0f0;
  font-size: 15px;
  font-weight: 700;
  color: #262626;
}

.symbol-section {
  margin-bottom: 22px;
}

.symbol-heading {
  margin: 0 0 4px;
  font-size: 14px;
  font-weight: 700;
  color: #262626;

  code {
    margin-left: 4px;
    padding: 1px 5px;
    background: #f5f5f5;
    border-radius: 3px;
    font-size: 12px;
    font-weight: normal;
    font-family: 'JetBrains Mono', 'Consolas', monospace;
    color: #d4380d;
  }
}

.symbol-support {
  margin-bottom: 8px;
  font-size: 12px;
  color: #8c8c8c;
}

.symbol-row {
  display: flex;
  gap: 10px;
  margin-bottom: 4px;

  .row-label {
    flex-shrink: 0;
    font-size: 13px;
    color: #bfbfbf;
  }

  .row-text {
    font-size: 13px;
    color: #434343;
    line-height: 1.7;
  }
}
</style>
