package com.hxh.apboa.engine.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hxh.apboa.common.enums.ToolChoiceStrategy;
import com.hxh.apboa.node.agent.AgentNodeRequest;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.tool.Toolkit;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowAgentNodeExecutorTest {

    @Test
    void shouldOnlyConstructSkillBoxForNonEmptySkillList() {
        AgentNodeRequest request = new AgentNodeRequest();
        assertThat(WorkflowAgentNodeExecutor.shouldCreateSkillBox(request)).isFalse();

        request.setSkillPackageIds(List.of(10L));
        assertThat(WorkflowAgentNodeExecutor.shouldCreateSkillBox(request)).isTrue();
    }

    @Test
    void shouldForceSingleIterationWithoutCallableCapabilitiesOrWhenToolsAreDisabled() {
        AgentNodeRequest request = new AgentNodeRequest();
        request.setMaxIterations(5);
        request.setSkillPackageIds(List.of(999L));
        Toolkit toolkit = new Toolkit();

        assertThat(WorkflowAgentNodeExecutor.resolveEffectiveMaxIterations(
                request,
                ToolChoiceStrategy.AUTO,
                WorkflowAgentNodeExecutor.hasCallableCapabilities(toolkit, null)))
                .isEqualTo(1);

        SkillBox skillBox = new SkillBox(toolkit);
        skillBox.registerSkill(AgentSkill.builder()
                .name("test-skill")
                .description("test")
                .skillContent("test")
                .build());
        assertThat(WorkflowAgentNodeExecutor.resolveEffectiveMaxIterations(
                request,
                ToolChoiceStrategy.AUTO,
                WorkflowAgentNodeExecutor.hasCallableCapabilities(toolkit, skillBox)))
                .isEqualTo(5);
        assertThat(WorkflowAgentNodeExecutor.resolveEffectiveMaxIterations(
                request,
                ToolChoiceStrategy.NONE,
                WorkflowAgentNodeExecutor.hasCallableCapabilities(toolkit, skillBox)))
                .isEqualTo(1);
    }

    @Test
    void shouldMergeQuickModelSwitchesWithoutEnablingAdvancedOverride() {
        AgentNodeRequest request = new AgentNodeRequest();
        request.setStreaming(false);
        request.setThinking(false);

        JsonNode quickOnly = WorkflowAgentNodeExecutor.mergeModelParamsOverride(request);
        assertThat(quickOnly.get("streaming").asBoolean()).isFalse();
        assertThat(quickOnly.get("thinking").asBoolean()).isFalse();

        ObjectNode advanced = JsonNodeFactory.instance.objectNode();
        advanced.put("temperature", 0.2);
        advanced.put("streaming", false);
        request.setModelParamsOverrideEnabled(true);
        request.setModelParamsOverride(advanced);
        request.setStreaming(true);

        JsonNode merged = WorkflowAgentNodeExecutor.mergeModelParamsOverride(request);
        assertThat(merged.get("temperature").asDouble()).isEqualTo(0.2);
        assertThat(merged.get("streaming").asBoolean()).isTrue();
        assertThat(merged.get("thinking").asBoolean()).isFalse();
    }
}
