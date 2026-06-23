package com.hxh.apboa.node.iterate;

import com.hxh.apboa.node.base.CodeLanguage;
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
    /**
     * 迭代处理代码
     */
    private String iterateCode;
    /**
     * 代码语言
     */
    private CodeLanguage language;
}
