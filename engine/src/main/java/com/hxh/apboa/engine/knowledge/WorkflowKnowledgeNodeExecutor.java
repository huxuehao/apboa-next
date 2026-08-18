package com.hxh.apboa.engine.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hxh.apboa.common.entity.KnowledgeBaseConfig;
import com.hxh.apboa.common.enums.KbType;
import com.hxh.apboa.common.util.JsonUtils;
import com.hxh.apboa.common.wrapper.KnowledgeWrapper;
import com.hxh.apboa.knowledge.service.KnowledgeBaseConfigService;
import com.hxh.apboa.node.knowledge.KnowledgeNodeExecutor;
import io.agentscope.core.rag.Knowledge;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.RetrieveConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 工作流知识库检索节点执行器。
 * 在 workflow 节点中执行知识库检索，返回检索到的文档列表。
 *
 * @author huxuehao
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowKnowledgeNodeExecutor implements KnowledgeNodeExecutor {

    private final KnowledgeFactory knowledgeFactory;
    private final KnowledgeBaseConfigService knowledgeBaseConfigService;

    @Override
    public Object retrieve(Long knowledgeBaseConfigId, String query, JsonNode retrievalConfigOverride) {
        if (knowledgeBaseConfigId == null) {
            throw new RuntimeException("知识库配置ID不能为空");
        }
        if (query == null || query.isBlank()) {
            throw new RuntimeException("检索关键词不能为空");
        }

        KnowledgeBaseConfig knowledgeBaseConfig = knowledgeBaseConfigService.getById(knowledgeBaseConfigId);
        if (knowledgeBaseConfig == null) {
            throw new RuntimeException("知识库配置不存在，ID: " + knowledgeBaseConfigId);
        }
        if (!Boolean.TRUE.equals(knowledgeBaseConfig.getEnabled())) {
            throw new RuntimeException("知识库未启用，ID: " + knowledgeBaseConfigId);
        }

        // 将节点级核心检索覆盖项合并到知识库 retrievalConfig，再据此构建知识库实例
        JsonNode mergedRetrievalConfig = mergeRetrievalConfig(
                knowledgeBaseConfig.getRetrievalConfig(), retrievalConfigOverride);
        knowledgeBaseConfig.setRetrievalConfig(mergedRetrievalConfig);

        KnowledgeWrapper knowledgeWrapper = knowledgeFactory.getKnowledgeWrapper(knowledgeBaseConfig);
        if (knowledgeWrapper == null || knowledgeWrapper.getKnowledge() == null) {
            throw new RuntimeException("知识库类型未注册或构建失败，ID: " + knowledgeBaseConfigId);
        }

        Knowledge knowledge = knowledgeWrapper.getKnowledge();
        RetrieveConfig retrieveConfig = buildRetrieveConfig(knowledgeBaseConfig.getKbType(), mergedRetrievalConfig);

        List<Document> documents = knowledge.retrieve(query, retrieveConfig).block();
        log.debug("知识库检索完成, knowledgeBaseConfigId={}, query={}, results={}",
                knowledgeBaseConfigId, query, documents == null ? 0 : documents.size());
        return documents == null ? List.of() : documents;
    }

    /**
     * 将节点级覆盖项浅合并到知识库检索配置之上，覆盖项为空时直接返回基础配置。
     */
    private JsonNode mergeRetrievalConfig(JsonNode base, JsonNode override) {
        if (override == null || override.isNull() || !override.isObject() || override.isEmpty()) {
            return base;
        }
        if (base == null || base.isNull() || !base.isObject()) {
            return override;
        }
        ObjectNode merged = base.deepCopy();
        override.fields().forEachRemaining(entry -> merged.set(entry.getKey(), entry.getValue()));
        return merged;
    }

    /**
     * 根据知识库类型从合并后的检索配置中构建 RetrieveConfig。
     */
    private RetrieveConfig buildRetrieveConfig(KbType kbType, JsonNode mergedRetrievalConfig) {
        int defaultLimit;
        String thresholdKey;
        double defaultThreshold;
        switch (kbType) {
            case DIFY -> {
                defaultLimit = 10;
                thresholdKey = "scoreThreshold";
                defaultThreshold = 0.0;
            }
            case RAGFLOW -> {
                defaultLimit = 1024;
                thresholdKey = "similarityThreshold";
                defaultThreshold = 0.2;
            }
            case LOCAL -> {
                defaultLimit = 5;
                thresholdKey = "scoreThreshold";
                defaultThreshold = 0.5;
            }
            default -> {
                defaultLimit = 5;
                thresholdKey = "scoreThreshold";
                defaultThreshold = 0.0;
            }
        }

        int limit = JsonUtils.getIntValue(mergedRetrievalConfig, "topK", defaultLimit);
        double scoreThreshold = JsonUtils.getDoubleValue(mergedRetrievalConfig, thresholdKey, defaultThreshold);

        return RetrieveConfig.builder()
                .limit(Math.max(limit, 1))
                .scoreThreshold(clampScore(scoreThreshold))
                .build();
    }

    /**
     * 将分数阈值限制在 [0, 1] 区间，避免 RetrieveConfig 构建失败。
     */
    private double clampScore(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
