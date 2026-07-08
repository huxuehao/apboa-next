package com.hxh.apboa.workflowbiz.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hxh.apboa.node.base.Node;
import com.hxh.apboa.common.enums.NodeType;
import com.hxh.apboa.node.base.context.NodeContext;
import com.hxh.apboa.node.base.verify.VerifyFail;
import com.hxh.apboa.node.base.verify.VerifyResult;
import com.hxh.apboa.workflow.run.RunWorkflow;
import com.hxh.apboa.workflowbiz.core.WorkflowDefinitionCompiler;
import com.hxh.apboa.workflowbiz.service.WorkflowValidator;
import com.hxh.apboa.workflowbiz.vo.WorkflowValidationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WorkflowValidatorImpl implements WorkflowValidator {
    private final ObjectMapper objectMapper;
    private final WorkflowDefinitionCompiler compiler;

    @Override
    public WorkflowValidationResult validate(Object definition) {
        WorkflowValidationResult result = new WorkflowValidationResult();
        JsonNode root = objectMapper.valueToTree(definition);
        JsonNode nodes = root.path("nodes");
        JsonNode edges = root.path("edges");

        if (!nodes.isArray() || nodes.isEmpty()) {
            result.addError(null, "nodes", "工作流节点列表必须至少包含一个开始节点和一个结束节点");
            return result;
        }
        if (!edges.isMissingNode() && !edges.isArray()) {
            result.addError(null, "edges", "工作流连线必须为数组格式");
        }

        Set<String> nodeIds = new HashSet<>();
        Map<String, JsonNode> nodeById = new HashMap<>();
        Set<String> startNodeIds = new HashSet<>();
        Set<String> endNodeIds = new HashSet<>();
        int startCount = 0;
        int endCount = 0;
        for (JsonNode node : nodes) {
            String id = text(node, "id");
            String typeText = text(node, "type");
            if (id == null || id.isBlank()) {
                result.addError(null, "id", "节点ID不能为空");
                continue;
            }
            if (!nodeIds.add(id)) {
                result.addError(id, "id", "节点ID重复");
            }
            nodeById.put(id, node);
            try {
                NodeType type = NodeType.valueOf(typeText);
                if (!WorkflowDefinitionCompiler.supportedTypes().contains(type)) {
                    result.addError(id, "type", "不支持的节点类型：" + typeText);
                }
                if (type == NodeType.START) {
                    startCount++;
                    startNodeIds.add(id);
                }
                if (type == NodeType.END) {
                    endCount++;
                    endNodeIds.add(id);
                }
            } catch (Exception e) {
                result.addError(id, "type", "无效的节点类型：" + typeText);
            }
        }
        if (startCount != 1) {
            result.addError(null, "nodes", "工作流必须包含且仅包含一个开始节点");
        }
        if (endCount < 1) {
            result.addError(null, "nodes", "工作流必须至少包含一个结束节点");
        }
        Map<String, List<String>> adjacency = new HashMap<>();
        Map<String, Integer> indegree = new HashMap<>();
        nodeIds.forEach(id -> {
            adjacency.put(id, new ArrayList<>());
            indegree.put(id, 0);
        });
        if (edges.isArray()) {
            Set<String> edgeIds = new HashSet<>();
            for (JsonNode edge : edges) {
                String id = text(edge, "id");
                String source = text(edge, "source");
                String target = text(edge, "target");
                if (id == null || id.isBlank()) {
                    result.addError(null, "edges", "连线ID不能为空");
                } else if (!edgeIds.add(id)) {
                    result.addError(id, "id", "连线ID重复");
                }
                if (source == null || !nodeIds.contains(source)) {
                    result.addError(id, "source", "连线的源节点不存在");
                }
                if (target == null || !nodeIds.contains(target)) {
                    result.addError(id, "target", "连线的目标节点不存在");
                }
                if (source != null && target != null && nodeIds.contains(source) && nodeIds.contains(target)) {
                    adjacency.get(source).add(target);
                    indegree.put(target, indegree.get(target) + 1);
                }
            }
        }
        validateGraph(result, nodeIds, startNodeIds, endNodeIds, adjacency, indegree);
        validateInputReferences(result, nodeById, nodeIds);
        if (!result.isValid()) {
            return result;
        }

        try {
            RunWorkflow workflow = compiler.compile("validate", definition);
            workflow.injectWorkflowToLoopNodes();
            for (Node node : workflow.getNodes()) {
                VerifyResult verifyResult = node.verifyConfig(Map.of());
                if (!verifyResult.isValid()) {
                    for (VerifyFail error : verifyResult.getErrors()) {
                        result.addError(node.getId(), error.getField(), error.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            result.addError(null, "definition", e.getMessage());
        }
        return result;
    }

    private void validateGraph(WorkflowValidationResult result,
                               Set<String> nodeIds,
                               Set<String> startNodeIds,
                               Set<String> endNodeIds,
                               Map<String, List<String>> adjacency,
                               Map<String, Integer> indegree) {
        for (String startId : startNodeIds) {
            if (indegree.getOrDefault(startId, 0) > 0) {
                result.addError(startId, "edges", "开始节点不能有入边");
            }
            if (adjacency.getOrDefault(startId, List.of()).isEmpty()) {
                result.addError(startId, "edges", "开始节点必须连接到至少一个下游节点");
            }
        }
        for (String endId : endNodeIds) {
            if (!adjacency.getOrDefault(endId, List.of()).isEmpty()) {
                result.addError(endId, "edges", "结束节点不能有出边");
            }
            if (indegree.getOrDefault(endId, 0) == 0) {
                result.addError(endId, "edges", "结束节点必须至少有一条入边");
            }
        }

        Set<String> reachable = reachableFrom(startNodeIds, adjacency);
        for (String nodeId : nodeIds) {
            if (!reachable.contains(nodeId)) {
                result.addError(nodeId, "edges", "该节点从开始节点不可达");
            }
        }

        Set<String> canReachEnd = reverseReachableFrom(endNodeIds, adjacency);
        for (String nodeId : nodeIds) {
            if (!canReachEnd.contains(nodeId)) {
                result.addError(nodeId, "edges", "该节点无法到达任何结束节点");
            }
        }

        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        for (String nodeId : nodeIds) {
            if (hasCycle(nodeId, adjacency, visiting, visited)) {
                result.addWarning(nodeId, "edges", "工作流存在环路，请确认循环条件能够正常退出");
                break;
            }
        }
    }

    private void validateInputReferences(WorkflowValidationResult result, Map<String, JsonNode> nodeById, Set<String> nodeIds) {
        for (Map.Entry<String, JsonNode> entry : nodeById.entrySet()) {
            JsonNode inputs = entry.getValue().path("inputConfigs");
            if (!inputs.isArray()) {
                continue;
            }
            Set<String> inputNames = new HashSet<>();
            for (JsonNode input : inputs) {
                String name = text(input, "name");
                if (name == null || name.isBlank()) {
                    result.addError(entry.getKey(), "inputConfigs", "输入参数名称不能为空");
                } else if (!inputNames.add(name)) {
                    result.addError(entry.getKey(), "inputConfigs." + name, "输入参数名称重复");
                }
                String classify = firstText(input, "classify", "sourceType");
                if (classify == null || classify.isBlank() || "NODE_OUTPUT".equals(classify)) {
                    String sourceNodeId = firstText(input, "sourceNodeId", "nodeId");
                    if (sourceNodeId == null || sourceNodeId.isBlank()) {
                        result.addError(entry.getKey(), "inputConfigs." + name, "来源节点不能为空");
                    } else if (!nodeIds.contains(sourceNodeId)) {
                        result.addError(entry.getKey(), "inputConfigs." + name, "来源节点不存在：" + sourceNodeId);
                    }
                    String outputName = firstText(input, "sourceOutputName", "outputName");
                    if (outputName == null || outputName.isBlank()) {
                        result.addError(entry.getKey(), "inputConfigs." + name, "来源节点输出名称不能为空");
                    }
                } else if ("VARIABLE".equals(classify) && isBlank(text(input, "variableName"))) {
                    result.addError(entry.getKey(), "inputConfigs." + name, "变量名称不能为空");
                } else if ("EXPRESSION".equals(classify) && isBlank(text(input, "expression"))) {
                    result.addError(entry.getKey(), "inputConfigs." + name, "表达式不能为空");
                }
            }
        }
    }

    private Set<String> reachableFrom(Set<String> startIds, Map<String, List<String>> adjacency) {
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new ArrayDeque<>(startIds);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (!visited.add(current)) {
                continue;
            }
            queue.addAll(adjacency.getOrDefault(current, List.of()));
        }
        return visited;
    }

    private Set<String> reverseReachableFrom(Set<String> endIds, Map<String, List<String>> adjacency) {
        Map<String, List<String>> reverse = new HashMap<>();
        adjacency.forEach((source, targets) -> {
            reverse.computeIfAbsent(source, ignored -> new ArrayList<>());
            for (String target : targets) {
                reverse.computeIfAbsent(target, ignored -> new ArrayList<>()).add(source);
            }
        });
        return reachableFrom(endIds, reverse);
    }

    private boolean hasCycle(String nodeId, Map<String, List<String>> adjacency, Set<String> visiting, Set<String> visited) {
        if (visited.contains(nodeId)) {
            return false;
        }
        if (!visiting.add(nodeId)) {
            return true;
        }
        for (String next : adjacency.getOrDefault(nodeId, List.of())) {
            if (hasCycle(next, adjacency, visiting, visited)) {
                return true;
            }
        }
        visiting.remove(nodeId);
        visited.add(nodeId);
        return false;
    }

    private String firstText(JsonNode node, String first, String second) {
        String firstValue = text(node, first);
        return isBlank(firstValue) ? text(node, second) : firstValue;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
