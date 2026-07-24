package com.hxh.apboa.engine.log.telemetry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话渠道登记：threadId -> 最近一次 run 的认证渠道（WEB / CHAT_KEY / SK_API）。
 * AguiRequestProcessor 在 run/resume 建立时从认证层（request attribute）取值登记，
 * ChatLogHook（主链落 meta）与子智能体流水按 threadId 读取——渠道是 run 级属性
 * （同一会话可被主人网页聊、也可被外嵌用户聊），不能挂 agent 构建期。
 *
 * <p>与 ChatLogHook.THREAD_TENANT_CACHE 同策略的进程内缓存（每会话一条，不主动过期）；
 * 超上限整体清空兜底，丢失只影响渠道标记回落 NULL（统计显示「未标记」），不影响记账。
 *
 * @author huxuehao
 */
public final class ChatChannelHolder {

    private static final int MAX_ENTRIES = 10_000;

    private static final Map<String, String> CHANNEL_MAP = new ConcurrentHashMap<>();

    private ChatChannelHolder() {
    }

    public static void put(String threadId, String channel) {
        if (threadId == null || channel == null) {
            return;
        }
        if (CHANNEL_MAP.size() >= MAX_ENTRIES) {
            CHANNEL_MAP.clear();
        }
        CHANNEL_MAP.put(threadId, channel);
    }

    public static String get(String threadId) {
        return threadId == null ? null : CHANNEL_MAP.get(threadId);
    }
}
