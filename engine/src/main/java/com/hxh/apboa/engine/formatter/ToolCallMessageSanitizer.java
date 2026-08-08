package com.hxh.apboa.engine.formatter;

import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenAI 工具消息序列校验器。
 *
 * <p>OpenAI 兼容接口要求 assistant 的每个 tool_call 都必须紧随对应的 tool 结果，且 tool 结果不能脱离
 * tool_call 独立出现。上下文压缩发生边界调整时，可能出现其中任一种不完整序列。发送模型请求前过滤这些
 * 消息，避免单个异常会话直接触发 HTTP 400。
 */
public final class ToolCallMessageSanitizer {

    private static final Logger log = LoggerFactory.getLogger(ToolCallMessageSanitizer.class);

    private ToolCallMessageSanitizer() {
    }

    public static List<Msg> sanitize(List<Msg> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }

        List<Msg> result = new ArrayList<>(messages.size());
        PendingToolCallGroup pendingGroup = null;
        int[] removedCount = new int[] {0};

        for (Msg message : messages) {
            if (message == null) {
                continue;
            }

            if (message.hasContentBlocks(ToolUseBlock.class)) {
                flushPendingGroup(result, pendingGroup, removedCount);
                pendingGroup = new PendingToolCallGroup(message);
                continue;
            }

            if (MsgRole.TOOL.equals(message.getRole())) {
                if (pendingGroup == null) {
                    removedCount[0]++;
                    continue;
                }
                pendingGroup.addResults(message);
                continue;
            }

            flushPendingGroup(result, pendingGroup, removedCount);
            pendingGroup = null;
            result.add(message);
        }

        flushPendingGroup(result, pendingGroup, removedCount);

        if (removedCount[0] > 0) {
            log.warn("过滤 {} 条不完整的工具调用或工具结果消息，避免发送非法 tool-call 上下文", removedCount[0]);
        }
        return result;
    }

    /** 将一组 assistant tool_calls 与后续 tool 结果整理为 OpenAI 合法序列。 */
    private static void flushPendingGroup(
            List<Msg> result, PendingToolCallGroup pendingGroup, int[] removedCount) {
        if (pendingGroup == null) {
            return;
        }

        Set<String> resultIds = pendingGroup.resultIds();
        List<ContentBlock> validAssistantBlocks = new ArrayList<>();
        Set<String> validToolCallIds = new HashSet<>();

        for (ContentBlock block : pendingGroup.assistantMessage().getContent()) {
            if (!(block instanceof ToolUseBlock toolUse)) {
                validAssistantBlocks.add(block);
                continue;
            }
            String toolCallId = toolUse.getId();
            if (toolCallId != null && !toolCallId.isBlank() && resultIds.contains(toolCallId)) {
                validAssistantBlocks.add(toolUse);
                validToolCallIds.add(toolCallId);
            } else {
                removedCount[0]++;
            }
        }

        if (!validAssistantBlocks.isEmpty()) {
            result.add(copyMessage(pendingGroup.assistantMessage(), validAssistantBlocks));
        } else {
            removedCount[0]++;
        }

        Set<String> emittedToolResultIds = new HashSet<>();
        for (ToolResultBlock toolResult : pendingGroup.results()) {
            if (!validToolCallIds.contains(toolResult.getId())) {
                removedCount[0]++;
                continue;
            }
            if (!emittedToolResultIds.add(toolResult.getId())) {
                removedCount[0]++;
                continue;
            }
            // OpenAI formatter 每个 TOOL Msg 只消费一个 ToolResultBlock，必须拆分并行工具结果。
            result.add(Msg.builder()
                    .role(MsgRole.TOOL)
                    .name(pendingGroup.toolResultName(toolResult))
                    .content(toolResult)
                    .metadata(pendingGroup.toolResultMetadata())
                    .build());
        }
    }

    private static Msg copyMessage(Msg source, List<ContentBlock> content) {
        return Msg.builder()
                .role(source.getRole())
                .name(source.getName())
                .content(content)
                .metadata(source.getMetadata())
                .build();
    }

    private static final class PendingToolCallGroup {
        private final Msg assistantMessage;
        private final List<ToolResultBlock> results = new ArrayList<>();
        private String toolResultName;
        private java.util.Map<String, Object> toolResultMetadata;

        private PendingToolCallGroup(Msg assistantMessage) {
            this.assistantMessage = assistantMessage;
        }

        private Msg assistantMessage() {
            return assistantMessage;
        }

        private void addResults(Msg toolMessage) {
            results.addAll(toolMessage.getContentBlocks(ToolResultBlock.class));
            if (toolResultName == null) {
                toolResultName = toolMessage.getName();
            }
            if (toolResultMetadata == null) {
                toolResultMetadata = toolMessage.getMetadata();
            }
        }

        private List<ToolResultBlock> results() {
            return results;
        }

        private Set<String> resultIds() {
            Set<String> resultIds = new HashSet<>();
            for (ToolResultBlock result : results) {
                String resultId = result.getId();
                if (resultId != null && !resultId.isBlank()) {
                    resultIds.add(resultId);
                }
            }
            return resultIds;
        }

        private String toolResultName(ToolResultBlock toolResult) {
            return toolResultName != null ? toolResultName : toolResult.getName();
        }

        private java.util.Map<String, Object> toolResultMetadata() {
            return toolResultMetadata;
        }
    }
}
