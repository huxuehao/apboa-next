package com.hxh.apboa.engine.log;

import com.hxh.apboa.common.entity.ChatMessage;
import com.hxh.apboa.common.util.AgentMetadataStore;
import com.hxh.apboa.common.util.BeanUtils;
import com.hxh.apboa.common.util.JsonUtils;
import com.hxh.apboa.engine.log.telemetry.RunStatAccumulator;
import com.hxh.apboa.engine.log.telemetry.RunTelemetryExtractor;
import org.springframework.jdbc.core.JdbcTemplate;
import io.agentscope.core.agent.AgentBase;
import io.agentscope.core.hook.*;
import io.agentscope.core.message.*;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 描述：保存聊天记录hook
 *
 * @author huxuehao
 **/
public class ChatLogHook implements Hook {
    private final Map<String, Map<String, Object>> TOOL_CACHE_MAP = new ConcurrentHashMap<>();

    /**
     * run 级统计：threadId → 累积器（提取/字段契约见 telemetry 包）。
     * 首轮 PreReasoningEvent 时创建（首轮 LLM 调用发起前，耗时覆盖全程；实测若用 PostReasoning
     * 起点会漏掉首轮模型调用耗时，13s 的 run 只记 5.9s），assistant 正文落库 / ErrorEvent 时清理
     */
    private final Map<String, RunStatAccumulator> RUN_STAT_MAP = new ConcurrentHashMap<>();

    /** 每个认领键下积压的已完成子过程上限（主 tool 消息不落库时防堆积） */
    private static final int SUB_DONE_QUEUE_LIMIT = 8;

    /**
     * 子智能体中间过程收集（跨 Hook 实例：主/子 agent 各持有独立的 ChatLogHook 实例，必须 static 共享）。
     * SUB_STEPS_MAP：进行中收集，key = 子 agentId；
     * SUB_TOOL_PENDING：子智能体进行中的工具步，key = 子agentId|toolUseId，
     *   tool_use 建步骤（含起始时间），tool_result 按 id 找回补 result/elapsed——合并为一个 "tool" 步；
     * SUB_DONE_MAP：已完成待认领，key = 主threadId|工具名，主 agent 落 tool 消息时按序出队认领。
     * 同一主会话并行调用多个同名子智能体时按完成顺序认领（极端场景可能错位，可接受）
     */
    private static final Map<String, List<Map<String, Object>>> SUB_STEPS_MAP = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, Object>> SUB_TOOL_PENDING = new ConcurrentHashMap<>();
    private static final Map<String, Deque<List<Map<String, Object>>>> SUB_DONE_MAP = new ConcurrentHashMap<>();
    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        // 首轮推理发起前建立 run 统计起点（后续轮的 Pre 事件因 key 已存在而无操作）
        if (event instanceof PreReasoningEvent) {
            String threadId = extractThreadId(event);
            if (threadId != null) {
                RUN_STAT_MAP.computeIfAbsent(threadId, k -> new RunStatAccumulator());
            }
            return Mono.just(event);
        }

