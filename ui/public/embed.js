/**
 * Apboa Next 智能体 · 网站嵌入悬浮气泡（chat bubble）
 *
 * 用法：把下面一行放到你网站 </body> 之前即可：
 *   <script src="https://你的域名/embed.js" data-chat-key="你的chatKey" defer></script>
 *
 * 可选属性：
 *   data-base-url   嵌入页基地址，默认从本脚本 src 自动推导（跨域 / CDN 场景建议显式指定）
 *   data-title      浮窗标题，默认"在线助手"
 *   data-color      主色（气泡 + 标题栏），默认 #0F74FF
 *   data-position   气泡位置 left | right，默认 right
 *   data-width      浮窗宽度 px，默认 400
 *   data-height     浮窗高度 px，默认 600
 *   data-user-jwt   嵌入用户凭证（业务方后端用 embedSecret 签的短命 JWT，
 *                   {sub:用户ID, name?:显示名, exp:建议5分钟}）。经 postMessage
 *                   一次性传给 iframe（不进 URL/浏览器历史），平台验签后按
 *                   业务方用户身份对话；不传 = 匿名访客
 *
 * 全局 API：
 *   window.apboaEmbed.reset()   业务方用户登出/切换账号时必须调用——
 *                               清除 iframe 内的平台会话，否则下一个用户
 *                               会继续用上一个用户的身份（串号）
 */
