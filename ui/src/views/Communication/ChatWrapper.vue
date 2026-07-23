<script lang="ts">
import { defineComponent, ref, computed, h } from 'vue'
import { useRoute } from 'vue-router'
import { useAccountStore } from '@/stores'
import { chatKeyToken } from '@/api/auth.ts'
import { getAgentIdByChatKey } from '@/api/agentChatKey.ts'
import Chat from '@/views/Chat/index.vue'

/** 换人检测用：记录上次换 token 时的业务方用户标识（userJwt.sub） */
const EMBED_SUB_STORAGE_KEY = 'apboa-embed-external-sub'

/**
 * 解 JWT payload 取 sub（不验签——真伪由后端换 token 时用 embedSecret 验，
 * 这里只做"同一浏览器换人了没"的比对）
 */
function parseJwtSub(jwt: string): string | null {
  try {
    const part = jwt.split('.')[1]
    if (!part) return null
    const payload = JSON.parse(atob(part.replace(/-/g, '+').replace(/_/g, '/')))
    return payload.sub ?? null
  } catch {
    return null
  }
}

/**
 * postMessage 握手（docs/identity-propagation-design.md §6.M7）：被嵌入时向父窗口
 * 广播 ready，等 embed.js 回发 userJwt。凭证经窗口消息一次性传递，不进 URL/
 * 浏览器历史。iframe 侧无法预知业务方页面的 origin（任意接入方），故来源校验
 * 降级为 event.source === window.parent；父页→iframe 方向由 embed.js 用
 * targetOrigin=平台源保证凭证不会发给别人。
 *
 * @returns userJwt；独立打开/老版 embed.js（不会回消息）时超时返回 null
 */
function requestUserJwtFromParent(timeoutMs = 800): Promise<string | null> {
  if (window.parent === window) {
    return Promise.resolve(null)
  }
  return new Promise((resolve) => {
    const timer = window.setTimeout(() => {
      window.removeEventListener('message', onMessage)
      resolve(null)
    }, timeoutMs)

    function onMessage(event: MessageEvent) {
      if (event.source !== window.parent) return
      const data = event.data
      if (data && data.type === 'apboa-embed-user-jwt') {
        window.clearTimeout(timer)
        window.removeEventListener('message', onMessage)
        resolve(typeof data.userJwt === 'string' && data.userJwt ? data.userJwt : null)
      }
    }

    window.addEventListener('message', onMessage)
    // ready 不含敏感数据，'*' 广播（业务方 origin 未知）
    window.parent.postMessage({ type: 'apboa-embed-ready' }, '*')
  })
}

/**
 * 通道二：整页打开外置链接时从 URL query 读 userJwt。
 *
 * postMessage 通道依赖父窗口，整页打开（window.parent === window）用不了；此时业务方
 * 把 userJwt 拼在链接上：/#/communication/{chatKey}?userJwt=xxx。读到后**立刻**用
 * history.replaceState 从地址栏抹除，避免这张 5 分钟凭证随浏览器历史 / 被复制的整条
 * 链接泄漏（业务方拍板：5 分钟过期 + 换 token 后一次性失效 + 全程 https，风险可接受）。
 *
 * hash 路由下 userJwt 落在 '#/communication/xxx?userJwt=...' 的 query 段，直接改 history
 * 不惊动 vue-router（不派发 popstate）；route.query 此前已读取，抹除不影响换 token。
 *
 * 与 postMessage 互补，业务方二选一：iframe 悬浮嵌入走 postMessage（安全，但跨源砍
 * ASR/TTS）；整页打开走本通道（保留语音）。
 */
function scrubAndReadUrlUserJwt(): string | null {
  const hash = window.location.hash
  const qIndex = hash.indexOf('?')
  if (qIndex === -1) return null
  const params = new URLSearchParams(hash.slice(qIndex + 1))
  const userJwt = params.get('userJwt')
  if (!userJwt) return null
  params.delete('userJwt')
  const rest = params.toString()
  const newHash = hash.slice(0, qIndex) + (rest ? '?' + rest : '')
  history.replaceState(history.state, '', window.location.pathname + window.location.search + newHash)
  return userJwt
}

export default defineComponent({
  name: 'ChatWrapper',
  emits: ['error'],
  async setup(props, { emit }) {
    const chatAgentId = ref<string>()
    const route = useRoute()
    const chatKey = computed(() => (route.params.chatKey as string) || '')
    const accountStore = useAccountStore()
    accountStore.initStore()

    // 业务方登出/换账号时由 embed.js reset() 触发：清本地会话并重载
    window.addEventListener('message', (event: MessageEvent) => {
      if (event.source !== window.parent) return
      if (event.data && event.data.type === 'apboa-embed-reset') {
        accountStore.clearUserData()
        localStorage.removeItem(EMBED_SUB_STORAGE_KEY)
        window.location.reload()
      }
    })

    try {
      // userJwt 两条通道，业务方按需二选一（互补，互不冲突）：
      //   ① iframe 悬浮嵌入 → embed.js 经 postMessage 传（安全，但跨源砍 ASR/TTS）
      //   ② 整页打开外置链接 → URL query 传（保留语音；读后抹除 + 5 分钟过期 + https 兜底）
      // postMessage 优先：整页打开时它立刻返回 null（无父窗口），自动回落到 URL 通道。
      const userJwt = (await requestUserJwtFromParent()) ?? scrubAndReadUrlUserJwt()

      // 换人检测（设计文档 §7 坑 1）：同一浏览器业务方用户变了，旧 token 里
      // external_sub 是烙死的，必须废弃重换，否则以前一个用户身份调工具（串号）
      const incomingSub = userJwt ? parseJwtSub(userJwt) : null
      const lastSub = localStorage.getItem(EMBED_SUB_STORAGE_KEY)
      if (accountStore.isLoggedIn && incomingSub && incomingSub !== lastSub) {
        accountStore.clearUserData()
      }

      if (!accountStore.isLoggedIn) {
        const response = await chatKeyToken(chatKey.value, userJwt ?? undefined)
        const data = response.data.data
        if (!data) {
          window.location.href = '/#/login';
          return
        }
        accountStore.setAccessInfo(data)
        const userDetail = data.userDetail
        accountStore.setUserInfo({
          id: userDetail.id,
          nickname: userDetail.name,
          email: userDetail.email,
          username: userDetail.username,
          enabled: true
        })
        if (incomingSub) {
          localStorage.setItem(EMBED_SUB_STORAGE_KEY, incomingSub)
        } else {
          localStorage.removeItem(EMBED_SUB_STORAGE_KEY)
        }
      }

      const res = await getAgentIdByChatKey(chatKey.value)
      chatAgentId.value = res.data.data
    } catch (error) {
      // 使用 h 函数返回虚拟节点
      console.error('初始化失败:', error)
      emit('error', error)
    }

    // 使用 h 函数返回虚拟节点
    return () => h(Chat, {
      chatAgentId: chatAgentId.value,
      showAccount: false
    })
  }
})
</script>
