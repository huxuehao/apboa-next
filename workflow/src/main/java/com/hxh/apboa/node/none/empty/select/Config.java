package com.hxh.apboa.node.none.empty.select;

import com.hxh.apboa.node.base.NodeConfig;
import lombok.Getter;
import lombok.Setter;

/**
 * 描述：配置类
 *
 * @author huxuehao
 **/
@Getter
@Setter
public class Config implements NodeConfig {
    /**
     * 选择策略
     * 默认为第一个
     * 1. FIRST: 返回第一个
     * 2. LAST: 返回最后一个
     */
    private Strategy strategy = Strategy.FIRST;
    private String defaultNextNodeId;

    public enum Strategy {
        FIRST, // 返回第一个
        LAST // 返回最后一个
    }
}