        if (event instanceof PostReasoningEvent || event instanceof PostActingEvent || event instanceof ErrorEvent) {
            // 解析租户ID和线程ID
            Long tenantId = extractTenantId(event);
            String threadId = extractThreadId(event);
            if (tenantId == null || threadId == null) {
                // 无主会话身份：若是子智能体（ToolkitFactory 创建时登记过归属），收集其中间过程
                collectSubAgentStep(event);
                return Mono.just(event);
            }

            // 处理错误事件
            if (event instanceof ErrorEvent errorEvent) {
                // run 异常结束：丢弃本次统计防泄漏
                RUN_STAT_MAP.remove(threadId);
                Throwable error = errorEvent.getError();
                ConcurrentLogProducer.pushLog(buildErrorMessage(
                        Long.valueOf(threadId),
                        error.getMessage(),
                        tenantId));

                return Mono.just(event);
            }

            // 解析工具调用是否活跃
            boolean toolProcessActive = extractToolProcessActive(event);

            // 处理 reasoning 后置事件
            if (event instanceof PostReasoningEvent postReasoningEvent) {
                Msg reasoningMessage = postReasoningEvent.getReasoningMessage();

                // run 级统计：每轮 LLM 推理轮次 +1、token 累加（累积逻辑与实时侧 RUN_META 共用）
                RUN_STAT_MAP.computeIfAbsent(threadId, k -> new RunStatAccumulator())
                        .onReasoningComplete(reasoningMessage);

                List<ContentBlock> content = reasoningMessage.getContent();
                if (content.isEmpty()) {
                    return Mono.just(event);
                }

                for (ContentBlock block : content) {
                    if (block instanceof TextBlock textBlock) {
                        // 正文保存：挂 run 级 meta（耗时/轮次/token），并结束本次统计
                        String longTextContent = getLongTextContent(textBlock);
                        if (longTextContent != null) {
                            ConcurrentLogProducer.pushLog(buildChatMessage(
                                    Long.valueOf(threadId),
                                    "assistant",
                                    longTextContent,
                                    tenantId,
                                    buildMeta(RUN_STAT_MAP.remove(threadId))));
                        }
                    } else if (block instanceof ThinkingBlock thinkingBlock) {
                        // 思考保存
                        String longThinkingContent = getLongThinkingContent(thinkingBlock);
                        if (longThinkingContent != null) {
                            ConcurrentLogProducer.pushLog(buildChatMessage(
                                    Long.valueOf(threadId),
                                    "thinking",
                                    longThinkingContent,
                                    tenantId));
                        }
                    } else if (block instanceof ToolUseBlock toolUseBlock && toolProcessActive) {
                        String longToolUseContent = getLongToolUseContent(toolUseBlock);
                        if (longToolUseContent != null) {
                            String toolCallId = toolUseBlock.getId();
                            if (toolCallId == null) {
                                toolCallId = UUID.randomUUID().toString();
                            }
                            // 工具调用入参暂保存
                            TOOL_CACHE_MAP.put(toolCallId, Map.of(
                                    "tool_name", toolUseBlock.getName(),
                                    "tool_args", longToolUseContent,
                                    "tool_start", System.currentTimeMillis()
                            ));
                        }

                    }
                }
            }
            // 处理 acting 后置事件
            else if (toolProcessActive) {
                PostActingEvent postActingEvent = (PostActingEvent) event;
                Msg reasoningMessage = postActingEvent.getToolResultMsg();
                List<ContentBlock> content = reasoningMessage.getContent();
                if (content.isEmpty()) {
                    return Mono.just(event);
                }

                // 工具结果保存
                for (ContentBlock block : content) {
                    if (block instanceof ToolResultBlock toolResult) {
                        String toolId = toolResult.getId();
                        try {
                            Map<String, Object> toolCache = TOOL_CACHE_MAP.get(toolId);
                            if (toolCache == null) {
                                return Mono.just(event);
                            }
                            String toolRes = RunTelemetryExtractor.toolResultText(toolResult);
                            String toolName = toolCache.get("tool_name").toString();
                            ConcurrentLogProducer.pushLog(buildChatMessage(
                                    Long.valueOf(threadId),
                                    "tool",
                                    buildToolContent(
                                            toolName,
                                            System.currentTimeMillis() - (Long) toolCache.get("tool_start"),
                                            toolCache.get("tool_args").toString(),
                                            toolRes,
                                            pollSubProcess(threadId, toolName)),
                                    tenantId));
                        } finally {
                            TOOL_CACHE_MAP.remove(toolId);
                        }
                    }
                }
            }
        }

