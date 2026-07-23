package com.hxh.apboa.node.intent;

import com.fasterxml.jackson.databind.JsonNode;
import com.hxh.apboa.node.base.NodeConfig;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 意图识别节点配置
 *
 * @author huxuehao
 */
@Getter
@Setter
public class Config implements NodeConfig {
    /**
     * 模型配置 ID
     */
    private Long modelConfigId;
    /**
     * 是否启用模型参数覆盖
     */
    private boolean modelParamsOverrideEnabled = false;
    /**
     * 模型参数覆盖配置
     */
    private JsonNode modelParamsOverride;
    /**
     * 系统提示词扩展（用户可追加，补充到内置提示词末尾）
     */
    private String systemPromptExtension;
    /**
     * 意图匹配列表
     */
    private List<IntentMatch> intents;
    /**
     * 默认下游节点 ID（无匹配时使用）
     */
    private String defaultNextNodeId;
}
