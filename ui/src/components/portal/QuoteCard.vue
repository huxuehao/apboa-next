<script setup lang="ts">
/**
 * 每日一言：内置分类语录库，按日期稳定轮换，支持换一句与复制。
 * 语录以打字机逐字展开（内容驱动的一次性动画），署名在打字完成后淡入。
 *
 * @author huxuehao
 */
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { message } from 'ant-design-vue'

defineOptions({
  portalMeta: {
    name: '每日一言',
    description: '每天一句励志、幽默或程序员哲学，给一天定个基调',
  },
})

const props = withDefaults(
  defineProps<{
    /** 语录分类：all / 励志 / 幽默 / 程序员 */
    category?: string
  }>(),
  { category: 'all' },
)

interface Quote {
  text: string
  author: string
  cat: '励志' | '幽默' | '程序员'
}

const QUOTES: Quote[] = [
  { text: '种一棵树最好的时间是十年前，其次是现在。', author: '谚语', cat: '励志' },
  { text: '慢慢来，比较快。', author: '佚名', cat: '励志' },
  { text: '所有伟大的事，都是由一连串的小事组成的。', author: '梵高', cat: '励志' },
  { text: '你不必很厉害才能开始，但你必须开始才会很厉害。', author: '佚名', cat: '励志' },
  { text: '把每一件简单的事做好，就是不简单。', author: '佚名', cat: '励志' },
  { text: '行动是治愈恐惧的良药。', author: '威廉·詹姆斯', cat: '励志' },
  { text: '真正的进步，是今天的你比昨天的你更好一点。', author: '佚名', cat: '励志' },
  { text: '休息不是浪费时间，是为了走更远的路。', author: '佚名', cat: '励志' },
  { text: '我不是懒，我是在节能模式。', author: '佚名', cat: '幽默' },
  { text: '计划赶不上变化，变化赶不上老板的一句话。', author: '打工人语录', cat: '幽默' },
  { text: '早起的鸟儿有虫吃，早起的虫儿被鸟吃。', author: '佚名', cat: '幽默' },
  { text: '失败乃成功之母，摸鱼乃工作之父。', author: '佚名', cat: '幽默' },
  { text: '只要我跑得够快，烦恼就追不上我。', author: '佚名', cat: '幽默' },
  { text: '天塌下来有个子高的顶着，需求来了有排期顶着。', author: '打工人语录', cat: '幽默' },
  { text: '任何一个傻瓜都能写出计算机可以理解的代码，好的程序员写的是人类能读懂的代码。', author: 'Martin Fowler', cat: '程序员' },
  { text: '过早的优化是万恶之源。', author: 'Donald Knuth', cat: '程序员' },
  { text: '简单是可靠的前提。', author: 'Edsger Dijkstra', cat: '程序员' },
  { text: '衡量进度的唯一标准是可工作的软件。', author: '敏捷宣言', cat: '程序员' },
  { text: '先让它跑起来，再让它跑对，最后让它跑快。', author: 'Kent Beck', cat: '程序员' },
  { text: '删除的代码是调试过的代码。', author: '佚名', cat: '程序员' },
  { text: '命名是计算机科学中最难的两件事之一。', author: 'Phil Karlton', cat: '程序员' },
  { text: '能用代码解释的，就不要写注释；能用测试证明的，就不要口头保证。', author: '佚名', cat: '程序员' },
]

const offset = ref(0)

const pool = computed(() => {
  const cat = props.category.trim()
  if (!cat || cat === 'all') return QUOTES
  const filtered = QUOTES.filter((q) => q.cat === cat)
  return filtered.length ? filtered : QUOTES
})

/** 按日期哈希稳定选取，换一句时叠加偏移 */
const quote = computed<Quote>(() => {
  const day = new Date().toISOString().slice(0, 10)
  let h = 0
  for (let i = 0; i < day.length; i++) h = (h * 31 + day.charCodeAt(i)) >>> 0
  const idx = (h + offset.value) % pool.value.length
  return pool.value[idx] as Quote
})

function next() {
  offset.value += 1
}

// ── 打字机逐字展开（rAF 驱动，一次性；切换语录时重放） ──
const shownText = ref('')
const typingDone = ref(false)
let typeRaf: number | null = null

function cancelType() {
  if (typeRaf !== null) {
    cancelAnimationFrame(typeRaf)
    typeRaf = null
  }
}

function typeQuote(full: string) {
  cancelType()
  shownText.value = ''
  typingDone.value = false
  let count = 0
  let last = 0
  const step = (now: number) => {
    if (now - last >= 26) {
      count += 1
      shownText.value = full.slice(0, count)
      last = now
    }
    if (count < full.length) {
      typeRaf = requestAnimationFrame(step)
    } else {
      typingDone.value = true
      typeRaf = null
    }
  }
  typeRaf = requestAnimationFrame(step)
}

watch(quote, (q) => typeQuote(q.text), { immediate: true })
onBeforeUnmount(cancelType)

async function copy() {
  try {
    await navigator.clipboard.writeText(`${quote.value.text} —— ${quote.value.author}`)
    message.success('已复制')
  } catch {
    message.warning('复制失败，请手动选择文本')
  }
}

defineExpose({
  panelActions: [
    { key: 'next', label: '换一句', icon: 'RedoOutlined', run: next },
    { key: 'copy', label: '复制', icon: 'CopyOutlined', run: copy },
  ],
})
</script>

<template>
  <div class="quote">
    <svg viewBox="0 0 24 24" class="qt-mark">
      <path
        d="M5 6h5v6H7c0 2 .8 3.4 3 4v2c-3.8-.6-5-3.4-5-6.6V6zm9 0h5v6h-3c0 2 .8 3.4 3 4v2c-3.8-.6-5-3.4-5-6.6V6z"
        fill="#e8eaed"
      />
    </svg>
    <div class="qt-text">
      {{ shownText }}<span v-if="!typingDone" class="qt-caret" />
    </div>
    <div class="qt-author" :class="{ show: typingDone }">—— {{ quote.author }}<i class="qt-cat">{{ quote.cat }}</i></div>
  </div>
</template>

<style scoped lang="scss">
.quote {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
  height: 100%;
}

.qt-mark {
  width: 28px;
  height: 28px;
  flex-shrink: 0;
}

.qt-text {
  min-height: 2.4em;
  font-size: 15px;
  color: #434343;
  line-height: 1.8;
  word-break: break-word;
}

/* 打字光标：仅打字期间存在，非循环闪烁 */
.qt-caret {
  display: inline-block;
  width: 2px;
  height: 1em;
  margin-left: 2px;
  vertical-align: -0.15em;
  background: #1677ff;
}

.qt-author {
  align-self: flex-end;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #999;
  opacity: 0;
  transition: opacity 0.45s ease;
}

.qt-author.show {
  opacity: 1;
}

.qt-cat {
  padding: 1px 8px;
  border-radius: 9px;
  background: #f5f6f8;
  font-size: 11px;
  font-style: normal;
  color: #8c8c8c;
}
</style>
