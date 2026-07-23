package com.hxh.apboa.engine.log.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import io.agentscope.core.agent.user.UserAgent;
import io.agentscope.core.hook.PostActingEvent;
import io.agentscope.core.hook.PostReasoningEvent;
import io.agentscope.core.hook.PreActingEvent;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.message.MessageMetadataKeys;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.tool.Toolkit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkflowUsageHookTest {

    @Test
    void shouldArchiveRoundUsageThinkingAndActualToolFailure() {
        UserAgent agent = UserAgent.builder().name("test-user").build();
        Toolkit toolkit = new Toolkit();
        ToolUseBlock toolUse = ToolUseBlock.builder()
                .id("call-1")
                .name("load_skill_through_path")
                .input(Map.of("skillId", "flow_engine", "path", "SKILL.md"))
                .content("{\"skillId\":\"flow_engine\",\"path\":\"SKILL.md\"}")
                .build();
        Msg reasoning = Msg.builder()
                .role(MsgRole.ASSISTANT)
                .content(
                        ThinkingBlock.builder().thinking("hidden reasoning").build(),
                        toolUse)
                .metadata(Map.of(
                        MessageMetadataKeys.CHAT_USAGE,
                        ChatUsage.builder().inputTokens(524).outputTokens(14373).time(223.8).build()))
                .generateReason(GenerateReason.TOOL_CALLS)
                .build();
        ToolResultBlock result = new ToolResultBlock(
                "call-1",
                "load_skill_through_path",
                List.of(TextBlock.builder()
                        .text("Error: Parameter validation failed: enumeration []")
                        .build()));
        WorkflowUsageHook hook = new WorkflowUsageHook();

        hook.onEvent(new PostReasoningEvent(agent, "fake-model", null, reasoning)).block();
        hook.onEvent(new PreActingEvent(agent, toolkit, toolUse)).block();
        hook.onEvent(new PostActingEvent(agent, toolkit, toolUse, result)).block();

        WorkflowUsageHook.RoundTelemetry round = hook.snapshotRounds().getFirst();
        assertThat(round.inputTokens()).isEqualTo(524);
        assertThat(round.outputTokens()).isEqualTo(14373);
        assertThat(round.generateReason()).isEqualTo("TOOL_CALLS");
        assertThat(round.thinkingChars()).isEqualTo("hidden reasoning".length());
        assertThat(round.toolCalls()).singleElement().satisfies(tool -> {
            assertThat(tool.name()).isEqualTo("load_skill_through_path");
            assertThat(tool.arguments()).contains("flow_engine");
            assertThat(tool.status()).isEqualTo("FAIL");
            assertThat(tool.elapsed()).isNotNull().isNotNegative();
            assertThat(tool.detail()).contains("enumeration []");
        });
    }
}
