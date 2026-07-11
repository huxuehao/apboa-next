package com.hxh.apboa.node.start;

import com.hxh.apboa.node.base.NodeConfig;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 描述：配置类
 *
 * @author huxuehao
 **/
@Getter
@Setter
public class Config implements NodeConfig {
    /**
     * 请求参数
     */
    private List<Param> params = List.of();
}
