package com.hxh.apboa.workflow.core;

import lombok.Getter;
import lombok.Setter;

/**
 * 描述：边的定义
 *
 * @author huxuehao
 **/
@Getter
@Setter
public class Edge {
    // 边的id
    private String id;
    // 边的起点
    private String source;
    // 边的终点
    private String target;

    public Edge(String id, String source, String target) {
        this.id = id;
        this.source = source;
        this.target = target;
    }
}
