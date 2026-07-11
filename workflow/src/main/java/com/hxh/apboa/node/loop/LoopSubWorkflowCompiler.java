package com.hxh.apboa.node.loop;

import com.hxh.apboa.node.base.Node;
import com.hxh.apboa.workflow.core.Edge;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * 子工作流编译器桥接接口。
 * 由 biz-workflow 模块实现，负责将 JSON 格式的子工作流定义编译为 Node/Edge 对象。
 *
 * @author huxuehao
 */
public interface LoopSubWorkflowCompiler {
    /**
     * 编译子工作流节点定义。
     *
     * @param subNodesJson JSON数组，每个元素为节点定义
     * @return 编译后的 Node 列表
     */
    List<Node> compileNodes(JsonNode subNodesJson);

    /**
     * 编译子工作流边定义。
     *
     * @param subEdgesJson JSON数组，每个元素为边定义
     * @return 编译后的 Edge 列表
     */
    List<Edge> compileEdges(JsonNode subEdgesJson);
}
