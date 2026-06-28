package com.hxh.apboa.node.start;

import com.hxh.apboa.node.base.inputout.OutputConfig;
import lombok.Getter;
import lombok.Setter;

/**
 * 描述：请求参数
 *
 * @author huxuehao
 **/
@Getter
@Setter
public class Param {
    /**
     * 参数名称
     */
    private String name;
    /**
     * 参数值
     */
    private String value;
    /**
     * 参数类型
     */
    private OutputConfig.VariableType type;
    /**
     * 是否必填
     */
    private Boolean required;
    /**
     * 备注
     */
    private String remark;
}
