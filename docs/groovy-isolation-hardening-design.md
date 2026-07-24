# Groovy 执行隔离加固 —— 方案设计（供 review）

> 配套文档：`groovy-isolation-hardening-handoff.md`（背景/现状/已核实事实 + 信任模型 + 接力指引）。
>
> **设计基调**：治本、不追求临时止血、从第一性原理出发。
>
> **🚦 决策状态（2026-07-05）：暂缓实施。** 用户运营模式 =「私有化单租户为主 + 兼容多租户 SaaS」；已决策 **上多租户 SaaS 前不做本加固，当前也不关注册**（风险已知情接受）。**触发条件 = 上多租户 SaaS。** 届时读本文件 + handoff.md 接力，按 §8 分阶段落地，§9 的 🔲 决策点届时再拍板。
>
> **为什么可以暂缓**：私有化单租户下客户全权限也只炸自己那套环境，Groovy 无沙箱 ≈ 可接受的信任假设；跨租户风险只在"多客户共用一套实例"时才成立（一个客户的恶意/被盗 Groovy → `@Autowired DataSource` 绕租户拦截器读全库）。
>
> 状态：v2（工具侧、hook 侧均已定稿；决策 = 暂缓，触发条件明确）。

---

## 0. 结论先行（TL;DR）

1. **治本方向 = 进程级外移隔离**，把不可信 Groovy 代码从 runtime 主进程移到 proxy 强隔离容器执行。这不是新范式，而是**照抄原作者已经为 shell 做过的那套**（`ShellCommandTool → ShellProxyClient → proxy 容器`），一致性最高、风险最低。
2. **进程内沙箱（groovy-sandbox / SecureASTCustomizer / SecurityManager）一律不作为主防线**——它们是治标：拦不住运行时元编程、反射、bean 注入，且 JDK 17+ 已废弃 SecurityManager。只把 AST 扫描留作"纵深的一层"，不是"密封"。
3. **工具侧改造高度收敛**：核心逻辑只改 `DynamicAgentTool.callAsync` 一个方法（加 `if (proxyEnabled)` 分支，与 `ShellCommandTool` 完全对称），两个注册注入点（`ToolkitFactory:131/168`）**不用动**。
4. **bean 注入是唯一硬取舍**：外移后物理上拿不到 runtime 的 Spring bean——但这是**安全收益不是纯损失**（切断了"拿 `DataSource` 绕租户拦截器裸查全库"这个最大越权面）。合法工具要的是"受控数据/IO 能力"，不是"整个 bean 容器"。推荐：默认能力降级（纯计算 + 受控 HTTP）+ 按需受控回调 + 部署级开关。
5. **hook 侧不能外移（已核实）**：hook 的入参是进程内可变 agent 状态（`Agent`/`Memory`/`Toolkit`）、返回 `Mono`、核心能力是就地改写事件 + 控制 agent 主循环，无法跨进程。其治本解不是套沙箱，而是**信任分级**——动态 hook 创建收紧到高信任角色（普通租户只能用 BUILTIN hook），叠加 AST 白名单 + 收紧 bean 注入两道纵深。
6. **现网零预置动态工具/hook**（`db_init.sql` 对两表零 INSERT），所以**没有存量迁移负担**，方案可以直接上默认最严配置。

---

## 1. 第一性原理

**问题的本质**：动态工具/hook 允许 `EDITOR+` 用户提交**任意 Groovy 代码**，在 runtime 进程内以 runtime 的**全部权限**执行。这是一个经典的"**不可信代码在可信进程内执行**"问题。

**第一性原理**：不可信代码必须在**与信任边界相匹配的隔离环境**中执行，且默认取最严。

- **信任边界由部署形态决定**：
  - 单租户/自部署：`EDITOR` = 可信管理员，"自己写给自己跑"，信任边界 = 部署边界 → 无沙箱是可接受的信任假设（类比 Jenkins pipeline、n8n code node）。
  - 多租户 SaaS：A 租户 `EDITOR` 对 B 租户/宿主机不可信，信任边界 = 租户 → 必须真隔离。
- 用户要求两种都支持、按多租户最严 → **默认必须真隔离，同时给单租户留一个显式降级开关**。

