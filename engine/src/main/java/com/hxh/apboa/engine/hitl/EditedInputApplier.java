package com.hxh.apboa.engine.hitl;

import com.hxh.apboa.common.util.JsonUtils;
import com.hxh.apboa.engine.hook.builtins.IConfirmationHook;
import com.hxh.apboa.engine.log.ChatLogHook;
import io.agentscope.core.memory.Memory;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ToolUseBlock;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * HITL 改参写回器：resume 续跑前，把用户在确认 UI 中修改后的工具参数写回暂停态记忆。
 *
 * <p>agent 续跑执行 pending 工具时取的就是记忆中最后一条 ASSISTANT 消息里
 * {@link ToolUseBlock} 的 input（ReActAgent.extractPendingToolCalls），故在此改写后
 * 无需其他透传，acting 自然以新参数执行；改写后模型视角即"自己本就这么调的"，
 * 后续推理、落库、历史回放全部自洽。主 resume（AguiRequestProcessor）与
 * 子智能体确认（SubAgentTool）共用本类。
 *
 * <p>改写约束（缺一不可，均为实测确认的框架行为）：
 * <ul>
 *   <li>{@code ToolUseBlock}/{@code Msg} 均不可变，只能 builder 重建后
 *       {@code deleteMessage + addMessage} 整条替换（Memory 无原地替换 API，
 *       且 getMessages 返回副本）；重建消息仍在末位，暂停态结构判据不破坏。</li>
 *   <li>input 与 content 双表示必须同步：ToolExecutor 执行取 input(Map)，
 *       但 schema 校验取 content(JSON 串)，只改一边会校验旧值、执行新值。</li>
 *   <li>metadata 原样保留：generateReason 与 Gemini thoughtSignature 都存在
 *       metadata 里，丢失会破坏事件判定/供应商协议。</li>
 *   <li>仅改写 need_confirm 登记命中的工具：防止借 resume 通道篡改同轮
 *       非确认工具的参数。</li>
 * </ul>
 *
 * @author vaulka
 */
@Slf4j
public final class EditedInputApplier {

    private EditedInputApplier() {}

    /**
     * ToolUseBlock.metadata 标记：本次调用参数经用户在确认环节修改。
     * ToolExecutor 据此在执行结果尾部追加「用户改参」系统提示——改写抹掉了
     * "用户修改"事件本身，模型面对「用户原话 ↔ 执行参数/结果」的冲突会归因
     * "自己传错了"并重调纠正；把事件证据注入结果文本即消解该冲突。
     * metadata 为框架内部字段（不进 LLM 请求体），随暂停态序列化跨实例不丢。
     */
    public static final String META_USER_EDITED_INPUT = "userEditedInput";

    /**
     * ToolUseBlock.metadata：用户实际修改的字段级 diff（人类可读串，如
     * "quantity: 2 → 3"）。提示必须精确到字段——笼统说"参数已修改为当前入参"
     * 会把模型自己生成的其余参数也归因为用户意志，误导模型的后续归因与复述。
     */
    public static final String META_USER_EDITED_DIFF = "userEditedDiff";

    /** 计算字段级修改摘要：仅列出值有变化/新增/移除的字段，其余字段不提。 */
    private static String buildEditedDiff(Map<String, Object> original, Map<String, Object> edited) {
        Map<String, Object> old = original == null ? Map.of() : original;
        StringBuilder diff = new StringBuilder();
        java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>(old.keySet());
        keys.addAll(edited.keySet());
        for (String key : keys) {
            Object o = old.get(key);
            Object n = edited.get(key);
            if (java.util.Objects.equals(o, n)) {
                continue;
            }
            if (diff.length() > 0) {
                diff.append("；");
            }
            if (!old.containsKey(key)) {
                diff.append(key).append(": (新增) ").append(plain(n));
            } else if (!edited.containsKey(key)) {
                diff.append(key).append(": (移除，原值 ").append(plain(o)).append(")");
            } else {
                diff.append(key).append(": ").append(plain(o)).append(" → ").append(plain(n));
            }
        }
        return diff.toString();
    }

    /** 简单值直接展示，复杂结构 JSON 序列化 */
    private static String plain(Object v) {
        if (v == null) {
            return "null";
        }
        if (v instanceof String || v instanceof Number || v instanceof Boolean) {
            return String.valueOf(v);
        }
        return JsonUtils.toJsonStr(v);
    }

    /**
     * 把用户修改后的参数写回记忆中最后一条 ASSISTANT 消息的对应工具块。
     *
     * @param memory            暂停态 agent 的记忆（主链路 loadFrom 之后 / 子链路内存态直取）
     * @param editedByToolUseId toolUseId → 修改后的完整参数（仅 approved 且非空的才应传入）
     * @return 实际改写的工具块数量（0 = 无命中或记忆结构不符，均安全跳过）
     */
    public static int apply(Memory memory, Map<String, Map<String, Object>> editedByToolUseId) {
        if (memory == null || editedByToolUseId == null || editedByToolUseId.isEmpty()) {
            return 0;
        }
        List<Msg> messages = memory.getMessages();
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        Msg last = messages.get(messages.size() - 1);
        if (last.getRole() != MsgRole.ASSISTANT) {
            log.warn("HITL 改参跳过：记忆末条非 ASSISTANT（可能暂停态已被消费）");
            return 0;
        }

        List<ContentBlock> newBlocks = new ArrayList<>();
        int applied = 0;
        for (ContentBlock block : last.getContent()) {
            if (block instanceof ToolUseBlock toolUse
                    && editedByToolUseId.containsKey(toolUse.getId())
                    && IConfirmationHook.isNeedConfirm(toolUse.getName())) {
                Map<String, Object> edited = editedByToolUseId.get(toolUse.getId());
                String newContent = JsonUtils.toJsonStr(edited);
                // 原 metadata 不可变，重建合并「用户改参」标记（保留 thoughtSignature 等原有项）
                Map<String, Object> newMetadata = new java.util.HashMap<>(
                        toolUse.getMetadata() == null ? Map.of() : toolUse.getMetadata());
                newMetadata.put(META_USER_EDITED_INPUT, Boolean.TRUE);
                newMetadata.put(META_USER_EDITED_DIFF, buildEditedDiff(toolUse.getInput(), edited));
                newBlocks.add(ToolUseBlock.builder()
                        .id(toolUse.getId())
                        .name(toolUse.getName())
                        .input(edited)
                        .content(newContent)
                        .metadata(newMetadata)
                        .build());
                // 落库入参缓存同步修正：ChatLogHook 在暂停前（PostReasoning）已缓存原始参数，
                // 不修正则历史消息 args 与实际执行参数不一致
                ChatLogHook.updateToolArgs(toolUse.getId(), newContent);
                log.info("HITL 改参: tool={}, toolUseId={}", toolUse.getName(), toolUse.getId());
                applied++;
            } else {
                newBlocks.add(block);
            }
        }
        if (applied == 0) {
            return 0;
        }

        // generateReason 亦存于 metadata，整体复制即随行；勿在复制后再调 generateReason()
        // （builder 默认 metadata 为不可变 Map.of()，put 会抛异常——复制路径无此问题但语义上多余）
        Msg rebuilt = Msg.builder()
                .id(last.getId())
                .name(last.getName())
                .role(last.getRole())
                .content(newBlocks)
                .metadata(last.getMetadata() == null ? Map.of() : last.getMetadata())
                .timestamp(last.getTimestamp())
                .build();
        memory.deleteMessage(messages.size() - 1);
        memory.addMessage(rebuilt);
        return applied;
    }
}
