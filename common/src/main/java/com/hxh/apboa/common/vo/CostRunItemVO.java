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

    public static final String PATH_CURRENT = "CURRENT";
    public static final String PATH_DISCARDED = "DISCARDED";
    public static final String PATH_INTERNAL = "INTERNAL";

    private Long recordId;

    /** assistant 正文消息ID（跳回聊天记录用） */
    private Integer messageId;

    private LocalDateTime createdAt;

    /** 本条用量的实际执行主体；内部调用时用于显示子智能体名称 */
    private Long agentId;

    private String agentName;

    /** CHAT / SUB_AGENT / WORKFLOW / SCHEDULED_JOB */
    private String bizType;

    private Long modelConfigId;

    private String modelLabel;

    /** run 内 LLM 调用轮数（>1 说明经历了工具调用再回答） */
    private Integer iterationCount;

    private Long inputTokens;

    private Long outputTokens;

    private Long durationMs;

    /** 本轮成本（元）；null=未计价 */
    private BigDecimal cost;

    /**
     * 路径状态：CURRENT=当前可见回复，DISCARDED=被重新生成顶替，
     * INTERNAL=无独立聊天消息的内部调用。
     */
    private String pathStatus;

    /**
     * 兼容旧客户端：CURRENT=true、DISCARDED=false、INTERNAL=null。
     * 新代码应优先使用 pathStatus，不能把 null 当作废弃。
     */
    private Boolean onCurrentPath;

    /** 触发本轮的用户问题摘要（沿消息树回溯最近 user 消息，截断） */
    private String userQuestion;

    /** 本轮回复摘要（截断） */
    private String assistantSummary;
}
