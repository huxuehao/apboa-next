package com.hxh.apboa.node.intent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hxh.apboa.common.consts.NodeConst;
import com.hxh.apboa.common.enums.NodeType;
import com.hxh.apboa.common.util.FuncUtils;
import com.hxh.apboa.node.agent.AgentNodeExecutor;
import com.hxh.apboa.node.agent.AgentNodeRequest;
import com.hxh.apboa.node.agent.AgentNodeResult;
import com.hxh.apboa.node.base.EnhancedNode;
import com.hxh.apboa.node.base.NodeOutput;
import com.hxh.apboa.node.base.context.NodeContext;
import com.hxh.apboa.node.base.spring.SpringContextHolder;
import com.hxh.apboa.node.base.verify.VerifyFail;
import com.hxh.apboa.node.base.verify.VerifyResult;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * 意图识别节点，借助大模型识别用户输入的意图并匹配到预设意图选项
 *
 * @author huxuehao
 */
public class IntentRecognitionNode extends EnhancedNode {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String BUILTIN_SYSTEM_PROMPT_PREFIX =
            "You are an intent recognition assistant. Based on the user's input, determine which of the following intents the user is expressing:\n\n";
    private static final String BUILTIN_SYSTEM_PROMPT_SUFFIX =
            "\nYou MUST respond with a JSON object containing only an intent field: {\"intent\": \"intent name\"}";

    private static final String BUILTIN_USER_PROMPT =
            "The user's input is: \"${input}\". Please identify the intent.";

    @Getter
    private final Config config;

    public IntentRecognitionNode(String id, String name, Config config) {
        super(id, name, NodeType.INTENT_RECOGNITION);
        this.config = config;
    }

    @Override
    protected NodeOutput doExecute(Map<String, Object> inputs, NodeOutput output, NodeContext context) {
        try {
            return successNodeOutput(inputs, output, context);
        } catch (Exception e) {
            return executionNodeOutput(e, output);
        }
    }

    private NodeOutput successNodeOutput(Map<String, Object> inputs, NodeOutput output, NodeContext context) {
        // 获取有效意图列表（过滤空条目）
        List<IntentMatch> validIntents = getValidIntents();
        boolean hasIntents = !validIntents.isEmpty();

        // 构建系统提示词
        String systemPrompt = buildSystemPrompt(validIntents);

        // 构建结构化输出 schema（根据意图列表动态生成）
        JsonNode structuredOutput = buildStructuredOutputSchema();

        // 构建请求
        AgentNodeExecutor executor = SpringContextHolder.getBean(AgentNodeExecutor.class);
        AgentNodeRequest request = buildRequest(inputs, context, systemPrompt, structuredOutput, hasIntents);
        AgentNodeResult result = executor.execute(request);

        // 记录执行上下文
        output.addExecutionContext("modelName", result.getModelName());
        output.addExecutionContext("usage", result.getUsage());
        output.addExecutionContext("llmText", result.getText());

        // 提取识别到的意图
        String matchedIntent = extractIntent(result);

        // 匹配 intent → 路由
        String nextNodeId = null;
        if (matchedIntent != null && hasIntents) {
            for (IntentMatch intent : validIntents) {
                if (matchedIntent.equals(intent.getName())) {
                    nextNodeId = intent.getNextNodeId();
                    break;
                }
            }
        }
        if (nextNodeId == null) {
            nextNodeId = config.getDefaultNextNodeId();
        }
        context.setNextNodeId(nextNodeId);

        output.addExecutionContext("matchedIntent", matchedIntent);
        output.addExecutionContext("nextNodeId", nextNodeId);

        output.addOutput("intent", matchedIntent);
        output.addOutput(NodeConst.DEFAULT_OUTPUT_NAME, matchedIntent);
        output.markComplete();
        return output;
    }

    /**
     * 获取有效意图列表（过滤 name 和 nextNodeId 同时为空的残留条目）
     */
    private List<IntentMatch> getValidIntents() {
        if (config.getIntents() == null) {
            return List.of();
        }
        return config.getIntents().stream()
                .filter(i -> !FuncUtils.isEmpty(i.getName()) && !FuncUtils.isEmpty(i.getNextNodeId()))
                .toList();
    }

    /**
     * 构建系统提示词：内置前缀 + 意图列表 + 内置后缀 + 用户扩展
     */
    private String buildSystemPrompt() {
        return buildSystemPrompt(getValidIntents());
    }

