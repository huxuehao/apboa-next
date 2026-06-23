package com.hxh.apboa.node.condition;

import lombok.Getter;
import lombok.Setter;

/**
 * 描述：被比较的值的包装
 *
 * @author huxuehao
 **/
@Getter
@Setter
public class CompareTo {
    private Type type;
    /**
     * 被比较的值
     * 如果是type是变量，那么value为节点的输出名
     * 如果是type是常量，那么value为常量值
     */
    private Object value;
    // 如果是变量，那么需要源节点ID
    private String sourceNodeId;

    public enum Type {
        CONSTANT, // 常量
        VARIABLE // 变量（来自于节点的输出）
    }
}
