package com.hxh.apboa.node.condition;

import com.hxh.apboa.node.base.NodeConfig;
import com.hxh.apboa.node.base.inputout.OutputConfig;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 描述：IfElse节点配置
 *
 * @author huxuehao
 **/
@Getter
@Setter
public class Config implements NodeConfig {
    // 表达式求值器类型
    private String evaluatorType = "GROOVY";
    // 条件表达式
    private String conditionExpression;
    // 允许的输入值类型
    private List<OutputConfig.VariableType> allowInputType;
    /*
     * 计算范围（本身或长度）
     * 本身：元素本身计算
     * 长度：元素长度计算
     */
    private Scope scope;
    // 输入值是否为空时使用
    private Boolean inputIsNullUse;
    // 运算符
    private Symbol symbol;
    // 被比较值
    private CompareTo compareTo;
    // 真值节点ID
    private String trueNextNodeId;
    // 假值节点ID
    private String falseNextNodeId;

    /**
     * 条件分支计算范围
     **/
    public enum Scope {
        SELF, // 元素本身计算
        LENGTH // 长度计算
    }

    public enum Symbol {
        EQ, // 等于
        NE, // 不等于
        GT, // 大于
        LT, // 小于
        GE, // 大于等于
        LE, // 小于等于
        CONTAINS, // 包含
        NOT_CONTAINS, // 不包含
        IS_ALL, // 全部是
        STARTS_WITH, // 开头匹配
        ENDS_WITH, // 结尾匹配
        EQUALS,// 等于
        NOT_EQUALS, // 不等于
        IS_TRUE, // 是true
        IS_FALSE, // 是false
        EXPRESSION, // 表达式
    }

}
