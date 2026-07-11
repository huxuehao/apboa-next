package com.hxh.apboa.node.list.filter;

import com.hxh.apboa.node.base.NodeConfig;
import lombok.Getter;
import lombok.Setter;

/**
 * 描述：配置类
 *
 * @author huxuehao
 **/
@Getter
@Setter
public class Config implements NodeConfig {
    /**
     * 过滤模式
     * 简单模式：效率最高，直接使用Java API进行过滤
     * 表达式模式：效率低，需要对每个元素进行GROOVY求值
     */
    private FilterMode mode;
    /**
     * 支持类型
     * 配合简单模式使用
     */
    private SupportType supportType;
    /**
     * 输入值是否为空时使用
     * 配合简单模式使用
     */
    private Boolean itemIsNullUse;
    /**
     * 简单运算符
     * 配合简单模式使用
     */
    private SimpleSymbol simpleSymbol;
    /**
     * 表达式求值器类型
     * 配合表达式模式（EXPRESSION）使用
     */
    private String evaluatorType = "GROOVY";
    /**
     * 条件表达式
     * 过滤模式为简单模式：只支持两种写法（item 或 item.子元素）
     * 过滤模式为表达式模式：支持GROOVY表达式，注意变量名仅可为item
     */
    private String condition;
    /**
     * 比较值
     */
    private CompareTo compareTo;
}
