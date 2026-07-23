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

    /**
     * 子智能体 HITL 确认请求（子智能体内需确认工具触发暂停、SubAgentTool 挂起等待时下发）。
     * 载荷：{@code {parentToolCallId, subagentName, subSessionId, pending: [{toolUseId, name, input}]}}
     * <ul>
     *   <li>前端按 parentToolCallId 定位工具卡片，把 pending 标注到对应 subSteps 工具步上
     *       渲染「允许/禁止」；决策齐了调 {@code POST /agui/subagent/resume}
     *       （body: {subSessionId, decisions:[{toolUseId, name, approved}]}）唤醒续跑</li>
     *   <li>刷新/重进会话经 {@code GET /agui/subagent/pending?threadId=} 重建（载荷同构，
     *       字段名对齐 PendingSubConfirmRegistry.PendingInfo）</li>
     *   <li>挂起超时（默认 10 分钟）后端按全拒绝自动续跑，前端确认 UI 决策提交会得到
     *       resumed=false，按已失效清理即可</li>
     * </ul>
     */
    public static final String SUBAGENT_CONFIRM_REQUIRED = "SUBAGENT_CONFIRM_REQUIRED";

    /**
     * 单工具完成即时通知（工具真正执行完的瞬间下发，不等同批其余工具跑完——
     * 批量结果受 ToolExecutor 的 collectList 屏障约束，要到整批完成才随消息流走）。
     * 载荷：{@code {toolUseId, elapsed}}，elapsed 为该工具真实执行耗时
     * （订阅→结果，串行时排队等待不计入；与落库/TOOL_ELAPSED 同一次测量）。
     * <ul>
     *   <li>前端收到即把对应工具卡片翻转完成态并定格耗时；结果详情仍等 ToolCallResult</li>
     *   <li>串行执行下第 N 个完成同时意味着第 N+1 个开始执行（排队态→执行态推导依据）</li>
     *   <li>HITL 挂起的工具不发本事件（挂起在等人，不是完成）</li>
     * </ul>
     */
    public static final String TOOL_FINISHED = "TOOL_FINISHED";

    /**
     * 主 agent 工具调用的权威耗时（唯一计时者 ChatLogHook 的落库同源值，实时下发消除
     * 前端掐表与落库两次测量的误差；先于对应 ToolCallResult 下发）。
     * 载荷：{@code {toolUseId, elapsed}}
     * <ul>
     *   <li>正常完成：Adapter 转换 TOOL_RESULT 时经 pollToolElapsed 取走落库值随发</li>
     *   <li>拒绝（自动/手动）：落库补偿函数的返回值随拒绝结果事件下发</li>
     *   <li>前端 onCustom 记 toolUseId→elapsed 权威表，onToolCallResult 只查表不掐表，
     *       表无值时不显示耗时（宁缺毋错）；子过程步的权威耗时不走本事件，
     *       随 SUBAGENT_STEP 完成步的 elapsed 字段下发（同一出处）</li>
     * </ul>
     */
    public static final String TOOL_ELAPSED = "TOOL_ELAPSED";
}