**为什么排除进程内沙箱（它们是治标）**：
- `SecureASTCustomizer`：编译期 AST 白/黑名单，拦不住**运行时**元编程（`"cmd".execute()`）、反射、`dependencyInjection` 注入 bean 后的调用。只能抬门槛。
- `org.kohsuke:groovy-sandbox`：对 Groovy 4 兼容性未证实，历史有多次元编程/反射逃逸 CVE。
- `SecurityManager`：JDK 17 起 deprecated for removal，不能作为长期方案。
- 共同问题：**它们都在"同一个 JVM 进程内"划线，而 Groovy 的元编程 + 本项目的 bean 注入让这条线천然有缝。** 治本必须换到"不同进程/容器"这个更强的隔离原语。

**为什么选进程外移（它是治本）**：
- 物理隔离：不同 JVM，脚本连 runtime 的 bean、内存、`DataSource` 都碰不到。
- **对齐既有范式**：原作者已为 shell 建好 `proxy` 强隔离容器（read_only + tmpfs + pids_limit + 非 root + cap_drop ALL + 无对外端口），且该容器 Dockerfile 注释明写"为阻断容器→宿主机逃逸"、本就是"多语言代码执行器"。Groovy 是 JVM 语言，proxy 已是 JRE 镜像——**复用它 = 最小新增攻击面**。

---

## 2. 目标 / 非目标

**目标**：
- 多租户下，A 租户提交的 Groovy 工具代码**碰不到** B 租户数据、runtime 进程内存/凭据、宿主机。
- 逃逸边界从"runtime 容器基础隔离"降到"proxy 容器强隔离"（补齐 read_only/tmpfs/pids/断 bean）。
- 执行有超时、有资源上限、销毁即失（无状态）。

**非目标**：
- 不追求防住"单租户自部署下的可信管理员"（提供显式开关让其切回进程内、拿回完整能力）。
- 不追求进程内密封 Groovy（明确放弃这条治标路线）。

---

## 3. 工具侧设计（已定稿）

### 3.1 数据流（对齐 shell 外移）

```
Agent 对话内调用动态工具                     doTool/debugTool HTTP 直调（已 @RoleNeed 收口）
        │                                              │
        ▼                                              ▼
DynamicAgentTool.callAsync              ToolEndPoint.executeTool（同步路径）
        │   if (groovyProxyEnabled)  ← 两条路径都加同一分支
        ▼
GroovyProxyClient.execute(code, params, ctxJson, workDir, timeout)   ← 照抄 ShellProxyClient
        │   HTTP POST {proxyBaseUrl}/api/proxy/execute/groovy
        ▼
[apboa-proxy 强隔离容器]
  GroovyController → GroovyService（校验/超时裁剪）→ GroovyExecutor
        │   parseClass + 反射实例化 + execute(ctxProjection, params)
        │   进程内超时（对齐 ShellExecutor.waitFor）+ 输出上限 + 强制清理
        ▼   返回 { result | error }
```

### 3.2 复用 proxy 容器（而非新建 groovy-executor 容器）

选 **复用**。理由：proxy 定位注释就是"多语言代码执行器"、已装 python/node、已是强隔离沙箱；Groovy 只需在 pom 加 `groovy-all`（纯 JVM jar，无需额外运行时）。新建容器只会多一份镜像/运维/心跳，不增加隔离收益。

**代价**：proxy 从"纯 shell 哑执行器"变成"shell + groovy 执行器"，pom 从 2 个依赖变 3 个。可接受。

### 3.3 改造点清单（收敛，按模块）

| 模块 | 改动 | 说明 |
|---|---|---|
| **新增 SPI 模块** `dynamic-tool-spi`（极小） | `IDynamicAgentTool` 接口 + `AgentContext` 的**可序列化投影** | proxy 和 engine 都依赖它；见 3.4 |
| **runner-runtime** | 新增 `GroovyProxyClient`（照抄 `ShellProxyClient`）+ `GroovyProxyConfig`（照抄 `ShellProxyConfig`，读 `groovy-proxy.enabled/base-url` 注入静态字段） | 纯新增 |
| **engine** | `DynamicAgentTool.callAsync` 加 `if (proxyEnabled)` 分支；`ToolEndPoint.executeTool` 同步路径同样加分支 | 唯一的行为改动点，收敛 |
| **runner-proxy** | 新增 `GroovyController`(`/api/proxy/execute/groovy`) + `GroovyService` + `GroovyExecutor` + `model/GroovyExecuteRequest/Response`；pom 加 `groovy-all` + `dynamic-tool-spi` | 对称 shell 那 7 个类 |
| **配置** | `application-docker.yml` 加 `groovy-proxy.enabled`；compose 加 `GROOVY_PROXY_ENABLED`（默认 true）；proxy 容器 pom/Dockerfile 加 groovy | 对齐 shell-proxy 开关 |

