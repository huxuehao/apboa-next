package com.hxh.apboa.common.consts;

/**
 * 描述：RedisChannelTopic
 *
 * @author huxuehao
 **/
public class RedisChannelTopic {
    public static final String AGENT_REREGISTER_CHANNEL = "apboa:agent:cluster:reRegister";
    public static final String AGENT_UNREGISTER_CHANNEL = "apboa:agent:cluster:unRegister";

    public static final String AGENT_CONSTRUCTOR_REREGISTER_CHANNEL = "apboa:agent:cluster:constructor:reRegister";
    public static final String AGENT_CONSTRUCTOR_UNREGISTER_CHANNEL = "apboa:agent:cluster:constructor:unRegister";

    public static final String SK_SYNC_CHANNEL = "apboa:sk:sync";
    public static final String JOB_CLUSTER_CONTROL = "apboa:job:cluster:control";
    public static final String WS_CHANNEL_PATTERN = "apboa:ws:cluster:*";

    /** 系统参数变更频道（任一节点增/改 Params 后广播，订阅者按 paramKey 过滤处理） */
    public static final String PARAM_CHANGE_CHANNEL = "apboa:params:change";

    /** 分布式锁：内置工具同步到数据库 */
    public static final String LOCK_TOOLS_SYNC = "apboa:lock:tools:sync";
    /** 分布式锁：内置Hook同步到数据库 */
    public static final String LOCK_HOOKS_SYNC = "apboa:lock:hooks:sync";
    /** 分布式锁：内置技能包同步到数据库 */
    public static final String LOCK_SKILLS_SYNC = "apboa:lock:skills:sync";
    /** 分布式锁：技能包文件初始化同步 */
    public static final String LOCK_SKILL_INIT = "apboa:lock:skill:init";
    /** 分布式锁：聊天消息月度归档 */
    public static final String LOCK_MESSAGE_ARCHIVE = "apboa:lock:message:archive";

    /** 技能文件同步频道（通知 runner-file 节点增量同步） */
    public static final String SKILL_FILE_SYNC_CHANNEL = "apboa:skill:file:sync";
}
