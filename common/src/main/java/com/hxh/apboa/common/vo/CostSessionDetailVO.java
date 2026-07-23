package com.hxh.apboa.common.vo;

import com.hxh.apboa.common.config.SerializableEnable;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 会话成本明细 VO：按「实际发生」口径展示会话内全部 run（含被重新生成
 * 顶替的废弃分支——它们真实调用过模型、真实产生费用），废弃行以
 * onCurrentPath=false 标记。
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

    /** 总轮次（run 数） */
    private Integer runCount;

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