        return Mono.just(event);
    }

    /** 租户兜底缓存：threadId → tenantId（metadata 缺失时按会话查库一次） */
    private static final Map<String, Long> THREAD_TENANT_CACHE = new ConcurrentHashMap<>();

    private Long extractTenantId(HookEvent event) {
        if (event.getAgent() instanceof AgentBase agentBase) {
            Long tenantId = AgentMetadataStore.get(agentBase.getAgentId(), "tenantId");
            if (tenantId != null) {
                return tenantId;
            }
            // 兜底：部分链路（runtime 重启后旧会话首条消息）AgentContext 无租户、metadata 缺失，
            // 按 threadId 查 chat_session 补齐，否则整轮回复不落库
            String threadId = AgentMetadataStore.get(agentBase.getAgentId(), "threadId");
            if (threadId != null) {
                return THREAD_TENANT_CACHE.computeIfAbsent(threadId, ChatLogHook::queryTenantIdBySession);
            }
        }
        return null;
    }

    /** 按会话 ID 查租户（查不到返回 null，computeIfAbsent 不缓存负结果，下次重试） */
    private static Long queryTenantIdBySession(String threadId) {
        try {
            JdbcTemplate jdbcTemplate = BeanUtils.getBean(JdbcTemplate.class);
            return jdbcTemplate.query(
                    "SELECT tenant_id FROM chat_session WHERE id = ?",
                    rs -> rs.next() ? rs.getLong("tenant_id") : null,
                    Long.valueOf(threadId));
        } catch (Exception e) {
            return null;
        }
    }
    private String extractThreadId(HookEvent event) {
        if (event.getAgent() instanceof AgentBase agentBase) {
            return AgentMetadataStore.get(agentBase.getAgentId(), "threadId");
        }
        return null;
    }
    private boolean extractToolProcessActive(HookEvent event) {
        if (event.getAgent() instanceof AgentBase agentBase) {
            Object o = AgentMetadataStore.get(agentBase.getAgentId(), "toolProcessActive");
            if (o instanceof Boolean b) return b;
        }
        return false;
    }

    private String getLongTextContent(TextBlock textBlock) {
        String text = textBlock.getText();
        if (text.isEmpty()) {
            return null;
        }
        return text;
    }

    // 获取思考内容
    private String getLongThinkingContent(ThinkingBlock thinkingBlock) {
        String thinking = thinkingBlock.getThinking();
        if (thinking.isEmpty()) {
            return null;
        }
        return thinking;
    }


    // 获取中文工具使用内容
    private String getLongToolUseContent(ToolUseBlock toolUseBlock) {
        String content = toolUseBlock.getContent();
        if (content.isEmpty()) {
            return null;
        }

        return content;
    }

    // 构建工具内容（subProcess 为子智能体中间过程步骤，非子智能体工具为 null 时不写该字段，保持旧格式）
    private String buildToolContent(String toolName, Long totalTimes, String args, String result,
                                    List<Map<String, Object>> subProcess) {
        Map<String, Object> toolContent = new HashMap<>() {{
            put("name", toolName);
            put("totalTimes", totalTimes);
            put("args", args);
            put("result", result);
        }};
        if (subProcess != null && !subProcess.isEmpty()) {
            toolContent.put("subProcess", subProcess);
        }
        return JsonUtils.toJsonStr(toolContent);
    }

    // ─── 子智能体中间过程收集 ─────────────────────────────────────

    /**
     * 收集子智能体的 Hook 事件为过程步骤。
     * 子智能体与主 agent 走同一套 ChatLogHook，但无主会话身份（threadId 为 null），
     * 凭 ToolkitFactory 登记的 subParentThreadId 识别；步骤截断存储，仅作过程概览
     */
    private void collectSubAgentStep(HookEvent event) {
        if (!(event.getAgent() instanceof AgentBase agentBase)) {
            return;
        }
        String agentId = agentBase.getAgentId();
        String parentThreadId = AgentMetadataStore.get(agentId, "subParentThreadId");
        if (parentThreadId == null) {
            return;
        }

        if (event instanceof ErrorEvent errorEvent) {
            String msg = errorEvent.getError() != null ? errorEvent.getError().getMessage() : "unknown error";
            addSubStep(agentId, RunTelemetryExtractor.errorStep(RunTelemetryExtractor.truncate(msg)));
            // 错误也归档：主 tool 消息仍可带上已收集的部分过程
            finishSubProcess(agentId, parentThreadId);
            return;
        }

        if (event instanceof PostReasoningEvent postReasoningEvent) {
            // 遍历/截断/步骤构造走共享提取器（与实时侧 SUBAGENT_STEP 同构），配对计时状态自持
            boolean hasFinalText = RunTelemetryExtractor.visitReasoning(
                    postReasoningEvent.getReasoningMessage(),
                    new RunTelemetryExtractor.StepVisitor() {
                        @Override
                        public void onPlainStep(Map<String, Object> step) {
                            addSubStep(agentId, step);
                        }

                        @Override
                        public void onToolUse(ToolUseBlock block) {
                            startSubToolStep(agentId, block);
                        }
                    });
            // 子智能体出正文 = 本次调用结束，归档待主 tool 消息认领
            if (hasFinalText) {
                finishSubProcess(agentId, parentThreadId);
            }
        } else if (event instanceof PostActingEvent postActingEvent) {
            RunTelemetryExtractor.visitToolResults(
                    postActingEvent.getToolResultMsg(),
                    toolResultBlock -> completeSubToolStep(agentId, toolResultBlock));
        }
    }

    private void addSubStep(String agentId, Map<String, Object> step) {
        Object content = step.get("content");
        if (content == null || String.valueOf(content).isEmpty()) {
            return;
        }
        SUB_STEPS_MAP.computeIfAbsent(agentId, k -> new ArrayList<>()).add(step);
    }

    /** 子智能体发起工具调用：建 "tool" 步骤（含起始时间），入列表并按 toolUseId 挂 pending 等配对 */
    private void startSubToolStep(String agentId, ToolUseBlock toolUseBlock) {
        Map<String, Object> step = RunTelemetryExtractor.toolStep(toolUseBlock.getName(), toolUseBlock.getContent());
        step.put("_start", System.currentTimeMillis());
        SUB_STEPS_MAP.computeIfAbsent(agentId, k -> new ArrayList<>()).add(step);

        String toolUseId = toolUseBlock.getId();
        if (toolUseId != null) {
            SUB_TOOL_PENDING.put(agentId + "|" + toolUseId, step);
        }
    }

    /** 子智能体工具执行完成：按 toolUseId 找回步骤补 result / elapsed（配不上则忽略，防孤儿结果） */
    private void completeSubToolStep(String agentId, ToolResultBlock toolResultBlock) {
        String toolUseId = toolResultBlock.getId();
        if (toolUseId == null) {
            return;
        }
        Map<String, Object> step = SUB_TOOL_PENDING.remove(agentId + "|" + toolUseId);
        if (step == null) {
            return;
        }
        step.put("result", RunTelemetryExtractor.truncate(RunTelemetryExtractor.toolResultText(toolResultBlock)));
        Object start = step.remove("_start");
        if (start instanceof Long startMs) {
            step.put("elapsed", System.currentTimeMillis() - startMs);
        }
    }

    /** 子智能体本次调用结束：步骤列表按 主threadId|工具名 入队，等主 agent 的 tool 消息落库时认领 */
    private void finishSubProcess(String agentId, String parentThreadId) {
        List<Map<String, Object>> steps = SUB_STEPS_MAP.remove(agentId);
        // 清理该子 agent 未配对的工具 pending（异常中断残留），并去掉内部字段 _start
        SUB_TOOL_PENDING.keySet().removeIf(k -> k.startsWith(agentId + "|"));
        if (steps == null || steps.isEmpty()) {
            return;
        }
        steps.forEach(s -> s.remove("_start"));
        String toolName = AgentMetadataStore.get(agentId, "subToolName");
        String key = parentThreadId + "|" + (toolName == null ? "" : toolName);
        Deque<List<Map<String, Object>>> queue =
                SUB_DONE_MAP.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        queue.addLast(steps);
        // 防堆积：主 tool 消息可能不落库（如关闭工具过程展示），超限丢最老
        while (queue.size() > SUB_DONE_QUEUE_LIMIT) {
            queue.pollFirst();
        }
    }

    /** 主 agent 落 tool 消息时认领对应的子过程（无则返回 null，普通工具走此路径） */
    private List<Map<String, Object>> pollSubProcess(String threadId, String toolName) {
        Deque<List<Map<String, Object>>> queue = SUB_DONE_MAP.get(threadId + "|" + toolName);
        return queue == null ? null : queue.pollFirst();
    }

    // 构建聊天消息
    private ChatMessage buildChatMessage(Long sessionId, String role, String content, Long tenantId) {
        return buildChatMessage(sessionId, role, content, tenantId, null);
    }

    // 构建聊天消息（带 meta 元数据）
    private ChatMessage buildChatMessage(Long sessionId, String role, String content, Long tenantId, String meta) {
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setSessionId(sessionId);
        chatMessage.setRole(role);
        chatMessage.setContent(content);
        chatMessage.setTenantId(tenantId);
        chatMessage.setMeta(meta);

        return chatMessage;
    }

    /**
     * 构建 run 级元数据 JSON：durationMs 为墙钟总耗时（含工具执行与 HITL 等待），
     * token 为全 run 各轮模型调用累计。usage 全程未取到（provider 不回）时 token 记 0，前端按字段值兜底不展示
     */
    private String buildMeta(RunStatAccumulator stat) {
        if (stat == null) {
            return null;
        }
        return JsonUtils.toJsonStr(stat.buildMeta());
    }

    // 构建错误消息
    private ChatMessage buildErrorMessage(Long sessionId, String content, Long tenantId) {
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setSessionId(sessionId);
        chatMessage.setRole("error");
        chatMessage.setContent(content);
        chatMessage.setTenantId(tenantId);

        return chatMessage;
    }
}
