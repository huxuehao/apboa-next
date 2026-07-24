package com.hxh.apboa.common.vo;

import com.hxh.apboa.common.config.SerializableEnable;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 成本中心-模型配价行：LLM 模型的价格与近 30 天用量合并视图（批量改价页用）
 *
 * @author huxuehao
 */
@Data
@Builder
public class CostModelPricingVO implements SerializableEnable {

    private Long modelConfigId;

    /** 模型名称 */
    private String name;

    /** 模型编号（一键填充按它前缀匹配官网价） */
    private String modelId;

    /** 供应商名称 */
    private String providerName;

    /** 供应商类型（OLLAMA 前端直接填 0） */
    private String providerType;

    /** 模型是否启用 */
    private Boolean enabled;

    /** 输入单价（元/百万token；null=未配价） */
    private BigDecimal inputPrice;

    /** 输出单价（元/百万token；null=未配价） */
    private BigDecimal outputPrice;

    /** 近 30 天 token 总量（输入+输出，含未计价） */
    private Long tokens30d;

    /** 近 30 天已计成本（元） */
    private BigDecimal cost30d;

    /** 近 30 天未计价 token */
    private Long unpricedTokens30d;

    /** 近 30 天调用次数 */
    private Long runCount30d;
}
