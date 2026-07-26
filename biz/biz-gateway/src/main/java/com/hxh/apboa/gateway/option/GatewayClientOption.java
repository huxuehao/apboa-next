package com.hxh.apboa.gateway.option;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

/**
 * 描述：网关客户端运行时选项（数据面鉴权缓存所需的完整信息）
 *
 * @author huxuehao
 **/
@Getter
@Setter
@NoArgsConstructor
public class GatewayClientOption {
    private Long id;
    private Long tenantId;
    private String code;
    private String name;
    /**
     * 客户端过期时间（毫秒时间戳，null表示永不过期）
     */
    private Long expireAt;
    /**
     * Token签名密钥
     */
    private String tokenSecret;
    /**
     * Token有效期（毫秒）
     */
    private Long tokenTtl;
    /**
     * 在线状态：1在线、0下线
     */
    private Integer online;
    /**
     * 已授权的API ID集合
     */
    private Set<Long> apiIds;

    /**
     * 判断客户端当前是否有效（在线且未过期）
     */
    public boolean valid() {
        if (online == null || online != 1) {
            return false;
        }
        return expireAt == null || expireAt > System.currentTimeMillis();
    }
}
