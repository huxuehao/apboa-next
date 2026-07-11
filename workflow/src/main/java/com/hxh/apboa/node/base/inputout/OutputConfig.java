package com.hxh.apboa.node.base.inputout;

import lombok.Getter;
import lombok.Setter;

/**
 * 描述：节点输出配置定义
 *
 * @author huxuehao
 **/
@Getter
@Setter
public class OutputConfig {
    /**
     * 输出变量名
     */
    private String name;
    /**
     * 输出变量类型
     */
    private VariableType type;
    /**
     * 来源节点ID
     */
    private String fromNodeId;


    public enum VariableType {
        String,
        Long,
        Integer,
        Float,
        Double,
        Boolean,
        Array,
        Object
    }

    /**
     * 创建输出配置
     */
    public static OutputConfig create(String nodeId, String name, VariableType type){
        OutputConfig config = new OutputConfig();
        config.name = name;
        config.type = type;
        config.fromNodeId = nodeId;
        return config;
    }
}
