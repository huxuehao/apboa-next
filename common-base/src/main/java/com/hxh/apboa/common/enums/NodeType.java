package com.hxh.apboa.common.enums;

/**
 * 描述：节点类型
 *
 * @author huxuehao
 **/
public enum NodeType {
    TEST_NODE, // 测试节点
    /**
     * 开始节点
     */
    START,
    /**
     * 结束节点
     */
    END,
    /**
     * 获取缓存节点
     */
    CACHE_FETCH,
    /**
     * 刷新缓存节点
     */
    CACHE_REFRESH,
    /**
     * 删除缓存节点
     */
    CACHE_REMOVE,
    /**
     * 设置缓存节点
     */
    CACHE_SET,
    /**
     * 代码节点
     */
    CODE,
    /**
     * 删除数据库节点
     */
    DB_DELETE,
    /**
     * 插入数据库节点
     */
    DB_INSERT,
    /**
     * 查询数据库节点
     */
    DB_SELECT,
    /**
     * 更新数据库节点
     */
    DB_UPDATE,
    /**
     * 外部http请求节点
     */
    HTTP_EXTERNAL,
    /**
     * 内联http请求节点
     */
    HTTP_INLINE,
    /**
     * 条件判断节点
     */
    IF_ELSE,
    /**
     * 迭代节点
     */
    ITERATE,
    /**
     * 序列化节点
     */
    SERIALIZE,
    /**
     * 反序列化节点
     */
    UNSERIALIZE,
    /**
     * 列表过滤节点
     */
    LIST_FILTER,
    /**
     * 列表排序节点
     */
    LIST_SORT,
    /**
     * 语言模型节点
     */
    LLM,
    /**
     * 智能体节点
     */
    AGENT,
    /**
     * 循环节点
     */
    LOOP,
    /**
     * 队列推送节点
     */
    MQ_PUSH,
    /**
     * 插件节点
     */
    PLUGIN,
    /**
     * 字符串模板节点
     */
    STRING_TEMPLATE,
    /**
     * 字符串分割节点
     */
    STRING_SPLIT,
    /**
     * 变量聚合节点
     */
    VARIABLE_AGG,
    /**
     * 分类节点（作用和 switch-case 一样）
     */
    CLASSIFY,
    /**
     * 非空选择节点
     */
    NON_EMPTY_SELECT,
    /**
     * 匹配结果节点
     */
    MATCH_RESULT,
    /**
     * 工具执行节点
     */
    TOOL_EXECUTE,
    /**
     * mcp调用节点
     */
    MCP_CALL,
    /**
     * 发送邮件节点
     */
    EMAIL_SEND,
    /**
     * 企业微信消息节点
     */
    WECOM_SEND,
    /**
     * 钉钉消息节点
     */
    DINGTALK_SEND,
    /**
     * 飞书消息节点
     */
    FEISHU_SEND
}