    private String buildSystemPrompt(List<IntentMatch> validIntents) {
        StringBuilder sb = new StringBuilder(BUILTIN_SYSTEM_PROMPT_PREFIX);

        int index = 1;
        for (IntentMatch intent : validIntents) {
            sb.append(index++).append(". Intent Name: ").append(intent.getName()).append("\n");
            sb.append("   Intent Description: ").append(!FuncUtils.isEmpty(intent.getDescription()) ? intent.getDescription() : "None").append("\n\n");
        }

        sb.append(BUILTIN_SYSTEM_PROMPT_SUFFIX);

        if (!FuncUtils.isEmpty(config.getSystemPromptExtension())) {
            sb.append("\n\n").append(config.getSystemPromptExtension());
        }

        return sb.toString();
    }

    /**
     * 根据意图列表构建结构化输出 schema
     */
    private JsonNode buildStructuredOutputSchema() {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("intent", "intent name");
        return root;
    }

    /**
     * 从 LLM 结果中提取 intent 字段
     */
    private String extractIntent(AgentNodeResult result) {
        // 优先从结构化输出中提取
        if (result.getStructured() != null) {
            Object intent = result.getStructured().get("intent");
            if (intent != null) {
                return intent.toString();
            }
        }
        // 兜底：尝试从文本中提取
        if (!FuncUtils.isEmpty(result.getText())) {
            try {
                JsonNode parsed = MAPPER.readTree(result.getText());
                if (parsed.has("intent")) {
                    return parsed.get("intent").asText();
                }
            } catch (Exception ignored) {
                // 无法解析为 JSON，使用原始文本
            }
            return result.getText().trim();
        }
        return null;
    }

    private AgentNodeRequest buildRequest(Map<String, Object> inputs, NodeContext context,
                                          String systemPrompt, JsonNode structuredOutput,
                                          boolean structuredOutputEnabled) {
        Object input = getFirstValue(inputs);
        AgentNodeRequest request = new AgentNodeRequest();
        request.setWorkflowInstanceId(context.getWorkflowInstanceId());
        request.setNodeId(getId());
        request.setNodeName(getName());
        request.setModelConfigId(config.getModelConfigId());
        request.setModelParamsOverrideEnabled(config.isModelParamsOverrideEnabled());
        request.setModelParamsOverride(config.getModelParamsOverride());
        request.setSystemPrompt(systemPrompt);
        request.setUserPrompt(BUILTIN_USER_PROMPT.replace("${input}", input == null ? "" : input.toString()));
        request.setStructuredOutputEnabled(structuredOutputEnabled);
        request.setStructuredOutput(structuredOutput);
        request.setSkillPackageIds(List.of());
        request.setToolIds(List.of());
        request.setMcps(List.of());
        request.setMaxIterations(1);
        request.setInputs(inputs);
        return request;
    }

    public static Object getFirstValue(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        // 获取第一个 entry 的值
        return map.values().iterator().next();
    }

    private NodeOutput executionNodeOutput(Exception e, NodeOutput output) {
        output.markFailed(getName() + "执行失败: " + e.getMessage());
        return output;
    }

    @Override
    public VerifyResult verifyConfig(Map<String, Object> inputs) {
        if (config.getModelConfigId() == null) {
            return VerifyResult.invalid(new VerifyFail("modelConfigId", "模型配置ID不能为空"));
        }
        if (config.getIntents() == null || config.getIntents().isEmpty()) {
            return VerifyResult.invalid(new VerifyFail("intents", "意图列表不能为空"));
        }
        boolean hasValidIntent = false;
        for (IntentMatch intent : config.getIntents()) {
            // 跳过同时缺少 name 和 nextNodeId 的空条目（可能由前端残留在 config 中）
            if (FuncUtils.isEmpty(intent.getName()) && FuncUtils.isEmpty(intent.getNextNodeId())) {
                continue;
            }
            if (FuncUtils.isEmpty(intent.getName())) {
                return VerifyResult.invalid(new VerifyFail("intents.name", "意图名称不能为空"));
            }
            if (FuncUtils.isEmpty(intent.getNextNodeId())) {
                return VerifyResult.invalid(new VerifyFail("intents.nextNodeId", "意图下游节点不能为空"));
            }
            hasValidIntent = true;
        }
        if (!hasValidIntent) {
            return VerifyResult.invalid(new VerifyFail("intents", "意图列表不能为空"));
        }
        return VerifyResult.valid();
    }

    @Override
    public String getNextNodeId(NodeContext context) {
        return context.getNextNodeId();
    }
}
