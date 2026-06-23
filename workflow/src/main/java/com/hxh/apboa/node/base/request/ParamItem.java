package com.hxh.apboa.node.base.request;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * 描述：请求参数项
 *
 * @author huxuehao
 **/
@Getter
@Setter
@Builder
public class ParamItem {
    /**
     * 参数位置
     */
    private Position position;
    /**
     * 参数名称
     */
    private String name;
    /**
     * 参数值
     */
    private String value;
}
