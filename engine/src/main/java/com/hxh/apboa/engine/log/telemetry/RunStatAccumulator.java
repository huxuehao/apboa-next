package com.hxh.apboa.engine.log.telemetry;

import io.agentscope.core.message.Msg;
import io.agentscope.core.model.ChatUsage;

import java.util.HashMap;
import java.util.Map;

/**
 * run 级统计累积器：墙钟耗时 / LLM 推理轮次 / token 用量。
 *
 * <p>落库侧（{@code ChatLogHook} 写 assistant 消息 meta）与实时侧
 * （{@code AguiAgentAdapter} 发 RUN_META 自定义事件）各自持有实例、喂入每轮推理完成的 Msg，
 * meta 字段契约（durationMs/iterationCount/inputTokens/outputTokens/totalTokens）以本类为准。
 *
 * <p>两侧起点略有差异（Hook 在首轮 PreReasoning、Adapter 在 run 建立时），durationMs 可能有
 * 百毫秒级偏差；HITL 恢复的 run 中 Adapter 侧只含恢复后的量——均以落库版为准（刷新后一致）。
 * 非线程安全：单 run 事件流内串行使用。
 *
 * @author huxuehao
 */
public class RunStatAccumulator {

    private final long startMs = System.currentTimeMillis();
    private int iterations = 0;
    private long inputTokens = 0;
    private long outputTokens = 0;

    /** 每轮 LLM 推理完成：轮次 +1、token 累加（usage 由框架流式聚合器写入 Msg metadata） */
    public void onReasoningComplete(Msg msg) {
        iterations++;
        ChatUsage usage = msg.getChatUsage();
        if (usage != null) {
            inputTokens += usage.getInputTokens();
            outputTokens += usage.getOutputTokens();
        }
    }

    /** 是否有任何累积（无推理轮次的 run 不产出 meta） */
    public boolean hasData() {
        return iterations > 0;
    }

    /** 构建 meta 字段 Map（落库序列化 / RUN_META 事件载荷共用） */
    public Map<String, Object> buildMeta() {
        Map<String, Object> meta = new HashMap<>();
        meta.put("durationMs", System.currentTimeMillis() - startMs);
        meta.put("iterationCount", iterations);
        meta.put("inputTokens", inputTokens);
        meta.put("outputTokens", outputTokens);
        meta.put("totalTokens", inputTokens + outputTokens);
        return meta;
    }
}
