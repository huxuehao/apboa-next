package com.hxh.apboa.node.start;

import lombok.Getter;
import lombok.Setter;

/**
 * 描述：访问限制
 *
 * @author huxuehao
 **/
@Getter
@Setter
public class AccessLimit {
    /**
     * 是否开始访问限制
     */
    private Boolean enable;
    /**
     * 单位：MINUTE/HOUR/DAY
     */
    private String  unit;
    /**
     * IP访问次数
     * 控制单个 IP 在单位时间内访问该路由的次数上限，0 表示不限制
     */
    private Integer ipTimes;
    /**
     * 路由访问次数
     * 控制当前路由在单位时间内访问次数总和的上限，0 表示不限制
     */
    private Integer routerTimes;
}
