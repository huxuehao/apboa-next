
package io.agentscope.core.agui.processor;

import com.hxh.apboa.common.consts.TableConst;
import com.hxh.apboa.common.entity.AgentDefinition;
import com.hxh.apboa.common.entity.ChatSession;
import com.hxh.apboa.common.util.AgentMetadataStore;
import com.hxh.apboa.common.util.TenantUtils;
import com.hxh.apboa.engine.agui.AgentContext;
import com.hxh.apboa.engine.hook.builtins.IConfirmationHook;
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
    private final AguiAdapterConfig config;
    private final Session session;
    private final JdbcTemplate jdbcTemplate;

    private AguiRequestProcessor(Builder builder) {
        this.agentResolver =
                Objects.requireNonNull(builder.agentResolver, "agentResolver cannot be null");
        this.config = builder.config != null ? builder.config : AguiAdapterConfig.defaultConfig();
        this.session = builder.session != null ? builder.session : new InMemorySession();
        this.jdbcTemplate = builder.jdbcTemplate;
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

        // Resolve agent ID
        String agentId = resolveAgentId(input, headerAgentId, pathAgentId);

        // Resolve agent
        Agent agent = agentResolver.resolveAgent(agentId, threadId);

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

        // 设置租户信息
        Object tenantId = AgentMetadataStore.get(agent.getAgentId(), "tenantId");
        Object tenantCode = AgentMetadataStore.get(agent.getAgentId(), "tenantCode");
        if (tenantId == null || tenantCode == null) {
            throw new IllegalStateException("Tenant information not found");
        }
        TenantUtils.setCurrentTenant(Long.valueOf(tenantId.toString()), tenantCode.toString());

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
        ResumeContext rc = loadResumeContext(threadId);
        if (rc == null) {
            throw new IllegalStateException("找不到会话或暂停态: " + threadId);
        }
        TenantUtils.setCurrentTenant(rc.tenantId, rc.tenantCode);

        Agent agent = agentResolver.resolveAgent(rc.agentCode, threadId);
        if (agent instanceof ReActAgent reActAgent) {
            reActAgent.loadFrom(session, threadId);
        }

        List<Msg> resumeInput = buildResumeInput(decisions);

        String runId = UUID.randomUUID().toString();
        AguiAgentAdapter adapter = new AguiAgentAdapter(agent, config);
        Flux<AguiEvent> events = adapter.runWithMessages(resumeInput, threadId, runId)
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
            return List.of();
        }
        ResumeContext rc = loadResumeContext(threadId);
        if (rc == null) {
            return List.of();
        }
        TenantUtils.setCurrentTenant(rc.tenantId, rc.tenantCode);
        try {
            // 重建 agent 会重新构建 toolkit → 重新登记 need_confirm 工具（isNeedConfirm 才有依据），
            // 再 loadFrom 加载暂停态。二者顺序不能反。
            Agent agent = agentResolver.resolveAgent(rc.agentCode, threadId);
            if (!(agent instanceof ReActAgent reActAgent) || reActAgent.getMemory() == null) {
                return List.of();
            }
            reActAgent.loadFrom(session, threadId);

            List<Msg> messages = reActAgent.getMemory().getMessages();
            if (messages == null || messages.isEmpty()) {
                return List.of();
            }
            Msg last = messages.get(messages.size() - 1);
            if (last.getRole() != MsgRole.ASSISTANT) {
                return List.of();
            }
            List<Map<String, Object>> pending = new ArrayList<>();
            for (ToolUseBlock toolUse : last.getContentBlocks(ToolUseBlock.class)) {
                if (IConfirmationHook.isNeedConfirm(toolUse.getName())) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("toolUseId", toolUse.getId());
                    item.put("name", toolUse.getName());
                    item.put("input", toolUse.getInput());
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
     */
    private static final String REJECT_RESULT_TEXT =
            "Error: 用户拒绝授权调用该工具，本轮对话中该工具不可用。请勿重试该工具，"
                    + "更不得自行编造、虚构或凭常识臆测该工具本应返回的结果数据；"
                    + "必须如实告知用户：因未获授权调用该工具，无法获取相关信息。";

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

    /** resume 逐工具决策。 */
    public record ResumeDecision(String toolUseId, String name, boolean approved) {}

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
