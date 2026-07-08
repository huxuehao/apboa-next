package com.hxh.apboa.workflowbiz.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hxh.apboa.node.base.EnhancedNode;
import com.hxh.apboa.node.base.Node;
import com.hxh.apboa.node.loop.LoopSubWorkflowCompiler;
import com.hxh.apboa.workflow.core.Edge;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 子工作流编译器实现。
 * 复用 WorkflowDefinitionCompiler 的编译逻辑，将 JSON 定义转为 Node/Edge 对象。
 *
 * @author huxuehao
 */
@Component
@RequiredArgsConstructor
public class LoopSubWorkflowCompilerImpl implements LoopSubWorkflowCompiler {

    private final WorkflowDefinitionCompiler compiler;
    private final ObjectMapper objectMapper;

    @Override
    public List<Node> compileNodes(JsonNode subNodesJson) {
        List<Node> nodes = new ArrayList<>();
        if (subNodesJson == null || !subNodesJson.isArray()) {
            return nodes;
        }
        for (JsonNode nodeJson : subNodesJson) {
            nodes.add(compiler.compileSubNode(nodeJson));
        }
        return nodes;
    }

    @Override
    public List<Edge> compileEdges(JsonNode subEdgesJson) {
        List<Edge> edges = new ArrayList<>();
        if (subEdgesJson == null || !subEdgesJson.isArray()) {
            return edges;
        }
        for (JsonNode edgeJson : subEdgesJson) {
            Edge edge = objectMapper.convertValue(edgeJson, Edge.class);
            edges.add(edge);
        }
        return edges;
    }
}
