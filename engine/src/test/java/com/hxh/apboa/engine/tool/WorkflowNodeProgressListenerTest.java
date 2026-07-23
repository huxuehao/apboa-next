package com.hxh.apboa.engine.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hxh.apboa.common.ApboaSpringContextHolder;
import com.hxh.apboa.common.enums.NodeType;
import com.hxh.apboa.node.base.EnhancedNode;
import com.hxh.apboa.node.base.NodeOutput;
import com.hxh.apboa.node.base.context.NodeContext;
import com.hxh.apboa.node.base.context.NodeExecutionListener;
import com.hxh.apboa.node.base.verify.VerifyResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class WorkflowNodeProgressListenerTest {

    @BeforeAll
    static void setUpJsonMapper() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
        context.refresh();
        new ApboaSpringContextHolder().setApplicationContext(context);
    }

    @Test
    void shouldEmitStartedAndFinishedSnapshotsFromEnhancedNodeLifecycle() {
        String toolUseId = "workflow-node-progress-test";
        List<ToolProgressBridge.Progress> events = new ArrayList<>();
        ToolProgressBridge.register(toolUseId, events::add);
        try {
            NodeContext context = new NodeContext("run-1");
            context.setExecutionListener(new WorkflowNodeProgressListener(toolUseId));
            EnhancedNode node = new EnhancedNode("agent", "智能体节点", NodeType.AGENT) {
                @Override
                protected NodeOutput doExecute(
                        Map<String, Object> inputs, NodeOutput output, NodeContext context) {
                    output.addOutput("answer", "ok");
                    output.markComplete();
                    return output;
                }

                @Override
                public VerifyResult verifyConfig(Map<String, Object> inputs) {
                    return VerifyResult.valid();
                }
            };

            NodeOutput result = node.execute(context);

            assertThat(result.getStatus()).isEqualTo(NodeOutput.ExecutionStatus.SUCCESS);
            assertThat(events).extracting(ToolProgressBridge.Progress::getPhase)
                    .containsExactly("WORKFLOW_NODE_STARTED", "WORKFLOW_NODE_FINISHED");
            assertThat(events.get(0).getWorkflowNode())
                    .containsEntry("invocationId", "agent#1")
                    .containsEntry("status", "RUNNING");
            assertThat(events.get(1).getWorkflowNode())
                    .containsEntry("invocationId", "agent#1")
                    .containsEntry("status", "SUCCESS")
                    .containsEntry("outputs", "{\"answer\":\"ok\"}");
        } finally {
            ToolProgressBridge.unregister(toolUseId);
        }
    }

    @Test
    void shouldUseDistinctInvocationIdsForRepeatedNodeExecutions() {
        NodeContext context = new NodeContext("run-2");
        List<String> invocationIds = new ArrayList<>();
        context.setExecutionListener(new NodeExecutionListener() {
            @Override
            public void onNodeStarted(String invocationId, NodeOutput output) {
                invocationIds.add(invocationId);
            }
        });
        EnhancedNode node = new EnhancedNode("loop-node", "循环节点", NodeType.CODE) {
            @Override
            protected NodeOutput doExecute(
                    Map<String, Object> inputs, NodeOutput output, NodeContext context) {
                output.markComplete();
                return output;
            }

            @Override
            public VerifyResult verifyConfig(Map<String, Object> inputs) {
                return VerifyResult.valid();
            }
        };

        node.execute(context);
        node.execute(context);

        assertThat(invocationIds).containsExactly("loop-node#1", "loop-node#2");
    }
}
