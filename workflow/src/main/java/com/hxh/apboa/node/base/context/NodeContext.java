package com.hxh.apboa.node.base.context;

import com.hxh.apboa.node.base.request.RequestParams;
import lombok.Getter;
import lombok.Setter;

/**
 * 描述：节点上下文
 *
 * @author huxuehao
 **/
@Getter
@Setter
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
     * 用于存储http请求的核心请求。
     */
    private RequestParams requestParams;
    /**
     * 下一个节点ID
     * 用于存储流程执行过程中的下一个节点ID。
     * 该节点ID为null时，表示下一个节点不是由当前节点决定的。此时开发者将通过节点的出边来决定下一个节点。
     */
    private String nextNodeId;

    public NodeContext(String workflowInstanceId) {
        this.workflowInstanceId = workflowInstanceId;
        this.variables = new VariableContext();
    }

    /**
     * 重置下一个节点ID
     */
    public void resetNextNodeId() {
        this.nextNodeId = null;
    }
}
