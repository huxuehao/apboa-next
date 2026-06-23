package com.hxh.apboa.node.match.result;

import com.hxh.apboa.node.base.NodeConfig;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 描述：配置类
 *
 * @author huxuehao
 **/
@Getter
@Setter
public class Config implements NodeConfig {
    private List<MatchConfig> matches;
    private MatchType matchType = MatchType.EQUALS;
    private boolean caseSensitive = true; // 区分大小写
    private String defaultNextNodeId;
}
