package com.hxh.apboa.common.vo;

import com.hxh.apboa.common.config.SerializableEnable;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 会话成本明细 VO：按「实际发生」口径展示会话内全部 run，区分当前回复、
 * 被重新生成顶替的废弃分支，以及无独立消息载体的子智能体等内部调用。
 *
 * @author huxuehao
 */
@Data
@Builder
public class CostSessionDetailVO implements SerializableEnable {

    private Long sessionId;

    private String title;

    private Long agentId;

    private String agentName;

    private Long userId;

    private String userName;

    /** 会话总成本（已计价口径，元） */
    private BigDecimal totalCost;

    private Long inputTokens;

    private Long outputTokens;

    /** 智能体运行数（成本流水条数，含当前回复、废弃回复和内部调用） */
    private Integer runCount;

    /** 有 assistant 消息载体的可见回复数（含后来被重新生成顶替的回复） */
    private Integer visibleReplyCount;

    /** 无独立 assistant 消息载体的内部调用数 */
    private Integer internalRunCount;

    /** 所有 run 内实际 LLM 调用次数之和 */
    private Integer llmCallCount;

    /** 废弃分支轮次 */
    private Integer discardedRunCount;

    /** 废弃分支成本（元） */
    private BigDecimal discardedCost;

    /** 未计价轮次（模型未配价） */
    private Integer unpricedRunCount;

    /** 按模型小计 chips：modelLabel / runCount / cost */
    private List<Map<String, Object>> byModel;

    /** 逐轮明细，按发生时间升序 */
    private List<CostRunItemVO> runs;
}
