# Apboa 工具人工确认（HITL）机制重构 — 完整实施清单

> **致执行者（下一个 Claude）**：这份文档是自包含的，你不需要任何先前的对话上下文。请**按顺序通读** §0~§5（背景、问题、根因、官方机制、目标架构），再进入 §6 逐步实施。
>
> 文档中所有 `文件:行号` 是撰写时的快照，**动手前务必用 `grep`/`Read` 二次核对当前代码**（代码可能已变动）。文档区分了「✅ 已用代码/运行时实证确认」与「⚠️ 需你二次验证」两类信息，请留意标注。
>
> **本次重构的方向与三项关键决策已由项目负责人拍板，不要推翻**：
> 1. 走 **AgentScope 官方 HITL「暂停-恢复」重构**（不是在现有 hack 上打补丁）。
> 2. 暂停态用 **生产级分布式 Session（Redis 或 MySQL）**，不能用单机内存。
> 3. 拒绝工具时回传 **`ToolResultBlock("用户已拒绝执行")`**（喂结果继续，而非重新推理）。

---

## 0. TL;DR

**做什么**：把工具人工确认从"前端代执行工具 + 重新发起一轮对话（run）"的 hack 实现，重构为 AgentScope 官方 HITL 的「暂停 → 推送待确认 → 用户决策 → 恢复（agent 自己继续执行 / 喂入工具结果）」状态机。

**为什么**：现有实现已复现 3 个 bug + 1 个假象 + 多个隐患，根因是**误用了官方机制**（只用了暂停 `stopAgent`，恢复却用前端代执行 + 重新 run）。

**核心改动**：
1. 分布式 Session（Redis/MySQL）保持「暂停态」，与 `memoryActive` 解耦。
2. 暂停时通过 AG-UI 推送 `TOOL_CONFIRM_REQUIRED` 事件（带每个待确认工具的 id/name/input）。
3. 新增 `resume` 接口：全允许 → `agent` 继续执行 pending 工具；含拒绝 → 喂 `ToolResultBlock("用户已拒绝执行")` 继续。
4. 确认登记逻辑删掉 `&& isMemoryActive` 耦合。
5. MCP 工具接入确认（加字段 + 注册登记 + 前端开关），确认后由 agent 自己执行（天然带租户上下文）。
6. 前端：按 `toolUseId` 逐工具确认 UI，决策齐了才调 `resume`；删除前端代执行与"塞文本"逻辑。

---

## 1. 项目背景（动手前必读）

### 1.1 技术栈
- 后端：Java 21 · Spring Boot 3.4.9 · **AgentScope 1.0.12**（`io.agentscope:agentscope-core`，阿里开源智能体框架）· MyBatis-Plus · Druid
- 前端：Vue 3.5 · Ant Design Vue 4 · Pinia · Vue Router 5（目录 `ui/`）
- 数据库：MySQL（主库 `apboa_next`）· Redis · pgvector（向量库）
- 部署：Docker Compose；**`runner-runtime` 是可水平扩展的多实例服务**（这点对"分布式暂停态"至关重要）。

### 1.2 关键模块
| 模块 | 职责 | 与本次相关的内容 |
|---|---|---|
| `engine` | 智能体引擎 | Hook、Toolkit、Mcp、AgentContext、确认机制核心 |
| `runner-runtime` | AI 运行时 + AG-UI 端点 | agent 执行、SSE 事件流、Session、resume 接口要加在这 |
| `runner-console` | 管理控制台 | MCP/工具/模型等管理接口 |
| `biz-tool` / `biz-mcp` | 工具/MCP 业务 | `need_confirm` 字段的存取 |
| `common` / `common-base` | 实体/枚举/工具类 | `ToolConfig`、`McpTool`、`AgentContext` 等 |
| `ui` | 前端 | 确认 UI、AG-UI 客户端、resume 调用 |

### 1.3 必须先理解的概念
- **ReActAgent**：AgentScope 的 ReAct（推理 Reasoning + 行动 Acting）循环智能体。一轮里可一次性决定调用**多个**工具。
- **AG-UI 协议**：前端 ↔ runtime 的流式（SSE）协议。前端发一个 `run` 请求，后端流式吐事件：`TEXT_MESSAGE_*`、`TOOL_CALL_START/ARGS/RESULT`、`REASONING_MESSAGE_*`、`RUN_STARTED`、`RUN_FINISHED` 等。前端按事件类型更新 UI。
- **Hook**：AgentScope 钩子，监听 agent 生命周期。关键事件：`PostReasoningEvent`（推理后、工具执行前）、`PostActingEvent`（工具执行后）。
- **Toolkit**：agent 的工具集，由 `ToolkitFactory` 按 agent 配置构建。
- **AgentContext**（apboa 自定义，`engine/.../engui/AgentContext.java`）：基于 `ThreadLocal` 存当前请求的租户、`memoryActive`、`toolProcessActive` 等。⚠️ ThreadLocal 在异步线程会丢失（这是 Bug3 的关键之一）。
- **Session**（AgentScope，`io.agentscope.core.session.Session`，实现 `InMemorySession`）：保存/加载 agent 的 memory 状态，`reActAgent.loadFrom(session, threadId)` / `reActAgent.saveTo(session, threadId)`。

### 1.4 现状：当前的确认机制是怎么"硬凑"出来的（关键）
> 这是所有 bug 的源头，务必看懂。

```
① agent 推理 → 决定调用 [工具A(need_confirm), 工具B] 
② IConfirmationHook 监听 PostReasoningEvent：发现 A 在"需确认清单"里 → e.stopAgent() 暂停整个 agent（工具都不执行）
③ agent 暂停 → AG-UI 吐 RUN_FINISHED（注意：并没有"哪些工具要确认"的事件）
④ 前端 onRunFinished：把【本轮所有进行中的工具】统统标记 needConfirm=true ← 粗暴
⑤ 前端 ToolCallItem 渲染「允许/禁止」按钮
⑥ 点「允许」：前端调 agentDoTool 接口【自己执行工具】，把结果 emit 回去
   点「禁止」：前端构造一段"用户已拒绝"文本，emit 回去
⑦ sendToolContent：client.messages=[那一个工具的结果] → 重新 run()（开启全新一轮）
⑧ run() 触发 onRunStarted → 清空所有确认 UI
```

**官方做法本应是**：②暂停后，③推送待确认工具，⑥用户决策，⑦**调 `agent.call()` 让 agent 从暂停点继续**（或 `agent.call(toolResult)` 喂结果），全程同一个 agent 实例、不重开一轮。apboa 完全跳过了官方恢复，用"前端代执行 + 重新 run"硬凑——于是派生出下面所有 bug。

---

## 2. 问题全景（现象 → 根因 → 源码 → 复现）

> 下面每个问题都给了「如何复现验证」，执行者在动手前应先复现一遍，建立直观认识。

### 2.1 复现环境准备（通用）
- 一个绑定了「需确认的系统工具」+「MCP 工具」的智能体。当前测试用例：
  - agent：`test agent`（库 `agent_definition`，id 见下）
  - 系统工具 `get_current_datetime`（`tool_config.need_confirm = 1`）
  - MCP 工具 `maps_weather`（高德地图 MCP，`mcp_tool` 表，**无 need_confirm**）
- 数据库自查命令（确认前提）：
  ```bash
  # 哪些普通工具开了确认
  docker exec mysql mysql -uroot -p123456 apboa_next -e \
    "SELECT id,tool_id,name,tool_type,need_confirm,enabled FROM tool_config;"
  # MCP 工具表结构（确认无 need_confirm 列）
  docker exec mysql mysql -uroot -p123456 apboa_next -e "SHOW COLUMNS FROM mcp_tool;"
  # agent 绑了哪些普通工具
  docker exec mysql mysql -uroot -p123456 apboa_next -e "SELECT id,agent_definition_id,tool_id FROM agent_tools;"
  ```
