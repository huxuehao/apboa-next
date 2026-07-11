package com.hxh.apboa.node.toolexecute;

import com.hxh.apboa.node.base.NodeConfig;
import lombok.Getter;
import lombok.Setter;

/**
 * 工具执行节点配置。
 *
 * @author huxuehao
 */
@Getter
@Setter
public class Config implements NodeConfig {
    /**
     * 工具ID。
     */
    private Long toolId;
}
