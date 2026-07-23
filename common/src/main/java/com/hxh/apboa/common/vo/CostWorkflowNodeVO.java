package com.hxh.apboa.common.vo;

import com.hxh.apboa.common.config.SerializableEnable;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 工作流执行账单中的节点执行与成本小计。
 *
 * @author huxuehao
 */
@Data
@Builder
public class CostWorkflowNodeVO implements SerializableEnable {

    private String nodeId;
    private String nodeName;
    private String nodeType;
    private String status;
    private String error;
    private String inputs;
    private String outputs;
    private Long startTime;
    private Long endTime;
    private Long durationMs;
    private Long inputTokens;
    private Long outputTokens;
    private Integer usageRunCount;
    private Integer llmCallCount;
    private Integer unpricedRunCount;
    private BigDecimal cost;
    private List<String> models;
}
