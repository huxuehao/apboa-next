
package io.agentscope.core.agui.processor;

import com.hxh.apboa.common.consts.TableConst;
import com.hxh.apboa.common.entity.AgentDefinition;
import com.hxh.apboa.common.entity.ChatSession;
import com.hxh.apboa.common.util.AgentMetadataStore;
import com.hxh.apboa.common.util.TenantUtils;
import com.hxh.apboa.engine.agui.AgentContext;
import com.hxh.apboa.engine.agui.AguiCustomEvents;
import com.hxh.apboa.engine.hitl.EditedInputApplier;
import com.hxh.apboa.engine.hook.builtins.IConfirmationHook;
import com.hxh.apboa.engine.log.ChatLogHook;
import com.hxh.apboa.engine.model.SessionModelResolver;
import com.hxh.apboa.engine.model.ThinkingModeResolver;
import io.agentscope.spring.boot.agui.common.ThreadSessionManager;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.AgentBase;
import io.agentscope.core.agui.adapter.AguiAdapterConfig;
import io.agentscope.core.agui.adapter.AguiAgentAdapter;
import io.agentscope.core.agui.event.AguiEvent;
import io.agentscope.core.agui.model.AguiMessage;
import io.agentscope.core.agui.model.RunAgentInput;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.state.SimpleSessionKey;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import io.agentscope.core.session.InMemorySession;
import io.agentscope.core.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import reactor.core.publisher.Flux;