注意：`ToolkitFactory:131/168` 两个 `new DynamicAgentTool(toolConfig)` 注入点**不改**——分支逻辑内聚在 `DynamicAgentTool` 内部，和 `ShellCommandTool.executeCommand` 的做法一模一样。

### 3.4 接口下沉：`dynamic-tool-spi` 模块

**为什么必须下沉**：proxy 要执行用户的 Groovy 类（`implements IDynamicAgentTool`），就必须在 classpath 上有这个接口。但 `IDynamicAgentTool.execute(AgentContext, Map)` 依赖 engine 包的 `AgentContext`，而 proxy 绝不能依赖整个 engine（会把 agentscope/mybatis/spring 一大坨拉进来，毁掉 proxy 的极简纯净）。

**已核实的有利条件**：
- `IDynamicAgentTool` 引用面只有 5 个文件（全在 engine + ToolEndPoint），下沉影响可控。
- `AgentContext` 是纯 `@Data` POJO，字段全可 JSON 序列化（本就从 `forwardedProp` JSON 反序列化而来）。
- 工具模板里 `AgentContext` 对工具是**可选增强**——`ToolForm.vue:495` 明示"开启 needConfirm 后不支持传递 AgentContext"，即产品本就允许工具不依赖它。

**做法**：新建 micro 模块 `dynamic-tool-spi`，只含：
- `IDynamicAgentTool` 接口（原样）；
- `AgentContext` 的**只读投影**（`ToolContext`：threadId/runId/tenantCode/tenantId/userInfo/params 等可序列化字段的子集）。

engine 侧把 runtime 的 `AgentContext` 映射成 `ToolContext` 传给执行器；proxy 侧收到 `ToolContext` 反序列化后注入。两边都不依赖 engine。

### 3.5 无状态 & 缓存

- proxy 侧 `GroovyExecutor` 按 code 的 MD5 缓存已 parse 的 Class（对齐现有 `TOOL_OBJ_CACHE`），避免每次调用重编译；但**执行本身无状态**（tmpfs 销毁即失、不落盘、不跨请求共享可变状态）。
- 多租户下 code 缓存以 (tenantCode + codeMd5) 为 key，避免跨租户命中。

---

## 4. bean 注入取舍（核心决策 🔲 需你拍板）

这是全方案唯一的"能力 vs 安全"硬取舍，也是文档 §6.3 留给你确认的点。

**现状**：`dependencyInjection` 用 `BeanUtils.getBean` 注入**任意** bean（`@Resource`/`@Autowired`），无白名单。外移到独立进程后，proxy 没有 runtime 的 Spring 容器 → 这个能力自动消失。

**第一性判断**：这个能力消失**主要是安全收益**——它正是"拿 `DataSource` 绕过 MyBatis 租户拦截器裸查全库"的入口。合法工具要的是"能查到（受约束的）数据 / 能发 HTTP"，不是"持有 runtime 的单例对象"。

**我的推荐（组合方案，非在 a/b/c 三选一）**：

| 能力档位 | 处理 | 覆盖的合法用途 |
|---|---|---|
| 纯计算 / JDK 核心库 / JSON | **直接允许**（proxy 容器内无害） | MathCalculator 式（现网示例即此类） |
| 出站 HTTP | **允许 + SSRF 防护**（禁内网段/云元数据端点/`file://`） | 调外部 API（模板里的 WebClient 用法） |
| 数据库 / Redis / 内部 Service | **不透传 bean**，改**受控回调 SPI**：执行器带 `ToolContext`（含 tenantCode + 签名）回调 runtime 的专用端点，runtime 侧在**租户拦截器约束下**代查后返回 | 需要平台数据的工具（现网 0 个，按需再开） |

- **部署级开关（对应 §6.3 的 c）**：`groovy.execution.mode = isolated（默认）| in-process`。多租户强制 `isolated`；单租户自部署可显式切 `in-process` 拿回完整 bean 注入（可信假设，类比 Jenkins）。
- 受控回调（对应 §6.3 的 a）默认**不开**，作为 Stage 2 的按需扩展点——现网零预置工具，MVP 不需要它。

> 需要你确认的：是否接受"默认降级到 纯计算 + 受控 HTTP，数据访问走 Stage 2 受控回调"？还是有已知的动态工具用法必须在 Stage 1 就拿到数据库/内部 bean？（据现网数据是没有的。）

