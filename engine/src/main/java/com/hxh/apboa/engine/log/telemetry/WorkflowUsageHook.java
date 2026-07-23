package com.hxh.apboa.engine.log.telemetry;

import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostReasoningEvent;
import reactor.core.publisher.Mono;

/**
 * 工作流智能体节点的 run 级用量累计 hook：节点带工具时 ReAct 会多轮调用 LLM，
 * 最终 Msg 的 ChatUsage 只含末轮用量，须逐轮（PostReasoning）累计才不低估 token。
 *
 * <p>工作流节点每次执行新建 agent 与本 hook 实例（不同于跨 run 共享的 ChatLogHook，
 * 无需按 threadId 建 static 映射），事件流内串行喂入，无并发。
 *
 * @author huxuehao
 */
public class WorkflowUsageHook implements Hook {

    private final RunStatAccumulator accumulator = new RunStatAccumulator();

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof PostReasoningEvent postReasoningEvent) {
            accumulator.onReasoningComplete(postReasoningEvent.getReasoningMessage());
        }
        return Mono.just(event);
    }

    public RunStatAccumulator getAccumulator() {
        return accumulator;
    }
}