- 浏览器复现：打开对话页（如 `http://localhost/web/#/chat/<agentId>`），新开会话，发送：
  > 现在是什么时候，然后泉州今天天气怎么样
- ✅ 上述场景与现象本文档作者已在浏览器中实地复现确认。

### 2.2 「MCP 确认假象」——MCP 不是默认 need_confirm，是被连累的
- **现象**：`maps_weather`（MCP，无 need_confirm）竟然也弹出「允许/禁止」。
- **根因**：MCP 工具**从不进入**确认清单（`McpClientFactory.getLazyMcpTools` 注册时完全没有 confirm 逻辑；`McpTool` 实体无 `need_confirm`）。它弹确认，是因为**同一轮里 `get_current_datetime`（need_confirm=1）触发了 `stopAgent` 暂停整个 agent**，然后前端 `onRunFinished` 把本轮**所有**工具（含 MCP）都标记成 needConfirm。
- **源码**：
  - `IConfirmationHook.onEvent`（`engine/.../hook/builtins/IConfirmationHook.java` 第32-67行）：仅对在 `NEED_CONFIRM_TOOLS` 里的工具收集，但只要非空就 `stopAgent()`（暂停整轮）。
  - 前端 `ui/src/composables/chat/useChatStream.ts` 第197-201行 `onRunFinished`：`toolCallsInProgress.value.forEach(item => item.needConfirm = true)`（全标记）。
  - `setNeedConfirmTool` 的唯一调用方是 `ToolkitFactory`（仅普通工具 `toolConfig`），grep 验证：
    ```bash
    grep -rn "setNeedConfirmTool\|removeNeedConfirmTool" --include=*.java .
    ```
- **复现/验证**：只发"泉州天气怎么样"（不带时间，本轮无 need_confirm 工具）→ MCP **不弹**确认。证明 MCP 确认是连累，不是自身能力。

### 2.3 Bug1 —— 点一个工具的按钮，所有工具都被处理
- **现象**：两个工具各有「允许/禁止」。点其中**一个**工具的任一按钮，**两个工具的确认 UI 全部消失**，且没点的那个工具也被处理。
- **根因（两层）**：
  1. 前端 `sendToolContent`（`useChatStream.ts` 第233-266行）只把**被点工具**的结果塞进 `client.messages`，然后 `run()` 重开一轮。
  2. `run()` 触发 `onRunStarted`（`useChatStream.ts` 第61-65行）`toolCallsInProgress.value = []` → **清空所有确认 UI**；另一个工具的决策直接丢失，由重新 run 的 agent 自行处置。
- **源码**：`useChatStream.ts:61`、`:200`、`:233`；`ui/src/components/chat/MessageList.vue:41-50`（`v-for` 渲染多个 ToolCallItem）；`ui/src/views/Chat/index.vue:335` `handelToolContent → sendToolContent`。
- **复现/验证**：复现场景中点 `maps_weather` 的「禁止」→ 两个按钮全消失，`get_current_datetime` 也被执行（✅ 已实测）。

### 2.4 Bug2 —— 拒绝后，工具仍被 agent 强行重新调用
- **现象**：点 MCP 工具「禁止」后，它仍被调用（在对话流里出现两次：先"用户已拒绝"，再被真正调用一次）。
- **根因**：`ToolCallItem.handleCancel`（`ui/src/components/chat/ToolCallItem.vue` 第64-82行）只是构造一段**自然语言文本**"用户本次拒绝了……"，`emit` 回去；`sendToolContent` 再 `run()` 重开一轮。agent 重新推理时，看到用户的原始诉求未满足（要天气），就**重新调用了被拒绝的工具**。拒绝从未真正"终止"该工具。
- **加剧因素**：`ToolExecutor.applyRetry`（`runner-runtime/.../core/tool/ToolExecutor.java` 第379-411行）有工具调用重试（`Retry.backoff(maxAttempts-1)`），MCP 第一次连接失败时会再试一次。
- **源码**：`ToolCallItem.vue:64`、`useChatStream.ts:233`、`ToolExecutor.java:379`。
- **复现/验证**：✅ 已实测，对话流里 `maps_weather` 出现两次（拒绝 0ms + 重调 374ms 真连 MCP）。

### 2.5 Bug3 —— need_confirm 工具被绕过确认、裸跑（最严重）
- **现象**：拒绝 MCP 工具后，`get_current_datetime`（它自己是 need_confirm 工具）**未经我同意就被执行了**。
- **根因（两层）**：
  1. （承接 Bug1）`get_current_datetime` 的决策丢失，agent 重新 run 时重新调用它。
  2. 重新调用时本该再次被确认拦截，但 `ToolkitFactory` 第134行：
     ```java
     if (toolConfig.getNeedConfirm() && isMemoryActive) {
         IConfirmationHook.setNeedConfirmTool(toolConfig.getToolId());   // 加入确认清单
     } else {
         IConfirmationHook.removeNeedConfirmTool(toolConfig.getToolId()); // ← 移出清单，确认失效！
     }
     ```
     当 `isMemoryActive=false` 时，need_confirm 工具被**移出确认清单、直接裸跑**。
  - `isMemoryActive` 来自 `ToolkitFactory:120` `AgentContext.getIfExists().map(AgentContext::isMemoryActive).orElse(false)`；`AgentContext` 是 **ThreadLocal**（`AgentContext.java:23`），异步线程拿不到就 `orElse(false)`。
  - 前端 `memoryActive` 是稳定的持久化偏好（`ui/src/views/Chat/index.vue:52` computed，两次 run 传值一致）→ 所以第二次 `isMemoryActive=false` 是**后端 ThreadLocal 在异步线程丢了 AgentContext**（与本项目 git 历史里反复修的"异步上下文传递"同根）。
- **更广的漏洞（举一反三）**：`&& isMemoryActive` 这个耦合意味着——**只要用户没开"记忆"功能，所有 need_confirm 工具的确认从头到尾都是摆设、全部裸跑**。这是比 Bug3 更普遍的安全问题。
- **源码**：`ToolkitFactory.java:120,134,169`、`AgentContext.java:23,104`、`Chat/index.vue:52`。
- **复现/验证**：✅ 已实测（拒绝 weather 后 datetime 结果直接出现）。

### 2.6 其余隐患（一并纳入本次治理）
| 编号 | 隐患 | 位置 | 说明 |
|---|---|---|---|
| P6 | 工具重试加剧拒绝后重调 | `ToolExecutor.java:379` | 需对"需确认工具"评估是否禁用重试 |
| P7 | 确认后执行接口不认 MCP | `runner-runtime/.../endpoint/ToolEndPoint.java:36` | `doTool` 按 `tool_config` 查，MCP 工具不在该表→查不到（重构后此路径废弃） |
| P8 | 该接口 AgentContext 是空壳 | `ToolEndPoint.java:52` `new AgentContext()` 无租户 | MCP 工具连不上 server（重构后此路径废弃） |
| P9 | 确认清单静态全局 + 按工具名匹配 | `IConfirmationHook.java:29` | 跨 agent/租户共享；MCP 工具原生名易重名 → 张冠李戴 |
| P10 | 确认耦合 isMemoryActive | `ToolkitFactory.java:134` | 即 Bug3 根因 |

---

## 3. 根本原因（一图说清）

