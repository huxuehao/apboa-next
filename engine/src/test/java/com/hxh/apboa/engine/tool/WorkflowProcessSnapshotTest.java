package com.hxh.apboa.engine.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hxh.apboa.common.ApboaSpringContextHolder;
import com.hxh.apboa.common.entity.WorkflowNodeExecution;
import com.hxh.apboa.common.entity.WorkflowRun;
import com.hxh.apboa.common.enums.NodeRunStatus;
import com.hxh.apboa.common.enums.NodeType;
import com.hxh.apboa.common.enums.WorkflowRunStatus;
import com.hxh.apboa.node.agent.AgentModelRequestTrace;
import com.hxh.apboa.workflowbiz.vo.WorkflowRunResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class WorkflowProcessSnapshotTest {

    private static ObjectMapper objectMapper;

    @BeforeAll
    static void setUpJsonMapper() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        objectMapper = new ObjectMapper();
        context.registerBean(ObjectMapper.class, () -> objectMapper);
        context.refresh();
        new ApboaSpringContextHolder().setApplicationContext(context);
    }

    @Test
    void shouldKeepExecutionOrderAndArchiveModelRetries() {
        WorkflowRun run = new WorkflowRun();
        run.setId(9001L);
        run.setWorkflowId("workflow-1");
        run.setVersion("7");
        run.setStatus(WorkflowRunStatus.SUCCESS);
        run.setStartTime(1_000L);
        run.setEndTime(1_500L);

        WorkflowRunResult result = new WorkflowRunResult();
        result.setRun(run);
        result.setNodeExecutions(List.of(
                node("start", "开始", NodeType.START, 1_000L, 1_010L),
                node("agent", "智能体节点", NodeType.AGENT, 1_010L, 1_490L),
                node("end", "结束", NodeType.END, 1_490L, 1_500L)));

        List<ToolProgressBridge.Progress> progress = List.of(
                progress("MODEL_WAITING", "agent", "invocation-1", 1, 1, 3, 1_020L, null, null),
                progress("MODEL_RETRYING", "agent", "invocation-1", 1, 2, 3, 1_120L, 100L, "temporary failure"),
                progress("MODEL_WAITING", "agent", "invocation-1", 1, 2, 3, 1_130L, null, null),
                progress("MODEL_GENERATING", "agent", "invocation-1", 1, 2, 3, 1_200L, null, null),
                progress("MODEL_SUCCEEDED", "agent", "invocation-1", 1, 2, 3, 1_470L, 340L, null));

        WorkflowProcessSnapshot snapshot = WorkflowProcessSnapshot.from(result, progress);

        assertThat(snapshot.runId()).isEqualTo("9001");
        assertThat(snapshot.status()).isEqualTo("SUCCESS");
        assertThat(snapshot.elapsed()).isEqualTo(500L);
        assertThat(snapshot.nodes())
                .extracting(WorkflowProcessSnapshot.NodeSnapshot::title)
                .containsExactly("开始", "智能体节点", "结束");

        WorkflowProcessSnapshot.NodeSnapshot agent = snapshot.nodes().get(1);
        assertThat(agent.elapsed()).isEqualTo(480L);
        assertThat(agent.modelRequests()).hasSize(1);
        assertThat(agent.modelRequests().getFirst().attempts())
                .extracting(
                        AgentModelRequestTrace.Attempt::attempt,
                        AgentModelRequestTrace.Attempt::status,
                        AgentModelRequestTrace.Attempt::elapsed,
                        AgentModelRequestTrace.Attempt::detail)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, "FAIL", 100L, "temporary failure"),
                        org.assertj.core.groups.Tuple.tuple(2, "SUCCESS", 340L, null));
    }

    @Test
    void shouldAssignRepeatedNodeInvocationsInActualExecutionOrder() {
        WorkflowRun run = new WorkflowRun();
        run.setStatus(WorkflowRunStatus.SUCCESS);
        WorkflowRunResult result = new WorkflowRunResult();
        result.setRun(run);
        result.setNodeExecutions(List.of(
                node("agent", "循环智能体", NodeType.AGENT, 10L, 20L),
                node("agent", "循环智能体", NodeType.AGENT, 30L, 50L)));

        List<ToolProgressBridge.Progress> progress = List.of(
                progress("MODEL_WAITING", "agent", "invocation-1", 1, 1, 3, 11L, null, null),
                progress("MODEL_SUCCEEDED", "agent", "invocation-1", 1, 1, 3, 19L, 8L, null),
                progress("MODEL_WAITING", "agent", "invocation-2", 1, 1, 3, 31L, null, null),
                progress("MODEL_FAILED", "agent", "invocation-2", 1, 1, 3, 49L, 18L, "second failed"));

        WorkflowProcessSnapshot snapshot = WorkflowProcessSnapshot.from(result, progress);

        assertThat(snapshot.nodes().get(0).modelRequests().getFirst().attempts().getFirst().status())
                .isEqualTo("SUCCESS");
        assertThat(snapshot.nodes().get(1).modelRequests().getFirst().attempts().getFirst().detail())
                .isEqualTo("second failed");
    }

    @Test
    void shouldPreferPersistedModelMetricsAndToolCallsOverTransientProgress() throws Exception {
        WorkflowRunResult result = new WorkflowRunResult();
        result.setRun(new WorkflowRun());
        WorkflowNodeExecution agent =
                node("agent", "智能体节点", NodeType.AGENT, 1_000L, 2_000L);
        AgentModelRequestTrace persisted = new AgentModelRequestTrace(
                1,
                1,
                "SUCCESS",
                950L,
                820L,
                524,
                14373,
                14897,
                127.5,
                "STOP",
                "TOOL_CALLS",
                2048,
                Map.of("load_duration", 12_000_000L),
                List.of(new AgentModelRequestTrace.Attempt(1, "SUCCESS", 950L, 820L, null)),
                List.of(new AgentModelRequestTrace.ToolCall(
                        "call-1",
                        "get_current_datetime",
                        "{}",
                        "SUCCESS",
                        3L,
                        null)));
        agent.setProcessData(objectMapper.writeValueAsString(Map.of(
                "modelName", "local-ollama",
                "modelRequests", List.of(persisted))));
        result.setNodeExecutions(List.of(agent));

        List<ToolProgressBridge.Progress> transientProgress = List.of(
                progress("MODEL_WAITING", "agent", "invocation-1", 1, 1, 1, 1_010L, null, null),
                progress("MODEL_SUCCEEDED", "agent", "invocation-1", 1, 1, 1, 1_020L, 10L, null));

        WorkflowProcessSnapshot snapshot =
                WorkflowProcessSnapshot.from(result, transientProgress);
        WorkflowProcessSnapshot.NodeSnapshot node = snapshot.nodes().getFirst();
        AgentModelRequestTrace request = node.modelRequests().getFirst();

        assertThat(node.modelName()).isEqualTo("local-ollama");
        assertThat(request.durationMs()).isEqualTo(950L);
        assertThat(request.ttftMs()).isEqualTo(820L);
        assertThat(request.inputTokens()).isEqualTo(524);
        assertThat(request.outputTokens()).isEqualTo(14373);
        assertThat(request.thinkingChars()).isEqualTo(2048);
        assertThat(request.providerMetrics()).containsEntry("load_duration", 12_000_000);
        assertThat(request.toolCalls())
                .extracting(
                        AgentModelRequestTrace.ToolCall::name,
                        AgentModelRequestTrace.ToolCall::status)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                        "get_current_datetime", "SUCCESS"));
    }

    private static WorkflowNodeExecution node(
            String id, String title, NodeType type, long start, long end) {
        WorkflowNodeExecution node = new WorkflowNodeExecution();
        node.setNodeId(id);
        node.setNodeTitle(title);
        node.setNodeType(type);
        node.setStatus(NodeRunStatus.SUCCESS);
        node.setStartTime(start);
        node.setEndTime(end);
        node.setInputs("{\"input\":\"" + title + "\"}");
        node.setOutputs("{\"output\":\"" + title + "\"}");
        return node;
    }

    private static ToolProgressBridge.Progress progress(
            String phase,
            String nodeId,
            String invocationId,
            int requestIndex,
            int attempt,
            int maxAttempts,
            long occurredAt,
            Long elapsed,
            String detail) {
        return ToolProgressBridge.Progress.builder()
                .phase(phase)
                .nodeId(nodeId)
                .nodeInvocationId(invocationId)
                .requestIndex(requestIndex)
                .attempt(attempt)
                .maxAttempts(maxAttempts)
                .occurredAt(occurredAt)
                .elapsed(elapsed)
                .detail(detail)
                .build();
    }
}
