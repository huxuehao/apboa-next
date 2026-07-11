package com.hxh.apboa.node.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.hxh.apboa.node.base.NodeConfig;
import com.hxh.apboa.node.base.template.FormatterType;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 智能体节点配置。
 *
 * @author huxuehao
 */
@Getter
@Setter
public class Config implements NodeConfig {
    /**
     * 模型配置ID。
     */
    private Long modelConfigId;
    /**
     * 是否启用模型参数覆盖。
     */
    private boolean modelParamsOverrideEnabled = false;
    /**
     * 模型参数覆盖配置。
     */
    private JsonNode modelParamsOverride;
    /**
     * 提示词模板渲染方式。
     */
    private FormatterType formatterType = FormatterType.STRING;
    /**
     * 系统提示词。
     */
    private String systemPrompt;
    /**
     * 用户提示词。
     */
    private String userPrompt;
    /**
     * 技能包ID列表。
     */
    private List<Long> skillPackageIds = new ArrayList<>();
    /**
     * 工具ID列表。
     */
    private List<Long> toolIds = new ArrayList<>();
    /**
     * MCP配置列表。
     */
    private List<McpConfig> mcps = new ArrayList<>();
    /**
     * ReAct最大迭代次数。
     */
    private int maxIterations = 5;
    /**
     * 是否启用结构化输出。
     */
    private boolean structuredOutputEnabled = false;
    /**
     * 结构化输出JSON Schema。
     */
    private JsonNode structuredOutput;
}
