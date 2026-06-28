package com.hxh.apboa.workflowbiz.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hxh.apboa.node.base.Node;
import com.hxh.apboa.node.base.NodeType;
import com.hxh.apboa.node.base.context.NodeContext;
import com.hxh.apboa.node.base.verify.VerifyFail;
import com.hxh.apboa.node.base.verify.VerifyResult;
import com.hxh.apboa.workflow.run.RunWorkflow;
import com.hxh.apboa.workflowbiz.core.WorkflowDefinitionCompiler;
import com.hxh.apboa.workflowbiz.service.WorkflowValidator;
import com.hxh.apboa.workflowbiz.vo.WorkflowValidationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
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
            result.addError(null, "nodes", "workflow.nodes must contain at least START and END nodes");
            return result;
        }
        if (!edges.isMissingNode() && !edges.isArray()) {
            result.addError(null, "edges", "workflow.edges must be an array");
        }

        Set<String> nodeIds = new HashSet<>();
        int startCount = 0;
        int endCount = 0;
        for (JsonNode node : nodes) {
            String id = text(node, "id");
            String typeText = text(node, "type");
            if (id == null || id.isBlank()) {
                result.addError(null, "id", "node id is required");
                continue;
            }
            if (!nodeIds.add(id)) {
                result.addError(id, "id", "node id is duplicated");
            }
            try {
                NodeType type = NodeType.valueOf(typeText);
                if (!WorkflowDefinitionCompiler.supportedTypes().contains(type)) {
                    result.addError(id, "type", "node type is not supported: " + typeText);
                }
                if (type == NodeType.START) startCount++;
                if (type == NodeType.END) endCount++;
            } catch (Exception e) {
                result.addError(id, "type", "node type is invalid: " + typeText);
            }
        }
        if (startCount != 1) {
            result.addError(null, "nodes", "workflow must contain exactly one START node");
        }
        if (endCount < 1) {
            result.addError(null, "nodes", "workflow must contain at least one END node");
        }
        if (edges.isArray()) {
            for (JsonNode edge : edges) {
                String id = text(edge, "id");
                String source = text(edge, "source");
                String target = text(edge, "target");
                if (source == null || !nodeIds.contains(source)) {
                    result.addError(id, "source", "edge source node does not exist");
                }
                if (target == null || !nodeIds.contains(target)) {
                    result.addError(id, "target", "edge target node does not exist");
                }
            }
        }
        if (!result.isValid()) {
            return result;
        }

        try {
            RunWorkflow workflow = compiler.compile("validate", definition);
            NodeContext context = new NodeContext("validate");
            for (Node node : workflow.getNodes()) {
                VerifyResult verifyResult = node.verifyConfig(java.util.Map.of());
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

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