```
官方 HITL（正确）：
  推理 → PostReasoningEvent.stopAgent() 暂停
       → 把 pending ToolUseBlock 给用户看
       → 用户允许：agent.call() 让【同一个 agent】从暂停点继续执行 pending 工具
       → 用户拒绝：agent.call(ToolResultBlock("已拒绝")) 喂入结果继续（不执行工具）
  全程同一个 agent 实例 / Session 状态，工具由 agent 自己执行（天然带租户/记忆/MCP）

apboa（错误）：
  推理 → stopAgent 暂停（✅ 这步对）
       → ❌ 不推送 pending；前端 onRunFinished 全标记
       → ❌ 允许：前端 agentDoTool 自己执行工具 + run() 重开一轮
       → ❌ 拒绝：前端塞文本 + run() 重开一轮
  每次 run 重建 agent、丢暂停态；agent 状态仅 memoryActive=true 才 saveTo
  → 派生 Bug1（重开清空+单结果）、Bug2（重新推理重调）、Bug3（重建时确认随 isMemoryActive 失效）、MCP 假象（全标记连累）
```

**一句话**：apboa 把"恢复"做成了"重开"。重构就是把"重开"换成官方的"恢复"。

---

## 4. 官方 HITL 机制参考（已从官方文档 + jar 反编译确认）

### 4.1 官方文档要点（AgentScope-Java HITL）
- 两个暂停时机：`PostReasoningEvent.stopAgent()`（推理后/执行前）、`PostActingEvent.stopAgent()`（执行后）。
- 处理暂停与恢复（官方示例，阻塞式）：
  ```java
  Msg response = agent.call(userMsg).block();
  while (response.hasContentBlocks(ToolUseBlock.class)) {       // 有待确认工具
      List<ToolUseBlock> pending = response.getContentBlocks(ToolUseBlock.class);
      if (userConfirms()) {
          response = agent.call().block();                      // 允许：继续执行 pending 工具
      } else {
          Msg cancelResult = Msg.builder().role(MsgRole.TOOL)
              .content(pending.stream()
                  .map(t -> ToolResultBlock.of(t.getId(), t.getName(),
                       TextBlock.builder().text("操作已取消").build()))
                  .toArray(ToolResultBlock[]::new))
              .build();
          response = agent.call(cancelResult).block();          // 拒绝：喂入工具结果后继续
      }
  }
  ```
- API 速查：
  - 暂停：`PostReasoningEvent.stopAgent()`、`PostActingEvent.stopAgent()`
  - 恢复：`agent.call()`（继续执行待处理工具）、`agent.call(toolResultMsg)`（喂结果后继续）
  - 判断暂停原因：`response.getGenerateReason()` → `REASONING_STOP_REQUESTED` / `ACTING_STOP_REQUESTED`

### 4.2 jar 反编译确认的 API（`agentscope-core-1.0.12.jar`）
> 用 `javap -cp <jar> <类全名>` 复核。
- `AgentBase`：
  - `call(List<Msg>)` / `call(List<Msg>, Class)` / `call(List<Msg>, JsonNode)` — 阻塞式（官方"无参 call()"对应**空列表**继续）
  - `stream(List<Msg>, StreamOptions)` / `stream(..., Class)` / `stream(..., JsonNode)` — **apboa 用的是这个（流式）**
  - 中断机制：`interrupt()` / `interrupt(Msg)` / `interrupt(InterruptSource)`、`handleInterrupt(InterruptContext, Msg...)`、`checkInterruptedAsync()`、`resetInterruptFlag()`、`getInterruptFlag()`、`getInterruptSource()`
  - 相关类型在包 `io.agentscope.core.interruption`（`InterruptContext` / `InterruptSource`）
- ⚠️ **需你二次验证（V0 必做）**：流式 `stream(...)` 的"恢复"语义——暂停后用 `stream(空列表/toolResults, opts)` 是否能"从暂停点继续"而非"开新一轮"。官方文档只演示了阻塞式 `call()`。务必先做 §6.0 的 V0 验证。

### 4.3 apboa 当前的执行入口（已确认）
- `runner-runtime/.../agui/processor/AguiRequestProcessor.java`
  - `process(...)` 第64行：解析 agent（`agentResolver.resolveAgent(agentId, threadId)` 第71行）
  - Session：`InMemorySession`（第42行，**单机内存，必须换掉**）
  - 第104-133行：`memoryActive=true` 才 `reActAgent.loadFrom(session, threadId)`（第110行）；否则 `getMemory().clear()`（第127行）
  - 第147-151行：`adapter.run(effectiveInput)`，结束时 `memoryActive` 才 `reActAgent.saveTo(session, threadId)`（第151行）
- `runner-runtime/.../agui/adapter/AguiAgentAdapter.java`
  - 第106行：`agent.stream(msgs, options)` 流式执行
  - 把 AgentScope 事件转 AG-UI 事件；`ThinkingBlock → REASONING_*`（第176行）；`enableReasoning`（第55行）

---

## 5. 目标架构

```
                         ┌─────────── 分布式 Session（Redis/MySQL）───────────┐
                         │  保存/恢复：agent memory + 暂停态（pending 工具）    │
                         └───────────────────────────────────────────────────┘
                                   ▲ saveTo                       │ loadFrom
                                   │（暂停时，无条件保存）          ▼（resume 时恢复）
前端                         runner-runtime（多实例）
 │ 1. run（发消息）   ─────────▶ AguiRequestProcessor.process
 │                              → agent.stream(...)
 │                              → IConfirmationHook 命中 need_confirm → stopAgent
 │ 2. ◀── 推送 TOOL_CONFIRM_REQUIRED（pending: [{toolUseId,name,input}]）
 │ 3. 逐工具渲染「允许/禁止」（按 toolUseId 独立）
 │ 4. 决策齐 ──▶ POST /agui/resume {threadId, decisions:[{toolUseId,approved}]}
 │                              → loadFrom(session) 恢复暂停态
 │                              → 全允许：agent 继续执行 pending（stream 继续）
 │                              → 含拒绝：喂 ToolResultBlock("用户已拒绝执行") 继续
 │ 5. ◀── 继续流式：TOOL_CALL_RESULT / TEXT_MESSAGE / RUN_FINISHED
```

**关键不变量**：
- 工具一律由 **agent 自己执行**（不再有前端 `agentDoTool` 代执行）→ MCP/租户/记忆天然正确。
- 确认是否生效**只取决于工具自身的 `need_confirm`**，与 `memoryActive` 无关。
- 暂停态**跨请求、跨实例**可恢复（分布式 Session）。

---

## 6. 实施方案（逐步执行）

> 实施顺序：**§6.0 V0 验证 → §6.1 分布式 Session → §6.2 暂停事件 → §6.3 resume 恢复 → §6.4 解耦+清单 → §6.5 前端 → §6.6 MCP → §6.7 清理**。每步都有「验证」，通过后再进入下一步。

### 6.0 V0 前置验证（最重要，决定方案成立与否）
**目标**：在 apboa 现有 `stream + Session` 模式下，验证「暂停 → 保存 Session → 新请求加载 → 继续执行/喂结果」链路可行。

**做法**（写一个最小集成测试或临时 endpoint）：
1. 构造一个 `ReActAgent`，挂一个永远 `stopAgent()` 的 `PostReasoningEvent` hook，绑两个简单工具。
2. `agent.stream(userMsg, opts)` 跑到暂停；`saveTo(session, threadId)`。
3. 新建/重新解析 agent，`loadFrom(session, threadId)`；
   - 方式A：`stream(空列表, opts)` → 观察是否"继续执行 pending 工具"。
   - 方式B：`stream([ToolResultBlock(...)], opts)` → 观察是否"用喂入结果继续"。
4. 用 `response.getGenerateReason()` 确认 `REASONING_STOP_REQUESTED`，用 `hasContentBlocks(ToolUseBlock.class)` 确认 pending。

