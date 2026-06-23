package com.hxh.apboa.node.code;

import com.hxh.apboa.node.base.CodeLanguage;
import com.hxh.apboa.node.base.NodeConfig;
import lombok.Getter;
import lombok.Setter;

/**
 * 描述：配置配置项
 *
 * @author huxuehao
 **/
@Getter
@Setter
public class Config implements NodeConfig {
    /**
     * 执行代码源码
     */
    private String codeSource;
    /**
     * 语言
     */
    private CodeLanguage language;
}
