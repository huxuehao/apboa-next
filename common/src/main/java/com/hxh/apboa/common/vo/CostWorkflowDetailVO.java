package com.hxh.apboa.common.vo;

import com.hxh.apboa.common.config.SerializableEnable;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 工作流执行账单详情：运行快照、节点轨迹与模型用量使用同一 workflow_run.id 对齐。
 *
 * @author huxuehao
 */
@Data
@Builder
public class CostWorkflowDetailVO implements SerializableEnable {

    private String runId;
    private String workflowId;
    private String workflowName;
    private String version;
    private String status;
    private Object inputs;
    private Object outputs;
    private String error;
    private Long startTime;
    private Long endTime;
    private Long durationMs;
    private Long userId;
    private String userName;
    private String channel;
    private Long sourceSessionId;
    private boolean legacy;

    private BigDecimal totalCost;
    private Long inputTokens;
    private Long outputTokens;
    private Integer usageRunCount;
    private Integer llmCallCount;
    private Integer unpricedRunCount;
    private List<Map<String, Object>> byModel;
    private List<CostWorkflowNodeVO> nodes;
    private List<CostWorkflowUsageVO> usages;
}
