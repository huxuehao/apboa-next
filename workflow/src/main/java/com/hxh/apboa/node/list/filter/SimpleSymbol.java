package com.hxh.apboa.node.list.filter;

/**
 * 描述： 简单运算符
 *
 * @author huxuehao
 **/
public enum SimpleSymbol {
    EQ, // 等于
    NE, // 不等于
    GT, // 大于
    LT, // 小于
    GE, // 大于等于
    LE, // 小于等于

    CONTAINS, // 包含
    NOT_CONTAINS, // 不包含
    STARTS_WITH, // 开头匹配
    ENDS_WITH, // 结尾匹配
    EQUALS,// 等于
    NOT_EQUALS, // 不等于

    IS_TRUE, // 是true
    IS_FALSE, // 是false
}
