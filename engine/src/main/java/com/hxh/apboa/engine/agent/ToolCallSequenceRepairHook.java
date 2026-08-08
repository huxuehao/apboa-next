package com.hxh.apboa.engine.agent;

import com.hxh.apboa.engine.formatter.ToolCallMessageSanitizer;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PreReasoningEvent;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 模型调用前的工具消息序列修复钩子。
 *
 * <p>该钩子位于 AutoContextHook 之后，负责对压缩后的实际模型输入执行最后一次协议校验，
 * 防止孤立的 tool 结果进入 OpenAI 兼容接口。
 */
public class ToolCallSequenceRepairHook implements Hook {

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (!(event instanceof PreReasoningEvent preReasoningEvent)
                || !(preReasoningEvent.getAgent() instanceof ReActAgent)) {
            return Mono.just(event);
        }

        List<io.agentscope.core.message.Msg> messages = preReasoningEvent.getInputMessages();
        List<io.agentscope.core.message.Msg> sanitized = ToolCallMessageSanitizer.sanitize(messages);
        preReasoningEvent.setInputMessages(sanitized);
        return Mono.just(event);
    }

    @Override
    public int priority() {
        // AutoContextHook 使用 0，本钩子必须在其后执行。
        return 10;
    }
}
