package com.hxh.apboa.node.serialize;

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
    // 序列化模式
    private SerializeMode mode;
    // 序列化格式
    private SerializeFormat format;
    // 是否排除空值
    private boolean excludeNulls;
    // 是否排除空字符串
    private boolean excludeEmptyStrings;
    // 当format为BASE64/URL_ENCODED时需要设置编码格式
    private String encoding;
}
