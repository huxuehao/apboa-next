package com.hxh.apboa.workflow.run;

import com.hxh.apboa.node.base.Node;
import com.hxh.apboa.node.base.NodeOutput;
import com.hxh.apboa.common.enums.NodeType;
import com.hxh.apboa.node.base.context.NodeContext;
import com.hxh.apboa.node.base.request.ParamItem;
import com.hxh.apboa.node.loop.LoopNode;
import com.hxh.apboa.node.start.Param;
import com.hxh.apboa.node.start.StartNode;
import com.hxh.apboa.workflow.core.Edge;
import com.hxh.apboa.workflow.core.Workflow;

import java.util.ArrayList;
import java.util.List;

/**
 * 描述：执行工作流
 *
 * @author huxuehao
 **/
public class RunWorkflow extends Workflow {

    public RunWorkflow(String workflowId) {
        super(workflowId);
    }

    /**
     * 执行工作流
     *
     * @param context 执行上下文
     * @return 响应结果
     */
    @Override
    public Object execute(NodeContext context) {
        injectWorkflowToLoopNodes();
        List<Node> nodes = executeStart(context);
        while (nodes != null && !nodes.isEmpty()) {
            // 条件成立：下一执行节点为单个
            if (nodes.size() == 1) {
                Node node = nodes.getFirst();
                NodeOutput output = node.execute(context);
                assertSuccess(output);
                // 节点类型为结束节点，结束循环，触发响应
                if (node.getType() == NodeType.END) {
                    return output.getDefaultOutput();
                }
                nodes = getNextNode(node, context);
            }
            // 条件成立：下一执行节点为多个
            else {
                nodes = executeNextNodes(nodes, context);
            }
        }

        throw new RuntimeException("没有有效的结束节点");
    }

    /**
     * 批量执行下一节点
     *
     * @param nodes 当前节点
     * @param context 执行上下文
     * @return 下一个节点
     */
    private List<Node> executeNextNodes(List<Node> nodes, NodeContext context) {
        List<Node> nextNodes = null;
        // TODO: 待优化，基于虚拟线程，可以搞成 异步执行+阻塞 提高效率，但是要考虑context变量线程隔离的问题
        for (Node node : nodes) {
            NodeOutput output = node.execute(context);
            assertSuccess(output);
            if (nextNodes != null && !nextNodes.isEmpty()) {
                continue;
            }
            nextNodes = getNextNode(node, context);
        }
        if (nextNodes == null) {
           throw new RuntimeException("没有有效的下一节点");
        }
        return nextNodes;
    }

    /**
     * 向所有 LoopNode 注入当前工作流实例，使其能够执行子工作流。
     */
    public void injectWorkflowToLoopNodes() {
        for (Node node : getNodes()) {
            if (node instanceof LoopNode loopNode) {
                loopNode.getConfig().setWorkflow(this);
            }
        }
    }

    /**
     * 执行开始节点
     *
     * @return 下一个节点集合（有可能会同时执行多个下一节点）
     */
    private List<Node> executeStart(NodeContext context) {
        StartNode startNode = (StartNode) getStartNode();

        // 完善开始节点的请求参数
        List<Param> params = startNode.getConfig() == null || startNode.getConfig().getParams() == null
                ? List.of()
                : startNode.getConfig().getParams();
        List<ParamItem> requestParams = context.getRequestParams() == null || context.getRequestParams().getParams() == null
                ? List.of()
                : context.getRequestParams().getParams();
        for (ParamItem reqParam : requestParams) {
            for (Param originalParam : params) {
                if (reqParam.getName().equals(originalParam.getName())) {
                    originalParam.setValue(reqParam.getValue());
                    break;
                }
            }
        }

        NodeOutput output = startNode.execute(context);
        assertSuccess(output);
        return getNextNode(startNode, context);
    }

    private void assertSuccess(NodeOutput output) {
        if (output == null || output.getStatus() != NodeOutput.ExecutionStatus.SUCCESS) {
            String nodeName = output == null ? "未知节点" : output.getNodeName();
            String error = output == null ? "节点未返回执行结果" : output.getErrorMessage();
            if ((error == null || error.isBlank()) && output.getVerifyErrors() != null && !output.getVerifyErrors().isEmpty()) {
                error = output.getVerifyErrors().toString();
            }
            throw new RuntimeException(nodeName + "执行失败" + (error == null || error.isBlank() ? "" : ": " + error));
        }
    }

    /**
     * 获取下一个节点
     *
     * @param node 当前节点
     * @param context 执行上下文
     * @return 下一个节点集合（有可能会同时执行多个下一节点）
     */
    private List<Node> getNextNode(Node node, NodeContext context) {
        String nodeNextNodeId = node.getNextNodeId(context);
        // 条件成立：节点内部没有获取到下一个节点ID
        if (nodeNextNodeId == null || nodeNextNodeId.isEmpty()) {
            List<String> outEdgeIds = node.getOutEdgeIds();
            if (outEdgeIds.isEmpty()) {
                return null;
            }
            ArrayList<Node> nodes = new ArrayList<>();
            for (String outEdgeId : outEdgeIds) {
                Edge edgeById = getEdgeById(outEdgeId);
                nodes.add(getNodeById(edgeById.getTarget()));
            }
            return nodes;
        }
        // 条件成立：节点内部获取到下一个节点ID
        else {
            return List.of(getNodeById(nodeNextNodeId));
        }
    }
}
