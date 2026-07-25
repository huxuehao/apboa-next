package com.hxh.apboa.node.condition;

import com.hxh.apboa.node.base.NodeConfig;
import com.hxh.apboa.node.base.inputout.OutputConfig;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
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
    // 条件分支列表（有序，index 0 为 IF，其余为 ELSE IF）
    private List<Branch> branches;
    // 所有分支均未命中时跳转的下游节点ID（ELSE 兜底）
    private String elseNextNodeId;

    // ============ 以下为旧版单条件平铺字段，仅用于反序列化兼容 ============
    // 条件表达式
    @Deprecated
    private String conditionExpression;
    /*
     * 计算范围（本身或长度）
     * 本身：元素本身计算
     * 长度：元素长度计算
     */
    @Deprecated
    private Scope scope;
    // 输入值是否为空时使用
    @Deprecated
    private Boolean inputIsNullUse;
    // 运算符
    @Deprecated
    private Symbol symbol;
    // 被比较值
    @Deprecated
    private CompareTo compareTo;
    // 真值节点ID
    @Deprecated
    private String trueNextNodeId;
    // 假值节点ID
    @Deprecated
    private String falseNextNodeId;

    /**
     * 旧版平铺配置规范化为多分支结构，保证已发布旧版本工作流可继续执行
     */
    public void normalize() {
        if ((branches != null && !branches.isEmpty()) || symbol == null) {
            return;
        }
        Branch branch = new Branch();
        branch.setScope(scope);
        branch.setInputIsNullUse(inputIsNullUse);
        branch.setSymbol(symbol);
        branch.setConditionExpression(conditionExpression);
        branch.setCompareTo(compareTo);
        branch.setNextNodeId(trueNextNodeId);
        branches = new ArrayList<>();
        branches.add(branch);
        elseNextNodeId = falseNextNodeId;
    }

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
