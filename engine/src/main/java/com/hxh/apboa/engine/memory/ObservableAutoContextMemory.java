package com.hxh.apboa.engine.memory;

import com.hxh.apboa.engine.formatter.ToolCallSanitizingModel;
import io.agentscope.core.memory.autocontext.AutoContextConfig;
import io.agentscope.core.memory.autocontext.AutoContextMemory;
import io.agentscope.core.memory.autocontext.TokenCounterUtil;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.Model;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 支持压缩生命周期通知的上下文记忆。
 *
 * <p>该类在调用上游 AutoContextMemory 压缩逻辑之前通知监听器，使运行时可以在压缩模型调用开始前
 * 推送“记忆压缩中”事件；同时统一为压缩模型注入工具消息协议保护代理。
 */
public class ObservableAutoContextMemory extends AutoContextMemory {

    private final AutoContextConfig config;
    private volatile Consumer<CompressionLifecycleEvent> compressionListener;
    private volatile String noProgressFingerprint;

    public ObservableAutoContextMemory(AutoContextConfig config, Model model) {
        super(config, new ToolCallSanitizingModel(model));
        this.config = Objects.requireNonNull(config, "config cannot be null");
    }

    public void setCompressionListener(Consumer<CompressionLifecycleEvent> listener) {
        this.compressionListener = listener;
    }

    @Override
    public void addMessage(Msg message) {
        super.addMessage(message);
        // 新消息会改变压缩输入，允许下一轮重新尝试压缩。
        noProgressFingerprint = null;
    }

    @Override
    public void deleteMessage(int index) {
        super.deleteMessage(index);
        noProgressFingerprint = null;
    }

    public ContextUsageSnapshot getContextUsageSnapshot() {
        List<Msg> messages = getMessages();
        int usedTokens = TokenCounterUtil.calculateToken(messages);
        // 上游实现会先将 token 阈值截断为 int，这里保持相同的阈值，保证圆环与实际触发点一致。
        long tokenThreshold = (int) (config.getMaxToken() * config.getTokenRatio());
        int messageThreshold = config.getMsgThreshold();
        double tokenPressure = tokenThreshold <= 0 ? 1D : usedTokens / (double) tokenThreshold;
        double messagePressure = messageThreshold <= 0 ? 1D : messages.size() / (double) messageThreshold;
        double compressionPressure = Math.min(1D, Math.max(tokenPressure, messagePressure));
        String triggerReason = tokenPressure >= messagePressure ? "TOKEN" : "MESSAGE";
        return new ContextUsageSnapshot(
                usedTokens,
                config.getMaxToken(),
                tokenThreshold,
                messages.size(),
                messageThreshold,
                tokenPressure,
                messagePressure,
                compressionPressure,
                triggerReason);
    }

    @Override
    public boolean compressIfNeeded() {
        ContextUsageSnapshot before = getContextUsageSnapshot();
        boolean shouldCompress = before.messageCount() >= config.getMsgThreshold()
                || before.usedTokens() >= before.tokenThreshold();
        if (!shouldCompress) {
            return false;
        }

        String fingerprint = before.usedTokens() + ":" + before.messageCount();
        if (fingerprint.equals(noProgressFingerprint)) {
            notifyLifecycle(CompressionStatus.SKIPPED, before, before, false);
            return false;
        }

        notifyLifecycle(CompressionStatus.STARTED, before, before, false);
        try {
            boolean compressed = super.compressIfNeeded();
            ContextUsageSnapshot after = getContextUsageSnapshot();
            boolean reduced = after.usedTokens() < before.usedTokens()
                    || after.messageCount() < before.messageCount();
            if (!reduced) {
                noProgressFingerprint = fingerprint;
            } else {
                noProgressFingerprint = null;
            }
            notifyLifecycle(
                    compressed ? CompressionStatus.FINISHED : CompressionStatus.SKIPPED,
                    before,
                    after,
                    compressed);
            return compressed;
        } catch (RuntimeException | Error error) {
            ContextUsageSnapshot after = getContextUsageSnapshot();
            notifyLifecycle(CompressionStatus.FAILED, before, after, false);
            throw error;
        }
    }

    @Override
    public void clear() {
        super.clear();
        noProgressFingerprint = null;
    }

    private void notifyLifecycle(
            CompressionStatus status,
            ContextUsageSnapshot before,
            ContextUsageSnapshot after,
            boolean compressed) {
        Consumer<CompressionLifecycleEvent> listener = compressionListener;
        if (listener != null) {
            listener.accept(new CompressionLifecycleEvent(status, before, after, compressed));
        }
    }

    public enum CompressionStatus {
        STARTED,
        FINISHED,
        SKIPPED,
        FAILED
    }

    public record CompressionLifecycleEvent(
            CompressionStatus status,
            ContextUsageSnapshot before,
            ContextUsageSnapshot after,
            boolean compressed) {
    }

    public record ContextUsageSnapshot(
            int usedTokens,
            long totalTokens,
            long tokenThreshold,
            int messageCount,
            int messageThreshold,
            double tokenPressure,
            double messagePressure,
            double compressionPressure,
            String triggerReason) {
    }
}
