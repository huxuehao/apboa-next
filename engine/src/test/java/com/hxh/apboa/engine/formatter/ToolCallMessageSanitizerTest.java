package com.hxh.apboa.engine.formatter;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import java.util.List;
import junit.framework.TestCase;

public class ToolCallMessageSanitizerTest extends TestCase {

    public void testShouldKeepMatchingToolResult() {
        Msg assistant = toolUse("call-1");
        Msg result = toolResultMessage("call-1");

        List<Msg> sanitized = ToolCallMessageSanitizer.sanitize(List.of(assistant, result));

        assertEquals(2, sanitized.size());
        assertEquals("call-1", sanitized.get(1).getFirstContentBlock(ToolResultBlock.class).getId());
    }

    public void testShouldKeepParallelToolResults() {
        Msg assistant = Msg.builder()
                .role(MsgRole.ASSISTANT)
                .content(toolUseBlock("call-1"), toolUseBlock("call-2"))
                .build();
        Msg results = Msg.builder()
                .role(MsgRole.TOOL)
                .content(toolResult("call-1"), toolResult("call-2"))
                .build();

        List<Msg> sanitized = ToolCallMessageSanitizer.sanitize(List.of(assistant, results));

        assertEquals(2, sanitized.size());
        assertEquals(2, sanitized.get(1).getContentBlocks(ToolResultBlock.class).size());
    }

    public void testShouldDropOrphanToolResult() {
        List<Msg> sanitized = ToolCallMessageSanitizer.sanitize(List.of(toolResultMessage("missing")));

        assertTrue(sanitized.isEmpty());
    }

    public void testShouldDropToolResultAfterNonToolMessageBreaksSequence() {
        List<Msg> messages = List.of(
                toolUse("call-1"),
                Msg.builder().role(MsgRole.USER).content(TextBlock.builder().text("打断").build()).build(),
                toolResultMessage("call-1"));

        List<Msg> sanitized = ToolCallMessageSanitizer.sanitize(messages);

        assertEquals(2, sanitized.size());
        assertEquals(MsgRole.USER, sanitized.get(1).getRole());
    }

    public void testShouldFilterOnlyInvalidBlocksFromMixedToolMessage() {
        Msg assistant = toolUse("call-1");
        Msg mixed = Msg.builder()
                .role(MsgRole.TOOL)
                .content(toolResult("call-1"), toolResult("missing"))
                .build();

        List<Msg> sanitized = ToolCallMessageSanitizer.sanitize(List.of(assistant, mixed));

        assertEquals(2, sanitized.size());
        assertEquals(1, sanitized.get(1).getContentBlocks(ToolResultBlock.class).size());
        assertEquals("call-1", sanitized.get(1).getFirstContentBlock(ToolResultBlock.class).getId());
    }

    private static Msg toolUse(String id) {
        return Msg.builder()
                .role(MsgRole.ASSISTANT)
                .content(toolUseBlock(id))
                .build();
    }

    private static ToolUseBlock toolUseBlock(String id) {
        return ToolUseBlock.builder().id(id).name("test-tool").input(java.util.Map.of()).build();
    }

    private static ToolResultBlock toolResult(String id) {
        return ToolResultBlock.of(id, "test-tool", TextBlock.builder().text("ok").build());
    }

    private static Msg toolResultMessage(String id) {
        return Msg.builder().role(MsgRole.TOOL).content(toolResult(id)).build();
    }
}
