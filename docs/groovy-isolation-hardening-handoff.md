# Apboa 安全审查与 Groovy 执行隔离加固 —— 交接文档

> **给接手的 Claude**：本文件自包含，不依赖任何对话上下文。读完你应能理解：这套系统的架构与隔离现状、已经修掉的问题、当前唯一的重大遗留缺口（Groovy 动态工具/hook 无隔离），以及已论证过的加固方向。你的任务在文末「下一步」一节。
>
> - **分支**：`psh`（这是一个开源 fork，用户在此分支本地修原作者来不及处理的 bug；改动按小节 commit）。
> - **构建**：命令行 `mvn` 必须用 JDK 21，否则 Lombok 1.18.30 在 JDK 26 上静默失效、全项目编译失败。
>   `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn -f <根pom绝对路径> -pl <模块> [-am] compile -o -q`
> - **前端类型检查**：`cd ui && npx vue-tsc --build`。
>
> **【v2 复核标记，2026-07-05】** 本文档已经第二轮独立复核（回到源码 + `docker inspect`/`docker top` 实测运行中容器）。核心论断全部属实；已就若干措辞做校正（文中以"⚠️ 校正"标注）。最重要一处：§5 原写的"无容器隔离"应理解为"无**进程内 JVM 级**沙箱，但运行在有**基础隔离**的 runtime 容器内"——详见 §3.1 实测与 §5。

---

## 1. 来龙去脉（这条线怎么走到这的）

1. 从「智能体配置 → API 文档篇」（前端硬编码的接口清单）审计前后端一致性开始。
2. 发现文档漂移（漏接口、JSON 示例 bug），补齐 → **2 个 docs commit**。
3. 对抗性审查（多 agent）反查遗漏，顺带发现 **`doTool` 端点过度暴露**。
4. 确认 `doTool` 是真实漏洞（免登可越权执行工具、潜在 RCE），修复 → **2 个 fix commit**。
5. 深挖 `doTool` 暴露出的根因——**动态 Groovy 工具/hook 在 runtime 进程内无沙箱裸跑**，且绕过全部准入安全网。
6. 调研系统既有隔离架构（runner-proxy / runner-runtime），确认外移隔离范式。
7. **← 你在这里**：出 Groovy 执行加固的方案设计 / 落地。

已落地的 4 个 commit（psh 分支，`git log` 可见）：
- `3e02c76` docs：接口文档对齐后端（补 13 漏接口 / 修 JSON 示例 / 加维护注释）
- `c480969` docs：补 chatKey 白名单接口（chatKey / 语音 / 子 HITL / 附件下载删除）
- `fbf5cb5` fix：**doTool 收紧鉴权**（`@SkAccess/@ChatKeyAccess` → `@RoleNeed`）
- `c2343fb` fix：**doTool 补 enabled 校验**

---

## 2. 系统架构速览

### 2.1 模块

Maven 多模块（`groupId=com.hxh.apboa.next`）：
- `runner-console`（:3060 管理台 API）、`runner-runtime`（:3061 AI 运行时）、`runner-proxy`（:3062 shell 执行代理）、`runner-file`（文件同步，web disabled）、`runner-websocket`（:3064）
- `engine`（agent/工具/hook/workspace 核心，**只被 scheduler→runtime 依赖，即 engine 代码在 runtime 进程跑**）
- `biz/biz-*`（account/agent/tool/hook/model/skill… 业务）、`common` / `common-base`（实体/工具/鉴权）

### 2.2 部署形态

- **管理节点**：console（+ mysql/redis/pg 等）。
- **执行节点**（可多实例，`docker/docker-compose-execute.yml`）：`apboa-runtime` + `apboa-proxy` + `apboa-file` 三容器一组，同挂一份 `.apboa` 卷、同在 `apboa_execute` 内网、同一 `APBOA_NODE_ID`。
- 简版全家桶：`docker/docker-compose-simple.yml`（localhost:80 是容器全家桶；测宿主新代码要直连 3060/3061）。

```
   apboa-runtime :3061  ──HTTP──►  apboa-proxy :3062        apboa-file
   (跑 agent / 写 workspace)        (隔离执行 shell)         (只同步 skill 文件)
        │                              │                        │
        └──────── 共享卷 /app/.apboa ──┴────────────────────────┘
   仅 runtime 暴露 3061；proxy / file 无 ports 映射（纯内网，只 runtime 能调 proxy）
```

