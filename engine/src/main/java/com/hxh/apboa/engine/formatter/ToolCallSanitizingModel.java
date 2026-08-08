package com.hxh.apboa.engine.formatter;

import io.agentscope.core.message.Msg;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import java.util.List;
import java.util.Objects;
import reactor.core.publisher.Flux;

/**
 * 工具调用协议保护模型代理。
 *
 * <p>用于 AutoContextMemory 的内部压缩模型调用。压缩流程直接调用 {@link Model#stream}，不会经过
 * ReActAgent 的 PreReasoningHook，因此必须在此处校验消息序列。
 */
public final class ToolCallSanitizingModel implements Model {

    private final Model delegate;

    public ToolCallSanitizingModel(Model delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate model cannot be null");
    }

    @Override
    public Flux<ChatResponse> stream(
            List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
        return delegate.stream(ToolCallMessageSanitizer.sanitize(messages), tools, options);
    }

    @Override
    public String getModelName() {
        return delegate.getModelName();
    }
}
