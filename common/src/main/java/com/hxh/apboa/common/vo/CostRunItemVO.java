package com.hxh.apboa.common.vo;

import com.hxh.apboa.common.config.SerializableEnable;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 会话成本明细的单轮（run）条目
 *
 * @author huxuehao
 */
@Data
@Builder
public class CostRunItemVO implements SerializableEnable {

    private Long recordId;

    /** assistant 正文消息ID（跳回聊天记录用） */
    private Integer messageId;

    private LocalDateTime createdAt;

    private Long modelConfigId;

    private String modelLabel;

    /** run 内 LLM 调用轮数（>1 说明经历了工具调用再回答） */
    private Integer iterationCount;

    private Long inputTokens;

    private Long outputTokens;

    private Long durationMs;

    /** 本轮成本（元）；null=未计价 */
    private BigDecimal cost;

    /** 是否在当前对话链上；false=被重新生成顶替的废弃分支 */
    private Boolean onCurrentPath;

    /** 触发本轮的用户问题摘要（沿消息树回溯最近 user 消息，截断） */
    private String userQuestion;

    /** 本轮回复摘要（截断） */
    private String assistantSummary;
}
