package com.hxh.apboa.workflow.core;

import com.hxh.apboa.node.base.Node;
import com.hxh.apboa.node.base.NodeOutput;
import com.hxh.apboa.common.enums.NodeType;
import com.hxh.apboa.node.base.context.NodeContext;
import com.hxh.apboa.node.base.context.VariableContext;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 描述：工作流
 *
 * @author huxuehao
 **/
public abstract class Workflow {
    @Getter
    @Setter
    private String workflowId;
    // 边
    private List<Edge> edges;
    // 节点
    private List<Node> nodes;

    public List<Edge> getEdges() {
        return edges == null ? List.of() : edges;
    }

    public List<Node> getNodes() {
        return nodes == null ? List.of() : nodes;
    }

    public Workflow(String workflowId) {
        this.workflowId = workflowId;
    }

    /**
     * 执行工作流
     * @param context 执行上下文
     */
    abstract public Object execute(NodeContext context);

    /**
     * 执行子工作流（不含 START/END 约束的轻量级工作流）
     * 从指定入口节点开始，按拓扑顺序执行，返回最后一个节点的默认输出。
     *
     * @param subNodes     子工作流节点列表
     * @param subEdges     子工作流边列表
     * @param entryNodeId  入口节点ID
     * @param parentContext 父工作流的变量上下文（子工作流将继承此上下文中的变量）
     * @return 子工作流最后一个节点的默认输出
     */
    public Object executeSubWorkflow(List<Node> subNodes, List<Edge> subEdges,
                                      String entryNodeId, VariableContext parentContext) {
        if (subNodes == null || subNodes.isEmpty()) {
            throw new RuntimeException("子工作流节点列表不能为空");
        }
        if (subEdges == null || subEdges.isEmpty()) {
            throw new RuntimeException("子工作流边列表不能为空");
        }
        if (entryNodeId == null || entryNodeId.isEmpty()) {
            throw new RuntimeException("子工作流入口节点ID不能为空");
        }

        // 构建临时节点查找映射
        Map<String, Node> nodeMap = new HashMap<>();
        for (Node node : subNodes) {
            nodeMap.put(node.getId(), node);
        }

        // 构建临时边查找映射
        Map<String, Edge> edgeMap = new HashMap<>();
        for (Edge edge : subEdges) {
            edgeMap.put(edge.getId(), edge);
        }

        // 获取入口节点
        Node entryNode = nodeMap.get(entryNodeId);
        if (entryNode == null) {
            throw new RuntimeException("子工作流入口节点不存在: " + entryNodeId);
        }

        // 构建子工作流的 NodeContext，共享父上下文中的变量
        NodeContext subContext = new NodeContext(this.workflowId + "-sub");
        // 将父上下文的变量复制到子上下文
        if (parentContext != null) {
            subContext.getVariables().getAllVariables().putAll(parentContext.getAllVariables());
            subContext.getVariables().getNodeOutputs().putAll(parentContext.getNodeOutputs());
        }

        // 按拓扑顺序执行节点
        Node currentNode = entryNode;
        NodeOutput lastOutput = null;
        while (currentNode != null) {
            lastOutput = currentNode.execute(subContext);

            // 获取下一个节点
            String nextNodeId = currentNode.getNextNodeId(subContext);
            if (nextNodeId != null && !nextNodeId.isEmpty()) {
                currentNode = nodeMap.get(nextNodeId);
            } else {
                // 通过出边查找下一个节点
                List<String> outEdgeIds = currentNode.getOutEdgeIds();
                if (outEdgeIds != null && !outEdgeIds.isEmpty()) {
                    Edge outEdge = edgeMap.get(outEdgeIds.getFirst());
                    if (outEdge != null) {
                        currentNode = nodeMap.get(outEdge.getTarget());
                    } else {
                        currentNode = null;
                    }
                } else {
                    currentNode = null;
                }
            }
        }

        // 将子工作流中的变量变更同步回父上下文
        if (parentContext != null) {
            parentContext.getAllVariables().putAll(subContext.getVariables().getAllVariables());
            parentContext.getNodeOutputs().putAll(subContext.getVariables().getNodeOutputs());
        }

        return lastOutput != null ? lastOutput.getDefaultOutput() : null;
    }

    /**
     * 添加节点
     * @param node 节点
     */
    public void addNode(Node node) {
        if (node.getId() == null) {
            throw new RuntimeException("节点ID不能为空");
        }
        if (nodes == null) {
            nodes = new ArrayList<>();
        }
        nodes.add(node);
    }

    /**
     * 获取开始节点
     */
    public Node getStartNode () {
        List<Node> startNodes = nodes.stream().filter(node -> node.getType() == NodeType.START).toList();

        if (startNodes.isEmpty()) {
            throw new RuntimeException("没有开始节点");
        } else if (startNodes.size() > 1) {
            throw new RuntimeException("只能有一个开始节点");
        }

        return startNodes.getFirst();
    }

    /**
     * 判断是否是结束节点
     * @param node 节点
     */
    public boolean isEndNode(Node  node) {
        return node.getType() == NodeType.END;
    }

    /**
     * 根据ID获取节点
     * @param nodeId 节点ID
     */
    public Node getNodeById(String nodeId) {
        for (Node node : nodes) {
            if (node.getId().equals(nodeId)) {
                return node;
            }
        }
        throw new RuntimeException("没有找到节点：" + nodeId);
    }

    /**
     * 添加边
     * @param edge 边
     */
    public void addEdge(Edge edge) {
        if (edge.getId() == null) {
            throw new RuntimeException("边ID不能为空");
        }
        if (nodes == null || nodes.isEmpty()) {
            throw new RuntimeException("请先添加节点");
        }

        if (edges == null) {
            edges = new ArrayList<>();
        }
        edges.add(edge);

        // 给节点添加入边和出边
        for (Node node : nodes) {
            if (node.getId().equals(edge.getSource())) {
                node.addOutEdgeId(edge.getId());
            } else if (node.getId().equals(edge.getTarget())) {
                node.addInEdgeId(edge.getId());
            }
        }
    }

    /**
     * 根据ID获取节点
     * @param edgeId 边ID
     */
    public Edge getEdgeById(String edgeId) {
        for (Edge node : edges) {
            if (node.getId().equals(edgeId)) {
                return node;
            }
        }
        throw new RuntimeException("没有找到边：" + edgeId);
    }
}
