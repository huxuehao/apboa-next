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

    /**
     * 会话「一键授权」开关 key 前缀（+ sessionId/threadId，值 "1"，TTL 30 天滚动）。
     * console 侧 ChatSessionService 读写，runtime 侧 IConfirmationHook 在 stopAgent 前实时读，
     * 开着则直接放行需确认工具（不走 暂停→确认→resume 循环）。
     */
    public static final String CHAT_AUTO_APPROVE_KEY_PREFIX = "apboa:chat:auto-approve:";

    /**
     * TTS 播报控制频道（websocket 服务 → runtime）：JSON 的 TtsCtrlMessage，
     * open=有人订阅该 thread 的播报（runtime 建合成会话）/ close=退订（打断并释放会话）。
     */
    public static final String TTS_CTRL_CHANNEL = "apboa:tts:ctrl";

    /**
     * TTS 流事件频道（runtime → websocket 服务）：JSON 的 TtsStreamEvent，
     * start（含音频格式）/ end / error，由 websocket 服务转发给订阅该 thread 的前端。
     */
    public static final String TTS_EVENT_CHANNEL = "apboa:tts:event";

    /**
     * TTS 音频帧频道（runtime → websocket 服务）：二进制 TtsAudioFrame 封包，
     * 必须经 BinaryChannelSubscriber 订阅（字符串通道会破坏字节）。
     */
    public static final String TTS_AUDIO_CHANNEL = "apboa:tts:audio";

    /**
     * 会话级思考模式覆盖 key 前缀（key = 前缀 + threadId）。
     * 值 "1"=强制开 / "0"=强制关 / 无 key=默认开（仅对支持开关的供应商生效，当前 DASH_SCOPE）。
     * ChatModelFactory 构建模型时读取合成，AguiRequestProcessor 检测变化触发 agent 重建。
     */
    public static final String CHAT_THINKING_KEY_PREFIX = "apboa:chat:thinking:";
}
