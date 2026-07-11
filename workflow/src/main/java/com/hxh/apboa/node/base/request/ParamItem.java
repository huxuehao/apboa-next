package com.hxh.apboa.node.base.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 描述：请求参数项
 *
 * @author huxuehao
 **/
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParamItem {
    /**
     * 参数名称
     */
    private String name;
    /**
     * 参数值
     */
    private Object value;
}
