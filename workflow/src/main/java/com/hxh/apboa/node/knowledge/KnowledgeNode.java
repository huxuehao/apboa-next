package com.hxh.apboa.node.knowledge;

import com.hxh.apboa.common.consts.NodeConst;
import com.hxh.apboa.node.base.EnhancedNode;
import com.hxh.apboa.node.base.NodeOutput;
import com.hxh.apboa.common.enums.NodeType;
import com.hxh.apboa.node.base.context.NodeContext;
import com.hxh.apboa.node.base.spring.SpringContextHolder;
import com.hxh.apboa.node.base.verify.VerifyFail;
import com.hxh.apboa.node.base.verify.VerifyResult;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * 知识库检索节点。
 * 调用指定知识库的检索能力，将输入关键词传递给知识库并返回检索到的文档列表。
 *
 * @author huxuehao
 */
public class KnowledgeNode extends EnhancedNode {
    @Getter
    private final Config config;

    public KnowledgeNode(String id, String name, Config config) {
        super(id, name, NodeType.KNOWLEDGE_RETRIEVE);
        this.config = config;
    }

    @Override
    protected NodeOutput doExecute(Map<String, Object> inputs, NodeOutput output, NodeContext context) {
        try {
            return successNodeOutput(inputs, output);
        } catch (Exception e) {
            return executionNodeOutput(e, output);
        }
    }

    /**
     * 创建成功输出。
     *
     * @param inputs 节点输入
     * @param output 节点输出
     * @return 节点输出
     */
    private NodeOutput successNodeOutput(Map<String, Object> inputs, NodeOutput output) {
        String query = resolveQuery(inputs);
        KnowledgeNodeExecutor executor = SpringContextHolder.getBean(KnowledgeNodeExecutor.class);
        Object result = executor.retrieve(config.getKnowledgeBaseConfigId(), query, config.getRetrievalConfig());

        int resultCount = result instanceof List ? ((List<?>) result).size() : (result == null ? 0 : 1);
        output.addExecutionContext("knowledgeBaseConfigId", config.getKnowledgeBaseConfigId());
        output.addExecutionContext("query", query);
        output.addExecutionContext("resultCount", resultCount);
        output.addOutput(NodeConst.DEFAULT_OUTPUT_NAME, result);
        output.markComplete();
        return output;
    }

    /**
     * 从输入中解析检索关键词，优先取 query，其次取 input。
     */
    private String resolveQuery(Map<String, Object> inputs) {
        Object query = inputs.get("query");
        if (query == null) {
            query = inputs.get(NodeConst.DEFAULT_INPUT_NAME);
        }
        return query == null ? null : query.toString();
    }

    /**
     * 创建异常输出。
     */
    private NodeOutput executionNodeOutput(Exception e, NodeOutput output) {
        output.markFailed(getName() + "执行失败: " + e.getMessage());
        return output;
    }

    @Override
    public VerifyResult verifyConfig(Map<String, Object> inputs) {
        if (config.getKnowledgeBaseConfigId() == null) {
            return VerifyResult.invalid(new VerifyFail("knowledgeBaseConfigId", "知识库配置ID不能为空"));
        }
        return VerifyResult.valid();
    }
}