**判定**：
- ✅ 通过（能继续/能喂结果）→ 按本方案继续。
- ❌ 不通过（stream 无法从暂停点恢复，只能开新一轮）→ **暂停并上报**：可能需要 (a) 改用阻塞 `call()` 包装成 SSE，或 (b) 用 `interrupt()/handleInterrupt()` 这套中断机制。把 `javap io.agentscope.core.ReActAgent` 与 `handleInterrupt`/`InterruptContext` 的实现反编译出来研究后再定。

> ⚠️ 在 V0 通过前，不要写后面的生产代码。

---

### ✅ V0 验证结论（2026-06-30 完成，已实测通过）

> 验证载体：`engine/src/test/java/com/hxh/apboa/engine/hitl/HitlV0StreamRecoveryTest.java`，真实调用本地 Ollama（OpenAI 兼容端点，模型 `hauhaucs-qwen3.6-35b-a3b-q8kp`，capabilities：tools + thinking）+ 真实 MySQL。

**判定：✅ 通过，方案成立。** `stream` 模式可从暂停点恢复（不是开新一轮），且 AgentScope 1.0.12 **自带官方「暂停-恢复」机制**，无需手搓状态机。

**四个场景全部实测通过：**

| 场景 | 恢复方式 | 结果 |
|---|---|---|
| 全允许 | `loadFrom` 后 `stream(List.of())` | pending 工具执行、参数完整、无重复 |
| 全拒绝 | `stream([ToolResultBlock("用户已拒绝执行") …])` | 加 prompt 约束后不重调被拒工具 |
| 部分允许/拒绝 | `stream([部分 ToolResultBlock])` | 被拒的喂结果、被允许的 agent 自执行、**协议完整** |
| MySQL 跨实例 | 全新 `MysqlSession`+`agent` `loadFrom` | 暂停态落库后跨实例完整恢复 |

**对本文档「现状」的实证修正（执行 §6.1+ 时以此为准，不要再按原描述返工）：**

1. **Session 已是官方 `MysqlSession`（`@Primary`，见 `runner-runtime/.../ApboaAgentSessionConfig.java`），不是 `InMemorySession`**（后者只是 `AguiRequestProcessor` builder 的兜底默认）。`agentscope_sessions` 表生产已在用。→ §6.1 **不必造 RedisSession**，沿用 MySQL；核心工作收敛为「解除 `memoryActive` 对**暂停态** save/load 的门控」。
2. **AgentScope 自带官方暂停-恢复 API**：`ReActAgent.Builder.enablePendingToolRecovery(true)` + `statePersistence(memoryManaged + statefulToolsManaged)`；恢复时 `stream(空)` 让 agent 继续执行 pending，`stream([ToolResultBlock])` 喂入结果继续。→ 无需手搓状态机；`ReActAgent` 内部已有 `extractPendingToolCalls/buildSuspendedMsg` 等。
3. **暂停信号**：`AGENT_RESULT` 事件的 `Msg.getGenerateReason() == REASONING_STOP_REQUESTED`，pending 工具在该 Msg 的 `ToolUseBlock` 里；`AguiAgentAdapter` 当前只消费 `REASONING`/`TOOL_RESULT`、忽略 `AGENT_RESULT`，故暂停只吐 `RUN_FINISHED`。→ §6.2 推送 `TOOL_CONFIRM_REQUIRED` 的可靠触发点就在这里。

**关键回归项（呼应 §8.4）：** 喂入「用户已拒绝执行」后，ReAct 模型会因「用户诉求未满足」倾向于**重新调用被拒工具**；已验证在 system prompt 加一条约束（“被用户拒绝的工具调用，本轮不得重试”）即可止住。§6.3 实现务必带上这条并作为回归项盯住。

**环境注意（务必）：** 命令行 `mvn` 必须用 **JDK 21**（本机 `/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`）。默认的 JDK 26 会让 Lombok 1.18.30 注解处理器**静默失效**，导致 `common-base` 等模块「找不到 getter/构造器/log」全量编译失败（与业务代码无关）。本机首次需 `mvn -pl <模块> -am install -DskipTests` 把内部模块装进本地仓库。

---

### 6.1 后端 — 分布式 Session（Redis/MySQL）
**问题**：`AguiRequestProcessor.java:42` 用 `InMemorySession`，多实例下暂停态会丢（resume 请求可能落到别的实例）。

**目标**：实现 `io.agentscope.core.session.Session` 接口的持久化版本（**优先 Redis**，可同时落 MySQL 做持久备份）。

**步骤**：
1. `javap -cp <agentscope-core jar> io.agentscope.core.session.Session` 看接口方法（load/save 的签名、key 维度）。同时反编译 `InMemorySession` 作为实现参考：
   ```bash
   JAR=~/.m2/repository/io/agentscope/agentscope-core/1.0.12/agentscope-core-1.0.12.jar
   # 提取 class 后用 cfr 反编译（cfr 可从 maven 阿里云镜像下载 org/benf/cfr/0.152）
   ```
2. 新建 `RedisSession implements Session`（建议放 `runner-runtime` 或 `engine`）：
   - key：`apboa:agent-session:{tenantId}:{threadId}`（**带租户维度**，避免跨租户串）。
   - value：agent 状态的序列化（memory + 暂停态）。需确认 AgentScope 状态对象是否可 JSON 序列化；不行则用其提供的 state 导出/导入 API（看 `saveTo/loadFrom` 内部）。
   - TTL：给暂停态设过期（如 30 分钟），避免用户不确认导致泄漏（见 §8 举一反三）。
3. 在 `AguiRequestProcessor` builder 注入 `RedisSession` 替换 `InMemorySession`。
4. **解耦 memoryActive**：把"暂停态的保存/加载"与"长期记忆的 loadFrom/saveTo"**分离**。当前 `AguiRequestProcessor:104-133,147-151` 把两者都门控在 `memoryActive`。改为：
   - 长期记忆：维持原 `memoryActive` 语义。
   - **暂停态**：只要本轮发生了 `stopAgent`（待确认），**无条件保存**；resume 时无条件加载。可在 `AgentContext`/`AgentMetadataStore` 里加一个 `pendingConfirmation` 标记驱动。

**验证**：
- 单测：保存后从"另一个 `AguiRequestProcessor` 实例 + 新 Redis 连接"加载，能恢复 pending。
- 集成：起两个 runtime 实例（或模拟），run 落 A、resume 落 B，确认能恢复。

### 6.2 后端 — 暂停时推送待确认事件
**问题**：`IConfirmationHook.onEvent`（`IConfirmationHook.java:32-67`）只 `stopAgent()`，不告诉前端"哪些工具要确认"；前端只能在 `RUN_FINISHED` 后全标记（Bug1/MCP 假象的源头）。

**目标**：暂停时，把待确认工具（`toolUseId`/`name`/`input`/`source`）通过 AG-UI 推送一个**新事件** `TOOL_CONFIRM_REQUIRED`。

**步骤**：
1. `IConfirmationHook` 在收集到 `toolsNeedConfirm`（已含 id/name/input/dangerous，第47-55行）后，除 `stopAgent()` 外，把这份列表传出去。两种实现路径，择一（看 §6.0 后的可行性）：
   - 路径A：在 `AguiAgentAdapter` 把 agent 暂停（`getGenerateReason()==REASONING_STOP_REQUESTED` 且 `response.hasContentBlocks(ToolUseBlock.class)`）转换成 `TOOL_CONFIRM_REQUIRED` AG-UI 事件。**推荐**（与官方语义一致，hook 不必侵入事件流）。
   - 路径B：hook 内通过事件总线/上下文把 pending 暂存，adapter 读取后发事件。
2. 在 AG-UI 事件模型里新增事件类型 `TOOL_CONFIRM_REQUIRED`（后端 `AguiEvent` + 前端 `ui/src/types/agui.ts`）。字段：`{ toolUseId, name, input, source: 'builtin'|'dynamic'|'mcp' }`。
   - `source` 用于前端区分展示（举一反三：MCP 高危工具可标红）。
