package com.hxh.apboa.node.start;

import lombok.Getter;
import lombok.Setter;

/**
 * 描述：鉴权配置
 *
 * @author huxuehao
 **/
@Getter
@Setter
public class Authenticate {
    /**
     * 是否开启鉴权
     */
    private Boolean enable;
    /**
     * 鉴权位置：Header/Query
     */
    private String position;
    /**
     * 鉴权键值对名
     */
    private String name;
    /**
     * 鉴权键值对值
     */
    private String value;
}
