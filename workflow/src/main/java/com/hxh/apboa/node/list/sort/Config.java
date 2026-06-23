package com.hxh.apboa.node.list.sort;

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
    // 表达式求值器类型
    private String evaluatorType = "GROOVY";
    // 条件表达式（按照什么进行排序）
    private String condition;
    // 排序类型
    private SortDirection direction = SortDirection.ASC;
    // 是否空值前排，默认false（空值排在末尾）
    private Boolean nullFirst = false;
    // 是否是严格模式，在严格模式下，遇到错误元素时，会抛出异常。默认false
    private Boolean strictMode = false;
}