3. 为每个待确认工具带上 `source`：普通工具来自 `ToolConfig`，MCP 来自 `McpTool`。可在确认清单里登记时附带来源（见 §6.4 命名空间）。

**验证**：
- 浏览器 + Chrome MCP / DevTools 看 SSE：发消息后应收到 `TOOL_CONFIRM_REQUIRED`，pending 精确等于真正 need_confirm 的工具（不再把无辜 MCP 算进去，除非该 MCP 自己开了确认）。

### 6.3 后端 — resume 接口与恢复逻辑（核心）
**问题**：当前"恢复"靠前端 `agentDoTool` 代执行 + `run()` 重开。

**目标**：新增 `POST /agui/resume/{threadId}`，由后端用官方机制恢复。

**步骤**：
1. 新增 controller（与现有 `AguiMvcController`/`AguiRestController` 同目录，`runner-runtime/.../agui/spring/boot/...`）。请求体：
   ```json
   { "threadId": "...", "decisions": [ {"toolUseId":"...","approved":true}, {"toolUseId":"...","approved":false} ] }
   ```
2. 处理流程：
   1. 校验该 threadId 确有暂停态（从分布式 Session 取；无则 409/幂等返回）。
   2. `loadFrom(session, threadId)` 恢复 agent 暂停态。
   3. 取回 pending `ToolUseBlock` 列表。
   4. 组装恢复输入：
      - **全部 approved** → 让 agent 继续执行 pending（V0 验证出的"继续"方式，如 `stream(空, opts)`）。
      - **存在 rejected** → 为每个被拒工具构造 `ToolResultBlock.of(toolUseId, name, TextBlock("用户已拒绝执行"))`；approved 的工具不喂结果（留给 agent 执行）。
        - ⚠️ 注意 agentscope 协议要求"每个 tool_use 都要有 tool_result"。若混合（部分允许部分拒绝）时框架要求一次性提供全部结果，则需：被拒的喂"已拒绝"，被允许的**也要让 agent 执行并产出结果**——这取决于 §6.0 验证出的恢复模型。三种组合都要在 V0/集成测试覆盖：全允许 / 全拒绝 / 部分。
   5. 用 `TenantUtils.setCurrentTenant(...)` 注入租户（从 Session/AgentMetadataStore 取，不要像 `ToolEndPoint:52` 那样 new 空的）。
   6. 继续流式输出（复用 `AguiAgentAdapter` 的事件转换），SSE 回前端。
3. 拒绝文案：统一用 `"用户已拒绝执行"`（项目决策）。⚠️ 举一反三：若发现模型收到"已拒绝"仍重试该工具，需在系统提示词补一条约束（"被用户拒绝的工具调用，本轮不得重试"）。把这点作为验证项盯住。

**验证**（关键回归）：
- 全允许：两个工具都执行、结果正确、过程只有一轮、无重复调用。
- 全拒绝：两个工具都不执行，agent 用"已拒绝"继续，给出合理回复，**不重新调用**（Bug2 修复确认）。
- 部分（拒 MCP、允许 datetime）：datetime 正常执行、MCP 不执行且不被重调（Bug2/Bug3 同时修复确认）。
- 浏览器实测：复现 §2.1 场景，逐项点按钮验证。

### 6.4 后端 — 确认登记解耦 + per-agent 清单 + 命名空间
**问题**：`ToolkitFactory:134` 的 `&& isMemoryActive`（Bug3/P10）；`NEED_CONFIRM_TOOLS` 静态全局 + 按工具名匹配（P9）。

**步骤**：
1. **解耦**（修 Bug3，必做）：`ToolkitFactory.java:134` 与 `:169` 统一为：
   ```java
   if (Boolean.TRUE.equals(toolConfig.getNeedConfirm())) {
       IConfirmationHook.setNeedConfirmTool(<key>);
   } else {
       IConfirmationHook.removeNeedConfirmTool(<key>);
   }
   ```
   删除 `&& isMemoryActive`。
2. **per-agent + 命名空间**（修 P9）：`IConfirmationHook.NEED_CONFIRM_TOOLS`（`IConfirmationHook.java:29`）从静态全局 `List<String>` 改为**按 agentId/threadId 维度**的结构（如 `Map<agentKey, Set<toolKey>>`），避免多 agent/多租户串味与并发覆盖。
   - 工具 key 设计：普通工具用 `toolId`（`DynamicAgentTool.getName()==toolConfig.getToolId()`，`DynamicAgentTool.java:29-30`）；**MCP 工具用 `mcp:{serverId}:{toolName}` 防重名**。但注意：`IConfirmationHook.onEvent` 是按 `tool.getName()`（即 ToolUseBlock 的 name）匹配的——
     - 若 MCP 工具在 toolkit 里的注册名是原生 `toolSchema.name()`（`LazyMcpAgentTool.java:50`），那 onEvent 拿到的 name 也是原生名，无法直接匹配带前缀的 key。**两种解法择一**：(a) 确认清单同时保存"原生名→是否确认 + 来源"，匹配用原生名但 value 带 serverId 辨识；(b) 给 MCP 工具的注册名加命名空间前缀（会影响 LLM 看到的工具名，需评估 prompt 影响）。**推荐 (a)**，改动小。
3. ⚠️ 举一反三：确认 hook 的清单生命周期要跟随 agent 实例/会话；resume 恢复时清单也要在场（确保第二次推理仍能拦截 need_confirm 工具）。这点与 §6.1 暂停态恢复一起测。

**验证**：
- 关掉记忆功能的会话里，need_confirm 工具**仍然弹确认**（修复"未开记忆确认全失效"的更广漏洞）。
- 两个不同 agent 同时跑，确认清单互不影响。

### 6.5 前端 — 逐工具确认 UI + resume 调用
**问题**：`onRunFinished` 全标记（`useChatStream.ts:200`）；`ToolCallItem` 代执行/塞文本（`ToolCallItem.vue:23-82`）；`sendToolContent` 重开一轮（`useChatStream.ts:233`）。

**步骤**：
1. 新增 AG-UI 事件 `TOOL_CONFIRM_REQUIRED` 的接收处理（`ui/src/composables/chat/useChatStream.ts` 的 handlers；事件类型加到 `ui/src/types/agui.ts`，参考既有 `REASONING_MESSAGE_*` 的定义第145-160行）。收到后**按 `toolUseId` 建立 pending 列表**，每项独立 `{toolUseId, name, input, source, decision: 'pending'|'approved'|'rejected'}`。
2. 删除 `onRunFinished` 里 `forEach(item => item.needConfirm = true)` 的全标记（`useChatStream.ts:197-201`）。
3. `ToolCallItem.vue`：「允许/禁止」改为**只记录该工具决策**（更新 pending 项的 decision），**不再**调 `agentDoTool`、不再 emit 执行结果。删除 `handleConfirm` 的 `agentDoTool` 调用（第38行）与 `handleCancel` 的塞文本逻辑（第64-82行）。
4. 新增"决策完成"判定：当 pending 列表里所有项都已 approved/rejected → 调 `POST /agui/resume`（带 `decisions`），并切回流式接收（复用现有 SSE 处理）。
5. 删除/废弃 `sendToolContent` 的"`client.messages=单个` + `run()`"老路径（`useChatStream.ts:233-266`），`Chat/index.vue:335` 的 `handelToolContent` 改为走 resume。
6. ⚠️ 举一反三（前端体验）：
   - 一个工具点了决策后应禁用其按钮、显示状态；其它工具仍可独立操作。
   - 可加"全部允许/全部拒绝"快捷键。
   - resume 进行中要有 loading，防重复提交（幂等）。
   - 流式中途用户刷新页面：需能基于会话/暂停态重建确认 UI（与 §6.1 暂停态配合）。

