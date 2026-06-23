package com.hxh.apboa.node.string.template;

import com.hxh.apboa.node.base.NodeConfig;
import com.hxh.apboa.node.base.template.FormatterType;
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
    // 模版引擎只支持 STRING 和 VELOCITY
    private FormatterType templateType = FormatterType.STRING;
    private String template;
}
