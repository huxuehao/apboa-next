package com.hxh.apboa.engine.agui;

/**
 * AGUI 自定义事件注册表（Custom 事件 name 与载荷契约的唯一出处）。
 *
 * <p>AGUI 标准事件是框架 jar 内封死的 record，扩展信息一律走 {@code AguiEvent.Custom} 通道；
 * 新增扩展事件必须在此登记常量并注明载荷结构，前端 useChatStream.onCustom 按同名分支消费。
 *
 * @author huxuehao
 */
public final class AguiCustomEvents {

    private AguiCustomEvents() {}

    /**
     * HITL 工具确认请求（推理后暂停时下发）。
     * 载荷：{@code {pending: [{toolUseId, name, input}]}}
     */
    public static final String TOOL_CONFIRM_REQUIRED = "TOOL_CONFIRM_REQUIRED";

    /**
     * 子智能体过程步骤增量（子智能体每完成一个节点实时下发，步骤结构与落库 subProcess 元素同构，
     * 由 RunTelemetryExtractor 统一生成）。
     * 载荷：{@code {parentToolCallId, subagentName, sessionId, subToolUseId?, step}}
     * <ul>
     *   <li>流式增量步：step = {type: thinking|text, delta}——前端逐字追加到同类型进行中步骤，
     *       轮末完整步到达时定稿替换（内容以完整步为准，与落库一致）</li>
     *   <li>普通步（轮末完整）：step = {type: thinking|text|error, content}</li>
     *   <li>工具步发起：step = {type: tool, name, args, running: true}，附 subToolUseId 供完成时配对</li>
     *   <li>工具步完成：step = {type: tool, name, result, elapsed}，附 subToolUseId，前端按其合并</li>
     * </ul>
     */
    public static final String SUBAGENT_STEP = "SUBAGENT_STEP";

    /**
     * run 级元数据（run 收尾、RUN_FINISHED 之前下发；字段与落库 meta 同构，
     * 由 RunStatAccumulator 统一生成）。
     * 载荷：{@code {durationMs, iterationCount, inputTokens, outputTokens, totalTokens}}
     */
    public static final String RUN_META = "RUN_META";
}
