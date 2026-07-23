# Apboa 身份断言 · 业务方接入指南

> 读者：把系统通过 **MCP / 自定义工具**接入 Apboa 平台、或把 agent **嵌入自家网页**的业务方开发者。
> 平台侧设计文档见 `docs/identity-propagation-design.md`（本指南自包含，不读它也能接入）。

## 0. 一句话原理

平台替用户调你的系统时，会随请求附一张**平台私钥签名的短命 JWT（身份断言，介绍信）**："本次调用由认证用户 X（租户 T）发起，仅限贵司使用，有效期 5 分钟"。你用平台公钥（JWKS）**验章**，然后**自己决定**该用户能干什么——权限表在你手里，平台零参与。

如果你的用户是通过**网页嵌入**访问 agent 的，你的后端可以给自己的登录用户签一张"小条"（userJwt），平台核验后在断言中如实转述（`external_sub`）——你在 MCP 侧拿到的就是**你自己系统的用户 ID**。

---

## 1. 接收端：验证平台的身份断言（所有接入方必读）

### 1.1 断言长什么样、从哪取

**MCP server**：`tools/call` 请求的 `_meta` 里，key 为 `apboa.identityAssertion`，值是 JWT 字符串。
**自定义工具（Groovy 脚本发起的 HTTP 调用）**：按你与脚本约定的请求头（推荐 `Authorization: Bearer <JWT>`）。

payload 示例：

```json
{
  "iss": "apboa-platform",
  "sub": "1024",
  "tenant_id": "7",
  "tenant_role": "TENANT_EDITOR",
  "agent_id": "2071495895812374530",
  "thread_id": "th_abc123",
  "tool_name": "query_order",
  "aud": ["mcp:order-system"],
  "external_iss": "ck-xxx",
  "external_sub": "42",
  "external_name": "张三",
  "iat": 1752110000,
  "exp": 1752110300,
  "jti": "uuid-xxxx"
}
```

### 1.2 验签五步（缺一不可）

1. **拉公钥**：启动时 GET `https://<平台>/.well-known/jwks.json`，按断言 header 的 `kid` 匹配公钥。缓存并**每日刷新**（平台轮换密钥时双 kid 并存，刷新即可无缝过渡）。
2. **验签名 + 过期**：标准 JWT 库（jose/jjwt/pyjwt...），RS256。**时钟容差设 30~60 秒**（断言只活 5 分钟，时钟偏移会误杀）。
3. **验 `iss`** = `apboa-platform`。
4. **必须验 `aud`** 是你自己（在平台 MCP 配置里填的 audience）。否则别的系统拿到发给它的断言可以重放到你这里。
5. **嵌入场景必须验 `external_iss`** 是你自己发的 chatKey。`external_sub` **仅在 `external_iss` 命名空间内有意义**——业务方 B 的"用户 42"不是你的"用户 42"，只看 `external_sub` 不看出处会被替身攻击。

Node 验签示例（jose，~15 行）：

```js
import { createRemoteJWKSet, jwtVerify } from 'jose'

const JWKS = createRemoteJWKSet(new URL('https://<平台>/.well-known/jwks.json'))

async function verifyAssertion(jwt) {
  const { payload } = await jwtVerify(jwt, JWKS, {
    issuer: 'apboa-platform',
    audience: 'mcp:order-system',   // ← 换成你的 audience
    clockTolerance: 60,
  })
  if (payload.external_sub && payload.external_iss !== 'ck-xxx') {  // ← 你的 chatKey
    throw new Error('external identity from unknown issuer')
  }
  return payload
}
```

### 1.3 按三档身份做权限

| 断言特征 | 这是谁 | 建议策略 |
|---|---|---|
| 有 `external_sub` + `external_iss`=你的 chatKey | 你自己系统的登录用户 | 查你的权限表，按用户放行 |
| 无 `external_sub`，`sub` 为平台正式用户 | 平台侧登录用户 | 按你与平台约定的映射（映射表你维护） |
| 无 `external_sub`（嵌入匿名访客；`sub` 为会话级随机 id） | 匿名 | 只放公开能力（查天气可以，查订单免谈） |

**没带断言的请求**（未配置 audience 的 MCP、或非平台流量）怎么处理由你定：拒绝、或只开公开能力。

---

## 2. 嵌入端：让平台知道"这是我家用户 42"（网页嵌入方选读）

### 2.1 三步接入

**① 拿 embedSecret**：平台管理后台 → 该 agent 的 chatKey 管理 → 生成嵌入身份密钥（接口：`POST /agent/chat-key/{agentId}/embed-secret/rotate`，需平台登录态）。密钥**只放你的后端**，绝不进前端代码。

**② 后端签 userJwt**（用户每次打开嵌入页时现签，HMAC-SHA256，有效期建议 5 分钟——它只用于换会话那一下）：

```js
// Node（jsonwebtoken）
const jwt = require('jsonwebtoken')
const userJwt = jwt.sign(
  { sub: String(user.id), name: user.displayName },
  EMBED_SECRET,
  { algorithm: 'HS256', expiresIn: '5m' }
)
```

```java
// Java（jjwt）
String userJwt = Jwts.builder()
        .subject(String.valueOf(user.getId()))
        .claim("name", user.getDisplayName())
        .expiration(new Date(System.currentTimeMillis() + 300_000))
        .signWith(Keys.hmacShaKeyFor(embedSecret.getBytes(StandardCharsets.UTF_8)))
        .compact();
```

**③ 页面嵌入时带上**：

```html
<script src="https://<平台>/embed.js"
        data-chat-key="ck-xxx"
        data-user-jwt="<服务端渲染注入的 userJwt>"
        defer></script>
```

userJwt 经 postMessage 传入 iframe（不进 URL、不进浏览器历史）。平台验签通过后，该访客的所有工具调用断言都会带 `external_sub`。**验签失败会直接拒绝会话**（不会静默降级成匿名），所以签错了你会立刻发现。

### 2.2 ⚠️ 登出/换账号必须调 reset()

会话身份在换 token 时烙死。**你的用户登出或切换账号时必须调用**：

```js
window.apboaEmbed.reset()
```

否则下一个用户会继续以上一个用户的身份对话（串号）。同一浏览器内用户变化时（userJwt 的 sub 变了）嵌入页会自动重新换身份兜底，但**主动 reset 是你的责任**——自动兜底只在重新加载嵌入页时生效。

### 2.3 密钥安全

- embedSecret 泄漏 = 攻击者可伪造**你家任意用户**的身份（影响半径只有你自己，断言里 `external_iss` 标注了出处）。发现泄漏立即在平台轮换：新密钥即时生效，旧密钥转入观察期（新旧双活，改完你后端的签发代码后旧的自然失效）。
- 平台对 `external_sub` 的背书语义是"**这话确实是你说的**"——外部用户 ID 的真伪由你自己负责（本来也只有你能验证你的用户）。

---

## 3. 常见问题

**Q: 断言验签偶发过期失败？** 服务器时钟偏移。验签容差设 30~60 秒；再查 NTP。

**Q: 平台换了签名密钥我要做什么？** 什么都不用。JWKS 双 kid 并存 + 你的每日刷新缓存自动过渡。

**Q: 要防重放吗？** 断言只活 5 分钟且 `aud`/`tool_name` 限定了用途，内网场景一般不用。极高安全要求可记录 `jti` 做 5 分钟窗口去重。

**Q: 我的 MCP 同时接了别家平台？** 按 `iss` 区分发行方、各自验各自的 JWKS 即可；Apboa 的断言 `iss` 恒为 `apboa-platform`（平台可配置，以对接约定为准）。
