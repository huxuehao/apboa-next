package com.hxh.apboa.node.match.result;

import lombok.Getter;
import lombok.Setter;

/**
 * 描述：匹配配置
 *
 * @author huxuehao
 **/
@Getter
@Setter
public class MatchConfig {
    // 匹配值，类型为字符串，这是因为我在匹配的时候会将输入的参数进行toString()
    private String matchValue;
    private String nextNodeId;
}
