package com.hxh.apboa.node.end;

import com.hxh.apboa.node.base.NodeConfig;
import com.hxh.apboa.node.base.template.FormatterType;
import lombok.Getter;
import lombok.Setter;

/**
 * 描述：配置信息
 *
 * @author huxuehao
 **/
@Getter
@Setter
public class Config implements NodeConfig {
    /**
     * 响应模板
     */
    private String responseTemplate;
    /**
     * 响应模板格式化类型
     */
    private FormatterType formatterType = FormatterType.JACKSON;
}
