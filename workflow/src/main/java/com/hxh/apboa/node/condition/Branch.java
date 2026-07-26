package com.hxh.apboa.node.condition;

import lombok.Getter;
import lombok.Setter;

/**
 * 描述：条件分支（IF / ELSE IF），按顺序求值，首个命中即路由
 *
 * @author huxuehao
 **/
@Getter
@Setter
public class Branch {
    // 分支名称（可选，用于输出与展示）
    private String name;
    /*
     * 计算范围（本身或长度）
     * 本身：元素本身计算
     * 长度：元素长度计算
     */
    private Config.Scope scope;
    // 输入值是否为空时使用
    private Boolean inputIsNullUse;
    // 运算符
    private Config.Symbol symbol;
    // 条件表达式（symbol 为 EXPRESSION 时使用）
    private String conditionExpression;
    // 被比较值
    private CompareTo compareTo;
    // 命中后跳转的下游节点ID
    private String nextNodeId;
}
