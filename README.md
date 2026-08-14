<h1 align="center">Apboa Next</h1>

<p align="center">
  <strong>企业级 AI 智能体平台 — 从定义到生产</strong>
</p>

<p align="center">
  基于 ReAct 范式的多租户智能体构建与运行平台<br/>
  <code>MCP 协议</code> · <code>A2A 协作</code> · <code>多向量存储</code> · <code>一键部署</code>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white" alt="Java 21"/>
  <img src="https://img.shields.io/badge/Spring%20Boot-3.4-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot 3.4"/>
  <img src="https://img.shields.io/badge/AgentScope-1.0-FF6B35" alt="AgentScope 1.0"/>
  <img src="https://img.shields.io/badge/Vue-3.5-4FC08D?logo=vuedotjs&logoColor=white" alt="Vue 3.5"/>
  <img src="https://img.shields.io/badge/Docker-Ready-2496ED?logo=docker&logoColor=white" alt="Docker Ready"/>
  <img src="https://img.shields.io/badge/License-MIT-green" alt="MIT License"/>
</p>

> **仓库镜像**：本项目的官方主站为 [https://gitee.com/studioustiger/apboa-next](https://gitee.com/studioustiger/apboa-next)，最新代码将优先发布在主站上，并定期同步至 [https://github.com/huxuehao/apboa-next](https://github.com/huxuehao/apboa-next)。推荐通过 Gitee 主站获取最新版本。

Apboa Next 是基于ReAct理念的智能体开发与管理平台，旨在简化AI智能体的构建流程，帮助用户快速打造专属数字助手。平台整合了敏感词过滤、提示词管理、多模型接入、工具集成、知识库和智能体编排等核心功能，形成一站式解决方案，架构清晰，易于使用。

## 目录

- [快速开始](#快速开始)
- [本地开发](#本地开发)
- [核心特性](#核心特性)
- [系统架构](#系统架构)
- [为什么选择 Apboa](#为什么选择-apboa)
- [能力清单](#能力清单)
- [技术栈](#技术栈)
- [核心页面预览](#核心页面预览)
- [部署指南](#部署指南)
- [项目结构](#项目结构)
- [贡献指南](#贡献指南)
- [交流与赞助](#交流与赞助)
- [开源协议](#开源协议)
- [Workflow 专题](#workflow-可视化工作流引擎)

## 快速开始

前置条件：Docker Engine 20+ & Docker Compose v2+

```bash
git clone https://gitee.com/studious_tiger/apboa-next.git
cd apboa-next/docker
bash start-simple.sh
```

启动完成后访问 `http://localhost`，默认账号 `admin / Admin@123.com`。

> 单机体验版包含全部 5 个服务 + 3 个中间件，约 5 分钟完成初始化。生产部署请参考 [docker-compose-execute.yml](docker/docker-compose-execute.yml)。

## 本地开发

### 环境要求

| 依赖 | 版本 | 说明 |
|------|------|------|
| JDK | 21+ | 后端编译与运行 |
| Maven | 3.8+ | 后端构建 |
| MySQL | 8.0+ | 主数据库，库名 `apboa_next` |
| Redis | 7+ | 缓存与分布式锁 |
| Node.js | 20.19+ 或 22.12+ | 前端运行 |
| pnpm | 9+ | 前端包管理 |

### 1. 初始化中间件

确保 MySQL 和 Redis 已启动。创建数据库并导入初始化脚本：

```bash
# 创建数据库
mysql -u root -p -e "CREATE DATABASE apboa_next DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"

# 导入表结构与初始数据（仅首次部署需要）
mysql -u root -p apboa_next < sql/db_init.sql
```

> 首次初始化后，后续表结构变更全部由 Flyway 自动管理：`runner-console` 启动时会按序执行 `db/migration` 目录下的增量脚本（`V2__xxx.sql` 起，见 [runner-console/src/main/resources/db/migration](runner-console/src/main/resources/db/migration)），无需手工比对或执行 SQL。

### 2. 配置后端

各 Runner 模块使用 `application-dev.yml` 作为本地开发配置，部分模块提供了 `application-dev.sample.yml` 模板，复制并按需修改即可。

```bash
# runner-console
cp runner-console/src/main/resources/application-dev.sample.yml runner-console/src/main/resources/application-dev.yml

# runner-runtime
cp runner-runtime/src/main/resources/application-dev.sample.yml runner-runtime/src/main/resources/application-dev.yml
```

编辑 `application-dev.yml`，填入本地的 MySQL、Redis 连接信息（地址、端口、密码）。

### 3. 依次启动后端服务

按以下顺序在 IDE 或终端中启动三个 Java 服务：

| 顺序 | 模块 | 启动类 | 端口 |
|------|------|--------|------|
| 1 | runner-console | `ConsoleApplication` | 3060 |
| 2 | runner-runtime | `RuntimeApplication` | 3061 |
| 3 | runner-websocket | `WebsocketApplication` | 3064 |

> **说明：** `runner-file`（技能文件同步服务）仅在分布式 Docker 部署时需要，本地开发无需启动。

### 4. 启动前端

```bash
cd ui
pnpm install
pnpm dev
```

前端开发服务器启动后访问 `http://localhost:3030`，Vite 已内置代理配置：

- `/api` → `http://127.0.0.1:3060`（Console）
- `/api/runtime/` → `http://127.0.0.1:3061`（Runtime）
- `/api/ws/` → `http://127.0.0.1:3064`（WebSocket）

默认账号 `admin / Admin@123.com`。

---

## 核心特性

### 🧠 ReAct 智能体引擎

ReAct（Reasoning + Acting）循环驱动，内置 PlanNotebook 任务规划、AutoContext 记忆压缩、长期记忆（Mem0 / ReMe / Bailian），支持树状消息分支与多 Session 并行。

### 🔌 MCP 协议

HTTP / SSE / STDIO 三协议 MCP 客户端。LazyMcpAgentTool 实现懒加载——注册阶段仅绑定 Schema，首次调用时建立连接。连续失败达阈值自动降级。

### 🤖 多模型适配器

统一模型工厂适配 OpenAI · DashScope · Anthropic · Gemini · Ollama · OrcaRouter 六大供应商。Agent 级参数覆盖（temperature / topP / topK / seed），无需改代码切换供应商。

### 📚 知识库与 RAG

四种知识库后端（百炼 / Dify / RagFlow / 本地向量库），五种向量存储（PgVector / Milvus / Elasticsearch / Qdrant / Weaviate）。内置文档解析 → 分块 → 嵌入 → 存储全流水线。

### 🛡️ 安全沙箱

Shell 命令通过独立 Proxy 进程执行，Docker 容器 `cap_drop: ALL` + `read_only` + `pids_limit` 最小权限运行。Python / Node.js / Shell / HTML 四语言安全扫描引擎。

### 🏢 多租户 SaaS

完整多租户体系：租户发现、申请、审批流程，双层 RBAC（平台 3 级 + 租户 4 级）。MyBatis-Plus 租户拦截器自动注入，48 张业务表数据完全隔离。

### 🔄 Workflow 可视化工作流引擎

**30+ 开箱即用节点，让复杂业务流程像搭积木一样简单。**

Apboa Workflow 是平台的核心能力之一，提供企业级可视化工作流编排引擎。通过拖拽式操作，用户可以快速构建包含 AI 智能体调用、数据库操作、外部 API 集成、消息队列推送等复杂业务流程，无需编写代码。

**核心亮点：**

| 特性 | 说明 |
|------|------|
| **30+ 节点类型** | 基础（START/END）、逻辑（IF_ELSE/LOOP/ITERATE/MATCH）、数据（DB CRUD）、缓存（Cache CRUD）、消息（MQ）、集成（AGENT/TOOL/MCP/HTTP/CODE）、转换（String/Split/Template/Serialize）、列表（Filter/Sort）、变量（Agg） |
| **可视化编排** | 基于 Vue Flow 的画布引擎，拖拽节点、自动连线、对齐辅助线、小地图导航 |
| **灵活数据绑定** | 四种输入来源：常量、变量、节点输出、Groovy 表达式，BFS 算法自动发现上游节点 |
| **实时调试** | 执行轨迹可视化、节点级耗时统计、失败节点自动高亮、错误即时定位 |
| **模板引擎** | 支持 String / Velocity / JSON 三种模板格式化器 |
| **子工作流** | LOOP 节点支持内嵌子工作流，实现复杂循环逻辑 |
| **桥接解耦** | workflow 模块定义接口，engine 模块提供实现，独立演进 |

**典型应用场景：**

- **智能客服**：接收消息 → AI 分析意图 → 条件分支 → 知识库查询 → 生成回答
- **数据处理**：数据源 → 查询 → 迭代处理 → 格式化 → 缓存 → 消息通知
- **多系统集成**：事件触发 → API 调用 → AI 决策 → MCP 执行 → 数据更新

> 详细技术文档请参考：[Apboa Workflow 技术文章](ui/src/views/Workflow/apboa-workflow-技术文章.md)

### ⏰ 自动化定时任务

**为智能体和工作流设置定时执行计划，实现无人值守的自动化运营。**

Apboa 自动化模块将定时调度与 AI 能力深度整合，让用户可以为任意智能体或工作流绑定 Cron 定时策略，实现周期性自动执行。无论是每日报表生成、定时数据同步，还是周期性内容推送，只需简单配置即可完成。

**核心亮点：**

| 特性 | 说明 |
|------|------|
| **双目标类型** | 支持智能体和工作流两种执行目标，统一管理入口 |
| **Cron 可视化配置** | 内置 CronBuilder 组件，提供预设模板与无极调节，无需手写 cron 表达式 |
| **手动触发** | 支持临时手动执行任务，验证配置或应急处理 |
| **启用/禁用开关** | 一键控制任务启停，无需删除重建 |
| **执行记录** | 完整记录每次执行结果：工作流展示节点级日志，智能体展示完整对话历史 |
| **集群协调** | Redis 分布式锁 + 节点心跳机制，多节点部署时保证任务单次执行，负载均衡分配 |

**典型应用场景：**

- **智能日报**：每天 9:00，智能体自动拉取数据生成日报 → 推送到工作空间
- **定时监控**：每 5 分钟，工作流查询数据库 → 条件判断 → 异常时发送消息
- **周期性批量处理**：工作日每小时，智能体批量处理待办项 → 记录执行结果

> 调度引擎基于 Quartz + Redis 分布式锁，后端由 [AgentScheduler](scheduler/src/main/java/com/hxh/apboa/scheduler/scheduler/AgentScheduler.java) 和 [WorkflowScheduler](scheduler/src/main/java/com/hxh/apboa/scheduler/scheduler/WorkflowScheduler.java) 实现。

### 🌐 API 服务网关

**将已发布的工作流一键暴露为标准 HTTP API，对外提供服务。**

基于 Vert.x 异步非阻塞网关，用户可创建监听独立端口的网关应用，并将已发布的工作流注册为应用下的 HTTP API。请求参数自动转换为工作流输入，同步返回执行结果，配合统一鉴权与访问控制，快速完成 AI 能力的服务化输出。

**核心亮点：**

| 特性 | 说明 |
|------|------|
| **独立端口应用** | 一个应用对应一个监听端口，应用间完全隔离，支持 CORS 与请求体大小限制 |
| **动态路由** | 应用与 API 上下线实时挂载/卸载路由，无需重启服务 |
| **统一鉴权** | 复用平台凭证体系，携带平台登录 Token 或已注册 SK 即可调用 |
| **IP 访问白名单** | 应用级白名单，基于 TCP 层真实来源判定防伪造，IPv4 / IPv6 归一化匹配，留空不限制 |
| **限流控制** | API 级限流策略，超限请求快速失败 |
| **访问日志** | 全链路访问日志异步落库，支持按应用 / API 检索 |
| **多节点同步** | 配置变更经 Redis 广播至所有网关节点自动重新部署，集群状态一致 |

> 数据面由 [GatewayLifecycleManager](gateway/src/main/java/com/hxh/apboa/gateway/core/GatewayLifecycleManager.java) 管理应用生命周期。

### 📊 千人千面工作台

**拖拽搭建专属数据看板，让每个人的首页都不一样。**

Apboa 工作台是面向终端用户的可视化数据门户。通过拖拽面板、绑定数据集，用户几分钟即可搭出属于自己的数据看板——关心的指标、顺手的入口、实时的图表各就各位。设计器提供细粒度栅格、自动排版、历史版本与未保存离开拦截，兼具专业工具的手感与克制的视觉美学。

**核心亮点：**

| 特性 | 说明 |
|------|------|
| **20+ 面板类型** | 指标（数据卡片 / KPI 趋势 / 进度环 / 数字翻牌）、图表（柱状 / 折线 / 面积 / 散点 / 饼图 / 雷达）、表格（数据表格 / 滚动轮播表）、内容（文本 / Markdown / 图片 / 网页 / 时钟）、快捷方式 |
| **双通道数据集** | SQL 数据集（参数化 + 缓存 + 限流）与 HTTP 数据集（GET + dataPath 映射 + 同源带 token），统一策略执行 |
| **数据集归属与共享** | 数据集归创建人私有，可选租户内共享；非创建人仅可使用与运行预览，不可查看细节或改删 |
| **租户隔离强制注入** | 平台自动为数据集 SQL 追加租户过滤，敏感系统表黑名单 + 可查询白名单 + 越权防护 + 审计日志 |
| **面板私有筛选器** | 日期 / 月份 / 年份 / 下拉 / 文本，可拖拽排序、四角停靠，同一数据集各面板各看切面 |
| **可视化设计器** | 48 列细粒度栅格、防碰撞开关、skyline 自动排版、撤销重做、历史版本回滚、未保存离开拦截 |
| **动态占位与自定义组件** | 文本 / Markdown 支持 `{{ 字段 }}` 占位；portal 目录 Vue 组件自动扫描、props 自动识别，零胶水扩展 |
| **样式覆盖与定时刷新** | 每面板可覆盖背景 / 边框 / 圆角 / 内边距 / 文字样式（含透明度）；定时静默刷新不闪屏 |

**典型应用场景：**

- **运营看板**：绑定 SQL 数据集 → 拖入指标卡与图表 → 私有筛选按日期/状态切片 → 定时刷新
- **接口聚合**：HTTP 数据集对接内部服务 → dataPath 映射为表格 → 与本地数据同屏对比
- **个性首页**：快捷方式导航 + 摸鱼进度条 + 每日一言等自定义组件，打造专属工作起点

> 数据集执行经安全校验与租户改写后执行，详见 [DatasetExecutionService](biz/biz-dashboard/src/main/java/com/hxh/apboa/dashboard/dataset/DatasetExecutionService.java) 与 [TenantPredicateRewriter](biz/biz-dashboard/src/main/java/com/hxh/apboa/dashboard/dataset/guard/TenantPredicateRewriter.java)。

---

## 系统架构

5 服务解耦架构，Runtime 支持弹性扩容：

<img src="image/image-20260617180312763.png" width="100%">

- **Console** — 管理控制台、API 网关、心跳中心（单实例）
- **Runtime** — AI 推理运行时、AG-UI 协议端点（弹性扩容）
- **Proxy** — Shell 命令沙箱执行（随 Runtime 扩容）
- **File** — 技能文件跨节点同步（随 Runtime 扩容）
- **WebSocket** — 实时消息推送、Redis Pub/Sub 集群（单实例）

---

## 为什么选择 Apboa

### 🔗 A2A 跨智能体协作

基于 WellKnown / Nacos 标准 A2A 协议，智能体间可互相发现和调用。支持 Agent-as-Tool 模式，父智能体将子智能体注册为工具，构建多智能体协作网络。

### 📊 VEP + APIP 双协议

**VEP**（Vision Enhancement Protocol）：AI 生成结构化数据卡片与 ECharts 图表（雷达图 / 折线图 / 柱状图 / 饼图），前端原生渲染。
**APIP**（Agent-Platform Interaction Protocol）：AI 生成交互表单、选择器、确认组件，标准化人机交互协议。

### 🔬 脚本安全扫描器

`ScriptSecurityService` 注册机制驱动，已实现 Python / Node.js / Shell / HTML四种语言安全检查，覆盖注入攻击、数据泄露、权限提升等风险类别。

### 🗄️ 五种向量存储，零代码切换

PgVector / Milvus / Elasticsearch / Qdrant / Weaviate——修改 `VECTOR_STORE_TYPE` 环境变量即可切换，`VectorStore` 接口统一抽象。

### 🚀 分布式架构与弹性扩容

后台拆分为 5 个服务：1 个控制台与 3 个运行时（支持无限横向扩展）加 1 个消息服务协同工作，辅以优化后的 SKILL 同步机制与内置服务监控，实现企业级分布式部署与弹性扩容。同时提供更友好的前端多租户交互体验，满足企业级组织架构下的权限隔离需求。

### ⚡ 多 Session 并行与状态持久化

支持多 Session 并行运行，无需等待消息结束即可开启新对话。消息记录迁移至后端存储，运行中的 Session 状态自动保存，强制刷新页面后流式输出仍可继续。

### 📄 文档识别与交互增强

新增的文档识别能力与模型解耦，理论上可扩展支持任意类型的文档内容识别。交互式表单让对话不再局限于纯文本，视觉增强卡片与图表使信息呈现更加直观。

---

## 能力清单

- **ReAct 智能体** — Reasoning + Acting 循环，可配置最大迭代次数、计划规划、用户确认
- **多模型支持** — OpenAI / DashScope / Anthropic / Gemini / Ollama / OrcaRouter，Agent 级参数覆盖
- **MCP 集成** — HTTP / SSE / STDIO 三协议，懒加载 + 运行时降级 + 工具治理
- **工具系统** — 内置工具 + Groovy 动态工具 + Agent-as-Tool + 工具确认机制
- **技能包** — VEP / APIP 内置协议技能 + 用户自定义技能（26 种文件类型）
- **知识库** — 百炼 / Dify / RagFlow / 本地 RAG，GENERIC + AGENTIC 双模式
- **向量存储** — PgVector / Milvus / Elasticsearch / Qdrant / Weaviate
- **短期记忆** — InMemory + AutoContext 自动压缩（可配置 tokenRatio / lastKeep）
- **长期记忆** — Mem0 / ReMe / Bailian 三种后端，异步记录不阻塞主流程
- **计划笔记本** — 任务分解、子任务管理、用户确认、状态持久化
- **Hook 系统** — 内置 Hook + Groovy 动态 Hook，GLOBAL / TENANT 双作用域
- **代码执行** — Shell 沙箱 + 文件读写 + Search/Replace 增量更新 + 工作空间容量管控
- **A2A 协议** — WellKnown / Nacos 双模式，跨智能体发现与调用
- **多租户** — 租户发现 / 申请 / 审批 / RBAC / 数据隔离
- **会话归档** — 消息按月分表归档（`chat_message_yyyyMM`），主表保持轻量
- **分布式锁** — Redis 分布式锁 + Pub/Sub，多实例定时任务不重复执行
- **限流策略** — Nginx 三级限流：API 50r/s · 低频 20r/s · 并发连接 100/IP
- **容器安全** — cap_drop + no-new-privileges + mem_limit + cpus + pids_limit
- **健康监控** — 独立心跳上报 + 双注册表（执行节点 / WebSocket 节点），超时自动清理
- **多 Session 并行** — 多会话并行运行，状态自动保存，刷新页面流式输出不中断
- **文档识别** — 与模型解耦的文档解析能力，可扩展支持任意类型文档内容识别
- **交互式表单（APIP）** — AI 生成交互组件，对话不再局限于纯文本
- **视觉增强（VEP）** — 结构化数据卡片与 ECharts 图表，信息呈现更加直观
- **可视化工作流（Workflow）** — 30+ 节点类型、拖拽编排、实时调试、模板引擎、子工作流、桥接解耦
- **自动化定时任务（Automation）** — Cron 可视化配置、预设模板、启用/禁用开关、手动触发、执行记录追踪
- **集群调度** — Quartz 调度引擎 + Redis 分布式锁 + 执行历史负载均衡 + 节点心跳存活检测
- **API 服务网关（Gateway）** — 工作流一键暴露为 HTTP API，独立端口应用 + 动态路由 + 统一鉴权 + IP 白名单 + 访问日志（尝鲜）
- **千人千面工作台（Dashboard）** — 拖拽式数据看板，20+ 面板 + SQL/HTTP 双通道数据集 + 数据集归属共享 + 租户隔离强制注入 + 自动排版 + 历史版本 + 自定义组件扩展

---

## 技术栈

- **后端** — Java 21 · Spring Boot 3.4.9 · AgentScope 1.0.12 · MyBatis-Plus 3.5.7
- **前端** — Vue 3.5 · Ant Design Vue 4 · Vite 7 · Pinia 3 · Vue Router 5
- **编辑器** — CodeMirror 6（JS / TS / Java / Python / HTML / CSS / JSON / XML / Markdown）
- **可视化** — ECharts 6 · Mermaid 11 · Vue Flow · KaTeX
- **工作流** — Vue Flow 画布引擎 · Groovy 表达式 · Velocity 模板 · 30+ 节点类型
- **数据库** — MySQL 8.0 · Redis 7 · pgvector (PG 16)
- **消息通信** — WebSocket + Redis Pub/Sub 集群
- **任务调度** — Quartz + Redis 分布式锁
- **部署方式** — Docker Compose · Nginx · Multi-stage Build

---

## 核心页面预览

| ![image-20260716222838455](image/image-20260716222838455.png) | ![image-20260716223019231](image/image-20260716223019231.png) |
| ------------------------------------------------------------ | ------------------------------------------------------------ |
|                                                              |                                                              |
| ![工具配置](image/image-20260617181600141.png)               | ![image-20260716223114184](image/image-20260716223114184.png) |
| ![image-20260716223152097](image/image-20260716223152097.png) | ![image-20260716223246390](image/image-20260716223246390.png) |
| ![image-20260716223526060](image/image-20260716223526060.png) | ![image-20260716223602299](image/image-20260716223602299.png) |

| ![智能体编排](image/image-20260617181923101.png) | ![image-20260716223329257](image/image-20260716223329257.png) | ![数据可视化](image/image-20260617182021804.png) |
| ------------------------------------------------ | ------------------------------------------------------------ | ------------------------------------------------ |

| ![image-20260721194023660](image/image-20260721194023660.png) | ![image-20260721194031681](image/image-20260721194031681.png) |
| ------------------------------------------------------------ | ------------------------------------------------------------ |
| ![image-20260721194042584](image/image-20260721194042584.png) | ![image-20260721194052750](image/image-20260721194052750.png) |

| ![image-20260710171008016](image/image-20260710171008016.png) | ![image-20260710171108812](image/image-20260710171108812.png) |
| ---- | ---- |

---

## 部署指南

### 开发 / 评估

```bash
cd docker
bash start-simple.sh        # 单机全量部署，约 5 分钟
```

### 生产环境

```bash
cd docker
bash start-middleware.sh    # 中间件服务（Mysql、Redis、向量库）
bash start-console.sh       # 仅管理控制台
bash start-execute.sh       # 分布式部署，Runtime 可独立扩容
```

> [!NOTE]
> 生产部署建议：Runtime 至少 2 实例，MySQL 、 Redis 与 向量库 使用托管服务。

---

## 项目结构

```
apboa-next/
├── common-base/          # 基础层：枚举、常量、工具类、加密
├── common/               # 公共层：Entity、DTO、VO、Wrapper
├── biz/                  # 业务层（22 个扁平化模块）
│   ├── biz-agent/        #   智能体定义 + 会话管理
│   ├── biz-account/      #   账号 + 租户 + 审批
│   ├── biz-mcp/          #   MCP 服务管理 + 运行时降级
│   ├── biz-knowledge/    #   知识库配置
│   ├── biz-skill/        #   技能包管理
│   ├── biz-tool/         #   工具配置
│   ├── biz-hook/         #   Hook 配置
│   ├── biz-model/        #   模型供应商 + 模型配置
│   ├── biz-prompt/       #   系统提示词模板
│   ├── biz-sensitive/    #   敏感词管理
│   ├── biz-resource/     #   附件 + 存储协议
│   ├── biz-params/       #   系统参数
│   ├── biz-a2a/          #   A2A 协议配置
│   ├── biz-studio/       #   Studio 集成
│   ├── biz-sk/           #   密钥管理
│   ├── biz-workflow/     #   工作流定义 + 运行记录 + 资源绑定
│   ├── biz-datasource/   #   数据源配置（工作流 DB 节点）
│   ├── biz-cache/        #   缓存配置（工作流缓存节点）
│   ├── biz-mq/           #   消息队列配置（工作流 MQ 节点）
│   ├── biz-channel/      #   消息渠道配置（工作流渠道节点）
│   ├── biz-gateway/      #   网关应用 + API 定义 + 访问日志
│   └── biz-longterm/     #   长期记忆配置
├── engine/               # 引擎层
│   ├── agent/            #   ReAct / A2A 智能体工厂
│   ├── agui/             #   AG-UI 协议智能体装配与注册
│   ├── model/            #   多模型供应商适配
│   ├── formatter/        #   模型消息格式化器
│   ├── tool/             #   工具系统 + 动态加载
│   ├── skill/            #   VEP / APIP 内置技能
│   ├── knowledge/        #   多后端知识库工厂
│   ├── mcp/              #   MCP 客户端工厂 + 懒加载
│   ├── memory/           #   记忆管理 + 长期记忆
│   ├── rag/              #   本地 RAG 流水线
│   ├── hook/             #   Hook 生命周期
│   ├── prompt/           #   提示词工程
│   ├── security/         #   脚本安全扫描引擎
│   ├── workspace/        #   工作空间 + 安全校验
│   ├── log/              #   对话日志异步生产消费
│   ├── studio/           #   Studio 运行时集成
│   ├── controller/       #   引擎内置接口（文件访问）
│   └── mpatch/           #   代码增量更新器
├── workflow/             # 工作流引擎层
│   ├── node/             #   30+ 节点实现（base/cache/code/condition/db/http/loop/mcp/mq/...）
│   └── workflow/         #   工作流核心（Workflow/Edge/RunWorkflow）
├── gateway/              # API 服务网关数据面（Vert.x 动态路由 + 鉴权 + 白名单 + 限流）
├── scheduler/            # 调度层：Quartz + 分布式锁
├── heartbeat/            # 基础设施：心跳监控
├── runner-console/       # 应用：管理控制台（36 个 Controller）
├── runner-runtime/       # 应用：AI 运行时 + AG-UI 端点
├── runner-proxy/         # 应用：Shell 沙箱
├── runner-file/          # 应用：文件同步
├── runner-websocket/     # 应用：WebSocket 推送
├── ui/                   # 前端：Vue 3 管理界面
├── docker/               # 部署：Docker Compose + Nginx
└── sql/                  # 数据库初始化脚本
```

---

## 贡献指南

我们欢迎任何形式的贡献！请按照以下流程提交 Pull Request：

### 1. Fork 与克隆

在 Gitee 上 Fork 本仓库，然后克隆到本地：

```bash
git clone https://gitee.com/<your-username>/apboa-next.git
cd apboa-next
git remote add upstream https://gitee.com/studious_tiger/apboa-next.git
```

### 2. 创建分支

从 `main` 分支创建你的工作分支，分支命名遵循以下规范：

| 类型 | 分支前缀 | 示例 |
|------|----------|------|
| 新功能 | `feature/` | `feature/mcp-timeout-retry` |
| 缺陷修复 | `fix/` | `fix/react-loop-null-check` |
| 文档更新 | `docs/` | `docs/update-deploy-guide` |
| 重构优化 | `refactor/` | `refactor/vector-store-factory` |

```bash
git checkout -b feature/your-feature-name
```

### 3. 开发与提交

**代码规范：**

- 后端代码遵循项目已有的注释规范（参见类/方法上的 Javadoc）
- 前端代码遵循 ESLint + Prettier 配置
- 新增功能应附带相应的单元测试
- 确保本地 `mvn compile` 和前端 `pnpm build` 通过

**Commit Message 规范：**

采用 [Conventional Commits](https://www.conventionalcommits.org/) 格式：

```
<type>(<scope>): <subject>

[可选的正文]

[可选的脚注]
```

常用 `type`：

- `feat` — 新功能
- `fix` — 缺陷修复
- `docs` — 文档变更
- `refactor` — 重构（非新功能、非修复）
- `perf` — 性能优化
- `test` — 测试相关
- `chore` — 构建/工具链变更

`scope` 为可选的模块名，如 `engine`、`mcp`、`ui`、`docker` 等。

示例：

```
feat(mcp): 添加 MCP 连接超时自动重试机制

- 连续失败 3 次后自动降级
- 支持通过配置调整重试阈值
```

### 4. 提交 Pull Request

- 确保你的分支与上游 `main` 保持同步：

  ```bash
  git fetch upstream
  git rebase upstream/main
  ```

- 推送分支到你的 Fork 仓库后，向本仓库的 `main` 分支发起 Pull Request
- PR 标题遵循 Commit Message 格式，简要描述变更内容
- PR 描述中请说明：**变更动机**、**变更内容**、**测试方式**
- 一个 PR 只做一件事，避免混合多个不相关的变更
- 如果 PR 关联某个 Issue，请在描述中引用

### 5. 代码审查

- PR 提交后将由维护者进行 Code Review
- 请根据审查意见及时修改并推送更新
- 审查通过后由维护者合并到 `main` 分支

> **提示：** 提交前请确保已签署 [贡献者许可协议（CLA）](https://cla-assistant.io/)（如适用），并同意你的贡献遵循本项目的 [MIT](LICENSE) 开源协议。

---

## 交流与赞助

<table>
  <tr>
    <td align="center"><strong>入群交流（备注 apboa）</strong></td>
    <td align="center"><strong>微信赞助</strong></td>
    <td align="center"><strong>支付宝赞助</strong></td>
  </tr>
  <tr>
    <td align="center"><img src="image/image-20260617193446946.png" width="200" alt="入群二维码"/></td>
    <td align="center"><img src="image/image-20260617193023284.png" width="200" alt="微信收款码"/></td>
    <td align="center"><img src="image/image-20260617184137320.png" width="200" alt="支付宝收款码"/></td>
  </tr>
</table>

---

## 开源协议

[MIT](LICENSE) — Copyright (c) 2026 StudiousTiger

---

<p align="center">
  <sub>如果觉得 Apboa 对你有帮助，请给一颗 Star 支持。</sub>
</p>

---

无论是构建客服助手、创意伙伴还是行业专家，您都无需从零开始。在 Apboa Next 的赋能下，只需通过简单的配置与拖拽，即可将前沿的 AI 能力快速转化为解决实际业务问题的智能体，大幅降低技术门槛与开发周期，真正实现智能应用的随需而创、高效落地。