(function () {
  'use strict'

  if (window.__apboaEmbedLoaded) return // 防重复注入
  window.__apboaEmbedLoaded = true

  function escapeHtml(s) {
    return String(s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]
    })
  }

  // 定位自身脚本标签（currentScript 在 defer/async 下同样有效，querySelector 兜底）
  var script =
    document.currentScript ||
    (function () {
      var ss = document.querySelectorAll('script[data-chat-key]')
      return ss[ss.length - 1] || null
    })()
  if (!script) {
    console.error('[apboa-embed] 未找到脚本标签')
    return
  }

  var chatKey = script.getAttribute('data-chat-key')
  if (!chatKey) {
    console.error('[apboa-embed] 缺少必填属性 data-chat-key')
    return
  }

  // baseUrl 从脚本自身 src 推导，天然适配 dev(根路径) 与生产(/web/ 子路径)；可用 data-base-url 覆盖
  var baseUrl =
    script.getAttribute('data-base-url') || script.src.replace(/\/embed\.js(\?.*)?$/, '')
  var userJwt = script.getAttribute('data-user-jwt') || ''
  var title = script.getAttribute('data-title') || '在线助手'
  var color = script.getAttribute('data-color') || '#0F74FF'
  var position = script.getAttribute('data-position') === 'left' ? 'left' : 'right'
  var width = parseInt(script.getAttribute('data-width'), 10) || 400
  var height = parseInt(script.getAttribute('data-height'), 10) || 800

  var iframeSrc = baseUrl + '/#/communication/' + encodeURIComponent(chatKey) + '?embed=1'
  var side = position + ':20px'

  // Shadow DOM 隔离：宿主页面 CSS 进不来，本组件 CSS 也不外泄
  var host = document.createElement('div')
  host.setAttribute('data-apboa-embed', '')
  document.body.appendChild(host)
  var root = host.attachShadow({ mode: 'open' })

  root.innerHTML =
    '<style>' +
    '.fab{position:fixed;bottom:20px;' +
    side +
    ';width:56px;height:56px;border-radius:50%;background:' +
    color +
    ';box-shadow:0 6px 18px rgba(0,0,0,.24);cursor:pointer;border:none;z-index:2147483000;' +
    'display:flex;align-items:center;justify-content:center;transition:transform .2s ease,opacity .2s ease;}' +
    '.fab:hover{transform:scale(1.06);}' +
    '.fab svg{width:28px;height:28px;fill:#fff;}' +
    '.panel{position:fixed;bottom:20px;' +
    side +
    ';width:' +
    width +
    'px;height:' +
    height +
    'px;max-width:calc(100vw - 40px);max-height:calc(100vh - 40px);background:#fff;border-radius:16px;' +
    'box-shadow:0 12px 40px rgba(0,0,0,.28);z-index:2147483000;overflow:hidden;' +
    'display:none;flex-direction:column;opacity:0;transform:translateY(12px);' +
    'transition:opacity .22s ease,transform .22s ease;}' +
    '.panel.open{display:flex;opacity:1;transform:translateY(0);}' +
    '.head{flex:0 0 48px;height:48px;background:' +
    color +
    ';color:#fff;display:flex;align-items:center;justify-content:space-between;padding:0 8px 0 16px;' +
    'font:600 15px/1.2 system-ui,-apple-system,"PingFang SC","Microsoft YaHei",sans-serif;}' +
    '.close{width:32px;height:32px;border:none;background:transparent;color:#fff;cursor:pointer;' +
    'border-radius:8px;font-size:22px;line-height:1;display:flex;align-items:center;justify-content:center;}' +
    '.close:hover{background:rgba(255,255,255,.18);}' +
    '.frame{flex:1;width:100%;border:none;display:block;}' +
    '@media (max-width:480px){.panel{width:100vw;height:100vh;max-width:100vw;max-height:100vh;bottom:0;' +
    position +
    ':0;border-radius:0;}}' +
    '</style>' +
    '<div class="panel">' +
    '<div class="head"><span>' +
    escapeHtml(title) +
    '</span><button class="close" aria-label="关闭">×</button></div>' +
    '<iframe class="frame" title="' +
    escapeHtml(title) +
    '"></iframe>' +
    '</div>' +
    '<button class="fab" aria-label="' +
    escapeHtml(title) +
    '"><svg viewBox="0 0 24 24"><path d="M12 2C6.48 2 2 6.04 2 11c0 2.7 1.34 5.12 3.47 6.77L4.5 21.5l3.94-1.87C9.53 20.53 10.73 20.8 12 20.8c5.52 0 10-4.04 10-9.8S17.52 2 12 2z"/></svg></button>'

  var panel = root.querySelector('.panel')
  var iframe = root.querySelector('.frame')
  var fab = root.querySelector('.fab')
  var loaded = false

  function open() {
    if (!loaded) {
      iframe.src = iframeSrc // 懒加载：首次打开才载入 iframe，不打开不烧后端会话
      loaded = true
    }
    panel.classList.add('open')
    fab.style.opacity = '0'
    fab.style.pointerEvents = 'none'
  }
  function close() {
    panel.classList.remove('open')
    fab.style.opacity = '1'
    fab.style.pointerEvents = ''
  }

  fab.addEventListener('click', function () {
    panel.classList.contains('open') ? close() : open()
  })
  root.querySelector('.close').addEventListener('click', close)

  // ===== 嵌入用户身份传递（postMessage 握手，docs/identity-propagation-design.md §6.M7）=====
  // iframe 内 ChatWrapper 加载后广播 ready，这里回发 userJwt。
  // targetOrigin 固定为平台源：凭证只会发给自家 iframe，页面上其他 iframe 收不到
  var platformOrigin = (function () {
    try {
      return new URL(baseUrl, window.location.href).origin
    } catch (e) {
      return '*'
    }
  })()

  window.addEventListener('message', function (event) {
    if (!iframe.contentWindow || event.source !== iframe.contentWindow) return
    if (platformOrigin !== '*' && event.origin !== platformOrigin) return
    if (event.data && event.data.type === 'apboa-embed-ready' && userJwt) {
      iframe.contentWindow.postMessage(
        { type: 'apboa-embed-user-jwt', userJwt: userJwt },
        platformOrigin
      )
    }
  })

  // ===== 全局 API =====
  // reset()：业务方用户登出/切换账号时必须调用，清 iframe 内平台会话防串号
  window.apboaEmbed = {
    reset: function () {
      if (loaded && iframe.contentWindow) {
        iframe.contentWindow.postMessage({ type: 'apboa-embed-reset' }, platformOrigin)
      }
    }
  }
})()