**验证**：复现 §2.1，逐工具独立点击，互不影响（Bug1 修复）；拒绝项不执行不重调（Bug2/3 修复）。

### 6.6 MCP 工具接入确认
**问题**：`McpTool` 无 `need_confirm`；`McpClientFactory.getLazyMcpTools` 注册时无确认登记。

**步骤**：
1. **数据库**：`mcp_tool` 加列 `need_confirm tinyint(1) NOT NULL DEFAULT 0`。
2. **实体/VO**：`common/.../entity/McpTool.java` 加 `private Boolean needConfirm;`；对应 VO（`McpToolVO`）加字段供前端读写。
3. **schema 刷新保留**：MCP 同步工具目录时不要覆盖用户设置。参考 `biz-mcp/.../impl/McpToolServiceImpl.java:112`（现有 `enabled` 的保留写法），给 `need_confirm` 同样的"已存在则保留"逻辑。
4. **注册登记**：`engine/.../mcp/McpClientFactory.java` 的 `getLazyMcpTools`（第86-135行）在 `runtimeTools.forEach`（第123行）里，按 `tool.getNeedConfirm()` 调 `IConfirmationHook.setNeedConfirmTool(...)`（key/来源见 §6.4）：
   ```java
   runtimeTools.forEach(tool -> {
       McpSchema.Tool toolSchema = parseToolSchema(tool);
       if (toolSchema == null) return;
       if (Boolean.TRUE.equals(tool.getNeedConfirm())) {
           IConfirmationHook.setNeedConfirmTool(/* key + source=mcp */);
       } else {
           IConfirmationHook.removeNeedConfirmTool(/* key */);
       }
       result.add(new LazyMcpAgentTool(degradeContext, toolSchema, ...));
   });
   ```
5. **后端接口**：`runner-console/.../mcp/McpServerController.java` 加"设置 MCP 工具是否确认"接口；`biz-mcp/.../McpToolServiceImpl` 加 `updateNeedConfirm`（照搬 `updateGlobalEnabled` 第136行的批量更新写法）。前端 `ui/src/api/mcp.ts` 加 `updateToolsNeedConfirm`（照搬 `updateToolsGlobalEnabled` 第68行）。
6. **前端开关**：`ui/src/components/mcp/McpToolGovernance.vue`（MCP 工具治理弹窗）给每个工具加"需要确认"`ASwitch`（照搬现有 enabled 开关的 `toolEnabledChange`→父组件→API 链路）。
7. **确认后执行**：✅ 重构后由 agent 自己执行 MCP 工具（resume→恢复→agent 调 `LazyMcpAgentTool`），**天然带租户上下文**（`LazyMcpAgentTool.callAsync:71` 从 AgentContext 取租户）。→ **不需要改 `ToolEndPoint.doTool`，P7/P8 自动消失。**

**验证**：
- 给某个 MCP 工具开 `need_confirm`，单独调用它 → 弹确认；允许 → agent 执行成功（连得上 MCP，租户正确）；拒绝 → 不执行、不重调。
- 不开 `need_confirm` 的 MCP 工具，单独调用 → 不弹（除非同轮有别的 need_confirm 工具，但那已是"精确事件"而非"全标记"，所以**不会再连累**）。

### 6.7 清理与收尾
1. 废弃前端代执行链路：`ui/src/api/agent.ts:80 agentDoTool` 与后端 `ToolEndPoint.doTool`（`runner-runtime/.../endpoint/ToolEndPoint.java`）——确认无其它调用方后删除或标注 `@Deprecated`。
2. P6：评估 `ToolExecutor.applyRetry`（`ToolExecutor.java:379`）对"需确认工具"是否应禁用重试（避免拒绝/失败被重试放大）。至少在文档/日志层面明确其行为。
3. 旧的 `onRunFinished` 全标记、`sendToolContent` 老路径彻底移除，避免两套机制并存。

---

## 7. 验证总清单（回归用）

> 每完成一个阶段就跑对应验证；全部完成后整体回归。

### 7.1 单元/集成
- [ ] V0：stream 暂停→Session 保存→加载→继续/喂结果（§6.0）。
- [ ] 分布式 Session：跨实例恢复 pending（§6.1）。
- [ ] resume：全允许 / 全拒绝 / 部分，三种组合（§6.3）。
- [ ] 解耦：关闭记忆的会话里 need_confirm 仍拦截（§6.4）。
- [ ] 多 agent 并发：确认清单互不串（§6.4）。
- [ ] MCP：开/不开 need_confirm 的行为（§6.6）。

### 7.2 浏览器端到端（复现 §2.1 场景）
> 可用 Chrome DevTools 或自动化看 SSE 与 DOM。
- [ ] 发"现在是什么时候，然后泉州今天天气怎么样" → 只有真正 need_confirm 的工具弹确认（修 MCP 假象）。
- [ ] 点一个工具按钮 → **只有该工具**状态变化，另一个仍待决策（修 Bug1）。
- [ ] 拒绝某工具 → 该工具不执行、agent 不重新调用它（修 Bug2）。
- [ ] 拒绝 A、不碰 B → B 不会被裸跑执行（修 Bug3）。
- [ ] 给 MCP 工具开 need_confirm 后单独调用 → 弹确认、允许后真执行（连得上 MCP）。
- [ ] 关闭"记忆"功能的会话 → need_confirm 工具仍弹确认（修更广漏洞）。

### 7.3 数据/日志核对
- [ ] `mcp_tool.need_confirm` 正确持久化、schema 刷新不丢。
- [ ] `runner-runtime` 日志无 `AgentContext`/租户丢失告警；无重复工具调用。

---

## 8. 风险与"举一反三"（务必通读）

1. **流式恢复可行性（最高风险）**：若 §6.0 验证 stream 无法从暂停点恢复，整套方案要改走 `interrupt()/handleInterrupt()` 或阻塞 `call()` 包装。**先验证再写**。
2. **分布式状态一致性**：
   - Session 序列化：确认 AgentScope 的状态对象能被 Redis/MySQL 持久化（必要时用其官方 state 导出/导入 API，而非直接 JSON 反射）。
   - 暂停态 TTL：用户长时间不确认要能过期清理，避免 Redis 膨胀与"僵尸暂停"。
   - resume 幂等：用户重复点、网络重试 → 同一 threadId 的 resume 要幂等（已恢复则忽略/返回当前态）。
   - 路由：若不换共享 Session，可考虑网关按 threadId 做 sticky 路由；但既然已决定 Redis/MySQL，优先共享存储。
3. **协议完整性**：一轮多工具时，"部分允许部分拒绝"必须保证每个 `tool_use` 最终都有 `tool_result`（被拒的喂"已拒绝"，被允许的由 agent 执行产出）。三种组合都要测。
4. **拒绝后模型仍重试**：若模型无视"用户已拒绝执行"继续调同一工具，需系统提示词补约束。盯住此回归项。
5. **确认清单生命周期**：per-agent 清单要在 resume 的"第二段推理"中仍然有效，否则第二段又会出现"确认失效"。
6. **多端/刷新**：流式中刷新页面，前端要能据会话/暂停态重建确认 UI。
7. **MCP 工具重名**：不同 MCP server 同名工具（如都叫 `search`）——确认匹配必须带 serverId 维度辨识（§6.4）。
8. **行动后暂停（PostActingEvent）**：本次只做"推理后暂停"（执行前确认）。若将来要"执行后确认结果再继续"，官方 `PostActingEvent.stopAgent()` 同理可扩展，架构应预留。
9. **与 Plan/Skill/子 Agent 的交互**：确认机制不应破坏 PlanNotebook、技能、Agent-as-Tool 的流程，回归时覆盖一个带子 agent/技能的用例。
10. **时区小问题（顺带）**：复现中发现工具返回时间与最终回复时间差 8 小时（容器 UTC vs 本地）。非本次重点，但建议核对 `runner-runtime` 容器 `TZ` 与时间工具实现。

