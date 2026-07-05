package com.hxh.apboa.node.base.inputout;

import lombok.Getter;
import lombok.Setter;

/**
 * 描述：节点输入配置定义
 *
 * @author huxuehao
 **/
@Getter
@Setter
public class InputConfig {
    /**
     * 输入名称
     */
    private String name;
    /**
     * 输入分类
     * 类型需要基于值本身，自行推测，该字段已弃用，保留是为了兼容性考虑
     */
    @Deprecated
    private OutputConfig.VariableType type;
    /**
     * 输入分类
     */
    private InputClassify classify;
    /**
     * 常量值（若type为“常量”，常量值即为该值）
     */
    private Object value;
    /**
     * 变量名称（若type为“变量”，那么意味着本次的输入是从变量上下文中查找具体的值）
     */
    private String variableName;
    /**
     * 来源节点ID（若type为“其他节点输出”，则用来标记来源于哪一个节点）
     */
    private String sourceNodeId;
    /**
     * 来源节点输出名称（与来源节点ID配合使用，用于定位是来源节点中的那一个输出变量）
     */
    private String sourceOutputName;
    /**
     * 表达式（若type为“表达式”，即为表达式的内容）
     */
    private String expression;

    /**
     * 输入类型枚举
     * 说明：不存在其他“节点的输入”类型，因为节点的输入，只能来源于其他节点的输出
     */
    public enum InputClassify {
        CONSTANT,      // 常量
        VARIABLE,      // 变量
        NODE_OUTPUT,   // 其他节点输出
        EXPRESSION     // 表达式
    }

    /**
     * 私有构造函数，避免外部进行实例化
     */
    public InputConfig() {}

//    /**
//     * 创建一个常量输入配置
//     *
//     * @param value 常量值
//     * @return 输入配置
//     */
//    public static InputConfig buildConstantInputConfig(String name, Object value) {
//        InputConfig config = new InputConfig();
//        config.name = name;
//        config.classify = InputClassify.CONSTANT;
//        config.value = value;
//        return config;
//    }
//
//    /**
//     * 创建一个变量输入配置
//     *
//     * @param variableName 变量名
//     * @return 输入配置
//     */
//    public static InputConfig buildVariableInputConfig(String name, String variableName) {
//        InputConfig config = new InputConfig();
//        config.name = name;
//        config.classify = InputClassify.VARIABLE;
//        config.variableName = variableName;
//        return config;
//    }
//
//    /**
//     * 创建一个其他节点输出输入配置
//     *
//     * @param sourceNodeId 来源节点ID
//     * @param sourceOutputName 来源节点输出名称
//     * @return 输入配置
//     */
//    public static InputConfig buildNodeOutputInputConfig(String name, OutputConfig.VariableType type, String sourceNodeId, String sourceOutputName) {
//        InputConfig config = new InputConfig();
//        config.name = name;
//        config.type = type;
//        config.classify = InputClassify.NODE_OUTPUT;
//        config.sourceNodeId = sourceNodeId;
//        config.sourceOutputName = sourceOutputName;
//        return config;
//    }
//
//    /**
//     * 创建一个表达式输入配置
//     *
//     * @param expression 表达式
//     * @return 输入配置
//     */
//    public static InputConfig buildExpressionInputConfig(String name, String expression) {
//        InputConfig config = new InputConfig();
//        config.name = name;
//        config.classify = InputClassify.EXPRESSION;
//        config.expression = expression;
//        return config;
//    }
}