### 2.3 对外鉴权体系（`common/.../config/auth/AuthInterceptor.java`）

`preHandle` 三分支：无 token→401；`sk-` 前缀→`handleSkToken`（API Key）；常规 JWT→`handleJwtToken`（登录态 / chatKey）。两个方法/类级注解是**对外白名单**：

- **`@SkAccess`**：允许 API Key（`sk-`）访问。解压还原 JWT、校验内存 `SK_ID_SET`、UserDetail 来自创建 SK 时绑定的用户。**SK 分支从不调 `checkRoleNeed`（不校验角色）**。
- **`@ChatKeyAccess`**：chatKey 免登。chatKey 是「外置对话分享链接 `/#/communication/{chatKey}`」的底层机制：匿名 `POST /api/account/chat-key-token/{chatKey}`（`@PassAuth`）换取**永不过期** JWT，UserDetail 绑定 **agent 主人租户**的合成身份（`tenantId`=主人租户、`tenantRole` 为空），后续请求识别为 chatKey token 后只放行带 `@ChatKeyAccess` 的接口。
- **安全语义**：chatKey 持有者 = 以 agent 主人身份、免登、被限制在 `@ChatKeyAccess` 白名单内。所有写/危险操作（删账号/改密码/建删改 agent）均用 `@RoleNeed` 锁登录态角色，chatKey/SK 碰不到（已核 `AccountController`/`AgentDefinitionController` 仅只读方法带注解、无类级注解）。
- **多租户**：`tool_config` 等业务表受 MyBatis-Plus `TenantLineInnerInterceptor` 约束（自动 `tenant_id=当前租户`），`IGNORE_TABLES` 仅 `tenant/account/account_tenant/agentscope_sessions/skill_token/agent_chat_key`（`common/.../config/mybatis/MybatisPlusConfig.java`）。
- 后端 `@RequestMapping` **不含 `/api`**，`/api` 由网关统一加。

---

## 3. 现有隔离架构（重点，原作者设计得相当成熟）

### 3.1 容器隔离对比：proxy 明显更强

| 隔离项 | apboa-runtime | **apboa-proxy** | 证据 |
|---|---|---|---|
| cap_drop ALL + no-new-privileges + 非root（gosu 降权） | ✅ | ✅ | `docker-compose-execute.yml:18-25 / 79-86` |
| **read_only 根文件系统** | ❌ | ✅ | `:87` |
| **tmpfs /tmp:exec（唯一可写、销毁即失）** | ❌ | ✅ | `:88-90` |
| **pids_limit（防 fork bomb）** | ❌ | ✅ 200 | `:93` |
| mem / cpu | 8g / 8 | 1g / 2 | `:26-27 / 91-92` |
| 装 Python3 + Node.js 22 | ❌ | ✅ | `docker/proxy/Dockerfile:69-83` |
| 运行用户 | 非root `apboa`(1001) | 非root `shellproxy`(1002) | 两个 Dockerfile |

`apboa-proxy` 是刻意打造的**一次性强隔离沙箱**（Dockerfile 注释：为"阻断容器→宿主机逃逸"），且自带 Python/Node → 本就是多语言代码执行器。生产 runtime 默认 `SHELL_PROXY_ENABLED=true`。

**运行时实测（2026-07-05，`docker inspect`/`docker top` 于 simple 全家桶实例）——证明 runtime 也有"基础隔离"，只是比 proxy 弱一档，并非"裸奔"：**

| 项 | apboa-runtime（实测） | apboa-proxy（实测） |
|---|---|---|
| CapDrop / CapAdd | `ALL` / CHOWN·SETGID·SETUID（gosu 用） | 同 |
| no-new-privileges | ✅ | ✅ |
| java 进程实际用户（`docker top`） | **uid 1001 apboa**（非 root） | **uid 1002 shellproxy**（非 root） |
| ReadonlyRootfs | ❌ `false` | ✅ `true` |
| tmpfs | ❌ 无 | ✅ `/tmp:exec` |
| pids_limit | ❌ **无（fork bomb 无防护）** | ✅ `200` |
| Memory / Cpu | 4G / 4 | 1G / 2 |
| 对外端口 | **无映射**（只 frontend nginx 反代） | 无映射 |
| 容器内解释器 | 有 sh/bash，**无 python/node** | 装 python3/node22 |
| `/app/.apboa` 共享卷 | 与 proxy/file 同挂，**rw** | 同 |

