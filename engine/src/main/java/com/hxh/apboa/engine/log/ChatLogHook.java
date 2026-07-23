package com.hxh.apboa.engine.log;

import com.hxh.apboa.common.entity.ChatMessage;
import com.hxh.apboa.common.util.AgentMetadataStore;
import com.hxh.apboa.common.util.BeanUtils;
import com.hxh.apboa.common.util.JsonUtils;
import com.hxh.apboa.engine.hook.builtins.IConfirmationHook;
import com.hxh.apboa.engine.log.telemetry.RunStatAccumulator;
import com.hxh.apboa.engine.log.telemetry.RunTelemetryExtractor;
import org.springframework.jdbc.core.JdbcTemplate;
import io.agentscope.core.agent.AgentBase;
import io.agentscope.core.hook.*;
import io.agentscope.core.message.*;
import reactor.core.publisher.Mono;

import lombok.extern.slf4j.Slf4j;

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
@Slf4j
public class ChatLogHook implements Hook {
    /** 工具调用入参缓存（key = toolUseId 全局唯一；static 供拒绝补偿静态入口跨实例访问） */
    private static final Map<String, Map<String, Object>> TOOL_CACHE_MAP = new ConcurrentHashMap<>();

    /**
     * 工具权威耗时表（toolUseId → elapsed）：全链路唯一计时者是本 Hook（落库权威），
     * 每算出一个耗时（主工具/子步骤、正常完成/拒绝补偿）即存表，下发链（Adapter 的
     * Custom(TOOL_ELAPSED)、SUBAGENT_STEP 完成步）经 {@link #pollToolElapsed} 取走同一个值
     * ——实时显示与落库同一次测量，刷新前后零误差。消费即删；防泄漏上限兜底。
     */
    private static final Map<String, Long> TOOL_ELAPSED_MAP = new ConcurrentHashMap<>();
    private static final int TOOL_ELAPSED_MAP_LIMIT = 2000;

    /** 取走某工具的权威耗时（读后删）；无值返回 null（下发链宁缺毋错，不自行计时） */
    public static Long pollToolElapsed(String toolUseId) {
        return toolUseId == null ? null : TOOL_ELAPSED_MAP.remove(toolUseId);
    }

    /** 查看某工具的权威耗时（不删——落库读取在 Adapter poll 取走之前，两个消费者共享同一测量） */
    public static Long peekToolElapsed(String toolUseId) {
        return toolUseId == null ? null : TOOL_ELAPSED_MAP.get(toolUseId);
    }

    /**
     * 存权威耗时（异常场景消费缺席导致的滞留由上限兜底清空）。
     * public：真实单工具起止只有 ToolExecutor 能测到（订阅→结果，排队不计入），
     * 由它在每个工具完成瞬间喂入；本 Hook 的 PostActing 批级计时退为兜底
     */
    public static void offerToolElapsed(String toolUseId, long elapsed) {
        if (toolUseId == null) {
            return;
        }
        if (TOOL_ELAPSED_MAP.size() > TOOL_ELAPSED_MAP_LIMIT) {
            TOOL_ELAPSED_MAP.clear();
        }
        TOOL_ELAPSED_MAP.put(toolUseId, elapsed);
    }

