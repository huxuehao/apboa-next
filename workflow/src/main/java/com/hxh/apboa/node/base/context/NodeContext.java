package com.hxh.apboa.node.base.context;

import com.hxh.apboa.node.base.request.RequestParams;
import com.hxh.apboa.node.base.NodeOutput;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 描述：节点上下文
 *
 * @author huxuehao
 **/
@Getter
@Setter
@Slf4j
public class NodeContext {
    /**
     * 工作流实例ID
     */
    private String workflowInstanceId;
    /**
     * 变量上下文
     * 用于存储流程执行过程中的所有变量（包括各个节点的输出）
     */
    private VariableContext variables;
    /**
     * 请求参数
     */
    private RequestParams requestParams;
    /**
     * 下一个节点ID
     * 用于存储流程执行过程中的下一个节点ID。
     * 该节点ID为null时，表示下一个节点不是由当前节点决定的。此时开发者将通过节点的出边来决定下一个节点。
     */
    private String nextNodeId;
    /**
     * 节点执行轨迹，严格按照本次运行的节点执行顺序记录。
     */
    private final List<NodeOutput> executionTrace;
    /** 节点生命周期旁路监听器；异常只记日志，不能中断工作流。 */
    private NodeExecutionListener executionListener;
    /** 本次运行内单调递增，用于区分循环节点的多次执行。 */
    private long executionSequence;

    public NodeContext(String workflowInstanceId) {
        this.workflowInstanceId = workflowInstanceId;
        this.variables = new VariableContext();
        this.executionTrace = new ArrayList<>();
        this.executionListener = NodeExecutionListener.noop();
    }

    /**
     * 重置下一个节点ID
     */
    public void resetNextNodeId() {
        this.nextNodeId = null;
    }

    public String notifyExecutionStarted(NodeOutput output) {
        if (output == null) {
            return null;
        }
        String invocationId = output.getNodeId() + "#" + (++executionSequence);
        try {
            executionListener.onNodeStarted(invocationId, output);
        } catch (RuntimeException e) {
            log.warn("节点开始监听失败 instanceId={}, nodeId={}: {}",
                    workflowInstanceId, output.getNodeId(), e.getMessage());
        }
        return invocationId;
    }

    public void recordExecution(String invocationId, NodeOutput output) {
        if (output != null) {
            executionTrace.add(output);
            try {
                executionListener.onNodeFinished(invocationId, output);
            } catch (RuntimeException e) {
                log.warn("节点完成监听失败 instanceId={}, nodeId={}: {}",
                        workflowInstanceId, output.getNodeId(), e.getMessage());
            }
        }
    }

    /** 兼容独立调用方；引擎主链应传入 notifyExecutionStarted 返回的 invocationId。 */
    public void recordExecution(NodeOutput output) {
        recordExecution(null, output);
    }

    public void setExecutionListener(NodeExecutionListener executionListener) {
        this.executionListener = executionListener == null
                ? NodeExecutionListener.noop()
                : executionListener;
    }
}
