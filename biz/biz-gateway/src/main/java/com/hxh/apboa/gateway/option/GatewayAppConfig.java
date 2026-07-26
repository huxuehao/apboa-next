package com.hxh.apboa.gateway.option;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 描述：网关应用配置（存储于 gateway_app.config JSON 字段）
 *
 * @author huxuehao
 **/
@Getter
@Setter
public class GatewayAppConfig {
    /**
     * 是否开启跨域
     */
    private Boolean corsOpen = false;
    /**
     * 允许的来源（*表示全部）
     */
    private String allowedOrigin;
    /**
     * 是否允许携带凭证
     */
    private Boolean allowCredentials;
    /**
     * 预检结果缓存时间（秒）
     */
    private Integer maxAgeSeconds;
    /**
     * 允许的请求方法
     */
    private List<String> allowedMethods;
    /**
     * 请求体最大长度（字节，小于等于0表示不限制）
     */
    private Long contentLength = 2L * 1024 * 1024;
}