---

## 9. 附录

### 9.1 关键文件与行号速查（撰写时快照，执行前 grep 复核）
**后端 / engine**
- `engine/src/main/java/com/hxh/apboa/engine/hook/builtins/IConfirmationHook.java`：确认 hook。`NEED_CONFIRM_TOOLS`:29；`onEvent`:32-67；`stopAgent`:62；`setNeedConfirmTool`:81。
- `engine/src/main/java/com/hxh/apboa/engine/tool/ToolkitFactory.java`：工具集构建。`getToolkit(definition)`:75；MCP 注册:93-94；`getToolkit(toolIds)`:108；`isMemoryActive`:120；**`needConfirm && isMemoryActive`:134**；`registerTools`:150-176。
- `engine/src/main/java/com/hxh/apboa/engine/agui/AgentContext.java`：ThreadLocal:23；`getIfExists`:104；`memoryActive`:27,44。
- `engine/src/main/java/com/hxh/apboa/engine/mcp/McpClientFactory.java`：`getLazyMcpTools`:86-135（forEach:123）。
- `engine/src/main/java/com/hxh/apboa/engine/mcp/LazyMcpAgentTool.java`：`getName`=toolSchema.name:50；`callAsync` 用租户:71-83。
- `engine/src/main/java/com/hxh/apboa/engine/tool/dynamices/DynamicAgentTool.java`：`getName`=toolId:29-30；`callAsync`:83-109。

**后端 / runner-runtime**
- `runner-runtime/src/main/java/io/agentscope/core/agui/processor/AguiRequestProcessor.java`：`process`:64；`InMemorySession`:42；`loadFrom`:110；`saveTo`:151；`memoryActive`:90。
- `runner-runtime/src/main/java/io/agentscope/core/agui/adapter/AguiAgentAdapter.java`：`agent.stream`:106；REASONING→AG-UI:136,176；`enableReasoning`:55。
- `runner-runtime/src/main/java/io/agentscope/core/tool/ToolExecutor.java`：`applyRetry`:379-411。
- `runner-runtime/src/main/java/com/hxh/apboa/runtime/endpoint/ToolEndPoint.java`：`doTool`:35；查 tool_config:36；空 AgentContext:52。（重构后废弃）

**后端 / 实体 / biz**
- `common/src/main/java/com/hxh/apboa/common/entity/ToolConfig.java`：`needConfirm`:52。
- `common/src/main/java/com/hxh/apboa/common/entity/McpTool.java`：**需加 `needConfirm`**。
- `biz/biz-mcp/src/main/java/com/hxh/apboa/mcp/service/impl/McpToolServiceImpl.java`：`enabled` 刷新保留:112；`updateGlobalEnabled`:136,152。
- `runner-console/src/main/java/com/hxh/apboa/console/mcp/McpServerController.java`：MCP 治理接口。

**前端**
- `ui/src/composables/chat/useChatStream.ts`：`getForwardedProps`:37；`onRunStarted` 清空:61-65；`onToolCallStart`:123；**`onRunFinished` 全标记:197-201**；`sendToolContent`:233-266。
- `ui/src/components/chat/ToolCallItem.vue`：`handleConfirm`(代执行):23-61；`handleCancel`(塞文本):64-82；按钮:96-107。
- `ui/src/components/chat/MessageList.vue`：`v-for ToolCallItem`:41-50。
- `ui/src/views/Chat/index.vue`：`memoryActive` computed:52；`handelToolContent`:335。
- `ui/src/api/agent.ts`：`agentDoTool`:80（废弃）。
- `ui/src/types/agui.ts`：AG-UI 事件类型（参考 `REASONING_MESSAGE_*`:145-160 新增 `TOOL_CONFIRM_REQUIRED`）。
- `ui/src/components/mcp/McpToolGovernance.vue`：MCP 工具治理（加确认开关）。
- `ui/src/api/mcp.ts`：`updateToolsGlobalEnabled`:68（照此加 `updateToolsNeedConfirm`）。

### 9.2 自查命令速记
```bash
# 确认清单的所有登记点（应只有 ToolkitFactory + 新增的 McpClientFactory）
grep -rn "setNeedConfirmTool\|removeNeedConfirmTool" --include=*.java .
# isMemoryActive 用法（解耦时核对）
grep -rn "isMemoryActive\|memoryActive" --include=*.java .
# MCP 工具注册（确认 forEach 落点）
grep -n "getLazyMcpTools\|runtimeTools\|LazyMcpAgentTool" engine/src/main/java/com/hxh/apboa/engine/mcp/McpClientFactory.java
# 反编译 agentscope（看 Session / 恢复 API）
JAR=~/.m2/repository/io/agentscope/agentscope-core/1.0.12/agentscope-core-1.0.12.jar
javap -cp "$JAR" io.agentscope.core.agent.AgentBase | grep -iE "call|stream|interrupt"
javap -cp "$JAR" io.agentscope.core.session.Session
```

### 9.3 AgentScope 官方 HITL 文档
`https://raw.githubusercontent.com/agentscope-ai/agentscope-java/master/docs/v1/zh/docs/task/hitl.md`

---

## 10. 给执行者的提交建议
- 先开分支（不要直接动 master）。
- 严格按 §6 顺序，每阶段独立提交 + 自测，commit message 标注对应 §小节与修复的 bug 编号。
- §6.0 V0 不通过就停下上报，不要硬写后续生产代码。
- 每完成一个 bug 修复，对照 §7.2 在浏览器实测一遍再继续。
- 全程中文沟通；技术标识符保留英文。

---

## 11. 实现进度（截至 2026-06-30）

> 分支 `psh`。构建/测试命令必须 `JAVA_HOME=/opt/homebrew/opt/openjdk@21/...`（见顶部环境注意）。

**✅ 全部完成（A+B+C + bug 修复，本次会话端到端验证 + 已提交）：**

A 组（后端 resume 机制）：
- `6088fb9` V0 验证 / `1a4d4d5` §6.1 暂停态持久化 / `3cc4c29` runWithMessages / `6e96e0b` §6.3 resume 逻辑
- `602a58d` §6.2/§6.3 接通 HTTP 入口：AguiRestController `POST /agui/resume/{threadId}`；AguiMvcController.handleResume 在 submit 线程 `AgentContext.init`（修最大风险点的异步租户传递），抽取 `subscribeAndTrack` 共用 SSE 管道；AguiAgentAdapter 在 AGENT_RESULT(REASONING_STOP_REQUESTED) 推 `TOOL_CONFIRM_REQUIRED`；**resume 用 agentCode（非数字 agent_id）解析 agent**（端到端逮到的 Agent not found）；拒绝改喂 error 语义文案。

B 组（解耦 + 前端切换）：
- `cf5404c` §6.4 解耦 ToolkitFactory 的 `&& isMemoryActive`（修 Bug3：不开记忆裸跑）。per-agent 清单经分析推迟（普通工具 toolId 全局唯一不串扰，重名风险只在 MCP）。
- `86c5eb8` §6.5 前端：onCustom 接 `TOOL_CONFIRM_REQUIRED` 精确标记、逐工具 `decideConfirm`（决策齐调 `/agui/resume`）、删 `onRunFinished` 全标记与 `sendToolContent` 重开一轮、ToolCallItem 删 agentDoTool 代执行。

