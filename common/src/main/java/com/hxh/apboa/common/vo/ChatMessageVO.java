package com.hxh.apboa.common.vo;

import com.hxh.apboa.common.config.SerializableEnable;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 聊天消息 VO
 *
 * @author huxuehao
 */
@Getter
@Setter
public class ChatMessageVO implements SerializableEnable {
    private Integer id;
    private Long sessionId;
    private String role;
    private String content;
    private Integer parentId;
    private String path;
    private Integer depth;
    /** 消息元数据 JSON（durationMs/iterationCount/inputTokens/outputTokens/totalTokens），无则 NULL */
    private String meta;
    private LocalDateTime createdAt;
}
