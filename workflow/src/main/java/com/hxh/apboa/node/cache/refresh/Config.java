package com.hxh.apboa.node.cache.refresh;

import com.hxh.apboa.node.base.NodeConfig;
import com.hxh.apboa.node.base.template.FormatterType;
import lombok.Getter;
import lombok.Setter;

/**
 * 描述：缓存刷新节点配置
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
     * 缓存键，支持 Velocity 动态变量语法
     */
    private String key;

    /**
     * 新的过期时间（秒）
     */
    private Long expire;

    /**
     * 模板格式化器类型，默认 VELOCITY
     */
    private FormatterType formatterType = FormatterType.VELOCITY;
}
