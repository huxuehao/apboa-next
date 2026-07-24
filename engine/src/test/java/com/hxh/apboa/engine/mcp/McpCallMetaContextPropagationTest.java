package com.hxh.apboa.engine.mcp;

import com.hxh.apboa.engine.identity.IdentityAssertionSigner;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * M3 传递机制验证（docs/identity-propagation-design.md §6.M3）：
 * LazyMcpAgentTool 在 Mono 链尾 contextWrite 写入 _meta，runner-runtime 覆盖的
 * wrapper 在链上游经 deferContextual 读取——Reactor Context 自下游向上游传播，
 * 本测试固化这个方向性契约（模拟 wrapper 读取端，不连真实 MCP）。
 *
 * @author vaulka
 */
class McpCallMetaContextPropagationTest {

    /**
     * 模拟覆盖版 wrapper 的读取端：deferContextual 取 _meta
     */
    private static Mono<Map<String, Object>> simulatedWrapperRead() {
        return Mono.deferContextual(ctxView ->
                Mono.justOrEmpty(ctxView.<Map<String, Object>>getOrDefault(
                        IdentityAssertionSigner.MCP_CALL_META_CONTEXT_KEY, null)));
    }

    @Test
    void metaWrittenDownstreamIsVisibleUpstream() {
        Map<String, Object> meta = Map.of(
                IdentityAssertionSigner.MCP_META_ASSERTION_KEY, "jwt-token-here");

        // 模拟 LazyMcpAgentTool.callAsync 的结构：上游 wrapper 读，链尾 contextWrite
        Map<String, Object> received = Mono.defer(
                        McpCallMetaContextPropagationTest::simulatedWrapperRead)
                .contextWrite(ctx -> ctx.put(
                        IdentityAssertionSigner.MCP_CALL_META_CONTEXT_KEY, meta))
                .block();

        assertEquals(meta, received);
        assertEquals("jwt-token-here",
                received.get(IdentityAssertionSigner.MCP_META_ASSERTION_KEY));
    }

    @Test
    void noContextWriteMeansNoMeta() {
        // 未配置 audience（callMeta=null 不写 Context）时，wrapper 读到空——行为与原版一致
        Map<String, Object> received = simulatedWrapperRead().block();
        assertNull(received);
    }
}
