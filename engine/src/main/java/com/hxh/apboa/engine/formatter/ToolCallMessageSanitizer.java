package com.hxh.apboa.engine.formatter;

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
 * <p>OpenAI 兼容接口要求每个 tool 消息都必须响应前方 assistant 消息中的 tool_calls。
 * 上下文压缩发生边界调整时，可能残留孤立的工具结果。发送模型请求前过滤这些结果，
 * 避免单个异常会话直接触发 HTTP 400。
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
        Set<String> availableToolCallIds = new HashSet<>();
        int removedMessages = 0;

        for (Msg message : messages) {
            if (message == null) {
                continue;
            }

            if (message.hasContentBlocks(ToolUseBlock.class)) {
                result.add(message);
                message.getContentBlocks(ToolUseBlock.class).stream()
                        .map(ToolUseBlock::getId)
                        .filter(id -> id != null && !id.isBlank())
                        .forEach(availableToolCallIds::add);
                continue;
            }

            boolean toolRole = MsgRole.TOOL.equals(message.getRole());
            if (!toolRole && !message.hasContentBlocks(ToolResultBlock.class)) {
                result.add(message);
                availableToolCallIds.clear();
                continue;
            }

            // role=tool 但没有结构化结果块时无法携带合法 tool_call_id，直接丢弃。
            if (toolRole && !message.hasContentBlocks(ToolResultBlock.class)) {
                removedMessages++;
                continue;
            }

            List<ToolResultBlock> validResults = message.getContentBlocks(ToolResultBlock.class)
                    .stream()
                    .filter(block -> block.getId() != null && availableToolCallIds.contains(block.getId()))
                    .toList();
            if (validResults.isEmpty()) {
                removedMessages++;
                continue;
            }

            if (validResults.size() == message.getContentBlocks(ToolResultBlock.class).size()) {
                result.add(message);
                validResults.stream().map(ToolResultBlock::getId).forEach(availableToolCallIds::remove);
                continue;
            }

            // 一个工具消息可能同时包含多个结果，只保留仍有对应 tool-call 的结果块。
            result.add(Msg.builder()
                    .role(message.getRole())
                    .name(message.getName())
                    .content(new ArrayList<>(validResults))
                    .metadata(message.getMetadata())
                    .build());
            validResults.stream().map(ToolResultBlock::getId).forEach(availableToolCallIds::remove);
            removedMessages++;
        }

        if (removedMessages > 0) {
            log.warn("过滤 {} 条不完整的工具结果消息，避免发送非法 tool-call 上下文", removedMessages);
        }
        return result;
    }
}
