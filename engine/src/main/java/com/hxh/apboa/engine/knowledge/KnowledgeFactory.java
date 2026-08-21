package com.hxh.apboa.engine.knowledge;

import com.hxh.apboa.common.entity.AgentDefinition;
import com.hxh.apboa.common.entity.KnowledgeBaseConfig;
import com.hxh.apboa.common.enums.KbType;
import com.hxh.apboa.common.wrapper.KnowledgeWrapper;
import com.hxh.apboa.knowledge.service.KnowledgeBaseConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 描述：知识库工程
 *
 * @author huxuehao
 **/
@Component
@RequiredArgsConstructor
public class KnowledgeFactory {
    private static final Map<KbType, IKnowledge> KNOWLEDGE_MAP = new ConcurrentHashMap<>();

    private final KnowledgeBaseConfigService knowledgeBaseConfigService;

    public List<KnowledgeWrapper> getKnowledge(AgentDefinition definition) {
        List<KnowledgeBaseConfig> knowledgeBaseConfigs = knowledgeBaseConfigService.getByAgentId(definition.getId());
        return getKnowledgeWrapper(knowledgeBaseConfigs);
    }

    /**
     * 根据知识库配置构建知识库实体（Knowledge）。
     *
     * @param knowledgeBaseConfigs 知识库配置
     * @return 知识库包装对象，配置为空/未启用/类型未注册时返回 null
     */
    public List<KnowledgeWrapper> getKnowledgeWrapper(List<KnowledgeBaseConfig> knowledgeBaseConfigs) {
        if (knowledgeBaseConfigs == null) {
            return List.of();
        }

        List<KnowledgeWrapper> objects = new ArrayList<>();
        for (KnowledgeBaseConfig knowledgeBaseConfig : knowledgeBaseConfigs) {
            if (!knowledgeBaseConfig.getEnabled()) {
                continue;
            }

            IKnowledge iKnowledge = KNOWLEDGE_MAP.get(knowledgeBaseConfig.getKbType());
            if (iKnowledge == null) {
                continue;
            }

            objects.add(KnowledgeWrapper
                    .builder()
                    .ragMode(knowledgeBaseConfig.getRagMode())
                    .knowledge(iKnowledge.build(knowledgeBaseConfig))
                    .retrievalConfig(knowledgeBaseConfig.getRetrievalConfig())
                    .build());
        }
        return objects;
    }

    public static IKnowledge getKnowledge(KbType kbType) {
        return KNOWLEDGE_MAP.get(kbType);
    }

    public static void register(IKnowledge knowledge) {
        KNOWLEDGE_MAP.put(knowledge.type(), knowledge);
    }

    public static void unregister(KbType type) {
        KNOWLEDGE_MAP.remove(type);
    }

    public static void clear() {
        KNOWLEDGE_MAP.clear();
    }
}
