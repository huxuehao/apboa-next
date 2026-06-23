package com.hxh.apboa.node.base;

import com.hxh.apboa.node.base.context.NodeContext;
import com.hxh.apboa.node.base.verify.VerifyResult;

import java.util.List;
import java.util.Map;

/**
 * 描述：标准化节点接口
 * 每一个节点必须实现此节点接口
 *
 * @author huxuehao
 **/
public interface Node {
    /**
     * 节点ID
     */
    String getId();

    /**
     * 节点名称
     */
    String getName();

    /**
     * 节点类型
     */
    NodeType getType();

    /**
     * 执行节点
     */
    NodeOutput execute(NodeContext nodeContext);

    /**
     * 校验节点配置
     */
    VerifyResult verifyConfig(Map<String, Object> inputs);

    /**
     * 获取节点的下一个节点ID
     */
    default String getNextNodeId(NodeContext context) {
        return null;
    }

    /**
     * 添加节点的入边ID
     */
    void addInEdgeId(String edgeId);

    /**
     * 添加节点的出边ID
     */
    void addOutEdgeId(String edgeId);

    /**
     * 获取节点的入边ID
     */
    List<String> getInEdgeIds();

    /**
     * 获取节点的出边ID
     */
    List<String> getOutEdgeIds();

    /**
     * 判断某个边是否是节点的入边
     */
    boolean isInEdge(String edgeId);

    /**
     * 判断某个边是否是节点的出边
     */
    boolean isOutEdge(String edgeId);
}