结论：Groovy 逃逸边界 = runtime 容器的**基础隔离**（cap_drop ALL + no-new-privileges + 非 root + 无对外端口），不是宿主机、也不是 proxy 强隔离沙箱。这直接决定了加固方案的目标——把 Groovy 从 runtime 基础隔离**降到 proxy 强隔离**（补 read_only/tmpfs/pids + 断 bean 注入）。

### 3.2 危险执行的隔离分工

| 执行类型 | 现状 | 隔离 |
|---|---|---|
| **Shell 命令** | 生产外移 proxy 容器；runtime 侧叠加命令白名单 / `ShellValidator` / AST 扫描 / HITL 确认 | 🟢 强（容器 + 准入双层） |
| **workspace 文件** | 每租户每会话独立目录 + 双层路径穿越防护 + 容量限流 30MB | 🟢 有护栏 |
| 内置文件读写工具 | runtime 进程内 nio，受 `WorkspaceHook`/`PathValidator` 约束 | 🟡 应用层护栏 |
| **动态 Groovy 工具 / Groovy hook** | runtime 进程内 `groovyClassLoader.parseClass`（复用成员字段）**裸跑** | 🟠 **仅 runtime 容器基础隔离**（无进程内沙箱 + 绕路径/容量/AST 准入；HITL 对 agent 内调用仍生效） |

### 3.3 shell 外移链路（外移范式，Groovy 可直接套）

`ShellCommandTool.executeCommand`（`runner-runtime/.../coding/ShellCommandTool.java:566-577`）：
- `if (proxyEnabled)` → `ShellProxyClient.execute(...)` HTTP `POST {proxyBaseUrl}/api/proxy/execute/shell`（`ShellProxyClient.java:53-83`）。
- 开关由 `ShellProxyConfig`（`runner-runtime/.../shellproxy/ShellProxyConfig.java`）读 `shell-proxy.enabled` 注入静态字段。
- proxy 侧 `ShellController`（`/api/proxy/execute/shell`）→ `ShellService`（校验命令非空/目录存在/超时裁剪，**无鉴权、无命令白名单**——proxy 是"哑执行器"，信任 runtime，安全靠容器隔离 + 内网不可达）→ `ShellExecutor`（`ProcessBuilder("sh","-c")` + 双虚拟线程读流防死锁 + 超时 `waitFor` + 输出上限 50MB 溢出即杀 + 进程强制清理）。

### 3.4 runtime 侧准入安全网（原作者的"入口层"防护）

- `WorkspaceHook` 在 `PreActingEvent` 对工具做准入校验：`PathValidator`（禁绝对路径/`..`）、`ShellValidator`（危险命令模式）、`CapacityValidator`（容量）、以及 **`ScriptSecurityService`**（对 Python/Node/Shell/Html 内联代码做 **AST 语义扫描**）。⚠️ 校正：AST 内联扫描只在 `execute_shell_command` 分支触发、只认 python/node/bash/sh/zsh（perl/ruby/php 跳过）；对 Groovy 是 **fail-open**（`ScriptType` 无 Groovy 分支，识别不出 → 静默判"安全"、放行）。
- **关键限制**：这套安全网**只对固定工具名白名单 `ToolConstants.PATH_SENSITIVE_TOOLS` 生效**——实为 6 个固定内置名（只读 `view_text_file`/`list_directory`；写 `insert_text_file`/`write_text_file`/`search_replace_file`/`execute_shell_command`）。`WorkspaceHook` 第 72 行 `if (PATH_SENSITIVE_TOOLS.contains(toolName))`，不在名单内 → 整个 if 块跳过、原样放行、零校验。
- HITL 人工确认走 `IConfirmationHook`（按 `toolConfig.getNeedConfirm()` 注册，`ToolkitFactory.java`）。⚠️ 校正：HITL 是**独立于上述白名单**的一条链路（`PostReasoningEvent` 按工具名匹配 `NEED_CONFIRM_TOOLS`），**对 agent 对话内调用的动态工具是生效的**——动态工具可配 `needConfirm=true` 并正常触发确认暂停。只有 `doTool` 这条 HTTP 直调路径不进 agentscope Hook 链，才连 HITL 一起不生效（psh 已加 `@RoleNeed` 收口）。

### 3.5 workspace 隔离

