package com.hxh.apboa.engine.tool;

import com.hxh.apboa.common.util.JsonUtils;
import com.hxh.apboa.engine.log.telemetry.RunTelemetryExtractor;
import com.hxh.apboa.node.base.NodeOutput;
import com.hxh.apboa.node.base.context.NodeExecutionListener;
import java.util.LinkedHashMap;
import java.util.Map;

/** 把工作流节点生命周期转换为所属工具调用的实时进度事件。 */
final class WorkflowNodeProgressListener implements NodeExecutionListener {

    private final String toolUseId;

    WorkflowNodeProgressListener(String toolUseId) {
        this.toolUseId = toolUseId;
    }

    @Override
    public void onNodeStarted(String invocationId, NodeOutput output) {
        ToolProgressBridge.emit(toolUseId, ToolProgressBridge.Progress.builder()
                .phase("WORKFLOW_NODE_STARTED")
                .message(nodeName(output) + "正在执行")
                .nodeId(output.getNodeId())
                .nodeName(output.getNodeName())
                .nodeInvocationId(invocationId)
                .workflowNode(nodeSnapshot(invocationId, output, true))
                .build());
    }

    @Override
    public void onNodeFinished(String invocationId, NodeOutput output) {
        String status = status(output.getStatus());
        ToolProgressBridge.emit(toolUseId, ToolProgressBridge.Progress.builder()
                .phase("WORKFLOW_NODE_FINISHED")
                .message(nodeName(output) + ("SUCCESS".equals(status) ? "执行完成" : "执行失败"))
                .nodeId(output.getNodeId())
                .nodeName(output.getNodeName())
                .nodeInvocationId(invocationId)
                .elapsed(output.getExecutionDuration())
                .workflowNode(nodeSnapshot(invocationId, output, false))
                .build());
    }

    private Map<String, Object> nodeSnapshot(
            String invocationId, NodeOutput output, boolean started) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("invocationId", invocationId);
        node.put("nodeId", output.getNodeId());
        node.put("title", output.getNodeName());
        node.put("type", output.getNodeType() == null ? null : output.getNodeType().name());
        node.put("status", started ? "RUNNING" : status(output.getStatus()));
        if (!started) {
            putIfNotNull(node, "elapsed", output.getExecutionDuration());
            putJson(node, "inputs", output.getExecutionContext().get("inputs"));
            putJson(node, "outputs", output.getAllOutput());
            String error = output.getErrorMessage();
            if ((error == null || error.isBlank()) && !output.getVerifyErrors().isEmpty()) {
                error = safeJson(output.getVerifyErrors());
            }
            putIfNotNull(node, "error", RunTelemetryExtractor.truncate(error));
            putIfNotNull(node, "modelName", output.getExecutionContext().get("modelName"));
            putIfNotNull(node, "modelRequests", output.getExecutionContext().get("modelRequests"));
        }
        return node;
    }

    private void putJson(Map<String, Object> target, String key, Object value) {
        String json = safeJson(value);
        if (json != null && !"{}".equals(json)) {
            target.put(key, RunTelemetryExtractor.truncate(json));
        }
    }

    private String safeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return JsonUtils.toJsonStr(value);
        } catch (RuntimeException ignored) {
            return String.valueOf(value);
        }
    }

    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private String nodeName(NodeOutput output) {
        return output.getNodeName() == null || output.getNodeName().isBlank()
                ? "节点"
                : output.getNodeName();
    }

    private String status(NodeOutput.ExecutionStatus status) {
        if (status == NodeOutput.ExecutionStatus.SUCCESS) {
            return "SUCCESS";
        }
        if (status == NodeOutput.ExecutionStatus.RUNNING) {
            return "RUNNING";
        }
        return "FAIL";
    }
}
