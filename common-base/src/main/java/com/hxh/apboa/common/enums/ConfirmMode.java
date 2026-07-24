package com.hxh.apboa.common.enums;

/**
 * 会话级 HITL 授权模式（三态）。
 *
 * <p>Redis 存储沿用 {@code RedisChannelTopic.CHAT_AUTO_APPROVE_KEY_PREFIX + threadId}：
 * 值 "1"=一键授权（历史兼容）、"2"=拒绝授权、无 key/其他=逐步确认。
 * 主/子 agent 的需确认工具按模式行为：AUTO_APPROVE 自动放行（IConfirmationHook 不暂停）；
 * MANUAL 暂停冒泡人工决策；AUTO_REJECT 暂停后由暂停处理层自动全拒续跑
 * （语义与人工点「禁止」一致，喂 REJECT_RESULT_TEXT）。
 *
 * @author huxuehao
 */
public enum ConfirmMode {

    /** 一键授权：需确认工具自动放行 */
    AUTO_APPROVE("1"),

    /** 逐步确认：暂停等待人工逐工具决策（默认，Redis 无记录） */
    MANUAL(null),

    /** 拒绝授权：需确认工具自动拒绝（喂拒绝结果，模型如实告知不重试） */
    AUTO_REJECT("2");

    /**
     * 工具被拒时喂回模型的错误结果文案（主 resume / 子智能体 / 定时任务三链路单一出处；
     * OpenAI/Ollama 协议无结构化 is_error，拒绝语义只能靠文本表达）
     */
    public static final String REJECT_RESULT_TEXT =
            "Error: 用户拒绝授权调用该工具，本轮对话中该工具不可用。请勿重试该工具，"
                    + "更不得自行编造、虚构或凭常识臆测该工具本应返回的结果数据；"
                    + "必须如实告知用户：因未获授权调用该工具，无法获取相关信息。";

    private final String redisValue;

    ConfirmMode(String redisValue) {
        this.redisValue = redisValue;
    }

    /** Redis 存储值；MANUAL 为 null（删 key 语义） */
    public String getRedisValue() {
        return redisValue;
    }

    /** 从 Redis 值解析，null/未知一律回退 MANUAL（宁可多确认，不可静默放行/误拒） */
    public static ConfirmMode fromRedisValue(String value) {
        if (AUTO_APPROVE.redisValue.equals(value)) {
            return AUTO_APPROVE;
        }
        if (AUTO_REJECT.redisValue.equals(value)) {
            return AUTO_REJECT;
        }
        return MANUAL;
    }

    /** 从名称解析（接口入参），未知回退 MANUAL */
    public static ConfirmMode fromName(String name) {
        for (ConfirmMode mode : values()) {
            if (mode.name().equalsIgnoreCase(name)) {
                return mode;
            }
        }
        return MANUAL;
    }
}
