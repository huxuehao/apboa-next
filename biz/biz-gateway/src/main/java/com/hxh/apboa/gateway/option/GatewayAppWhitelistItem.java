package com.hxh.apboa.gateway.option;

import lombok.Getter;
import lombok.Setter;

/**
 * 描述：网关应用访问白名单条目
 * 存储于应用配置中，白名单非空时仅允许命中IP访问该应用
 *
 * @author huxuehao
 **/
@Getter
@Setter
public class GatewayAppWhitelistItem {
    /**
     * 来源IP（精确匹配）
     */
    private String ip;
    /**
     * 描述
     */
    private String remark;
}