public class AguiRequestProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AguiRequestProcessor.class);

    private final AgentResolver agentResolver;
    private final ThreadSessionManager sessionManager;
    private final AguiAdapterConfig config;
    private final Session session;
    private final JdbcTemplate jdbcTemplate;

    private AguiRequestProcessor(Builder builder) {
        this.agentResolver =
                Objects.requireNonNull(builder.agentResolver, "agentResolver cannot be null");
        this.sessionManager = builder.sessionManager;
        this.config = builder.config != null ? builder.config : AguiAdapterConfig.defaultConfig();
        this.session = builder.session != null ? builder.session : new InMemorySession();
        this.jdbcTemplate = builder.jdbcTemplate;
    }

    /**
     * 解析 agent，并检测会话思考模式覆盖变化：变化则重建（模型 thinking 在构建期固化，
     * 无法动态改；重建后上下文由前端传入的全量历史消息恢复——与 runtime 重启后续聊同机制）。
     * 只比覆盖值（构建时记录 vs Redis 当前），无覆盖态之间零开销。
     */
    private Agent resolveAgentWithOverrideCheck(String agentId, String threadId) {
        Agent agent = agentResolver.resolveAgent(agentId, threadId);
        if (sessionManager == null || !(agent instanceof AgentBase built)) {
            return agent;
        }
        String changed = firstChangedOverride(built.getAgentId(), threadId);
        if (changed == null) {
            return agent;
        }
        // 挂起保护：会话有待确认工具时跳过重建——removeSession 会连挂起现场一起删，
        // 确认卡变死卡且刷新恢复（getPendingConfirms）失效。前端已禁用挂起中切换，
        // 本兜底防 API 直调/多端旧页面/外置集成方绕过。覆盖值仍在 Redis，
        // 挂起处理完之后的下一条消息照常检测重建（只延后生效，不丢覆盖）。
        // getPendingConfirms 异常降级为空列表 = 回到现状重建行为，失败模式温和。
        if (!getPendingConfirms(threadId).isEmpty()) {
            logger.info("会话级配置变化（{}）但存在待确认工具，跳过重建保护挂起现场: threadId={}",
                    changed, threadId);
            return agent;
        }
        logger.info("会话级配置变化（{}），重建 agent: threadId={}", changed, threadId);
        sessionManager.removeSession(threadId);
        return agentResolver.resolveAgent(agentId, threadId);
    }

    /**
     * 对比构建期记录的会话级覆盖（思考模式 / 对话模型）与 Redis 当前值，
     * 返回首个变化描述；null=无变化。构建期未记录（旧 agent）不参与对比。
     */
    private String firstChangedOverride(String builtAgentId, String threadId) {
        String builtThinking = AgentMetadataStore.get(builtAgentId, "builtThinkingOverride");
        if (builtThinking != null) {
            String current = ThinkingModeResolver.overrideKey(ThinkingModeResolver.resolveOverride(threadId));
            if (!builtThinking.equals(current)) {
                return "thinking " + builtThinking + " -> " + current;
            }
        }
        String builtModel = AgentMetadataStore.get(builtAgentId, "builtModelOverride");
        if (builtModel != null) {
            String current = SessionModelResolver.overrideKey(SessionModelResolver.resolveOverride(threadId));
            if (!builtModel.equals(current)) {
                return "model " + builtModel + " -> " + current;
            }
        }
        return null;
    }

    /**
     * Result of processing an AG-UI request.
     *
     * <p>Contains the resolved agent (for interrupt handling) and the event stream.
     *
     * @param agent The resolved agent instance
     * @param events The event stream
     */
    public record ProcessResult(Agent agent, Flux<AguiEvent> events) {}

    /**
     * Process an AG-UI request and return the result containing agent and event stream.
     *
     * @param input The run agent input
     * @param headerAgentId The agent ID from HTTP header (may be null)
     * @param pathAgentId The agent ID from URL path variable (may be null)
     * @return A ProcessResult containing the agent and event stream
     */
    public ProcessResult process(RunAgentInput input, String headerAgentId, String pathAgentId) {
        String threadId = input.getThreadId();

        // 登记本次 run 的认证渠道（成本流水归因：WEB/CHAT_KEY/SK_API），
        // AuthInterceptor 打标、成本落库层按 threadId 读取
        registerChannel(threadId);

        // Resolve agent ID
        String agentId = resolveAgentId(input, headerAgentId, pathAgentId);

        // Resolve agent（含思考模式覆盖变化的重建检查）
        Agent agent = resolveAgentWithOverrideCheck(agentId, threadId);

        // 添加threadId和租户信息
        if (agent instanceof AgentBase agentBase) {
            if (AgentMetadataStore.get(agentBase.getAgentId(),"tenantId") == null) {
                AgentContext agentContext = AgentContext.get();
                AgentMetadataStore.put(agentBase.getAgentId(), "tenantId", agentContext.getTenantId());
                AgentMetadataStore.put(agentBase.getAgentId(), "tenantCode", agentContext.getTenantCode());
                AgentMetadataStore.put(agentBase.getAgentId(), "threadId", threadId);
            }

            // 获取是否开启记忆
            boolean toolProcessActive = input.getForwardedProp("toolProcessActive") != null
                    ? (Boolean) input.getForwardedProp("toolProcessActive")
                    : false;
            AgentMetadataStore.put(agentBase.getAgentId(), "toolProcessActive", toolProcessActive);
        }

        // 获取是否开启记忆
        boolean memoryActive = input.getForwardedProp("memoryActive") != null
                ? (Boolean) input.getForwardedProp("memoryActive")
                : false;

        // Determine effective input based on server-side memory
        RunAgentInput effectiveInput = input;
        if (agentResolver.hasMemory(threadId)) {
            logger.debug(
                    "Using server-side memory for thread {}, extracting latest user message",
                    threadId);
            effectiveInput = extractLatestUserMessage(input);
        }

        // 加载历史记忆
        if (agent instanceof ReActAgent reActAgent) {
            if (memoryActive) {
                try {
                    AgentDefinition agentDefinition = getAgentDefinition(threadId, jdbcTemplate);
                    if (agentDefinition != null && agentDefinition.getEnableMemory()) {
                        // 从session中加载历史会话
                        reActAgent.loadFrom(session, threadId);
                    }
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                }
            } else  {
                if (!input.getMessages().isEmpty()) {
                    boolean isUserMsg = "user".equalsIgnoreCase(input.getMessages().getFirst().getRole());

                    boolean isUIP = false;
                    String messageId = input.getMessages().getFirst().getId();
                    if (messageId != null) {
                        isUIP = messageId.toLowerCase().startsWith("uip");
                    }

                    // 成立条件：是用户消息且不是用户交互协议消息且记忆不为空
                    if (isUserMsg && !isUIP && reActAgent.getMemory() != null) {
                        reActAgent.getMemory().clear();
                    }
                }


            }
        }

        // Create adapter and run
        AguiAgentAdapter adapter = new AguiAgentAdapter(agent, config);

        // 设置租户信息：metadata 缺失时（runtime 重启后旧会话首条消息——AgentContext.init 不含租户，
        // 实测会让整个 run 失败）按 threadId 查 chat_session 兜底并回填 metadata，下游（ChatLogHook
        // 落库、session 持久化）随之恢复正常
        Object tenantId = AgentMetadataStore.get(agent.getAgentId(), "tenantId");
        Object tenantCode = AgentMetadataStore.get(agent.getAgentId(), "tenantCode");
        if (tenantId == null || tenantCode == null) {
            ResumeContext rc = loadResumeContext(threadId);
            if (rc == null) {
                throw new IllegalStateException("Tenant information not found");
            }
            tenantId = rc.tenantId;
            tenantCode = rc.tenantCode;
            AgentMetadataStore.put(agent.getAgentId(), "tenantId", rc.tenantId);
            AgentMetadataStore.put(agent.getAgentId(), "tenantCode", rc.tenantCode);
        }
        TenantUtils.setCurrentTenant(Long.valueOf(tenantId.toString()), tenantCode.toString());

        // 月度预算熔断：达到预算的智能体拒绝新 run（外嵌 chatKey 防盗刷的最后闸门），
        // 以正常文本消息回复提示，前端零改动
        Flux<AguiEvent> budgetRejectEvents = checkMonthlyBudget(threadId, input.getRunId());
        if (budgetRejectEvents != null) {
            TenantUtils.clear();
            return new ProcessResult(agent, budgetRejectEvents);
        }

        // 执行完成后保存session
        Flux<AguiEvent> events = adapter.run(effectiveInput)
                .doFinally(signalType -> {
                    // HITL（§6.1）：发生确认暂停时无条件保存暂停态（即便未开记忆），供 resume 跨实例恢复；
                    // 否则维持原长期记忆语义（仅 memoryActive 时保存）。
                    boolean shouldSave = agent instanceof ReActAgent && (memoryActive || adapter.isSuspended());
                    if (shouldSave) {
                        try {
                            ((ReActAgent) agent).saveTo(session, threadId);
                        } catch (Exception ex) {
                            logger.error(ex.getMessage(), ex);
                        } finally {
                            TenantUtils.clear();
                        }
                    } else  {
                        TenantUtils.clear();
                    }
                });

        return new ProcessResult(agent, events);
    }

    /**
     * HITL resume（docs/hitl-confirmation-refactor.md §6.3）：根据用户确认决策恢复暂停的 agent。
     *
     * <p>全部允许 → agent 继续执行 pending 工具；含拒绝 → 喂入「用户已拒绝执行」结果后继续。
     * <p>跨实例安全：租户/agentId 从 chat_session 查（threadId 全局唯一），暂停态从分布式 Session 恢复。
     *
     * @param threadId 会话 ID（暂停态的 key）
     * @param decisions 逐工具决策（toolUseId/name/approved）；空表示全部允许
     * @param memoryActive 是否开启长期记忆（决定 resume 完成后保留还是删除 session）
     */
    public ProcessResult resume(String threadId, List<ResumeDecision> decisions, boolean memoryActive) {
        // resume 续跑同样消耗 token，渠道一并登记
        registerChannel(threadId);
        ResumeContext rc = loadResumeContext(threadId);
        if (rc == null) {
            throw new IllegalStateException("找不到会话或暂停态: " + threadId);
        }
        TenantUtils.setCurrentTenant(rc.tenantId, rc.tenantCode);

        Agent agent = agentResolver.resolveAgent(rc.agentCode, threadId);
        if (agent instanceof ReActAgent reActAgent) {
            reActAgent.loadFrom(session, threadId);
            // HITL 改参：把用户在确认 UI 修改后的参数写回暂停态记忆（仅 approved + need_confirm），
            // 续跑 acting 即以新参数执行，无需其他透传；落库入参缓存由 Applier 内部同步修正
            applyEditedInputs(reActAgent, decisions);
        }

        String runId = UUID.randomUUID().toString();

        // 被拒工具不会真实执行、无 PostActingEvent：tool 消息落库由 ChatLogHook 补偿
        // （否则拒绝结果刷新后丢失；缓存跨重启丢失时静默跳过），并在续跑流头下发拒绝
        // 结果事件——权威耗时（补偿返回的落库同源值）先于结果事件，前端只消费不掐表，
        // 与自动拒绝（Adapter 侧）的事件路径一致
        List<AguiEvent> rejectEvents = new ArrayList<>();
        if (decisions != null) {
            for (ResumeDecision d : decisions) {
                if (d == null || d.approved()) {
                    continue;
                }
                Long authElapsed = ChatLogHook.completeMainToolRejected(
                        threadId, d.toolUseId(), REJECT_RESULT_TEXT);
                if (authElapsed != null) {
                    rejectEvents.add(new AguiEvent.Custom(
                            threadId,
                            runId,
                            AguiCustomEvents.TOOL_ELAPSED,
                            Map.of("toolUseId", d.toolUseId(), "elapsed", authElapsed)));
                }
                rejectEvents.add(new AguiEvent.ToolCallEnd(threadId, runId, d.toolUseId()));
                rejectEvents.add(new AguiEvent.ToolCallResult(
                        threadId, runId, d.toolUseId(), REJECT_RESULT_TEXT, "tool", null));
            }
        }

        List<Msg> resumeInput = buildResumeInput(decisions);

        AguiAgentAdapter adapter = new AguiAgentAdapter(agent, config);
        Flux<AguiEvent> events = Flux.concat(
                        Flux.fromIterable(rejectEvents),
                        adapter.runWithMessages(resumeInput, threadId, runId))
                .doFinally(signalType -> {
                    try {
                        if (agent instanceof ReActAgent reActAgent) {
                            if (memoryActive) {
                                reActAgent.saveTo(session, threadId);          // 长期记忆：保留
                            } else {
                                session.delete(SimpleSessionKey.of(threadId)); // 临时暂存：用完即删
                            }
                        }
                    } catch (Exception ex) {
                        logger.error(ex.getMessage(), ex);
                    } finally {
                        TenantUtils.clear();
                    }
                });

        return new ProcessResult(agent, events);
    }

    /**
     * HITL 刷新恢复（配合前端刷新/重进会话）：从持久 Session 的暂停态重建「待确认工具」列表，
     * 供前端在没有内存态（RunTracker 已 markCompleted、或跨实例/重启）时重建确认 UI。
     *
     * <p><b>只读</b>：仅 {@code loadFrom} 加载暂停态用于读取，<b>不</b> saveTo / delete，
     * 暂停态原封不动，后续真正 {@link #resume} 时仍可从暂停点续跑。
     *
     * <p><b>暂停态判据</b>：序列化后的 {@code Msg} 不保留 {@code generateReason}
     * （实测 MysqlSession 的 memory JSON 无该字段），故不能靠 {@code REASONING_STOP_REQUESTED} 判断，
     * 改用结构判据——memory 最后一条为 {@code ASSISTANT} 且含 {@link ToolUseBlock}
     * （确认暂停发生在工具执行前，其后不会再有 TOOL 结果消息）。再用
     * {@link IConfirmationHook#isNeedConfirm} 过滤，与 {@code AguiAgentAdapter} 推送
     * {@code TOOL_CONFIRM_REQUIRED} 的口径一致（排除同轮被 stopAgent 连累的普通/MCP 工具，修 §2.2「MCP 确认假象」）。
     *
     * <p>调用前需已初始化最小 {@link AgentContext}（resolveAgent 重建时经 setTenantInfo 回填），
     * 与 {@link #resume} 的租户/上下文处理一致。
     *
     * @param threadId 会话 ID（暂停态的 key）
     * @return 待确认工具 [{toolUseId,name,input}]；无暂停态返回空列表
     */
    public List<Map<String, Object>> getPendingConfirms(String threadId) {
        if (threadId == null || threadId.isEmpty()) {
            return List.of();
        }
        // 快速否定：Session 无任何暂停态/记忆 → 直接空（避免无谓重建 agent）
        if (session == null || !session.exists(SimpleSessionKey.of(threadId))) {
            logger.debug("[pending判定] session空或不存在: sessionNull={}, threadId={}", session == null, threadId);
            return List.of();
        }
        ResumeContext rc = loadResumeContext(threadId);
        if (rc == null) {
            logger.debug("[pending判定] loadResumeContext=null: threadId={}", threadId);
            return List.of();
        }
        TenantUtils.setCurrentTenant(rc.tenantId, rc.tenantCode);
        try {
            // 重建 agent 会重新构建 toolkit → 重新登记 need_confirm 工具（isNeedConfirm 才有依据），
            // 再 loadFrom 加载暂停态。二者顺序不能反。
            Agent agent = agentResolver.resolveAgent(rc.agentCode, threadId);
            if (!(agent instanceof ReActAgent reActAgent) || reActAgent.getMemory() == null) {
                logger.debug("[pending判定] agent非ReAct或无memory: type={}, threadId={}",
                        agent == null ? "null" : agent.getClass().getSimpleName(), threadId);
                return List.of();
            }
            reActAgent.loadFrom(session, threadId);

            List<Msg> messages = reActAgent.getMemory().getMessages();
            if (messages == null || messages.isEmpty()) {
                logger.debug("[pending判定] loadFrom后memory为空: memoryType={}, threadId={}",
                        reActAgent.getMemory().getClass().getSimpleName(), threadId);
                return List.of();
            }
            Msg last = messages.get(messages.size() - 1);
            if (last.getRole() != MsgRole.ASSISTANT) {
                logger.debug("[pending判定] 尾消息非ASSISTANT: lastRole={}, size={}, memoryType={}, threadId={}",
                        last.getRole(), messages.size(),
                        reActAgent.getMemory().getClass().getSimpleName(), threadId);
                return List.of();
            }
            List<Map<String, Object>> pending = new ArrayList<>();
            for (ToolUseBlock toolUse : last.getContentBlocks(ToolUseBlock.class)) {
                if (IConfirmationHook.isNeedConfirm(toolUse.getName())) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("toolUseId", toolUse.getId());
                    item.put("name", toolUse.getName());
                    item.put("input", toolUse.getInput());
                    // 与实时 TOOL_CONFIRM_REQUIRED 同构：刷新恢复的表单同样拿到字段元数据
                    // （上方 resolveAgent 重建 toolkit 时已重新登记，此处静态读取必有依据）
                    item.put("fields", IConfirmationHook.getConfirmFields(toolUse.getName()));
                    pending.add(item);
                }
            }
            return pending;
        } catch (Exception e) {
            // 恢复失败不应阻断页面加载：降级为「无待确认」
            logger.warn("恢复暂停态待确认列表失败 threadId={}: {}", threadId, e.getMessage());
            return List.of();
        } finally {
            TenantUtils.clear();
        }
    }

    /**
     * 拒绝工具时回填的「错误结果」文案：用 error/不可用语义（而非中性「已拒绝」），
     * 降低 ReAct 模型把「拒绝」理解成「诉求未满足→重试」的倾向
     * （实测中性「用户已拒绝执行」会触发模型重调被拒工具）。
     * 注意：OpenAI/Ollama 协议无结构化 is_error，错误只能靠喂回的文本语义表达。
     * public 供 SubAgentTool 子智能体确认拒绝时复用（主/子 resume 拒绝语义单一出处）。
     */
    public static final String REJECT_RESULT_TEXT =
            com.hxh.apboa.common.enums.ConfirmMode.REJECT_RESULT_TEXT;

    /** 汇总用户修改过参数的 approved 决策，写回暂停态记忆（无命中零开销）。 */
    private void applyEditedInputs(ReActAgent reActAgent, List<ResumeDecision> decisions) {
        if (decisions == null || decisions.isEmpty()) {
            return;
        }
        Map<String, Map<String, Object>> edited = new LinkedHashMap<>();
        for (ResumeDecision d : decisions) {
            if (d != null && d.approved() && d.input() != null && !d.input().isEmpty()) {
                edited.put(d.toolUseId(), d.input());
            }
        }
        if (edited.isEmpty()) {
            return;
        }
        int applied = EditedInputApplier.apply(reActAgent.getMemory(), edited);
        if (applied != edited.size()) {
            logger.warn("HITL 改参部分未命中: 请求 {} 项, 实际改写 {} 项", edited.size(), applied);
        }
    }

    /** 将拒绝的工具喂回「授权被拒/工具不可用」错误结果；允许的不喂（留给 agent 自己执行）。全允许则返回空列表。 */
    private List<Msg> buildResumeInput(List<ResumeDecision> decisions) {
        if (decisions == null || decisions.isEmpty()) {
            return List.of();
        }
        List<ContentBlock> rejects = new ArrayList<>();
        for (ResumeDecision d : decisions) {
            if (d != null && !d.approved()) {
                rejects.add(ToolResultBlock.of(d.toolUseId(), d.name(),
                        TextBlock.builder().text(REJECT_RESULT_TEXT).build()));
            }
        }
        if (rejects.isEmpty()) {
            return List.of();
        }
        return List.of(Msg.builder().role(MsgRole.TOOL).content(rejects).build());
    }

    /** 从 chat_session 查 agentId + 租户（threadId 全局唯一，故不加租户过滤）。 */
    private ResumeContext loadResumeContext(String threadId) {
        if (threadId == null || threadId.isEmpty()) {
            return null;
        }
        // resolveAgent 用 agentCode（registry 注册 key 是 agent_code，不是数字 id），故 JOIN agent_definition 取 code
        String sql = String.format(
                "SELECT ad.agent_code AS agentCode, cs.tenant_id AS tenantId, t.code AS tenantCode "
                        + "FROM %s cs JOIN tenant t ON t.id = cs.tenant_id "
                        + "JOIN %s ad ON ad.id = cs.agent_id "
                        + "WHERE cs.id = %s",
                TableConst.CHAT_SESSION, TableConst.AGENT, threadId);
        List<ResumeContext> list = jdbcTemplate.query(sql, (rs, n) -> {
            ResumeContext c = new ResumeContext();
            c.agentCode = rs.getString("agentCode");
            c.tenantId = rs.getLong("tenantId");
            c.tenantCode = rs.getString("tenantCode");
            return c;
        });
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * resume 逐工具决策。
     *
     * @param input 用户在确认 UI 中修改后的完整参数；null/空 = 未修改（沿用模型原始参数，
     *              旧客户端不传该字段天然兼容）。仅 approved 且 need_confirm 工具生效。
     */
    public record ResumeDecision(
            String toolUseId, String name, boolean approved, Map<String, Object> input) {

        /** 兼容构造器：未携带修改参数的决策（自动全拒等内部构造路径沿用） */
        public ResumeDecision(String toolUseId, String name, boolean approved) {
            this(toolUseId, name, approved, null);
        }
    }

    private static class ResumeContext {
        private String agentCode;
        private Long tenantId;
        private String tenantCode;
    }

    /**
     * 通过 sessionId 获取 AgentDefinition
     * @param sessionId threadId
     * @param jdbcTemplate jdbcTemplate
     */
    /**
     * 登记本次请求的认证渠道到会话（MVC 同线程内从 RequestHolder 取认证层打的标记；
     * 取不到时不覆盖已有登记，流水渠道回落 NULL 显示「未标记」）
     */
    private void registerChannel(String threadId) {
        try {
            jakarta.servlet.http.HttpServletRequest request = com.hxh.apboa.common.util.RequestHolder.getRequest();
            if (request != null && request.getAttribute(com.hxh.apboa.common.consts.SysConst.AUTH_CHANNEL) instanceof String channel) {
                com.hxh.apboa.engine.log.telemetry.ChatChannelHolder.put(threadId, channel);
            }
        } catch (Exception e) {
            logger.debug("渠道登记失败 threadId={}: {}", threadId, e.getMessage());
        }
    }

    /**
     * 月度预算检查：会话归属智能体配置了 monthly_budget 且当月已计价成本（chat_usage_record
     * 聚合，含对话/子智能体等全部场景）达到预算时，返回一段拒绝提示事件流；未配预算或未超额
     * 返回 null 放行。查询走两条主键/索引 SQL，run 建立频度下开销可忽略。
     * 未配价模型的用量 cost 为 NULL 不计入——启用预算前应先为相关模型配价。
     */
    private Flux<AguiEvent> checkMonthlyBudget(String threadId, String runId) {
        try {
            Long agentId = jdbcTemplate.query(
                    "SELECT agent_id FROM chat_session WHERE id = ?",
                    rs -> rs.next() ? rs.getLong("agent_id") : null,
                    Long.valueOf(threadId)
            );
            // 判定收口公共 checker（定时任务链路共用同一口径）
            java.util.Optional<com.hxh.apboa.engine.log.telemetry.MonthlyBudgetChecker.BudgetExceeded> exceeded =
                    com.hxh.apboa.engine.log.telemetry.MonthlyBudgetChecker.check(agentId, jdbcTemplate);
            if (exceeded.isEmpty()) {
                return null;
            }
            java.math.BigDecimal spent = exceeded.get().spent();
            java.math.BigDecimal budget = exceeded.get().budget();
            logger.warn("智能体 {} 月度预算熔断：已用 {} / 预算 {}，拒绝 run {}", agentId, spent, budget, runId);
            String messageId = "budget-" + UUID.randomUUID();
            String text = String.format("本智能体本月的成本预算已用完（已用 ¥%.2f / 预算 ¥%.2f），暂时无法继续对话。请联系管理员调整预算或等下月自动恢复。",
                    spent, budget);
            return Flux.just(
                    new AguiEvent.RunStarted(threadId, runId),
                    new AguiEvent.TextMessageStart(threadId, runId, messageId, "assistant"),
                    new AguiEvent.TextMessageContent(threadId, runId, messageId, text),
                    new AguiEvent.TextMessageEnd(threadId, runId, messageId),
                    new AguiEvent.RunFinished(threadId, runId)
            );
        } catch (Exception e) {
            // 预算检查自身故障不拦业务：放行并告警
            logger.error("月度预算检查失败 threadId={}: {}", threadId, e.getMessage());
            return null;
        }
    }

    private AgentDefinition getAgentDefinition(String sessionId, JdbcTemplate jdbcTemplate) {
        if (sessionId == null || sessionId.isEmpty()) {
            return null;
        }

        String chat_session_sql = String.format("SELECT * FROM %s WHERE id = %s", TableConst.CHAT_SESSION, sessionId);
        // 添加租户过滤（JdbcTemplate 绕过 MyBatis-Plus 拦截器）
        if (TenantUtils.getCurrentTenantId() != null) {
            chat_session_sql += " AND tenant_id = " + TenantUtils.getCurrentTenantId();
        }
        List<ChatSession> chatSessions = jdbcTemplate.query(chat_session_sql, (rs, rowNum) -> {
            ChatSession chatSession = new ChatSession();
            // 手动映射字段
            chatSession.setId(rs.getLong("id"));
            chatSession.setAgentId(rs.getLong("agent_id"));
            return chatSession;
        });

        if (chatSessions.isEmpty()) {
            return null;
        }

        String agent_definition_sql = String.format("SELECT * FROM %s WHERE id = %s", TableConst.AGENT, chatSessions.getFirst().getAgentId());
        // 添加租户过滤（JdbcTemplate 绕过 MyBatis-Plus 拦截器）
        if (TenantUtils.getCurrentTenantId() != null) {
            agent_definition_sql += " AND tenant_id = " + TenantUtils.getCurrentTenantId();
        }
        List<AgentDefinition> AgentDefinitions = jdbcTemplate.query(agent_definition_sql, (rs, rowNum) -> {
            AgentDefinition agentDefinition = new AgentDefinition();
            // 手动映射字段
            agentDefinition.setId(rs.getLong("id"));
            agentDefinition.setEnableMemory(rs.getBoolean("enable_memory"));
            return agentDefinition;
        });

        if (AgentDefinitions.isEmpty()) {
            return null;
        }

        return AgentDefinitions.getFirst();
    }

    /**
     * Resolve the agent ID from multiple sources.
     *
     * <p>The agent ID is resolved in the following priority order:
     * <ol>
     *   <li>URL path variable (if provided)</li>
     *   <li>HTTP header (if provided)</li>
     *   <li>forwardedProps.agentId in request body</li>
     *   <li>config.defaultAgentId</li>
     *   <li>"default"</li>
     * </ol>
     *
     * @param input The request input
     * @param headerAgentId The agent ID from HTTP header (may be null)
     * @param pathAgentId The agent ID from URL path variable (may be null)
     * @return The resolved agent ID
     */
    public String resolveAgentId(RunAgentInput input, String headerAgentId, String pathAgentId) {
        // 1. URL path variable has highest priority
        if (pathAgentId != null && !pathAgentId.isEmpty()) {
            logger.debug("Using agent ID from path variable: {}", pathAgentId);
            return pathAgentId;
        }

        // 2. Check HTTP header
        if (headerAgentId != null && !headerAgentId.isEmpty()) {
            logger.debug("Using agent ID from header: {}", headerAgentId);
            return headerAgentId;
        }

        // 3. Check forwardedProps for agentId
        Object agentIdProp = input.getForwardedProp("agentId");
        if (agentIdProp != null) {
            String propsAgentId = agentIdProp.toString();
            logger.debug("Using agent ID from forwardedProps: {}", propsAgentId);
            return propsAgentId;
        }

        // 4. Use config default
        if (config.getDefaultAgentId() != null) {
            logger.debug("Using default agent ID from config: {}", config.getDefaultAgentId());
            return config.getDefaultAgentId();
        }

        // 5. Fall back to "default"
        logger.debug("Using fallback agent ID: default");
        return "default";
    }

    /**
     * Extract only the latest user message from the input.
     *
     * <p>This is used when server-side memory is enabled and the agent already
     * has conversation history. Only the latest user message needs to be passed.
     *
     * @param input The original input
     * @return A new input with only the latest user message
     */
    public RunAgentInput extractLatestUserMessage(RunAgentInput input) {
        List<AguiMessage> messages = input.getMessages();
        if (messages == null || messages.isEmpty()) {
            return input;
        }

        // Find the last user message
        AguiMessage lastUserMessage = null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            AguiMessage msg = messages.get(i);
            if ("user".equalsIgnoreCase(msg.getRole())) {
                lastUserMessage = msg;
                break;
            }
        }

        if (lastUserMessage == null) {
            return input;
        }

        // Create new input with only the last user message
        return RunAgentInput.builder()
                .threadId(input.getThreadId())
                .runId(input.getRunId())
                .messages(List.of(lastUserMessage))
                .tools(input.getTools())
                .context(input.getContext())
                .forwardedProps(input.getForwardedProps())
                .build();
    }

    /**
     * Creates a new builder for AguiRequestProcessor.
     *
     * @return A new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /** Builder for AguiRequestProcessor. */
    public static class Builder {

        private AgentResolver agentResolver;
        private AguiAdapterConfig config;
        private Session session;
        private JdbcTemplate jdbcTemplate;
        private ThreadSessionManager sessionManager;

        /**
         * Set the agent resolver.
         *
         * @param agentResolver The agent resolver
         * @return This builder
         */
        public Builder agentResolver(AgentResolver agentResolver) {
            this.agentResolver = agentResolver;
            return this;
        }

        /**
         * 会话级 agent 缓存管理器（可选）：思考模式等构建期固化参数变化时，
         * 由 process 检测并 removeSession 强制重建 agent。null 时跳过检测。
         */
        public Builder sessionManager(ThreadSessionManager sessionManager) {
            this.sessionManager = sessionManager;
            return this;
        }

        /**
         * Set the adapter configuration.
         *
         * @param config The adapter configuration
         * @return This builder
         */
        public Builder config(AguiAdapterConfig config) {
            this.config = config;
            return this;
        }

        /**
         * Set the session storage.
         *
         * @param session The session storage (InMemorySession or MysqlSession)
         * @return This builder
         */
        public Builder session(Session session) {
            this.session = session;
            return this;
        }

        /**
         * Set the jdbcTemplate storage.
         *
         * @param jdbcTemplate jdbcTemplate
         * @return This builder
         */
        public Builder jdbcTemplate(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
            return this;
        }

        /**
         * Build the processor.
         *
         * @return The built processor
         * @throws NullPointerException if agentResolver is not set
         */
        public AguiRequestProcessor build() {
            return new AguiRequestProcessor(this);
        }
    }
}
