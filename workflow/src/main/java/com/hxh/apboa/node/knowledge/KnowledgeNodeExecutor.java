package com.hxh.apboa.node.knowledge;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 知识库检索节点执行器桥接接口。
 * 由 engine 模块实现，避免 workflow 模块直接依赖 engine 造成循环依赖。
 *
 * @author huxuehao
 */
public interface KnowledgeNodeExecutor {
    /**
     * 从指定知识库中检索相关文档。
     *
     * @param knowledgeBaseConfigId    知识库配置ID
     * @param query                    检索关键词
     * @param retrievalConfigOverride  核心检索配置覆盖项，可为空
     * @return 检索结果（文档列表）
     */
    Object retrieve(Long knowledgeBaseConfigId, String query, JsonNode retrievalConfigOverride);
}