- 目录：`.apboa/tenants/{tenantCode}/workspaces/{sessionId/threadId}`（根常量 `common-base/.../SysConst.java`，容器内 `/app/.apboa` 共享卷）。
- 租户从 `TenantUtils`（ThreadLocal，`AuthInterceptor` 注入）取 → 物理目录按 tenantCode 分叉。
- 双层路径穿越防护：HTTP 层 `WorkspaceServiceImpl`（`validateSessionId` 禁 `../\`、`resolve().normalize()`+`startsWith`、Zip Slip 防护）+ agent 工具层 `WorkspaceHook`→`PathValidator`。
- session/tenant 间**真隔离，有完整穿越防护**。

### 3.6 CodeExecutionConfig 注入链路

- `AgentCodeExecution` 表绑定 `agentDefinitionId→codeExecutionId`；`CodeExecutionConfig`（`common/.../entity/CodeExecutionConfig.java`）字段：`workDir`、命令白名单 `command`(JsonNode)、`enableShell/enableRead/enableWrite`。
- `ReActAgentHelper` 查配置 → `SkillBoxFactory.configureCodeExecution()` 组装工具：`enableShell` → `new ShellCommandTool(null, allowedCommands, null)`（**全项目唯一真实构建 ShellCommandTool 处**）；`enableRead/Write` → 注册文件工具。

### 3.7 多节点 / file 服务

- `APBOA_NODE_ID`：物理执行节点标识（默认取宿主机 IP，`start-execute.sh`）。用于心跳注册（console 侧 NodeRegistry）、会话路由、跨节点 Redis 消息去重。同一节点三服务必须同 NODE_ID（共享同一 `.apboa` 卷）。
- `runner-file`：web disabled，只**同步 skill 技能文件**（不碰 workspace）——全量同步 + Redis 订阅增量 + 从 console 下载 zip 到 `.apboa/tenants/{code}/skills/`。

---

## 4. 已修复的问题（背景，供你理解 doTool 与 Groovy 同源）

### 4.1 接口文档漂移（已修）
前端 `ui/src/components/agent/config/AgentConfigApiDoc.vue` 是**前端手写硬编码**的接口清单（`endpoints`/`workspaceEndpoints`/`aguiEndpoints`/`voiceEndpoints` 四数组 + 区块），后端不提供文档数据源。已补齐并加维护注释（含"哪些 Controller 对应哪个数组、为何不收 doTool/元数据"）。

### 4.2 doTool 过度暴露（已修，与 Groovy 缺口同源）
`POST /api/runtime/agent/do/{toolName}/tool`（`runner-runtime/.../endpoint/ToolEndPoint.java`）原带 `@SkAccess+@ChatKeyAccess`，使**匿名 chatKey / SK 持有者可绕过对话、按名直接执行本租户任意工具**——无 agent 归属校验、无 HITL、无 enabled 校验；CUSTOM 工具走无沙箱 Groovy → 未授权 RCE（危害限 agent 主人租户，租户拦截器兜底）。
- 判定为遗留疏漏（与 debugTool 的 `@RoleNeed`+enabled 校验+"不对 SK/ChatKey 开放"注释刺眼反差；前端 `agentDoTool` 函数已定义 `ui/src/api/agent.ts:96` 但**零调用点**——死代码，故收紧无功能损失）。
- 已改 `@RoleNeed(TENANT_ADMIN/TENANT_EDITOR)` + 补 enabled 校验（`fbf5cb5`/`c2343fb`）。

---

## 5. 🔴 待解决的核心缺口：Groovy 动态工具/hook「双重逃逸」

**动态工具/hook 的 code 是完整 Groovy 类**（工具须 `implements IDynamicAgentTool`、钩子须 `implements Hook`），存 `tool_config.code`/`hook_config.code`，`language=JAVA` 对应 Groovy（`CodeLanguage` 仅 JAVA/JAVASCRIPT，只有 Groovy loader 注册）。

执行链路（`engine` 模块，**在 runtime 进程内**）：
`GroovyToolInstanceLoader.loadInstance` → `groovyClassLoader.parseClass(code)`（⚠️ 校正：**复用成员字段** `GroovyClassLoader`，非每次 new；按 code 的 MD5 缓存实例 `TOOL_OBJ_CACHE`）→ `InstanceLoader.getObject`（反射无参构造）→ `dependencyInjection`（按 `@Resource/@Autowired` **注入任意 Spring bean**，`BeanUtils.getBean` 无白名单）→ `DynamicAgentTool.callAsync` 里 `execute(ctx, params)`（有 try-catch，**无超时**）。Hook 侧 `GroovyHookInstanceLoader` 对称。注意：`doTool`/`debugTool` 端点走的是 `ToolEndPoint.executeTool` **同步直调** `execute()`（不经 `callAsync` 的 Mono），加超时时两条路径都要覆盖。

**为什么叫"双重逃逸"**（⚠️ 措辞已按第二轮复核校正）：
1. **无进程内 JVM 级沙箱** —— `GroovyClassLoader` 在 runtime 主进程内裸跑，可 `Runtime.exec`/读写文件/JDBC/**注入任意 Spring bean**，等价 runtime 进程权限内任意代码（Groovy 4.0.14，无 `SecureASTCustomizer`/`SecurityManager`）。**但不是"无容器隔离"**：Groovy 只在 runtime 进程执行（依赖链 `runner-runtime → scheduler → engine`，console/proxy 不含 engine/groovy），逃逸边界 = **runtime 容器的基础隔离**（§3.1 实测）。准确说：**能在 runtime 容器内为所欲为**（读写 rw 共享卷 `/app/.apboa`、无 pids_limit 可 fork bomb、直连无鉴权的 proxy:3062 执行 shell、拿 `DataSource` 绕租户拦截器裸查全库），**但打穿到宿主机需内核漏洞——比 shell 走的 proxy 强隔离低一档**。
2. **绕过 runtime 侧准入安全网** —— 动态工具名 = `toolConfig.getToolId()`（用户建工具时可控的任意值，`ToolController.save` 无保留字/唯一性校验），**不在 `ToolConstants.PATH_SENSITIVE_TOOLS`（6 个固定内置名）里**，因此 `WorkspaceHook` + `ScriptSecurityService`（AST 扫描）+ 路径/容量校验全部不生效。⚠️ 校正：**HITL 不在此列**——见 §3.4，HITL 对 agent 内调用的动态工具仍生效，只有 `doTool` HTTP 直调不进 Hook 链才连 HITL 一起失效。

这是原作者整套安全体系里**唯一的漏网之鱼**：shell 有全套防护（容器隔离 + 白名单 + AST 扫描 + HITL），Groovy 一层都没有。

**风险定级 + 运营模式（v3 决策，2026-07-05）**：
- 创建/编辑动态工具、hook 需登录态**租户角色 ≥ EDITOR**（`checkRoleNeed` 是分级鉴权：userLevel ≥ 门槛即过，`AuthInterceptor`；`ToolController.save` 卡 `{ADMIN,EDITOR}`）。doTool 匿名入口已修。
- **实际运营模式**（用户确认）：客户账号 = **自己租户全权限 + 后台全开**（客户即自己租户的 admin/owner）；tool/hook/agent 由**运营方帮建**成成品交付，客户通常无研发能力。⚠️ 但**能力 ≠ 权限**：客户账号权限上就能建 Groovy（前端藏按钮也拦不住直接调 API），且账号可能被盗。
- **无平台运营方超管层**（`Account` 实体无超管字段，权限全是租户内 `TenantRole`：OWNER>ADMIN>EDITOR>VIEWER）→ 运营方与客户在同一租户同权限，**无法用角色把"建 Groovy"锁给运营方** → 多租户下"信任分级"这条轻量路走不通，只能靠外移沙箱。
- **单租户/自部署（用户理想形态）**：客户全权限也只炸自己那套 → 无沙箱 ≈ 可接受信任假设（类似 Jenkins/n8n）→ **不用做**。
- **多租户 SaaS（要兼容）**：客户能建 Groovy + 无沙箱 → `@Autowired DataSource` 绕租户拦截器裸查全库 → 跨客户数据泄露 → **真实高危，必须做**。
- 🔲 **当前决策：暂缓实施。上多租户 SaaS 前不做加固；`/register` 注册也暂不关**（用户知情接受现状）。触发条件与届时任务见 §7 及 `groovy-isolation-hardening-design.md` 顶部。

---

## 6. 加固方案（已论证的方向）

### 6.1 已排除：进程内 groovy-sandbox
`org.kohsuke:groovy-sandbox` 对 Groovy 4 兼容性未证实（历史跟 Groovy 2.x）；即便能用，进程内隔离弱（bean 注入 + 元编程 `"cmd".execute()` / 反射逃逸史 + CVE），且不符合本项目"外移隔离"范式。**不采用。**（纯 `SecureASTCustomizer` 是编译期 AST 白名单，拦不住运行时元编程/注入 bean 调用，只能抬门槛不能密封。）

### 6.2 推荐：两层防御（对齐原作者对 shell 的双层设计）
- **入口层（runtime）**：把动态工具/hook 的 `code` 纳入 `ScriptSecurityService` 扫描，并把动态工具名纳入 `PATH_SENSITIVE_TOOLS` 机制，让它经过 `WorkspaceHook`/HITL 准入 —— **复用现成安全网，堵住"绕过"**。
- **执行层（proxy）**：proxy 加 `POST /api/proxy/execute/groovy`（或新建 groovy 执行器容器），Groovy 编译执行移进强隔离沙箱（read_only/tmpfs/pids/非root/cap_drop）—— **复用 shell 外移范式（`ShellCommandTool→ShellProxyClient→proxy`），堵住"进程内裸跑"**。

### 6.3 唯一硬 tradeoff：bean 注入
外移到独立进程后脚本拿不到 runtime 的 Spring bean。**现网无任何预置动态工具**（`db_init.sql` 对 tool_config/hook_config 零 INSERT，仅 params/tenant/account 有数据），损失面可控。合法用法画像（据前端模板 + 文档示例——非真实数据，现网本就没有）：主要用 java 核心库 / `@Autowired` bean / JSON / WebClient(HTTP) / Reactor / 并发容器 / JdbcTemplate 查询 / 日志；危险**执行类** API（`Runtime`/`ProcessBuilder`/`File`/元编程/`GroovyShell`/`System.exit`）在示例语料中 **0%**（⚠️ 校正：hook 示例里有无害的 `System.out/err.println`；内置工具"框架源码"摘录里有 `java.lang.reflect.*`，但那走 classPath 非 Groovy loader，不在外移范围）。取舍三选：
- **(a) 受控 RPC 回调**：执行器通过白名单接口回主进程取数据（保留能力，工程量大）。
- **(b) 能力降级**：外移工具只允许纯计算 + 受控 HTTP（简单，能力受限）。
- **(c) 分级执行**：普通租户工具外移（受限），可信内置进程内（灵活，复杂度中）。

### 6.4 分阶段
| 阶段 | 措施 | 定位 |
|---|---|---|
| **P0（即时，低风险）** | 给 `DynamicAgentTool.callAsync` / hook 执行加**超时**（现无超时）；可选 `SecureASTCustomizer` 保守黑名单（禁 `File/nio/Runtime/ProcessBuilder/reflect`+`indirectImportCheck`，现网 0% 用不误伤）+ `dependencyInjection` bean 黑名单 | 单租户够用、即时缓解 |
| **P1（多租户目标）** | 6.2 两层防御：入口 `ScriptSecurityService` 扫描 + 外移 proxy 隔离执行 | 真隔离 |

---

## 7. 下一步（接力指引）

**当前状态（2026-07-05 决策）**：加固方案已成文 `groovy-isolation-hardening-design.md`（工具侧、hook 侧均已定稿）。用户已决策 **暂缓实施**——当前按私有化单租户 / 小范围使用，**不做 Groovy 加固、暂不关注册**（风险已知情接受，理由见 §5 风险定级）。§6 是早期方向记录，**方案细节以 design.md 为准**。

**触发条件**：**上多租户 SaaS 前**必须启动本加固。届时读本文件（背景/现状/信任模型）+ `groovy-isolation-hardening-design.md`（方案/改造点/待拍板决策）接力。

**届时要做的（详见 design.md）**：
1. **工具外移沙箱**（design.md §3 + Stage 1）：Groovy 工具执行移进 proxy 强隔离容器；改造收敛在 `DynamicAgentTool.callAsync`，注入点 `ToolkitFactory:131/168` 不动。
2. **hook 无法外移**（入参是进程内可变 `Agent`/`Memory`/`Toolkit`、返回 `Mono`、能 `stopAgent` 控流）→ 多租户下**禁用动态 hook 创建**（只用 BUILTIN），或引入平台超管层后把 hook 创建 `@RoleNeed` 收紧。
3. **收紧 `dependencyInjection`**（注入任意 bean → 白名单受限门面，design.md §4.1）；`ScriptSecurityService` 补 Groovy checker 作纵深。
4. （运营侧，可选）关公开注册 `/register`（现 `@PassAuth` 无开关），发号走已有的 `adminCreateAccount`（`/admin/create-account`，`@RoleNeed(TENANT_ADMIN)`）；视需要引入平台运营方超管层。

**已完成且保留**：doTool 收紧（`fbf5cb5`/`c2343fb`）——与部署形态无关，独立正确。

---

## 8. 关键文件索引

**鉴权 / doTool（已核实）**
- `common/src/main/java/com/hxh/apboa/common/config/auth/AuthInterceptor.java`（三分支鉴权）
- `common/src/main/java/com/hxh/apboa/common/config/auth/{SkAccess,ChatKeyAccess,RoleNeed}.java`
- `runner-runtime/src/main/java/com/hxh/apboa/runtime/endpoint/ToolEndPoint.java`（doTool 已修 / debugTool 参照）
- `biz/biz-agent/src/main/java/com/hxh/apboa/agent/service/impl/AgentChatKeyServiceImpl.java`
- `biz/biz-account/src/main/java/com/hxh/apboa/account/service/impl/AccountServiceImpl.java`（`chatKeyToken` 免登换 token，绑定 agent 主人租户）
- `common/src/main/java/com/hxh/apboa/common/config/mybatis/MybatisPlusConfig.java`（租户拦截器 + IGNORE_TABLES）

**Groovy 动态执行（加固目标，已核实）**
- `engine/src/main/java/com/hxh/apboa/engine/InstanceLoader.java`（parseClass 后反射实例化 + `dependencyInjection` 注入 bean）
- `engine/src/main/java/com/hxh/apboa/engine/tool/dynamices/loader/GroovyToolInstanceLoader.java`
- `engine/src/main/java/com/hxh/apboa/engine/hook/dynamices/loader/GroovyHookInstanceLoader.java`
- `engine/src/main/java/com/hxh/apboa/engine/tool/dynamices/{IDynamicAgentTool,DynamicAgentTool,ToolInstanceLoadFactory}.java`
- `common-base/src/main/java/com/hxh/apboa/common/enums/CodeLanguage.java`
- 前端模板：`ui/src/components/tool/ToolForm.vue`、`ui/src/components/hook/HookForm.vue`；文档示例：`ui/src/doc/content/tool/index.md`

**shell 外移范式（复用蓝本，已核实）**
- `runner-runtime/src/main/java/io/agentscope/core/tool/coding/ShellCommandTool.java`
- `runner-runtime/src/main/java/com/hxh/apboa/runtime/shellproxy/{ShellProxyClient,ShellProxyConfig}.java`
- `runner-proxy/src/main/java/com/hxh/apboa/shellproxy/{controller/ShellController,service/ShellService,executor/ShellExecutor,config/ShellProperties}.java`

**准入安全网（入口层复用，来自子 agent 调研，行号需自行核实）**
- `engine/src/main/java/com/hxh/apboa/engine/workspace/hook/{WorkspaceHook,PathValidator,ShellValidator,CapacityValidator,ToolConstants}.java`
- `engine/src/main/java/com/hxh/apboa/engine/security/script/ScriptSecurityService.java`（AST 扫描，全项目仅被 WorkspaceHook 调用）
- `engine/src/main/java/com/hxh/apboa/engine/skill/SkillBoxFactory.java`（`configureCodeExecution`）、`engine/.../tool/ToolkitFactory.java`、`engine/.../agent/ReActAgentHelper.java`
- `common/src/main/java/com/hxh/apboa/common/entity/{CodeExecutionConfig,AgentCodeExecution}.java`

**部署 / 隔离（已核实）**
- `docker/docker-compose-execute.yml`、`docker/docker-compose-simple.yml`、`docker/proxy/Dockerfile`、`docker/runtime/Dockerfile`、`docker/README.md`

---

## 9. 环境备忘（本地实测）

- 命令行 mvn 需 JDK 21（见文首）；改 `common/engine/biz-*` 被依赖模块要 `install` 到 m2（`spring-boot:run` 从 m2 加载），只 `compile` 不生效。
- engine 代码在 **runtime** 进程跑（console 无 engine）。改 engine → 重启 runtime。
- 前端 dev：`npm` 需 `--legacy-peer-deps`。
- 中文测试数据必须走后端 API 写入（docker mysql client latin1，直写会写坏）。
