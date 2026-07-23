package com.hxh.apboa.node.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.hxh.apboa.common.enums.ToolChoiceStrategy;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 智能体节点执行请求。
 *
 * @author huxuehao
 */
@Getter
@Setter
public class AgentNodeRequest {
    private String workflowInstanceId;
    /**
     * 工作流名（成本流水归属快照用；经变量上下文下传，可能为 null）
     */
    private String workflowName;
    private String nodeId;
    private String nodeName;
    private Long modelConfigId;
    private boolean modelParamsOverrideEnabled;
    private JsonNode modelParamsOverride;
    private String systemPrompt;
    private String userPrompt;
    private List<Long> skillPackageIds = new ArrayList<>();
    private List<Long> toolIds = new ArrayList<>();
    private List<McpConfig> mcps = new ArrayList<>();
    private ToolChoiceStrategy toolChoiceStrategy = ToolChoiceStrategy.AUTO;
    private int maxIterations;
    private boolean structuredOutputEnabled;
    private JsonNode structuredOutput;
    private Map<String, Object> inputs;
}
