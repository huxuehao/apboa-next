package com.hxh.apboa.common.enums;

/**
 * 描述：节点类型
 *
 * @author huxuehao
 **/
public enum NodeType {
    // 基础流程控制
    START,
    END,

    // 代码执行
    CODE,

    // HTTP 节点
    HTTP_EXTERNAL,
    HTTP_INLINE,

    // 条件分支
    IF_ELSE,
    NON_EMPTY_SELECT,
    MATCH_RESULT,

    // 迭代与循环
    ITERATE,
    LOOP,

    // 数据库操作
    DB_SELECT,
    DB_INSERT,
    DB_UPDATE,
    DB_DELETE,

    // 缓存操作
    CACHE_FETCH,
    CACHE_SET,
    CACHE_REMOVE,
    CACHE_REFRESH,

    // 消息队列
    MQ_PUSH,

    // 变量聚合
    VARIABLE_AGG,

    // 列表操作
    LIST_FILTER,
    LIST_SORT,

    // 字符串操作
    STRING_SPLIT,
    STRING_TEMPLATE,

    // 序列化
    SERIALIZE,
    UNSERIALIZE,

    // 插件
    PLUGIN,

    // LLM
    LLM,

    // 测试
    TEST_NODE
}