    /**
     * HITL 改参后同步修正入参缓存（EditedInputApplier 调用）：入参在暂停前的
     * PostReasoning 就已缓存为模型原始参数，resume 续跑不再触发 PostReasoning，
     * 不修正则落库 tool 消息的 args 与实际执行参数不一致。
     * 跨实例 resume 时缓存本就不在本进程（该场景工具消息现状即不落库），保持既有行为。
     */
    public static void updateToolArgs(String toolUseId, String newArgsJson) {
        if (toolUseId == null || newArgsJson == null || newArgsJson.isEmpty()) {
            return;
        }
        Map<String, Object> cache = TOOL_CACHE_MAP.get(toolUseId);
        if (cache == null) {
            return;
        }
        TOOL_CACHE_MAP.put(toolUseId, Map.of(
                "tool_name", cache.get("tool_name"),
                "tool_args", newArgsJson,
                "tool_start", cache.get("tool_start")
        ));
    }

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
                            // 权威耗时优先取 ToolExecutor 完成瞬间喂入的单工具真实值（peek 不删，
                            // Adapter 随后 poll 取走下发 TOOL_ELAPSED——落库与实时同一次测量）；
                            // 表无值（异常路径）才退回批级兜底：PostActing 是整批工具完成后的事件，
                            // 此处起止算的是批时长，串行多工具时每个都≈总时长（历史 bug 根源）
                            Long realElapsed = peekToolElapsed(toolId);
                            long elapsed = realElapsed != null
                                    ? realElapsed
                                    : System.currentTimeMillis() - (Long) toolCache.get("tool_start");
                            if (realElapsed == null) {
                                offerToolElapsed(toolId, elapsed);
                            }
                            ConcurrentLogProducer.pushLog(buildChatMessage(
                                    Long.valueOf(threadId),
                                    "tool",
                                    buildToolContent(
                                            toolName,
                                            elapsed,
                                            toolCache.get("tool_args").toString(),
                                            toolRes,
                                            pollSubProcess(threadId, toolName),
                                            // 走到这里=真实执行完成，被拒工具无 PostActing（补偿路径落 rejected）
                                            IConfirmationHook.isNeedConfirm(toolName) ? "approved" : null),
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
    private static String buildToolContent(String toolName, Long totalTimes, String args, String result,
                                    List<Map<String, Object>> subProcess) {
        return buildToolContent(toolName, totalTimes, args, result, subProcess, null);
    }

    /**
     * @param confirmState HITL 确认态标记："approved"（need_confirm 工具被放行执行，含一键授权）/
     *                     "rejected"（用户/自动拒绝）；非确认工具传 null 不落该字段。
     *                     历史消息据此渲染确认状态徽标与定制确认卡只读回显。
     */
    private static String buildToolContent(String toolName, Long totalTimes, String args, String result,
                                    List<Map<String, Object>> subProcess, String confirmState) {
        Map<String, Object> toolContent = new HashMap<>() {{
            put("name", toolName);
            put("totalTimes", totalTimes);
            put("args", args);
            put("result", result);
        }};
        if (subProcess != null && !subProcess.isEmpty()) {
            toolContent.put("subProcess", subProcess);
        }
        if (confirmState != null) {
            toolContent.put("confirmState", confirmState);
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
            // 权威耗时：算一次，落库步骤与实时 SUBAGENT_STEP 完成步（Adapter 取走）同一个值
            long elapsed = System.currentTimeMillis() - startMs;
            step.put("elapsed", elapsed);
            offerToolElapsed(toolUseId, elapsed);
        }
    }

    /**
     * 主 agent HITL 工具被拒（手动 resume 拒绝 / 拒绝授权自动全拒）：工具未真实执行、
     * 无 PostActingEvent，落库 tool 消息由此补偿（TOOL_CACHE_MAP 在发起轮的
     * PostReasoningEvent 已缓存入参），否则实时流的拒绝工具卡刷新后丢失。
     * toolProcessActive 关闭时缓存本就不写，天然对齐「不落工具消息」。
     * 耗时口径 = 发起到拒绝处置的间隔（自动拒毫秒级、手动拒含用户决策等待）。
     * 返回落库的同一个 elapsed，调用方随 Custom(TOOL_ELAPSED) 下发前端——实时与落库
     * 同一次测量，刷新前后零误差。
     *
     * @return 落库的 elapsed（毫秒）；缓存缺失（重启丢失/未开工具过程）或落库失败返回 null
     */
    public static Long completeMainToolRejected(String threadId, String toolUseId, String resultText) {
        if (threadId == null || toolUseId == null) {
            return null;
        }
        Map<String, Object> toolCache = TOOL_CACHE_MAP.remove(toolUseId);
        if (toolCache == null) {
            return null;
        }
        try {
            Long tenantId = THREAD_TENANT_CACHE.computeIfAbsent(threadId, ChatLogHook::queryTenantIdBySession);
            if (tenantId == null) {
                return null;
            }
            long elapsed = System.currentTimeMillis() - (Long) toolCache.get("tool_start");
            ConcurrentLogProducer.pushLog(buildChatMessage(
                    Long.valueOf(threadId),
                    "tool",
                    buildToolContent(
                            toolCache.get("tool_name").toString(),
                            elapsed,
                            toolCache.get("tool_args").toString(),
                            resultText,
                            null,
                            "rejected"),
                    tenantId));
            return elapsed;
        } catch (Exception e) {
            log.warn("拒绝工具落库补偿失败 threadId={} toolUseId={}: {}", threadId, toolUseId, e.getMessage());
            return null;
        }
    }

    /**
     * 子智能体 HITL 确认被拒：工具未真实执行、不会有 PostActingEvent 来配对，
     * 由 SubAgentTool 在决策拒绝时直接补拒绝结果，否则落库工具步无 result、
     * 历史渲染兜底显示「完成」造成误导。
     * 耗时口径同主工具补偿（发起到拒绝处置的间隔），返回给调用方随直发拒绝步
     * 下发前端——实时与落库同一个值，刷新前后显示一致。
     *
     * @return 补偿的 elapsed（毫秒）；步骤不存在或无起始时刻时返回 null
     */
    public static Long completeSubToolStepRejected(String agentId, String toolUseId, String resultText) {
        if (agentId == null || toolUseId == null) {
            return null;
        }
        Map<String, Object> step = SUB_TOOL_PENDING.remove(agentId + "|" + toolUseId);
        if (step == null) {
            return null;
        }
        step.put("result", RunTelemetryExtractor.truncate(resultText));
        Long elapsed = null;
        if (step.remove("_start") instanceof Long startMs) {
            elapsed = System.currentTimeMillis() - startMs;
            step.put("elapsed", elapsed);
        }
        return elapsed;
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
    private static ChatMessage buildChatMessage(Long sessionId, String role, String content, Long tenantId) {
        return buildChatMessage(sessionId, role, content, tenantId, null);
    }

    // 构建聊天消息（带 meta 元数据）
    private static ChatMessage buildChatMessage(Long sessionId, String role, String content, Long tenantId, String meta) {
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
