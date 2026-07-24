package com.hxh.apboa.common.vo;

import com.hxh.apboa.common.config.SerializableEnable;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 成本中心概览 VO。金额单位元（人民币），为「已计价」口径；
 * 未配价模型的用量单独以 unpricedTokens / unpricedModels 呈现。
 *
 * @author huxuehao
 */
@Data
@Builder
public class CostOverviewVO implements SerializableEnable {

    /** 区间总成本（元） */
    private BigDecimal totalCost;

    /** 输入侧成本（元） */
    private BigDecimal inputCost;

    /** 输出侧成本（元） */
    private BigDecimal outputCost;

    /** 输入 token 总量（含未计价） */
    private Long inputTokens;

    /** 输出 token 总量（含未计价） */
    private Long outputTokens;

    /** run（完整回复）次数 */
    private Long runCount;

    /** 覆盖会话数 */
    private Long sessionCount;

    /** 未计价 token 总量（模型未配价的流水） */
    private Long unpricedTokens;

    /** 日趋势：date / inputCost / outputCost / cost（日期骨架连续无缺口） */
    private List<Map<String, Object>> trend;

    /** 按模型分布：modelConfigId / modelLabel / tokens / cost / unpricedTokens / runCount */
    private List<Map<String, Object>> byModel;

    /** 按场景分布：bizType / cost / tokens / runCount */
    private List<Map<String, Object>> byBizType;

    /** 按渠道分布：channel（WEB/CHAT_KEY/SK_API，null=未标记历史）/ cost / tokens / runCount */
    private List<Map<String, Object>> byChannel;

    /** 场景 × 渠道交叉分布：bizType / channel / cost / tokens / runCount */
    private List<Map<String, Object>> byBizChannel;

    /** 消耗主体 TopN：agentId / agentName / bizTypes / sessionCount / runCount / tokens / cost */
    private List<Map<String, Object>> topAgents;

    /** 未配价告警：byModel 中 unpricedTokens>0 的子集 */
    private List<Map<String, Object>> unpricedModels;
}