### 4.1 收紧 `dependencyInjection`（独立必做项，不依赖外移）

`InstanceLoader.dependencyInjection`（工具/hook 两个 loader 都调用）当前用 `BeanUtils.getBean` 注入**任意** `@Resource`/`@Autowired` bean，无白名单。这是一条**独立于"外移"的提权链**：

- 工具**外移后**，proxy 容器里根本没有 Spring 容器，这条链对工具自动失效——不用额外处理。
- 但 **hook（不外移）**、以及单租户 `in-process` 模式下的工具，仍留在进程内，`@Autowired JdbcTemplate` 一行就拿到裸库连接、绕过 MyBatis 租户拦截器。
- **加固**：把 `dependencyInjection` 的注入源从"整个 Spring 容器"收窄为"一个**白名单化的受限门面 bean 集合**"（只暴露安全能力，如受租户约束的数据访问门面 / 受控 HTTP 客户端），非白名单 bean 一律拒绝注入。

这条对"任何留在进程内执行的动态代码"都必须做，是 §5 hook 隔离的一部分，也是 `in-process` 工具模式的安全底线。

---

## 5. hook 侧（已定稿：不能外移 → 治本 = 收缩信任边界）

**核实结论：动态 hook 无法外移**（已回源码 + agentscope-core 1.1.0-RC2 源码核对）。本质障碍：
- Hook 接口是 `<T extends HookEvent> Mono<T> onEvent(T event)`——返回 reactor `Mono`（进程内响应式流），入参 `HookEvent`（sealed 类）拿到的全是**进程内可变 agent 对象**：`getAgent()`→整个 Agent 实例、`getMemory()`→可变会话记忆、`getToolkit()`→工具集、`getInputMessages()`→`List<Msg>`、`getToolUse()`→`ToolUseBlock`。
- hook 的核心能力是**原地回写事件对象**（`setToolUse`/`setReasoningMessage`/`setInputMessages`，接口 javadoc 明写"changes will affect agent execution"）和**直接控制 agent 主循环**（`stopAgent()` 暂停、`gotoReasoning()` 强制回推理）。
- 这些对象不可 JSON 序列化，其"修改即影响本进程 agent 事件循环"的语义**无法用跨进程 RPC 表达**。内置 hook 实证——`IConfirmationHook` 用 `stopAgent()` 暂停等确认、`WorkspaceHook` 用 `setToolUse()` 篡改危险工具调用、`WebsocketHook` 推 WS、`ChatLogHook` 落库——无一例外都在"就地操作进程内 agent 状态"，外移即失去存在意义。

**第一性原理下 hook 的"治本"≠ 给不可信代码套沙箱，而是收缩信任边界**：
既然 hook 必须操作进程内 agent 状态、技术上无法隔离，那"允许不可信主体提交 hook 代码"这个**前提本身就是错的**。hook 能读整个 `Agent`/`Memory`、能 `stopAgent`、能 `@Autowired` 注入任意 bean——权限等价于"改写 agent 的大脑"。治本 = 移除错误前提 = **把动态 hook 划到可信侧**，而不是给一段权限等于大脑的代码打补丁假装它安全。

**方案（三重进程内隔离，信任分级为架构主轴）**：
1. **【主轴·架构性】信任分级准入**：动态 hook 的创建/编辑用 `@RoleNeed` 锁到高信任角色。普通租户 `EDITOR` **不能写 Groovy hook**，只能用平台预置的 BUILTIN hook。这不是止血补丁，而是架构性地把 hook 从"不可信输入"收缩为"受控可信输入"——与项目已有的 `@RoleNeed` / doTool 收紧一脉相承。
   > ⚠️ **现实校正（2026-07 复核）**：当前框架**无平台运营方超管层**（客户即自己租户的 admin/owner，权限全是租户内 `TenantRole`，见 handoff §5）。所以多租户下"锁到平台超管"**需先引入平台超管层**（跨租户、高于所有 `TenantRole`）；在引入之前，等价可行的兜底是**多租户模式下直接禁用动态 hook 创建**（只用 BUILTIN hook）——因为租户内角色无法把运营方和客户分开。
