package com.hxh.apboa.common.vo;

import com.hxh.apboa.common.config.SerializableEnable;
import com.hxh.apboa.common.entity.AgentA2A;
import com.hxh.apboa.common.entity.JobInfo;
import com.hxh.apboa.common.enums.AgentType;
import com.hxh.apboa.common.enums.ToolChoiceStrategy;
import com.fasterxml.jackson.databind.JsonNode;
import io.agentscope.core.model.StructuredOutputReminder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 智能体定义VO
 *
 * @author huxuehao
 */
@Data
@EqualsAndHashCode
public class AgentDefinitionVO implements SerializableEnable {
    private Long id;
    private AgentType agentType;
    private String name;
    private String agentCode;
    private String description;
    private JsonNode commonQuestions;
    private Boolean commonQuestionsPinned;
    private Long modelConfigId;
    private Long asrModelConfigId;
    private Long ttsModelConfigId;
    private JsonNode modelParamsOverride;
    private List<Long> skill;
    private List<Long> workflow;
    private List<Long> tool;
    private List<Long> mcp;
    private List<AgentMcpBindingVO> mcpBindings;
    private List<Long> hook;
    private List<Long> subAgent;
    private List<Long> knowledgeBase;
    private ToolChoiceStrategy toolChoiceStrategy;
    private String specificToolName;
    private Long systemPromptTemplateId;
    private Boolean followTemplate;
    private String systemPrompt;
    private Long sensitiveWordConfigId;
    private Boolean sensitiveFilterEnabled;
    private Integer maxIterations;
    private Boolean enablePlanning;
    private Integer maxSubtasks;
    private Boolean requirePlanConfirmation;
    private Boolean showToolProcess;

    /**
     * 当前模型是否支持会话级思考模式开关（派生字段，detail 时按 model_config.thinking 算出：
     * DASH_SCOPE 靠内置 enable_thinking、OPEN_AI 靠模型配的 thinkingParams；前端据此决定是否展示思考按钮）
     */
    private Boolean thinkingSwitchSupported;
    private Boolean enableMemory;
    private Boolean enableMemoryCompression;
    private JsonNode memoryCompressionConfig;
    private Boolean structuredOutputEnabled;
    private StructuredOutputReminder structuredOutputReminder;
    private JsonNode structuredOutputSchema;
    private String version;
    private String tag;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;
    private List<Object> used;
    private AgentA2A agentA2A;
    private JobInfo jobInfo;
    private Long studioConfigId;
    private Long codeExecutionConfigId;
    private Long longTermMemoryConfigId;
}
