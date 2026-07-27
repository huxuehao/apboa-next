package com.hxh.apboa.gateway.enums;

import lombok.Getter;

/**
 * 描述：网关API访问限制类型（固定时间窗）
 *
 * @author huxuehao
 **/
@Getter
public enum GatewayLimitType {
    DAY(86400),
    HOUR(3600),
    MINUTE(60),
    NONE(0);

    /** 时间窗长度（秒） */
    private final long seconds;

    GatewayLimitType(long seconds) {
        this.seconds = seconds;
    }
}
