package com.hxh.apboa.engine.log.telemetry;

import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 运行过程遥测提取器：从 agentscope 消息中提取"过程步骤"的单一实现。
 *
 * <p>落库侧（{@code ChatLogHook} 写 tool 消息的 subProcess）与实时侧
 * （{@code AguiAgentAdapter} 发 SUBAGENT_STEP 自定义事件）共用本类的遍历/截断/步骤构造，
 * 保证两侧步骤结构同构（type/name/args/result/elapsed/content 字段契约以本类为准），
 * 前端只需一套渲染模型。步骤状态管理（配对、计时、归档）由各消费方自持。
 *
 * @author huxuehao
 */
public final class RunTelemetryExtractor {

    /** 单步内容防御性上限（正常场景碰不到；防爬取全网页等极端结果把消息撑到 MB 级） */
    public static final int SUB_STEP_MAX_LEN = 50000;

    private RunTelemetryExtractor() {}

    /**
     * 步骤访问器：{@link #visitReasoning} 按消息内块顺序回调，保证步骤时间线有序
     */
    public interface StepVisitor {
        /** thinking / text 普通步（已构造好、内容已截断） */
        void onPlainStep(Map<String, Object> step);

        /** 工具调用发起（步骤构造与计时由调用方决定，便于各自管理配对状态） */
        void onToolUse(ToolUseBlock block);
    }

    /**
     * 遍历推理消息的内容块提取步骤。
     *
     * @return 是否包含正文（TextBlock）——正文出现即该次子智能体调用结束的信号
     */
    public static boolean visitReasoning(Msg msg, StepVisitor visitor) {
        boolean hasFinalText = false;
        for (ContentBlock block : msg.getContent()) {
            if (block instanceof ThinkingBlock thinkingBlock && !thinkingBlock.getThinking().isEmpty()) {
                visitor.onPlainStep(thinkingStep(truncate(thinkingBlock.getThinking())));
            } else if (block instanceof TextBlock textBlock && !textBlock.getText().isEmpty()) {
                visitor.onPlainStep(textStep(truncate(textBlock.getText())));
                hasFinalText = true;
            } else if (block instanceof ToolUseBlock toolUseBlock) {
                visitor.onToolUse(toolUseBlock);
            }
        }
        return hasFinalText;
    }

    /** 遍历工具结果消息中的 ToolResultBlock（结果配对由调用方按 id 完成） */
    public static void visitToolResults(Msg msg, Consumer<ToolResultBlock> visitor) {
        for (ContentBlock block : msg.getContent()) {
            if (block instanceof ToolResultBlock toolResultBlock) {
                visitor.accept(toolResultBlock);
            }
        }
    }

    /**
     * 工具结果全文：拼接输出里的全部文本块（\n 分隔）。实时展示（AguiAgentAdapter）
     * 与落库（ChatLogHook 主工具/子步骤）共用本单一实现——历史上两链路各写一份且语义
     * 不一致（落库只取首块），多块结果（如 HITL 改参提示为独立追加的文本块）落库即丢，
     * 切换会话后与实时所见不同文。无任何文本块返回 null。
     */
    public static String toolResultText(ToolResultBlock toolResultBlock) {
        java.util.List<ContentBlock> output =
                toolResultBlock == null ? null : toolResultBlock.getOutput();
        if (output == null || output.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : output) {
            if (block instanceof TextBlock textBlock) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(textBlock.getText());
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    // ─── 步骤构造（字段契约单点，前端 SubProcessStep 类型与此对应） ───

    public static Map<String, Object> thinkingStep(String content) {
        return plainStep("thinking", content);
    }

    public static Map<String, Object> textStep(String content) {
        return plainStep("text", content);
    }

    public static Map<String, Object> errorStep(String content) {
        return plainStep("error", content);
    }

    /** 工具步骤（发起时刻）：result / elapsed 由消费方在结果配对后补充 */
    public static Map<String, Object> toolStep(String name, String args) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("type", "tool");
        step.put("name", name);
        step.put("args", truncate(args));
        return step;
    }

    private static Map<String, Object> plainStep(String type, String content) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("type", type);
        step.put("content", content);
        return step;
    }

    /** 步骤内容截断（防御性上限） */
    public static String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > SUB_STEP_MAX_LEN ? text.substring(0, SUB_STEP_MAX_LEN) + "..." : text;
    }
}
