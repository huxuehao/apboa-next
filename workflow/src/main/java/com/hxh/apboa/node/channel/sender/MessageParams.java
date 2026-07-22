package com.hxh.apboa.node.channel.sender;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 消息发送参数，封装所有渠道通用的发送信息
 *
 * @author huxuehao
 */
@Setter
@Getter
public class MessageParams {
    /**
     * 消息标题
     */
    private String subject;
    /**
     * 消息内容（已渲染的最终内容）
     */
    private String content;
    /**
     * 扩展参数（渠道特有的如 toRecipients/atMobiles 等）
     */
    private Map<String, Object> extras;

    public MessageParams() {}

    public MessageParams(String subject, String content, Map<String, Object> extras) {
        this.subject = subject;
        this.content = content;
        this.extras = extras;
    }

    public String getExtraString(String key) {
        if (extras == null) return null;
        Object val = extras.get(key);
        return val != null ? val.toString() : null;
    }
}