2. **【纵深】编译期 AST 白名单**：把 §6 的 `GroovySecurityChecker` 前移到 `GroovyHookInstanceLoader.parseClass` 之后、缓存之前，对 hook 编译产物做白名单校验（禁反射/`Runtime`/`System.exit`/文件/网络等）。对"可信但可能手滑"的 hook 作者是一道保险，不是主防线。
3. **【纵深·独立必做】收紧 `dependencyInjection`**：见 §4.1——这条对 hook 尤其关键，因为 hook 留在进程内，`@Autowired 任意 bean` 的提权面对它始终敞开。

> **为什么工具外移、hook 收缩准入，却都算"治本"**：治本 = 让每一类不可信代码落在**与其信任等级匹配的边界**内。工具 I/O 简单 → 可移到强隔离容器（边界 = 容器）；hook 必须碰进程内状态、无法隔离 → 只能把准入收缩到可信主体（边界 = 角色）。两者都消除了"不可信代码在可信进程内自由执行"的根因，手段不同而已。

---

## 6. 纵深（入口层保留，但明确不是主防线）

治本靠外移；入口层 AST 扫描作为**多一层**保留，不假装它能密封：
- 给 `ScriptSecurityService` 补一个 `GroovySecurityChecker`（现在对 Groovy 是 fail-open），扫描明显危险模式（`Runtime`/`ProcessBuilder`/元编程/反射/`System.exit`）——现网合法用法 0 命中这些，不误伤。
- HITL（needConfirm）对 agent 内调用的动态工具本就生效，保留。
- 定位：入口层拦掉"低级/脚本化"攻击、留审计信号；真正的隔离由 proxy 容器兜底。

---

## 7. 迁移、回滚、现网影响

- **现网影响 = 无迁移负担**：`db_init.sql` 对 tool_config/hook_config 零 INSERT，现网无任何存量动态工具/hook；默认最严配置不会破坏任何现有功能。
- **回滚**：`groovy.execution.mode=in-process`（或 `groovy-proxy.enabled=false`）一键切回现状行为，与 `shell-proxy.enabled` 回滚方式一致。
- **灰度**：可先对新建工具默认 isolated，观察；因无存量，灰度风险极低。

---

## 8. 分阶段（强调：一个正确架构分步落地，每步都治本，非"止血→根治"）

| 阶段 | 内容 | 达成的治本效果 |
|---|---|---|
| **Stage 1（工具外移 MVP）** | SPI 模块 + GroovyProxyClient/Config + proxy groovy 执行器（纯计算 + 受控 HTTP + 超时/资源上限）+ DynamicAgentTool 分支 + 默认 isolated | **工具侧核心风险已根治**：多租户下工具代码碰不到 B 租户/runtime 内存/宿主机 |
| **Stage 2（能力补全）** | 受控回调 SPI（带租户上下文签名），让需数据的工具在受约束下工作 | 在不放开 bean 的前提下补回数据能力 |
| **Stage 3（hook）** | §5 三重：hook 创建 `@RoleNeed` 锁高信任角色（主轴）+ Groovy 编译期 AST 白名单 + 收紧 `dependencyInjection`（§4.1） | hook 收缩到可信边界 |
| **纵深（并行）** | ScriptSecurityService 补 Groovy checker + 保留 HITL | 多一层，非主防线 |

> 关于"超时"：外移后超时是 proxy 执行器的**固有组成**（对齐 `ShellExecutor.waitFor`），不是独立的"P0 止血补丁"。因此本方案不单列 P0——Stage 1 一步到位。

---

## 9. 决策点（🚦 已决策暂缓——以下待启动实施时再拍板）

> 当前已决策**暂缓实施**（见顶部）。以下 🔲 是**上多租户、启动本方案时**才需要拍板的点，现在无需决定。

1. 🔲 **bean 取舍（§4）**：接受"Stage 1 默认降级到 纯计算 + 受控 HTTP、数据访问留 Stage 2 受控回调"吗？
2. 🔲 **proxy 复用 vs 新容器（§3.2）**：接受复用 proxy 容器吗？（推荐复用）
3. 🔲 **hook 策略（§5）**：hook 技术上无法外移。多租户下二选一——(a) 先引入**平台超管层**再把 hook 创建锁给运营方；(b) 直接**禁用动态 hook 创建**（只用 BUILTIN）。选哪种？
4. 🔲 **`dependencyInjection` 收紧（§4.1）**：认可把"注入任意 bean"改为"只注入白名单受限门面 bean"吗？这条对 hook 和 in-process 工具都必做。
5. 🔲 **部署开关默认值**：`groovy.execution.mode` 默认 `isolated`、单租户可切 `in-process`——认可吗？
