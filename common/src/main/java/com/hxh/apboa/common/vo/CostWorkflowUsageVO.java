package com.hxh.apboa.common.vo;

import com.hxh.apboa.common.config.SerializableEnable;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工作流执行账单中的单条模型用量流水。
 *
 * @author huxuehao
 */
@Data
@Builder
public class CostWorkflowUsageVO implements SerializableEnable {

    private Long recordId;
    private LocalDateTime createdAt;
    private String nodeId;
    private String nodeName;
    private Long modelConfigId;
    private String modelLabel;
    private Integer iterationCount;
    private Long inputTokens;
    private Long outputTokens;
    private Long durationMs;
    private BigDecimal cost;
}