C 组（MCP 接入确认）：
- `e4904a2` §6.6 核心：mcp_tool 加 `need_confirm` 列、McpTool 实体、McpToolServiceImpl schema 保留、McpClientFactory 注册登记（key 用原生名 `toolSchema.name()`）。确认后 agent 自 resume 执行 MCP，天然带租户，P7/P8 自动消失。
- `4223226` §6.6 UI 开关：照搬 `global-enabled` 链路加 `updateNeedConfirm`（DTO/Service/Controller/前端 api/McpToolGovernance 开关）。

bug 修复：
- `9ff5d56` 拒绝回填文案强化：禁止模型编造被拒工具的数据（现象：拒绝天气工具后模型凭常识编造泛泛天气；拒绝逻辑本身正确、工具未执行、MCP 零连接，问题是 hallucination）。

**端到端验证（curl + 浏览器双重，本地 runtime + 真实 Ollama + 真实 MySQL，详见 [[local-hitl-test-setup]]）：**
resume 全允许/全拒绝、error 文案不重调、解耦后 memoryActive=false 也暂停、前端逐工具确认 UI（允许执行/禁止不重调/连续多轮）、MCP maps_weather 确认暂停 + agent 自执行返回真实天气、拒绝后模型如实告知不编造 —— 全部通过。

**DB 变更（需在各环境手动执行 / 写入 migration）：**
```sql
ALTER TABLE mcp_tool ADD COLUMN need_confirm tinyint(1) NOT NULL DEFAULT 0 AFTER enabled;
```

**⚠️ 本地测试临时改动，部署/还原前需恢复：**
- `model_provider.base_url`：本地测试临时改 `http://127.0.0.1:11434/v1`，容器环境需还原 `http://host.docker.internal:11434/v1`。
- `ui/vite.config.ts`：`/api` proxy 临时指向 `127.0.0.1:80`（本地登录走 nginx），还原为 `127.0.0.1:3060`（或 `git checkout ui/vite.config.ts`）。
- 本地起的 runtime(3061)/dev(3030)、误生成的 `ui/package-lock.json`（项目用 pnpm）测试完清理。

**遗留 / 可选增强：**
- 阶段2 MCP need_confirm UI 开关已编译通过（照搬验证过的 enabled 链路），UI 运行时点选可补验。
- IConfirmationHook 清单仍为全局静态（普通工具 toolId 唯一安全）；多 MCP server 同名工具的 serverId 辨识（P9）未做，需要时再加。
- `ToolEndPoint.doTool` / `agentDoTool` 旧代执行链路已废弃（resume 后不再用），可后续删除（§6.7）。

---

## 12. 刷新/重进恢复确认 UI（2026-07-01，落实 §8.6「多端/刷新」）

> 分支 `psh`。承接 §8.6 风险项「流式中刷新页面，前端要能据会话/暂停态重建确认 UI」。

**问题**：一轮命中两个 need_confirm 工具（datetime + maps_weather）→ 前端出现两个「允许/禁止」→ **刷新页面（或重新进入该会话）确认按钮消失、暂停态卡死无法再点**。

**根因**：
- 后端暂停态**持久**：`AguiRequestProcessor` 在 `adapter.isSuspended()` 时无条件 `saveTo`（MysqlSession）。刷新后暂停态还在。
- 前端确认 UI 是**内存态**：`useChatStream` 的 `pendingConfirms` / `toolCallsInProgress.needConfirm` 由 `onCustom` 收到的 `TOOL_CONFIRM_REQUIRED` 建立，刷新即丢。
- reconnect 回放依赖 `RunTracker`（内存态），且暂停后已 `markCompleted`（`getActiveRuns` 不含该会话），跨重启/实例全丢——**缺「从持久 session 暂停态重建确认 UI」的路径**。

**方案 A（从持久 Session 恢复 pending）**：
1. 后端新增 `GET /agui/pending/{threadId}`（`AguiRestController` → `AguiMvcController.getPendingConfirms` → `AguiRequestProcessor.getPendingConfirms`）：
   - **只读**：`loadFrom` 加载暂停态用于读取，**不** saveTo / delete，暂停态原封不动，后续真正 resume 仍可续跑。
   - 复用 resume 的租户/上下文模式：`session.exists` 快速否定 → `loadResumeContext`（agentCode+租户）→ `setCurrentTenant` → `resolveAgent`（重建 toolkit 才会重新登记 need_confirm 工具，`isNeedConfirm` 才有依据）→ `loadFrom`。controller 层先 `AgentContext.init` 最小上下文（同 handleResume）。
   - **暂停态判据（关键）**：实测 MysqlSession 序列化的 memory JSON **不保留 `generateReason`**，故不能靠 `REASONING_STOP_REQUESTED` 判断；改用**结构判据**——`getMemory().getMessages()` 最后一条为 `ASSISTANT` 且含 `ToolUseBlock`（确认暂停发生在工具执行前，其后不会再有 TOOL 结果消息）。再用 `IConfirmationHook.isNeedConfirm` 过滤（与 `AguiAgentAdapter` 推 `TOOL_CONFIRM_REQUIRED` 同口径，排除被连累的普通/MCP 工具）。
2. 前端恢复：
   - `useChatStream` 把 `onCustom(TOOL_CONFIRM_REQUIRED)` 的逻辑抽成 `restorePending(list)` 并**增强**——刷新场景 `toolCallsInProgress` 已清空，须按 `input` **新建**工具项（否则没有承载「允许/禁止」的项）；实时事件场景工具项已由 `ToolCallStart` 建立，只标记 `needConfirm`。二者共用。
   - `Chat/index.vue` 加 `restoreConfirm(sid)`：调 `getPending` 非空则 `restorePending`。在 `onMounted`（当前会话非运行中）与 `handleSelectSession`（点击非运行中会话）双路径调用——暂停态会话不在 `active-runs`，故独立于 reconnect。`currentSessionId` 刷新后为 null（无持久化），主路径是 `handleSelectSession`。

**改动文件**：后端 `AguiRequestProcessor`（getPendingConfirms）、`AguiMvcController`（getPendingConfirms）、`AguiRestController`（/pending 端点）；前端 `api/agui/request.ts`（getPendingURL）、`api/agui/index.ts`（getPending）、`composables/chat/useChatStream.ts`（restorePending）、`views/Chat/index.vue`（restoreConfirm + 双路径）。

**验证（本地 runtime + 真实 Ollama + 真实 MySQL）**：
- curl：干净会话 run「现在几点+泉州天气」→ 暂停推 `TOOL_CONFIRM_REQUIRED`(2 工具) → **GET /pending 返回逐字段一致的 pending**（toolUseId/name/input）→ 部分 resume（datetime 允许/weather 拒绝）续跑：datetime 执行、weather 喂拒绝不重调、模型回复、RUN_FINISHED → resume 后再 GET /pending 返回空（暂停态已消费）。序列 run→pending→resume 中 pending 只读不干扰 resume，正是真实刷新场景。
- 浏览器：进入暂停会话 → 两个「允许/禁止」按钮重建（weather 有「展开参数」= input 非空、datetime 无 = input 空）→ 点 datetime「允许」仅该工具收起、weather 独立保留（Bug1）→ 点 weather「禁止」决策齐自动 resume → 按钮消失、工具折叠、模型文本回复。

**环境坑（本地测试必踩，容器/生产无此问题）**：agent **重建**（`resolveAgent` 缓存未命中，如 runtime 重启后）会走 `ReActAgentHelper → StudioService.init → StudioManagerUtils.initOnce`（名为 once 实则每次 `block()` 连 Studio）。DB `studio_config.url` 生产是 `http://host.docker.internal:3000`（宿主机不可达），本地测试须临时改 `http://127.0.0.1:3000`（同 `model_provider.base_url` 坑）。**真实「刷新页面」场景 runtime 不重启、agent 在 dev 缓存，pending 命中缓存不触发 Studio。** 部署/还原前需改回。
