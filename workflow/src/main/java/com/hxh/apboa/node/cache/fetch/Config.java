package com.hxh.apboa.node.cache.fetch;

import com.hxh.apboa.node.base.NodeConfig;
import com.hxh.apboa.node.base.template.FormatterType;
import lombok.Getter;
import lombok.Setter;

/**
 * 描述：缓存获取节点配置
 *
 * @author huxuehao
 **/
@Getter
@Setter
public class Config implements NodeConfig {

    /**
     * 缓存ID，对应 apboa-cache 中配置的缓存实例
     */
    private String cacheId;

    /**
     * 缓存键，支持 Velocity 动态变量语法，如 "user:${userId}"
     * 参数值由 inputConfigs 动态解析后替换
     */
    private String key;

    /**
     * 模板格式化器类型，默认 VELOCITY
     */
    private FormatterType formatterType = FormatterType.VELOCITY;
}
