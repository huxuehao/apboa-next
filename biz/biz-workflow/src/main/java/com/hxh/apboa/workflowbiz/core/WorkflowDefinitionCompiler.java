package com.hxh.apboa.workflowbiz.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hxh.apboa.node.base.EnhancedNode;
import com.hxh.apboa.node.base.Node;
import com.hxh.apboa.common.enums.NodeType;
import com.hxh.apboa.node.base.inputout.InputConfig;
import com.hxh.apboa.node.base.inputout.OutputConfig;
import com.hxh.apboa.workflow.core.Edge;
import com.hxh.apboa.workflow.run.RunWorkflow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.lang.reflect.Constructor;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class WorkflowDefinitionCompiler {
    private final ObjectMapper objectMapper;

    private static final Map<NodeType, NodeBinding> BINDINGS = new EnumMap<>(NodeType.class);

    static {
        bind(NodeType.START, com.hxh.apboa.node.start.StartNode.class, com.hxh.apboa.node.start.Config.class);
        bind(NodeType.END, com.hxh.apboa.node.end.EndNode.class, com.hxh.apboa.node.end.Config.class);
        bind(NodeType.NO_OPERATION, com.hxh.apboa.node.nooperation.NoOperationNode.class, com.hxh.apboa.node.nooperation.Config.class);
        bind(NodeType.CACHE_FETCH, com.hxh.apboa.node.cache.fetch.CacheFetchNode.class, com.hxh.apboa.node.cache.fetch.Config.class);
        bind(NodeType.CACHE_REFRESH, com.hxh.apboa.node.cache.refresh.CacheRefreshNode.class, com.hxh.apboa.node.cache.refresh.Config.class);
        bind(NodeType.CACHE_REMOVE, com.hxh.apboa.node.cache.remove.CacheRemoveNode.class, com.hxh.apboa.node.cache.remove.Config.class);
        bind(NodeType.CACHE_SET, com.hxh.apboa.node.cache.set.CacheSetNode.class, com.hxh.apboa.node.cache.set.Config.class);
        bind(NodeType.AGENT, com.hxh.apboa.node.agent.AgentNode.class, com.hxh.apboa.node.agent.Config.class);
        bind(NodeType.CODE, com.hxh.apboa.node.code.CodeNode.class, com.hxh.apboa.node.code.Config.class);
        bind(NodeType.DB_DELETE, com.hxh.apboa.node.db.delete.DbDeleteNode.class, com.hxh.apboa.node.db.delete.Config.class);
        bind(NodeType.DB_INSERT, com.hxh.apboa.node.db.insert.DbInsertNode.class, com.hxh.apboa.node.db.insert.Config.class);
        bind(NodeType.DB_SELECT, com.hxh.apboa.node.db.select.DbSelectNode.class, com.hxh.apboa.node.db.select.Config.class);
        bind(NodeType.DB_UPDATE, com.hxh.apboa.node.db.update.DbUpdateNode.class, com.hxh.apboa.node.db.update.Config.class);
        bind(NodeType.HTTP_EXTERNAL, com.hxh.apboa.node.http.HttpExternalNode.class, com.hxh.apboa.node.http.Config.class);
        bind(NodeType.IF_ELSE, com.hxh.apboa.node.condition.ConditionNode.class, com.hxh.apboa.node.condition.Config.class);
        bind(NodeType.ITERATE, com.hxh.apboa.node.iterate.IterateNode.class, com.hxh.apboa.node.iterate.Config.class);
        bind(NodeType.LIST_FILTER, com.hxh.apboa.node.list.filter.ListFilterNode.class, com.hxh.apboa.node.list.filter.Config.class);
        bind(NodeType.LIST_SORT, com.hxh.apboa.node.list.sort.ListSortNode.class, com.hxh.apboa.node.list.sort.Config.class);
        bind(NodeType.LOOP, com.hxh.apboa.node.loop.LoopNode.class, com.hxh.apboa.node.loop.Config.class);
        bind(NodeType.MATCH_RESULT, com.hxh.apboa.node.match.result.MatchResultNode.class, com.hxh.apboa.node.match.result.Config.class);
        bind(NodeType.MQ_PUSH, com.hxh.apboa.node.mq.push.MqPushNode.class, com.hxh.apboa.node.mq.push.Config.class);
        bind(NodeType.NON_EMPTY_SELECT, com.hxh.apboa.node.none.empty.select.NonEmptySelectNode.class, com.hxh.apboa.node.none.empty.select.Config.class);
        bind(NodeType.SERIALIZE, com.hxh.apboa.node.serialize.SerializeNode.class, com.hxh.apboa.node.serialize.Config.class);
        bind(NodeType.STRING_SPLIT, com.hxh.apboa.node.string.split.StringSplitNode.class, com.hxh.apboa.node.string.split.Config.class);
        bind(NodeType.STRING_TEMPLATE, com.hxh.apboa.node.string.template.StringTemplateNode.class, com.hxh.apboa.node.string.template.Config.class);
        bind(NodeType.UNSERIALIZE, com.hxh.apboa.node.unserialize.UnserializeNode.class, com.hxh.apboa.node.unserialize.Config.class);
        bind(NodeType.VARIABLE_AGG, com.hxh.apboa.node.variable.agg.VariableAggNode.class, com.hxh.apboa.node.variable.agg.Config.class);
        bind(NodeType.TOOL_EXECUTE, com.hxh.apboa.node.toolexecute.ToolExecuteNode.class, com.hxh.apboa.node.toolexecute.Config.class);
        bind(NodeType.MCP_CALL, com.hxh.apboa.node.mcp.McpNode.class, com.hxh.apboa.node.mcp.Config.class);
        bind(NodeType.EMAIL_SEND, com.hxh.apboa.node.channel.email.EmailSendNode.class, com.hxh.apboa.node.channel.email.Config.class);
        bind(NodeType.WECOM_SEND, com.hxh.apboa.node.channel.wecom.WecomSendNode.class, com.hxh.apboa.node.channel.wecom.Config.class);
        bind(NodeType.DINGTALK_SEND, com.hxh.apboa.node.channel.dingtalk.DingTalkSendNode.class, com.hxh.apboa.node.channel.dingtalk.Config.class);
        bind(NodeType.FEISHU_SEND, com.hxh.apboa.node.channel.feishu.FeishuSendNode.class, com.hxh.apboa.node.channel.feishu.Config.class);
        bind(NodeType.INTENT_RECOGNITION, com.hxh.apboa.node.intent.IntentRecognitionNode.class, com.hxh.apboa.node.intent.Config.class);
    }

    public static Set<NodeType> supportedTypes() {
        return BINDINGS.keySet();
    }

    public RunWorkflow compile(String workflowId, Object definition) {
        JsonNode root = objectMapper.valueToTree(definition);
        RunWorkflow workflow = new RunWorkflow(workflowId);

        JsonNode nodes = root.path("nodes");
        if (!nodes.isArray()) {
            throw new IllegalArgumentException("workflow.nodes must be an array");
        }
        for (JsonNode nodeJson : nodes) {
            workflow.addNode(compileNode(nodeJson));
        }

        JsonNode edges = root.path("edges");
        if (edges.isArray()) {
            for (JsonNode edgeJson : edges) {
                String id = text(edgeJson, "id", "edge-" + edgeJson.path("source").asText() + "-" + edgeJson.path("target").asText());
                workflow.addEdge(new Edge(id, text(edgeJson, "source", null), text(edgeJson, "target", null)));
            }
        }
        return workflow;
    }

    private Node compileNode(JsonNode nodeJson) {
        return compileSubNode(nodeJson);
    }

    /**
     * 公开的子节点编译方法，供 LoopSubWorkflowCompiler 等调用。
     */
    public Node compileSubNode(JsonNode nodeJson) {
        String id = text(nodeJson, "id", null);
        String name = text(nodeJson, "name", text(nodeJson, "label", id));
        NodeType type = NodeType.valueOf(text(nodeJson, "type", null));
        NodeBinding binding = BINDINGS.get(type);
        if (binding == null) {
            throw new IllegalArgumentException("Unsupported workflow node type: " + type);
        }

        Object config = objectMapper.convertValue(nodeJson.path("config"), binding.configClass());
        EnhancedNode node = instantiate(binding, id, name, config);

        JsonNode inputs = nodeJson.path("inputConfigs");
        if (inputs.isArray()) {
            for (JsonNode input : inputs) {
                node.addInputConfig(compileInputConfig(input));
            }
        }

        JsonNode outputs = nodeJson.path("outputConfigs");
        if (outputs.isArray()) {
            for (JsonNode output : outputs) {
                OutputConfig outputConfig = objectMapper.convertValue(output, OutputConfig.class);
                if (outputConfig.getFromNodeId() == null) {
                    outputConfig.setFromNodeId(id);
                }
                node.addOutputConfig(outputConfig);
            }
        }

        return node;
    }

    private InputConfig compileInputConfig(JsonNode inputJson) {
        InputConfig config = new InputConfig();
        config.setName(text(inputJson, "name", "input"));

        String typeText = text(inputJson, "type", null);
        if (typeText != null && !typeText.isBlank()) {
            config.setType(OutputConfig.VariableType.valueOf(typeText));
        }

        String classifyText = text(inputJson, "classify", text(inputJson, "sourceType", null));
        if (classifyText == null || classifyText.isBlank()) {
            classifyText = InputConfig.InputClassify.NODE_OUTPUT.name();
        }
        config.setClassify(InputConfig.InputClassify.valueOf(classifyText));

        JsonNode valueNode = inputJson.get("value");
        if (valueNode != null && !valueNode.isNull()) {
            config.setValue(objectMapper.convertValue(valueNode, Object.class));
        }
        config.setVariableName(text(inputJson, "variableName", null));
        config.setSourceNodeId(text(inputJson, "sourceNodeId", text(inputJson, "nodeId", null)));
        config.setSourceOutputName(text(inputJson, "sourceOutputName", text(inputJson, "outputName", "output")));
        config.setExpression(text(inputJson, "expression", null));
        return config;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private EnhancedNode instantiate(NodeBinding binding, String id, String name, Object config) {
        try {
            Constructor constructor = binding.nodeClass().getConstructor(String.class, String.class, binding.configClass());
            return (EnhancedNode) constructor.newInstance(id, name, config);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to create workflow node " + id + ": " + e.getMessage(), e);
        }
    }

    private static String text(JsonNode node, String field, String defaultValue) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? defaultValue : value.asText();
    }

    private static void bind(NodeType type, Class<? extends EnhancedNode> nodeClass, Class<?> configClass) {
        BINDINGS.put(type, new NodeBinding(nodeClass, configClass));
    }

    private record NodeBinding(Class<? extends EnhancedNode> nodeClass, Class<?> configClass) {
    }
}
