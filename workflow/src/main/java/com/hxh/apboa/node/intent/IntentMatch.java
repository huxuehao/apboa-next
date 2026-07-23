package com.hxh.apboa.node.intent;

import lombok.Getter;
import lombok.Setter;

/**
 * 意图匹配项
 *
 * @author huxuehao
 */
@Getter
@Setter
public class IntentMatch {
    /**
     * 意图名称（匹配值）
     */
    private String name;
    /**
     * 意图描述（用于构建 LLM 提示词）
     */
    private String description;
    /**
     * 下游节点 ID
     */
    private String nextNodeId;
}
